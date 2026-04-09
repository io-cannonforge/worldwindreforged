/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — GLSL 1.30 vertex+fragment shader for terrain tile rendering, replacing
 * the fixed-function glTexEnvi(GL_MODULATE) blending path in SurfaceTileRenderer.
 * Uses compatibility-profile built-ins (gl_Vertex, gl_MultiTexCoord0, gl_TextureMatrix,
 * gl_ModelViewProjectionMatrix) so no changes to the existing attribute setup or
 * SurfaceTileRenderer texture-transform matrix calls are required. Uploads per-tile
 * elevation data as a GL_R32F heightmap texture (unit 3) for future GPU displacement
 * (u_useHeightmap is 0 in Task 4.1; enabled in subsequent tessellation work).
 * Falls back to the fixed-function path during picking and on GL < 3.0.
 *
 * Fix — Flat globe (2D) heightmap displacement:
 * Added u_flatGlobe uniform; when set to 1 the vertex shader uses a constant Z-up
 * surface normal instead of normalize(worldPos) which is incorrect for the flat
 * globe's Cartesian coordinate system (X=east, Y=north, Z=elevation).
 *
 * Changes (Phase 5 — SurfaceTileRenderer modernization):
 * - Added picking support: u_usePickColor uniform + gl_Color forwarding through
 *   v_primaryColor varying. When u_usePickColor=1 the fragment shader uses the
 *   primary color (pick color set via glColor) for RGB instead of imagery.
 * - Added shader-based alpha test: discard fragments with alpha <= 0.01, replacing
 *   the fixed-function glAlphaFunc(GL_GREATER, 0.01) call in SurfaceTileRenderer.
 *
 * Changes (Phase 7 — core profile shader cleanup):
 * - Replaced deprecated gl_TexCoord[] built-in with explicit v_texCoord0/v_texCoord1 varyings.
 * - Replaced deprecated gl_FragColor with explicit out vec4 fragColor.
 * - Remaining deprecated built-ins (gl_Vertex, gl_MultiTexCoord0, gl_ModelViewProjectionMatrix,
 *   gl_Color) require Java-side refactoring of vertex attribute setup and
 *   matrix upload — deferred to a future phase.
 *
 * Changes (Texture Matrix Optimisation):
 * - Replaced deprecated gl_TextureMatrix[0/1] built-ins with explicit u_texMatrix0/u_texMatrix1
 *   uniform mat4 uploads. SurfaceTileRenderer computes the texture matrix in Java and uploads
 *   directly via updateTextureState(), eliminating the fixed-function matrix stack calls and
 *   glGetFloatv readbacks that cause GPU pipeline stalls.
 */
package gov.nasa.worldwind.render.shaders;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;

import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.util.Logging;

/**
 * GLSL 1.30 vertex+fragment shader for terrain tile rendering.
 * <p>
 * Replaces fixed-function {@code glTexEnvi(GL_MODULATE)} with explicit texture sampling.
 * The vertex shader passes texture coordinates transformed by the GL texture matrix (set by
 * {@code SurfaceTileRenderer}) to the fragment shader, which samples imagery (unit 0) and
 * multiplies by the alpha mask (unit 1).
 * <p>
 * Uniforms:
 * <ul>
 *   <li>{@code u_imagery}    — texture unit 0 (imagery tile)</li>
 *   <li>{@code u_alphaMask}  — texture unit 1 (alpha mask)</li>
 *   <li>{@code u_useHeightmap} — 1 to enable heightmap displacement (reserved; set to 0 in Task 4.1)</li>
 *   <li>{@code u_heightmap}  — texture unit 3 (R32F elevation heightmap, when u_useHeightmap=1)</li>
 *   <li>{@code u_refCenter}  — tile reference center in ECEF (for displacement normal computation)</li>
 *   <li>{@code u_heightScale} — vertical exaggeration scale for heightmap displacement</li>
 *   <li>{@code u_useLighting} — 1 to enable directional lighting (reserved; set to 0 in Task 4.1)</li>
 *   <li>{@code u_flatGlobe}  — 1 when rendering a 2D/flat globe; uses Z-up normal instead of radial</li>
 *   <li>{@code u_usePickColor} — 1 to replace imagery RGB with the primary color (pick color) in picking mode</li>
 * </ul>
 */
