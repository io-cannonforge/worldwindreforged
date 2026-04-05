/*
 * WorldWind Reforged — NYC Buildings 3D Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: Swing filter/stats panel for the NYC Buildings demo.
 */
package gov.nasa.worldwindx.examples.nycbuildings;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Control panel for the NYC Buildings demo: data source status, statistics,
 * height filter, building category toggles, name search, and height colour legend.
 *
 * seaglassfoundry.com
 */
public class BuildingFilterPanel extends JPanel
{
    private static final long serialVersionUID = 1L;

    private final BuildingBatchRenderer renderer;

    /** Callback for address geocode requests. */
    private ActionListener geocodeListener;

    // ── Stats labels ─────────────────────────────────────────────────────────
    private final JLabel sourceLabel;
    private final JLabel totalLabel;
    private final JLabel visibleLabel;
    private final JLabel tallestLabel;
    private final JLabel statusLabel;

    // ── Category checkboxes ──────────────────────────────────────────────────
    private final Map<BuildingCategory, JCheckBox> catCheckBoxes = new EnumMap<>(BuildingCategory.class);

    // ── Height slider ────────────────────────────────────────────────────────
    private final JSlider minHeightSlider;

    // ── Search ───────────────────────────────────────────────────────────────
    private final JTextField searchField;

    // ── Detail panel (shown on building click) ────────────────────────────
    private final JPanel detailPanel;
    private final JLabel detailLabel;
    private volatile BuildingRecord detailRecord;

    // ── Address geocode ─────────────────────────────────────────────────────
    private final JTextField addressField;
    private final JLabel geocodeStatusLabel;

    public BuildingFilterPanel(BuildingBatchRenderer renderer)
    {
        this.renderer = renderer;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(WWStyle.BG_DARK);

        // ── Data Source Status ────────────────────────────────────────────
        JPanel sourceSection = section("Data Source");
        sourceLabel = statLabel(sourceSection, "Source:");
        statusLabel = statLabel(sourceSection, "Status:");
        statusLabel.setText("Loading...");
        statusLabel.setForeground(WWStyle.STATUS_WARN);
        add(sourceSection);
        add(vgap(WWStyle.GAP_XS));

        // ── Building Detail (hidden until click) ────────────────────────
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

        // ── Statistics ───────────────────────────────────────────────────
        JPanel statsSection = section("Statistics");
        totalLabel   = statLabel(statsSection, "Total:");
        visibleLabel = statLabel(statsSection, "Visible:");
        tallestLabel = statLabel(statsSection, "Tallest:");
        add(statsSection);
        add(vgap(WWStyle.GAP_XS));

        // ── Min Height Filter ────────────────────────────────────────────
        JPanel heightSection = section("Minimum Height Filter");
        JLabel heightLabel = WWStyle.label("Min height: 0 m", false);
        heightLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        minHeightSlider = new JSlider(0, 300, 0);
        minHeightSlider.setBackground(WWStyle.BG_DARK);
        minHeightSlider.setForeground(WWStyle.FG_PRIMARY);
        minHeightSlider.setMajorTickSpacing(50);
        minHeightSlider.setMinorTickSpacing(10);
        minHeightSlider.setPaintTicks(true);
        minHeightSlider.setPaintLabels(true);
        minHeightSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        minHeightSlider.addChangeListener(e ->
        {
            heightLabel.setText("Min height: " + minHeightSlider.getValue() + " m");
            if (!minHeightSlider.getValueIsAdjusting())
                applyFilters();
        });
        heightSection.add(heightLabel);
        heightSection.add(minHeightSlider);
        add(heightSection);
        add(vgap(WWStyle.GAP_XS));

        // ── Building Categories ──────────────────────────────────────────
        JPanel catSection = section("Building Categories");
        for (BuildingCategory cat : BuildingCategory.values())
        {
            JCheckBox cb = WWStyle.checkBox(
                cat.getDisplayName() + " (" + (int) cat.getMinHeight() + "-"
                    + (cat.getMaxHeight() < 1000 ? (int) cat.getMaxHeight() : "\u221E") + " m)", true);
            cb.setForeground(cat.getColor());
            cb.addActionListener(e -> applyFilters());
            catCheckBoxes.put(cat, cb);
            catSection.add(cb);
        }
        add(catSection);
        add(vgap(WWStyle.GAP_XS));

        // ── Search ───────────────────────────────────────────────────────
        JPanel searchSection = section("Search (name, address, type)");
        searchField = new JTextField();
        searchField.setFont(WWStyle.FONT_BASE);
        searchField.setForeground(WWStyle.FG_PRIMARY);
        searchField.setBackground(WWStyle.BG_PANEL);
        searchField.setCaretColor(WWStyle.FG_PRIMARY);
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override public void insertUpdate(DocumentEvent e)  { applyFilters(); }
            @Override public void removeUpdate(DocumentEvent e)  { applyFilters(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilters(); }
        });
        searchSection.add(searchField);
        add(searchSection);
        add(vgap(WWStyle.GAP_XS));

