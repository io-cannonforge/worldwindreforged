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
 */

package gov.nasa.worldwindx.examples;

import java.util.ArrayList;

import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.ExtrudedPolygon;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwind.view.orbit.OrbitView;

/**
 * Shows how to use {@link ExtrudedPolygon} with a specified base depth that places the extruded polygon's base vertices
 * below the terrain. You might want to do this if the extruded polygon spans a valley and the polygon boundary is not
 * sampled in sufficient detail to capture the valley. Specifying a base depth can fill the gap between the base and the
 * valley floor.
 *
 * @author tag
 * @version $Id: ExtrudedPolygonWithBaseDepth.java 2109 2014-06-30 16:52:38Z tgaskins $
 */
public class ExtrudedPolygonWithBaseDepth extends ApplicationTemplate
{
    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;
        public AppFrame()
        {
            super(true, true, false);

            // Modified by seaglassfoundry.com - reworked example with larger polygon over flat
            // coastal terrain so the orbit view center at sea level doesn't embed in terrain.
            // The orbit view does not resolve terrain collisions for the initial camera position
            // (resolveCollisionsWithCenterPosition requires a DrawContext, unavailable at init).
            RenderableLayer layer = new RenderableLayer();

            // Create and set an attribute bundle.
            ShapeAttributes sideAttributes = new BasicShapeAttributes();
            sideAttributes.setInteriorMaterial(Material.MAGENTA);
            sideAttributes.setOutlineOpacity(0.5);
            sideAttributes.setInteriorOpacity(0.5);
            sideAttributes.setOutlineMaterial(Material.GREEN);
            sideAttributes.setOutlineWidth(2);
            sideAttributes.setDrawOutline(true);
            sideAttributes.setDrawInterior(true);

            ShapeAttributes capAttributes = new BasicShapeAttributes(sideAttributes);
            capAttributes.setInteriorMaterial(Material.YELLOW);
            capAttributes.setInteriorOpacity(0.5);
            capAttributes.setDrawInterior(true);

            // Create an extruded polygon over flat coastal terrain (Outer Banks, NC area).
            // The cap sits 200m above ground. The base depth of 200m pushes the base vertices
            // below the terrain surface, demonstrating how base depth fills gaps where terrain
            // dips below the polygon boundary.
            ArrayList<Position> pathPositions = new ArrayList<>();
            pathPositions.add(Position.fromDegrees(35.51, -75.51, 200));
            pathPositions.add(Position.fromDegrees(35.51, -75.49, 200));
            pathPositions.add(Position.fromDegrees(35.49, -75.49, 200));
            pathPositions.add(Position.fromDegrees(35.49, -75.51, 200));
            pathPositions.add(Position.fromDegrees(35.51, -75.51, 200));
            ExtrudedPolygon pgon = new ExtrudedPolygon(pathPositions);

            pgon.setAltitudeMode(WorldWind.RELATIVE_TO_GROUND);
            pgon.setSideAttributes(sideAttributes);
            pgon.setCapAttributes(capAttributes);
            pgon.setBaseDepth(200); // Push base 200m below terrain to fill any gaps.
            layer.addRenderable(pgon);

            // Add the layer to the model.
            insertBeforeCompass(getWwd(), layer);

            // Set an angled orbit view looking at the polygon. Using flat coastal terrain
            // ensures center elevation 0 is at ground level, avoiding the camera-in-terrain
            // bug caused by the orbit view not resolving terrain collisions at init time.
            OrbitView view = (OrbitView) getWwd().getView();
            view.setCenterPosition(Position.fromDegrees(35.50, -75.50, 0));
            view.setHeading(Angle.fromDegrees(0));
            view.setPitch(Angle.fromDegrees(50));
            view.setZoom(5000);
        }
    }

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind Extruded Polygon with Base Depth", AppFrame.class);
    }
}
