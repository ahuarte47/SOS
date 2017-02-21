/**
 * Copyright (C) 2012-2016 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package org.n52.sos.extensions.hydrology.epanet;

import java.io.File;

import java.sql.Connection;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.addition.epanet.network.FieldsMap;
import org.addition.epanet.network.Network;
import org.addition.epanet.network.io.input.InputParser;
import org.addition.epanet.network.structures.Link;
import org.addition.epanet.network.structures.Link.LinkType;
import org.addition.epanet.network.structures.Link.StatType;
import org.addition.epanet.network.structures.Node;
import org.addition.epanet.network.structures.Pump;
import org.addition.epanet.network.structures.Tank;
import org.addition.epanet.network.structures.Valve;

import org.n52.sos.extensions.ObservableObject;

import org.opengis.feature.simple.SimpleFeature;

/**
 * Provides a solver implementation of EPANET networks using the Baseform Java-implemented Epanet simulation engine.
 * http://baseform.com/np4/epanetTool.html
 * 
 * @author Alvaro Huarte <ahuarte@tracasa.es>
 */
public class BaseformEpanetSolver implements EpanetSolver 
{
    private static final Logger LOG = Logger.getLogger(EpanetModel.class.toString());
    
    /**
     * Load the configuration data from the specified settings entry.
     */
    @Override
    public void loadSettings(String settingsFileName, org.w3c.dom.Element rootEntry, org.w3c.dom.Element modelEntry, org.w3c.dom.Element solverEntry)
    {
    }
    
    /**
     * Solve the Network managed of a EPANET model and save results to the specified Sqlite database.
     */
    @Override
    public boolean solveNetwork(EpanetModel model, String sqliteFileName) throws RuntimeException
    {
        return solveNetwork(model, sqliteFileName, null);
    }
    /**
     * Solve the Network managed of a EPANET model and save results to the specified Sqlite database.
     */
    public boolean solveNetwork(EpanetModel model, String sqliteFileName, final Iterable<ObservableObject> newnetworkObjects) throws RuntimeException
    {
        File sptliteFile = model.createNetworkSchemaDatabase(sqliteFileName);
        File networkFile = new File(model.getFileName());
        
        String simulationName = sptliteFile.getName();
        if (simulationName.contains(".")) simulationName = simulationName.substring(0, simulationName.lastIndexOf('.'));
        boolean resultCode = false;
        
        FileHandler logFileHandler = null;
        Connection connection = null;
        
        try
        {
            Logger logFile = Logger.getLogger("NetworkLog_" + model.getName());
            logFileHandler = new FileHandler(new File(sptliteFile.getParent(), simulationName+".log").getAbsolutePath());
            logFileHandler.setFormatter(new SimpleFormatter());
            logFile.addHandler(logFileHandler);
            logFile.setUseParentHandlers(false);
            
            // Serialize the results to the output database.
            connection = org.n52.sos.extensions.hydrology.util.JDBCUtils.openSqliteConnection(sqliteFileName);
            connection.setAutoCommit(false);
            
            // Solve the simulation of the EPANET network.
            /* TEST AND DEBUG EPATool!
             * EPATool.main(new String[]{ networkFile.getAbsolutePath() });
             */
            Network network = openNetwork(networkFile, logFile);
            
            // Process each EDIT operation on the current Network.
            updateNetwork(network, newnetworkObjects);
            
            // Save network and results.
            org.n52.sos.extensions.hydrology.epanet.io.output.EpanetDatabaseComposer databaseComposer = new org.n52.sos.extensions.hydrology.epanet.io.output.EpanetDatabaseComposer();
            resultCode = databaseComposer.writeToDatabase(network, null, connection, true, logFile);
            
            connection.commit();
        }
        catch (Exception e)
        {
            try { connection.rollback(); } catch (Exception e2) { }
            sptliteFile.delete();
            
            String errorMsg = String.format("Exception solving the EPANET model '%s'", model.getName());
            throw new RuntimeException(errorMsg, e);
        }
        finally
        {
            org.n52.sos.extensions.hydrology.util.JDBCUtils.close(connection);
            
            if (logFileHandler!=null) logFileHandler.close();
        }
        return resultCode;
    }
    
    /**
     * Returns the EPANET Network structure from the specified File.
     */
    private static Network openNetwork(File networkFile, Logger log) throws RuntimeException
    {
        Network network = new Network();
        
        try
        {
            InputParser parserINP = InputParser.create(Network.FileType.INP_FILE, log);
            parserINP.parse(network, networkFile);
        }
        catch (org.addition.epanet.util.ENException e)
        {
            throw new RuntimeException(e);
        }
        return network;
    }
    
    /**
     * Parse the specified generic value as an double value.
     */
    private static Double parseSafeDoubleValue(Object value)
    {
        if (value != null)
        {
            if (value instanceof Number)
            {
                return ((Number)value).doubleValue();
            }
            else try
            {
                return Double.parseDouble(value.toString());
            }
            catch (Exception e) { }
        }
        return 0.0;
    }
    
