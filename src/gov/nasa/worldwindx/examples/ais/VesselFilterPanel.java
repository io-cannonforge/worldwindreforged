/*
 * WorldWind Reforged — AIS Vessel Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: Swing filter panel for the AIS vessel tracker demo.
 */
package gov.nasa.worldwindx.examples.ais;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Swing panel with interactive AIS vessel filters: vessel type, speed, track
 * display, and name/MMSI search. Wires directly to a {@link VesselManager}
 * to show/hide vessels in real time.
 *
 * seaglassfoundry.com
 */
public class VesselFilterPanel extends JPanel
{
    private static final long serialVersionUID = 1L;

    private final VesselManager vesselManager;

    // ── UI components ─────────────────────────────────────────────────────────
    private final JLabel sourceLabel;
    private final JLabel countLabel;
    private final Map<VesselCategory, JCheckBox> typeCheckBoxes = new EnumMap<>(VesselCategory.class);
    private final JSlider speedSlider;
    private final JLabel speedLabel;
    private final JCheckBox showTracksBox;
    private final JComboBox<String> trackLengthCombo;
    private final JTextField searchField;
    private final JLabel attributionLabel;

    public VesselFilterPanel(VesselManager vesselManager)
    {
        this.vesselManager = vesselManager;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(WWStyle.BG_DARK);

        // ── Data source indicator ─────────────────────────────────────────
        JPanel sourceSection = section("Data Source");
        sourceLabel = WWStyle.label("Connecting…", false);
        sourceSection.add(sourceLabel);
        add(sourceSection);
        add(vgap(WWStyle.GAP_XS));

        // ── Vessel count ──────────────────────────────────────────────────
        countLabel = WWStyle.label("Vessels: —", false);
        add(countLabel);
        add(vgap(WWStyle.GAP_XS));

        // ── Vessel type filters ───────────────────────────────────────────
        JPanel typeSection = section("Vessel Types");
        for (VesselCategory cat : VesselCategory.values())
        {
            JCheckBox cb = WWStyle.checkBox(cat.name(), true);
            cb.setForeground(cat.getColor());
            cb.addActionListener(e -> applyFilters());
            typeCheckBoxes.put(cat, cb);
            typeSection.add(cb);
        }
        add(typeSection);
        add(vgap(WWStyle.GAP_XS));

        // ── Speed filter ──────────────────────────────────────────────────
        JPanel speedSection = section("Minimum Speed");
        speedLabel = WWStyle.label("0 kt", false);
        speedSlider = WWStyle.slider(0, 30, 0);
        speedSlider.setMajorTickSpacing(10);
        speedSlider.setMinorTickSpacing(1);
        speedSlider.setPaintTicks(true);
        speedSlider.addChangeListener(e ->
        {
            speedLabel.setText(speedSlider.getValue() + " kt");
            if (!speedSlider.getValueIsAdjusting())
                applyFilters();
        });
        speedSection.add(speedSlider);
        speedSection.add(speedLabel);
        add(speedSection);
        add(vgap(WWStyle.GAP_XS));

        // ── Track display ─────────────────────────────────────────────────
        JPanel trackSection = section("Tracks");
        showTracksBox = WWStyle.checkBox("Show track history", true);
        showTracksBox.addActionListener(e -> vesselManager.setShowTracks(showTracksBox.isSelected()));
        trackSection.add(showTracksBox);

        trackLengthCombo = WWStyle.comboBox(new String[]{"30 min", "1 hour", "2 hours", "All"});
        trackLengthCombo.setSelectedIndex(1);
        trackLengthCombo.addActionListener(e -> applyTrackLength());
        trackSection.add(trackLengthCombo);
        add(trackSection);
        add(vgap(WWStyle.GAP_XS));

        // ── Search ────────────────────────────────────────────────────────
        JPanel searchSection = section("Search (name or MMSI)");
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

        // ── Attribution ───────────────────────────────────────────────────
        add(vgap(WWStyle.GAP_S));
        attributionLabel = WWStyle.label(
            "<html><small>AIS data: Fintraffic / digitraffic.fi (CC BY 4.0)</small></html>", false);
        attributionLabel.setForeground(WWStyle.FG_SECONDARY);
        add(attributionLabel);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Update the data source indicator. */
    public void setSourceLabel(String name, boolean live)
    {
        SwingUtilities.invokeLater(() ->
        {
            String dot = live ? "\u25CF " : "\u25CB ";
            Color c = live ? new Color(50, 200, 80) : new Color(200, 160, 50);
            sourceLabel.setForeground(c);
            sourceLabel.setText(dot + (live ? "LIVE" : "REPLAY") + " — " + name);
        });
    }

    /** Refresh the vessel count display. */
    public void updateCounts()
    {
        SwingUtilities.invokeLater(() ->
            countLabel.setText("Vessels: " + vesselManager.getVisibleVesselCount()
                + " of " + vesselManager.getTotalVesselCount()));
    }

    // ── Filter logic ──────────────────────────────────────────────────────────

    private void applyFilters()
    {
        // Snapshot current filter state
        Set<VesselCategory> enabledTypes = EnumSet.noneOf(VesselCategory.class);
        for (Map.Entry<VesselCategory, JCheckBox> entry : typeCheckBoxes.entrySet())
            if (entry.getValue().isSelected())
                enabledTypes.add(entry.getKey());

        double minSpeed = speedSlider.getValue();
        String searchText = searchField.getText().trim().toLowerCase();

        vesselManager.setFilterPredicate(mmsi ->
        {
            // Type filter
            VesselInfo info = vesselManager.getVesselInfo(mmsi);
            VesselCategory cat = info != null ? info.getCategory() : VesselCategory.OTHER;
            if (!enabledTypes.contains(cat))
                return false;

            // Speed filter
            VesselPosition vp = vesselManager.getPosition(mmsi);
            if (vp != null && vp.getSog() < minSpeed)
                return false;

            // Search filter
            if (!searchText.isEmpty())
            {
                String mmsiStr = String.valueOf(mmsi);
                String name = info != null ? info.getName().toLowerCase() : "";
                if (!mmsiStr.contains(searchText) && !name.contains(searchText))
                    return false;
            }

            return true;
        });

        updateCounts();
    }

    private void applyTrackLength()
    {
        int idx = trackLengthCombo.getSelectedIndex();
        long ms = switch (idx)
        {
            case 0  -> 30 * 60 * 1000L;      // 30 min
            case 1  -> 60 * 60 * 1000L;      // 1 hour
            case 2  -> 2 * 60 * 60 * 1000L;  // 2 hours
            default -> Long.MAX_VALUE;        // All
        };
        vesselManager.setMaxTrackAgeMs(ms);
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private JPanel section(String title)
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(WWStyle.BG_DARK);
        p.setBorder(WWStyle.sectionBorder(title));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static JPanel vgap(int height)
    {
        JPanel p = new JPanel();
        p.setBackground(WWStyle.BG_DARK);
        p.setPreferredSize(new Dimension(0, height));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return p;
    }
}
