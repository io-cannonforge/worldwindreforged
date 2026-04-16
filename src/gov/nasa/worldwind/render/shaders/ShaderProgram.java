/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — OpenGL shader program management utility. Handles compilation and linking
 * of vertex and fragment shaders, uniform location caching, and program lifecycle
 * (dispose/cleanup). Provides the shader infrastructure used by DashLineShader,
 * GpuTessellator, GpuTriangulator, and SurfaceShapeFillShader.
 *
 * Changes (Terrain Shader Removal):
 * - Removed initTessellation() and tessellation shader fields. Terrain tiles now render
 *   exclusively via the fixed-function pipeline; this class is retained for non-terrain shaders.
 */
package gov.nasa.worldwind.render.shaders;

import java.util.HashMap;
import java.util.Map;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;

import gov.nasa.worldwind.util.Logging;

/**
 * Manages an OpenGL shader program (vertex + fragment). Handles compilation, linking, uniform caching, and cleanup.
 */
public class ShaderProgram
{
    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;
    private boolean valid;
    private final Map<String, Integer> uniformLocations = new HashMap<>();
    private final Map<Integer, String> pendingAttribBindings = new HashMap<>();

    public ShaderProgram()
    {
    }

    /**
     * Queues a vertex attribute location binding to be applied before linking.
     * Call this before {@link #init} or {@link #initTessellation}.
     * Replaces {@code layout(location=N)} qualifiers for GLSL versions below 3.30.
     * seaglassfoundry.com
     */
    public void bindAttribLocation(int index, String name)
    {
        this.pendingAttribBindings.put(index, name);
    }

    /**
     * Compile and link the program from source strings.
     *
     * @return true if compilation and linking succeeded.
     */
    public boolean init(GL2 gl, String vertexSource, String fragmentSource)
    {
        this.vertexShaderId = compileShader(gl, GL2ES2.GL_VERTEX_SHADER, vertexSource);
        if (this.vertexShaderId == 0)
            return false;

        this.fragmentShaderId = compileShader(gl, GL2ES2.GL_FRAGMENT_SHADER, fragmentSource);
        if (this.fragmentShaderId == 0)
        {
            gl.glDeleteShader(this.vertexShaderId);
            return false;
        }

        this.programId = gl.glCreateProgram();
        gl.glAttachShader(this.programId, this.vertexShaderId);
        gl.glAttachShader(this.programId, this.fragmentShaderId);

        // Bind any pre-link attribute locations (set by caller via bindAttribLocation)
        for (var entry : this.pendingAttribBindings.entrySet())
            gl.glBindAttribLocation(this.programId, entry.getKey(), entry.getValue());
        this.pendingAttribBindings.clear();

        gl.glLinkProgram(this.programId);

        int[] linkStatus = new int[1];
        gl.glGetProgramiv(this.programId, GL2ES2.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == GL.GL_FALSE)
        {
            int[] logLen = new int[1];
            gl.glGetProgramiv(this.programId, GL2ES2.GL_INFO_LOG_LENGTH, logLen, 0);
            byte[] log = new byte[logLen[0]];
            gl.glGetProgramInfoLog(this.programId, logLen[0], null, 0, log, 0);
            Logging.logger().severe("Shader link error: " + new String(log));
            dispose(gl);
            return false;
        }

        this.valid = true;
        return true;
    }

