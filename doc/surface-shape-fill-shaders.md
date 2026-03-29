# Surface Shape Fill Shaders

This document describes the GLSL shader-based interior fill rendering added to `AbstractSurfaceShape` in WorldWind Reforged. This replaces the legacy GLU tessellator immediate-mode rendering path with triangulated VBO rendering via a custom fill shader.

---

## Problem

The original interior rendering path in `AbstractSurfaceShape` had several performance issues:

1. **GLU tessellator with immediate mode** — `doTessellateInterior()` created a `GLUtessellator` every frame that issued `glBegin()`/`glVertex3f()`/`glEnd()` calls through JNI callbacks. Tessellation and rendering were fused — the geometry could not be cached.

2. **Per-frame retessellation** — Since the GLU callback directly issued GL draw commands, there was no way to cache the triangulated result. Every frame re-tessellated and re-submitted all interior vertices.

3. **Deprecated texture coordinate generation** — `applyInteriorTextureState()` used `GL_TEXTURE_GEN_S/T` with `GL_OBJECT_LINEAR` mode, which is deprecated in modern OpenGL and often software-emulated on current drivers.

4. **No VBO usage** — Interior vertices were never uploaded to GPU-resident buffers, unlike the outline path which already had VBO caching.

---

## Solution

### SurfaceShapeFillShader

**File:** `src/gov/nasa/worldwind/render/shaders/SurfaceShapeFillShader.java`

A GLSL 130 shader program (compatibility profile) that supports two rendering modes:

**Solid color mode:**
- Fragment shader outputs a uniform RGBA color
- Used for shapes with no `imageSource` set, and during picking

**Textured mode:**
- Vertex shader generates texture coordinates from vertex positions, transformed by a uniform `mat4 u_texMatrix`
- Fragment shader samples a 2D texture and modulates with a uniform color
- Replicates the `GL_OBJECT_LINEAR` behavior: vertex (x, y) positions in degrees-offset-from-reference ARE the texture coordinates, then the texture matrix applies reference position translation, latitude cosine correction, image scale, and pixel-to-texel ratio matching

**Vertex shader:**
```glsl
#version 130
in vec2 a_position;
uniform mat4 u_texMatrix;
uniform int u_useTexture;
out vec2 v_texCoord;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * vec4(a_position, 0.0, 1.0);
    if (u_useTexture == 1) {
        vec4 tc = u_texMatrix * vec4(a_position, 0.0, 1.0);
        v_texCoord = tc.st;
    }
}
```

Uses `gl_ModelViewProjectionMatrix` (compatibility profile built-in), same approach as `DashLineShader`. The texture matrix is passed as a uniform since we are replacing the fixed-function texture matrix stack.

**Fragment shader:**
```glsl
#version 130
uniform vec4 u_color;
uniform int u_useTexture;
uniform sampler2D u_texture;
in vec2 v_texCoord;

void main() {
    if (u_useTexture == 1)
        gl_FragColor = u_color * texture2D(u_texture, v_texCoord);
    else
        gl_FragColor = u_color;
}
```

### Interior VBO Cache

**File:** `src/gov/nasa/worldwind/render/AbstractSurfaceShape.java`

A new `InteriorVBOData` structure caches triangulated interior geometry in GPU-resident VBOs:

```
InteriorVBOData:
  vertexVboId   — GL_ARRAY_BUFFER with float (x, y) positions
  indexVboId    — GL_ELEMENT_ARRAY_BUFFER with int triangle indices
  indexCount    — total number of indices (triangleCount * 3)
```

Cached per geometry key (same key used for the outline VBO cache). Built on first access, reused across frames until geometry changes.

### Triangulation

`buildInteriorVBOs()` triangulates each contour in `activeGeometry` independently using `GpuTriangulator.triangulateCPU()` (the ear-clipping algorithm). Each contour is treated as a separate simple polygon — this is correct because `activeGeometry` contours from dateline splitting are independent polygons, not holes in each other.

Shapes with actual holes (like `SurfacePolygon`) have their own rendering path and don't use this code.

### Texture Matrix Computation

`computeInteriorTextureMatrix()` replicates the exact transform chain from the legacy `applyInteriorTextureState()`:

1. **Reference position translation** — offsets coordinates to the shape's reference position
2. **Latitude cosine correction** — compensates for Mercator distortion on the x-axis
3. **Image scale** — applies `attributes.getImageScale()`
4. **Pixel-to-texel ratio** — scales so one texture pixel matches one FBO draw tile pixel
5. **Internal texture transform** — captures `texture.applyInternalTransform()` (e.g., vertical flip) by reading back the GL texture matrix

The combined result is passed as a single `mat4` uniform to the vertex shader.

---

## Rendering Flow

```
drawInterior(dc, sdc)
  |
  +-- drawInteriorWithShader(dc, sdc)    [NEW — shader path]
  |     |
  |     +-- Lazy-init SurfaceShapeFillShader (shared across all shapes)
  |     +-- Look up / build InteriorVBOData from cache
  |     +-- Determine mode: solid color vs textured
  |     +-- For picking: read current GL color (set by tile builder)
  |     +-- For solid: fillShader.beginSolid(gl, r, g, b, a)
  |     +-- For textured: compute texture matrix, fillShader.beginTextured(...)
  |     +-- Bind vertex VBO, bind index VBO, glDrawElements(GL_TRIANGLES)
  |     +-- fillShader.end(gl)
  |
  +-- [FALLBACK] Legacy GLU tessellator + immediate mode
        applyInteriorState() → tessellateInterior()
```

