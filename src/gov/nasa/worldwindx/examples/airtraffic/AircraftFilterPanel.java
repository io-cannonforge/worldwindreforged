/*
 * WorldWind Reforged — Air Traffic Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: Swing filter/stats panel for the air traffic demo.
 */
package gov.nasa.worldwindx.examples.airtraffic;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Rich control panel for the Air Traffic demo: live statistics, altitude filter,
 * aircraft type toggles, military overlay, follow-camera status, and search.
 *
 * seaglassfoundry.com
 */
public class AircraftFilterPanel extends JPanel
{
    private static final long serialVersionUID = 1L;

    private final AircraftManager manager;
    private final Consumer<Boolean> militaryToggleCallback;

    // ── Stats labels ──────────────────────────────────────────────────────────
    private final JLabel totalLabel;
    private final JLabel airborneLabel;
    private final JLabel militaryLabel;
    private final JLabel emergencyLabel;
    private final JLabel visibleLabel;

    // (follow camera removed)

    // ── Type checkboxes ───────────────────────────────────────────────────────
    private final Map<AircraftCategory, JCheckBox> typeCheckBoxes = new EnumMap<>(AircraftCategory.class);

    // ── Display toggles ───────────────────────────────────────────────────────
    private final JCheckBox showTrailsBox;
    private final JCheckBox showLeadersBox;
    private final JCheckBox showDropLinesBox;
    private final JCheckBox showGroundBox;
    private final JCheckBox showMilitaryGlobalBox;

    // ── Search ────────────────────────────────────────────────────────────────
    private final JTextField searchField;

