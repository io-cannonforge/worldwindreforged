/*
 * WorldWind Reforged — Satellite Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: pre-computes full orbit paths, ground tracks, and footprint radii
 * for satellite visualization.
 */
package gov.nasa.worldwindx.examples.satellites;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import gov.nasa.worldwind.geom.Position;

/**
 * Pre-computes a full orbit path (one complete revolution) for a satellite,
 * plus the subsatellite ground track and the radio-horizon footprint radius.
 * <p>
 * These are expensive to compute (360 SGP4 propagations per satellite) so they
 * are only calculated once per TLE update, not every animation tick.
 *
 * seaglassfoundry.com
 */
public final class OrbitCalculator
{
    /** Number of sample points for one full orbit. */
    private static final int ORBIT_SAMPLES = 360;
    private static final double EARTH_RADIUS_KM = 6378.137;

    private OrbitCalculator() {}

    /**
     * Compute a full orbit path (one period) as a list of 3D positions at actual altitude.
     *
     * @param propagator the SGP4 propagator for this satellite
     * @param tle        the TLE record (for orbital period)
     * @param refTime    the reference time to centre the orbit around
     * @return list of positions (lat, lon, altitude in metres), or empty if propagation fails
     */
    public static List<Position> computeOrbitPath(Sgp4Propagator propagator, TleRecord tle,
        Instant refTime)
    {
        List<Position> positions = new ArrayList<>(ORBIT_SAMPLES);
        double periodSec = tle.getPeriodMinutes() * 60.0;
        if (periodSec <= 0) return positions;

        // Start half a period before refTime, end half a period after
        double startOffsetSec = -periodSec / 2.0;
        double stepSec = periodSec / ORBIT_SAMPLES;

        for (int i = 0; i <= ORBIT_SAMPLES; i++)
        {
            double offsetSec = startOffsetSec + i * stepSec;
            Instant t = refTime.plusMillis((long) (offsetSec * 1000));
            SatellitePosition sp = propagator.propagate(t);
            if (sp != null && sp.getAltitudeKm() > 0)
            {
                positions.add(Position.fromDegrees(sp.getLatDeg(), sp.getLonDeg(),
                    sp.getAltitudeMeters()));
            }
        }

        return positions;
    }

    /**
     * Compute the ground track (subsatellite point) for one full orbit as a surface path.
     *
     * @param propagator the SGP4 propagator
     * @param tle        the TLE record
     * @param refTime    reference time to centre the ground track around
     * @return list of surface positions (altitude = 0), or empty if propagation fails
     */
    public static List<Position> computeGroundTrack(Sgp4Propagator propagator, TleRecord tle,
        Instant refTime)
    {
        List<Position> positions = new ArrayList<>(ORBIT_SAMPLES);
        double periodSec = tle.getPeriodMinutes() * 60.0;
        if (periodSec <= 0) return positions;

        double startOffsetSec = -periodSec / 2.0;
        double stepSec = periodSec / ORBIT_SAMPLES;

        for (int i = 0; i <= ORBIT_SAMPLES; i++)
        {
            double offsetSec = startOffsetSec + i * stepSec;
            Instant t = refTime.plusMillis((long) (offsetSec * 1000));
            SatellitePosition sp = propagator.propagate(t);
            if (sp != null)
            {
                positions.add(Position.fromDegrees(sp.getLatDeg(), sp.getLonDeg(), 0));
            }
        }

        return positions;
    }

    /**
     * Compute the radio-horizon footprint radius in metres for a satellite at the given altitude.
     * This is the ground-visible radius assuming a smooth Earth.
     *
     * @param altitudeKm satellite altitude in kilometres
     * @return footprint radius in metres
     */
    public static double computeFootprintRadiusMeters(double altitudeKm)
    {
        if (altitudeKm <= 0) return 0;
        double rho = EARTH_RADIUS_KM / (EARTH_RADIUS_KM + altitudeKm);
        double halfAngle = Math.acos(rho); // angular radius in radians
        return halfAngle * EARTH_RADIUS_KM * 1000.0; // metres
    }
}
