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
package org.n52.sos.decode.xml.stream.ogc.gml;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.n52.sos.decode.xml.stream.XmlReader;
import org.n52.sos.ogc.gml.GmlConstants;
import org.n52.sos.ogc.gml.time.TimeInstant;
import org.n52.sos.ogc.ows.OwsExceptionReport;
import org.n52.sos.util.DateTimeHelper;

/**
 * TODO JavaDoc
 *
 * @author Christian Autermann
 */
public class TimeInstantReader extends XmlReader<TimeInstant> {

    private TimeInstant time;

    @Override
    protected void begin()
            throws XMLStreamException, OwsExceptionReport {
        this.time = new TimeInstant();
    }

    @Override
    protected void read(QName name)
            throws XMLStreamException, OwsExceptionReport {
        if (name.equals(GmlConstants.QN_TIME_POSITION_32)) {
            time.setValue(DateTimeHelper.parseIsoString2DateTime(chars()));
        } else {
            ignore();
        }
    }

    @Override
    protected TimeInstant finish()
            throws OwsExceptionReport {
        return this.time;
    }

}
