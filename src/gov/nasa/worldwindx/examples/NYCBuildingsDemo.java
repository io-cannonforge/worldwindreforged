/*
 * WorldWind Reforged — NYC Buildings 3D Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New example: 3D extruded buildings of New York City from NYC Open Data building
 * footprints, with view-based loading, height-based colouring, interactive filtering,
 * and click-for-details.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import gov.nasa.worldwind.event.RenderingEvent;
import gov.nasa.worldwind.event.RenderingListener;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwindx.examples.nycbuildings.BuildingBatchRenderer;
import gov.nasa.worldwindx.examples.nycbuildings.BuildingFilterPanel;
import gov.nasa.worldwindx.examples.nycbuildings.BuildingRecord;
import gov.nasa.worldwindx.examples.nycbuildings.BundledGeoJsonDataSource;
import gov.nasa.worldwindx.examples.nycbuildings.NYCOpenDataSource;

/**
 * 3D extruded buildings of New York City from NYC Open Data building footprints.
 * <p>
 * <b>Wow-factor features:</b>
 * <ul>
 *   <li><b>View-based loading</b> — buildings load as you pan across all 5 boroughs</li>
 *   <li><b>Batched VBO rendering</b> — thousands of buildings in a handful of draw calls</li>
 *   <li><b>Height-based colour spectrum</b>: low-rise(steel blue) &rarr; mid-rise(blue) &rarr;
 *       high-rise(deep blue) &rarr; skyscrapers(gold) &rarr; supertall(red)</li>
 *   <li><b>Two-phase cell loading</b>: tall buildings first for instant skyline, then all</li>
 *   <li><b>Height-based LOD</b>: only tall buildings visible when zoomed out</li>
 *   <li><b>Quad-tree frustum culling</b> for per-tile visibility</li>
 *   <li><b>Interactive filters</b>: height slider, category toggles, name search</li>
 *   <li><b>Click-for-details</b>: building name, height, floors, type</li>
 *   <li><b>Automatic offline fallback</b> to bundled Midtown sample data</li>
 * </ul>
 *
 * <p>Data from <a href="https://data.cityofnewyork.us">NYC Open Data</a> — free, no API key.
 * Building footprints with HEIGHT_ROOF on virtually every record.</p>
 *
 * seaglassfoundry.com — new showcase example for WorldWind Reforged
 */
public class NYCBuildingsDemo extends ApplicationTemplate
{
    /** Grid cell size in degrees (~1.1 km lat × ~0.85 km lon at NYC latitude) */
    private static final double CELL_SIZE = 0.01;

    /** NYC bounding box — covers all 5 boroughs. Used to clamp cell queries. */
    private static final double NYC_SOUTH = 40.49;
    private static final double NYC_WEST  = -74.26;
    private static final double NYC_NORTH = 40.92;
    private static final double NYC_EAST  = -73.70;

    // ── AppFrame ──────────────────────────────────────────────────────────────

    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;

        private BuildingBatchRenderer renderer;
        private BuildingFilterPanel filterPanel;
        private RenderableLayer buildingLayer;

        /** All loaded buildings, keyed by id for dedup. */
        private final Map<String, BuildingRecord> allBuildings = new LinkedHashMap<>();

        /** Grid cells that have been fully loaded (all buildings). */
        private final Set<String> loadedCellsAll = new HashSet<>();

        /** Grid cells that have had tall buildings loaded. */
        private final Set<String> loadedCellsTall = new HashSet<>();

        /** Debounce timer for view-based loading. */
        private Timer viewDebounceTimer;

        /** Track last sector we checked to avoid redundant work. */
        private Sector lastCheckedSector;

        /** Whether a fetch is currently in progress. */
        private volatile boolean fetching;

        public AppFrame()
        {
            super(true, true, false);

            // ── Layers ───────────────────────────────────────────────────
            buildingLayer = new RenderableLayer();
            buildingLayer.setName("NYC Buildings");

            insertBeforeCompass(getWwd(), buildingLayer);

            // ── Enable high-res imagery for urban detail ────────────────
            gov.nasa.worldwind.layers.Layer bing = getWwd().getModel().getLayers().getLayerByName("Bing Imagery");
            if (bing != null) bing.setEnabled(true);

            // ── Renderer ─────────────────────────────────────────────────
            renderer = new BuildingBatchRenderer();
            buildingLayer.addRenderable(renderer);

            // ── Filter panel ─────────────────────────────────────────────
            filterPanel = new BuildingFilterPanel(renderer);
            filterPanel.setGeocodeListener(e -> geocodeAddress(e.getActionCommand()));
            installControlPanel(filterPanel);

            // ── Click-for-details ────────────────────────────────────────
            getWwd().addSelectListener(new BuildingSelectListener());

            // ── Initial view: One World Trade Center ─────────────────────
            getWwd().getView().setEyePosition(
                Position.fromDegrees(40.7127, -74.0134, 3000));

            // ── View-based loading ──────────────────────────────────────
            viewDebounceTimer = new Timer(800, e -> checkVisibleSectorAndFetch());
            viewDebounceTimer.setRepeats(false);

            getWwd().addRenderingListener(event ->
            {
                if (RenderingEvent.BEFORE_RENDERING.equals(event.getStage()))
                    viewDebounceTimer.restart();
            });

            // ── Initial fetch for WTC area ──────────────────────────────
            fetchCellsForSector(Sector.fromDegrees(40.700, 40.725, -74.025, -74.000), true);
        }

