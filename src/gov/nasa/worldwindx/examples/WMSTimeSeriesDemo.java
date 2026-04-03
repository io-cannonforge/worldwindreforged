/*
 * WorldWind Reforged — WMSTimeSeriesDemo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New example demonstrating Phase 2 WMS time-series animation with NASA GIBS.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.avlist.AVListImpl;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.ogc.wms.WMSCapabilities;
import gov.nasa.worldwind.ogc.wms.WMSLayerCapabilities;
import gov.nasa.worldwind.util.Level;
import gov.nasa.worldwind.util.LevelSet;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.wms.WMSTiledImageLayer;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Demonstrates Phase 2 WMS time-series animation using NASA GIBS.
 * <p>
 * On startup the application connects to the NASA GIBS WMS endpoint and offers
 * four preset time-aware layers:
 * <ul>
 *   <li>MODIS Terra True Color (daily, 2000-02-24 – present)</li>
 *   <li>VIIRS S-NPP True Color (daily, 2012-01-19 – present)</li>
 *   <li>MODIS Terra Thermal Anomalies (fires, daily)</li>
 *   <li>MODIS Terra Snow Cover (daily)</li>
 * </ul>
 * The time slider steps through all 366 days of 2024. Playback speed is
 * selectable (1×, 2×, 4× — one animation frame per second per speed step).
 * <p>
 * This example reuses the {@link WMSTiledImageLayer.URLBuilder} time-string
 * mechanism introduced in Phase 2: each time step sets the ISO date string on
 * every level's URL builder and calls {@code setExpiryTime} to flush cached tiles.
 *
 * @see WMSTiledImageLayer
 *
 * seaglassfoundry.com — new example for WorldWind Reforged Phase 2
 */
public class WMSTimeSeriesDemo extends ApplicationTemplate
{
    private static final String GIBS_URL = "https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi";

    /** Preset layers: [display name, WMS layer name] */
    private static final String[][] PRESETS = {
        {"MODIS Terra True Color",  "MODIS_Terra_CorrectedReflectance_TrueColor"},
        {"VIIRS S-NPP True Color",  "VIIRS_SNPP_CorrectedReflectance_TrueColor"},
        {"MODIS Terra Fires",       "MODIS_Terra_Thermal_Anomalies_Day"},
        {"MODIS Snow Cover",        "MODIS_Terra_Snow_Cover"},
    };

