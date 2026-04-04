/*
 * WorldWind Reforged — AIS Vessel Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: immutable value object for a single AIS position report.
 */
package gov.nasa.worldwindx.examples.ais;

/**
 * Immutable snapshot of a single AIS position report.
 *
 * seaglassfoundry.com
 */
public final class VesselPosition
{
    private final int mmsi;
    private final double lat;
    private final double lon;
    private final double sog;   // speed over ground, knots
    private final double cog;   // course over ground, degrees
    private final int heading;  // true heading, 0-359 (511 = not available)
    private final int navStatus;
    private final long timestamp; // epoch millis

    public VesselPosition(int mmsi, double lat, double lon, double sog, double cog,
        int heading, int navStatus, long timestamp)
    {
        this.mmsi = mmsi;
        this.lat = lat;
        this.lon = lon;
        this.sog = sog;
        this.cog = cog;
        this.heading = heading;
        this.navStatus = navStatus;
        this.timestamp = timestamp;
    }

    public int getMmsi()      { return mmsi; }
    public double getLat()    { return lat; }
    public double getLon()    { return lon; }
    public double getSog()    { return sog; }
    public double getCog()    { return cog; }
    public int getNavStatus() { return navStatus; }
    public long getTimestamp() { return timestamp; }

    /**
     * Returns the best available heading in degrees. If the AIS true heading is
     * unavailable (511), falls back to course-over-ground.
     */
    public double getEffectiveHeading()
    {
        return (heading >= 0 && heading < 360) ? heading : cog;
    }
}
