# Small Surface Shape Precision Bug — fp32 in the GPU Tessellator

**Status:** fixed (2026-04-16)
**Files touched:** `src/gov/nasa/worldwind/render/shaders/GpuTessellator.java`
**Symptom file:** `src/gov/nasa/worldwindx/examples/SmallSurfaceShapesDemo.java` exercises the failure case.

---

## Symptom

At close zoom (eye altitude ≤ ~100 m) small `AbstractSurfaceShape` instances — triangles, circles, squares with sides on the order of 5–20 m — rendered with visibly distorted geometry. The outlines were *not* smooth lines drifting with the camera (which would be the classic "float MVP wobble"). They were **steady but mis-shaped**: a triangle with three straight 8 m sides came out looking like an irregular scribble, each vertex and midpoint offset from its correct location by a random amount of up to ~1 m. Lines between those vertices were straight, but the *endpoints* of each segment were in the wrong place, so the overall polygon looked nothing like the shape in code.

This was specifically visible on:
- `SurfacePolygon` with few vertices (triangles, squares)
- `SurfaceEllipse` / `SurfaceCircle` with small radius
- `SurfacePolyline` with short segments

It was invisible on large shapes (>1 km) because the error is absolute (~0.6–1 m), not relative to shape size.

## False leads investigated

Before finding the real cause we considered and ruled out several hypotheses:

| Hypothesis | Ruled out because |
|---|---|
| fp32 MVP matrix in vertex shader | Already fixed — `DashLineShader` and `SurfaceShapeFillShader` have an fp64 (`dvec2` / `dmat4`) vertex path, and `ShaderProgram.setUniformMvpDouble` reads `GL_MODELVIEW`/`GL_PROJECTION` as doubles. Solid outlines were later routed through `DashLineShader` too so they'd get the same path. |
| `SurfaceObjectTileBuilder` tile-pyramid level too coarse | At level 16 the tile delta is ~5.5e-4°, giving ~0.12 m per texel over a 512×512 tile. More than sharp enough for a 10 m shape. Bumping levels didn't help and — correctly pointed out — the tile pyramid controls *which* tile is picked, not the in-tile rasterisation precision. |
| Terrain tessellator distortion | Disabled by swapping in `ZeroElevationModel` for the demo. The distortion persisted. |
| `LatLon.interpolateGreatCircle` precision | Uses `Math.asin` / `Math.atan2` with doubles throughout. For 5–10 m distances the spherical trig preserves full double precision. Not the culprit. |

## Root cause

`GpuTessellator` runs great-circle, rhumb, and ellipse interpolation in an OpenGL 4.3 **compute shader**, and every intermediate value in that shader is `float` (fp32).

`COMPUTE_SOURCE` (`GpuTessellator.java`, ~line 81):

```glsl
#version 430
layout(std430, binding = 0) readonly buffer EdgeBuffer   { vec4  edges[];      };
layout(std430, binding = 2) writeonly buffer OutputBuffer { vec2  outVerts[];   };
...
vec2 greatCircleInterp(float lon1, float lat1, float lon2, float lat2, float t)
{
    float cosD = sin(lat1) * sin(lat2) + cos(lat1) * cos(lat2) * cos(lon2 - lon1);
    float d    = acos(clamp(cosD, -1.0, 1.0));
    ...
}
```

The Java side uploads edge endpoints in *radians*, cast to `float`:

```java
this.edgeStagingBuffer.put((float) a.getLongitude().radians);   // ← fp32 cast here
this.edgeStagingBuffer.put((float) a.getLatitude().radians);
this.edgeStagingBuffer.put((float) b.getLongitude().radians);
this.edgeStagingBuffer.put((float) b.getLatitude().radians);
```

and reads the interpolated output back as `float` degrees:

```java
float lon = this.outputStagingBuffer.get(bufPos);
float lat = this.outputStagingBuffer.get(bufPos + 1);
locations.add(LatLon.fromDegrees(lat, lon));
```

`ELLIPSE_COMPUTE_SOURCE` has exactly the same structure: `u_centerLon` / `u_centerLat` are `float` uniforms, the per-thread great-circle end-position math runs in `float`, and the output is `vec2` degrees.

### The precision math

IEEE-754 fp32 has a 23-bit mantissa → ~7 decimal digits of precision. For a value of magnitude *C* the ULP is ≈ *C* × 2⁻²³ ≈ 1.19e-7 × *C*.

At **Times Square** (the demo location):
- `lon` = -73.9855° = **-1.2912 rad**
- `lat` =  40.7580° = **+0.7114 rad**

ULP of `lon` as a `float` radian ≈ 1.2912 × 1.19e-7 ≈ **1.54e-7 rad**.

Converting to a surface distance on Earth: 1.54e-7 rad × 6 378 137 m ≈ **0.98 m**.

**So every edge endpoint, every midpoint, and every ellipse point produced by the compute shader is quantised to a ~1 m grid in absolute position.** Different points round to different grid cells, so the per-vertex error is effectively white noise at the ~1 m level.

### Why this destroys small shapes specifically

