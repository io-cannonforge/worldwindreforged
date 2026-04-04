/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file (Task 4.2 — GPU LOD / Tessellation Shaders; updated Task 4.4 — crack-free stitching;
 * updated Task 4.5 — sub-grid heightmap displacement) — GLSL 4.00 tessellation pipeline
 * for terrain tile rendering. Uses GL 4.0 tessellation control and evaluation shaders to
 * adaptively subdivide each coarse grid quad patch based on screen-space edge length:
 *
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │  Vertex Shader (4.00 compat)                                    │
 *   │    pass gl_Vertex.xyz (object-space, pre-displaced by CPU) and  │
 *   │    gl_MultiTexCoord0.st through — no projection here            │
 *   │        ↓                                                        │
 *   │  Tessellation Control Shader (4.00 compat)                      │
 *   │    4 vertices per patch (quad); computes screen-space edge      │
 *   │    length per outer edge; sets gl_TessLevelOuter[0-3] and       │
 *   │    gl_TessLevelInner[0-1] proportional to pixel coverage        │
 *   │        ↓                                                        │
 *   │  Tessellation Evaluation Shader (4.00 compat)                   │
 *   │    layout(quads, fractional_even_spacing, ccw); bilinearly      │
 *   │    interpolates position + tile UV; applies SurfaceTileRenderer  │
 *   │    texture-matrix transforms; heightmap delta-correction;       │
 *   │    projects to clip space                                       │
 *   │        ↓                                                        │
 *   │  Fragment Shader (4.00 compat)                                  │
 *   │    same as TerrainShader: samples imagery (unit 0) × alpha      │
 *   │    mask (unit 1)                                                │
 *   └─────────────────────────────────────────────────────────────────┘
 *
 * Patch vertex ordering (indices 0-3 in the quad patch index buffer):
 *   0 = BL (u=0, v=0),  1 = BR (u=1, v=0)
 *   2 = TR (u=1, v=1),  3 = TL (u=0, v=1)
 *
 * The coarse tile vertices are already displaced by the terrain elevation computed
 * on the CPU in RectangularTessellator.buildVerts(). The TES bilinearly interpolates
 * between those pre-elevated corners — which bakes in a coarse (density+2)-resolution
 * elevation field. Task 4.5 adds sub-grid heightmap displacement using a delta-correction
 * approach: the heightmap stores (verticalExaggeration × elevation) for the interior
 * (density+1)² sample grid; the TES computes the sphere-approximated height already
 * encoded in the bilinearly-interpolated position (h_bilinear = length(worldPos) −
 * u_earthRadius) and adds only the residual (h_actual − h_bilinear) along the surface
 * normal. u_earthRadius is the ellipsoid radius at the tile center (length(refCenter))
 * so the approximation stays accurate across small tiles.
 *
 * Falls back to the TerrainShader (Task 4.1) when GL 4.0 is unavailable.
 *
 * Fix — Flat globe (2D) heightmap delta-correction:
 * Added u_flatGlobe uniform; when set to 1 the TES uses the Z component directly
 * as the bilinear elevation (since Z=elevation on flat globes) and displaces along
 * the constant Z-up normal, instead of the spherical radial computation which
 * produces incorrect results in the flat Cartesian coordinate system.
 *
 * Changes (Phase 5 — SurfaceTileRenderer modernization):
 * - Added picking support: gl_Color forwarded through all stages as primaryColor
 *   varying. Fragment shader uses u_usePickColor uniform to replace imagery RGB
 *   with the pick color during picking. Alpha test via discard replaces
 *   glAlphaFunc(GL_GREATER, 0.01).
 */
package gov.nasa.worldwind.render.shaders;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES1;
import com.jogamp.opengl.GL3ES3;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;

import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.util.Logging;

