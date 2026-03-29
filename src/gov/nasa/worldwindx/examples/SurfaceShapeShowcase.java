/*
 * WorldWind Reforged — SurfaceShapeShowcase
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New example — all surface shape types with Phase 3 fill patterns.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSlider;

import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.AbstractSurfaceShape;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.ProceduralFillPattern;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwind.render.SurfaceCircle;
import gov.nasa.worldwind.render.SurfaceEllipse;
import gov.nasa.worldwind.render.SurfacePolygon;
import gov.nasa.worldwind.render.SurfacePolyline;
import gov.nasa.worldwind.render.SurfaceQuad;
import gov.nasa.worldwind.render.SurfaceSector;
import gov.nasa.worldwind.render.airspaces.SurfaceBox;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Demonstrates every built-in surface shape type in a single view, each decorated
 * with a Phase 3 {@link ProceduralFillPattern}.
 * <p>
 * The seven shapes displayed are:
 * <ol>
 *   <li>{@link SurfacePolygon} — custom polygon outline</li>
 *   <li>{@link SurfacePolyline} — open line shape</li>
 *   <li>{@link SurfaceCircle} — circle at a centre point with a radius</li>
 *   <li>{@link SurfaceEllipse} — ellipse with major/minor radii and a heading</li>
 *   <li>{@link SurfaceQuad} — rectangle aligned to a heading</li>
 *   <li>{@link SurfaceSector} — filled lat/lon sector (bounding box)</li>
 *   <li>{@link SurfaceBox} — corridor box defined by two centre-line endpoints</li>
 * </ol>
 * The control panel lets you choose a shape, change its fill color, outline color,
 * opacity, and fill pattern (None, Hatch, Crosshatch, Dots) in real time.
 *
 * @see ProceduralFillPattern
 * @see AbstractSurfaceShape#setFillPattern(ProceduralFillPattern)
 *
 * seaglassfoundry.com — new example for WorldWind Reforged
 */
public class SurfaceShapeShowcase extends ApplicationTemplate
{
    // ── Shape layout — two rows centered on the continental US ────────────────
    //  Row 1 (top):    Polygon   Polyline  Circle  Ellipse
    //  Row 2 (bottom): Quad      Sector    Box
    private static final double ROW1_LAT =  42.0;
    private static final double ROW2_LAT =  34.0;
    private static final double[] COL_LONS = { -116.0, -104.0, -92.0, -80.0 };

    private static final double RADIUS_M   = 400_000;  // metres for circle/ellipse
    private static final double QUAD_DEG   = 6.0;      // ~6° side for quad

    // ── Pattern selector ──────────────────────────────────────────────────────
    private static final String[] PATTERN_NAMES = {"None", "Hatch", "Crosshatch", "Dots"};

    // ── AppFrame ──────────────────────────────────────────────────────────────

    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;
        private static final String[] SHAPE_NAMES = {
            "Polygon", "Polyline", "Circle", "Ellipse", "Quad", "Sector", "Box"
        };

        // The seven shapes — all are AbstractSurfaceShape
        private final AbstractSurfaceShape[] shapes = new AbstractSurfaceShape[7];
        // Per-shape current fill colors
        private final Color[] fillColors = new Color[7];
        // Per-shape current outline colors
        private final Color[] outlineColors = new Color[7];
        // Per-shape current pattern index (0=None, 1=Hatch, 2=Crosshatch, 3=Dots)
        private final int[] patternIdx = new int[7];

        private int selected = 0;

        // Controls
        private JComboBox<String> shapeCombo;
        private JButton           fillColorBtn;
        private JButton           outlineColorBtn;
        private JSlider           opacitySlider;
        private JComboBox<String> patternCombo;

        private boolean syncing = false;

