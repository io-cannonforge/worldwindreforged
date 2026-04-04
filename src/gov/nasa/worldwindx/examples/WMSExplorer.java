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
 *     Jackson Parser - Licensed under Apache 2.0
 *     GDAL - Licensed under MIT
 *     JOGL - Licensed under  Berkeley Software Distribution (BSD)
 *     Gluegen - Licensed under Berkeley Software Distribution (BSD)
 *
 * A complete listing of 3rd Party software notices and licenses included in
 * NASA World Wind Java (WWJ)  can be found in the WorldWindJava-v2.2 3rd-party
 * notices and licenses PDF found in code directory.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.util.logging.Logger;

import gov.nasa.worldwind.Factory;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.avlist.AVListImpl;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.globes.ElevationModel;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwind.ogc.wms.WMSCapabilities;
import gov.nasa.worldwind.ogc.wms.WMSLayerCapabilities;
import gov.nasa.worldwind.ogc.wms.WMSLayerDimension;
import gov.nasa.worldwind.ogc.wms.WMSLayerStyle;
import gov.nasa.worldwind.terrain.CompoundElevationModel;
import gov.nasa.worldwind.util.Level;
import gov.nasa.worldwind.util.LevelSet;
import gov.nasa.worldwind.util.WWUtil;
import gov.nasa.worldwind.wms.WMSTiledImageLayer;

/**
 * A polished WMS Explorer application with a dark-themed UI. Provides server connection management, layer browsing
 * with filtering, per-layer opacity controls, and basic time-dimension controls.
 */