/**
 * GLSL 4.00 tessellation pipeline for terrain tile rendering.
 * <p>
 * Replaces the fixed triangle-strip draw with an adaptive quad-patch draw.  Each coarse
 * grid cell becomes one 4-vertex patch.  The tessellation control shader computes a
 * screen-space pixel coverage metric per edge and assigns {@code gl_TessLevelOuter[]} /
 * {@code gl_TessLevelInner[]} accordingly, clamped to [1, 64].  The tessellation
 * evaluation shader bilinearly interpolates positions and applies the existing
 * {@code SurfaceTileRenderer} texture-matrix transforms so no changes to the renderer
 * are required.
 * <p>
 * Requires OpenGL 4.0 (ARB_tessellation_shader).  Initialization is guarded by
 * {@code gl.isGL4()} and the caller's {@code GLRuntimeCapabilities.isUseTessellation()}
 * flag.
 */
public class TessellationTerrainShader
{
    /**
     * Target screen-space pixels per subdivided triangle edge.  A value of 8 means the
     * GPU will subdivide until each tessellated edge spans roughly 12 pixels, balancing
     * geometric detail against triangle budget.
     */
    public static final float DEFAULT_PIXELS_PER_TRIANGLE = 12.0f;

    // --- Vertex Shader -----------------------------------------------------------
    // Passes the raw object-space position and tile UV through to the TCS.
    // No projection here — the TES projects after any displacement.
    // seaglassfoundry.com: Phase 6 — replaced gl_Vertex/gl_MultiTexCoord0/gl_Color with
    // explicit attribs (layout 0/1) and u_primaryColor uniform.
    private static final String VERTEX_SOURCE = """
        #version 400 compatibility

        layout(location=0) in vec4 aPosition;
        layout(location=1) in vec2 aTexCoord;

        uniform vec4 u_primaryColor;

        out vec2 tc_tileUV;
        out vec4 tc_primaryColor;

        void main()
        {
            gl_Position    = aPosition;
            tc_tileUV      = aTexCoord;
            tc_primaryColor = u_primaryColor;
        }
        """;

    // --- Tessellation Control Shader --------------------------------------------
    // layout(vertices = 4): one invocation per patch corner.
    // Invocation 0 computes the four outer and two inner tessellation levels from
    // the screen-space edge lengths of the projected quad corners.
    private static final String TCS_SOURCE = """
        #version 400 compatibility

        layout(vertices = 4) out;

        in  vec2 tc_tileUV[];
        in  vec4 tc_primaryColor[];
        out vec2 te_tileUV[];
        out vec4 te_primaryColor[];

        uniform mat4  u_mvp;                 // seaglassfoundry.com Phase 6: replaces gl_ModelViewProjectionMatrix
        uniform vec2  u_viewport;           // framebuffer width, height in pixels
        uniform float u_pixelsPerTriangle;  // target px per tessellated edge
        uniform float u_maxOuter[4];        // per-edge max level; CPU sets to min(own,neighbour)

        void main()
        {
            // Pass position, UV, and primary color to TES unchanged.
            gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position;
            te_tileUV[gl_InvocationID] = tc_tileUV[gl_InvocationID];
            te_primaryColor[gl_InvocationID] = tc_primaryColor[gl_InvocationID];

            // Only invocation 0 computes tessellation levels (barrier not needed for
            // writes to gl_TessLevelOuter/Inner since all invocations see the same result).
            if (gl_InvocationID == 0)
            {
                // Project the four patch corners to clip space, then to screen space.
                // Patch order: [0]=BL, [1]=BR, [2]=TR, [3]=TL
                vec4 c0 = u_mvp * gl_in[0].gl_Position;
                vec4 c1 = u_mvp * gl_in[1].gl_Position;
                vec4 c2 = u_mvp * gl_in[2].gl_Position;
                vec4 c3 = u_mvp * gl_in[3].gl_Position;

                // Modified by seaglassfoundry.com - clamp w to a small positive value
                // instead of using abs(w). When vertices fall behind the near clip plane
                // (w < 0), abs(w) inverts the perspective divide, flipping screen-space
                // coordinates and causing terrain tiles to appear rotated/inside-out.
                float w0 = max(c0.w, 1e-4);
                float w1 = max(c1.w, 1e-4);
                float w2 = max(c2.w, 1e-4);
                float w3 = max(c3.w, 1e-4);

                // NDC → screen pixel coordinates.
                vec2 s0 = (c0.xy / w0 * 0.5 + 0.5) * u_viewport;
                vec2 s1 = (c1.xy / w1 * 0.5 + 0.5) * u_viewport;
                vec2 s2 = (c2.xy / w2 * 0.5 + 0.5) * u_viewport;
                vec2 s3 = (c3.xy / w3 * 0.5 + 0.5) * u_viewport;

                // Screen-space length of each outer edge.
                // gl_TessLevelOuter[0] → u=0 edge (left:  TL→BL = s3→s0)
                // gl_TessLevelOuter[1] → v=0 edge (bottom: BL→BR = s0→s1)
                // gl_TessLevelOuter[2] → u=1 edge (right:  BR→TR = s1→s2)
                // gl_TessLevelOuter[3] → v=1 edge (top:    TR→TL = s2→s3)
                // Crack-free stitching (Task 4.4): cap each outer level to the CPU-computed
                // min(own, neighbour) so both sides of a shared tile edge use the same level.
                float p = u_pixelsPerTriangle;
                gl_TessLevelOuter[0] = min(clamp(length(s0 - s3) / p, 1.0, 32.0), u_maxOuter[0]);
                gl_TessLevelOuter[1] = min(clamp(length(s1 - s0) / p, 1.0, 32.0), u_maxOuter[1]);
                gl_TessLevelOuter[2] = min(clamp(length(s2 - s1) / p, 1.0, 32.0), u_maxOuter[2]);
                gl_TessLevelOuter[3] = min(clamp(length(s3 - s2) / p, 1.0, 32.0), u_maxOuter[3]);

                // Inner level = max of all four outer levels for square-ish subdivision.
                float inner = max(max(gl_TessLevelOuter[0], gl_TessLevelOuter[2]),
                                  max(gl_TessLevelOuter[1], gl_TessLevelOuter[3]));
                gl_TessLevelInner[0] = inner;
                gl_TessLevelInner[1] = inner;
            }
        }
        """;

