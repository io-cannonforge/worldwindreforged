/*
 * WorldWind Reforged — AIS Vessel Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: interface for AIS data providers (live API and CSV replay).
 */
package gov.nasa.worldwindx.examples.ais;

import java.util.Map;

/**
 * Abstraction over an AIS data provider. Implementations may fetch live data from
 * a REST API or replay a bundled CSV dataset.
 *
 * seaglassfoundry.com
 */
public interface AISDataSource
{
    /**
     * Fetch current vessel positions.
     *
     * @return map of MMSI to the latest position report, or an empty map on error
     */
    Map<Integer, VesselPosition> fetchPositions();

    /**
     * Fetch vessel static/voyage metadata.
     *
     * @return map of MMSI to vessel info, or an empty map on error
     */
    Map<Integer, VesselInfo> fetchVesselInfo();

    /** Returns a short human-readable label for the data source (e.g. "Digitraffic" or "CSV Replay"). */
    String getSourceLabel();

    /** Returns {@code true} if this source provides live (non-replay) data. */
    boolean isLive();
}
