# GPU Tessellation Improvements

This document describes the GPU-accelerated tessellation work added to WorldWind Reforged. Phase 3 work (surface shape tessellation) moved CPU tessellation and triangulation to OpenGL 4.3 compute shaders. Phase 4 work moves terrain rendering itself onto the GPU — starting with a GLSL 1.30 shader path for terrain tile rendering and heightmap elevation upload (Task 4.1), leading toward full GPU LOD and compute-shader mesh generation in subsequent tasks.

---

## Overview

**Phase 3 — Surface Shape GPU Acceleration (complete)**

| Component | File | Purpose | GL Requirement |
|-----------|------|---------|----------------|
| **GpuTessellator** | `GpuTessellator.java` | Great-circle/rhumb/linear arc interpolation | GL 4.3+ |
| **GpuTriangulator** | `GpuTriangulator.java` | Polygon ear-clipping triangulation | GL 4.3+ |
| **VBO Outline Cache** | `AbstractSurfaceShape.java` | Zero-copy outline rendering via GPU-resident buffers | GL 4.3+ |

**Phase 4 — Terrain GPU Rendering (REVERTED)**

> **Note (seaglassfoundry.com, 2026-04-14):** All Phase 4 terrain GPU rendering
> (TerrainShader, TessellationTerrainShader, ComputeMeshShader, heightmap upload,
> VAO fast paths, crack-free LOD stitching) has been removed. These features caused
> persistent tile stitching/cracking artifacts that could not be resolved across
> multiple sessions. Terrain tiles now render exclusively via the original WorldWind
> Java fixed-function pipeline (glVertexPointer, glTexCoordPointer, GL_TRIANGLE_STRIP).
> Phase 3 surface shape GPU work (GpuTessellator, GpuTriangulator) is retained.

Phase 3 surface shape GPU acceleration is integrated into the existing surface shape pipeline and activates transparently when GL 4.3 is available.

---

## GpuTessellator — Compute Shader Arc Interpolation

**File:** `src/gov/nasa/worldwind/render/shaders/GpuTessellator.java` (~1,081 lines)

### What It Replaces

The original `AbstractSurfaceShape.addIntermediateLocations()` iterated over every edge of every surface shape on the CPU, computing intermediate points along great-circle, rhumb line, or linear paths using Java trigonometry. For shapes with many edges (country borders, coastlines, complex polygons), this was a major per-cache-miss CPU cost.

### How It Works

A single GLSL 4.3 compute shader dispatch interpolates all edges in parallel:

- **One workgroup per edge** — each pair of consecutive vertices is an independent workgroup
- **256 threads per workgroup** — each thread computes one intermediate point (local_size_x = 256)
- **Three path types** supported via `u_pathType` uniform:
  - `PATH_GREAT_CIRCLE` (0): Spherical linear interpolation (slerp) on the unit sphere using the Vincenty formula
  - `PATH_RHUMB_LINE` (1): Loxodrome interpolation via Mercator projection with proper pole handling
  - `PATH_LINEAR` (2): Simple linear interpolation in degree space

### Shader I/O

| SSBO Binding | Direction | Format | Content |
|-------------|-----------|--------|---------|
| 0 | Input | `vec4` | Edge endpoints in radians: `(lon1, lat1, lon2, lat2)` |
| 1 | Input | `ivec2` | Per-edge params: `(numIntervals, outputOffset)` |
| 2 | Output | `vec2` | Interpolated points in degrees: `(lon, lat)` |

### Integration

Integrated into `AbstractSurfaceShape.addIntermediateLocations()`:

1. On first call, lazily creates a `GpuTessellator` singleton shared across all surface shapes
2. If GL 4.3 is available, dispatches the compute shader with all edges batched into a single GPU call
3. Results are read back to `List<LatLon>` and cached in the existing geometry cache
4. GPU tessellation allows up to 256 intervals per edge (vs typical CPU limits), producing smoother curves
5. If init fails or GL 4.3 is unavailable, sets `gpuTessellatorFailed = true` and falls back to CPU path permanently

### Ellipse Support

`GpuTessellator.tessellateEllipse()` provides a separate compute shader path specifically for `SurfaceEllipse`. It generates evenly-spaced points around an ellipse perimeter on the GPU, replacing the CPU loop in `SurfaceEllipse.computeLocations()`.

---

## GpuTriangulator — Compute Shader Polygon Triangulation

