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

import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import org.n52.sos.extensions.Measure;
import org.n52.sos.extensions.MeasureSet;
import org.n52.sos.extensions.ObservableAttribute;
import org.n52.sos.extensions.ObservableContextArgs;
import org.n52.sos.extensions.ObservableModel;
import org.n52.sos.extensions.ObservableObject;
import org.n52.sos.extensions.hydrology.epanet.io.output.EpanetDatabaseComposer;

import com.vividsolutions.jts.geom.Polygon;

/**
 * Implements an enumerable cursor of MeasureSet entities from a SQL-command filter.
 * 
 * @author Alvaro Huarte <ahuarte@tracasa.es>
 */
class EpanetObservableMeasureCursor extends EpanetObservableDataCursor<MeasureSet>
{
    private ConcurrentLinkedQueue<MeasureSet> measureQueue = new ConcurrentLinkedQueue<MeasureSet>();
    private int requestFlags = ObservableContextArgs.NONE_FLAGS;
    private org.joda.time.DateTime timeFrom;
    private org.joda.time.DateTime timeTo;
    
    /** 
     * Creates a new EpanetObservableMeasureCursor object.
     * 
     * @param observableModel: Reference to the ObservableModel owner.
     * @param objectFilterPattern: Data filter pattern of valid type:object.property entities to return (e.g. '*:*.*[;...]').
     * @param objectMaximumCount: Maximum number of features of each type to return.
     * @param filterRegion: Spatial area to filter features to return.
     * @param envelope: Spatial envelope filter.
     * @param timeFrom: Minimum valid phenomenon DateTime.
     * @param timeTo: Maximum valid phenomenon DateTime.
     * @param requestFlags: Flags of the request.
     */
    public EpanetObservableMeasureCursor(ObservableModel observableModel, String sqliteFileName, CoordinateReferenceSystem coordinateSystem, String objectFilterPattern, int objectMaximumCount, Polygon filterRegion, ReferencedEnvelope envelope, org.joda.time.DateTime timeFrom, org.joda.time.DateTime timeTo, String whereClause, int requestFlags) throws RuntimeException
    {
        super(observableModel, sqliteFileName, coordinateSystem, objectFilterPattern, objectMaximumCount, filterRegion, envelope, timeFrom, timeTo, whereClause);
        this.requestFlags = requestFlags;
        this.timeFrom = timeFrom;
        this.timeTo = timeTo;
    }
    
