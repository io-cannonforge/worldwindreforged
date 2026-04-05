/*
 * WorldWind Reforged — NYC Buildings 3D Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: interface for building data providers.
 */
package gov.nasa.worldwindx.examples.nycbuildings;

import java.util.List;

/**
 * Abstraction over a building footprint data provider. Implementations fetch
 * building geometry and metadata for a geographic bounding box.
 *
 * seaglassfoundry.com
 */
public interface BuildingDataSource
{
    /**
     * Fetch building records within the specified bounding box.
     *
     * @param south southern latitude (degrees)
     * @param west  western longitude (degrees)
     * @param north northern latitude (degrees)
     * @param east  eastern longitude (degrees)
     * @return list of building records, or empty list on error
     */
    List<BuildingRecord> fetchBuildings(double south, double west, double north, double east);

    /** Returns a short human-readable label for this data source. */
    String getSourceLabel();

    /** Returns true if this source fetches live data from an API. */
    boolean isLive();
}