        // ── View-based loading ──────────────────────────────────────────

        private void checkVisibleSectorAndFetch()
        {
            if (fetching) return;

            Position eye = getWwd().getView().getEyePosition();
            if (eye == null) return;

            double alt = eye.getAltitude();
            // Don't load at extreme altitudes
            if (alt > 20000 || alt < 10) return;

            // Compute a sector around the eye position based on altitude
            double span = Math.min(alt / 100000.0, 0.15); // degrees of visible area
            span = Math.max(span, 0.015); // minimum span

            double lat = eye.getLatitude().degrees;
            double lon = eye.getLongitude().degrees;

            Sector visible = Sector.fromDegrees(
                lat - span, lat + span,
                lon - span * 1.3, lon + span * 1.3); // wider horizontally

            // Clamp to NYC bounds
            double s = Math.max(visible.getMinLatitude().degrees, NYC_SOUTH);
            double n = Math.min(visible.getMaxLatitude().degrees, NYC_NORTH);
            double w = Math.max(visible.getMinLongitude().degrees, NYC_WEST);
            double e = Math.min(visible.getMaxLongitude().degrees, NYC_EAST);

            if (s >= n || w >= e) return; // outside NYC

            Sector clamped = Sector.fromDegrees(s, n, w, e);

            // Skip if sector hasn't changed meaningfully
            if (lastCheckedSector != null && lastCheckedSector.contains(clamped))
                return;
            lastCheckedSector = clamped;

            fetchCellsForSector(clamped, false);
        }

        /**
         * Determine which grid cells overlap the given sector and fetch any that
         * haven't been loaded yet. If {@code initialLoad} is true, fetches tall
         * buildings first for fast display.
         */
        private void fetchCellsForSector(Sector sector, boolean initialLoad)
        {
            double s = sector.getMinLatitude().degrees;
            double n = sector.getMaxLatitude().degrees;
            double w = sector.getMinLongitude().degrees;
            double e = sector.getMaxLongitude().degrees;

            // Find grid cells that overlap this sector
            List<double[]> cellsToFetch = new ArrayList<>();
            for (double lat = snapToGrid(s); lat < n; lat += CELL_SIZE)
            {
                for (double lon = snapToGrid(w); lon < e; lon += CELL_SIZE)
                {
                    String key = cellKey(lat, lon);
                    if (!loadedCellsAll.contains(key))
                        cellsToFetch.add(new double[]{lat, lon});
                }
            }

            if (cellsToFetch.isEmpty()) return;

            // Determine which cells need tall-only fetch first
            List<double[]> tallCells = new ArrayList<>();
            List<double[]> allCells = new ArrayList<>();

            for (double[] cell : cellsToFetch)
            {
                String key = cellKey(cell[0], cell[1]);
                if (!loadedCellsTall.contains(key))
                    tallCells.add(cell);
                allCells.add(cell);
            }

            // Phase 1: tall buildings first if there are new tall cells
            if (!tallCells.isEmpty())
            {
                fetchCells(tallCells, true, initialLoad ? "Loading skyline..." : null);
            }

            // Phase 2: all buildings
            if (!allCells.isEmpty())
            {
                fetchCells(allCells, false, null);
            }
        }

