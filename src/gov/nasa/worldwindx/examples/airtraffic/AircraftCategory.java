/*
 * WorldWind Reforged — Air Traffic Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: aircraft category enum with ADS-B emitter category mapping.
 */
package gov.nasa.worldwindx.examples.airtraffic;

import java.awt.Color;

/**
 * Aircraft categories mapped from ADS-B emitter category codes, each with an
 * associated display color and icon.
 *
 * seaglassfoundry.com
 */
public enum AircraftCategory
{
    HEAVY_JET(new Color(30, 144, 255), "heavy.png", "Heavy Jet"),
    LARGE_JET(new Color(65, 160, 255), "jet.png", "Large Jet"),
    MEDIUM(new Color(100, 180, 255), "jet.png", "Medium"),
    SMALL_GA(new Color(80, 200, 120), "ga.png", "Small / GA"),
    HELICOPTER(new Color(255, 165, 0), "helo.png", "Helicopter"),
    MILITARY(new Color(255, 60, 60), "military.png", "Military"),
    GROUND(new Color(180, 180, 100), "ground.png", "Ground Vehicle"),
    OTHER(new Color(200, 200, 200), "other.png", "Other");

    private final Color color;
    private final String iconFile;
    private final String displayName;

    AircraftCategory(Color color, String iconFile, String displayName)
    {
        this.color = color;
        this.iconFile = iconFile;
        this.displayName = displayName;
    }

    public Color getColor() { return color; }
    public String getDisplayName() { return displayName; }

    public String getIconPath()
    {
        return "gov/nasa/worldwindx/examples/airtraffic/icons/" + iconFile;
    }

    /**
     * Map ADS-B emitter category code and optional flags to an aircraft category.
     *
     * @param adsb   the ADS-B category string (e.g. "A1" through "A7", "B1", "C1")
     * @param isMil  whether this aircraft is flagged as military
     * @return the matching category
     */
    public static AircraftCategory fromAdsb(String adsb, boolean isMil)
    {
        if (isMil)
            return MILITARY;
        if (adsb == null || adsb.isEmpty())
            return OTHER;

        return switch (adsb)
        {
            case "A1"       -> SMALL_GA;      // light (< 15,500 lbs)
            case "A2"       -> SMALL_GA;      // small (15,500 – 75,000 lbs)
            case "A3"       -> LARGE_JET;     // large (75,000 – 300,000 lbs)
            case "A4"       -> HEAVY_JET;     // high vortex large (B757)
            case "A5"       -> HEAVY_JET;     // heavy (> 300,000 lbs)
            case "A6"       -> HEAVY_JET;     // high performance (> 5g, > 400 kt)
            case "A7"       -> HELICOPTER;    // rotorcraft
            case "B1", "B2" -> GROUND;        // surface vehicle
            default         -> OTHER;
        };
    }

    /** Map ADS-B category to an altitude-independent base color for track paths. */
    public static Color altitudeColor(double altitudeFeet)
    {
        if (altitudeFeet <= 0)
            return new Color(80, 200, 80);     // ground — green
        if (altitudeFeet < 5000)
            return new Color(180, 220, 60);    // low — yellow-green
        if (altitudeFeet < 15000)
            return new Color(255, 200, 0);     // medium — gold
        if (altitudeFeet < 25000)
            return new Color(255, 130, 0);     // climbing — orange
        if (altitudeFeet < 35000)
            return new Color(100, 160, 255);   // cruise — blue
        return new Color(180, 100, 255);       // high cruise — violet
    }
}
