/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — GPU compute shader for terrain ray-heightmap intersection.
 * Accepts a 2D elevation grid (heightmap) and a set of rays, dispatches a GLSL 430
 * compute shader that ray-marches each ray against the heightmap, and reads back
 * intersection results.
 */
package gov.nasa.worldwind.render.shaders;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3ES3;
import com.jogamp.opengl.GL4;

import gov.nasa.worldwind.util.Logging;

/**
 * Compute shader that performs ray-heightmap intersection on the GPU.
 * <p>
 * Each invocation handles one ray. The ray is marched through a flat-earth local-meter
 * coordinate system, bilinearly sampling a heightmap SSBO at each step. When the ray
 * passes below the terrain surface, binary search refinement pinpoints the crossing.
 * <p>
 * Created by seaglassfoundry.com — GPU terrain intersection for the TerrainIntersections example.
 */
public class TerrainIntersectionCompute {

    private static final int LOCAL_SIZE = 64;

    // @formatter:off
    private static final String COMPUTE_SOURCE = """
        #version 430
        layout(local_size_x = 64, local_size_y = 1, local_size_z = 1) in;

        // Heightmap: row-major float array, (hmWidth * hmHeight) elements.
        // Elevation in meters, indexed as [row * hmWidth + col].
        layout(std430, binding = 0) readonly buffer Heightmap { float heightmap[]; };

        // Rays: 6 floats per ray — origin (x,y,z) then target (x,y,z) in local meters.
        layout(std430, binding = 1) readonly buffer Rays { float rays[]; };

        // Results: 4 floats per ray — intersection (x,y,z) + w (1.0 = hit, 0.0 = miss).
        layout(std430, binding = 2) writeonly buffer Results { float results[]; };

        uniform int   u_numRays;
        uniform int   u_hmWidth;
        uniform int   u_hmHeight;
        uniform float u_sectorMinX;   // min local-X (meters) of heightmap coverage
        uniform float u_sectorMinY;   // min local-Y (meters)
        uniform float u_sectorMaxX;   // max local-X (meters)
        uniform float u_sectorMaxY;   // max local-Y (meters)

        // Bilinear sample of the heightmap at local-meter position (px, py).
        // Returns interpolated elevation, or -1e30 if outside the heightmap.
        float sampleHeightmap(float px, float py) {
            // Convert local meters to fractional texel coordinates
            float u = (px - u_sectorMinX) / (u_sectorMaxX - u_sectorMinX) * float(u_hmWidth - 1);
            float v = (py - u_sectorMinY) / (u_sectorMaxY - u_sectorMinY) * float(u_hmHeight - 1);

            if (u < 0.0 || u > float(u_hmWidth - 1) || v < 0.0 || v > float(u_hmHeight - 1))
                return -1e30;

            int u0 = int(floor(u));
            int v0 = int(floor(v));
            int u1 = min(u0 + 1, u_hmWidth - 1);
            int v1 = min(v0 + 1, u_hmHeight - 1);

            float fu = u - float(u0);
            float fv = v - float(v0);

            float h00 = heightmap[v0 * u_hmWidth + u0];
            float h10 = heightmap[v0 * u_hmWidth + u1];
            float h01 = heightmap[v1 * u_hmWidth + u0];
            float h11 = heightmap[v1 * u_hmWidth + u1];

            return mix(mix(h00, h10, fu), mix(h01, h11, fu), fv);
        }

        void main() {
            uint rid = gl_GlobalInvocationID.x;
            if (int(rid) >= u_numRays)
                return;

            uint base = rid * 6u;
            vec3 origin = vec3(rays[base], rays[base + 1u], rays[base + 2u]);
            vec3 target = vec3(rays[base + 3u], rays[base + 4u], rays[base + 5u]);

            vec3 dir = target - origin;
            float totalDist = length(dir);
            if (totalDist < 0.001) {
                // Degenerate ray
                uint rbase = rid * 4u;
                results[rbase]     = 0.0;
                results[rbase + 1u] = 0.0;
                results[rbase + 2u] = 0.0;
                results[rbase + 3u] = 0.0;
                return;
            }

            // March along the ray in 256 uniform steps
            const int NUM_STEPS = 256;
            float dt = 1.0 / float(NUM_STEPS);
            float prevT = 0.0;
            float prevDiff = 1e30;  // positive = ray above terrain
            bool hit = false;
            float hitT = 0.0;

            for (int i = 0; i <= NUM_STEPS; i++) {
                float t = float(i) * dt;
                vec3 p = origin + dir * t;
                float terrainElev = sampleHeightmap(p.x, p.y);

                if (terrainElev <= -1e29) {
                    // Outside heightmap — skip
                    prevT = t;
                    prevDiff = 1e30;
                    continue;
                }

                float diff = p.z - terrainElev;  // positive = above, negative = below

                if (diff <= 0.0 && prevDiff > 0.0) {
                    // Crossed from above to below — refine with binary search
                    float lo = prevT;
                    float hi = t;
                    for (int j = 0; j < 8; j++) {
                        float mid = (lo + hi) * 0.5;
                        vec3 mp = origin + dir * mid;
                        float me = sampleHeightmap(mp.x, mp.y);
                        if (me > -1e29 && mp.z - me <= 0.0)
                            hi = mid;
                        else
                            lo = mid;
                    }
                    hitT = (lo + hi) * 0.5;
                    hit = true;
                    break;
                }

                prevT = t;
                prevDiff = diff;
            }

            uint rbase = rid * 4u;
            if (hit) {
                vec3 hitPos = origin + dir * hitT;
                results[rbase]     = hitPos.x;
                results[rbase + 1u] = hitPos.y;
                results[rbase + 2u] = hitPos.z;
                results[rbase + 3u] = 1.0;
            } else {
                results[rbase]     = 0.0;
                results[rbase + 1u] = 0.0;
                results[rbase + 2u] = 0.0;
                results[rbase + 3u] = 0.0;
            }
        }
        """;
    // @formatter:on

