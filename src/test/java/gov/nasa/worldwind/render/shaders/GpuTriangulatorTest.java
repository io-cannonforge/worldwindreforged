/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — Phase 8 test coverage for GpuTriangulator CPU path.
 * No GL context required; tests triangulateCPU(), bridgeHoles(), and generateOutlineIndices().
 */
package gov.nasa.worldwind.render.shaders;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GpuTriangulator} CPU-side methods.
 * No OpenGL context is required — all tested methods are pure Java.
 */
@RunWith(JUnit4.class)
public class GpuTriangulatorTest
{
    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Build a flat vertex array from alternating x,y pairs. */
    private static float[] verts(float... xy)
    {
        return xy;
    }

    /** Build an identity ring [0, 1, 2, ... n-1]. */
    private static int[] ring(int n)
    {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = i;
        return r;
    }

    /** Verify that the index array has no out-of-range indices. */
    private static void assertValidIndices(int[] indices, int vertexCount)
    {
        for (int idx : indices)
        {
            assertTrue("index " + idx + " out of range [0," + vertexCount + ")", idx >= 0 && idx < vertexCount);
        }
    }

    /** Sum signed triangle areas; should be positive for CCW winding. */
    private static double signedArea(float[] verts, int[] tris)
    {
        double area = 0;
        for (int i = 0; i < tris.length; i += 3)
        {
            float ax = verts[tris[i]     * 2], ay = verts[tris[i]     * 2 + 1];
            float bx = verts[tris[i + 1] * 2], by = verts[tris[i + 1] * 2 + 1];
            float cx = verts[tris[i + 2] * 2], cy = verts[tris[i + 2] * 2 + 1];
            area += (bx - ax) * (double)(cy - ay) - (cx - ax) * (double)(by - ay);
        }
        return area;
    }

    // -----------------------------------------------------------------------
    // triangulateCPU — basic polygon types
    // -----------------------------------------------------------------------

    @Test
    public void testTriangle()
    {
        // Simplest possible polygon — already a triangle
        float[] v = verts(0, 0,  1, 0,  0.5f, 1);
        int[] r = ring(3);
        int[] tris = GpuTriangulator.triangulateCPU(v, r);
        assertEquals("triangle should produce 3 indices", 3, tris.length);
        assertValidIndices(tris, 3);
    }

    @Test
    public void testConvexQuad()
    {
        // CCW unit square: should produce 2 triangles = 6 indices
        float[] v = verts(0, 0,  1, 0,  1, 1,  0, 1);
        int[] r = ring(4);
        int[] tris = GpuTriangulator.triangulateCPU(v, r);
        assertEquals("quad should produce 6 indices", 6, tris.length);
        assertValidIndices(tris, 4);
        assertTrue("total area should be positive", signedArea(v, tris) > 0);
    }

    @Test
    public void testConvexPentagon()
    {
        // Regular CCW pentagon approximation
        float[] v = new float[10];
        for (int i = 0; i < 5; i++)
        {
            double angle = Math.PI / 2 + i * 2 * Math.PI / 5;
            v[i * 2]     = (float) Math.cos(angle);
            v[i * 2 + 1] = (float) Math.sin(angle);
        }
        int[] r = ring(5);
        int[] tris = GpuTriangulator.triangulateCPU(v, r);
        assertEquals("pentagon should produce 9 indices", 9, tris.length);
        assertValidIndices(tris, 5);
    }

    @Test
    public void testConcavePolygon()
    {
        // L-shape (concave, 6 vertices, CCW)
        // (0,0) → (2,0) → (2,1) → (1,1) → (1,2) → (0,2)
        float[] v = verts(0,0,  2,0,  2,1,  1,1,  1,2,  0,2);
        int[] r = ring(6);
        int[] tris = GpuTriangulator.triangulateCPU(v, r);
        assertEquals("L-shape should produce 12 indices", 12, tris.length);
        assertValidIndices(tris, 6);
        // Total area of L-shape = 3; signed area from triangles should match
        assertTrue("signed area should be positive (CCW)", signedArea(v, tris) > 0);
    }

    @Test
    public void testDegenerateFewerThan3Vertices()
    {
        float[] v = verts(0, 0,  1, 0);
        int[] r = {0, 1};
        int[] tris = GpuTriangulator.triangulateCPU(v, r);
        assertEquals("degenerate (2 verts) should return empty", 0, tris.length);
    }

    @Test
    public void testDegenerateCollinear()
    {
        // Three collinear points — area ~ 0, no valid ear but shouldn't throw
        float[] v = verts(0, 0,  1, 0,  2, 0);
        int[] r = ring(3);
        // Should not throw; result may be empty or a degenerate triangle
        int[] tris = GpuTriangulator.triangulateCPU(v, r);
        assertNotNull(tris);
    }

    @Test
    public void testLargeConvexPolygon()
    {
        // 32-vertex circle — stress test for ear-clipping loop
        int n = 32;
        float[] v = new float[n * 2];
        for (int i = 0; i < n; i++)
        {
            double angle = i * 2 * Math.PI / n;
            v[i * 2]     = (float) Math.cos(angle);
            v[i * 2 + 1] = (float) Math.sin(angle);
        }
        int[] r = ring(n);
        int[] tris = GpuTriangulator.triangulateCPU(v, r);
        assertEquals("n-gon should produce (n-2)*3 indices", (n - 2) * 3, tris.length);
        assertValidIndices(tris, n);
    }

