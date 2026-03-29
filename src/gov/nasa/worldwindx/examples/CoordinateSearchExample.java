/*
 * WorldWind Reforged — CoordinateSearchExample
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New example — geocoder search using Nominatim (OSM), replacing defunct GazetteerApp.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Open geocoder search using the <a href="https://nominatim.openstreetmap.org/">Nominatim</a>
 * (OpenStreetMap) API, replacing the defunct Yahoo Gazetteer functionality.
 * <p>
 * <b>Usage:</b>
 * <ol>
 *   <li>Type a place name (e.g. "Grand Canyon", "Tokyo", "51.5,-0.1") in the search field.</li>
 *   <li>Click <b>Fly To</b> or press Enter.</li>
 *   <li>The globe animates to the top result; the result name and bounding box are shown below.</li>
 * </ol>
 * Requests are rate-limited to 1 per second per the
 * <a href="https://operations.osmfoundation.org/policies/nominatim/">Nominatim usage policy</a>.
 * Network access runs in a background thread so the globe stays responsive.
 *
 * @see java.net.http.HttpClient
 *
 * seaglassfoundry.com — new example for WorldWind Reforged
 */
public class CoordinateSearchExample extends ApplicationTemplate
{
    /** Nominatim API endpoint — returns JSON, top 1 result. */
    private static final String NOMINATIM_URL =
        "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=";

    /** User-Agent required by Nominatim policy. */
    private static final String USER_AGENT = "WorldWindReforged/1.0 (seaglassfoundry.com)";

    // ── AppFrame ──────────────────────────────────────────────────────────────

    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;
        private JTextField  searchField;
        private JButton     flyBtn;
        private JLabel      resultLabel;
        private JLabel      statusLabel;

        /** Tracks the time of the last request (ms) for rate limiting. */
        private final AtomicLong lastRequestTime = new AtomicLong(0);

        private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

        public AppFrame()
        {
            super(true, true, false);
            getControlPanel().add(buildControlPanel(), BorderLayout.SOUTH);
        }

        // ── Search ────────────────────────────────────────────────────────────

        private void search()
        {
            String query = searchField.getText().trim();
            if (query.isEmpty())
                return;

            // Rate-limit: enforce 1 s between requests
            long now = System.currentTimeMillis();
            long last = lastRequestTime.get();
            if (now - last < 1000) {
                setStatus("Please wait before searching again.");
                return;
            }
            lastRequestTime.set(now);

            flyBtn.setEnabled(false);
            setStatus("Searching…");
            resultLabel.setText(" ");

            new SwingWorker<NominatimResult, Void>()
            {
                @Override
                protected NominatimResult doInBackground() throws Exception
                {
                    String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
                    HttpRequest req = HttpRequest.newBuilder()
                        .uri(new URI(NOMINATIM_URL + encoded))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept-Language", "en")
                        .timeout(java.time.Duration.ofSeconds(10))
                        .GET()
                        .build();

                    HttpResponse<String> resp =
                        http.send(req, HttpResponse.BodyHandlers.ofString());

                    if (resp.statusCode() != 200)
                        throw new RuntimeException("HTTP " + resp.statusCode());

                    return parseFirstResult(resp.body());
                }

                @Override
                protected void done()
                {
                    flyBtn.setEnabled(true);
                    try {
                        NominatimResult r = get();
                        if (r == null) {
                            setStatus("No results found.");
                            return;
                        }
                        flyToResult(r);
                        resultLabel.setText("<html><b>" + r.displayName + "</b></html>");
                        setStatus(String.format("Bounding box: %.4f, %.4f → %.4f, %.4f",
                            r.minLat, r.minLon, r.maxLat, r.maxLon));
                    } catch (Exception ex) {
                        Logging.logger().warning("CoordinateSearchExample: " + ex.getMessage());
                        setStatus("Error: " + ex.getMessage());
                    }
                }
            }.execute();
        }

        private void flyToResult(NominatimResult r)
        {
            double lat = (r.minLat + r.maxLat) / 2.0;
            double lon = (r.minLon + r.maxLon) / 2.0;
            double spanDeg = Math.max(r.maxLat - r.minLat, r.maxLon - r.minLon);
            double alt = Math.max(spanDeg * 111_000 * 2.0, 10_000);  // at least 10 km
            getWwd().getView().setEyePosition(Position.fromDegrees(lat, lon, alt));
            getWwd().redraw();
        }

        // ── Nominatim JSON parser ─────────────────────────────────────────────

