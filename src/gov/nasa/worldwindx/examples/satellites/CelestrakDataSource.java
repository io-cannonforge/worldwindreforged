/*
 * WorldWind Reforged — Satellite Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: live satellite TLE data source using CelesTrak's GP data API.
 * TLE data provided by CelesTrak (celestrak.org) — free, no API key required.
 */
package gov.nasa.worldwindx.examples.satellites;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import gov.nasa.worldwind.util.Logging;

/**
 * Fetches live satellite TLE data from
 * <a href="https://celestrak.org">CelesTrak</a>'s GP data API in JSON format.
 * No API key required. TLEs are updated roughly every 8-12 hours by CelesTrak.
 * <p>
 * Fetches multiple satellite groups (stations, weather, GNSS, visual, active)
 * and merges them into a single map keyed by NORAD catalogue ID.
 *
 * seaglassfoundry.com
 */
public class CelestrakDataSource implements SatelliteDataSource
{
    private static final String BASE_URL =
        "https://celestrak.org/NORAD/elements/gp.php?FORMAT=json&GROUP=";
    private static final String USER_AGENT =
        "WorldWindReforged/1.0 (seaglassfoundry.com)";

    /** Satellite groups to fetch. Fetched in order; later groups may add new satellites. */
    private static final String[] GROUPS = {
        "stations",     // ISS, Tiangong, etc. (~10)
        "visual",       // brightest satellites (~200)
        "weather",      // weather satellites (~50)
        "gnss",         // GPS, GLONASS, Galileo, BeiDou (~120)
        "resource",     // Earth resources (~50)
        "science",      // science satellites (~80)
        "geo",          // geostationary (~500)
        "active",       // all active satellites (~8000, includes Starlink)
    };

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final JsonFactory jsonFactory = new JsonFactory();

    @Override
    public String getSourceLabel() { return "CelesTrak"; }

    @Override
    public boolean isLive() { return true; }

    @Override
    public Map<Integer, TleRecord> fetchTLEs()
    {
        Map<Integer, TleRecord> result = new HashMap<>();

        for (String group : GROUPS)
        {
            try
            {
                Map<Integer, TleRecord> groupResult = fetchGroup(group);
                result.putAll(groupResult);
                Logging.logger().info("CelesTrak: fetched " + groupResult.size()
                    + " satellites from group '" + group + "'");
            }
            catch (Exception e)
            {
                Logging.logger().warning("CelesTrak: failed to fetch group '" + group
                    + "': " + e.getMessage());
            }
        }

        return result;
    }

    private Map<Integer, TleRecord> fetchGroup(String group) throws Exception
    {
        String url = BASE_URL + group;
        String body = httpGet(url);
        return parseGpJson(body);
    }

    /**
     * Parse CelesTrak GP JSON format. Each element is an object with fields like:
     * <pre>
     * { "OBJECT_NAME":"ISS (ZARYA)", "OBJECT_ID":"1998-067A", "EPOCH":"2024-...",
     *   "MEAN_MOTION":15.49, "ECCENTRICITY":0.0006, "INCLINATION":51.64,
     *   "RA_OF_ASC_NODE":..., "ARG_OF_PERICENTER":..., "MEAN_ANOMALY":...,
     *   "NORAD_CAT_ID":25544, "BSTAR":..., "REV_AT_EPOCH":...,
     *   "TLE_LINE0":"0 ISS (ZARYA)", "TLE_LINE1":"1 ...", "TLE_LINE2":"2 ..." }
     * </pre>
     */
    private Map<Integer, TleRecord> parseGpJson(String json) throws Exception
    {
        Map<Integer, TleRecord> result = new HashMap<>();
        try (JsonParser p = jsonFactory.createParser(json))
        {
            if (p.nextToken() != JsonToken.START_ARRAY)
                return result;

            while (p.nextToken() == JsonToken.START_OBJECT)
            {
                TleRecord rec = parseOneGpObject(p);
                if (rec != null)
                    result.put(rec.getNoradCatId(), rec);
            }
        }
        return result;
    }