    // -----------------------------------------------------------------------
    // triangulateCPU — non-identity ring (indices into a shared vertex pool)
    // -----------------------------------------------------------------------

    @Test
    public void testNonIdentityRing()
    {
        // Vertices at indices 5,6,7,8 inside a larger pool (10 vertices)
        float[] v = new float[20]; // 10 vertices
        // Put a CCW quad at positions 5-8
        v[5*2] = 0; v[5*2+1] = 0;
        v[6*2] = 1; v[6*2+1] = 0;
        v[7*2] = 1; v[7*2+1] = 1;
        v[8*2] = 0; v[8*2+1] = 1;
        int[] r = {5, 6, 7, 8};
        int[] tris = GpuTriangulator.triangulateCPU(v, r);
        assertEquals("non-identity quad should produce 6 indices", 6, tris.length);
        assertValidIndices(tris, 10);
    }

    // -----------------------------------------------------------------------
    // bridgeHoles
    // -----------------------------------------------------------------------

    @Test
    public void testBridgeHolesNone()
    {
        // No holes — should return identity ring [outerStart, outerStart+1, ...]
        float[] v = verts(0,0,  4,0,  4,4,  0,4);
        int[] merged = GpuTriangulator.bridgeHoles(v, 0, 4, null, null);
        assertEquals("no holes: merged ring length == outer count", 4, merged.length);
        for (int i = 0; i < 4; i++)
            assertEquals(i, merged[i]);
    }

    @Test
    public void testBridgeHolesEmptyArray()
    {
        float[] v = verts(0,0,  4,0,  4,4,  0,4);
        int[] merged = GpuTriangulator.bridgeHoles(v, 0, 4, new int[0], new int[0]);
        assertEquals("empty hole array: merged ring length == outer count", 4, merged.length);
    }

    @Test
    public void testBridgeHolesOneHole()
    {
        // Outer square [0,0]→[4,0]→[4,4]→[0,4] (CCW)
        // Inner square (hole) [1,1]→[1,3]→[3,3]→[3,1] (CW, inside outer)
        float[] v = verts(
            0,0,  4,0,  4,4,  0,4,   // outer verts 0-3
            1,1,  1,3,  3,3,  3,1    // hole verts 4-7
        );
        int[] merged = GpuTriangulator.bridgeHoles(v, 0, 4, new int[]{4}, new int[]{4});
        // Bridge adds 2 extra indices (bridge edge repeated), so merged length = 4 + 4 + 2 = 10
        assertNotNull(merged);
        assertTrue("merged ring should be longer than outer alone", merged.length > 4);
        assertValidIndices(merged, 8);
    }

    @Test
    public void testBridgeHolesTwoHoles()
    {
        // Outer 8x1 rectangle, two 1x0.5 holes inside
        float[] v = verts(
            0,0,  8,0,  8,1,  0,1,            // outer 0-3
            1,0.25f, 2,0.25f, 2,0.75f, 1,0.75f,  // hole1 4-7
            5,0.25f, 6,0.25f, 6,0.75f, 5,0.75f   // hole2 8-11
        );
        int[] merged = GpuTriangulator.bridgeHoles(v, 0, 4,
            new int[]{4, 8}, new int[]{4, 4});
        assertNotNull(merged);
        assertTrue("two holes: merged ring longer than outer alone", merged.length > 4);
        assertValidIndices(merged, 12);

        // Result should be triangulatable
        int[] tris = GpuTriangulator.triangulateCPU(v, merged);
        assertNotNull(tris);
        assertTrue("bridged+triangulated should produce triangles", tris.length >= 3);
    }

    // -----------------------------------------------------------------------
    // generateOutlineIndices
    // -----------------------------------------------------------------------

    @Test
    public void testOutlineIndicesTriangle()
    {
        int[] ol = GpuTriangulator.generateOutlineIndices(0, 3);
        // 3 edges × 2 endpoints = 6 indices
        assertArrayEquals("triangle outline", new int[]{0,1, 1,2, 2,0}, ol);
    }

    @Test
    public void testOutlineIndicesWithOffset()
    {
        int[] ol = GpuTriangulator.generateOutlineIndices(5, 3);
        assertArrayEquals("offset triangle outline", new int[]{5,6, 6,7, 7,5}, ol);
    }

    @Test
    public void testOutlineIndicesQuad()
    {
        int[] ol = GpuTriangulator.generateOutlineIndices(0, 4);
        assertEquals("quad outline: 8 indices", 8, ol.length);
        assertArrayEquals(new int[]{0,1, 1,2, 2,3, 3,0}, ol);
    }

    @Test
    public void testOutlineIndicesDegenerate()
    {
        assertEquals("fewer than 2 vertices: empty", 0,
            GpuTriangulator.generateOutlineIndices(0, 1).length);
        assertEquals("0 vertices: empty", 0,
            GpuTriangulator.generateOutlineIndices(0, 0).length);
    }

    @Test
    public void testOutlineIndicesCountProperty()
    {
        // n vertices → n*2 indices
        for (int n = 2; n <= 10; n++)
        {
            int[] ol = GpuTriangulator.generateOutlineIndices(0, n);
            assertEquals("outline indices for n=" + n, n * 2, ol.length);
        }
    }
}