        /**
         * Parses the first element of the Nominatim JSON array using the streaming
         * {@link JsonParser} (jackson-core).  Only reads the fields we need.
         */
        private static NominatimResult parseFirstResult(String json) throws Exception
        {
            JsonFactory factory = new JsonFactory();
            try (JsonParser p = factory.createParser(json)) {
                // Expect outer array
                // Move to first object
                if ((p.nextToken() != JsonToken.START_ARRAY) || (p.nextToken() != JsonToken.START_OBJECT)) return null;

                String displayName = "";
                double lat = 0, lon = 0;
                double minLat = 0, maxLat = 0, minLon = 0, maxLon = 0;

                while (p.nextToken() != JsonToken.END_OBJECT) {
                    String field = p.currentName();
                    p.nextToken();
                    switch (field) {
                        case "display_name" -> displayName = p.getText();
                        case "lat"          -> lat = Double.parseDouble(p.getText());
                        case "lon"          -> lon = Double.parseDouble(p.getText());
                        case "boundingbox"  -> {
                            // Array: [minLat, maxLat, minLon, maxLon]
                            if (p.currentToken() == JsonToken.START_ARRAY) {
                                minLat = Double.parseDouble(p.nextTextValue());
                                maxLat = Double.parseDouble(p.nextTextValue());
                                minLon = Double.parseDouble(p.nextTextValue());
                                maxLon = Double.parseDouble(p.nextTextValue());
                                p.nextToken(); // END_ARRAY
                            }
                        }
                        default             -> p.skipChildren();
                    }
                }

                // Fall back to point if no bounding box
                if (minLat == 0 && maxLat == 0) {
                    minLat = maxLat = lat;
                    minLon = maxLon = lon;
                }
                return new NominatimResult(displayName, lat, lon,
                    minLat, maxLat, minLon, maxLon);
            }
        }

        // ── Control panel ─────────────────────────────────────────────────────

        private JPanel buildControlPanel()
        {
            JPanel root = new JPanel();
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            root.setBackground(WWStyle.BG_DARK);

            // ── Search section ────────────────────────────────────────────────
            JPanel searchSection = new JPanel();
            searchSection.setLayout(new BoxLayout(searchSection, BoxLayout.Y_AXIS));
            searchSection.setBackground(WWStyle.BG_DARK);
            searchSection.setBorder(WWStyle.sectionBorder("Geocoder Search (Nominatim/OSM)"));

            searchField = new JTextField();
            searchField.setFont(WWStyle.FONT_BASE);
            searchField.setForeground(WWStyle.FG_PRIMARY);
            searchField.setBackground(WWStyle.BG_PANEL);
            searchField.setCaretColor(WWStyle.FG_PRIMARY);
            searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
            searchField.addActionListener(e -> search());  // Enter key

            flyBtn = WWStyle.accentButton("Fly To");
            flyBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            flyBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            flyBtn.addActionListener(e -> search());

            searchSection.add(WWStyle.label("Place name or coordinates:", false));
            searchSection.add(vgap(WWStyle.GAP_XS));
            searchSection.add(searchField);
            searchSection.add(vgap(WWStyle.GAP_XS));
            searchSection.add(flyBtn);
            root.add(searchSection);
            root.add(vgap(WWStyle.GAP_XS));

            // ── Result section ────────────────────────────────────────────────
            JPanel resultSection = new JPanel();
            resultSection.setLayout(new BoxLayout(resultSection, BoxLayout.Y_AXIS));
            resultSection.setBackground(WWStyle.BG_DARK);
            resultSection.setBorder(WWStyle.sectionBorder("Result"));

            resultLabel = new JLabel(" ");
            resultLabel.setFont(WWStyle.FONT_BASE);
            resultLabel.setForeground(WWStyle.ACCENT);
            resultLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            statusLabel = WWStyle.label("Enter a place name and click Fly To.", false);
            statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            resultSection.add(resultLabel);
            resultSection.add(vgap(WWStyle.GAP_XS));
            resultSection.add(statusLabel);
            root.add(resultSection);

            return root;
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private void setStatus(String msg)
        {
            SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
        }

        private static JPanel vgap(int height)
        {
            JPanel p = new JPanel();
            p.setBackground(WWStyle.BG_DARK);
            p.setPreferredSize(new Dimension(0, height));
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            return p;
        }

        // ── Data record ───────────────────────────────────────────────────────

        private record NominatimResult(
            String displayName,
            double lat, double lon,
            double minLat, double maxLat,
            double minLon, double maxLon) {}
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind — Coordinate Search (Nominatim)", AppFrame.class);
    }
}