**File:** `src/gov/nasa/worldwind/render/shaders/GpuTriangulator.java` (~828 lines)

### What It Replaces

The legacy path used `GLUtessellator`, a 1990s callback-based API that:
- Fired a Java→JNI callback per vertex, triangle, and contour transition
- Could only process one polygon at a time (no batching)
- Had opaque internals with no control over the algorithm

### How It Works

The ear-clipping algorithm runs on the GPU with polygon-level parallelism:

- **One workgroup per polygon** — a tile with 200 polygons dispatches 200 simultaneous workgroups
- **Hole handling** via bridge edge insertion (CPU preprocessing merges holes into outer ring)
- **Maximum 4,096 vertices** per polygon; larger polygons fall back to CPU

### Shader I/O (5 SSBOs)

| Binding | Name | Type | Purpose |
|---------|------|------|---------|
| 0 | `Vertices` | `vec2[]` readonly | All vertex positions (shared across polygons) |
| 1 | `RingIdx` | `int[]` readonly | Merged ring indices per polygon |
| 2 | `PolyDescs` | `ivec4[]` readonly | Per-polygon descriptor: (ringStart, vertexCount, triOutputStart, pad) |
| 3 | `TriOutput` | `int[]` writeonly | Output triangle indices (groups of 3) |
| 4 | `Scratch` | `int[]` | Working memory for linked lists (prev/next/flags) |

### Algorithm

The GPU shader mirrors the CPU ear-clipping algorithm:

1. **Winding detection** — compute signed area to determine vertex ordering
2. **Classify vertices** — mark each as convex or reflex based on cross product
3. **Ear scanning** — find convex vertices whose triangle contains no reflex vertices
4. **Ear removal** — output triangle, remove vertex from linked list, reclassify neighbors
5. **Safety counter** — `n * n` iteration limit prevents infinite loops on degenerate input

### Bridge Edge Insertion (Hole Merging)

`GpuTriangulator.bridgeHoles()` converts polygons with holes into simple polygons using the Eberly/O'Rourke algorithm:

1. Sort holes by rightmost x-coordinate (descending)
2. For each hole, find its rightmost vertex P
3. Cast a horizontal ray rightward from P to find the nearest visible outer ring vertex M
4. Splice the hole into the ring at M, creating bridge edges M→P and P→M

### CPU Fallback

`GpuTriangulator.triangulateCPU()` provides the same ear-clipping algorithm in pure Java, used when:
- GL 4.3 compute shaders are unavailable
- A polygon exceeds 4,096 vertices
- The GPU path fails for any reason

---

## VBO Outline Cache — Zero-Copy Outline Rendering

**File:** `src/gov/nasa/worldwind/render/AbstractSurfaceShape.java`

### What It Replaces

Previously, every frame rebuilt outline vertex arrays on the CPU and pushed them to the GPU via `glVertexPointer()` with client-side memory. This meant per-frame CPU work proportional to total outline vertex count.

### How It Works

1. On first render (cache miss), `buildOutlineVBOs()` uses `GpuTessellator.tessellateToVBO()` to upload outline vertices directly into GPU-resident Vertex Buffer Objects
2. The `TessellationResult` (VBO ID + vertex count) is cached in a per-shape `vboCache` keyed by geometry key
3. On subsequent frames, `drawOutlineFromVBOs()` binds the cached VBO and issues `glDrawArrays()` — zero CPU vertex work
4. VBO lifecycle is managed with deferred deletion via `pendingVBODeletes` for thread safety (VBOs can be evicted from any thread but must be deleted on the GL thread)

---

## Performance Impact

### Arc Tessellation (GpuTessellator)

| Metric | CPU Path | GPU Path | Improvement |
|--------|----------|----------|-------------|
| Edge parallelism | Sequential (1 edge at a time) | All edges simultaneously | Proportional to edge count |
| Points per edge | Typically 10-50 | Up to 256 | Smoother curves at no extra CPU cost |
| Trig operations | Java `Math.sin/cos/acos` | GPU shader units (massively parallel) | Orders of magnitude for large shapes |
| Cache miss cost | Blocks render thread | Single compute dispatch + readback | Reduced latency |

### Polygon Triangulation (GpuTriangulator)