    /** 
     * Read the MeasureSet from the specified ResultSet.
     */
    @Override
    protected MeasureSet readObservableObject(ResultSet recordset)
    {
        MeasureSet measureSet = measureQueue.poll();
        if (measureSet!=null) return measureSet;
        
        try
        {
            ObservableObject theObject = readMainObservableObjectInformation(recordset);
            if (theObject==null) return null;
            
            int propertyIndex = 1;
            propertyIndex += EpanetDatabaseComposer.makeTableDefinition(currentObjectType==1 ? EpanetDatabaseComposer.NODE_TABLENAME : EpanetDatabaseComposer.LINK_TABLENAME, true).size();
            
            String reportTableName = currentObjectType==1 ? EpanetDatabaseComposer.REPORT_NODE_TABLENAME : EpanetDatabaseComposer.REPORT_LINK_TABLENAME;
            List<Map.Entry<String,Class<?>>> tableDef = EpanetDatabaseComposer.makeTableDefinition(reportTableName, false);
            
            List<MeasureSet> measureList = new ArrayList<MeasureSet>();
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
            org.joda.time.DateTime phenomenonTime = null;
            org.joda.time.DateTime startTime = null;
            org.joda.time.DateTime finalTime = null;
            long numSteps = 0;
            
            boolean returnFirst = (requestFlags&ObservableContextArgs.FIRST_TIMEINSTANT_FLAG  )==ObservableContextArgs.FIRST_TIMEINSTANT_FLAG;
            boolean returnLast  = (requestFlags&ObservableContextArgs.LASTEST_TIMEINSTANT_FLAG)==ObservableContextArgs.LASTEST_TIMEINSTANT_FLAG;
            
            // Read information of attributes.
            do
            {
                if (!theObject.objectName.equalsIgnoreCase(recordset.getString(1))) //-> Skip next object ?
                {
                    break;
                }
                
                phenomenonTime = new DateTime(simpleDateFormat.parse(recordset.getString(propertyIndex+1)), currentModel.getTimeZone());
                phenomenonTime = phenomenonTime.withZone(DateTimeZone.UTC);
                if (startTime==null) startTime = finalTime = phenomenonTime; else finalTime = phenomenonTime;
                numSteps++;
                
                for (int itemIndex = 0, itemCount = tableDef.size()-2; itemIndex < itemCount; itemIndex++)
                {
                    Measure measureValue = new Measure();
                    measureValue.phenomenonTime = phenomenonTime;
                    if ((measureValue.value = recordset.getObject(propertyIndex+2+itemIndex))==null) continue;
                    
                    if (numSteps==1)
                    {
                        measureSet = new MeasureSet();
                        measureSet.ownerObject = theObject;
                        measureList.add(measureSet);
                    }
                    measureSet = measureList.get(itemIndex);
                    
                    // ... FIRST/LAST request?
                    if (returnFirst && measureSet.measures.size()>0) continue; else
                    if (returnLast  && measureSet.measures.size()>0) measureSet.measures.remove(0);
                    
                    measureSet.measures.add(measureValue);
                }
            }
            while (recordset.next());
            
            // Populate measures by complex time filter ?
            if (numSteps>0 && timeFrom!=ObservableObject.UNDEFINED_DATETIME_FILTER_FLAG && timeTo!=ObservableObject.UNDEFINED_DATETIME_FILTER_FLAG && !hasValidQueryableTimeFilter(timeFrom, timeTo))
            {
                List<MeasureSet> populatedList = new ArrayList<MeasureSet>();
                
                long stepTime = numSteps>1 ? (finalTime.getMillis()-startTime.getMillis()) / (numSteps-1) : 0;
                long initTime = timeFrom.getMillis();
                long endsTime = timeTo.getMillis();
                
                for (MeasureSet previousSet : measureList)
                {
                    MeasureSet populatedSet = new MeasureSet();
                    populatedSet.ownerObject = theObject;
                    numSteps = 0;
                    
                    long currentTime = endsTime;
                    long forwardTime = 0;
                    long nwstartTime = Long.MAX_VALUE;
                    long nwfinalTime = Long.MIN_VALUE;
                    long nwlastsTime = 0;
                    long measureSize = previousSet.measures.size();
                    
                    while (currentTime <= endsTime)
                    {
                        for (Measure measureValue : previousSet.measures)
                        {
                            currentTime = measureValue.phenomenonTime.getMillis() + forwardTime;
                            if (currentTime < initTime || (numSteps >= 1 && currentTime == nwlastsTime)) continue;
                            if (currentTime > endsTime || (numSteps == 1 && returnFirst)) { currentTime = Long.MAX_VALUE; break; }
                            
                            Measure populatedValue = new Measure();
                            populatedValue.phenomenonTime = new org.joda.time.DateTime(currentTime, measureValue.phenomenonTime.getZone());
                            populatedValue.value = measureValue.value;
                            
                            nwstartTime = Math.min(nwstartTime, currentTime);
                            nwfinalTime = Math.max(nwfinalTime, currentTime);
                            nwlastsTime = currentTime;
                            
                            populatedSet.measures.add(populatedValue);
                            numSteps++;
                        }
                        forwardTime += stepTime * (measureSize - 1);
                    }
                    if (nwstartTime != Long.MAX_VALUE) startTime = new org.joda.time.DateTime(nwstartTime, startTime.getZone());
                    if (nwfinalTime != Long.MIN_VALUE) finalTime = new org.joda.time.DateTime(nwfinalTime, finalTime.getZone());
                    populatedList.add(populatedSet);
                }
                measureList.clear();
                measureList = populatedList;
            }
            
            // Save information of attributes.
            if (numSteps>0)
            {
                // ... FIRST/LAST request?
                if (returnFirst) { finalTime = startTime; numSteps = 1; } else
                if (returnLast ) { startTime = finalTime; numSteps = 1; }
                
                long stepTime = numSteps>1 ? (finalTime.getMillis()-startTime.getMillis()) / (numSteps-1) : 0;
                int itemIndex = 0;
                
                for (Map.Entry<String,Class<?>> entry : tableDef)
                {
                    if (itemIndex>=2)
                    {
                        ObservableAttribute attribute = new ObservableAttribute();
                        attribute.name = entry.getKey();
                        attribute.description = "Provides measures of attribute '"+entry.getKey()+"' of object type '"+theObject.objectType+"'";
                        attribute.timeFrom = startTime;
                        attribute.timeTo = finalTime;
                        attribute.stepTime = stepTime;
                        attribute.units = uoms.get(attribute.name);
                        theObject.attributes.add(attribute);
                        
                        measureList.get(itemIndex-2).attribute = attribute;
                    }
                    itemIndex++;
                }
            }
            
            // Save information of measures.
            measureQueue.addAll(measureList);
            measureList.clear();
            measureList = null;
            
            return measureQueue.poll();
        }
        catch (Exception e) 
        {
            LOG.severe(e.getMessage());
            return null;
        }
    }
    
    /**
     * Returns if the Observable entity is valid or pass specific filters.
     */
    @Override
    protected boolean validateObservableObject(MeasureSet theObject)
    {
        return passObservableObject(theObject.ownerObject);
    }
}
