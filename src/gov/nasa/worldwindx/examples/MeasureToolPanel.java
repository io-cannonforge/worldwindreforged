/*
 * Copyright 2006-2009, 2017, 2020 United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The NASA World Wind Java (WWJ) platform is licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * NASA World Wind Java (WWJ) also contains the following 3rd party Open Source
 * software:
 *
 *     Jackson Parser – Licensed under Apache 2.0
 *     GDAL – Licensed under MIT
 *     JOGL – Licensed under  Berkeley Software Distribution (BSD)
 *     Gluegen – Licensed under Berkeley Software Distribution (BSD)
 *
 * A complete listing of 3rd Party software notices and licenses included in
 * NASA World Wind Java (WWJ)  can be found in the WorldWindJava-v2.2 3rd-party
 * notices and licenses PDF found in code directory.
 *
 * Modifications and additions by seaglassfoundry.com — WorldWind Reforged project.
 * MeasureToolPanel.java: complete rewrite using WWStyle design system. Adds
 * segment-level detail table, cumulative distances, per-segment bearings,
 * elevation display, undo-last-point, and copy-to-clipboard.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.util.UnitsFormat;
import gov.nasa.worldwind.util.measure.MeasureTool;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Enhanced control panel for the {@link MeasureTool}.
 * <p>
 * Features beyond the original panel:
 * <ul>
 *   <li>Full {@link WWStyle} design-system integration (dark theme)</li>
 *   <li>Segment detail table — per-segment distance, cumulative distance,
 *       bearing, and elevation for each vertex</li>
 *   <li>Undo Last Point button</li>
 *   <li>Copy All Metrics to clipboard</li>
 *   <li>All numeric display honours the user-selected length / area / angle
 *       units via {@link UnitsFormat}</li>
 * </ul>
 *
 * @see MeasureTool
 * @see MeasureToolUsage
 *
 * seaglassfoundry.com — rewritten for WorldWind Reforged
 */
@SuppressWarnings("serial")
public class MeasureToolPanel extends JPanel {

    private final WorldWindow wwd;
    private final MeasureTool measureTool;

    // ── Controls ────────────────────────────────────────────────────────────
    private JComboBox<String> shapeCombo;
    private JComboBox<String> pathTypeCombo;
    private JComboBox<String> unitsCombo;
    private JComboBox<String> anglesCombo;
    private JCheckBox followCheck;
    private JCheckBox showControlsCheck;
    private JCheckBox showAnnotationCheck;
    private JCheckBox rubberBandCheck;
    private JCheckBox freeHandCheck;
    private JButton newButton;
    private JButton pauseButton;
    private JButton endButton;
    private JButton undoButton;

    // ── Metric labels ───────────────────────────────────────────────────────
    private JLabel lengthLabel;
    private JLabel areaLabel;
    private JLabel widthLabel;
    private JLabel heightLabel;
    private JLabel headingLabel;
    private JLabel centerLabel;
    private JLabel pointCountLabel;

    // ── Segment table ───────────────────────────────────────────────────────
    private SegmentTableModel segmentModel;
    private JTable segmentTable;

    // ── Shape & unit mappings ───────────────────────────────────────────────
    private static final String[] SHAPE_NAMES =
        {"Line", "Path", "Polygon", "Circle", "Ellipse", "Square", "Rectangle"};
    private static final String[] SHAPE_TYPES = {
        MeasureTool.SHAPE_LINE, MeasureTool.SHAPE_PATH, MeasureTool.SHAPE_POLYGON,
        MeasureTool.SHAPE_CIRCLE, MeasureTool.SHAPE_ELLIPSE, MeasureTool.SHAPE_SQUARE,
        MeasureTool.SHAPE_QUAD
    };

    // =====================================================================
    // Construction
    // =====================================================================

    public MeasureToolPanel(WorldWindow wwdObject, MeasureTool measureToolObject) {
        super(new BorderLayout());
        this.wwd = wwdObject;
        this.measureTool = measureToolObject;
        this.setBackground(WWStyle.BG_DARK);

        buildUI();
        wireEvents();
    }

    public MeasureTool getMeasureTool() {
        return this.measureTool;
    }

    // =====================================================================
    // UI construction
    // =====================================================================