    /**
     * Update the EPANET Network structure with the specified EDIT operations.
     */
    private static int updateNetwork(Network network, final Iterable<ObservableObject> newnetworkObjects)
    {
        int editionCount = 0;
        
        // Process each EDIT operation on the specified Network.
        if (newnetworkObjects != null)
        {
            FieldsMap fieldsMap = network.getFieldsMap();
            
            for (ObservableObject observableObject : newnetworkObjects)
            {
                SimpleFeature feature = observableObject.featureOfInterest;
                Object epanetObject = null;
                String oname = observableObject.objectName;
                String otype = observableObject.objectType;
                Object value = null;
                
                // ... fetch current object!
                if ((value = feature.getAttribute("dc_id")) != null || (value = feature.getAttribute("id")) != null || (value = feature.getAttribute("name")) != null)
                {
                    oname = value.toString();
                }
                if (oname != null && ((epanetObject = network.getNode(oname)) != null || (epanetObject = network.getLink(oname)) != null))
                {
                    // Entity exists, found!
                }
                if ((value = feature.getAttribute("object_type")) != null || (value = feature.getAttribute("type")) != null)
                {
                    otype = value.toString();
                }
                if ((value = feature.getAttribute("operationCode")) != null)
                {
                    int operationCode = parseSafeDoubleValue(value).intValue();
                    if (updateNetworkObject(network, fieldsMap, observableObject, operationCode, otype.toUpperCase(), oname, epanetObject)) editionCount++;
                }
            }
        }
        return editionCount;
    }
    /**
     * Update the EPANET Network object with the specified EDIT feature operation.
     */
    private static boolean updateNetworkObject(Network network, FieldsMap fieldsMap, ObservableObject observableObject, int operationCode, String objectType, String objectName, Object epanetObject)
    {
        // Virtual edit operations.
        //  FEATURE_OPERATION_NORMAL_FLAG = 0;
        //  FEATURE_OPERATION_SELECT_FLAG = 1;
        int FEATURE_OPERATION_APPEND_FLAG = 2;
        int FEATURE_OPERATION_DELETE_FLAG = 4;
        int FEATURE_OPERATION_UPDATE_FLAG = 8;
        
        // Feature deleted ?
        if ((operationCode & FEATURE_OPERATION_DELETE_FLAG) == FEATURE_OPERATION_DELETE_FLAG)
        {
            if (epanetObject == null) {
                return false;
            }
            if (epanetObject instanceof Link) { //-> Better CLOSE the link, it preserves the topology!
                Link link = (Link)epanetObject;
                link.setStatus(StatType.CLOSED);
                return true;
            }
            if (epanetObject instanceof Tank) { //-> This includes Reservoirs, It is a Node too!
                Tank tank = (Tank)epanetObject;
                tank.setH0(0.0);
                tank.setHmin(0);
                tank.setHmax(0);
                tank.setPattern(null);
            }
            if (epanetObject instanceof Node) {
                Node node = (Node)epanetObject;
                node.setInitDemand(0);
                if (node.getDemand() != null) node.getDemand().clear();
                node.setSource(null);
                return true;
            }
            LOG.warning(String.format("Epanet object type '%s' (name='%s') doesn't support DELETE operations", objectType, objectName));
            return false;
        }
        
        // Feature added ?
        if ((operationCode & FEATURE_OPERATION_APPEND_FLAG) == FEATURE_OPERATION_APPEND_FLAG)
        {
            // TODO: create new EPANET entity!
            
            operationCode |= FEATURE_OPERATION_UPDATE_FLAG;
        }
        
        // Feature updated ?
        if ((operationCode & FEATURE_OPERATION_UPDATE_FLAG) == FEATURE_OPERATION_UPDATE_FLAG)
        {
            SimpleFeature feature = observableObject.featureOfInterest;
            Object value = null;
            
            // TODO: verify attributes of the modified EPANET entity!
            
            // ... edit attributes of node-type objects.
            if (epanetObject == null) {
                return false;
            }            
            if (epanetObject instanceof Tank) {
                Tank tank = (Tank)epanetObject;
                if ((value = feature.getAttribute("pattern"     )) != null) tank.setPattern(network.getPattern(value.toString()));                
                if ((value = feature.getAttribute("initiallev"  )) != null) tank.setH0(parseSafeDoubleValue(value));
                if ((value = feature.getAttribute("head"        )) != null) tank.setH0(parseSafeDoubleValue(value));
                if ((value = feature.getAttribute("minimumlev"  )) != null) tank.setHmin(parseSafeDoubleValue(value));
                if ((value = feature.getAttribute("maximumlev"  )) != null) tank.setHmax(parseSafeDoubleValue(value));
            }
            if (epanetObject instanceof Node) {
                Node node = (Node)epanetObject;
                if ((value = feature.getAttribute("elevation"   )) != null) node.setElevation(parseSafeDoubleValue(value));
                if ((value = feature.getAttribute("demand"      )) != null) node.setInitDemand(parseSafeDoubleValue(value));
                return true;
            }
            
            // ... edit attributes of link-type objects.
            if (epanetObject instanceof Pump) {
                Pump pump = (Pump)epanetObject;
                if ((value = feature.getAttribute("pattern"     )) != null) pump.setEpat(network.getPattern(value.toString()));
            }
            if (epanetObject instanceof Valve) {
                Valve valve = (Valve)epanetObject;
                if ((value = feature.getAttribute("type"        )) != null) valve.setType(LinkType.parse(value.toString()));
                if ((value = feature.getAttribute("valve_type"  )) != null) valve.setType(LinkType.parse(value.toString()));
            }
            if (epanetObject instanceof Link) {
                Link link = (Link)epanetObject;
                if ((value = feature.getAttribute("diameter"    )) != null) link.setDiameter (parseSafeDoubleValue(value));
                if ((value = feature.getAttribute("roughness"   )) != null) link.setRoughness(parseSafeDoubleValue(value));
                if ((value = feature.getAttribute("minorloss"   )) != null) link.setFlowResistance(parseSafeDoubleValue(value));
                if ((value = feature.getAttribute("status"      )) != null) link.setStatus(value.toString().equalsIgnoreCase("CLOSED") ? StatType.CLOSED : StatType.OPEN);
                return true;
            }
            LOG.warning(String.format("Epanet object type '%s' (name='%s') doesn't support UPDATE operations", objectType, objectName));
            return false;
        }
        return false;
    }
}