        private void fetchCells(List<double[]> cells, boolean tallOnly, String statusMessage)
        {
            if (fetching) return;
            fetching = true;

            if (statusMessage != null)
                filterPanel.setStatus(statusMessage, new Color(230, 180, 50));

            new SwingWorker<List<BuildingRecord>, Void>()
            {
                private boolean usedFallback;

                @Override
                protected List<BuildingRecord> doInBackground()
                {
                    NYCOpenDataSource source = new NYCOpenDataSource(tallOnly);
                    List<BuildingRecord> results = new ArrayList<>();

                    for (double[] cell : cells)
                    {
                        double cs = cell[0];
                        double cw = cell[1];
                        double cn = cs + CELL_SIZE;
                        double ce = cw + CELL_SIZE;

                        List<BuildingRecord> cellRecords = source.fetchBuildings(cs, cw, cn, ce);
                        results.addAll(cellRecords);
                    }

                    // If we got nothing and this is the initial load, try bundled fallback
                    if (results.isEmpty() && allBuildings.isEmpty())
                    {
                        BundledGeoJsonDataSource bundled = new BundledGeoJsonDataSource();
                        results = bundled.fetchBuildings(0, 0, 0, 0);
                        usedFallback = true;
                    }

                    return results;
                }

                @Override
                protected void done()
                {
                    fetching = false;
                    try
                    {
                        List<BuildingRecord> newRecords = get();
                        if (!newRecords.isEmpty())
                        {
                            // Merge into accumulated map (dedup by id)
                            for (BuildingRecord r : newRecords)
                                allBuildings.put(r.getId(), r);

                            // Track loaded cells
                            for (double[] cell : cells)
                            {
                                String key = cellKey(cell[0], cell[1]);
                                if (tallOnly)
                                    loadedCellsTall.add(key);
                                else
                                    loadedCellsAll.add(key);
                            }

                            // Rebuild renderer with all accumulated buildings
                            renderer.loadBuildings(new ArrayList<>(allBuildings.values()));

                            filterPanel.setSourceInfo(
                                usedFallback ? "Bundled Sample" : "NYC Open Data",
                                !usedFallback);
                            filterPanel.setStatus(
                                allBuildings.size() + " buildings loaded",
                                usedFallback ? new Color(230, 180, 50) : new Color(80, 200, 120));
                            filterPanel.updateStats();
                            getWwd().redraw();
                        }
                    }
                    catch (Exception ex)
                    {
                        Logging.logger().warning("Building fetch failed: " + ex.getMessage());
                        filterPanel.setStatus("Fetch error", new Color(210, 70, 70));
                    }
                }
            }.execute();
        }

        private static double snapToGrid(double value)
        {
            return Math.floor(value / CELL_SIZE) * CELL_SIZE;
        }

        private static String cellKey(double lat, double lon)
        {
            return String.format("%.4f_%.4f", snapToGrid(lat), snapToGrid(lon));
        }

        // ── Address geocoding ───────────────────────────────────────────

