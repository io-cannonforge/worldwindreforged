/*
 * WorldWind Reforged — ProceduralFillPatternsExample
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New example demonstrating Phase 3 procedural fill patterns (SurfaceShapeFillShader).
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;

import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.AbstractSurfaceShape;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.ProceduralFillPattern;
import gov.nasa.worldwind.render.SurfacePolygon;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Demonstrates the Phase 3 procedural fill patterns rendered by
 * {@link gov.nasa.worldwind.render.shaders.SurfaceShapeFillShader}.
 * <p>
 * Three {@link SurfacePolygon} shapes are displayed side-by-side over the western United States,
 * each pre-loaded with a different pattern type:
 * <ul>
 *   <li><b>Hatch</b> — single-direction lines at a configurable angle</li>
 *   <li><b>Crosshatch</b> — two perpendicular sets of lines</li>
 *   <li><b>Dots</b> — a regular grid of filled circles</li>
 * </ul>
 * The control panel lets you select a shape and adjust its pattern parameters
 * (scale, line width / dot size, and rotation angle) in real time.
 * <p>
 * Patterns tile correctly across FBO tile boundaries and remain geographically
 * stable as the camera moves — both are properties of the GPU shader implementation.
 *
 * @see ProceduralFillPattern
 * @see AbstractSurfaceShape#setFillPattern(ProceduralFillPattern)
 *
 * seaglassfoundry.com — new example for WorldWind Reforged Phase 3
 */
public class ProceduralFillPatternsExample extends ApplicationTemplate {

    // ── Globe positions for the three demo polygons ───────────────────────────
    private static final double CENTER_LAT  =  38.0;
    private static final double LON_HATCH   = -115.0;
    private static final double LON_CROSS   = -103.0;
    private static final double LON_DOTS    =  -91.0;
    private static final double POLY_W      =   9.0;
    private static final double POLY_H      =   7.0;

    private static List<LatLon> rect(double lat, double lon) {
        double n = lat + POLY_H / 2, s = lat - POLY_H / 2;
        double e = lon + POLY_W / 2, w = lon - POLY_W / 2;
        return Arrays.asList(
            LatLon.fromDegrees(n, w), LatLon.fromDegrees(n, e),
            LatLon.fromDegrees(s, e), LatLon.fromDegrees(s, w)
        );
    }

    // ── AppFrame ──────────────────────────────────────────────────────────────

    public static class AppFrame extends ApplicationTemplate.AppFrame {
        private static final long serialVersionUID = 1L;

        private static final String[] SHAPE_NAMES = {"Hatch (left)", "Crosshatch (center)", "Dots (right)"};
        private static final int[]    MODES        = {
            ProceduralFillPattern.HATCH,
            ProceduralFillPattern.CROSSHATCH,
            ProceduralFillPattern.DOTS
        };
        private static final Color[]  FILL_COLORS  = {
            new Color(100, 149, 237),  // cornflower blue
            new Color(144, 238, 144),  // light green
            new Color(255, 160, 122)   // salmon
        };

        // Per-shape mutable pattern state
        private final float[] scales = {0.20f, 0.20f, 0.20f};
        private final float[] widths = {0.06f, 0.06f, 0.40f};  // dots: radius factor
        private final float[] angles = {45f,    0f,    0f};

        private final SurfacePolygon[] shapes = new SurfacePolygon[3];

        // Controls
        private JComboBox<String> shapeCombo;
        private JSlider           scaleSlider;
        private JSlider           widthSlider;
        private JSlider           angleSlider;
        private JLabel            widthLabel;
        private JLabel            angleLabel;

        private int     selected = 0;
        private boolean syncing  = false;  // suppress change-listener feedback while syncing

        public AppFrame() {
            super(true, true, false);

            RenderableLayer layer = new RenderableLayer();
            layer.setName("Procedural Fill Patterns");

            double[] lons = {LON_HATCH, LON_CROSS, LON_DOTS};
            for (int i = 0; i < 3; i++) {
                BasicShapeAttributes attr = new BasicShapeAttributes();
                attr.setInteriorMaterial(new Material(FILL_COLORS[i]));
                attr.setInteriorOpacity(0.55);
                attr.setOutlineMaterial(Material.WHITE);
                attr.setOutlineWidth(1.5);

                SurfacePolygon poly = new SurfacePolygon(rect(CENTER_LAT, lons[i]));
                poly.setAttributes(attr);
                poly.setFillPattern(makePattern(i));
                shapes[i] = poly;
                layer.addRenderable(poly);
            }

            insertBeforePlacenames(getWwd(), layer);

            // Fly to show all three polygons
            getWwd().getView().setEyePosition(
                Position.fromDegrees(CENTER_LAT, -103.0, 4_500_000));

            JPanel customPanel = buildControlPanel();

            // Modified by seaglassfoundry.com - put the layers panel and controls panel in a
            // tabbed pane so they don't overlap. Each tab gets a scroll pane for small windows.
            // Use a split pane between the map and the side panel so it can be resized.
            if (this.controlPanel != null)
            {
                this.getContentPane().remove(this.controlPanel);
                this.getContentPane().remove(this.wwjPanel);

                JTabbedPane tabs = new JTabbedPane();
                tabs.setBackground(new Color(45, 45, 48));

                JScrollPane layerScroll = new JScrollPane(this.layerPanel);
                layerScroll.setBorder(null);
                tabs.addTab("Layers", layerScroll);

                JScrollPane controlScroll = new JScrollPane(customPanel);
                controlScroll.setBorder(null);
                tabs.addTab("Controls", controlScroll);

                this.controlPanel.add(tabs, BorderLayout.CENTER);

                JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    this.wwjPanel, this.controlPanel);
                splitPane.setResizeWeight(0.67);
                splitPane.setDividerSize(5);
                splitPane.setContinuousLayout(true);
                this.getContentPane().add(splitPane, BorderLayout.CENTER);

                this.addComponentListener(new java.awt.event.ComponentAdapter() {
                    private boolean initialized;
                    @Override
                    public void componentResized(java.awt.event.ComponentEvent e) {
                        if (!initialized) {
                            splitPane.setDividerLocation(getWidth() * 2 / 3);
                            initialized = true;
                        }
                    }
                });
            }
        }

