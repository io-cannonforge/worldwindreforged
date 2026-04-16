/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — one-shot query for optional GL features used by the shader layer.
 * Currently reports whether the current context supports double-precision in
 * the vertex stage (ARB_gpu_shader_fp64 + ARB_vertex_attrib_64bit, or GL >= 4.1).
 */
package gov.nasa.worldwind.render.shaders;

import java.util.WeakHashMap;
import java.util.Map;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLContext;

import gov.nasa.worldwind.util.Logging;

/**
 * Queries optional GL features relevant to the shader layer. Results are cached per
 * {@link GLContext} so the extension strings are parsed once.
 */
public final class GLCapabilityCheck
{
    private static final Map<GLContext, Boolean> fp64Cache = new WeakHashMap<>();

    private GLCapabilityCheck() {}

    /**
     * @return true iff the current GL context can compile double-precision vertex shaders
     *         (dvec / dmat) and accepts GL_DOUBLE vertex attributes through
     *         {@code glVertexAttribLPointer}. Requires ARB_gpu_shader_fp64 and
     *         ARB_vertex_attrib_64bit, both of which are core from GL 4.1 onward.
     */
    public static boolean hasShaderFp64(GL2 gl)
    {
        GLContext ctx = gl.getContext();
        synchronized (fp64Cache) {
            Boolean cached = fp64Cache.get(ctx);
            if (cached != null) {
                return cached;
            }
        }

        boolean ok;
        try {
            // ARB_gpu_shader_fp64 is core in 4.0; ARB_vertex_attrib_64bit is core in 4.1.
            // JOGL's isGL4() returns true for any GL 4.x context (core or compat). If that's set
            // we still verify the two extensions are advertised — some drivers expose 4.0 without
            // ARB_vertex_attrib_64bit, and emulated/software stacks may lie about both.
            boolean extsOk = gl.isExtensionAvailable("GL_ARB_gpu_shader_fp64")
                          && gl.isExtensionAvailable("GL_ARB_vertex_attrib_64bit");
            ok = ctx.isGL4() && extsOk;
        } catch (Exception e) {
            ok = false;
        }

        synchronized (fp64Cache) {
            fp64Cache.put(ctx, ok);
        }
        Logging.logger().info("Shader fp64 support: " + (ok ? "enabled" : "unavailable — using fp32 shaders"));
        return ok;
    }
}
