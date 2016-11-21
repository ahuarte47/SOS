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

import java.util.Collections;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.n52.sos.cache.ContentCache;
import org.n52.sos.event.SosEvent;
import org.n52.sos.event.SosEventBus;
import org.n52.sos.event.SosEventListener;
import org.n52.sos.event.events.ConfiguratorInitializedEvent;
import org.n52.sos.extensions.VirtualCapabilitiesExtension;
import org.n52.sos.extensions.VirtualCapabilitiesExtensionRepository;
import org.n52.sos.extensions.DynamicWritableCache;
import org.n52.sos.extensions.ObservableModel;
import org.n52.sos.extensions.model.ModelManager;
import org.n52.sos.extensions.util.FileUtils;
import org.n52.sos.request.AbstractServiceRequest;
import org.n52.sos.response.AbstractServiceResponse;
import org.n52.sos.service.Configurator;

/**
 * Main class manager to integrate Wonderware frameworks as 52°North SOS Sensors/Observation objects.
 *  
 * @author Alvaro Huarte <ahuarte@tracasa.es>
 */
public class WonderwareModelingManager implements VirtualCapabilitiesExtension
{
    private static final Logger LOG = LoggerFactory.getLogger(WonderwareModelingManager.class);
    
    /** Default settings file. */
    public static String DEFAULT_SETTINGS_FILE = "dynamic-models/wonderware-settings.xml";
    
    /** Creates a new WonderwareModelingManager using the specified settings file. */
    public WonderwareModelingManager(String settingsFileName)
    {        
        VirtualCapabilitiesExtensionRepository virtualCapabilitiesExtensionRepository = VirtualCapabilitiesExtensionRepository.getInstance();
        virtualCapabilitiesExtensionRepository.registerVirtualCapabilitiesExtension(this);
        
        LOG.info("WonderwareModelingManager registered as VirtualCapabilitiesExtension!");
        
        try
        {
            java.net.URI url = FileUtils.resolveAbsoluteURI(settingsFileName, WonderwareModelingManager.class.getClassLoader());
            if (url!=null) settingsFileName = url.getPath();
            
            LOG.info("Creating WonderwareModelingManager... SettingsFile='"+settingsFileName+"'.");
            
            modelManager = new ModelManager();
            modelManager.loadSettings(settingsFileName);
            modelManager.asynchronouslyPrepareModels();
            
            LOG.info("WonderwareModelingManager successly created!");
        }
        catch (Exception e)
        {
            LOG.error(e.getMessage());
        }
    }
    
    /**
     * LazyHolder of the ModelingManager singleton instance.
     */
    private static class LazyHolder implements SosEventListener {
        private static final Set<Class<? extends SosEvent>> TYPES = Collections.<Class<? extends SosEvent>> singleton(ConfiguratorInitializedEvent.class);
        private WonderwareModelingManager INSTANCE = null;
        
        private LazyHolder() {
            SosEventBus.getInstance().register(this);
        }
        public Set<Class<? extends SosEvent>> getTypes() {
            return Collections.unmodifiableSet(TYPES);
        }
        public void handle(SosEvent event) {
            if (event instanceof ConfiguratorInitializedEvent) {
                try {
                    Configurator configurator = Configurator.getInstance();
                    
                    // Gets the absolute settings path using the current web application WEB-INF path.
                    String webinfPath = configurator.getWebInfPath();
                    LOG.info("Current WEB-INF path of the application: " + webinfPath);
                    
                    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                    String settingsFileName = DEFAULT_SETTINGS_FILE;
                    
                    java.net.URI url = FileUtils.resolveAbsoluteURI(webinfPath + settingsFileName, classLoader);
                    if (url!=null) {
                        settingsFileName = url.getPath();
                    }
                    else {
                        url = FileUtils.resolveAbsoluteURI(settingsFileName, classLoader);
                        if (url!=null) settingsFileName = url.getPath();
                    }
                    
                    // Create and configure the singleton instance.
                    INSTANCE = new WonderwareModelingManager(settingsFileName);
                    INSTANCE.getModelManager();
                }
                catch (Exception e) {
                    WonderwareModelingManager.LOG.error("Error processing Event", e);
                }
            }
        }
    }
    /**
     * Register in the application the LazyHolder of a WonderwareModelingManager singleton instance.
     */
    public static boolean registerSingletonInstance() {
        if (lazyHolder==null) {
            WonderwareModelingManager.LOG.info("Registering WonderwareModelingManager Singleton instance!");
            lazyHolder = new LazyHolder();
            return true;
        }
        return false;
    }
    /**
     * Returns the WonderwareModelingManager singleton instance of the application.
     */
    public static WonderwareModelingManager getInstance() {
        if (lazyHolder==null || lazyHolder.INSTANCE==null) {
            registerSingletonInstance();
            lazyHolder.handle(new ConfiguratorInitializedEvent());
        }
        return lazyHolder.INSTANCE;
    }
    private static LazyHolder lazyHolder;
    
    /**
     * Returns the related Wonderware workspace managed.
     */
    public ModelManager getModelManager()
    {
        return modelManager;
    }
    private ModelManager modelManager;    

    /**
     * Returns a new ContentCache adding the virtual capabilities managed by this extension and required for the specified request.
     */
    public ContentCache injectVirtualCapabilities(ContentCache contentCache, AbstractServiceRequest<?> request) throws org.n52.sos.exception.CodedException
    {
      //LOG.info("WonderwareModelingManager::injectDymanicCapabilities()");
        
        if (contentCache instanceof DynamicWritableCache)
        {
            DynamicWritableCache dynamicCache = (DynamicWritableCache)contentCache;
            for (ObservableModel model : modelManager.getPreparedModels()) dynamicCache.addContentOfDynamicModel(model, request);
            return (ContentCache)dynamicCache;
        }
        else
        {
            DynamicWritableCache dynamicCache = new DynamicWritableCache();
            dynamicCache.addContentOfCache(contentCache);
            for (ObservableModel model : modelManager.getPreparedModels()) dynamicCache.addContentOfDynamicModel(model, request);
            return (ContentCache)dynamicCache;
        }
    }
    
    /**
     * Injects the virtual objects managed by this extension and required for the specified request.
     */
    public boolean injectVirtualResponse(AbstractServiceResponse response, ContentCache contentCache, AbstractServiceRequest<?> request) throws org.n52.sos.exception.CodedException
    {
      //LOG.info("WonderwareModelingManager::injectVirtualResponse()");
        boolean dataInjected = false;
        
        if (contentCache instanceof DynamicWritableCache)
        {
            DynamicWritableCache dynamicCache = (DynamicWritableCache)contentCache;
            for (ObservableModel model : modelManager.getPreparedModels()) dataInjected |= dynamicCache.addResponseDataOfDynamicModel(response, model, request);
            return dataInjected;
        }
        else
        {
            DynamicWritableCache dynamicCache = new DynamicWritableCache();
            dynamicCache.addContentOfCache(contentCache);
            for (ObservableModel model : modelManager.getPreparedModels()) dynamicCache.addContentOfDynamicModel(model, request);
            for (ObservableModel model : modelManager.getPreparedModels()) dataInjected |= dynamicCache.addResponseDataOfDynamicModel(response, model, request);
            return dataInjected;
        }
    }
}
