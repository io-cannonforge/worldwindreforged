/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — GLSL vertex+fragment shader for dashed line rendering, replacing the
 * deprecated glLineStipple() fixed-function call. Uses cumulative distance passed
 * from the vertex shader to the fragment shader with mod()-based dash/gap patterns.
 * Includes segment subdivision and batch rebasing for float32 precision at extreme
 * zoom levels. Falls back to glLineStipple() on systems without GLSL 1.30 support.
 */
package gov.nasa.worldwind.render.shaders;

import com.jogamp.opengl.GL2;

/**
 * GLSL shader program that renders dashed lines. The vertex shader passes the cumulative distance along the line to
 * the fragment shader, which discards fragments in the gap portions of the dash pattern.
 * <p>
 * This replaces the deprecated GL2 {@code glLineStipple()} with a GPU-based solution that produces zoom-independent,
 * resolution-independent dashes. The dash pattern stays the same visual size regardless of zoom level.
 * <p>
 * Uniforms:
 * <ul>
 *   <li>{@code u_color} — RGBA line color (vec4)</li>
 *   <li>{@code u_dashLength} — length of one dash+gap cycle in screen pixels</li>
 *   <li>{@code u_gapRatio} — fraction of the cycle that is gap (0.0–1.0)</li>
 *   <li>{@code u_picking} — 1 if in picking mode (disables dash, renders solid)</li>
 * </ul>
 * Vertex attribute:
 * <ul>
 *   <li>{@code a_dist} — cumulative distance along the line strip in screen pixels (computed on CPU)</li>
 * </ul>
 */
public class DashLineShader
{
    // seaglassfoundry.com: positions are now sourced from a generic vertex
    // attribute (a_position) instead of fixed-function gl_Vertex. Mixing
    // glVertexPointer with a generic glVertexAttribPointer in the same draw
    // is undefined in compatibility profile and produced stray dots in the
    // dashed line example plus state-leak terrain corruption. Both attributes
    // are pinned to non-zero slots in init() to avoid aliasing with any
    // remaining fixed-function client array on slot 0.
    private static final String VERTEX_SOURCE_FP32 = """
        #version 130
        uniform mat4 u_mvp;
        in vec2 a_position;
        in float a_dist;
        // seaglassfoundry.com: noperspective so screen-space dash distance
        // interpolates linearly in window space (avoids stray dots on
        // foreshortened segments).
        noperspective out float v_dist;

        void main()
        {
            gl_Position = u_mvp * vec4(a_position, 0.0, 1.0);
            v_dist = a_dist;
        }
        """;

    // seaglassfoundry.com: double-precision variant. Positions and MVP computed in fp64 to
    // eliminate visible dash wobble at close zoom (sub-metre segment lengths). gl_Position must
    // still downcast to vec4. a_dist remains float — screen-space distance is small-magnitude and
    // already rebased every 100k pixels on the CPU side.
    private static final String VERTEX_SOURCE_FP64 = """
        #version 410
        uniform dmat4 u_mvp;
        in dvec2 a_position;
        in float a_dist;
        noperspective out float v_dist;

        void main()
        {
            gl_Position = vec4(u_mvp * dvec4(a_position, 0.0LF, 1.0LF));
            v_dist = a_dist;
        }
        """;

    // seaglassfoundry.com: Phase 7 — replaced deprecated gl_FragColor with explicit out vec4 fragColor
    private static final String FRAGMENT_SOURCE = """
        #version 130
        // seaglassfoundry.com: must match vertex stage qualifier.
        noperspective in float v_dist;
        uniform vec4 u_color;
        uniform float u_dashLength;
        uniform int u_stipplePattern;
        uniform int u_picking;

        out vec4 fragColor;

        void main()
        {
            if (u_picking == 0 && u_dashLength > 0.0)
            {
                // Map distance within the cycle to a bit index in the 16-bit stipple pattern.
                // The cycle length equals u_dashLength (one full 16-bit repeat).
                // Bit 0 is LSB, matching glLineStipple() bit ordering.
                float pos = mod(v_dist, u_dashLength);
                // seaglassfoundry.com: clamp instead of mask — float rounding in
                // mod() can push the index to 16, and a bare & 15 would wrap to 0
                // and re-light bit 0 inside a gap.
                int bit = clamp(int(pos / u_dashLength * 16.0), 0, 15);
                if (((u_stipplePattern >> bit) & 1) == 0)
                    discard;
            }
            fragColor = u_color;
        }
        """;

