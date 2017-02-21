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
import java.io.IOException;

import java.sql.Connection;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.ResourceBundle;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.joda.time.DateTime;

import org.n52.sos.extensions.MeasureSet;
import org.n52.sos.extensions.ObservableContextArgs;
import org.n52.sos.extensions.ObservableObject;
import org.n52.sos.extensions.ObservableUpdatableModel;
import org.n52.sos.extensions.hydrology.epanet.io.output.EpanetDatabaseComposer;
import org.n52.sos.extensions.model.AbstractModel;
import org.n52.sos.extensions.model.Model;
import org.n52.sos.extensions.model.ModelManager;
import org.n52.sos.extensions.util.FileUtils;
import org.n52.sos.ogc.om.NamedValue;
import org.n52.sos.ogc.om.values.Value;

import com.google.common.io.Files;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTReader;

/**
 * Implements the Hydraulic model of EPANET network structures.
 *  
 * @author Alvaro Huarte <ahuarte@tracasa.es>
 */
public class EpanetModel extends AbstractModel implements ObservableUpdatableModel
{
    private static final Logger LOG = Logger.getLogger(EpanetModel.class.toString());
    
    /** Default seed database as target of Network structures. */
    private static final String DEFAULT_SQLITE_SEED_DATABASE_FILE = "epanet/sqlite.template.db";
    
    /** Default ResourceBundle to localize messages. */
    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("epanet/messages");
    
    /** Semaphore to lock temporary solving tasks to a maximum of --## 5 ## -- concurrent processes. */
    private static Semaphore RESOURCE_SEMAPHORE = new Semaphore(5);
    
    private String fileName;
    private String sqliteFileName;
    private String objectFilter = "*:*.*";
    private int objectMaximumCount = 0;
    private Polygon filterRegion = null;
    private String smtpHostServer;
    
    private CoordinateReferenceSystem coordinateSystem;
    private EpanetSolver solver;
    
    /** Gets the EPANET Network FileName of this model. */
    public String getFileName()
    {
        return fileName;
    }
    
    /**
     * Copy the settings of this model from the specified source object.
     */
    @Override
    public void CopySettings(AbstractModel model)
    {
        super.CopySettings(model);
        
        if (model instanceof EpanetModel)
        {
            EpanetModel epmodel = (EpanetModel)model;            
            this.fileName = epmodel.fileName;
            this.sqliteFileName = epmodel.sqliteFileName;
            this.objectFilter = epmodel.objectFilter;
            this.objectMaximumCount = epmodel.objectMaximumCount;
            this.filterRegion = epmodel.filterRegion;
            this.coordinateSystem = epmodel.coordinateSystem;
            this.solver = epmodel.solver;
            this.smtpHostServer = epmodel.smtpHostServer;
        }
    }
    
