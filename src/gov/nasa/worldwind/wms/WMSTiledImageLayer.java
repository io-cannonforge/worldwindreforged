/*
 * Copyright 2006-2009, 2017, 2020 United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The NASA World Wind Java (WWJ) platform is licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * NASA World Wind Java (WWJ) also contains the following 3rd party Open Source
 * software:
 *
 *     Jackson Parser – Licensed under Apache 2.0
 *     GDAL – Licensed under MIT
 *     JOGL – Licensed under  Berkeley Software Distribution (BSD)
 *     Gluegen – Licensed under Berkeley Software Distribution (BSD)
 *
 * A complete listing of 3rd Party software notices and licenses included in
 * NASA World Wind Java (WWJ)  can be found in the WorldWindJava-v2.2 3rd-party
 * notices and licenses PDF found in code directory.
 */
package gov.nasa.worldwind.wms;

import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.avlist.AVListImpl;
import gov.nasa.worldwind.exception.WWRuntimeException;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.layers.BasicTiledImageLayer;
import gov.nasa.worldwind.layers.TextureTile;
import gov.nasa.worldwind.ogc.wms.WMSCapabilities;
import gov.nasa.worldwind.ogc.wms.WMSLayerCapabilities;
import gov.nasa.worldwind.ogc.wms.WMSLayerDimension;
import gov.nasa.worldwind.util.DataConfigurationUtils;
import gov.nasa.worldwind.util.ImageUtil;
import gov.nasa.worldwind.util.Level;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.RestorableSupport;
import gov.nasa.worldwind.util.Tile;
import gov.nasa.worldwind.util.TileUrlBuilder;
import gov.nasa.worldwind.util.WWIO;
import gov.nasa.worldwind.util.WWUtil;
import gov.nasa.worldwind.util.WWXML;

/**
 * @author tag
 * @version $Id: WMSTiledImageLayer.java 1957 2014-04-23 23:32:39Z tgaskins $
 */
public class WMSTiledImageLayer extends BasicTiledImageLayer
{
    private static final String[] formatOrderPreference = new String[]
        {
            "image/dds", "image/png", "image/jpeg"
        };

    // Modified by seaglassfoundry.com - auto-refresh support for live WMS layers.
    // When a WMS layer advertises current="true" on its time dimension, or when
    // the user manually enables auto-refresh, tiles are periodically expired and
    // re-fetched from the server.  A shared daemon executor is used so that no
    // per-layer threads are created, and the JVM can exit cleanly.
    private static final long DEFAULT_REFRESH_INTERVAL_MS = 300_000; // 5 minutes
    private static ScheduledExecutorService refreshExecutor;

    private boolean liveData;                    // true if caps advertised current="true"
    private boolean autoRefresh;                 // whether auto-refresh is currently active
    private long autoRefreshIntervalMs = DEFAULT_REFRESH_INTERVAL_MS;
    private ScheduledFuture<?> refreshTask;

    public WMSTiledImageLayer(AVList params)
    {
        super(params);
    }

    public WMSTiledImageLayer(Document dom, AVList params)
    {
        this(dom.getDocumentElement(), params);
    }

    public WMSTiledImageLayer(Element domElement, AVList params)
    {
        this(wmsGetParamsFromDocument(domElement, params));
    }

    // Modified by seaglassfoundry.com - detect current="true" from WMS capabilities
    public WMSTiledImageLayer(WMSCapabilities caps, AVList params)
    {
        this(wmsGetParamsFromCapsDoc(caps, params));
        this.liveData = detectLiveData(caps, params);
        if (this.liveData)
            setAutoRefresh(true);
    }

    public WMSTiledImageLayer(String stateInXml)
    {
        this(wmsRestorableStateToParams(stateInXml));

        RestorableSupport rs;
        try
        {
            rs = RestorableSupport.parse(stateInXml);
        }
        catch (Exception e)
        {
            // Parsing the document specified by stateInXml failed.
            String message = Logging.getMessage("generic.ExceptionAttemptingToParseStateXml", stateInXml);
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message, e);
        }