| Metric | GLU Tessellator | GPU Ear-Clipping | Improvement |
|--------|----------------|------------------|-------------|
| Polygon parallelism | 1 polygon at a time | All polygons simultaneously | Proportional to polygon count |
| Per-vertex overhead | JNI callback per vertex | Zero callbacks (array-based) | Eliminated JNI overhead |
| Batch processing | Not supported | Native (one dispatch for entire tile) | Major improvement for dense tiles |

### Outline Rendering (VBO Cache)

| Metric | Client Memory | VBO Cache | Improvement |
|--------|--------------|-----------|-------------|
| Per-frame CPU work | Rebuild vertex arrays every frame | Zero (bind cached VBO) | Eliminated |
| CPU→GPU transfer | Every frame | Once (on cache miss) | Eliminated per-frame transfer |
| Draw call overhead | `glVertexPointer` + client pointer | `glBindBuffer` + `glDrawArrays` | Faster driver path |

### Where You'll See the Difference

- **Complex surface shapes** (country borders, coastlines with thousands of vertices): Dramatically faster tessellation on cache miss, smoother curves from higher interpolation density
- **Shapefile layers** with hundreds of polygons per tile: Polygon-level parallelism means triangulation scales with GPU core count rather than being CPU-bound
- **Steady-state rendering** (camera not moving): VBO cache eliminates all per-frame CPU vertex work for outlines, freeing the CPU for other tasks
- **Zoomed-in views** with high edge density: 256 intervals per edge (vs ~10-50 on CPU) produces visibly smoother great-circle arcs

---

---

## Task 4.1 — Heightmap Terrain Renderer

**Status:** Complete

### What It Replaces

The original `RectangularTessellator.render()` path used the GL2 fixed-function texture combiner (`glTexEnvi(GL_MODULATE)`) to blend imagery and alpha mask tiles. Terrain vertices were uploaded via `glVertexPointer()` / `glTexCoordPointer()` with no shader involvement.

### How It Works

A GLSL 1.30 vertex+fragment shader (`TerrainShader`) activates for each terrain tile when GL 3.0+ is available and picking mode is off:

**Vertex Shader** — uses compatibility-profile built-ins (`gl_Vertex`, `gl_MultiTexCoord0`, `gl_TextureMatrix`, `gl_ModelViewProjectionMatrix`) so no changes to the existing VBO binding or `SurfaceTileRenderer` texture-matrix calls were needed. Passes texture-matrix-transformed coords to the fragment shader via `gl_TexCoord[0/1]`.

**Fragment Shader** — samples imagery (unit 0) and alpha mask (unit 1) explicitly; outputs `vec4(color.rgb, color.a * alphaSample.a)`.

**Heightmap texture** — `buildVerts()` extracts interior `(density+1)²` elevations into a `FloatBuffer` on `RenderInfo.pendingHeightmap`. On first render, `fillHeightmapTexture()` uploads this as a `GL_R32F` texture into `GpuResourceCache` on unit 3. The uniform `u_useHeightmap` is currently set to 0 (displacement is a no-op in Task 4.1); enabling it is reserved for Task 4.2.

### Fallback Conditions

- GL < 3.0: fixed-function path permanently
- Picking mode: fixed-function (flat color; no texture complexity needed)
- Three texture units active (tile outline debug mode): fixed-function
- Shader compilation failure: `terrainShaderInitFailed = true`, fixed-function permanently

### Capability Flag

`GLRuntimeCapabilities.isUseTerrainShader()` — returns `true` when `glVersion >= 3.0` AND the terrain shader is enabled (default: enabled). Can be overridden via `setTerrainShaderEnabled(false)` for testing.

### Files Modified

| File | Changes |
|------|---------|
| `src/gov/nasa/worldwind/render/shaders/TerrainShader.java` | New — GLSL 1.30 terrain tile shader with heightmap infrastructure |
| `src/gov/nasa/worldwind/terrain/RectangularTessellator.java` | TerrainShader integration in `render()`; `RenderInfo.pendingHeightmap` / `fillHeightmapTexture()`; heightmap extraction in `buildVerts()` |
| `src/gov/nasa/worldwind/render/GLRuntimeCapabilities.java` | Added `isTerrainShaderAvailable`, `isTerrainShaderEnabled`, `isUseTerrainShader()` |
| `src/gov/nasa/worldwind/render/shaders/ShaderProgram.java` | Added `setUniform3f()` for `u_refCenter` vec3 upload |

---

## Hardware Requirements