public class TerrainShader
{
    // seaglassfoundry.com: uses deprecated GLSL 1.30 compatibility built-ins (gl_Vertex,
    // gl_MultiTexCoord0, gl_Color, gl_ModelViewProjectionMatrix) so the shader works with
    // the existing fixed-function client state set up by beginRendering() and
    // SurfaceTileRenderer — no generic vertex attribute setup required on this path.
    // Texture matrices are explicit uniforms (u_texMatrix0/u_texMatrix1) uploaded from Java.
    private static final String VERTEX_SOURCE = """
        #version 130

        uniform sampler2D u_heightmap;
        uniform vec3 u_refCenter;
        uniform float u_heightScale;
        uniform int u_useHeightmap;
        uniform int u_flatGlobe;
        uniform mat4 u_texMatrix0;
        uniform mat4 u_texMatrix1;

        out vec2 v_tileUV;
        out vec4 v_primaryColor;
        out vec2 v_texCoord0;
        out vec2 v_texCoord1;

        void main()
        {
            vec3 pos = gl_Vertex.xyz;
            v_tileUV = gl_MultiTexCoord0.st;
            v_primaryColor = gl_Color;

            if (u_useHeightmap == 1)
            {
                float h = texture(u_heightmap, v_tileUV).r;
                vec3 surfaceNormal;
                if (u_flatGlobe == 1)
                    surfaceNormal = vec3(0.0, 0.0, 1.0);
                else
                    surfaceNormal = normalize(pos + u_refCenter);
                pos += surfaceNormal * h * u_heightScale;
            }

            gl_Position = gl_ModelViewProjectionMatrix * vec4(pos, 1.0);

            v_texCoord0 = (u_texMatrix0 * gl_MultiTexCoord0).st;
            v_texCoord1 = (u_texMatrix1 * gl_MultiTexCoord0).st;
        }
        """;

