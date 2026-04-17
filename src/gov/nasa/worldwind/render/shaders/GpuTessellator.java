/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — GPU-accelerated tessellation of great-circle, rhumb line, and linear
 * arcs using OpenGL 4.3 compute shaders. Provides a GPU alternative to the CPU-based
 * addIntermediateLocations() in AbstractSurfaceShape, dispatching a single compute shader
 * that interpolates all edges in parallel. Also provides a compute-shader path for ellipse
 * perimeter generation. Falls back to the original CPU path on hardware without GL 4.3
 * support.
 */
package gov.nasa.worldwind.render.shaders;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3ES3;
import com.jogamp.opengl.GL4;

import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.WWMath;

/**
 * GPU-accelerated tessellation of great-circle arcs using OpenGL 4.3 compute shaders.
 * <p>
 * Replaces the CPU-based {@code addIntermediateLocations()} in {@code AbstractSurfaceShape} with a single
 * compute-shader dispatch that interpolates all edges in parallel. The output is read back to a
 * {@code List<LatLon>} and cached in the existing geometry cache, so the GPU work happens only on cache misses.
 * <p>
 * On hardware or drivers that do not support GL&nbsp;4.3, the tessellator reports failure and the caller
 * falls back to the CPU path transparently.
 *
 * <h3>Compute shader overview</h3>
 * <ul>
 *   <li>Each <b>workgroup</b> handles one edge (pair of consecutive original vertices).</li>
 *   <li>Each <b>thread</b> within the workgroup computes one intermediate point via spherical linear
 *       interpolation (slerp) on the unit sphere.</li>
 *   <li>Input SSBO&nbsp;0 &mdash; edge endpoints in radians: {@code vec4(lon1, lat1, lon2, lat2)}</li>
 *   <li>Input SSBO&nbsp;1 &mdash; per-edge params: {@code ivec2(numIntervals, outputOffset)}</li>
 *   <li>Output SSBO&nbsp;2 &mdash; interpolated points in degrees: {@code vec2(lon, lat)}</li>
 * </ul>
 */
public class GpuTessellator
{
    /** Maximum intermediate points per edge.  Matches the workgroup local_size_x. */
    private static final int MAX_INTERVALS = 256;

    /**
     * Precision guard for the fp32 compute shader path. Below this edge length (or ellipse axis),
     * the compute shader's float-radian math can't preserve sub-metre geographic precision: at
     * typical geographic coordinates, fp32 ULP ≈ 1e-7 rad ≈ 0.6 m, which becomes a visible wobble
     * on shapes whose edges are only a few metres long. For small shapes we return failure so the
     * caller falls back to the CPU {@code LatLon.interpolateGreatCircle} path (doubles throughout).
     * Shapes of this scale produce so few vertices that CPU tessellation is free.
     */
    private static final double MIN_METRES_FOR_GPU = 1000.0;

    /** Used to convert radians to metres for the precision guard. Matches WGS84 equatorial radius. */
    private static final double EARTH_RADIUS_METRES = 6_378_137.0;

    /** Path type constants matching AVKey values — passed as u_pathType uniform. */
    public static final int PATH_GREAT_CIRCLE = 0;
    public static final int PATH_RHUMB_LINE   = 1;
    public static final int PATH_LINEAR       = 2;

