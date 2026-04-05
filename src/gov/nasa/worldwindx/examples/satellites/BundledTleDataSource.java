/*
 * WorldWind Reforged — Satellite Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: offline bundled TLE data source for the satellite tracker demo.
 * Falls back to hard-coded TLEs for well-known satellites when the bundled JSON
 * resource is not available.
 */
package gov.nasa.worldwindx.examples.satellites;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import gov.nasa.worldwind.util.Logging;

/**
 * Provides satellite TLE data from a bundled three-line TLE text file, or falls back
 * to a hard-coded set of well-known satellites when the resource is not available.
 * <p>
 * The bundled TLE file uses standard three-line format:
 * <pre>
 * ISS (ZARYA)
 * 1 25544U 98067A   24076.51782528  .00026475  00000+0  47253-3 0  9993
 * 2 25544  51.6412 200.3480 0006730  20.3484 339.7868 15.49560325443774
 * </pre>
 *
 * seaglassfoundry.com
 */
public class BundledTleDataSource implements SatelliteDataSource
{
    private static final String TLE_RESOURCE =
        "gov/nasa/worldwindx/examples/satellites/tle_sample_data.txt";

    private Map<Integer, TleRecord> cached = null;

    @Override
    public String getSourceLabel() { return "Bundled TLEs"; }

    @Override
    public boolean isLive() { return false; }

    @Override
    public Map<Integer, TleRecord> fetchTLEs()
    {
        if (cached != null) return cached;

        cached = loadFromResource();
        if (cached.isEmpty())
        {
            Logging.logger().info("BundledTleDataSource: resource not found, using synthetic TLEs");
            cached = generateSyntheticTLEs();
        }
        else
        {
            Logging.logger().info("BundledTleDataSource: loaded " + cached.size() + " satellites");
        }
        return cached;
    }

