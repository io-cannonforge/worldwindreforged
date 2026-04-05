/*
 * WorldWind Reforged — Satellite Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: Swing filter/stats panel for the satellite tracker demo. Provides
 * category toggles, altitude presets, display options, ISS tracking, UTC clock,
 * live statistics, search, and orbit altitude colour legend.
 */
package gov.nasa.worldwindx.examples.satellites;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Rich control panel for the Satellite Tracker demo: UTC clock, live statistics,
 * ISS quick-track button, category checkboxes, display toggles, search, and an
 * orbit altitude colour legend.
 *
 * seaglassfoundry.com
 */
public class SatelliteFilterPanel extends JPanel
{
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter UTC_FORMAT =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    private final SatelliteManager manager;
    private final Consumer<Integer> followCallback;

    // ── Stats labels ─────────────────────────────────────────────────────────
    private final JLabel dataSourceLabel;
    private final JLabel clockLabel;
    private final JLabel totalLabel;
    private final JLabel visibleLabel;
    private final JLabel leoLabel;
    private final JLabel meoLabel;
    private final JLabel geoLabel;

    // ── Type checkboxes ──────────────────────────────────────────────────────
    private final Map<SatelliteCategory, JCheckBox> typeCheckBoxes = new EnumMap<>(SatelliteCategory.class);

    // ── Display toggles ──────────────────────────────────────────────────────
    private final JCheckBox showOrbitsBox;
    private final JCheckBox showGroundTracksBox;
    private final JCheckBox showFootprintsBox;
    private final JCheckBox showDropLinesBox;
    private final JCheckBox showLeadersBox;
    private final JCheckBox showLabelsBox;

    // ── Search ───────────────────────────────────────────────────────────────
    private final JTextField searchField;

    // ── Detail panel (shown on satellite click) ─────────────────────────────
    private final JPanel detailPanel;
    private final JLabel detailLabel;

    // ── Clock timer ──────────────────────────────────────────────────────────
    private final Timer clockTimer;