    /**
     * Load the configuration data from the specified settings entry.
     */
    @Override
    public boolean loadSettings(ModelManager modelManager, String settingsFileName, org.w3c.dom.Element rootEntry, org.w3c.dom.Element modelEntry)
    {
        if (super.loadSettings(modelManager, settingsFileName, rootEntry, modelEntry))
        {
            NodeList nodeList = modelEntry.getChildNodes();
            NodeList smtpList = null;
            
            if ((smtpList = rootEntry.getElementsByTagName("smtpHostServer")) != null && smtpList.getLength() > 0)
            {
                smtpHostServer = smtpList.item(0).getTextContent();
            }
            for (int i = 0, icount = nodeList.getLength(); i < icount; i++)
            {
                Node node = nodeList.item(i);
                if (node.getNodeType()!=Node.ELEMENT_NODE) continue;
                String nodeName = node.getNodeName();
                
                if (nodeName.equalsIgnoreCase("srid"))
                {
                    String srid = node.getTextContent();
                    
                    if (!srid.equals("0")) try
                    {
                        coordinateSystem = srid.toUpperCase().contains("EPSG:") ? CRS.decode(srid) : CRS.parseWKT(srid);
                    }
                    catch (Exception e)
                    {
                        LOG.severe(String.format("Invalid CoordinateReferenceSystemID defined in settings entry '%s', msg='%s'", name, e.getMessage()));
                    }
                }
                else
                if (nodeName.equalsIgnoreCase("fileName"))
                {
                    java.net.URI uri = FileUtils.resolveAbsoluteURI(node.getTextContent(), settingsFileName);
                    
                    if (uri==null)
                    {
                        LOG.severe(String.format("Invalid EPANET model FileName defined in settings entry '%s', msg='%s'", name, node.getTextContent()));
                        return false;
                    }
                    else
                    {
                        File file = new File(uri);
                        fileName = file.getAbsolutePath();
                    }
                }
                else
                if (nodeName.equalsIgnoreCase("objectFilter"))
                {
                    objectFilter = node.getTextContent();
                }
                else
                if (nodeName.equalsIgnoreCase("objectMaximumCount"))
                {
                    String text = node.getTextContent();
                    if (text.length()>0) objectMaximumCount = Integer.parseInt(text);
                }
                else
                if (nodeName.equalsIgnoreCase("filterRegion"))
                {
                    String text = node.getTextContent();
                    
                    if (text.length()>0) try
                    {
                        WKTReader reader = new WKTReader();
                        filterRegion = (Polygon)reader.read(text);
                    }
                    catch (Exception e)
                    {
                        LOG.severe(String.format("Invalid WKT('%s') for the filter region, msg='%s'", node.getTextContent(), e.getMessage()));
                    }
                }
                else
                if (nodeName.equalsIgnoreCase("networkSolver"))
                {
                    String className = ((Element)node).getAttributeNode("class").getValue();
                    
                    try 
                    {
                        solver = (EpanetSolver)Class.forName(className).newInstance();
                        solver.loadSettings(settingsFileName, rootEntry, modelEntry, (Element)node);
                    }
                    catch (Exception e) 
                    {
                        LOG.severe(String.format("Invalid EpanetSolver Type defined in settings entry '%s', msg='%s'", className, e.getMessage()));
                        return false;
                    }
                }
            }
            return solver!=null;
        }
        return false;
    }
    
    /**
     * Create a Network database related to this EPANET model.
     */
    protected File createNetworkSchemaDatabase(String sqliteFileName) throws RuntimeException
    {
        java.net.URI sptseedFile = FileUtils.resolveAbsoluteURI(DEFAULT_SQLITE_SEED_DATABASE_FILE, EpanetModel.class.getClassLoader());
        File sptliteFile = new File(sqliteFileName);
        
        // -----------------------------------------------------------------------------------------------------------------
        // Clone the seed to the specified EPANET database.
        
        String sqliteSeedFileName = DEFAULT_SQLITE_SEED_DATABASE_FILE;
        if (sptseedFile!=null) sqliteSeedFileName = sptseedFile.toString();
        
        LOG.info(String.format("Cloning the network seed database '%s' for the EPANET model '%s'", sqliteSeedFileName, name));
        
        if (sptseedFile==null)
        {
            String errorMsg = String.format("Invalid network seed database '%s'", sqliteSeedFileName);
            throw new RuntimeException(errorMsg);
        }
        try
        {
            org.apache.commons.io.FileUtils.copyURLToFile(sptseedFile.toURL(), sptliteFile);
        }
        catch (IOException e)
        {
            String errorMsg = String.format("Exception copying the Spatialite seed database from '%s' to '%s'", sptseedFile.getPath(), sptliteFile.toPath());
            throw new RuntimeException(errorMsg);
        }
        
        LOG.info(String.format("Network database '%s' of EPANET model '%s' initialized!", sptliteFile.getAbsolutePath(), name));
        
        // -----------------------------------------------------------------------------------------------------------------
        // Fill the EPANET Database schema.
        
        LOG.info(String.format("Creating the network database schema of EPANET model '%s'", name));
        
        Connection connection = null;
                
        try
        {
            connection = org.n52.sos.extensions.hydrology.util.JDBCUtils.openSqliteConnection(sqliteFileName);
            connection.setAutoCommit(false);
            
            EpanetDatabaseComposer.createSchemaDatabase(connection);
            connection.commit();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            org.n52.sos.extensions.hydrology.util.JDBCUtils.close(connection);
        }
        
        LOG.info(String.format("Database schema of EPANET model '%s' created!", name));
        
        return sptliteFile;
    }