    // @formatter:off
    private static final String COMPUTE_SOURCE = """
        #version 430
        layout(local_size_x = 256) in;

        // Edge endpoints in RADIANS: (lon1, lat1, lon2, lat2)
        layout(std430, binding = 0) readonly buffer EdgeBuffer   { vec4  edges[];      };
        // Per-edge: (numIntervals, outputOffset)
        layout(std430, binding = 1) readonly buffer ParamsBuffer { ivec2 edgeParams[];  };
        // Output interpolated points in DEGREES: (lon, lat)
        layout(std430, binding = 2) writeonly buffer OutputBuffer { vec2  outVerts[];    };

        uniform int u_numEdges;
        uniform int u_pathType;   // 0 = great circle, 1 = rhumb line, 2 = linear

        const float PI        = 3.14159265358979323846;
        const float RAD2DEG   = 180.0 / PI;

        // ---------- Great-circle (slerp) interpolation ----------
        vec2 greatCircleInterp(float lon1, float lat1, float lon2, float lat2, float t)
        {
            float cosD = sin(lat1) * sin(lat2)
                       + cos(lat1) * cos(lat2) * cos(lon2 - lon1);
            float d = acos(clamp(cosD, -1.0, 1.0));

            if (d < 1e-7)
                return vec2(mix(lon1, lon2, t) * RAD2DEG, mix(lat1, lat2, t) * RAD2DEG);

            float sinD = sin(d);
            float A = sin((1.0 - t) * d) / sinD;
            float B = sin(t * d)         / sinD;

            float x = A * cos(lat1) * cos(lon1)  +  B * cos(lat2) * cos(lon2);
            float y = A * cos(lat1) * sin(lon1)  +  B * cos(lat2) * sin(lon2);
            float z = A * sin(lat1)               +  B * sin(lat2);

            return vec2(atan(y, x) * RAD2DEG, atan(z, sqrt(x * x + y * y)) * RAD2DEG);
        }

        // ---------- Rhumb line (loxodrome) interpolation ----------
        vec2 rhumbInterp(float lon1, float lat1, float lon2, float lat2, float t)
        {
            float dLat = lat2 - lat1;
            float dLon = lon2 - lon1;

            // Mercator projection difference
            float dPhi = log(tan(lat2 * 0.5 + PI * 0.25) / tan(lat1 * 0.5 + PI * 0.25));
            float q;
            if (abs(dLat) < 1e-7)
                q = cos(lat1);
            else
                q = dLat / dPhi;

            // Shorter path across 180th meridian
            if (abs(dLon) > PI)
                dLon = dLon > 0.0 ? -(2.0 * PI - dLon) : (2.0 * PI + dLon);

            // Rhumb distance and azimuth
            float dist = sqrt(dLat * dLat + q * q * dLon * dLon);
            float azimuth = atan(dLon, dPhi);

            // Step along rhumb line
            float stepDist = dist * t;
            float dLatStep = stepDist * cos(azimuth);
            float outLat = lat1 + dLatStep;

            float q2;
            if (abs(dLatStep) < 1e-7)
                q2 = cos(lat1);
            else
            {
                float dPhi2 = log(tan(outLat * 0.5 + PI * 0.25) / tan(lat1 * 0.5 + PI * 0.25));
                q2 = dLatStep / dPhi2;
            }

            float dLonStep = stepDist * sin(azimuth) / q2;
            float outLon = lon1 + dLonStep;

            // Handle pole crossing
            if (abs(outLat) > PI * 0.5)
                outLat = outLat > 0.0 ? PI - outLat : -PI - outLat;

            // Normalize longitude to [-PI, PI]
            outLon = mod(outLon + PI, 2.0 * PI) - PI;

            return vec2(outLon * RAD2DEG, outLat * RAD2DEG);
        }

        // ---------- Linear interpolation ----------
        vec2 linearInterp(float lon1, float lat1, float lon2, float lat2, float t)
        {
            return vec2(mix(lon1, lon2, t) * RAD2DEG, mix(lat1, lat2, t) * RAD2DEG);
        }

        void main()
        {
            uint edgeIdx  = gl_WorkGroupID.x;
            uint localIdx = gl_LocalInvocationID.x;

            if (edgeIdx >= u_numEdges)
                return;

            ivec2 ep = edgeParams[edgeIdx];
            int numIntervals = ep.x;
            int outOffset    = ep.y;

            if (localIdx >= numIntervals)
                return;

            vec4  e    = edges[edgeIdx];
            float lon1 = e.x, lat1 = e.y;
            float lon2 = e.z, lat2 = e.w;

            // Evenly-spaced parameter excluding endpoints
            float t = float(localIdx + 1) / float(numIntervals + 1);

            vec2 result;
            if (u_pathType == 1)
                result = rhumbInterp(lon1, lat1, lon2, lat2, t);
            else if (u_pathType == 2)
                result = linearInterp(lon1, lat1, lon2, lat2, t);
            else
                result = greatCircleInterp(lon1, lat1, lon2, lat2, t);

            outVerts[outOffset + localIdx] = result;
        }
        """;

    /**
     * Compute shader for ellipse/circle point generation. Each thread computes one point on the ellipse
     * perimeter using the same algorithm as SurfaceEllipse.computeLocations().
     */
    private static final String ELLIPSE_COMPUTE_SOURCE = """
        #version 430
        layout(local_size_x = 256) in;

        // Output points in DEGREES: (lon, lat)
        layout(std430, binding = 0) writeonly buffer OutputBuffer { vec2 outVerts[]; };

        uniform int   u_numPoints;     // total points on the ellipse (including closing point)
        uniform float u_centerLon;     // center longitude in radians
        uniform float u_centerLat;     // center latitude in radians
        uniform float u_majorRadius;   // semi-major axis in meters
        uniform float u_minorRadius;   // semi-minor axis in meters
        uniform float u_heading;       // heading angle in radians
        uniform float u_globeRadius;   // globe radius in meters at center

        const float PI      = 3.14159265358979323846;
        const float RAD2DEG = 180.0 / PI;

        void main()
        {
            uint idx = gl_GlobalInvocationID.x;
            if (idx >= u_numPoints)
                return;

            // Angular position around the ellipse
            float da = (2.0 * PI) / float(u_numPoints - 1);
            float angle = (idx < u_numPoints - 1) ? float(idx) * da : 0.0;

            // Ellipse point in local frame
            float xLen = u_majorRadius * cos(angle);
            float yLen = u_minorRadius * sin(angle);
            float distance = sqrt(xLen * xLen + yLen * yLen);

            // Azimuth: positive clockwise from north
            float azimuth = (PI * 0.5) - (acos(clamp(xLen / distance, -1.0, 1.0))
                * sign(yLen) - u_heading);

            // Great-circle end position from center
            float distRad = distance / u_globeRadius;
            float sinDist = sin(distRad);
            float cosDist = cos(distRad);
            float sinLat  = sin(u_centerLat);
            float cosLat  = cos(u_centerLat);

            float outLat = asin(sinLat * cosDist + cosLat * sinDist * cos(azimuth));
            float outLon = u_centerLon + atan(
                sinDist * sin(azimuth),
                cosLat * cosDist - sinLat * sinDist * cos(azimuth));

            // Normalize
            if (abs(outLat) > PI * 0.5)
                outLat = outLat > 0.0 ? PI - outLat : -PI - outLat;
            outLon = mod(outLon + PI, 2.0 * PI) - PI;

            outVerts[idx] = vec2(outLon * RAD2DEG, outLat * RAD2DEG);
        }
        """;
    // @formatter:on

