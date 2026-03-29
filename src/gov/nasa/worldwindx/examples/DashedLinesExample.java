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
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwind.render.SurfaceCircle;
import gov.nasa.worldwind.render.SurfaceEllipse;
import gov.nasa.worldwind.render.SurfacePolygon;
import gov.nasa.worldwind.render.SurfacePolyline;

/**
 * Demonstrates shader-based dashed line rendering for surface shapes. The dashes are rendered using a GLSL fragment
 * shader that discards pixels in the gap portions, replacing the deprecated GL2 {@code glLineStipple()} call.
 * <p>
 * The example shows several surface polylines and polygons with different dash patterns, line widths, and colors.
 */
public class DashedLinesExample extends ApplicationTemplate
{
    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;
        public AppFrame()
        {
            super(true, true, false);

            RenderableLayer layer = new RenderableLayer();
            layer.setName("Dashed Lines");

            // 1. Solid line (no stipple) — for comparison
            addPolyline(layer, "Solid Line", Color.WHITE, 2.0,
                0, (short) 0xFFFF,
                LatLon.fromDegrees(40, -120), LatLon.fromDegrees(40, -80));

            // 2. Fine dashed line (factor=1, 0xF0F0 = ████░░░░████░░░░)
            addPolyline(layer, "Fine Dash (1, 0xF0F0)", Color.CYAN, 2.0,
                1, (short) 0xF0F0,
                LatLon.fromDegrees(38, -120), LatLon.fromDegrees(38, -80));

            // 3. Medium dashed line (factor=2, 0xFF00 = ████████░░░░░░░░)
            addPolyline(layer, "Medium Dash (2, 0xFF00)", Color.YELLOW, 2.0,
                2, (short) 0xFF00,
                LatLon.fromDegrees(36, -120), LatLon.fromDegrees(36, -80));

            // 4. Wide dashed line (factor=3, 0xF0F0)
            addPolyline(layer, "Wide Dash (3, 0xF0F0)", Color.GREEN, 3.0,
                3, (short) 0xF0F0,
                LatLon.fromDegrees(34, -120), LatLon.fromDegrees(34, -80));

            // 5. Dotted line (factor=1, 0xAAAA = ░█░█░█░█░█░█░█░█)
            addPolyline(layer, "Dotted (1, 0xAAAA)", Color.ORANGE, 2.0,
                1, (short) 0xAAAA,
                LatLon.fromDegrees(32, -120), LatLon.fromDegrees(32, -80));

            // 6. Dash-dot (factor=2, 0xFFC8 = ██████████░░░█░░░)
            addPolyline(layer, "Dash-Dot (2, 0xFFC8)", new Color(255, 100, 200), 2.0,
                2, (short) 0xFFC8,
                LatLon.fromDegrees(30, -120), LatLon.fromDegrees(30, -80));

            // 7. Thick dashed line
            addPolyline(layer, "Thick Dash (2, 0xFF00)", Color.RED, 5.0,
                2, (short) 0xFF00,
                LatLon.fromDegrees(28, -120), LatLon.fromDegrees(28, -80));

            // 8. Dashed polygon
            addDashedPolygon(layer, "Dashed Pentagon", new Color(100, 200, 255), 2.0,
                2, (short) 0xFF00,
                37, -100, 4.0, 5);

            // 9. Dashed circle
            SurfaceCircle circle = new SurfaceCircle(LatLon.fromDegrees(37, -110), 200000);
            ShapeAttributes circleAttrs = new BasicShapeAttributes();
            circleAttrs.setDrawInterior(false);
            circleAttrs.setOutlineMaterial(new Material(new Color(255, 200, 50)));
            circleAttrs.setOutlineWidth(2.0);
            circleAttrs.setOutlineStippleFactor(2);
            circleAttrs.setOutlineStipplePattern((short) 0xF0F0);
            circleAttrs.setEnableAntialiasing(true);
            circle.setAttributes(circleAttrs);
            layer.addRenderable(circle);

            // 10. Dashed ellipse
            SurfaceEllipse ellipse = new SurfaceEllipse(LatLon.fromDegrees(33, -100), 300000, 150000, Angle.fromDegrees(30));
            ShapeAttributes ellipseAttrs = new BasicShapeAttributes();
            ellipseAttrs.setDrawInterior(true);
            ellipseAttrs.setInteriorMaterial(new Material(new Color(100, 255, 100, 60)));
            ellipseAttrs.setInteriorOpacity(0.25);
            ellipseAttrs.setOutlineMaterial(new Material(new Color(100, 255, 100)));
            ellipseAttrs.setOutlineWidth(2.0);
            ellipseAttrs.setOutlineStippleFactor(1);
            ellipseAttrs.setOutlineStipplePattern((short) 0xF0F0);
            ellipseAttrs.setEnableAntialiasing(true);
            ellipse.setAttributes(ellipseAttrs);
            layer.addRenderable(ellipse);

            // Add the layer
            insertBeforePlacenames(this.getWwd(), layer);

            // Set the view
            this.getWwd().getView().setEyePosition(
                Position.fromDegrees(35, -100, 5000000));

            // Add a legend panel
            this.getContentPane().add(createLegendPanel(), BorderLayout.EAST);
        }

