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
package org.n52.sos.extensions;

import org.joda.time.DateTimeZone;

/**
 * Defines a generic data Model interface with Objects that provide Observable Attributes.
 *  
 * @author Alvaro Huarte <ahuarte@tracasa.es>
 */
public interface ObservableModel
{
    /** No flags */
    public static final int NONE_FLAGS = 0;
    /** Grouping-objects-by-feature-type flag (It indicates whether the SOS objects must be grouped by feature type) */
    public static final int GROUPING_BY_FEATURE_TYPE_FLAG = 1;
    /** User-defined flag (It indicates whether the Model is user-defined or private, it is ignored in GetCapabilities requests) */
    public static final int USER_DEFINED_FLAG = 2;
    
    /**
     * Get the name of this Model.
     */
    public String getName();

    /**
     * Get the description of this Model.
     */
    public String getDescription();
    
    /**
     * Get the time zone of this Model.
     */
    public DateTimeZone getTimeZone();
    
    /**
     * Gets the capabilities flags of this Model.
     */
    public int capabilitiesFlags();
    
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
    public Iterable<ObservableObject> enumerateObservableObjects(final ObservableContextArgs observableContextArgs) throws RuntimeException;
    
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
    public Iterable<MeasureSet> enumerateMeasures(final ObservableContextArgs observableContextArgs) throws RuntimeException;
}