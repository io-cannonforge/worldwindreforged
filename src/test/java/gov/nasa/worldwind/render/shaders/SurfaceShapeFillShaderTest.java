/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — Phase 8 test coverage for SurfaceShapeFillShader.
 * Verifies initial Java-side state, pattern constants, and GLSL source content
 * without a GL context.
 */
package gov.nasa.worldwind.render.shaders;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link SurfaceShapeFillShader} Java-side state and GLSL source content.
 * No OpenGL context required.
 */
@RunWith(JUnit4.class)
public class SurfaceShapeFillShaderTest
{
    private static String getSource(String fieldName) throws Exception
    {
        Field f = SurfaceShapeFillShader.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        return (String) f.get(null);
    }

    // -----------------------------------------------------------------------
    // Pattern mode constants
    // -----------------------------------------------------------------------

    @Test
    public void patternNone_isZero()
    {
        assertEquals(0, SurfaceShapeFillShader.PATTERN_NONE);
    }

    @Test
    public void patternHatch_isOne()
    {
        assertEquals(1, SurfaceShapeFillShader.PATTERN_HATCH);
    }

    @Test
    public void patternCrosshatch_isTwo()
    {
        assertEquals(2, SurfaceShapeFillShader.PATTERN_CROSSHATCH);
    }

    @Test
    public void patternDots_isThree()
    {
        assertEquals(3, SurfaceShapeFillShader.PATTERN_DOTS);
    }

    @Test
    public void patternConstants_areDistinct()
    {
        int[] modes = {
            SurfaceShapeFillShader.PATTERN_NONE,
            SurfaceShapeFillShader.PATTERN_HATCH,
            SurfaceShapeFillShader.PATTERN_CROSSHATCH,
            SurfaceShapeFillShader.PATTERN_DOTS
        };
        for (int i = 0; i < modes.length; i++)
            for (int j = i + 1; j < modes.length; j++)
                assertNotEquals("pattern constants must be distinct", modes[i], modes[j]);
    }

    // -----------------------------------------------------------------------
    // Initial state
    // -----------------------------------------------------------------------

    @Test
    public void isValid_falseBeforeInit()
    {
        assertFalse("SurfaceShapeFillShader must not be valid before init()", new SurfaceShapeFillShader().isValid());
    }

    @Test
    public void positionAttribLocation_negativeOneBeforeInit() throws Exception
    {
        SurfaceShapeFillShader shader = new SurfaceShapeFillShader();
        Field f = SurfaceShapeFillShader.class.getDeclaredField("positionAttribLocation");
        f.setAccessible(true);
        assertEquals(-1, f.getInt(shader));
    }

    @Test
    public void texCoordAttribLocation_negativeOneBeforeInit() throws Exception
    {
        SurfaceShapeFillShader shader = new SurfaceShapeFillShader();
        Field f = SurfaceShapeFillShader.class.getDeclaredField("texCoordAttribLocation");
        f.setAccessible(true);
        assertEquals(-1, f.getInt(shader));
    }

    @Test
    public void texCoordAttribEnabled_falseBeforeInit() throws Exception
    {
        SurfaceShapeFillShader shader = new SurfaceShapeFillShader();
        Field f = SurfaceShapeFillShader.class.getDeclaredField("texCoordAttribEnabled");
        f.setAccessible(true);
        assertFalse((boolean) f.get(shader));
    }

    @Test
    public void getPositionAttribLocation_negativeOneBeforeInit()
    {
        assertEquals(-1, new SurfaceShapeFillShader().getPositionAttribLocation());
    }

    @Test
    public void getTexCoordAttribLocation_negativeOneBeforeInit()
    {
        assertEquals(-1, new SurfaceShapeFillShader().getTexCoordAttribLocation());
    }

    // -----------------------------------------------------------------------
    // GLSL vertex source
    // -----------------------------------------------------------------------

    @Test
    public void vertexSource_version130() throws Exception
    {
        assertTrue(getSource("VERTEX_SOURCE").contains("#version 130"));
    }

    @Test
    public void vertexSource_declares_u_mvp() throws Exception
    {
        String src = getSource("VERTEX_SOURCE");
        assertTrue("vertex shader must declare u_mvp", src.contains("uniform mat4 u_mvp"));
        assertTrue("vertex shader must use u_mvp for gl_Position", src.contains("u_mvp *"));
    }

