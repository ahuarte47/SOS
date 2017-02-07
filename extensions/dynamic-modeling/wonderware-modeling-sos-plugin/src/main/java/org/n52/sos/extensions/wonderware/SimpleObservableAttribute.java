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
package org.n52.sos.extensions.wonderware;

import org.joda.time.DateTime;
import org.n52.sos.extensions.ObservableAttribute;

/**
 * Provides information of one Observable Attribute of an Object in a Simple Feature data Model.
 * 
 * @author Alvaro Huarte <ahuarte@tracasa.es>
 */
class SimpleObservableAttribute extends ObservableAttribute
{
    /** Default maximum cycle count to read of a measure from a database. */
    public static long MAXIMUM_CYCLE_COUNT = 577;
    
    /** Creates a new SimpleObservableAttribute object. */
    public SimpleObservableAttribute()
    {
        super();
    }
    /** Creates a cloned SimpleObservableAttribute object. */
    public SimpleObservableAttribute(SimpleObservableAttribute attribute)
    {
        super(attribute);
        
        fieldId = attribute.fieldId;
        retrievalMode = attribute.retrievalMode;
        retrievalAlignment = attribute.retrievalAlignment;
        maximumCycleCount = attribute.maximumCycleCount;
    }
    
    /** Name of Field in the related FeatureStore. */
    public String fieldId;    
    /** Retrieval mode to read data of the related database. */
    public String retrievalMode = "Cyclic";
    /** Retrieval alignment to read data of the related database. */
    public RetrievalAlignment retrievalAlignment = RetrievalAlignment.EndDateAligned;
    /** Maximum cycle count to read measures of the related database. */
    public long maximumCycleCount = MAXIMUM_CYCLE_COUNT;

    /**
     * Calculates the start/time/cycle retrieval data. 
     */
    private Object[] calculateRetrievalData()
    {
        DateTime startTime = timeFrom;
        DateTime finalTime = timeTo!=null ? timeTo : DateTime.now(); //-> now()!
        
        long queryCycleCount = 1 + (finalTime.getMillis() - startTime.getMillis()) / stepTime;
        long maximCycleCount = maximumCycleCount>0 ? maximumCycleCount : MAXIMUM_CYCLE_COUNT;
        
        // Calculate a limited cycle count to avoid very long queries from the current TimePeriod.
        if (retrievalAlignment==RetrievalAlignment.ClampMaximum && queryCycleCount>maximCycleCount)
        {
            queryCycleCount = maximCycleCount;
        }
        if (retrievalAlignment!=RetrievalAlignment.Full && queryCycleCount>maximCycleCount)
        {
            queryCycleCount = maximCycleCount;
            
            if (retrievalAlignment==RetrievalAlignment.StartDateAligned)
            {
                finalTime = new DateTime(startTime.getMillis() + stepTime*(queryCycleCount-1), startTime.getZone());
            }
            else
            {
                startTime = new DateTime(finalTime.getMillis() - stepTime*(queryCycleCount-1), finalTime.getZone());
            }
        }
        return new Object[]{startTime,finalTime,queryCycleCount};
    }
    /** Calculates the retrieval start DateTime. */
    public DateTime retrievalStartDateTime()
    {
        return (DateTime)calculateRetrievalData()[0];
    }
    /** Calculates the retrieval final DateTime. */
    public DateTime retrievalFinalDateTime()
    {
        return (DateTime)calculateRetrievalData()[1];
    }
    /** Calculates the retrieval cycle count. */
    public long retrievalCicleCount()
    {
        return (long)calculateRetrievalData()[2];
    }
}
