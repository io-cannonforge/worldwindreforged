/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file (Task 4.3 — Compute Shader Mesh Generation) — OpenGL 4.3 compute shader for
 * GPU-side frustum culling of terrain patches and indirect draw.
 *
 * Each tile dispatch runs one thread per coarse quad patch. The compute shader tests each
 * patch's four corner vertices against the six view-frustum planes (in tile-local ECEF
 * space). Visible patches are written atomically to a compact output SSBO that is consumed
 * directly by glDrawElementsIndirect — no CPU readback of culling results.
 *
 * Buffer layout per dispatch:
 *   Binding 0 — vertex VBO (reused as SSBO): packed float XYZ, 3 floats per vertex
 *   Binding 1 — source patch SSBO:  4 uint indices per patch (matches patchIndexLists)
 *   Binding 2 — output patch SSBO:  compacted indices for visible patches
 *   Binding 3 — draw command SSBO:  glDrawElementsIndirect command structure (5 × uint32)
 *
 * Requires OpenGL 4.3 (compute shaders + SSBOs + indirect draw). Falls back transparently
 * to the Task 4.2 tessellation path when GL 4.3 is unavailable.
 */
package gov.nasa.worldwind.render.shaders;

import java.nio.Buffer;
import java.nio.IntBuffer;
import java.util.HashMap;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3ES3;
import com.jogamp.opengl.GL4;

import gov.nasa.worldwind.geom.Frustum;
import gov.nasa.worldwind.geom.Plane;
import gov.nasa.worldwind.geom.Vec4;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.util.Logging;

/**
 * GPU-side frustum culling and indirect draw for terrain patches using OpenGL 4.3 compute shaders.
 * <p>
 * Vertex positions are read directly from the tile's existing vertex VBO (bound as SSBO binding 0)
 * without any extra copy. The source patch-index buffer ({@code patchIndexLists}) is uploaded once
 * per density to a dedicated SSBO (binding 1). The compute output (binding 2) is bound as
 * {@code GL_ELEMENT_ARRAY_BUFFER} and the atomic draw-command buffer (binding 3) as
 * {@code GL_DRAW_INDIRECT_BUFFER} before issuing {@code glDrawElementsIndirect}.
 */
public class ComputeMeshShader
{
    // @formatter:off
    private static final String COMPUTE_SOURCE = """
        #version 430
        layout(local_size_x = 64, local_size_y = 1, local_size_z = 1) in;

        // Tile vertex positions: packed XYZ floats relative to referenceCenter (3 floats/vertex).
        // Same memory as the vertex VBO; bound here as an SSBO so the compute shader can read it.
        layout(std430, binding = 0) readonly buffer Vertices  { float verts[];  };

        // Source quad-patch index buffer: 4 uint indices per patch (from patchIndexLists).
        layout(std430, binding = 1) readonly buffer SrcPatch  { uint  srcIdx[]; };

        // Output: compacted visible-patch indices (same 4-per-patch layout as SrcPatch).
        layout(std430, binding = 2) writeonly buffer DstPatch { uint  dstIdx[]; };

        // glDrawElementsIndirect command: { count, primCount, firstIndex, baseVertex, baseInstance }.
        // dc_count is incremented atomically; the other four fields are pre-set to 1/0/0/0.
        layout(std430, binding = 3) buffer DrawCmd {
            uint dc_count;
            uint dc_primCount;
            uint dc_firstIndex;
            uint dc_baseVertex;
            uint dc_baseInstance;
        };

        // Frustum planes in tile-local space (ECEF minus referenceCenter).
        // A point p is inside when dot(plane.xyz, p) + plane.w >= 0.
        uniform int  u_numPatches;
        uniform vec4 u_frustumPlanes[6];

        void main()
        {
            uint pid = gl_GlobalInvocationID.x;
            if (int(pid) >= u_numPatches)
                return;

            // Four vertex indices for this patch.
            uint b  = pid * 4u;
            uint i0 = srcIdx[b];
            uint i1 = srcIdx[b + 1u];
            uint i2 = srcIdx[b + 2u];
            uint i3 = srcIdx[b + 3u];

            // Vertex positions (3 packed floats per vertex in the VBO).
            vec3 c0 = vec3(verts[i0 * 3u], verts[i0 * 3u + 1u], verts[i0 * 3u + 2u]);
            vec3 c1 = vec3(verts[i1 * 3u], verts[i1 * 3u + 1u], verts[i1 * 3u + 2u]);
            vec3 c2 = vec3(verts[i2 * 3u], verts[i2 * 3u + 1u], verts[i2 * 3u + 2u]);
            vec3 c3 = vec3(verts[i3 * 3u], verts[i3 * 3u + 1u], verts[i3 * 3u + 2u]);

            // Cull: if all four corners are outside the same frustum half-space, drop the patch.
            bool visible = true;
            for (int i = 0; i < 6 && visible; i++)
            {
                vec4 p = u_frustumPlanes[i];
                if (dot(p.xyz, c0) + p.w < 0.0 &&
                    dot(p.xyz, c1) + p.w < 0.0 &&
                    dot(p.xyz, c2) + p.w < 0.0 &&
                    dot(p.xyz, c3) + p.w < 0.0)
                    visible = false;
            }

            if (visible)
            {
                uint base  = atomicAdd(dc_count, 4u);
                dstIdx[base]        = i0;
                dstIdx[base + 1u]   = i1;
                dstIdx[base + 2u]   = i2;
                dstIdx[base + 3u]   = i3;
            }
        }
        """;
    // @formatter:on

