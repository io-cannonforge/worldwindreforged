/*
 * WorldWind Reforged — NYC Buildings 3D Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: batched VBO renderer for thousands of extruded buildings, inspired by
 * ShapefileExtrudedPolygons. Uses quad-tree spatial culling, per-tile VBO merging,
 * height-based LOD, and per-vertex colour to minimise draw calls.
 */
package gov.nasa.worldwindx.examples.nycbuildings;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.fixedfunc.GLPointerFunc;

import gov.nasa.worldwind.cache.GpuResourceCache;
import gov.nasa.worldwind.geom.Extent;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Matrix;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.geom.Vec4;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.pick.PickSupport;
import gov.nasa.worldwind.pick.PickedObject;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.OrderedRenderable;
import gov.nasa.worldwind.render.PolygonTessellator;
import gov.nasa.worldwind.render.Renderable;
import gov.nasa.worldwind.terrain.Terrain;

/**
 * High-performance batched renderer for thousands of 3D extruded buildings.
 * <p>
 * Merges building geometry into per-tile VBOs and draws with one
 * {@code glDrawElements} call per tile instead of per-building. Uses a quad-tree
 * for frustum culling and height-based LOD to keep frame times low even with
 * 40k+ buildings.
 * <p>
 * Architecture follows {@link gov.nasa.worldwind.formats.shapefile.ShapefileExtrudedPolygons}
 * but decoupled from the Shapefile record format.
 *
 * seaglassfoundry.com
 */
public class BuildingBatchRenderer implements Renderable, OrderedRenderable
{
    // ── LOD altitude thresholds (metres) ─────────────────────────────────────
    private static final double LOD_HIDE_ALL      = 50_000;
    private static final double LOD_SKYSCRAPER    = 10_000;
    private static final double LOD_HIGH_RISE     = 2_000;
    private static final double LOD_MID_RISE      = 500;
    // below 500 m: all buildings visible

    private static final int TILE_MAX_LEVEL    = 3;
    private static final int TILE_MAX_CAPACITY = 5000;
    private static final double DEFAULT_BASE_DEPTH = 10; // metres below terrain

    // ── Internal record (lightweight, no WorldWind shape overhead) ────────────
    static class BRecord
    {
        final BuildingRecord source;
        final Sector sector;
        // Tessellated indices (computed once per record)
        IntBuffer interiorIndices;
        IntBuffer outlineIndices;
        int numberOfPoints; // total footprint + holes vertex count

        BRecord(BuildingRecord source)
        {
            this.source = source;

            // Compute sector from footprint
            double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
            double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
            int ptCount = source.getFootprint().size();
            for (LatLon ll : source.getFootprint())
            {
                double lat = ll.getLatitude().degrees;
                double lon = ll.getLongitude().degrees;
                if (lat < minLat) minLat = lat;
                if (lat > maxLat) maxLat = lat;
                if (lon < minLon) minLon = lon;
                if (lon > maxLon) maxLon = lon;
            }
            for (List<LatLon> hole : source.getHoles())
            {
                ptCount += hole.size();
                for (LatLon ll : hole)
                {
                    double lat = ll.getLatitude().degrees;
                    double lon = ll.getLongitude().degrees;
                    if (lat < minLat) minLat = lat;
                    if (lat > maxLat) maxLat = lat;
                    if (lon < minLon) minLon = lon;
                    if (lon > maxLon) maxLon = lon;
                }
            }
            this.sector = Sector.fromDegrees(minLat, maxLat, minLon, maxLon);
            this.numberOfPoints = ptCount;
        }
    }

    // ── Tile quad-tree ───────────────────────────────────────────────────────
    static class Tile
    {
        final Sector sector;
        final int level;
        final ArrayList<BRecord> records = new ArrayList<>();
        Tile[] children;
        // Per-tile geometry (regenerated when terrain / globe changes)
        FloatBuffer vertices;    // interleaved: topXYZ, bottomXYZ per point (stride 6 floats)
        ByteBuffer colors;       // per-vertex RGBA (top and bottom get same colour)
        IntBuffer interiorIndices;
        IntBuffer outlineIndices;
        Vec4 referencePoint;
        Matrix transformMatrix;
        Object vboVertKey = new Object();
        Object vboIdxKey  = new Object();
        Object vboOutKey  = new Object();
        Object vboColKey  = new Object();
        boolean geometryValid;

