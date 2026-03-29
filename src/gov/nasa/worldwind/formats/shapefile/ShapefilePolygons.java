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
/*
 * Modifications copyright 2025-2026 seaglassfoundry.com — Part of the WorldWind Reforged project.
 *
 * Changes:
 * - Added ear-clipping tessellation (tessellateEarClipping) via GpuTriangulator, replacing
 *   GLUtessellator as the primary triangulation path with GLU retained as fallback
 * - Wired GPU compute-shader batch triangulation (GpuTriangulator.triangulateBatch) to render
 *   thread via two-phase dispatch: background thread collects merged ring data into
 *   GpuTessellationData; render thread calls dispatchGpuTriangulation() to run the GL 4.3
 *   compute shader and populate RecordIndices; CPU ear-clipping fallback when GL 4.3 unavailable
 * - Added VBO rendering with GL_STATIC_DRAW caching via GpuResourceCache
 * - Added merged draw calls (assembleMergedBuffers/drawMerged) reducing 2N draw calls to 2
 *   per tile using per-vertex colors
 * - Added short index buffers (GL_UNSIGNED_SHORT) for tiles with <= 65,535 vertices
 * - Replaced GL_CLIP_PLANE0..3 with GL_SCISSOR_TEST for tile clipping
 * - Added GLSL 330 shader pipeline (vertex+fragment) replacing fixed-function rendering
 * - Added bounding extent cache eliminating per-frame Sector.computeBoundingBox() cost
 * - Optimized per-tile modelview matrix with direct column-3 translation (zero allocations)
 * - Added tile subdivision caching eliminating per-frame child tile allocations
 */
package gov.nasa.worldwind.formats.shapefile;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES1;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.fixedfunc.GLPointerFunc;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUtessellator;
import com.jogamp.opengl.glu.GLUtessellatorCallback;

import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.cache.BasicMemoryCache;
import gov.nasa.worldwind.cache.Cacheable;
import gov.nasa.worldwind.cache.GpuResourceCache;
import gov.nasa.worldwind.cache.MemoryCache;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.Extent;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.geom.Vec4;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.pick.PickSupport;
import gov.nasa.worldwind.pick.PickedObject;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.OrderedRenderable;
import gov.nasa.worldwind.render.PreRenderable;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwind.render.SurfaceObjectTileBuilder;
import gov.nasa.worldwind.render.SurfaceRenderable;
import gov.nasa.worldwind.render.shaders.GpuTriangulator;
import gov.nasa.worldwind.render.shaders.ShaderProgram;
import gov.nasa.worldwind.util.BasicQuadTree;
import gov.nasa.worldwind.util.ClippingTessellator;
import gov.nasa.worldwind.util.GLUTessellatorSupport;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.PolygonTessellator2;
import gov.nasa.worldwind.util.PolylineGeneralizer;
import gov.nasa.worldwind.util.Range;
import gov.nasa.worldwind.util.SurfaceTileDrawContext;
import gov.nasa.worldwind.util.Tile;
import gov.nasa.worldwind.util.VecBuffer;
import gov.nasa.worldwind.util.WWMath;
import gov.nasa.worldwind.util.combine.Combinable;
import gov.nasa.worldwind.util.combine.CombineContext;

/**
 * @author dcollins
 * @version $Id: ShapefilePolygons.java 3053 2015-04-28 19:15:46Z dcollins $
 */
public class ShapefilePolygons extends ShapefileRenderable implements OrderedRenderable, PreRenderable, Combinable
{
    /**
     * When {@code true}, VBO rendering is forcibly disabled regardless of GL capabilities.
     * Used for A/B benchmarking of VBO vs client-memory rendering.
     */
    public static volatile boolean forceDisableVBO = false;
    public static volatile boolean forceDisableMerged = false;
    public static volatile boolean forceDisableEarClipping = false;
    public static volatile boolean forceDisableShader = false;

    public static class Record extends ShapefileRenderable.Record
    {
        protected double[][] boundaryEffectiveArea;
        protected boolean[] boundaryCrossesAntimeridian;

        public Record(ShapefileRenderable shapefileRenderable, ShapefileRecord shapefileRecord)
        {
            super(shapefileRenderable, shapefileRecord);
        }

        protected double[] getBoundaryEffectiveArea(int boundaryIndex)
        {
            return this.boundaryEffectiveArea != null ? this.boundaryEffectiveArea[boundaryIndex] : null;
        }

        protected boolean isBoundaryCrossesAntimeridian(int boundaryIndex)
        {
            return this.boundaryCrossesAntimeridian != null && this.boundaryCrossesAntimeridian[boundaryIndex];
        }
    }

    protected static class RecordGroup
    {
        protected final ShapeAttributes attributes;
        protected IntBuffer indices;
        protected Range interiorIndexRange = new Range(0, 0);
        protected Range outlineIndexRange = new Range(0, 0);
        protected ArrayList<RecordIndices> recordIndices = new ArrayList<>();
        protected Object vboKey = new Object(); // VBO cache key for index buffer

        public RecordGroup(ShapeAttributes attributes)
        {
            this.attributes = attributes;
        }
    }

    protected static class RecordIndices
    {
        protected final int ordinal;
        protected Range vertexRange = new Range(0, 0);
        protected IntBuffer interiorIndices;
        protected IntBuffer outlineIndices;

        public RecordIndices(int ordinal)
        {
            this.ordinal = ordinal;
        }
    }

    protected static class ShapefileTile implements OrderedRenderable, SurfaceRenderable
    {
        // Properties that define the tile.
        protected final ShapefileRenderable shape;
        protected final Sector sector;
        protected final double resolution;
        // Properties supporting geometry caching.
        protected ShapefileTile fallbackTile;
        protected ShapefileGeometry geometry;
        protected ShapefileTile[] children; // cached subdivision — avoids per-frame allocations
        protected final Object nullGeometryStateKey = new Object();

        public ShapefileTile(ShapefileRenderable shape, Sector sector, double resolution)
        {
            this.shape = shape;
            this.sector = sector;
            this.resolution = resolution;
        }

        public ShapefileRenderable getShape()
        {
            return this.shape;
        }

        public Sector getSector()
        {
            return this.sector;
        }

        public double getResolution()
        {
            return this.resolution;
        }

        public ShapefileGeometry getGeometry()
        {
            return this.geometry;
        }

        public void setGeometry(ShapefileGeometry geometry)
        {
            this.geometry = geometry;
        }

        public ShapefileTile[] subdivide()
        {
            if (this.children == null)
            {
                Sector[] sectors = this.sector.subdivide();
                this.children = new ShapefileTile[4];
                this.children[0] = new ShapefileTile(this.shape, sectors[0], this.resolution / 2);
                this.children[1] = new ShapefileTile(this.shape, sectors[1], this.resolution / 2);
                this.children[2] = new ShapefileTile(this.shape, sectors[2], this.resolution / 2);
                this.children[3] = new ShapefileTile(this.shape, sectors[3], this.resolution / 2);
            }
            return this.children;
        }

        @Override
        public double getDistanceFromEye()
        {
            return 0;
        }

        @Override
        public List<Sector> getSectors(DrawContext dc)
        {
            return Arrays.asList(this.sector);
        }

        @Override
        public void pick(DrawContext dc, Point pickPoint)
        {
        }

        @Override
        public Object getStateKey(DrawContext dc)
        {
            return this.geometry != null ? new ShapefileGeometryStateKey(this.geometry) : this.nullGeometryStateKey;
        }

        @Override
        public void render(DrawContext dc)
        {
            ((ShapefilePolygons) this.shape).render(dc, this);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
                return true;
            if (o == null || this.getClass() != o.getClass())
                return false;

            ShapefileTile that = (ShapefileTile) o;
            return this.shape.equals(that.shape)
                && this.sector.equals(that.sector)
                && this.resolution == that.resolution;
        }

        @Override
        public int hashCode()
        {
            long temp = this.resolution != +0.0d ? Double.doubleToLongBits(this.resolution) : 0L;
            int result;
            result = this.shape.hashCode();
            result = 31 * result + this.sector.hashCode();
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }
    }

    protected static class ShapefileGeometry implements Runnable, Cacheable, Comparable<ShapefileGeometry>
    {
        // Properties that define the geometry.
        protected final ShapefileRenderable shape;
        protected final Sector sector;
        protected final double resolution;
        // Properties supporting geometry tessellation.
        protected MemoryCache memoryCache;
        protected Object memoryCacheKey;
        protected PropertyChangeListener listener;
        protected double priority;
        // Properties supporting geometry rendering.
        protected FloatBuffer vertices;
        protected int vertexStride;
        protected int vertexCount;
        protected Vec4 vertexOffset;
        protected ArrayList<RecordIndices> recordIndices = new ArrayList<>();
        protected ArrayList<RecordGroup> attributeGroups = new ArrayList<>();
        protected long attributeStateID;
        // VBO cache keys for GPU-side storage
        protected Object vertexVboKey = new Object();
        protected Object indexVboKey = new Object();
        // Merged rendering: per-vertex colors + combined index buffers for minimal draw calls
        protected ByteBuffer mergedInteriorColors;  // RGBA per vertex (4 bytes each)
        protected ByteBuffer mergedOutlineColors;
        protected Buffer mergedInteriorIndices;  // IntBuffer or ShortBuffer depending on vertex count
        protected Buffer mergedOutlineIndices;
        protected int mergedInteriorCount;
        protected int mergedOutlineCount;
        protected float mergedOutlineWidth;
        protected int mergedIndexType = GL.GL_UNSIGNED_INT; // GL_UNSIGNED_SHORT when vertexCount <= 65535
        protected Object mergedInteriorVboKey = new Object();
        protected Object mergedOutlineVboKey = new Object();
        protected Object mergedInteriorColorVboKey = new Object();
        protected Object mergedOutlineColorVboKey = new Object();
        protected long mergedStateID;
        // GPU triangulation: prepared on background thread, dispatched on render thread
        protected volatile GpuTessellationData gpuPendingData;

        public ShapefileGeometry(ShapefileRenderable shape, Sector sector, double resolution)
        {
            this.shape = shape;
            this.sector = sector;
            this.resolution = resolution;
        }

        @Override
        public void run()
        {
            try
            {
                ((ShapefilePolygons) this.shape).tessellate(this);
            }
            catch (Exception e)
            {
                String msg = Logging.getMessage("generic.ExceptionWhileTessellating", this.shape);
                Logging.logger().log(java.util.logging.Level.SEVERE, msg, e);
            }
            finally
            {
                if (this.memoryCache != null && this.memoryCacheKey != null)
                {
                    this.memoryCache.add(this.memoryCacheKey, this);
                }

                if (this.listener != null)
                {
                    this.listener.propertyChange(new PropertyChangeEvent(this, AVKey.REPAINT, null, null));
                }

                // don't need the caching and notification properties anymore
                this.memoryCache = null;
                this.memoryCacheKey = null;
                this.listener = null;
            }
        }

        @Override
        public long getSizeInBytes()
        {
            return 244 + this.sector.getSizeInBytes() + (this.vertices != null ? 4 * this.vertices.remaining() : 0);
        }

        @Override
        public int compareTo(ShapefileGeometry that)
        {
            return Double.compare(this.priority, that.priority);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
                return true;
            if (o == null || this.getClass() != o.getClass())
                return false;

            ShapefileGeometry that = (ShapefileGeometry) o;
            return this.shape.equals(that.shape)
                && this.sector.equals(that.sector)
                && this.resolution == that.resolution;
        }