- **Terrain shader (Task 4.1):** OpenGL 3.0+
  - NVIDIA: GeForce 8 series+ (2006+)
  - AMD: Radeon HD 2000 series+ (2007+)
  - Intel: HD Graphics 2nd gen+ (Sandy Bridge, 2011+)
- **Tessellation shader (Task 4.2):** OpenGL 4.0+
  - NVIDIA: GeForce 400 series+ (Fermi, 2010+)
  - AMD: Radeon HD 6000 series+ (Northern Islands, 2010+)
  - Intel: HD Graphics 4th gen+ (Haswell, 2013+)
- **Compute mesh / frustum culling (Task 4.3):** OpenGL 4.3+ (compute shaders + SSBOs + indirect draw)
  - NVIDIA: GeForce 600 series+ (Kepler, 2012+)
  - AMD: Radeon HD 7000 series+ (GCN, 2012+)
  - Intel: HD Graphics 4th gen+ (Haswell, 2013+)
- **Full surface shape GPU acceleration:** OpenGL 4.3+ (compute shaders, SSBOs)
  - NVIDIA: GeForce 600 series+ (Kepler, 2012+)
  - AMD: Radeon HD 7000 series+ (GCN, 2012+)
  - Intel: HD Graphics 4th gen+ (Haswell, 2013+)
- **Fallback:** Any OpenGL 2.0 system runs the CPU/fixed-function path with identical visual output

---

## Task 4.2 — GPU LOD System (Tessellation Control/Evaluation Shaders)

**Status:** Complete

### What It Replaces

The `TerrainShader` (Task 4.1) still rendered a fixed triangle count per tile: the coarse
`(density+2)²` grid cells, each as two triangles.  Tiles close to the viewer generated far
too few triangles (blocky terrain); tiles far away generated far too many (wasted fill rate).

### How It Works

Each coarse grid cell becomes a GL_PATCHES 4-vertex quad patch.  Two new shader stages
bracket the existing vertex and fragment shaders:

**Tessellation Control Shader (TCS)**

Receives the 4 corners of each quad patch.  Invocation 0 projects all four corners through
`gl_ModelViewProjectionMatrix`, converts from NDC to screen-pixel coordinates using the
`u_viewport` uniform, measures the screen-space length of each outer edge, and sets:

```
gl_TessLevelOuter[k] = clamp(edge_pixels[k] / u_pixelsPerTriangle, 1.0, 64.0)
gl_TessLevelInner[0] = gl_TessLevelInner[1] = max(outer[0..3])
```

The default `u_pixelsPerTriangle = 8.0` means the GPU subdivides until each tessellated
triangle edge spans roughly 8 screen pixels.  Distant tiles (small screen footprint) get
level 1 (no extra triangles); close tiles get up to level 64 (4,096× per patch).

Behind-camera vertices are handled by clamping `|w|` to `1e-4` before the perspective
divide, preventing NaN or infinite screen-space coordinates.

**Tessellation Evaluation Shader (TES)**

`layout(quads, fractional_even_spacing, ccw)`:

- `quads` — bilinear interpolation domain; `gl_TessCoord.xy ∈ [0,1]²`
- `fractional_even_spacing` — smooth LOD morphing; avoids visible popping at tessellation-level boundaries
- `ccw` — matches WorldWind's counter-clockwise front-face winding

Bilinearly interpolates object-space positions and tile UVs from the 4 patch corners.
Applies the existing `SurfaceTileRenderer` texture matrices (`gl_TextureMatrix[0/1]`) to
produce `v_texCoord0/1` for the fragment shader.  Projects to clip space via
`gl_ModelViewProjectionMatrix`.

The coarse vertex positions already encode the full terrain elevation computed by
`RectangularTessellator.buildVerts()` on the CPU; the TES interpolates those corners
smoothly.  Sub-grid heightmap delta-correction displacement is added in Task 4.5 — see
that section for the derivation.

**Fragment Shader**

Identical to `TerrainShader`'s fragment shader: samples imagery (unit 0) × alpha mask
(unit 1), adapted to use explicit `v_texCoord0/1` varyings from the TES.

### Patch Index Buffer

`createPatchIndices(int density)` generates a new `IntBuffer` of `4 × (density+2)²` indices —
one 4-vertex patch per grid cell, vertex order BL/BR/TR/TL:

```
BL = (j  ) * stride + (i  )
BR = (j  ) * stride + (i+1)
TR = (j+1) * stride + (i+1)
TL = (j+1) * stride + (i  )
```