    // --- Tessellation Evaluation Shader -----------------------------------------
    // layout(quads, fractional_even_spacing, ccw):
    //   quads             — bilinear interpolation domain (gl_TessCoord.xy ∈ [0,1]²)
    //   fractional_even   — smooth LOD morphing; avoids popping at level boundaries
    //   ccw               — matches WorldWind's counter-clockwise front-face winding
    //
    // Bilinearly interpolates object-space positions from the 4 patch corners, applies
    // the SurfaceTileRenderer texture matrices, and projects to clip space.
    //
    // Sub-grid heightmap displacement (Task 4.5):
    //   The 4 patch corners carry CPU-pre-elevated positions — their bilinear blend
    //   already encodes a coarse elevation field.  Sampling the heightmap and adding h
    //   directly would double-displace.  Instead we add only the RESIDUAL:
    //     h_bilinear = length(worldPos) − u_earthRadius   (sphere approx, accurate for
    //                                                       small tiles)
    //     delta      = h_actual − h_bilinear
    //     pos       += normalize(worldPos) * delta
    //   u_earthRadius is the ellipsoid radius at the tile centre (length of refCenter,
    //   computed on the CPU).  The heightmap stores verticalExaggeration × elevation so
    //   no extra scale is needed here.
    private static final String TES_SOURCE = """
        #version 400 compatibility

        layout(quads, fractional_even_spacing, ccw) in;

        in  vec2 te_tileUV[];
        in  vec4 te_primaryColor[];

        out vec2 v_texCoord0;
        out vec2 v_texCoord1;
        out vec4 v_primaryColor;

        uniform mat4      u_mvp;          // seaglassfoundry.com Phase 6: replaces gl_ModelViewProjectionMatrix
        uniform mat4      u_texMatrix0;   // seaglassfoundry.com Phase 6: replaces gl_TextureMatrix[0]
        uniform mat4      u_texMatrix1;   // seaglassfoundry.com Phase 6: replaces gl_TextureMatrix[1]
        uniform sampler2D u_heightmap;
        uniform vec3      u_refCenter;
        uniform float     u_earthRadius;
        uniform int       u_useHeightmap;
        uniform int       u_flatGlobe;

        void main()
        {
            // Primary color is constant across the patch (pick color); use vertex 0.
            v_primaryColor = te_primaryColor[0];
            float u = gl_TessCoord.x;
            float v = gl_TessCoord.y;

            // Bilinear interpolation of object-space positions.
            // Patch order: [0]=BL(u=0,v=0), [1]=BR(u=1,v=0), [2]=TR(u=1,v=1), [3]=TL(u=0,v=1)
            vec4 pos = mix(
                mix(gl_in[0].gl_Position, gl_in[1].gl_Position, u),
                mix(gl_in[3].gl_Position, gl_in[2].gl_Position, u),
                v
            );

            // Bilinear interpolation of tile UV coordinates.
            vec2 uv = mix(
                mix(te_tileUV[0], te_tileUV[1], u),
                mix(te_tileUV[3], te_tileUV[2], u),
                v
            );

            // Sub-grid heightmap delta-correction (Task 4.5).
            if (u_useHeightmap == 1)
            {
                float h_actual = texture(u_heightmap, uv).r;
                if (u_flatGlobe == 1)
                {
                    // Flat globe: Z axis is elevation.  The bilinearly-interpolated position
                    // already encodes a coarse elevation in its Z component.  Add only the
                    // residual along the constant Z-up normal.
                    float h_bilinear = pos.z;
                    pos.z += h_actual - h_bilinear;
                }
                else
                {
                    // Spherical globe: radial distance from Earth centre encodes elevation.
                    vec3  worldPos   = pos.xyz + u_refCenter;
                    vec3  normal     = normalize(worldPos);
                    float h_bilinear = length(worldPos) - u_earthRadius;
                    pos.xyz += normal * (h_actual - h_bilinear);
                }
            }

            // seaglassfoundry.com: Phase 6 — explicit uniforms replace deprecated gl_TextureMatrix
            // and gl_ModelViewProjectionMatrix. Both are uploaded per image tile in activate().
            v_texCoord0 = (u_texMatrix0 * vec4(uv, 0.0, 1.0)).st;
            v_texCoord1 = (u_texMatrix1 * vec4(uv, 0.0, 1.0)).st;

            gl_Position = u_mvp * pos;
        }
        """;

