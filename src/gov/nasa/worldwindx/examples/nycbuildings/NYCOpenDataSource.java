/*
 * WorldWind Reforged — NYC Buildings 3D Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: live building data source using NYC Open Data (data.cityofnewyork.us).
 * Building footprints with HEIGHT_ROOF field on virtually every record. No API key
 * required. Supports view-based loading via within_box spatial queries.
 */
package gov.nasa.worldwindx.examples.nycbuildings;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.util.Logging;

/**
 * Fetches building footprints from
 * <a href="https://data.cityofnewyork.us/Housing-Development/Building-Footprints/nqwf-w8eh">NYC Open Data</a>
 * via the Socrata GeoJSON endpoint. No API key required.
 * <p>
 * Supports <b>view-based loading</b> via {@code within_box} spatial queries: pass the
 * visible bounding box and only buildings in that area are returned. Also supports a
 * {@code tallOnly} mode that adds {@code height_roof > 100} (feet) for fast initial
 * skyline loading.
 * <p>
 * Successful responses are cached per grid cell to {@code ~/.worldwind/nyc_cells/}
 * so repeated pans don't re-download previously visited areas.
 *
 * <p>Data: NYC Open Data, provided by NYC Department of Information Technology
 * &amp; Telecommunications (DoITT). Public domain (CC0).</p>
 *
 * seaglassfoundry.com — new data source for WorldWind Reforged NYC Buildings demo
 */
public class NYCOpenDataSource implements BuildingDataSource
{
    private static final String BASE_URL = "https://data.cityofnewyork.us/resource/5zhs-2jue.geojson";
    private static final String USER_AGENT = "WorldWindReforged/1.0 (seaglassfoundry.com)";
    private static final int PAGE_SIZE = 50000;
    private static final long CACHE_MAX_AGE_HOURS = 24;

    private static final Path CACHE_DIR = Path.of(System.getProperty("user.home"), ".worldwind", "nyc_cells");

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();
    private final JsonFactory jsonFactory = new JsonFactory();

    private boolean tallOnly;

    /** Create a data source. If {@code tallOnly} is true, only fetches buildings taller than ~30 m. */
    public NYCOpenDataSource(boolean tallOnly)
    {
        this.tallOnly = tallOnly;
    }

    public NYCOpenDataSource()
    {
        this(false);
    }

    public void setTallOnly(boolean tallOnly) { this.tallOnly = tallOnly; }
    public boolean isTallOnly() { return tallOnly; }

    @Override
    public String getSourceLabel() { return "NYC Open Data"; }

    @Override
    public boolean isLive() { return true; }

    /**
     * Fetch buildings within the given bounding box using {@code within_box} spatial
     * query. The bbox parameters define the visible area.
     */
    @Override
    public List<BuildingRecord> fetchBuildings(double south, double west, double north, double east)
    {
        // Try disk cache first
        String cellKey = cellKey(south, west, north, east);
        List<BuildingRecord> cached = loadFromCache(cellKey, false);
        if (cached != null && !cached.isEmpty())
        {
            Logging.logger().info("NYCOpenDataSource: cache hit for " + cellKey
                + " (" + cached.size() + " buildings)");
            return cached;
        }

        // Build within_box query
        try
        {
            String where = buildWithinBoxWhere(south, west, north, east);
            List<BuildingRecord> records = new ArrayList<>();
            String firstPageJson = null;
            int offset = 0;

            while (true)
            {
                String url = buildUrl(where, PAGE_SIZE, offset);
                String pageJson = httpGet(url);
                List<BuildingRecord> page = parseGeoJson(pageJson);
                records.addAll(page);

                if (firstPageJson == null)
                    firstPageJson = pageJson;

                Logging.logger().info("NYCOpenDataSource: fetched " + cellKey
                    + " offset=" + offset + " (" + page.size() + " buildings)");

                if (page.size() < PAGE_SIZE)
                    break;

                offset += PAGE_SIZE;
            }

            // Cache the first page (for single-page results this is the full response)
            if (!records.isEmpty() && firstPageJson != null)
                saveToCache(cellKey, firstPageJson);

            return records;
        }
        catch (Exception e)
        {
            Logging.logger().warning("NYCOpenDataSource: fetch failed for " + cellKey + ": " + e.getMessage());

            // Try expired cache
            List<BuildingRecord> expired = loadFromCache(cellKey, true);
            if (expired != null && !expired.isEmpty())
            {
                Logging.logger().info("NYCOpenDataSource: using expired cache for " + cellKey);
                return expired;
            }

            return Collections.emptyList();
        }
    }

