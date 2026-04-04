/*
 * WorldWind Reforged — AIS Vessel Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: AIS vessel type category enum with color and icon mapping.
 */
package gov.nasa.worldwindx.examples.ais;

import java.awt.Color;

/**
 * Vessel categories derived from AIS ship-type codes, each with an associated
 * display color and icon resource path.
 *
 * seaglassfoundry.com
 */
public enum VesselCategory
{
    CARGO(new Color(0, 200, 80), "cargo.png"),
    TANKER(new Color(220, 40, 40), "tanker.png"),
    PASSENGER(new Color(50, 120, 255), "passenger.png"),
    FISHING(new Color(240, 200, 0), "fishing.png"),
    TUG(new Color(240, 140, 0), "tug.png"),
    MILITARY(new Color(160, 160, 170), "military.png"),
    OTHER(new Color(220, 220, 220), "other.png");

    private final Color color;
    private final String iconFile;

    VesselCategory(Color color, String iconFile)
    {
        this.color = color;
        this.iconFile = iconFile;
    }

    public Color getColor()
    {
        return this.color;
    }

    /** Returns the classpath-relative icon path under the ais/icons/ directory. */
    public String getIconPath()
    {
        return "gov/nasa/worldwindx/examples/ais/icons/" + this.iconFile;
    }

    /**
     * Map an AIS ship-type code (0-99) to a vessel category.
     *
     * @param shipType the AIS ship type integer
     * @return the matching category, or {@link #OTHER} if unrecognized
     */
    public static VesselCategory fromShipType(int shipType)
    {
        if (shipType == 30)
            return FISHING;
        if (shipType == 35)
            return MILITARY;
        if (shipType >= 36 && shipType <= 37)
            return OTHER; // sailing / pleasure
        if (shipType >= 40 && shipType <= 49)
            return OTHER; // high-speed craft
        if (shipType == 50 || shipType == 52)
            return TUG;
        if (shipType >= 60 && shipType <= 69)
            return PASSENGER;
        if (shipType >= 70 && shipType <= 79)
            return CARGO;
        if (shipType >= 80 && shipType <= 89)
            return TANKER;
        return OTHER;
    }
}