        @Override
        public int hashCode()
        {
            long temp = this.resolution != +0.0d ? Double.doubleToLongBits(this.resolution) : 0L;
            int result;
            result = this.shape.hashCode();
            result = 31 * result + this.sector.hashCode();
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }
    }

    protected static class ShapefileGeometryStateKey
    {
        protected final ShapefileGeometry geometry;
        protected final long attributeStateID;
        protected final ShapeAttributes[] attributeGroups;

        public ShapefileGeometryStateKey(ShapefileGeometry geom)
        {
            this.geometry = geom;
            this.attributeStateID = geom.attributeStateID;
            this.attributeGroups = new ShapeAttributes[geom.attributeGroups.size()];

            for (int i = 0; i < this.attributeGroups.length; i++)
            {
                this.attributeGroups[i] = geom.attributeGroups.get(i).attributes.copy();
            }
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            ShapefileGeometryStateKey that = (ShapefileGeometryStateKey) o;
            return this.geometry.equals(that.geometry)
                && this.attributeStateID == that.attributeStateID
                && Arrays.equals(this.attributeGroups, that.attributeGroups);
        }

        @Override
        public int hashCode()
        {
            int result = this.geometry.hashCode();
            result = 31 * result + (int) (this.attributeStateID ^ (this.attributeStateID >>> 32));
            result = 31 * result + Arrays.hashCode(this.attributeGroups);
            return result;
        }
    }

    /** Intermediate data prepared on background thread for GPU triangulation on the render thread. */
    protected static class GpuTessellationData
    {
        float[] vertices;          // all vertex positions as x,y pairs (offset applied)
        int vertexCount;           // number of vertices
        int vertexStride;          // always 2
        Vec4 vertexOffset;         // the coordinate offset applied to vertices

        // Per-polygon data (one entry per record that produced geometry)
        int[] ringIndices;         // merged ring index lists for all polygons, packed
        int[] ringOffsets;         // per-polygon start offset into ringIndices
        int[] ringCounts;          // per-polygon vertex count in merged ring
        int[] recordOrdinals;      // record ordinal per polygon
        int[][] vertexRanges;      // per-polygon [startVertex, vertexCount] in the shared buffer
        int[][] outlineIndices;    // pre-computed GL_LINES outline indices per polygon
        int numPolygons;
    }

    static
    {
        if (!WorldWind.getMemoryCacheSet().containsCache(ShapefileGeometry.class.getName()))
        {
            long size = Configuration.getLongValue(AVKey.SHAPEFILE_GEOMETRY_CACHE_SIZE, (long) 50e6); // default 50MB
            MemoryCache cache = new BasicMemoryCache((long) (0.8 * size), size);
            cache.setName("Shapefile Geometry");
            WorldWind.getMemoryCacheSet().addCache(ShapefileGeometry.class.getName(), cache);
        }
    }

    // ShapefilePolygons properties.
    protected double detailHint = 0;
    protected double detailHintOrigin = 2.8;
    protected int outlinePickWidth = 10;
    // Properties supporting shapefile tile assembly and tessellation.
    protected BasicQuadTree<Record> recordTree;
    protected ArrayList<ShapefileTile> topLevelTiles = new ArrayList<>();
    protected ArrayList<ShapefileTile> currentTiles = new ArrayList<>();
    protected ShapefileTile currentAncestorTile;
    protected PriorityQueue<Runnable> requestQueue = new PriorityQueue<>();
    protected MemoryCache cache = WorldWind.getMemoryCache(ShapefileGeometry.class.getName());
    protected long recordStateID;
    // Properties supporting picking and rendering.
    protected PickSupport pickSupport = new PickSupport();
    protected HashMap<Integer, Color> pickColorMap = new HashMap<>();
    protected SurfaceObjectTileBuilder pickTileBuilder = new SurfaceObjectTileBuilder(new Dimension(512, 512),
        GL2.GL_RGBA8, false, false);
    protected ByteBuffer pickColors;
    protected Layer layer;
    protected double[] matrixArray = new double[16];
    protected double[] baseMatrixArray = new double[16]; // cached SurfaceTileDrawContext modelview (same for all tiles in FBO)
    protected Object lastSdc; // tracks which SurfaceTileDrawContext was cached
    protected double[] clipPlaneArray = new double[16];
    // Shader pipeline for merged rendering
    protected ShaderProgram shapeShader;
    protected boolean shaderInitAttempted;
    protected float[] projectionArray = new float[16]; // cached projection matrix (same for all tiles in FBO)
    protected float[] mvpArray = new float[16]; // modelview-projection for shader uniform
    // Bounding extent cache — avoids recomputing Sector.computeBoundingBox() every frame for static tile sectors.
    // Invalidated when globe or vertical exaggeration changes.
    protected HashMap<Sector, Extent> extentCache = new HashMap<>();
    protected Object extentCacheGlobe;
    protected double extentCacheVertExag = Double.NaN;

    protected static final String MERGED_VERTEX_SHADER =
        "#version 330\n" +
        "layout(location = 0) in vec2 aPosition;\n" +
        "layout(location = 1) in vec4 aColor;\n" +
        "uniform mat4 uMVP;\n" +
        "out vec4 vColor;\n" +
        "void main() {\n" +
        "    gl_Position = uMVP * vec4(aPosition, 0.0, 1.0);\n" +
        "    vColor = aColor;\n" +
        "}\n";

    protected static final String MERGED_FRAGMENT_SHADER =
        "#version 330\n" +
        "in vec4 vColor;\n" +
        "out vec4 fragColor;\n" +
        "void main() {\n" +
        "    fragColor = vColor;\n" +
        "}\n";