    @Test
    public void vertexSource_declares_a_position() throws Exception
    {
        assertTrue(getSource("VERTEX_SOURCE").contains("in vec2 a_position"));
    }

    @Test
    public void vertexSource_declares_a_texCoord() throws Exception
    {
        assertTrue(getSource("VERTEX_SOURCE").contains("in vec2 a_texCoord"));
    }

    @Test
    public void vertexSource_declares_u_texMatrix() throws Exception
    {
        assertTrue(getSource("VERTEX_SOURCE").contains("uniform mat4 u_texMatrix"));
    }

    @Test
    public void vertexSource_branches_on_u_useTexture() throws Exception
    {
        String src = getSource("VERTEX_SOURCE");
        assertTrue("vertex shader must branch for mode 1 (computed tex)", src.contains("u_useTexture == 1"));
        assertTrue("vertex shader must branch for mode 2 (explicit tex)", src.contains("u_useTexture == 2"));
    }

    // -----------------------------------------------------------------------
    // GLSL fragment source
    // -----------------------------------------------------------------------

    @Test
    public void fragmentSource_version130() throws Exception
    {
        assertTrue(getSource("FRAGMENT_SOURCE").contains("#version 130"));
    }

    @Test
    public void fragmentSource_explicit_fragColor() throws Exception
    {
        String src = getSource("FRAGMENT_SOURCE");
        assertTrue("fragment shader must use explicit out vec4 fragColor",
            src.contains("out vec4 fragColor"));
        assertFalse("fragment shader must not assign to deprecated gl_FragColor",
            src.contains("gl_FragColor ="));
    }

    @Test
    public void fragmentSource_declares_all_uniforms() throws Exception
    {
        String src = getSource("FRAGMENT_SOURCE");
        assertTrue(src.contains("uniform vec4 u_color"));
        assertTrue(src.contains("uniform int u_useTexture"));
        assertTrue(src.contains("uniform sampler2D u_texture"));
        assertTrue(src.contains("uniform int u_patternMode"));
        assertTrue(src.contains("uniform float u_patternScale"));
        assertTrue(src.contains("uniform float u_patternLineWidth"));
        assertTrue(src.contains("uniform float u_patternAngle"));
    }

    @Test
    public void fragmentSource_handles_patternMode_hatch() throws Exception
    {
        String src = getSource("FRAGMENT_SOURCE");
        // Mode 1: hatch uses angle-based line distance with mod()
        assertTrue("fragment shader must handle hatch mode", src.contains("u_patternMode == 1"));
        assertTrue("hatch must use u_patternAngle", src.contains("u_patternAngle"));
        assertTrue("hatch must use radians()", src.contains("radians("));
    }

    @Test
    public void fragmentSource_handles_patternMode_crosshatch() throws Exception
    {
        String src = getSource("FRAGMENT_SOURCE");
        assertTrue("fragment shader must handle crosshatch mode", src.contains("u_patternMode == 2"));
    }

    @Test
    public void fragmentSource_handles_patternMode_dots() throws Exception
    {
        String src = getSource("FRAGMENT_SOURCE");
        assertTrue("fragment shader must handle dots mode", src.contains("u_patternMode == 3"));
        assertTrue("dots mode must use length()", src.contains("length("));
    }

    @Test
    public void fragmentSource_uses_discard_for_patterns() throws Exception
    {
        // All three procedural pattern modes use discard to punch holes
        assertTrue("fragment shader must use discard for pattern fill",
            getSource("FRAGMENT_SOURCE").contains("discard"));
    }

    // -----------------------------------------------------------------------
    // Pattern mode semantics (pure Java)
    // -----------------------------------------------------------------------

    @Test
    public void patternNone_skipsProceduralPath()
    {
        // When patternMode == PATTERN_NONE (0), the GLSL fragment falls through to solid color.
        // Verify the constant matches the GLSL branch guard (u_patternMode > 0).
        assertFalse("PATTERN_NONE must not be > 0", SurfaceShapeFillShader.PATTERN_NONE > 0);
    }

    @Test
    public void proceduralPatterns_areGreaterThanZero()
    {
        assertTrue(SurfaceShapeFillShader.PATTERN_HATCH      > 0);
        assertTrue(SurfaceShapeFillShader.PATTERN_CROSSHATCH > 0);
        assertTrue(SurfaceShapeFillShader.PATTERN_DOTS       > 0);
    }
}
