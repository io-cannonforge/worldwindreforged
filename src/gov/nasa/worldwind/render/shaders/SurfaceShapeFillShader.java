/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — GLSL vertex+fragment shader for surface shape interior fill rendering.
 * Supports solid color fills, computed texture fills (GL_OBJECT_LINEAR replacement),
 * and explicit per-vertex texture coordinate fills. Replaces the legacy GLU tessellator
 * immediate-mode path (glBegin/glVertex/glEnd) and the deprecated GL_TEXTURE_GEN_S/T
 * fixed-function texture coordinate generation. Falls back to the legacy path on systems
 * without GLSL 1.30 support.
 */
package gov.nasa.worldwind.render.shaders;

import com.jogamp.opengl.GL2;

/**
 * GLSL shader program for rendering surface shape interiors (fills). Supports three modes:
 * <ul>
 *   <li><b>Solid color</b> ({@code u_useTexture=0}) — uniform RGBA color applied to all fragments</li>
 *   <li><b>Computed texture</b> ({@code u_useTexture=1}) — texture sampled using vertex positions as
 *       texture coordinates, transformed by a uniform texture matrix that replicates the legacy
 *       {@code GL_OBJECT_LINEAR} behavior with scaling, reference position offset,
 *       and latitude correction</li>
 *   <li><b>Explicit texture</b> ({@code u_useTexture=2}) — texture sampled using per-vertex texture
 *       coordinates passed via the {@code a_texCoord} attribute (used by SurfacePolygon's
 *       {@code setTextureImageSource()} API)</li>
 * </ul>
 * <p>
 * Uses {@code gl_ModelViewProjectionMatrix} from the compatibility profile (same approach
 * as {@link DashLineShader}) to avoid manual matrix readback.
 * <p>
 * Uniforms:
 * <ul>
 *   <li>{@code u_color} — RGBA fill color (vec4)</li>
 *   <li>{@code u_useTexture} — 0 for solid, 1 for computed texture, 2 for explicit texture</li>
 *   <li>{@code u_texMatrix} — 4x4 texture coordinate transform matrix (mode 1 only)</li>
 *   <li>{@code u_texture} — texture sampler (unit 0)</li>
 * </ul>
 * Vertex attributes:
 * <ul>
 *   <li>{@code a_position} — 2D vertex position in degrees offset from reference (vec2)</li>
 *   <li>{@code a_texCoord} — 2D explicit texture coordinate (vec2, mode 2 only)</li>
 * </ul>
 */
public class SurfaceShapeFillShader
{
    // seaglassfoundry.com: Phase 6 — replaced deprecated gl_ModelViewProjectionMatrix with
    // explicit u_mvp uniform (read from GL matrix stack in each begin*() entry point).
    private static final String VERTEX_SOURCE_FP32 = """
        #version 130
        in vec2 a_position;
        in vec2 a_texCoord;
        uniform mat4 u_mvp;
        uniform mat4 u_texMatrix;
        uniform int u_useTexture;
        out vec2 v_texCoord;

        void main()
        {
            gl_Position = u_mvp * vec4(a_position, 0.0, 1.0);
            if (u_useTexture == 1)
            {
                vec4 tc = u_texMatrix * vec4(a_position, 0.0, 1.0);
                v_texCoord = tc.st;
            }
            else if (u_useTexture == 2)
            {
                v_texCoord = a_texCoord;
            }
        }
        """;

    // seaglassfoundry.com: double-precision variant. Positions and MVP are computed in double so
    // sub-metre surface shapes no longer wobble when the camera zooms to close range. Only the
    // MVP multiply runs at fp64; gl_Position downcasts to vec4 (required). Texture math stays float.
    private static final String VERTEX_SOURCE_FP64 = """
        #version 410
        in dvec2 a_position;
        in vec2 a_texCoord;
        uniform dmat4 u_mvp;
        uniform mat4 u_texMatrix;
        uniform int u_useTexture;
        out vec2 v_texCoord;

        void main()
        {
            gl_Position = vec4(u_mvp * dvec4(a_position, 0.0LF, 1.0LF));
            if (u_useTexture == 1)
            {
                vec4 tc = u_texMatrix * vec4(vec2(a_position), 0.0, 1.0);
                v_texCoord = tc.st;
            }
            else if (u_useTexture == 2)
            {
                v_texCoord = a_texCoord;
            }
        }
        """;