    /**
     * Creates a new ShapefilePolygons with the specified shapefile. The normal attributes and the highlight attributes
     * for each ShapefileRenderable.Record are assigned default values. In order to modify ShapefileRenderable.Record
     * shape attributes or key-value attributes during construction, use {@link #ShapefilePolygons(gov.nasa.worldwind.formats.shapefile.Shapefile,
     * gov.nasa.worldwind.render.ShapeAttributes, gov.nasa.worldwind.render.ShapeAttributes,
     * gov.nasa.worldwind.formats.shapefile.ShapefileRenderable.AttributeDelegate)}.
     *
     * @param shapefile The shapefile to display.
     *
     * @throws IllegalArgumentException if the shapefile is null.
     */
    public ShapefilePolygons(Shapefile shapefile)
    {
        if (shapefile == null)
        {
            String msg = Logging.getMessage("nullValue.ShapefileIsNull");
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        this.init(shapefile, null, null, null);
    }

    /**
     * Creates a new ShapefilePolygons with the specified shapefile. The normal attributes, the highlight attributes and
     * the attribute delegate are optional. Specifying a non-null value for normalAttrs or highlightAttrs causes each
     * ShapefileRenderable.Record to adopt those attributes. Specifying a non-null value for the attribute delegate
     * enables callbacks during creation of each ShapefileRenderable.Record. See {@link
     * gov.nasa.worldwind.formats.shapefile.ShapefileRenderable.AttributeDelegate} for more information.
     *
     * @param shapefile         The shapefile to display.
     * @param normalAttrs       The normal attributes for each ShapefileRenderable.Record. May be null to use the
     *                          default attributes.
     * @param highlightAttrs    The highlight attributes for each ShapefileRenderable.Record. May be null to use the
     *                          default highlight attributes.
     * @param attributeDelegate Optional callback for configuring each ShapefileRenderable.Record's shape attributes and
     *                          key-value attributes. May be null.
     *
     * @throws IllegalArgumentException if the shapefile is null.
     */
    public ShapefilePolygons(Shapefile shapefile, ShapeAttributes normalAttrs, ShapeAttributes highlightAttrs,
        AttributeDelegate attributeDelegate)
    {
        if (shapefile == null)
        {
            String msg = Logging.getMessage("nullValue.ShapefileIsNull");
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        this.init(shapefile, normalAttrs, highlightAttrs, attributeDelegate);
    }

    @Override
    protected void assembleRecords(Shapefile shapefile)
    {
        // Store the shapefile records in a quad tree with eight levels. This depth provides fast access to records in
        // regions much smaller than the shapefile's sector while avoiding a lot of overhead in building the quad tree.
        this.recordTree = new BasicQuadTree<>(8, this.sector, null);
        super.assembleRecords(shapefile);
    }

    @Override
    protected boolean mustAssembleRecord(ShapefileRecord shapefileRecord)
    {
        return super.mustAssembleRecord(shapefileRecord)
            && (shapefileRecord.isPolylineRecord()
            || shapefileRecord.isPolygonRecord()); // accept both polyline and polygon records
    }

    @Override
    protected void assembleRecord(ShapefileRecord shapefileRecord)
    {
        ShapefilePolygons.Record record = this.createRecord(shapefileRecord);
        this.addRecord(shapefileRecord, record);
        this.recordTree.add(record, record.sector.asDegreesArray());
    }

    @Override
    protected void recordDidChange(ShapefileRenderable.Record record)
    {
        this.recordStateID++;
    }

    protected ShapefilePolygons.Record createRecord(ShapefileRecord shapefileRecord)
    {
        return new ShapefilePolygons.Record(this, shapefileRecord);
    }

    /**
     * Indicates the object's detail hint, which is described in {@link #setDetailHint(double)}.
     *
     * @return the detail hint
     *
     * @see #setDetailHint(double)
     */
    public double getDetailHint()
    {
        return this.detailHint;
    }

    /**
     * Modifies the default relationship of shape resolution to screen resolution as the viewing altitude changes.
     * Values greater than 0 cause shape detail to appear at higher resolution at greater altitudes than normal, but at
     * an increased performance cost. Values less than 0 decrease the default resolution at any given altitude. The
     * default value is 0. Values typically range between -0.5 and 0.5.
     * <p>
     * Note: The resolution-to-height relationship is defined by a scale factor that specifies the approximate size of
     * discernible lengths in the shape relative to eye distance. The scale is specified as a power of 10. A value of 3,
     * for example, specifies that a length of 1 meter on the shape should be distinguishable from an altitude of 10^3
     * meters (1000 meters). The default scale is 1/10^2.8, (1 over 10 raised to the power 2.8). The detail hint
     * specifies deviations from that default. A detail hint of 0.2 specifies a scale of 1/1000, i.e., 1/10^(2.8 + .2) =
     * 1/10^3. Scales much larger than 3 typically cause the applied resolution to be higher than discernible for the
     * altitude. Such scales significantly decrease performance.
     *
     * @param detailHint the degree to modify the default relationship of shape resolution to screen resolution with
     *                   changing view altitudes. Values greater than 1 increase the resolution. Values less than zero
     *                   decrease the resolution. The default value is 0.
     */
    public void setDetailHint(double detailHint)
    {
        this.detailHint = detailHint;
    }

    protected double getDetailFactor()
    {
        return this.detailHintOrigin + this.getDetailHint();
    }

    /**
     * Indicates the outline line width to use during picking. A larger width than normal typically makes the outline
     * easier to pick.
     *
     * @return the outline line width used during picking.
     */
    public int getOutlinePickWidth()
    {
        return this.outlinePickWidth;
    }

    /**
     * Specifies the outline line width to use during picking. A larger width than normal typically makes the outline
     * easier to pick.
     * <p>
     * Note that the size of the pick aperture also affects the precision necessary to pick.
     *
     * @param outlinePickWidth the outline pick width. The default is 10.
     *
     * @throws IllegalArgumentException if the width is less than 0.
     */
    public void setOutlinePickWidth(int outlinePickWidth)
    {
        if (outlinePickWidth < 0)
        {
            String message = Logging.getMessage("generic.ArgumentOutOfRange", "width < 0");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.outlinePickWidth = outlinePickWidth;
    }

    @Override
    public double getDistanceFromEye()
    {
        return 0; // ordered surface renderables don't use eye distance
    }

    @Override
    public void preRender(DrawContext dc)
    {
        if (dc == null)
        {
            String msg = Logging.getMessage("nullValue.DrawContextIsNull");
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        if (!this.visible || (this.getRecordCount() == 0)) // shapefile is empty or contains only null records
            return;

        Extent extent = this.getOrComputeExtent(dc, this.sector);

        if (!dc.getView().getFrustumInModelCoordinates().intersects(extent))
            return;

        if (dc.isSmall(extent, 1))
            return;

        this.layer = dc.getCurrentLayer();

        // Assemble the tiles used for rendering, then add those tiles to the scene controller's list of renderables to
        // draw into the scene's shared surface tiles.
        this.assembleTiles(dc);
        for (ShapefileTile tile : this.currentTiles)
        {
            dc.addOrderedSurfaceRenderable(tile);
        }

        // Assemble the tiles used for picking, then build a set of surface object tiles containing unique colors for
        // each record.
        if (dc.getCurrentLayer().isPickEnabled())
        {
            try
            {
                // Setup the draw context state and GL state for creating pick tiles.
                dc.enablePickingMode();
                this.pickSupport.beginPicking(dc);
                // Assemble the tiles intersecting the pick frustums, then draw them with unique pick colors.
                this.assembleTiles(dc);
                this.pickTileBuilder.setForceTileUpdates(true);
                this.pickTileBuilder.buildTiles(dc, this.currentTiles);
            }
            finally
            {
                // Clear pick color map in order to use different pick colors for each globe.
                this.pickColorMap.clear();
                // Restore the draw context state and GL state.
                this.pickSupport.endPicking(dc);
                dc.disablePickingMode();
            }
        }

        // Send requests for tile geometry.
        this.sendRequests();
    }

    @Override
    public void pick(DrawContext dc, Point pickPoint)
    {
        if (dc == null)
        {
            String msg = Logging.getMessage("nullValue.DrawContextIsNull");
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        if (!this.visible || (this.getRecordCount() == 0)) // shapefile is empty or contains only null records
            return;

        GL2 gl = dc.getGL().getGL2(); // GL initialization checks for GL2 compatibility.

        try
        {
            this.pickSupport.beginPicking(dc);
            gl.glEnable(GL.GL_CULL_FACE);
            dc.getGeographicSurfaceTileRenderer().setUseImageTilePickColors(true);
            dc.getGeographicSurfaceTileRenderer().renderTiles(dc, this.pickTileBuilder.getTiles(dc));

            for (PickedObject po : this.pickTileBuilder.getPickCandidates(dc))
            {
                this.pickSupport.addPickableObject(po); // transfer picked objects captured during pre rendering
            }
        }
        finally
        {
            dc.getGeographicSurfaceTileRenderer().setUseImageTilePickColors(false);
            gl.glDisable(GL.GL_CULL_FACE);
            this.pickSupport.endPicking(dc);
            this.pickSupport.resolvePick(dc, pickPoint, this.layer);
            this.pickTileBuilder.clearTiles(dc);
            this.pickTileBuilder.clearPickCandidates(dc);
        }
    }

    @Override
    public void render(DrawContext dc)
    {
        if (dc == null)
        {
            String msg = Logging.getMessage("nullValue.DrawContextIsNull");
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        if (!this.visible || (this.getRecordCount() == 0)) // shapefile is empty or contains only null records
            return;

        if (dc.isPickingMode() && this.pickTileBuilder.getTileCount(dc) > 0)
        {
            dc.addOrderedSurfaceRenderable(this); // perform the pick during ordered surface rendering
        }
    }

    /** {@inheritDoc} */
    @Override
    public void combine(CombineContext cc)
    {
        if (cc == null)
        {
            String msg = Logging.getMessage("nullValue.CombineContextIsNull");
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        if (cc.isBoundingSectorMode())
            this.combineBounds(cc);
        else
            this.combineContours(cc);
    }

    protected void assembleTiles(DrawContext dc)
    {
        this.currentTiles.clear();

        if (this.topLevelTiles.size() == 0)
        {
            this.createTopLevelTiles();
        }

        for (ShapefileTile tile : this.topLevelTiles)
        {
            this.currentAncestorTile = null;

            if (this.isTileVisible(dc, tile))
            {
                this.addTileOrDescendants(dc, tile);
            }
        }
    }

    protected void createTopLevelTiles()
    {
        Angle latDelta = Angle.fromDegrees(45);
        Angle lonDelta = Angle.fromDegrees(45);
        double resolution = latDelta.radians / 512;

        int firstRow = Tile.computeRow(latDelta, this.sector.getMinLatitude(), Angle.NEG90);
        int lastRow = Tile.computeRow(latDelta, this.sector.getMaxLatitude(), Angle.NEG90);
        int firstCol = Tile.computeColumn(lonDelta, this.sector.getMinLongitude(), Angle.NEG180);
        int lastCol = Tile.computeColumn(lonDelta, this.sector.getMaxLongitude(), Angle.NEG180);

        Angle p1 = Tile.computeRowLatitude(firstRow, latDelta, Angle.NEG90);
        for (int row = firstRow; row <= lastRow; row++)
        {
            Angle p2 = p1.add(latDelta);
            Angle t1 = Tile.computeColumnLongitude(firstCol, lonDelta, Angle.NEG180);
            for (int col = firstCol; col <= lastCol; col++)
            {
                Angle t2 = t1.add(lonDelta);
                this.topLevelTiles.add(new ShapefileTile(this, new Sector(p1, p2, t1, t2), resolution));
                t1 = t2;
            }
            p1 = p2;
        }
    }

    protected boolean isTileVisible(DrawContext dc, ShapefileTile tile)
    {
        Extent extent = this.getOrComputeExtent(dc, tile.sector);

        if (dc.isPickingMode())
        {
            return dc.getPickFrustums().intersectsAny(extent);
        }

        return dc.getView().getFrustumInModelCoordinates().intersects(extent);
    }

    protected Extent getOrComputeExtent(DrawContext dc, Sector sector)
    {
        // Invalidate cache if globe or vertical exaggeration changed
        Object globe = dc.getGlobe();
        double vertExag = dc.getVerticalExaggeration();
        if (globe != this.extentCacheGlobe || vertExag != this.extentCacheVertExag)
        {
            this.extentCache.clear();
            this.extentCacheGlobe = globe;
            this.extentCacheVertExag = vertExag;
        }

        Extent extent = this.extentCache.get(sector);
        if (extent == null)
        {
            extent = Sector.computeBoundingBox(dc.getGlobe(), vertExag, sector);
            this.extentCache.put(sector, extent);
        }
        return extent;
    }

    protected void initShader(GL2 gl)
    {
        this.shaderInitAttempted = true;
        this.shapeShader = new ShaderProgram();
        if (!this.shapeShader.init(gl, MERGED_VERTEX_SHADER, MERGED_FRAGMENT_SHADER))
        {
            Logging.logger().warning("ShapefilePolygons: shader init failed, using fixed-function fallback");
            this.shapeShader = null;
        }
    }

    /**
     * Multiply two 4x4 matrices in column-major order: result = a * b.
     * All arrays must be length >= 16.
     */
    protected static void multiplyMatrices4x4(float[] a, double[] b, float[] result)
    {
        for (int col = 0; col < 4; col++)
        {
            for (int row = 0; row < 4; row++)
            {
                result[col * 4 + row] = (float) (
                    a[row]      * b[col * 4]     +
                    a[4 + row]  * b[col * 4 + 1] +
                    a[8 + row]  * b[col * 4 + 2] +
                    a[12 + row] * b[col * 4 + 3]);
            }
        }
    }

    protected void addTileOrDescendants(DrawContext dc, ShapefileTile tile)
    {
        ShapefileGeometry geom = this.lookupGeometry(tile);
        tile.setGeometry(geom); // may be null

        if (this.meetsRenderCriteria(dc, tile))
        {
            this.addTile(dc, tile);
            return;
        }

        ShapefileTile previousAncestorTile = null;
        try
        {
            if (tile.getGeometry() != null)
            {
                previousAncestorTile = this.currentAncestorTile;
                this.currentAncestorTile = tile;
            }

            ShapefileTile[] children = tile.subdivide();
            for (ShapefileTile child : children)
            {
                if (child.sector.intersects(this.sector) && this.isTileVisible(dc, child))
                {
                    this.addTileOrDescendants(dc, child);
                }
            }
        }
        finally
        {
            if (previousAncestorTile != null)
            {
                this.currentAncestorTile = previousAncestorTile;
            }
        }
    }

    protected void addTile(DrawContext dc, ShapefileTile tile)
    {
        if (tile.getGeometry() == null)
        {
            this.requestGeometry(dc, tile); // request the tile's geometry

            if (this.currentAncestorTile != null) // try to use the ancestor's geometry
            {
                tile.setGeometry(this.currentAncestorTile.getGeometry());
            }
        }

        if ((tile.getGeometry() == null) || (tile.getGeometry().vertexCount == 0)) // don't use empty geometry
            return;

        // Dispatch any pending GPU triangulation on the render thread before attribute assembly.
        // Background thread may have deferred triangulation to here (GPU compute shader path).
        if (tile.getGeometry().gpuPendingData != null)
            this.dispatchGpuTriangulation(dc, tile.getGeometry());

        if (this.mustAssembleAttributeGroups(
            tile.getGeometry())) // build geometry attribute groups on the rendering thread
        {
            this.assembleAttributeGroups(tile.getGeometry());
        }

        this.currentTiles.add(tile);
    }

    protected boolean meetsRenderCriteria(DrawContext dc, ShapefileTile tile)
    {
        return !this.needToSplit(dc, tile);
    }

    protected boolean needToSplit(DrawContext dc, ShapefileTile tile)
    {
        // Compute the resolution in meters of the specified tile. Take care to convert from the radians to meters by
        // multiplying by the globe's radius, not the length of a Cartesian point. Using the length of a Cartesian point
        // is incorrect when the globe is flat.
        double resolutionRadians = tile.resolution;
        double resolutionMeters = dc.getGlobe().getRadius() * resolutionRadians;

        // Compute the level of detail scale and the field of view scale. These scales are multiplied by the eye
        // distance to derive a scaled distance that is then compared to the resolution. The level of detail scale is
        // specified as a power of 10. For example, a detail factor of 3 means split when the resolution becomes more
        // than one thousandth of the eye distance. The field of view scale is specified as a ratio between the current
        // field of view and a the default field of view. In a perspective projection, decreasing the field of view by
        // 50% has the same effect on object size as decreasing the distance between the eye and the object by 50%.
        double detailScale = Math.pow(10, -this.getDetailFactor());
        double fieldOfViewScale = dc.getView().getFieldOfView().tanHalfAngle() / Angle.fromDegrees(45).tanHalfAngle();
        fieldOfViewScale = WWMath.clamp(fieldOfViewScale, 0, 1);

        // Compute the distance between the eye point and the sector in meters, and compute a fraction of that distance
        // by multiplying the actual distance by the level of detail scale and the field of view scale.
        double eyeDistanceMeters = tile.sector.distanceTo(dc, dc.getView().getEyePoint());
        double scaledEyeDistanceMeters = eyeDistanceMeters * detailScale * fieldOfViewScale;

        // Split when the resolution in meters becomes greater than the specified fraction of the eye distance, also in
        // meters. Another way to say it is, use the current tile if its texel size is less than the specified fraction
        // of the eye distance.
        //
        // NOTE: It's tempting to instead compare a screen pixel size to the resolution, but that calculation is
        // window-size dependent and results in selecting an excessive number of tiles when the window is large.
        return resolutionMeters > scaledEyeDistanceMeters;
    }

    protected ShapefileGeometry lookupGeometry(ShapefileTile tile)
    {
        return (ShapefileGeometry) this.cache.getObject(tile); // corresponds to the key used in requestGeometry
    }

    protected void requestGeometry(DrawContext dc, ShapefileTile tile)
    {
        Vec4 eyePoint = dc.getView().getEyePoint();
        Vec4 centroid = tile.sector.computeCenterPoint(dc.getGlobe(), dc.getVerticalExaggeration());

        ShapefileGeometry geom = new ShapefileGeometry(tile.shape, tile.sector, tile.resolution);
        geom.memoryCache = this.cache;
        geom.memoryCacheKey = tile; // corresponds to the key used in lookupGeometry
        geom.listener = this.layer;
        geom.priority = eyePoint.distanceTo3(centroid);

        this.requestQueue.offer(geom);
    }

    protected void sendRequests()
    {
        Runnable request;
        while ((request = this.requestQueue.poll()) != null)
        {
            if (WorldWind.getTaskService().isFull())
                break;

            WorldWind.getTaskService().addTask(request);
        }

        this.requestQueue.clear(); // clear any remaining requests
    }

    protected void tessellate(ShapefileGeometry geom)
    {
        // Get the records intersecting the geometry's sector.
        Set<Record> intersectingRecords = this.recordTree.getItemsInRegion(geom.sector, null);
        if (intersectingRecords.isEmpty())
            return;

        double minEffectiveArea = 4 * geom.resolution * geom.resolution;
        double xOffset = geom.sector.getCentroid().longitude.degrees;
        double yOffset = geom.sector.getCentroid().latitude.degrees;

        PolylineGeneralizer generalizer = new PolylineGeneralizer();

        // Filter records that intersect and meet resolution criteria
        ArrayList<Record> validRecords = new ArrayList<>();
        for (Record record : intersectingRecords)
        {
            if (!record.sector.intersects(geom.sector))
                continue;
            double effectiveArea = record.sector.getDeltaLatRadians() * record.sector.getDeltaLonRadians();
            if (effectiveArea < minEffectiveArea)
                continue;
            this.computeRecordMetrics(record, generalizer);
            validRecords.add(record);
        }

        // Try ear-clipping path first; fall back to GLU on failure
        if (validRecords.isEmpty() || (!forceDisableEarClipping && this.tessellateEarClipping(geom, validRecords, xOffset, yOffset)))
            return;

        // Fallback: GLU tessellation (original path)
        this.tessellateGLU(geom, validRecords, xOffset, yOffset);
    }

    /**
     * Ear-clipping tessellation path. Collects polygon contours, merges holes via bridge edges,
     * and triangulates using CPU ear-clipping (results available immediately) or prepares data
     * for GPU compute dispatch on the render thread.
     *
     * @return true if tessellation succeeded (or GPU data was prepared), false to fall back to GLU
     */
    protected boolean tessellateEarClipping(ShapefileGeometry geom, List<Record> records,
                                             double xOffset, double yOffset)
    {
        double resolutionDegrees = geom.resolution * 180.0 / Math.PI;
        double minEffectiveArea = resolutionDegrees * resolutionDegrees;

        // Fall back to GLU for tiles near the antimeridian — rare, but GLU handles it correctly
        for (Record record : records)
        {
            for (int b = 0; b < record.getBoundaryCount(); b++)
            {
                if (record.isBoundaryCrossesAntimeridian(b))
                    return false;
            }
        }

        // Phase 1: Collect all polygon vertices and contour info
        // Accumulate vertices into a growable float list (lon+offset, lat+offset pairs)
        ArrayList<Float> vertexList = new ArrayList<>(1024);

        // Per-record data
        ArrayList<Integer> recOrdinals = new ArrayList<>();
        ArrayList<int[]> recVertexRanges = new ArrayList<>(); // [startVertex, count]
        ArrayList<int[]> recRingIndices = new ArrayList<>();   // merged ring indices
        ArrayList<int[]> recOutlineIndices = new ArrayList<>(); // outline indices

        for (Record record : records)
        {
            int recordVertStart = vertexList.size() / 2;

            // Collect vertices for each boundary (outer ring + holes)
            ArrayList<Integer> contourStarts = new ArrayList<>();
            ArrayList<Integer> contourCounts = new ArrayList<>();

            for (int b = 0; b < record.getBoundaryCount(); b++)
            {
                int contourStart = vertexList.size() / 2;
                contourStarts.add(contourStart);

                this.collectBoundaryVertices(record, b, minEffectiveArea, vertexList,
                    xOffset, yOffset);

                int contourEnd = vertexList.size() / 2;
                contourCounts.add(contourEnd - contourStart);
            }

            int totalVerts = vertexList.size() / 2 - recordVertStart;
            if (totalVerts < 3)
                continue;

            // Convert to flat float array for bridge/triangulation
            float[] allVerts = toFloatArray(vertexList);

            // Bridge holes into outer ring
            int outerStart = contourStarts.get(0);
            int outerCount = contourCounts.get(0);
            int[] holeStarts = null;
            int[] holeCounts = null;

            if (contourStarts.size() > 1)
            {
                holeStarts = new int[contourStarts.size() - 1];
                holeCounts = new int[contourStarts.size() - 1];
                for (int h = 1; h < contourStarts.size(); h++)
                {
                    holeStarts[h - 1] = contourStarts.get(h);
                    holeCounts[h - 1] = contourCounts.get(h);
                }
            }

            int[] mergedRing;
            try
            {
                mergedRing = GpuTriangulator.bridgeHoles(allVerts, outerStart, outerCount,
                    holeStarts, holeCounts);
            }
            catch (Exception e)
            {
                return false; // bridge failed, fall back to GLU
            }

            if (mergedRing.length < 3)
                continue;

            // Compute outline indices from original contours
            ArrayList<Integer> outlineList = new ArrayList<>();
            for (int b = 0; b < contourStarts.size(); b++)
            {
                int cs = contourStarts.get(b);
                int cc = contourCounts.get(b);
                if (cc < 2) continue;
                int[] ol = GpuTriangulator.generateOutlineIndices(cs, cc);
                for (int idx : ol)
                    outlineList.add(idx);
            }

            recOrdinals.add(record.ordinal);
            recVertexRanges.add(new int[]{recordVertStart, totalVerts});
            recRingIndices.add(mergedRing); // store merged ring for GPU/CPU triangulation below

            int[] outlineArr = new int[outlineList.size()];
            for (int i = 0; i < outlineArr.length; i++)
                outlineArr[i] = outlineList.get(i);
            recOutlineIndices.add(outlineArr);
        }

        if (recOrdinals.isEmpty())
            return true; // no geometry, but not a failure

        // Phase 2: Build final vertex buffer
        float[] allVertices = toFloatArray(vertexList);
        int totalVertexCount = allVertices.length / 2;

        FloatBuffer vertBuf = Buffers.newDirectFloatBuffer(allVertices.length);
        vertBuf.put(allVertices).rewind();
        geom.vertices = vertBuf;
        geom.vertexStride = 2;
        geom.vertexCount = totalVertexCount;
        geom.vertexOffset = new Vec4(xOffset, yOffset, 0);

        // GPU fast path: pack merged ring data for dispatch on the render thread.
        // The render thread will call dispatchGpuTriangulation() to run the compute shader
        // and populate geom.recordIndices. Falls back to CPU path if GPU unavailable.
        if (GpuTriangulator.isGpuViable())
        {
            GpuTessellationData pending = new GpuTessellationData();
            pending.vertices = allVertices;
            pending.vertexCount = totalVertexCount;
            pending.vertexStride = 2;
            pending.vertexOffset = geom.vertexOffset;
            pending.numPolygons = recOrdinals.size();
            pending.recordOrdinals = recOrdinals.stream().mapToInt(x -> x).toArray();
            pending.vertexRanges = recVertexRanges.toArray(new int[0][]);
            pending.outlineIndices = recOutlineIndices.toArray(new int[0][]);

            // Pack all polygon ring indices into a single flat array
            int totalRingInts = 0;
            for (int[] ring : recRingIndices)
                totalRingInts += ring.length;
            pending.ringIndices = new int[totalRingInts];
            pending.ringOffsets = new int[recRingIndices.size()];
            pending.ringCounts  = new int[recRingIndices.size()];
            int ringOff = 0;
            for (int i = 0; i < recRingIndices.size(); i++)
            {
                int[] ring = recRingIndices.get(i);
                pending.ringOffsets[i] = ringOff;
                pending.ringCounts[i]  = ring.length;
                System.arraycopy(ring, 0, pending.ringIndices, ringOff, ring.length);
                ringOff += ring.length;
            }

            geom.gpuPendingData = pending;
            return true;
        }

        // CPU path: triangulate immediately on the background thread
        for (int i = 0; i < recOrdinals.size(); i++)
        {
            int[] triIndices;
            try
            {
                triIndices = GpuTriangulator.triangulateCPU(allVertices, recRingIndices.get(i));
            }
            catch (Exception e)
            {
                return false; // triangulation failed, fall back to GLU
            }
            if (triIndices.length == 0)
                continue;

            RecordIndices ri = new RecordIndices(recOrdinals.get(i));
            ri.vertexRange.location = recVertexRanges.get(i)[0];
            ri.vertexRange.length = recVertexRanges.get(i)[1];
            ri.interiorIndices = IntBuffer.wrap(triIndices);
            ri.outlineIndices = IntBuffer.wrap(recOutlineIndices.get(i));
            geom.recordIndices.add(ri);
        }

        return true;
    }

    /**
     * Dispatches GPU compute-shader triangulation for pending polygon data on the render thread.
     * Populates geom.recordIndices from the GPU results, or falls back to CPU ear-clipping if
     * GL 4.3 is unavailable or the compute dispatch fails. Clears geom.gpuPendingData when done.
     * Must be called from the OpenGL render thread (DrawContext provides the GL handle).
     * seaglassfoundry.com
     */
    protected void dispatchGpuTriangulation(DrawContext dc, ShapefileGeometry geom)
    {
        GpuTessellationData pending = geom.gpuPendingData;
        if (pending == null)
            return;

        boolean gpuSuccess = false;

        if (dc.getGL().isGL4())
        {
            GL4 gl4 = dc.getGL().getGL4();
            GpuTriangulator tri = GpuTriangulator.getInstance();
            tri.initialize(dc.getGL());

            if (tri.isAvailable())
            {
                int[][] results = tri.triangulateBatch(gl4,
                    pending.vertices, pending.vertexCount,
                    pending.ringIndices, pending.ringOffsets, pending.ringCounts,
                    pending.numPolygons);

                if (results != null)
                {
                    for (int i = 0; i < pending.numPolygons; i++)
                    {
                        if (results[i] == null || results[i].length == 0)
                            continue;
                        RecordIndices ri = new RecordIndices(pending.recordOrdinals[i]);
                        ri.vertexRange.location = pending.vertexRanges[i][0];
                        ri.vertexRange.length   = pending.vertexRanges[i][1];
                        ri.interiorIndices = IntBuffer.wrap(results[i]);
                        ri.outlineIndices  = IntBuffer.wrap(pending.outlineIndices[i]);
                        geom.recordIndices.add(ri);
                    }
                    gpuSuccess = true;
                }
            }
        }

        if (!gpuSuccess)
        {
            // CPU fallback: triangulate each polygon from the pending ring data
            for (int i = 0; i < pending.numPolygons; i++)
            {
                int start = pending.ringOffsets[i];
                int count = pending.ringCounts[i];
                int[] ring = Arrays.copyOfRange(pending.ringIndices, start, start + count);

                int[] triIndices;
                try
                {
                    triIndices = GpuTriangulator.triangulateCPU(pending.vertices, ring);
                }
                catch (Exception e)
                {
                    continue;
                }
                if (triIndices.length == 0)
                    continue;

                RecordIndices ri = new RecordIndices(pending.recordOrdinals[i]);
                ri.vertexRange.location = pending.vertexRanges[i][0];
                ri.vertexRange.length   = pending.vertexRanges[i][1];
                ri.interiorIndices = IntBuffer.wrap(triIndices);
                ri.outlineIndices  = IntBuffer.wrap(pending.outlineIndices[i]);
                geom.recordIndices.add(ri);
            }
        }

        geom.gpuPendingData = null;
    }

    /** Collects filtered boundary vertices into the vertex list, applying coordinate offset. */
    protected void collectBoundaryVertices(Record record, int boundaryIndex, double minEffectiveArea,
                                            ArrayList<Float> vertexList, double xOffset, double yOffset)
    {
        // Note: antimeridian-crossing boundaries are handled by the GLU fallback path.
        // This method is only called for non-crossing boundaries.
        VecBuffer boundaryCoords = record.getBoundaryPoints(boundaryIndex);
        double[] boundaryEffectiveArea = record.getBoundaryEffectiveArea(boundaryIndex);
        double[] coord = new double[2];

        for (int j = 0; j < boundaryCoords.getSize(); j++)
        {
            if (boundaryEffectiveArea[j] < minEffectiveArea)
                continue;
            boundaryCoords.get(j, coord); // lon, lat
            vertexList.add((float) (coord[0] - xOffset)); // x = lon - offset
            vertexList.add((float) (coord[1] - yOffset)); // y = lat - offset
        }
    }

    private static float[] toFloatArray(ArrayList<Float> list)
    {
        float[] arr = new float[list.size()];
        for (int i = 0; i < arr.length; i++)
            arr[i] = list.get(i);
        return arr;
    }

    /** GLU tessellation fallback — original code path. */
    protected void tessellateGLU(ShapefileGeometry geom, List<Record> records,
                                  double xOffset, double yOffset)
    {
        PolygonTessellator2 tess = new PolygonTessellator2();
        tess.setPolygonNormal(0, 0, 1);
        tess.setPolygonClipCoords(geom.sector.getMinLongitude().degrees, geom.sector.getMaxLongitude().degrees,
            geom.sector.getMinLatitude().degrees, geom.sector.getMaxLatitude().degrees);
        tess.setVertexStride(2);
        tess.setVertexOffset(-xOffset, -yOffset, 0);

        for (Record record : records)
        {
            this.tessellateRecord(geom, record, tess);
        }

        if (tess.getVertexCount() == 0 || geom.recordIndices.size() == 0)
            return;

        FloatBuffer vertices = Buffers.newDirectFloatBuffer(2 * tess.getVertexCount());
        tess.getVertices(vertices);
        geom.vertices = vertices.rewind();
        geom.vertexStride = 2;
        geom.vertexCount = tess.getVertexCount();
        geom.vertexOffset = new Vec4(xOffset, yOffset, 0);
    }

    protected void computeRecordMetrics(Record record, PolylineGeneralizer generalizer)
    {
        synchronized (record) // synchronize access to checking and computing a record's effective area
        {
            if (record.boundaryEffectiveArea != null)
                return;

            record.boundaryEffectiveArea = new double[record.getBoundaryCount()][];
            record.boundaryCrossesAntimeridian = new boolean[record.getBoundaryCount()];

            for (int i = 0; i < record.getBoundaryCount(); i++)
            {
                VecBuffer boundaryCoords = record.getBoundaryPoints(i);
                double[] coord = new double[2]; // lon, lat
                double[] prevCoord = new double[2]; // prevlon, prevlat

                generalizer.reset();
                generalizer.beginPolyline();

                for (int j = 0; j < boundaryCoords.getSize(); j++)
                {
                    boundaryCoords.get(j, coord);
                    generalizer.addVertex(coord[0], coord[1], 0); // lon, lat, 0

                    if (j > 0 && Math.signum(prevCoord[0]) != Math.signum(coord[0]) &&
                        Math.abs(prevCoord[0] - coord[0]) > 180)
                    {
                        record.boundaryCrossesAntimeridian[i] = true;
                    }

                    prevCoord[0] = coord[0]; // prevlon = lon
                    prevCoord[1] = coord[1]; // prevlat = lat
                }

                record.boundaryEffectiveArea[i] = new double[boundaryCoords.getSize()];
                generalizer.endPolyline();
                generalizer.getVertexEffectiveArea(record.boundaryEffectiveArea[i]);
            }
        }
    }

    protected void tessellateRecord(ShapefileGeometry geom, Record record, final PolygonTessellator2 tess)
    {
        // Compute the minimum effective area for a vertex based on the geometry resolution. We convert the resolution
        // from radians to square degrees. This ensures the units are consistent with the vertex effective area computed
        // by PolylineGeneralizer, which adopts the units of the source data (degrees).
        double resolutionDegrees = geom.resolution * 180.0 / Math.PI;
        double minEffectiveArea = resolutionDegrees * resolutionDegrees;

        tess.resetIndices(); // clear indices from previous records, but retain the accumulated vertices
        tess.beginPolygon();

        for (int i = 0; i < record.getBoundaryCount(); i++)
        {
            this.tessellateBoundary(record, i, minEffectiveArea, new TessBoundaryCallback()
            {
                @Override
                public void beginBoundary()
                {
                    tess.beginContour();
                }

                @Override
                public void vertex(double degreesLatitude, double degreesLongitude)
                {
                    tess.addVertex(degreesLongitude, degreesLatitude, 0);
                }

                @Override
                public void endBoundary()
                {
                    tess.endContour();
                }
            });
        }

        tess.endPolygon();

        Range range = tess.getPolygonVertexRange();
        if (range.length == 0) // this should never happen, but we check anyway
            return;

        IntBuffer interiorIndices = IntBuffer.allocate(tess.getInteriorIndexCount());
        IntBuffer outlineIndices = IntBuffer.allocate(tess.getBoundaryIndexCount());
        tess.getInteriorIndices(interiorIndices);
        tess.getBoundaryIndices(outlineIndices);

        RecordIndices ri = new RecordIndices(record.ordinal);
        ri.vertexRange.location = range.location;
        ri.vertexRange.length = range.length;
        ri.interiorIndices = interiorIndices.rewind();
        ri.outlineIndices = outlineIndices.rewind();
        geom.recordIndices.add(ri);
    }

    protected boolean mustAssembleAttributeGroups(ShapefileGeometry geom)
    {
        return geom.attributeGroups.size() == 0 || geom.attributeStateID != this.recordStateID;
    }

    protected void assembleAttributeGroups(ShapefileGeometry geom)
    {
        geom.attributeGroups.clear();
        geom.attributeStateID = this.recordStateID;

        // Assemble the tile's records into groups with common attributes. Attributes are grouped by reference using an
        // InstanceHashMap, so that subsequent changes to an Attribute instance will be reflected in the record group
        // automatically. We take care to avoid assembling groups based on any Attribute property, as those properties
        // may change without re-assembling these groups. However, changes to a record's visibility state, highlight
        // state, normal attributes reference and highlight attributes reference invalidate this grouping.
        Map<ShapeAttributes, RecordGroup> attrMap = new IdentityHashMap<>();
        for (RecordIndices ri : geom.recordIndices)
        {
            ShapefileRenderable.Record record = this.getRecord(ri.ordinal);
            if (!record.isVisible()) // ignore records marked as not visible
                continue;

            ShapeAttributes attrs = this.determineActiveAttributes(record);
            RecordGroup group = attrMap.get(attrs);

            if (group == null) // create a new group if one doesn't already exist
            {
                group = new RecordGroup(attrs);
                attrMap.put(attrs, group); // add it to the map to prevent duplicates
                geom.attributeGroups.add(group); // add it to the tile's attribute group list
            }

            group.recordIndices.add(ri);
            group.interiorIndexRange.length += ri.interiorIndices != null ? ri.interiorIndices.remaining() : 0;
            group.outlineIndexRange.length += ri.outlineIndices != null ? ri.outlineIndices.remaining() : 0;
        }

        // Make the indices for each record group. We take care to make indices for both the interior and the outline,
        // regardless of the current state of Attributes.isDrawInterior and Attributes.isDrawOutline. This enable these
        // properties change state without needing to re-assemble these groups.
        for (RecordGroup group : geom.attributeGroups)
        {
            int indexCount = group.interiorIndexRange.length + group.outlineIndexRange.length;
            IntBuffer indices = Buffers.newDirectIntBuffer(indexCount);

            group.interiorIndexRange.location = indices.position();
            for (RecordIndices ri : group.recordIndices) // assemble the group's triangle indices in a single contiguous range
            {
                indices.put(ri.interiorIndices);
                ri.interiorIndices.rewind();
            }

            group.outlineIndexRange.location = indices.position();
            for (RecordIndices ri : group.recordIndices) // assemble the group's line indices in a single contiguous range
            {
                indices.put(ri.outlineIndices);
                ri.outlineIndices.rewind();
            }

            group.indices = indices.rewind();
            group.recordIndices.clear();
            group.recordIndices.trimToSize(); // Reduce memory overhead from unused ArrayList capacity.
        }

        // Build merged buffers for batch rendering (2 draw calls instead of 2N)
        this.assembleMergedBuffers(geom);
    }

    /**
     * Build merged index buffers and per-vertex color buffers for batch rendering. Instead of one draw call per
     * attribute group, all interiors are drawn in a single GL_TRIANGLES call and all outlines in a single GL_LINES
     * call, with per-vertex colors encoding the attribute colors.
     */
    protected void assembleMergedBuffers(ShapefileGeometry geom)
    {
        if (geom.vertexCount == 0 || geom.attributeGroups.isEmpty())
            return;

        // Count total indices
        int totalInterior = 0;
        int totalOutline = 0;
        float maxOutlineWidth = 1.0f;

        for (RecordGroup group : geom.attributeGroups)
        {
            ShapeAttributes attrs = group.attributes;
            if (attrs.isDrawInterior())
                totalInterior += group.interiorIndexRange.length;
            if (attrs.isDrawOutline())
                totalOutline += group.outlineIndexRange.length;
            maxOutlineWidth = Math.max(maxOutlineWidth, (float) attrs.getOutlineWidth());
        }

        // Build per-vertex color arrays: each vertex gets the color of the record it belongs to.
        // We need separate color arrays for interior and outline since they can have different colors/opacity.
        byte[] interiorVertexColors = new byte[geom.vertexCount * 4]; // RGBA
        byte[] outlineVertexColors = new byte[geom.vertexCount * 4];

        for (RecordIndices ri : geom.recordIndices)
        {
            ShapefileRenderable.Record record = this.getRecord(ri.ordinal);
            if (record == null || !record.isVisible())
                continue;

            ShapeAttributes attrs = this.determineActiveAttributes(record);
            Color intColor = attrs.getInteriorMaterial().getDiffuse();
            byte intA = (byte) (attrs.getInteriorOpacity() * 255 + 0.5);
            Color outColor = attrs.getOutlineMaterial().getDiffuse();
            byte outA = (byte) (attrs.getOutlineOpacity() * 255 + 0.5);

            for (int v = ri.vertexRange.location; v < ri.vertexRange.location + ri.vertexRange.length; v++)
            {
                int off = v * 4;
                if (off + 3 < interiorVertexColors.length)
                {
                    interiorVertexColors[off] = (byte) intColor.getRed();
                    interiorVertexColors[off + 1] = (byte) intColor.getGreen();
                    interiorVertexColors[off + 2] = (byte) intColor.getBlue();
                    interiorVertexColors[off + 3] = intA;

                    outlineVertexColors[off] = (byte) outColor.getRed();
                    outlineVertexColors[off + 1] = (byte) outColor.getGreen();
                    outlineVertexColors[off + 2] = (byte) outColor.getBlue();
                    outlineVertexColors[off + 3] = outA;
                }
            }
        }

        // Build merged index buffers — use GL_UNSIGNED_SHORT when vertex count allows (halves index bandwidth)
        boolean useShort = geom.vertexCount <= 65535;
        geom.mergedIndexType = useShort ? GL.GL_UNSIGNED_SHORT : GL.GL_UNSIGNED_INT;

        if (totalInterior > 0)
        {
            if (useShort)
            {
                ShortBuffer mergedInt = Buffers.newDirectShortBuffer(totalInterior);
                for (RecordGroup group : geom.attributeGroups)
                {
                    if (!group.attributes.isDrawInterior() || group.interiorIndexRange.length == 0)
                        continue;
                    group.indices.position(group.interiorIndexRange.location);
                    for (int i = 0; i < group.interiorIndexRange.length; i++)
                        mergedInt.put((short) group.indices.get());
                    group.indices.rewind();
                }
                geom.mergedInteriorIndices = mergedInt.flip();
            }
            else
            {
                IntBuffer mergedInt = Buffers.newDirectIntBuffer(totalInterior);
                for (RecordGroup group : geom.attributeGroups)
                {
                    if (!group.attributes.isDrawInterior() || group.interiorIndexRange.length == 0)
                        continue;
                    group.indices.position(group.interiorIndexRange.location);
                    for (int i = 0; i < group.interiorIndexRange.length; i++)
                        mergedInt.put(group.indices.get());
                    group.indices.rewind();
                }
                geom.mergedInteriorIndices = mergedInt.flip();
            }
            geom.mergedInteriorCount = totalInterior;
        }

        if (totalOutline > 0)
        {
            if (useShort)
            {
                ShortBuffer mergedOut = Buffers.newDirectShortBuffer(totalOutline);
                for (RecordGroup group : geom.attributeGroups)
                {
                    if (!group.attributes.isDrawOutline() || group.outlineIndexRange.length == 0)
                        continue;
                    group.indices.position(group.outlineIndexRange.location);
                    for (int i = 0; i < group.outlineIndexRange.length; i++)
                        mergedOut.put((short) group.indices.get());
                    group.indices.rewind();
                }
                geom.mergedOutlineIndices = mergedOut.flip();
            }
            else
            {
                IntBuffer mergedOut = Buffers.newDirectIntBuffer(totalOutline);
                for (RecordGroup group : geom.attributeGroups)
                {
                    if (!group.attributes.isDrawOutline() || group.outlineIndexRange.length == 0)
                        continue;
                    group.indices.position(group.outlineIndexRange.location);
                    for (int i = 0; i < group.outlineIndexRange.length; i++)
                        mergedOut.put(group.indices.get());
                    group.indices.rewind();
                }
                geom.mergedOutlineIndices = mergedOut.flip();
            }
            geom.mergedOutlineCount = totalOutline;
        }

        geom.mergedOutlineWidth = maxOutlineWidth;

        // Wrap colors into direct ByteBuffers
        geom.mergedInteriorColors = Buffers.newDirectByteBuffer(interiorVertexColors.length);
        geom.mergedInteriorColors.put(interiorVertexColors).flip();
        geom.mergedOutlineColors = Buffers.newDirectByteBuffer(outlineVertexColors.length);
        geom.mergedOutlineColors.put(outlineVertexColors).flip();

        geom.mergedStateID = geom.attributeStateID;

        // Invalidate VBO keys so new VBOs are created
        geom.mergedInteriorVboKey = new Object();
        geom.mergedOutlineVboKey = new Object();
        geom.mergedInteriorColorVboKey = new Object();
        geom.mergedOutlineColorVboKey = new Object();
    }

    protected void render(DrawContext dc, ShapefileTile tile)
    {
        try
        {
            this.beginDrawing(dc);
            this.draw(dc, tile);
        }
        finally
        {
            this.endDrawing(dc);
        }
    }

    protected void beginDrawing(DrawContext dc)
    {
        GL2 gl = dc.getGL().getGL2(); // GL initialization checks for GL2 compatibility.
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glEnableClientState(GLPointerFunc.GL_VERTEX_ARRAY); // all drawing uses vertex arrays
        gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
        gl.glPushMatrix();

        if (!dc.isPickingMode())
        {
            gl.glEnable(GL.GL_BLEND);
            gl.glEnable(GL.GL_LINE_SMOOTH);
            gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        }

        // Initialize shader on first use; cache projection matrix for the current FBO
        if (!this.shaderInitAttempted)
            this.initShader(gl);
        if (this.shapeShader != null && this.shapeShader.isValid())
            gl.glGetFloatv(GLMatrixFunc.GL_PROJECTION_MATRIX, this.projectionArray, 0);
    }

    protected void endDrawing(DrawContext dc)
    {
        GL2 gl = dc.getGL().getGL2(); // GL initialization checks for GL2 compatibility.
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDisableClientState(GLPointerFunc.GL_VERTEX_ARRAY);
        gl.glDisableClientState(GLPointerFunc.GL_COLOR_ARRAY);
        gl.glColor4f(1, 1, 1, 1);
        gl.glLineWidth(1);
        gl.glPopMatrix();

        // Unbind any VBOs
        if (dc.getGLRuntimeCapabilities().isUseVertexBufferObject())
        {
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
        }

        // Disable scissor clipping (replaces legacy clip planes)
        gl.glDisable(GL.GL_SCISSOR_TEST);

        if (!dc.isPickingMode())
        {
            gl.glDisable(GL.GL_BLEND);
            gl.glDisable(GL.GL_LINE_SMOOTH);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ZERO);
        }
    }

    protected void draw(DrawContext dc, ShapefileTile tile)
    {
        GL2 gl = dc.getGL().getGL2();
        ShapefileGeometry geom = tile.getGeometry();
        boolean useVbo = dc.getGLRuntimeCapabilities().isUseVertexBufferObject() && !forceDisableVBO;

        // Cache the SurfaceTileDrawContext base matrix — it's the same for all tiles in one FBO pass.
        // Apply per-tile vertex offset translation directly in the array (avoids 2 Matrix allocations per tile).
        SurfaceTileDrawContext sdc = (SurfaceTileDrawContext) dc.getValue(AVKey.SURFACE_TILE_DRAW_CONTEXT);
        if (sdc != this.lastSdc)
        {
            sdc.getModelviewMatrix().toArray(this.baseMatrixArray, 0, false);
            this.lastSdc = sdc;
        }
        // M' = M * T(tx,ty,tz): copy base matrix, then update column 3 with translation offset
        System.arraycopy(this.baseMatrixArray, 0, this.matrixArray, 0, 16);
        double tx = geom.vertexOffset.x, ty = geom.vertexOffset.y, tz = geom.vertexOffset.z;
        this.matrixArray[12] += this.matrixArray[0] * tx + this.matrixArray[4] * ty + this.matrixArray[8] * tz;
        this.matrixArray[13] += this.matrixArray[1] * tx + this.matrixArray[5] * ty + this.matrixArray[9] * tz;
        this.matrixArray[14] += this.matrixArray[2] * tx + this.matrixArray[6] * ty + this.matrixArray[10] * tz;
        this.matrixArray[15] += this.matrixArray[3] * tx + this.matrixArray[7] * ty + this.matrixArray[11] * tz;
        gl.glLoadMatrixd(this.matrixArray, 0);

        // Bind vertex data — use VBO when available for zero per-frame CPU→GPU transfer
        if (useVbo)
        {
            int[] vboId = (int[]) dc.getGpuResourceCache().get(geom.vertexVboKey);
            if (vboId == null)
            {
                vboId = new int[1];
                gl.glGenBuffers(1, vboId, 0);
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboId[0]);
                gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) geom.vertices.remaining() * Float.BYTES,
                    geom.vertices, GL.GL_STATIC_DRAW);
                dc.getGpuResourceCache().put(geom.vertexVboKey, vboId,
                    GpuResourceCache.VBO_BUFFERS, (long) geom.vertices.remaining() * Float.BYTES);
            }
            else
            {
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboId[0]);
            }
            gl.glVertexPointer(geom.vertexStride, GL.GL_FLOAT, 0, 0);
        }
        else
        {
            gl.glVertexPointer(geom.vertexStride, GL.GL_FLOAT, 0, geom.vertices);
        }

        this.applyClipSector(dc, tile.sector, geom.vertexOffset);

        if (dc.isPickingMode())
        {
            // glColorPointer with a client-side pointer requires GL_ARRAY_BUFFER to be unbound
            if (useVbo)
                dc.getGL().getGL2().glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
            this.applyPickColors(dc, geom);
            // Picking mode: fall back to per-group rendering (needs unique pick colors per record)
            for (RecordGroup attrGroup : geom.attributeGroups)
                this.drawAttributeGroup(dc, attrGroup, useVbo);
        }
        else if (!forceDisableMerged && (geom.mergedInteriorIndices != null || geom.mergedOutlineIndices != null))
        {
            // Merged rendering: 2 draw calls for the entire tile
            this.drawMerged(dc, geom, useVbo);
        }
        else
        {
            // Fallback: per-group rendering
            for (RecordGroup attrGroup : geom.attributeGroups)
                this.drawAttributeGroup(dc, attrGroup, useVbo);
        }

        if (useVbo)
        {
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
        }
    }

    /**
     * Draw all interior and outline geometry in 2 draw calls using per-vertex colors.
     * Reduces 2N draw calls (N attribute groups) to exactly 2.
     */
    protected void drawMerged(DrawContext dc, ShapefileGeometry geom, boolean useVbo)
    {
        boolean hasInterior = geom.mergedInteriorIndices != null && geom.mergedInteriorCount > 0;
        boolean hasOutline = geom.mergedOutlineIndices != null && geom.mergedOutlineCount > 0;
        int idxType = geom.mergedIndexType;
        int idxBytes = idxType == GL.GL_UNSIGNED_SHORT ? Short.BYTES : Integer.BYTES;

        // Use shader pipeline when available, fixed-function as fallback
        boolean useShader = !forceDisableShader && this.shapeShader != null && this.shapeShader.isValid();

        if (useShader)
        {
            this.drawMergedShader(dc, geom, useVbo, hasInterior, hasOutline, idxType, idxBytes);
        }
        else
        {
            this.drawMergedFixedFunction(dc, geom, useVbo, hasInterior, hasOutline, idxType, idxBytes);
        }
    }

    protected void drawMergedFixedFunction(DrawContext dc, ShapefileGeometry geom, boolean useVbo,
                                            boolean hasInterior, boolean hasOutline, int idxType, int idxBytes)
    {
        GL2 gl = dc.getGL().getGL2();
        GpuResourceCache gpuCache = dc.getGpuResourceCache();

        gl.glEnableClientState(GLPointerFunc.GL_COLOR_ARRAY);

        if (useVbo)
        {
            int[] vertVbo = (int[]) gpuCache.get(geom.vertexVboKey);

            if (hasInterior)
            {
                bindOrCreateVbo(gl, gpuCache, geom.mergedInteriorColorVboKey,
                    GL.GL_ARRAY_BUFFER, geom.mergedInteriorColors, geom.mergedInteriorColors.remaining());
                gl.glColorPointer(4, GL.GL_UNSIGNED_BYTE, 0, 0);

                if (vertVbo != null)
                {
                    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vertVbo[0]);
                    gl.glVertexPointer(geom.vertexStride, GL.GL_FLOAT, 0, 0);
                }

                bindOrCreateVbo(gl, gpuCache, geom.mergedInteriorVboKey,
                    GL.GL_ELEMENT_ARRAY_BUFFER, geom.mergedInteriorIndices,
                    (long) geom.mergedInteriorCount * idxBytes);
                gl.glDrawElements(GL.GL_TRIANGLES, geom.mergedInteriorCount, idxType, 0);
            }

            if (hasOutline)
            {
                gl.glLineWidth(geom.mergedOutlineWidth);

                bindOrCreateVbo(gl, gpuCache, geom.mergedOutlineColorVboKey,
                    GL.GL_ARRAY_BUFFER, geom.mergedOutlineColors, geom.mergedOutlineColors.remaining());
                gl.glColorPointer(4, GL.GL_UNSIGNED_BYTE, 0, 0);

                if (vertVbo != null)
                {
                    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vertVbo[0]);
                    gl.glVertexPointer(geom.vertexStride, GL.GL_FLOAT, 0, 0);
                }

                bindOrCreateVbo(gl, gpuCache, geom.mergedOutlineVboKey,
                    GL.GL_ELEMENT_ARRAY_BUFFER, geom.mergedOutlineIndices,
                    (long) geom.mergedOutlineCount * idxBytes);
                gl.glDrawElements(GL.GL_LINES, geom.mergedOutlineCount, idxType, 0);
            }
        }
        else
        {
            if (hasInterior)
            {
                gl.glColorPointer(4, GL.GL_UNSIGNED_BYTE, 0, geom.mergedInteriorColors);
                gl.glDrawElements(GL.GL_TRIANGLES, geom.mergedInteriorCount, idxType,
                    geom.mergedInteriorIndices);
            }

            if (hasOutline)
            {
                gl.glLineWidth(geom.mergedOutlineWidth);
                gl.glColorPointer(4, GL.GL_UNSIGNED_BYTE, 0, geom.mergedOutlineColors);
                gl.glDrawElements(GL.GL_LINES, geom.mergedOutlineCount, idxType,
                    geom.mergedOutlineIndices);
            }
        }

        gl.glDisableClientState(GLPointerFunc.GL_COLOR_ARRAY);
    }

