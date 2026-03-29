# Shapefile Rendering Optimizations

This document describes the performance optimizations applied to `ShapefilePolygons` in WorldWind Reforged. These changes target both per-frame rendering speed and tile load time, replacing legacy fixed-function OpenGL patterns and the GLU tessellator with modern GPU-friendly techniques.

## Architecture Overview

Shapefiles render through a tiled quadtree. Each `ShapefileTile` covers a geographic sector and contains tessellated polygon geometry. Tiles render into off-screen 512x512 FBOs managed by `SurfaceObjectTileBuilder`, which composites the results onto the globe surface.

**Tile lifecycle:**
1. **Tessellation** (background thread): polygon contours are triangulated into `GL_TRIANGLES` indices and `GL_LINES` outline indices. Results are cached per tile.
2. **Attribute assembly** (render thread): records are grouped by `ShapeAttributes` and merged into combined index/color buffers.
3. **Rendering** (render thread): `beginDrawing` → `draw` → `endDrawing` per tile, issuing draw calls into the surface tile FBO.

---

## Tessellation

Tessellation converts polygon contours (outer rings + holes) from the shapefile into triangle indices for `GL_TRIANGLES` rendering and line segment indices for `GL_LINES` outlines. This happens once per tile on a background thread; results are cached and reused across frames.

### Legacy Path: GLU Tessellator

**File:** `ShapefilePolygons.java` (`tessellateGLU`, `tessellateRecord`)

The original tessellation used OpenGL's `GLUtessellator`, a callback-based API from the 1990s:

```java
GLUtessellatorCallback callback = new GLUtessellatorCallbackAdapter() {
    public void vertex(Object vertexData) { ... }
    public void begin(int type) { ... }
    public void end() { ... }
    public void combine(...) { ... }
};
glu.gluTessBeginPolygon(tess, null);
glu.gluTessBeginContour(tess);
// ... feed vertices one at a time ...
glu.gluTessEndContour(tess);
glu.gluTessEndPolygon(tess);
```

Problems with GLU:
- **Callback overhead**: each vertex, triangle, and contour transition fires a Java callback through JNI. For a polygon with 100 vertices, that's 100+ callbacks.
- **No batch processing**: polygons must be fed one at a time; no way to triangulate multiple polygons in parallel.
- **Opaque internals**: can't control the triangulation algorithm, vertex ordering, or degenerate handling.
- **Antimeridian coupling**: the GLU path is interleaved with antimeridian-crossing logic (`repeatLocationsAroundDateline`), making the code hard to maintain.

The GLU path is retained as a fallback, activated when ear-clipping fails or when `forceDisableEarClipping` is set. Toggle: **E** key in the benchmark.

### New Path: Ear-Clipping Tessellation

**Files:** `ShapefilePolygons.java` (`tessellateEarClipping`, `collectBoundaryVertices`), `GpuTriangulator.java`

The new tessellation path uses the ear-clipping algorithm operating on flat arrays with a linked-list structure, replacing all GLU callbacks with direct array manipulation.

#### How It Works

The `tessellate()` method in `ShapefilePolygons` first filters records by intersection and resolution, then tries ear-clipping before falling back to GLU:

```java
protected void tessellate(ShapefileGeometry geom) {
    // ... filter records ...
    if (!forceDisableEarClipping && this.tessellateEarClipping(geom, validRecords, xOffset, yOffset))
        return;
    this.tessellateGLU(geom, validRecords, xOffset, yOffset);
}
```

The ear-clipping path (`tessellateEarClipping`) processes each record in two phases:

**Phase 1 — Contour Collection and Hole Bridging:**

For each shapefile record, boundary vertices are collected into a flat `float[]` array (x,y pairs with a coordinate offset applied for numerical stability). Each boundary contour (outer ring + holes) is tracked by start index and vertex count.

