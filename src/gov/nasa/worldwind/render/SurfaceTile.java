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
package gov.nasa.worldwind.render;

import java.util.List;

import gov.nasa.worldwind.geom.Extent;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Sector;

/**
 * @author tag
 * @version $Id: SurfaceTile.java 1171 2013-02-11 21:45:02Z dcollins $
 */
public interface SurfaceTile
{
    boolean bind(DrawContext dc);
    void applyInternalTransform(DrawContext dc, boolean textureIdentityActive);
    Sector getSector();
    Extent getExtent(DrawContext dc);
    List<? extends LatLon> getCorners();

    // seaglassfoundry.com: shader-path texture matrix computation.
    // Returns the internal texture transform (flip, fallback scaling) as a
    // scale+translate quadruple [sx, sy, tx, ty].  SurfaceTileRenderer uses
    // this to compute the full texture matrix in Java, bypassing the
    // fixed-function matrix stack and eliminating glGetFloatv readbacks.
    /**
     * Returns this tile's internal texture transform as a scale+translate.
     * The result is written into {@code out} as {@code [sx, sy, tx, ty]}
     * representing the 2D affine transform that would be applied by
     * {@link #applyInternalTransform}.  The default implementation returns
     * identity (no flip, no fallback).
     *
     * @param dc  the current draw context
     * @param out a double[4] array to receive [sx, sy, tx, ty]
     */
    default void getTextureTransform(DrawContext dc, double[] out)
    {
        out[0] = 1; out[1] = 1; out[2] = 0; out[3] = 0;
    }
}