    public SatelliteFilterPanel(SatelliteManager manager, Consumer<Integer> followCallback)
    {
        this.manager = manager;
        this.followCallback = followCallback;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(WWStyle.BG_DARK);

        // ── Data Source + Clock ───────────────────────────────────────────
        JPanel headerSection = section("Data Source");
        JPanel sourceRow = row();
        dataSourceLabel = WWStyle.label("\u25CF Connecting...", false);
        dataSourceLabel.setForeground(WWStyle.STATUS_WARN);
        sourceRow.add(dataSourceLabel);
        headerSection.add(sourceRow);

        JPanel clockRow = row();
        JLabel clockTitle = WWStyle.label("UTC:", false);
        clockTitle.setPreferredSize(new Dimension(30, 18));
        clockRow.add(clockTitle);
        clockLabel = WWStyle.label("--:--:--", false);
        clockLabel.setForeground(new Color(130, 200, 255));
        clockRow.add(clockLabel);
        headerSection.add(clockRow);

        add(headerSection);
        add(vgap(WWStyle.GAP_XS));

        // ── Satellite Detail (hidden until click) ────────────────────────
        detailPanel = new JPanel();
        detailPanel.setLayout(new BoxLayout(detailPanel, BoxLayout.Y_AXIS));
        detailPanel.setBackground(new Color(28, 30, 36));
        detailPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailPanel.setVisible(false);

        detailLabel = new JLabel();
        detailLabel.setForeground(Color.WHITE);
        detailLabel.setFont(WWStyle.FONT_BASE);
        detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailPanel.add(detailLabel);
        add(detailPanel);
        add(vgap(WWStyle.GAP_XS));

        // ── Live Statistics ───────────────────────────────────────────────
        JPanel statsSection = section("Live Statistics");
        totalLabel   = statLabel(statsSection, "Total:");
        visibleLabel = statLabel(statsSection, "Visible:");
        leoLabel     = statLabel(statsSection, "LEO:");
        meoLabel     = statLabel(statsSection, "MEO:");
        geoLabel     = statLabel(statsSection, "GEO:");
        add(statsSection);
        add(vgap(WWStyle.GAP_XS));

        // ── ISS Quick Track ──────────────────────────────────────────────
        JPanel issSection = section("Quick Track");
        javax.swing.JButton issButton = WWStyle.accentButton("\u25B6 Track ISS");
        issButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        issButton.addActionListener(e ->
        {
            if (manager.getFollowId() != null && manager.getFollowId() == 25544)
            {
                manager.setFollowId(null);
                issButton.setText("\u25B6 Track ISS");
            }
            else
            {
                followCallback.accept(25544);
                issButton.setText("\u25A0 Stop Tracking");
            }
        });
        issSection.add(issButton);
        add(issSection);
        add(vgap(WWStyle.GAP_XS));

        // ── Satellite Categories ─────────────────────────────────────────
        JPanel typeSection = section("Satellite Categories");
        for (SatelliteCategory cat : SatelliteCategory.values())
        {
            boolean defaultOn = cat != SatelliteCategory.STARLINK && cat != SatelliteCategory.DEBRIS;
            JCheckBox cb = WWStyle.checkBox(cat.getDisplayName(), defaultOn);
            cb.setForeground(cat.getColor());
            cb.addActionListener(e -> applyFilters());
            typeCheckBoxes.put(cat, cb);
            typeSection.add(cb);

            // Warning for Starlink
            if (cat == SatelliteCategory.STARLINK)
            {
                JLabel warn = WWStyle.label("<html><small>  (6000+ objects)</small></html>", false);
                warn.setForeground(WWStyle.FG_SECONDARY);
                typeSection.add(warn);
            }
        }
        add(typeSection);
        add(vgap(WWStyle.GAP_XS));

        // ── Display Options ──────────────────────────────────────────────
        JPanel displaySection = section("Display");

        showOrbitsBox = WWStyle.checkBox("Orbit Paths", false);
        showOrbitsBox.addActionListener(e -> manager.setShowOrbits(showOrbitsBox.isSelected()));

        showGroundTracksBox = WWStyle.checkBox("Ground Tracks", false);
        showGroundTracksBox.addActionListener(e -> manager.setShowGroundTracks(showGroundTracksBox.isSelected()));

        showFootprintsBox = WWStyle.checkBox("Footprints", false);
        showFootprintsBox.addActionListener(e -> manager.setShowFootprints(showFootprintsBox.isSelected()));

        showDropLinesBox = WWStyle.checkBox("Drop Lines", false);
        showDropLinesBox.addActionListener(e -> manager.setShowDropLines(showDropLinesBox.isSelected()));

        showLeadersBox = WWStyle.checkBox("Speed Leaders", false);
        showLeadersBox.addActionListener(e -> manager.setShowLeaders(showLeadersBox.isSelected()));

        showLabelsBox = WWStyle.checkBox("Labels", true);
        showLabelsBox.addActionListener(e -> manager.setShowLabels(showLabelsBox.isSelected()));

        displaySection.add(showOrbitsBox);
        displaySection.add(showGroundTracksBox);
        displaySection.add(showFootprintsBox);
        displaySection.add(showDropLinesBox);
        displaySection.add(showLeadersBox);
        displaySection.add(showLabelsBox);
        add(displaySection);
        add(vgap(WWStyle.GAP_XS));

        // ── Search ───────────────────────────────────────────────────────
        JPanel searchSection = section("Search (name or NORAD ID)");
        searchField = new JTextField();
        searchField.setFont(WWStyle.FONT_BASE);
        searchField.setForeground(WWStyle.FG_PRIMARY);
        searchField.setBackground(WWStyle.BG_PANEL);
        searchField.setCaretColor(WWStyle.FG_PRIMARY);
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override public void insertUpdate(DocumentEvent e) { applyFilters(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilters(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilters(); }
        });
        searchSection.add(searchField);
        add(searchSection);

        // ── Altitude Colour Legend ────────────────────────────────────────
        add(vgap(WWStyle.GAP_XS));
        JPanel legendSection = section("Orbit Altitude Colours");
        addLegendEntry(legendSection, SatelliteCategory.altitudeColor(400),   "LEO (< 600 km)");
        addLegendEntry(legendSection, SatelliteCategory.altitudeColor(1200),  "High LEO (600–2,000 km)");
        addLegendEntry(legendSection, SatelliteCategory.altitudeColor(10000), "MEO (2,000–20,000 km)");
        addLegendEntry(legendSection, SatelliteCategory.altitudeColor(30000), "Near-GEO (20,000–35,000 km)");
        addLegendEntry(legendSection, SatelliteCategory.altitudeColor(36000), "GEO (~35,786 km)");
        addLegendEntry(legendSection, SatelliteCategory.altitudeColor(50000), "HEO (> 37,000 km)");
        add(legendSection);

        // ── Attribution ──────────────────────────────────────────────────
        add(vgap(WWStyle.GAP_S));
        JLabel attr = WWStyle.label(
            "<html><small>TLE data: CelesTrak (celestrak.org)</small></html>", false);
        attr.setForeground(WWStyle.FG_SECONDARY);
        add(attr);

        // ── Clock update timer ───────────────────────────────────────────
        clockTimer = new Timer(1000, e -> clockLabel.setText(UTC_FORMAT.format(Instant.now()) + " UTC"));
        clockTimer.start();

        // Apply initial checkbox state so unchecked categories (Starlink,
        // Debris) are filtered out from the start.
        applyFilters();
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Show satellite detail in a panel at the top of the filter area.
     * seaglassfoundry.com — moved from WorldWind annotation to Swing panel
     */
    public void showDetail(int noradId)
    {
        TleRecord tle = manager.getTle(noradId);
        SatellitePosition sp = manager.getPosition(noradId);
        if (tle == null || sp == null) return;

        SatelliteCategory cat = tle.getCategory();

        StringBuilder html = new StringBuilder("<html><div style='width:200px'>");
        html.append("<b>").append(tle.getDisplayName()).append("</b><br>");
        html.append("<font color='").append(colorHex(cat.getColor())).append("'>")
            .append(cat.getDisplayName()).append("</font><br><br>");

        html.append("NORAD ID: ").append(tle.getNoradCatId()).append("<br>");
        if (!tle.getIntlDesignator().isEmpty())
            html.append("Intl Des: ").append(tle.getIntlDesignator()).append("<br>");

        html.append("<br>");
        html.append(String.format("Altitude: %.1f km<br>", sp.getAltitudeKm()));
        html.append(String.format("Velocity: %.2f km/s<br>", sp.getVelocityKmS()));
        html.append(String.format("Inclination: %.2f\u00B0<br>", tle.getInclinationDeg()));
        html.append(String.format("Period: %.1f min<br>", tle.getPeriodMinutes()));
        html.append(String.format("Apogee: %.0f km<br>", tle.getApogeeKm()));
        html.append(String.format("Perigee: %.0f km<br>", tle.getPerigeeKm()));
        html.append(String.format("Orbit Type: %s<br>", sp.getOrbitType()));

        html.append("<br>");
        html.append(String.format("Lat: %.4f\u00B0<br>", sp.getLatDeg()));
        html.append(String.format("Lon: %.4f\u00B0<br>", sp.getLonDeg()));
        html.append(String.format("Azimuth: %.1f\u00B0<br>", sp.getAzimuthDeg()));

        if (sp.isEclipsed())
            html.append("<br><i>In Earth's shadow</i><br>");

        html.append("</div></html>");

        SwingUtilities.invokeLater(() ->
        {
            detailLabel.setText(html.toString());
            detailPanel.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(cat.getColor(), 2),
                javax.swing.BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            detailPanel.setVisible(true);
            revalidate();
            repaint();
        });
    }

    /** Hide the satellite detail panel. */
    public void hideDetail()
    {
        SwingUtilities.invokeLater(() ->
        {
            detailPanel.setVisible(false);
            revalidate();
            repaint();
        });
    }

    public void updateStats()
    {
        SwingUtilities.invokeLater(() ->
        {
            totalLabel.setText(String.valueOf(manager.getTotalCount()));
            visibleLabel.setText(String.valueOf(manager.getVisibleCount()));
            leoLabel.setText(String.valueOf(manager.getLeoCount()));
            meoLabel.setText(String.valueOf(manager.getMeoCount()));
            geoLabel.setText(String.valueOf(manager.getGeoCount()));
        });
    }

    public void setDataSourceLabel(String label, boolean live)
    {
        SwingUtilities.invokeLater(() ->
        {
            dataSourceLabel.setText("\u25CF " + label);
            dataSourceLabel.setForeground(live ? WWStyle.STATUS_OK : WWStyle.STATUS_WARN);
        });
    }

    // ── Filter logic ─────────────────────────────────────────────────────────

    private void applyFilters()
    {
        Set<SatelliteCategory> enabled = EnumSet.noneOf(SatelliteCategory.class);
        for (Map.Entry<SatelliteCategory, JCheckBox> entry : typeCheckBoxes.entrySet())
            if (entry.getValue().isSelected())
                enabled.add(entry.getKey());

        String searchText = searchField.getText().trim().toLowerCase();

        manager.setFilterPredicate(id ->
        {
            TleRecord tle = manager.getTle(id);
            if (tle == null) return false;

            // Category filter
            if (!enabled.contains(tle.getCategory())) return false;

            // Search filter
            if (!searchText.isEmpty())
            {
                boolean match = tle.getObjectName().toLowerCase().contains(searchText)
                    || String.valueOf(tle.getNoradCatId()).contains(searchText)
                    || tle.getIntlDesignator().toLowerCase().contains(searchText);
                if (!match) return false;
            }

            return true;
        });

        updateStats();
    }

    // ── UI helpers ───────────────────────────────────────────────────────────

    private JPanel section(String title)
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(WWStyle.BG_DARK);
        p.setBorder(WWStyle.sectionBorder(title));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JLabel statLabel(JPanel parent, String label)
    {
        JPanel r = row();
        JLabel nameLabel = WWStyle.label(label, false);
        nameLabel.setPreferredSize(new Dimension(60, 18));
        r.add(nameLabel);
        JLabel valueLabel = WWStyle.label("\u2014", false);
        valueLabel.setForeground(new Color(130, 200, 255));
        r.add(valueLabel);
        parent.add(r);
        return valueLabel;
    }

    private JPanel row()
    {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        r.setBackground(WWStyle.BG_DARK);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        return r;
    }

    private void addLegendEntry(JPanel parent, Color color, String label)
    {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        r.setBackground(WWStyle.BG_DARK);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel dot = new JLabel("\u25CF");
        dot.setForeground(color);
        dot.setFont(WWStyle.FONT_BASE);
        r.add(dot);

        JLabel text = WWStyle.label(label, false);
        text.setForeground(WWStyle.FG_SECONDARY);
        r.add(text);

        parent.add(r);
    }

    private static JPanel vgap(int height)
    {
        JPanel p = new JPanel();
        p.setBackground(WWStyle.BG_DARK);
        p.setPreferredSize(new Dimension(0, height));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return p;
    }

    /** Convert a Color to #RRGGBB hex for use in HTML labels. */
    private static String colorHex(Color c)
    {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