    private int computeProgram;
    private int heightmapSsbo;
    private int raySsbo;
    private int resultSsbo;
    private int heightmapCapacity; // current buffer capacity in bytes
    private int rayCapacity;
    private int resultCapacity;

    private int uNumRaysLoc;
    private int uHmWidthLoc;
    private int uHmHeightLoc;
    private int uSectorMinXLoc;
    private int uSectorMinYLoc;
    private int uSectorMaxXLoc;
    private int uSectorMaxYLoc;

    private boolean initialized;
    private boolean initFailed;

    /**
     * Initializes the compute shader program. Must be called on the GL thread.
     *
     * @return true if initialization succeeded
     */
    public boolean init(GL4 gl4) {
        if (this.initialized)
            return true;
        if (this.initFailed)
            return false;

        this.computeProgram = compileComputeProgram(gl4, COMPUTE_SOURCE, "TerrainIntersectionCompute");
        if (this.computeProgram == 0) {
            this.initFailed = true;
            return false;
        }

        this.uNumRaysLoc = gl4.glGetUniformLocation(this.computeProgram, "u_numRays");
        this.uHmWidthLoc = gl4.glGetUniformLocation(this.computeProgram, "u_hmWidth");
        this.uHmHeightLoc = gl4.glGetUniformLocation(this.computeProgram, "u_hmHeight");
        this.uSectorMinXLoc = gl4.glGetUniformLocation(this.computeProgram, "u_sectorMinX");
        this.uSectorMinYLoc = gl4.glGetUniformLocation(this.computeProgram, "u_sectorMinY");
        this.uSectorMaxXLoc = gl4.glGetUniformLocation(this.computeProgram, "u_sectorMaxX");
        this.uSectorMaxYLoc = gl4.glGetUniformLocation(this.computeProgram, "u_sectorMaxY");

        // Create SSBOs (will be sized on first dispatch)
        int[] bufs = new int[3];
        gl4.glGenBuffers(3, bufs, 0);
        this.heightmapSsbo = bufs[0];
        this.raySsbo = bufs[1];
        this.resultSsbo = bufs[2];

        this.initialized = true;
        return true;
    }