    public AircraftFilterPanel(AircraftManager manager, Consumer<Boolean> militaryToggleCallback)
    {
        this.manager = manager;
        this.militaryToggleCallback = militaryToggleCallback;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(WWStyle.BG_DARK);

        // ── Live Statistics ───────────────────────────────────────────────
        JPanel statsSection = section("Live Statistics");

        totalLabel     = statLabel(statsSection, "Total:");
        airborneLabel  = statLabel(statsSection, "Airborne:");
        visibleLabel   = statLabel(statsSection, "Visible:");
        militaryLabel  = statLabel(statsSection, "Military:");
        emergencyLabel = statLabel(statsSection, "Emergency:");
        emergencyLabel.setForeground(new Color(255, 80, 80));

        add(statsSection);
        add(vgap(WWStyle.GAP_XS));

        add(vgap(WWStyle.GAP_XS));

        // ── Aircraft Types ────────────────────────────────────────────────
        JPanel typeSection = section("Aircraft Types");
        for (AircraftCategory cat : AircraftCategory.values())
        {
            JCheckBox cb = WWStyle.checkBox(cat.getDisplayName(), true);
            cb.setForeground(cat.getColor());
            cb.addActionListener(e -> applyFilters());
            typeCheckBoxes.put(cat, cb);
            typeSection.add(cb);
        }
        add(typeSection);
        add(vgap(WWStyle.GAP_XS));

        // ── Display Options ───────────────────────────────────────────────
        JPanel displaySection = section("Display");
        showTrailsBox = WWStyle.checkBox("3D Contrails", true);
        showTrailsBox.addActionListener(e -> manager.setShowTrails(showTrailsBox.isSelected()));

        showLeadersBox = WWStyle.checkBox("Speed Leaders", true);
        showLeadersBox.addActionListener(e -> manager.setShowLeaders(showLeadersBox.isSelected()));

        showDropLinesBox = WWStyle.checkBox("Drop Lines to Ground", true);
        showDropLinesBox.addActionListener(e -> manager.setShowDropLines(showDropLinesBox.isSelected()));

        showGroundBox = WWStyle.checkBox("Show Ground Aircraft", true);
        showGroundBox.addActionListener(e ->
        {
            manager.setShowOnGround(showGroundBox.isSelected());
            applyFilters();
        });

        displaySection.add(showTrailsBox);
        displaySection.add(showLeadersBox);
        displaySection.add(showDropLinesBox);
        displaySection.add(showGroundBox);
        add(displaySection);
        add(vgap(WWStyle.GAP_XS));

        // ── Military Worldwide ────────────────────────────────────────────
        JPanel milSection = section("Military Overlay");
        showMilitaryGlobalBox = WWStyle.checkBox("Show All Military Worldwide", false);
        showMilitaryGlobalBox.setForeground(new Color(255, 80, 80));
        showMilitaryGlobalBox.addActionListener(e ->
            militaryToggleCallback.accept(showMilitaryGlobalBox.isSelected()));
        milSection.add(showMilitaryGlobalBox);
        milSection.add(WWStyle.label("<html><small>Uses dedicated military endpoint</small></html>", false));
        add(milSection);
        add(vgap(WWStyle.GAP_XS));

        // ── Search ────────────────────────────────────────────────────────
        JPanel searchSection = section("Search (callsign, reg, type)");
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

        // ── Altitude Legend ────────────────────────────────────────────────
        add(vgap(WWStyle.GAP_XS));
        JPanel legendSection = section("Altitude Colour Key");
        addLegendEntry(legendSection, AircraftCategory.altitudeColor(0), "Ground");
        addLegendEntry(legendSection, AircraftCategory.altitudeColor(3000), "< 5,000 ft");
        addLegendEntry(legendSection, AircraftCategory.altitudeColor(10000), "5,000 – 15,000 ft");
        addLegendEntry(legendSection, AircraftCategory.altitudeColor(20000), "15,000 – 25,000 ft");
        addLegendEntry(legendSection, AircraftCategory.altitudeColor(30000), "25,000 – 35,000 ft");
        addLegendEntry(legendSection, AircraftCategory.altitudeColor(40000), "> 35,000 ft");
        add(legendSection);

        // ── Attribution ───────────────────────────────────────────────────
        add(vgap(WWStyle.GAP_S));
        JLabel attr = WWStyle.label(
            "<html><small>ADS-B data: airplanes.live (free, community-sourced)</small></html>", false);
        attr.setForeground(WWStyle.FG_SECONDARY);
        add(attr);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void updateStats()
    {
        SwingUtilities.invokeLater(() ->
        {
            totalLabel.setText(String.valueOf(manager.getTotalCount()));
            airborneLabel.setText(String.valueOf(manager.getAirborneCount()));
            visibleLabel.setText(String.valueOf(manager.getVisibleCount()));
            militaryLabel.setText(String.valueOf(manager.getMilitaryCount()));
            int emerg = manager.getEmergencyCount();
            emergencyLabel.setText(emerg > 0 ? "\u26A0 " + emerg : "0");
        });
    }

    // Follow camera removed — detail popup is sufficient.

    // ── Filter logic ──────────────────────────────────────────────────────────

    private void applyFilters()
    {
        Set<AircraftCategory> enabled = EnumSet.noneOf(AircraftCategory.class);
        for (Map.Entry<AircraftCategory, JCheckBox> entry : typeCheckBoxes.entrySet())
            if (entry.getValue().isSelected())
                enabled.add(entry.getKey());

        String searchText = searchField.getText().trim().toLowerCase();

        manager.setFilterPredicate(hex ->
        {
            AircraftPosition ac = manager.getAircraft(hex);
            if (ac == null) return false;

            // Type filter
            if (!enabled.contains(ac.getCategory())) return false;

            // Search filter
            if (!searchText.isEmpty())
            {
                boolean match = ac.getCallsign().toLowerCase().contains(searchText)
                    || ac.getRegistration().toLowerCase().contains(searchText)
                    || ac.getTypeCode().toLowerCase().contains(searchText)
                    || ac.getOperator().toLowerCase().contains(searchText)
                    || ac.getHex().toLowerCase().contains(searchText);
                if (!match) return false;
            }

            return true;
        });

        updateStats();
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

    private JLabel statLabel(JPanel parent, String label)
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        row.setBackground(WWStyle.BG_DARK);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel nameLabel = WWStyle.label(label, false);
        nameLabel.setPreferredSize(new Dimension(80, 18));
        row.add(nameLabel);

        JLabel valueLabel = WWStyle.label("—", false);
        valueLabel.setForeground(new Color(130, 200, 255));
        row.add(valueLabel);

        parent.add(row);
        return valueLabel;
    }

    private void addLegendEntry(JPanel parent, Color color, String label)
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        row.setBackground(WWStyle.BG_DARK);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel dot = new JLabel("\u25CF");
        dot.setForeground(color);
        dot.setFont(WWStyle.FONT_BASE);
        row.add(dot);

        JLabel text = WWStyle.label(label, false);
        text.setForeground(WWStyle.FG_SECONDARY);
        row.add(text);

        parent.add(row);
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