        Tile(Sector sector, int level)
        {
            this.sector = sector;
            this.level = level;
        }
    }

    // ── State ────────────────────────────────────────────────────────────────
    private Tile rootTile;
    private final List<Tile> currentTiles = new ArrayList<>();
    private final PolygonTessellator tess = new PolygonTessellator();
    private Layer pickLayer;
    private final PickSupport pickSupport = new PickSupport();
    private final double[] matrixArray = new double[16];
    private double maxHeight;
    private volatile Predicate<BuildingRecord> filter = r -> true;

    // Listeners for stats
    private final CopyOnWriteArrayList<Runnable> loadListeners = new CopyOnWriteArrayList<>();
    private volatile int totalCount;
    private volatile int visibleCount;

    // ── Public API ────────────────────────────────────────────���──────────────

    /**
     * Load building records into the renderer. Can be called from the EDT after a
     * background fetch. Replaces any previously loaded buildings.
     */
    public void loadBuildings(List<BuildingRecord> records)
    {
        if (records == null || records.isEmpty())
            return;

        // Compute overall sector
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        double maxH = 0;

        List<BRecord> bRecords = new ArrayList<>(records.size());
        for (BuildingRecord r : records)
        {
            if (r.getFootprint().size() < 3) continue; // skip degenerate
            BRecord br = new BRecord(r);
            bRecords.add(br);
            if (r.getHeightMeters() > maxH) maxH = r.getHeightMeters();
            if (br.sector.getMinLatitude().degrees < minLat) minLat = br.sector.getMinLatitude().degrees;
            if (br.sector.getMaxLatitude().degrees > maxLat) maxLat = br.sector.getMaxLatitude().degrees;
            if (br.sector.getMinLongitude().degrees < minLon) minLon = br.sector.getMinLongitude().degrees;
            if (br.sector.getMaxLongitude().degrees > maxLon) maxLon = br.sector.getMaxLongitude().degrees;
        }

        this.maxHeight = maxH;
        this.totalCount = bRecords.size();

        Sector rootSector = Sector.fromDegrees(minLat, maxLat, minLon, maxLon);
        Tile root = new Tile(rootSector, 0);
        root.records.addAll(bRecords);

        if (mustSplitTile(root))
            splitTile(root);

        root.records.trimToSize();
        this.rootTile = root;

        for (Runnable listener : loadListeners)
            listener.run();
    }

    public void setFilter(Predicate<BuildingRecord> filter)
    {
        this.filter = filter != null ? filter : r -> true;
        invalidateAllTileGeometry();
    }

    public void addLoadListener(Runnable listener) { loadListeners.add(listener); }

    public int getTotalCount() { return totalCount; }
    public int getVisibleCount() { return visibleCount; }
    public double getMaxHeight() { return maxHeight; }

    /** Find a record by id for detail popup. */
    public BuildingRecord findById(String id)
    {
        if (rootTile == null || id == null) return null;
        Queue<Tile> queue = new ArrayDeque<>();
        queue.add(rootTile);
        while (!queue.isEmpty())
        {
            Tile t = queue.poll();
            for (BRecord br : t.records)
                if (id.equals(br.source.getId())) return br.source;
            if (t.children != null)
                Collections.addAll(queue, t.children);
        }
        return null;
    }

    // ── Tile splitting ───────────────────────────────────────────────────────

    private boolean mustSplitTile(Tile tile)
    {
        return tile.level < TILE_MAX_LEVEL && tile.records.size() > TILE_MAX_CAPACITY;
    }

    private void splitTile(Tile tile)
    {
        Sector[] childSectors = tile.sector.subdivide();
        tile.children = new Tile[4];
        for (int i = 0; i < 4; i++)
            tile.children[i] = new Tile(childSectors[i], tile.level + 1);

        var it = tile.records.iterator();
        while (it.hasNext())
        {
            BRecord br = it.next();
            for (int i = 0; i < 4; i++)
            {
                if (tile.children[i].sector.contains(br.sector))
                {
                    tile.children[i].records.add(br);
                    it.remove();
                    break;
                }
            }
        }

        for (int i = 0; i < 4; i++)
        {
            if (mustSplitTile(tile.children[i]))
                splitTile(tile.children[i]);
            tile.children[i].records.trimToSize();
        }
    }

