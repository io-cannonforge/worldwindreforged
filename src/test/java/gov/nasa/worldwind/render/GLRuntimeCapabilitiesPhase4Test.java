/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * Unit tests for Phase 4 capability flags added to GLRuntimeCapabilities:
 *   Task 4.1 — isTerrainShader{Available,Enabled}  / isUseTerrainShader()
 *   Task 4.2 — isTessellation{Available,Enabled}   / isUseTessellation()
 *   Task 4.3 — isComputeMesh{Available,Enabled}    / isUseComputeMesh()
 *
 * All tests are pure-Java (no GL context required).
 */
package gov.nasa.worldwind.render;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

@RunWith(JUnit4.class)
public class GLRuntimeCapabilitiesPhase4Test
{
    // =========================================================================
    // Task 4.1 — TerrainShader capability
    // =========================================================================

    @Test
    public void terrainShader_enabledByDefault()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        assertTrue("TerrainShader should be enabled by default", caps.isTerrainShaderEnabled());
    }

    @Test
    public void terrainShader_notAvailableBeforeInitialize()
    {
        // Available is set by initialize(GLContext); before that it defaults false.
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        assertFalse("TerrainShader should not be available before initialize()", caps.isTerrainShaderAvailable());
    }

    @Test
    public void terrainShader_useTrue_whenAvailableAndEnabled()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTerrainShaderAvailable(true);
        caps.setTerrainShaderEnabled(true);
        assertTrue(caps.isUseTerrainShader());
    }

    @Test
    public void terrainShader_useFalse_whenAvailableButDisabled()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTerrainShaderAvailable(true);
        caps.setTerrainShaderEnabled(false);
        assertFalse(caps.isUseTerrainShader());
    }

    @Test
    public void terrainShader_useFalse_whenNotAvailableButEnabled()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTerrainShaderAvailable(false);
        caps.setTerrainShaderEnabled(true);
        assertFalse(caps.isUseTerrainShader());
    }

    @Test
    public void terrainShader_useFalse_whenNeitherAvailableNorEnabled()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTerrainShaderAvailable(false);
        caps.setTerrainShaderEnabled(false);
        assertFalse(caps.isUseTerrainShader());
    }

    @Test
    public void terrainShader_setAvailableRoundtrip()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTerrainShaderAvailable(true);
        assertTrue(caps.isTerrainShaderAvailable());
        caps.setTerrainShaderAvailable(false);
        assertFalse(caps.isTerrainShaderAvailable());
    }

    @Test
    public void terrainShader_setEnabledRoundtrip()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTerrainShaderEnabled(false);
        assertFalse(caps.isTerrainShaderEnabled());
        caps.setTerrainShaderEnabled(true);
        assertTrue(caps.isTerrainShaderEnabled());
    }

    // =========================================================================
    // Task 4.2 — Tessellation capability
    // =========================================================================

    @Test
    public void tessellation_enabledByDefault()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        assertTrue("Tessellation should be enabled by default", caps.isTessellationEnabled());
    }

    @Test
    public void tessellation_notAvailableBeforeInitialize()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        assertFalse("Tessellation should not be available before initialize()", caps.isTessellationAvailable());
    }

    @Test
    public void tessellation_useTrue_whenAvailableAndEnabled()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTessellationAvailable(true);
        caps.setTessellationEnabled(true);
        assertTrue(caps.isUseTessellation());
    }

    @Test
    public void tessellation_useFalse_whenAvailableButDisabled()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTessellationAvailable(true);
        caps.setTessellationEnabled(false);
        assertFalse(caps.isUseTessellation());
    }

    @Test
    public void tessellation_useFalse_whenNotAvailableButEnabled()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTessellationAvailable(false);
        caps.setTessellationEnabled(true);
        assertFalse(caps.isUseTessellation());
    }

    @Test
    public void tessellation_useFalse_whenNeitherAvailableNorEnabled()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTessellationAvailable(false);
        caps.setTessellationEnabled(false);
        assertFalse(caps.isUseTessellation());
    }

    @Test
    public void tessellation_setAvailableRoundtrip()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTessellationAvailable(true);
        assertTrue(caps.isTessellationAvailable());
        caps.setTessellationAvailable(false);
        assertFalse(caps.isTessellationAvailable());
    }

    @Test
    public void tessellation_setEnabledRoundtrip()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTessellationEnabled(false);
        assertFalse(caps.isTessellationEnabled());
        caps.setTessellationEnabled(true);
        assertTrue(caps.isTessellationEnabled());
    }

    // =========================================================================
    // Task 4.3 — ComputeMesh capability
    // =========================================================================

    @Test
    public void computeMesh_enabledByDefault()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        assertTrue("ComputeMesh should be enabled by default", caps.isComputeMeshEnabled());
    }

    @Test
    public void computeMesh_notAvailableBeforeInitialize()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        assertFalse("ComputeMesh should not be available before initialize()", caps.isComputeMeshAvailable());
    }

    @Test
    public void computeMesh_useTrue_whenAvailableAndEnabled()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setComputeMeshAvailable(true);
        caps.setComputeMeshEnabled(true);
        assertTrue(caps.isUseComputeMesh());
    }

    @Test
    public void computeMesh_useFalse_whenAvailableButDisabled()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setComputeMeshAvailable(true);
        caps.setComputeMeshEnabled(false);
        assertFalse(caps.isUseComputeMesh());
    }

    @Test
    public void computeMesh_useFalse_whenNotAvailableButEnabled()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setComputeMeshAvailable(false);
        caps.setComputeMeshEnabled(true);
        assertFalse(caps.isUseComputeMesh());
    }

    @Test
    public void computeMesh_useFalse_whenNeitherAvailableNorEnabled()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setComputeMeshAvailable(false);
        caps.setComputeMeshEnabled(false);
        assertFalse(caps.isUseComputeMesh());
    }

    @Test
    public void computeMesh_setAvailableRoundtrip()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setComputeMeshAvailable(true);
        assertTrue(caps.isComputeMeshAvailable());
        caps.setComputeMeshAvailable(false);
        assertFalse(caps.isComputeMeshAvailable());
    }

    @Test
    public void computeMesh_setEnabledRoundtrip()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setComputeMeshEnabled(false);
        assertFalse(caps.isComputeMeshEnabled());
        caps.setComputeMeshEnabled(true);
        assertTrue(caps.isComputeMeshEnabled());
    }

    // =========================================================================
    // Feature independence and priority (Tasks 4.1–4.3)
    // =========================================================================

    @Test
    public void capabilities_areIndependent_terrainShaderDoesNotImplyTessellation()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTerrainShaderAvailable(true);
        assertFalse("Setting terrainShader available must not affect tessellation",
            caps.isTessellationAvailable());
        assertFalse("Setting terrainShader available must not affect computeMesh",
            caps.isComputeMeshAvailable());
    }

    @Test
    public void capabilities_areIndependent_tessellationDoesNotImplyComputeMesh()
    {
        // Simulate GL 4.0: tessellation yes, compute mesh no.
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTessellationAvailable(true);
        caps.setComputeMeshAvailable(false);
        assertTrue(caps.isUseTessellation());
        assertFalse(caps.isUseComputeMesh());
    }

    @Test
    public void capabilities_terrainShaderFallback_whenOnlyGL30()
    {
        // Simulate GL 3.0: terrain shader yes, tessellation and compute mesh no.
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTerrainShaderAvailable(true);
        caps.setTessellationAvailable(false);
        caps.setComputeMeshAvailable(false);
        assertTrue(caps.isUseTerrainShader());
        assertFalse(caps.isUseTessellation());
        assertFalse(caps.isUseComputeMesh());
    }

    @Test
    public void capabilities_allThreeCanBeSimultaneouslyAvailable()
    {
        // Availability is orthogonal; the renderer chooses based on priority.
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTerrainShaderAvailable(true);
        caps.setTessellationAvailable(true);
        caps.setComputeMeshAvailable(true);
        assertTrue(caps.isUseTerrainShader());
        assertTrue(caps.isUseTessellation());
        assertTrue(caps.isUseComputeMesh());
    }

    @Test
    public void capabilities_disablingOneDoesNotAffectOthers()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTerrainShaderAvailable(true);
        caps.setTessellationAvailable(true);
        caps.setComputeMeshAvailable(true);

        caps.setTessellationEnabled(false);
        assertTrue("TerrainShader use should be unaffected", caps.isUseTerrainShader());
        assertFalse("Tessellation should be disabled", caps.isUseTessellation());
        assertTrue("ComputeMesh use should be unaffected", caps.isUseComputeMesh());
    }

    @Test
    public void capabilities_disableDoesNotClearAvailability()
    {
        // Disabling a feature should not change its availability flag.
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTessellationAvailable(true);
        caps.setTessellationEnabled(false);
        assertTrue("Availability must not be cleared when feature is disabled",
            caps.isTessellationAvailable());
        assertFalse(caps.isUseTessellation());
    }

    @Test
    public void capabilities_noFeaturesUsed_whenAllDisabled()
    {
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        caps.setTerrainShaderAvailable(true);
        caps.setTessellationAvailable(true);
        caps.setComputeMeshAvailable(true);
        caps.setTerrainShaderEnabled(false);
        caps.setTessellationEnabled(false);
        caps.setComputeMeshEnabled(false);
        assertFalse(caps.isUseTerrainShader());
        assertFalse(caps.isUseTessellation());
        assertFalse(caps.isUseComputeMesh());
    }

    @Test
    public void capabilities_noFeaturesUsed_whenNoneAvailable()
    {
        // Default state: all enabled, none available.
        GLRuntimeCapabilities caps = new GLRuntimeCapabilities();
        assertFalse(caps.isUseTerrainShader());
        assertFalse(caps.isUseTessellation());
        assertFalse(caps.isUseComputeMesh());
    }
}