where `stride = density+3` (vertex row width including skirt).  The buffer is shared across
all tiles of the same density (like the existing triangle-strip index buffer) and uploaded to
a GPU VBO on first use via `fillPatchIndexVbo()`.

### Shader Selection Priority

```
GL 4.0+ available & isUseTessellation()  →  TessellationTerrainShader
GL 3.0+ available & isUseTerrainShader() →  TerrainShader
Otherwise / picking mode                  →  fixed-function
```

### Performance Impact

| Scenario | Triangle-strip (Task 4.1) | Tessellation (Task 4.2) |
|----------|--------------------------|------------------------|
| Tile fills 1/4 screen | ~800 triangles | ~800–3,200 (adaptive) |
| Tile fills 1/64 screen | ~800 triangles | ~12 (level 1) |
| Zoomed very close | ~800 triangles | Up to 51,200 (level 64) |

The primary win is the far-field case: a tile occupying only a few pixels now submits
~12 triangles instead of 800+, reducing vertex-shader and rasterization overhead
proportionally to the number of visible tiles.

### Files Modified

| File | Changes |
|------|---------|
| `src/gov/nasa/worldwind/render/shaders/TessellationTerrainShader.java` | New — GLSL 4.00 vert+TCS+TES+frag tessellation pipeline |
| `src/gov/nasa/worldwind/terrain/RectangularTessellator.java` | `createPatchIndices()`; `renderVBOTessellated/renderVATessellated`; `bindVbosTessellated`; `fillPatchIndexVbo`; tessellation path in `render()` |
| `src/gov/nasa/worldwind/render/GLRuntimeCapabilities.java` | Added `isUseTessellation()` capability flag (GL 4.0+) |
| `src/gov/nasa/worldwind/render/shaders/ShaderProgram.java` | Added `initTessellation()` for 4-stage pipeline; updated `compileShader()` log; updated `dispose()` |

---

## Task 4.3 — Compute Shader Mesh Generation / GPU Frustum Culling

**Status:** Complete

### What It Replaces

`renderVBOTessellated()` issued a single `glDrawElements(GL_PATCHES, totalPatches × 4, ...)` covering every coarse grid patch in the tile — even those that were entirely off-screen or behind the camera. Although the tessellation control shader handles screen-space LOD, it cannot skip patches that are outside the view frustum; those are still processed (and quickly discarded) by the rasterizer.

### How It Works

A GLSL 4.30 compute shader (`ComputeMeshShader`) runs before the tessellation draw and produces a compact index buffer containing only the visible patches.  The tessellation shader pipeline then consumes it via `glDrawElementsIndirect`.

**Compute shader (GLSL 4.30, `local_size_x = 64`)**

One thread per coarse quad patch.  Each thread:

1. Reads the patch's 4 vertex indices from SSBO binding 1 (source patch index buffer)
2. Loads the 3 packed XYZ floats per vertex from SSBO binding 0 (tile vertex VBO, reused without copying)
3. Tests each corner against the 6 frustum planes.  A patch is culled only if **all 4 corners** are on the outside of the **same** plane — a conservative test that never discards a patch that even partially intersects the frustum
4. If the patch is visible, `atomicAdd(dc_count, 4)` reserves space in the output SSBO and writes the 4 indices

**Frustum planes in tile-local space**

`getFrustumInModelCoordinates()` returns planes in ECEF.  Each plane `(a, b, c, d)` satisfies `dot(n, ECEF_point) + d ≥ 0` for points inside.  Substituting `ECEF = local + refCenter`:

```
dot(n, local) + (d + dot(n, refCenter)) ≥ 0
```

So the per-tile adjusted plane is `(a, b, c, d + dot(n, refCenter))`, computed in `buildFrustumPlanes()` and uploaded as `uniform vec4 u_frustumPlanes[6]`.

**SSBO layout**

| Binding | Content | Notes |
|---------|---------|-------|
| 0 | Tile vertex positions (`float[]`, 3 per vertex) | Existing vertex VBO bound as SSBO — zero copy |
| 1 | Source patch indices (`uint[]`, 4 per patch) | Uploaded once per density from `patchIndexLists`; static |
| 2 | Output patch indices (`uint[]`, 4 per visible patch) | Allocated at worst-case size (all patches visible) |
| 3 | `glDrawElementsIndirect` command (`uint[5]`) | `dc_count` filled atomically; remaining fields pre-set to `{0, 1, 0, 0, 0}` |