        private void geocodeAddress(String address)
        {
            filterPanel.setGeocodeStatus("Searching...", new Color(230, 180, 50));

            new SwingWorker<double[], Void>()
            {
                private String displayName;

                @Override
                protected double[] doInBackground()
                {
                    return nominatimGeocode(address);
                }

                @Override
                protected void done()
                {
                    try
                    {
                        double[] result = get();
                        if (result != null)
                        {
                            filterPanel.setGeocodeStatus(
                                displayName != null ? displayName : "Found",
                                new Color(80, 200, 120));

                            // Fly camera to the location
                            Position target = Position.fromDegrees(result[0], result[1], 0);
                            getWwd().getView().goTo(target, 1500);
                        }
                        else
                        {
                            filterPanel.setGeocodeStatus("Not found", new Color(210, 70, 70));
                        }
                    }
                    catch (Exception ex)
                    {
                        filterPanel.setGeocodeStatus("Error", new Color(210, 70, 70));
                    }
                }

                /**
                 * Query Nominatim (OpenStreetMap) geocoder. Returns [lat, lon] or null.
                 */
                private double[] nominatimGeocode(String query)
                {
                    try
                    {
                        String encoded = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
                        String url = "https://nominatim.openstreetmap.org/search"
                            + "?q=" + encoded
                            + "&format=json&limit=1"
                            + "&viewbox=-74.26,40.92,-73.70,40.49&bounded=1";

                        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(url))
                            .header("User-Agent", "WorldWindReforged/1.0 (seaglassfoundry.com)")
                            .timeout(java.time.Duration.ofSeconds(10))
                            .GET()
                            .build();

                        java.net.http.HttpResponse<String> resp = java.net.http.HttpClient.newHttpClient()
                            .send(req, java.net.http.HttpResponse.BodyHandlers.ofString());

                        if (resp.statusCode() != 200) return null;

                        // Minimal JSON parsing — response is: [{"lat":"40.7","lon":"-74.0","display_name":"..."}]
                        String body = resp.body().trim();
                        if (body.equals("[]")) return null;

                        double lat = extractJsonDouble(body, "lat");
                        double lon = extractJsonDouble(body, "lon");
                        displayName = extractJsonString(body, "display_name");

                        // Truncate long display names
                        if (displayName != null && displayName.length() > 60)
                            displayName = displayName.substring(0, 57) + "...";

                        if (lat != 0 && lon != 0) return new double[]{lat, lon};
                    }
                    catch (Exception e)
                    {
                        Logging.logger().fine("Geocode failed: " + e.getMessage());
                    }
                    return null;
                }

                private double extractJsonDouble(String json, String key)
                {
                    String pattern = "\"" + key + "\":\"";
                    int i = json.indexOf(pattern);
                    if (i < 0) return 0;
                    int start = i + pattern.length();
                    int end = json.indexOf("\"", start);
                    if (end < 0) return 0;
                    try { return Double.parseDouble(json.substring(start, end)); }
                    catch (NumberFormatException e) { return 0; }
                }

                private String extractJsonString(String json, String key)
                {
                    String pattern = "\"" + key + "\":\"";
                    int i = json.indexOf(pattern);
                    if (i < 0) return null;
                    int start = i + pattern.length();
                    int end = json.indexOf("\"", start);
                    if (end < 0) return null;
                    return json.substring(start, end);
                }
            }.execute();
        }

        // ── Click-for-details ────────────────────────────────────────────

        private class BuildingSelectListener implements SelectListener
        {
            @Override
            public void selected(SelectEvent event)
            {
                if (event.getEventAction().equals(SelectEvent.LEFT_CLICK))
                {
                    if (event.hasObjects() && event.getTopObject() instanceof BuildingRecord record)
                    {
                        showDetail(record, event.getPickPoint());
                        return;
                    }
                    hideDetail();
                }
            }
        }

        private void showDetail(BuildingRecord record, java.awt.Point screenPoint)
        {
            Position pos = computeBuildingCentre(record);
            if (pos == null) return;

            filterPanel.showDetail(record, null);

            // Reverse-geocode to get address asynchronously
            reverseGeocode(pos.getLatitude().degrees, pos.getLongitude().degrees, record);
        }

        private void reverseGeocode(double lat, double lon, BuildingRecord record)
        {
            new SwingWorker<String, Void>()
            {
                @Override
                protected String doInBackground()
                {
                    try
                    {
                        String url = String.format(
                            "https://nominatim.openstreetmap.org/reverse?lat=%.6f&lon=%.6f&format=json&zoom=18",
                            lat, lon);

                        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(url))
                            .header("User-Agent", "WorldWindReforged/1.0 (seaglassfoundry.com)")
                            .timeout(java.time.Duration.ofSeconds(10))
                            .GET()
                            .build();

                        java.net.http.HttpResponse<String> resp = java.net.http.HttpClient.newHttpClient()
                            .send(req, java.net.http.HttpResponse.BodyHandlers.ofString());

                        if (resp.statusCode() != 200) return null;

                        String body = resp.body();

                        String number = extractNestedJsonString(body, "house_number");
                        String road = extractNestedJsonString(body, "road");

                        if (road != null && !road.isEmpty())
                        {
                            if (number != null && !number.isEmpty())
                                return number + " " + road;
                            return road;
                        }

                        return null;
                    }
                    catch (Exception e)
                    {
                        return null;
                    }
                }

                @Override
                protected void done()
                {
                    try
                    {
                        String address = get();
                        filterPanel.updateDetailAddress(record,
                            address != null ? address : "");
                    }
                    catch (Exception ignored) { }
                }

                private String extractNestedJsonString(String json, String key)
                {
                    String pattern = "\"" + key + "\":\"";
                    int i = json.indexOf(pattern);
                    if (i < 0) return null;
                    int start = i + pattern.length();
                    int end = json.indexOf("\"", start);
                    if (end < 0) return null;
                    return json.substring(start, end);
                }
            }.execute();
        }

        private void hideDetail()
        {
            filterPanel.hideDetail();
        }

        private Position computeBuildingCentre(BuildingRecord record)
        {
            if (record.getFootprint().isEmpty()) return null;
            double lat = 0, lon = 0;
            for (var ll : record.getFootprint())
            {
                lat += ll.getLatitude().degrees;
                lon += ll.getLongitude().degrees;
            }
            int n = record.getFootprint().size();
            return Position.fromDegrees(lat / n, lon / n, record.getHeightMeters());
        }

        // ── Control panel setup ──────────────────────────────────────────

        private void installControlPanel(BuildingFilterPanel panel)
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
            tabs.addTab("Buildings", filterScroll);
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

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind \u2014 NYC Buildings 3D", AppFrame.class);
    }
}
