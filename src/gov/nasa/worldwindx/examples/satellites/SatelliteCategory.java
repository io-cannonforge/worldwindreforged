/*
 * WorldWind Reforged — Satellite Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: satellite category enum with color, icon, and name-based classification.
 */
package gov.nasa.worldwindx.examples.satellites;

import java.awt.Color;

/**
 * Satellite categories with associated display colour, icon, and a heuristic
 * classifier that maps satellite names / NORAD IDs to a category.
 *
 * seaglassfoundry.com
 */
public enum SatelliteCategory
{
    SPACE_STATION(new Color(255, 255, 100), "station.png",    "Space Station"),
    NAVIGATION(   new Color(50, 180, 255),  "navigation.png", "Navigation / GNSS"),
    WEATHER(      new Color(100, 220, 160), "weather.png",    "Weather"),
    COMMUNICATION(new Color(220, 160, 50),  "comm.png",       "Communication"),
    EARTH_OBS(    new Color(60, 200, 200),  "earthobs.png",   "Earth Observation"),
    STARLINK(     new Color(180, 180, 220), "starlink.png",   "Starlink"),
    MILITARY(     new Color(255, 80, 80),   "military.png",   "Military"),
    DEBRIS(       new Color(140, 140, 140), "debris.png",     "Debris / Rocket Body"),
    OTHER(        new Color(200, 200, 200), "other.png",      "Other");

    private final Color color;
    private final String iconFile;
    private final String displayName;

    SatelliteCategory(Color color, String iconFile, String displayName)
    {
        this.color = color;
        this.iconFile = iconFile;
        this.displayName = displayName;
    }

    public Color getColor()       { return color; }
    public String getDisplayName() { return displayName; }

    /** Returns the classpath-relative icon path under the satellites/icons/ directory. */
    public String getIconPath()
    {
        return "gov/nasa/worldwindx/examples/satellites/icons/" + iconFile;
    }

    /**
     * Returns the orbit-altitude colour for rendering orbit paths.
     * LEO(cyan) → MEO(green) → GEO(gold) → HEO(purple).
     */
    public static Color altitudeColor(double altitudeKm)
    {
        if (altitudeKm <= 600)
            return new Color(0, 200, 255);       // LEO — cyan
        if (altitudeKm <= 2000)
            return new Color(60, 120, 255);      // high LEO — blue
        if (altitudeKm <= 20000)
            return new Color(80, 220, 100);      // MEO — green
        if (altitudeKm <= 35000)
            return new Color(255, 200, 50);      // near-GEO — gold
        if (altitudeKm <= 37000)
            return new Color(255, 220, 80);      // GEO — bright gold
        return new Color(180, 100, 255);         // HEO — purple
    }

    /**
     * Classify a satellite into a category by its name and NORAD ID.
     *
     * @param name     the satellite object name (from TLE line 0 or GP data)
     * @param noradId  the NORAD catalogue number
     * @return the best-matching category
     */
    public static SatelliteCategory classify(String name, int noradId)
    {
        if (name == null) name = "";
        String upper = name.toUpperCase().trim();

        // Space stations by NORAD ID
        if (noradId == 25544 || noradId == 48274)
            return SPACE_STATION;
        if (upper.contains("ISS") || upper.contains("TIANGONG") || upper.contains("CSS "))
            return SPACE_STATION;

        // Starlink (check early — there are thousands)
        if (upper.contains("STARLINK"))
            return STARLINK;

        // Debris / rocket bodies
        if (upper.contains(" DEB") || upper.contains(" R/B") || upper.startsWith("R/B"))
            return DEBRIS;

        // Navigation / GNSS
        if (upper.contains("GPS ") || upper.contains("NAVSTAR") || upper.contains("GLONASS")
            || upper.contains("GALILEO") || upper.contains("BEIDOU") || upper.contains("IRNSS")
            || upper.contains("QZSS"))
            return NAVIGATION;

        // Weather
        if (upper.contains("GOES ") || upper.contains("NOAA ") || upper.contains("METEOSAT")
            || upper.contains("HIMAWARI") || upper.contains("FENGYUN") || upper.contains("METEOR-M")
            || upper.contains("METOP") || upper.contains("SUOMI") || upper.contains("DMSP"))
            return WEATHER;

        // Communication
        if (upper.contains("INTELSAT") || upper.contains("SES-") || upper.contains("EUTELSAT")
            || upper.contains("ANIK") || upper.contains("VIASAT") || upper.contains("TDRS")
            || upper.contains("INMARSAT") || upper.contains("IRIDIUM") || upper.contains("GLOBALSTAR")
            || upper.contains("ORBCOMM") || upper.contains("O3B") || upper.contains("TELSTAR")
            || upper.contains("ASTRA "))
            return COMMUNICATION;

        // Earth observation / science
        if (upper.contains("LANDSAT") || upper.contains("SENTINEL") || upper.contains("TERRA ")
            || upper.contains("AQUA ") || upper.contains("WORLDVIEW") || upper.contains("SPOT ")
            || upper.contains("PLEIADES") || upper.contains("JASON") || upper.contains("CRYOSAT")
            || upper.contains("ICE") || upper.contains("GRACE"))
            return EARTH_OBS;

        // Military (US convention: "USA NNN")
        if (upper.startsWith("USA ") || upper.contains("NOSS") || upper.contains("LACROSSE")
            || upper.contains("KEYHOLE") || upper.contains("MISTY"))
            return MILITARY;

        return OTHER;
    }
}
