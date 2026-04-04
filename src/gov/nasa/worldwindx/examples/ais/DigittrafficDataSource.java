/*
 * WorldWind Reforged — AIS Vessel Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: live AIS data source using Finland's Digitraffic Maritime API.
 * AIS data provided by Fintraffic / digitraffic.fi, licensed under CC 4.0 BY.
 */
package gov.nasa.worldwindx.examples.ais;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import gov.nasa.worldwind.util.Logging;

/**
 * Fetches live AIS vessel data from Finland's
 * <a href="https://www.digitraffic.fi/en/marine-traffic/">Digitraffic Maritime API</a>.
 * No API key required — data is open under CC BY 4.0.
 * <p>
 * The locations endpoint returns a GeoJSON FeatureCollection; the vessels endpoint
 * returns a flat JSON array. Both are parsed with Jackson streaming for efficiency.
 *
 * seaglassfoundry.com
 */
public class DigittrafficDataSource implements AISDataSource
{
    private static final String LOCATIONS_URL =
        "https://meri.digitraffic.fi/api/ais/v1/locations";
    private static final String VESSELS_URL =
        "https://meri.digitraffic.fi/api/ais/v1/vessels";
    private static final String USER_AGENT =
        "WorldWindReforged/1.0 (seaglassfoundry.com)";

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();
    private final JsonFactory jsonFactory = new JsonFactory();

    @Override
    public String getSourceLabel() { return "Digitraffic"; }

    @Override
    public boolean isLive() { return true; }

    // ── Positions ─────────────────────────────────────────────────────────────