        // ── Go to Address ───────────────────────────────────────────────
        JPanel addressSection = section("Go to Address");
        addressField = new JTextField();
        addressField.setFont(WWStyle.FONT_BASE);
        addressField.setForeground(WWStyle.FG_PRIMARY);
        addressField.setBackground(WWStyle.BG_PANEL);
        addressField.setCaretColor(WWStyle.FG_PRIMARY);
        addressField.setAlignmentX(Component.LEFT_ALIGNMENT);
        addressField.setToolTipText("Enter an address or place name and press Enter");
        addressField.addActionListener(e -> fireGeocode());
        addressSection.add(addressField);

        JPanel goRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        goRow.setBackground(WWStyle.BG_DARK);
        goRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton goButton = new JButton("Go");
        goButton.setFont(WWStyle.FONT_SMALL);
        goButton.addActionListener(e -> fireGeocode());
        goRow.add(goButton);

        geocodeStatusLabel = WWStyle.label("", false);
        geocodeStatusLabel.setFont(WWStyle.FONT_SMALL);
        goRow.add(geocodeStatusLabel);
        addressSection.add(goRow);

        add(addressSection);
        add(vgap(WWStyle.GAP_XS));

        // ── Height Colour Legend ─────────────────────────────────────────
        JPanel legendSection = section("Height Colour Key");
        for (BuildingCategory cat : BuildingCategory.values())
        {
            addLegendEntry(legendSection, cat.getColor(),
                cat.getDisplayName() + (cat.getMaxHeight() < 1000
                    ? " (" + (int) cat.getMinHeight() + "-" + (int) cat.getMaxHeight() + " m)"
                    : " (300+ m)"));
        }
        add(legendSection);

        // ── Attribution ──────────────────────────────────────────────────
        add(vgap(WWStyle.GAP_S));
        JLabel attr = WWStyle.label(
            "<html><small>Building data: NYC Open Data (CC0)</small></html>", false);
        attr.setForeground(WWStyle.FG_SECONDARY);
        add(attr);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Show building detail in the panel. seaglassfoundry.com
     *
     * @param record  the building record
     * @param address initial address text, or null to show "Looking up..."
     */
    public void showDetail(BuildingRecord record, String address)
    {
        this.detailRecord = record;
        BuildingCategory cat = record.getCategory();

        String html = buildDetailHtml(record, address);

        SwingUtilities.invokeLater(() ->
        {
            detailLabel.setText(html);
            detailPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(cat.getColor(), 2),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            detailPanel.setVisible(true);
            revalidate();
            repaint();
        });
    }

    /**
     * Update the address in the currently shown detail panel (called after
     * async reverse-geocode completes).
     */
    public void updateDetailAddress(BuildingRecord record, String address)
    {
        if (detailRecord == null || !detailRecord.getId().equals(record.getId()))
            return;
        String html = buildDetailHtml(record, address != null ? address : "");
        SwingUtilities.invokeLater(() ->
        {
            detailLabel.setText(html);
            revalidate();
            repaint();
        });
    }

    /** Hide the building detail panel. */
    public void hideDetail()
    {
        detailRecord = null;
        SwingUtilities.invokeLater(() ->
        {
            detailPanel.setVisible(false);
            revalidate();
            repaint();
        });
    }