    private static final String FRAGMENT_SOURCE = """
        #version 130
        uniform vec4 u_color;
        uniform int u_useTexture;
        uniform sampler2D u_texture;
        uniform int u_patternMode;
        uniform float u_patternScale;
        uniform float u_patternLineWidth;
        uniform float u_patternAngle;
        in vec2 v_texCoord;

        // seaglassfoundry.com: Phase 7 — explicit fragment output replaces deprecated gl_FragColor
        out vec4 fragColor;

        void main()
        {
            if (u_useTexture >= 1 && u_patternMode == 0)
            {
                // Texture fill (computed coords or explicit per-vertex UVs)
                fragColor = u_color * texture2D(u_texture, v_texCoord);
            }
            else if (u_patternMode > 0)
            {
                // Procedural pattern — v_texCoord holds geographic (lon, lat) offset in degrees
                float scale = u_patternScale;
                float lw    = u_patternLineWidth;

                if (u_patternMode == 1)
                {
                    // Hatch: lines at u_patternAngle degrees
                    float rad = radians(u_patternAngle);
                    float d = mod(v_texCoord.x * cos(rad) + v_texCoord.y * sin(rad), scale);
                    if (d > lw) discard;
                }
                else if (u_patternMode == 2)
                {
                    // Crosshatch: horizontal + vertical lines
                    float dx = mod(v_texCoord.x, scale);
                    float dy = mod(v_texCoord.y, scale);
                    if (dx > lw && dy > lw) discard;
                }
                else if (u_patternMode == 3)
                {
                    // Dots: circular dots on a regular grid
                    vec2 cell   = mod(v_texCoord, scale);
                    float radius = scale * 0.5 * lw;
                    if (length(cell - vec2(scale * 0.5)) > radius) discard;
                }

                fragColor = u_color;
            }
            else
            {
                fragColor = u_color;
            }
        }
        """;

    private ShaderProgram program;
    private int positionAttribLocation = -1;
    private int texCoordAttribLocation = -1;
    private boolean texCoordAttribEnabled;
    // seaglassfoundry.com: true when the linked program uses dvec2 positions and dmat4 MVP.
    // Callers upload position VBOs as GL_DOUBLE via glVertexAttribLPointer and upload the MVP
    // via setUniformMvpDouble. False on GL 3.x fallback.
    private boolean fp64Enabled;

    /**
     * Initializes the shader program. Returns true if compilation and linking succeeded.
     */
    /** Pattern mode constants matching GLSL u_patternMode values. */
    public static final int PATTERN_NONE       = 0;
    public static final int PATTERN_HATCH      = 1;
    public static final int PATTERN_CROSSHATCH = 2;
    public static final int PATTERN_DOTS       = 3;

    /**
     * Initializes the shader program. Returns true if compilation and linking succeeded.
     */
    public boolean init(GL2 gl)
    {
        if (this.program != null && this.program.isValid())
            return true;

        this.fp64Enabled = GLCapabilityCheck.hasShaderFp64(gl);
        String vertexSource = this.fp64Enabled ? VERTEX_SOURCE_FP64 : VERTEX_SOURCE_FP32;

        this.program = new ShaderProgram();
        if (!this.program.init(gl, vertexSource, FRAGMENT_SOURCE))
        {
            // seaglassfoundry.com: if fp64 compile/link fails (driver claims support but rejects
            // the program), retry with the fp32 shader so the feature still works.
            if (this.fp64Enabled)
            {
                this.fp64Enabled = false;
                this.program = new ShaderProgram();
                if (!this.program.init(gl, VERTEX_SOURCE_FP32, FRAGMENT_SOURCE))
                {
                    this.program = null;
                    return false;
                }
            }
            else
            {
                this.program = null;
                return false;
            }
        }

        this.positionAttribLocation = gl.glGetAttribLocation(this.program.getProgramId(), "a_position");
        this.texCoordAttribLocation = gl.glGetAttribLocation(this.program.getProgramId(), "a_texCoord");
        return true;
    }

    /** @return true if this program uses double-precision positions and MVP. */
    public boolean isFp64Enabled()
    {
        return this.fp64Enabled;
    }

    /**
     * Begins solid color fill rendering. Activates the shader and sets the fill color.
     *
     * @param gl the GL2 context
     * @param r  red component (0-1)
     * @param g  green component (0-1)
     * @param b  blue component (0-1)
     * @param a  alpha component (0-1)
     */
    public void beginSolid(GL2 gl, float r, float g, float b, float a)
    {
        this.program.use(gl);
        if (this.fp64Enabled)
            this.program.setUniformMvpDouble(gl, "u_mvp");
        else
            this.program.setUniformMvp(gl, "u_mvp");
        this.program.setUniform4f(gl, "u_color", r, g, b, a);
        this.program.setUniform1i(gl, "u_useTexture", 0);
        this.program.setUniform1i(gl, "u_patternMode", PATTERN_NONE);

        if (this.positionAttribLocation >= 0)
            gl.glEnableVertexAttribArray(this.positionAttribLocation);
        this.texCoordAttribEnabled = false;
    }

