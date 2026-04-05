/*
 * WorldWind Reforged — NYC Buildings 3D Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: building height-range category enum with associated display colors.
 */
package gov.nasa.worldwindx.examples.nycbuildings;

import java.awt.Color;

/**
 * Building categories based on height ranges, each with an associated display
 * color for 3D rendering. Categories follow standard architectural terminology.
 *
 * seaglassfoundry.com
 */
public enum BuildingCategory
{
    LOW_RISE(0, 15, new Color(120, 160, 200), "Low-Rise"),
    MID_RISE(15, 50, new Color(80, 140, 220), "Mid-Rise"),
    HIGH_RISE(50, 150, new Color(60, 100, 200), "High-Rise"),
    SKYSCRAPER(150, 300, new Color(200, 160, 60), "Skyscraper"),
    SUPERTALL(300, Double.MAX_VALUE, new Color(220, 80, 60), "Supertall");

    private final double minHeight;
    private final double maxHeight;
    private final Color color;
    private final String displayName;

    BuildingCategory(double minHeight, double maxHeight, Color color, String displayName)
    {
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.color = color;
        this.displayName = displayName;
    }

    public double getMinHeight() { return minHeight; }
    public double getMaxHeight() { return maxHeight; }
    public Color getColor() { return color; }
    public String getDisplayName() { return displayName; }

    /** Slightly darker variant for building side faces to give visual depth. */
    public Color getSideColor()
    {
        return new Color(
            Math.max(0, color.getRed() - 40),
            Math.max(0, color.getGreen() - 40),
            Math.max(0, color.getBlue() - 40));
    }

    /**
     * Classify a building by its height in metres.
     *
     * @param heightMeters the building height
     * @return the matching category
     */
    public static BuildingCategory fromHeight(double heightMeters)
    {
        if (heightMeters >= 300) return SUPERTALL;
        if (heightMeters >= 150) return SKYSCRAPER;
        if (heightMeters >= 50) return HIGH_RISE;
        if (heightMeters >= 15) return MID_RISE;
        return LOW_RISE;
    }
}
