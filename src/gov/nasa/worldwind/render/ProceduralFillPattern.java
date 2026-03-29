/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — Descriptor for procedural fill patterns rendered entirely in the GLSL
 * fragment shader without any texture. Used with AbstractSurfaceShape.setFillPattern().
 */
package gov.nasa.worldwind.render;

/**
 * Describes a procedural fill pattern rendered by {@link gov.nasa.worldwind.render.shaders.SurfaceShapeFillShader}
 * entirely in GLSL without any external texture. Patterns are computed from geographic position
 * (degrees offset from the shape's reference point), so they tile correctly across FBO tile
 * boundaries and remain geographically stable as the camera moves.
 * <p>
 * Create instances with the static factory methods:
 * <pre>{@code
 *   shape.setFillPattern(ProceduralFillPattern.hatch(0.2f, 0.02f, 45f));
 *   shape.setFillPattern(ProceduralFillPattern.crosshatch(0.15f, 0.015f));
 *   shape.setFillPattern(ProceduralFillPattern.dots(0.2f, 0.5f));
 * }</pre>
 *
 * @param mode       Pattern type: {@link #HATCH}, {@link #CROSSHATCH}, or {@link #DOTS}
 * @param scale      Pattern repeat size in degrees. Smaller values produce denser patterns.
 * @param lineWidth  Line width for hatch/crosshatch, or dot radius factor for dots (0–1 range
 *                   relative to scale). Larger values produce thicker lines or larger dots.
 * @param angle      Rotation angle in degrees for hatch patterns. 0 = horizontal, 45 = diagonal.
 *                   Ignored for crosshatch and dots.
 */
public record ProceduralFillPattern(int mode, float scale, float lineWidth, float angle)
{
    /** Diagonal or straight line hatch fill. */
    public static final int HATCH = 1;
    /** Two-direction hatch (horizontal + vertical) fill. */
    public static final int CROSSHATCH = 2;
    /** Regular grid of dots fill. */
    public static final int DOTS = 3;

    /**
     * Creates a hatch (single-direction lines) pattern.
     *
     * @param scale     repeat size in degrees (e.g. 0.2 for lines every 0.2°)
     * @param lineWidth line width in degrees (e.g. 0.02 for thin lines)
     * @param angle     rotation angle in degrees (0 = horizontal, 45 = diagonal)
     */
    public static ProceduralFillPattern hatch(float scale, float lineWidth, float angle)
    {
        return new ProceduralFillPattern(HATCH, scale, lineWidth, angle);
    }

    /**
     * Creates a crosshatch (two perpendicular sets of lines) pattern.
     *
     * @param scale     repeat size in degrees between each set of lines
     * @param lineWidth line width in degrees
     */
    public static ProceduralFillPattern crosshatch(float scale, float lineWidth)
    {
        return new ProceduralFillPattern(CROSSHATCH, scale, lineWidth, 0f);
    }

    /**
     * Creates a dot grid pattern.
     *
     * @param scale      spacing between dot centers in degrees
     * @param radiusFactor dot radius as a fraction of scale/2 (0.0–1.0). 0.5 = half the cell radius.
     */
    public static ProceduralFillPattern dots(float scale, float radiusFactor)
    {
        return new ProceduralFillPattern(DOTS, scale, radiusFactor, 0f);
    }
}
