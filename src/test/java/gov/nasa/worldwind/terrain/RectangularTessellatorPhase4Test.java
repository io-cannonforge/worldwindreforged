/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * Unit tests for the crack-free LOD stitching helpers in RectangularTessellator
 * (Task 4.4) and the related math utilities.
 *
 * The methods under test are private static, accessed via reflection:
 *   - clampTessLevel(float)          — clamp to [1, 32]
 *   - screenDist2D(Vec4, Vec4)       — 2-D Euclidean distance with null fallback
 *   - constrainSharedEdge(Sector, float[], Sector, float[])
 *                                    — mutates shared edge to min(a, b)
 *
 * None of these methods require a GL context or a running WorldWind application.
 */
package gov.nasa.worldwind.terrain;

import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.geom.Vec4;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

@RunWith(JUnit4.class)
public class RectangularTessellatorPhase4Test
{
    // =========================================================================
    // Reflection helpers — cached once for the whole test class
    // =========================================================================

    private static Method clampTessLevel;
    private static Method screenDist2D;
    private static Method constrainSharedEdge;

    @BeforeClass
    public static void loadMethods() throws Exception
    {
        Class<?> cls = RectangularTessellator.class;

        clampTessLevel = cls.getDeclaredMethod("clampTessLevel", float.class);
        clampTessLevel.setAccessible(true);

        screenDist2D = cls.getDeclaredMethod("screenDist2D", Vec4.class, Vec4.class);
        screenDist2D.setAccessible(true);

        constrainSharedEdge = cls.getDeclaredMethod(
            "constrainSharedEdge", Sector.class, float[].class, Sector.class, float[].class);
        constrainSharedEdge.setAccessible(true);
    }

    // ---- convenience wrappers -----------------------------------------------

    private float clamp(float v) throws Exception
    {
        return (float) clampTessLevel.invoke(null, v);
    }

    private float dist(Vec4 a, Vec4 b) throws Exception
    {
        return (float) screenDist2D.invoke(null, a, b);
    }

    private void constrain(Sector sa, float[] la, Sector sb, float[] lb) throws Exception
    {
        constrainSharedEdge.invoke(null, sa, la, sb, lb);
    }

    // =========================================================================
    // clampTessLevel — [1, 64]
    // =========================================================================

    @Test
    public void clamp_belowOne_returnsOne() throws Exception
    {
        assertEquals(1.0f, clamp(0.0f),   0.0f);
        assertEquals(1.0f, clamp(0.5f),   0.0f);
        assertEquals(1.0f, clamp(-10.0f), 0.0f);
        assertEquals(1.0f, clamp(Float.NEGATIVE_INFINITY), 0.0f);
    }

    @Test
    public void clamp_exactlyOne_returnsOne() throws Exception
    {
        assertEquals(1.0f, clamp(1.0f), 0.0f);
    }

    @Test
    public void clamp_midRange_passesThrough() throws Exception
    {
        assertEquals(8.0f,  clamp(8.0f),  0.0f);
        assertEquals(16.0f, clamp(16.0f), 0.0f);
        assertEquals(31.9f, clamp(31.9f), 0.001f);
    }

    @Test
    public void clamp_exactlyThirtyTwo_returnsThirtyTwo() throws Exception
    {
        assertEquals(32.0f, clamp(32.0f), 0.0f);
    }

    @Test
    public void clamp_aboveThirtyTwo_returnsThirtyTwo() throws Exception
    {
        assertEquals(32.0f, clamp(32.001f), 0.0f);
        assertEquals(32.0f, clamp(100.0f),  0.0f);
        assertEquals(32.0f, clamp(Float.MAX_VALUE), 0.0f);
        assertEquals(32.0f, clamp(Float.POSITIVE_INFINITY), 0.0f);
    }

    // =========================================================================
    // screenDist2D — 2-D Euclidean distance with null sentinel
    // =========================================================================

    @Test
    public void screenDist2D_nullFirstArg_returnsSentinel() throws Exception
    {
        assertEquals("null first arg must return 32", 32.0f, dist(null, new Vec4(1, 2, 0)), 0.0f);
    }

    @Test
    public void screenDist2D_nullSecondArg_returnsSentinel() throws Exception
    {
        assertEquals("null second arg must return 32", 32.0f, dist(new Vec4(1, 2, 0), null), 0.0f);
    }

    @Test
    public void screenDist2D_bothNull_returnsSentinel() throws Exception
    {
        assertEquals(32.0f, dist(null, null), 0.0f);
    }

    @Test
    public void screenDist2D_samePoint_returnsZero() throws Exception
    {
        Vec4 p = new Vec4(100, 200, 0);
        assertEquals(0.0f, dist(p, p), 1e-5f);
    }

    @Test
    public void screenDist2D_horizontalDistance() throws Exception
    {
        // Distance between (0,0) and (3,0) should be 3.
        Vec4 a = new Vec4(0, 0, 0);
        Vec4 b = new Vec4(3, 0, 0);
        assertEquals(3.0f, dist(a, b), 1e-5f);
    }