For a 10 m triangle, a ±1 m independent wobble on each of (say) 12 tessellated points (3 originals + 9 subdivision midpoints) produces ±10 % random displacement per point. The eye reads that as "not a triangle". Lines between quantised points are *straight* (the outline rasteriser produces smooth segments between whatever endpoints it's given) but the endpoints themselves are scattered.

For a 10 km polygon the same ~1 m error is 0.01 % of shape size — sub-pixel, invisible.

### Catastrophic cancellation makes it worse

`cos(lon2 - lon1)` in fp32 does the subtraction at full fp32 precision, but `lon1` and `lon2` already carry ±1 ULP of error from the `(float)` cast. For a 10 m edge, `lon2 - lon1` ≈ 1.6e-6 rad — comparable to the ULP error itself — so the small difference *is* basically noise. The great-circle formula then amplifies that noise through `acos`, `sin`, `cos`, and `atan2`.

Result: for edges below roughly 10 m the interpolation shader is producing random numbers in a shape-sized box, not interpolated points.

## Why the fp64 shader path didn't rescue this

The fp64 work in `DashLineShader` / `SurfaceShapeFillShader` / `ShaderProgram.setUniformMvpDouble` is still correct and load-bearing — it keeps MVP math at double precision inside the vertex stage. But that stage sees already-corrupted vertex positions: the `List<LatLon>` passed to the VBO builder was populated from `GpuTessellator`'s fp32 output. Making the *downstream* math fp64 cannot undo quantisation that happened *upstream*.

This is the general lesson: an fp64 pipeline only helps if every stage that handles position data is fp64. A single fp32 bottleneck anywhere in the chain sets the precision floor for the whole pipeline.

## The fix

Both GPU paths — edge-interpolation and ellipse/circle — now bail out when the input is below the precision floor of fp32 radians, so the caller falls through to the existing CPU double-precision path (`LatLon.interpolateGreatCircle`, `LatLon.greatCircleEndPosition`).

```java
// GpuTessellator.java — new constant, ~line 70
/**
 * Precision guard for the fp32 compute shader path. Below this edge length (or
 * ellipse axis), the compute shader's float-radian math can't preserve sub-metre
 * geographic precision: at typical geographic coordinates, fp32 ULP ≈ 1e-7 rad ≈
 * 0.6 m, which becomes a visible wobble on shapes whose edges are only a few
 * metres long. For small shapes we return failure so the caller falls back to
 * the CPU LatLon.interpolateGreatCircle path (doubles throughout). Shapes of
 * this scale produce so few vertices that CPU tessellation is free.
 */
private static final double MIN_METRES_FOR_GPU = 1000.0;
private static final double EARTH_RADIUS_METRES = 6_378_137.0;
```

**Path 1 — `tessellate()`**: during the edge-scanning loop that already computes `LatLon.greatCircleDistance` per edge, track the shortest *interpolated* edge in metres. If any edge that would actually produce midpoints is below the guard, return `false` so `AbstractSurfaceShape.generateIntermediateLocations` falls through to its CPU loop.

**Path 2 — `tessellateEllipse()`**: if `max(majorRadius, minorRadius) < MIN_METRES_FOR_GPU`, return `null`. `SurfaceEllipse.computeLocations` already handles null by falling through to the CPU `LatLon.greatCircleEndPosition` loop.

### Why 1000 m

It's a single threshold that covers both correctness and performance:

1. **Correctness** — at the worst typical geographic magnitude (|lon|, |lat| in rad up to ~π), fp32 ULP is ~2e-7 rad ≈ 1.3 m. A 1 km edge carries that as 0.13 % relative error — well below a screen pixel at any reasonable zoom.
2. **Performance** — shapes smaller than 1 km tessellate to at most a few hundred points. `LatLon.interpolateGreatCircle` on the CPU is cheap at that scale; the GPU dispatch overhead (buffer uploads, SSBO bind, compute dispatch, memory barrier, `glGetBufferSubData` readback stall) is comparable or larger.

So below the threshold the CPU path is not just more precise, it's also the faster path for the vertex counts involved.

### What the fix does **not** do

- It does not modify the compute shader. Any large-shape GPU tessellation continues to use fp32, which is fine at that scale.
- It does not rebase coordinates against a reference point in the shader. That would let the compute shader stay in use for small shapes, but it's a larger change (new uniforms, shader edits in two places) and unnecessary given the CPU path is plenty fast at this scale.
- It does not touch any of the fp64 vertex-stage work. That remains correct and necessary for MVP precision on the large shapes that *do* stay on the GPU tessellation path.

## Validation

`SmallSurfaceShapesDemo` — seven sub-20 m surface shapes over Times Square viewed from 80 m altitude — renders correctly after the fix. The 32-gon circle outline closes on itself; the 8 m triangle has three straight equal sides; the 6 m circle is visibly round.

The same demo with the fix reverted reproduces the mis-shaped geometry.

## Lessons

1. **An fp64 vertex pipeline is a chain, not a single stage.** Any producer feeding it — tessellator, CPU geometry builder, VBO upload path — has to match the precision you want to preserve, or the downstream fp64 work is wasted.
2. **Geographic coordinates exaggerate fp32 precision loss.** At absolute magnitudes of ~1 rad the surface-distance ULP is ~1 m, vastly worse than the ~1 cm people intuit from "fp32 has 7 decimal digits". Anything that does geographic math in fp32 needs to either work in a local reference frame or stay above a ~1 km scale.
3. **GPU tessellation's win shrinks at small scale.** Compute-shader dispatch has fixed overhead that dominates for the vertex counts small shapes actually need. The guard here is as much a performance normalisation as a correctness fix.
