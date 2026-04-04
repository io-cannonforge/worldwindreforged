/*
 * WorldWind Reforged — AIS Vessel Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: offline CSV replay data source for the AIS vessel tracker demo.
 * Sample data originally captured from Fintraffic / digitraffic.fi (CC BY 4.0).
 */
package gov.nasa.worldwindx.examples.ais;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import gov.nasa.worldwind.util.Logging;

/**
 * Replays a bundled CSV dataset of AIS positions, producing a new "frame" of
 * vessel positions on each {@link #fetchPositions()} call. When all frames have
 * been played, the replay loops back to the beginning.
 * <p>
 * CSV format (header row required):
 * <pre>
 * timestamp,mmsi,lat,lon,sog,cog,heading,navStat,name,shipType,destination
 * </pre>
 *
 * seaglassfoundry.com
 */
public class CsvReplayDataSource implements AISDataSource
{
    private static final String CSV_RESOURCE =
        "gov/nasa/worldwindx/examples/ais/ais_sample_data.csv";

    /** Frames keyed by ascending timestamp. Each frame is a map of MMSI → position. */
    private final List<Map<Integer, VesselPosition>> frames = new ArrayList<>();
    /** Vessel metadata extracted from the CSV. */
    private final Map<Integer, VesselInfo> vesselInfoMap = new HashMap<>();
    /** Current frame index. */
    private int frameIndex = 0;
    /** True once the CSV has been loaded. */
    private boolean loaded = false;

    public CsvReplayDataSource()
    {
        loadCsv();
    }

    @Override
    public String getSourceLabel() { return "CSV Replay"; }

    @Override
    public boolean isLive() { return false; }

    @Override
    public Map<Integer, VesselPosition> fetchPositions()
    {
        if (!loaded || frames.isEmpty())
            return Collections.emptyMap();

        Map<Integer, VesselPosition> frame = frames.get(frameIndex);
        frameIndex = (frameIndex + 1) % frames.size();
        return frame;
    }

    @Override
    public Map<Integer, VesselInfo> fetchVesselInfo()
    {
        return vesselInfoMap;
    }

    // ── CSV loading ───────────────────────────────────────────────────────────