**Draw sequence**

```
tessellationShader.activate(...)         // tessellation program active, uniforms set
bindVbosTessellated(...)                 // vertex VBO bound to GL_ARRAY_BUFFER; texcoord VBO bound
                                         // patch IBO uploaded to GPU cache (used by non-compute path)

// Save tessellation program ID via glGetIntegerv(GL_CURRENT_PROGRAM)
glUseProgram(computeProgram)
glUniform1i(u_numPatches, ...)
glUniform4fv(u_frustumPlanes, 6, ...)
glDispatchCompute(ceil(numPatches / 64), 1, 1)
glMemoryBarrier(GL_COMMAND_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT)
glUseProgram(savedTessellationProgram)   // restore tessellation shader

glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, dstSsboId)     // culled index buffer
glBindBuffer(GL_DRAW_INDIRECT_BUFFER,  drawCmdSsbo)  // { count, 1, 0, 0, 0 }
glDrawElementsIndirect(GL_PATCHES, GL_UNSIGNED_INT, 0)
glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0)

tessellationShader.deactivate(...)
```

No CPU readback at any point.  The GPU writes the count and reads it from the same buffer.

### Capability Flag

`GLRuntimeCapabilities.isUseComputeMesh()` — returns `true` when `glVersion >= 4.3` AND compute mesh is enabled (default: enabled).  Can be overridden via `setComputeMeshEnabled(false)`.

### Shader Selection Priority

```
GL 4.3+ & isUseComputeMesh() & VBOs available  →  ComputeMeshShader + TessellationTerrainShader
GL 4.0+ & isUseTessellation()                  →  TessellationTerrainShader (all patches)
GL 3.0+ & isUseTerrainShader()                 →  TerrainShader (triangle strip)
Otherwise / picking mode / 3-unit path          →  fixed-function
```

### Performance Impact

| Scenario | Tessellation only (Task 4.2) | + Compute culling (Task 4.3) |
|----------|------------------------------|------------------------------|
| Tile fully on screen | All `(density+2)²` patches drawn | Same |
| Tile half off-screen | All patches still submitted | ~50% fewer patches in index buffer |
| Tile nearly edge-on (horizon) | All patches submitted; TCS sets level 1 | Majority of patches culled before TCS |
| Multiple overlapping tiles from adjacent LOD | All patches from each tile | Off-screen patches eliminated per-tile |

The primary win is at tile edges and horizon tiles that partially intersect the frustum — common during globe panning and at low camera altitudes.

### Files Modified

| File | Changes |
|------|---------|
| `src/gov/nasa/worldwind/render/shaders/ComputeMeshShader.java` | New — GLSL 4.30 compute shader for GPU patch culling; SSBO management; `dispatchAndDraw()` |
| `src/gov/nasa/worldwind/terrain/RectangularTessellator.java` | `computeMeshShader` / `computeMeshShaderInitFailed` fields; compute mesh path in `render()`; `renderVBOComputeTessellated()` |
| `src/gov/nasa/worldwind/render/GLRuntimeCapabilities.java` | Added `isUseComputeMesh()` capability flag (GL 4.3+) |

---

## Task 4.4 — Crack-Free LOD Stitching

**Status:** Complete

### Problem: T-Junction Cracks at Tile Boundaries

The TCS computes tessellation levels independently per tile by projecting each patch's
corner vertices to screen space.  Two adjacent same-density tiles see the same shared-edge
geometry (identical world-space positions after adding their respective refCenters), so they
should in principle compute the same levels.  In practice, floating-point rounding differences
in the per-tile screen-space projection, combined with slightly different tile reference centers
shifting the vertex coordinates, can produce off-by-one level mismatches.  When patch A
tessellates its east edge at level 8 and patch B (immediately to the east) tessellates its
west edge at level 7, the generated vertex positions no longer coincide — producing a visible
crack or Z-fight at the boundary.

### Solution: CPU-Side Min-Constraint Pre-Pass

`constrainNeighbourLevels(DrawContext dc)` runs once per `tessellate()` call, after all
`RenderInfo` objects have been built but before the tiles are handed back to the renderer.