    /** glDrawElementsIndirect command size: { count, primCount, firstIndex, baseVertex, baseInstance }. */
    private static final int DRAW_CMD_BYTES = 5 * Integer.BYTES;

    /** Compute workgroup size (must match local_size_x in GLSL). */
    private static final int LOCAL_SIZE = 64;

    /** Source patch-index SSBOs — uploaded once per density from the CPU patchIndexLists. */
    private final HashMap<Integer, Integer> srcPatchSsbos    = new HashMap<>();

    /** Destination (culled) patch-index SSBOs — output of the compute dispatch per density. */
    private final HashMap<Integer, Integer> dstIndexSsbos    = new HashMap<>();

    /** Allocated size (number of indices) of each per-density source patch SSBO. */
    private final HashMap<Integer, Integer> srcPatchCapacity = new HashMap<>();

    /** Allocated size (number of indices) of each per-density destination patch SSBO. */
    private final HashMap<Integer, Integer> dstPatchCapacity = new HashMap<>();

    /** Shared draw-command SSBO; the count field is reset to 0 before each dispatch. */
    private int drawCmdSsbo;

    private int     computeProgram;
    private boolean initialized;
    private boolean failed;

    // Cached uniform locations — queried once after program link.
    private int uNumPatchesLoc    = -1;
    private int uFrustumPlanesLoc = -1;

    // Reusable per-dispatch buffers — allocated once in init() to avoid per-tile GC pressure.
    private IntBuffer drawCmdResetBuffer;          // single int = 0, for resetting dc_count
    private final float[] frustumPlanesBuffer = new float[24]; // 6 planes × vec4
    private final int[]   savedProgramArray   = new int[1];    // glGetIntegerv result
    private final int[]   bufferIdArray       = new int[1];    // glGenBuffers result

    // -----------------------------------------------------------------------
    // Init / query
    // -----------------------------------------------------------------------

