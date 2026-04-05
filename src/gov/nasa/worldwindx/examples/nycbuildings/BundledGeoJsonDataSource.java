/*
 * WorldWind Reforged — NYC Buildings 3D Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: offline fallback data source that loads bundled GeoJSON or generates
 * synthetic building data when the resource is unavailable.
 */
package gov.nasa.worldwindx.examples.nycbuildings;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.util.Logging;

/**
 * Offline building data source that loads a bundled GeoJSON FeatureCollection
 * from the classpath. Falls back to synthetic grid generation if the resource
 * is unavailable.
 *
 * seaglassfoundry.com
 */
public class BundledGeoJsonDataSource implements BuildingDataSource
{
    private static final String RESOURCE_PATH =
        "gov/nasa/worldwindx/examples/nycbuildings/nyc_buildings_sample.geojson";

    private final JsonFactory jsonFactory = new JsonFactory();

    @Override
    public String getSourceLabel() { return "Bundled Sample"; }

    @Override
    public boolean isLive() { return false; }

    @Override
    public List<BuildingRecord> fetchBuildings(double south, double west, double north, double east)
    {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_PATH))
        {
            if (is != null)
                return parseGeoJson(is);
        }
        catch (Exception e)
        {
            Logging.logger().warning("BundledGeoJsonDataSource: failed to load resource: " + e.getMessage());
        }

        Logging.logger().info("BundledGeoJsonDataSource: resource not found, generating synthetic buildings");
        return generateSyntheticBuildings();
    }

    // ── GeoJSON parser ───────────────────────────────────────────────────────

    private List<BuildingRecord> parseGeoJson(InputStream is) throws Exception
    {
        List<BuildingRecord> records = new ArrayList<>();

        try (JsonParser jp = jsonFactory.createParser(is))
        {
            // Advance to "features" array
            while (jp.nextToken() != null)
            {
                if (jp.currentToken() == JsonToken.FIELD_NAME && "features".equals(jp.currentName()))
                {
                    jp.nextToken(); // START_ARRAY
                    break;
                }
            }

            // Parse each Feature
            while (jp.nextToken() == JsonToken.START_OBJECT)
            {
                BuildingRecord record = parseFeature(jp);
                if (record != null)
                    records.add(record);
            }
        }

        return records;
    }

    private BuildingRecord parseFeature(JsonParser jp) throws Exception
    {
        String id = null;
        String heightTag = null, levelsTag = null, name = null, buildingType = null;
        String addrStreet = null, addrNumber = null;
        List<List<double[]>> rings = null; // outer + inner rings as [lon, lat] arrays

        while (jp.nextToken() != JsonToken.END_OBJECT)
        {
            if (jp.currentToken() != JsonToken.FIELD_NAME) continue;
            String field = jp.currentName();

            switch (field)
            {
                case "properties" ->
                {
                    jp.nextToken(); // START_OBJECT
                    while (jp.nextToken() != JsonToken.END_OBJECT)
                    {
                        if (jp.currentToken() != JsonToken.FIELD_NAME) continue;
                        String prop = jp.currentName();
                        jp.nextToken(); // value
                        switch (prop)
                        {
                            case "id" -> id = jp.getValueAsString();
                            case "height" -> heightTag = jp.getValueAsString();
                            case "building:levels" -> levelsTag = jp.getValueAsString();
                            case "name" -> name = jp.getValueAsString();
                            case "building" -> buildingType = jp.getValueAsString();
                            case "addr:street" -> addrStreet = jp.getValueAsString();
                            case "addr:housenumber" -> addrNumber = jp.getValueAsString();
                        }
                    }
                }
                case "geometry" ->
                {
                    jp.nextToken(); // START_OBJECT
                    while (jp.nextToken() != JsonToken.END_OBJECT)
                    {
                        if (jp.currentToken() != JsonToken.FIELD_NAME) continue;
                        String geomField = jp.currentName();
                        if ("coordinates".equals(geomField))
                        {
                            rings = parsePolygonCoordinates(jp);
                        }
                        else
                        {
                            jp.nextToken();
                            jp.skipChildren();
                        }
                    }
                }
                default ->
                {
                    jp.nextToken();
                    jp.skipChildren();
                }
            }
        }

        if (rings == null || rings.isEmpty() || rings.get(0).size() < 3)
            return null;

        // Convert outer ring to LatLon
        List<LatLon> footprint = new ArrayList<>();
        for (double[] coord : rings.get(0))
            footprint.add(LatLon.fromDegrees(coord[1], coord[0])); // GeoJSON is [lon, lat]

        // Inner rings (holes)
        List<List<LatLon>> holes = new ArrayList<>();
        for (int i = 1; i < rings.size(); i++)
        {
            List<LatLon> hole = new ArrayList<>();
            for (double[] coord : rings.get(i))
                hole.add(LatLon.fromDegrees(coord[1], coord[0]));
            holes.add(hole);
        }

        double height = BuildingRecord.resolveHeight(heightTag, levelsTag, buildingType);
        int levels = BuildingRecord.resolveLevels(levelsTag, height);
        String address = formatAddress(addrNumber, addrStreet);

        return new BuildingRecord(id, footprint, holes, height, levels, name, buildingType, address);
    }

    /**
     * Parse GeoJSON Polygon coordinates: [ [ [lon,lat], ... ], [ [lon,lat], ... ] ]
     * First ring is outer, subsequent are holes.
     */
    private List<List<double[]>> parsePolygonCoordinates(JsonParser jp) throws Exception
    {
        jp.nextToken(); // START_ARRAY (outer array of rings)
        List<List<double[]>> rings = new ArrayList<>();

        while (jp.nextToken() == JsonToken.START_ARRAY) // each ring
        {
            List<double[]> ring = new ArrayList<>();
            while (jp.nextToken() == JsonToken.START_ARRAY) // each coordinate pair
            {
                jp.nextToken(); // VALUE_NUMBER for lon
                double lon = jp.getDoubleValue();
                jp.nextToken(); // VALUE_NUMBER for lat
                double lat = jp.getDoubleValue();
                // Skip optional altitude and advance to END_ARRAY
                while (jp.nextToken() != JsonToken.END_ARRAY) { /* skip */ }
                ring.add(new double[]{lon, lat});
            }
            rings.add(ring);
        }

        return rings;
    }

    private static String formatAddress(String number, String street)
    {
        if (street == null || street.isEmpty()) return "";
        if (number == null || number.isEmpty()) return street;
        return number + " " + street;
    }

    // ── Synthetic fallback ───────────────────────────────────────────────────

    /**
     * Generate a grid of synthetic buildings in Midtown Manhattan when the bundled
     * resource is unavailable.
     */
    private List<BuildingRecord> generateSyntheticBuildings()
    {
        Random rng = new Random(42); // deterministic seed
        List<BuildingRecord> buildings = new ArrayList<>();

        // Midtown Manhattan grid: ~40.748 to 40.760 N, ~-73.990 to -73.970 W
        double baseLat = 40.748;
        double baseLon = -73.990;
        double latStep = 0.0008; // ~90 m block
        double lonStep = 0.0012; // ~90 m block
        int rows = 15;
        int cols = 17;

        for (int r = 0; r < rows; r++)
        {
            for (int c = 0; c < cols; c++)
            {
                double lat = baseLat + r * latStep;
                double lon = baseLon + c * lonStep;

                // Randomise building footprint size
                double w = 0.0002 + rng.nextDouble() * 0.0003; // width in degrees
                double h = 0.0001 + rng.nextDouble() * 0.0002; // height in degrees

                // Height: mix of low and tall buildings, taller toward centre
                double distFromCentre = Math.abs(r - rows / 2.0) / rows + Math.abs(c - cols / 2.0) / cols;
                double baseHeight = 15 + rng.nextDouble() * 30;
                double centreBonus = (1 - distFromCentre) * 200 * rng.nextDouble();
                double buildingHeight = baseHeight + centreBonus;

                List<LatLon> footprint = List.of(
                    LatLon.fromDegrees(lat, lon),
                    LatLon.fromDegrees(lat, lon + w),
                    LatLon.fromDegrees(lat + h, lon + w),
                    LatLon.fromDegrees(lat + h, lon),
                    LatLon.fromDegrees(lat, lon) // close ring
                );

                int levels = (int) Math.round(buildingHeight / 3.5);
                String id = "synthetic/" + r + "/" + c;
                String type = buildingHeight > 100 ? "office" : (rng.nextBoolean() ? "residential" : "commercial");

                buildings.add(new BuildingRecord(id, footprint, Collections.emptyList(),
                    buildingHeight, levels, null, type, null));
            }
        }

        return buildings;
    }
}