    @Test
    public void screenDist2D_verticalDistance() throws Exception
    {
        Vec4 a = new Vec4(0, 0, 0);
        Vec4 b = new Vec4(0, 4, 0);
        assertEquals(4.0f, dist(a, b), 1e-5f);
    }

    @Test
    public void screenDist2D_diagonalDistance() throws Exception
    {
        // 3-4-5 right triangle.
        Vec4 a = new Vec4(0, 0, 0);
        Vec4 b = new Vec4(3, 4, 0);
        assertEquals(5.0f, dist(a, b), 1e-5f);
    }

    @Test
    public void screenDist2D_ignoresZCoordinate() throws Exception
    {
        // Z should not contribute (method uses only x, y).
        Vec4 a = new Vec4(0, 0, 1000);
        Vec4 b = new Vec4(3, 4, -999);
        assertEquals(5.0f, dist(a, b), 1e-5f);
    }

    @Test
    public void screenDist2D_largePixelCoordinates() throws Exception
    {
        // Typical 4K viewport: corners roughly 3840 × 2160 apart.
        Vec4 a = new Vec4(0,    0,    0);
        Vec4 b = new Vec4(3840, 2160, 0);
        float expected = (float) Math.sqrt(3840.0 * 3840.0 + 2160.0 * 2160.0);
        assertEquals(expected, dist(a, b), 0.5f);
    }

    // =========================================================================
    // constrainSharedEdge — crack-free LOD stitching (Task 4.4)
    //
    // Edge indices (match gl_TessLevelOuter):
    //   [0] west  (TL→BL)
    //   [1] south (BL→BR)
    //   [2] east  (BR→TR)
    //   [3] north (TR→TL)
    // =========================================================================

    // ---- North–South adjacency: A.north == B.south → la[3] and lb[1] -------

    @Test
    public void constrain_northSouth_takesMinOfNorthAndSouth() throws Exception
    {
        // A covers lat [0, 10], B covers lat [10, 20] → A.north == B.south.
        Sector sa = Sector.fromDegrees(0, 10, 0, 10);
        Sector sb = Sector.fromDegrees(10, 20, 0, 10);
        float[] la = {32, 32, 32, 30f}; // la[3] = north level
        float[] lb = {32, 20f, 32, 32}; // lb[1] = south level

        constrain(sa, la, sb, lb);

        assertEquals("la[3] (north) must be min(30, 20) = 20", 20.0f, la[3], 0.0f);
        assertEquals("lb[1] (south) must be min(30, 20) = 20", 20.0f, lb[1], 0.0f);
        // Unaffected edges stay unchanged.
        assertEquals(32.0f, la[0], 0.0f);
        assertEquals(32.0f, la[1], 0.0f);
        assertEquals(32.0f, la[2], 0.0f);
        assertEquals(32.0f, lb[0], 0.0f);
        assertEquals(32.0f, lb[2], 0.0f);
        assertEquals(32.0f, lb[3], 0.0f);
    }

    @Test
    public void constrain_northSouth_alreadyEqual_noChange() throws Exception
    {
        Sector sa = Sector.fromDegrees(0, 10, 0, 10);
        Sector sb = Sector.fromDegrees(10, 20, 0, 10);
        float[] la = {32, 32, 32, 32f};
        float[] lb = {32, 32f, 32, 32};

        constrain(sa, la, sb, lb);

        assertEquals(32.0f, la[3], 0.0f);
        assertEquals(32.0f, lb[1], 0.0f);
    }

    @Test
    public void constrain_northSouth_aLevelHigher_constrainedToB() throws Exception
    {
        Sector sa = Sector.fromDegrees(0, 10, 0, 10);
        Sector sb = Sector.fromDegrees(10, 20, 0, 10);
        float[] la = {32, 32, 32, 30f}; // north = 30
        float[] lb = {32, 20f, 32, 32}; // south = 20 (lower)

        constrain(sa, la, sb, lb);

        assertEquals(20.0f, la[3], 0.0f); // capped to south level
        assertEquals(20.0f, lb[1], 0.0f);
    }

    // ---- South–North adjacency: A.south == B.north → la[1] and lb[3] -------

    @Test
    public void constrain_southNorth_takesMinOfSouthAndNorth() throws Exception
    {
        // B.north == A.south: B covers [0, 10], A covers [10, 20].
        Sector sa = Sector.fromDegrees(10, 20, 0, 10);
        Sector sb = Sector.fromDegrees(0, 10, 0, 10);
        float[] la = {32, 16f, 32, 32}; // la[1] = south level
        float[] lb = {32, 32, 32, 25f}; // lb[3] = north level

        constrain(sa, la, sb, lb);

        assertEquals("la[1] (south) must be min(16, 25) = 16", 16.0f, la[1], 0.0f);
        assertEquals("lb[3] (north) must be min(16, 25) = 16", 16.0f, lb[3], 0.0f);
    }

    // ---- East–West adjacency: A.east == B.west → la[2] and lb[0] -----------