    /**
     * Result of a GPU terrain intersection dispatch.
     */
    public static class IntersectionResult {
        /** Local-meter X of intersection (relative to sector centroid). */
        public final float x;
        /** Local-meter Y of intersection. */
        public final float y;
        /** Elevation at intersection in meters. */
        public final float z;
        /** True if this ray hit the terrain. */
        public final boolean hit;

        public IntersectionResult(float x, float y, float z, boolean hit) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.hit = hit;
        }
    }

    /**
     * Dispatches the compute shader to intersect rays against a heightmap.
     * Must be called on the GL thread after {@link #init(GL4)}.
     *
     * @param gl4        the GL4 context
     * @param heightmap  flat row-major elevation data in meters, (hmWidth * hmHeight) floats
     * @param hmWidth    number of columns in the heightmap
     * @param hmHeight   number of rows in the heightmap
     * @param sectorMinX minimum local-X of the heightmap sector (meters)
     * @param sectorMinY minimum local-Y of the heightmap sector (meters)
     * @param sectorMaxX maximum local-X of the heightmap sector (meters)
     * @param sectorMaxY maximum local-Y of the heightmap sector (meters)
     * @param rayData    packed ray data: 6 floats per ray (originX,Y,Z, targetX,Y,Z) in local meters
     * @param numRays    number of rays
     *
     * @return array of IntersectionResult, one per ray
     */
    public IntersectionResult[] dispatch(GL4 gl4, float[] heightmap, int hmWidth, int hmHeight,
                                         float sectorMinX, float sectorMinY, float sectorMaxX, float sectorMaxY,
                                         float[] rayData, int numRays) {
        if (!this.initialized || this.initFailed)
            return null;

        // ---- Upload heightmap SSBO ----
        int hmBytes = heightmap.length * Float.BYTES;
        FloatBuffer hmBuf = ByteBuffer.allocateDirect(hmBytes).order(ByteOrder.nativeOrder()).asFloatBuffer();
        hmBuf.put(heightmap).flip();
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.heightmapSsbo);
        if (hmBytes > this.heightmapCapacity) {
            gl4.glBufferData(GL3ES3.GL_SHADER_STORAGE_BUFFER, hmBytes, hmBuf, GL.GL_DYNAMIC_DRAW);
            this.heightmapCapacity = hmBytes;
        } else {
            gl4.glBufferSubData(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0, hmBytes, hmBuf);
        }

        // ---- Upload ray SSBO ----
        int rayBytes = rayData.length * Float.BYTES;
        FloatBuffer rayBuf = ByteBuffer.allocateDirect(rayBytes).order(ByteOrder.nativeOrder()).asFloatBuffer();
        rayBuf.put(rayData).flip();
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.raySsbo);
        if (rayBytes > this.rayCapacity) {
            gl4.glBufferData(GL3ES3.GL_SHADER_STORAGE_BUFFER, rayBytes, rayBuf, GL.GL_DYNAMIC_DRAW);
            this.rayCapacity = rayBytes;
        } else {
            gl4.glBufferSubData(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0, rayBytes, rayBuf);
        }

        // ---- Allocate/resize result SSBO ----
        int resBytes = numRays * 4 * Float.BYTES; // vec4 per ray
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.resultSsbo);
        if (resBytes > this.resultCapacity) {
            gl4.glBufferData(GL3ES3.GL_SHADER_STORAGE_BUFFER, resBytes, null, GL2ES3.GL_DYNAMIC_READ);
            this.resultCapacity = resBytes;
        }
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0);

        // ---- Bind SSBOs ----
        gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0, this.heightmapSsbo);
        gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 1, this.raySsbo);
        gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 2, this.resultSsbo);

        // ---- Set uniforms and dispatch ----
        gl4.glUseProgram(this.computeProgram);
        gl4.glUniform1i(this.uNumRaysLoc, numRays);
        gl4.glUniform1i(this.uHmWidthLoc, hmWidth);
        gl4.glUniform1i(this.uHmHeightLoc, hmHeight);
        gl4.glUniform1f(this.uSectorMinXLoc, sectorMinX);
        gl4.glUniform1f(this.uSectorMinYLoc, sectorMinY);
        gl4.glUniform1f(this.uSectorMaxXLoc, sectorMaxX);
        gl4.glUniform1f(this.uSectorMaxYLoc, sectorMaxY);

        gl4.glDispatchCompute((numRays + LOCAL_SIZE - 1) / LOCAL_SIZE, 1, 1);

        // ---- Memory barrier: SSBO writes must complete before CPU read-back ----
        gl4.glMemoryBarrier(GL3ES3.GL_SHADER_STORAGE_BARRIER_BIT | GL2ES3.GL_BUFFER_UPDATE_BARRIER_BIT);

        // ---- Read back results ----
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.resultSsbo);
        ByteBuffer mapped = gl4.glMapBufferRange(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0, resBytes, GL2ES3.GL_MAP_READ_BIT);
        IntersectionResult[] out = new IntersectionResult[numRays];
        if (mapped != null) {
            mapped.order(ByteOrder.nativeOrder());
            FloatBuffer fb = mapped.asFloatBuffer();
            for (int i = 0; i < numRays; i++) {
                float x = fb.get();
                float y = fb.get();
                float z = fb.get();
                float w = fb.get();
                out[i] = new IntersectionResult(x, y, z, w > 0.5f);
            }
            gl4.glUnmapBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER);
        } else {
            Logging.logger().warning("TerrainIntersectionCompute: glMapBufferRange returned null");
            for (int i = 0; i < numRays; i++)
                out[i] = new IntersectionResult(0, 0, 0, false);
        }
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0);

        // ---- Restore previous program ----
        gl4.glUseProgram(0);

        // Unbind SSBOs
        gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0, 0);
        gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 1, 0);
        gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 2, 0);

        return out;
    }

    /**
     * Releases GPU resources. Must be called on the GL thread.
     */
    public void dispose(GL4 gl4) {
        if (this.computeProgram != 0) {
            gl4.glDeleteProgram(this.computeProgram);
            this.computeProgram = 0;
        }
        int[] bufs = {this.heightmapSsbo, this.raySsbo, this.resultSsbo};
        gl4.glDeleteBuffers(3, bufs, 0);
        this.heightmapSsbo = 0;
        this.raySsbo = 0;
        this.resultSsbo = 0;
        this.heightmapCapacity = 0;
        this.rayCapacity = 0;
        this.resultCapacity = 0;
        this.initialized = false;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public boolean isInitFailed() {
        return this.initFailed;
    }

    // ---- Shader compilation (same pattern as GpuTriangulator.compileComputeProgram) ----

    private static int compileComputeProgram(GL4 gl4, String source, String label) {
        int shader = gl4.glCreateShader(GL3ES3.GL_COMPUTE_SHADER);
        gl4.glShaderSource(shader, 1, new String[]{source}, null);
        gl4.glCompileShader(shader);

        int[] status = new int[1];
        gl4.glGetShaderiv(shader, GL2ES2.GL_COMPILE_STATUS, status, 0);
        if (status[0] == GL.GL_FALSE) {
            int[] logLen = new int[1];
            gl4.glGetShaderiv(shader, GL2ES2.GL_INFO_LOG_LENGTH, logLen, 0);
            byte[] log = new byte[logLen[0]];
            gl4.glGetShaderInfoLog(shader, logLen[0], null, 0, log, 0);
            Logging.logger().severe(label + " compile error: " + new String(log));
            gl4.glDeleteShader(shader);
            return 0;
        }

        int prog = gl4.glCreateProgram();
        gl4.glAttachShader(prog, shader);
        gl4.glLinkProgram(prog);
        gl4.glDeleteShader(shader);

        gl4.glGetProgramiv(prog, GL2ES2.GL_LINK_STATUS, status, 0);
        if (status[0] == GL.GL_FALSE) {
            int[] logLen = new int[1];
            gl4.glGetProgramiv(prog, GL2ES2.GL_INFO_LOG_LENGTH, logLen, 0);
            byte[] log = new byte[logLen[0]];
            gl4.glGetProgramInfoLog(prog, logLen[0], null, 0, log, 0);
            Logging.logger().severe(label + " link error: " + new String(log));
            gl4.glDeleteProgram(prog);
            return 0;
        }

        return prog;
    }
}