    private static int compileShader(GL2 gl, int type, String source)
    {
        int shader = gl.glCreateShader(type);
        gl.glShaderSource(shader, 1, new String[]{source}, null);
        gl.glCompileShader(shader);

        int[] compileStatus = new int[1];
        gl.glGetShaderiv(shader, GL2ES2.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == GL.GL_FALSE)
        {
            int[] logLen = new int[1];
            gl.glGetShaderiv(shader, GL2ES2.GL_INFO_LOG_LENGTH, logLen, 0);
            byte[] log = new byte[logLen[0]];
            gl.glGetShaderInfoLog(shader, logLen[0], null, 0, log, 0);
            String typeStr;
            if      (type == GL2ES2.GL_VERTEX_SHADER)   typeStr = "vertex";
            else if (type == GL2ES2.GL_FRAGMENT_SHADER) typeStr = "fragment";
            else                                        typeStr = "unknown";
            Logging.logger().severe("Shader compile error (" + typeStr + "): " + new String(log));
            gl.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    public void use(GL2 gl)
    {
        if (this.valid)
            gl.glUseProgram(this.programId);
    }

    public void unuse(GL2 gl)
    {
        gl.glUseProgram(0);
    }

    public int getUniformLocation(GL2 gl, String name)
    {
        return this.uniformLocations.computeIfAbsent(name,
            n -> gl.glGetUniformLocation(this.programId, n));
    }

    public void setUniform1f(GL2 gl, String name, float value)
    {
        gl.glUniform1f(getUniformLocation(gl, name), value);
    }

    public void setUniform2f(GL2 gl, String name, float x, float y)
    {
        gl.glUniform2f(getUniformLocation(gl, name), x, y);
    }

    public void setUniform3f(GL2 gl, String name, float x, float y, float z)
    {
        gl.glUniform3f(getUniformLocation(gl, name), x, y, z);
    }

    public void setUniform4f(GL2 gl, String name, float x, float y, float z, float w)
    {
        gl.glUniform4f(getUniformLocation(gl, name), x, y, z, w);
    }

    public void setUniform1i(GL2 gl, String name, int value)
    {
        gl.glUniform1i(getUniformLocation(gl, name), value);
    }

    public void setUniformMatrix4fv(GL2 gl, String name, float[] matrix)
    {
        gl.glUniformMatrix4fv(getUniformLocation(gl, name), 1, false, matrix, 0);
    }

    /**
     * Reads the current OpenGL modelview and projection matrices, multiplies them (P × MV),
     * and uploads the result as the named mat4 uniform. Replaces {@code gl_ModelViewProjectionMatrix}
     * for shaders that want to be compatible with core profile without changing Java call sites.
     * seaglassfoundry.com
     */
    public void setUniformMvp(GL2 gl, String name)
    {
        float[] mv   = new float[16];
        float[] proj = new float[16];
        gl.glGetFloatv(GLMatrixFunc.GL_MODELVIEW_MATRIX,  mv,   0);
        gl.glGetFloatv(GLMatrixFunc.GL_PROJECTION_MATRIX, proj, 0);
        float[] mvp = new float[16];
        // Column-major multiply: mvp = proj * mv
        for (int col = 0; col < 4; col++)
            for (int row = 0; row < 4; row++)
            {
                float sum = 0f;
                for (int k = 0; k < 4; k++)
                    sum += proj[k * 4 + row] * mv[col * 4 + k];
                mvp[col * 4 + row] = sum;
            }
        gl.glUniformMatrix4fv(getUniformLocation(gl, name), 1, false, mvp, 0);
    }

    /**
     * Double-precision variant of {@link #setUniformMvp}. Reads the MODELVIEW and PROJECTION matrices
     * as double (the fixed-function matrix stack stores them as double internally), multiplies them in
     * double, and uploads the result as a {@code dmat4} uniform via {@code glUniformMatrix4dv}. Requires
     * a GL 4.0+ context (caller should verify via {@link GLCapabilityCheck#hasShaderFp64}).
     * seaglassfoundry.com
     */
    public void setUniformMvpDouble(GL2 gl, String name)
    {
        double[] mv   = new double[16];
        double[] proj = new double[16];
        gl.glGetDoublev(GLMatrixFunc.GL_MODELVIEW_MATRIX,  mv,   0);
        gl.glGetDoublev(GLMatrixFunc.GL_PROJECTION_MATRIX, proj, 0);
        double[] mvp = new double[16];
        for (int col = 0; col < 4; col++)
            for (int row = 0; row < 4; row++)
            {
                double sum = 0;
                for (int k = 0; k < 4; k++)
                    sum += proj[k * 4 + row] * mv[col * 4 + k];
                mvp[col * 4 + row] = sum;
            }
        GL3 gl3 = gl.getGL3();
        gl3.glUniformMatrix4dv(getUniformLocation(gl, name), 1, false, mvp, 0);
    }

    public int getProgramId()
    {
        return this.programId;
    }

    public boolean isValid()
    {
        return this.valid;
    }

    public void dispose(GL2 gl)
    {
        if (this.programId != 0)
        {
            gl.glDeleteProgram(this.programId);
            this.programId = 0;
        }
        if (this.vertexShaderId != 0)
        {
            gl.glDeleteShader(this.vertexShaderId);
            this.vertexShaderId = 0;
        }
        if (this.fragmentShaderId != 0)
        {
            gl.glDeleteShader(this.fragmentShaderId);
            this.fragmentShaderId = 0;
        }
        this.valid = false;
        this.uniformLocations.clear();
    }
}