    /**
     * Prepare the Network structure managed.
     */
    @Override
    public boolean prepareObject() throws RuntimeException
    {
        String currentSqliteFileName = sqliteFileName;
        
        File networkFile = new File(fileName);
        File sptliteFile = null;
        
        // Test valid EPANET network FileName.
        if (!networkFile.exists())
        {
            String errorMsg = String.format("Invalid EPANET model FileName defined '%s'", fileName);
            throw new RuntimeException(errorMsg);
        }
        if (com.google.common.base.Strings.isNullOrEmpty(currentSqliteFileName))
        {
            currentSqliteFileName = fileName+".db";
            sqliteFileName = currentSqliteFileName;
        }
        
        // Test valid EPANET database FileName.
        if ((sptliteFile = new File(currentSqliteFileName)).exists() && networkFile.lastModified()>sptliteFile.lastModified())
        {
            String simulationName = Long.toString(System.currentTimeMillis());
            
            LOG.info(String.format("Deleting obsolete Spatialite database '%s'", sptliteFile.getAbsolutePath()));
            sptliteFile.deleteOnExit();
            
            sptliteFile = new File( FileUtils.resolveAbsoluteURI(fileName+"."+simulationName+".db" , fileName) );
            sptliteFile.deleteOnExit();            
            File logFle = new File( FileUtils.resolveAbsoluteURI(fileName+"."+simulationName+".log", fileName) );
            logFle.deleteOnExit();
            
            currentSqliteFileName = sptliteFile.getPath(); 
        }
        
        // Simulate the Network and save results when needed.
        if (!sptliteFile.exists())
        {
            LOG.info(String.format("Preparing the network database '%s' for the EPANET model '%s'", currentSqliteFileName, name));
            
            if (solver==null)
            {
                LOG.severe(String.format("Undefined Network solver of EPANET model '%s'", name));
                return false;
            }
            if (solver.solveNetwork(this, currentSqliteFileName))
            {
                LOG.info(String.format("Network database for the EPANET model '%s' successly prepared!", name));
                sqliteFileName = currentSqliteFileName;
                return true;
            }                
            LOG.warning(String.format("Network database for the EPANET model '%s' preparation failed!", name));
            return false;
        }
        return true;
    }
    
