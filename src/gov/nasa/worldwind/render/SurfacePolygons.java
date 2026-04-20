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
 *
 * Modifications and additions by seaglassfoundry.com — WorldWind Reforged project.
 * Added GPU triangulation support via GpuTriangulator for Phase 3 shader rendering.
 */
package gov.nasa.worldwind.render;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2GL3;

import gov.nasa.worldwind.render.shaders.SurfaceShapeFillShader;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUtessellator;
import com.jogamp.opengl.glu.GLUtessellatorCallback;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.cache.GpuResourceCache;
import gov.nasa.worldwind.exception.WWRuntimeException;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.geom.Vec4;
import gov.nasa.worldwind.render.shaders.GpuTriangulator;
import gov.nasa.worldwind.util.CompoundVecBuffer;
import gov.nasa.worldwind.util.GLUTessellatorSupport;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.SurfaceTileDrawContext;
import gov.nasa.worldwind.util.VecBuffer;
import gov.nasa.worldwind.util.WWMath;

/**
 * Modified 2025-2026 seaglassfoundry.com — Replaced GLU tessellator + display list interior rendering
 * with shader-based VBO rendering. Added buildInteriorVBOs() override that groups rings into polygon
 * groups via polygonRingGroups or winding order, bridges holes using GpuTriangulator.bridgeHoles(),
 * and triangulates via GpuTriangulator.triangulateCPU(). Dateline-crossing interiors still render twice
 * via the drawInterior() override (same visual result as the original display list path). The original
 * GLU tessellator + display list code is retained as a fallback for pole-wrapping shapes.
 *
 * Renders fast multiple polygons with or without holes in one pass. It relies on a {@link
 * gov.nasa.worldwind.util.CompoundVecBuffer}.
 * <p>
 * Whether a polygon ring is filled or is a hole in another polygon depends on the vertices winding order and the
 * winding rule used - see setWindingRule(String).
 *
 * @author Dave Collins
 * @author Patrick Murris
 * @version $Id: SurfacePolygons.java 1171 2013-02-11 21:45:02Z dcollins $
 */
public class SurfacePolygons extends SurfacePolylines // TODO: Review
{
    protected int[] polygonRingGroups;
    protected String windingRule = AVKey.CLOCKWISE;
    protected boolean needsInteriorTessellation = true;
    protected boolean crossesDateLine = false;
    protected WWTexture texture;
    protected Object interiorDisplayListCacheKey = new Object();

    public SurfacePolygons(CompoundVecBuffer buffer)
    {
        super(buffer);
    }

    public SurfacePolygons(Sector sector, CompoundVecBuffer buffer)
    {
        super(sector, buffer);
    }

    /**
     * Get a copy of the polygon ring groups array - can be null.
     * <p>
     * When not null the polygon ring groups array identifies the starting sub buffer index for each polygon. In that
     * case rings from a same group will be tesselated together as part of the same polygon.
     * <p>
     * When <code>null</code> polygon rings that follow the current winding rule are tessellated separatly as different
     * polygons. Rings that are reverse winded are considered holes to be applied to the last straight winded ring
     * polygon.
     *
     * @return a copy of the polygon ring groups array - can be null.
     */
    public int[] getPolygonRingGroups()
    {
        return this.polygonRingGroups.clone();
    }

    /**
     * Set the polygon ring groups array - can be null.
     * <p>
     * When not null the polygon ring groups array identifies the starting sub buffer index for each polygon. In that
     * case rings from a same group will be tesselated together as part of the same polygon.
     * <p>
     * When <code>null</code> polygon rings that follow the current winding rule are tessellated separatly as different
     * polygons. Rings that are reverse winded are considered holes to be applied to the last straight winded ring
     * polygon.
     *
     * @param ringGroups a copy of the polygon ring groups array - can be null.
     */
    public void setPolygonRingGroups(int[] ringGroups)
    {
        this.polygonRingGroups = ringGroups.clone();
        this.onGeometryChanged();
    }