    private String buildDetailHtml(BuildingRecord record, String address)
    {
        StringBuilder html = new StringBuilder("<html><div style='width:200px'>");

        if (!record.getName().isEmpty())
            html.append("<b>").append(esc(record.getName())).append("</b><br>");
        else
            html.append("<b>").append(esc(record.getBuildingType())).append("</b><br>");

        html.append("<br>");
        html.append(String.format("Height: %.0f m (%d floors)<br>",
            record.getHeightMeters(), record.getLevels()));
        html.append("Category: ").append(esc(record.getCategory().getDisplayName())).append("<br>");
        html.append("Type: ").append(esc(record.getBuildingType())).append("<br>");

        if (address != null && !address.isEmpty())
            html.append("Address: ").append(esc(address)).append("<br>");
        else if (address == null)
            html.append("<i>Looking up address...</i><br>");

        html.append("<br><small>").append(esc(record.getId())).append("</small>");
        html.append("</div></html>");

        return html.toString();
    }

    /** Escape HTML special characters in external data. */
    private static String esc(String s)
    {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public void setSourceInfo(String label, boolean live)
    {
        SwingUtilities.invokeLater(() ->
        {
            sourceLabel.setText(label);
            sourceLabel.setForeground(live ? WWStyle.STATUS_OK : WWStyle.STATUS_WARN);
        });
    }

    public void setStatus(String text, Color color)
    {
        SwingUtilities.invokeLater(() ->
        {
            statusLabel.setText(text);
            statusLabel.setForeground(color);
        });
    }

    /** Set the listener that handles address geocode requests. */
    public void setGeocodeListener(ActionListener listener)
    {
        this.geocodeListener = listener;
    }

    /** Get the current address text. */
    public String getAddressText()
    {
        return addressField.getText().trim();
    }

    /** Update the geocode status message. */
    public void setGeocodeStatus(String text, Color color)
    {
        SwingUtilities.invokeLater(() ->
        {
            geocodeStatusLabel.setText(text);
            geocodeStatusLabel.setForeground(color);
        });
    }

    private void fireGeocode()
    {
        String text = addressField.getText().trim();
        if (text.isEmpty() || geocodeListener == null) return;
        geocodeListener.actionPerformed(new java.awt.event.ActionEvent(this, 0, text));
    }

    public void updateStats()
    {
        SwingUtilities.invokeLater(() ->
        {
            totalLabel.setText(String.valueOf(renderer.getTotalCount()));
            visibleLabel.setText(String.valueOf(renderer.getVisibleCount()));
            double maxH = renderer.getMaxHeight();
            tallestLabel.setText(maxH > 0 ? String.format("%.0f m (%d floors)", maxH, (int)(maxH / 3.5)) : "—");
        });
    }

    // ── Filter logic ─────────────────────────────────────────────────────────

    private void applyFilters()
    {
        Set<BuildingCategory> enabled = EnumSet.noneOf(BuildingCategory.class);
        for (Map.Entry<BuildingCategory, JCheckBox> entry : catCheckBoxes.entrySet())
            if (entry.getValue().isSelected())
                enabled.add(entry.getKey());

        int minHeight = minHeightSlider.getValue();
        String searchText = searchField.getText().trim().toLowerCase();

        renderer.setFilter(record ->
        {
            if (!enabled.contains(record.getCategory())) return false;
            if (record.getHeightMeters() < minHeight) return false;
            if (!searchText.isEmpty())
            {
                boolean match = record.getName().toLowerCase().contains(searchText)
                    || record.getAddress().toLowerCase().contains(searchText)
                    || record.getBuildingType().toLowerCase().contains(searchText)
                    || record.getId().toLowerCase().contains(searchText);
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
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        row.setBackground(WWStyle.BG_DARK);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel nameLabel = WWStyle.label(label, false);
        nameLabel.setPreferredSize(new Dimension(80, 18));
        row.add(nameLabel);

        JLabel valueLabel = WWStyle.label("\u2014", false);
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
        text.setFont(WWStyle.FONT_SMALL);
        row.add(text);

        parent.add(row);
    }

    private static Component vgap(int height)
    {
        JPanel gap = new JPanel();
        gap.setPreferredSize(new Dimension(0, height));
        gap.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        gap.setBackground(WWStyle.BG_DARK);
        return gap;
    }
}