    // --- Fragment Shader --------------------------------------------------------
    // Identical to the TerrainShader fragment shader, adapted for explicit varyings.
    private static final String FRAGMENT_SOURCE = """
        #version 400 compatibility

        uniform sampler2D u_imagery;
        uniform sampler2D u_alphaMask;
        uniform int u_usePickColor;

        in vec2 v_texCoord0;
        in vec2 v_texCoord1;
        in vec4 v_primaryColor;

        // Phase 7: explicit fragment output replaces deprecated gl_FragColor
        out vec4 fragColor;

        void main()
        {
            vec4 color       = texture(u_imagery,   v_texCoord0);
            vec4 alphaSample = texture(u_alphaMask,  v_texCoord1);
            float alpha = color.a * alphaSample.a;

            // Shader-based alpha test replaces glAlphaFunc(GL_GREATER, 0.01)
            if (alpha <= 0.01) discard;

            // Pick color mode: replace imagery RGB with primary color (pick color)
            vec3 rgb = (u_usePickColor == 1) ? v_primaryColor.rgb : color.rgb;

            // seaglassfoundry.com: apply per-layer opacity from v_primaryColor.a (set via
            // glColor4d in TiledImageLayer.setBlendingFunction). Premultiplied alpha blending
            // requires scaling both RGB and alpha by the layer opacity.
            float layerOpacity = v_primaryColor.a;
            fragColor = vec4(rgb * layerOpacity, alpha * layerOpacity);
        }
        """;

    /** Passed to {@link #activate} when no neighbour-level constraint is needed. */
    private static final float[] UNCONSTRAINED_OUTER = {32f, 32f, 32f, 32f};

    private ShaderProgram program;