        public AppFrame()
        {
            super(true, true, false);

            RenderableLayer layer = new RenderableLayer();
            layer.setName("Surface Shape Showcase");

            // Palette: one distinct color per shape
            Color[] palette = {
                new Color(100, 149, 237),  // cornflower blue  — polygon
                new Color(255, 160,  50),  // orange           — polyline
                new Color(144, 238, 144),  // light green      — circle
                new Color(220, 100, 220),  // violet           — ellipse
                new Color(255, 215,   0),  // gold             — quad
                new Color( 64, 224, 208),  // turquoise        — sector
                new Color(250, 128, 114),  // salmon           — box
            };

            for (int i = 0; i < 7; i++) {
                fillColors[i]    = palette[i];
                outlineColors[i] = Color.WHITE;
                patternIdx[i]    = i % 3 + 1;  // default: cycle Hatch/Crosshatch/Dots, then repeat
            }

            shapes[0] = makePolygon();
            shapes[1] = makePolyline();
            shapes[2] = makeCircle();
            shapes[3] = makeEllipse();
            shapes[4] = makeQuad();
            shapes[5] = makeSector();
            shapes[6] = makeBox();

            for (int i = 0; i < 7; i++) {
                applyAttributes(i);
                layer.addRenderable(shapes[i]);
            }

            insertBeforePlacenames(getWwd(), layer);

            // Fly to show all seven shapes
            getWwd().getView().setEyePosition(
                Position.fromDegrees(38.0, -98.0, 10_000_000));

            getControlPanel().add(buildControlPanel(), BorderLayout.SOUTH);
        }

        // ── Shape constructors ────────────────────────────────────────────────

        private static SurfacePolygon makePolygon()
        {
            List<LatLon> pts = new ArrayList<>();
            double lat = ROW1_LAT, lon = COL_LONS[0];
            pts.add(LatLon.fromDegrees(lat + 4,  lon - 4));
            pts.add(LatLon.fromDegrees(lat + 5,  lon));
            pts.add(LatLon.fromDegrees(lat + 4,  lon + 4));
            pts.add(LatLon.fromDegrees(lat,       lon + 3));
            pts.add(LatLon.fromDegrees(lat - 1,   lon));
            pts.add(LatLon.fromDegrees(lat,       lon - 3));
            return new SurfacePolygon(pts);
        }

        private static SurfacePolyline makePolyline()
        {
            List<LatLon> pts = new ArrayList<>();
            double lat = ROW1_LAT, lon = COL_LONS[1];
            for (int i = 0; i <= 8; i++) {
                double frac = i / 8.0;
                double dlat = Math.sin(frac * Math.PI * 2) * 3;
                pts.add(LatLon.fromDegrees(lat + dlat, lon - 4 + frac * 8));
            }
            return new SurfacePolyline(pts);
        }

        private static SurfaceCircle makeCircle()
        {
            return new SurfaceCircle(
                LatLon.fromDegrees(ROW1_LAT, COL_LONS[2]),
                RADIUS_M);
        }

        private static SurfaceEllipse makeEllipse()
        {
            return new SurfaceEllipse(
                LatLon.fromDegrees(ROW1_LAT, COL_LONS[3]),
                RADIUS_M,
                RADIUS_M * 0.5,
                Angle.fromDegrees(30));
        }

        private static SurfaceQuad makeQuad()
        {
            return new SurfaceQuad(
                LatLon.fromDegrees(ROW2_LAT, COL_LONS[0]),
                QUAD_DEG * 111_000,
                QUAD_DEG * 0.6 * 111_000,
                Angle.fromDegrees(20));
        }

        private static SurfaceSector makeSector()
        {
            double lat = ROW2_LAT, lon = COL_LONS[1];
            return new SurfaceSector(
                Sector.fromDegrees(lat - 3, lat + 3, lon - 4, lon + 4));
        }

        private static SurfaceBox makeBox()
        {
            // SurfaceBox: corridor along two endpoints
            double lat = ROW2_LAT, lon = COL_LONS[2];
            List<LatLon> locs = List.of(
                LatLon.fromDegrees(lat, lon - 4),
                LatLon.fromDegrees(lat + 2, lon + 4));
            SurfaceBox box = new SurfaceBox();
            box.setLocations(locs);
            box.setWidthSegments(4);
            return box;
        }

        // ── Attribute application ─────────────────────────────────────────────

        private void applyAttributes(int i)
        {
            AbstractSurfaceShape shape = shapes[i];

            // SurfacePolyline does not have an interior
            boolean hasInterior = !(shape instanceof SurfacePolyline);

            BasicShapeAttributes attr = new BasicShapeAttributes();
            attr.setDrawInterior(hasInterior);
            attr.setDrawOutline(true);
            attr.setInteriorMaterial(new Material(fillColors[i]));
            attr.setInteriorOpacity(opacitySlider != null ? opacitySlider.getValue() / 100.0 : 0.6);
            attr.setOutlineMaterial(new Material(outlineColors[i]));
            attr.setOutlineWidth(2.0);
            shape.setAttributes(attr);

            // Apply fill pattern (null = none)
            shape.setFillPattern(makePattern(i));
        }