1. **Level estimation** — `computeEdgeLevels()` projects the four geographic corners of
   each tile's sector to screen space via `dc.getView().project()` and applies the same
   pixel-per-triangle formula as the TCS:

   ```
   level[k] = clamp(screen_pixels(edge_k) / u_pixelsPerTriangle, 1, 64)
   ```

   Edge indices match `gl_TessLevelOuter`:

   | Index | Edge | Corners |
   |-------|------|---------|
   | 0 | West (left) | TL → BL |
   | 1 | South (bottom) | BL → BR |
   | 2 | East (right) | BR → TR |
   | 3 | North (top) | TR → TL |

2. **Adjacency detection** — all pairs of same-density visible tiles are compared.  Two
   tiles are adjacent if their shared latitude/longitude boundary agrees to within 1e-10°.

3. **Level constraint** — for each shared edge, both entries are replaced with their
   minimum:
   ```
   constrained = min(own_level, neighbour_level)
   ```
   The mutual assignment ensures symmetry: both sides of the edge always cap to the same
   value regardless of which tile is processed first.

4. **Uniform upload** — the constrained levels are stored in `RenderInfo.constrainedOuterLevels`
   and passed to `TessellationTerrainShader.activate()` as `maxOuterLevels`.  In the TCS:
   ```glsl
   gl_TessLevelOuter[k] = min(clamp(px / p, 1.0, 64.0), u_maxOuter[k]);
   ```
   The TCS still computes its fine-grained per-patch level from screen-space geometry; the
   CPU constraint is only an upper bound.  Patches whose computed level already falls below
   the cap are unaffected.

### Scope Limitation: Different-Density Tiles

When two adjacent tiles have **different densities** (different geographic LOD levels), their
vertex grids along the shared edge have different spacings.  No amount of tessellation level
matching can avoid T-junctions there — the coarse tile has fewer edge vertices than the fine
tile.  This case is handled by the existing **skirt geometry**: each tile renders a
min-elevation border row/column that drops below the terrain surface, closing the visual gap
between mismatched grids.

### Performance

The O(N²) adjacency scan is fast for typical visible tile counts (20–100 tiles):
- N=100: ~4,950 comparisons, each being 4 floating-point comparisons
- Plus 4×N `globe.computePointFromPosition()` + `view.project()` calls

Total overhead is well under 1 ms per frame.

### Files Modified

| File | Changes |
|------|---------|
| `src/gov/nasa/worldwind/terrain/RectangularTessellator.java` | `constrainedOuterLevels` on `RenderInfo`; `constrainNeighbourLevels()` pre-pass in `tessellate()`; `computeEdgeLevels()`, `constrainSharedEdge()`, `screenDist2D()`, `clampTessLevel()` helpers; `render()` passes constrained levels to `activate()` |
| `src/gov/nasa/worldwind/render/shaders/TessellationTerrainShader.java` | `u_maxOuter[4]` uniform in TCS; `min(computed, u_maxOuter[k])` for each outer level; `activate()` accepts `maxOuterLevels` param; `UNCONSTRAINED_OUTER` constant; `u_maxOuter[0]` location → `glUniform1fv` upload |

---

## Task 4.5 — Sub-Grid Heightmap Displacement in TES

**Status:** Complete

### Problem: Double-Displacement

The `RectangularTessellator.buildVerts()` CPU step converts every coarse grid vertex from
geodetic coordinates to tile-local ECEF using `globe.computePointFromPosition(lat, lon,
vertExagg × elevation)`.  The resulting vertex positions already encode the terrain elevation.

The TES bilinearly interpolates those 4 pre-elevated corners.  The bilinear blend produces a
smooth surface that captures the elevation of the coarse `(density+2)²` grid but misses the
sub-grid detail recorded in the `(density+1)²` interior heightmap samples (uploaded as a
`GL_R32F` texture on unit 3).  Naively adding `normal × h_heightmap × scale` in the TES
would **double-displace**: the vertex already sits at `earthRadius + elevation`, so adding
elevation again overshoots by `elevation`.

### Solution: Delta-Correction

The TES computes and adds only the **residual** between the actual heightmap sample and the
elevation already encoded in the bilinearly-interpolated position:

```
worldPos   = pos.xyz + u_refCenter         // full ECEF of tessellated vertex
h_bilinear = length(worldPos) − u_earthRadius  // height above the local sphere
h_actual   = texture(u_heightmap, uv).r        // vertExagg × raw_elevation
delta      = h_actual − h_bilinear
pos.xyz   += normalize(worldPos) × delta
```