    /**
     * Compiles and links the tessellation shader pipeline.
     *
     * @param gl the GL2 context (must be backed by a GL 4.0+ implementation)
     * @return true if compilation and linking succeeded
     */
    public boolean init(GL2 gl)
    {
        this.program = new ShaderProgram();
        if (!this.program.initTessellation(gl, VERTEX_SOURCE, TCS_SOURCE, TES_SOURCE, FRAGMENT_SOURCE))
        {
            Logging.logger().warning(
                "TessellationTerrainShader: compilation failed, falling back to TerrainShader.");
            this.program = null;
            return false;
        }
        return true;
    }

    public boolean isValid()
    {
        return this.program != null && this.program.isValid();
    }

    /**
     * Activates the tessellation pipeline for the next patch draw call.
     * Sets {@code GL_PATCH_VERTICES = 4} and uploads all required uniforms.
     * When a heightmap texture is available the TES applies sub-grid delta-correction
     * displacement (Task 4.5): only the residual elevation above the bilinearly-
     * interpolated coarse position is added, avoiding double-displacement.
     *
     * @param gl               the GL2 context
     * @param dc               the current draw context (viewport, GpuResourceCache)
     * @param heightmapCacheKey cache key for this tile's heightmap texture (may be null)
     * @param refCenterX       tile reference center X in ECEF (zero-elevation point)
     * @param refCenterY       tile reference center Y in ECEF (zero-elevation point)
     * @param refCenterZ       tile reference center Z in ECEF (zero-elevation point)
     * @param maxOuterLevels   per-edge max tessellation level caps (4 floats, index = gl_TessLevelOuter index);
     *                         pass {@code null} for no constraint (all caps = 64)
     */
    public void activate(GL2 gl, DrawContext dc, Object heightmapCacheKey,
                         double refCenterX, double refCenterY, double refCenterZ,
                         float[] maxOuterLevels)
    {
        this.activate(gl, dc, heightmapCacheKey, refCenterX, refCenterY, refCenterZ,
            maxOuterLevels, false);
    }