    @Override
    public Map<Integer, VesselPosition> fetchPositions()
    {
        try
        {
            String body = httpGet(LOCATIONS_URL);
            return parsePositions(body);
        }
        catch (Exception e)
        {
            Logging.logger().warning("DigittrafficDataSource: failed to fetch positions: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Parse the GeoJSON FeatureCollection from the locations endpoint.
     * <pre>
     * { "type":"FeatureCollection", "features":[
     *     { "type":"Feature",
     *       "geometry":{"type":"Point","coordinates":[lon,lat]},
     *       "properties":{"mmsi":..., "sog":..., "cog":..., "heading":..., "navStat":..., "timestampExternal":...}
     *     }, ...
     * ]}
     * </pre>
     */
    private Map<Integer, VesselPosition> parsePositions(String json) throws Exception
    {
        Map<Integer, VesselPosition> result = new HashMap<>();
        try (JsonParser p = jsonFactory.createParser(json))
        {
            // Advance to the "features" array
            while (p.nextToken() != null)
            {
                if (p.currentToken() == JsonToken.FIELD_NAME && "features".equals(p.currentName()))
                {
                    p.nextToken(); // START_ARRAY
                    break;
                }
            }
            if (p.currentToken() != JsonToken.START_ARRAY)
                return result;

            // Each element is a Feature object
            while (p.nextToken() == JsonToken.START_OBJECT)
            {
                VesselPosition vp = parseOneFeature(p);
                if (vp != null)
                    result.put(vp.getMmsi(), vp);
            }
        }
        return result;
    }

    /**
     * Parse a single Feature object. The parser is positioned at START_OBJECT on
     * entry and will be at END_OBJECT on return.
     */
    private VesselPosition parseOneFeature(JsonParser p) throws Exception
    {
        double lon = 0, lat = 0;
        int mmsi = 0, heading = 511, navStat = 0;
        double sog = 0, cog = 0;
        long timestamp = 0;

        while (p.nextToken() != JsonToken.END_OBJECT)
        {
            String field = p.currentName();
            p.nextToken();

            if ("geometry".equals(field) && p.currentToken() == JsonToken.START_OBJECT)
            {
                // Parse {"type":"Point","coordinates":[lon,lat]}
                while (p.nextToken() != JsonToken.END_OBJECT)
                {
                    String geoField = p.currentName();
                    p.nextToken();
                    if ("coordinates".equals(geoField) && p.currentToken() == JsonToken.START_ARRAY)
                    {
                        p.nextToken(); lon = p.getDoubleValue();
                        p.nextToken(); lat = p.getDoubleValue();
                        // skip any remaining coordinate elements (altitude)
                        while (p.nextToken() != JsonToken.END_ARRAY) { /* drain */ }
                    }
                    else
                    {
                        p.skipChildren();
                    }
                }
            }
            else if ("properties".equals(field) && p.currentToken() == JsonToken.START_OBJECT)
            {
                while (p.nextToken() != JsonToken.END_OBJECT)
                {
                    String propField = p.currentName();
                    p.nextToken();
                    switch (propField)
                    {
                        case "mmsi"              -> mmsi      = p.getIntValue();
                        case "sog"               -> sog       = p.getDoubleValue();
                        case "cog"               -> cog       = p.getDoubleValue();
                        case "heading"           -> heading   = p.getIntValue();
                        case "navStat"           -> navStat   = p.getIntValue();
                        case "timestampExternal" -> timestamp = p.getLongValue();
                        default                  -> p.skipChildren();
                    }
                }
            }
            else
            {
                p.skipChildren();
            }
        }

        if (mmsi == 0)
            return null;

        return new VesselPosition(mmsi, lat, lon, sog, cog, heading, navStat, timestamp);
    }

    // ── Vessel metadata ───────────────────────────────────────────────────────

    @Override
    public Map<Integer, VesselInfo> fetchVesselInfo()
    {
        try
        {
            String body = httpGet(VESSELS_URL);
            return parseVesselInfo(body);
        }
        catch (Exception e)
        {
            Logging.logger().warning("DigittrafficDataSource: failed to fetch vessel info: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Parse the JSON array from the vessels endpoint.
     * <pre>
     * [{ "mmsi":..., "name":..., "callSign":..., "imo":..., "shipType":...,
     *    "draught":..., "destination":...,
     *    "dimensions":{"referencePointA":...,"referencePointB":...,"referencePointC":...,"referencePointD":...}
     * }, ...]
     * </pre>
     */
    private Map<Integer, VesselInfo> parseVesselInfo(String json) throws Exception
    {
        Map<Integer, VesselInfo> result = new HashMap<>();
        try (JsonParser p = jsonFactory.createParser(json))
        {
            if (p.nextToken() != JsonToken.START_ARRAY)
                return result;

            while (p.nextToken() == JsonToken.START_OBJECT)
            {
                VesselInfo vi = parseOneVessel(p);
                if (vi != null)
                    result.put(vi.getMmsi(), vi);
            }
        }
        return result;
    }

    private VesselInfo parseOneVessel(JsonParser p) throws Exception
    {
        int mmsi = 0, imo = 0, shipType = 0, draught = 0;
        String name = "", callSign = "", destination = "";
        int refA = 0, refB = 0, refC = 0, refD = 0;

        while (p.nextToken() != JsonToken.END_OBJECT)
        {
            String field = p.currentName();
            p.nextToken();
            switch (field)
            {
                case "mmsi"        -> mmsi        = p.getIntValue();
                case "imo"         -> imo          = p.getIntValue();
                case "name"        -> name         = p.getText();
                case "callSign"    -> callSign     = p.getText();
                case "shipType"    -> shipType     = p.getIntValue();
                case "draught"     -> draught      = p.getIntValue();
                case "destination" -> destination  = p.getText();
                case "dimensions"  ->
                {
                    if (p.currentToken() == JsonToken.START_OBJECT)
                    {
                        while (p.nextToken() != JsonToken.END_OBJECT)
                        {
                            String dimField = p.currentName();
                            p.nextToken();
                            switch (dimField)
                            {
                                case "referencePointA" -> refA = p.getIntValue();
                                case "referencePointB" -> refB = p.getIntValue();
                                case "referencePointC" -> refC = p.getIntValue();
                                case "referencePointD" -> refD = p.getIntValue();
                                default -> p.skipChildren();
                            }
                        }
                    }
                    else
                    {
                        p.skipChildren();
                    }
                }
                default -> p.skipChildren();
            }
        }

        if (mmsi == 0)
            return null;

        return new VesselInfo(mmsi, name, callSign, imo, shipType, draught, destination,
            refA + refB, refC + refD);
    }

    // ── HTTP helper ───────────────────────────────────────────────────────────

    private String httpGet(String url) throws Exception
    {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(new URI(url))
            .header("User-Agent", USER_AGENT)
            .header("Accept-Encoding", "gzip")
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

        HttpResponse<java.io.InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200)
            throw new RuntimeException("HTTP " + resp.statusCode() + " from " + url);

        // Digitraffic requires gzip and always returns gzip-compressed responses
        java.io.InputStream bodyStream = new java.util.zip.GZIPInputStream(resp.body());
        return new String(bodyStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
