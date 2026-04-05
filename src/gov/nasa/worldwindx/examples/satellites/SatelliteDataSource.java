/*
 * WorldWind Reforged — Satellite Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: interface for satellite TLE data providers (live API and bundled fallback).
 */
package gov.nasa.worldwindx.examples.satellites;

import java.util.Map;

/**
 * Abstraction over a satellite TLE data provider. Implementations may fetch live
 * data from CelesTrak or provide bundled offline TLE data.
 *
 * seaglassfoundry.com
 */
public interface SatelliteDataSource
{
    /**
     * Fetch satellite TLE records.
     *
     * @return map of NORAD catalogue ID to TLE record, or an empty map on error
     */
    Map<Integer, TleRecord> fetchTLEs();

    /** Returns a short human-readable label for the data source. */
    String getSourceLabel();

    /** Returns {@code true} if this source provides live data. */
    boolean isLive();
}