        private static void addPolyline(RenderableLayer layer, String name, Color color, double width,
                                         int stippleFactor, short stipplePattern, LatLon... points)
        {
            SurfacePolyline line = new SurfacePolyline(Arrays.asList(points));
            ShapeAttributes attrs = new BasicShapeAttributes();
            attrs.setDrawInterior(false);
            attrs.setOutlineMaterial(new Material(color));
            attrs.setOutlineWidth(width);
            attrs.setOutlineStippleFactor(stippleFactor);
            attrs.setOutlineStipplePattern(stipplePattern);
            attrs.setEnableAntialiasing(true);
            line.setAttributes(attrs);
            layer.addRenderable(line);
        }

        private static void addDashedPolygon(RenderableLayer layer, String name, Color color, double width,
                                              int stippleFactor, short stipplePattern,
                                              double centerLat, double centerLon, double radius, int sides)
        {
            List<LatLon> positions = new ArrayList<>();
            for (int i = 0; i < sides; i++)
            {
                double angle = 2 * Math.PI * i / sides - Math.PI / 2;
                positions.add(LatLon.fromDegrees(
                    centerLat + radius * Math.sin(angle),
                    centerLon + radius * Math.cos(angle)));
            }

            SurfacePolygon polygon = new SurfacePolygon(positions);
            ShapeAttributes attrs = new BasicShapeAttributes();
            attrs.setDrawInterior(true);
            attrs.setInteriorMaterial(new Material(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40)));
            attrs.setInteriorOpacity(0.15);
            attrs.setOutlineMaterial(new Material(color));
            attrs.setOutlineWidth(width);
            attrs.setOutlineStippleFactor(stippleFactor);
            attrs.setOutlineStipplePattern(stipplePattern);
            attrs.setEnableAntialiasing(true);
            polygon.setAttributes(attrs);
            layer.addRenderable(polygon);
        }

        private static JPanel createLegendPanel()
        {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(new Color(45, 45, 48));
            panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(80, 83, 85)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
            panel.setPreferredSize(new Dimension(250, 0));

            JLabel title = new JLabel("Shader-Based Dashed Lines");
            title.setFont(new Font("Segoe UI", Font.BOLD, 14));
            title.setForeground(new Color(220, 220, 220));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(title);
            panel.add(javax.swing.Box.createVerticalStrut(8));

            String[] labels = {
                "1. Solid (no stipple)",
                "2. Fine dash (factor=1)",
                "3. Medium dash (factor=2)",
                "4. Wide dash (factor=3)",
                "5. Dotted (0xAAAA)",
                "6. Dash-dot (0xFFC8)",
                "7. Thick dash (width=5)",
                "8. Dashed pentagon",
                "9. Dashed circle",
                "10. Dashed ellipse"
            };
            Color[] colors = {
                Color.WHITE, Color.CYAN, Color.YELLOW, Color.GREEN, Color.ORANGE,
                new Color(255, 100, 200), Color.RED, new Color(100, 200, 255),
                new Color(255, 200, 50), new Color(100, 255, 100)
            };

            for (int i = 0; i < labels.length; i++)
            {
                JLabel label = new JLabel(labels[i]);
                label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                label.setForeground(colors[i]);
                label.setAlignmentX(Component.LEFT_ALIGNMENT);
                panel.add(label);
                panel.add(javax.swing.Box.createVerticalStrut(4));
            }

            panel.add(javax.swing.Box.createVerticalStrut(16));

            JLabel note = new JLabel("<html><body style='width:200px; color:#aaa; font-size:10px;'>"
                + "Dashes are rendered with a GLSL fragment shader that replaces the deprecated "
                + "GL2 glLineStipple(). The shader discards fragments in gap regions based on "
                + "cumulative distance along the line."
                + "</body></html>");
            note.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(note);

            return panel;
        }
    }

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind Dashed Lines (Shader)", AppFrame.class);
    }
}