    /**
     * Get the winding rule used when tessellating polygons. Can be one of {@link AVKey#CLOCKWISE} (default) or {@link
     * AVKey#COUNTER_CLOCKWISE}.
     * <p>
     * When set to {@link AVKey#CLOCKWISE} polygons which run clockwise will be filled and those which run counter
     * clockwise will produce 'holes'. The interpretation is reversed when the winding rule is set to {@link
     * AVKey#COUNTER_CLOCKWISE}.
     *
     * @return the winding rule used when tessellating polygons.
     */
    public String getWindingRule()
    {
        return this.windingRule;
    }

    /**
     * Set the winding rule used when tessellating polygons. Can be one of {@link AVKey#CLOCKWISE} (default) or {@link
     * AVKey#COUNTER_CLOCKWISE}.
     * <p>
     * When set to {@link AVKey#CLOCKWISE} polygons which run clockwise will be filled and those which run counter
     * clockwise will produce 'holes'. The interpretation is reversed when the winding rule is set to {@link
     * AVKey#COUNTER_CLOCKWISE}.
     *
     * @param windingRule the winding rule to use when tessellating polygons.
     */
    public void setWindingRule(String windingRule)
    {
        this.windingRule = windingRule;
        this.onGeometryChanged();
    }

    @Override
	protected void onGeometryChanged()
    {
        this.needsInteriorTessellation = true;
        super.onGeometryChanged();
    }

    @Override
	protected void drawInterior(DrawContext dc, SurfaceTileDrawContext sdc)
    {
        // Exit immediately if the polygon has no coordinate data.
        if (this.buffer.size() == 0) {
			return;
		}

        Position referencePos = this.getReferencePosition();
        if (referencePos == null) {
			return;
		}

        // Try shader-based VBO rendering first
        if (!Boolean.TRUE.equals(fillShaderFailed.get()) && this.drawInteriorWithShader(dc, sdc))
        {
            if (this.crossesDateLine)
            {
                GL2 gl = dc.getGL().getGL2();
                gl.glPushMatrix();
                try
                {
                    double hemisphereSign = Math.signum(referencePos.getLongitude().degrees);
                    gl.glTranslated(360 * hemisphereSign, 0, 0);
                    this.drawInteriorWithShader(dc, sdc); // VBO already cached, no rebuild
                }
                finally
                {
                    gl.glPopMatrix();
                }
            }
            return;
        }

        // Fallback: legacy GLU tessellator + display list caching
        int[] dlResource = (int[]) dc.getGpuResourceCache().get(this.interiorDisplayListCacheKey);
        if (dlResource == null || this.needsInteriorTessellation) {
			dlResource = this.tessellateInterior(dc, referencePos);
		}

        if (dlResource == null) {
			return;
		}

        GL2 gl = dc.getGL().getGL2();
        this.applyInteriorState(dc, sdc, this.getActiveAttributes(), this.getTexture(), referencePos);
        gl.glCallList(dlResource[0]);

        if (this.crossesDateLine)
        {
            gl.glPushMatrix();
            try
            {
                double hemisphereSign = Math.signum(referencePos.getLongitude().degrees);
                gl.glTranslated(360 * hemisphereSign, 0, 0);
                gl.glCallList(dlResource[0]);
            }
            finally
            {
                gl.glPopMatrix();
            }
        }
    }