    // seaglassfoundry.com: pin both attributes to fixed, non-zero slots so the
    // linker can't alias a_position with gl_Vertex (slot 0 in compat profile).
    private static final int POS_ATTRIB_INDEX  = 1;
    private static final int DIST_ATTRIB_INDEX = 2;

    private ShaderProgram program;
    private int posAttribLocation  = -1;
    private int distAttribLocation = -1;
    // seaglassfoundry.com: when true, a_position is dvec2 and u_mvp is dmat4; callers must upload
    // positions via glVertexAttribLPointer(GL_DOUBLE) and the MVP via setUniformMvpDouble.
    private boolean fp64Enabled;

    public boolean init(GL2 gl)
    {
        if (this.program != null && this.program.isValid())
            return true;

        this.fp64Enabled = GLCapabilityCheck.hasShaderFp64(gl);
        String vertexSource = this.fp64Enabled ? VERTEX_SOURCE_FP64 : VERTEX_SOURCE_FP32;

        this.program = new ShaderProgram();
        this.program.bindAttribLocation(POS_ATTRIB_INDEX,  "a_position");
        this.program.bindAttribLocation(DIST_ATTRIB_INDEX, "a_dist");
        if (!this.program.init(gl, vertexSource, FRAGMENT_SOURCE))
        {
            if (this.fp64Enabled)
            {
                this.fp64Enabled = false;
                this.program = new ShaderProgram();
                this.program.bindAttribLocation(POS_ATTRIB_INDEX,  "a_position");
                this.program.bindAttribLocation(DIST_ATTRIB_INDEX, "a_dist");
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

        this.posAttribLocation  = gl.glGetAttribLocation(this.program.getProgramId(), "a_position");
        this.distAttribLocation = gl.glGetAttribLocation(this.program.getProgramId(), "a_dist");
        return true;
    }

    /** @return true if this program uses double-precision positions and MVP. */
    public boolean isFp64Enabled()
    {
        return this.fp64Enabled;
    }

    /**
     * Activates the shader and sets dash pattern uniforms.
     *
     * @param gl             the GL2 context
     * @param r              red (0-1)
     * @param g              green (0-1)
     * @param b              blue (0-1)
     * @param a              alpha (0-1)
     * @param dashLength     total cycle length in screen pixels (one 16-bit repeat)
     * @param stipplePattern 16-bit stipple pattern (same encoding as glLineStipple):
     *                       bit 0 (LSB) is the start of the pattern; 1=draw, 0=gap.
     *                       E.g. 0xF0F0 gives long dash – long gap, 0xAAAA gives dot-dash.
     * @param picking        true to suppress dashing and render solid for hit-testing
     */
    public void begin(GL2 gl, float r, float g, float b, float a,
                      float dashLength, int stipplePattern, boolean picking)
    {
        this.program.use(gl);
        if (this.fp64Enabled)
            this.program.setUniformMvpDouble(gl, "u_mvp");
        else
            this.program.setUniformMvp(gl, "u_mvp");
        this.program.setUniform4f(gl, "u_color", r, g, b, a);
        this.program.setUniform1f(gl, "u_dashLength", dashLength);
        this.program.setUniform1i(gl, "u_stipplePattern", stipplePattern & 0xFFFF);
        this.program.setUniform1i(gl, "u_picking", picking ? 1 : 0);

        if (this.posAttribLocation >= 0)
            gl.glEnableVertexAttribArray(this.posAttribLocation);
        if (this.distAttribLocation >= 0)
            gl.glEnableVertexAttribArray(this.distAttribLocation);
    }

    public int getPosAttribLocation()
    {
        return this.posAttribLocation;
    }

    public int getDistAttribLocation()
    {
        return this.distAttribLocation;
    }

    public void end(GL2 gl)
    {
        if (this.distAttribLocation >= 0)
            gl.glDisableVertexAttribArray(this.distAttribLocation);
        if (this.posAttribLocation >= 0)
            gl.glDisableVertexAttribArray(this.posAttribLocation);
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