public class WMSExplorer
{
    private static final Logger logger = Logger.getLogger(WMSExplorer.class.getName());
    // ---- Color palette ----
    private static final Color BG_DARK = new Color(45, 45, 48);
    private static final Color BG_PANEL = new Color(60, 63, 65);
    private static final Color BG_FIELD = new Color(69, 73, 74);
    private static final Color FG_PRIMARY = new Color(220, 220, 220);
    private static final Color FG_SECONDARY = new Color(160, 160, 160);
    private static final Color ACCENT = new Color(0, 122, 204);
    private static final Color ACCENT_HOVER = new Color(28, 151, 234);
    private static final Color BG_SELECTED = new Color(30, 70, 110);   // Modified by seaglassfoundry.com - highlight selected layer entries
    private static final Color BG_HOVER    = new Color(70, 73, 76);    // Modified by seaglassfoundry.com - hover feedback on layer entries
    private static final Color BORDER_COLOR = new Color(80, 83, 85);
    private static final Color STATUS_GREEN  = new Color(80,  200, 120);
    private static final Color STATUS_YELLOW = new Color(230, 180,  50);
    private static final Color STATUS_RED    = new Color(210,  70,  70);
    private static final Color STATUS_GRAY   = new Color(140, 140, 140);
    private static final Font UI_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font UI_FONT_BOLD = new Font("Segoe UI", Font.BOLD, 12);
    private static final Font UI_FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);

    // ---- Preset servers ----
    private static final String[][] PRESET_SERVERS = {
        {"NASA GIBS", "https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi"},
        {"USGS NatMap", "https://basemap.nationalmap.gov/arcgis/services/USGSTopo/MapServer/WmsServer"},
        {"NOAA Nautical Charts", "https://gis.charttools.noaa.gov/arcgis/rest/services/MCS/NOAAChartDisplay/MapServer/exts/MaritimeChartService/WMSServer"},
        {"NOAA Radar", "https://mapservices.weather.noaa.gov/eventdriven/services/radar/radar_base_reflectivity/MapServer/WMSServer"},
        {"NOAA GOES", "https://nowcoast.noaa.gov/geoserver/satellite/wms"},
        {"DWD Germany", "https://maps.dwd.de/geoserver/wms"},
    };

    // ---- Temporal layer presets: {label, serverUrl, autoSelectLayerName} ----
    // These connect to a server and auto-enable a specific time-aware layer.
    private static final String[][] TEMPORAL_PRESETS = {
        {"MODIS Terra",  "https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi",
            "MODIS_Terra_CorrectedReflectance_TrueColor"},
        {"VIIRS S-NPP",  "https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi",
            "VIIRS_SNPP_CorrectedReflectance_TrueColor"},
        {"MODIS Fires",  "https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi",
            "MODIS_Terra_Thermal_Anomalies_Day"},
        {"Snow Cover",   "https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi",
            "MODIS_Terra_Snow_Cover"},
    };

    // ------------------------------------------------------------------
    //  Data class for a WMS layer entry
    // ------------------------------------------------------------------
    protected static class LayerEntry
    {
        protected WMSCapabilities caps;
        protected AVListImpl params = new AVListImpl();
        protected Object component;     // currently active Layer or ElevationModel
        protected boolean enabled;
        protected WMSLayerCapabilities layerCaps;

        // Time-aware cache: maps ISO date string → WMSTiledImageLayer with per-step DataCacheName.
        // Each entry caches tiles separately so scrubbing backwards serves from disk, not network.
        protected final Map<String, WMSTiledImageLayer> timeLayerCache = new LinkedHashMap<>()
        {
            private static final long serialVersionUID = 1L;
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, WMSTiledImageLayer> eldest)
            {
                return size() > 30; // keep at most 30 time steps in memory
            }
        };

        protected String getTitle()
        {
            return params.getStringValue(AVKey.DISPLAY_NAME);
        }

        protected String getName()
        {
            return params.getStringValue(AVKey.LAYER_NAMES);
        }

        protected String getAbstract()
        {
            return params.getStringValue(AVKey.LAYER_ABSTRACT);
        }

        /** Check whether this WMS layer advertises a time dimension. */
        protected boolean hasTimeDimension()
        {
            if (layerCaps == null)
                return false;
            Set<WMSLayerDimension> dims = layerCaps.getDimensions();
            if (dims == null)
                return false;
            for (WMSLayerDimension d : dims)
            {
                if ("time".equalsIgnoreCase(d.getName()))
                    return true;
            }
            return false;
        }

        // Modified by seaglassfoundry.com - detect live/current WMS layers for auto-refresh
        /** Check whether this WMS layer advertises current="true" on its time dimension. */
        protected boolean isLiveData()
        {
            if (layerCaps == null)
                return false;
            Set<WMSLayerDimension> dims = layerCaps.getDimensions();
            if (dims == null)
                return false;
            for (WMSLayerDimension d : dims)
            {
                if ("time".equalsIgnoreCase(d.getName()) && Boolean.TRUE.equals(d.isCurrent()))
                    return true;
            }
            return false;
        }

        /** Return the raw time dimension string, or null. */
        protected String getTimeDimensionString()
        {
            if (layerCaps == null)
                return null;
            Set<WMSLayerDimension> dims = layerCaps.getDimensions();
            if (dims == null)
                return null;
            for (WMSLayerDimension d : dims)
            {
                if ("time".equalsIgnoreCase(d.getName()))
                    return d.getDimension();
            }
            return null;
        }
    }

    // ------------------------------------------------------------------
    //  ExplorerFrame - the main frame
    // ------------------------------------------------------------------
    protected static class ExplorerFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;
        private ServerPanel serverPanel;
        private ActiveLayersPanel activeLayersPanel;
        private TimeControlPanel timeControlPanel;
        private JLabel statusLabel;
        private JWindow featureInfoPopup; // floating GetFeatureInfo result panel

        public ExplorerFrame()
        {
            super(false, false, false); // no default status bar, layer panel, stats panel

            this.setTitle("WorldWind WMS Explorer");

            // Build the custom layout
            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(BG_DARK);

            // ---- Globe panel (center) ----
            JPanel globePanel = new JPanel(new BorderLayout());
            globePanel.setBackground(BG_DARK);
            globePanel.add((Component) this.getWwd(), BorderLayout.CENTER);

            // ---- Bottom bar (must be created before ServerPanel, which references timeControlPanel) ----
            timeControlPanel = new TimeControlPanel();

            // ---- Right-side panel ----
            activeLayersPanel = new ActiveLayersPanel(this.getWwd());
            serverPanel = new ServerPanel(this.getWwd(), activeLayersPanel, timeControlPanel, this::setStatus);

            JPanel rightPanel = new JPanel(new BorderLayout(0, 0));
            rightPanel.setBackground(BG_DARK);
            rightPanel.setMinimumSize(new Dimension(250, 0));
            rightPanel.setPreferredSize(new Dimension(350, 0));
            rightPanel.add(serverPanel, BorderLayout.CENTER);
            rightPanel.add(activeLayersPanel, BorderLayout.SOUTH);

            // ---- Bottom bar ----
            JPanel bottomBar = new JPanel(new BorderLayout(0, 0));
            bottomBar.setBackground(BG_DARK);
            bottomBar.add(timeControlPanel, BorderLayout.CENTER);

            statusLabel = new JLabel("  Ready");
            statusLabel.setFont(UI_FONT_SMALL);
            statusLabel.setForeground(FG_SECONDARY);
            statusLabel.setBackground(BG_DARK);
            statusLabel.setOpaque(true);
            statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
            statusLabel.setPreferredSize(new Dimension(0, 26));
            bottomBar.add(statusLabel, BorderLayout.SOUTH);

            // Modified by seaglassfoundry.com - use a split pane between the globe and
            // the right panel so it can be resized, consistent with other examples.
            javax.swing.JSplitPane splitPane = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.HORIZONTAL_SPLIT, globePanel, rightPanel);
            splitPane.setResizeWeight(0.67);
            splitPane.setDividerSize(5);
            splitPane.setContinuousLayout(true);

            root.add(splitPane, BorderLayout.CENTER);
            root.add(bottomBar, BorderLayout.SOUTH);

            // Set initial divider at 2/3 after layout is complete.
            this.addComponentListener(new java.awt.event.ComponentAdapter() {
                private boolean initialized;
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    if (!initialized) {
                        splitPane.setDividerLocation(getWidth() * 2 / 3);
                        initialized = true;
                    }
                }
            });

            this.getContentPane().removeAll();
            this.getContentPane().add(root, BorderLayout.CENTER);

            this.setPreferredSize(new Dimension(1280, 800));
            this.pack();
            WWUtil.alignComponent(null, this, AVKey.CENTER);

            // GetFeatureInfo: right-click on the globe queries all active WMS layers.
            ((java.awt.Component) this.getWwd()).addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    if (e.getButton() == MouseEvent.BUTTON3) // right-click
                    {
                        Position pos = getWwd().getCurrentPosition();
                        if (pos != null)
                            queryFeatureInfo(pos, e.getPoint());
                    }
                }
            });
        }

        private void setStatus(String msg)
        {
            SwingUtilities.invokeLater(() -> statusLabel.setText("  " + msg));
        }

        /** Issue WMS GetFeatureInfo for every active WMS layer and show results in a popup. */
        private void queryFeatureInfo(Position pos, Point screenPt)
        {
            List<LayerEntry> activeWms = serverPanel.getActiveWmsEntries();
            if (activeWms.isEmpty())
                return;

            // Build request using a small 101x101 bounding box centred on the clicked position.
            double halfDeg = 0.5;
            double minLon = pos.getLongitude().degrees - halfDeg;
            double maxLon = pos.getLongitude().degrees + halfDeg;
            double minLat = pos.getLatitude().degrees  - halfDeg;
            double maxLat = pos.getLatitude().degrees  + halfDeg;
            int W = 101, H = 101;

            StringBuilder html = new StringBuilder("<html><body style='font-family:Segoe UI;font-size:11px'>");
            html.append("<b>Position:</b> ").append(String.format("%.4f°, %.4f°",
                pos.getLatitude().degrees, pos.getLongitude().degrees)).append("<br><br>");

            for (LayerEntry entry : activeWms)
            {
                if (!(entry.component instanceof WMSTiledImageLayer))
                    continue;
                String serviceUrl = entry.caps.getRequestURL("GetFeatureInfo", "HTTP", "get");
                if (serviceUrl == null)
                    serviceUrl = entry.caps.getRequestURL("GetMap", "HTTP", "get");
                String layerName = entry.params.getStringValue(AVKey.LAYER_NAMES);
                String bbox = String.format("%f,%f,%f,%f", minLon, minLat, maxLon, maxLat);
                String query = serviceUrl
                    + (serviceUrl.contains("?") ? "&" : "?")
                    + "SERVICE=WMS&REQUEST=GetFeatureInfo&VERSION=1.1.1"
                    + "&LAYERS=" + layerName + "&QUERY_LAYERS=" + layerName
                    + "&STYLES=&SRS=EPSG:4326"
                    + "&BBOX=" + bbox
                    + "&WIDTH=" + W + "&HEIGHT=" + H
                    + "&X=50&Y=50&INFO_FORMAT=text/html";
                html.append("<b>").append(truncate(entry.getTitle(), 40)).append(":</b><br>");
                try
                {
                    URL url = new URL(query);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(10000);
                    conn.connect();
                    if (conn.getResponseCode() == 200)
                    {
                        try (java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream())))
                        {
                            String line;
                            int lines = 0;
                            while ((line = br.readLine()) != null && lines < 20)
                            {
                                String stripped = line.replaceAll("<[^>]+>", "").trim();
                                if (!stripped.isEmpty())
                                {
                                    html.append(stripped).append("<br>");
                                    lines++;
                                }
                            }
                        }
                    }
                    else
                    {
                        html.append("(no data – HTTP ").append(conn.getResponseCode()).append(")<br>");
                    }
                }
                catch (Exception ex)
                {
                    html.append("(error: ").append(ex.getMessage()).append(")<br>");
                }
                html.append("<br>");
            }
            html.append("</body></html>");

            // Show in a dismissable popup near the click point.
            String content = html.toString();
            SwingUtilities.invokeLater(() -> showFeatureInfoPopup(content, screenPt));
        }

        private void showFeatureInfoPopup(String html, Point screenPt)
        {
            if (featureInfoPopup != null)
                featureInfoPopup.dispose();

            featureInfoPopup = new JWindow(this);
            featureInfoPopup.setBackground(BG_PANEL);

            JEditorPane pane = new JEditorPane("text/html", html);
            pane.setEditable(false);
            pane.setBackground(BG_PANEL);
            pane.setForeground(FG_PRIMARY);

            JScrollPane scroll = new JScrollPane(pane);
            scroll.setPreferredSize(new Dimension(320, 220));
            scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

            JButton closeBtn = createAccentButton("✕");
            closeBtn.setPreferredSize(new Dimension(24, 20));
            closeBtn.addActionListener(e -> featureInfoPopup.dispose());

            JPanel top = new JPanel(new BorderLayout());
            top.setBackground(BG_PANEL);
            JLabel title = createLabel(" Feature Info", UI_FONT_BOLD);
            title.setBackground(BG_PANEL);
            title.setOpaque(true);
            top.add(title, BorderLayout.CENTER);
            top.add(closeBtn, BorderLayout.EAST);

            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(BG_PANEL);
            root.add(top, BorderLayout.NORTH);
            root.add(scroll, BorderLayout.CENTER);
            featureInfoPopup.add(root);
            featureInfoPopup.pack();

            Point globePos = ((Component) getWwd()).getLocationOnScreen();
            featureInfoPopup.setLocation(globePos.x + screenPt.x + 12, globePos.y + screenPt.y + 12);
            featureInfoPopup.setVisible(true);

            // Auto-dismiss after 30 s
            new javax.swing.Timer(30000, e -> {
                if (featureInfoPopup != null) featureInfoPopup.dispose();
            }).start();
        }
    }

    // ------------------------------------------------------------------
    //  ServerPanel - server connection + layer browser
    // ------------------------------------------------------------------
    @SuppressWarnings("serial")
    protected static class ServerPanel extends JPanel
    {
        private final WorldWindow wwd;
        private final ActiveLayersPanel activeLayersPanel;
        private final TimeControlPanel timeControlPanel;
        private final java.util.function.Consumer<String> statusCallback;

        private JTextField urlField;
        private JTextField filterField;
        private JPanel layerListPanel;
        private JScrollPane layerScrollPane;
        private final List<LayerEntry> allEntries = new ArrayList<>();
        private final List<JCheckBox> allCheckBoxes = new ArrayList<>();
        private Thread loadingThread;
        // Server health: maps server URL → colored status dot label
        private final Map<String, JLabel> serverHealthDots = new LinkedHashMap<>();
        private ScheduledExecutorService healthExecutor;
        // Auto-select layer name when a temporal preset is loaded
        private volatile String pendingAutoSelect;
        // Modified by seaglassfoundry.com - track active server button for selection highlight
        private final List<JButton> allServerButtons = new ArrayList<>();
        @SuppressWarnings("unused") // assigned to track state; read access planned for future highlight logic
        private JButton activeServerButton;

        public ServerPanel(WorldWindow wwd, ActiveLayersPanel activeLayersPanel,
                           TimeControlPanel timeControlPanel,
                           java.util.function.Consumer<String> statusCallback)
        {
            super(new BorderLayout(0, 0));
            this.wwd = wwd;
            this.activeLayersPanel = activeLayersPanel;
            this.timeControlPanel = timeControlPanel;
            this.statusCallback = statusCallback;
            setBackground(BG_DARK);
            setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            buildUI();
        }

        private void buildUI()
        {
            // ---- Top: server connection area ----
            JPanel connectionArea = new JPanel();
            connectionArea.setLayout(new BoxLayout(connectionArea, BoxLayout.Y_AXIS));
            connectionArea.setBackground(BG_PANEL);
            connectionArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

            // Server URL label + field
            JLabel serverLabel = createLabel("Server URL", UI_FONT_BOLD);
            serverLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            connectionArea.add(serverLabel);
            connectionArea.add(Box.createVerticalStrut(4));

            JPanel urlRow = new JPanel(new BorderLayout(6, 0));
            urlRow.setBackground(BG_PANEL);
            urlRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            urlRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

            urlField = createTextField("");
            urlField.addActionListener(e -> connectToServer());
            urlRow.add(urlField, BorderLayout.CENTER);

            JButton connectBtn = createAccentButton("Connect");
            connectBtn.addActionListener(e -> connectToServer());
            urlRow.add(connectBtn, BorderLayout.EAST);

            connectionArea.add(urlRow);
            connectionArea.add(Box.createVerticalStrut(10));

            // Preset buttons (with health indicator dots)
            JLabel presetsLabel = createLabel("Servers", UI_FONT_BOLD);
            presetsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            connectionArea.add(presetsLabel);
            connectionArea.add(Box.createVerticalStrut(4));

            JPanel presetsPanel = new JPanel(new GridLayout(0, 2, 4, 4));
            presetsPanel.setBackground(BG_PANEL);
            presetsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            presetsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 105));
            // Modified by seaglassfoundry.com - track server buttons for active highlight
            for (String[] preset : PRESET_SERVERS)
            {
                JLabel dot = createStatusDot(STATUS_GRAY);
                serverHealthDots.put(preset[1], dot);
                JButton btn = createPresetButtonWithDot(preset[0], dot);
                final String url = preset[1];
                btn.addActionListener(e -> {
                    pendingAutoSelect = null;
                    urlField.setText(url);
                    setActiveServerButton(btn);
                    connectToServer();
                });
                allServerButtons.add(btn);
                presetsPanel.add(btn);
            }
            connectionArea.add(presetsPanel);
            connectionArea.add(Box.createVerticalStrut(10));

            // Temporal layer presets — connect and auto-enable a specific time-series layer
            JLabel temporalLabel = createLabel("Weather (auto-add)", UI_FONT_BOLD);
            temporalLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            connectionArea.add(temporalLabel);
            connectionArea.add(Box.createVerticalStrut(4));

            JPanel temporalPanel = new JPanel(new GridLayout(0, 2, 4, 4));
            temporalPanel.setBackground(BG_PANEL);
            temporalPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            temporalPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
            // Modified by seaglassfoundry.com - track temporal buttons for active highlight
            for (String[] tp : TEMPORAL_PRESETS)
            {
                JButton btn = createPresetButton(tp[0]);
                final String url  = tp[1];
                final String layer = tp[2];
                btn.addActionListener(e -> {
                    pendingAutoSelect = layer;
                    urlField.setText(url);
                    setActiveServerButton(btn);
                    connectToServer();
                });
                allServerButtons.add(btn);
                temporalPanel.add(btn);
            }
            connectionArea.add(temporalPanel);

            // Start background server health checks
            startHealthChecks();

            // ---- Middle: filter + layer list ----
            JPanel layerArea = new JPanel(new BorderLayout(0, 0));
            layerArea.setBackground(BG_PANEL);
            layerArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));

            // Separator
            JPanel filterBar = new JPanel(new BorderLayout(6, 0));
            filterBar.setBackground(BG_PANEL);
            filterBar.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

            JLabel layersLabel = createLabel("Layers", UI_FONT_BOLD);
            filterBar.add(layersLabel, BorderLayout.WEST);

            filterField = createTextField("");
            filterField.setToolTipText("Filter layers by name");
            filterField.setPreferredSize(new Dimension(160, 26));
            filterField.getDocument().addDocumentListener(new DocumentListener()
            {
                @Override
				public void insertUpdate(DocumentEvent e) { applyFilter(); }
                @Override
				public void removeUpdate(DocumentEvent e) { applyFilter(); }
                @Override
				public void changedUpdate(DocumentEvent e) { applyFilter(); }
            });

            JPanel filterWrapper = new JPanel(new BorderLayout(4, 0));
            filterWrapper.setBackground(BG_PANEL);
            JLabel filterIcon = createLabel("Filter:", UI_FONT_SMALL);
            filterWrapper.add(filterIcon, BorderLayout.WEST);
            filterWrapper.add(filterField, BorderLayout.CENTER);
            filterBar.add(filterWrapper, BorderLayout.CENTER);

            layerArea.add(filterBar, BorderLayout.NORTH);

            layerListPanel = new JPanel();
            layerListPanel.setLayout(new BoxLayout(layerListPanel, BoxLayout.Y_AXIS));
            layerListPanel.setBackground(BG_PANEL);

            layerScrollPane = new JScrollPane(layerListPanel);
            layerScrollPane.setBackground(BG_PANEL);
            layerScrollPane.getViewport().setBackground(BG_PANEL);
            layerScrollPane.setBorder(BorderFactory.createEmptyBorder());
            layerScrollPane.getVerticalScrollBar().setUnitIncrement(16);
            layerArea.add(layerScrollPane, BorderLayout.CENTER);

            // Assemble
            this.add(connectionArea, BorderLayout.NORTH);
            this.add(layerArea, BorderLayout.CENTER);
        }

        private void connectToServer()
        {
            String serverUrl = urlField.getText().trim();
            if (serverUrl.isEmpty())
            {
                JOptionPane.showMessageDialog(this, "Please enter a WMS server URL.",
                    "No URL", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Validate URI
            final URI uri;
            try
            {
                uri = new URI(serverUrl);
            }
            catch (URISyntaxException ex)
            {
                JOptionPane.showMessageDialog(this, "Invalid URL: " + ex.getMessage(),
                    "Invalid URL", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Cancel any previous loading thread
            if (loadingThread != null && loadingThread.isAlive())
                loadingThread.interrupt();

            // Clear current state
            clearLayers();

            // Show loading indicator
            layerListPanel.removeAll();
            JProgressBar progress = new JProgressBar();
            progress.setIndeterminate(true);
            progress.setBackground(BG_PANEL);
            progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            layerListPanel.add(Box.createVerticalStrut(20));
            layerListPanel.add(progress);
            JLabel loadingLabel = createLabel("  Connecting to server...", UI_FONT);
            loadingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            layerListPanel.add(Box.createVerticalStrut(6));
            layerListPanel.add(loadingLabel);
            layerListPanel.revalidate();
            layerListPanel.repaint();

            statusCallback.accept("Connecting to " + uri.getHost() + "...");

            loadingThread = new Thread(() -> loadCapabilities(uri, loadingLabel));
            loadingThread.setPriority(Thread.MIN_PRIORITY);
            loadingThread.start();
        }

        private void loadCapabilities(URI uri, JLabel loadingLabel)
        {
            WMSCapabilities caps;
            try
            {
                statusCallback.accept("Downloading capabilities from " + uri.getHost() + "...");
                caps = WMSCapabilities.retrieve(uri);
                SwingUtilities.invokeLater(() -> loadingLabel.setText("  Parsing layers (this may take a moment)..."));
                statusCallback.accept("Parsing capabilities...");
                caps.parse();
            }
            catch (Exception e)
            {
                logger.log(java.util.logging.Level.WARNING, "WMS capabilities retrieval failed: " + uri + " — " + e.getMessage());
                logger.log(java.util.logging.Level.FINE, "WMS capabilities error details", e);
                SwingUtilities.invokeLater(() -> {
                    layerListPanel.removeAll();
                    JLabel errorLabel = createLabel("  Error: " + e.getMessage(), UI_FONT);
                    errorLabel.setForeground(new Color(255, 100, 100));
                    layerListPanel.add(errorLabel);
                    layerListPanel.revalidate();
                    layerListPanel.repaint();
                    statusCallback.accept("Connection failed: " + e.getMessage());
                });
                return;
            }

            final List<WMSLayerCapabilities> namedLayers = caps.getNamedLayers();
            if (namedLayers == null || namedLayers.isEmpty())
            {
                SwingUtilities.invokeLater(() -> {
                    layerListPanel.removeAll();
                    layerListPanel.add(createLabel("  No named layers found.", UI_FONT));
                    layerListPanel.revalidate();
                    layerListPanel.repaint();
                    statusCallback.accept("No layers found on server.");
                });
                return;
            }

            // Build layer entries
            final List<LayerEntry> entries = new ArrayList<>();
            try
            {
                for (WMSLayerCapabilities lc : namedLayers)
                {
                    Set<WMSLayerStyle> styles = lc.getStyles();
                    if (styles == null || styles.isEmpty())
                    {
                        LayerEntry entry = createLayerEntry(caps, lc, null);
                        entries.add(entry);
                    }
                    else
                    {
                        for (WMSLayerStyle style : styles)
                        {
                            LayerEntry entry = createLayerEntry(caps, lc, style);
                            entries.add(entry);
                        }
                    }
                }
            }
            catch (Exception e)
            {
                logger.log(java.util.logging.Level.WARNING, "Error parsing WMS capabilities: " + e.getMessage());
                logger.log(java.util.logging.Level.FINE, "WMS parsing error details", e);
                SwingUtilities.invokeLater(() -> {
                    statusCallback.accept("Error parsing capabilities.");
                });
                return;
            }

            // Sort by title
            entries.sort(Comparator.comparing(e -> e.getTitle() != null ? e.getTitle() : ""));

            final String autoSelect = pendingAutoSelect;
            pendingAutoSelect = null;
            SwingUtilities.invokeLater(() -> {
                allEntries.clear();
                allEntries.addAll(entries);
                rebuildLayerList();
                statusCallback.accept("Loaded " + entries.size() + " layers from " + uri.getHost());
                // Auto-enable layer if requested by a temporal preset
                if (autoSelect != null)
                {
                    for (LayerEntry entry : allEntries) {
                        LayerEntry e = entry;
                        if (autoSelect.equalsIgnoreCase(e.getName()))
                        {
                            toggleLayer(e, true);
                            e.enabled = true;
                            rebuildLayerList(); // refresh checkboxes
                            statusCallback.accept("Auto-enabled: " + e.getTitle());
                            break;
                        }
                    }
                }
            });
        }

        private LayerEntry createLayerEntry(WMSCapabilities caps, WMSLayerCapabilities layerCaps,
                                            WMSLayerStyle style)
        {
            LayerEntry entry = new LayerEntry();
            entry.caps = caps;
            entry.layerCaps = layerCaps;
            entry.params = new AVListImpl();
            entry.params.setValue(AVKey.LAYER_NAMES, layerCaps.getName());
            if (style != null)
                entry.params.setValue(AVKey.STYLE_NAMES, style.getName());
            String abs = layerCaps.getLayerAbstract();
            if (!WWUtil.isEmpty(abs))
                entry.params.setValue(AVKey.LAYER_ABSTRACT, abs);
            entry.params.setValue(AVKey.DISPLAY_NAME, makeTitle(caps, entry));
            return entry;
        }

        private void rebuildLayerList()
        {
            layerListPanel.removeAll();
            allCheckBoxes.clear();

            String filter = filterField.getText().trim().toLowerCase();

            for (LayerEntry entry : allEntries)
            {
                String title = entry.getTitle();
                if (title == null)
                    title = entry.getName();
                if (!filter.isEmpty() && title != null && !title.toLowerCase().contains(filter))
                    continue;

                // Modified by seaglassfoundry.com - highlight selected layers and add hover feedback
                String label = title != null ? title : "(unnamed)";
                if (entry.isLiveData())
                    label += "  [LIVE]";
                JCheckBox cb = new JCheckBox(label);
                cb.setFont(UI_FONT);
                cb.setForeground(FG_PRIMARY);
                cb.setOpaque(true);
                cb.setBackground(entry.enabled ? BG_SELECTED : BG_PANEL);
                cb.setFocusPainted(false);
                cb.setSelected(entry.enabled);
                cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                cb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
                cb.setBorder(entry.enabled
                    ? BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT),
                        BorderFactory.createEmptyBorder(2, 7, 2, 10))
                    : BorderFactory.createEmptyBorder(2, 10, 2, 10));

                if (entry.getAbstract() != null)
                    cb.setToolTipText(truncate(entry.getAbstract(), 200));

                final LayerEntry layerEntry = entry;
                cb.addActionListener(e -> {
                    toggleLayer(layerEntry, cb.isSelected());
                    cb.setBackground(cb.isSelected() ? BG_SELECTED : BG_PANEL);
                    cb.setBorder(cb.isSelected()
                        ? BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT),
                            BorderFactory.createEmptyBorder(2, 7, 2, 10))
                        : BorderFactory.createEmptyBorder(2, 10, 2, 10));
                });
                cb.addMouseListener(new MouseAdapter()
                {
                    @Override
                    public void mouseEntered(MouseEvent e)
                    {
                        if (!cb.isSelected())
                            cb.setBackground(BG_HOVER);
                    }

                    @Override
                    public void mouseExited(MouseEvent e)
                    {
                        cb.setBackground(cb.isSelected() ? BG_SELECTED : BG_PANEL);
                    }
                });

                layerListPanel.add(cb);
                allCheckBoxes.add(cb);
            }

            if (layerListPanel.getComponentCount() == 0)
            {
                JLabel noMatch = createLabel("  No matching layers.", UI_FONT);
                noMatch.setForeground(FG_SECONDARY);
                layerListPanel.add(noMatch);
            }

            layerListPanel.revalidate();
            layerListPanel.repaint();
        }

        private void applyFilter()
        {
            rebuildLayerList();
        }

        // Modified by seaglassfoundry.com - highlight the active server button and reset others
        private void setActiveServerButton(JButton btn)
        {
            for (JButton b : allServerButtons)
            {
                b.putClientProperty("wms.active", Boolean.FALSE);
                b.setBackground(BG_FIELD);
                b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)));
            }
            activeServerButton = btn;
            btn.putClientProperty("wms.active", Boolean.TRUE);
            btn.setBackground(BG_SELECTED);
            btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT, 2),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        }

        private void toggleLayer(LayerEntry entry, boolean enable)
        {
            entry.enabled = enable;
            if (enable)
            {
                if (entry.component == null)
                    entry.component = createComponent(entry.caps, entry.params);

                if (entry.component instanceof Layer layer)
                {
                    layer.setEnabled(true);
                    LayerList layers = wwd.getModel().getLayers();
                    if (!layers.contains(layer))
                        ApplicationTemplate.insertBeforePlacenames(wwd, layer);
                }
                else if (entry.component instanceof ElevationModel model)
                {
                    CompoundElevationModel cem =
                        (CompoundElevationModel) wwd.getModel().getGlobe().getElevationModel();
                    if (!cem.getElevationModels().contains(model))
                        cem.addElevationModel(model);
                }
                activeLayersPanel.addLayer(entry);

                // If this layer has a time dimension, wire up the time controls
                if (entry.hasTimeDimension())
                {
                    String dimString = entry.getTimeDimensionString();
                    timeControlPanel.setTimeDimension(dimString);
                    timeControlPanel.setTimeChangeListener(timeStr -> updateLayerTime(timeStr));
                    // Apply current time if already set
                    String current = timeControlPanel.getCurrentTimeString();
                    if (current != null)
                        updateLayerTime(current);
                    statusCallback.accept("Time dimension active: " + dimString);
                }
            }
            else
            {
                if (entry.component instanceof Layer layer)
                {
                    layer.setEnabled(false);
                    wwd.getModel().getLayers().remove(layer);
                }
                activeLayersPanel.removeLayer(entry);

                // If no remaining time-enabled layers, clear the time controls
                boolean anyTimeEnabled = allEntries.stream()
                    .anyMatch(e -> e.enabled && e.hasTimeDimension());
                if (!anyTimeEnabled)
                {
                    timeControlPanel.setTimeDimension(null);
                    timeControlPanel.setTimeChangeListener(null);
                }
            }
            wwd.redraw();
        }

        /**
         * Switch all active time-enabled WMS layers to the given ISO date.
         * Uses per-time-step layer instances so each step has its own DataCacheName on disk —
         * scrubbing backwards reuses cached tiles rather than re-downloading.
         * Adjacent time steps are pre-created in background so they are ready immediately.
         */
        private void updateLayerTime(String isoDate)
        {
            for (LayerEntry entry : allEntries)
            {
                if (!entry.enabled || !entry.hasTimeDimension())
                    continue;

                WMSTiledImageLayer newLayer = getOrCreateTimeLayer(entry, isoDate);
                if (newLayer == null)
                    continue;

                // Hide all other time layers for this entry
                for (var kv : entry.timeLayerCache.entrySet())
                {
                    boolean isActive = kv.getKey().equals(isoDate);
                    kv.getValue().setEnabled(isActive);
                    if (!isActive && !wwd.getModel().getLayers().contains(kv.getValue()))
                        continue; // not yet added; skip
                }

                // Add the new layer to the globe if not already present
                LayerList layers = wwd.getModel().getLayers();
                if (!layers.contains(newLayer))
                    ApplicationTemplate.insertBeforePlacenames(wwd, newLayer);
                newLayer.setEnabled(true);

                // Remove the generic component layer if it was added during toggleLayer
                if (entry.component instanceof Layer generic && generic != newLayer
                    && layers.contains(generic))
                    layers.remove(generic);

                entry.component = newLayer;
            }
            wwd.redraw();

            // Prefetch adjacent time steps in background to reduce future latency
            prefetchAdjacentTimeSteps(isoDate);
        }

        /** Get or create a WMSTiledImageLayer for the given time step, with its own disk-cache subdirectory. */
        private WMSTiledImageLayer getOrCreateTimeLayer(LayerEntry entry, String isoDate)
        {
            WMSTiledImageLayer existing = entry.timeLayerCache.get(isoDate);
            if (existing != null)
                return existing;

            try
            {
                // Process caps → params, then redirect DataCacheName to a time-stamped subdirectory.
                AVList processed = WMSTiledImageLayer.wmsGetParamsFromCapsDoc(entry.caps, entry.params.copy());
                processed.setValue(AVKey.URL_CONNECT_TIMEOUT, 30000);
                processed.setValue(AVKey.URL_READ_TIMEOUT, 30000);
                processed.setValue(AVKey.RETRIEVAL_QUEUE_STALE_REQUEST_LIMIT, 60000);

                String base = processed.getStringValue(AVKey.DATA_CACHE_NAME);
                if (base != null && !isoDate.isEmpty())
                    processed.setValue(AVKey.DATA_CACHE_NAME,
                        base + "/t_" + isoDate.replaceAll("[^A-Za-z0-9_-]", "_"));

                WMSTiledImageLayer layer = new WMSTiledImageLayer(processed);

                // Set the time string on every level's URLBuilder
                LevelSet levels = layer.getLevels();
                if (levels != null)
                {
                    for (int i = 0; i < levels.getNumLevels(); i++)
                    {
                        Level lv = levels.getLevel(i);
                        if (lv.getUrlBuilder() instanceof WMSTiledImageLayer.URLBuilder ub)
                        {
                            ub.setTimeString(isoDate);
                            ub.URLTemplate = null;
                        }
                    }
                }

                layer.setEnabled(false); // caller enables it
                entry.timeLayerCache.put(isoDate, layer);
                return layer;
            }
            catch (Exception e)
            {
                logger.log(java.util.logging.Level.WARNING, "Failed to create time-step layer for " + isoDate + ": " + e.getMessage());
                return null;
            }
        }

        /** Pre-create layer objects for the immediately adjacent time steps (N-1, N+1). */
        private void prefetchAdjacentTimeSteps(String isoDate)
        {
            // Find isoDate in the time slider values to determine adjacent steps
            List<Long> times = timeControlPanel.getTimeValues();
            if (times == null || times.isEmpty())
                return;

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            int idx = -1;
            try
            {
                long target = sdf.parse(isoDate).getTime();
                for (int i = 0; i < times.size(); i++)
                {
                    if (times.get(i) == target) { idx = i; break; }
                }
            }
            catch (Exception ex) { return; }

            if (idx < 0) return;

            List<Integer> prefetchIdxs = new ArrayList<>();
            if (idx > 0)           prefetchIdxs.add(idx - 1);
            if (idx < times.size() - 1) prefetchIdxs.add(idx + 1);
            if (idx < times.size() - 2) prefetchIdxs.add(idx + 2);

            for (int pi : prefetchIdxs)
            {
                final String prefetchDate = sdf.format(new Date(times.get(pi)));
                Thread t = new Thread(() -> {
                    for (LayerEntry entry : allEntries)
                    {
                        if (entry.enabled && entry.hasTimeDimension())
                            getOrCreateTimeLayer(entry, prefetchDate); // creates layer + cache dir
                    }
                });
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                t.start();
            }
        }

        /** Return all enabled LayerEntries whose component is a WMSTiledImageLayer. */
        public List<LayerEntry> getActiveWmsEntries()
        {
            List<LayerEntry> result = new ArrayList<>();
            for (LayerEntry e : allEntries)
            {
                if (e.enabled && e.component instanceof WMSTiledImageLayer)
                    result.add(e);
            }
            return result;
        }

        /** Start a scheduled background thread that pings each preset server every 60 s. */
        private void startHealthChecks()
        {
            healthExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "WMSExplorer-HealthCheck");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
            // Initial check after 2 s, then every 60 s
            healthExecutor.scheduleWithFixedDelay(this::checkAllServers, 2, 60, TimeUnit.SECONDS);
        }

        private void checkAllServers()
        {
            for (String[] preset : PRESET_SERVERS)
                checkServer(preset[1]);
        }

        /** Probe a server with a lightweight GetCapabilities request; update health dot colour. */
        private void checkServer(String serverUrl)
        {
            JLabel dot = serverHealthDots.get(serverUrl);
            if (dot == null)
                return;
            try
            {
                String probe = serverUrl
                    + (serverUrl.contains("?") ? "&" : "?")
                    + "SERVICE=WMS&REQUEST=GetCapabilities";
                HttpURLConnection conn = (HttpURLConnection) new URL(probe).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestMethod("HEAD");
                conn.connect();
                int code = conn.getResponseCode();
                Color c = (code >= 200 && code < 400) ? STATUS_GREEN : STATUS_YELLOW;
                SwingUtilities.invokeLater(() -> dot.setForeground(c));
            }
            catch (Exception e)
            {
                SwingUtilities.invokeLater(() -> dot.setForeground(STATUS_RED));
            }
        }

        /** Remove all currently enabled WMS layers from the globe. */
        private void clearLayers()
        {
            for (LayerEntry entry : allEntries)
            {
                if (entry.enabled && entry.component instanceof Layer layer)
                {
                    layer.setEnabled(false);
                    wwd.getModel().getLayers().remove(layer);
                }
                entry.enabled = false;
            }
            allEntries.clear();
            allCheckBoxes.clear();
            activeLayersPanel.clearAll();
            wwd.redraw();
        }

        private static Object createComponent(WMSCapabilities caps, AVList params)
        {
            AVList configParams = params.copy();
            configParams.setValue(AVKey.URL_CONNECT_TIMEOUT, 30000);
            configParams.setValue(AVKey.URL_READ_TIMEOUT, 30000);
            configParams.setValue(AVKey.RETRIEVAL_QUEUE_STALE_REQUEST_LIMIT, 60000);

            try
            {
                String factoryKey = getFactoryKeyForCapabilities(caps);
                Factory factory = (Factory) WorldWind.createConfigurationComponent(factoryKey);
                return factory.createFromConfigSource(caps, configParams);
            }
            catch (Exception e)
            {
                logger.log(java.util.logging.Level.WARNING, "Failed to create WMS component: " + e.getMessage());
            }
            return null;
        }

        private static String getFactoryKeyForCapabilities(WMSCapabilities caps)
        {
            boolean hasApplicationBilFormat = false;
            Set<String> formats = caps.getImageFormats();
            for (String s : formats)
            {
                if (s.contains("application/bil"))
                {
                    hasApplicationBilFormat = true;
                    break;
                }
            }
            return hasApplicationBilFormat ? AVKey.ELEVATION_MODEL_FACTORY : AVKey.LAYER_FACTORY;
        }

        private static String makeTitle(WMSCapabilities caps, LayerEntry entry)
        {
            String layerNames = entry.params.getStringValue(AVKey.LAYER_NAMES);
            String styleNames = entry.params.getStringValue(AVKey.STYLE_NAMES);
            String[] lNames = layerNames.split(",");
            String[] sNames = styleNames != null ? styleNames.split(",") : null;

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lNames.length; i++)
            {
                if (sb.length() > 0)
                    sb.append(", ");

                String layerName = lNames[i];
                WMSLayerCapabilities lc = caps.getLayerByName(layerName);
                if (lc == null)
                {
                    sb.append(layerName);
                    continue;
                }
                String layerTitle = lc.getTitle();
                sb.append(layerTitle != null ? layerTitle : layerName);

                if (sNames == null || sNames.length <= i)
                    continue;

                String styleName = sNames[i];
                WMSLayerStyle style = lc.getStyleByName(styleName);
                if (style == null)
                    continue;

                sb.append(" : ");
                String styleTitle = style.getTitle();
                sb.append(styleTitle != null ? styleTitle : styleName);
            }
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------
    //  ActiveLayersPanel - shows enabled layers with opacity sliders
    // ------------------------------------------------------------------
    protected static class ActiveLayersPanel extends JPanel
    {
        private static final long serialVersionUID = 1L;
        private final WorldWindow wwd;
        private final JPanel listPanel;
        private final Map<LayerEntry, JPanel> entryPanels = new LinkedHashMap<>();
        // Modified by seaglassfoundry.com - refresh status labels keyed by layer entry
        private final Map<LayerEntry, JLabel> refreshLabels = new LinkedHashMap<>();

        public ActiveLayersPanel(WorldWindow wwd)
        {
            super(new BorderLayout(0, 0));
            this.wwd = wwd;
            setBackground(BG_PANEL);
            setPreferredSize(new Dimension(350, 200));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 0, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));

            // Header
            JLabel header = createLabel("Active Layers", UI_FONT_BOLD);
            header.setBorder(BorderFactory.createEmptyBorder(8, 10, 6, 10));
            header.setOpaque(true);
            header.setBackground(BG_PANEL);
            add(header, BorderLayout.NORTH);

            listPanel = new JPanel();
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            listPanel.setBackground(BG_PANEL);

            JScrollPane scroll = new JScrollPane(listPanel);
            scroll.setBackground(BG_PANEL);
            scroll.getViewport().setBackground(BG_PANEL);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            add(scroll, BorderLayout.CENTER);
        }

        // Modified by seaglassfoundry.com - auto-refresh toggle for all layers, auto-enabled
        // when WMS advertises current="true" on its time dimension (live data indicator).
        public void addLayer(LayerEntry entry)
        {
            if (entryPanels.containsKey(entry))
                return;

            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setBackground(BG_PANEL);
            row.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            String title = entry.getTitle();
            JLabel nameLabel = createLabel(truncate(title != null ? title : "(unnamed)", 30), UI_FONT_SMALL);
            nameLabel.setToolTipText(title);

            JSlider opacitySlider = new JSlider(0, 100, 100);
            opacitySlider.setBackground(BG_PANEL);
            opacitySlider.setForeground(FG_PRIMARY);
            opacitySlider.setPreferredSize(new Dimension(150, 20));
            opacitySlider.setToolTipText("Opacity");
            opacitySlider.addChangeListener(e -> {
                double opacity = opacitySlider.getValue() / 100.0;
                if (entry.component instanceof Layer layer)
                {
                    layer.setOpacity(opacity);
                    wwd.redraw();
                }
            });

            JLabel pctLabel = createLabel("100%", UI_FONT_SMALL);
            pctLabel.setPreferredSize(new Dimension(36, 16));
            opacitySlider.addChangeListener(e -> pctLabel.setText(opacitySlider.getValue() + "%"));

            JPanel sliderRow = new JPanel(new BorderLayout(4, 0));
            sliderRow.setBackground(BG_PANEL);
            sliderRow.add(opacitySlider, BorderLayout.CENTER);
            sliderRow.add(pctLabel, BorderLayout.EAST);

            // Legend button
            JButton legendBtn = new JButton("Legend");
            legendBtn.setFont(UI_FONT_SMALL);
            legendBtn.setForeground(FG_SECONDARY);
            legendBtn.setBackground(BG_FIELD);
            legendBtn.setFocusPainted(false);
            legendBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
            legendBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            legendBtn.addActionListener(e -> fetchAndShowLegend(entry));

            // Refresh status label (shown when auto-refresh is active)
            JLabel refreshLabel = createLabel("", UI_FONT_SMALL);
            refreshLabel.setForeground(STATUS_GREEN);
            refreshLabels.put(entry, refreshLabel);

            // Modified by seaglassfoundry.com - auto-refresh toggle delegates to
            // WMSTiledImageLayer.setAutoRefresh() so refresh works even without this panel.
            boolean isWmsLayer = entry.component instanceof WMSTiledImageLayer;
            boolean autoStart = isWmsLayer && ((WMSTiledImageLayer) entry.component).getAutoRefresh();
            JButton refreshBtn = new JButton("Auto 5m");
            refreshBtn.setFont(new Font("Segoe UI", Font.BOLD, 9));
            refreshBtn.setFocusPainted(false);
            refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            refreshBtn.setToolTipText("Toggle auto-refresh (every 5 minutes)");
            refreshBtn.putClientProperty("refresh.active", autoStart);
            styleRefreshButton(refreshBtn, autoStart);
            refreshBtn.addActionListener(e -> {
                if (!(entry.component instanceof WMSTiledImageLayer wmsLayer))
                    return;
                boolean active = Boolean.TRUE.equals(refreshBtn.getClientProperty("refresh.active"));
                boolean next = !active;
                wmsLayer.setAutoRefresh(next);
                refreshBtn.putClientProperty("refresh.active", next);
                styleRefreshButton(refreshBtn, next);
                refreshLabel.setText(next ? "  Auto-refresh: 5 min" : "");
                row.revalidate();
                row.repaint();
            });

            // Top row: name + buttons
            JPanel topRow = new JPanel(new BorderLayout(4, 0));
            topRow.setBackground(BG_PANEL);
            topRow.add(nameLabel, BorderLayout.CENTER);

            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
            btnRow.setBackground(BG_PANEL);
            btnRow.setOpaque(false);

            // LIVE badge for layers that advertise current="true"
            if (isWmsLayer && ((WMSTiledImageLayer) entry.component).isLiveData())
            {
                JLabel liveBadge = new JLabel("LIVE");
                liveBadge.setFont(new Font("Segoe UI", Font.BOLD, 9));
                liveBadge.setForeground(new Color(255, 255, 255));
                liveBadge.setBackground(STATUS_GREEN);
                liveBadge.setOpaque(true);
                liveBadge.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
                btnRow.add(liveBadge);
            }
            btnRow.add(refreshBtn);
            btnRow.add(legendBtn);
            topRow.add(btnRow, BorderLayout.EAST);

            // Bottom area: slider + refresh status
            JPanel bottomRow = new JPanel(new BorderLayout(4, 0));
            bottomRow.setBackground(BG_PANEL);
            bottomRow.add(sliderRow, BorderLayout.CENTER);
            bottomRow.add(refreshLabel, BorderLayout.SOUTH);

            row.add(topRow, BorderLayout.NORTH);
            row.add(bottomRow, BorderLayout.CENTER);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

            entryPanels.put(entry, row);
            listPanel.add(row);
            listPanel.revalidate();
            listPanel.repaint();

            // Show refresh status if layer already has auto-refresh active (e.g., live data)
            if (autoStart)
                refreshLabel.setText("  Auto-refresh: 5 min");
        }

        private void styleRefreshButton(JButton btn, boolean active)
        {
            if (active)
            {
                btn.setForeground(new Color(255, 255, 255));
                btn.setBackground(STATUS_GREEN);
                btn.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
            }
            else
            {
                btn.setForeground(FG_SECONDARY);
                btn.setBackground(BG_FIELD);
                btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1),
                    BorderFactory.createEmptyBorder(1, 3, 1, 3)));
            }
        }

        /** Fetch GetLegendGraphic from the WMS server and display it in a dialog. */
        private void fetchAndShowLegend(LayerEntry entry)
        {
            String serviceUrl = entry.caps.getRequestURL("GetLegendGraphic", "HTTP", "get");
            if (serviceUrl == null)
                serviceUrl = entry.caps.getRequestURL("GetMap", "HTTP", "get");
            if (serviceUrl == null)
                return;
            String layerName = entry.params.getStringValue(AVKey.LAYER_NAMES);
            final String legendUrl = serviceUrl
                + (serviceUrl.contains("?") ? "&" : "?")
                + "SERVICE=WMS&REQUEST=GetLegendGraphic&VERSION=1.1.1"
                + "&LAYER=" + layerName + "&FORMAT=image/png";

            Thread t = new Thread(() -> {
                try
                {
                    URL url = new URL(legendUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.connect();
                    int responseCode = conn.getResponseCode();
                    if (responseCode != 200)
                    {
                        SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this,
                                "Server returned HTTP " + responseCode,
                                "Legend Unavailable", JOptionPane.WARNING_MESSAGE));
                        return;
                    }
                    BufferedImage img = javax.imageio.ImageIO.read(conn.getInputStream());
                    if (img == null)
                    {
                        SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this,
                                "Server did not return a valid image.",
                                "Legend Unavailable", JOptionPane.WARNING_MESSAGE));
                        return;
                    }
                    final BufferedImage finalImg = img;
                    SwingUtilities.invokeLater(() -> {
                        JLabel imgLabel = new JLabel(new ImageIcon(finalImg));
                        imgLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
                        imgLabel.setBackground(Color.WHITE);
                        imgLabel.setOpaque(true);
                        JScrollPane scroll = new JScrollPane(imgLabel);
                        scroll.setPreferredSize(new Dimension(
                            Math.min(finalImg.getWidth() + 32, 600),
                            Math.min(finalImg.getHeight() + 32, 400)));
                        JOptionPane.showMessageDialog(
                            SwingUtilities.getWindowAncestor(this),
                            scroll,
                            "Legend: " + truncate(entry.getTitle(), 50),
                            JOptionPane.PLAIN_MESSAGE);
                    });
                }
                catch (Exception ex)
                {
                    SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this,
                            "Failed to fetch legend: " + ex.getMessage(),
                            "Legend Error", JOptionPane.ERROR_MESSAGE));
                }
            });
            t.setDaemon(true);
            t.start();
        }

        public void removeLayer(LayerEntry entry)
        {
            refreshLabels.remove(entry);
            JPanel row = entryPanels.remove(entry);
            if (row != null)
            {
                listPanel.remove(row);
                listPanel.revalidate();
                listPanel.repaint();
            }
        }

        public void clearAll()
        {
            refreshLabels.clear();
            entryPanels.clear();
            listPanel.removeAll();
            listPanel.revalidate();
            listPanel.repaint();
        }
    }

    // ------------------------------------------------------------------
    //  TimeControlPanel - time slider and playback controls
    // ------------------------------------------------------------------
    @SuppressWarnings("serial")
    protected static class TimeControlPanel extends JPanel
    {
        private final JSlider timeSlider;
        private final JLabel timeLabel;
        private final JButton stepBackBtn;
        private final JButton playPauseBtn;
        private final JButton stepForwardBtn;
        private final JLabel speedLabel;
        private javax.swing.Timer animationTimer;
        private boolean playing = false;
        private int speedMultiplier = 1;

        // Time values parsed from dimension (as epoch millis)
        private final List<Long> timeValues = new ArrayList<>();

        // Listener notified when the selected time changes (receives ISO date string)
        private java.util.function.Consumer<String> timeChangeListener;

        public TimeControlPanel()
        {
            super(new BorderLayout(8, 0));
            setBackground(BG_DARK);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));

            // Time slider
            timeSlider = new JSlider(0, 100, 0);
            timeSlider.setBackground(BG_DARK);
            timeSlider.setForeground(FG_PRIMARY);
            timeSlider.setEnabled(false);
            timeSlider.addChangeListener(e -> updateTimeLabel());

            // Time label
            timeLabel = createLabel("No time dimension", UI_FONT_SMALL);
            timeLabel.setPreferredSize(new Dimension(180, 20));
            timeLabel.setHorizontalAlignment(SwingConstants.CENTER);

            // Buttons
            stepBackBtn = createSmallButton("|<");
            stepBackBtn.setEnabled(false);
            stepBackBtn.addActionListener(e -> stepTime(-1));

            playPauseBtn = createSmallButton(">");
            playPauseBtn.setEnabled(false);
            playPauseBtn.addActionListener(e -> togglePlayPause());

            stepForwardBtn = createSmallButton(">|");
            stepForwardBtn.setEnabled(false);
            stepForwardBtn.addActionListener(e -> stepTime(1));

            speedLabel = createLabel("1x", UI_FONT_SMALL);
            speedLabel.setPreferredSize(new Dimension(30, 20));
            speedLabel.setHorizontalAlignment(SwingConstants.CENTER);
            speedLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            speedLabel.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    cycleSpeed();
                }
            });

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
            buttonPanel.setBackground(BG_DARK);
            buttonPanel.add(stepBackBtn);
            buttonPanel.add(playPauseBtn);
            buttonPanel.add(stepForwardBtn);
            buttonPanel.add(Box.createHorizontalStrut(8));
            buttonPanel.add(speedLabel);

            add(timeSlider, BorderLayout.CENTER);
            add(timeLabel, BorderLayout.WEST);
            add(buttonPanel, BorderLayout.EAST);

            // Animation timer
            animationTimer = new javax.swing.Timer(1000, e -> stepTime(1));
            animationTimer.setRepeats(true);
        }

        /** Populate time controls from a dimension string like "2020-01-01/2020-12-31/P1D" or comma-separated dates. */
        public void setTimeDimension(String dimString)
        {
            timeValues.clear();
            if (dimString == null || dimString.trim().isEmpty())
            {
                disableControls();
                return;
            }

            // Parse comma-separated dates or interval
            try
            {
                if (dimString.contains("/"))
                {
                    // Interval format: start/end/period  or  start/end
                    String[] parts = dimString.trim().split("/");
                    if (parts.length >= 2)
                    {
                        long start = parseISODate(parts[0].trim());
                        long end = parseISODate(parts[1].trim());
                        long step = (parts.length >= 3) ? parsePeriod(parts[2].trim()) : 86400000L; // default 1 day
                        if (step <= 0)
                            step = 86400000L;
                        // Limit to 1000 steps to avoid memory issues
                        long count = (end - start) / step;
                        if (count > 1000)
                            step = (end - start) / 1000;
                        for (long t = start; t <= end; t += step)
                            timeValues.add(t);
                    }
                }
                else
                {
                    // Comma-separated
                    String[] parts = dimString.split(",");
                    for (String part : parts)
                    {
                        String s = part.trim();
                        if (!s.isEmpty())
                            timeValues.add(parseISODate(s));
                    }
                }
            }
            catch (Exception e)
            {
                // If parsing fails, just leave timeValues empty
                timeValues.clear();
            }

            if (timeValues.isEmpty())
            {
                disableControls();
                return;
            }

            Collections.sort(timeValues);
            timeSlider.setMinimum(0);
            timeSlider.setMaximum(timeValues.size() - 1);
            timeSlider.setValue(0);
            timeSlider.setEnabled(true);
            stepBackBtn.setEnabled(true);
            playPauseBtn.setEnabled(true);
            stepForwardBtn.setEnabled(true);
            updateTimeLabel();
        }

        private void disableControls()
        {
            timeSlider.setEnabled(false);
            stepBackBtn.setEnabled(false);
            playPauseBtn.setEnabled(false);
            stepForwardBtn.setEnabled(false);
            timeLabel.setText("No time dimension");
            if (animationTimer.isRunning())
                animationTimer.stop();
            playing = false;
            playPauseBtn.setText(">");
        }

        public void setTimeChangeListener(java.util.function.Consumer<String> listener)
        {
            this.timeChangeListener = listener;
        }

        /** Return an unmodifiable view of the parsed time values (epoch millis), or empty list. */
        public List<Long> getTimeValues()
        {
            return Collections.unmodifiableList(timeValues);
        }

        /** Return the current ISO date string for the slider position, or null. */
        public String getCurrentTimeString()
        {
            if (timeValues.isEmpty())
                return null;
            int idx = timeSlider.getValue();
            if (idx >= 0 && idx < timeValues.size())
            {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                return sdf.format(new java.util.Date(timeValues.get(idx)));
            }
            return null;
        }

        private void updateTimeLabel()
        {
            if (timeValues.isEmpty())
            {
                timeLabel.setText("No time dimension");
                return;
            }
            int idx = timeSlider.getValue();
            if (idx >= 0 && idx < timeValues.size())
            {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                String dateStr = sdf.format(new java.util.Date(timeValues.get(idx)));
                timeLabel.setText(dateStr);

                if (timeChangeListener != null)
                    timeChangeListener.accept(dateStr);
            }
        }

        private void stepTime(int direction)
        {
            int val = timeSlider.getValue() + direction;
            if (val < timeSlider.getMinimum())
                val = timeSlider.getMinimum();
            if (val > timeSlider.getMaximum())
            {
                val = timeSlider.getMinimum(); // wrap around
            }
            timeSlider.setValue(val);
        }

        private void togglePlayPause()
        {
            playing = !playing;
            if (playing)
            {
                playPauseBtn.setText("||");
                animationTimer.setDelay(1000 / speedMultiplier);
                animationTimer.start();
            }
            else
            {
                playPauseBtn.setText(">");
                animationTimer.stop();
            }
        }

        private void cycleSpeed()
        {
            if (speedMultiplier == 1)
                speedMultiplier = 2;
            else if (speedMultiplier == 2)
                speedMultiplier = 4;
            else if (speedMultiplier == 4)
                speedMultiplier = 8;
            else
                speedMultiplier = 1;

            speedLabel.setText(speedMultiplier + "x");
            if (animationTimer.isRunning())
                animationTimer.setDelay(1000 / speedMultiplier);
        }

        /** Parse an ISO 8601 date string to epoch millis (basic support). */
        private static long parseISODate(String s)
        {
            // Handle common formats: yyyy-MM-dd, yyyy-MM-ddTHH:mm:ss, yyyy-MM-ddTHH:mm:ssZ
            s = s.replace("Z", "").replace("z", "");
            try
            {
                if (s.length() == 10)
                {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    return sdf.parse(s).getTime();
                }
                else if (s.contains("T"))
                {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    return sdf.parse(s).getTime();
                }
                else
                {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    return sdf.parse(s).getTime();
                }
            }
            catch (java.text.ParseException e)
            {
                return 0;
            }
        }

        /** Parse an ISO 8601 duration like P1D, P1M, P1Y to millis (approximate). */
        private static long parsePeriod(String s)
        {
            if (s == null || !s.startsWith("P"))
                return 86400000L; // default to 1 day

            s = s.substring(1); // remove 'P'
            long total = 0;
            boolean inTime = false;
            StringBuilder num = new StringBuilder();

            for (char c : s.toCharArray())
            {
                if (c == 'T')
                {
                    inTime = true;
                    continue;
                }
                if (Character.isDigit(c) || c == '.')
                {
                    num.append(c);
                    continue;
                }

                double val = num.length() > 0 ? Double.parseDouble(num.toString()) : 0;
                num.setLength(0);

                if (!inTime)
                {
                    switch (c)
                    {
                        case 'Y': total += (long)(val * 365.25 * 86400000L); break;
                        case 'M': total += (long)(val * 30.44 * 86400000L); break;
                        case 'W': total += (long)(val * 7 * 86400000L); break;
                        case 'D': total += (long)(val * 86400000L); break;
                    }
                }
                else
                {
                    switch (c)
                    {
                        case 'H': total += (long)(val * 3600000L); break;
                        case 'M': total += (long)(val * 60000L); break;
                        case 'S': total += (long)(val * 1000L); break;
                    }
                }
            }
            return total > 0 ? total : 86400000L;
        }

        private static JButton createSmallButton(String text)
        {
            JButton btn = new JButton(text);
            btn.setFont(UI_FONT_BOLD);
            btn.setForeground(FG_PRIMARY);
            btn.setBackground(BG_PANEL);
            btn.setFocusPainted(false);
            btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setPreferredSize(new Dimension(40, 24));
            return btn;
        }
    }

    // ------------------------------------------------------------------
    //  UI helper methods
    // ------------------------------------------------------------------
    private static JLabel createLabel(String text, Font font)
    {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(FG_PRIMARY);
        return label;
    }

    private static JTextField createTextField(String text)
    {
        JTextField field = new JTextField(text);
        field.setFont(UI_FONT);
        field.setForeground(FG_PRIMARY);
        field.setBackground(BG_FIELD);
        field.setCaretColor(FG_PRIMARY);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        return field;
    }

    private static JButton createAccentButton(String text)
    {
        JButton btn = new JButton(text);
        btn.setFont(UI_FONT_BOLD);
        btn.setForeground(Color.WHITE);
        btn.setBackground(ACCENT);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(80, 28));
        btn.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e) { btn.setBackground(ACCENT_HOVER); }
            @Override
            public void mouseExited(MouseEvent e) { btn.setBackground(ACCENT); }
        });
        return btn;
    }

    // Modified by seaglassfoundry.com - hover respects active server selection state
    private static JButton createPresetButton(String text)
    {
        JButton btn = new JButton(text);
        btn.setFont(UI_FONT_SMALL);
        btn.setForeground(FG_PRIMARY);
        btn.setBackground(BG_FIELD);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                if (!Boolean.TRUE.equals(btn.getClientProperty("wms.active")))
                    btn.setBackground(ACCENT_HOVER);
            }
            @Override
            public void mouseExited(MouseEvent e)
            {
                if (!Boolean.TRUE.equals(btn.getClientProperty("wms.active")))
                    btn.setBackground(BG_FIELD);
            }
        });
        return btn;
    }

    private static String truncate(String s, int maxLen)
    {
        if (s == null)
            return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }

    /** Small filled-circle label used as a server health indicator. */
    private static JLabel createStatusDot(Color color)
    {
        JLabel dot = new JLabel("●");
        dot.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        dot.setForeground(color);
        dot.setToolTipText("Server status: unknown");
        return dot;
    }

    /** Preset button that shows a status dot on its left side. */
    private static JButton createPresetButtonWithDot(String text, JLabel dot)
    {
        JButton btn = new JButton();
        btn.setLayout(new BorderLayout(3, 0));
        btn.add(dot, BorderLayout.WEST);
        btn.add(new JLabel(text) {
            private static final long serialVersionUID = 1L;
            { setFont(UI_FONT_SMALL); setForeground(FG_PRIMARY); }
        }, BorderLayout.CENTER);
        btn.setFont(UI_FONT_SMALL);
        btn.setBackground(BG_FIELD);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // Modified by seaglassfoundry.com - hover respects active server selection state
        btn.addMouseListener(new MouseAdapter()
        {
            @Override public void mouseEntered(MouseEvent e)
            {
                if (!Boolean.TRUE.equals(btn.getClientProperty("wms.active")))
                    btn.setBackground(ACCENT_HOVER);
            }
            @Override public void mouseExited(MouseEvent e)
            {
                if (!Boolean.TRUE.equals(btn.getClientProperty("wms.active")))
                    btn.setBackground(BG_FIELD);
            }
        });
        return btn;
    }

    // ------------------------------------------------------------------
    //  main
    // ------------------------------------------------------------------
    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind WMS Explorer", ExplorerFrame.class);
    }
}