    // ── Renderable / OrderedRenderable ───────────────────────────────────────

    @Override
    public void render(DrawContext dc)
    {
        if (rootTile == null) return;

        if (dc.isOrderedRenderingMode())
            drawOrderedSurfaceRenderable(dc);
        else
            makeOrderedSurfaceRenderable(dc);
    }

    @Override
    public double getDistanceFromEye() { return 0; }

    @Override
    public void pick(DrawContext dc, java.awt.Point pickPoint)
    {
        if (rootTile == null) return;
        pickOrderedSurfaceRenderable(dc, pickPoint);
    }

    private void makeOrderedSurfaceRenderable(DrawContext dc)
    {
        assembleTiles(dc);
        if (currentTiles.isEmpty()) return;
        pickLayer = dc.getCurrentLayer();
        dc.addOrderedSurfaceRenderable(this);
    }

    // ── Tile assembly with LOD ───────────────────────────────────────────────

    private void assembleTiles(DrawContext dc)
    {
        currentTiles.clear();
        visibleCount = 0;

        double eyeAltitude = dc.getView().getEyePosition().getAltitude();
        int minCatOrdinal = computeMinCategoryOrdinal(eyeAltitude);

        addTileOrDescendants(dc, rootTile, minCatOrdinal);
    }

    private int computeMinCategoryOrdinal(double eyeAltitude)
    {
        if (eyeAltitude > LOD_HIDE_ALL) return Integer.MAX_VALUE;
        if (eyeAltitude > LOD_SKYSCRAPER) return BuildingCategory.SKYSCRAPER.ordinal();
        if (eyeAltitude > LOD_HIGH_RISE) return BuildingCategory.HIGH_RISE.ordinal();
        if (eyeAltitude > LOD_MID_RISE) return BuildingCategory.MID_RISE.ordinal();
        return 0; // show all
    }

    private void addTileOrDescendants(DrawContext dc, Tile tile, int minCatOrdinal)
    {
        if (!isTileVisible(dc, tile))
            return;

        if (!tile.records.isEmpty())
        {
            if (!tile.geometryValid)
                regenerateTileGeometry(dc.getTerrain(), tile, minCatOrdinal);

            if (tile.interiorIndices != null && tile.interiorIndices.remaining() > 0)
            {
                currentTiles.add(tile);
                visibleCount += countVisibleRecords(tile, minCatOrdinal);
            }
        }

        if (tile.children != null)
        {
            for (Tile child : tile.children)
                addTileOrDescendants(dc, child, minCatOrdinal);
        }
    }

    private int countVisibleRecords(Tile tile, int minCatOrdinal)
    {
        int count = 0;
        for (BRecord br : tile.records)
        {
            if (br.source.getCategory().ordinal() >= minCatOrdinal && filter.test(br.source))
                count++;
        }
        return count;
    }

    private boolean isTileVisible(DrawContext dc, Tile tile)
    {
        Extent extent = makeTileExtent(dc.getTerrain(), tile);
        if (extent == null) return false;
        if (dc.isSmall(extent, 1)) return false;
        if (dc.isPickingMode())
            return dc.getPickFrustums().intersectsAny(extent);
        return dc.getView().getFrustumInModelCoordinates().intersects(extent);
    }