    /**
     * Enumerate the available Observable Object collection from the specified filter criteria.
     * 
     * @param observableContextArgs: Information context of a request to fetch objects.
     * <p>With:
     * @param objectId: Object ID or Name from who recover data (Optional).
     * @param envelope: Spatial envelope filter.
     * @param timeFrom: Minimum valid phenomenon DateTime.
     * @param timeTo: Maximum valid phenomenon DateTime.
     * @param flags: Flags of the request.
     * 
     * @return ObservableObject collection that matches the specified filter criteria.
     */
    public Iterable<ObservableObject> enumerateObservableObjects(final ObservableContextArgs observableContextArgs) throws RuntimeException
    {
        final Model currentModel = this;
        
        return new Iterable<ObservableObject>() 
        {
            public final Iterator<ObservableObject> iterator() 
            {
                String whereClause = "";
                
                String objectFilterToUse = objectFilter;
                int objectMaximumCountToUse = objectMaximumCount;
                Polygon filterRegionToUse = filterRegion;
                
                final String objectId = observableContextArgs.objectId;
                final String objectType = observableContextArgs.objectType;
                final ReferencedEnvelope envelope = observableContextArgs.envelope;
                final DateTime timeFrom = observableContextArgs.timeFrom;
                final DateTime timeTo = observableContextArgs.timeTo;
                
                // Only apply the filter for 'GetCapabilities' requests.
                if (!(observableContextArgs.request instanceof org.n52.sos.request.GetCapabilitiesRequest))
                {
                    objectFilterToUse = "*:*.*";
                    objectMaximumCountToUse = 0;
                    filterRegionToUse = null;
                }
                // Define the where clause if needed.
                if (!com.google.common.base.Strings.isNullOrEmpty(objectId))
                {
                    whereClause = ObservableObject.composeWhereClause("a.object_id", objectId, true);
                }
                if (!com.google.common.base.Strings.isNullOrEmpty(objectType))
                {
                    String type = ObservableObject.composeWhereClause("a.enet_type", objectType, true);
                    
                    if (!com.google.common.base.Strings.isNullOrEmpty(whereClause))
                    {
                        whereClause = "("+whereClause+") AND ("+type+")";
                    }
                    else
                    {
                        whereClause = type;
                    }
                }
                return new EpanetObservableObjectCursor(currentModel,sqliteFileName, coordinateSystem, objectFilterToUse, objectMaximumCountToUse, filterRegionToUse, envelope, timeFrom, timeTo, whereClause);
            }
        };
    }
    /**
     * Enumerate the available Measures from the specified filter criteria.
     * 
     * @param observableContextArgs: Information context of a request to fetch objects.
     * <p>With:
     * @param objectId: Object ID or Name from who recover data (Optional).
     * @param envelope: Spatial envelope filter.
     * @param timeFrom: Minimum valid phenomenon DateTime.
     * @param timeTo: Maximum valid phenomenon DateTime.
     * @param flags: Flags of the request.
     * 
     * @return ObservableResultSet collection that matches the specified filter criteria.
     */
    public Iterable<MeasureSet> enumerateMeasures(final ObservableContextArgs observableContextArgs) throws RuntimeException
    {
        final Model currentModel = this;
        
        return new Iterable<MeasureSet>() 
        {
            public final Iterator<MeasureSet> iterator()
            {
                String whereClause = "";
                
                String objectFilterToUse = objectFilter;
                int objectMaximumCountToUse = objectMaximumCount;
                Polygon filterRegionToUse = filterRegion;
                
                final String objectId = observableContextArgs.objectId;
                final String objectType = observableContextArgs.objectType;
                final ReferencedEnvelope envelope = observableContextArgs.envelope;
                final DateTime timeFrom = observableContextArgs.timeFrom;
                final DateTime timeTo = observableContextArgs.timeTo;
                final int flags = observableContextArgs.flags;
                
                // Only apply the filter for 'GetCapabilities' requests.
                if (!(observableContextArgs.request instanceof org.n52.sos.request.GetCapabilitiesRequest))
                {
                    objectFilterToUse = "*:*.*";
                    objectMaximumCountToUse = 0;
                    filterRegionToUse = null;
                }
                // Define the where clause if needed.
                if (!com.google.common.base.Strings.isNullOrEmpty(objectId))
                {
                    whereClause = ObservableObject.composeWhereClause("a.object_id", objectId, true);
                }
                if (!com.google.common.base.Strings.isNullOrEmpty(objectType))
                {
                    String type = ObservableObject.composeWhereClause("a.enet_type", objectType, true);
                    
                    if (!com.google.common.base.Strings.isNullOrEmpty(whereClause))
                    {
                        whereClause = "("+whereClause+") AND ("+type+")";
                    }
                    else
                    {
                        whereClause = type;
                    }
                }                
                return new EpanetObservableMeasureCursor(currentModel,sqliteFileName, coordinateSystem, objectFilterToUse, objectMaximumCountToUse, filterRegionToUse, envelope, timeFrom, timeTo, whereClause, flags);
            }
        };
    }
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // ObservableUpdatableModel implementation
    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////
    
    /** Helper class to asynchronously test the expiration of a dynamic model. */
    private class MyExpirationTestRunnable implements Runnable
    {
        /** Creates a new MyExpirationTestRunnable object. */
        public MyExpirationTestRunnable(ModelManager modelManager, EpanetModel model, String modelName, Date expirationDate)
        {
            this.modelManager = modelManager;
            this.model = model;
            this.modelName = modelName;
            this.expirationDate = expirationDate;
        }
        private ModelManager modelManager;
        private EpanetModel model;
        private String modelName;
        private Date expirationDate;
        
