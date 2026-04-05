/*
 * WorldWind Reforged — NYC Buildings 3D Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: immutable record representing a single OSM building footprint.
 */
package gov.nasa.worldwindx.examples.nycbuildings;

import java.util.Collections;
import java.util.List;

import gov.nasa.worldwind.geom.LatLon;

/**
 * Immutable snapshot of an OpenStreetMap building with footprint geometry,
 * height data, and metadata. Heights are resolved from OSM tags using a
 * priority chain: {@code height} → {@code building:levels × 3.5} → type-based
 * default.
 *
 * seaglassfoundry.com
 */
public final class BuildingRecord
{
    private final String id;
    private final List<LatLon> footprint;
    private final List<List<LatLon>> holes;
    private final double heightMeters;
    private final int levels;
    private final String name;
    private final String buildingType;
    private final String address;
    private final BuildingCategory category;

    public BuildingRecord(String id, List<LatLon> footprint, List<List<LatLon>> holes,
        double heightMeters, int levels, String name, String buildingType, String address)
    {
        this.id = id != null ? id : "";
        this.footprint = footprint != null ? List.copyOf(footprint) : Collections.emptyList();
        this.holes = holes != null ? holes.stream().map(List::copyOf).toList() : Collections.emptyList();
        this.heightMeters = heightMeters;
        this.levels = levels;
        this.name = name != null ? name.trim() : "";
        this.buildingType = buildingType != null ? buildingType.trim() : "yes";
        this.address = address != null ? address.trim() : "";
        this.category = BuildingCategory.fromHeight(heightMeters);
    }

    public String getId()            { return id; }
    public List<LatLon> getFootprint() { return footprint; }
    public List<List<LatLon>> getHoles() { return holes; }
    public double getHeightMeters()  { return heightMeters; }
    public int getLevels()           { return levels; }
    public String getName()          { return name; }
    public String getBuildingType()  { return buildingType; }
    public String getAddress()       { return address; }
    public BuildingCategory getCategory() { return category; }

    /** Short display label: name if available, else type + id. */
    public String getDisplayLabel()
    {
        if (!name.isEmpty()) return name;
        return buildingType + " " + id;
    }

    /**
     * Resolve building height from OSM tags with fallback logic.
     *
     * @param heightTag     the raw "height" tag value (may be null)
     * @param levelsTag     the raw "building:levels" tag value (may be null)
     * @param buildingType  the "building" tag value (e.g. "residential", "skyscraper")
     * @return resolved height in metres
     */
    public static double resolveHeight(String heightTag, String levelsTag, String buildingType)
    {
        if (heightTag != null && !heightTag.isEmpty())
        {
            try
            {
                // Detect explicit feet indicators: "ft", "'", "feet"
                boolean isFeet = heightTag.matches(".*(?:ft|feet|')\\s*$");

                String clean = heightTag.replaceAll("[^0-9.]", "");
                double h = Double.parseDouble(clean);
                if (h > 0)
                {
                    if (isFeet)
                        h *= 0.3048; // convert feet to metres

                    // Heuristic: if no unit was specified and the value seems implausibly
                    // tall for metres (> 700m, taller than any building on Earth) but
                    // plausible in feet, assume feet. Cross-check with levels if available.
                    if (!isFeet && h > 700)
                    {
                        double hFeet = h * 0.3048;
                        if (levelsTag != null && !levelsTag.isEmpty())
                        {
                            try
                            {
                                int lvl = Integer.parseInt(levelsTag.trim());
                                // If feet conversion is within 30% of levels * 3.5m, it's feet
                                double levelsHeight = lvl * 3.5;
                                if (Math.abs(hFeet - levelsHeight) < levelsHeight * 0.3)
                                    h = hFeet;
                            }
                            catch (NumberFormatException ignored2) { }
                        }
                        // Even without levels cross-check, values > 700 are almost
                        // certainly feet (no building exceeds ~830m / 2700ft)
                        if (h > 700)
                            h *= 0.3048;
                    }

                    return h;
                }
            }
            catch (NumberFormatException ignored) { }
        }

        if (levelsTag != null && !levelsTag.isEmpty())
        {
            try
            {
                int lvl = Integer.parseInt(levelsTag.trim());
                if (lvl > 0) return lvl * 3.5;
            }
            catch (NumberFormatException ignored) { }
        }

        // Type-based defaults
        if (buildingType != null)
        {
            return switch (buildingType.toLowerCase())
            {
                case "skyscraper" -> 100;
                case "tower" -> 80;
                case "office" -> 30;
                case "commercial" -> 20;
                case "apartments" -> 25;
                case "industrial", "warehouse" -> 12;
                case "house", "detached", "residential", "terrace" -> 8;
                case "garage", "garages", "shed" -> 4;
                default -> 12;
            };
        }

        return 12; // NYC default low-rise
    }

    /**
     * Resolve floor count from OSM tags.
     *
     * @param levelsTag    the raw "building:levels" tag value
     * @param heightMeters the resolved height
     * @return estimated floor count
     */
    public static int resolveLevels(String levelsTag, double heightMeters)
    {
        if (levelsTag != null && !levelsTag.isEmpty())
        {
            try
            {
                int lvl = Integer.parseInt(levelsTag.trim());
                if (lvl > 0) return lvl;
            }
            catch (NumberFormatException ignored) { }
        }

        return Math.max(1, (int) Math.round(heightMeters / 3.5));
    }
}
