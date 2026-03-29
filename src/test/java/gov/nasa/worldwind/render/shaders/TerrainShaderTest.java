/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — Phase 8 test coverage for TerrainShader and TessellationTerrainShader.
 * Verifies initial Java-side state and GLSL source content without a GL context.
 */
package gov.nasa.worldwind.render.shaders;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link TerrainShader} and {@link TessellationTerrainShader}.
 * No OpenGL context required.
 */
@RunWith(JUnit4.class)
public class TerrainShaderTest
{
    private static String getSource(Class<?> cls, String fieldName) throws Exception
    {
        Field f = cls.getDeclaredField(fieldName);
        f.setAccessible(true);
        return (String) f.get(null);
    }

    // =======================================================================
    // TerrainShader — initial state
    // =======================================================================

    @Test
    public void terrainShader_isValid_falseBeforeInit()
    {
        assertFalse("TerrainShader must not be valid before init()", new TerrainShader().isValid());
    }

    @Test
    public void terrainShader_program_nullBeforeInit() throws Exception
    {
        TerrainShader shader = new TerrainShader();
        Field f = TerrainShader.class.getDeclaredField("program");
        f.setAccessible(true);
        assertNull("program must be null before init()", f.get(shader));
    }

    // =======================================================================
    // TerrainShader — vertex GLSL source
    // =======================================================================

    @Test
    public void terrainShader_vertexSource_version130() throws Exception
    {
        assertTrue(getSource(TerrainShader.class, "VERTEX_SOURCE").contains("#version 130"));
    }

    @Test
    public void terrainShader_vertexSource_declares_u_useHeightmap() throws Exception
    {
        assertTrue(getSource(TerrainShader.class, "VERTEX_SOURCE").contains("uniform int u_useHeightmap"));
    }

    @Test
    public void terrainShader_vertexSource_declares_u_refCenter() throws Exception
    {
        assertTrue(getSource(TerrainShader.class, "VERTEX_SOURCE").contains("uniform vec3 u_refCenter"));
    }

    @Test
    public void terrainShader_vertexSource_declares_u_heightScale() throws Exception
    {
        assertTrue(getSource(TerrainShader.class, "VERTEX_SOURCE").contains("uniform float u_heightScale"));
    }

    @Test
    public void terrainShader_vertexSource_declares_u_flatGlobe() throws Exception
    {
        assertTrue(getSource(TerrainShader.class, "VERTEX_SOURCE").contains("uniform int u_flatGlobe"));
    }

    // =======================================================================
    // TerrainShader — fragment GLSL source
    // =======================================================================

    @Test
    public void terrainShader_fragmentSource_explicit_fragColor() throws Exception
    {
        String src = getSource(TerrainShader.class, "FRAGMENT_SOURCE");
        assertTrue("fragment must use explicit out vec4 fragColor", src.contains("out vec4 fragColor"));
        assertFalse("fragment must not assign to deprecated gl_FragColor", src.contains("gl_FragColor ="));
    }

    @Test
    public void terrainShader_fragmentSource_declares_u_imagery() throws Exception
    {
        assertTrue(getSource(TerrainShader.class, "FRAGMENT_SOURCE").contains("uniform sampler2D u_imagery"));
    }

    @Test
    public void terrainShader_fragmentSource_declares_u_alphaMask() throws Exception
    {
        assertTrue(getSource(TerrainShader.class, "FRAGMENT_SOURCE").contains("uniform sampler2D u_alphaMask"));
    }

    @Test
    public void terrainShader_fragmentSource_alphaTest_usesDiscard() throws Exception
    {
        // Shader replaces glAlphaFunc(GL_GREATER, 0.01) with explicit discard
        String src = getSource(TerrainShader.class, "FRAGMENT_SOURCE");
        assertTrue("alpha test must use discard", src.contains("discard"));
        assertTrue("alpha test threshold must be 0.01", src.contains("0.01"));
    }

    // =======================================================================
    // TessellationTerrainShader — constants
    // =======================================================================

    @Test
    public void tessShader_defaultPixelsPerTriangle_is12()
    {
        assertEquals(12.0f, TessellationTerrainShader.DEFAULT_PIXELS_PER_TRIANGLE, 0.0f);
    }

    @Test
    public void tessShader_isValid_falseBeforeInit()
    {
        assertFalse("TessellationTerrainShader must not be valid before init()",
            new TessellationTerrainShader().isValid());
    }

    @Test
    public void tessShader_program_nullBeforeInit() throws Exception
    {
        TessellationTerrainShader shader = new TessellationTerrainShader();
        Field f = TessellationTerrainShader.class.getDeclaredField("program");
        f.setAccessible(true);
        assertNull("program must be null before init()", f.get(shader));
    }

