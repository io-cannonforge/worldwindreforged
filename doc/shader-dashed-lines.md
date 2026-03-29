# Shader-Based Dashed Lines

## Overview

WorldWind Reforged replaces the deprecated OpenGL 2 `glLineStipple()` call with a
GLSL shader that renders dashed outlines on all surface shapes (polylines, polygons,
circles, ellipses, quads, sectors). The new implementation is zoom-independent: the
dash pattern maintains the same visual size regardless of altitude, from orbital view
down to 2 meters above the ground.

The old fixed-function stipple has two problems:
1. **Deprecated** -- `glLineStipple()` was removed from the OpenGL core profile.
   Modern drivers still support it in the compatibility profile, but that is not
   guaranteed going forward.
2. **Screen-space dependent** -- the classic stipple pattern is applied in screen
   pixels. As the camera zooms in, each dash covers more geographic distance and the
   pattern appears to stretch. As the camera zooms out the dashes shrink and collapse.

The shader replacement solves both issues.

---

## Architecture

Three pieces work together:

```
ShaderProgram          (general-purpose GLSL program manager)
      |
DashLineShader         (vertex + fragment source, uniforms, attribute binding)
      |
AbstractSurfaceShape   (CPU-side distance computation, vertex subdivision, draw calls)
```

### New files

| File | Purpose |
|------|---------|
| `src/gov/nasa/worldwind/render/shaders/ShaderProgram.java` | Compile, link, cache uniforms, dispose |
| `src/gov/nasa/worldwind/render/shaders/DashLineShader.java` | Dashed-line vertex/fragment shader pair |
| `src/gov/nasa/worldwindx/examples/DashedLinesExample.java` | Demo with 10 shapes showing various patterns |

### Modified files

| File | What changed |
|------|-------------|
| `AbstractSurfaceShape.java` | `drawOutline()`, `drawLineStripWithDist()`, `applyOutlineState()`, new static fields |

---

## How it works, step by step

### 1. Detecting that a shape needs dashing

In `AbstractSurfaceShape.drawOutline()`, the code checks three conditions:

```java
boolean useDashShader = !dc.isPickingMode()
    && attrs.getOutlineStippleFactor() > 0
    && !dashLineShaderFailed;
```

- Picking mode always renders solid (the shader would discard gap fragments, making
  them un-pickable).
- `outlineStippleFactor == 0` means the shape has no stipple at all.
- If shader compilation failed on a previous frame, a static flag prevents retrying.

If all conditions pass, `initDashShader()` lazily compiles the shader on first use.
The compiled program is stored in a **static** field shared by all surface shapes --
there is only ever one shader program instance.

### 2. Converting the legacy stipple parameters

The existing `ShapeAttributes` API exposes two values inherited from `glLineStipple`:

| Attribute | Meaning |
|-----------|---------|
| `outlineStippleFactor` | Repeat count per bit (integer >= 1) |
| `outlineStipplePattern` | 16-bit bitmask, 1 = draw, 0 = gap |

These are translated into the shader's two uniforms:

```java
float dashLengthPixels = attrs.getOutlineStippleFactor() * 16.0f;
float gapRatio = convertStippleToGapRatio(attrs.getOutlineStipplePattern());
```

**Dash length** is the total cycle length in tile-pixels (tile-pixels are the pixel
coordinates of the off-screen surface tile texture, not the on-screen window).
`factor * 16` is the length in pixels because the 16-bit pattern is stretched by the
factor.

**Gap ratio** is the fraction of zero-bits in the pattern. For example, pattern
`0xFF00` has 8 zero-bits out of 16, giving a gap ratio of 0.5 (half dash, half gap).

```java
private static float convertStippleToGapRatio(short pattern)
{
    int bits = pattern & 0xFFFF;
    int zeroBits = 0;
    for (int i = 0; i < 16; i++)
    {
        if ((bits & (1 << i)) == 0)
            zeroBits++;
    }
    return zeroBits / 16.0f;
}
```

This is a simplification: the original 16-bit pattern can encode arbitrary sequences
(dash-dot-dash, for instance), but the shader reduces it to a single dash/gap ratio.
The simplification is visually acceptable because most real-world patterns use
symmetric repeating groups like `0xFF00`, `0xF0F0`, or `0xAAAA`.