    /**
     * Shader-based merged rendering. Uses vertex attributes instead of fixed-function, with a simple
     * vertex+fragment shader that takes position and per-vertex color, and applies an MVP uniform.
     */
    protected void drawMergedShader(DrawContext dc, ShapefileGeometry geom, boolean useVbo,
                                     boolean hasInterior, boolean hasOutline, int idxType, int idxBytes)
    {
        GL2 gl = dc.getGL().getGL2();
        GpuResourceCache gpuCache = dc.getGpuResourceCache();

        // Compute MVP = projection * modelview (both in matrixArray/projectionArray)
        multiplyMatrices4x4(this.projectionArray, this.matrixArray, this.mvpArray);
        this.shapeShader.use(gl);
        gl.glUniformMatrix4fv(this.shapeShader.getUniformLocation(gl, "uMVP"), 1, false, this.mvpArray, 0);

        gl.glEnableVertexAttribArray(0); // position
        gl.glEnableVertexAttribArray(1); // color

        if (useVbo)
        {
            int[] vertVbo = (int[]) gpuCache.get(geom.vertexVboKey);

            if (hasInterior)
            {
                // Bind color VBO → attribute 1
                bindOrCreateVbo(gl, gpuCache, geom.mergedInteriorColorVboKey,
                    GL.GL_ARRAY_BUFFER, geom.mergedInteriorColors, geom.mergedInteriorColors.remaining());
                gl.glVertexAttribPointer(1, 4, GL.GL_UNSIGNED_BYTE, true, 0, 0);

                // Bind vertex VBO → attribute 0
                if (vertVbo != null)
                {
                    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vertVbo[0]);
                    gl.glVertexAttribPointer(0, geom.vertexStride, GL.GL_FLOAT, false, 0, 0);
                }

                bindOrCreateVbo(gl, gpuCache, geom.mergedInteriorVboKey,
                    GL.GL_ELEMENT_ARRAY_BUFFER, geom.mergedInteriorIndices,
                    (long) geom.mergedInteriorCount * idxBytes);
                gl.glDrawElements(GL.GL_TRIANGLES, geom.mergedInteriorCount, idxType, 0);
            }

            if (hasOutline)
            {
                gl.glLineWidth(geom.mergedOutlineWidth);

                bindOrCreateVbo(gl, gpuCache, geom.mergedOutlineColorVboKey,
                    GL.GL_ARRAY_BUFFER, geom.mergedOutlineColors, geom.mergedOutlineColors.remaining());
                gl.glVertexAttribPointer(1, 4, GL.GL_UNSIGNED_BYTE, true, 0, 0);

                if (vertVbo != null)
                {
                    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vertVbo[0]);
                    gl.glVertexAttribPointer(0, geom.vertexStride, GL.GL_FLOAT, false, 0, 0);
                }

                bindOrCreateVbo(gl, gpuCache, geom.mergedOutlineVboKey,
                    GL.GL_ELEMENT_ARRAY_BUFFER, geom.mergedOutlineIndices,
                    (long) geom.mergedOutlineCount * idxBytes);
                gl.glDrawElements(GL.GL_LINES, geom.mergedOutlineCount, idxType, 0);
            }
        }
        else
        {
            // Client memory with shader
            if (hasInterior)
            {
                gl.glVertexAttribPointer(0, geom.vertexStride, GL.GL_FLOAT, false, 0, geom.vertices);
                gl.glVertexAttribPointer(1, 4, GL.GL_UNSIGNED_BYTE, true, 0, geom.mergedInteriorColors);
                gl.glDrawElements(GL.GL_TRIANGLES, geom.mergedInteriorCount, idxType,
                    geom.mergedInteriorIndices);
            }

            if (hasOutline)
            {
                gl.glLineWidth(geom.mergedOutlineWidth);
                gl.glVertexAttribPointer(0, geom.vertexStride, GL.GL_FLOAT, false, 0, geom.vertices);
                gl.glVertexAttribPointer(1, 4, GL.GL_UNSIGNED_BYTE, true, 0, geom.mergedOutlineColors);
                gl.glDrawElements(GL.GL_LINES, geom.mergedOutlineCount, idxType,
                    geom.mergedOutlineIndices);
            }
        }

        gl.glDisableVertexAttribArray(1);
        gl.glDisableVertexAttribArray(0);
        this.shapeShader.unuse(gl);
    }

    /**
     * Bind an existing VBO or create and upload a new one.
     */
    private static void bindOrCreateVbo(GL2 gl, GpuResourceCache cache, Object key,
                                        int target, java.nio.Buffer data, long sizeBytes)
    {
        int[] vboId = (int[]) cache.get(key);
        if (vboId == null)
        {
            vboId = new int[1];
            gl.glGenBuffers(1, vboId, 0);
            gl.glBindBuffer(target, vboId[0]);
            gl.glBufferData(target, sizeBytes, data, GL.GL_STATIC_DRAW);
            cache.put(key, vboId, GpuResourceCache.VBO_BUFFERS, sizeBytes);
        }
        else
        {
            gl.glBindBuffer(target, vboId[0]);
        }
    }

    protected void applyClipSector(DrawContext dc, Sector sector, Vec4 vertexOffset)
    {
        GL2 gl = dc.getGL().getGL2();

        // Use scissor test instead of legacy clip planes. The vertexOffset cancels out
        // in the geographic→pixel transform, so we just need sector-to-pixel math.
        SurfaceTileDrawContext sdc = (SurfaceTileDrawContext) dc.getValue(AVKey.SURFACE_TILE_DRAW_CONTEXT);
        if (sdc != null)
        {
            Sector tileSector = sdc.getSector();
            java.awt.Rectangle vp = sdc.getViewport();
            double w = vp.width;
            double h = vp.height;
            double invDLon = w / tileSector.getDeltaLonDegrees();
            double invDLat = h / tileSector.getDeltaLatDegrees();

            double pxMin = (sector.getMinLongitude().degrees - tileSector.getMinLongitude().degrees) * invDLon;
            double pxMax = (sector.getMaxLongitude().degrees - tileSector.getMinLongitude().degrees) * invDLon;
            double pyMin = (sector.getMinLatitude().degrees - tileSector.getMinLatitude().degrees) * invDLat;
            double pyMax = (sector.getMaxLatitude().degrees - tileSector.getMinLatitude().degrees) * invDLat;

            int sx = Math.max(0, (int) Math.floor(pxMin));
            int sy = Math.max(0, (int) Math.floor(pyMin));
            int sw = Math.min((int) w, (int) Math.ceil(pxMax)) - sx;
            int sh = Math.min((int) h, (int) Math.ceil(pyMax)) - sy;

            gl.glEnable(GL.GL_SCISSOR_TEST);
            gl.glScissor(sx, sy, Math.max(1, sw), Math.max(1, sh));
            return;
        }

        // Fallback to legacy clip planes if SurfaceTileDrawContext unavailable
        fillArray4(this.clipPlaneArray, 0, 1, 0, 0, -(sector.getMinLongitude().degrees - vertexOffset.x));
        fillArray4(this.clipPlaneArray, 4, -1, 0, 0, sector.getMaxLongitude().degrees - vertexOffset.x);
        fillArray4(this.clipPlaneArray, 8, 0, 1, 0, -(sector.getMinLatitude().degrees - vertexOffset.y));
        fillArray4(this.clipPlaneArray, 12, 0, -1, 0, sector.getMaxLatitude().degrees - vertexOffset.y);

        for (int i = 0; i < 4; i++)
        {
            gl.glEnable(GL2ES1.GL_CLIP_PLANE0 + i);
            gl.glClipPlane(GL2ES1.GL_CLIP_PLANE0 + i, this.clipPlaneArray, 4 * i);
        }
    }

    protected void applyPickColors(DrawContext dc, ShapefileGeometry geom)
    {
        SurfaceTileDrawContext sdc = (SurfaceTileDrawContext) dc.getValue(AVKey.SURFACE_TILE_DRAW_CONTEXT);

        if (this.pickColors == null || this.pickColors.capacity() < 3 * geom.vertexCount)
        {
            this.pickColors = Buffers.newDirectByteBuffer(3 * geom.vertexCount);
        }
        this.pickColors.clear();

        for (RecordIndices ri : geom.recordIndices)
        {
            // Assign each record a unique RGB color. Generate vertex colors for every record - regardless of its
            // visibility - since the tile's color array must match the tile's vertex array. Keep a map of record
            // ordinals to pick colors in order to avoid drawing records in more than one unique color.
            Color color = this.pickColorMap.get(ri.ordinal);
            if (color == null)
            {
                color = dc.getUniquePickColor();
                this.pickColorMap.put(ri.ordinal, color);
            }

            // Associated the record's pickable object with the pickTileBuilder's list of pick candidates. This list
            // is saved during pre rendering and used during picking.
            ShapefileRenderable.Record record = this.getRecord(ri.ordinal);
            sdc.addPickCandidate(new PickedObject(color.getRGB(), record));

            // Add the unique color each vertex of the record.
            for (int i = 0; i < ri.vertexRange.length; i++)
            {
                this.pickColors.put((byte) color.getRed()).put((byte) color.getGreen()).put((byte) color.getBlue());
            }
        }

        GL2 gl = dc.getGL().getGL2(); // GL initialization checks for GL2 compatibility.
        gl.glEnableClientState(GLPointerFunc.GL_COLOR_ARRAY);
        gl.glColorPointer(3, GL.GL_UNSIGNED_BYTE, 0, this.pickColors.flip());
    }

    protected void drawAttributeGroup(DrawContext dc, RecordGroup attributeGroup, boolean useVbo)
    {
        GL2 gl = dc.getGL().getGL2(); // GL initialization checks for GL2 compatibility.
        ShapeAttributes attrs = attributeGroup.attributes;

        // Bind index VBO if available
        if (useVbo)
        {
            int[] vboId = (int[]) dc.getGpuResourceCache().get(attributeGroup.vboKey);
            if (vboId == null)
            {
                vboId = new int[1];
                gl.glGenBuffers(1, vboId, 0);
                gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, vboId[0]);
                gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER,
                    (long) attributeGroup.indices.remaining() * Integer.BYTES,
                    attributeGroup.indices, GL.GL_STATIC_DRAW);
                dc.getGpuResourceCache().put(attributeGroup.vboKey, vboId,
                    GpuResourceCache.VBO_BUFFERS,
                    (long) attributeGroup.indices.remaining() * Integer.BYTES);
            }
            else
            {
                gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, vboId[0]);
            }
        }

        if (attrs.isDrawInterior() && (dc.isPickingMode() || attrs.getInteriorOpacity() > 0))
        {
            if (!dc.isPickingMode())
            {
                Color rgb = attrs.getInteriorMaterial().getDiffuse();
                double alpha = attrs.getInteriorOpacity() * 255 + 0.5;
                gl.glColor4ub((byte) rgb.getRed(), (byte) rgb.getGreen(), (byte) rgb.getBlue(), (byte) alpha);
            }

            if (useVbo)
            {
                gl.glDrawElements(GL.GL_TRIANGLES, attributeGroup.interiorIndexRange.length,
                    GL.GL_UNSIGNED_INT, (long) attributeGroup.interiorIndexRange.location * Integer.BYTES);
            }
            else
            {
                gl.glDrawElements(GL.GL_TRIANGLES, attributeGroup.interiorIndexRange.length, GL.GL_UNSIGNED_INT,
                    attributeGroup.indices.position(attributeGroup.interiorIndexRange.location));
                attributeGroup.indices.rewind();
            }
        }

        if (attrs.isDrawOutline() && (dc.isPickingMode() || attrs.getOutlineOpacity() > 0))
        {
            if (!dc.isPickingMode())
            {
                Color rgb = attrs.getOutlineMaterial().getDiffuse();
                double alpha = attrs.getOutlineOpacity() * 255 + 0.5;
                gl.glColor4ub((byte) rgb.getRed(), (byte) rgb.getGreen(), (byte) rgb.getBlue(), (byte) alpha);
                gl.glLineWidth((float) attrs.getOutlineWidth());
            }
            else
            {
                gl.glLineWidth((float) Math.max(attrs.getOutlineWidth(), this.getOutlinePickWidth()));
            }

            if (useVbo)
            {
                gl.glDrawElements(GL.GL_LINES, attributeGroup.outlineIndexRange.length,
                    GL.GL_UNSIGNED_INT, (long) attributeGroup.outlineIndexRange.location * Integer.BYTES);
            }
            else
            {
                gl.glDrawElements(GL.GL_LINES, attributeGroup.outlineIndexRange.length, GL.GL_UNSIGNED_INT,
                    attributeGroup.indices.position(attributeGroup.outlineIndexRange.location));
                attributeGroup.indices.rewind();
            }
        }
    }

    protected static void fillArray4(double[] array, int offset, double x, double y, double z, double w)
    {
        array[0 + offset] = x;
        array[1 + offset] = y;
        array[2 + offset] = z;
        array[3 + offset] = w;
    }

    protected void combineBounds(CombineContext cc)
    {
        cc.addBoundingSector(this.sector);
    }

    protected void combineContours(CombineContext cc)
    {
        if (!cc.getSector().intersects(this.sector))
            return;  // the shapefile does not intersect the region of interest

        this.doCombineContours(cc);
    }

    protected void doCombineContours(CombineContext cc)
    {
        // Get the records intersecting the context's sector. The implementation of getItemsInRegion may return entries
        // outside the requested sector, so we cull them further in the loop below.
        Set<Record> intersectingRecords = this.recordTree.getItemsInRegion(cc.getSector(), null);
        if (intersectingRecords.isEmpty())
            return; // no records in the context's sector

        // Compute the minimum effective area for a vertex based on the context's resolution. We convert the resolution
        // from radians to square degrees. This ensures the units are consistent with the vertex effective area computed
        // by PolylineGeneralizer, which adopts the units of the source data (degrees).
        PolylineGeneralizer generalizer = new PolylineGeneralizer();
        double resolutionDegrees = cc.getResolution() * 180.0 / Math.PI;
        double minEffectiveArea = resolutionDegrees * resolutionDegrees;

        // Recursively tessellate the records to compute the boundaries of single polygon, then forward the resultant
        // contours to the context's GLU tessellator. We perform this recursive tessellation in order to draw the union
        // of the records into the context's GLU tessellator. Since we're eliminating vertices based on the context's
        // resolution, computing this union is necessary avoids incorrectly drawing regions where the absolute winding
        // order is greater than one due to two records overlapping.
        GLUtessellator tess = GLU.gluNewTess();

        try
        {
            GLUtessellatorCallback cb = new GLUTessellatorSupport.RecursiveCallback(cc.getTessellator());
            GLU.gluTessCallback(tess, GLU.GLU_TESS_BEGIN, cb);
            GLU.gluTessCallback(tess, GLU.GLU_TESS_VERTEX, cb);
            GLU.gluTessCallback(tess, GLU.GLU_TESS_END, cb);
            GLU.gluTessCallback(tess, GLU.GLU_TESS_COMBINE, cb);
            GLU.gluTessProperty(tess, GLU.GLU_TESS_BOUNDARY_ONLY, GL.GL_TRUE);
            GLU.gluTessProperty(tess, GLU.GLU_TESS_WINDING_RULE, GLU.GLU_TESS_WINDING_NONZERO); // union winding rule
            GLU.gluTessNormal(tess, 0, 0, 1);
            GLU.gluTessBeginPolygon(tess, null);

            for (Record record : intersectingRecords)
            {
                 // ignore records marked as not visible

                if (!record.isVisible() || !record.sector.intersects(cc.getSector()))
                    continue; // the record quadtree may return entries outside the sector passed to getItemsInRegion

                double effectiveArea = record.sector.getDeltaLatDegrees() * record.sector.getDeltaLonDegrees();
                if (effectiveArea < minEffectiveArea)
                    continue; // ignore records that don't meet the resolution criteria

                this.computeRecordMetrics(record, generalizer);
                this.doCombineRecord(tess, cc.getSector(), minEffectiveArea, record);
            }
        }
        finally
        {
            GLU.gluTessEndPolygon(tess);
            GLU.gluDeleteTess(tess);
        }
    }

    protected void doCombineRecord(GLUtessellator tess, Sector sector, double minEffectiveArea, Record record)
    {
        for (int i = 0; i < record.getBoundaryCount(); i++)
        {
            this.doCombineBoundary(tess, sector, minEffectiveArea, record, i);
        }
    }

    protected void doCombineBoundary(GLUtessellator tess, Sector sector, double minEffectiveArea, Record record,
        int boundaryIndex)
    {
        final ClippingTessellator clipTess = new ClippingTessellator(tess, sector);

        this.tessellateBoundary(record, boundaryIndex, minEffectiveArea, new TessBoundaryCallback()
        {
            @Override
            public void beginBoundary()
            {
                clipTess.beginContour();
            }

            @Override
            public void vertex(double degreesLatitude, double degreesLongitude)
            {
                clipTess.addVertex(degreesLatitude, degreesLongitude);
            }

            @Override
            public void endBoundary()
            {
                clipTess.endContour();
            }
        });
    }

    protected interface TessBoundaryCallback
    {
        void beginBoundary();

        void vertex(double degreesLatitude, double degreesLongitude);

        void endBoundary();
    }

    protected void tessellateBoundary(Record record, int boundaryIndex, double minEffectiveArea, TessBoundaryCallback callback)
    {
        VecBuffer boundaryCoords = record.getBoundaryPoints(boundaryIndex);
        double[] boundaryEffectiveArea = record.getBoundaryEffectiveArea(boundaryIndex);
        double[] coord = new double[2];

        if (!record.isBoundaryCrossesAntimeridian(boundaryIndex))
        {
            callback.beginBoundary();
            for (int j = 0; j < boundaryCoords.getSize(); j++)
            {
                if (boundaryEffectiveArea[j] < minEffectiveArea)
                    continue; // ignore vertices that don't meet the resolution criteria

                boundaryCoords.get(j, coord); // lon, lat
                callback.vertex(coord[1], coord[0]); // lat, lon
            }
            callback.endBoundary();
        }
        else
        {
            // Copy the boundary locations into a list of LatLon instances in order to utilize existing code that
            // handles locations that cross the antimeridian.
            ArrayList<LatLon> locations = new ArrayList<>();
            for (int j = 0; j < boundaryCoords.getSize(); j++)
            {
                if (boundaryEffectiveArea[j] < minEffectiveArea)
                    continue; // ignore vertices that don't meet the resolution criteria

                boundaryCoords.get(j, coord); // lon, lat
                locations.add(LatLon.fromDegrees(coord[1], coord[0])); // lat, lon
            }

            String pole = LatLon.locationsContainPole(locations);
            if (pole != null) // wrap the boundary around the pole and along the antimeridian
            {
                callback.beginBoundary();
                for (LatLon location : LatLon.cutLocationsAlongDateLine(locations, pole, null))
                {
                    callback.vertex(location.latitude.degrees, location.longitude.degrees);
                }
                callback.endBoundary();
            }
            else // tessellate on both sides of the antimeridian
            {
                for (List<LatLon> antimeridianLocations : LatLon.repeatLocationsAroundDateline(locations))
                {
                    callback.beginBoundary();
                    for (LatLon location : antimeridianLocations)
                    {
                        callback.vertex(location.latitude.degrees, location.longitude.degrees);
                    }
                    callback.endBoundary();
                }
            }
        }
    }
}
