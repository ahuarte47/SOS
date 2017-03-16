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

import java.util.ArrayList;
import java.util.List;

/**
 * Set of Measures of one Observable Attribute of an Object in a generic data Model.
 * 
 * @author Alvaro Huarte <ahuarte@tracasa.es>
 */
public class MeasureSet 
{
    /**
     * Owner Object of the MeasureSet.
     */
    public ObservableObject ownerObject;

    /**
     * Attribute descriptor of the MeasureSet.
     */
    public ObservableAttribute attribute;
    
    /**
     * List of Measures of the MeasureSet.
     */
    public List<Measure> measures = new ArrayList<Measure>();
    
    /**
     * Filter the List of measures with the specified Filter function.
     */
    public static List<Measure> filterMeasuresWithFunction(List<Measure> measures, String measureFilterFunction) 
    {
        List<Measure> outputList = measures;
        
        if (measureFilterFunction != null && measureFilterFunction.length() > 0 && measures.size() > 0)
        {
            outputList = new ArrayList<Measure>();
            int icount = measures.size();
            
            if (measureFilterFunction.equals("sos_first(obs)"))
            {
                outputList.add(measures.get(0));
            }
            else
            if (measureFilterFunction.equals("sos_last(obs)"))
            {
                outputList.add(measures.get(icount-1));
            }
            else
            if (measureFilterFunction.equals("sos_minimum(obs)"))
            {
                if (!(measures.get(0).value instanceof Number))
                    return measures;
                
                double minimumValue = Double.MAX_VALUE;
                int minimumIndex = -1;
                Object valob = null;
                double value = 0;
                
                for (int i = 0; i < icount; i++)
                {
                    if ((valob = measures.get(i).value) != null && (value = ((Number)valob).doubleValue()) < minimumValue)
                    {
                        minimumValue = value;
                        minimumIndex = i;
                    }
                }
                if (minimumIndex != -1)
                {
                    outputList.add(measures.get(minimumIndex));
                }
            }
            else
            if (measureFilterFunction.equals("sos_maximum(obs)"))
            {
                if (!(measures.get(0).value instanceof Number))
                    return measures;
                
                double maximumValue = Double.MIN_VALUE;
                int maximumIndex = -1;
                Object valob = null;
                double value = 0;
                
                for (int i = 0; i < icount; i++)
                {
                    if ((valob = measures.get(i).value) != null && (value = ((Number)valob).doubleValue()) > maximumValue)
                    {
                        maximumValue = value;
                        maximumIndex = i;
                    }
                }
                if (maximumIndex != -1)
                {
                    outputList.add(measures.get(maximumIndex));
                }
            }
            else
            if (measureFilterFunction.equals("sos_average(obs)"))
            {
                if (!(measures.get(0).value instanceof Number))
                    return measures;
                
                Object valob = null;
                double value = 0;
                for (int i = 0; i < icount; i++) if ((valob = measures.get(i).value) != null) value += ((Number)valob).doubleValue();
                value /= (double)icount;
                
                long phenomenonTime = measures.get(0).phenomenonTime.getMillis();
                if (icount > 1) phenomenonTime += (measures.get(icount-1).phenomenonTime.getMillis() - phenomenonTime) / 2;
                
                Measure measure = new Measure();
                measure.phenomenonTime = new org.joda.time.DateTime(phenomenonTime);
                measure.value = value;
                
                outputList.add(measure);
            }
            else
            if (measureFilterFunction.startsWith("sos_clamp(obs,"))
            {
                if (!(measures.get(0).value instanceof Number))
                    return measures;
                
                measureFilterFunction = measureFilterFunction.substring(14, measureFilterFunction.length()-2);
                String[] clampRange = measureFilterFunction.split(",");
                
                double minimumValue = Double.parseDouble(clampRange[0]);
                double maximumValue = Double.parseDouble(clampRange[1]);
                Object valob = null;
                double value = 0;
                
                for (int i = 0; i < icount; i++)
                {
                    if ((valob = measures.get(i).value) != null && (value = ((Number)valob).doubleValue()) >= minimumValue && value <= maximumValue)
                    {
                        outputList.add(measures.get(i));
                    }
                }
            }
            else
            if (measureFilterFunction.startsWith("sos_averageclamp(obs,"))
            {
                outputList = filterMeasuresWithFunction(measures, measureFilterFunction.replace("sos_averageclamp", "sos_clamp"));
                outputList = filterMeasuresWithFunction(outputList, "sos_average(obs)");
            }
            else
            if (measureFilterFunction.startsWith("sos_avclamp(obs,"))
            {
                outputList = filterMeasuresWithFunction(measures, measureFilterFunction.replace("sos_avclamp", "sos_clamp"));
                outputList = filterMeasuresWithFunction(outputList, "sos_average(obs)");
            }
        }
        return outputList;
    }
    
    @Override
    public String toString()
    {
        return String.format("Attribute=%s Measures=[%s]", attribute.name, measures.toString());
    }
}
