/*
 * WorldWind Reforged — Satellite Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: immutable computed satellite position at a given instant.
 */
package gov.nasa.worldwindx.examples.satellites;

/**
 * Immutable satellite position computed by {@link Sgp4Propagator} at a specific
 * instant. Provides geodetic coordinates, orbital velocity, and direction of travel.
 *
 * seaglassfoundry.com
 */
public final class SatellitePosition
{
    private final int noradCatId;
    private final double latDeg;
    private final double lonDeg;
    private final double altitudeKm;
    private final double velocityKmS;
    private final double azimuthDeg;  // direction of travel (0=north, 90=east)
    private final boolean eclipsed;   // in Earth's shadow

    public SatellitePosition(int noradCatId, double latDeg, double lonDeg,
        double altitudeKm, double velocityKmS, double azimuthDeg, boolean eclipsed)
    {
        this.noradCatId = noradCatId;
        this.latDeg = latDeg;
        this.lonDeg = lonDeg;
        this.altitudeKm = altitudeKm;
        this.velocityKmS = velocityKmS;
        this.azimuthDeg = azimuthDeg;
        this.eclipsed = eclipsed;
    }

    public int getNoradCatId()    { return noradCatId; }
    public double getLatDeg()     { return latDeg; }
    public double getLonDeg()     { return lonDeg; }
    public double getAltitudeKm() { return altitudeKm; }
    public double getVelocityKmS() { return velocityKmS; }
    public double getAzimuthDeg() { return azimuthDeg; }
    public boolean isEclipsed()   { return eclipsed; }

    /** Altitude in metres (for WorldWind positioning). */
    public double getAltitudeMeters() { return altitudeKm * 1000.0; }

    /** Classify the orbit type by altitude. */
    public String getOrbitType()
    {
        if (altitudeKm < 2000)   return "LEO";
        if (altitudeKm < 20000)  return "MEO";
        if (altitudeKm < 37000)  return "GEO";
        return "HEO";
    }
}