The shader path is attempted first. If shader initialization fails (e.g., GLSL 130 not available), `fillShaderFailed` is set and all subsequent calls go directly to the legacy path.

---

## VBO Lifecycle

- **Creation:** On first render after geometry change (cache miss in `interiorVboCache`)
- **Reuse:** Cached per geometry key, reused across all frames until geometry changes
- **Eviction:** When geometry changes (`clearCaches()`), old VBOs are scheduled for deferred deletion
- **Deletion:** `flushPendingVBODeletes()` runs at the start of `doDrawGeographic()`, deleting VBOs on the GL thread. Uses the same `pendingVBODeletes` list as outline VBOs for thread-safe cross-thread eviction.

---

## Performance Impact

| Metric | Legacy GLU Path | Shader + VBO Path | Improvement |
|--------|----------------|-------------------|-------------|
| Per-frame tessellation | Every frame (GLU callbacks) | Once (cached in VBO) | Eliminated |
| Per-frame CPU→GPU transfer | Every frame (glVertex3f) | Once (VBO upload) | Eliminated |
| GL draw calls | glBegin/glEnd per primitive | Single glDrawElements | Reduced |
| Texture coord generation | GL_TEXTURE_GEN (deprecated) | Shader uniform matrix | Modern path |
| Picking | Immediate mode | Same shader, pick color as uniform | Consistent |

The improvement is most noticeable for shapes with many vertices (complex polygons, ellipses with high segment count) since the per-frame cost drops from O(vertices) to O(1) after the initial cache build.

---

## Hardware Requirements

- **Shader path:** GLSL 1.30+ (OpenGL 3.0 compatibility profile)
- **Fallback:** Any OpenGL 2.0 system runs the legacy GLU path with identical visual output

---

---

## SurfacePolygon Migration

`SurfacePolygon` extends `AbstractSurfaceShape` and previously had its own `doDrawGeographic()` override with a custom GLU tessellator that handled holes and per-vertex texture coordinates. This has been replaced:

**Removed:**
- `doDrawGeographic()` — custom rendering path that fused tessellation + rendering
- `tessellateContours()` — GLU tessellator callback handler for triangle + line extraction
- `CollectPrimitivesCallback` usage
- `ShapeData` inner class and `shapeDataCache` field
- `applyInteriorState()` override (explicit texture handling)

**Added:**
- `buildInteriorVBOs()` override — calls `assembleContours()` to get edge-interpolated contours with texture coordinates, then uses `GpuTriangulator.bridgeHoles()` + `triangulateCPU()` for polygon-with-holes tessellation
- `getExplicitInteriorTexture()` override — returns the `explicitTexture` field for use in the shader path

**Explicit texture coordinates** (set via `setTextureImageSource(imageSource, texCoords, texCoordCount)`) are interleaved in the VBO as `[x, y, s, t]` per vertex with stride=16, and drawn using shader mode `u_useTexture=2` via `beginExplicitTextured()`.

**Fallback:** Simple polygons (no holes, no explicit textures) delegate to `super.buildInteriorVBOs()`. Outlines continue to use the inherited path unchanged.

---

## SurfacePolygons Migration

`SurfacePolygons` handles batch rendering of many polygons from a `CompoundVecBuffer` (used by shapefile rendering). Previously it used `drawInterior()` + `doTessellateInterior()` with GLU tessellator into display lists. Now:

**Added:**
- `buildInteriorVBOs()` override — reads from `CompoundVecBuffer`, groups rings by `polygonRingGroups` or winding order, flattens vertices with hemisphere offset for dateline crossings, bridges holes via `GpuTriangulator.bridgeHoles()`, triangulates via `triangulateCPU()`, produces a single combined VBO

**Retained:**
- `drawInterior()` override — tries shader path, falls back to display list; handles dateline double-rendering (renders VBO twice with 360° translation)
- GLU tessellator + display list fallback for pole-wrapping shapes (returns null from `buildInteriorVBOs()`, triggering GLU path)
- Ring group logic, winding order detection, dateline crossing detection

---

## Shader Modes

| Mode | `u_useTexture` | Use case |
|------|---------------|----------|
| Solid color | 0 | Default fill, picking |
| Computed texture | 1 | PatternFactory fills, `setImageSource()` |
| Explicit texture | 2 | `SurfacePolygon.setTextureImageSource()` with per-vertex UVs |

---

## Files

| File | Changes |
|------|---------|
| `src/gov/nasa/worldwind/render/shaders/SurfaceShapeFillShader.java` | New — GLSL fill shader, modes 0/1/2, beginExplicitTextured() |
| `src/gov/nasa/worldwind/render/AbstractSurfaceShape.java` | Shader fill integration, interior VBO cache, explicit texture hook |
| `src/gov/nasa/worldwind/render/SurfacePolygon.java` | Migrated from GLU+display to shader+VBO; holes via bridgeHoles() |
| `src/gov/nasa/worldwind/render/SurfacePolygons.java` | buildInteriorVBOs() override for batch polygons; display list fallback retained |
