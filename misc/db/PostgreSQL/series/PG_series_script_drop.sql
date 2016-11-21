--
-- Copyright (C) 2012-2016 52°North Initiative for Geospatial Open Source
-- Software GmbH
--
-- This program is free software; you can redistribute it and/or modify it
-- under the terms of the GNU General Public License version 2 as published
-- by the Free Software Foundation.
--
-- If the program is linked with libraries which are licensed under one of
-- the following licenses, the combination of the program with the linked
-- library is not considered a "derivative work" of the program:
--
--     - Apache License, version 2.0
--     - Apache Software License, version 1.0
--     - GNU Lesser General Public License, version 3
--     - Mozilla Public License, versions 1.0, 1.1 and 2.0
--     - Common Development and Distribution License (CDDL), version 1.0
--
-- Therefore the distribution of the program linked with libraries licensed
-- under the aforementioned licenses, is permitted by the copyright holders
-- if the distribution is compliant with both the GNU General Public
-- License version 2 and the aforementioned licenses.
--
-- This program is distributed in the hope that it will be useful, but
-- WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
-- Public License for more details.
--

alter table public."procedure" drop constraint procProcDescFormatFk;
alter table public."procedure" drop constraint procCodespaceIdentifierFk;
alter table public."procedure" drop constraint procCodespaceNameFk;
alter table public."procedure" drop constraint typeOfFk;
alter table public.blobvalue drop constraint observationBlobValueFk;
alter table public.booleanfeatparamvalue drop constraint featParamBooleanValueFk;
alter table public.booleanparametervalue drop constraint parameterBooleanValueFk;
alter table public.booleanseriesparamvalue drop constraint seriesParamBooleanValueFk;
alter table public.booleanvalue drop constraint observationBooleanValueFk;
alter table public.categoryfeatparamvalue drop constraint featParamCategoryValueFk;
alter table public.categoryfeatparamvalue drop constraint catfeatparamvalueUnitFk;
alter table public.categoryparametervalue drop constraint parameterCategoryValueFk;
alter table public.categoryparametervalue drop constraint catParamValueUnitFk;
alter table public.categoryseriesparamvalue drop constraint seriesParamCategoryValueFk;
alter table public.categoryseriesparamvalue drop constraint seriesCatParamValueUnitFk;
alter table public.categoryvalue drop constraint observationCategoryValueFk;
alter table public.complexvalue drop constraint observationComplexValueFk;
alter table public.compositeobservation drop constraint observationChildFk;
alter table public.compositeobservation drop constraint observationParentFK;
alter table public.compositephenomenon drop constraint observablePropertyChildFk;
alter table public.compositephenomenon drop constraint observablePropertyParentFk;
alter table public.countfeatparamvalue drop constraint featParamCountValueFk;
alter table public.countparametervalue drop constraint parameterCountValueFk;
alter table public.countseriesparamvalue drop constraint seriesParamCountValueFk;
alter table public.countvalue drop constraint observationCountValueFk;
alter table public.featureofinterest drop constraint featureFeatureTypeFk;
alter table public.featureofinterest drop constraint featureCodespaceIdentifierFk;
alter table public.featureofinterest drop constraint featureCodespaceNameFk;
alter table public.featureparameter drop constraint FK_4ps6yv41rwnbu3q0let2v7772;
alter table public.featurerelation drop constraint featureOfInterestChildFk;
alter table public.featurerelation drop constraint featureOfInterestParentFk;
alter table public.geometryvalue drop constraint observationGeometryValueFk;
alter table public.i18nfeatureofinterest drop constraint i18nFeatureFeatureFk;
alter table public.i18nobservableproperty drop constraint i18nObsPropObsPropFk;
alter table public.i18noffering drop constraint i18nOfferingOfferingFk;
alter table public.i18nprocedure drop constraint i18nProcedureProcedureFk;
alter table public.numericfeatparamvalue drop constraint featParamNumericValueFk;
alter table public.numericfeatparamvalue drop constraint quanfeatparamvalueUnitFk;
alter table public.numericparametervalue drop constraint parameterNumericValueFk;
alter table public.numericparametervalue drop constraint quanParamValueUnitFk;
alter table public.numericseriesparamvalue drop constraint seriesParamNumericValueFk;
alter table public.numericseriesparamvalue drop constraint seriesQuanParamValueUnitFk;
alter table public.numericvalue drop constraint observationNumericValueFk;
alter table public.observableproperty drop constraint obsPropCodespaceIdentifierFk;
alter table public.observableproperty drop constraint obsPropCodespaceNameFk;
alter table public.observation drop constraint observationSeriesFk;
alter table public.observation drop constraint obsCodespaceIdentifierFk;
alter table public.observation drop constraint obsCodespaceNameFk;
alter table public.observation drop constraint observationUnitFk;
alter table public.observationconstellation drop constraint obsConstObsPropFk;
alter table public.observationconstellation drop constraint obsnConstProcedureFk;
alter table public.observationconstellation drop constraint obsConstObservationIypeFk;
alter table public.observationconstellation drop constraint obsConstOfferingFk;
alter table public.observationhasoffering drop constraint observationOfferingFk;
alter table public.observationhasoffering drop constraint FK_s19siow5aetbwb8ppww4kb96n;
alter table public.offering drop constraint offCodespaceIdentifierFk;
alter table public.offering drop constraint offCodespaceNameFk;
alter table public.offeringallowedfeaturetype drop constraint offeringFeatureTypeFk;
alter table public.offeringallowedfeaturetype drop constraint FK_cu8nfsf9q5vsn070o2d3u6chg;
alter table public.offeringallowedobservationtype drop constraint offeringObservationTypeFk;
alter table public.offeringallowedobservationtype drop constraint FK_jehw0637hllvta9ao1tqdhrtm;
alter table public.offeringhasrelatedfeature drop constraint relatedFeatureOfferingFk;
alter table public.offeringhasrelatedfeature drop constraint offeringRelatedFeatureFk;
alter table public.offeringrelation drop constraint offeringChildFk;
alter table public.offeringrelation drop constraint offeringParenfFk;
alter table public.parameter drop constraint FK_3v5iovcndi9w0hgh827hcvivw;
alter table public.profileobservation drop constraint profileObsChildFk;
alter table public.profileobservation drop constraint profileObsParentFK;
alter table public.profilevalue drop constraint observationProfileValueFk;
alter table public.profilevalue drop constraint profileUnitFk;
alter table public.relatedfeature drop constraint relatedFeatureFeatureFk;
alter table public.relatedfeaturehasrole drop constraint relatedFeatRelatedFeatRoleFk;
alter table public.relatedfeaturehasrole drop constraint FK_5fd921q6mnbkc57mgm5g4uyyn;
alter table public.relatedobservation drop constraint FK_g0f0mpuxn3co65uwud4pwxh4q;
alter table public.relatedobservation drop constraint FK_m4nuof4x6w253biuu1r6ttnqc;
alter table public.relatedseries drop constraint relatedSeriesFk;
alter table public.resulttemplate drop constraint resultTemplateOfferingIdx;
alter table public.resulttemplate drop constraint resultTemplateObsPropFk;
alter table public.resulttemplate drop constraint resultTemplateProcedureFk;
alter table public.resulttemplate drop constraint resultTemplateFeatureIdx;
alter table public.sensorsystem drop constraint procedureChildFk;
alter table public.sensorsystem drop constraint procedureParenfFk;
alter table public.series drop constraint seriesFeatureFk;
alter table public.series drop constraint seriesObPropFk;
alter table public.series drop constraint seriesProcedureFk;
alter table public.series drop constraint seriesOfferingFk;
alter table public.series drop constraint seriesUnitFk;
alter table public.series drop constraint seriesCodespaceIdentifierFk;
alter table public.series drop constraint seriesCodespaceNameFk;
alter table public.swedataarrayvalue drop constraint observationSweDataArrayValueFk;
alter table public.textfeatparamvalue drop constraint featParamTextValueFk;
alter table public.textparametervalue drop constraint parameterTextValueFk;
alter table public.textseriesparamvalue drop constraint seriesParamTextValueFk;
alter table public.textvalue drop constraint observationTextValueFk;
alter table public.validproceduretime drop constraint validProcedureTimeProcedureFk;
alter table public.validproceduretime drop constraint validProcProcDescFormatFk;
alter table public.xmlfeatparamvalue drop constraint featParamXmlValueFk;
alter table public.xmlparametervalue drop constraint parameterXmlValueFk;
alter table public.xmlseriesparamvalue drop constraint seriesParamXmlValueFk;
drop table if exists public."procedure" cascade;
drop table if exists public.blobvalue cascade;
drop table if exists public.booleanfeatparamvalue cascade;
drop table if exists public.booleanparametervalue cascade;
drop table if exists public.booleanseriesparamvalue cascade;
drop table if exists public.booleanvalue cascade;
drop table if exists public.categoryfeatparamvalue cascade;
drop table if exists public.categoryparametervalue cascade;
drop table if exists public.categoryseriesparamvalue cascade;
drop table if exists public.categoryvalue cascade;
drop table if exists public.codespace cascade;
drop table if exists public.complexvalue cascade;
drop table if exists public.compositeobservation cascade;
drop table if exists public.compositephenomenon cascade;
drop table if exists public.countfeatparamvalue cascade;
drop table if exists public.countparametervalue cascade;
drop table if exists public.countseriesparamvalue cascade;
drop table if exists public.countvalue cascade;
drop table if exists public.featureofinterest cascade;
drop table if exists public.featureofinteresttype cascade;
drop table if exists public.featureparameter cascade;
drop table if exists public.featurerelation cascade;
drop table if exists public.geometryvalue cascade;
drop table if exists public.i18nfeatureofinterest cascade;
drop table if exists public.i18nobservableproperty cascade;
drop table if exists public.i18noffering cascade;
drop table if exists public.i18nprocedure cascade;
drop table if exists public.numericfeatparamvalue cascade;
drop table if exists public.numericparametervalue cascade;
drop table if exists public.numericseriesparamvalue cascade;
drop table if exists public.numericvalue cascade;
drop table if exists public.observableproperty cascade;
drop table if exists public.observation cascade;
drop table if exists public.observationconstellation cascade;
drop table if exists public.observationhasoffering cascade;
drop table if exists public.observationtype cascade;
drop table if exists public.offering cascade;
drop table if exists public.offeringallowedfeaturetype cascade;
drop table if exists public.offeringallowedobservationtype cascade;
drop table if exists public.offeringhasrelatedfeature cascade;
drop table if exists public.offeringrelation cascade;
drop table if exists public.parameter cascade;
drop table if exists public.proceduredescriptionformat cascade;
drop table if exists public.profileobservation cascade;
drop table if exists public.profilevalue cascade;
drop table if exists public.relatedfeature cascade;
drop table if exists public.relatedfeaturehasrole cascade;
drop table if exists public.relatedfeaturerole cascade;
drop table if exists public.relatedobservation cascade;
drop table if exists public.relatedseries cascade;
drop table if exists public.resulttemplate cascade;
drop table if exists public.sensorsystem cascade;
drop table if exists public.series cascade;
drop table if exists public.seriesmetadata cascade;
drop table if exists public.seriesparameter cascade;
drop table if exists public.swedataarrayvalue cascade;
drop table if exists public.textfeatparamvalue cascade;
drop table if exists public.textparametervalue cascade;
drop table if exists public.textseriesparamvalue cascade;
drop table if exists public.textvalue cascade;
drop table if exists public.unit cascade;
drop table if exists public.validproceduretime cascade;
drop table if exists public.xmlfeatparamvalue cascade;
drop table if exists public.xmlparametervalue cascade;
drop table if exists public.xmlseriesparamvalue cascade;
drop sequence public.codespaceId_seq;
drop sequence public.featureOfInterestId_seq;
drop sequence public.featureOfInterestTypeId_seq;
drop sequence public.i18nObsPropId_seq;
drop sequence public.i18nOfferingId_seq;
drop sequence public.i18nProcedureId_seq;
drop sequence public.i18nfeatureOfInterestId_seq;
drop sequence public.metadataId_seq;
drop sequence public.observablePropertyId_seq;
drop sequence public.observationConstellationId_seq;
drop sequence public.observationId_seq;
drop sequence public.observationTypeId_seq;
drop sequence public.offeringId_seq;
drop sequence public.parameterId_seq;
drop sequence public.procDescFormatId_seq;
drop sequence public.procedureId_seq;
drop sequence public.relatedFeatureId_seq;
drop sequence public.relatedFeatureRoleId_seq;
drop sequence public.relatedObservationId_seq;
drop sequence public.resultTemplateId_seq;
drop sequence public.seriesId_seq;
drop sequence public.seriesRelationId_seq;
drop sequence public.unitId_seq;
drop sequence public.validProcedureTimeId_seq;