`u_earthRadius` is the ellipsoid radius at the tile centre — `length(refCenter)`, where
`refCenter` is the zero-elevation ECEF point uploaded from Java via:

```java
float earthRadius = (float) Math.sqrt(
    refCenterX * refCenterX + refCenterY * refCenterY + refCenterZ * refCenterZ);
program.setUniform1f(gl, "u_earthRadius", earthRadius);
```

Using `length(refCenter)` as the sphere radius rather than a global constant (e.g., WGS84
equatorial radius) makes the approximation accurate to within a few centimetres across a
typical terrain tile.

### Why No Extra Scale

The heightmap stores `verticalExaggeration × elevation` (same units as the CPU vertices).
No `u_heightScale` multiplier is applied in the TES — the delta is already in the same unit
system as the vertex coordinates.  The `u_heightScale` uniform was removed from the TES and
from `activate()`.

### Heightmap UV Mapping

The TES uses the bilinearly-interpolated tile UV (`uv`) to sample the heightmap.  The tile UV
spans `[0,1]² ` over the `(density+1)²` interior grid, matching the layout written by
`buildVerts()`:

```java
for (int j = 1; j <= density+1; j++)
    for (int i = 1; i <= density+1; i++)
        heightmapBuf.put(verticalExaggeration * elevations[j * stride + i]);
```

The outer skirt row/column is excluded, so the texture exactly covers the interior tile area.

### Activation Condition

`activate()` sets `u_useHeightmap = 1` only when all of the following hold:

1. A heightmap cache key was supplied by `RectangularTessellator`
2. The GPU has ≥ 4 texture units
3. The heightmap texture is present in `GpuResourceCache`

Otherwise `u_useHeightmap = 0` and the TES behaves identically to Task 4.2.

### Files Modified

| File | Changes |
|------|---------|
| `src/gov/nasa/worldwind/render/shaders/TessellationTerrainShader.java` | TES: replaced `u_heightScale` with `u_earthRadius`; naive `h × scale` → delta-correction; `activate()`: `useHeightmap = 1` when heightmap bound; `u_earthRadius` upload; removed `u_heightScale` upload |

---

## Files Modified

### Phase 3 — Surface Shape Tessellation

| File | Changes |
|------|---------|
| `src/gov/nasa/worldwind/render/shaders/GpuTessellator.java` | New — compute shader tessellator for arcs and ellipses |
| `src/gov/nasa/worldwind/render/shaders/GpuTriangulator.java` | New — compute shader polygon triangulator with ear-clipping |
| `src/gov/nasa/worldwind/render/AbstractSurfaceShape.java` | Added GPU tessellation integration, VBO outline cache, deferred VBO deletion |
| `src/gov/nasa/worldwind/render/SurfaceEllipse.java` | Added GPU fast path for ellipse point generation |

### Phase 4 — Terrain Rendering

| File | Changes |
|------|---------|
| `src/gov/nasa/worldwind/render/shaders/TerrainShader.java` | New (Task 4.1) — GLSL 1.30 terrain tile shader with heightmap infrastructure |
| `src/gov/nasa/worldwind/render/shaders/TessellationTerrainShader.java` | New (Task 4.2) — GLSL 4.00 tessellation pipeline with screen-space LOD metric. Task 4.4 — `u_maxOuter[4]` TCS cap; `activate()` `maxOuterLevels` param. Task 4.5 — TES delta-correction heightmap displacement; `u_earthRadius` uniform; `activate()` enables `u_useHeightmap` |
| `src/gov/nasa/worldwind/render/shaders/ComputeMeshShader.java` | New (Task 4.3) — GLSL 4.30 compute shader for GPU patch culling + indirect draw |
| `src/gov/nasa/worldwind/terrain/RectangularTessellator.java` | Task 4.1 — TerrainShader integration; heightmap extraction and lazy GPU upload. Task 4.2 — tessellation render path; quad-patch index buffers. Task 4.3 — compute mesh path; `renderVBOComputeTessellated()` |
| `src/gov/nasa/worldwind/render/GLRuntimeCapabilities.java` | Task 4.1 — `isUseTerrainShader()`. Task 4.2 — `isUseTessellation()`. Task 4.3 — `isUseComputeMesh()` |
| `src/gov/nasa/worldwind/render/shaders/ShaderProgram.java` | Task 4.1 — `setUniform3f()`. Task 4.2 — `initTessellation()` for 4-stage pipeline |