    private Map<Integer, TleRecord> loadFromResource()
    {
        Map<Integer, TleRecord> result = new HashMap<>();
        try
        {
            InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(TLE_RESOURCE);
            if (is == null) return result;

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    // Three-line TLE format: name, line1, line2
                    if (!line.startsWith("1 ") && !line.startsWith("2 "))
                    {
                        String name = line;
                        String line1 = reader.readLine();
                        String line2 = reader.readLine();
                        if (line1 != null && line2 != null)
                        {
                            TleRecord rec = TleRecord.fromTleLines(name.trim(),
                                line1.trim(), line2.trim());
                            if (rec != null)
                                result.put(rec.getNoradCatId(), rec);
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            Logging.logger().warning("BundledTleDataSource: error loading resource: " + e.getMessage());
        }
        return result;
    }

    /**
     * Generate synthetic TLE data for well-known satellites with approximate
     * orbital elements. These TLEs are realistic enough for visual demonstration
     * even though they don't match current real-time positions.
     */
    private static Map<Integer, TleRecord> generateSyntheticTLEs()
    {
        Map<Integer, TleRecord> result = new HashMap<>();

        // Each entry: name, noradId, inclination, raan, eccentricity, argPerigee, meanAnomaly, meanMotion
        Object[][] sats = {
            // Space Stations
            {"ISS (ZARYA)",      25544, 51.64, 200.35, 0.0006730, 20.35, 339.79, 15.4956},
            {"CSS (TIANHE)",     48274, 41.47,  20.00, 0.0003000, 10.00, 350.00, 15.6200},

            // GPS constellation (some)
            {"GPS BIIR-2  (PRN 13)", 24876, 55.0,  50.0, 0.005, 270.0,  90.0, 2.00563},
            {"GPS BIIRM-3 (PRN 12)", 29601, 55.0, 110.0, 0.008, 180.0, 180.0, 2.00568},
            {"GPS BIIRM-8 (PRN 07)", 32711, 55.0, 170.0, 0.012, 100.0, 260.0, 2.00555},
            {"GPS BIII-3  (PRN 11)", 44506, 55.0, 230.0, 0.003,  30.0,  30.0, 2.00571},

            // GLONASS
            {"COSMOS 2564 (GLONASS)", 55300, 64.8, 100.0, 0.001, 270.0, 90.0, 2.13103},
            {"COSMOS 2566 (GLONASS)", 55547, 64.8, 220.0, 0.001,  90.0, 270.0, 2.13108},

            // Weather
            {"GOES 16",          41866, 0.05, 265.5, 0.0001,  90.0, 270.0, 1.00273},
            {"GOES 18",          51850, 0.03, 225.3, 0.0001, 180.0, 180.0, 1.00274},
            {"NOAA 20",          43013, 98.7, 120.0, 0.001,  100.0, 260.0, 14.1952},
            {"METEOSAT-11",      40732, 0.10,  75.0, 0.0002,  90.0, 270.0, 1.00274},
            {"HIMAWARI-9",       41836, 0.03, 140.7, 0.0001, 200.0, 160.0, 1.00274},

            // Communication
            {"IRIDIUM 180",      56730, 86.4,  30.0, 0.001, 100.0, 260.0, 14.3423},
            {"INTELSAT 40E",     54833, 0.02,  40.0, 0.0002, 90.0, 270.0, 1.00274},
            {"TDRS 13",          46269, 0.05, 174.5, 0.0001, 90.0, 270.0, 1.00274},

            // Earth Observation
            {"LANDSAT 9",        49260, 98.2, 120.0, 0.001,  90.0, 270.0, 14.5713},
            {"SENTINEL-2A",      40697, 98.6, 130.0, 0.001, 100.0, 260.0, 14.3085},
            {"TERRA",            25994, 98.2, 110.0, 0.001,  90.0, 270.0, 14.5712},
            {"AQUA",             27424, 98.2, 140.0, 0.001, 100.0, 260.0, 14.5712},
            {"WORLDVIEW-3",      40115, 97.9, 160.0, 0.001,  80.0, 280.0, 14.8400},

            // Starlink samples
            {"STARLINK-1007",    44713, 53.0,  50.0, 0.0001, 90.0, 270.0, 15.0600},
            {"STARLINK-1130",    44914, 53.0, 110.0, 0.0001, 90.0, 270.0, 15.0605},
            {"STARLINK-2045",    47364, 53.0, 170.0, 0.0001, 90.0, 270.0, 15.0610},
            {"STARLINK-3000",    49150, 53.0, 230.0, 0.0001, 90.0, 270.0, 15.0615},
            {"STARLINK-4100",    52150, 53.2, 290.0, 0.0001, 90.0, 270.0, 15.0608},
            {"STARLINK-5200",    56100, 43.0,  20.0, 0.0001, 90.0, 270.0, 15.0620},
            {"STARLINK-5500",    57100, 43.0,  80.0, 0.0001, 90.0, 270.0, 15.0625},
            {"STARLINK-5800",    58100, 53.0, 350.0, 0.0001, 90.0, 270.0, 15.0630},

            // Military / classified
            {"USA 314",          50504, 97.9, 200.0, 0.001,  90.0, 270.0, 15.1900},
            {"USA 326",          55053, 63.4, 100.0, 0.700, 270.0,  90.0, 2.00600},

            // Science
            {"HUBBLE",           20580, 28.5,  80.0, 0.002, 100.0, 260.0, 15.0946},
            {"JAMES WEBB (JWST)",52218, 1.0,  180.0, 0.001, 180.0, 180.0, 0.99726},

            // Other / Misc
            {"VANGUARD 1",       5,     34.3,  50.0, 0.185, 130.0, 230.0, 10.8482},
            {"COSMOS 2251 DEB",  34454, 74.0, 160.0, 0.010,  50.0, 310.0, 14.5100},

            // Galileo
            {"GALILEO-FM1",      37846, 56.0, 300.0, 0.0001, 90.0, 270.0, 1.70475},
            {"GALILEO-FM8",      40889, 56.0,  60.0, 0.0001, 90.0, 270.0, 1.70475},

            // More LEO variety
            {"SPOT 7",           40053, 98.2, 150.0, 0.001, 90.0, 270.0, 14.6295},
            {"PLEIADES NEO 3",   49258, 98.0, 170.0, 0.001, 90.0, 270.0, 14.9900},
            {"CRYOSAT 2",        36508, 92.0, 200.0, 0.001, 90.0, 270.0, 14.5222},
        };

        // Use a reference epoch (current approximate)
        double refEpochJd = 2460405.5; // ~2024-03-15

        for (Object[] s : sats)
        {
            String name = (String) s[0];
            int noradId = (int) s[1];
            double incl = (double) s[2];
            double raan = (double) s[3];
            double ecc = (double) s[4];
            double argP = (double) s[5];
            double ma = (double) s[6];
            double mm = (double) s[7];

            // Distribute mean anomalies to spread satellites around orbits
            double offsetMa = (ma + noradId * 137.0) % 360.0;

            TleRecord rec = new TleRecord(noradId, name, "", refEpochJd, mm, ecc,
                incl, raan, argP, offsetMa, 0.0, 0,
                generateTleLine1(noradId, refEpochJd, 0),
                generateTleLine2(noradId, incl, raan, ecc, argP, offsetMa, mm));
            result.put(noradId, rec);
        }

        return result;
    }

    /** Generate a minimal TLE line 1 for synthetic data. */
    private static String generateTleLine1(int noradId, double epochJd, double bstar)
    {
        // Simplified — not checksum-valid but sufficient for our SGP4
        return String.format("1 %05dU 24001A   24076.50000000  .00000000  00000-0  00000-0 0  9999",
            noradId);
    }

    /** Generate a minimal TLE line 2 for synthetic data. */
    private static String generateTleLine2(int noradId, double incl, double raan,
        double ecc, double argP, double ma, double mm)
    {
        // Format eccentricity without leading "0."
        int eccInt = (int) Math.round(ecc * 10000000);
        return String.format("2 %05d %8.4f %8.4f %07d %8.4f %8.4f %11.8f00000",
            noradId, incl, raan, eccInt, argP, ma, mm);
    }
}
