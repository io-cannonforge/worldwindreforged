/*
 * WorldWind Reforged — Satellite Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New example: real-time satellite tracking at orbital altitude with SGP4 propagation,
 * altitude-coloured orbit paths, ground tracks, radio-horizon footprints, drop lines,
 * speed leaders, ISS quick-track, and live CelesTrak TLE data with offline fallback.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.time.Instant;
import java.util.Map;

import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.PointPlacemark;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwindx.examples.satellites.BundledTleDataSource;
import gov.nasa.worldwindx.examples.satellites.CelestrakDataSource;
import gov.nasa.worldwindx.examples.satellites.SatelliteDataSource;
import gov.nasa.worldwindx.examples.satellites.SatelliteFilterPanel;
import gov.nasa.worldwindx.examples.satellites.SatelliteManager;
import gov.nasa.worldwindx.examples.satellites.SatellitePosition;
import gov.nasa.worldwindx.examples.satellites.TleRecord;

/**
 * Real-time satellite tracking at orbital altitude with SGP4 propagation.
 * <p>
 * <b>Wow-factor features:</b>
 * <ul>
 *   <li>Satellites rendered at <b>actual 3D orbital altitude</b> (200 km to 36,000 km)</li>
 *   <li><b>Full orbit ellipses</b> with altitude-gradient colouring: LEO(cyan) → MEO(green) → GEO(gold)</li>
 *   <li><b>Ground track</b> projection showing the subsatellite path on the globe surface</li>
 *   <li><b>Radio-horizon footprint circles</b> showing each satellite's ground coverage</li>
 *   <li><b>Vertical drop lines</b> from satellite to subsatellite point</li>
 *   <li><b>Speed leader lines</b> showing projected 60-second-ahead position</li>
 *   <li><b>Self-contained SGP4 propagator</b> — no external orbital mechanics library required</li>
 *   <li><b>9 satellite categories</b> with distinct icons: stations, GNSS, weather, comm, etc.</li>
 *   <li><b>"Track ISS" button</b> with follow camera</li>
 *   <li><b>Rich detail popup</b> with orbital parameters (inclination, period, apogee/perigee)</li>
 *   <li><b>Live CelesTrak data</b> with automatic offline fallback</li>
 *   <li><b>UTC clock</b> and live satellite count statistics</li>
 *   <li><b>Level-of-detail</b> rendering based on eye altitude</li>
 * </ul>
 *
 * <p>TLE data from <a href="https://celestrak.org">CelesTrak</a> — free, no API key.</p>
 *
 * seaglassfoundry.com — new showcase example for WorldWind Reforged
 */
public class SatelliteTrackerDemo extends ApplicationTemplate
{
    /** TLE fetch interval (30 minutes — TLEs update infrequently). */
    private static final int TLE_FETCH_INTERVAL_MS = 30 * 60 * 1000;
    /** Propagation tick interval (1 second). */
    private static final int PROPAGATION_MS = 1000;

    // ── AppFrame ──────────────────────────────────────────────────────────────

    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;

        private SatelliteDataSource dataSource;
        private SatelliteManager manager;
        private SatelliteFilterPanel filterPanel;

        private Timer fetchTimer;
        private Timer propagationTimer;