If a polygon has holes, `GpuTriangulator.bridgeHoles()` merges them into the outer ring to produce a single simple polygon. See [Bridge Edge Insertion](#bridge-edge-insertion-for-holes) below.

**Phase 2 — Triangle Generation:**

`GpuTriangulator.triangulateCPU()` runs the ear-clipping algorithm on the merged ring. See [CPU Ear-Clipping Algorithm](#cpu-ear-clipping-algorithm) below.

Outline indices are generated separately from the original (un-merged) contours via `GpuTriangulator.generateOutlineIndices()`, producing `GL_LINES` index pairs for each consecutive vertex pair plus a closing segment.

#### Antimeridian Handling

Polygons that cross the antimeridian (±180° longitude) are detected via `record.isBoundaryCrossesAntimeridian()` and fall back to GLU tessellation. The reason: `repeatLocationsAroundDateline()` splits these polygons into multiple disjoint contour segments that can't be represented as a single simple polygon for ear-clipping. This is a rare case (only for polygons straddling the date line).

```java
for (Record record : records) {
    for (int b = 0; b < record.getBoundaryCount(); b++) {
        if (record.isBoundaryCrossesAntimeridian(b))
            return false; // fall back to GLU
    }
}
```

#### Vertex Collection

`collectBoundaryVertices()` walks the shapefile record's boundary points, applying:
- **Effective area filtering**: vertices with effective area below the tile's resolution threshold are skipped (level-of-detail simplification, same as the GLU path)
- **Coordinate offset**: longitude and latitude are offset by the tile centroid for numerical stability (prevents floating-point precision loss with large geographic coordinates)

The result is a flat `ArrayList<Float>` of (x, y) pairs shared across all records in the tile.

---

### Bridge Edge Insertion for Holes

**File:** `GpuTriangulator.java` (`bridgeHoles`)

The ear-clipping algorithm can only triangulate simple polygons (no holes). To handle polygons with holes, bridge edges are inserted to connect each hole to the outer ring, creating a single simple polygon that traces the outer boundary, dips into each hole, and returns.

#### Algorithm (Eberly / O'Rourke)

1. **Sort holes by rightmost x-coordinate (descending).**
   Processing the rightmost hole first ensures that when we cast a ray rightward from a hole's rightmost vertex, it won't accidentally intersect a not-yet-merged hole.

   ```java
   Arrays.sort(order, (a, b) -> {
       float maxA = maxX(vertices, holeStarts[a], holeCounts[a]);
       float maxB = maxX(vertices, holeStarts[b], holeCounts[b]);
       return Float.compare(maxB, maxA);
   });
   ```

2. **For each hole, find its rightmost vertex P.**
   Ties are broken by y-coordinate (higher y wins). This vertex is the bridge endpoint on the hole side.

3. **Cast a horizontal ray from P rightward.**
   Find the closest edge of the (already-merged) outer ring that the ray intersects. The intersection point I and the edge index are recorded.

   ```java
   // Does horizontal ray from (px,py) going right intersect edge (a,b)?
   if ((ay > py) != (by > py)) {
       float t = (py - ay) / (by - ay);
       float ix = ax + t * (bx - ax);
       if (ix >= px) { /* candidate */ }
   }
   ```

4. **Determine the mutually visible vertex M.**
   The initial candidate is the endpoint of the intersected edge with the larger x-coordinate. Then all ring vertices inside the triangle (P, I, M) are tested — any vertex inside this triangle that minimizes the angle ∠MPv is a better bridge target (it's more directly visible from P).

   ```java
   if (isInsideTriangle(px, py, closestIx, py, mx, my, vx, vy)) {
       float tan = dy / dx; // smaller = closer to the horizontal ray
       if (tan < bestTan) bestRingIdx = j;
   }
   ```

5. **Splice the hole into the ring.**
   Insert the sequence `P, hole[rightmost+1], hole[rightmost+2], ..., hole[rightmost], P, M` into the ring immediately after M. This creates a bridge edge from M to P, traces the hole, and returns via bridge edge from P back to M.

   ```java
   // After splice: ..., M, P, hole..., P, M, ...
   ring.addAll(mRingIdx + 1, insertion);
   ```

The result is a single index list describing a simple polygon that includes all hole geometry via bridge edges. This merged ring is then passed to the ear-clipping triangulator.

---

### CPU Ear-Clipping Algorithm

**File:** `GpuTriangulator.java` (`triangulateCPU`)

The CPU ear-clipping algorithm triangulates a simple polygon (no holes — holes must be merged first via `bridgeHoles`). It operates on a flat vertex array with a linked-list structure for O(1) vertex removal.

#### Data Structures

```java
int[] prev  = new int[n];  // linked list: previous vertex index
int[] next  = new int[n];  // linked list: next vertex index
int[] flags = new int[n];  // -1=removed, 0=reflex, 1=convex (ear candidate)
```

#### Algorithm

1. **Winding detection**: compute the signed area of the polygon to determine winding direction. This determines which cross-product sign indicates a convex vertex.

   ```java
   double area = 0;
   for (int i = 0; i < n; i++) {
       area += vertices[vi * 2] * vertices[vj * 2 + 1];
       area -= vertices[vj * 2] * vertices[vi * 2 + 1];
   }
   float winding = area >= 0 ? 1f : -1f;
   ```

2. **Classify vertices**: each vertex is classified as convex (flag=1) or reflex (flag=0) based on the cross product of its adjacent edges multiplied by the winding direction.

   ```java
   float c = cross2Df(vertices, ring[prev[i]], ring[i], ring[next[i]]);
   flags[i] = (c * winding >= 0) ? 1 : 0;
   ```

3. **Ear scanning**: scan the linked list for a convex vertex whose triangle (prev, current, next) contains no reflex vertices. Only reflex vertices need to be tested for containment (convex vertices can never be inside an ear triangle).

   ```java
   // Only check reflex vertices for containment
   if (flags[chk] == 0 && pointInTriF(vertices, ring[chk], ring[pi], ring[cur], ring[ni])) {
       isEar = false;
       break;
   }
   ```

4. **Ear removal**: when an ear is found, output its triangle (prev, ear, next), remove the ear vertex from the linked list, and reclassify the two neighbors (they may have changed from reflex to convex or vice versa).

   ```java
   tris[triIdx++] = ring[pi];
   tris[triIdx++] = ring[ear];
   tris[triIdx++] = ring[ni];

   // Remove from linked list
   next[pi] = ni;
   prev[ni] = pi;

   // Reclassify neighbors
   flags[pi] = (cross2Df(...) * winding >= 0) ? 1 : 0;
   flags[ni] = (cross2Df(...) * winding >= 0) ? 1 : 0;
   ```

5. **Final triangle**: when 3 vertices remain, output the last triangle directly.

#### Complexity

- **Best case**: O(n) for convex polygons (every vertex is an ear)
- **Worst case**: O(n²) for highly concave polygons (each ear scan traverses all reflex vertices)
- **Typical case**: O(n·r) where r is the number of reflex vertices, usually << n for geographic polygons

---

### GPU Compute Shader Tessellation

**File:** `GpuTriangulator.java` (`triangulateBatch`, compute shader source)

A GLSL 4.3 compute shader implements the same ear-clipping algorithm on the GPU, enabling polygon-level parallelism: a tile with 200 polygons dispatches 200 workgroups simultaneously.

#### Shader Design

```glsl
#version 430
layout(local_size_x = 1) in;  // one thread per workgroup, one polygon per workgroup
```

**SSBO layout (5 bindings):**

| Binding | Name | Type | Purpose |
|---------|------|------|---------|
| 0 | `Vertices` | `vec2[]` readonly | All vertex positions (shared across polygons) |
| 1 | `RingIdx` | `int[]` readonly | Merged ring indices (packed sequentially per polygon) |
| 2 | `PolyDescs` | `ivec4[]` readonly | Per-polygon descriptor: (ringStart, vertexCount, triOutputStart, padding) |
| 3 | `TriOutput` | `int[]` writeonly | Output triangle indices (groups of 3) |
| 4 | `Scratch` | `int[]` | Per-polygon linked list workspace: prev[], next[], flags[] |

**Scratch memory layout:**
Each polygon gets `MAX_POLYGON_VERTICES * 3` ints of scratch space:
- `scratch[sBase .. sBase+maxVerts-1]` = prev array
- `scratch[sBase+maxVerts .. sBase+2*maxVerts-1]` = next array
- `scratch[sBase+2*maxVerts .. sBase+3*maxVerts-1]` = flags array

Where `sBase = gl_WorkGroupID.x * u_maxVerts`.

#### Dispatch Flow

```java
// Upload vertices, ring indices, and polygon descriptors to SSBOs
gl4.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, vertexSSBO);
// ... bind all 5 SSBOs ...

// Dispatch one workgroup per polygon
gl4.glDispatchCompute(numPolygons, 1, 1);
gl4.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT);

// Read back triangle indices
gl4.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, size, result);
```

#### Current Status

The GPU tessellation path is fully implemented and compiles but is not yet wired into the tile loading pipeline. Currently, `tessellateEarClipping` uses `GpuTriangulator.triangulateCPU()` on the background thread. The GPU path requires a render-thread dispatch (compute shaders can only run on the GL thread), which will be integrated in a future update.

#### Safety Features

- `MAX_POLYGON_VERTICES = 4096`: polygons exceeding this are handled by the CPU path
- Safety counter (`n * n` iterations) prevents infinite loops on degenerate input
- SSBOs are lazily allocated and grown as needed via `ensureSSBO()`
- GL4 availability is checked at initialization (`gl.isGL4()`, `GL_MAX_COMPUTE_WORK_GROUP_COUNT`)
- Falls back to CPU ear-clipping when compute shaders are unavailable

---

### Outline Index Generation

**File:** `GpuTriangulator.java` (`generateOutlineIndices`)

Outlines are generated from the original (un-merged) contours, not from the bridged polygon. This ensures that bridge edges don't appear as visible outline segments.

For each contour with N vertices, `2 * N` indices are generated as `GL_LINES` pairs:

```java
// Vertex i → vertex i+1, with wraparound
indices[i * 2]     = startIdx + i;
indices[i * 2 + 1] = startIdx + (i + 1) % count;
```

Each boundary (outer ring and each hole) generates its own closed outline loop. These are concatenated into a single outline index buffer per record.

---

## Rendering Optimizations

### 1. VBO Rendering

**File:** `ShapefilePolygons.java` (draw method)

Vertex data is uploaded to GPU-resident Vertex Buffer Objects on first use and cached via `GpuResourceCache`. Subsequent frames reference the VBO by ID with zero CPU-to-GPU transfer.

- `GL_STATIC_DRAW` hint tells the driver the data won't change
- VBO IDs are cached per-geometry and invalidated only when geometry changes
- Toggle: **V** key in benchmark

**Impact:** Eliminates per-frame vertex data transfer. Major FPS improvement on discrete GPUs.

### 2. Merged Draw Calls

**File:** `ShapefilePolygons.java` (assembleMergedBuffers, drawMerged)

The original renderer issued 2 draw calls per attribute group (interior triangles + outline lines). With N attribute groups per tile, that's 2N draw calls. Merged rendering combines all interiors into a single `GL_TRIANGLES` call and all outlines into a single `GL_LINES` call by using per-vertex colors.

How it works:
- `assembleMergedBuffers()` builds combined index buffers across all attribute groups
- Per-vertex RGBA color arrays encode each record's interior/outline color
- `GL_COLOR_ARRAY` with `GL_UNSIGNED_BYTE` provides the colors
- Result: exactly 2 draw calls per tile regardless of attribute group count

Toggle: **M** key in benchmark

**Impact:** Reduces draw calls from 2N to 2 per tile. Significant improvement when many attribute groups exist (e.g., randomly colored records).

### 3. Short Index Buffers

**File:** `ShapefilePolygons.java` (assembleMergedBuffers, drawMerged)

When a tile's vertex count is <= 65,535, merged index buffers use `GL_UNSIGNED_SHORT` instead of `GL_UNSIGNED_INT`. This halves index buffer size and reduces GPU memory bandwidth for index reads.

- `ShapefileGeometry.mergedIndexType` tracks `GL_UNSIGNED_SHORT` vs `GL_UNSIGNED_INT`
- `assembleMergedBuffers()` creates `ShortBuffer` or `IntBuffer` accordingly
- VBO upload and draw calls use the correct type and byte size
- Falls back to 32-bit indices transparently for tiles exceeding 65K vertices

**Impact:** 50% reduction in index buffer size and bandwidth for most tiles.

### 4. Scissor Test Clipping

**File:** `ShapefilePolygons.java` (applyClipSector, endDrawing)

Replaced legacy `GL_CLIP_PLANE0..3` with `GL_SCISSOR_TEST`. The old approach required 8 GL calls per tile (4x `glEnable` + 4x `glClipPlane`), and clip planes are often software-emulated on modern GPUs. The scissor test is a single `glScissor` call and is free in hardware.

The scissor rectangle is computed by mapping the shapefile tile's sector to pixel coordinates within the surface tile FBO viewport:

```java
double pxMin = (sector.minLon - tileSector.minLon) * (viewportWidth / tileSector.deltaLon);
// ... similar for other edges
gl.glScissor(sx, sy, sw, sh);
```

The vertex offset cancels out in the geographic-to-pixel transform, so scissor bounds depend only on the shapefile tile sector and the surface tile sector.

**Impact:** Reduces 8 GL calls to 1 per tile. Avoids software-emulated clip planes.

### 5. Shader Pipeline

**Files:** `ShapefilePolygons.java`, `ShaderProgram.java`

The merged rendering path optionally uses a GLSL 330 vertex+fragment shader instead of the fixed-function pipeline (`glVertexPointer`, `glColorPointer`, `glLoadMatrix`).

Vertex shader:
```glsl
#version 330
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec4 aColor;
uniform mat4 uMVP;
out vec4 vColor;
void main() {
    gl_Position = uMVP * vec4(aPosition, 0.0, 1.0);
    vColor = aColor;
}
```

Fragment shader:
```glsl
#version 330
in vec4 vColor;
out vec4 fragColor;
void main() {
    fragColor = vColor;
}
```

Benefits:
- `glVertexAttribPointer` with explicit locations replaces fixed-function attribute setup
- MVP matrix computed on CPU and uploaded as a single uniform (no matrix mode switching, no push/pop)
- Projection matrix queried once per FBO pass via `glGetFloatv(GL_PROJECTION_MATRIX)` and combined with the per-tile modelview using a CPU 4x4 multiply
- Modern driver fast paths (drivers internally translate fixed-function to shaders anyway)
- Foundation for future instanced rendering

Falls back to fixed-function if GLSL 330 is unavailable. Toggle: **S** key in benchmark.

**Impact:** Eliminates fixed-function translation overhead in the driver. Enables future instanced rendering.

### 6. Bounding Extent Cache

**File:** `ShapefilePolygons.java` (getOrComputeExtent)

`Sector.computeBoundingBox()` was called every frame for every tile in the quadtree traversal. This computation involves globe elevation lookups, 9 Cartesian point computations, and PCA-based oriented bounding box construction.

Now cached in a `HashMap<Sector, Extent>` that auto-invalidates when the globe or vertical exaggeration changes. Tile sectors are immutable, so the cache has excellent hit rates.

**Impact:** Eliminates the most expensive per-frame CPU cost during tile selection.

### 7. Matrix Translation Optimization

**File:** `ShapefilePolygons.java` (draw method)

The per-tile modelview matrix was computed as:
```java
Matrix modelview = sdc.getModelviewMatrix().multiply(Matrix.fromTranslation(geom.vertexOffset));
```

This allocated 2 Matrix objects (17 fields each) and performed a full 4x4 multiply (64 multiplications + 48 additions) per tile per frame.

Now the SurfaceTileDrawContext base matrix is cached once per FBO pass in a `double[16]` array, and the per-tile vertex offset translation is applied directly to column 3 (4 dot products, zero allocations):

```java
System.arraycopy(baseMatrixArray, 0, matrixArray, 0, 16);
matrixArray[12] += matrixArray[0] * tx + matrixArray[4] * ty + matrixArray[8] * tz;
matrixArray[13] += matrixArray[1] * tx + matrixArray[5] * ty + matrixArray[9] * tz;
matrixArray[14] += matrixArray[2] * tx + matrixArray[6] * ty + matrixArray[10] * tz;
matrixArray[15] += matrixArray[3] * tx + matrixArray[7] * ty + matrixArray[11] * tz;
```

**Impact:** Eliminates 2 object allocations and reduces arithmetic per tile.

### 8. Tile Subdivision Caching

**File:** `ShapefilePolygons.java` (ShapefileTile.subdivide)

`ShapefileTile.subdivide()` was called every frame during quadtree traversal, creating 4 new `ShapefileTile` objects and 4 new `Sector` objects each time. Since tile sectors are immutable and the tree structure doesn't change, children are now cached on first subdivision and reused across frames.

**Impact:** Eliminates ~100+ object allocations per frame with deep quadtrees.

---

## Benchmark

`ShapefileBenchmark` (`gov.nasa.worldwindx.examples.ShapefileBenchmark`) loads three shapefile layers for stress testing:

1. **TM_WORLD_BORDERS** — global country polygons
2. **ne_10m_land** — high-detail Natural Earth land polygons (if available)
3. **florida_coast_test** — 1024 generated polygons off Florida's east coast, including concave shapes and polygons with holes

### Benchmark Controls

| Key | Toggle | Stat Label |
|-----|--------|------------|
| V | VBO vs client memory | VBO / CLT |
| M | Merged vs per-group draw calls | MRG / 2N |
| E | Ear-clipping vs GLU tessellation | (affects load, not FPS) |
| S | Shader vs fixed-function pipeline | SH / FF |

Stats are reported every 3 seconds in the title bar and console: FPS, average/min/max frame time, total frame count. Stats reset on each toggle for clean A/B comparison.

### Test Shapefile Generator

`ShapefileGenerator` (`gov.nasa.worldwindx.examples.util.ShapefileGenerator`) creates test shapefiles with configurable polygon counts:

```
java gov.nasa.worldwindx.examples.util.ShapefileGenerator [basePath] [count]
```

Default: 1024 polygons in a 32x32 grid off Florida's east coast (lat 24-28N, lon 79.5-76W). Polygon mix:
- 40% rectangles (randomized sizes)
- 20% triangles
- 20% hexagons
- 10% L-shapes (concave — tests ear-clipping with reflex vertices)
- 10% rectangles with 1-2 holes (tests bridge edge insertion)

---

## Files

| File | Description |
|------|-------------|
| `ShapefilePolygons.java` | Core shapefile polygon renderer: tessellation, attribute assembly, VBO, merged draws, short indices, scissor test, shader pipeline, extent cache, matrix optimization, tile caching |
| `GpuTriangulator.java` | Polygon triangulation engine: CPU ear-clipping, bridge edge insertion for holes, GPU compute shader triangulation, outline index generation |
| `ShaderProgram.java` | OpenGL shader program manager: vertex+fragment compilation, linking, uniform caching |
| `ShapefileBenchmark.java` | Benchmark harness: loads test shapefiles, measures FPS/frame times, toggle keys for A/B comparison |
| `ShapefileGenerator.java` | Test shapefile generator: creates ESRI .shp/.shx/.dbf files with configurable polygon counts and shapes |
