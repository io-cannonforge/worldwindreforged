/*
 * WorldWind Reforged — AIS Vessel Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New example: real-time AIS maritime vessel tracking with proper symbology,
 * interactive filtering, track history, and click-for-details.
 * AIS data provided by Fintraffic / digitraffic.fi, licensed under CC 4.0 BY.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Map;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.pick.PickedObject;
import gov.nasa.worldwind.render.PointPlacemark;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwindx.examples.ais.AISDataSource;
import gov.nasa.worldwindx.examples.ais.CsvReplayDataSource;
import gov.nasa.worldwindx.examples.ais.DigittrafficDataSource;
import gov.nasa.worldwindx.examples.ais.VesselFilterPanel;
import gov.nasa.worldwindx.examples.ais.VesselInfo;
import gov.nasa.worldwindx.examples.ais.VesselManager;
import gov.nasa.worldwindx.examples.ais.VesselPosition;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Real-time AIS maritime vessel tracker using Finland's open Digitraffic API,
 * with a bundled CSV fallback for offline use.
 * <p>
 * Features:
 * <ul>
 *   <li>Vessel icons colored and shaped by type (cargo, tanker, passenger, fishing, tug)</li>
 *   <li>Icons rotated to true heading or course-over-ground</li>
 *   <li>Fading track-history trails with per-vertex opacity</li>
 *   <li>Interactive filter panel: vessel type, speed, name/MMSI search</li>
 *   <li>Click any vessel for a detail popup (name, MMSI, destination, speed, dimensions)</li>
 *   <li>Automatic fallback to bundled CSV replay when offline</li>
 * </ul>
 *
 * <p>AIS data provided by Fintraffic / digitraffic.fi, licensed under CC 4.0 BY.</p>
 *
 * seaglassfoundry.com — new showcase example for WorldWind Reforged
 */
public class AISVesselTrackerDemo extends ApplicationTemplate
{
    /** Position update interval in milliseconds. */
    private static final int UPDATE_INTERVAL_MS = 30_000;
    /** Vessel metadata refresh interval in milliseconds. */
    private static final int METADATA_INTERVAL_MS = 5 * 60_000;

    // ── AppFrame ──────────────────────────────────────────────────────────────

    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;

        private AISDataSource dataSource;
        private VesselManager vesselManager;
        private VesselFilterPanel filterPanel;
        private Timer positionTimer;
        private Timer metadataTimer;

        public AppFrame()
        {
            super(true, true, false);

            // ── Layers ────────────────────────────────────────────────────
            RenderableLayer trackLayer = new RenderableLayer();
            trackLayer.setName("Vessel Tracks");
            RenderableLayer vesselLayer = new RenderableLayer();
            vesselLayer.setName("AIS Vessels");

            insertBeforeCompass(getWwd(), trackLayer);
            insertBeforeCompass(getWwd(), vesselLayer);

            vesselManager = new VesselManager(getWwd(), vesselLayer, trackLayer);

            // ── Filter panel ──────────────────────────────────────────────
            filterPanel = new VesselFilterPanel(vesselManager);
            installControlPanel(filterPanel);

            // ── Detail popup on click ─────────────────────────────────────
            getWwd().addSelectListener(new VesselSelectListener());

            // ── Initial view: Baltic Sea ──────────────────────────────────
            getWwd().getView().setEyePosition(
                Position.fromDegrees(59.8, 24.0, 800_000));

            // ── Data source: try live, fall back to CSV ───────────────────
            initDataSource();

            // ── Periodic updates ──────────────────────────────────────────
            positionTimer = new Timer(UPDATE_INTERVAL_MS, e -> fetchPositionsAsync());
            positionTimer.setInitialDelay(0);
            positionTimer.start();

            metadataTimer = new Timer(METADATA_INTERVAL_MS, e -> fetchMetadataAsync());
            metadataTimer.setInitialDelay(500); // slight delay after positions
            metadataTimer.start();
        }

        // ── Data source initialisation ────────────────────────────────────