### 3. Computing cumulative distance on the CPU

The key insight of the shader approach: each vertex carries a **cumulative distance**
along the line strip, measured in tile-pixels. The fragment shader then uses
`mod(distance, dashLength)` to decide whether a given fragment is inside a dash or a
gap.

The distance is computed in `drawLineStripWithDist()`:

```java
double cumulDist = 0;
for each vertex V:
    dx = V.lon - prevV.lon   (in degrees, offset from reference position)
    dy = V.lat - prevV.lat
    cumulDist += sqrt(dx*dx + dy*dy) * pixelsPerDegree;
```

Where `pixelsPerDegree` comes from the surface tile context:

```java
double pixelsPerDegree = sdc.getViewport().width / sdc.getSector().getDeltaLonDegrees();
```

This makes the dash size proportional to the tile's resolution. Since the tile system
adapts its resolution to the camera altitude, the dashes stay the same visual size on
screen at every zoom level.

### 4. The vertex shader

```glsl
#version 130
in float a_dist;        // cumulative distance, set via glVertexAttribPointer
out float v_dist;       // passed to fragment shader

void main()
{
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    v_dist = a_dist;
}
```

The shader runs in the **GL2 compatibility profile**, so it can use the built-in
`gl_ModelViewProjectionMatrix` and `gl_Vertex`. The distance attribute is simply
passed through -- no transformation is needed because the CPU already computed it in
tile-pixel units.

### 5. The fragment shader

```glsl
#version 130
in float v_dist;
uniform vec4 u_color;
uniform float u_dashLength;
uniform float u_gapRatio;
uniform int u_picking;

void main()
{
    if (u_picking == 0 && u_dashLength > 0.0)
    {
        float pos = mod(v_dist, u_dashLength) / u_dashLength;
        if (pos > (1.0 - u_gapRatio))
            discard;
    }
    gl_FragColor = u_color;
}
```

`mod(v_dist, u_dashLength)` wraps the distance into a single cycle. Dividing by
`u_dashLength` normalises it to `[0, 1)`. The first `(1 - gapRatio)` fraction is
dash; the remaining `gapRatio` fraction is gap, and those fragments are discarded.

### 6. Skipping the old GL2 stipple

`applyOutlineState()` gained a `usingDashShader` parameter. When `true`, the legacy
stipple is explicitly disabled:

```java
if (usingDashShader || dc.isPickingMode() || (attrs.getOutlineStippleFactor() <= 0))
    gl.glDisable(GL2.GL_LINE_STIPPLE);
else
{
    gl.glEnable(GL2.GL_LINE_STIPPLE);
    gl.glLineStipple(attrs.getOutlineStippleFactor(), attrs.getOutlineStipplePattern());
}
```

If the shader fails to compile (e.g. on a very old GPU), the code falls back to the
GL2 stipple path automatically.

---

## The extreme-zoom precision problem

Surface shapes in WorldWind are rendered via the **surface tile system**: each shape
is drawn into an off-screen 512x512 texture tile, and the tile is later draped onto
the terrain. At extreme zoom (2 meters altitude), the finest tile level covers about
50 meters of ground at 0.1 m per tile-pixel.

The shape's tessellated geometry has relatively few vertices (up to 100 intermediate
points per segment, capped by `maxEdgeIntervals`). A polyline spanning 40 degrees of
longitude might have only ~100 tessellation vertices, meaning each pair of adjacent
vertices is about 0.4 degrees apart -- or **740,000 tile-pixels** at the finest tile
resolution.

This creates a precision problem in two places:

### Problem 1: GPU float interpolation aliasing

The GPU interpolates the `v_dist` attribute between vertices using **single-precision
float (float32)**. Float32 has a 24-bit mantissa, giving about 7 significant decimal
digits.

When two vertices are 740,000 tile-pixels apart and the GPU must interpolate between
them, consecutive tile-pixel fragments receive `v_dist` values that differ by only 1.
But the absolute `v_dist` values are in the hundreds of thousands. At that magnitude,
float32 cannot distinguish values that differ by 1.

For example, at `v_dist = 370,000`:
- Float32 precision: ~0.02 (fine)
- `mod(370000, 32) = 16` (correct)