    @Override
    protected InteriorVBOData buildInteriorVBOs(DrawContext dc, SurfaceTileDrawContext sdc)
    {
        if (this.buffer.size() == 0) {
			return null;
		}

        Position refPos = this.getReferencePosition();
        if (refPos == null) {
			return null;
		}

        double refLon = refPos.getLongitude().degrees;
        double refLat = refPos.getLatitude().degrees;
        this.crossesDateLine = false;

        // seaglassfoundry.com: determine upfront whether the fill shader wants doubles so we can
        // build a parallel double-precision ring list without narrowing through float.
        SurfaceShapeFillShader shader = fillShaders.get();
        boolean useDoublePositions = shader != null && shader.isFp64Enabled();

        // Flatten all rings, applying hemisphere offset for dateline crossings.
        // Returns null if pole-wrapping is detected (fall back to GLU).
        int numRings = this.buffer.size();
        List<float[]> ringVertices = new ArrayList<>(numRings);
        List<double[]> ringVerticesD = useDoublePositions ? new ArrayList<>(numRings) : null;
        for (int i = 0; i < numRings; i++)
        {
            VecBuffer vb = this.buffer.subBuffer(i);
            boolean[] crossedRef = {false};
            float[] verts = this.flattenRingVertices(vb, refLon, refLat, crossedRef);
            if (verts == null) {
				return null; // pole-wrapping — fall back to GLU
			}
            if (crossedRef[0]) {
				this.crossesDateLine = true;
			}
            ringVertices.add(verts);
            if (ringVerticesD != null)
            {
                boolean[] unused = {false};
                double[] vertsD = this.flattenRingVerticesAsDouble(vb, refLon, refLat, unused);
                if (vertsD == null) {
                    return null;
                }
                ringVerticesD.add(vertsD);
            }
        }

        // Group rings into polygons (outer + holes)
        List<List<Integer>> polygonGroups = this.computePolygonGroups(ringVertices);

        // Build combined vertex + index data
        List<int[]> allTriangles = new ArrayList<>();
        int totalIndices = 0;

        // Build single flat vertex array across all rings (needed for bridgeHoles indices)
        int totalVertices = 0;
        for (float[] v : ringVertices) {
			totalVertices += v.length / 2;
		}

        float[] globalVerts = new float[totalVertices * 2];
        int[] ringOffsets = new int[numRings];
        int gvi = 0;
        for (int i = 0; i < numRings; i++)
        {
            float[] v = ringVertices.get(i);
            ringOffsets[i] = gvi / 2;
            System.arraycopy(v, 0, globalVerts, gvi, v.length);
            gvi += v.length;
        }

        for (List<Integer> group : polygonGroups)
        {
            if (group.isEmpty()) {
				continue;
			}

            int outerIdx = group.get(0);
            float[] outerVerts = ringVertices.get(outerIdx);
            int outerStart = ringOffsets[outerIdx];
            int outerCount = outerVerts.length / 2;

            int[] triangles;
            if (group.size() == 1)
            {
                // No holes — triangulate directly
                if (outerCount < 3) {
					continue;
				}
                int[] ring = new int[outerCount];
                for (int i = 0; i < outerCount; i++) {
					ring[i] = outerStart + i;
				}
                triangles = GpuTriangulator.triangulateCPU(globalVerts, ring);
            }
            else
            {
                // Holes — bridge them in
                int numHoles = group.size() - 1;
                int[] holeStarts = new int[numHoles];
                int[] holeCounts = new int[numHoles];
                for (int h = 0; h < numHoles; h++)
                {
                    int holeIdx = group.get(h + 1);
                    holeStarts[h] = ringOffsets[holeIdx];
                    holeCounts[h] = ringVertices.get(holeIdx).length / 2;
                }

                int[] mergedRing = GpuTriangulator.bridgeHoles(globalVerts,
                    outerStart, outerCount, holeStarts, holeCounts);
                if (mergedRing == null || mergedRing.length < 3) {
					continue;
				}
                triangles = GpuTriangulator.triangulateCPU(globalVerts, mergedRing);
            }

            if (triangles.length > 0)
            {
                allTriangles.add(triangles);
                totalIndices += triangles.length;
            }
        }

        if (totalIndices == 0) {
			return null;
		}

        // Upload to VBOs
        GL gl = dc.getGL();
        int[] vboIds = new int[2];
        gl.glGenBuffers(2, vboIds, 0);

        // seaglassfoundry.com: upload positions as double when the fill shader was linked with
        // dvec2 inputs. ringVerticesD holds the full-precision per-ring positions built alongside
        // the float rings above, so we can build globalVertsD by concatenating in ring order.
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboIds[0]);
        if (useDoublePositions)
        {
            double[] globalVertsD = new double[totalVertices * 2];
            int gviD = 0;
            for (int i = 0; i < numRings; i++)
            {
                double[] v = ringVerticesD.get(i);
                System.arraycopy(v, 0, globalVertsD, gviD, v.length);
                gviD += v.length;
            }
            DoubleBuffer vertBuf = Buffers.newDirectDoubleBuffer(globalVertsD);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) globalVertsD.length * Double.BYTES,
                vertBuf, GL.GL_STATIC_DRAW);
        }
        else
        {
            FloatBuffer vertBuf = Buffers.newDirectFloatBuffer(globalVerts);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) globalVerts.length * Float.BYTES,
                vertBuf, GL.GL_STATIC_DRAW);
        }

        int[] allIndices = new int[totalIndices];
        int off = 0;
        for (int[] tri : allTriangles)
        {
            System.arraycopy(tri, 0, allIndices, off, tri.length);
            off += tri.length;
        }

        IntBuffer idxBuf = Buffers.newDirectIntBuffer(allIndices);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, vboIds[1]);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) allIndices.length * Integer.BYTES, idxBuf, GL.GL_STATIC_DRAW);

        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);

        int positionGLType = useDoublePositions ? GL2GL3.GL_DOUBLE : GL.GL_FLOAT;
        return new InteriorVBOData(vboIds[0], vboIds[1], totalIndices,
            false, 0, positionGLType, -1);
    }

    /**
     * Flattens a ring's vertices to a float array of (lon-refLon, lat-refLat) pairs, applying
     * hemisphere offset for dateline crossing. Returns null if pole-wrapping is detected.
     */
    private float[] flattenRingVertices(VecBuffer vecBuffer, double refLon, double refLat,
        boolean[] datelineCrossedRef)
    {
        // Pole-wrapping detection
        List<double[]> dlCrossPoints = this.computeDateLineCrossingPoints(vecBuffer);
        int pole = this.computePole(dlCrossPoints);
        if (pole != 0) {
			return null; // pole-wrapping not handled — fall back to GLU
		}

        List<float[]> points = new ArrayList<>();
        double[] previousPoint = null;
        int sign = 0;

        for (double[] coords : vecBuffer.getCoords(3))
        {
            if (previousPoint != null && Math.abs(previousPoint[0] - coords[0]) > 180)
            {
                sign += (int) Math.signum(previousPoint[0]);
                datelineCrossedRef[0] = true;
            }
            double lon = coords[0] + sign * 360 - refLon;
            double lat = coords[1] - refLat;
            points.add(new float[] {(float) lon, (float) lat});
            previousPoint = coords.clone();
        }

        if (points.size() < 3) {
			return null;
		}

        float[] result = new float[points.size() * 2];
        for (int i = 0; i < points.size(); i++)
        {
            result[i * 2] = points.get(i)[0];
            result[i * 2 + 1] = points.get(i)[1];
        }
        return result;
    }

    /**
     * Double-precision variant of {@link #flattenRingVertices} — same dateline/pole logic but the
     * per-vertex (lon-refLon, lat-refLat) pairs are returned without narrowing to float. Used by the
     * fp64 shader path so sub-metre vertex positions survive the MVP multiply.
     * seaglassfoundry.com
     */
    private double[] flattenRingVerticesAsDouble(VecBuffer vecBuffer, double refLon, double refLat,
        boolean[] datelineCrossedRef)
    {
        List<double[]> dlCrossPoints = this.computeDateLineCrossingPoints(vecBuffer);
        int pole = this.computePole(dlCrossPoints);
        if (pole != 0) {
            return null;
        }

        List<double[]> points = new ArrayList<>();
        double[] previousPoint = null;
        int sign = 0;

        for (double[] coords : vecBuffer.getCoords(3))
        {
            if (previousPoint != null && Math.abs(previousPoint[0] - coords[0]) > 180)
            {
                sign += (int) Math.signum(previousPoint[0]);
                datelineCrossedRef[0] = true;
            }
            double lon = coords[0] + sign * 360 - refLon;
            double lat = coords[1] - refLat;
            points.add(new double[] {lon, lat});
            previousPoint = coords.clone();
        }

        if (points.size() < 3) {
            return null;
        }

        double[] result = new double[points.size() * 2];
        for (int i = 0; i < points.size(); i++)
        {
            result[i * 2] = points.get(i)[0];
            result[i * 2 + 1] = points.get(i)[1];
        }
        return result;
    }

    /**
     * Groups ring indices into polygon groups (outer ring + associated holes).
     * Uses polygonRingGroups if set, otherwise uses winding order.
     */
    private List<List<Integer>> computePolygonGroups(List<float[]> ringVertices)
    {
        List<List<Integer>> groups = new ArrayList<>();
        int numRings = ringVertices.size();

        if (this.polygonRingGroups != null)
        {
            int numGroups = this.polygonRingGroups.length;
            for (int g = 0; g < numGroups; g++)
            {
                int groupStart = this.polygonRingGroups[g];
                int groupEnd = (g == numGroups - 1) ? numRings : this.polygonRingGroups[g + 1];
                List<Integer> group = new ArrayList<>();
                for (int i = groupStart; i < groupEnd; i++) {
					group.add(i);
				}
                if (!group.isEmpty()) {
					groups.add(group);
				}
            }
        }
        else
        {
            // Use winding order to group: rings matching windingRule are outer rings
            List<Integer> currentGroup = null;
            for (int i = 0; i < numRings; i++)
            {
                VecBuffer vb = this.buffer.subBuffer(i);
                String winding = WWMath.computeWindingOrderOfLocations(vb.getLocations());
                boolean isOuter = winding.equals(this.windingRule);

                if (isOuter || currentGroup == null)
                {
                    currentGroup = new ArrayList<>();
                    currentGroup.add(i);
                    groups.add(currentGroup);
                }
                else
                {
                    currentGroup.add(i);
                }
            }
        }
        return groups;
    }

    protected WWTexture getTexture()
    {
        if (this.getActiveAttributes().getImageSource() == null) {
			return null;
		}

        if (this.texture == null && this.getActiveAttributes().getImageSource() != null) {
			this.texture = new BasicWWTexture(this.getActiveAttributes().getImageSource(), true);
		}

        return this.texture;
    }

    //**************************************************************//
    //********************  Interior Tessellation  *****************//
    //**************************************************************//

    protected int[] tessellateInterior(DrawContext dc, LatLon referenceLocation)
    {
        if (dc == null)
        {
            String message = Logging.getMessage("nullValue.DrawContextIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        try
        {
            return this.doTessellateInterior(dc, referenceLocation);
        }
        catch (OutOfMemoryError e)
        {
            String message = Logging.getMessage("generic.ExceptionWhileTessellating", this);
            Logging.logger().log(Level.SEVERE, message, e);

            //noinspection ThrowableInstanceNeverThrown
            dc.addRenderingException(new WWRuntimeException(message, e));

            this.handleUnsuccessfulInteriorTessellation(dc);

            return null;
        }
    }

    protected int[] doTessellateInterior(DrawContext dc, LatLon referenceLocation)
    {
        GL2 gl = dc.getGL().getGL2(); // GL initialization checks for GL2 compatibility.
        GLUtessellatorCallback cb = GLUTessellatorSupport.createOGLDrawPrimitivesCallback(gl);

        int[] dlResource = new int[] {gl.glGenLists(1), 1};
        GLUTessellatorSupport glts = new GLUTessellatorSupport();

        try
        {
            glts.beginTessellation(cb, new Vec4(0, 0, 1));
            gl.glNewList(dlResource[0], GL2.GL_COMPILE);
            int numBytes = this.tessellateInteriorVertices(glts.getGLUtessellator(), referenceLocation);
            glts.endTessellation();
            gl.glEndList();
            this.needsInteriorTessellation = false;

            dc.getGpuResourceCache().put(this.interiorDisplayListCacheKey, dlResource, GpuResourceCache.DISPLAY_LISTS,
                numBytes);

            return dlResource;
        }
        catch (Throwable e)
        {
            // Free any heap memory used for tessellation immediately. If tessellation has consumed all available heap
            // memory, we must free memory used by tessellation immediately or subsequent operations such as message
            // logging will fail.
            gl.glEndList();
            glts.endTessellation();
            gl.glDeleteLists(dlResource[0], dlResource[1]);

            String message = Logging.getMessage("generic.ExceptionWhileTessellating", this);
            Logging.logger().log(Level.SEVERE, message, e);

            //noinspection ThrowableInstanceNeverThrown
            dc.addRenderingException(new WWRuntimeException(message, e));

            this.handleUnsuccessfulInteriorTessellation(dc);

            return null;
        }
    }

    @Override
	protected void handleUnsuccessfulInteriorTessellation(DrawContext dc)
    {
        // If tessellating the polygon's interior was unsuccessful, we modify the polygon to avoid any additional
        // tessellation attempts, and free any resources that the polygon won't use.

        // Replace the polygon's coordinate buffer with an empty CompoundVecBuffer. This ensures that any rendering
        // code won't attempt to re-tessellate this polygon.
        this.buffer = CompoundVecBuffer.emptyCompoundVecBuffer(2);
        // Flag the polygon as having changed, since we've replaced its coordinate buffer with an empty
        // CompoundVecBuffer.
        this.onGeometryChanged();
    }

    protected int tessellateInteriorVertices(GLUtessellator tess, LatLon referenceLocation)
    {
        // Setup the winding order to correctly tessellate the outer and inner rings.
        GLU.gluTessProperty(tess, GLU.GLU_TESS_WINDING_RULE, this.windingRule.equals(AVKey.CLOCKWISE) ?
            GLU.GLU_TESS_WINDING_NEGATIVE : GLU.GLU_TESS_WINDING_POSITIVE);

        this.crossesDateLine = false;

        int numBytes = 0;
        int numRings = this.buffer.size();
        if (this.polygonRingGroups == null)
        {
            boolean inBeginPolygon = false;

            // Polygon rings are drawn following the sub buffers order. If the winding rule is CW all clockwise
            // rings are considered an outer ring possibly followed by counter clock wise inner rings.
            for (int i = 0; i < numRings; i++)
            {
                VecBuffer vecBuffer = this.buffer.subBuffer(i);
                numBytes += vecBuffer.getSize() * 3 * 4; // 3 float coords per vertex

                // Start a new polygon for each outer ring
                if (WWMath.computeWindingOrderOfLocations(vecBuffer.getLocations()).equals(this.getWindingRule()))
                {
                    if (inBeginPolygon) {
						GLU.gluTessEndPolygon(tess);
					}

                    GLU.gluTessBeginPolygon(tess, null);
                    inBeginPolygon = true;
                }

                if (tessellateRing(tess, vecBuffer, referenceLocation)) {
					this.crossesDateLine = true;
				}
            }

            if (inBeginPolygon) {
				GLU.gluTessEndPolygon(tess);
			}
        }
        else
        {
            // Tessellate one polygon per ring group
            int numGroups = this.polygonRingGroups.length;
            for (int group = 0; group < numGroups; group++)
            {
                int groupStart = this.polygonRingGroups[group];
                int groupLength = (group == numGroups - 1) ? numRings - groupStart
                    : this.polygonRingGroups[group + 1] - groupStart;

                GLU.gluTessBeginPolygon(tess, null);
                for (int i = 0; i < groupLength; i++)
                {
                    VecBuffer subBuffer = this.buffer.subBuffer(groupStart + i);
                    numBytes += subBuffer.getSize() * 3 * 4; // 3 float coords per vertex
                    if (tessellateRing(tess, subBuffer, referenceLocation)) {
						this.crossesDateLine = true;
					}
                }
                GLU.gluTessEndPolygon(tess);
            }
        }

        return numBytes;
    }

    protected boolean tessellateRing(GLUtessellator tess, VecBuffer vecBuffer, LatLon referenceLocation)
    {
        // Check for pole wrapping shape
        List<double[]> dateLineCrossingPoints = this.computeDateLineCrossingPoints(vecBuffer);
        int pole = this.computePole(dateLineCrossingPoints);
        double[] poleWrappingPoint = this.computePoleWrappingPoint(pole, dateLineCrossingPoints);

        GLU.gluTessBeginContour(tess);
        Iterable<double[]> iterable = vecBuffer.getCoords(3);
        boolean dateLineCrossed = false;
        int sign = 0;
        double[] previousPoint = null;
        for (double[] coords : iterable)
        {
            if (poleWrappingPoint != null && previousPoint != null
                && poleWrappingPoint[0] == previousPoint[0] && poleWrappingPoint[1] == previousPoint[1])
            {
                previousPoint = coords.clone();

                // Wrapping a pole
                double[] dateLinePoint1 = this.computeDateLineEntryPoint(poleWrappingPoint, coords);
                double[] polePoint1 = new double[] {180 * Math.signum(poleWrappingPoint[0]), 90d * pole, 0};
                double[] dateLinePoint2 = dateLinePoint1.clone();
                double[] polePoint2 = polePoint1.clone();
                dateLinePoint2[0] *= -1;
                polePoint2[0] *= -1;

                // Move to date line then to pole
                tessVertex(tess, dateLinePoint1, referenceLocation);
                tessVertex(tess, polePoint1, referenceLocation);

                // Move to the other side of the date line
                tessVertex(tess, polePoint2, referenceLocation);
                tessVertex(tess, dateLinePoint2, referenceLocation);

                // Finally, draw current point past the date line
                tessVertex(tess, coords, referenceLocation);

                dateLineCrossed = true;
            }
            else
            {
                if (previousPoint != null && Math.abs(previousPoint[0] - coords[0]) > 180)
                {
                    // Crossing date line, sum departure point longitude sign for hemisphere offset
                    sign += (int) Math.signum(previousPoint[0]);
                    dateLineCrossed = true;
                }

                previousPoint = coords.clone();

                coords[0] += sign * 360;   // apply hemisphere offset
                tessVertex(tess, coords, referenceLocation);
            }
        }
        GLU.gluTessEndContour(tess);

        return dateLineCrossed;
    }

    private static void tessVertex(GLUtessellator tess, double[] coords, LatLon referenceLocation)
    {
        double[] vertex = new double[3];
        vertex[0] = coords[0] - referenceLocation.getLongitude().degrees;
        vertex[1] = coords[1] - referenceLocation.getLatitude().degrees;
        GLU.gluTessVertex(tess, vertex, 0, vertex);
    }

    // --- Pole wrapping shapes handling ---

    protected List<double[]> computeDateLineCrossingPoints(VecBuffer vecBuffer)
    {
        // Shapes that include a pole will yield an odd number of points
        List<double[]> list = new ArrayList<>();
        Iterable<double[]> iterable = vecBuffer.getCoords(3);
        double[] previousPoint = null;
        for (double[] coords : iterable)
        {
            if (previousPoint != null && Math.abs(previousPoint[0] - coords[0]) > 180) {
				list.add(previousPoint);
			}
            previousPoint = coords;
        }

        return list;
    }

    protected int computePole(List<double[]> dateLineCrossingPoints)
    {
        int sign = 0;
        for (double[] point : dateLineCrossingPoints)
        {
            sign += Math.signum(point[0]);
        }

        if (sign == 0) {
			return 0;
		}

        // If we cross the date line going west (from a negative longitude) with a clockwise polygon,
        // then the north pole (positive) is included.
        return this.getWindingRule().equals(AVKey.CLOCKWISE) && sign < 0 ? 1 : -1;
    }

    protected double[] computePoleWrappingPoint(int pole, List<double[]> dateLineCrossingPoints)
    {
        if (pole == 0) {
			return null;
		}

        // Find point with latitude closest to pole
        int idx = -1;
        double max = pole < 0 ? 90 : -90;
        for (int i = 0; i < dateLineCrossingPoints.size(); i++)
        {
            double[] point = dateLineCrossingPoints.get(i);
            if (pole < 0 && point[1] < max) // increasing latitude toward north pole
            {
                idx = i;
                max = point[1];
            }
            if (pole > 0 && point[1] > max) // decreasing latitude toward south pole
            {
                idx = i;
                max = point[1];
            }
        }

        return dateLineCrossingPoints.get(idx);
    }

    protected double[] computeDateLineEntryPoint(double[] from, double[] to)
    {
        // Linear interpolation between from and to at the date line
        double dLat = to[1] - from[1];
        double dLon = 360 - Math.abs(to[0] - from[0]);
        double s = Math.abs(180 * Math.signum(from[0]) - from[0]) / dLon;
        double lat = from[1] + dLat * s;
        double lon = 180 * Math.signum(from[0]); // same side as from

        return new double[] {lon, lat, 0};
    }
}