        public AppFrame()
        {
            super(true, true, false);

            // ── Layers (bottom to top) ────────────────────────────────────
            RenderableLayer groundTrackLayer = new RenderableLayer();
            groundTrackLayer.setName("Ground Tracks");
            groundTrackLayer.setPickEnabled(false);

            RenderableLayer footprintLayer = new RenderableLayer();
            footprintLayer.setName("Footprints");
            footprintLayer.setPickEnabled(false);

            RenderableLayer orbitLayer = new RenderableLayer();
            orbitLayer.setName("Orbits");
            orbitLayer.setPickEnabled(false);

            RenderableLayer dropLineLayer = new RenderableLayer();
            dropLineLayer.setName("Drop Lines");
            dropLineLayer.setPickEnabled(false);

            RenderableLayer leaderLayer = new RenderableLayer();
            leaderLayer.setName("Speed Leaders");
            leaderLayer.setPickEnabled(false);

            RenderableLayer satelliteLayer = new RenderableLayer();
            satelliteLayer.setName("Satellites");

            insertBeforeCompass(getWwd(), groundTrackLayer);
            insertBeforeCompass(getWwd(), footprintLayer);
            insertBeforeCompass(getWwd(), orbitLayer);
            insertBeforeCompass(getWwd(), dropLineLayer);
            insertBeforeCompass(getWwd(), leaderLayer);
            insertBeforeCompass(getWwd(), satelliteLayer);

            manager = new SatelliteManager(getWwd(), satelliteLayer, orbitLayer,
                groundTrackLayer, footprintLayer, dropLineLayer, leaderLayer);

            // ── Filter panel ──────────────────────────────────────────────
            filterPanel = new SatelliteFilterPanel(manager, this::followSatellite);
            installControlPanel(filterPanel);

            // ── Click-for-details ─────────────────────────────────────────
            getWwd().addSelectListener(new SatelliteSelectListener());

            // ── Initial view: high altitude to see orbit paths ────────────
            getWwd().getView().setEyePosition(
                Position.fromDegrees(20.0, 0.0, 15_000_000));

            // ── Start with live data, fall back to bundled ────────────────
            dataSource = new CelestrakDataSource();

            // ── Initial TLE fetch ─────────────────────────────────────────
            fetchTLEsAsync();

            // ── TLE refetch timer ─────────────────────────────────────────
            fetchTimer = new Timer(TLE_FETCH_INTERVAL_MS, e -> fetchTLEsAsync());
            fetchTimer.start();

            // ── Propagation timer (1 second ticks) ────────────────────────
            propagationTimer = new Timer(PROPAGATION_MS, e ->
            {
                manager.propagateTick(Instant.now());

                // Follow camera
                Position followPos = manager.getFollowPosition();
                if (followPos != null)
                {
                    getWwd().getView().setEyePosition(
                        Position.fromDegrees(followPos.getLatitude().degrees,
                            followPos.getLongitude().degrees,
                            followPos.getElevation() + 2_000_000));
                }
            });
            propagationTimer.start();
        }

        // ── TLE fetching ──────────────────────────────────────────────────

        private void fetchTLEsAsync()
        {
            new SwingWorker<Map<Integer, TleRecord>, Void>()
            {
                @Override
                protected Map<Integer, TleRecord> doInBackground()
                {
                    return dataSource.fetchTLEs();
                }

                @Override
                protected void done()
                {
                    try
                    {
                        Map<Integer, TleRecord> tles = get();

                        // Fall back to bundled data if live fetch returned empty
                        if (tles.isEmpty() && dataSource.isLive())
                        {
                            Logging.logger().info("Live fetch returned empty, switching to bundled TLEs");
                            dataSource = new BundledTleDataSource();
                            tles = dataSource.fetchTLEs();
                        }

                        if (!tles.isEmpty())
                        {
                            manager.updateTLEs(tles);
                            filterPanel.setDataSourceLabel(dataSource.getSourceLabel(),
                                dataSource.isLive());
                            filterPanel.updateStats();

                            // Compute orbits in background
                            computeOrbitsAsync();
                        }
                    }
                    catch (Exception ex)
                    {
                        Logging.logger().warning("TLE fetch failed: " + ex.getMessage());
                    }
                }
            }.execute();
        }

        private void computeOrbitsAsync()
        {
            Instant now = Instant.now();
            new SwingWorker<Void, Void>()
            {
                @Override
                protected Void doInBackground()
                {
                    manager.computeOrbits(now);
                    return null;
                }

                @Override
                protected void done()
                {
                    getWwd().redraw();
                }
            }.execute();
        }

        // ── Follow camera ─────────────────────────────────────────────────

        private void followSatellite(int noradId)
        {
            manager.setFollowId(noradId);
            // Immediately jump to satellite position
            SatellitePosition sp = manager.getPosition(noradId);
            if (sp != null)
            {
                getWwd().getView().setEyePosition(
                    Position.fromDegrees(sp.getLatDeg(), sp.getLonDeg(),
                        sp.getAltitudeMeters() + 2_000_000));
            }
        }

        // ── Click-for-details ─────────────────────────────────────────────

        private class SatelliteSelectListener implements SelectListener
        {
            @Override
            public void selected(SelectEvent event)
            {
                if (event.getEventAction().equals(SelectEvent.LEFT_CLICK))
                {
                    if (event.hasObjects() && event.getTopObject() instanceof PointPlacemark pm)
                    {
                        Object idObj = pm.getValue("noradId");
                        if (idObj instanceof Integer id)
                        {
                            showDetail(id, pm.getPosition());
                            return;
                        }
                    }
                    hideDetail();
                }
            }
        }

        private void showDetail(int noradId, Position pos)
        {
            filterPanel.showDetail(noradId);
        }

        private void hideDetail()
        {
            filterPanel.hideDetail();
        }

        // ── Control panel setup ───────────────────────────────────────────

        private void installControlPanel(SatelliteFilterPanel panel)
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
            tabs.addTab("Satellites", filterScroll);
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
        ApplicationTemplate.start("WorldWind \u2014 Satellite Tracker", AppFrame.class);
    }
}