        /** Cleanup/Remove all files related of a data Model. */
        private void cleanupAllFiles(String fileName)
        {
            File epanetFile = new File(fileName);
            File sqliteFile = new File(fileName + ".db");
            File logginFile = new File(fileName + ".log");
            File epanetPath = epanetFile.getParentFile();
            
            try
            {
                if (epanetFile.exists()) epanetFile.delete();
                if (sqliteFile.exists()) sqliteFile.delete();
                if (logginFile.exists()) logginFile.delete();
                if (epanetPath.exists()) epanetPath.delete();
            }
            catch (Exception e)
            {
                LOG.severe(e.getMessage());
            }
        }
        /** Cleanup/Remove all files of the data Model. */
        private void cleanupAllFiles()
        {
            cleanupAllFiles(model.fileName);
        }
        
        @Override
        public void run() 
        {
            try
            {
                File epanetFile = new File(model.fileName);
                int sleepTime = 60000;
                
                Runtime.getRuntime().addShutdownHook(new Thread() 
                {
                    @Override
                    public void run()
                    {
                        cleanupAllFiles();
                    }
                });
                while (epanetFile.exists())
                {
                    Thread.sleep(sleepTime);
                    Date now = new Date();
                    //LOG.warning(String.format("Checking expiration of temporary EPANET model '%s'!", projectName));
                    
                    if (!epanetFile.exists() || now.getTime() > expirationDate.getTime())
                    {
                        LOG.info(String.format("Temporary EPANET model '%s' expired!", modelName));
                        modelManager.preparedModelsRef().remove(model);
                        cleanupAllFiles();
                        break;
                    }
                }
            }
            catch (Exception e)
            {
                LOG.severe(e.getMessage());
            }
        }
    }
    
