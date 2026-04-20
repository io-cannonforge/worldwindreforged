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

package gov.nasa.worldwind.render.airspaces;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jogamp.opengl.GL2;

import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.globes.Globe;
import gov.nasa.worldwind.render.AbstractSurfaceShape;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.SurfaceTileDrawContext;

public class SurfaceBox extends AbstractSurfaceShape
{
    protected List<LatLon> locations;
    protected int lengthSegments;
    protected int widthSegments;
    protected boolean enableStartCap = true;
    protected boolean enableEndCap = true;
    protected boolean enableCenterLine;
    protected List<List<LatLon>> activeCenterLineGeometry = new ArrayList<>(); // re-determined each frame

    public SurfaceBox()
    {
    }

    public List<LatLon> getLocations()
    {
        return this.locations;
    }

    public void setLocations(List<LatLon> locations)
    {
        this.locations = locations;
        this.onShapeChanged();
    }

    /**
     * Convenience setter that expands a two-endpoint centre line into the full perimeter vertex
     * list {@link #createGeometry} expects, and assigns both the perimeter and the segment counts
     * in one call. Use this when you have a simple corridor (two endpoints + a half-width on each
     * side) rather than a pre-tessellated perimeter. For complex shapes with asymmetric end
     * azimuths, use {@link Box} and let its airspace geometry produce the perimeter.
     *
     * @param globe             globe used to convert metre widths to arc-length radians.
     * @param beginLocation     one endpoint of the corridor centre line.
     * @param endLocation       the other endpoint.
     * @param leftWidthMetres   corridor half-width on the left of the begin→end direction.
     * @param rightWidthMetres  corridor half-width on the right of the begin→end direction.
     * @param lengthSegments    number of segments along each long side (must be ≥ 1).
     * @param widthSegments     number of segments along each half of a cap (must be ≥ 1).
     */
    public void setLocationsFromCenterLine(Globe globe, LatLon beginLocation, LatLon endLocation,
        double leftWidthMetres, double rightWidthMetres, int lengthSegments, int widthSegments)
    {
        if (globe == null)
        {
            String message = Logging.getMessage("nullValue.GlobeIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }
        if (beginLocation == null || endLocation == null)
        {
            String message = Logging.getMessage("nullValue.LatLonIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }
        if (lengthSegments < 1 || widthSegments < 1)
        {
            String message = Logging.getMessage("generic.ArgumentOutOfRange");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        double beginAzimuth = LatLon.greatCircleAzimuth(beginLocation, endLocation).radians;
        double endAzimuth   = LatLon.greatCircleAzimuth(endLocation,   beginLocation).radians;
        double leftArc  = leftWidthMetres  / globe.getRadius();
        double rightArc = rightWidthMetres / globe.getRadius();

        LatLon beginLeft  = LatLon.greatCircleEndPosition(beginLocation, beginAzimuth - Math.PI / 2, leftArc);
        LatLon beginRight = LatLon.greatCircleEndPosition(beginLocation, beginAzimuth + Math.PI / 2, rightArc);
        LatLon endLeft    = LatLon.greatCircleEndPosition(endLocation,   endAzimuth   + Math.PI / 2, leftArc);
        LatLon endRight   = LatLon.greatCircleEndPosition(endLocation,   endAzimuth   - Math.PI / 2, rightArc);

        List<LatLon> locs = new ArrayList<>();

        // begin side: beginLeft → beginLocation → beginRight (2*widthSegments + 1 pts)
        appendCenterLineLeg(beginLeft, beginLocation, beginRight, widthSegments, locs);

        // right side: beginRight + (lengthSegments-1) interior + endRight
        locs.add(beginRight);
        for (int i = 1; i < lengthSegments; i++)
        {
            double amount = (double) i / lengthSegments;
            LatLon proj = LatLon.interpolateGreatCircle(amount, beginLocation, endLocation);
            double az   = LatLon.greatCircleAzimuth(proj, endLocation).radians + Math.PI / 2;
            locs.add(LatLon.greatCircleEndPosition(proj, az, rightArc));
        }
        locs.add(endRight);

        // end side: endRight → endLocation → endLeft
        appendCenterLineLeg(endRight, endLocation, endLeft, widthSegments, locs);

        // left side: endLeft + (lengthSegments-1) interior + beginLeft
        locs.add(endLeft);
        for (int i = 1; i < lengthSegments; i++)
        {
            double amount = (double) i / lengthSegments;
            LatLon proj = LatLon.interpolateGreatCircle(amount, endLocation, beginLocation);
            double az   = LatLon.greatCircleAzimuth(proj, endLocation).radians - Math.PI / 2;
            locs.add(LatLon.greatCircleEndPosition(proj, az, leftArc));
        }
        locs.add(beginLeft);

        this.locations      = locs;
        this.lengthSegments = lengthSegments;
        this.widthSegments  = widthSegments;
        this.onShapeChanged();
    }

    /** Symmetric-width overload of {@link #setLocationsFromCenterLine}. */
    public void setLocationsFromCenterLine(Globe globe, LatLon beginLocation, LatLon endLocation,
        double halfWidthMetres, int lengthSegments, int widthSegments)
    {
        this.setLocationsFromCenterLine(globe, beginLocation, endLocation,
            halfWidthMetres, halfWidthMetres, lengthSegments, widthSegments);
    }

    private static void appendCenterLineLeg(LatLon begin, LatLon middle, LatLon end,
        int numSegments, List<LatLon> out)
    {
        for (int i = 0; i <= numSegments; i++)
        {
            double amount = (double) i / numSegments;
            out.add(LatLon.interpolateGreatCircle(amount, begin, middle));
        }
        for (int i = 1; i <= numSegments; i++) // skip i=0: already added above
        {
            double amount = (double) i / numSegments;
            out.add(LatLon.interpolateGreatCircle(amount, middle, end));
        }
    }

    public int getLengthSegments()
    {
        return this.lengthSegments;
    }

    public void setLengthSegments(int lengthSegments)
    {
        this.lengthSegments = lengthSegments;
        this.onShapeChanged();
    }

    public int getWidthSegments()
    {
        return this.widthSegments;
    }

    public void setWidthSegments(int widthSegments)
    {
        this.widthSegments = widthSegments;
        this.onShapeChanged();
    }

    public boolean[] isEnableCaps()
    {
        return new boolean[] {this.enableStartCap, this.enableEndCap};
    }

    public void setEnableCaps(boolean enableStartCap, boolean enableEndCap)
    {
        this.enableStartCap = enableStartCap;
        this.enableEndCap = enableEndCap;
        this.onShapeChanged();
    }

    public boolean isEnableCenterLine()
    {
        return this.enableCenterLine;
    }

    public void setEnableCenterLine(boolean enable)
    {
        this.enableCenterLine = enable;
    }

    @Override
    public Position getReferencePosition()
    {
        return this.locations != null && this.locations.size() > 0 ? new Position(this.locations.get(0), 0) : null;
    }

    @Override
    protected void doMoveTo(Position oldReferencePosition, Position newReferencePosition)
    {
        // Intentionally left blank.
    }

    @Override
    protected void doMoveTo(Globe globe, Position oldReferencePosition, Position newReferencePosition)
    {
        // Intentionally left blank.
    }

    @Override
	protected List<List<LatLon>> createGeometry(Globe globe, double edgeIntervalsPerDegree)
    {
        if (this.locations == null)
            return null;

        ArrayList<List<LatLon>> geom = new ArrayList<>();

        // Generate the box interior locations via generateIntermediateLocations (GPU-accelerated).
        // The interior is a closed path through all box locations.
        ArrayList<LatLon> interior = new ArrayList<>();
        geom.add(interior);
        this.generateIntermediateLocations(this.locations, edgeIntervalsPerDegree, false, interior);

        // Generate the box outline locations. Store the outline locations in indices 1 through size-2.
        int[] sideSegments = {2 * this.widthSegments, this.lengthSegments, 2 * this.widthSegments, this.lengthSegments};
        boolean[] sideFlag = {this.enableStartCap, true, this.enableEndCap, true};

        int offset = 0;
        for (int i = 0; i < 4; i++)
        {
            if (sideFlag[i])
            {
                geom.add(this.makeLocations(offset, sideSegments[i], edgeIntervalsPerDegree));
            }

            offset += sideSegments[i] + 1;
        }

        // Generate the box center line locations via generateIntermediateLocations.
        LatLon beginLocation = this.locations.get(this.widthSegments);
        LatLon endLocation = this.locations.get(3 * this.widthSegments + this.lengthSegments + 2);
        List<LatLon> centerPair = Arrays.asList(beginLocation, endLocation);
        ArrayList<LatLon> centerLine = new ArrayList<>();
        this.generateIntermediateLocations(centerPair, edgeIntervalsPerDegree, false, centerLine);
        geom.add(centerLine);

        return geom;
    }

    protected ArrayList<LatLon> makeLocations(int offset, int count, double edgeIntervalsPerDegree)
    {
        // Extract the sub-path and tessellate it as a batch via generateIntermediateLocations
        List<LatLon> subPath = this.locations.subList(offset, offset + count + 1);
        ArrayList<LatLon> locations = new ArrayList<>();
        this.generateIntermediateLocations(subPath, edgeIntervalsPerDegree, false, locations);
        return locations;
    }

    @Override
    protected void determineActiveGeometry(DrawContext dc, SurfaceTileDrawContext sdc)
    {
        this.activeGeometry.clear();
        this.activeOutlineGeometry.clear();
        this.activeCenterLineGeometry.clear();

        List<List<LatLon>> geom = this.getCachedGeometry(dc, sdc); // calls createGeometry
        if (geom == null)
            return;

        int index = 0; // interior geometry stored in index 0
        List<LatLon> interior = geom.get(index++);
        String pole = this.containsPole(interior);
        if (pole != null) // interior compensates for poles and dateline crossing, see WWJ-284
        {
            this.activeGeometry.add(this.cutAlongDateLine(interior, pole, dc.getGlobe()));
        }
        else if (LatLon.locationsCrossDateLine(interior))
        {
            this.activeGeometry.addAll(this.repeatAroundDateline(interior));
        }
        else
        {
            this.activeGeometry.add(interior);
        }

        for (; index < geom.size() - 1; index++) // outline geometry stored in indices 1 through size-2
        {
            List<LatLon> outline = geom.get(index);
            if (LatLon.locationsCrossDateLine(outline)) // outlines compensate for dateline crossing, see WWJ-452
            {
                this.activeOutlineGeometry.addAll(this.repeatAroundDateline(outline));
            }
            else
            {
                this.activeOutlineGeometry.add(outline);
            }
        }

        if (index < geom.size()) // center line geometry stored in index size-1
        {
            List<LatLon> centerLine = geom.get(index);
            if (LatLon.locationsCrossDateLine(centerLine)) // outlines compensate for dateline crossing, see WWJ-452
            {
                this.activeCenterLineGeometry.addAll(this.repeatAroundDateline(centerLine));
            }
            else
            {
                this.activeCenterLineGeometry.add(centerLine);
            }
        }
    }

    @Override
	protected void drawOutline(DrawContext dc, SurfaceTileDrawContext sdc)
    {
        super.drawOutline(dc, sdc);

        if (this.enableCenterLine)
        {
            this.drawCenterLine(dc);
        }
    }

    protected void drawCenterLine(DrawContext dc)
    {
        if (this.activeCenterLineGeometry.isEmpty())
            return;

        this.applyCenterLineState(dc, this.getActiveAttributes());

        for (List<LatLon> drawLocations : this.activeCenterLineGeometry)
        {
            this.drawLineStrip(dc, drawLocations);
        }
    }

    protected void applyCenterLineState(DrawContext dc, ShapeAttributes attributes)
    {
        GL2 gl = dc.getGL().getGL2(); // GL initialization checks for GL2 compatibility.

        if (!dc.isPickingMode() && attributes.getOutlineStippleFactor() <= 0) // don't override stipple in attributes
        {
            gl.glEnable(GL2.GL_LINE_STIPPLE);
            gl.glLineStipple(Box.DEFAULT_CENTER_LINE_STIPPLE_FACTOR, Box.DEFAULT_CENTER_LINE_STIPPLE_PATTERN);
        }
    }

    @Override
    public Iterable<? extends LatLon> getLocations(Globe globe)
    {
        if (globe == null)
        {
            String message = Logging.getMessage("nullValue.GlobeIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        return this.locations;
    }
}