    private void buildUI() {
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setBackground(WWStyle.BG_DARK);
        outer.setBorder(BorderFactory.createEmptyBorder(
            WWStyle.GAP_XS, WWStyle.GAP_XS, WWStyle.GAP_XS, WWStyle.GAP_XS));

        outer.add(buildShapeSection());
        outer.add(Box.createVerticalStrut(WWStyle.GAP_S));
        outer.add(buildOptionsSection());
        outer.add(Box.createVerticalStrut(WWStyle.GAP_S));
        outer.add(buildActionSection());
        outer.add(Box.createVerticalStrut(WWStyle.GAP_S));
        outer.add(buildMetricSection());
        outer.add(Box.createVerticalStrut(WWStyle.GAP_S));
        outer.add(buildSegmentSection());

        JScrollPane scroll = WWStyle.scrollPane(outer);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.add(scroll, BorderLayout.CENTER);
    }

    // ── Shape & path type ───────────────────────────────────────────────────

    private JPanel buildShapeSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(WWStyle.BG_DARK);
        section.setBorder(WWStyle.sectionBorder("Shape"));

        // Shape combo
        shapeCombo = WWStyle.comboBox(SHAPE_NAMES);
        section.add(row("Shape:", shapeCombo));

        // Path type combo
        pathTypeCombo = WWStyle.comboBox(new String[]{"Linear", "Rhumb", "Great Circle"});
        pathTypeCombo.setSelectedIndex(2);
        section.add(row("Path type:", pathTypeCombo));

        // Units combo
        unitsCombo = WWStyle.comboBox(new String[]{
            "M/M\u00b2", "KM/KM\u00b2", "KM/Hectare",
            "Feet/Feet\u00b2", "Miles/Miles\u00b2", "Nm/Miles\u00b2", "Yards/Acres"
        });
        unitsCombo.setSelectedItem("KM/KM\u00b2");
        section.add(row("Units:", unitsCombo));

        // Angle format combo
        anglesCombo = WWStyle.comboBox(new String[]{"DD", "DMS"});
        section.add(row("Angles:", anglesCombo));

