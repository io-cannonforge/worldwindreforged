/*
 * WorldWind Reforged — Air Traffic Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New example: real-time 3D air traffic visualization using ADS-B data from
 * airplanes.live (free, no API key). Aircraft rendered at actual altitude with
 * contrails, speed leaders, altitude colouring, emergency alerts, and follow camera.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Map;

import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.PointPlacemark;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwindx.examples.airtraffic.AircraftFilterPanel;
import gov.nasa.worldwindx.examples.airtraffic.AircraftManager;
import gov.nasa.worldwindx.examples.airtraffic.AircraftPosition;
import gov.nasa.worldwindx.examples.airtraffic.AirplanesLiveDataSource;
import gov.nasa.worldwindx.examples.airtraffic.AirTrafficDataSource;

/**
 * Real-time 3D air traffic visualization using live ADS-B data.
 * <p>
 * <b>Wow-factor features:</b>
 * <ul>
 *   <li>Aircraft rendered at <b>actual 3D altitude</b> with vertical drop lines to ground</li>
 *   <li><b>Altitude colour spectrum</b>: ground(green) → low(gold) → cruise(blue) → high(violet)</li>
 *   <li><b>3D contrail paths</b> at altitude with per-vertex altitude colouring and age fading</li>
 *   <li><b>Speed leader lines</b> showing projected position 60 seconds ahead</li>
 *   <li><b>Smooth interpolation</b> between API updates for fluid aircraft movement</li>
 *   <li><b>Emergency alerts</b>: squawk 7500/7600/7700 aircraft pulse red</li>
 *   <li><b>Military aircraft</b> toggle using dedicated API endpoint</li>
 *   <li><b>Click-to-follow camera</b> that tracks a selected aircraft</li>
 *   <li><b>Viewport-aware loading</b>: queries aircraft within the visible area</li>
 *   <li><b>Rich detail popup</b> showing type, operator, altitude, speed, squawk</li>
 *   <li><b>Live statistics</b>: total, airborne, military, emergency counts</li>
 * </ul>
 *
 * <p>Data from <a href="https://airplanes.live">airplanes.live</a> — free, no API key.</p>
 *
 * seaglassfoundry.com — new showcase example for WorldWind Reforged
 */
public class AirTrafficDemo extends ApplicationTemplate
{
    /** Data fetch interval (ms). airplanes.live rate-limits aggressively; 10s is safe. */
    private static final int FETCH_INTERVAL_MS = 10_000;
    /** Smooth interpolation tick interval (ms). */
    private static final int INTERPOLATION_MS = 200;
    // ── AppFrame ──────────────────────────────────────────────────────────────

    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;

        private final AirTrafficDataSource dataSource = new AirplanesLiveDataSource();
        private AircraftManager manager;
        private AircraftFilterPanel filterPanel;

        private Timer fetchTimer;
        private Timer interpolationTimer;
        private long lastFetchTime = 0;

        private boolean showMilitaryGlobal = false;

        public AppFrame()
        {
            super(true, true, false);

            // ── Layers (bottom to top) ────────────────────────────────────
            RenderableLayer dropLineLayer = new RenderableLayer();
            dropLineLayer.setName("Drop Lines");
            dropLineLayer.setPickEnabled(false);

            RenderableLayer trailLayer = new RenderableLayer();
            trailLayer.setName("Contrails");
            trailLayer.setPickEnabled(false);

            RenderableLayer leaderLayer = new RenderableLayer();
            leaderLayer.setName("Speed Leaders");
            leaderLayer.setPickEnabled(false);

            RenderableLayer aircraftLayer = new RenderableLayer();
            aircraftLayer.setName("Aircraft");

            insertBeforeCompass(getWwd(), dropLineLayer);
            insertBeforeCompass(getWwd(), trailLayer);
            insertBeforeCompass(getWwd(), leaderLayer);
            insertBeforeCompass(getWwd(), aircraftLayer);

            manager = new AircraftManager(getWwd(), aircraftLayer, trailLayer, leaderLayer, dropLineLayer);

            // ── Filter panel ──────────────────────────────────────────────
            filterPanel = new AircraftFilterPanel(manager, this::toggleMilitaryGlobal);
            installControlPanel(filterPanel);

            // ── Click-for-details ─────────────────────────────────────────
            getWwd().addSelectListener(new AircraftSelectListener());

            // ── Initial view: somewhere interesting (New York area) ───────
            getWwd().getView().setEyePosition(
                Position.fromDegrees(40.7, -74.0, 300_000));

            // ── Fetch timer ───────────────────────────────────────────────
            fetchTimer = new Timer(FETCH_INTERVAL_MS, e -> fetchAsync());
            fetchTimer.setInitialDelay(500);
            fetchTimer.start();

            // ── Interpolation timer for smooth movement ───────────────────
            interpolationTimer = new Timer(INTERPOLATION_MS, e ->
            {
                double elapsed = System.currentTimeMillis() - lastFetchTime;
                double fraction = elapsed / FETCH_INTERVAL_MS;
                manager.interpolateTick(fraction);
            });
            interpolationTimer.start();

        }