    /** All 366 days of 2024 (leap year) as ISO-8601 date strings. */
    private static List<String> buildDateList()
    {
        List<String> dates = new ArrayList<>(366);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(2024, Calendar.JANUARY, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Calendar end = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        end.set(2024, Calendar.DECEMBER, 31, 0, 0, 0);
        while (!cal.after(end)) {
            dates.add(sdf.format(cal.getTime()));
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return Collections.unmodifiableList(dates);
    }

    // ── AppFrame ──────────────────────────────────────────────────────────────

    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;
        private static final List<String> DATES = buildDateList();
        private static final int[] SPEEDS = {1, 2, 4};  // animation frames per second

        private WMSCapabilities gibsCaps;
        private final WMSTiledImageLayer[] layers = new WMSTiledImageLayer[PRESETS.length];

        private int selectedPreset = 0;
        private int timeIndex      = 180;  // start mid-year

        // ── Controls (assigned in buildControlPanel, used across methods) ─────
        private JLabel         statusLabel;
        private JComboBox<String> layerCombo;
        private JSlider        opacitySlider;
        private JLabel         dateLabel;
        private JSlider        timeSlider;
        private JButton        stepBackBtn;
        private JButton        playPauseBtn;
        private JButton        stepFwdBtn;
        private JButton        speedBtn;

        private final javax.swing.Timer animTimer;
        private boolean playing  = false;
        private int     speedIdx = 0;

        public AppFrame()
        {
            super(true, true, false);

            animTimer = new javax.swing.Timer(1000, e -> stepTime(1));

            getWwd().getView().setEyePosition(Position.fromDegrees(20.0, 0.0, 20_000_000));

            JPanel timeControls = buildControlPanel();

            // Modified by seaglassfoundry.com - put the layers panel and controls panel in a
            // tabbed pane so they don't overlap. Each tab gets a scroll pane for small windows.
            // Use a split pane between the map and the side panel so it can be resized.
            if (this.controlPanel != null)
            {
                this.getContentPane().remove(this.controlPanel);
                this.getContentPane().remove(this.wwjPanel);

                JTabbedPane tabs = new JTabbedPane();
                tabs.setBackground(new java.awt.Color(45, 45, 48));

                JScrollPane layerScroll = new JScrollPane(this.layerPanel);
                layerScroll.setBorder(null);
                tabs.addTab("Layers", layerScroll);

                JScrollPane controlScroll = new JScrollPane(timeControls);
                controlScroll.setBorder(null);
                tabs.addTab("Controls", controlScroll);

                this.controlPanel.add(tabs, BorderLayout.CENTER);

                JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    this.wwjPanel, this.controlPanel);
                splitPane.setResizeWeight(0.67);
                splitPane.setDividerSize(5);
                splitPane.setContinuousLayout(true);
                this.getContentPane().add(splitPane, BorderLayout.CENTER);

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
            }

            loadCapsAsync();
        }

        // ── Capabilities loading ──────────────────────────────────────────────

        private void loadCapsAsync()
        {
            setStatus("Connecting to NASA GIBS…");
            setControlsEnabled(false);

            new SwingWorker<WMSCapabilities, Void>()
            {
                @Override
                protected WMSCapabilities doInBackground() throws Exception
                {
                    WMSCapabilities caps = WMSCapabilities.retrieve(new java.net.URI(GIBS_URL));
                    caps.parse();
                    return caps;
                }

                @Override
                protected void done()
                {
                    try {
                        gibsCaps = get();
                        createLayers();
                        setStatus("Ready — " + DATES.size() + " daily dates (2024)");
                        setControlsEnabled(true);
                        applyTime();
                    } catch (Exception ex) {
                        Logging.logger().warning("WMSTimeSeriesDemo: GIBS load failed: " + ex.getMessage());
                        setStatus("Connection failed — check network.");
                    }
                }
            }.execute();
        }

        private void createLayers()
        {
            for (int i = 0; i < PRESETS.length; i++) {
                try {
                    String wmsName = PRESETS[i][1];
                    WMSLayerCapabilities layerCaps = gibsCaps.getLayerByName(wmsName);
                    if (layerCaps == null) {
                        Logging.logger().warning("WMSTimeSeriesDemo: layer not found in caps: " + wmsName);
                        continue;
                    }

                    AVList params = new AVListImpl();
                    params.setValue(AVKey.LAYER_NAMES, wmsName);
                    params.setValue(AVKey.DATA_CACHE_NAME, "WMSTimeSeriesDemo/" + wmsName);
                    params.setValue(AVKey.URL_CONNECT_TIMEOUT, 30000);
                    params.setValue(AVKey.URL_READ_TIMEOUT, 30000);
                    params.setValue(AVKey.RETRIEVAL_QUEUE_STALE_REQUEST_LIMIT, 60000);

                    AVList processed = WMSTiledImageLayer.wmsGetParamsFromCapsDoc(gibsCaps, params);
                    layers[i] = new WMSTiledImageLayer(processed);
                    layers[i].setName(PRESETS[i][0]);
                    layers[i].setOpacity(opacitySlider.getValue() / 100.0);
                    layers[i].setEnabled(i == 0);

                    insertBeforePlacenames(getWwd(), layers[i]);
                } catch (Exception ex) {
                    Logging.logger().warning(
                        "WMSTimeSeriesDemo: failed to create layer " + PRESETS[i][0] + ": " + ex.getMessage());
                }
            }
        }

        // ── Time management ───────────────────────────────────────────────────

        private void applyTime()
        {
            WMSTiledImageLayer layer = layers[selectedPreset];
            if (layer == null) return;

            String isoDate = DATES.get(timeIndex);
            dateLabel.setText(isoDate);
            setLayerTime(layer, isoDate);
            getWwd().redraw();
        }

        private static void setLayerTime(WMSTiledImageLayer layer, String isoDate)
        {
            LevelSet levels = layer.getLevels();
            if (levels == null) return;
            for (int i = 0; i < levels.getNumLevels(); i++) {
                Level lv = levels.getLevel(i);
                if (lv.getUrlBuilder() instanceof WMSTiledImageLayer.URLBuilder ub) {
                    ub.setTimeString(isoDate);
                    ub.URLTemplate = null;  // force URL string rebuild
                }
            }
            layer.setExpiryTime(System.currentTimeMillis());
        }

        private void stepTime(int delta)
        {
            timeIndex = Math.floorMod(timeIndex + delta, DATES.size());
            timeSlider.setValue(timeIndex);  // triggers changeListener → applyTime
        }

        // ── Layer switching ───────────────────────────────────────────────────

        private void switchLayer(int newIdx)
        {
            if (layers[selectedPreset] != null)
                layers[selectedPreset].setEnabled(false);
            selectedPreset = newIdx;
            if (layers[selectedPreset] != null) {
                layers[selectedPreset].setOpacity(opacitySlider.getValue() / 100.0);
                layers[selectedPreset].setEnabled(true);
            }
            applyTime();
        }

        // ── Playback ──────────────────────────────────────────────────────────

        private void togglePlay()
        {
            playing = !playing;
            playPauseBtn.setText(playing ? "||" : ">");
            if (playing) {
                animTimer.setDelay(1000 / SPEEDS[speedIdx]);
                animTimer.start();
            } else {
                animTimer.stop();
            }
        }

        private void cycleSpeed()
        {
            speedIdx = (speedIdx + 1) % SPEEDS.length;
            speedBtn.setText(SPEEDS[speedIdx] + "x");
            if (animTimer.isRunning())
                animTimer.setDelay(1000 / SPEEDS[speedIdx]);
        }

        // ── Control panel ─────────────────────────────────────────────────────

        private JPanel buildControlPanel()
        {
            JPanel root = new JPanel();
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            root.setBackground(WWStyle.BG_DARK);

            // Status label
            statusLabel = WWStyle.label("Initializing…", false);
            statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            root.add(statusLabel);
            root.add(vgap(WWStyle.GAP_XS));

            // ── Layer section ─────────────────────────────────────────────────
            JPanel layerSection = new JPanel();
            layerSection.setLayout(new BoxLayout(layerSection, BoxLayout.Y_AXIS));
            layerSection.setBackground(WWStyle.BG_DARK);
            layerSection.setBorder(WWStyle.sectionBorder("Layer"));

            String[] names = Arrays.stream(PRESETS).map(p -> p[0]).toArray(String[]::new);
            layerCombo = WWStyle.comboBox(names);
            layerCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
            layerCombo.setEnabled(false);
            layerCombo.addActionListener(e -> {
                if (layerCombo.isEnabled())
                    switchLayer(layerCombo.getSelectedIndex());
            });

            opacitySlider = WWStyle.slider(0, 100, 90);
            opacitySlider.setAlignmentX(Component.LEFT_ALIGNMENT);
            opacitySlider.setEnabled(false);
            opacitySlider.setToolTipText("Layer opacity");
            opacitySlider.addChangeListener(e -> {
                WMSTiledImageLayer l = layers[selectedPreset];
                if (l != null) l.setOpacity(opacitySlider.getValue() / 100.0);
                getWwd().redraw();
            });

            layerSection.add(layerCombo);
            layerSection.add(vgap(WWStyle.GAP_XS));
            layerSection.add(WWStyle.label("Opacity:", false));
            layerSection.add(opacitySlider);
            root.add(layerSection);
            root.add(vgap(WWStyle.GAP_XS));

            // ── Time section ──────────────────────────────────────────────────
            JPanel timeSection = new JPanel();
            timeSection.setLayout(new BoxLayout(timeSection, BoxLayout.Y_AXIS));
            timeSection.setBackground(WWStyle.BG_DARK);
            timeSection.setBorder(WWStyle.sectionBorder("Time (2024)"));

            dateLabel = new JLabel(DATES.get(timeIndex));
            dateLabel.setFont(WWStyle.FONT_BOLD);
            dateLabel.setForeground(WWStyle.ACCENT);
            dateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            timeSlider = WWStyle.slider(0, DATES.size() - 1, timeIndex);
            timeSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
            timeSlider.setEnabled(false);
            timeSlider.addChangeListener(e -> {
                if (timeSlider.isEnabled()) {
                    timeIndex = timeSlider.getValue();
                    applyTime();
                }
            });

            stepBackBtn  = WWStyle.flatButton("|<");
            playPauseBtn = WWStyle.flatButton(">");
            stepFwdBtn   = WWStyle.flatButton(">|");
            speedBtn     = WWStyle.flatButton("1x");

            stepBackBtn.setToolTipText("Step back one day");
            playPauseBtn.setToolTipText("Play / pause");
            stepFwdBtn.setToolTipText("Step forward one day");
            speedBtn.setToolTipText("Cycle animation speed");

            stepBackBtn.addActionListener(e -> stepTime(-1));
            playPauseBtn.addActionListener(e -> togglePlay());
            stepFwdBtn.addActionListener(e -> stepTime(1));
            speedBtn.addActionListener(e -> cycleSpeed());

            for (JButton b : new JButton[]{stepBackBtn, playPauseBtn, stepFwdBtn, speedBtn})
                b.setEnabled(false);

            JPanel playRow = new JPanel(new FlowLayout(FlowLayout.LEFT, WWStyle.GAP_XS, 0));
            playRow.setBackground(WWStyle.BG_DARK);
            playRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            playRow.add(stepBackBtn);
            playRow.add(playPauseBtn);
            playRow.add(stepFwdBtn);
            playRow.add(speedBtn);

            timeSection.add(dateLabel);
            timeSection.add(vgap(WWStyle.GAP_XS));
            timeSection.add(timeSlider);
            timeSection.add(playRow);
            root.add(timeSection);

            return root;
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private void setStatus(String msg)
        {
            SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
        }

        private void setControlsEnabled(boolean enabled)
        {
            SwingUtilities.invokeLater(() -> {
                layerCombo.setEnabled(enabled);
                timeSlider.setEnabled(enabled);
                opacitySlider.setEnabled(enabled);
                stepBackBtn.setEnabled(enabled);
                playPauseBtn.setEnabled(enabled);
                stepFwdBtn.setEnabled(enabled);
                speedBtn.setEnabled(enabled);
            });
        }

        private static JPanel vgap(int height)
        {
            JPanel p = new JPanel();
            p.setBackground(WWStyle.BG_DARK);
            p.setPreferredSize(new Dimension(0, height));
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            return p;
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind — WMS Time Series (GIBS)", AppFrame.class);
    }
}