    private Extent makeTileExtent(Terrain terrain, Tile tile)
    {
        try
        {
            double[] extremes = terrain.getGlobe().getMinAndMaxElevations(tile.sector);
            double minH = extremes[0] - DEFAULT_BASE_DEPTH;
            double maxH = extremes[1] + Math.max(this.maxHeight, 12);
            return Sector.computeBoundingBox(terrain.getGlobe(), terrain.getVerticalExaggeration(),
                tile.sector, minH, maxH);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    // ── Geometry generation (per tile) ───────────────────────────────────────

    private void regenerateTileGeometry(Terrain terrain, Tile tile, int minCatOrdinal)
    {
        // Count total points for visible/LOD-passing buildings
        int totalPoints = 0;
        List<BRecord> visibleRecords = new ArrayList<>();
        for (BRecord br : tile.records)
        {
            if (br.source.getCategory().ordinal() >= minCatOrdinal && filter.test(br.source))
            {
                visibleRecords.add(br);
                totalPoints += br.numberOfPoints;
            }
        }

        if (totalPoints == 0)
        {
            tile.vertices = null;
            tile.interiorIndices = null;
            tile.outlineIndices = null;
            tile.colors = null;
            tile.geometryValid = true;
            return;
        }

        int vertexStride = 3;
        FloatBuffer vertices = Buffers.newDirectFloatBuffer(2 * vertexStride * totalPoints);
        ByteBuffer colors = Buffers.newDirectByteBuffer(2 * 4 * totalPoints); // RGBA per vertex (top+bottom)

        // Accumulators for indices
        int totalInterior = 0;
        int totalOutline = 0;

        Vec4 rp = null;
        float[] vertex = new float[6];

        for (BRecord br : visibleRecords)
        {
            double height = br.source.getHeightMeters();
            double depth = DEFAULT_BASE_DEPTH;
            Vec4 N = null;
            double NdotR = 0;

            Color capColor = br.source.getCategory().getColor();
            Color sideColor = br.source.getCategory().getSideColor();

            // Tessellate cap only once
            boolean needTess = (br.interiorIndices == null);
            tess.setEnabled(needTess);
            tess.reset();
            tess.setPolygonNormal(0, 0, 1);
            tess.beginPolygon();

            // Outer boundary
            tess.beginContour();
            List<LatLon> footprint = br.source.getFootprint();
            for (int j = 0; j < footprint.size(); j++)
            {
                LatLon ll = footprint.get(j);
                Vec4 p = terrain.getSurfacePoint(ll.getLatitude(), ll.getLongitude(), 0);
                if (p == null) continue;

                int index = vertices.position() / vertexStride;
                tess.addVertex(ll.getLongitude().degrees, ll.getLatitude().degrees, 0, index);

                if (rp == null) rp = p;
                if (N == null)
                {
                    N = terrain.getGlobe().computeSurfaceNormalAtPoint(p);
                    NdotR = p.x * N.x + p.y * N.y + p.z * N.z;
                }

                double t = height + NdotR - (p.x * N.x + p.y * N.y + p.z * N.z);
                double b = -depth;
                vertex[0] = (float)(p.x + N.x * t - rp.x);
                vertex[1] = (float)(p.y + N.y * t - rp.y);
                vertex[2] = (float)(p.z + N.z * t - rp.z);
                vertex[3] = (float)(p.x + N.x * b - rp.x);
                vertex[4] = (float)(p.y + N.y * b - rp.y);
                vertex[5] = (float)(p.z + N.z * b - rp.z);
                vertices.put(vertex);

                // Cap vertex colour (top)
                colors.put((byte) capColor.getRed());
                colors.put((byte) capColor.getGreen());
                colors.put((byte) capColor.getBlue());
                colors.put((byte) 255);
                // Side vertex colour (bottom)
                colors.put((byte) sideColor.getRed());
                colors.put((byte) sideColor.getGreen());
                colors.put((byte) sideColor.getBlue());
                colors.put((byte) 255);
            }
            tess.endContour();

            // Inner boundaries (holes)
            for (List<LatLon> hole : br.source.getHoles())
            {
                tess.beginContour();
                for (LatLon ll : hole)
                {
                    Vec4 p = terrain.getSurfacePoint(ll.getLatitude(), ll.getLongitude(), 0);
                    if (p == null) continue;

                    int index = vertices.position() / vertexStride;
                    tess.addVertex(ll.getLongitude().degrees, ll.getLatitude().degrees, 0, index);

                    if (N == null)
                    {
                        N = terrain.getGlobe().computeSurfaceNormalAtPoint(p);
                        NdotR = p.x * N.x + p.y * N.y + p.z * N.z;
                    }

                    double t = height + NdotR - (p.x * N.x + p.y * N.y + p.z * N.z);
                    double b = -depth;
                    vertex[0] = (float)(p.x + N.x * t - rp.x);
                    vertex[1] = (float)(p.y + N.y * t - rp.y);
                    vertex[2] = (float)(p.z + N.z * t - rp.z);
                    vertex[3] = (float)(p.x + N.x * b - rp.x);
                    vertex[4] = (float)(p.y + N.y * b - rp.y);
                    vertex[5] = (float)(p.z + N.z * b - rp.z);
                    vertices.put(vertex);

                    colors.put((byte) capColor.getRed());
                    colors.put((byte) capColor.getGreen());
                    colors.put((byte) capColor.getBlue());
                    colors.put((byte) 255);
                    colors.put((byte) sideColor.getRed());
                    colors.put((byte) sideColor.getGreen());
                    colors.put((byte) sideColor.getBlue());
                    colors.put((byte) 255);
                }
                tess.endContour();
            }

            tess.endPolygon();

            if (needTess)
                assembleRecordIndices(tess, br);

            totalInterior += br.interiorIndices.remaining();
            totalOutline += br.outlineIndices.remaining();
        }

        // Merge all record indices into tile-level buffers
        IntBuffer intBuf = Buffers.newDirectIntBuffer(totalInterior);
        IntBuffer outBuf = Buffers.newDirectIntBuffer(totalOutline);
        for (BRecord br : visibleRecords)
        {
            intBuf.put(br.interiorIndices);
            br.interiorIndices.rewind();
            outBuf.put(br.outlineIndices);
            br.outlineIndices.rewind();
        }

        tile.vertices = vertices.rewind();
        tile.colors = colors.rewind();
        tile.interiorIndices = intBuf.rewind();
        tile.outlineIndices = outBuf.rewind();
        tile.referencePoint = rp;
        tile.transformMatrix = rp != null ? Matrix.fromTranslation(rp.x, rp.y, rp.z) : Matrix.IDENTITY;
        tile.geometryValid = true;

        // Expire any cached VBOs
        tile.vboVertKey = new Object();
        tile.vboIdxKey = new Object();
        tile.vboOutKey = new Object();
        tile.vboColKey = new Object();
    }

    private void assembleRecordIndices(PolygonTessellator tessellator, BRecord record)
    {
        if (!tessellator.isEnabled()) return;

        IntBuffer tessInterior = tessellator.getInteriorIndices().flip();
        IntBuffer tessBoundary = tessellator.getBoundaryIndices().flip();

        IntBuffer interiorIndices = IntBuffer.allocate(tessInterior.remaining() + 3 * tessBoundary.remaining());
        IntBuffer outlineIndices = IntBuffer.allocate(2 * tessBoundary.remaining());

        // Cap triangles
        interiorIndices.put(tessInterior);

        // Side quads (2 triangles each) + outline edges
        for (int i = tessBoundary.position(); i < tessBoundary.limit(); i += 2)
        {
            int top1 = tessBoundary.get(i);
            int top2 = tessBoundary.get(i + 1);
            int bot1 = top1 + 1; // top and bottom vertices are adjacent in the buffer
            int bot2 = top2 + 1;
            interiorIndices.put(top1); interiorIndices.put(bot1); interiorIndices.put(top2);
            interiorIndices.put(top2); interiorIndices.put(bot1); interiorIndices.put(bot2);
            outlineIndices.put(top1); outlineIndices.put(top2);
            outlineIndices.put(top1); outlineIndices.put(bot1);
        }

        record.interiorIndices = interiorIndices.rewind();
        record.outlineIndices = outlineIndices.rewind();
    }

    private void invalidateAllTileGeometry()
    {
        if (rootTile == null) return;
        Queue<Tile> queue = new ArrayDeque<>();
        queue.add(rootTile);
        while (!queue.isEmpty())
        {
            Tile t = queue.poll();
            t.geometryValid = false;
            if (t.children != null)
                Collections.addAll(queue, t.children);
        }
    }

    // ── Drawing ──────────────────────────────────────────────────────────────

    private void drawOrderedSurfaceRenderable(DrawContext dc)
    {
        try
        {
            beginDrawing(dc);
            for (Tile tile : currentTiles)
                drawTile(dc, tile);
        }
        finally
        {
            endDrawing(dc);
        }
    }

    private void pickOrderedSurfaceRenderable(DrawContext dc, java.awt.Point pickPoint)
    {
        try
        {
            pickSupport.clearPickList();
            pickSupport.beginPicking(dc);
            beginDrawing(dc);

            for (Tile tile : currentTiles)
            {
                Color color = dc.getUniquePickColor();
                GL2 gl = dc.getGL().getGL2();
                gl.glColor3ub((byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue());
                pickSupport.addPickableObject(color.getRGB(), tile);
                drawTile(dc, tile);
            }

            // Resolve which tile was picked, then drill down to individual building
            PickedObject po = pickSupport.getTopObject(dc, pickPoint);
            if (po != null)
            {
                pickSupport.clearPickList();
                drawTileInUniqueColors(dc, (Tile) po.getObject());
                pickSupport.resolvePick(dc, pickPoint, pickLayer);
            }
        }
        finally
        {
            endDrawing(dc);
            pickSupport.endPicking(dc);
            pickSupport.clearPickList();
        }
    }

    private void beginDrawing(DrawContext dc)
    {
        GL2 gl = dc.getGL().getGL2();
        gl.glEnable(GL.GL_CULL_FACE);
        gl.glEnableClientState(GLPointerFunc.GL_VERTEX_ARRAY);
        gl.glDepthFunc(GL.GL_LEQUAL);
        gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
        gl.glPushMatrix();

        if (!dc.isPickingMode())
        {
            gl.glEnableClientState(GLPointerFunc.GL_COLOR_ARRAY); // only in render, NOT pick
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    private void endDrawing(DrawContext dc)
    {
        GL2 gl = dc.getGL().getGL2();
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDisableClientState(GLPointerFunc.GL_VERTEX_ARRAY);
        gl.glColor4f(1, 1, 1, 1);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glLineWidth(1);
        gl.glPopMatrix();

        if (!dc.isPickingMode())
        {
            gl.glDisableClientState(GLPointerFunc.GL_COLOR_ARRAY);
            gl.glDisable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ZERO);
        }

        if (dc.getGLRuntimeCapabilities().isUseVertexBufferObject())
        {
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
        }
    }

    private void drawTile(DrawContext dc, Tile tile)
    {
        if (tile.vertices == null || tile.referencePoint == null) return;

        GL2 gl = dc.getGL().getGL2();
        boolean useVbo = dc.getGLRuntimeCapabilities().isUseVertexBufferObject();

        // Vertex buffer: interleaved top/bottom XYZ with stride 6 floats (24 bytes)
        if (useVbo)
        {
            int[] vboId = (int[]) dc.getGpuResourceCache().get(tile.vboVertKey);
            if (vboId == null)
            {
                vboId = new int[1];
                gl.glGenBuffers(1, vboId, 0);
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboId[0]);
                gl.glBufferData(GL.GL_ARRAY_BUFFER, 4L * tile.vertices.remaining(), tile.vertices, GL.GL_STATIC_DRAW);
                dc.getGpuResourceCache().put(tile.vboVertKey, vboId, GpuResourceCache.VBO_BUFFERS,
                    4L * tile.vertices.remaining());
            }
            else
            {
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboId[0]);
            }
            gl.glVertexPointer(3, GL.GL_FLOAT, 0, 0);
        }
        else
        {
            gl.glVertexPointer(3, GL.GL_FLOAT, 0, tile.vertices);
        }

        // Colour buffer
        if (!dc.isPickingMode() && tile.colors != null)
        {
            if (useVbo)
            {
                int[] colId = (int[]) dc.getGpuResourceCache().get(tile.vboColKey);
                if (colId == null)
                {
                    colId = new int[1];
                    gl.glGenBuffers(1, colId, 0);
                    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, colId[0]);
                    gl.glBufferData(GL.GL_ARRAY_BUFFER, tile.colors.remaining(), tile.colors, GL.GL_STATIC_DRAW);
                    dc.getGpuResourceCache().put(tile.vboColKey, colId, GpuResourceCache.VBO_BUFFERS,
                        tile.colors.remaining());
                }
                else
                {
                    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, colId[0]);
                }
                gl.glColorPointer(4, GL.GL_UNSIGNED_BYTE, 0, 0);
            }
            else
            {
                gl.glColorPointer(4, GL.GL_UNSIGNED_BYTE, 0, tile.colors);
            }
        }

        // Modelview
        Matrix modelview = dc.getView().getModelviewMatrix().multiply(tile.transformMatrix);
        modelview.toArray(matrixArray, 0, false);
        gl.glLoadMatrixd(matrixArray, 0);

        // Interior triangles
        if (tile.interiorIndices != null && tile.interiorIndices.remaining() > 0)
        {
            if (useVbo)
            {
                int[] idxId = (int[]) dc.getGpuResourceCache().get(tile.vboIdxKey);
                if (idxId == null)
                {
                    idxId = new int[1];
                    gl.glGenBuffers(1, idxId, 0);
                    gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, idxId[0]);
                    gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, 4L * tile.interiorIndices.remaining(),
                        tile.interiorIndices, GL.GL_STATIC_DRAW);
                    dc.getGpuResourceCache().put(tile.vboIdxKey, idxId, GpuResourceCache.VBO_BUFFERS,
                        4L * tile.interiorIndices.remaining());
                }
                else
                {
                    gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, idxId[0]);
                }
                gl.glDrawElements(GL.GL_TRIANGLES, tile.interiorIndices.remaining(), GL.GL_UNSIGNED_INT, 0);
            }
            else
            {
                gl.glDrawElements(GL.GL_TRIANGLES, tile.interiorIndices.remaining(), GL.GL_UNSIGNED_INT,
                    tile.interiorIndices);
            }
        }

        // Outlines (thin dark lines for building edges)
        if (!dc.isPickingMode() && tile.outlineIndices != null && tile.outlineIndices.remaining() > 0)
        {
            gl.glDisableClientState(GLPointerFunc.GL_COLOR_ARRAY);
            gl.glColor4f(0.15f, 0.15f, 0.2f, 0.6f);
            gl.glLineWidth(1.0f);

            if (useVbo)
            {
                int[] outId = (int[]) dc.getGpuResourceCache().get(tile.vboOutKey);
                if (outId == null)
                {
                    outId = new int[1];
                    gl.glGenBuffers(1, outId, 0);
                    gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, outId[0]);
                    gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, 4L * tile.outlineIndices.remaining(),
                        tile.outlineIndices, GL.GL_STATIC_DRAW);
                    dc.getGpuResourceCache().put(tile.vboOutKey, outId, GpuResourceCache.VBO_BUFFERS,
                        4L * tile.outlineIndices.remaining());
                }
                else
                {
                    gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, outId[0]);
                }
                gl.glDrawElements(GL.GL_LINES, tile.outlineIndices.remaining(), GL.GL_UNSIGNED_INT, 0);
            }
            else
            {
                gl.glDrawElements(GL.GL_LINES, tile.outlineIndices.remaining(), GL.GL_UNSIGNED_INT,
                    tile.outlineIndices);
            }

            gl.glEnableClientState(GLPointerFunc.GL_COLOR_ARRAY);
        }
    }

    private void drawTileInUniqueColors(DrawContext dc, Tile tile)
    {
        if (tile.vertices == null || tile.referencePoint == null) return;

        GL2 gl = dc.getGL().getGL2();
        boolean useVbo = dc.getGLRuntimeCapabilities().isUseVertexBufferObject();

        // Bind vertex buffer (already uploaded)
        if (useVbo)
        {
            int[] vboId = (int[]) dc.getGpuResourceCache().get(tile.vboVertKey);
            if (vboId != null)
            {
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboId[0]);
                gl.glVertexPointer(3, GL.GL_FLOAT, 0, 0);
            }
            else
            {
                gl.glVertexPointer(3, GL.GL_FLOAT, 0, tile.vertices);
            }
        }
        else
        {
            gl.glVertexPointer(3, GL.GL_FLOAT, 0, tile.vertices);
        }

        // Color array is already disabled in pick mode (beginDrawing), so just use glColor per building.
        // Unbind any element VBO left by drawTile — we use client-side index buffers per building here.
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);

        Matrix modelview = dc.getView().getModelviewMatrix().multiply(tile.transformMatrix);
        modelview.toArray(matrixArray, 0, false);
        gl.glLoadMatrixd(matrixArray, 0);

        // Draw each building's interior with a unique pick colour
        for (BRecord br : tile.records)
        {
            if (br.interiorIndices == null || br.interiorIndices.remaining() == 0) continue;

            Color pickColor = dc.getUniquePickColor();
            gl.glColor3ub((byte) pickColor.getRed(), (byte) pickColor.getGreen(), (byte) pickColor.getBlue());
            pickSupport.addPickableObject(pickColor.getRGB(), br.source, null, false);

            gl.glDrawElements(GL.GL_TRIANGLES, br.interiorIndices.remaining(), GL.GL_UNSIGNED_INT,
                br.interiorIndices);
            br.interiorIndices.rewind();
        }
    }
}
