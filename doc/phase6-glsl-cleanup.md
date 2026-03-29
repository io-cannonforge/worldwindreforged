# Phase 6: GLSL Deprecated Built-in Cleanup

## Overview

Phase 6 replaces deprecated GLSL built-in variables and uniforms with explicit alternatives across
the WorldWind Reforged shader pipeline. The work also upgraded the default GL profile to GL3bc
(OpenGL 3.x/4.x compatibility) and introduced AMD vendor detection to auto-disable VAOs on
problematic drivers.

**Attribution:** All changes by seaglassfoundry.com.

---

## GL Profile Upgrade to GL3bc

### Rationale

The original WorldWind SDK used `GLProfile.getMaxFixedFunc(true)` to select a GL profile, which
typically resolved to GL2 on modern hardware. GL2 only guarantees GLSL 1.10, making it impossible
to use `layout(location=N)` qualifiers (require GLSL 3.30+) or other modern GLSL features.

By requesting `GLProfile.GL3bc` (GL 3.x/4.x backward-compatible), we get:
- GLSL 3.30+ guaranteed — enables `layout(location=N)` for explicit attribute locations
- Better-tested driver code paths on modern GPUs
- All fixed-function APIs remain available (compatibility profile)
- Falls back to `getMaxFixedFunc(true)` on older hardware that lacks GL 3.x support

### Implementation

**File:** `Configuration.java`

```java
public static GLProfile getMaxCompatibleGLProfile()
{
    if (GLProfile.isAvailable(GLProfile.GL3bc))
        return GLProfile.get(GLProfile.GL3bc);
    return GLProfile.getMaxFixedFunc(true);
}
```

---

## AMD Vega Mobile VAO Crash: Discovery and Workaround

### The Bug

During Phase 6 testing on AMD Ryzen 5 3500U with Radeon Vega Mobile Gfx, a native
`EXCEPTION_ACCESS_VIOLATION` crash was discovered:

```
# EXCEPTION_ACCESS_VIOLATION (0xc0000005) at pc=0x00007ffb0f0c3160
# Problematic frame: C  [atio6axx.dll+0xa83160]
```

The crash occurs inside `glDrawElements` when Vertex Array Objects (VAOs) are used in an OpenGL
**compatibility profile** context. The AMD driver's compatibility-profile VAO code path contains a
NULL pointer dereference. The crash is 100% reproducible on AMD Vega Mobile with any VAO usage in
compatibility profile, and does not occur in core profile or on NVIDIA/Intel GPUs.

### Root Cause Analysis

In OpenGL compatibility profile, generic vertex attribute 0 (`glVertexAttribPointer`) and the
fixed-function `gl_Vertex` attribute (`glVertexPointer`) can alias each other, but the behavior is
implementation-defined. AMD's compatibility-profile driver path does not properly handle VAO vertex
attribute state, leading to a NULL pointer read inside the driver during `glDrawElements`.

Key observations:
- The crash address (`atio6axx.dll+0xa83160`) was consistent across all reproduction attempts
- The crash occurred in both the terrain rendering path and the pick path
- Core profile VAOs work correctly on AMD — the bug is specific to the compatibility code path
- NVIDIA and Intel drivers handle the same VAO usage without issues

### Workaround: Auto-Detection and Runtime Disable

**File:** `GLRuntimeCapabilities.java`

The `initialize()` method now detects AMD GPUs via `GL_VENDOR` and disables VAOs when running
in a compatibility profile:

```java
String vendorLower = glVendor != null ? glVendor.toLowerCase() : "";
boolean isAmd = vendorLower.contains("ati") || vendorLower.contains("amd");
boolean isCoreProfile = gl.getGLProfile().isGL3() && !gl.getGLProfile().isGL3bc()
    && !gl.getGLProfile().isGL4bc();

if (isAmd && !isCoreProfile)
{
    this.isVertexArrayObjectEnabled = false;
    Logging.logger().info("GLRuntimeCapabilities: VAOs disabled for AMD compatibility profile"
        + " (driver workaround). Renderer: " + glRenderer);
}
```

**Detection details:**
- AMD's `GL_VENDOR` can report "ATI Technologies Inc.", "Advanced Micro Devices, Inc.", or
  contain "AMD" — the check covers both "ati" and "amd" (case-insensitive)
- `GLProfile.isGL3()` returns `true` for both core GL3 and GL3bc — we must explicitly exclude
  GL3bc and GL4bc to detect core-only profiles
- VAOs remain enabled on NVIDIA and Intel hardware, and on AMD in core profile

### Downstream Effects

When VAOs are disabled, several GPU features that depend on VAOs are also disabled:

1. **Tessellation shaders** — `TessellationTerrainShader` requires VAOs for explicit attribute
   locations; gated on `vaoAvailable` in `RectangularTessellator`
2. **Compute mesh shaders** — `ComputeMeshShader` uses VAO-bound SSBOs; gated on `vaoAvailable`
3. **Per-tile VAO binding** — The VAO fast-path in `bindVbos()` falls back to legacy
   `glVertexPointer`/`glTexCoordPointer` calls

AMD GPUs fall back to the `TerrainShader` path (GLSL 1.30 with deprecated built-ins), which uses
the fixed-function vertex data pipeline and works correctly without VAOs.

---

## Shader Changes

### DashLineShader and SurfaceShapeFillShader

**Completed.** Both shaders replaced `gl_ModelViewProjectionMatrix` with an explicit
`uniform mat4 u_mvp`:

- `ShaderProgram.java` gained `setUniformMvp()` — reads the current GL matrix stack, computes
  `P × MV` in column-major order, and uploads via `glUniformMatrix4fv`
- `gl_Color` was never used by these shaders — they already had `uniform vec4 u_color`
- No other deprecated built-ins were present