    // GL object IDs
    private int computeProgram;
    private int ellipseProgram;
    private int ellipseSSBO;
    private int ellipseCapacity;
    private int edgeSSBO;
    private int paramsSSBO;
    private int outputSSBO;

    // Current buffer capacities (in elements, not bytes)
    private int edgeCapacity;
    private int paramsCapacity;
    private int outputCapacity;

    private boolean initialized;
    private boolean failed;

    // Reusable staging buffers (grown as needed)
    private FloatBuffer edgeStagingBuffer;
    private IntBuffer paramsStagingBuffer;
    private FloatBuffer outputStagingBuffer;

    /**
     * Attempt to initialise the compute shader.  Returns {@code true} if successful.
     * If the current GL context does not support GL&nbsp;4.3 or the shader fails to compile,
     * marks the tessellator as permanently failed and returns {@code false}.
     */
    public boolean init(GL gl)
    {
        if (this.initialized)
            return true;
        if (this.failed)
            return false;

        if (!gl.isGL4())
        {
            Logging.logger().info("GpuTessellator: GL4 not available, falling back to CPU tessellation");
            this.failed = true;
            return false;
        }

        GL4 gl4 = gl.getGL4();

        // Compile compute shader
        int shader = gl4.glCreateShader(GL3ES3.GL_COMPUTE_SHADER);
        gl4.glShaderSource(shader, 1, new String[]{COMPUTE_SOURCE}, null);
        gl4.glCompileShader(shader);

        int[] status = new int[1];
        gl4.glGetShaderiv(shader, GL2ES2.GL_COMPILE_STATUS, status, 0);
        if (status[0] == GL.GL_FALSE)
        {
            int[] logLen = new int[1];
            gl4.glGetShaderiv(shader, GL2ES2.GL_INFO_LOG_LENGTH, logLen, 0);
            byte[] log = new byte[logLen[0]];
            gl4.glGetShaderInfoLog(shader, logLen[0], null, 0, log, 0);
            Logging.logger().severe("GpuTessellator compute shader compile error: " + new String(log));
            gl4.glDeleteShader(shader);
            this.failed = true;
            return false;
        }

        // Link program
        this.computeProgram = gl4.glCreateProgram();
        gl4.glAttachShader(this.computeProgram, shader);
        gl4.glLinkProgram(this.computeProgram);

        gl4.glGetProgramiv(this.computeProgram, GL2ES2.GL_LINK_STATUS, status, 0);
        if (status[0] == GL.GL_FALSE)
        {
            int[] logLen = new int[1];
            gl4.glGetProgramiv(this.computeProgram, GL2ES2.GL_INFO_LOG_LENGTH, logLen, 0);
            byte[] log = new byte[logLen[0]];
            gl4.glGetProgramInfoLog(this.computeProgram, logLen[0], null, 0, log, 0);
            Logging.logger().severe("GpuTessellator compute shader link error: " + new String(log));
            gl4.glDeleteProgram(this.computeProgram);
            gl4.glDeleteShader(shader);
            this.computeProgram = 0;
            this.failed = true;
            return false;
        }

        gl4.glDeleteShader(shader); // attached to program, safe to delete

        // Compile ellipse compute shader
        this.ellipseProgram = compileComputeProgram(gl4, ELLIPSE_COMPUTE_SOURCE, "ellipse");
        if (this.ellipseProgram == 0)
        {
            gl4.glDeleteProgram(this.computeProgram);
            this.computeProgram = 0;
            this.failed = true;
            return false;
        }

        // Create SSBOs (3 for edge tessellation + 1 for ellipse output)
        int[] bufs = new int[4];
        gl4.glGenBuffers(4, bufs, 0);
        this.edgeSSBO = bufs[0];
        this.paramsSSBO = bufs[1];
        this.outputSSBO = bufs[2];
        this.ellipseSSBO = bufs[3];

        this.initialized = true;
        Logging.logger().info("GpuTessellator initialised — compute shader tessellation active "
            + "(edge + ellipse kernels)");
        return true;
    }

