/*
 * Copyright 2025-2026 seaglassfoundry.com. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — stress test for sub-metre surface-shape rendering precision.
 * A collection of very small SurfaceShape instances (≤ 20 m) is placed at a
 * single location and the camera is parked directly overhead at ~60 m altitude.
 * Useful for validating the RTE (reference-point) vertex path that preserves
 * line geometry when the viewer is close enough for naive float32 MVP rounding
 * to produce visible wobble.
 */
package gov.nasa.worldwindx.examples;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.AnnotationLayer;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.AnnotationAttributes;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.GlobeAnnotation;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwind.render.SurfaceCircle;
import gov.nasa.worldwind.render.SurfaceEllipse;
import gov.nasa.worldwind.render.SurfacePolygon;
import gov.nasa.worldwind.render.SurfacePolyline;
import gov.nasa.worldwind.render.SurfaceSquare;
import gov.nasa.worldwind.terrain.ZeroElevationModel;
import gov.nasa.worldwind.view.orbit.OrbitView;

/**
 * A collection of very small {@link gov.nasa.worldwind.render.SurfaceShape}s
 * (all under ~20 m across) grouped at a single location, with the camera
 * framed directly overhead at close range. The centrepiece is a 32-sided
 * {@link SurfacePolygon} approximating a 10 m-radius circle — at this scale
 * each vertex is about 1 m from its neighbours, which is well below the
 * effective resolution of a float32 MVP transform. With the double-precision
 * shader path enabled the outline is rock-steady; with the float path the
 * same outline wobbles visibly when the viewer pans or rotates.
 *
 * @author seaglassfoundry.com
 */
public class SmallSurfaceShapesDemo extends ApplicationTemplate
{
    // ── Location: Times Square, NYC — arbitrary but identifiable ─────────────
    private static final double CENTER_LAT = 40.7580;
    private static final double CENTER_LON = -73.9855;

    // ── Earth radius used for metres → radians conversion ────────────────────
    private static final double EARTH_RADIUS_M = 6_378_137.0;

    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;

        public AppFrame()
        {
            super(true, true, false);

            // Flatten the terrain so any tessellator distortion isn't a confounding factor in
            // the precision test — surface shapes then drape onto a perfect sphere at 0 m.
            this.getWwd().getModel().getGlobe().setElevationModel(new ZeroElevationModel());

            RenderableLayer layer = new RenderableLayer();
            layer.setName("Small Surface Shapes");

            AnnotationLayer labels = new AnnotationLayer();
            labels.setName("Shape Labels");

            LatLon center = LatLon.fromDegrees(CENTER_LAT, CENTER_LON);

            // 1. Featured shape — 32-sided polygon approximating a 10 m-radius circle.
            //    Rendered as a SurfacePolygon (not SurfaceCircle) so the RTE vertex
            //    path in AbstractSurfaceShape / SurfacePolygon is exercised directly.
            List<LatLon> ring32 = buildCircleVertices(center, 10.0, 32);
            SurfacePolygon circle32 = new SurfacePolygon(ring32);
            circle32.setAttributes(shapeAttrs(new Color(255, 200,  60), new Color(255, 200,  60, 60), 2.0));
            layer.addRenderable(circle32);
            labels.addAnnotation(label("32-gon, r = 10 m\n(SurfacePolygon)",
                offset(center, 0.0, 12.0)));

            // 2. Tiny triangular polygon — 8 m sides, west of centre
            LatLon triCenter = offset(center, 270.0, 25.0);
            List<LatLon> triangle = Arrays.asList(
                offset(triCenter,   0.0, 5.0),
                offset(triCenter, 120.0, 5.0),
                offset(triCenter, 240.0, 5.0));
            SurfacePolygon triPoly = new SurfacePolygon(triangle);
            triPoly.setAttributes(shapeAttrs(new Color(100, 220, 255), new Color(100, 220, 255, 60), 2.0));
            layer.addRenderable(triPoly);
            labels.addAnnotation(label("Triangle\n~8 m sides", triCenter));

            // 3. Small SurfaceSquare — 15 m side, north of centre
            LatLon squareCenter = offset(center, 0.0, 25.0);
            SurfaceSquare square = new SurfaceSquare(squareCenter, 15.0);
            square.setHeading(Angle.fromDegrees(20));
            square.setAttributes(shapeAttrs(new Color(255, 120, 200), new Color(255, 120, 200, 60), 2.0));
            layer.addRenderable(square);
            labels.addAnnotation(label("Square, 15 m\nheading 20°", squareCenter));

            // 4. Small SurfaceEllipse — 8 × 4 m axes, south of centre
            LatLon ellipseCenter = offset(center, 180.0, 25.0);
            SurfaceEllipse ellipse = new SurfaceEllipse(ellipseCenter, 8.0, 4.0, Angle.fromDegrees(45));
            ellipse.setAttributes(shapeAttrs(new Color(160, 255, 120), new Color(160, 255, 120, 60), 2.0));
            layer.addRenderable(ellipse);
            labels.addAnnotation(label("Ellipse\n8 × 4 m, rot 45°", ellipseCenter));

            // 5. Small SurfaceCircle — 6 m radius, northeast of centre
            LatLon smallCircleCenter = offset(center, 45.0, 25.0);
            SurfaceCircle smallCircle = new SurfaceCircle(smallCircleCenter, 6.0);
            smallCircle.setAttributes(shapeAttrs(new Color(255, 255, 255), new Color(255, 255, 255, 50), 2.0));
            layer.addRenderable(smallCircle);
            labels.addAnnotation(label("Circle\nr = 6 m", smallCircleCenter));

            // 6. Zig-zag SurfacePolyline — ~30 m end-to-end, east of centre.
            //    Short segments are the worst case for float-rounding wobble.
            LatLon lineStart = offset(center, 90.0, 15.0);
            List<LatLon> zigzag = new ArrayList<>();
            zigzag.add(lineStart);
            zigzag.add(offset(lineStart,  60.0, 4.0));
            zigzag.add(offset(lineStart,  20.0, 8.0));
            zigzag.add(offset(lineStart,  80.0, 12.0));
            zigzag.add(offset(lineStart,  30.0, 18.0));
            zigzag.add(offset(lineStart,  75.0, 24.0));
            SurfacePolyline polyline = new SurfacePolyline(zigzag);
            ShapeAttributes lineAttrs = new BasicShapeAttributes();
            lineAttrs.setDrawInterior(false);
            lineAttrs.setOutlineMaterial(new Material(new Color(255,  80,  80)));
            lineAttrs.setOutlineWidth(2.5);
            lineAttrs.setEnableAntialiasing(true);
            polyline.setAttributes(lineAttrs);
            layer.addRenderable(polyline);
            labels.addAnnotation(label("Polyline\n~30 m zig-zag", zigzag.get(3)));

            // 7. Dashed outline version of the 32-gon (no interior), just inside the main one —
            //    exercises the RTE path through DashLineShader as well as the fill shader.
            List<LatLon> ring32Inner = buildCircleVertices(center, 9.0, 32);
            SurfacePolygon dashed32 = new SurfacePolygon(ring32Inner);
            ShapeAttributes dashAttrs = new BasicShapeAttributes();
            dashAttrs.setDrawInterior(false);
            dashAttrs.setOutlineMaterial(new Material(new Color(255, 200,  60)));
            dashAttrs.setOutlineWidth(1.5);
            dashAttrs.setOutlineStippleFactor(1);
            dashAttrs.setOutlineStipplePattern((short) 0xF0F0);
            dashAttrs.setEnableAntialiasing(true);
            dashed32.setAttributes(dashAttrs);
            layer.addRenderable(dashed32);
            labels.addAnnotation(label("Dashed 32-gon\nr = 9 m", offset(center, 180.0, 12.0)));

            insertBeforePlacenames(this.getWwd(), layer);
            insertBeforePlacenames(this.getWwd(), labels);

            // ── Camera: park directly overhead, tight framing ────────────────
            OrbitView view = (OrbitView) getWwd().getView();
            view.setCenterPosition(Position.fromDegrees(CENTER_LAT, CENTER_LON, 0));
            view.setHeading(Angle.ZERO);
            view.setPitch(Angle.ZERO);
            view.setZoom(80.0);  // ~80 m above ground — shapes fill roughly half the view

            // Disable roll-over highlight so the outlines aren't hidden by a highlight tint
            // when the mouse hovers over them during the A/B precision test.
            this.setHighlightController(null);
        }