        // ── Pattern construction ──────────────────────────────────────────────

        private ProceduralFillPattern makePattern(int i) {
            return new ProceduralFillPattern(MODES[i], scales[i], widths[i], angles[i]);
        }

        private void applyToSelected() {
            shapes[selected].setFillPattern(makePattern(selected));
            getWwd().redraw();
        }

        // ── Control panel ─────────────────────────────────────────────────────

        // Modified by seaglassfoundry.com - fixed layout: constrain slider max height
        // so BoxLayout doesn't stretch them vertically, and ensure consistent alignment.
        private JPanel buildControlPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(WWStyle.BG_DARK);
            panel.setBorder(WWStyle.sectionBorder("Pattern Controls"));

            // ── Shape selector ────────────────────────────────────────────────
            shapeCombo = WWStyle.comboBox(SHAPE_NAMES);
            shapeCombo.setSelectedIndex(0);
            shapeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, shapeCombo.getPreferredSize().height));
            shapeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
            shapeCombo.addActionListener(e -> {
                if (!syncing) {
                    selected = shapeCombo.getSelectedIndex();
                    syncSliders();
                }
            });

            JLabel shapeLabel = WWStyle.label("Shape:");
            shapeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(shapeLabel);
            panel.add(shapeCombo);
            panel.add(vgap(WWStyle.GAP_S));

            // ── Scale ─────────────────────────────────────────────────────────
            // slider value = scale × 100; range 5–80 → 0.05°–0.80°
            scaleSlider = WWStyle.slider(5, 80, Math.round(scales[selected] * 100));
            scaleSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
            scaleSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, scaleSlider.getPreferredSize().height));
            scaleSlider.addChangeListener(e -> {
                if (!syncing) {
                    scales[selected] = scaleSlider.getValue() / 100f;
                    applyToSelected();
                }
            });
            JLabel scaleLabel = WWStyle.label("Scale (°):");
            scaleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(scaleLabel);
            panel.add(scaleSlider);
            panel.add(vgap(WWStyle.GAP_S));

            // ── Line width / dot radius ───────────────────────────────────────
            // slider value = width × 100; range 1–70
            widthLabel = WWStyle.label(widthLabelText(selected));
            widthLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            widthSlider = WWStyle.slider(1, 70, Math.round(widths[selected] * 100));
            widthSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
            widthSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, widthSlider.getPreferredSize().height));
            widthSlider.addChangeListener(e -> {
                if (!syncing) {
                    widths[selected] = widthSlider.getValue() / 100f;
                    applyToSelected();
                }
            });
            panel.add(widthLabel);
            panel.add(widthSlider);
            panel.add(vgap(WWStyle.GAP_S));

            // ── Angle (hatch only) ────────────────────────────────────────────
            angleLabel = WWStyle.label("Angle (°) — hatch only:");
            angleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            angleSlider = WWStyle.slider(0, 180, Math.round(angles[selected]));
            angleSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
            angleSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, angleSlider.getPreferredSize().height));
            angleSlider.addChangeListener(e -> {
                if (!syncing) {
                    angles[selected] = angleSlider.getValue();
                    applyToSelected();
                }
            });
            panel.add(angleLabel);
            panel.add(angleSlider);

            syncSliders();
            return panel;
        }

        private static JPanel vgap(int height) {
            JPanel gap = new JPanel();
            gap.setBackground(WWStyle.BG_DARK);
            gap.setPreferredSize(new Dimension(0, height));
            gap.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            return gap;
        }

        private String widthLabelText(int idx) {
            return MODES[idx] == ProceduralFillPattern.DOTS ? "Dot Radius (factor):" : "Line Width (°):";
        }

        /** Push current per-shape values to the sliders without triggering change listeners. */
        private void syncSliders() {
            syncing = true;

            scaleSlider.setValue(Math.round(scales[selected] * 100));
            widthSlider.setValue(Math.round(widths[selected] * 100));
            angleSlider.setValue(Math.round(angles[selected]));

            widthLabel.setText(widthLabelText(selected));

            boolean isHatch = MODES[selected] == ProceduralFillPattern.HATCH;
            angleSlider.setEnabled(isHatch);
            angleLabel.setForeground(isHatch ? WWStyle.FG_PRIMARY : WWStyle.FG_DISABLED);

            syncing = false;
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) {
        ApplicationTemplate.start("WorldWind — Procedural Fill Patterns", AppFrame.class);
    }
}
