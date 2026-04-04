/*
 * WorldWind Reforged — Air Traffic Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: interface for air traffic data providers.
 */
package gov.nasa.worldwindx.examples.airtraffic;

import java.util.Map;

/**
 * Abstraction over an air traffic data provider.
 *
 * seaglassfoundry.com
 */
public interface AirTrafficDataSource
{
    /**
     * Fetch aircraft within a radius of a geographic point.
     *
     * @param lat    centre latitude (degrees)
     * @param lon    centre longitude (degrees)
     * @param radiusNm radius in nautical miles (max 250)
     * @return map of ICAO hex → latest position, or empty map on error
     */
    Map<String, AircraftPosition> fetchAircraft(double lat, double lon, int radiusNm);

    /**
     * Fetch all military aircraft worldwide.
     *
     * @return map of ICAO hex → position, or empty map on error
     */
    Map<String, AircraftPosition> fetchMilitary();

    /** Returns a short human-readable label for this data source. */
    String getSourceLabel();
}