        // ── Geometry helpers ─────────────────────────────────────────────────

        /** Build a regular {@code sides}-gon around {@code center} with the given radius in metres. */
        private static List<LatLon> buildCircleVertices(LatLon center, double radiusMeters, int sides)
        {
            List<LatLon> ring = new ArrayList<>(sides);
            double distRad = radiusMeters / EARTH_RADIUS_M;
            for (int i = 0; i < sides; i++)
            {
                double azDeg = i * 360.0 / sides;
                ring.add(LatLon.greatCircleEndPosition(center,
                    Angle.fromDegrees(azDeg), Angle.fromRadians(distRad)));
            }
            return ring;
        }

        /** @return the point {@code distanceMeters} away from {@code from} along bearing {@code azimuthDeg}. */
        private static LatLon offset(LatLon from, double azimuthDeg, double distanceMeters)
        {
            double distRad = distanceMeters / EARTH_RADIUS_M;
            return LatLon.greatCircleEndPosition(from,
                Angle.fromDegrees(azimuthDeg), Angle.fromRadians(distRad));
        }

        // ── Style helpers ────────────────────────────────────────────────────

        /** Shared, frameless annotation attributes — compact text that doesn't occlude the shapes. */
        private static final AnnotationAttributes LABEL_ATTRS = buildLabelAttrs();

        private static AnnotationAttributes buildLabelAttrs()
        {
            AnnotationAttributes a = new AnnotationAttributes();
            a.setFrameShape(AVKey.SHAPE_NONE);
            a.setLeader(AVKey.SHAPE_NONE);
            a.setDrawOffset(new java.awt.Point(0, 0));
            a.setTextAlign(AVKey.CENTER);
            a.setTextColor(Color.WHITE);
            a.setFont(Font.decode("Arial-BOLD-11"));
            a.setBackgroundColor(new Color(0, 0, 0, 140));
            a.setInsets(new java.awt.Insets(2, 4, 2, 4));
            return a;
        }

        private static GlobeAnnotation label(String text, LatLon location)
        {
            return new GlobeAnnotation(text,
                Position.fromDegrees(location.getLatitude().degrees, location.getLongitude().degrees, 0),
                LABEL_ATTRS);
        }

        private static ShapeAttributes shapeAttrs(Color outline, Color interior, double outlineWidth)
        {
            ShapeAttributes attrs = new BasicShapeAttributes();
            attrs.setDrawInterior(true);
            attrs.setInteriorMaterial(new Material(interior));
            attrs.setInteriorOpacity(interior.getAlpha() / 255.0);
            attrs.setOutlineMaterial(new Material(outline));
            attrs.setOutlineWidth(outlineWidth);
            attrs.setEnableAntialiasing(true);
            return attrs;
        }
    }

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind Small Surface Shapes (Sub-metre Precision Test)", AppFrame.class);
    }
}
