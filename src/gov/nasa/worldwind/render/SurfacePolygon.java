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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2GL3;

import gov.nasa.worldwind.Exportable;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.globes.Globe;
import gov.nasa.worldwind.ogc.kml.KMLConstants;
import gov.nasa.worldwind.ogc.kml.impl.KMLExportUtil;
import gov.nasa.worldwind.render.shaders.GpuTriangulator;
import gov.nasa.worldwind.render.shaders.SurfaceShapeFillShader;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.RestorableSupport;
import gov.nasa.worldwind.util.SurfaceTileDrawContext;
import gov.nasa.worldwind.util.WWMath;

/**
 * Modified 2025-2026 seaglassfoundry.com — Replaced GLU tessellator-based interior rendering
 * (doDrawGeographic, tessellateContours, ShapeData, applyInteriorState override) with shader-based
 * VBO rendering via AbstractSurfaceShape.drawInteriorWithShader(). Overrides buildInteriorVBOs()
 * to handle holes via GpuTriangulator.bridgeHoles() and explicit per-vertex texture coordinates
 * via interleaved VBO layout. The assembleContours() method and Vertex inner class are preserved
 * for contour generation (edge interpolation, pole/dateline clipping, texture coord propagation).
 *
 * @author dcollins
 * @version $Id: SurfacePolygon.java 3436 2015-10-28 17:43:24Z tgaskins $
 */
public class SurfacePolygon extends AbstractSurfaceShape implements Exportable
{
    protected static class Vertex extends LatLon
    {
        public double u;
        public double v;
        public boolean edgeFlag = true;

        public Vertex(LatLon location)
        {
            super(location);
        }

        public Vertex(Angle latitude, Angle longitude, double u, double v)
        {
            super(latitude, longitude);
            this.u = u;
            this.v = v;
        }
    }

    /* The polygon's boundaries. */
    protected List<Iterable<? extends LatLon>> boundaries = new ArrayList<>();
    /** If an image source was specified, this is the WWTexture form. */
    protected WWTexture explicitTexture;
    /** This shape's texture coordinates. */
    protected float[] explicitTextureCoords;

    /** Constructs a new surface polygon with the default attributes and no locations. */
    public SurfacePolygon()
    {
    }

    /**
     * Creates a shallow copy of the specified source shape.
     *
     * @param source the shape to copy.
     */
    public SurfacePolygon(SurfacePolygon source)
    {
        super(source);

        this.boundaries.addAll(source.boundaries);
    }

    /**
     * Constructs a new surface polygon with the specified normal (as opposed to highlight) attributes and no locations.
     * Modifying the attribute reference after calling this constructor causes this shape's appearance to change
     * accordingly.
     *
     * @param normalAttrs the normal attributes. May be null, in which case default attributes are used.
     */
    public SurfacePolygon(ShapeAttributes normalAttrs)
    {
        super(normalAttrs);
    }