    /**
     * Compiles and links the compute shader program. Returns {@code true} on success.
     * On failure the shader is marked permanently failed and {@code false} is returned.
     * <p>
     * Only call this method when {@code GLRuntimeCapabilities.isUseComputeMesh()} is true;
     * that flag already confirms GL 4.3 availability via the cached GL version.
     */
    public boolean init(GL gl)
    {
        if (this.initialized) return true;
        if (this.failed)      return false;

        // Defensive guard: GLRuntimeCapabilities.isUseComputeMesh() already verified GL 4.3,
        // but guard here in case init() is ever called outside the normal flow.
        if (!gl.isGL4())
        {
            Logging.logger().info("ComputeMeshShader: GL4 context not available; compute mesh skipped.");
            this.failed = true;
            return false;
        }

        GL4 gl4 = gl.getGL4();

        // Compile compute shader.
        int shader = gl4.glCreateShader(GL3ES3.GL_COMPUTE_SHADER);
        gl4.glShaderSource(shader, 1, new String[]{COMPUTE_SOURCE}, null);
        gl4.glCompileShader(shader);

        int[] status = new int[1];
        gl4.glGetShaderiv(shader, GL2ES2.GL_COMPILE_STATUS, status, 0);
        if (status[0] == GL.GL_FALSE)
        {
            int[] logLen = new int[1];
            gl4.glGetShaderiv(shader, GL2ES2.GL_INFO_LOG_LENGTH, logLen, 0);
            byte[] log = new byte[Math.max(logLen[0], 1)];
            gl4.glGetShaderInfoLog(shader, logLen[0], null, 0, log, 0);
            Logging.logger().severe("ComputeMeshShader compile error: " + new String(log));
            gl4.glDeleteShader(shader);
            this.failed = true;
            return false;
        }

        // Link program.
        this.computeProgram = gl4.glCreateProgram();
        gl4.glAttachShader(this.computeProgram, shader);
        gl4.glLinkProgram(this.computeProgram);
        gl4.glDeleteShader(shader);

        gl4.glGetProgramiv(this.computeProgram, GL2ES2.GL_LINK_STATUS, status, 0);
        if (status[0] == GL.GL_FALSE)
        {
            int[] logLen = new int[1];
            gl4.glGetProgramiv(this.computeProgram, GL2ES2.GL_INFO_LOG_LENGTH, logLen, 0);
            byte[] log = new byte[Math.max(logLen[0], 1)];
            gl4.glGetProgramInfoLog(this.computeProgram, logLen[0], null, 0, log, 0);
            Logging.logger().severe("ComputeMeshShader link error: " + new String(log));
            gl4.glDeleteProgram(this.computeProgram);
            this.computeProgram = 0;
            this.failed = true;
            return false;
        }

        // Cache uniform locations — avoids per-dispatch driver lookups.
        this.uNumPatchesLoc    = gl4.glGetUniformLocation(this.computeProgram, "u_numPatches");
        this.uFrustumPlanesLoc = gl4.glGetUniformLocation(this.computeProgram, "u_frustumPlanes");

        // Allocate the shared draw-command SSBO with default values { 0, 1, 0, 0, 0 }.
        gl4.glGenBuffers(1, this.bufferIdArray, 0);
        this.drawCmdSsbo = this.bufferIdArray[0];
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.drawCmdSsbo);
        IntBuffer initCmd = Buffers.newDirectIntBuffer(new int[]{0, 1, 0, 0, 0});
        gl4.glBufferData(GL3ES3.GL_SHADER_STORAGE_BUFFER, DRAW_CMD_BYTES, initCmd, GL.GL_DYNAMIC_DRAW);
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0);

        // Allocate reusable reset buffer (single int = 0) used to clear dc_count each dispatch.
        this.drawCmdResetBuffer = Buffers.newDirectIntBuffer(new int[]{0});

        this.initialized = true;
        return true;
    }

    /** Returns {@code true} if the compute shader compiled and linked successfully. */
    public boolean isValid()
    {
        return this.initialized && !this.failed;
    }

    // -----------------------------------------------------------------------
    // Dispatch + indirect draw
    // -----------------------------------------------------------------------

    /**
     * Runs the compute-mesh cull pass and issues an indirect draw for the visible patches.
     * <p>
     * The tessellation shader <em>must</em> be active (program bound, uniforms set) before
     * this method is called. The method temporarily switches to the compute program for the
     * dispatch, then restores the calling program before the indirect draw fires.
     *
     * @param gl4         GL4 context
     * @param dc          draw context (for frustum planes)
     * @param vertexVboId GPU ID of the tile's vertex VBO (3 floats per vertex, packed XYZ)
     * @param density     tile density, used to key per-density SSBOs
     * @param srcPatches  CPU-side quad-patch index buffer for this density (uploaded once)
     * @param refCenter   tile reference centre in ECEF (used to adjust frustum planes)
     */
    public void dispatchAndDraw(GL4 gl4, DrawContext dc, int vertexVboId,
                                int density, IntBuffer srcPatches, Vec4 refCenter)
    {
        int numIndices = srcPatches.limit();
        int numPatches = numIndices / 4;

        // ---- 1. Ensure per-density SSBOs exist and are populated ----
        // Both helpers return the SSBO ID, avoiding a redundant HashMap lookup after the call.
        int srcSsboId = ensureSrcSsbo(gl4, density, srcPatches, numIndices);
        int dstSsboId = ensureDstSsbo(gl4, density, numIndices);

        // ---- 2. Reset draw-command count to 0 (primCount, firstIndex etc. stay 1/0/0/0) ----
        this.drawCmdResetBuffer.rewind();
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.drawCmdSsbo);
        gl4.glBufferSubData(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0, Integer.BYTES, this.drawCmdResetBuffer);
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0);

        // ---- 3. Save current program (tessellation shader) so we can restore it ----
        gl4.glGetIntegerv(GL2ES2.GL_CURRENT_PROGRAM, this.savedProgramArray, 0);

        // ---- 4. Bind SSBOs ----
        gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0, vertexVboId);  // vertex positions
        gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 1, srcSsboId);    // source patch indices
        gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 2, dstSsboId);    // culled output indices
        gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 3, this.drawCmdSsbo); // indirect command

        // ---- 5. Set uniforms and dispatch ----
        gl4.glUseProgram(this.computeProgram);
        gl4.glUniform1i(this.uNumPatchesLoc, numPatches);
        buildFrustumPlanes(dc, refCenter, this.frustumPlanesBuffer);
        gl4.glUniform4fv(this.uFrustumPlanesLoc, 6, this.frustumPlanesBuffer, 0);
        gl4.glDispatchCompute((numPatches + LOCAL_SIZE - 1) / LOCAL_SIZE, 1, 1);

        // ---- 6. Memory barrier: SSBO writes must be visible to element + indirect reads ----
        gl4.glMemoryBarrier(GL2ES3.GL_COMMAND_BARRIER_BIT | GL3ES3.GL_SHADER_STORAGE_BARRIER_BIT);

        // ---- 7. Restore tessellation shader ----
        gl4.glUseProgram(this.savedProgramArray[0]);

        // ---- 8. Bind output SSBO as element array and issue indirect draw ----
        gl4.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, dstSsboId);
        gl4.glBindBuffer(GL3ES3.GL_DRAW_INDIRECT_BUFFER, this.drawCmdSsbo);
        gl4.glDrawElementsIndirect(GL3ES3.GL_PATCHES, GL.GL_UNSIGNED_INT, 0L);
        gl4.glBindBuffer(GL3ES3.GL_DRAW_INDIRECT_BUFFER, 0);
    }

    // -----------------------------------------------------------------------
    // SSBO helpers
    // -----------------------------------------------------------------------

    /** Ensures the source patch-index SSBO for {@code density} exists and is populated. Returns its ID. */
    private int ensureSrcSsbo(GL4 gl4, int density, IntBuffer srcPatches, int numIndices)
    {
        Integer existing = srcPatchSsbos.get(density);
        Integer capacity = srcPatchCapacity.get(density);

        if (existing == null)
        {
            gl4.glGenBuffers(1, this.bufferIdArray, 0);
            existing = this.bufferIdArray[0];
            srcPatchSsbos.put(density, existing);
            capacity = 0;
        }

        if (capacity < numIndices)
        {
            gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, existing);
            gl4.glBufferData(GL3ES3.GL_SHADER_STORAGE_BUFFER, (long) numIndices * Integer.BYTES,
                srcPatches.rewind(), GL.GL_STATIC_DRAW);
            gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0);
            srcPatchCapacity.put(density, numIndices);
        }

        return existing;
    }

    /** Ensures the destination patch-index SSBO for {@code density} exists and is sized. Returns its ID. */
    private int ensureDstSsbo(GL4 gl4, int density, int numIndices)
    {
        Integer existing = dstIndexSsbos.get(density);
        Integer capacity = dstPatchCapacity.get(density);

        if (existing == null)
        {
            gl4.glGenBuffers(1, this.bufferIdArray, 0);
            existing = this.bufferIdArray[0];
            dstIndexSsbos.put(density, existing);
            capacity = 0;
        }

        if (capacity < numIndices)
        {
            gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, existing);
            gl4.glBufferData(GL3ES3.GL_SHADER_STORAGE_BUFFER, (long) numIndices * Integer.BYTES,
                (Buffer) null, GL.GL_DYNAMIC_DRAW);
            gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0);
            dstPatchCapacity.put(density, numIndices);
        }

        return existing;
    }

    // -----------------------------------------------------------------------
    // Frustum utilities
    // -----------------------------------------------------------------------

    /**
     * Fills {@code out} (length 24, 6 × vec4) with frustum planes in tile-local space.
     * Plane order: left, right, bottom, top, near, far (same as {@link Frustum#getAllPlanes()}).
     * A point {@code p} in tile-local coordinates is inside when
     * {@code dot(plane.xyz, p) + plane.w >= 0}.
     * <p>
     * Planes are adjusted from ECEF to tile-local by adding {@code dot(n, refCenter)} to the
     * distance term: {@code dot(n, ECEF) + d = dot(n, local + ref) + d = dot(n, local) + (d + dot(n, ref))}.
     */
    private static void buildFrustumPlanes(DrawContext dc, Vec4 refCenter, float[] out)
    {
        Plane[] planes = dc.getView().getFrustumInModelCoordinates().getAllPlanes();
        for (int i = 0; i < 6; i++)
        {
            Vec4 n = planes[i].getVector();
            double adjustedW = n.w + n.x * refCenter.x + n.y * refCenter.y + n.z * refCenter.z;
            out[i * 4]     = (float) n.x;
            out[i * 4 + 1] = (float) n.y;
            out[i * 4 + 2] = (float) n.z;
            out[i * 4 + 3] = (float) adjustedW;
        }
    }

    // -----------------------------------------------------------------------
    // Dispose
    // -----------------------------------------------------------------------

    /** Releases all GPU resources created by this shader. */
    public void dispose(GL gl)
    {
        if (!gl.isGL4()) return;
        GL4 gl4 = gl.getGL4();

        if (this.computeProgram != 0)
        {
            gl4.glDeleteProgram(this.computeProgram);
            this.computeProgram = 0;
        }
        if (this.drawCmdSsbo != 0)
        {
            gl4.glDeleteBuffers(1, new int[]{this.drawCmdSsbo}, 0);
            this.drawCmdSsbo = 0;
        }
        for (int id : this.srcPatchSsbos.values())
            gl4.glDeleteBuffers(1, new int[]{id}, 0);
        for (int id : this.dstIndexSsbos.values())
            gl4.glDeleteBuffers(1, new int[]{id}, 0);

        this.srcPatchSsbos.clear();
        this.dstIndexSsbos.clear();
        this.srcPatchCapacity.clear();
        this.dstPatchCapacity.clear();
        this.uNumPatchesLoc    = -1;
        this.uFrustumPlanesLoc = -1;
        this.initialized = false;
        this.failed      = false;
    }
}