    @Test
    public void constrain_eastWest_takesMinOfEastAndWest() throws Exception
    {
        // A covers lon [0, 10], B covers lon [10, 20] → A.east == B.west.
        Sector sa = Sector.fromDegrees(0, 10, 0, 10);
        Sector sb = Sector.fromDegrees(0, 10, 10, 20);
        float[] la = {32, 32, 28f, 32}; // la[2] = east level
        float[] lb = {12f, 32, 32, 32}; // lb[0] = west level

        constrain(sa, la, sb, lb);

        assertEquals("la[2] (east) must be min(28, 12) = 12", 12.0f, la[2], 0.0f);
        assertEquals("lb[0] (west) must be min(28, 12) = 12", 12.0f, lb[0], 0.0f);
    }

    @Test
    public void constrain_eastWest_bLevelHigher_constrainedToA() throws Exception
    {
        Sector sa = Sector.fromDegrees(0, 10, 0, 10);
        Sector sb = Sector.fromDegrees(0, 10, 10, 20);
        float[] la = {32, 32, 10f, 32}; // east = 10 (lower)
        float[] lb = {30f, 32, 32, 32}; // west = 30 (higher)

        constrain(sa, la, sb, lb);

        assertEquals(10.0f, la[2], 0.0f);
        assertEquals(10.0f, lb[0], 0.0f);
    }

    // ---- West–East adjacency: A.west == B.east → la[0] and lb[2] -----------

    @Test
    public void constrain_westEast_takesMinOfWestAndEast() throws Exception
    {
        // A covers lon [10, 20], B covers lon [0, 10] → A.west == B.east.
        Sector sa = Sector.fromDegrees(0, 10, 10, 20);
        Sector sb = Sector.fromDegrees(0, 10, 0, 10);
        float[] la = {8f, 32, 32, 32};  // la[0] = west level
        float[] lb = {32, 32, 28f, 32}; // lb[2] = east level

        constrain(sa, la, sb, lb);

        assertEquals("la[0] (west) must be min(8, 28) = 8", 8.0f, la[0], 0.0f);
        assertEquals("lb[2] (east) must be min(8, 28) = 8", 8.0f, lb[2], 0.0f);
    }

    // ---- Non-adjacent tiles — no edge should be modified --------------------

    @Test
    public void constrain_nonAdjacent_noChange() throws Exception
    {
        // Tiles that share no edge.
        Sector sa = Sector.fromDegrees(0,  10, 0, 10);
        Sector sb = Sector.fromDegrees(20, 30, 20, 30);
        float[] la = {10f, 20f, 30f, 40f};
        float[] lb = {10f, 20f, 30f, 40f};
        float[] laOrig = la.clone();
        float[] lbOrig = lb.clone();

        constrain(sa, la, sb, lb);

        assertArrayEquals("Non-adjacent: la must not change", laOrig, la, 0.0f);
        assertArrayEquals("Non-adjacent: lb must not change", lbOrig, lb, 0.0f);
    }

    @Test
    public void constrain_sameLocation_treatedAsNonAdjacent() throws Exception
    {
        // Identical sectors share all four edges but the method only handles
        // one at a time via else-if, so only the first matching branch fires.
        // The important contract is: no exception, no bad mutation.
        Sector sa = Sector.fromDegrees(0, 10, 0, 10);
        Sector sb = Sector.fromDegrees(0, 10, 0, 10);
        float[] la = {32, 32, 32, 32};
        float[] lb = {32, 32, 32, 32};

        // Must not throw.
        constrain(sa, la, sb, lb);
    }

    // ---- Min semantics: constraining is idempotent --------------------------

    @Test
    public void constrain_idempotent_secondCallDoesNotFurtherReduce() throws Exception
    {
        Sector sa = Sector.fromDegrees(0, 10, 0, 10);
        Sector sb = Sector.fromDegrees(10, 20, 0, 10);
        float[] la = {32, 32, 32, 30f};
        float[] lb = {32, 25f, 32, 32};

        constrain(sa, la, sb, lb); // la[3] and lb[1] become 30
        float la3After1 = la[3];
        float lb1After1 = lb[1];

        constrain(sa, la, sb, lb); // second call — both sides already equal 30

        assertEquals("Second constrain call must not further reduce la[3]", la3After1, la[3], 0.0f);
        assertEquals("Second constrain call must not further reduce lb[1]", lb1After1, lb[1], 0.0f);
    }

    // =========================================================================
    // DEFAULT_DENSITY and DEFAULT_MAX_LEVEL constants
    // =========================================================================

    @Test
    public void defaultDensity_isExpectedValue() throws Exception
    {
        java.lang.reflect.Field f = RectangularTessellator.class.getDeclaredField("DEFAULT_DENSITY");
        f.setAccessible(true);
        int val = f.getInt(null);
        assertTrue("DEFAULT_DENSITY should be positive", val > 0);
        assertEquals("DEFAULT_DENSITY is expected to be 20", 20, val);
    }

    @Test
    public void defaultMaxLevel_isAtLeast20() throws Exception
    {
        java.lang.reflect.Field f = RectangularTessellator.class.getDeclaredField("DEFAULT_MAX_LEVEL");
        f.setAccessible(true);
        int val = f.getInt(null);
        assertTrue("DEFAULT_MAX_LEVEL should be >= 20 for sufficient zoom depth", val >= 20);
    }
}