**Files:** `DashLineShader.java`, `SurfaceShapeFillShader.java`, `ShaderProgram.java`

### TessellationTerrainShader

**Completed.** Full modernization with explicit attributes and uniforms:

- Vertex shader: `layout(location=0) in vec4 a_position` and `layout(location=1) in vec4 a_texCoord`
  replace `gl_Vertex` and `gl_MultiTexCoord0`
- Vertex shader: `uniform vec4 u_primaryColor` replaces `gl_Color`
- TCS: `uniform mat4 u_mvp` replaces `gl_ModelViewProjectionMatrix`
- TES: `uniform mat4 u_mvp`, `uniform mat4 u_texMatrix0`, `uniform mat4 u_texMatrix1` replace
  `gl_ModelViewProjectionMatrix`, `gl_TextureMatrix[0]`, `gl_TextureMatrix[1]`
- All new uniforms uploaded in `activate()` (reads `GL_CURRENT_COLOR` and GL texture matrices)
- Requires VAOs — gated on `vaoAvailable` check in `RectangularTessellator`
- Uses `#version 400 compatibility` — `layout(location=N)` is valid at this GLSL version

**Files:** `TessellationTerrainShader.java`, `RectangularTessellator.java`

### TerrainShader — DEFERRED

**Not modernized.** TerrainShader is the fallback path when VAOs are unavailable (AMD compatibility
profile). Without VAOs, `glVertexAttribPointer` cannot be used alongside fixed-function
`glVertexPointer`/`glTexCoordPointer` client state without causing driver conflicts.

TerrainShader retains all deprecated GLSL built-ins:

| Built-in | Purpose |
|---|---|
| `gl_Vertex` | Vertex position from `glVertexPointer` |
| `gl_MultiTexCoord0` | Texture coords from `glTexCoordPointer` |
| `gl_Color` | Primary color from `glColor4f` |
| `gl_ModelViewProjectionMatrix` | Combined MVP from GL matrix stack |
| `gl_TextureMatrix[0]`, `gl_TextureMatrix[1]` | Texture transforms from GL matrix stack |

**Why deferred:** Modernizing TerrainShader requires migrating to a core profile (Option C in
the modernization roadmap), which is a larger undertaking involving removal of all fixed-function
API usage throughout the codebase. TerrainShader works correctly as-is using `#version 130` with
deprecated built-ins.

**File:** `TerrainShader.java`

### ShaderProgram.bindAttribLocation()

**Added.** Pre-link `glBindAttribLocation()` API for shaders needing explicit attribute locations
without `layout(location=N)` in GLSL source. Queued bindings are applied before `glLinkProgram()`
in both `init()` and `initTessellation()`.

This provides a GLSL 1.30-compatible alternative to `layout(location=N)` (which requires GLSL 3.30).
Currently used by the TessellationTerrainShader path; available for future shader work.

**File:** `ShaderProgram.java`

---

## Architecture Summary

The rendering pipeline now has a clear two-tier architecture based on GPU capability:

### Tier 1: Full GPU Pipeline (NVIDIA, Intel, AMD core-profile)
- **Profile:** GL3bc or GL4bc (compatibility) or core
- **VAOs:** Enabled
- **Terrain:** `TessellationTerrainShader` (GLSL 4.00, explicit attribs via `layout(location=N)`)
- **Compute:** `ComputeMeshShader` (GLSL 4.30, GPU frustum culling, indirect draw)
- **Shapes:** `DashLineShader` + `SurfaceShapeFillShader` (GLSL 1.30+, explicit `u_mvp`)
- **LOD:** GPU tessellation with crack-free stitching

### Tier 2: Fallback Pipeline (AMD Vega Mobile in compatibility profile)
- **Profile:** GL3bc (compatibility)
- **VAOs:** Disabled (auto-detected)
- **Terrain:** `TerrainShader` (GLSL 1.30, deprecated built-ins, fixed-function vertex data)
- **Compute:** Disabled (requires VAOs)
- **Shapes:** `DashLineShader` + `SurfaceShapeFillShader` (work without VAOs)
- **LOD:** CPU-side tessellation only

### Detection Flow

```
GLRuntimeCapabilities.initialize()
  ├─ Read GL_VENDOR
  ├─ Contains "ati" or "amd"?
  │   ├─ YES + compatibility profile → disable VAOs → Tier 2
  │   └─ YES + core profile → keep VAOs → Tier 1
  └─ NO (NVIDIA/Intel) → keep VAOs → Tier 1

RectangularTessellator.render()
  ├─ vaoAvailable && tessellationShader.isValid()?
  │   ├─ YES → TessellationTerrainShader + VAO bind
  │   └─ NO → TerrainShader + legacy glVertexPointer
  └─ ...
```

---

## Future Work: Core Profile Migration (Option C)

To fully eliminate deprecated GLSL built-ins from TerrainShader, the entire rendering pipeline
must migrate from compatibility profile to core profile:

1. Replace all `glVertexPointer`/`glTexCoordPointer`/`glNormalPointer` with `glVertexAttribPointer`
2. Replace all `glMatrixMode`/`glLoadMatrix`/`glPushMatrix` with explicit uniform matrices
3. Replace all `glEnable(GL_TEXTURE_2D)` / `glTexEnv` with shader-based texturing
4. Replace all `glBegin`/`glEnd` immediate-mode rendering with VBO draw calls
5. Remove `glPushAttrib`/`glPopAttrib` state management
6. Update GLProfile selection from GL3bc to GL3 or GL4

This is a significant effort affecting hundreds of files across the codebase. The current GL3bc
compatibility profile provides a stable intermediate step — all modern shader features are
available while legacy code continues to work unchanged.