    /**
     * Activates the tessellation pipeline with optional picking support.
     *
     * @param gl               the GL2 context
     * @param dc               the current draw context (viewport, GpuResourceCache)
     * @param heightmapCacheKey cache key for this tile's heightmap texture (may be null)
     * @param refCenterX       tile reference center X in ECEF (zero-elevation point)
     * @param refCenterY       tile reference center Y in ECEF (zero-elevation point)
     * @param refCenterZ       tile reference center Z in ECEF (zero-elevation point)
     * @param maxOuterLevels   per-edge max tessellation level caps (4 floats, index = gl_TessLevelOuter index);
     *                         pass {@code null} for no constraint (all caps = 64)
     * @param usePickColor     true to replace imagery RGB with the primary color (pick color)
     */
    // seaglassfoundry.com: Phase 5 — added usePickColor parameter for shader-based picking
    public void activate(GL2 gl, DrawContext dc, Object heightmapCacheKey,
                         double refCenterX, double refCenterY, double refCenterZ,
                         float[] maxOuterLevels, boolean usePickColor)
    {
        this.program.use(gl);

        // Each patch has exactly 4 vertices (quad corners).
        if (gl.isGL4())
            gl.getGL4().glPatchParameteri(GL3ES3.GL_PATCH_VERTICES, 4);

        // seaglassfoundry.com: Phase 6 — upload explicit MVP, texture matrices, and primary color
        // replacing deprecated gl_ModelViewProjectionMatrix, gl_TextureMatrix[0/1], and gl_Color.
        this.program.setUniformMvp(gl, "u_mvp");

        float[] currentColor = new float[4];
        gl.glGetFloatv(GL2ES1.GL_CURRENT_COLOR, currentColor, 0);
        this.program.setUniform4f(gl, "u_primaryColor",
            currentColor[0], currentColor[1], currentColor[2], currentColor[3]);

        float[] mat = new float[16];
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glGetFloatv(GLMatrixFunc.GL_TEXTURE_MATRIX, mat, 0);
        this.program.setUniformMatrix4fv(gl, "u_texMatrix0", mat);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glGetFloatv(GLMatrixFunc.GL_TEXTURE_MATRIX, mat, 0);
        this.program.setUniformMatrix4fv(gl, "u_texMatrix1", mat);
        gl.glActiveTexture(GL.GL_TEXTURE0);

        // Imagery unit 0, alpha mask unit 1 — same as TerrainShader.
        this.program.setUniform1i(gl, "u_imagery",   0);
        this.program.setUniform1i(gl, "u_alphaMask", 1);
        this.program.setUniform1i(gl, "u_usePickColor", usePickColor ? 1 : 0);

        // Read viewport from the View (Java-side) instead of glGetIntegerv (GPU sync).
        java.awt.Rectangle vp = dc.getView().getViewport();
        this.program.setUniform2f(gl, "u_viewport", vp.width, vp.height);
        this.program.setUniform1f(gl, "u_pixelsPerTriangle", DEFAULT_PIXELS_PER_TRIANGLE);

        // Crack-free stitching (Task 4.4): per-edge max tessellation level caps.
        // CPU sets these to min(own_level, neighbour_level) so both sides of a shared
        // tile edge are capped to the same value, preventing T-junction cracks.
        int maxOuterLoc = this.program.getUniformLocation(gl, "u_maxOuter[0]");
        float[] outer = (maxOuterLevels != null) ? maxOuterLevels : UNCONSTRAINED_OUTER;
        gl.glUniform1fv(maxOuterLoc, 4, outer, 0);

        // Sub-grid heightmap displacement (Task 4.5).
        // The heightmap texture stores (verticalExaggeration × elevation) for the interior
        // (density+1)² sample grid.  We compute the residual above the bilinearly-
        // interpolated vertex position in the TES, so no u_heightScale is needed here —
        // the delta is already in the same units as the ECEF vertex coordinates.
        int useHeightmap = 0;
        if (heightmapCacheKey != null && dc.getGLRuntimeCapabilities().getNumTextureUnits() >= 4)
        {
            int[] texId = (int[]) dc.getGpuResourceCache().get(heightmapCacheKey);
            if (texId != null)
            {
                gl.glActiveTexture(GL.GL_TEXTURE3);
                gl.glBindTexture(GL.GL_TEXTURE_2D, texId[0]);
                gl.glActiveTexture(GL.GL_TEXTURE0);
                this.program.setUniform1i(gl, "u_heightmap", 3);
                this.program.setUniform3f(gl, "u_refCenter",
                    (float) refCenterX, (float) refCenterY, (float) refCenterZ);
                // Ellipsoid radius at the tile centre — length of the zero-elevation refCenter
                // point used as the local sphere radius for the delta-correction in the TES.
                float earthRadius = (float) Math.sqrt(
                    refCenterX * refCenterX + refCenterY * refCenterY + refCenterZ * refCenterZ);
                this.program.setUniform1f(gl, "u_earthRadius", earthRadius);
                useHeightmap = 1;
            }
        }
        this.program.setUniform1i(gl, "u_useHeightmap", useHeightmap);
        this.program.setUniform1i(gl, "u_flatGlobe", dc.is2DGlobe() ? 1 : 0);
    }

    /**
     * Deactivates the tessellation pipeline and unbinds the heightmap from unit 3.
     *
     * @param gl the GL2 context
     */
    public void deactivate(GL2 gl)
    {
        gl.glActiveTexture(GL.GL_TEXTURE3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);

        this.program.unuse(gl);
    }

    // ── seaglassfoundry.com: tile-scoped shader lifecycle ──────────────────
    // When SurfaceTileRenderer draws multiple image tiles for the same geometry
    // tile, the shader program and most uniforms are identical across draws.
    // These methods allow activating once per geometry tile and only updating
    // the texture matrices (via updateTextureState) per image tile draw.

