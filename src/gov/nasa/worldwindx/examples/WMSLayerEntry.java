/*
 * Copyright 2025-2026 seaglassfoundry.com. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Part of the WorldWind Reforged project — seaglassfoundry.com
 * WMSLayerEntry.java: data model for a single WMS named layer in the catalog,
 * encapsulating capabilities metadata and lazy layer/elevation-model creation.
 */
package gov.nasa.worldwindx.examples;

import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import gov.nasa.worldwind.Factory;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.avlist.AVListImpl;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.globes.ElevationModel;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.ogc.wms.WMSCapabilities;
import gov.nasa.worldwind.ogc.wms.WMSLayerCapabilities;
import gov.nasa.worldwind.ogc.wms.WMSLayerDimension;
import gov.nasa.worldwind.ogc.wms.WMSLayerStyle;
import gov.nasa.worldwind.util.WWUtil;

/**
 * Data model for a single WMS named layer. Wraps the parsed capabilities metadata
 * and provides lazy creation of the corresponding WorldWind {@link Layer} or
 * {@link ElevationModel}.
 *
 * <p>One entry per named layer — styles are selected via {@link #setSelectedStyle}
 * rather than creating separate entries per style.</p>
 *
 * seaglassfoundry.com
 */
public class WMSLayerEntry
{
    private static final Logger logger = Logger.getLogger(WMSLayerEntry.class.getName());

    private final WMSCapabilities caps;
    private final WMSLayerCapabilities layerCaps;
    private WMSLayerStyle selectedStyle;
    private Object component; // Layer or ElevationModel, lazily created
    private boolean addedToGlobe;

    public WMSLayerEntry(WMSCapabilities caps, WMSLayerCapabilities layerCaps)
    {
        this.caps = caps;
        this.layerCaps = layerCaps;
    }

    // ── Metadata accessors ──────────────────────────────────────────────────

    public String getTitle()
    {
        String title = layerCaps.getTitle();
        return title != null ? title : layerCaps.getName();
    }

    public String getName()
    {
        return layerCaps.getName();
    }

    public String getAbstract()
    {
        return layerCaps.getLayerAbstract();
    }

    public Sector getGeographicBoundingBox()
    {
        return layerCaps.getGeographicBoundingBox();
    }

    public Set<WMSLayerStyle> getStyles()
    {
        Set<WMSLayerStyle> styles = layerCaps.getStyles();
        return styles != null ? styles : Collections.emptySet();
    }

    public Set<WMSLayerDimension> getDimensions()
    {
        return layerCaps.getDimensions();
    }

    public Set<String> getCRS()
    {
        Set<String> crs = layerCaps.getCRS();
        return crs != null ? crs : Collections.emptySet();
    }

    public WMSCapabilities getCapabilities()
    {
        return caps;
    }

    public WMSLayerCapabilities getLayerCapabilities()
    {
        return layerCaps;
    }

    /**
     * Returns true if any dimension has {@code current="true"}, indicating live/streaming data.
     */
    public boolean isLiveData()
    {
        for (WMSLayerDimension dim : getDimensions())
        {
            if (Boolean.TRUE.equals(dim.isCurrent()))
                return true;
        }
        return false;
    }

    public boolean hasTimeDimension()
    {
        for (WMSLayerDimension dim : getDimensions())
        {
            if ("time".equalsIgnoreCase(dim.getName()))
                return true;
        }
        return false;
    }

    // ── Style selection ─────────────────────────────────────────────────────

    public WMSLayerStyle getSelectedStyle()
    {
        return selectedStyle;
    }

    public void setSelectedStyle(WMSLayerStyle style)
    {
        this.selectedStyle = style;
        // Invalidate cached component if style changes while not on globe
        if (!addedToGlobe)
            this.component = null;
    }

    // ── Globe state ─────────────────────────────────────────────────────────

    public boolean isAddedToGlobe()
    {
        return addedToGlobe;
    }

    public void setAddedToGlobe(boolean addedToGlobe)
    {
        this.addedToGlobe = addedToGlobe;
    }

    // ── Component creation ──────────────────────────────────────────────────

    /**
     * Returns the lazily-created WorldWind component (Layer or ElevationModel).
     * Creates it on first call. Returns null only if creation fails, with the
     * error logged rather than silently swallowed.
     */
    public Object getOrCreateComponent()
    {
        if (this.component == null)
            this.component = createComponent();
        return this.component;
    }

    public Object getComponent()
    {
        return this.component;
    }

    public boolean isElevationModel()
    {
        Set<String> formats = caps.getImageFormats();
        if (formats == null)
            return false;
        for (String s : formats)
        {
            if (s.contains("application/bil"))
                return true;
        }
        return false;
    }

    private Object createComponent()
    {
        AVList params = new AVListImpl();
        params.setValue(AVKey.LAYER_NAMES, layerCaps.getName());
        if (selectedStyle != null)
            params.setValue(AVKey.STYLE_NAMES, selectedStyle.getName());
        String abs = layerCaps.getLayerAbstract();
        if (!WWUtil.isEmpty(abs))
            params.setValue(AVKey.LAYER_ABSTRACT, abs);
        params.setValue(AVKey.DISPLAY_NAME, getTitle());

        AVList configParams = params.copy();
        configParams.setValue(AVKey.URL_CONNECT_TIMEOUT, 30000);
        configParams.setValue(AVKey.URL_READ_TIMEOUT, 30000);
        configParams.setValue(AVKey.RETRIEVAL_QUEUE_STALE_REQUEST_LIMIT, 60000);

        try
        {
            String factoryKey = isElevationModel() ? AVKey.ELEVATION_MODEL_FACTORY : AVKey.LAYER_FACTORY;
            Factory factory = (Factory) WorldWind.createConfigurationComponent(factoryKey);
            Object result = factory.createFromConfigSource(caps, configParams);
            if (result instanceof Layer layer)
                layer.setName(getTitle());
            return result;
        }
        catch (Exception e)
        {
            logger.log(Level.WARNING, "Failed to create WMS layer component for: " + getTitle(), e);
            return null;
        }
    }
}