    /**
     * Constructs a new surface polygon with the default attributes and the specified iterable of locations.
     * <p>
     * Note: If fewer than three locations is specified, no polygon is drawn.
     *
     * @param iterable the polygon locations.
     *
     * @throws IllegalArgumentException if the locations iterable is null.
     */
    public SurfacePolygon(Iterable<? extends LatLon> iterable)
    {
        if (iterable == null)
        {
            String message = Logging.getMessage("nullValue.IterableIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.setOuterBoundary(iterable);
    }

    /**
     * Constructs a new surface polygon with the specified normal (as opposed to highlight) attributes and the specified
     * iterable of locations. Modifying the attribute reference after calling this constructor causes this shape's
     * appearance to change accordingly.
     * <p>
     * Note: If fewer than three locations is specified, no polygon is drawn.
     *
     * @param normalAttrs the normal attributes. May be null, in which case default attributes are used.
     * @param iterable    the polygon locations.
     *
     * @throws IllegalArgumentException if the locations iterable is null.
     */
    public SurfacePolygon(ShapeAttributes normalAttrs, Iterable<? extends LatLon> iterable)
    {
        super(normalAttrs);

        if (iterable == null)
        {
            String message = Logging.getMessage("nullValue.IterableIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.setOuterBoundary(iterable);
    }

    @Override
	public Iterable<? extends LatLon> getLocations(Globe globe)
    {
        return this.getOuterBoundary();
    }

    public Iterable<? extends LatLon> getLocations()
    {
        return this.getOuterBoundary();
    }

    public List<Iterable<? extends LatLon>> getBoundaries()
    {
        return this.boundaries;
    }

    public void setLocations(Iterable<? extends LatLon> iterable)
    {
        if (iterable == null)
        {
            String message = Logging.getMessage("nullValue.IterableIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.setOuterBoundary(iterable);
    }

    public Iterable<? extends LatLon> getOuterBoundary()
    {
        return this.boundaries.size() > 0 ? this.boundaries.get(0) : null;
    }

    public void setOuterBoundary(Iterable<? extends LatLon> iterable)
    {
        if (iterable == null)
        {
            String message = Logging.getMessage("nullValue.IterableIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        if (this.boundaries.size() > 0) {
			this.boundaries.set(0, iterable);
		} else {
			this.boundaries.add(iterable);
		}

        this.onShapeChanged();
    }

    public void addInnerBoundary(Iterable<? extends LatLon> iterable)
    {
        if (iterable == null)
        {
            String message = Logging.getMessage("nullValue.IterableIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.boundaries.add(iterable);
        this.onShapeChanged();
    }

    /**
     * Returns this polygon's texture image source.
     *
     * @return the texture image source, or null if no source has been specified.
     */
    public Object getTextureImageSource()
    {
        return this.explicitTexture != null ? this.explicitTexture.getImageSource() : null;
    }

    /**
     * Returns the texture coordinates for this polygon.
     *
     * @return the texture coordinates, or null if no texture coordinates have been specified.
     */
    public float[] getTextureCoords()
    {
        return this.explicitTextureCoords;
    }

    /**
     * Specifies the texture to apply to this polygon.
     *
     * @param imageSource   the texture image source. May be a {@link String} identifying a file path or URL, a {@link
     *                      File}, or a {@link java.net.URL}.
     * @param texCoords     the (s, t) texture coordinates aligning the image to the polygon. There must be one texture
     *                      coordinate pair, (s, t), for each polygon location in the polygon's outer boundary.
     * @param texCoordCount the number of texture coordinates, (s, v) pairs, specified.
     *
     * @throws IllegalArgumentException if the image source is not null and either the texture coordinates are null or
     *                                  inconsistent with the specified texture-coordinate count, or there are fewer
     *                                  than three texture coordinate pairs.
     */
    public void setTextureImageSource(Object imageSource, float[] texCoords, int texCoordCount)
    {
        if (imageSource == null)
        {
            this.explicitTexture = null;
            this.explicitTextureCoords = null;
            this.onShapeChanged();
            return;
        }

        if (texCoords == null)
        {
            String message = Logging.getMessage("generic.ListIsEmpty");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        if (texCoordCount < 3 || texCoords.length < 2 * texCoordCount)
        {
            String message = Logging.getMessage("generic.InsufficientPositions");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.explicitTexture = new BasicWWTexture(imageSource, true);
        this.explicitTextureCoords = texCoords;
        this.onShapeChanged();
    }

    @Override
	public Position getReferencePosition()
    {
        if (this.getOuterBoundary() == null) {
			return null;
		}

        var iterator = this.getOuterBoundary().iterator();
        if (!iterator.hasNext()) {
			return null;
		}

        return new Position(iterator.next(), 0);
    }

    @Override
    protected WWTexture getExplicitInteriorTexture()
    {
        return this.explicitTexture;
    }

    @Override
    protected InteriorVBOData buildInteriorVBOs(DrawContext dc, SurfaceTileDrawContext sdc)
    {
        if (this.boundaries.isEmpty()) {
			return null;
		}

        Position refPos = this.getReferencePosition();
        if (refPos == null) {
			return null;
		}

        boolean hasHoles = this.boundaries.size() > 1;
        boolean hasTexCoords = this.explicitTextureCoords != null;

        // For simple polygons without holes or explicit textures, delegate to parent
        if (!hasHoles && !hasTexCoords) {
			return super.buildInteriorVBOs(dc, sdc);
		}

        // seaglassfoundry.com: when the fill shader was linked with dvec2 positions, upload full
        // double-precision vertex data so the MVP multiply preserves sub-metre precision. Tex coords
        // remain float (texture math stays in fp32 throughout). Triangulation runs on the float array
        // — topology doesn't need fp64.
        SurfaceShapeFillShader shader = fillShaders.get();
        boolean useDoublePositions = shader != null && shader.isFp64Enabled();

        // Use assembleContours() to get edge-interpolated contours with texture coordinates
        Angle degreesPerInterval = Angle.fromDegrees(1.0 / this.computeEdgeIntervalsPerDegree(sdc));
        List<List<Vertex>> contours = this.assembleContours(degreesPerInterval);

        if (contours.isEmpty()) {
			return null;
		}
        List<Vertex> outerContour = contours.get(0);
        if (WWMath.computeWindingOrderOfLocations(outerContour)!=AVKey.COUNTER_CLOCKWISE) {
        	Collections.reverse(outerContour);
        }

        double refLon = refPos.getLongitude().degrees;
        double refLat = refPos.getLatitude().degrees;
        int floatsPerVertex = hasTexCoords ? 4 : 2;  // x,y or x,y,s,t (for the triangulation-side float array)

        // Flatten all contour vertices into a single array
        int totalVertices = 0;
        for (List<Vertex> contour : contours) {
			totalVertices += contour.size();
		}

        if (totalVertices < 3) {
			return null;
		}

        float[] allVertices = new float[totalVertices * floatsPerVertex];
        // Parallel double[] carrying full-precision positions only (no tex coords). Indexed as
        // vertex i -> (allVerticesD[2*i], allVerticesD[2*i + 1]).
        double[] allVerticesD = useDoublePositions ? new double[totalVertices * 2] : null;
        int vi = 0;
        int vertexCount = 0;
        // Track contour boundaries for hole bridging
        int[] contourStarts = new int[contours.size()];
        int[] contourCounts = new int[contours.size()];

        for (int c = 0; c < contours.size(); c++)
        {
            List<Vertex> contour = contours.get(c);
            contourStarts[c] = vertexCount;
            contourCounts[c] = contour.size();

            for (Vertex v : contour)
            {
                double dx = v.getLongitude().degrees - refLon;
                double dy = v.getLatitude().degrees - refLat;
                allVertices[vi++] = (float) dx;
                allVertices[vi++] = (float) dy;
                if (hasTexCoords)
                {
                    allVertices[vi++] = (float) v.u;
                    allVertices[vi++] = (float) v.v;
                }
                if (allVerticesD != null)
                {
                    allVerticesD[vertexCount * 2]     = dx;
                    allVerticesD[vertexCount * 2 + 1] = dy;
                }
                vertexCount++;
            }
        }

        // Triangulate
        int[] triangles;
        if (hasHoles && contours.size() > 1)
        {
            // Bridge holes into outer boundary and triangulate as one polygon.
            // assembleContours() processes boundaries in order: first contour(s) from boundary 0 (outer),
            // then contour(s) from boundary 1+ (holes). For the common case (no dateline/pole crossing),
            // there's exactly one contour per boundary.
            // We use the first contour as outer and remaining as holes.
            int numHoles = contours.size() - 1;
            int[] holeStarts = new int[numHoles];
            int[] holeCounts = new int[numHoles];
            for (int h = 0; h < numHoles; h++)
            {
                holeStarts[h] = contourStarts[h + 1];
                holeCounts[h] = contourCounts[h + 1];
            }

            // bridgeHoles works with x,y pairs; if we have interleaved tex coords, extract positions
            float[] posOnly;
            int[] posContourStarts;
            int[] posContourCounts;
            if (hasTexCoords)
            {
                posOnly = new float[totalVertices * 2];
                int pi = 0;
                for (int i = 0; i < totalVertices; i++)
                {
                    posOnly[pi++] = allVertices[i * 4];
                    posOnly[pi++] = allVertices[i * 4 + 1];
                }
                posContourStarts = contourStarts;
                posContourCounts = contourCounts;
            }
            else
            {
                posOnly = allVertices;
                posContourStarts = contourStarts;
                posContourCounts = contourCounts;
            }

            int[] mergedRing = GpuTriangulator.bridgeHoles(posOnly,
                posContourStarts[0], posContourCounts[0],
                holeStarts, holeCounts);

            if (mergedRing == null || mergedRing.length < 3) {
				return null;
			}

            triangles = GpuTriangulator.triangulateCPU(posOnly, mergedRing);
        }
        else
        {
            // Single contour (possibly from dateline split) — triangulate directly
            float[] posOnly;
            if (hasTexCoords)
            {
                posOnly = new float[totalVertices * 2];
                int pi = 0;
                for (int i = 0; i < totalVertices; i++)
                {
                    posOnly[pi++] = allVertices[i * 4];
                    posOnly[pi++] = allVertices[i * 4 + 1];
                }
            }
            else
            {
                posOnly = allVertices;
            }

            // Triangulate each contour independently
            List<int[]> allTriangles = new ArrayList<>();
            int totalIndices = 0;
            for (int c = 0; c < contours.size(); c++)
            {
                int start = contourStarts[c];
                int count = contourCounts[c];
                if (count < 3) {
					continue;
				}
                int[] ring = new int[count];
                for (int i = 0; i < count; i++) {
					ring[i] = start + i;
				}
                int[] tri = GpuTriangulator.triangulateCPU(posOnly, ring);
                if (tri.length > 0)
                {
                    allTriangles.add(tri);
                    totalIndices += tri.length;
                }
            }

            if (totalIndices == 0) {
				return null;
			}

            triangles = new int[totalIndices];
            int off = 0;
            for (int[] tri : allTriangles)
            {
                System.arraycopy(tri, 0, triangles, off, tri.length);
                off += tri.length;
            }
        }

        if (triangles.length == 0) {
			return null;
		}

        // Upload to VBOs
        GL gl = dc.getGL();
        int[] vboIds = new int[2];
        gl.glGenBuffers(2, vboIds, 0);

        // Vertex VBO — layout depends on fp64 + hasTexCoords:
        //   fp32, no tex: tight float[x,y]                  stride 0   (8 bytes/vertex)
        //   fp32, tex:    interleaved float[x,y,s,t]        stride 16  (tex @ 8)
        //   fp64, no tex: tight double[x,y]                 stride 0   (16 bytes/vertex)
        //   fp64, tex:    interleaved double[x,y]+float[s,t] stride 24 (tex @ 16)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboIds[0]);
        int stride;
        int texCoordByteOffset;
        if (useDoublePositions)
        {
            if (hasTexCoords)
            {
                stride = 2 * Double.BYTES + 2 * Float.BYTES;
                texCoordByteOffset = 2 * Double.BYTES;
                ByteBuffer bb = Buffers.newDirectByteBuffer(totalVertices * stride);
                bb.order(ByteOrder.nativeOrder());
                for (int i = 0; i < totalVertices; i++)
                {
                    bb.putDouble(allVerticesD[i * 2]);
                    bb.putDouble(allVerticesD[i * 2 + 1]);
                    bb.putFloat(allVertices[i * 4 + 2]);
                    bb.putFloat(allVertices[i * 4 + 3]);
                }
                bb.flip();
                gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) totalVertices * stride, bb, GL.GL_STATIC_DRAW);
            }
            else
            {
                stride = 0;
                texCoordByteOffset = -1;
                DoubleBuffer dbuf = Buffers.newDirectDoubleBuffer(allVerticesD);
                gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) allVerticesD.length * Double.BYTES,
                    dbuf, GL.GL_STATIC_DRAW);
            }
        }
        else
        {
            stride = hasTexCoords ? 4 * Float.BYTES : 0;
            texCoordByteOffset = hasTexCoords ? 2 * Float.BYTES : -1;
            FloatBuffer vertBuf = Buffers.newDirectFloatBuffer(allVertices);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) allVertices.length * Float.BYTES,
                vertBuf, GL.GL_STATIC_DRAW);
        }

        // Index VBO
        IntBuffer idxBuf = Buffers.newDirectIntBuffer(triangles);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, vboIds[1]);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) triangles.length * Integer.BYTES,
            idxBuf, GL.GL_STATIC_DRAW);

        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);

        int positionGLType = useDoublePositions ? GL2GL3.GL_DOUBLE : GL.GL_FLOAT;
        return new InteriorVBOData(vboIds[0], vboIds[1], triangles.length,
            hasTexCoords, stride, positionGLType, texCoordByteOffset);
    }

    protected List<List<Vertex>> assembleContours(Angle maxEdgeLength)
    {
        List<List<Vertex>> result = new ArrayList<>();

        for (int b = 0; b < this.boundaries.size(); b++)
        {
            Iterable<? extends LatLon> locations = this.boundaries.get(b);
            float[] texCoords = (b == 0) ? this.explicitTextureCoords : null;
            int c = 0;

            // Merge the boundary locations with their respective texture coordinates, if any.
            List<Vertex> contour = new ArrayList<>();
            for (LatLon location : locations)
            {
                Vertex vertex = new Vertex(location);
                contour.add(vertex);

                if (texCoords != null && texCoords.length > c)
                {
                    vertex.u = texCoords[c++];
                    vertex.v = texCoords[c++];
                }
            }

            // Interpolate the contour vertices according to this polygon's path type and number of edge intervals.
            this.closeContour(contour);
            this.subdivideContour(contour, maxEdgeLength);

            // Modify the contour vertices to compensate for the spherical nature of geographic coordinates.
            String pole = LatLon.locationsContainPole(contour);
            if (pole != null)
            {
                result.add(this.clipWithPole(contour, pole, maxEdgeLength));
            }
            else if (LatLon.locationsCrossDateLine(contour))
            {
                result.addAll(this.clipWithDateline(contour));
            }
            else
            {
                result.add(contour);
            }
        }

        return result;
    }

    protected void closeContour(List<Vertex> contour)
    {
        if (!contour.get(0).equals(contour.get(contour.size() - 1)))
        {
            contour.add(contour.get(0));
        }
    }

    protected void subdivideContour(List<Vertex> contour, Angle maxEdgeLength)
    {
        List<Vertex> original = new ArrayList<>(contour.size());
        original.addAll(contour);
        contour.clear();

        for (int i = 0; i < original.size() - 1; i++)
        {
            Vertex begin = original.get(i);
            Vertex end = original.get(i + 1);
            contour.add(begin);
            this.subdivideEdge(begin, end, maxEdgeLength, contour);
        }

        Vertex last = original.get(original.size() - 1);
        contour.add(last);
    }

    protected void subdivideEdge(Vertex begin, Vertex end, Angle maxEdgeLength, List<Vertex> result)
    {
        Vertex center = new Vertex(LatLon.interpolate(this.pathType, 0.5, begin, end));
        center.u = 0.5 * (begin.u + end.u);
        center.v = 0.5 * (begin.v + end.v);
        center.edgeFlag = begin.edgeFlag || end.edgeFlag;

        Angle edgeLength = LatLon.linearDistance(begin, end);
        if (edgeLength.compareTo(maxEdgeLength) > 0)
        {
            this.subdivideEdge(begin, center, maxEdgeLength, result);
            result.add(center);
            this.subdivideEdge(center, end, maxEdgeLength, result);
        }
        else
        {
            result.add(center);
        }
    }

    protected List<Vertex> clipWithPole(List<Vertex> contour, String pole, Angle maxEdgeLength)
    {
        List<Vertex> newVertices = new ArrayList<>();

        Angle poleLat = AVKey.NORTH.equals(pole) ? Angle.POS90 : Angle.NEG90;

        Vertex vertex = null;
        for (Vertex nextVertex : contour)
        {
            if (vertex != null)
            {
                newVertices.add(vertex);
                if (LatLon.locationsCrossDateline(vertex, nextVertex))
                {
                    // Determine where the segment crosses the dateline.
                    LatLon separation = LatLon.intersectionWithMeridian(vertex, nextVertex, Angle.POS180);
                    double sign = Math.signum(vertex.getLongitude().degrees);

                    Angle lat = separation.getLatitude();
                    Angle thisSideLon = Angle.POS180.multiply(sign);
                    Angle otherSideLon = thisSideLon.multiply(-1);

                    // Add locations that run from the intersection to the pole, then back to the intersection. Note
                    // that the longitude changes sign when the path returns from the pole.
                    //         . Pole
                    //      2 ^ | 3
                    //        | |
                    //      1 | v 4
                    // --->---- ------>
                    Vertex in = new Vertex(lat, thisSideLon, 0, 0);
                    Vertex inPole = new Vertex(poleLat, thisSideLon, 0, 0);
                    Vertex centerPole = new Vertex(poleLat, Angle.ZERO, 0, 0);
                    Vertex outPole = new Vertex(poleLat, otherSideLon, 0, 0);
                    Vertex out = new Vertex(lat, otherSideLon, 0, 0);
                    in.edgeFlag = inPole.edgeFlag = centerPole.edgeFlag = outPole.edgeFlag = out.edgeFlag = false;

                    double vertexDistance = LatLon.linearDistance(vertex, in).degrees;
                    double nextVertexDistance = LatLon.linearDistance(nextVertex, out).degrees;
                    double a = vertexDistance / (vertexDistance + nextVertexDistance);
                    in.u = out.u = WWMath.mix(a, vertex.u, nextVertex.u);
                    in.v = out.v = WWMath.mix(a, vertex.v, nextVertex.v);

                    double[] uv = this.uvWeightedAverage(contour, centerPole);
                    inPole.u = outPole.u = centerPole.u = uv[0];
                    inPole.v = outPole.v = centerPole.v = uv[1];

                    newVertices.add(in);
                    newVertices.add(inPole);
                    this.subdivideEdge(inPole, centerPole, maxEdgeLength, newVertices);
                    newVertices.add(centerPole);
                    this.subdivideEdge(centerPole, outPole, maxEdgeLength, newVertices);
                    newVertices.add(outPole);
                    newVertices.add(out);
                }
            }
            vertex = nextVertex;
        }
        newVertices.add(vertex);

        return newVertices;
    }

    protected double[] uvWeightedAverage(List<Vertex> contour, Vertex vertex)
    {
        double[] weight = new double[contour.size()];
        double sumOfWeights = 0;
        for (int i = 0; i < contour.size(); i++)
        {
            double distance = LatLon.greatCircleDistance(contour.get(i), vertex).degrees;
            weight[i] = 1 / distance;
            sumOfWeights += weight[i];
        }

        double u = 0;
        double v = 0;
        for (int i = 0; i < contour.size(); i++)
        {
            double factor = weight[i] / sumOfWeights;
            u += contour.get(i).u * factor;
            v += contour.get(i).v * factor;
        }

        return new double[] {u, v};
    }

    protected List<List<Vertex>> clipWithDateline(List<Vertex> contour)
    {
        List<Vertex> result = new ArrayList<>();
        Vertex prev = null;
        Angle offset = null;
        boolean applyOffset = false;

        for (Vertex cur : contour)
        {
            if (prev != null && LatLon.locationsCrossDateline(prev, cur))
            {
                if (offset == null) {
					offset = (prev.longitude.degrees < 0 ? Angle.NEG360 : Angle.POS360);
				}
                applyOffset = !applyOffset;
            }

            if (applyOffset)
            {
                result.add(new Vertex(cur.latitude, cur.longitude.add(offset), cur.u, cur.v));
            }
            else
            {
                result.add(cur);
            }

            prev = cur;
        }

        List<Vertex> mirror = new ArrayList<>();
        for (Vertex cur : result)
        {
            mirror.add(new Vertex(cur.latitude, cur.longitude.subtract(offset), cur.u, cur.v));
        }

        return Arrays.asList(result, mirror);
    }

    @Override
	protected List<List<LatLon>> createGeometry(Globe globe, double edgeIntervalsPerDegree)
    {
        if (this.boundaries.isEmpty()) {
			return null;
		}

        ArrayList<List<LatLon>> geom = new ArrayList<>();

        for (Iterable<? extends LatLon> boundary : this.boundaries)
        {
            ArrayList<LatLon> drawLocations = new ArrayList<>();

            this.generateIntermediateLocations(boundary, edgeIntervalsPerDegree, true, drawLocations);

            // Ensure all contours have counter-clockwise winding order for consistent dateline/pole handling.
            //noinspection StringEquality
            if (WWMath.computeWindingOrderOfLocations(drawLocations) != AVKey.COUNTER_CLOCKWISE) {
				Collections.reverse(drawLocations);
			}

            geom.add(drawLocations);
        }

        if (geom.isEmpty() || geom.get(0).size() < 3) {
			return null;
		}

        return geom;
    }

    @Override
	protected void doMoveTo(Position oldReferencePosition, Position newReferencePosition)
    {
        if (this.boundaries.isEmpty()) {
			return;
		}

        for (int i = 0; i < this.boundaries.size(); i++)
        {
            ArrayList<LatLon> newLocations = new ArrayList<>();

            for (LatLon ll : this.boundaries.get(i))
            {
                Angle heading = LatLon.greatCircleAzimuth(oldReferencePosition, ll);
                Angle pathLength = LatLon.greatCircleDistance(oldReferencePosition, ll);
                newLocations.add(LatLon.greatCircleEndPosition(newReferencePosition, heading, pathLength));
            }

            this.boundaries.set(i, newLocations);
        }

        // We've changed the polygon's list of boundaries; flag the shape as changed.
        this.onShapeChanged();
    }

    @Override
	protected void doMoveTo(Globe globe, Position oldReferencePosition, Position newReferencePosition)
    {
        if (this.boundaries.isEmpty()) {
			return;
		}

        for (int i = 0; i < this.boundaries.size(); i++)
        {
            List<LatLon> newLocations = LatLon.computeShiftedLocations(globe, oldReferencePosition,
                newReferencePosition, this.boundaries.get(i));

            this.boundaries.set(i, newLocations);
        }

        // We've changed the polygon's list of boundaries; flag the shape as changed.
        this.onShapeChanged();
    }

    //**************************************************************//
    //********************  Interior Tessellation  *****************//
    //**************************************************************//

    /**
     * Overridden to clear the polygon's locations iterable upon an unsuccessful tessellation attempt. This ensures the
     * polygon won't attempt to re-tessellate itself each frame.
     *
     * @param dc the current DrawContext.
     */
    @Override
    protected void handleUnsuccessfulInteriorTessellation(DrawContext dc)
    {
        super.handleUnsuccessfulInteriorTessellation(dc);

        // If tessellating the polygon's interior was unsuccessful, we modify the polygon's to avoid any additional
        // tessellation attempts, and free any resources that the polygon won't use. This is accomplished by clearing
        // the polygon's boundary list.
        this.boundaries.clear();
        this.onShapeChanged();
    }

    //**************************************************************//
    //******************** Restorable State  ***********************//
    //**************************************************************//

    @Override
	protected void doGetRestorableState(RestorableSupport rs, RestorableSupport.StateObject context)
    {
        super.doGetRestorableState(rs, context);

        if (!this.boundaries.isEmpty())
        {
            RestorableSupport.StateObject so = rs.addStateObject(context, "boundaries");
            for (Iterable<? extends LatLon> boundary : this.boundaries)
            {
                rs.addStateValueAsLatLonList(so, "boundary", boundary);
            }
        }
    }

    @Override
	protected void doRestoreState(RestorableSupport rs, RestorableSupport.StateObject context)
    {
        super.doRestoreState(rs, context);

        RestorableSupport.StateObject so = rs.getStateObject(context, "boundaries");
        if (so != null)
        {
            this.boundaries.clear();

            RestorableSupport.StateObject[] sos = rs.getAllStateObjects(so, "boundary");
            if (sos != null)
            {
                for (RestorableSupport.StateObject boundary : sos)
                {
                    if (boundary == null) {
						continue;
					}

                    Iterable<LatLon> locations = rs.getStateObjectAsLatLonList(boundary);
                    if (locations != null) {
						this.boundaries.add(locations);
					}
                }
            }

            // We've changed the polygon's list of boundaries; flag the shape as changed.
            this.onShapeChanged();
        }
    }

    @Override
	protected void legacyRestoreState(RestorableSupport rs, RestorableSupport.StateObject context)
    {
        super.legacyRestoreState(rs, context);

        Iterable<LatLon> locations = rs.getStateValueAsLatLonList(context, "locationList");

        if (locations == null) {
			locations = rs.getStateValueAsLatLonList(context, "locations");
		}

        if (locations != null) {
			this.setOuterBoundary(locations);
		}
    }

    /**
     * Export the polygon to KML as a {@code <Placemark>} element. The {@code output} object will receive the data. This
     * object must be one of: java.io.Writer java.io.OutputStream javax.xml.stream.XMLStreamWriter
     *
     * @param output Object to receive the generated KML.
     *
     * @throws XMLStreamException If an exception occurs while writing the KML
     * @throws IOException        if an exception occurs while exporting the data.
     * @see #export(String, Object)
     */
    @Override
	protected void exportAsKML(Object output) throws IOException, XMLStreamException
    {
        XMLStreamWriter xmlWriter = null;
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        boolean closeWriterWhenFinished = true;

        if (output instanceof XMLStreamWriter)
        {
            xmlWriter = (XMLStreamWriter) output;
            closeWriterWhenFinished = false;
        }
        else if (output instanceof Writer)
        {
            xmlWriter = factory.createXMLStreamWriter((Writer) output);
        }
        else if (output instanceof OutputStream)
        {
            xmlWriter = factory.createXMLStreamWriter((OutputStream) output);
        }

        if (xmlWriter == null)
        {
            String message = Logging.getMessage("Export.UnsupportedOutputObject");
            Logging.logger().warning(message);
            throw new IllegalArgumentException(message);
        }

        xmlWriter.writeStartElement("Placemark");

        String property = getStringValue(AVKey.DISPLAY_NAME);
        if (property != null)
        {
            xmlWriter.writeStartElement("name");
            xmlWriter.writeCharacters(property);
            xmlWriter.writeEndElement();
        }

        xmlWriter.writeStartElement("visibility");
        xmlWriter.writeCharacters(KMLExportUtil.kmlBoolean(this.isVisible()));
        xmlWriter.writeEndElement();

        String shortDescription = (String) getValue(AVKey.SHORT_DESCRIPTION);
        if (shortDescription != null)
        {
            xmlWriter.writeStartElement("Snippet");
            xmlWriter.writeCharacters(shortDescription);
            xmlWriter.writeEndElement();
        }

        String description = (String) getValue(AVKey.BALLOON_TEXT);
        if (description != null)
        {
            xmlWriter.writeStartElement("description");
            xmlWriter.writeCharacters(description);
            xmlWriter.writeEndElement();
        }

        // KML does not allow separate attributes for cap and side, so just use the side attributes.
        final ShapeAttributes normalAttributes = getAttributes();
        final ShapeAttributes highlightAttributes = getHighlightAttributes();

        // Write style map
        if (normalAttributes != null || highlightAttributes != null)
        {
            xmlWriter.writeStartElement("StyleMap");
            KMLExportUtil.exportAttributesAsKML(xmlWriter, KMLConstants.NORMAL, normalAttributes);
            KMLExportUtil.exportAttributesAsKML(xmlWriter, KMLConstants.HIGHLIGHT, highlightAttributes);
            xmlWriter.writeEndElement(); // StyleMap
        }

        // Write geometry
        xmlWriter.writeStartElement("Polygon");

        xmlWriter.writeStartElement("extrude");
        xmlWriter.writeCharacters("0");
        xmlWriter.writeEndElement();

        xmlWriter.writeStartElement("altitudeMode");
        xmlWriter.writeCharacters("clampToGround");
        xmlWriter.writeEndElement();

        // Outer boundary
        Iterable<? extends LatLon> outerBoundary = this.getOuterBoundary();
        if (outerBoundary != null)
        {
            xmlWriter.writeStartElement("outerBoundaryIs");
            KMLExportUtil.exportBoundaryAsLinearRing(xmlWriter, outerBoundary, null);
            xmlWriter.writeEndElement(); // outerBoundaryIs
        }

        // Inner boundaries
        Iterator<Iterable<? extends LatLon>> boundaryIterator = boundaries.iterator();
        if (boundaryIterator.hasNext()) {
			boundaryIterator.next(); // Skip outer boundary, we already dealt with it above
		}

        while (boundaryIterator.hasNext())
        {
            Iterable<? extends LatLon> boundary = boundaryIterator.next();

            xmlWriter.writeStartElement("innerBoundaryIs");
            KMLExportUtil.exportBoundaryAsLinearRing(xmlWriter, boundary, null);
            xmlWriter.writeEndElement(); // innerBoundaryIs
        }

        xmlWriter.writeEndElement(); // Polygon
        xmlWriter.writeEndElement(); // Placemark

        xmlWriter.flush();
        if (closeWriterWhenFinished) {
			xmlWriter.close();
		}
    }
}