    private void loadCsv()
    {
        InputStream is = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream(CSV_RESOURCE);
        if (is == null)
        {
            Logging.logger().warning("CsvReplayDataSource: sample data not found at " + CSV_RESOURCE);
            // Generate synthetic demo data instead
            generateSyntheticData();
            loaded = true;
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)))
        {
            // Skip header
            String header = reader.readLine();
            if (header == null) return;

            // Group by timestamp bucket (10-second windows)
            TreeMap<Long, Map<Integer, VesselPosition>> buckets = new TreeMap<>();
            String line;
            while ((line = reader.readLine()) != null)
            {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",", -1);
                if (parts.length < 11) continue;

                try
                {
                    long timestamp = Long.parseLong(parts[0].trim());
                    int mmsi       = Integer.parseInt(parts[1].trim());
                    double lat     = Double.parseDouble(parts[2].trim());
                    double lon     = Double.parseDouble(parts[3].trim());
                    double sog     = Double.parseDouble(parts[4].trim());
                    double cog     = Double.parseDouble(parts[5].trim());
                    int heading    = Integer.parseInt(parts[6].trim());
                    int navStat    = Integer.parseInt(parts[7].trim());
                    String name    = parts[8].trim();
                    int shipType   = Integer.parseInt(parts[9].trim());
                    String dest    = parts[10].trim();

                    long bucket = (timestamp / 10_000) * 10_000; // 10-second buckets
                    buckets.computeIfAbsent(bucket, k -> new HashMap<>())
                        .put(mmsi, new VesselPosition(mmsi, lat, lon, sog, cog,
                            heading, navStat, timestamp));

                    // Collect metadata (last entry wins)
                    if (!name.isEmpty())
                        vesselInfoMap.put(mmsi, new VesselInfo(mmsi, name, "", 0,
                            shipType, 0, dest, 0, 0));
                }
                catch (NumberFormatException ignored) { /* skip malformed rows */ }
            }

            frames.addAll(buckets.values());
            loaded = true;
            Logging.logger().info("CsvReplayDataSource: loaded " + frames.size()
                + " frames, " + vesselInfoMap.size() + " vessels");
        }
        catch (Exception e)
        {
            Logging.logger().warning("CsvReplayDataSource: error loading CSV: " + e.getMessage());
            generateSyntheticData();
            loaded = true;
        }
    }

    /**
     * Generate synthetic vessel data for demonstration when no CSV file is bundled.
     * Creates ~40 vessels in the Gulf of Finland / Baltic Sea area with realistic
     * positions, headings, and types.
     */
    private void generateSyntheticData()
    {
        Logging.logger().info("CsvReplayDataSource: generating synthetic demo data");

        // Define some shipping lanes in the Baltic
        double[][] routes =
        {
            // Helsinki → Tallinn ferry route
            {60.16, 24.94, 59.44, 24.75},
            // Helsinki → Stockholm (westbound)
            {60.14, 24.80, 59.70, 22.00},
            // Cargo lane — Gulf of Finland (east-west)
            {60.05, 27.50, 60.10, 24.00},
            // Tanker route — approaching Helsinki
            {59.80, 25.50, 60.10, 25.00},
            // Fishing area off Suomenlinna
            {60.12, 25.00, 60.08, 25.10},
        };

        int[] shipTypes = {70, 80, 60, 70, 30}; // cargo, tanker, passenger, cargo, fishing
        String[] names = {"BALTIC CARGO", "NORDIC TANKER", "SILJA EUROPA", "FINNWAVE", "KALASTAJA"};
        String[] dests = {"TALLINN", "STOCKHOLM", "TALLINN", "HELSINKI", ""};

        int mmsiBase = 230000000; // Finland MMSI range
        long baseTime = System.currentTimeMillis();
        int vesselId = 0;

        // Generate 30 frames of data (each represents a 30-second update)
        for (int frame = 0; frame < 30; frame++)
        {
            Map<Integer, VesselPosition> frameData = new HashMap<>();
            long frameTime = baseTime + frame * 30_000L;

            for (int route = 0; route < routes.length; route++)
            {
                // 8 vessels per route
                for (int v = 0; v < 8; v++)
                {
                    int mmsi = mmsiBase + route * 100 + v;
                    double progress = (v * 0.12 + frame * 0.008) % 1.0;

                    double lat = routes[route][0] + (routes[route][2] - routes[route][0]) * progress;
                    double lon = routes[route][1] + (routes[route][3] - routes[route][1]) * progress;
                    // Add slight randomness
                    lat += (Math.sin(mmsi * 0.1 + frame * 0.3) * 0.02);
                    lon += (Math.cos(mmsi * 0.1 + frame * 0.3) * 0.03);

                    double dlat = routes[route][2] - routes[route][0];
                    double dlon = routes[route][3] - routes[route][1];
                    double cog = Math.toDegrees(Math.atan2(dlon, dlat));
                    if (cog < 0) cog += 360;

                    double sog = 8 + (route == 2 ? 6 : 0) + (route == 4 ? -5 : 0) + Math.sin(mmsi) * 2;
                    int heading = (int) cog;

                    frameData.put(mmsi, new VesselPosition(mmsi, lat, lon, sog, cog,
                        heading, 0, frameTime));

                    // Metadata (only set once)
                    if (frame == 0)
                    {
                        String name = names[route] + " " + (v + 1);
                        vesselInfoMap.put(mmsi, new VesselInfo(mmsi, name, "",
                            0, shipTypes[route], 0, dests[route], 150 + v * 20, 20 + v * 2));
                    }
                }
            }
            frames.add(frameData);
        }
    }
}