    /**
     * Create/Update/Delete the specified Observable Object collection in the data Model.
     * 
     * @param observableObjects: Object collection to update in the model.
     * @param operationContextArgs: Parameter collection related to the edit operation.
     * 
     * @return true whether this operation runs OK.
     */
    @Override
    public boolean editObservableObjects(final Iterable<ObservableObject> observableObjects, final Collection<NamedValue<?>> operationContextArgs) throws RuntimeException    
    {
        String projectName = "";
        String projectUuid = "";
        String description = "";
        int expirationTime = 3600 * 1000;
        String email = "";
        
        // Parse virtual project attributes.
        for (NamedValue<?> namedValue : operationContextArgs)
        {
            String   name = namedValue.getName().getHref().toLowerCase();
            Value<?> data = namedValue.getValue();
            Object   oval = null;
            
            if ((oval = data.getValue()) != null)
            {
                if (name.equals("vrtproject-name"       )) projectName = oval.toString(); else
                if (name.equals("vrtproject-uuid"       )) projectUuid = oval.toString(); else
                if (name.equals("vrtproject-email"      )) email = oval.toString(); else
                if (name.equals("vrtproject-description")) description = oval.toString(); else
                if (name.equals("vrtproject-expiration" )) expirationTime = Integer.parseInt(oval.toString());
            }
        }
        if (projectName.length() == 0 || projectUuid.length() == 0 || expirationTime == 0 || description == null)
        {
            throw new RuntimeException(String.format("The edit operation in model '%s' doesn't define some metadata attributes (Name, UUID, Expiration).", getName()));
        }
        if (solver == null || !(solver instanceof BaseformEpanetSolver))
        {
            throw new RuntimeException(String.format("The solver of the model '%s' doesn't support edit operations (We only can use the BaseformEpanet library).", getName()));
        }
        
        String smtpHostServer = this.smtpHostServer;
        String epanetFileName = "";
        boolean acquired = false;
        String subject = "";
        String bodyMsg = "";
        Date now = new Date();
        
        // Create a new cloned virtual instance of the EpanetModel... and update and solve its EPANET network.
        try
        {
            String tempDir  = System.getProperty("java.io.tmpdir");
            File epanetDir  = new File(tempDir, projectUuid);
            File sourceFile = new File(this.fileName);
            File epanetFile = new File(epanetDir, new File(this.fileName).getName());
            File sqliteFile = new File(epanetFile.getAbsolutePath() + ".db");
            epanetFileName  = epanetFile.getAbsolutePath();
            
            EpanetModel epanetModel = new EpanetModel();
            epanetModel.CopySettings(this);
            epanetModel.description = description.length() > 0 ? projectName + " (" + description + ")" : projectName;
            epanetModel.description += ". This model overrides '" + this.name + "'.";
            epanetModel.fileName = epanetFile.getAbsolutePath();
            epanetModel.sqliteFileName = sqliteFile.getAbsolutePath();
            epanetModel.capabilitiesFlags |= AbstractModel.USER_DEFINED_FLAG;
            
            // Exists results or solve.
            if (epanetDir.exists() && epanetFile.exists() && sqliteFile.exists())
            {
                subject = String.format(RESOURCE_BUNDLE.getString("EpanetModel.alreadyProcessed"), projectName);
                return true;
            }
            else
            {
                epanetDir.mkdir();
                Files.copy(sourceFile, epanetFile);
                
                // Acquire Lock.
                RESOURCE_SEMAPHORE.acquire();
                acquired = true;
                
                // Update and solve the new EPANET network.
                if (((BaseformEpanetSolver)epanetModel.solver).solveNetwork(epanetModel, epanetModel.sqliteFileName, observableObjects))
                {
                    Date expirationDate = new Date(new Date().getTime() + expirationTime + 10000); //-> For security, add a little time offset.
                    
                    epanetModel.name = projectUuid; //-> To avoid models with duplicated names.
                    modelManager.preparedModelsRef().add(epanetModel);
                    
                    Thread thread = new Thread(new MyExpirationTestRunnable(modelManager, epanetModel, projectName, expirationDate));
                    thread.start();
                    
                    subject = String.format(RESOURCE_BUNDLE.getString("EpanetModel.successProcess"), projectName);
                    return true;
                }
                else
                {
                    subject = String.format(RESOURCE_BUNDLE.getString("EpanetModel.failProcess"), projectName, "?");
                    return false;
                }
            }
        }
        catch (Exception e)
        {
            MyExpirationTestRunnable cleaner = new MyExpirationTestRunnable(null, null, null, null);
            cleaner.cleanupAllFiles(epanetFileName);
            
            subject = String.format(RESOURCE_BUNDLE.getString("EpanetModel.failProcess"), projectName, e.getMessage());
            throw new RuntimeException(e);
        }
        finally
        {
            // Release Lock.
            if (acquired)
            {
                RESOURCE_SEMAPHORE.release();
            }
            
            // Notify results task by email.
            DateFormat dateFormat = DateFormat.getDateTimeInstance();
            bodyMsg = String.format(RESOURCE_BUNDLE.getString("EpanetModel.bodyMessage"), projectName, projectName, description, dateFormat.format(now), subject);
            sendEmail(smtpHostServer, "no_reply@epanet.org", email, subject, bodyMsg);
        }
    }
    
    // Send a email with the specified parameters.
    private static boolean sendEmail(String smtpHostServer, String fromEmail, String toEmail, String subject, String body)
    {
        if (smtpHostServer != null && smtpHostServer.length() > 0 && toEmail != null && toEmail.length() > 0 && subject != null && subject.length() > 0) try
        {
            java.util.Properties props = System.getProperties();
            props.put("mail.smtp.host", smtpHostServer);
            
            Session session = Session.getDefaultInstance(props);
            MimeMessage msg = new MimeMessage(session);
            
            // Set message headers.
            msg.addHeader("Content-type", "text/HTML; charset=UTF-8");
            msg.addHeader("format", "flowed");
            msg.addHeader("Content-Transfer-Encoding", "8bit");
            msg.setFrom(new InternetAddress(fromEmail));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, false));
            msg.setSubject(subject, "UTF-8");
            msg.setText(body, "UTF-8");
            msg.setSentDate(new Date());
            
            Transport.send(msg);
            return true;
        }
        catch (Exception e)
        {
            LOG.severe(e.getMessage());
        }
        return false;
    }
}