        // ── Data fetching ─────────────────────────────────────────────────

        private void fetchAsync()
        {
            // Get the view centre for viewport-aware queries
            Position eye = getWwd().getView().getEyePosition();
            double lat = eye.getLatitude().degrees;
            double lon = eye.getLongitude().degrees;

            // Scale radius by altitude — wider view = larger query
            double eyeAlt = eye.getElevation();
            int radius = (int) Math.min(150, Math.max(30, eyeAlt / 5000));

            new SwingWorker<Map<String, AircraftPosition>, Void>()
            {
                @Override
                protected Map<String, AircraftPosition> doInBackground()
                {
                    // Copy into mutable map in case source returns unmodifiable
                    Map<String, AircraftPosition> result =
                        new java.util.HashMap<>(dataSource.fetchAircraft(lat, lon, radius));
                    return result;
                }

                @Override
                protected void done()
                {
                    try
                    {
                        Map<String, AircraftPosition> aircraft = get();
                        lastFetchTime = System.currentTimeMillis();
                        manager.updateAircraft(aircraft);
                        filterPanel.updateStats();
                    }
                    catch (Exception ex)
                    {
                        Logging.logger().warning("Air traffic fetch failed: " + ex.getMessage());
                    }
                }
            }.execute();
        }

        // ── Military global toggle ────────────────────────────────────────

        private Timer militaryTimer;

        private void toggleMilitaryGlobal(boolean enabled)
        {
            showMilitaryGlobal = enabled;
            if (enabled)
            {
                if (militaryTimer == null)
                {
                    militaryTimer = new Timer(15_000, e -> fetchMilitaryAsync());
                    militaryTimer.setInitialDelay(2000); // delay to avoid rate limit with main fetch
                    militaryTimer.start();
                }
            }
            else if (militaryTimer != null)
            {
                militaryTimer.stop();
                militaryTimer = null;
            }
        }

        private void fetchMilitaryAsync()
        {
            if (!showMilitaryGlobal) return;
            new SwingWorker<Map<String, AircraftPosition>, Void>()
            {
                @Override
                protected Map<String, AircraftPosition> doInBackground()
                {
                    return dataSource.fetchMilitary();
                }

                @Override
                protected void done()
                {
                    try
                    {
                        Map<String, AircraftPosition> mil = get();
                        if (!mil.isEmpty())
                        {
                            manager.updateAircraft(mil);
                            filterPanel.updateStats();
                        }
                    }
                    catch (Exception ex)
                    {
                        Logging.logger().warning("Military fetch failed: " + ex.getMessage());
                    }
                }
            }.execute();
        }

        // ── Click-for-details ─────────────────────────────────────────────

        private class AircraftSelectListener implements SelectListener
        {
            @Override
            public void selected(SelectEvent event)
            {
                if (event.getEventAction().equals(SelectEvent.LEFT_CLICK))
                {
                    if (event.hasObjects() && event.getTopObject() instanceof PointPlacemark pm)
                    {
                        Object hexObj = pm.getValue("hex");
                        if (hexObj instanceof String hex)
                        {
                            showDetail(hex, pm.getPosition());
                            return;
                        }
                    }
                    hideDetail();
                }
            }
        }

        private void showDetail(String hex, Position pos)
        {
            filterPanel.showDetail(hex);
        }

        private void hideDetail()
        {
            filterPanel.hideDetail();
        }

        // ── Control panel setup ───────────────────────────────────────────

        private void installControlPanel(AircraftFilterPanel panel)
        {
            if (this.controlPanel == null) return;

            this.getContentPane().remove(this.controlPanel);
            this.getContentPane().remove(this.wwjPanel);

            JTabbedPane tabs = new JTabbedPane();
            tabs.setBackground(new Color(45, 45, 48));

            JScrollPane layerScroll = new JScrollPane(this.layerPanel);
            layerScroll.setBorder(null);
            tabs.addTab("Layers", layerScroll);

            JScrollPane filterScroll = new JScrollPane(panel);
            filterScroll.setBorder(null);
            tabs.addTab("Air Traffic", filterScroll);
            tabs.setSelectedIndex(1);

            this.controlPanel.add(tabs, BorderLayout.CENTER);

            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                this.wwjPanel, this.controlPanel);
            splitPane.setResizeWeight(0.67);
            splitPane.setDividerSize(5);
            splitPane.setContinuousLayout(true);
            this.getContentPane().add(splitPane, BorderLayout.CENTER);

            this.addComponentListener(new java.awt.event.ComponentAdapter()
            {
                private boolean initialized;
                @Override
                public void componentResized(java.awt.event.ComponentEvent e)
                {
                    if (!initialized)
                    {
                        splitPane.setDividerLocation(getWidth() * 2 / 3);
                        initialized = true;
                    }
                }
            });
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind — Live Air Traffic", AppFrame.class);
    }
}