    /**
     * Activates the tessellation pipeline for a geometry tile. Call once before
     * rendering multiple image tiles, paired with {@link #deactivateForTile}.
     * <p>
     * Uploads all uniforms that are constant across image tiles: program, MVP,
     * primaryColor, viewport, pixelsPerTriangle, maxOuter, heightmap, samplers,
     * usePickColor, flatGlobe. Does NOT upload texture matrices — call
     * {@link #updateTextureState} before each image tile draw.
     */
    public void activateForTile(GL2 gl, DrawContext dc, Object heightmapCacheKey,
                                double refCenterX, double refCenterY, double refCenterZ,
                                float[] maxOuterLevels, boolean usePickColor)
    {
        this.program.use(gl);

        if (gl.isGL4())
            gl.getGL4().glPatchParameteri(GL3ES3.GL_PATCH_VERTICES, 4);

        // MVP and primary color are constant for all image tiles on this geometry tile.
        this.program.setUniformMvp(gl, "u_mvp");

        float[] currentColor = new float[4];
        gl.glGetFloatv(GL2ES1.GL_CURRENT_COLOR, currentColor, 0);
        this.program.setUniform4f(gl, "u_primaryColor",
            currentColor[0], currentColor[1], currentColor[2], currentColor[3]);

        // Sampler bindings.
        this.program.setUniform1i(gl, "u_imagery",   0);
        this.program.setUniform1i(gl, "u_alphaMask", 1);
        this.program.setUniform1i(gl, "u_usePickColor", usePickColor ? 1 : 0);

        // Viewport and tessellation parameters.
        java.awt.Rectangle vp = dc.getView().getViewport();
        this.program.setUniform2f(gl, "u_viewport", vp.width, vp.height);
        this.program.setUniform1f(gl, "u_pixelsPerTriangle", DEFAULT_PIXELS_PER_TRIANGLE);

        int maxOuterLoc = this.program.getUniformLocation(gl, "u_maxOuter[0]");
        float[] outer = (maxOuterLevels != null) ? maxOuterLevels : UNCONSTRAINED_OUTER;
        gl.glUniform1fv(maxOuterLoc, 4, outer, 0);

        // Heightmap displacement.
        int useHeightmap = 0;
        if (heightmapCacheKey != null && dc.getGLRuntimeCapabilities().getNumTextureUnits() >= 4)
        {
            int[] texId = (int[]) dc.getGpuResourceCache().get(heightmapCacheKey);
            if (texId != null)
            {
                gl.glActiveTexture(GL.GL_TEXTURE3);
                gl.glBindTexture(GL.GL_TEXTURE_2D, texId[0]);
                gl.glActiveTexture(GL.GL_TEXTURE0);
                this.program.setUniform1i(gl, "u_heightmap", 3);
                this.program.setUniform3f(gl, "u_refCenter",
                    (float) refCenterX, (float) refCenterY, (float) refCenterZ);
                float earthRadius = (float) Math.sqrt(
                    refCenterX * refCenterX + refCenterY * refCenterY + refCenterZ * refCenterZ);
                this.program.setUniform1f(gl, "u_earthRadius", earthRadius);
                useHeightmap = 1;
            }
        }
        this.program.setUniform1i(gl, "u_useHeightmap", useHeightmap);
        this.program.setUniform1i(gl, "u_flatGlobe", dc.is2DGlobe() ? 1 : 0);
    }

    /**
     * Updates only the texture matrix uniforms before an image tile draw.
     * <p>
     * Reads the texture matrix from unit 0 once and uploads it to both
     * {@code u_texMatrix0} and {@code u_texMatrix1} (SurfaceTileRenderer always
     * applies identical transforms to the imagery and alpha mask units).
     * This replaces the two separate {@code glGetFloatv} readbacks that the
     * monolithic {@link #activate} method performed.
     */
    public void updateTextureState(GL2 gl)
    {
        float[] mat = new float[16];
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glGetFloatv(GLMatrixFunc.GL_TEXTURE_MATRIX, mat, 0);
        this.program.setUniformMatrix4fv(gl, "u_texMatrix0", mat);
        this.program.setUniformMatrix4fv(gl, "u_texMatrix1", mat);
    }

    /**
     * Deactivates the tessellation pipeline after all image tiles for a
     * geometry tile have been drawn.
     */
    public void deactivateForTile(GL2 gl)
    {
        gl.glActiveTexture(GL.GL_TEXTURE3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);

        this.program.unuse(gl);
    }

    public void dispose(GL2 gl)
    {
        if (this.program != null)
        {
            this.program.dispose(gl);
            this.program = null;
        }
    }
}