        private ProceduralFillPattern makePattern(int i)
        {
            return switch (patternIdx[i]) {
                case 1 -> ProceduralFillPattern.hatch(0.20f, 0.05f, 45f);
                case 2 -> ProceduralFillPattern.crosshatch(0.20f, 0.05f);
                case 3 -> ProceduralFillPattern.dots(0.20f, 0.35f);
                default -> null;
            };
        }

        // ── Control panel ─────────────────────────────────────────────────────

        private JPanel buildControlPanel()
        {
            JPanel root = new JPanel();
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            root.setBackground(WWStyle.BG_DARK);
            root.setBorder(WWStyle.sectionBorder("Shape Controls"));

            // Shape selector
            shapeCombo = WWStyle.comboBox(SHAPE_NAMES);
            shapeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
            shapeCombo.addActionListener(e -> {
                if (!syncing) {
                    selected = shapeCombo.getSelectedIndex();
                    syncControls();
                }
            });
            root.add(makeRow("Shape:", shapeCombo));
            root.add(vgap(WWStyle.GAP_S));

            // Fill color
            fillColorBtn = colorButton(fillColors[selected]);
            fillColorBtn.addActionListener(e -> {
                Color c = JColorChooser.showDialog(this, "Choose Fill Color",
                    fillColors[selected]);
                if (c != null) {
                    fillColors[selected] = c;
                    fillColorBtn.setBackground(c);
                    applyAttributes(selected);
                    getWwd().redraw();
                }
            });
            root.add(makeRow("Fill color:", fillColorBtn));
            root.add(vgap(WWStyle.GAP_S));

            // Outline color
            outlineColorBtn = colorButton(outlineColors[selected]);
            outlineColorBtn.addActionListener(e -> {
                Color c = JColorChooser.showDialog(this, "Choose Outline Color",
                    outlineColors[selected]);
                if (c != null) {
                    outlineColors[selected] = c;
                    outlineColorBtn.setBackground(c);
                    applyAttributes(selected);
                    getWwd().redraw();
                }
            });
            root.add(makeRow("Outline color:", outlineColorBtn));
            root.add(vgap(WWStyle.GAP_S));

            // Opacity
            opacitySlider = WWStyle.slider(0, 100, 60);
            opacitySlider.setAlignmentX(Component.LEFT_ALIGNMENT);
            opacitySlider.addChangeListener(e -> {
                if (!syncing) {
                    applyAttributes(selected);
                    getWwd().redraw();
                }
            });
            root.add(WWStyle.label("Opacity:", false));
            root.add(opacitySlider);
            root.add(vgap(WWStyle.GAP_S));

            // Fill pattern
            patternCombo = WWStyle.comboBox(PATTERN_NAMES);
            patternCombo.setSelectedIndex(patternIdx[selected]);
            patternCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
            patternCombo.addActionListener(e -> {
                if (!syncing) {
                    patternIdx[selected] = patternCombo.getSelectedIndex();
                    applyAttributes(selected);
                    getWwd().redraw();
                }
            });
            root.add(makeRow("Fill pattern:", patternCombo));

            syncControls();
            return root;
        }

        /** Push the per-shape state for the currently selected shape to the controls. */
        private void syncControls()
        {
            syncing = true;
            fillColorBtn.setBackground(fillColors[selected]);
            outlineColorBtn.setBackground(outlineColors[selected]);
            if (opacitySlider != null) {
                // Derive opacity from the shape's current attribute
                ShapeAttributes attr = shapes[selected].getAttributes();
                if (attr != null)
                    opacitySlider.setValue((int) (attr.getInteriorOpacity() * 100));
            }
            patternCombo.setSelectedIndex(patternIdx[selected]);
            syncing = false;
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private static JButton colorButton(Color initial)
        {
            JButton btn = new JButton("      ");
            btn.setBackground(initial);
            btn.setOpaque(true);
            btn.setBorderPainted(true);
            btn.setPreferredSize(new Dimension(40, 20));
            btn.setMaximumSize(new Dimension(40, 20));
            return btn;
        }

        private JPanel makeRow(String text, JComponent control)
        {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, WWStyle.GAP_XS, 0));
            row.setBackground(WWStyle.BG_DARK);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(WWStyle.label(text, false));
            row.add(control);
            return row;
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

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind — Surface Shape Showcase", AppFrame.class);
    }
}