    /**
     * Begins textured fill rendering with computed texture coordinates. Activates the shader,
     * sets the color modulation, texture matrix, and binds texture unit 0. Texture coordinates
     * are derived from vertex positions via the texture matrix (replicates GL_OBJECT_LINEAR).
     *
     * @param gl        the GL2 context
     * @param r         red color modulation (0-1)
     * @param g         green color modulation (0-1)
     * @param b         blue color modulation (0-1)
     * @param a         alpha (opacity) (0-1)
     * @param texMatrix 16-element column-major texture coordinate transform matrix
     */
    public void beginTextured(GL2 gl, float r, float g, float b, float a, float[] texMatrix)
    {
        this.program.use(gl);
        if (this.fp64Enabled)
            this.program.setUniformMvpDouble(gl, "u_mvp");
        else
            this.program.setUniformMvp(gl, "u_mvp");
        this.program.setUniform4f(gl, "u_color", r, g, b, a);
        this.program.setUniform1i(gl, "u_useTexture", 1);
        this.program.setUniform1i(gl, "u_patternMode", PATTERN_NONE);
        this.program.setUniformMatrix4fv(gl, "u_texMatrix", texMatrix);
        this.program.setUniform1i(gl, "u_texture", 0); // texture unit 0

        if (this.positionAttribLocation >= 0)
            gl.glEnableVertexAttribArray(this.positionAttribLocation);
        this.texCoordAttribEnabled = false;
    }

    /**
     * Begins procedural pattern fill rendering. Uses vertex position → texture matrix to compute
     * geographic coordinates, then generates the pattern entirely in the fragment shader — no
     * external texture is needed. Call with the same texture matrix as {@link #beginTextured}.
     *
     * @param gl          the GL2 context
     * @param r           red color component (0-1)
     * @param g           green color component (0-1)
     * @param b           blue color component (0-1)
     * @param a           alpha (opacity) (0-1)
     * @param texMatrix   16-element column-major geographic coordinate transform matrix
     * @param patternMode one of {@link #PATTERN_HATCH}, {@link #PATTERN_CROSSHATCH}, {@link #PATTERN_DOTS}
     * @param scale       pattern repeat size in degrees
     * @param lineWidth   line width in degrees (hatch/crosshatch) or radius factor 0–1 (dots)
     * @param angleDeg    rotation angle in degrees (hatch only; ignored for other modes)
     */
    public void beginPattern(GL2 gl, float r, float g, float b, float a,
        float[] texMatrix, int patternMode, float scale, float lineWidth, float angleDeg)
    {
        this.program.use(gl);
        if (this.fp64Enabled)
            this.program.setUniformMvpDouble(gl, "u_mvp");
        else
            this.program.setUniformMvp(gl, "u_mvp");
        this.program.setUniform4f(gl, "u_color", r, g, b, a);
        this.program.setUniform1i(gl, "u_useTexture", 1);   // vertex shader computes v_texCoord
        this.program.setUniform1i(gl, "u_patternMode", patternMode);
        this.program.setUniformMatrix4fv(gl, "u_texMatrix", texMatrix);
        this.program.setUniform1f(gl, "u_patternScale", scale);
        this.program.setUniform1f(gl, "u_patternLineWidth", lineWidth);
        this.program.setUniform1f(gl, "u_patternAngle", angleDeg);

        if (this.positionAttribLocation >= 0)
            gl.glEnableVertexAttribArray(this.positionAttribLocation);
        this.texCoordAttribEnabled = false;
    }

    /**
     * Begins textured fill rendering with explicit per-vertex texture coordinates. Used by
     * shapes that provide their own UV mapping (e.g., SurfacePolygon with setTextureImageSource).
     * The caller must set up glVertexAttribPointer for both a_position and a_texCoord.
     *
     * @param gl the GL2 context
     * @param r  red color modulation (0-1)
     * @param g  green color modulation (0-1)
     * @param b  blue color modulation (0-1)
     * @param a  alpha (opacity) (0-1)
     */
    public void beginExplicitTextured(GL2 gl, float r, float g, float b, float a)
    {
        this.program.use(gl);
        if (this.fp64Enabled)
            this.program.setUniformMvpDouble(gl, "u_mvp");
        else
            this.program.setUniformMvp(gl, "u_mvp");
        this.program.setUniform4f(gl, "u_color", r, g, b, a);
        this.program.setUniform1i(gl, "u_useTexture", 2);
        this.program.setUniform1i(gl, "u_texture", 0); // texture unit 0

        if (this.positionAttribLocation >= 0)
            gl.glEnableVertexAttribArray(this.positionAttribLocation);
        if (this.texCoordAttribLocation >= 0)
            gl.glEnableVertexAttribArray(this.texCoordAttribLocation);
        this.texCoordAttribEnabled = true;
    }

    /**
     * Returns the attribute location for {@code a_position}, or -1 if not found.
     */
    public int getPositionAttribLocation()
    {
        return this.positionAttribLocation;
    }

    /**
     * Returns the attribute location for {@code a_texCoord}, or -1 if not found.
     */
    public int getTexCoordAttribLocation()
    {
        return this.texCoordAttribLocation;
    }

    /**
     * Ends fill rendering. Disables vertex attributes and deactivates the shader.
     */
    public void end(GL2 gl)
    {
        if (this.positionAttribLocation >= 0)
            gl.glDisableVertexAttribArray(this.positionAttribLocation);
        if (this.texCoordAttribEnabled && this.texCoordAttribLocation >= 0)
            gl.glDisableVertexAttribArray(this.texCoordAttribLocation);
        this.texCoordAttribEnabled = false;
        this.program.unuse(gl);
    }

    public boolean isValid()
    {
        return this.program != null && this.program.isValid();
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