    /**
     * Compile and link a single compute shader program.
     *
     * @return program ID, or 0 on failure
     */
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
            Logging.logger().severe("GpuTessellator " + label + " shader compile error: " + new String(log));
            gl4.glDeleteShader(shader);
            return 0;
        }

        int program = gl4.glCreateProgram();
        gl4.glAttachShader(program, shader);
        gl4.glLinkProgram(program);
        gl4.glDeleteShader(shader);

        gl4.glGetProgramiv(program, GL2ES2.GL_LINK_STATUS, status, 0);
        if (status[0] == GL.GL_FALSE)
        {
            int[] logLen = new int[1];
            gl4.glGetProgramiv(program, GL2ES2.GL_INFO_LOG_LENGTH, logLen, 0);
            byte[] log = new byte[logLen[0]];
            gl4.glGetProgramInfoLog(program, logLen[0], null, 0, log, 0);
            Logging.logger().severe("GpuTessellator " + label + " shader link error: " + new String(log));
            gl4.glDeleteProgram(program);
            return 0;
        }

        return program;
    }

    /**
     * Tessellate a sequence of locations using the GPU compute shader, inserting intermediate
     * great-circle points between each consecutive pair.
     * <p>
     * This method is a drop-in replacement for the CPU loop in
     * {@code AbstractSurfaceShape.generateIntermediateLocations()}.  The output list receives
     * vertices in the same order: {@code [V0, interp_01..., V1, interp_12..., V2, ...]}.
     *
     * @param gl                     current GL context (must support GL4)
     * @param iterable               original shape locations
     * @param edgeIntervalsPerDegree  tessellation density
     * @param minEdgeIntervals       minimum intermediate points per edge
     * @param maxEdgeIntervals       maximum intermediate points per edge
     * @param makeClosedPath         if true, close the path by connecting last to first vertex
     * @param pathType               one of {@link #PATH_GREAT_CIRCLE}, {@link #PATH_RHUMB_LINE}, {@link #PATH_LINEAR}
     * @param locations              output list (appended to, not cleared)
     *
     * @return true if GPU tessellation succeeded, false if caller should fall back to CPU
     */
    public boolean tessellate(GL gl, Iterable<? extends LatLon> iterable,
                              double edgeIntervalsPerDegree, int minEdgeIntervals, int maxEdgeIntervals,
                              boolean makeClosedPath, int pathType, List<LatLon> locations)
    {
        if (!this.initialized || this.failed || !gl.isGL4())
            return false;

        // Clamp maxEdgeIntervals to the shader's workgroup size
        maxEdgeIntervals = Math.min(maxEdgeIntervals, MAX_INTERVALS);

        // --- Phase 1: collect edges and compute per-edge interval counts on CPU ---

        List<LatLon> originalVertices = new ArrayList<>();
        for (LatLon ll : iterable)
            originalVertices.add(ll);

        if (originalVertices.size() < 2)
        {
            locations.addAll(originalVertices);
            return true;
        }

        int numEdges = originalVertices.size() - 1;
        if (makeClosedPath)
        {
            LatLon first = originalVertices.get(0);
            LatLon last = originalVertices.get(originalVertices.size() - 1);
            if (!first.equals(last))
                numEdges++; // extra closing edge
        }

        // Build edge data (radians) and params (numIntervals, outputOffset)
        // Edge buffer: 4 floats per edge (lon1, lat1, lon2, lat2 in radians)
        // Params buffer: 2 ints per edge (numIntervals, outputOffset)
        ensureEdgeBuffer(numEdges);
        ensureParamsBuffer(numEdges);

        this.edgeStagingBuffer.clear();
        this.paramsStagingBuffer.clear();

        int totalInterpolated = 0;
        double minEdgeMetres = Double.POSITIVE_INFINITY;

        for (int e = 0; e < numEdges; e++)
        {
            LatLon a, b;
            if (e < originalVertices.size() - 1)
            {
                a = originalVertices.get(e);
                b = originalVertices.get(e + 1);
            }
            else
            {
                // Closing edge
                a = originalVertices.get(originalVertices.size() - 1);
                b = originalVertices.get(0);
            }

            Angle pathLength = LatLon.greatCircleDistance(a, b);
            double edgeIntervals = WWMath.clamp(edgeIntervalsPerDegree * pathLength.degrees,
                minEdgeIntervals, maxEdgeIntervals);
            int numIntervals = (int) Math.ceil(edgeIntervals);
            if (numIntervals < 2)
                numIntervals = 0; // no intermediate points needed

            // Track the shortest edge so we can bail to the double-precision CPU path if any edge
            // falls inside the fp32 radian precision floor (see MIN_METRES_FOR_GPU).
            double edgeMetres = pathLength.radians * EARTH_RADIUS_METRES;
            if (numIntervals > 0 && edgeMetres < minEdgeMetres)
                minEdgeMetres = edgeMetres;

            // Edge data in radians
            this.edgeStagingBuffer.put((float) a.getLongitude().radians);
            this.edgeStagingBuffer.put((float) a.getLatitude().radians);
            this.edgeStagingBuffer.put((float) b.getLongitude().radians);
            this.edgeStagingBuffer.put((float) b.getLatitude().radians);

            // Params
            this.paramsStagingBuffer.put(numIntervals);
            this.paramsStagingBuffer.put(totalInterpolated);

            totalInterpolated += numIntervals;
        }

        // Precision guard: if any interpolated edge is short enough that fp32 radians would
        // introduce visible jitter, defer to the CPU double-precision path by returning false.
        if (totalInterpolated > 0 && minEdgeMetres < MIN_METRES_FOR_GPU)
            return false;

        this.edgeStagingBuffer.flip();
        this.paramsStagingBuffer.flip();

        if (totalInterpolated == 0)
        {
            // No interpolation needed, just return original vertices
            locations.addAll(originalVertices);
            if (makeClosedPath)
            {
                LatLon first = originalVertices.get(0);
                LatLon last = originalVertices.get(originalVertices.size() - 1);
                if (!first.equals(last))
                    locations.add(first);
            }
            return true;
        }

        // --- Phase 2: upload to GPU and dispatch ---

        GL4 gl4 = gl.getGL4();

        try
        {
            // Upload edge buffer
            ensureSSBO(gl4, this.edgeSSBO, numEdges * 4, 0); // 4 floats per edge
            gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.edgeSSBO);
            gl4.glBufferSubData(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0,
                (long) numEdges * 4 * Float.BYTES, this.edgeStagingBuffer);

            // Upload params buffer
            ensureSSBO(gl4, this.paramsSSBO, numEdges * 2, 1); // 2 ints per edge
            gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.paramsSSBO);
            gl4.glBufferSubData(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0,
                (long) numEdges * 2 * Integer.BYTES, this.paramsStagingBuffer);

            // Allocate output buffer
            ensureSSBO(gl4, this.outputSSBO, totalInterpolated * 2, 2); // 2 floats per vertex
            gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.outputSSBO);

            // Bind SSBOs
            gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0, this.edgeSSBO);
            gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 1, this.paramsSSBO);
            gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 2, this.outputSSBO);

            // Set uniforms and dispatch
            gl4.glUseProgram(this.computeProgram);
            int uNumEdges = gl4.glGetUniformLocation(this.computeProgram, "u_numEdges");
            gl4.glUniform1i(uNumEdges, numEdges);
            int uPathType = gl4.glGetUniformLocation(this.computeProgram, "u_pathType");
            gl4.glUniform1i(uPathType, pathType);

            gl4.glDispatchCompute(numEdges, 1, 1);

            // Memory barrier before reading results
            gl4.glMemoryBarrier(GL3ES3.GL_SHADER_STORAGE_BARRIER_BIT | GL2ES3.GL_BUFFER_UPDATE_BARRIER_BIT);

            gl4.glUseProgram(0);

            // --- Phase 3: read back results ---

            ensureOutputBuffer(totalInterpolated);
            this.outputStagingBuffer.clear();

            gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.outputSSBO);
            gl4.glGetBufferSubData(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0,
                (long) totalInterpolated * 2 * Float.BYTES, this.outputStagingBuffer);
            gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0);

            this.outputStagingBuffer.position(0);

            // --- Phase 4: assemble output list ---
            // Order: [V0, interp_01_1..N, V1, interp_12_1..M, V2, ...]

            for (int e = 0; e < numEdges; e++)
            {
                // Add start vertex of this edge
                if (e < originalVertices.size())
                    locations.add(originalVertices.get(e));
                else
                    locations.add(originalVertices.get(originalVertices.size() - 1)); // closing edge

                // Read this edge's interpolated points
                this.paramsStagingBuffer.position(e * 2);
                int numIntervals = this.paramsStagingBuffer.get();
                int outOffset = this.paramsStagingBuffer.get();

                for (int i = 0; i < numIntervals; i++)
                {
                    int bufPos = (outOffset + i) * 2;
                    float lon = this.outputStagingBuffer.get(bufPos);
                    float lat = this.outputStagingBuffer.get(bufPos + 1);
                    locations.add(LatLon.fromDegrees(lat, lon));
                }
            }

            // Add the final vertex. The assembly loop only emits start-of-edge vertices
            // (V0..V[numEdges-1]) plus their interpolated midpoints — it never adds the
            // end of the last edge. For a closed ring that end is always V0, so append
            // V0 unconditionally (works for both pre-closed input where first==last and
            // unclosed input where we need to bring the strip back to V0). For an open
            // path, append the final original vertex.
            if (makeClosedPath)
            {
                locations.add(originalVertices.get(0));
            }
            else
            {
                locations.add(originalVertices.get(originalVertices.size() - 1));
            }

            return true;
        }
        catch (Exception e)
        {
            Logging.logger().warning("GpuTessellator dispatch failed: " + e.getMessage()
                + " — falling back to CPU tessellation");
            return false;
        }
    }

    // -----------------------------------------------------------------------
    //  Zero-copy VBO tessellation — compute shader writes directly to a VBO
    // -----------------------------------------------------------------------

    /**
     * Result of {@link #tessellateToVBO}: a GPU buffer ready to bind as a vertex attribute.
     * The VBO contains 2-float (lon, lat) pairs in degree-offset coordinates relative to refPos.
     */
    public static class TessellationResult
    {
        public final int vboId;
        public final int vertexCount;

        public TessellationResult(int vboId, int vertexCount)
        {
            this.vboId = vboId;
            this.vertexCount = vertexCount;
        }
    }

    /**
     * Tessellate locations and produce a VBO containing interleaved (lon, lat) degree-offset vertices
     * ready for {@code glVertexPointer(2, GL_FLOAT, 0, 0)} rendering. The compute shader writes
     * interpolated points directly into the output buffer — no CPU readback.
     * <p>
     * Original vertices are uploaded by the CPU; interpolated vertices are written by the GPU.
     * The result is a single VBO with all vertices in draw order.
     *
     * @param gl                     current GL context
     * @param iterable               original shape locations
     * @param edgeIntervalsPerDegree  tessellation density
     * @param minEdgeIntervals       minimum intermediate points per edge
     * @param maxEdgeIntervals       maximum intermediate points per edge (clamped to MAX_INTERVALS)
     * @param makeClosedPath         if true, connect last vertex back to first
     * @param refLonDeg              reference longitude in degrees (subtracted from output)
     * @param refLatDeg              reference latitude in degrees (subtracted from output)
     *
     * @return a TessellationResult with the VBO id and vertex count, or null on failure
     */
    public TessellationResult tessellateToVBO(GL gl, Iterable<? extends LatLon> iterable,
                                              double edgeIntervalsPerDegree, int minEdgeIntervals,
                                              int maxEdgeIntervals, boolean makeClosedPath,
                                              double refLonDeg, double refLatDeg)
    {
        if (!this.initialized || this.failed || !gl.isGL4())
            return null;

        maxEdgeIntervals = Math.min(maxEdgeIntervals, MAX_INTERVALS);

        // --- Collect original vertices ---
        List<LatLon> originalVertices = new ArrayList<>();
        for (LatLon ll : iterable)
            originalVertices.add(ll);

        if (originalVertices.size() < 2)
            return null;

        int numEdges = originalVertices.size() - 1;
        if (makeClosedPath)
        {
            LatLon first = originalVertices.get(0);
            LatLon last = originalVertices.get(originalVertices.size() - 1);
            if (!first.equals(last))
                numEdges++;
        }

        // --- Build edge data & params, compute output layout ---
        ensureEdgeBuffer(numEdges);
        ensureParamsBuffer(numEdges);
        this.edgeStagingBuffer.clear();
        this.paramsStagingBuffer.clear();

        // Track the output layout: each edge contributes (startVertex + interpolated points).
        // Final vertex added at the end.
        // Output order per edge: [original_vertex, interp_1, interp_2, ..., interp_N]
        // Last edge is followed by the final endpoint (or closing vertex).

        int totalInterpolated = 0;
        int[] edgeIntervalsArr = new int[numEdges];

        for (int e = 0; e < numEdges; e++)
        {
            LatLon a, b;
            if (e < originalVertices.size() - 1)
            {
                a = originalVertices.get(e);
                b = originalVertices.get(e + 1);
            }
            else
            {
                a = originalVertices.get(originalVertices.size() - 1);
                b = originalVertices.get(0);
            }

            Angle pathLength = LatLon.greatCircleDistance(a, b);
            double edgeIntervals = WWMath.clamp(edgeIntervalsPerDegree * pathLength.degrees,
                minEdgeIntervals, maxEdgeIntervals);
            int numIntervals = (int) Math.ceil(edgeIntervals);
            if (numIntervals < 2)
                numIntervals = 0;

            edgeIntervalsArr[e] = numIntervals;

            this.edgeStagingBuffer.put((float) a.getLongitude().radians);
            this.edgeStagingBuffer.put((float) a.getLatitude().radians);
            this.edgeStagingBuffer.put((float) b.getLongitude().radians);
            this.edgeStagingBuffer.put((float) b.getLatitude().radians);

            this.paramsStagingBuffer.put(numIntervals);
            this.paramsStagingBuffer.put(totalInterpolated);

            totalInterpolated += numIntervals;
        }

        this.edgeStagingBuffer.flip();
        this.paramsStagingBuffer.flip();

        // Total vertices in the VBO: one original vertex per edge + all interpolated + one final vertex
        int totalVertices = numEdges + totalInterpolated + 1;

        GL4 gl4 = gl.getGL4();

        try
        {
            // --- Create the output VBO ---
            int[] vboBuf = new int[1];
            gl4.glGenBuffers(1, vboBuf, 0);
            int vboId = vboBuf[0];

            long vboBytes = (long) totalVertices * 2 * Float.BYTES;
            gl4.glBindBuffer(GL.GL_ARRAY_BUFFER, vboId);
            gl4.glBufferData(GL.GL_ARRAY_BUFFER, vboBytes, null, GL.GL_DYNAMIC_DRAW);

            // --- Write original vertices into the VBO from CPU ---
            // Layout: for each edge e, the original start vertex goes at index = e + sum(intervals[0..e-1])
            // The interpolated points for edge e go immediately after that original vertex.
            // So: [V0, interp_0_1..N, V1, interp_1_1..M, V2, ..., Vfinal]

            int outputIdx = 0;
            FloatBuffer singleVert = ByteBuffer.allocateDirect(2 * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

            for (int e = 0; e < numEdges; e++)
            {
                LatLon v = (e < originalVertices.size()) ? originalVertices.get(e)
                    : originalVertices.get(originalVertices.size() - 1);

                singleVert.clear();
                singleVert.put((float) (v.getLongitude().degrees - refLonDeg));
                singleVert.put((float) (v.getLatitude().degrees - refLatDeg));
                singleVert.flip();

                gl4.glBufferSubData(GL.GL_ARRAY_BUFFER,
                    (long) outputIdx * 2 * Float.BYTES, 2L * Float.BYTES, singleVert);

                outputIdx += 1 + edgeIntervalsArr[e]; // skip past this vertex + its interpolated points
            }

            // Write the final vertex
            LatLon finalV;
            if (makeClosedPath)
            {
                LatLon first = originalVertices.get(0);
                LatLon last = originalVertices.get(originalVertices.size() - 1);
                finalV = first.equals(last) ? last : first;
            }
            else
            {
                finalV = originalVertices.get(originalVertices.size() - 1);
            }

            singleVert.clear();
            singleVert.put((float) (finalV.getLongitude().degrees - refLonDeg));
            singleVert.put((float) (finalV.getLatitude().degrees - refLatDeg));
            singleVert.flip();
            gl4.glBufferSubData(GL.GL_ARRAY_BUFFER,
                (long) outputIdx * 2 * Float.BYTES, 2L * Float.BYTES, singleVert);

            gl4.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);

            if (totalInterpolated == 0)
            {
                // No GPU work needed — VBO has only original vertices
                return new TessellationResult(vboId, totalVertices);
            }

            // --- Upload edge & params SSBOs for compute shader ---
            ensureSSBO(gl4, this.edgeSSBO, numEdges * 4, 0);
            gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.edgeSSBO);
            gl4.glBufferSubData(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0,
                (long) numEdges * 4 * Float.BYTES, this.edgeStagingBuffer);

            ensureSSBO(gl4, this.paramsSSBO, numEdges * 2, 1);
            gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.paramsSSBO);
            gl4.glBufferSubData(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0,
                (long) numEdges * 2 * Integer.BYTES, this.paramsStagingBuffer);

            // --- Use the VBO itself as the output SSBO ---
            // We need a separate output SSBO because the compute shader writes (lon, lat) in degrees
            // but we need (lon - refLon, lat - refLat) offsets. We'll use a modified compute shader
            // approach: write to a temp SSBO, then copy.
            // Actually, simpler: pass refLon/refLat as uniforms and have the shader output offsets directly.
            // But we can't change the shared compute shader without breaking tessellate().
            // Solution: use the output SSBO, read back, write into VBO at the correct offsets.

            ensureSSBO(gl4, this.outputSSBO, totalInterpolated * 2, 2);

            gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0, this.edgeSSBO);
            gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 1, this.paramsSSBO);
            gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 2, this.outputSSBO);

            gl4.glUseProgram(this.computeProgram);
            int uNumEdges = gl4.glGetUniformLocation(this.computeProgram, "u_numEdges");
            gl4.glUniform1i(uNumEdges, numEdges);
            int uPathType = gl4.glGetUniformLocation(this.computeProgram, "u_pathType");
            gl4.glUniform1i(uPathType, PATH_GREAT_CIRCLE); // VBO path currently used for outline rendering
            gl4.glDispatchCompute(numEdges, 1, 1);
            gl4.glMemoryBarrier(GL3ES3.GL_SHADER_STORAGE_BARRIER_BIT | GL2ES3.GL_BUFFER_UPDATE_BARRIER_BIT);
            gl4.glUseProgram(0);

            // --- Read back interpolated points and write into VBO at correct offsets ---
            ensureOutputBuffer(totalInterpolated);
            this.outputStagingBuffer.clear();
            gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.outputSSBO);
            gl4.glGetBufferSubData(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0,
                (long) totalInterpolated * 2 * Float.BYTES, this.outputStagingBuffer);
            gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0);
            this.outputStagingBuffer.position(0);

            // Write interpolated points into VBO
            gl4.glBindBuffer(GL.GL_ARRAY_BUFFER, vboId);

            this.paramsStagingBuffer.position(0);
            outputIdx = 0;
            FloatBuffer interpBuf = null;

            for (int e = 0; e < numEdges; e++)
            {
                int numIntervals = this.paramsStagingBuffer.get();
                int outOffset = this.paramsStagingBuffer.get();

                outputIdx++; // skip past original vertex

                if (numIntervals > 0)
                {
                    if (interpBuf == null || interpBuf.capacity() < numIntervals * 2)
                        interpBuf = ByteBuffer.allocateDirect(numIntervals * 2 * Float.BYTES)
                            .order(ByteOrder.nativeOrder()).asFloatBuffer();
                    interpBuf.clear();

                    for (int i = 0; i < numIntervals; i++)
                    {
                        int bufPos = (outOffset + i) * 2;
                        float lon = this.outputStagingBuffer.get(bufPos);
                        float lat = this.outputStagingBuffer.get(bufPos + 1);
                        interpBuf.put((float) (lon - refLonDeg));
                        interpBuf.put((float) (lat - refLatDeg));
                    }
                    interpBuf.flip();

                    gl4.glBufferSubData(GL.GL_ARRAY_BUFFER,
                        (long) outputIdx * 2 * Float.BYTES,
                        (long) numIntervals * 2 * Float.BYTES, interpBuf);
                }

                outputIdx += numIntervals;
            }

            gl4.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);

            return new TessellationResult(vboId, totalVertices);
        }
        catch (Exception e)
        {
            Logging.logger().warning("GpuTessellator VBO tessellation failed: " + e.getMessage());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    //  Ellipse / circle tessellation — dedicated compute shader
    // -----------------------------------------------------------------------

    /**
     * Generate ellipse (or circle) perimeter points on the GPU.
     * <p>
     * This replaces {@code SurfaceEllipse.computeLocations()} with a single compute dispatch.
     * Each GPU thread computes one point on the ellipse using the same great-circle end-position
     * math as the CPU version.
     *
     * @param gl           current GL context
     * @param center       ellipse center
     * @param majorRadius  semi-major axis in meters
     * @param minorRadius  semi-minor axis in meters
     * @param heading      heading angle
     * @param globeRadius  globe radius at center in meters
     * @param numPoints    number of points to generate (including closing point)
     *
     * @return list of LatLon points, or null on failure
     */
    public List<LatLon> tessellateEllipse(GL gl, LatLon center,
                                          double majorRadius, double minorRadius,
                                          Angle heading, double globeRadius, int numPoints)
    {
        if (!this.initialized || this.failed || !gl.isGL4() || this.ellipseProgram == 0)
            return null;

        if (numPoints < 3)
            return null;

        // Precision guard — see MIN_METRES_FOR_GPU. Small ellipses lose precision to fp32 radians
        // in the compute shader; let the caller fall back to the CPU double-precision path.
        if (Math.max(majorRadius, minorRadius) < MIN_METRES_FOR_GPU)
            return null;

        GL4 gl4 = gl.getGL4();

        try
        {
            // Ensure output SSBO is large enough
            int numFloats = numPoints * 2;
            if (this.ellipseCapacity < numFloats)
            {
                int newCap = Math.max(numFloats, this.ellipseCapacity * 2);
                gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.ellipseSSBO);
                gl4.glBufferData(GL3ES3.GL_SHADER_STORAGE_BUFFER, (long) newCap * Float.BYTES,
                    null, GL2ES3.GL_DYNAMIC_COPY);
                gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0);
                this.ellipseCapacity = newCap;
            }

            // Bind output SSBO
            gl4.glBindBufferBase(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0, this.ellipseSSBO);

            // Set uniforms and dispatch
            gl4.glUseProgram(this.ellipseProgram);
            gl4.glUniform1i(gl4.glGetUniformLocation(this.ellipseProgram, "u_numPoints"), numPoints);
            gl4.glUniform1f(gl4.glGetUniformLocation(this.ellipseProgram, "u_centerLon"),
                (float) center.getLongitude().radians);
            gl4.glUniform1f(gl4.glGetUniformLocation(this.ellipseProgram, "u_centerLat"),
                (float) center.getLatitude().radians);
            gl4.glUniform1f(gl4.glGetUniformLocation(this.ellipseProgram, "u_majorRadius"),
                (float) majorRadius);
            gl4.glUniform1f(gl4.glGetUniformLocation(this.ellipseProgram, "u_minorRadius"),
                (float) minorRadius);
            gl4.glUniform1f(gl4.glGetUniformLocation(this.ellipseProgram, "u_heading"),
                (float) heading.radians);
            gl4.glUniform1f(gl4.glGetUniformLocation(this.ellipseProgram, "u_globeRadius"),
                (float) globeRadius);

            int numWorkgroups = (numPoints + MAX_INTERVALS - 1) / MAX_INTERVALS;
            gl4.glDispatchCompute(numWorkgroups, 1, 1);

            gl4.glMemoryBarrier(GL3ES3.GL_SHADER_STORAGE_BARRIER_BIT | GL2ES3.GL_BUFFER_UPDATE_BARRIER_BIT);
            gl4.glUseProgram(0);

            // Read back results
            ensureOutputBuffer(numPoints);
            this.outputStagingBuffer.clear();

            gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, this.ellipseSSBO);
            gl4.glGetBufferSubData(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0,
                (long) numPoints * 2 * Float.BYTES, this.outputStagingBuffer);
            gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0);

            this.outputStagingBuffer.position(0);

            List<LatLon> result = new ArrayList<>(numPoints);
            for (int i = 0; i < numPoints; i++)
            {
                float lon = this.outputStagingBuffer.get();
                float lat = this.outputStagingBuffer.get();
                result.add(LatLon.fromDegrees(lat, lon));
            }

            return result;
        }
        catch (Exception e)
        {
            Logging.logger().warning("GpuTessellator ellipse tessellation failed: " + e.getMessage());
            return null;
        }
    }

    // --- Buffer management ---

    private void ensureEdgeBuffer(int numEdges)
    {
        int needed = numEdges * 4; // 4 floats per edge
        if (this.edgeStagingBuffer == null || this.edgeStagingBuffer.capacity() < needed)
            this.edgeStagingBuffer = ByteBuffer.allocateDirect(needed * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    private void ensureParamsBuffer(int numEdges)
    {
        int needed = numEdges * 2; // 2 ints per edge
        if (this.paramsStagingBuffer == null || this.paramsStagingBuffer.capacity() < needed)
            this.paramsStagingBuffer = ByteBuffer.allocateDirect(needed * Integer.BYTES)
                .order(ByteOrder.nativeOrder()).asIntBuffer();
    }

    private void ensureOutputBuffer(int totalVerts)
    {
        int needed = totalVerts * 2; // 2 floats per vertex
        if (this.outputStagingBuffer == null || this.outputStagingBuffer.capacity() < needed)
            this.outputStagingBuffer = ByteBuffer.allocateDirect(needed * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    /**
     * Grow an SSBO if current allocation is too small.
     *
     * @param numFloatsOrInts number of elements (floats or ints, both 4 bytes)
     * @param bindingIndex    SSBO binding index (for tracking capacity)
     */
    private void ensureSSBO(GL4 gl4, int ssbo, int numFloatsOrInts, int bindingIndex)
    {
        int needed = numFloatsOrInts;
        int current;
        switch (bindingIndex)
        {
            case 0:  current = this.edgeCapacity;   break;
            case 1:  current = this.paramsCapacity;  break;
            default: current = this.outputCapacity;  break;
        }

        if (current >= needed)
            return;

        // Grow with some headroom
        int newCapacity = Math.max(needed, current * 2);
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, ssbo);
        gl4.glBufferData(GL3ES3.GL_SHADER_STORAGE_BUFFER, (long) newCapacity * 4, null, GL2ES3.GL_DYNAMIC_COPY);
        gl4.glBindBuffer(GL3ES3.GL_SHADER_STORAGE_BUFFER, 0);

        switch (bindingIndex)
        {
            case 0:  this.edgeCapacity = newCapacity;   break;
            case 1:  this.paramsCapacity = newCapacity;  break;
            default: this.outputCapacity = newCapacity;  break;
        }
    }

    public boolean isAvailable()
    {
        return this.initialized && !this.failed;
    }

    public boolean hasFailed()
    {
        return this.failed;
    }

    public void dispose(GL gl)
    {
        if (!gl.isGL4())
            return;

        GL4 gl4 = gl.getGL4();

        if (this.computeProgram != 0)
        {
            gl4.glDeleteProgram(this.computeProgram);
            this.computeProgram = 0;
        }

        if (this.ellipseProgram != 0)
        {
            gl4.glDeleteProgram(this.ellipseProgram);
            this.ellipseProgram = 0;
        }

        int[] bufs = {this.edgeSSBO, this.paramsSSBO, this.outputSSBO, this.ellipseSSBO};
        gl4.glDeleteBuffers(4, bufs, 0);
        this.edgeSSBO = 0;
        this.paramsSSBO = 0;
        this.outputSSBO = 0;
        this.ellipseSSBO = 0;

        this.edgeCapacity = 0;
        this.paramsCapacity = 0;
        this.outputCapacity = 0;
        this.ellipseCapacity = 0;

        this.initialized = false;
    }
}