    // ── Query construction ──────────────────────────────────────────────────────

    private String buildWithinBoxWhere(double south, double west, double north, double east)
    {
        // within_box(column, nwLat, nwLon, seLat, seLon)
        String bbox = String.format("within_box(the_geom,%.6f,%.6f,%.6f,%.6f)", north, west, south, east);
        if (tallOnly)
            return bbox + " AND height_roof>100";
        return bbox;
    }

    private String buildUrl(String where, int limit, int offset)
    {
        StringBuilder sb = new StringBuilder(BASE_URL);
        sb.append("?$where=").append(urlEncode(where));
        sb.append("&$limit=").append(limit);
        if (offset > 0)
            sb.append("&$offset=").append(offset);
        return sb.toString();
    }

    private static String urlEncode(String s)
    {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ── HTTP ────────────────────────────────────────────────────────────────────

    private String httpGet(String url) throws Exception
    {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        int status = resp.statusCode();
        if (status == 429)
            throw new RuntimeException("HTTP 429 — rate limited by NYC Open Data (try again shortly)");
        if (status != 200)
            throw new RuntimeException("HTTP " + status);

        return resp.body();
    }

    // ── Per-cell disk cache ─────────────────────────────────────────────────────

    private static String cellKey(double south, double west, double north, double east)
    {
        return String.format("%.4f_%.4f_%.4f_%.4f%s",
            south, west, north, east, ""); // tallOnly encoded in filename
    }

    private Path cacheFile(String cellKey)
    {
        String suffix = tallOnly ? "_tall" : "_all";
        return CACHE_DIR.resolve(cellKey + suffix + ".json");
    }

    private List<BuildingRecord> loadFromCache(String cellKey, boolean ignoreAge)
    {
        try
        {
            Path file = cacheFile(cellKey);
            if (!Files.exists(file)) return null;

            if (!ignoreAge)
            {
                Instant modified = Files.getLastModifiedTime(file).toInstant();
                if (Duration.between(modified, Instant.now()).toHours() > CACHE_MAX_AGE_HOURS)
                    return null;
            }

            String json = Files.readString(file);
            return parseGeoJson(json);
        }
        catch (Exception e)
        {
            Logging.logger().fine("NYCOpenDataSource: cache read failed: " + e.getMessage());
            return null;
        }
    }

    private void saveToCache(String cellKey, String json)
    {
        try
        {
            Files.createDirectories(CACHE_DIR);
            Files.writeString(cacheFile(cellKey), json);
        }
        catch (IOException e)
        {
            Logging.logger().fine("NYCOpenDataSource: cache write failed: " + e.getMessage());
        }
    }

    // ── GeoJSON parser ──────────────────────────────────────────────────────────

    /**
     * Parse a Socrata GeoJSON FeatureCollection. Each feature:
     * <pre>
     * { "type":"Feature",
     *   "properties":{ "bin":"1234567", "name":"...", "height_roof":"1454.00", ... },
     *   "geometry":{ "type":"Polygon", "coordinates":[[[lon,lat],...]] }
     * }
     * </pre>
     */
    List<BuildingRecord> parseGeoJson(String json) throws Exception
    {
        List<BuildingRecord> records = new ArrayList<>();

        try (JsonParser jp = jsonFactory.createParser(json))
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
        String bin = null, name = null, featureCode = null;
        String heightRoof = null;
        List<List<double[]>> rings = null;

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
                        jp.nextToken();
                        switch (prop)
                        {
                            case "bin" -> bin = jp.getValueAsString();
                            case "name" -> name = jp.getValueAsString();
                            case "height_roof" -> heightRoof = jp.getValueAsString();
                            case "feature_code" -> featureCode = jp.getValueAsString();
                        }
                    }
                }
                case "geometry" ->
                {
                    jp.nextToken(); // START_OBJECT
                    String geomType = null;
                    while (jp.nextToken() != JsonToken.END_OBJECT)
                    {
                        if (jp.currentToken() != JsonToken.FIELD_NAME) continue;
                        String geomField = jp.currentName();

                        if ("type".equals(geomField))
                        {
                            jp.nextToken();
                            geomType = jp.getValueAsString();
                        }
                        else if ("coordinates".equals(geomField))
                        {
                            if ("MultiPolygon".equals(geomType))
                                rings = parseMultiPolygonCoordinates(jp);
                            else
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

        // Convert outer ring to LatLon (GeoJSON is [lon, lat])
        List<LatLon> footprint = new ArrayList<>();
        for (double[] coord : rings.get(0))
            footprint.add(LatLon.fromDegrees(coord[1], coord[0]));

        // Inner rings (holes)
        List<List<LatLon>> holes = new ArrayList<>();
        for (int i = 1; i < rings.size(); i++)
        {
            List<LatLon> hole = new ArrayList<>();
            for (double[] coord : rings.get(i))
                hole.add(LatLon.fromDegrees(coord[1], coord[0]));
            holes.add(hole);
        }

        // HEIGHT_ROOF is in feet — convert to metres
        double heightMeters = parseHeightFeet(heightRoof);
        if (heightMeters <= 0)
            heightMeters = 12; // NYC default low-rise

        int levels = Math.max(1, (int) Math.round(heightMeters / 3.5));

        // Build a display-friendly type from feature_code
        String buildingType = resolveFeatureCode(featureCode);

        String id = bin != null ? "BIN/" + bin : "unknown";

        return new BuildingRecord(id, footprint, holes,
            heightMeters, levels, name, buildingType, "");
    }

    /** Parse HEIGHT_ROOF from feet string to metres. */
    private static double parseHeightFeet(String heightRoof)
    {
        if (heightRoof == null || heightRoof.isEmpty()) return 0;
        try
        {
            double feet = Double.parseDouble(heightRoof.trim());
            return feet > 0 ? feet * 0.3048 : 0;
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    /** Map NYC feature_code to a building type string. */
    private static String resolveFeatureCode(String code)
    {
        if (code == null || code.isEmpty()) return "building";
        return switch (code)
        {
            case "2100" -> "building";
            case "5100" -> "building (under construction)";
            case "5110" -> "garage";
            case "2110" -> "skybridge";
            default -> "building";
        };
    }

    // ── Coordinate parsers ──────────────────────────────────────────────────────

    /**
     * Parse GeoJSON Polygon coordinates: [ [ [lon,lat], ... ], [ [lon,lat], ... ] ]
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
                jp.nextToken();
                double lon = jp.getDoubleValue();
                jp.nextToken();
                double lat = jp.getDoubleValue();
                while (jp.nextToken() != JsonToken.END_ARRAY) { /* skip altitude */ }
                ring.add(new double[]{lon, lat});
            }
            rings.add(ring);
        }

        return rings;
    }

    /**
     * Parse GeoJSON MultiPolygon coordinates: [ [ [ [lon,lat], ... ] ], ... ]
     * Uses only the first polygon.
     */
    private List<List<double[]>> parseMultiPolygonCoordinates(JsonParser jp) throws Exception
    {
        jp.nextToken(); // START_ARRAY (outer array of polygons)

        List<List<double[]>> firstPolygonRings = null;

        while (jp.nextToken() == JsonToken.START_ARRAY) // each polygon
        {
            List<List<double[]>> rings = new ArrayList<>();
            while (jp.nextToken() == JsonToken.START_ARRAY) // each ring
            {
                List<double[]> ring = new ArrayList<>();
                while (jp.nextToken() == JsonToken.START_ARRAY) // each coordinate
                {
                    jp.nextToken();
                    double lon = jp.getDoubleValue();
                    jp.nextToken();
                    double lat = jp.getDoubleValue();
                    while (jp.nextToken() != JsonToken.END_ARRAY) { /* skip altitude */ }
                    ring.add(new double[]{lon, lat});
                }
                rings.add(ring);
            }

            if (firstPolygonRings == null)
                firstPolygonRings = rings;
        }

        return firstPolygonRings != null ? firstPolygonRings : Collections.emptyList();
    }
}