        return section;
    }

    // ── Options (checkboxes + colors) ───────────────────────────────────────

    private JPanel buildOptionsSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(WWStyle.BG_DARK);
        section.setBorder(WWStyle.sectionBorder("Options"));

        // Checkboxes — 3 x 2 grid
        JPanel checkGrid = new JPanel(new GridLayout(3, 2, WWStyle.GAP_XS, WWStyle.GAP_XS));
        checkGrid.setBackground(WWStyle.BG_DARK);

        followCheck = WWStyle.checkBox("Follow terrain", measureTool.isFollowTerrain());
        showControlsCheck = WWStyle.checkBox("Control points", measureTool.isShowControlPoints());
        rubberBandCheck = WWStyle.checkBox("Rubber band", measureTool.getController().isUseRubberBand());
        freeHandCheck = WWStyle.checkBox("Free hand", measureTool.getController().isFreeHand());
        showAnnotationCheck = WWStyle.checkBox("Tooltip", measureTool.isShowAnnotation());

        checkGrid.add(followCheck);
        checkGrid.add(showControlsCheck);
        checkGrid.add(rubberBandCheck);
        checkGrid.add(freeHandCheck);
        checkGrid.add(showAnnotationCheck);
        section.add(checkGrid);

        section.add(Box.createVerticalStrut(WWStyle.GAP_XS));

        // Color buttons
        JPanel colorRow = new JPanel(new GridLayout(1, 3, WWStyle.GAP_XS, 0));
        colorRow.setBackground(WWStyle.BG_DARK);

        JButton lineColorBtn = makeColorButton("Line", measureTool.getLineColor());
        JButton pointColorBtn = makeColorButton("Points",
            measureTool.getControlPointsAttributes().getBackgroundColor());
        JButton annotColorBtn = makeColorButton("Tooltip",
            measureTool.getAnnotationAttributes().getTextColor());

        lineColorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Line color", lineColorBtn.getBackground());
            if (c != null) {
                lineColorBtn.setBackground(c);
                measureTool.setLineColor(c);
                measureTool.setFillColor(new Color(
                    c.getRed() / 255f * .5f, c.getGreen() / 255f * .5f,
                    c.getBlue() / 255f * .5f, .5f));
            }
        });
        pointColorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Point color", pointColorBtn.getBackground());
            if (c != null) {
                pointColorBtn.setBackground(c);
                measureTool.getControlPointsAttributes().setBackgroundColor(c);
            }
        });
        annotColorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Tooltip color", annotColorBtn.getBackground());
            if (c != null) {
                annotColorBtn.setBackground(c);
                measureTool.getAnnotationAttributes().setTextColor(c);
            }
        });

        colorRow.add(lineColorBtn);
        colorRow.add(pointColorBtn);
        colorRow.add(annotColorBtn);
        section.add(colorRow);

        return section;
    }

    // ── Action buttons ──────────────────────────────────────────────────────

    private JPanel buildActionSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(WWStyle.BG_DARK);
        section.setBorder(WWStyle.sectionBorder("Actions"));

        // Row 1: New, Pause, End
        JPanel row1 = new JPanel(new GridLayout(1, 3, WWStyle.GAP_XS, 0));
        row1.setBackground(WWStyle.BG_DARK);

        newButton = WWStyle.accentButton("New");
        pauseButton = WWStyle.button("Pause");
        endButton = WWStyle.button("End");
        pauseButton.setEnabled(false);
        endButton.setEnabled(false);

        row1.add(newButton);
        row1.add(pauseButton);
        row1.add(endButton);
        section.add(row1);

        section.add(Box.createVerticalStrut(WWStyle.GAP_XS));

        // Row 2: Undo, Copy
        JPanel row2 = new JPanel(new GridLayout(1, 2, WWStyle.GAP_XS, 0));
        row2.setBackground(WWStyle.BG_DARK);

        undoButton = WWStyle.button("Undo Point");
        undoButton.setEnabled(false);
        JButton copyButton = WWStyle.button("Copy Metrics");

        row2.add(undoButton);
        row2.add(copyButton);
        section.add(row2);

        // --- Wire action buttons ---
        newButton.addActionListener(e -> {
            measureTool.clear();
            measureTool.setArmed(true);
        });
        pauseButton.addActionListener(e -> {
            measureTool.setArmed(!measureTool.isArmed());
            pauseButton.setText(!measureTool.isArmed() ? "Resume" : "Pause");
            pauseButton.setEnabled(true);
            ((Component) wwd).setCursor(!measureTool.isArmed()
                ? Cursor.getDefaultCursor()
                : Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        });
        endButton.addActionListener(e -> measureTool.setArmed(false));
        undoButton.addActionListener(e -> {
            if (measureTool.isArmed()) {
                measureTool.removeControlPoint();
            }
        });
        copyButton.addActionListener(e -> copyMetricsToClipboard());

        return section;
    }

    // ── Metric readouts ─────────────────────────────────────────────────────

    private JPanel buildMetricSection() {
        JPanel section = new JPanel(new GridLayout(0, 2, WWStyle.GAP_XS, 2));
        section.setBackground(WWStyle.BG_DARK);
        section.setBorder(WWStyle.sectionBorder("Metrics"));

        section.add(WWStyle.label("Length:"));
        lengthLabel = WWStyle.label("—", false);
        section.add(lengthLabel);

        section.add(WWStyle.label("Area:"));
        areaLabel = WWStyle.label("—", false);
        section.add(areaLabel);

        section.add(WWStyle.label("Width:"));
        widthLabel = WWStyle.label("—", false);
        section.add(widthLabel);

        section.add(WWStyle.label("Height:"));
        heightLabel = WWStyle.label("—", false);
        section.add(heightLabel);

        section.add(WWStyle.label("Heading:"));
        headingLabel = WWStyle.label("—", false);
        section.add(headingLabel);

        section.add(WWStyle.label("Center:"));
        centerLabel = WWStyle.label("—", false);
        section.add(centerLabel);

        section.add(WWStyle.label("Points:"));
        pointCountLabel = WWStyle.label("0", false);
        section.add(pointCountLabel);

        return section;
    }

    // ── Segment detail table ────────────────────────────────────────────────

    private JPanel buildSegmentSection() {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(WWStyle.BG_DARK);
        section.setBorder(WWStyle.sectionBorder("Segments"));

        segmentModel = new SegmentTableModel();
        segmentTable = new JTable(segmentModel);
        segmentTable.setFont(WWStyle.FONT_SMALL);
        segmentTable.setForeground(WWStyle.FG_PRIMARY);
        segmentTable.setBackground(WWStyle.BG_PANEL);
        segmentTable.setGridColor(WWStyle.BORDER);
        segmentTable.setSelectionBackground(WWStyle.BG_SELECTED);
        segmentTable.setSelectionForeground(WWStyle.FG_PRIMARY);
        segmentTable.setRowHeight(20);
        segmentTable.getTableHeader().setFont(WWStyle.FONT_SMALL);
        segmentTable.getTableHeader().setBackground(WWStyle.BG_DARK);
        segmentTable.getTableHeader().setForeground(WWStyle.FG_SECONDARY);
        segmentTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        // Right-align numeric columns
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int i = 1; i < segmentModel.getColumnCount(); i++) {
            segmentTable.getColumnModel().getColumn(i).setCellRenderer(rightRenderer);
        }
        // Narrow the # column
        segmentTable.getColumnModel().getColumn(0).setPreferredWidth(28);
        segmentTable.getColumnModel().getColumn(0).setMaxWidth(36);

        JScrollPane scroll = WWStyle.scrollPane(segmentTable);
        scroll.setPreferredSize(new Dimension(200, 180));
        section.add(scroll, BorderLayout.CENTER);

        return section;
    }

    // =====================================================================
    // Event wiring
    // =====================================================================

    private void wireEvents() {
        // Shape combo
        shapeCombo.addActionListener(e -> {
            int idx = shapeCombo.getSelectedIndex();
            if (idx >= 0 && idx < SHAPE_TYPES.length) {
                measureTool.setMeasureShapeType(SHAPE_TYPES[idx]);
            }
        });

        // Path type
        pathTypeCombo.addActionListener(e -> {
            switch (pathTypeCombo.getSelectedIndex()) {
                case 0: measureTool.setPathType(AVKey.LINEAR); break;
                case 1: measureTool.setPathType(AVKey.RHUMB_LINE); break;
                case 2: measureTool.setPathType(AVKey.GREAT_CIRCLE); break;
            }
        });

        // Units
        unitsCombo.addActionListener(e -> applyUnits());

        // Angle format
        anglesCombo.addActionListener(e ->
            measureTool.getUnitsFormat().setShowDMS("DMS".equals(anglesCombo.getSelectedItem())));

        // Checkboxes
        followCheck.addActionListener(e -> {
            measureTool.setFollowTerrain(followCheck.isSelected());
            wwd.redraw();
        });
        showControlsCheck.addActionListener(e -> {
            measureTool.setShowControlPoints(showControlsCheck.isSelected());
            wwd.redraw();
        });
        showAnnotationCheck.addActionListener(e -> {
            measureTool.setShowAnnotation(showAnnotationCheck.isSelected());
            wwd.redraw();
        });
        rubberBandCheck.addActionListener(e -> {
            measureTool.getController().setUseRubberBand(rubberBandCheck.isSelected());
            freeHandCheck.setEnabled(rubberBandCheck.isSelected());
            wwd.redraw();
        });
        freeHandCheck.addActionListener(e -> {
            measureTool.getController().setFreeHand(freeHandCheck.isSelected());
            wwd.redraw();
        });

        // MeasureTool events
        measureTool.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            String prop = evt.getPropertyName();
            if (prop.equals(MeasureTool.EVENT_POSITION_ADD)
                    || prop.equals(MeasureTool.EVENT_POSITION_REMOVE)
                    || prop.equals(MeasureTool.EVENT_POSITION_REPLACE)) {
                refreshSegmentTable();
                updateUndoState();
            } else if (prop.equals(MeasureTool.EVENT_ARMED)) {
                onArmedChanged();
            } else if (prop.equals(MeasureTool.EVENT_METRIC_CHANGED)) {
                updateMetric();
            }
        });
    }

    // =====================================================================
    // Unit application
    // =====================================================================

    private void applyUnits() {
        UnitsFormat uf = measureTool.getUnitsFormat();
        switch ((String) unitsCombo.getSelectedItem()) {
            case "M/M\u00b2":
                uf.setLengthUnits(UnitsFormat.METERS);
                uf.setAreaUnits(UnitsFormat.SQUARE_METERS);
                break;
            case "KM/KM\u00b2":
                uf.setLengthUnits(UnitsFormat.KILOMETERS);
                uf.setAreaUnits(UnitsFormat.SQUARE_KILOMETERS);
                break;
            case "KM/Hectare":
                uf.setLengthUnits(UnitsFormat.KILOMETERS);
                uf.setAreaUnits(UnitsFormat.HECTARE);
                break;
            case "Feet/Feet\u00b2":
                uf.setLengthUnits(UnitsFormat.FEET);
                uf.setAreaUnits(UnitsFormat.SQUARE_FEET);
                break;
            case "Miles/Miles\u00b2":
                uf.setLengthUnits(UnitsFormat.MILES);
                uf.setAreaUnits(UnitsFormat.SQUARE_MILES);
                break;
            case "Nm/Miles\u00b2":
                uf.setLengthUnits(UnitsFormat.NAUTICAL_MILES);
                uf.setAreaUnits(UnitsFormat.SQUARE_MILES);
                break;
            case "Yards/Acres":
                uf.setLengthUnits(UnitsFormat.YARDS);
                uf.setAreaUnits(UnitsFormat.ACRE);
                break;
        }
        updateMetric();
        refreshSegmentTable();
    }

    // =====================================================================
    // Metric display — uses UnitsFormat for all values
    // =====================================================================

    private void updateMetric() {
        UnitsFormat uf = measureTool.getUnitsFormat();

        // Length
        double len = measureTool.getLength();
        lengthLabel.setText(len > 0 ? uf.length("", len).trim() : "—");

        // Area
        double area = measureTool.getArea();
        areaLabel.setText(area >= 0 ? uf.area("", area).trim() : "—");

        // Width
        double w = measureTool.getWidth();
        widthLabel.setText(w >= 0 ? uf.length("", w).trim() : "—");

        // Height
        double h = measureTool.getHeight();
        heightLabel.setText(h >= 0 ? uf.length("", h).trim() : "—");

        // Heading
        Angle heading = measureTool.getOrientation();
        headingLabel.setText(heading != null
            ? String.format("%,.2f\u00B0", heading.degrees) : "—");

        // Center
        Position center = measureTool.getCenterPosition();
        centerLabel.setText(center != null
            ? String.format("%,.4f\u00B0 %,.4f\u00B0",
                center.getLatitude().degrees, center.getLongitude().degrees)
            : "—");

        // Point count
        ArrayList<? extends LatLon> pos = measureTool.getPositions();
        pointCountLabel.setText(pos != null ? String.valueOf(pos.size()) : "0");
    }

    // =====================================================================
    // Armed state change
    // =====================================================================

    private void onArmedChanged() {
        if (measureTool.isArmed()) {
            newButton.setEnabled(false);
            pauseButton.setText("Pause");
            pauseButton.setEnabled(true);
            endButton.setEnabled(true);
            ((Component) wwd).setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        } else {
            newButton.setEnabled(true);
            pauseButton.setText("Pause");
            pauseButton.setEnabled(false);
            endButton.setEnabled(false);
            ((Component) wwd).setCursor(Cursor.getDefaultCursor());
        }
        updateUndoState();
    }

    private void updateUndoState() {
        ArrayList<? extends LatLon> pos = measureTool.getPositions();
        undoButton.setEnabled(measureTool.isArmed() && pos != null && pos.size() > 0);
    }

    // =====================================================================
    // Segment table
    // =====================================================================

    private void refreshSegmentTable() {
        segmentModel.refresh();
    }

    /**
     * Table model that computes per-segment distance, cumulative distance,
     * bearing, and elevation from the MeasureTool's position list.
     */
    private class SegmentTableModel extends AbstractTableModel {

        private static final String[] COLS = {"#", "Seg Dist", "Cumul", "Bearing", "Elev"};
        private final ArrayList<Object[]> rows = new ArrayList<>();

        void refresh() {
            rows.clear();
            ArrayList<? extends Position> positions = measureTool.getPositions();
            if (positions == null || positions.isEmpty()) {
                fireTableDataChanged();
                return;
            }

            UnitsFormat uf = measureTool.getUnitsFormat();
            double globeRadius = wwd.getModel().getGlobe().getRadius();
            double cumulative = 0;

            for (int i = 0; i < positions.size(); i++) {
                Position p = positions.get(i);
                String segDist = "—";
                String cumStr = "—";
                String bearing = "—";

                if (i > 0) {
                    Position prev = positions.get(i - 1);
                    double segMeters = LatLon.greatCircleDistance(prev, p).radians * globeRadius;
                    cumulative += segMeters;
                    segDist = uf.length("", segMeters).trim();
                    cumStr = uf.length("", cumulative).trim();

                    Angle azimuth = LatLon.greatCircleAzimuth(prev, p);
                    double deg = azimuth.degrees;
                    if (deg < 0) deg += 360;
                    bearing = String.format("%,.1f\u00B0", deg);
                } else {
                    cumStr = uf.length("", 0).trim();
                }

                // Elevation from position (0 if surface)
                double elevM = p.getElevation();
                String elev = elevM != 0
                    ? uf.length("", elevM).trim()
                    : "sfc";

                rows.add(new Object[]{i + 1, segDist, cumStr, bearing, elev});
            }
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int col) { return COLS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            return rows.get(row)[col];
        }
    }

    // =====================================================================
    // Copy to clipboard
    // =====================================================================

    private void copyMetricsToClipboard() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== WorldWind Reforged — Measure Tool ===\n\n");

        // Summary metrics
        sb.append("Shape:   ").append(shapeCombo.getSelectedItem()).append('\n');
        sb.append("Length:  ").append(lengthLabel.getText()).append('\n');
        sb.append("Area:    ").append(areaLabel.getText()).append('\n');
        sb.append("Width:   ").append(widthLabel.getText()).append('\n');
        sb.append("Height:  ").append(heightLabel.getText()).append('\n');
        sb.append("Heading: ").append(headingLabel.getText()).append('\n');
        sb.append("Center:  ").append(centerLabel.getText()).append('\n');
        sb.append("Points:  ").append(pointCountLabel.getText()).append('\n');

        // Segment table
        ArrayList<? extends Position> positions = measureTool.getPositions();
        if (positions != null && !positions.isEmpty()) {
            sb.append("\n--- Segments ---\n");
            sb.append(String.format("%-4s  %-14s  %-14s  %-14s  %-10s  %-10s  %-10s\n",
                "#", "Latitude", "Longitude", "Seg Dist", "Cumulative", "Bearing", "Elevation"));

            UnitsFormat uf = measureTool.getUnitsFormat();
            double globeRadius = wwd.getModel().getGlobe().getRadius();
            double cumulative = 0;

            for (int i = 0; i < positions.size(); i++) {
                Position p = positions.get(i);
                String segDist = "—";
                String cumStr;
                String bearing = "—";

                if (i > 0) {
                    Position prev = positions.get(i - 1);
                    double segM = LatLon.greatCircleDistance(prev, p).radians * globeRadius;
                    cumulative += segM;
                    segDist = uf.length("", segM).trim();
                    Angle az = LatLon.greatCircleAzimuth(prev, p);
                    double deg = az.degrees;
                    if (deg < 0) deg += 360;
                    bearing = String.format("%.1f\u00B0", deg);
                }
                cumStr = uf.length("", cumulative).trim();
                String elev = p.getElevation() != 0
                    ? uf.length("", p.getElevation()).trim() : "sfc";

                sb.append(String.format("%-4d  %14.6f  %14.6f  %-14s  %-10s  %-10s  %-10s\n",
                    i + 1, p.getLatitude().degrees, p.getLongitude().degrees,
                    segDist, cumStr, bearing, elev));
            }
        }

        StringSelection sel = new StringSelection(sb.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Build a label + component row using WWStyle. */
    private static JPanel row(String labelText, JComboBox<?> combo) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, WWStyle.GAP_XS, 2));
        p.setBackground(WWStyle.BG_DARK);
        JLabel lbl = WWStyle.label(labelText);
        lbl.setPreferredSize(new Dimension(70, lbl.getPreferredSize().height));
        p.add(lbl);
        p.add(combo);
        return p;
    }

    /** Flat color-swatch button. */
    private static JButton makeColorButton(String text, Color initial) {
        JButton b = new JButton(text);
        b.setFont(WWStyle.FONT_SMALL);
        b.setForeground(WWStyle.FG_PRIMARY);
        b.setBackground(initial);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(WWStyle.BORDER, 1));
        b.setPreferredSize(new Dimension(60, 24));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
