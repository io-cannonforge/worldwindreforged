/*
 * Copyright 2025-2026 seaglassfoundry.com. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project — seaglassfoundry.com
 *
 * SurfacePolygonWithHole.java — visual smoke test for the SurfacePolygon
 * winding-order fix in buildInteriorVBOs(). Three side-by-side donut polygons
 * built with different (outer, hole) winding combinations. With a correct fix
 * (outer normalized CCW, holes normalized CW) all three should render as clean
 * donuts. With a broken or partial fix the offending donut's interior renders
 * as a self-intersecting/scribbled fill.
 */
package gov.nasa.worldwindx.examples;

import java.awt.Color;
import java.awt.Font;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.SurfacePolygon;
import gov.nasa.worldwind.render.SurfaceText;

/**
 * Three SurfacePolygon donuts side-by-side, one per (outer, hole) winding
 * combination. Used to eyeball whether the fix in
 * {@link gov.nasa.worldwind.render.SurfacePolygon#buildInteriorVBOs} normalizes
 * winding correctly before bridging holes.
 *
 * <ul>
 *   <li><b>Left</b>  — outer CCW + hole CW: matches the bridgeHoles contract; should always render cleanly.</li>
 *   <li><b>Center</b> — outer CW + hole CW: the case the current outer-flip fix targets.</li>
 *   <li><b>Right</b>  — outer CCW + hole CCW: the gap the current fix does not yet close.</li>
 * </ul>
 */
public class SurfacePolygonWithHole extends ApplicationTemplate
{
    // Centred over the western US so all three donuts fit on screen at once.
    private static final double CENTER_LAT  = 38.0;
    private static final double LON_LEFT    = -115.0;
    private static final double LON_CENTER  = -103.0;
    private static final double LON_RIGHT   = -91.0;
    private static final double OUTER_HALF  = 4.0;
    private static final double HOLE_HALF   = 1.5;
    private static final double LABEL_OFFSET = OUTER_HALF + 1.0;

    /** SW, SE, NE, NW — counter-clockwise when viewed from above. */
    private static List<LatLon> ccwSquare(double lat, double lon, double half)
    {
        return Arrays.asList(
            LatLon.fromDegrees(lat - half, lon - half),
            LatLon.fromDegrees(lat - half, lon + half),
            LatLon.fromDegrees(lat + half, lon + half),
            LatLon.fromDegrees(lat + half, lon - half));
    }

    private static List<LatLon> cwSquare(double lat, double lon, double half)
    {
        List<LatLon> ccw = ccwSquare(lat, lon, half);
        Collections.reverse(ccw);
        return ccw;
    }

    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;

        public AppFrame()
        {
            super(true, true, false);

            RenderableLayer layer = new RenderableLayer();
            layer.setName("SurfacePolygon winding test");

            addDonut(layer, LON_LEFT,
                ccwSquare(CENTER_LAT, LON_LEFT,  OUTER_HALF),
                cwSquare(CENTER_LAT,  LON_LEFT,  HOLE_HALF),
                new Color(100, 200, 100), "outer CCW + hole CW (correct)");

            addDonut(layer, LON_CENTER,
                cwSquare(CENTER_LAT,  LON_CENTER, OUTER_HALF),
                cwSquare(CENTER_LAT,  LON_CENTER, HOLE_HALF),
                new Color(255, 180,  80), "outer CW + hole CW (outer-flip fix)");

            addDonut(layer, LON_RIGHT,
                ccwSquare(CENTER_LAT, LON_RIGHT,  OUTER_HALF),
                ccwSquare(CENTER_LAT, LON_RIGHT,  HOLE_HALF),
                new Color(220, 110, 110), "outer CCW + hole CCW (still broken)");

            insertBeforePlacenames(getWwd(), layer);

            getWwd().getView().setEyePosition(
                Position.fromDegrees(CENTER_LAT, LON_CENTER, 4_500_000));
        }

        private void addDonut(RenderableLayer layer, double labelLon,
            List<LatLon> outer, List<LatLon> hole, Color fill, String label)
        {
            BasicShapeAttributes attr = new BasicShapeAttributes();
            attr.setInteriorMaterial(new Material(fill));
            attr.setInteriorOpacity(0.55);
            attr.setOutlineMaterial(Material.WHITE);
            attr.setOutlineWidth(1.5);

            SurfacePolygon poly = new SurfacePolygon(outer);
            poly.addInnerBoundary(hole);
            poly.setAttributes(attr);
            layer.addRenderable(poly);

            SurfaceText text = new SurfaceText(label,
                Position.fromDegrees(CENTER_LAT + LABEL_OFFSET, labelLon, 0));
            text.setFont(new Font("Arial", Font.BOLD, 14));
            text.setColor(Color.WHITE);
            text.setTextSize(80_000);
            layer.addRenderable(text);
        }
    }

    public static void main(String[] args)
    {
        ApplicationTemplate.start("SurfacePolygon winding-order smoke test", AppFrame.class);
    }
}