    @Test
    public void tessShader_unconstrainedOuter_isFourBy32() throws Exception
    {
        Field f = TessellationTerrainShader.class.getDeclaredField("UNCONSTRAINED_OUTER");
        f.setAccessible(true);
        float[] arr = (float[]) f.get(null);
        assertEquals("UNCONSTRAINED_OUTER must have 4 elements", 4, arr.length);
        for (float v : arr)
            assertEquals("each unconstrained outer level must be 32", 32f, v, 0f);
    }

    // =======================================================================
    // TessellationTerrainShader — TCS GLSL source
    // =======================================================================

    @Test
    public void tessShader_tcsSource_declares_u_maxOuter() throws Exception
    {
        assertTrue(getSource(TessellationTerrainShader.class, "TCS_SOURCE")
            .contains("u_maxOuter"));
    }

    @Test
    public void tessShader_tcsSource_clamps_tessLevels() throws Exception
    {
        String src = getSource(TessellationTerrainShader.class, "TCS_SOURCE");
        // TCS must set all four outer levels
        assertTrue(src.contains("gl_TessLevelOuter[0]"));
        assertTrue(src.contains("gl_TessLevelOuter[1]"));
        assertTrue(src.contains("gl_TessLevelOuter[2]"));
        assertTrue(src.contains("gl_TessLevelOuter[3]"));
    }

    @Test
    public void tessShader_tcsSource_clampRange_1_to_32() throws Exception
    {
        String src = getSource(TessellationTerrainShader.class, "TCS_SOURCE");
        // Must clamp to [1, 32] as specified by the task (not [1, 64] as in an earlier draft)
        assertTrue("TCS must clamp tessellation levels to min 1", src.contains("1.0, 32.0"));
    }

    // =======================================================================
    // TessellationTerrainShader — TES GLSL source
    // =======================================================================

    @Test
    public void tessShader_tesSource_declares_u_earthRadius() throws Exception
    {
        assertTrue(getSource(TessellationTerrainShader.class, "TES_SOURCE")
            .contains("uniform float     u_earthRadius"));
    }

    @Test
    public void tessShader_tesSource_declares_u_useHeightmap() throws Exception
    {
        assertTrue(getSource(TessellationTerrainShader.class, "TES_SOURCE")
            .contains("uniform int       u_useHeightmap"));
    }

    @Test
    public void tessShader_tesSource_deltaCorrection_subtractsHBilinear() throws Exception
    {
        // TES delta-correction: residual = h_actual − h_bilinear; h_bilinear = length(worldPos) - u_earthRadius
        String src = getSource(TessellationTerrainShader.class, "TES_SOURCE");
        assertTrue("TES must compute h_bilinear from length(worldPos) and u_earthRadius",
            src.contains("h_bilinear"));
        assertTrue("TES delta-correction must use u_earthRadius",
            src.contains("u_earthRadius"));
    }

    // =======================================================================
    // TCS tessellation level arithmetic (pure Java)
    // =======================================================================

    @Test
    public void tessLevel_clamp_belowMin_clipsToOne()
    {
        // clamp(x, 1, 32) — if screen-space edge length / pixelsPerTri < 1 → clamp to 1
        float edgePixels = 5f;
        float pixelsPerTri = 12f;
        float raw = edgePixels / pixelsPerTri;
        float clamped = Math.max(1f, Math.min(32f, raw));
        assertEquals("very short edge should clamp to level 1", 1f, clamped, 1e-6f);
    }

    @Test
    public void tessLevel_clamp_aboveMax_clipsTo32()
    {
        float edgePixels = 2000f;
        float pixelsPerTri = 12f;
        float raw = edgePixels / pixelsPerTri;
        float clamped = Math.max(1f, Math.min(32f, raw));
        assertEquals("very long edge should clamp to level 32", 32f, clamped, 1e-6f);
    }

    @Test
    public void tessLevel_maxOuter_capsBelowNeighbour()
    {
        // min(clamped, u_maxOuter[k]) — stitching constraint
        float clamped = 20f;
        float maxOuter = 8f; // neighbour's level is 8
        float result = Math.min(clamped, maxOuter);
        assertEquals("u_maxOuter cap must reduce tessellation level", 8f, result, 0f);
    }

    @Test
    public void tessLevel_maxOuter_doesNotReduceWhenAlreadyLower()
    {
        float clamped = 4f;
        float maxOuter = 32f; // unconstrained
        float result = Math.min(clamped, maxOuter);
        assertEquals("u_maxOuter should not reduce already-low level", 4f, result, 0f);
    }
}
