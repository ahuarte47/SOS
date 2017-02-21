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

import java.util.Collection;

import org.n52.sos.ogc.om.NamedValue;

/**
 * Defines a generic data Model interface with Objects that provide Observable Attributes,
 * and who also supports edit operations to create/update/delete information.
 * 
 * @author Alvaro Huarte <ahuarte@tracasa.es>
 */
public interface ObservableUpdatableModel extends ObservableModel
{
    /**
     * Create/Update/Delete the specified Observable Object collection in the data Model.
     * 
     * @param observableObjects: Object collection to update in the model.
     * @param operationContextArgs: Parameter collection related to the edit operation.
     * 
     * @return true whether this operation runs OK.
     */
    public boolean editObservableObjects(final Iterable<ObservableObject> observableObjects, final Collection<NamedValue<?>> operationContextArgs) throws RuntimeException;
}
