/*
 * Copyright 2025-2026 seaglassfoundry.com. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 */
package gov.nasa.worldwindx.examples;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwind.render.SurfacePolylines;

/**
 * Demonstrates {@link SurfacePolylines} — the batch-rendering sibling of
 * {@link gov.nasa.worldwind.render.SurfacePolyline} — constructed from a
 * multi-line-string (an iterable of line strings).
 * <p>
 * This example builds a lawn-mower / boustrophedon pattern: ten parallel
 * east–west tracks stacked at evenly spaced latitudes, with every other
 * track running in the opposite direction. All ten tracks are handed to a
 * single {@code SurfacePolylines} instance via the new multi-line-string
 * constructor, so the entire pattern is rendered in one tessellation pass.
 * Because the tracks are disjoint, no connecting segments appear at the
 * row ends — confirming the multi-line-string semantics are preserved
 * through the {@link gov.nasa.worldwind.util.CompoundVecBuffer} plumbing.
 */
public class SurfacePolylinesExample extends ApplicationTemplate
{
    private static final double CENTER_LON = -160.0;

    // Yellow solid-line set — centered on the equator.
    private static final double SOLID_CENTER_LAT = 0.0;
    // Green dashed-line set — a second batch placed south of the solid one so the
    // two SurfacePolylines instances are visually distinct and adjacent.
    private static final double DASHED_CENTER_LAT = -5.0;

    // Focal latitude for camera framing: between the two bands.
    private static final double VIEW_CENTER_LAT = -2.5;

    private static final int    LINE_COUNT      = 10;
    private static final double LATITUDE_SPAN   = 4.0;  // total N-S extent, degrees
    private static final double LONGITUDE_SPAN  = 8.0;  // total E-W extent, degrees
    private static final int    POINTS_PER_LINE = 6;    // including both endpoints

    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;

        public AppFrame()
        {
            super(true, true, false);

            RenderableLayer layer = new RenderableLayer();
            layer.setName("Surface Polylines (batch)");

            // Batch 1 — solid yellow tracks, centred on the equator.
            List<List<LatLon>> solidTracks = buildLawnmowerPattern(
                SOLID_CENTER_LAT, CENTER_LON,
                LINE_COUNT, LATITUDE_SPAN, LONGITUDE_SPAN, POINTS_PER_LINE);
            SurfacePolylines solid = new SurfacePolylines(solidTracks);
            solid.setAttributes(buildSolidAttributes());
            layer.addRenderable(solid);

            // Batch 2 — dashed green tracks, a second SurfacePolylines instance
            // placed just south of the first so both batches are visible together.
            List<List<LatLon>> dashedTracks = buildLawnmowerPattern(
                DASHED_CENTER_LAT, CENTER_LON,
                LINE_COUNT, LATITUDE_SPAN, LONGITUDE_SPAN, POINTS_PER_LINE);
            SurfacePolylines dashed = new SurfacePolylines(dashedTracks);
            dashed.setAttributes(buildDashedAttributes());
            layer.addRenderable(dashed);

            insertBeforePlacenames(this.getWwd(), layer);
        }

        /**
         * Builds a boustrophedon grid: {@code lineCount} parallel E–W line strings
         * evenly spaced in latitude, each spanning {@code lonSpan} degrees, with every
         * other row reversed so the pattern "runs back and forth". Each line contains
         * {@code pointsPerLine} evenly-spaced vertices.
         */
        private static List<List<LatLon>> buildLawnmowerPattern(double centerLat, double centerLon,
            int lineCount, double latSpan, double lonSpan, int pointsPerLine)
        {
            List<List<LatLon>> lines = new ArrayList<>(lineCount);
            double startLat = centerLat - latSpan / 2.0;
            double startLon = centerLon - lonSpan / 2.0;
            double endLon   = centerLon + lonSpan / 2.0;
            double latStep  = latSpan / (lineCount - 1);

            for (int row = 0; row < lineCount; row++)
            {
                double lat = startLat + row * latStep;
                double fromLon = (row % 2 == 0) ? startLon : endLon;
                double toLon   = (row % 2 == 0) ? endLon   : startLon;

                List<LatLon> line = new ArrayList<>(pointsPerLine);
                for (int i = 0; i < pointsPerLine; i++)
                {
                    double t = i / (double) (pointsPerLine - 1);
                    double lon = fromLon + t * (toLon - fromLon);
                    line.add(LatLon.fromDegrees(lat, lon));
                }
                lines.add(line);
            }
            return lines;
        }

        private static ShapeAttributes buildSolidAttributes()
        {
            ShapeAttributes attrs = new BasicShapeAttributes();
            attrs.setDrawInterior(false);
            attrs.setOutlineMaterial(new Material(new Color(255, 220, 80)));
            attrs.setOutlineWidth(2.0);
            attrs.setOutlineOpacity(1.0);
            attrs.setEnableAntialiasing(true);
            return attrs;
        }

        private static ShapeAttributes buildDashedAttributes()
        {
            ShapeAttributes attrs = new BasicShapeAttributes();
            attrs.setDrawInterior(false);
            attrs.setOutlineMaterial(new Material(new Color(80, 220, 120)));
            attrs.setOutlineWidth(2.0);
            attrs.setOutlineOpacity(1.0);
            attrs.setOutlineStippleFactor(3);
            attrs.setOutlineStipplePattern((short) 0xAAAA);
            attrs.setEnableAntialiasing(true);
            return attrs;
        }
    }

    public static void main(String[] args)
    {
        Configuration.setValue(AVKey.INITIAL_LATITUDE,  VIEW_CENTER_LAT);
        Configuration.setValue(AVKey.INITIAL_LONGITUDE, CENTER_LON);
        Configuration.setValue(AVKey.INITIAL_ALTITUDE,  3_000_000.0); // ~3000 km — frames both batches
        ApplicationTemplate.start("WorldWind SurfacePolylines (multi-line-string)", AppFrame.class);
    }
}