But at `v_dist = 37,000,000` (50th segment):
- Float32 precision: ~2.0
- Adjacent pixels get the **same** float value
- `mod()` produces irregular staircase steps instead of smooth progression
- The dash pattern breaks into random-looking blocks and slivers

### Problem 2: Stored float overflow

Even if the GPU interpolation were perfect, the cumulative distance stored in the
vertex attribute buffer is cast from `double` to `float`:

```java
distBuffer.put((float) cumulDist);
```

For a 40-degree polyline at extreme zoom, the maximum cumulative distance reaches
~1 billion tile-pixels. Float32 cannot represent individual pixels at that magnitude.

### The fix: segment subdivision + batch rebasing

The solution addresses both problems simultaneously:

**Segment subdivision** -- When a segment between two tessellation vertices exceeds
`MAX_SEGMENT_PIXELS` (500) tile-pixels, the CPU inserts intermediate vertices along
the segment. Each sub-segment is at most 500 tile-pixels long, keeping the GPU's
float interpolation well within sub-pixel precision. The distance at each intermediate
vertex is computed with full `double` precision on the CPU.

```
Before (extreme zoom):
    V0 ---- 740,000 px ---- V1    (GPU can't interpolate this precisely)

After:
    V0 --500-- V0a --500-- V0b --500-- ... --500-- V1
    (each sub-segment is within float32 precision)
```

**Batch rebasing** -- When the cumulative distance exceeds `MAX_BATCH_DIST`
(100,000 tile-pixels), the current batch of vertices is flushed via `glDrawArrays`,
and a new batch begins with the distance offset reset. The reset is aligned to a dash
cycle boundary so the pattern continues seamlessly:

```java
double prevRel = prevCumulDist - batchOffset;
batchOffset = prevCumulDist - (prevRel % dashLengthPixels);
```

The previous vertex is duplicated as the first vertex of the new batch to maintain
the `GL_LINE_STRIP` connection. Since the boundary vertex appears as the last vertex
of one batch and the first of the next, there is no gap in the rendered line.

### Why both are needed

- Subdivision alone does not help if the accumulated distance values grow too large
  for float32 storage (problem 2).
- Rebasing alone does not help if the GPU must interpolate between vertices that are
  far apart in tile-pixel distance (problem 1).

Together, they guarantee that:
1. No stored float value exceeds 100,000 (precision ~0.006 pixels)
2. No segment span exceeds 500 tile-pixels (interpolation precision ~0.00003 pixels)

---

## The `drawLineStripWithDist` algorithm in detail

```
INPUT:  List<LatLon> locations     -- tessellated outline vertices
        double pixelsPerDegree     -- from the surface tile context
        float dashLengthPixels     -- dash cycle length in tile-pixels

PHASE 1: Vertex count estimation
    Walk the locations list, computing the tile-pixel distance of each segment.
    For segments > 500 px, compute ceil(segPixels / 500) subdivisions.
    Sum to get totalVerts.  Allocate (or grow) vertex + distance buffers.

PHASE 2: Fill buffers with subdivided vertices
    For each original segment A -> B:
        segDegrees = euclidean distance in degree-space
        segPixels  = segDegrees * pixelsPerDegree
        subdivisions = max(ceil(segPixels / 500), 1)

        For s = 1 to subdivisions:
            t = s / subdivisions
            interpolated position = A + (B - A) * t
            interpolated distance = cumulDist + segDegrees * t * pixelsPerDegree

            If (distance - batchOffset) > 100,000:
                Flush current vertices via glDrawArrays(GL_LINE_STRIP)
                Reset batchOffset aligned to dash cycle boundary
                Duplicate previous vertex as start of new batch

            Append vertex position and (distance - batchOffset) to buffers

PHASE 3: Flush remaining vertices
    glDrawArrays(GL_LINE_STRIP, 0, vertexCount)
```

### Performance notes

At normal zoom levels (above ~100m altitude), segments are short enough that no
subdivision occurs and only one batch is needed. The overhead is a single pass over
the vertex list to estimate the count (identical cost to the original code that
allocated buffers) plus trivially cheap division checks per vertex.

At extreme zoom, subdivision generates more vertices but the visible tile covers such
a small area that few vertices actually contribute visible fragments. The GPU's
scissor test discards everything outside the 512x512 tile viewport.

---

## GL state management

The shader is activated with `DashLineShader.begin()` and deactivated with `.end()`:

```java
dashLineShader.begin(gl, r, g, b, a, dashLengthPixels, gapRatio, picking);
// ... draw all outline geometry ...
dashLineShader.end(gl);
```

`begin()` calls `glUseProgram`, sets the four uniforms, and enables the `a_dist`
vertex attribute array. `end()` disables the attribute array and calls
`glUseProgram(0)` to restore the fixed-function pipeline.

Because surface shapes are drawn into off-screen tile textures (not directly to the
screen), the shader operates within the tile's orthographic projection and viewport.
The `gl_ModelViewProjectionMatrix` in the vertex shader is the tile's geographic-to-
viewport matrix, which maps degree-offset coordinates to tile-pixel coordinates.

---

## ShaderProgram: the general-purpose GLSL manager

`ShaderProgram` is a reusable utility class with no knowledge of dashed lines. It
handles:

1. **Compilation** -- `compileShader(gl, type, source)` creates a shader object,
   compiles source, and checks `GL_COMPILE_STATUS`. On failure it logs the info log
   and returns 0.

2. **Linking** -- `init(gl, vertexSource, fragmentSource)` compiles both shaders,
   creates a program, attaches them, links, and checks `GL_LINK_STATUS`.

3. **Uniform caching** -- `getUniformLocation(gl, name)` uses a `HashMap` to avoid
   repeated `glGetUniformLocation` calls. Convenience setters (`setUniform1f`,
   `setUniform4f`, `setUniform1i`, etc.) wrap the lookup and the `glUniform*` call.

4. **Lifecycle** -- `use()` / `unuse()` bind and unbind the program. `dispose()`
   deletes the program and both shader objects, clears the uniform cache, and marks
   the program invalid.

`DashLineShader` owns one `ShaderProgram` instance and adds one extra piece of state:
the attribute location for `a_dist`, retrieved via `glGetAttribLocation` after
linking.

---

## Fallback behaviour

If `DashLineShader.init()` fails (returns `false`), the static flag
`dashLineShaderFailed` is set to `true`. All subsequent frames skip the shader check
and fall through to the legacy GL2 stipple path:

```java
gl.glEnable(GL2.GL_LINE_STIPPLE);
gl.glLineStipple(factor, pattern);
```

This means the shader is a **progressive enhancement**. On hardware or drivers that
do not support GLSL 1.30 in the compatibility profile, the old behaviour is preserved
automatically.

---

## How to use it

No API changes are needed. Any surface shape that already sets stipple attributes will
automatically use the shader:

```java
ShapeAttributes attrs = new BasicShapeAttributes();
attrs.setOutlineStippleFactor(2);              // repeat factor (>= 1 to enable)
attrs.setOutlineStipplePattern((short) 0xFF00); // 16-bit pattern
shape.setAttributes(attrs);
```

Common patterns:

| Pattern | Hex | Visual |
|---------|-----|--------|
| Even dash | `0xFF00` | `--------________` |
| Fine dash | `0xF0F0` | `----____----____` |
| Dotted | `0xAAAA` | `-_-_-_-_-_-_-_-_` |
| Dash-dot | `0xFFC8` | `----------_-___` |
| Solid | `0xFFFF` | `----------------` (factor must be 0 to disable) |

Set `outlineStippleFactor` to 0 (or don't set it) for a solid outline.

---

## Example: `DashedLinesExample`

The bundled example at `gov.nasa.worldwindx.examples.DashedLinesExample` creates
10 shapes demonstrating the shader:

1. Solid line (no stipple, for comparison)
2. Fine dashed line (factor=1, `0xF0F0`)
3. Medium dashed line (factor=2, `0xFF00`)
4. Wide dashed line (factor=3, `0xF0F0`)
5. Dotted line (factor=1, `0xAAAA`)
6. Dash-dot line (factor=2, `0xFFC8`)
7. Thick dashed line (width=5, factor=2, `0xFF00`)
8. Dashed polygon (pentagon)
9. Dashed circle
10. Dashed ellipse

Each uses the standard `ShapeAttributes` API. The example also includes a dark-themed
legend panel.

Run it with:

```
mvn exec:java -Dexec.mainClass=gov.nasa.worldwindx.examples.DashedLinesExample
```

Or use the provided Eclipse launch configuration `DashedLinesExample.launch`.
