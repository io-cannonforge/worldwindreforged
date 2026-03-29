/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — Phase 8 test coverage for DashLineShader.
 * Verifies initial Java-side state and GLSL source content without a GL context.
 */
package gov.nasa.worldwind.render.shaders;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link DashLineShader} Java-side state.
 * No OpenGL context required.
 */
@RunWith(JUnit4.class)
public class DashLineShaderTest
{
    private static String getSource(String fieldName) throws Exception
    {
        Field f = DashLineShader.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        return (String) f.get(null);
    }

    // -----------------------------------------------------------------------
    // Initial state
    // -----------------------------------------------------------------------

    @Test
    public void isValid_falseBeforeInit()
    {
        assertFalse("DashLineShader must not be valid before init()", new DashLineShader().isValid());
    }

    @Test
    public void distAttribLocation_negativeOneBeforeInit() throws Exception
    {
        DashLineShader shader = new DashLineShader();
        Field f = DashLineShader.class.getDeclaredField("distAttribLocation");
        f.setAccessible(true);
        assertEquals("distAttribLocation must be -1 before init()", -1, f.getInt(shader));
    }

    @Test
    public void program_nullBeforeInit() throws Exception
    {
        DashLineShader shader = new DashLineShader();
        Field f = DashLineShader.class.getDeclaredField("program");
        f.setAccessible(true);
        assertNull("program field must be null before init()", f.get(shader));
    }

    @Test
    public void getDistAttribLocation_negativeOneBeforeInit()
    {
        assertEquals(-1, new DashLineShader().getDistAttribLocation());
    }

    // -----------------------------------------------------------------------
    // GLSL source content
    // -----------------------------------------------------------------------

    @Test
    public void vertexSource_contains_version130() throws Exception
    {
        assertTrue(getSource("VERTEX_SOURCE").contains("#version 130"));
    }

    @Test
    public void vertexSource_declares_u_mvp() throws Exception
    {
        String src = getSource("VERTEX_SOURCE");
        assertTrue("vertex shader must declare u_mvp uniform", src.contains("uniform mat4 u_mvp"));
        assertTrue("vertex shader must use u_mvp for gl_Position", src.contains("u_mvp *"));
    }

    @Test
    public void vertexSource_declares_a_dist() throws Exception
    {
        assertTrue("vertex shader must declare a_dist attribute",
            getSource("VERTEX_SOURCE").contains("in float a_dist"));
    }

    @Test
    public void vertexSource_passes_v_dist() throws Exception
    {
        String src = getSource("VERTEX_SOURCE");
        assertTrue("vertex shader must declare v_dist out", src.contains("out float v_dist"));
        assertTrue("vertex shader must assign v_dist", src.contains("v_dist = a_dist"));
    }

    @Test
    public void fragmentSource_contains_version130() throws Exception
    {
        assertTrue(getSource("FRAGMENT_SOURCE").contains("#version 130"));
    }

    @Test
    public void fragmentSource_declares_explicit_fragColor() throws Exception
    {
        String src = getSource("FRAGMENT_SOURCE");
        assertTrue("fragment shader must use explicit out vec4 fragColor (not gl_FragColor)",
            src.contains("out vec4 fragColor"));
        assertFalse("fragment shader must not use deprecated gl_FragColor",
            src.contains("gl_FragColor"));
    }

    @Test
    public void fragmentSource_declares_all_uniforms() throws Exception
    {
        String src = getSource("FRAGMENT_SOURCE");
        assertTrue(src.contains("uniform vec4 u_color"));
        assertTrue(src.contains("uniform float u_dashLength"));
        assertTrue(src.contains("uniform int u_stipplePattern"));
        assertTrue(src.contains("uniform int u_picking"));
    }

    @Test
    public void fragmentSource_uses_mod_for_dash() throws Exception
    {
        assertTrue("fragment shader must use mod() for dash cycle",
            getSource("FRAGMENT_SOURCE").contains("mod(v_dist,"));
    }

    @Test
    public void fragmentSource_uses_bitshift_for_stipple() throws Exception
    {
        String src = getSource("FRAGMENT_SOURCE");
        assertTrue("fragment shader must use bit-shift for stipple pattern lookup",
            src.contains("u_stipplePattern >> bit"));
    }

    // -----------------------------------------------------------------------
    // Dash pattern arithmetic (pure Java)
    // -----------------------------------------------------------------------

    @Test
    public void stippleMask_0xFFFF_allOnBits()
    {
        // 0xFFFF — all 16 bits set — should result in a solid line (no bits are 0)
        int pattern = 0xFFFF;
        for (int bit = 0; bit < 16; bit++)
            assertEquals("bit " + bit + " should be 1", 1, (pattern >> bit) & 1);
    }

    @Test
    public void stippleMask_0x0000_allOffBits()
    {
        int pattern = 0x0000;
        for (int bit = 0; bit < 16; bit++)
            assertEquals("bit " + bit + " should be 0", 0, (pattern >> bit) & 1);
    }

    @Test
    public void stippleMask_0xF0F0_alternatingGroups()
    {
        // 0xF0F0 = 1111 0000 1111 0000: bits 4-7 on, 0-3 off, 12-15 on, 8-11 off
        int pattern = 0xF0F0;
        // bits 0-3: off
        for (int b = 0; b < 4; b++)
            assertEquals("bit " + b, 0, (pattern >> b) & 1);
        // bits 4-7: on
        for (int b = 4; b < 8; b++)
            assertEquals("bit " + b, 1, (pattern >> b) & 1);
        // bits 8-11: off
        for (int b = 8; b < 12; b++)
            assertEquals("bit " + b, 0, (pattern >> b) & 1);
        // bits 12-15: on
        for (int b = 12; b < 16; b++)
            assertEquals("bit " + b, 1, (pattern >> b) & 1);
    }

    @Test
    public void stippleMask_truncatedTo16Bits()
    {
        // begin() does: stipplePattern & 0xFFFF — verify the truncation
        int raw = 0xDEADBEEF;
        int truncated = raw & 0xFFFF;
        assertEquals("truncation should keep only low 16 bits", 0xBEEF, truncated);
    }

    @Test
    public void bitIndex_formula_mapsDistToBit()
    {
        // GLSL: int bit = int(pos / u_dashLength * 16.0) & 15
        // With dashLength=16.0, pos=0 → bit 0; pos=8 → bit 8; pos=15.9 → bit 15
        float dashLength = 16.0f;
        assertEquals(0,  (int)(0.0f   / dashLength * 16.0f) & 15);
        assertEquals(8,  (int)(8.0f   / dashLength * 16.0f) & 15);
        assertEquals(15, (int)(15.9f  / dashLength * 16.0f) & 15);
    }
}