        private void initDataSource()
        {
            // Try Digitraffic first; if it fails on the first fetch, switch to CSV
            dataSource = new DigittrafficDataSource();
            filterPanel.setSourceLabel(dataSource.getSourceLabel(), dataSource.isLive());
        }

        private void switchToCsvFallback()
        {
            Logging.logger().info("AISVesselTrackerDemo: switching to CSV replay fallback");
            dataSource = new CsvReplayDataSource();
            filterPanel.setSourceLabel(dataSource.getSourceLabel(), dataSource.isLive());
        }

        // ── Async data fetching ───────────────────────────────────────────

        private void fetchPositionsAsync()
        {
            new SwingWorker<Map<Integer, VesselPosition>, Void>()
            {
                @Override
                protected Map<Integer, VesselPosition> doInBackground()
                {
                    return dataSource.fetchPositions();
                }

                @Override
                protected void done()
                {
                    try
                    {
                        Map<Integer, VesselPosition> positions = get();
                        if (positions.isEmpty() && dataSource.isLive())
                        {
                            switchToCsvFallback();
                            positions = dataSource.fetchPositions();
                        }
                        vesselManager.updatePositions(positions);
                        filterPanel.updateCounts();
                    }
                    catch (Exception ex)
                    {
                        Logging.logger().warning("AIS position fetch failed: " + ex.getMessage());
                        if (dataSource.isLive())
                        {
                            switchToCsvFallback();
                        }
                    }
                }
            }.execute();
        }

        private void fetchMetadataAsync()
        {
            new SwingWorker<Map<Integer, VesselInfo>, Void>()
            {
                @Override
                protected Map<Integer, VesselInfo> doInBackground()
                {
                    return dataSource.fetchVesselInfo();
                }

                @Override
                protected void done()
                {
                    try
                    {
                        Map<Integer, VesselInfo> info = get();
                        if (!info.isEmpty())
                        {
                            vesselManager.updateVesselInfo(info);
                            filterPanel.updateCounts();
                        }
                    }
                    catch (Exception ex)
                    {
                        Logging.logger().warning("AIS metadata fetch failed: " + ex.getMessage());
                    }
                }
            }.execute();
        }

        // ── Click-for-details ─────────────────────────────────────────────

        private class VesselSelectListener implements SelectListener
        {
            @Override
            public void selected(SelectEvent event)
            {
                if (!event.getEventAction().equals(SelectEvent.LEFT_CLICK))
                    return;
                if (!event.hasObjects())
                {
                    hideDetail();
                    return;
                }

                PickedObject po = event.getTopPickedObject();
                Object topObj = event.getTopObject();

                if (topObj instanceof PointPlacemark pm)
                {
                    Object mmsiObj = pm.getValue("mmsi");
                    if (mmsiObj instanceof Integer mmsi)
                    {
                        showDetail(mmsi, pm.getPosition());
                        return;
                    }
                }
                hideDetail();
            }
        }

        private void showDetail(int mmsi, Position pos)
        {
            filterPanel.showDetail(mmsi);
        }

        private void hideDetail()
        {
            filterPanel.hideDetail();
        }

        // ── Control panel setup ───────────────────────────────────────────

        private void installControlPanel(VesselFilterPanel aisPanel)
        {
            if (this.controlPanel == null) return;

            this.getContentPane().remove(this.controlPanel);
            this.getContentPane().remove(this.wwjPanel);

            JTabbedPane tabs = new JTabbedPane();
            tabs.setBackground(new Color(45, 45, 48));

            JScrollPane layerScroll = new JScrollPane(this.layerPanel);
            layerScroll.setBorder(null);
            tabs.addTab("Layers", layerScroll);

            JScrollPane aisScroll = new JScrollPane(aisPanel);
            aisScroll.setBorder(null);
            tabs.addTab("AIS", aisScroll);
            tabs.setSelectedIndex(1); // start on AIS tab

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
        ApplicationTemplate.start("WorldWind — AIS Vessel Tracker", AppFrame.class);
    }
}
