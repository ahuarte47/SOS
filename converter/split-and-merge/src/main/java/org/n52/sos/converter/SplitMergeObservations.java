/**
 * Copyright (C) 2012-2015 52°North Initiative for Geospatial Open Source
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
package org.n52.sos.converter;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.n52.iceland.coding.CodingRepository;
import org.n52.iceland.coding.encode.OperationResponseEncoderKey;
import org.n52.iceland.coding.encode.XmlEncoderKey;
import org.n52.iceland.convert.RequestResponseModifier;
import org.n52.iceland.convert.RequestResponseModifierFacilitator;
import org.n52.iceland.convert.RequestResponseModifierKey;
import org.n52.iceland.exception.ows.NoApplicableCodeException;
import org.n52.iceland.exception.ows.OwsExceptionReport;
import org.n52.iceland.ogc.gml.CodeWithAuthority;
import org.n52.iceland.ogc.gml.time.Time;
import org.n52.iceland.ogc.gml.time.TimeInstant;
import org.n52.iceland.ogc.om.OmConstants;
import org.n52.iceland.ogc.sos.Sos1Constants;
import org.n52.iceland.ogc.sos.Sos2Constants;
import org.n52.iceland.ogc.sos.SosConstants;
import org.n52.iceland.request.AbstractServiceRequest;
import org.n52.iceland.response.AbstractServiceResponse;
import org.n52.iceland.util.DateTimeHelper;
import org.n52.iceland.util.http.HTTPStatus;
import org.n52.iceland.util.http.MediaType;
import org.n52.sos.coding.encode.ObservationEncoder;
import org.n52.sos.ogc.om.AbstractPhenomenon;
import org.n52.sos.ogc.om.ObservationValue;
import org.n52.sos.ogc.om.OmObservation;
import org.n52.sos.ogc.om.OmObservationConstellation;
import org.n52.sos.ogc.om.SingleObservationValue;
import org.n52.sos.ogc.om.values.BooleanValue;
import org.n52.sos.ogc.om.values.CategoryValue;
import org.n52.sos.ogc.om.values.CountValue;
import org.n52.sos.ogc.om.values.QuantityValue;
import org.n52.sos.ogc.om.values.SweDataArrayValue;
import org.n52.sos.ogc.om.values.TextValue;
import org.n52.sos.ogc.swe.SweDataRecord;
import org.n52.sos.ogc.swe.SweField;
import org.n52.sos.ogc.swe.simpleType.SweAbstractUomType;
import org.n52.sos.request.GetObservationRequest;
import org.n52.sos.request.InsertObservationRequest;
import org.n52.sos.response.GetObservationResponse;
import org.n52.sos.response.InsertObservationResponse;
import org.n52.sos.service.profile.Profile;
import org.n52.sos.service.profile.ProfileHandler;
import org.n52.sos.util.CodingHelper;
import org.n52.sos.util.OMHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class SplitMergeObservations implements RequestResponseModifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(SplitMergeObservations.class);

    private static final Set<RequestResponseModifierKey> REQUEST_RESPONSE_MODIFIER_KEY_TYPES = getKeyTypes();

    /**
     * Get the keys
     *
     * @return Set of keys
     */
    private static Set<RequestResponseModifierKey> getKeyTypes() {
        Set<String> services = Sets.newHashSet(SosConstants.SOS);
        Set<String> versions = Sets.newHashSet(Sos1Constants.SERVICEVERSION, Sos2Constants.SERVICEVERSION);
        Map<AbstractServiceRequest<?>, AbstractServiceResponse> requestResponseMap = Maps.newHashMap();

        requestResponseMap.put(new GetObservationRequest(), new GetObservationResponse());
        requestResponseMap.put(new InsertObservationRequest(), new InsertObservationResponse());
        Set<RequestResponseModifierKey> keys = Sets.newHashSet();
        for (String service : services) {
            for (String version : versions) {
                for (AbstractServiceRequest<?> request : requestResponseMap.keySet()) {
                    keys.add(new RequestResponseModifierKey(service, version, request));
                    keys.add(new RequestResponseModifierKey(service, version, request, requestResponseMap
                            .get(request)));
                }
            }
        }
        return keys;
    }

    @Override
    public Set<RequestResponseModifierKey> getKeys() {
        return Collections.unmodifiableSet(REQUEST_RESPONSE_MODIFIER_KEY_TYPES);
    }

    @Override
    public AbstractServiceRequest<?> modifyRequest(AbstractServiceRequest<?> request) throws OwsExceptionReport {
        if (request instanceof InsertObservationRequest) {
            splitObservations((InsertObservationRequest) request);
        }
        return request;
    }

    private void splitObservations(InsertObservationRequest request) throws OwsExceptionReport {
        if (request.isSetExtensionSplitDataArrayIntoObservations()) {
            splitDataArrayIntoObservations(request);
        }
    }

    private void splitDataArrayIntoObservations(final InsertObservationRequest request) throws OwsExceptionReport {
        LOGGER.debug("Start splitting observations. Count: {}", request.getObservations().size());
        final Collection<OmObservation> finalObservationCollection = Sets.newHashSet();
        for (final OmObservation observation : request.getObservations()) {
            if (isSweArrayObservation(observation)) {
                LOGGER.debug("Found SweArrayObservation to split.");
                final SweDataArrayValue sweDataArrayValue = (SweDataArrayValue) observation.getValue().getValue();
                final OmObservationConstellation observationConstellation = observation.getObservationConstellation();
                int counter = 0;
                final int resultTimeIndex =
                        getResultTimeIndex((SweDataRecord) sweDataArrayValue.getValue().getElementType());
                final int phenomenonTimeIndex =
                        getPhenomenonTimeIndex((SweDataRecord) sweDataArrayValue.getValue().getElementType());
                final int resultValueIndex =
                        getResultValueIndex((SweDataRecord) sweDataArrayValue.getValue().getElementType(),
                                observationConstellation.getObservableProperty());
                observationConstellation.setObservationType(getObservationTypeFromElementType(
                        (SweDataRecord) sweDataArrayValue.getValue().getElementType(),
                        observationConstellation.getObservableProperty()));
                // split into single observation
                for (final List<String> block : sweDataArrayValue.getValue().getValues()) {
                    LOGGER.debug("Processing block {}/{}", ++counter, sweDataArrayValue.getValue().getValues().size());
                    final OmObservation newObservation = new OmObservation();
                    newObservation.setObservationConstellation(observationConstellation);
                    // identifier
                    if (observation.isSetIdentifier()) {
                        final CodeWithAuthority identifier = observation.getIdentifierCodeWithAuthority();
                        identifier.setValue(identifier.getValue() + counter);
                        newObservation.setIdentifier(identifier);
                    }
                    // phen time
                    Time phenomenonTime;
                    if (phenomenonTimeIndex == -1) {
                        phenomenonTime = observation.getPhenomenonTime();
                    } else {
                        phenomenonTime = DateTimeHelper.parseIsoString2DateTime2Time(block.get(phenomenonTimeIndex));
                    }
                    // result time
                    if (resultTimeIndex == -1) {
                        // use phenomenon time if outer observation's resultTime
                        // value
                        // or nilReason is "template"
                        if ((!observation.isSetResultTime() || observation.isTemplateResultTime())
                                && phenomenonTime instanceof TimeInstant) {
                            newObservation.setResultTime((TimeInstant) phenomenonTime);
                        } else {
                            newObservation.setResultTime(observation.getResultTime());
                        }
                    } else {
                        newObservation.setResultTime(
                                new TimeInstant(DateTimeHelper.parseIsoString2DateTime(block.get(resultTimeIndex))));
                    }
                    if (observation.isSetParameter()) {
                        newObservation.setParameter(observation.getParameter());
                    }
                    // value
                    final ObservationValue<?> value = createObservationResultValue(
                            observationConstellation.getObservationType(), block.get(resultValueIndex), phenomenonTime,
                            ((SweDataRecord) sweDataArrayValue.getValue().getElementType()).getFields()
                                    .get(resultValueIndex));
                    newObservation.setValue(value);
                    finalObservationCollection.add(newObservation);
                }
            } else {
                LOGGER.debug("Found non splittable observation");
                finalObservationCollection.add(observation);
            }
        }
        request.setObservation(Lists.newArrayList(finalObservationCollection));
    }

    private ObservationValue<?> createObservationResultValue(final String observationType, final String valueString,
            final Time phenomenonTime, final SweField resultDefinitionField) throws OwsExceptionReport {
        ObservationValue<?> value = null;

        if (observationType.equalsIgnoreCase(OmConstants.OBS_TYPE_TRUTH_OBSERVATION)) {
            value = new SingleObservationValue<Boolean>(new BooleanValue(Boolean.parseBoolean(valueString)));
        } else if (observationType.equalsIgnoreCase(OmConstants.OBS_TYPE_COUNT_OBSERVATION)) {
            value = new SingleObservationValue<Integer>(new CountValue(Integer.parseInt(valueString)));
        } else if (observationType.equalsIgnoreCase(OmConstants.OBS_TYPE_MEASUREMENT)) {
            final QuantityValue quantity = new QuantityValue(Double.parseDouble(valueString));
            quantity.setUnit(getUom(resultDefinitionField));
            value = new SingleObservationValue<Double>(quantity);
        } else if (observationType.equalsIgnoreCase(OmConstants.OBS_TYPE_CATEGORY_OBSERVATION)) {
            final CategoryValue cat = new CategoryValue(valueString);
            cat.setUnit(getUom(resultDefinitionField));
            value = new SingleObservationValue<String>(cat);
        } else if (observationType.equalsIgnoreCase(OmConstants.OBS_TYPE_TEXT_OBSERVATION)) {
            value = new SingleObservationValue<String>(new TextValue(valueString));
        }
        // TODO Check for missing types
        if (value != null) {
            value.setPhenomenonTime(phenomenonTime);
        } else {
            throw new NoApplicableCodeException().withMessage("Observation type '{}' not supported.", observationType)
                    .setStatus(HTTPStatus.BAD_REQUEST);
        }
        return value;
    }

    private String getUom(final SweField resultDefinitionField) {
        return ((SweAbstractUomType<?>) resultDefinitionField.getElement()).getUom();
    }

    private int getResultValueIndex(final SweDataRecord elementTypeDataRecord,
            final AbstractPhenomenon observableProperty) {
        return elementTypeDataRecord.getFieldIndexByIdentifier(observableProperty.getIdentifier());
    }

    private int getPhenomenonTimeIndex(final SweDataRecord elementTypeDataRecord) {
        return elementTypeDataRecord.getFieldIndexByIdentifier(OmConstants.PHENOMENON_TIME);
    }

    private int getResultTimeIndex(final SweDataRecord elementTypeDataRecord) {
        return elementTypeDataRecord.getFieldIndexByIdentifier(OmConstants.PHEN_SAMPLING_TIME);
    }

    private String getObservationTypeFromElementType(final SweDataRecord elementTypeDataRecord,
            final AbstractPhenomenon observableProperty) throws OwsExceptionReport {
        for (final SweField sweField : elementTypeDataRecord.getFields()) {
            if (sweField.getElement() != null && sweField.getElement().isSetDefinition()
                    && sweField.getElement().getDefinition().equalsIgnoreCase(observableProperty.getIdentifier())) {
                return OMHelper.getObservationTypeFrom(sweField.getElement());
            }
        }
        throw new NoApplicableCodeException().withMessage(
                "Not able to derive observation type from elementType element '{}' for observable property '{}'.",
                elementTypeDataRecord, observableProperty).setStatus(HTTPStatus.BAD_REQUEST);
    }

    private boolean isSweArrayObservation(final OmObservation observation) {
        return observation.getObservationConstellation().getObservationType()
                .equalsIgnoreCase(OmConstants.OBS_TYPE_SWE_ARRAY_OBSERVATION)
                && observation.getValue().getValue() instanceof SweDataArrayValue
                && ((SweDataArrayValue) observation.getValue().getValue()).isSetValue();
    }

    @Override
    public AbstractServiceResponse modifyResponse(AbstractServiceRequest<?> request, AbstractServiceResponse response) throws OwsExceptionReport{
        if (request instanceof GetObservationRequest && response instanceof GetObservationResponse) {
            return  mergeObservations((GetObservationRequest) request, (GetObservationResponse) response);
        } else  if (response instanceof GetObservationResponse) {
            return mergeObservations((GetObservationResponse) response);
        }
        return response;
    }

    private GetObservationResponse mergeObservations(GetObservationRequest request, GetObservationResponse response) throws OwsExceptionReport {
        boolean checkForMergeObservationsInResponse = checkForMergeObservationsInResponse(request);
        request.setMergeObservationValues(checkForMergeObservationsInResponse);
        boolean checkEncoderForMergeObservations = checkEncoderForMergeObservations(response);
        if (checkForMergeObservationsInResponse || checkEncoderForMergeObservations) {
            if (!response.hasStreamingData()) {
                mergeObservationsWithSameConstellation(response);
            }
            response.setMergeObservations(true);
        }
        return response;
    }

    private void mergeObservationsWithSameConstellation(GetObservationResponse response) {
        // TODO merge all observations with the same observationContellation
        // FIXME Failed to set the observation type to sweArrayObservation for
        // the merged Observations
        // (proc, obsProp, foi)
        if (response.getObservationCollection() != null) {
            final List<OmObservation> mergedObservations = new LinkedList<>();
            int obsIdCounter = 1;
            for (final OmObservation sosObservation : response.getObservationCollection()) {
                if (mergedObservations.isEmpty()) {
                    sosObservation.setObservationID(Integer.toString(obsIdCounter++));
                    mergedObservations.add(sosObservation);
                } else {
                    boolean combined = false;
                    for (final OmObservation combinedSosObs : mergedObservations) {
                        if (combinedSosObs.checkForMerge(sosObservation)) {
                            combinedSosObs.setResultTime(null);
                            combinedSosObs.mergeWithObservation(sosObservation);
                            combined = true;
                            break;
                        }
                    }
                    if (!combined) {
                        mergedObservations.add(sosObservation);
                    }
                }
            }
            response.setObservationCollection(mergedObservations);
        }
    }

    private boolean checkEncoderForMergeObservations(GetObservationResponse response) throws OwsExceptionReport {
        if (response.isSetResponseFormat()) {
            // check for XML encoder
            ObservationEncoder<Object, Object> encoder =
                    (ObservationEncoder<Object, Object>) CodingRepository.getInstance().getEncoder(
                            new XmlEncoderKey(response.getResponseFormat(), new OmObservation().getClass()));
            // check for response contentType
            if (encoder == null && response.isSetContentType()) {
                encoder =
                        (ObservationEncoder<Object, Object>) CodingRepository.getInstance().getEncoder(
                                new OperationResponseEncoderKey(response.getService(), response.getVersion(), response
                                        .getOperationName(), response.getContentType()));
            }
            // check for responseFormat as MediaType
            if (encoder == null && response.isSetResponseFormat()) {
                try {
                    encoder =
                            (ObservationEncoder<Object, Object>) CodingRepository.getInstance().getEncoder(
                                    new OperationResponseEncoderKey(response.getService(), response.getVersion(), response
                                            .getOperationName(), MediaType.parse(response.getResponseFormat())));
                } catch (IllegalArgumentException iae) {
                    LOGGER.debug("ResponseFormat isNot a XML response format");
                }
            }

            if (encoder != null) {
                return encoder.shouldObservationsWithSameXBeMerged();
            }

        }
        return false;
    }

    private AbstractServiceResponse mergeObservations(GetObservationResponse response) throws OwsExceptionReport {
        boolean checkEncoderForMergeObservations = checkEncoderForMergeObservations(response);
        if (checkEncoderForMergeObservations && !response.hasStreamingData()) {
            if (!response.hasStreamingData()) {
                mergeObservationsWithSameConstellation(response);
            }
            response.setMergeObservations(checkEncoderForMergeObservations);
        }
        return response;
    }

    private boolean checkForMergeObservationsInResponse(GetObservationRequest sosRequest) {
        if (getActiveProfile().isMergeValues() || isSetExtensionMergeObservationsToSweDataArray(sosRequest)) {
            return true;
        }
        return false;
    }

    private boolean isSetExtensionMergeObservationsToSweDataArray(final GetObservationRequest sosRequest) {
        return sosRequest.isSetExtensions() && sosRequest.getExtensions()
                .isBooleanExtensionSet(Sos2Constants.Extensions.MergeObservationsIntoDataArray.name());
    }

    protected Profile getActiveProfile() {
        return ProfileHandler.getInstance().getActiveProfile();
    }

    @Override
    public RequestResponseModifierFacilitator getFacilitator() {
        // TODO Auto-generated method stub
        return new RequestResponseModifierFacilitator().setMerger(true).setSplitter(true);
    }

}