        this.doRestoreState(rs, null);
    }

    //**************************************************************//
    //********************  Auto-Refresh (Live Data)  *************//
    //**************************************************************//
    // Modified by seaglassfoundry.com - auto-refresh for live WMS layers

    /**
     * Returns true if the WMS capabilities indicated this layer serves live/current data
     * (i.e., the time dimension had current="true").
     */
    public boolean isLiveData()
    {
        return this.liveData;
    }

    /** Returns true if auto-refresh is currently active. */
    public boolean getAutoRefresh()
    {
        return this.autoRefresh;
    }

    /**
     * Enables or disables automatic tile refresh.  When enabled, all cached tiles are
     * periodically expired, forcing a re-download from the server on the next render pass.
     * The default interval is 5 minutes.
     *
     * @param enabled true to start auto-refresh, false to stop.
     */
    public synchronized void setAutoRefresh(boolean enabled)
    {
        if (this.autoRefresh == enabled)
            return;

        this.autoRefresh = enabled;
        if (enabled)
            startRefreshTimer();
        else
            stopRefreshTimer();
    }

    /** Returns the auto-refresh interval in milliseconds. */
    public long getAutoRefreshInterval()
    {
        return this.autoRefreshIntervalMs;
    }

    /**
     * Sets the auto-refresh interval.  If auto-refresh is already active the timer is
     * restarted with the new interval.
     *
     * @param intervalMs interval in milliseconds (minimum 30 000).
     */
    public synchronized void setAutoRefreshInterval(long intervalMs)
    {
        if (intervalMs < 30_000)
            intervalMs = 30_000;

        this.autoRefreshIntervalMs = intervalMs;
        if (this.autoRefresh)
        {
            stopRefreshTimer();
            startRefreshTimer();
        }
    }

    @Override
    public void setEnabled(boolean enabled)
    {
        super.setEnabled(enabled);

        // Pause/resume the refresh timer when the layer is disabled/enabled.
        synchronized (this)
        {
            if (this.autoRefresh)
            {
                if (enabled)
                    startRefreshTimer();
                else
                    stopRefreshTimer();
            }
        }
    }

    private void startRefreshTimer()
    {
        if (this.refreshTask != null)
            return; // already running

        if (refreshExecutor == null)
        {
            refreshExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "WMS-AutoRefresh");
                t.setDaemon(true);
                return t;
            });
        }

        this.refreshTask = refreshExecutor.scheduleWithFixedDelay(() -> {
            if (this.isEnabled())
            {
                this.setExpiryTime(System.currentTimeMillis());
                this.firePropertyChange(
                    new PropertyChangeEvent(this, AVKey.LAYER, null, this));
            }
        }, this.autoRefreshIntervalMs, this.autoRefreshIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void stopRefreshTimer()
    {
        if (this.refreshTask != null)
        {
            this.refreshTask.cancel(false);
            this.refreshTask = null;
        }
    }

    /**
     * Check WMS capabilities for a time dimension with current="true".
     */
    private static boolean detectLiveData(WMSCapabilities caps, AVList params)
    {
        if (caps == null || params == null)
            return false;

        String layerNames = params.getStringValue(AVKey.LAYER_NAMES);
        if (layerNames == null)
            return false;

        for (String name : layerNames.split(","))
        {
            WMSLayerCapabilities layerCaps = caps.getLayerByName(name.trim());
            if (layerCaps == null)
                continue;

            Set<WMSLayerDimension> dims = layerCaps.getDimensions();
            if (dims == null)
                continue;

            for (WMSLayerDimension d : dims)
            {
                if ("time".equalsIgnoreCase(d.getName()) && Boolean.TRUE.equals(d.isCurrent()))
                    return true;
            }
        }
        return false;
    }

    //**************************************************************//
    //********************  Parameter Extraction  *****************//
    //**************************************************************//

    /**
     * Extracts parameters necessary to configure the layer from an XML DOM element.
     *
     * @param domElement the element to search for parameters.
     * @param params     an attribute-value list in which to place the extracted parameters. May be null, in which case
     *                   a new attribue-value list is created and returned.
     *
     * @return the attribute-value list passed as the second parameter, or the list created if the second parameter is
     *         null.
     *
     * @throws IllegalArgumentException if the DOM element is null.
     */
    protected static AVList wmsGetParamsFromDocument(Element domElement, AVList params)
    {
        if (domElement == null)
        {
            String message = Logging.getMessage("nullValue.DocumentIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        if (params == null)
            params = new AVListImpl();

        DataConfigurationUtils.getWMSLayerConfigParams(domElement, params);
        BasicTiledImageLayer.getParamsFromDocument(domElement, params);

        params.setValue(AVKey.TILE_URL_BUILDER, new URLBuilder(params));

        return params;
    }

    /**
     * Extracts parameters necessary to configure the layer from a WMS capabilities document.
     *
     * @param caps   the capabilities document.
     * @param params an attribute-value list in which to place the extracted parameters. May be null, in which case a
     *               new attribute-value list is created and returned.
     *
     * @return the attribute-value list passed as the second parameter, or the list created if the second parameter is
     *         null.
     *
     * @throws IllegalArgumentException if the capabilities document reference is null.
     */
    public static AVList wmsGetParamsFromCapsDoc(WMSCapabilities caps, AVList params)
    {
        if (caps == null)
        {
            String message = Logging.getMessage("nullValue.WMSCapabilities");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        if (params == null)
            params = new AVListImpl();

        try
        {
            DataConfigurationUtils.getWMSLayerConfigParams(caps, formatOrderPreference, params);
        }
        catch (IllegalArgumentException e)
        {
            String message = Logging.getMessage("WMS.MissingLayerParameters");
            Logging.logger().log(java.util.logging.Level.SEVERE, message, e);
            throw new IllegalArgumentException(message, e);
        }
        catch (WWRuntimeException e)
        {
            String message = Logging.getMessage("WMS.MissingCapabilityValues");
            Logging.logger().log(java.util.logging.Level.SEVERE, message, e);
            throw new IllegalArgumentException(message, e);
        }

        setFallbacks(params);

        // Setup WMS URL builder.
        params.setValue(AVKey.TILE_URL_BUILDER, new URLBuilder(params));
        // Setup default WMS tiled image layer behaviors.
        params.setValue(AVKey.USE_TRANSPARENT_TEXTURES, true);

        return params;
    }

    // TODO: consolidate common code in WMSTiledImageLayer.URLBuilder and WMSBasicElevationModel.URLBuilder
    public static class URLBuilder implements TileUrlBuilder
    {
        private static final String MAX_VERSION = "1.3.0";

        private final String layerNames;
        private final String styleNames;
        private final String imageFormat;
        private final String wmsVersion;
        private final String crs;
        private final String backgroundColor;
        private String timeString;
        public String URLTemplate;

        public URLBuilder(AVList params)
        {
            this.layerNames = params.getStringValue(AVKey.LAYER_NAMES);
            this.styleNames = params.getStringValue(AVKey.STYLE_NAMES);
            this.imageFormat = params.getStringValue(AVKey.IMAGE_FORMAT);
            this.backgroundColor = params.getStringValue(AVKey.WMS_BACKGROUND_COLOR);
            this.timeString = params.getStringValue(AVKey.WMS_TIME_STRING);
            String version = params.getStringValue(AVKey.WMS_VERSION);

            String coordSystemKey;
            String defaultCS;
            if (version == null || WWUtil.compareVersion(version, "1.3.0") >= 0)
            {
                this.wmsVersion = MAX_VERSION;
                coordSystemKey = "&crs=";
                defaultCS = "CRS:84"; // would like to do EPSG:4326 but that's incompatible with our old WMS server, see WWJ-474
            }
            else
            {
                this.wmsVersion = version;
                coordSystemKey = "&srs=";
                defaultCS = "EPSG:4326";
            }

            String coordinateSystem = params.getStringValue(AVKey.COORDINATE_SYSTEM);
            this.crs = coordSystemKey + (coordinateSystem != null ? coordinateSystem : defaultCS);
        }

        @Override
		public URL getURL(Tile tile, String altImageFormat) throws MalformedURLException
        {
            StringBuilder sb;
            if (this.URLTemplate == null)
            {
                sb = new StringBuilder(WWXML.fixGetMapString(tile.getLevel().getService()));

                if (!sb.toString().toLowerCase().contains("service=wms"))
                    sb.append("service=WMS");
                sb.append("&request=GetMap");
                sb.append("&version=").append(this.wmsVersion);
                sb.append(this.crs);
                sb.append("&layers=").append(this.layerNames);
                sb.append("&styles=").append(this.styleNames != null ? this.styleNames : "");
                sb.append("&transparent=TRUE");
                if (this.backgroundColor != null)
                    sb.append("&bgcolor=").append(this.backgroundColor);

                this.URLTemplate = sb.toString();
            }
            else
            {
                sb = new StringBuilder(this.URLTemplate);
            }

            if (this.timeString != null)
                sb.append("&time=").append(this.timeString);

            String format = (altImageFormat != null) ? altImageFormat : this.imageFormat;
            if (null != format)
                sb.append("&format=").append(format);

            sb.append("&width=").append(tile.getWidth());
            sb.append("&height=").append(tile.getHeight());

            Sector s = tile.getSector();
            sb.append("&bbox=");
            // The order of the coordinate specification matters, and it changed with WMS 1.3.0.
            if (WWUtil.compareVersion(this.wmsVersion, "1.1.1") <= 0 || this.crs.contains("CRS:84"))
            {
                // 1.1.1 and earlier and CRS:84 use lon/lat order
                sb.append(s.getMinLongitude().getDegrees());
                sb.append(",");
                sb.append(s.getMinLatitude().getDegrees());
                sb.append(",");
                sb.append(s.getMaxLongitude().getDegrees());
                sb.append(",");
                sb.append(s.getMaxLatitude().getDegrees());
            }
            else
            {
                // 1.3.0 uses lat/lon ordering
                sb.append(s.getMinLatitude().getDegrees());
                sb.append(",");
                sb.append(s.getMinLongitude().getDegrees());
                sb.append(",");
                sb.append(s.getMaxLatitude().getDegrees());
                sb.append(",");
                sb.append(s.getMaxLongitude().getDegrees());
            }

            return new java.net.URL(sb.toString().replace(" ", "%20"));
        }

        public String getTimeString()
        {
            return this.timeString;
        }

        public void setTimeString(String time)
        {
            this.timeString = time;
        }
    }

    protected static class ComposeImageTile extends TextureTile
    {
        protected int width;
        protected int height;
        protected File file;

        public ComposeImageTile(Sector sector, String mimeType, Level level, int width, int height)
            throws IOException
        {
            super(sector, level, -1, -1); // row and column aren't used and need to signal that

            this.width = width;
            this.height = height;

            this.file = File.createTempFile(WWIO.DELETE_ON_EXIT_PREFIX, WWIO.makeSuffixForMimeType(mimeType));
        }

        @Override
        public int getWidth()
        {
            return this.width;
        }

        @Override
        public int getHeight()
        {
            return this.height;
        }

        @Override
        public String getPath()
        {
            return this.file.getPath();
        }

        public File getFile()
        {
            return this.file;
        }
    }

    @Override
    public BufferedImage composeImageForSector(Sector sector, int canvasWidth, int canvasHeight, double aspectRatio,
        int levelNumber, String mimeType, boolean abortOnError, BufferedImage image, int timeout) throws Exception
    {
        if (sector == null)
        {
            String message = Logging.getMessage("nullValue.SectorIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        Level requestedLevel;
        if ((levelNumber >= 0) && (levelNumber < this.getLevels().getNumLevels()))
            requestedLevel = this.getLevels().getLevel(levelNumber);
        else
            requestedLevel = this.getLevels().getLastLevel();
        ComposeImageTile tile =
            new ComposeImageTile(sector, mimeType, requestedLevel, canvasWidth, canvasHeight);
        try
        {
            if (image == null)
                image = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);

            downloadImage(tile, mimeType, timeout);
            Thread.sleep(1); // generates InterruptedException if thread has been interupted

            BufferedImage tileImage = ImageIO.read(tile.getFile());
            Thread.sleep(1); // generates InterruptedException if thread has been interupted

            ImageUtil.mergeImage(sector, tile.getSector(), aspectRatio, tileImage, image);
            Thread.sleep(1); // generates InterruptedException if thread has been interupted

            this.firePropertyChange(AVKey.PROGRESS, 0d, 1d);
        }
        catch (InterruptedIOException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            if (abortOnError)
                throw e;

            String message = Logging.getMessage("generic.ExceptionWhileRequestingImage", tile.getPath());
            Logging.logger().log(java.util.logging.Level.WARNING, message, e);
        }

        return image;
    }

    //**************************************************************//
    //********************  Configuration  *************************//
    //**************************************************************//

    /**
     * Appends WMS tiled image layer configuration elements to the superclass configuration document.
     *
     * @param params configuration parameters describing this WMS tiled image layer.
     *
     * @return a WMS tiled image layer configuration document.
     */
    @Override
	protected Document createConfigurationDocument(AVList params)
    {
        Document doc = super.createConfigurationDocument(params);
        if (doc == null || doc.getDocumentElement() == null)
            return doc;

        DataConfigurationUtils.createWMSLayerConfigElements(params, doc.getDocumentElement());

        return doc;
    }

    //**************************************************************//
    //********************  Restorable Support  ********************//
    //**************************************************************//

    @Override
	public void getRestorableStateForAVPair(String key, Object value,
        RestorableSupport rs, RestorableSupport.StateObject context)
    {
        if (value instanceof URLBuilder)
        {
            rs.addStateValueAsString(context, "wms.Version", ((URLBuilder) value).wmsVersion);
            rs.addStateValueAsString(context, "wms.Crs", ((URLBuilder) value).crs);
        }
        else
        {
            super.getRestorableStateForAVPair(key, value, rs, context);
        }
    }

    /**
     * Creates an attribute-value list from an xml document containing restorable state for this layer.
     *
     * @param stateInXml an xml document specified in a {@link String}.
     *
     * @return an attribute-value list containing the parameters in the specified restorable state.
     *
     * @throws IllegalArgumentException if the state reference is null.
     */
    public static AVList wmsRestorableStateToParams(String stateInXml)
    {
        if (stateInXml == null)
        {
            String message = Logging.getMessage("nullValue.StringIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        RestorableSupport rs;
        try
        {
            rs = RestorableSupport.parse(stateInXml);
        }
        catch (Exception e)
        {
            // Parsing the document specified by stateInXml failed.
            String message = Logging.getMessage("generic.ExceptionAttemptingToParseStateXml", stateInXml);
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message, e);
        }

        AVList params = new AVListImpl();
        wmsRestoreStateToParams(rs, null, params);
        return params;
    }

    protected static void wmsRestoreStateToParams(RestorableSupport rs, RestorableSupport.StateObject context,
        AVList params)
    {
        // Invoke the BasicTiledImageLayer functionality.
        restoreStateForParams(rs, context, params);
        // Parse any legacy WMSTiledImageLayer state values.
        legacyWmsRestoreStateToParams(rs, context, params);

        String s = rs.getStateValueAsString(context, AVKey.IMAGE_FORMAT);
        if (s != null)
            params.setValue(AVKey.IMAGE_FORMAT, s);

        s = rs.getStateValueAsString(context, AVKey.TITLE);
        if (s != null)
            params.setValue(AVKey.TITLE, s);

        s = rs.getStateValueAsString(context, AVKey.DISPLAY_NAME);
        if (s != null)
            params.setValue(AVKey.DISPLAY_NAME, s);

        RestorableSupport.adjustTitleAndDisplayName(params);

        s = rs.getStateValueAsString(context, AVKey.LAYER_NAMES);
        if (s != null)
            params.setValue(AVKey.LAYER_NAMES, s);

        s = rs.getStateValueAsString(context, AVKey.STYLE_NAMES);
        if (s != null)
            params.setValue(AVKey.STYLE_NAMES, s);

        s = rs.getStateValueAsString(context, "wms.Version");
        if (s != null)
            params.setValue(AVKey.WMS_VERSION, s);
        params.setValue(AVKey.TILE_URL_BUILDER, new URLBuilder(params));
    }

    protected static void legacyWmsRestoreStateToParams(RestorableSupport rs, RestorableSupport.StateObject context,
        AVList params)
    {
        // WMSTiledImageLayer has historically used a different format for storing LatLon and Sector properties
        // in the restorable state XML documents. Although WMSTiledImageLayer no longer writes these properties,
        // we must provide support for reading them here.
        Double lat = rs.getStateValueAsDouble(context, AVKey.LEVEL_ZERO_TILE_DELTA + ".Latitude");
        Double lon = rs.getStateValueAsDouble(context, AVKey.LEVEL_ZERO_TILE_DELTA + ".Longitude");
        if (lat != null && lon != null)
            params.setValue(AVKey.LEVEL_ZERO_TILE_DELTA, LatLon.fromDegrees(lat, lon));

        Double minLat = rs.getStateValueAsDouble(context, AVKey.SECTOR + ".MinLatitude");
        Double minLon = rs.getStateValueAsDouble(context, AVKey.SECTOR + ".MinLongitude");
        Double maxLat = rs.getStateValueAsDouble(context, AVKey.SECTOR + ".MaxLatitude");
        Double maxLon = rs.getStateValueAsDouble(context, AVKey.SECTOR + ".MaxLongitude");
        if (minLat != null && minLon != null && maxLat != null && maxLon != null)
            params.setValue(AVKey.SECTOR, Sector.fromDegrees(minLat, maxLat, minLon, maxLon));
    }
}
