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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.globes.Globe;
import gov.nasa.worldwind.util.BufferWrapper;
import gov.nasa.worldwind.util.CompoundVecBuffer;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.SurfaceTileDrawContext;
import gov.nasa.worldwind.util.VecBuffer;
import gov.nasa.worldwind.util.VecBufferSequence;
import gov.nasa.worldwind.util.WWBufferUtil;

/**
 * This class renders fast multiple surface polylines in one pass. It relies on a {@link CompoundVecBuffer}.
 *
 * @author Dave Collins
 * @author Patrick Murris
 * @version $Id: SurfacePolylines.java 2406 2014-10-29 23:39:29Z dcollins $
 */
public class SurfacePolylines extends AbstractSurfaceShape
{
    protected List<Sector> sectors;
    protected CompoundVecBuffer buffer;

    public SurfacePolylines(CompoundVecBuffer buffer)
    {
        if (buffer == null)
        {
            String message = Logging.getMessage("nullValue.BufferIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.buffer = buffer;
    }

    public SurfacePolylines(Sector sector, CompoundVecBuffer buffer)
    {
        if (sector == null)
        {
            String message = Logging.getMessage("nullValue.SectorIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }
        if (buffer == null)
        {
            String message = Logging.getMessage("nullValue.BufferIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.sectors = Arrays.asList(sector);
        this.buffer = buffer;
    }

    /**
     * Constructs a {@code SurfacePolylines} from a multi-line-string: an iterable of line
     * strings, each of which is an iterable of {@link LatLon}. Each element of
     * {@code lineStrings} becomes a separate, disjoint polyline in the rendered shape —
     * the class draws one {@code GL_LINE_STRIP} per element, with no connecting segments
     * between them. Elements with fewer than two points are silently skipped.
     *
     * @param lineStrings the line strings to render.
     *
     * @throws IllegalArgumentException if {@code lineStrings} is null.
     */
    public SurfacePolylines(Iterable<? extends Iterable<? extends LatLon>> lineStrings)
    {
        if (lineStrings == null)
        {
            String message = Logging.getMessage("nullValue.IterableIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.buffer = buildCompoundBuffer(lineStrings);
    }

    private static CompoundVecBuffer buildCompoundBuffer(
        Iterable<? extends Iterable<? extends LatLon>> lineStrings)
    {
        List<List<LatLon>> lines = new ArrayList<>();
        int totalPoints = 0;
        for (Iterable<? extends LatLon> line : lineStrings)
        {
            if (line == null) {
                continue;
            }
            List<LatLon> pts = new ArrayList<>();
            for (LatLon ll : line)
            {
                if (ll != null) {
                    pts.add(ll);
                }
            }
            if (pts.size() < 2) {
                continue; // GL_LINE_STRIP requires at least two vertices
            }
            lines.add(pts);
            totalPoints += pts.size();
        }

        if (lines.isEmpty()) {
            return VecBufferSequence.emptyVecBufferSequence(2);
        }

        BufferWrapper backing = WWBufferUtil.newDoubleBufferWrapper(totalPoints * 2, true);
        VecBuffer backingVec = new VecBuffer(2, backing);
        VecBufferSequence sequence = new VecBufferSequence(backingVec, lines.size());

        for (List<LatLon> line : lines)
        {
            BufferWrapper subWrapper = WWBufferUtil.newDoubleBufferWrapper(line.size() * 2, true);
            VecBuffer sub = new VecBuffer(2, subWrapper);
            sub.putLocations(0, line);
            sequence.append(sub);
        }

        return sequence;
    }

    /**
     * Get the underlying {@link CompoundVecBuffer} describing the geometry.
     *
     * @return the underlying {@link CompoundVecBuffer}.
     */
    public CompoundVecBuffer getBuffer()
    {
        return this.buffer;
    }

    @Override
    public List<Sector> getSectors(DrawContext dc)
    {
        if (dc == null)
        {
            String message = Logging.getMessage("nullValue.DrawContextIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        // SurfacePolylines does not interpolate between caller specified positions, therefore it has no path type.
        if (this.sectors == null) {
			this.sectors = this.computeSectors(dc);
		}

        return this.sectors;
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

        return this.getLocations();
    }

    @Override
	protected List<List<LatLon>> createGeometry(Globe globe, SurfaceTileDrawContext sdc)
    {
        // SurfacePolylines supplies its own geometry directly from the CompoundVecBuffer in
        // determineActiveGeometry, so the base-class generator pipeline is unused.
        return null;
    }

    @Override
	protected List<List<LatLon>> createGeometry(Globe globe, double edgeIntervalsPerDegree)
    {
        return null;
    }

    public Iterable<? extends LatLon> getLocations()
    {
        return this.buffer.getLocations();
    }

    @SuppressWarnings("unused")
    public void setLocations(Iterable<? extends LatLon> iterable)
    {
        throw new UnsupportedOperationException();
    }

    @Override
	public Position getReferencePosition()
    {
        var iterator = this.getLocations().iterator();
        if (iterator.hasNext()) {
			return new Position(iterator.next(), 0);
		}

        return null;
    }

    /** {@inheritDoc} Overridden to treat the polylines as open paths rather than closed polygons. */
    @Override
    protected boolean canContainPole()
    {
        return false;
    }

    @Override
	protected void doMoveTo(Position oldReferencePosition, Position newReferencePosition)
    {
        for (int i = 0; i < this.buffer.size(); i++)
        {
            VecBuffer vb = this.buffer.subBuffer(i);

            for (int pos = 0; pos < vb.getSize(); pos++)
            {
                LatLon ll = vb.getLocation(pos);
                Angle heading = LatLon.greatCircleAzimuth(oldReferencePosition, ll);
                Angle pathLength = LatLon.greatCircleDistance(oldReferencePosition, ll);
                vb.putLocation(pos, LatLon.greatCircleEndPosition(newReferencePosition, heading, pathLength));
            }
        }

        this.onGeometryChanged();
    }

    @Override
	protected void doMoveTo(Globe globe, Position oldReferencePosition, Position newReferencePosition)
    {
        for (int i = 0; i < this.buffer.size(); i++)
        {
            VecBuffer vb = this.buffer.subBuffer(i);

            List<LatLon> newLocations = LatLon.computeShiftedLocations(globe, oldReferencePosition,
                newReferencePosition, vb.getLocations());

            for (int pos = 0; pos < vb.getSize(); pos++)
            {
                vb.putLocation(pos, newLocations.get(i));
            }
        }

        this.onGeometryChanged();
    }

    protected void onGeometryChanged()
    {
        this.sectors = null;
        super.onShapeChanged();
    }

    /**
     * Publishes each sub-buffer as its own polyline in {@code activeOutlineGeometry}, which
     * the base class's {@code drawOutline} then renders through the {@link
     * gov.nasa.worldwind.render.shaders.DashLineShader} path (matching {@link SurfacePolyline}
     * / {@link SurfacePolygon} / {@link SurfaceEllipse}). Dateline-crossing lines are split
     * via {@code repeatAroundDateline} so the outline is drawn on both sides of the seam
     * without a shader-side translation hack.
     */
    @Override
	protected void determineActiveGeometry(DrawContext dc, SurfaceTileDrawContext sdc)
    {
        this.activeGeometry.clear();
        this.activeOutlineGeometry.clear();

        for (int i = 0; i < this.buffer.size(); i++)
        {
            VecBuffer sub = this.buffer.subBuffer(i);
            List<LatLon> line = new ArrayList<>(sub.getSize());
            for (LatLon ll : sub.getLocations()) {
                line.add(ll);
            }
            if (line.size() < 2) {
                continue;
            }

            if (LatLon.locationsCrossDateLine(line)) {
                this.activeOutlineGeometry.addAll(this.repeatAroundDateline(line));
            } else {
                this.activeOutlineGeometry.add(line);
            }
        }
    }

    @Override
	protected void drawInterior(DrawContext dc, SurfaceTileDrawContext sdc)
    {
        // Intentionally left blank; SurfacePolylines does not render an interior.
    }
}
