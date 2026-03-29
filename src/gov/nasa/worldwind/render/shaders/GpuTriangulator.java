/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — GPU-accelerated polygon triangulation using OpenGL 4.3 compute shaders
 * with the ear-clipping algorithm. Dispatches one workgroup per polygon for polygon-level
 * parallelism. Includes bridge edge insertion (Eberly/O'Rourke) for merging holes into
 * outer rings, CPU ear-clipping fallback, and outline index generation. Supersedes the
 * legacy GLUtessellator callback-based API in ShapefilePolygons (GLU retained as fallback).
 */
package gov.nasa.worldwind.render.shaders;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL3ES3;
import com.jogamp.opengl.GL4;

import gov.nasa.worldwind.util.GLContextLocal;
import gov.nasa.worldwind.util.Logging;

/**
 * GPU-accelerated polygon triangulation using OpenGL 4.3 compute shaders with ear-clipping.
 * <p>
 * Dispatches one workgroup per polygon for polygon-level parallelism: a tile with 200 polygons
 * runs 200 workgroups simultaneously. Handles polygons with holes via bridge edge insertion
 * (CPU preprocessing that merges holes into the outer ring to form a simple polygon).
 * <p>
 * Falls back to CPU ear-clipping when GL 4.3 compute shaders are unavailable.
 */
public class GpuTriangulator
{
    public static final int MAX_POLYGON_VERTICES = 4096;

    private int program;
    private int maxVertsLoc;
    private boolean initialized;
    private boolean available;

    // SSBOs
    private int vertexSSBO;
    private int ringSSBO;
    private int descSSBO;
    private int triSSBO;
    private int scratchSSBO;
    private int vertexCapacity;  // in floats
    private int ringCapacity;    // in ints
    private int descCapacity;    // in ints (4 per polygon)
    private int triCapacity;     // in ints
    private int scratchCapacity; // in ints

    // seaglassfoundry.com: per-GLContext instances — each GL context gets its own compiled program + SSBOs.
    // GPU capability flags remain static since the hardware is shared across contexts.
    private static final GLContextLocal<GpuTriangulator> instances = new GLContextLocal<>();
    private static volatile boolean gpuAvailable = false;
    private static volatile boolean gpuChecked = false;

    // ----- Compute shader source -------------------------------------------------------

    private static final String COMPUTE_SOURCE =
        "#version 430\n" +
        "\n" +
        "layout(local_size_x = 1) in;\n" +
        "\n" +
        "layout(std430, binding = 0) readonly buffer Vertices   { vec2 verts[];     };\n" +
        "layout(std430, binding = 1) readonly buffer RingIdx    { int  ringIdx[];   };\n" +
        "layout(std430, binding = 2) readonly buffer PolyDescs  { ivec4 descs[];    };\n" +
        "layout(std430, binding = 3) writeonly buffer TriOutput { int  triOut[];    };\n" +
        "layout(std430, binding = 4) buffer Scratch             { int  scratch[];   };\n" +
        "\n" +
        "uniform int u_maxVerts;\n" +
        "\n" +
        "float cross2D(vec2 o, vec2 a, vec2 b) {\n" +
        "    return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);\n" +
        "}\n" +
        "\n" +
        "bool pointInTri(vec2 p, vec2 a, vec2 b, vec2 c) {\n" +
        "    float d1 = cross2D(a, b, p);\n" +
        "    float d2 = cross2D(b, c, p);\n" +
        "    float d3 = cross2D(c, a, p);\n" +
        "    bool neg = (d1 < 0.0) || (d2 < 0.0) || (d3 < 0.0);\n" +
        "    bool pos = (d1 > 0.0) || (d2 > 0.0) || (d3 > 0.0);\n" +
        "    return !(neg && pos);\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    uint pid = gl_WorkGroupID.x;\n" +
        "    int rStart = descs[pid].x;\n" +   // offset into ringIdx
        "    int n      = descs[pid].y;\n" +   // vertex count in merged ring
        "    int tStart = descs[pid].z;\n" +   // offset into triOut (in ints)
        "\n" +
        "    if (n < 3) return;\n" +
        "\n" +
        "    int sBase = int(pid) * u_maxVerts;\n" +
        "    int pOff  = sBase;\n" +                       // prev[i]
        "    int nOff  = sBase + u_maxVerts;\n" +           // next[i]
        "    int fOff  = sBase + 2 * u_maxVerts;\n" +       // flags[i]
        "\n" +
        "    // Signed-area winding detection\n" +
        "    float area = 0.0;\n" +
        "    for (int i = 0; i < n; i++) {\n" +
        "        int j = (i + 1) % n;\n" +
        "        vec2 vi = verts[ringIdx[rStart + i]];\n" +
        "        vec2 vj = verts[ringIdx[rStart + j]];\n" +
        "        area += vi.x * vj.y - vj.x * vi.y;\n" +
        "    }\n" +
        "    float w = (area >= 0.0) ? 1.0 : -1.0;\n" +
        "\n" +
        "    // Initialize linked list and classify convex/reflex\n" +
        "    for (int i = 0; i < n; i++) {\n" +
        "        scratch[pOff + i] = (i - 1 + n) % n;\n" +
        "        scratch[nOff + i] = (i + 1) % n;\n" +
        "        int pi = (i - 1 + n) % n;\n" +
        "        int ni = (i + 1) % n;\n" +
        "        float c = cross2D(\n" +
        "            verts[ringIdx[rStart + pi]],\n" +
        "            verts[ringIdx[rStart + i]],\n" +
        "            verts[ringIdx[rStart + ni]]);\n" +
        "        scratch[fOff + i] = (c * w >= 0.0) ? 1 : 0;\n" +
        "    }\n" +
        "\n" +
        "    int remaining = n;\n" +
        "    int triCount  = 0;\n" +
        "    int start     = 0;\n" +
        "    int safety    = n * n;\n" +  // prevent infinite loops on degenerate input
        "\n" +
        "    while (remaining > 3 && safety-- > 0) {\n" +
        "        int cur = start;\n" +
        "        int ear = -1;\n" +
        "\n" +
        "        // Scan linked list for an ear\n" +
        "        for (int iter = 0; iter < remaining; iter++) {\n" +
        "            if (scratch[fOff + cur] >= 1) {\n" +
        "                int pi = scratch[pOff + cur];\n" +
        "                int ni = scratch[nOff + cur];\n" +
        "                vec2 a = verts[ringIdx[rStart + pi]];\n" +
        "                vec2 b = verts[ringIdx[rStart + cur]];\n" +
        "                vec2 c = verts[ringIdx[rStart + ni]];\n" +
        "\n" +
        "                bool isEar = true;\n" +
        "                int chk = scratch[nOff + ni];\n" +
        "                while (chk != pi) {\n" +
        "                    if (scratch[fOff + chk] == 0) {\n" +
        "                        if (pointInTri(verts[ringIdx[rStart + chk]], a, b, c)) {\n" +
        "                            isEar = false;\n" +
        "                            break;\n" +
        "                        }\n" +
        "                    }\n" +
        "                    chk = scratch[nOff + chk];\n" +
        "                }\n" +
        "\n" +
        "                if (isEar) { ear = cur; break; }\n" +
        "            }\n" +
        "            cur = scratch[nOff + cur];\n" +
        "        }\n" +
        "\n" +
        "        if (ear == -1) break;\n" +
        "\n" +
        "        int pi = scratch[pOff + ear];\n" +
        "        int ni = scratch[nOff + ear];\n" +
        "        triOut[tStart + triCount*3 + 0] = ringIdx[rStart + pi];\n" +
        "        triOut[tStart + triCount*3 + 1] = ringIdx[rStart + ear];\n" +
        "        triOut[tStart + triCount*3 + 2] = ringIdx[rStart + ni];\n" +
        "        triCount++;\n" +
        "\n" +
        "        // Remove ear from linked list\n" +
        "        scratch[nOff + pi] = ni;\n" +
        "        scratch[pOff + ni] = pi;\n" +
        "        scratch[fOff + ear] = -1;\n" +
        "        remaining--;\n" +
        "        if (ear == start) start = ni;\n" +
        "\n" +
        "        // Reclassify neighbors\n" +
        "        {\n" +
        "            int pp = scratch[pOff + pi];\n" +
        "            float c = cross2D(\n" +
        "                verts[ringIdx[rStart + pp]],\n" +
        "                verts[ringIdx[rStart + pi]],\n" +
        "                verts[ringIdx[rStart + ni]]);\n" +
        "            scratch[fOff + pi] = (c * w >= 0.0) ? 1 : 0;\n" +
        "        }\n" +
        "        {\n" +
        "            int nn = scratch[nOff + ni];\n" +
        "            float c = cross2D(\n" +
        "                verts[ringIdx[rStart + pi]],\n" +
        "                verts[ringIdx[rStart + ni]],\n" +
        "                verts[ringIdx[rStart + nn]]);\n" +
        "            scratch[fOff + ni] = (c * w >= 0.0) ? 1 : 0;\n" +
        "        }\n" +
        "    }\n" +
        "\n" +
        "    // Output final triangle\n" +
        "    if (remaining == 3) {\n" +
        "        int v0 = start;\n" +
        "        int v1 = scratch[nOff + v0];\n" +
        "        int v2 = scratch[nOff + v1];\n" +
        "        triOut[tStart + triCount*3 + 0] = ringIdx[rStart + v0];\n" +
        "        triOut[tStart + triCount*3 + 1] = ringIdx[rStart + v1];\n" +
        "        triOut[tStart + triCount*3 + 2] = ringIdx[rStart + v2];\n" +
        "    }\n" +
        "}\n";

    // ----- Singleton & lifecycle --------------------------------------------------------

    /**
     * Returns the per-GLContext instance, creating one if needed.
     * Must be called from a thread with an active GL context.
     */
    public static GpuTriangulator getInstance()
    {
        return instances.computeIfAbsent(GpuTriangulator::new);
    }

    /** Returns true once the GPU path has been probed and found available. */
    public static boolean isGpuAvailable()
    {
        return gpuChecked && gpuAvailable;
    }

    /**
     * Returns true when it is still worth attempting the GPU path — either the GPU has not yet
     * been probed (first frame), or it has been probed and confirmed available.
     * Returns false only when the GPU has been probed and confirmed unavailable.
     */
    public static boolean isGpuViable()
    {
        return !gpuChecked || gpuAvailable;
    }

    public boolean isAvailable()
    {
        return this.available;
    }

    /** Probe GPU capabilities and compile the compute shader. Call once from the GL thread. */
    public void initialize(GL gl)
    {
        if (this.initialized)
            return;
        this.initialized = true;

        if (!gl.isGL4())
        {
            gpuChecked = true;
            gpuAvailable = false;
            return;
        }

        GL4 gl4 = gl.getGL4();

        int[] val = new int[1];
        gl4.glGetIntegerv(GL3ES3.GL_MAX_COMPUTE_WORK_GROUP_COUNT, val, 0);
        if (val[0] == 0)
        {
            gpuChecked = true;
            gpuAvailable = false;
            return;
        }

        this.program = compileComputeProgram(gl4, COMPUTE_SOURCE, "triangulator");
        if (this.program == 0)
        {
            gpuChecked = true;
            gpuAvailable = false;
            return;
        }

        this.maxVertsLoc = gl4.glGetUniformLocation(this.program, "u_maxVerts");
        this.available = true;
        gpuChecked = true;
        gpuAvailable = true;
    }

    public void dispose(GL gl)
    {
        if (gl.isGL4())
        {
            GL4 gl4 = gl.getGL4();
            if (this.program != 0) { gl4.glDeleteProgram(this.program); this.program = 0; }

            int[] bufs = {vertexSSBO, ringSSBO, descSSBO, triSSBO, scratchSSBO};
            for (int buf : bufs)
                if (buf != 0) gl4.glDeleteBuffers(1, new int[]{buf}, 0);
            vertexSSBO = ringSSBO = descSSBO = triSSBO = scratchSSBO = 0;
        }
        this.available = false;
    }

    // ----- GPU batch triangulation ------------------------------------------------------

    /**
     * Triangulates a batch of simple polygons on the GPU via compute shader.
     * Each polygon must be a simple polygon — holes must already be merged via
     * {@link #bridgeHoles}.
     *
     * @param gl4          GL4 context
     * @param vertices     all vertex positions as x,y pairs (shared buffer for all polygons)
     * @param vertexCount  total number of vertices (= vertices.length / 2)
     * @param ringIndices  merged ring indices for all polygons, packed sequentially
     * @param ringOffsets  per-polygon start offset into ringIndices
     * @param ringCounts   per-polygon vertex count in merged ring
     * @param numPolygons  number of polygons
     * @return per-polygon triangle index arrays (each array = groups of 3 global vertex indices),
     *         or null on failure
     */
    public int[][] triangulateBatch(GL4 gl4, float[] vertices, int vertexCount,
                                     int[] ringIndices, int[] ringOffsets, int[] ringCounts,
                                     int numPolygons)
    {
        if (!this.available || numPolygons == 0)
            return null;

        // Compute output sizes
        int totalTris = 0;
        int[] triOffsets = new int[numPolygons];
        for (int i = 0; i < numPolygons; i++)
        {
            triOffsets[i] = totalTris * 3;
            totalTris += Math.max(0, ringCounts[i] - 2);
        }

        int totalRingInts = 0;
        for (int c : ringCounts)
            totalRingInts += c; // ringOffsets already track this but let's be safe
        if (ringIndices.length > 0)
            totalRingInts = ringIndices.length;

        // Ensure SSBOs
        ensureSSBO(gl4, 0, vertexCount * 2 * Float.BYTES);
        ensureSSBO(gl4, 1, totalRingInts * Integer.BYTES);
        ensureSSBO(gl4, 2, numPolygons * 4 * Integer.BYTES);
        ensureSSBO(gl4, 3, Math.max(1, totalTris * 3) * Integer.BYTES);
        ensureSSBO(gl4, 4, (long) numPolygons * MAX_POLYGON_VERTICES * 3 * Integer.BYTES);

        // Upload vertices
        FloatBuffer vBuf = Buffers.newDirectFloatBuffer(vertexCount * 2);
        vBuf.put(vertices, 0, vertexCount * 2).rewind();
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.vertexSSBO);
        gl4.glBufferSubData(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0, (long) vertexCount * 2 * Float.BYTES, vBuf);

        // Upload ring indices
        IntBuffer rBuf = Buffers.newDirectIntBuffer(totalRingInts);
        rBuf.put(ringIndices, 0, totalRingInts).rewind();
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.ringSSBO);
        gl4.glBufferSubData(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0, (long) totalRingInts * Integer.BYTES, rBuf);

        // Upload descriptors
        IntBuffer dBuf = Buffers.newDirectIntBuffer(numPolygons * 4);
        for (int i = 0; i < numPolygons; i++)
        {
            dBuf.put(ringOffsets[i]);   // offset into ringIdx
            dBuf.put(ringCounts[i]);    // vertex count in merged ring
            dBuf.put(triOffsets[i]);    // offset into triOut (in ints)
            dBuf.put(0);                // padding
        }
        dBuf.rewind();
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.descSSBO);
        gl4.glBufferSubData(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0, (long) numPolygons * 4 * Integer.BYTES, dBuf);

        // Bind SSBOs
        gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0, this.vertexSSBO);
        gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 1, this.ringSSBO);
        gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 2, this.descSSBO);
        gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 3, this.triSSBO);
        gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 4, this.scratchSSBO);

        // Dispatch
        gl4.glUseProgram(this.program);
        gl4.glUniform1i(this.maxVertsLoc, MAX_POLYGON_VERTICES);
        gl4.glDispatchCompute(numPolygons, 1, 1);
        gl4.glMemoryBarrier(GL3ES3.GL_SHADER_STORAGE_BARRIER_BIT);

        // Read back triangle indices
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.triSSBO);
        IntBuffer result = Buffers.newDirectIntBuffer(totalTris * 3);
        gl4.glGetBufferSubData(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0, (long) totalTris * 3 * Integer.BYTES, result);

        // Distribute to per-polygon arrays
        int[][] output = new int[numPolygons][];
        for (int i = 0; i < numPolygons; i++)
        {
            int count = Math.max(0, ringCounts[i] - 2) * 3;
            output[i] = new int[count];
            result.position(triOffsets[i]);
            result.get(output[i]);
        }

        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0);
        gl4.glUseProgram(0);

        return output;
    }

    // ----- SSBO management --------------------------------------------------------------

    private void ensureSSBO(GL4 gl4, int binding, long requiredBytes)
    {
        if (requiredBytes <= 0) requiredBytes = 4;

        switch (binding)
        {
            case 0:
                if (vertexSSBO != 0 && vertexCapacity >= requiredBytes) return;
                if (vertexSSBO != 0) gl4.glDeleteBuffers(1, new int[]{vertexSSBO}, 0);
                vertexSSBO = createSSBO(gl4, requiredBytes);
                vertexCapacity = (int) requiredBytes;
                break;
            case 1:
                if (ringSSBO != 0 && ringCapacity >= requiredBytes) return;
                if (ringSSBO != 0) gl4.glDeleteBuffers(1, new int[]{ringSSBO}, 0);
                ringSSBO = createSSBO(gl4, requiredBytes);
                ringCapacity = (int) requiredBytes;
                break;
            case 2:
                if (descSSBO != 0 && descCapacity >= requiredBytes) return;
                if (descSSBO != 0) gl4.glDeleteBuffers(1, new int[]{descSSBO}, 0);
                descSSBO = createSSBO(gl4, requiredBytes);
                descCapacity = (int) requiredBytes;
                break;
            case 3:
                if (triSSBO != 0 && triCapacity >= requiredBytes) return;
                if (triSSBO != 0) gl4.glDeleteBuffers(1, new int[]{triSSBO}, 0);
                triSSBO = createSSBO(gl4, requiredBytes);
                triCapacity = (int) requiredBytes;
                break;
            case 4:
                if (scratchSSBO != 0 && scratchCapacity >= requiredBytes) return;
                if (scratchSSBO != 0) gl4.glDeleteBuffers(1, new int[]{scratchSSBO}, 0);
                scratchSSBO = createSSBO(gl4, requiredBytes);
                scratchCapacity = (int) requiredBytes;
                break;
        }
    }

    private static int createSSBO(GL4 gl4, long bytes)
    {
        int[] buf = new int[1];
        gl4.glGenBuffers(1, buf, 0);
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, buf[0]);
        gl4.glBufferData(GL3ES3.GL_SHADER_STORAGE_BUFFER, bytes, null, GL.GL_DYNAMIC_DRAW);
        return buf[0];
    }

    // ----- Shader compilation -----------------------------------------------------------

    private static int compileComputeProgram(GL4 gl4, String source, String label)
    {
        int shader = gl4.glCreateShader(GL3ES3.GL_COMPUTE_SHADER);
        gl4.glShaderSource(shader, 1, new String[]{source}, null);
        gl4.glCompileShader(shader);

        int[] status = new int[1];
        gl4.glGetShaderiv(shader, GL2ES2.GL_COMPILE_STATUS, status, 0);
        if (status[0] == GL.GL_FALSE)
        {
            int[] logLen = new int[1];
            gl4.glGetShaderiv(shader, GL2ES2.GL_INFO_LOG_LENGTH, logLen, 0);
            byte[] log = new byte[logLen[0]];
            gl4.glGetShaderInfoLog(shader, logLen[0], null, 0, log, 0);
            Logging.logger().severe("GpuTriangulator " + label + " compile error: " + new String(log));
            gl4.glDeleteShader(shader);
            return 0;
        }

        int prog = gl4.glCreateProgram();
        gl4.glAttachShader(prog, shader);
        gl4.glLinkProgram(prog);
        gl4.glDeleteShader(shader);

        gl4.glGetProgramiv(prog, GL2ES2.GL_LINK_STATUS, status, 0);
        if (status[0] == GL.GL_FALSE)
        {
            int[] logLen = new int[1];
            gl4.glGetProgramiv(prog, GL2ES2.GL_INFO_LOG_LENGTH, logLen, 0);
            byte[] log = new byte[logLen[0]];
            gl4.glGetProgramInfoLog(prog, logLen[0], null, 0, log, 0);
            Logging.logger().severe("GpuTriangulator " + label + " link error: " + new String(log));
            gl4.glDeleteProgram(prog);
            return 0;
        }

        return prog;
    }

    // ===================================================================================
    // Bridge edge insertion — merges holes into the outer ring to form a simple polygon
    // ===================================================================================

    /**
     * Merges hole contours into the outer ring using bridge edges, producing a simple polygon.
     * <p>
     * Algorithm (Eberly / O'Rourke):
     * <ol>
     *   <li>Sort holes by their rightmost vertex x-coordinate (descending).</li>
     *   <li>For each hole, find its rightmost vertex P.</li>
     *   <li>Cast a horizontal ray from P to the right and find the closest intersecting edge
     *       of the (already-merged) outer ring.</li>
     *   <li>Determine the "mutually visible" vertex M on that edge.</li>
     *   <li>Splice the hole into the ring: ..., M, P, hole..., P, M, ...</li>
     * </ol>
     *
     * @param vertices    flat x,y coordinate array for all contours
     * @param outerStart  start index (in vertex units) of outer ring in vertices
     * @param outerCount  number of vertices in outer ring
     * @param holeStarts  start index of each hole contour
     * @param holeCounts  vertex count of each hole contour
     * @return index list describing the merged simple polygon, referencing into the original
     *         vertex array. Each entry is a vertex index (in vertex units).
     */
    public static int[] bridgeHoles(float[] vertices, int outerStart, int outerCount,
                                     int[] holeStarts, int[] holeCounts)
    {
        if (holeStarts == null || holeStarts.length == 0)
        {
            int[] result = new int[outerCount];
            for (int i = 0; i < outerCount; i++)
                result[i] = outerStart + i;
            return result;
        }

        int numHoles = holeStarts.length;

        // Sort holes by rightmost x (descending) — processing rightmost first ensures
        // that ray casts don't accidentally cross a not-yet-merged hole.
        Integer[] order = new Integer[numHoles];
        for (int i = 0; i < numHoles; i++) order[i] = i;
        Arrays.sort(order, (a, b) ->
        {
            float maxA = maxX(vertices, holeStarts[a], holeCounts[a]);
            float maxB = maxX(vertices, holeStarts[b], holeCounts[b]);
            return Float.compare(maxB, maxA);
        });

        // Start with outer ring as a mutable list
        ArrayList<Integer> ring = new ArrayList<>(outerCount + numHoles * 4);
        for (int i = 0; i < outerCount; i++)
            ring.add(outerStart + i);

        // Merge each hole
        for (int hi = 0; hi < numHoles; hi++)
        {
            int hIdx = order[hi];
            int hStart = holeStarts[hIdx];
            int hCount = holeCounts[hIdx];
            if (hCount < 3) continue;

            // Find rightmost vertex P of this hole
            int rightmost = 0;
            float maxXval = vertices[hStart * 2];
            for (int j = 1; j < hCount; j++)
            {
                float x = vertices[(hStart + j) * 2];
                if (x > maxXval || (x == maxXval && vertices[(hStart + j) * 2 + 1] > vertices[(hStart + rightmost) * 2 + 1]))
                {
                    maxXval = x;
                    rightmost = j;
                }
            }

            float px = vertices[(hStart + rightmost) * 2];
            float py = vertices[(hStart + rightmost) * 2 + 1];

            // Cast horizontal ray from P rightward, find closest intersecting edge of ring
            float closestDist = Float.MAX_VALUE;
            int closestEdge = -1;
            float closestIx = 0;

            int ringSize = ring.size();
            for (int j = 0; j < ringSize; j++)
            {
                int aIdx = ring.get(j);
                int bIdx = ring.get((j + 1) % ringSize);
                float ax = vertices[aIdx * 2], ay = vertices[aIdx * 2 + 1];
                float bx = vertices[bIdx * 2], by = vertices[bIdx * 2 + 1];

                // Does horizontal ray from (px,py) going right intersect edge (a,b)?
                if ((ay > py) != (by > py))
                {
                    float t = (py - ay) / (by - ay);
                    float ix = ax + t * (bx - ax);
                    if (ix >= px)
                    {
                        float dist = ix - px;
                        if (dist < closestDist)
                        {
                            closestDist = dist;
                            closestEdge = j;
                            closestIx = ix;
                        }
                    }
                }
            }

            if (closestEdge == -1) continue;

            // Find mutually visible vertex M
            // Candidate: the endpoint of the intersected edge with the larger x
            int edgeA = ring.get(closestEdge);
            int edgeB = ring.get((closestEdge + 1) % ring.size());
            float ax = vertices[edgeA * 2];
            float bx = vertices[edgeB * 2];

            int candidateRingIdx;
            if (ax >= bx)
                candidateRingIdx = closestEdge;
            else
                candidateRingIdx = (closestEdge + 1) % ring.size();

            int candidateVertIdx = ring.get(candidateRingIdx);
            float mx = vertices[candidateVertIdx * 2];
            float my = vertices[candidateVertIdx * 2 + 1];

            // Check if any vertex inside triangle (P, I, M) is a better bridge target.
            // A vertex inside this triangle that minimizes the angle ∠MPv is more visible.
            float bestTan = Float.MAX_VALUE;
            int bestRingIdx = candidateRingIdx;

            for (int j = 0; j < ring.size(); j++)
            {
                if (j == candidateRingIdx) continue;
                int vi = ring.get(j);
                float vx = vertices[vi * 2], vy = vertices[vi * 2 + 1];

                // Must be inside the triangle (P, intersection, M) and to the right of P
                if (vx < px) continue;
                if (isInsideTriangle(px, py, closestIx, py, mx, my, vx, vy))
                {
                    // Use tangent of angle from P to this vertex (smaller = closer to the ray)
                    float dx = vx - px;
                    float dy = Math.abs(vy - py);
                    float tan = (dx > 0) ? dy / dx : Float.MAX_VALUE;
                    if (tan < bestTan || (tan == bestTan && vx > vertices[ring.get(bestRingIdx) * 2]))
                    {
                        bestTan = tan;
                        bestRingIdx = j;
                    }
                }
            }

            // Splice hole into ring at bestRingIdx
            int mRingIdx = bestRingIdx;
            int mVert = ring.get(mRingIdx);
            int pVert = hStart + rightmost;

            ArrayList<Integer> insertion = new ArrayList<>(hCount + 2);
            insertion.add(pVert);
            for (int j = 1; j < hCount; j++)
                insertion.add(hStart + (rightmost + j) % hCount);
            insertion.add(pVert);
            insertion.add(mVert);

            ring.addAll(mRingIdx + 1, insertion);
        }

        int[] result = new int[ring.size()];
        for (int i = 0; i < ring.size(); i++)
            result[i] = ring.get(i);
        return result;
    }

    private static float maxX(float[] vertices, int start, int count)
    {
        float mx = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++)
        {
            float x = vertices[(start + i) * 2];
            if (x > mx) mx = x;
        }
        return mx;
    }

    private static boolean isInsideTriangle(float ax, float ay, float bx, float by,
                                             float cx, float cy, float px, float py)
    {
        float d1 = (bx - ax) * (py - ay) - (by - ay) * (px - ax);
        float d2 = (cx - bx) * (py - by) - (cy - by) * (px - bx);
        float d3 = (ax - cx) * (py - cy) - (ay - cy) * (px - cx);
        boolean hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0);
        boolean hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0);
        return !(hasNeg && hasPos);
    }

    // ===================================================================================
    // CPU ear-clipping fallback
    // ===================================================================================

    /**
     * CPU-based ear-clipping triangulation for a single simple polygon.
     * Used as fallback when GPU compute shaders are unavailable or the polygon exceeds GPU limits.
     *
     * @param vertices  flat x,y array of all vertex positions
     * @param ring      index list describing the simple polygon (from {@link #bridgeHoles})
     * @return triangle indices (groups of 3), each referencing global vertex indices in
     *         the vertices array, or empty array on failure
     */
    public static int[] triangulateCPU(float[] vertices, int[] ring)
    {
        int n = ring.length;
        if (n < 3) return new int[0];

        // Signed area for winding detection
        double area = 0;
        for (int i = 0; i < n; i++)
        {
            int j = (i + 1) % n;
            int vi = ring[i], vj = ring[j];
            area += (double) vertices[vi * 2] * vertices[vj * 2 + 1];
            area -= (double) vertices[vj * 2] * vertices[vi * 2 + 1];
        }
        float winding = area >= 0 ? 1f : -1f;

        // Linked list
        int[] prev = new int[n];
        int[] next = new int[n];
        int[] flags = new int[n]; // -1=removed, 0=reflex, 1=convex

        for (int i = 0; i < n; i++)
        {
            prev[i] = (i - 1 + n) % n;
            next[i] = (i + 1) % n;
            float c = cross2Df(vertices, ring[prev[i]], ring[i], ring[next[i]]);
            flags[i] = (c * winding >= 0) ? 1 : 0;
        }

        int[] tris = new int[(n - 2) * 3];
        int triIdx = 0;
        int remaining = n;
        int start = 0;

        while (remaining > 3)
        {
            int cur = start;
            int ear = -1;

            for (int iter = 0; iter < remaining; iter++)
            {
                if (flags[cur] >= 1)
                {
                    int pi = prev[cur];
                    int ni = next[cur];

                    boolean isEar = true;
                    int chk = next[ni];
                    while (chk != pi)
                    {
                        if (flags[chk] == 0 &&
                            pointInTriF(vertices, ring[chk], ring[pi], ring[cur], ring[ni]))
                        {
                            isEar = false;
                            break;
                        }
                        chk = next[chk];
                    }

                    if (isEar)
                    {
                        ear = cur;
                        break;
                    }
                }
                cur = next[cur];
            }

            if (ear == -1) break;

            int pi = prev[ear];
            int ni = next[ear];
            tris[triIdx++] = ring[pi];
            tris[triIdx++] = ring[ear];
            tris[triIdx++] = ring[ni];

            next[pi] = ni;
            prev[ni] = pi;
            flags[ear] = -1;
            remaining--;
            if (ear == start) start = ni;

            // Reclassify neighbors
            float c1 = cross2Df(vertices, ring[prev[pi]], ring[pi], ring[ni]);
            flags[pi] = (c1 * winding >= 0) ? 1 : 0;
            float c2 = cross2Df(vertices, ring[pi], ring[ni], ring[next[ni]]);
            flags[ni] = (c2 * winding >= 0) ? 1 : 0;
        }

        if (remaining == 3)
        {
            int v0 = start;
            int v1 = next[v0];
            int v2 = next[v1];
            tris[triIdx++] = ring[v0];
            tris[triIdx++] = ring[v1];
            tris[triIdx++] = ring[v2];
        }

        return triIdx == tris.length ? tris : Arrays.copyOf(tris, triIdx);
    }

    /** 2D cross product: (a-o) × (b-o) using vertex indices into a flat x,y array. */
    private static float cross2Df(float[] v, int o, int a, int b)
    {
        float ox = v[o * 2], oy = v[o * 2 + 1];
        float ax = v[a * 2], ay = v[a * 2 + 1];
        float bx = v[b * 2], by = v[b * 2 + 1];
        return (ax - ox) * (by - oy) - (ay - oy) * (bx - ox);
    }

    /** Point-in-triangle test using vertex indices into a flat x,y array. */
    private static boolean pointInTriF(float[] v, int p, int a, int b, int c)
    {
        float d1 = cross2Df(v, a, b, p);
        float d2 = cross2Df(v, b, c, p);
        float d3 = cross2Df(v, c, a, p);
        boolean hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0);
        boolean hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0);
        return !(hasNeg && hasPos);
    }

    // ===================================================================================
    // Outline index generation from original contours
    // ===================================================================================

    /**
     * Generates GL_LINES outline indices from original contour vertices.
     * Each consecutive pair of vertices in the contour produces one line segment,
     * plus a closing segment from the last vertex back to the first.
     *
     * @param startIdx  start vertex index in the shared vertex buffer
     * @param count     number of vertices in this contour
     * @return int array of GL_LINES index pairs
     */
    public static int[] generateOutlineIndices(int startIdx, int count)
    {
        if (count < 2) return new int[0];
        int[] indices = new int[count * 2];
        for (int i = 0; i < count; i++)
        {
            indices[i * 2] = startIdx + i;
            indices[i * 2 + 1] = startIdx + (i + 1) % count;
        }
        return indices;
    }
}