    private TleRecord parseOneGpObject(JsonParser p) throws Exception
    {
        String objectName = "", intlDes = "", tleLine1 = "", tleLine2 = "";
        int noradCatId = 0, revAtEpoch = 0;
        double meanMotion = 0, eccentricity = 0, inclination = 0;
        double raan = 0, argPericenter = 0, meanAnomaly = 0, bstarVal = 0;
        String epoch = "";

        while (p.nextToken() != JsonToken.END_OBJECT)
        {
            String field = p.currentName();
            p.nextToken();

            if (p.currentToken() == JsonToken.VALUE_NULL) continue;

            switch (field)
            {
                case "OBJECT_NAME"       -> objectName    = p.getText();
                case "OBJECT_ID"         -> intlDes       = p.getText();
                case "NORAD_CAT_ID"      -> noradCatId    = p.getIntValue();
                case "EPOCH"             -> epoch          = p.getText();
                case "MEAN_MOTION"       -> meanMotion     = p.getDoubleValue();
                case "ECCENTRICITY"      -> eccentricity   = p.getDoubleValue();
                case "INCLINATION"       -> inclination    = p.getDoubleValue();
                case "RA_OF_ASC_NODE"    -> raan           = p.getDoubleValue();
                case "ARG_OF_PERICENTER" -> argPericenter  = p.getDoubleValue();
                case "MEAN_ANOMALY"      -> meanAnomaly    = p.getDoubleValue();
                case "BSTAR"             -> bstarVal       = p.getDoubleValue();
                case "REV_AT_EPOCH"      -> revAtEpoch     = p.getIntValue();
                case "TLE_LINE1"         -> tleLine1       = p.getText();
                case "TLE_LINE2"         -> tleLine2       = p.getText();
                default -> p.skipChildren();
            }
        }

        if (noradCatId == 0 || meanMotion <= 0) return null;

        // Convert epoch string to Julian date
        double epochJd = parseEpochToJd(epoch);
        if (epochJd == 0) return null;

        return new TleRecord(noradCatId, objectName, intlDes, epochJd, meanMotion,
            eccentricity, inclination, raan, argPericenter, meanAnomaly,
            bstarVal, revAtEpoch, tleLine1, tleLine2);
    }

    /**
     * Parse ISO 8601 epoch string to Julian date.
     * Format: "2024-03-15T12:34:56.789" or "2024-03-15T12:34:56.789Z"
     */
    private static double parseEpochToJd(String epoch)
    {
        try
        {
            if (epoch == null || epoch.isEmpty()) return 0;
            // Remove trailing Z if present
            if (epoch.endsWith("Z")) epoch = epoch.substring(0, epoch.length() - 1);

            String[] parts = epoch.split("T");
            String[] dateParts = parts[0].split("-");
            int year = Integer.parseInt(dateParts[0]);
            int month = Integer.parseInt(dateParts[1]);
            int day = Integer.parseInt(dateParts[2]);

            double fracDay = 0;
            if (parts.length > 1)
            {
                String[] timeParts = parts[1].split(":");
                double hour = Double.parseDouble(timeParts[0]);
                double min = timeParts.length > 1 ? Double.parseDouble(timeParts[1]) : 0;
                double sec = timeParts.length > 2 ? Double.parseDouble(timeParts[2]) : 0;
                fracDay = (hour + min / 60.0 + sec / 3600.0) / 24.0;
            }

            // Julian date calculation
            int a = (14 - month) / 12;
            int y = year + 4800 - a;
            int m = month + 12 * a - 3;
            double jdn = day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;

            return jdn + fracDay - 0.5;
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    private String httpGet(String url) throws Exception
    {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(new URI(url))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200)
            throw new RuntimeException("HTTP " + resp.statusCode() + " from " + url);

        // CelesTrak may or may not gzip; check content-encoding
        InputStream bodyStream = resp.body();
        String encoding = resp.headers().firstValue("Content-Encoding").orElse("");
        if (encoding.contains("gzip"))
            bodyStream = new java.util.zip.GZIPInputStream(bodyStream);

        return new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