    private static final String FRAGMENT_SOURCE = """
        #version 130

        uniform sampler2D u_imagery;
        uniform sampler2D u_alphaMask;
        uniform int u_useLighting;
        uniform int u_usePickColor;

        in vec2 v_tileUV;
        in vec4 v_primaryColor;
        in vec2 v_texCoord0;
        in vec2 v_texCoord1;

        // Phase 7: explicit fragment output replaces deprecated gl_FragColor
        out vec4 fragColor;

        void main()
        {
            vec4 color = texture(u_imagery, v_texCoord0);
            vec4 alphaSample = texture(u_alphaMask, v_texCoord1);
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

    private ShaderProgram program;

    /**
     * Compiles and links the terrain shader program.
     *
     * @param gl the GL2 context
     * @return true if compilation and linking succeeded
     */
    public boolean init(GL2 gl)
    {
        this.program = new ShaderProgram();
        if (!this.program.init(gl, VERTEX_SOURCE, FRAGMENT_SOURCE))
        {
            Logging.logger().warning("TerrainShader: compilation failed, falling back to fixed-function.");
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
     * Activates the shader for the next draw call.
     *
     * @param gl               the GL2 context
     * @param dc               the current draw context (for GpuResourceCache and vertical exaggeration)
     * @param heightmapCacheKey cache key for this tile's heightmap texture (may be null)
     * @param refCenterX       tile reference center X in ECEF
     * @param refCenterY       tile reference center Y in ECEF
     * @param refCenterZ       tile reference center Z in ECEF
     */
    public void activate(GL2 gl, DrawContext dc, Object heightmapCacheKey,
                         double refCenterX, double refCenterY, double refCenterZ)
    {
        this.activate(gl, dc, heightmapCacheKey, refCenterX, refCenterY, refCenterZ, false);
    }

    /**
     * Activates the shader for the next draw call with optional picking support.
     *
     * @param gl               the GL2 context
     * @param dc               the current draw context (for GpuResourceCache and vertical exaggeration)
     * @param heightmapCacheKey cache key for this tile's heightmap texture (may be null)
     * @param refCenterX       tile reference center X in ECEF
     * @param refCenterY       tile reference center Y in ECEF
     * @param refCenterZ       tile reference center Z in ECEF
     * @param usePickColor     true to replace imagery RGB with the primary color (pick color)
     */
    // seaglassfoundry.com: Phase 5 — added usePickColor parameter for shader-based picking
    public void activate(GL2 gl, DrawContext dc, Object heightmapCacheKey,
                         double refCenterX, double refCenterY, double refCenterZ,
                         boolean usePickColor)
    {
        this.program.use(gl);

        // gl_ModelViewProjectionMatrix, gl_TextureMatrix[0/1], and gl_Color are provided
        // automatically by the compatibility profile — no explicit uniform upload needed.

        // Imagery is always on unit 0, alpha mask on unit 1.
        this.program.setUniform1i(gl, "u_imagery", 0);
        this.program.setUniform1i(gl, "u_alphaMask", 1);
        this.program.setUniform1i(gl, "u_useLighting", 0);
        this.program.setUniform1i(gl, "u_usePickColor", usePickColor ? 1 : 0);

        // Attempt to bind the heightmap texture to unit 3.
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
                this.program.setUniform1f(gl, "u_heightScale",
                    (float) dc.getVerticalExaggeration());
                useHeightmap = 1;
            }
        }
        this.program.setUniform1i(gl, "u_useHeightmap", useHeightmap);
        this.program.setUniform1i(gl, "u_flatGlobe", dc.is2DGlobe() ? 1 : 0);
    }

    /**
     * Deactivates the shader and unbinds the heightmap texture from unit 3.
     *
     * @param gl the GL2 context
     */
    public void deactivate(GL2 gl)
    {
        // Unbind heightmap from unit 3 if it was bound.
        gl.glActiveTexture(GL.GL_TEXTURE3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);

        this.program.unuse(gl);
    }

    // ── seaglassfoundry.com: tile-scoped shader lifecycle ──────────────────
    // When SurfaceTileRenderer draws multiple image tiles for the same geometry
    // tile, the shader program and most uniforms are identical across draws.
    // These methods allow activating once per geometry tile and only updating
    // what changes (texture binds + fixed-function texture matrices) per draw.

    /**
     * Activates the shader for a geometry tile. Call once before rendering
     * multiple image tiles, paired with {@link #deactivateForTile}.
     * <p>
     * The GLSL 1.30 vertex shader reads {@code gl_TextureMatrix[0/1]} from
     * fixed-function state automatically, so no per-image-tile texture matrix
     * upload is needed — SurfaceTileRenderer's {@code glScaled/glTranslated}
     * calls are visible on the next draw call.
     */
    public void activateForTile(GL2 gl, DrawContext dc, Object heightmapCacheKey,
                                double refCenterX, double refCenterY, double refCenterZ,
                                boolean usePickColor)
    {
        // Same setup as activate() — program, samplers, heightmap, uniforms.
        this.program.use(gl);

        this.program.setUniform1i(gl, "u_imagery", 0);
        this.program.setUniform1i(gl, "u_alphaMask", 1);
        this.program.setUniform1i(gl, "u_useLighting", 0);
        this.program.setUniform1i(gl, "u_usePickColor", usePickColor ? 1 : 0);

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
                this.program.setUniform1f(gl, "u_heightScale",
                    (float) dc.getVerticalExaggeration());
                useHeightmap = 1;
            }
        }
        this.program.setUniform1i(gl, "u_useHeightmap", useHeightmap);
        this.program.setUniform1i(gl, "u_flatGlobe", dc.is2DGlobe() ? 1 : 0);
    }

    /**
     * Deactivates the shader after all image tiles for a geometry tile have
     * been drawn.
     */
    public void deactivateForTile(GL2 gl)
    {
        gl.glActiveTexture(GL.GL_TEXTURE3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);

        this.program.unuse(gl);
    }

    /**
     * Uploads a pre-computed texture matrix to both {@code u_texMatrix0} and
     * {@code u_texMatrix1}.  Called per image tile draw when the shader is kept
     * active across multiple draws via the tile-scoped lifecycle.
     *
     * @param gl        the GL2 context
     * @param texMatrix column-major 4×4 texture matrix (float[16])
     */
    public void updateTextureState(GL2 gl, float[] texMatrix)
    {
        this.program.setUniformMatrix4fv(gl, "u_texMatrix0", texMatrix);
        this.program.setUniformMatrix4fv(gl, "u_texMatrix1", texMatrix);
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
