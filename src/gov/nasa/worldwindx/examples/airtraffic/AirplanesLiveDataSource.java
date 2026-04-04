/*
 * WorldWind Reforged — Air Traffic Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: live ADS-B data source using airplanes.live free API.
 * No API key required. Rate limit: 1 request/second.
 */
package gov.nasa.worldwindx.examples.airtraffic;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import gov.nasa.worldwind.util.Logging;

/**
 * Fetches live ADS-B aircraft data from
 * <a href="https://airplanes.live">airplanes.live</a>. No API key required.
 * <p>
 * Endpoints used:
 * <ul>
 *   <li>{@code /v2/point/{lat}/{lon}/{radius_nm}} — aircraft near a point (max 250 nm)</li>
 *   <li>{@code /v2/mil} — all military aircraft worldwide</li>
 * </ul>
 *
 * seaglassfoundry.com
 */
public class AirplanesLiveDataSource implements AirTrafficDataSource
{
    private static final String BASE_URL = "https://api.airplanes.live/v2";
    private static final String USER_AGENT = "WorldWindReforged/1.0 (seaglassfoundry.com)";

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();
    private final JsonFactory jsonFactory = new JsonFactory();

    @Override
    public String getSourceLabel() { return "airplanes.live"; }

    @Override
    public Map<String, AircraftPosition> fetchAircraft(double lat, double lon, int radiusNm)
    {
        try
        {
            String url = String.format("%s/point/%.4f/%.4f/%d", BASE_URL, lat, lon,
                Math.min(radiusNm, 250));
            String json = httpGet(url);
            return parseAircraftArray(json, false);
        }
        catch (Exception e)
        {
            Logging.logger().warning("AirplanesLiveDataSource: fetch failed: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, AircraftPosition> fetchMilitary()
    {
        try
        {
            String json = httpGet(BASE_URL + "/mil");
            return parseAircraftArray(json, true);
        }
        catch (Exception e)
        {
            Logging.logger().warning("AirplanesLiveDataSource: military fetch failed: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    // ── JSON parsing ──────────────────────────────────────────────────────────

    /**
     * Parse the airplanes.live response: {@code {"ac":[...], "total":N, ...}}.
     * Each aircraft is a JSON object with named fields.
     */
    private Map<String, AircraftPosition> parseAircraftArray(String json, boolean markMilitary) throws Exception
    {
        Map<String, AircraftPosition> result = new HashMap<>();
        try (JsonParser p = jsonFactory.createParser(json))
        {
            // Advance to "ac" array
            while (p.nextToken() != null)
            {
                if (p.currentToken() == JsonToken.FIELD_NAME && "ac".equals(p.currentName()))
                {
                    p.nextToken(); // START_ARRAY
                    break;
                }
            }
            if (p.currentToken() != JsonToken.START_ARRAY)
                return result;

            while (p.nextToken() == JsonToken.START_OBJECT)
            {
                AircraftPosition ac = parseOneAircraft(p, markMilitary);
                if (ac != null && ac.getLat() != 0 && ac.getLon() != 0)
                    result.put(ac.getHex(), ac);
            }
        }
        return result;
    }

    private AircraftPosition parseOneAircraft(JsonParser p, boolean markMilitary) throws Exception
    {
        String hex = "", callsign = "", reg = "", typeCode = "", typeDesc = "";
        String operator = "", squawk = "", emergency = "none", category = "";
        double lat = 0, lon = 0, altFeet = 0, gs = 0, track = 0;
        double trueHeading = Double.NaN, vertRate = 0;
        boolean onGround = false, isMil = markMilitary;
        long timestamp = System.currentTimeMillis();

        while (p.nextToken() != JsonToken.END_OBJECT)
        {
            String field = p.currentName();
            p.nextToken();

            switch (field)
            {
                case "hex"       -> hex       = p.getText();
                case "flight"    -> callsign  = p.getText();
                case "r"         -> reg       = p.getText();
                case "t"         -> typeCode  = p.getText();
                case "desc"      -> typeDesc  = p.getText();
                case "ownOp"     -> operator  = p.getText();
                case "squawk"    -> squawk    = p.getText();
                case "emergency" -> emergency = p.getText();
                case "category"  -> category  = p.getText();
                case "lat"       -> lat       = p.getDoubleValue();
                case "lon"       -> lon       = p.getDoubleValue();
                case "gs"        -> gs        = p.getDoubleValue();
                case "track"     -> track     = p.getDoubleValue();
                case "true_heading" -> trueHeading = p.getDoubleValue();
                case "baro_rate" -> vertRate  = p.getDoubleValue();
                case "alt_baro"  ->
                {
                    // Can be an int (feet) or the string "ground"
                    if (p.currentToken() == JsonToken.VALUE_STRING)
                    {
                        onGround = true;
                        altFeet = 0;
                    }
                    else
                    {
                        altFeet = p.getDoubleValue();
                    }
                }
                case "dbFlags"   ->
                {
                    // Bit 1 = military
                    if (p.currentToken() == JsonToken.VALUE_NUMBER_INT && (p.getIntValue() & 1) != 0)
                        isMil = true;
                    else
                        p.skipChildren();
                }
                default -> p.skipChildren();
            }
        }

        if (hex.isEmpty())
            return null;

        return new AircraftPosition(hex, callsign, reg, typeCode, typeDesc, operator,
            lat, lon, altFeet, onGround, gs, track, trueHeading, vertRate,
            squawk, emergency, category, isMil, timestamp);
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private String httpGet(String url) throws Exception
    {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(new URI(url))
            .header("User-Agent", USER_AGENT)
            .header("Accept-Encoding", "gzip")
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build();

        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200)
            throw new RuntimeException("HTTP " + resp.statusCode() + " from " + url);

        InputStream body = resp.body();
        String encoding = resp.headers().firstValue("Content-Encoding").orElse("");
        if ("gzip".equalsIgnoreCase(encoding))
            body = new GZIPInputStream(body);

        return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }
}
