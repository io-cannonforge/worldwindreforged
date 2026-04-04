/*
 * WorldWind Reforged — Air Traffic Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: immutable snapshot of a single aircraft ADS-B position report.
 */
package gov.nasa.worldwindx.examples.airtraffic;

/**
 * Immutable snapshot of an aircraft's ADS-B state vector from airplanes.live.
 *
 * seaglassfoundry.com
 */
public final class AircraftPosition
{
    private final String hex;           // ICAO 24-bit hex address
    private final String callsign;      // flight number / callsign
    private final String registration;  // tail number (e.g. N12345)
    private final String typeCode;      // ICAO type designator (e.g. B738)
    private final String typeDesc;      // human-readable type (e.g. "BOEING 737-800")
    private final String operator;      // airline / operator name
    private final double lat;
    private final double lon;
    private final double altitudeFeet;  // barometric altitude in feet (0 if on ground)
    private final boolean onGround;
    private final double groundSpeed;   // knots
    private final double track;         // degrees (course over ground)
    private final double trueHeading;   // degrees (or NaN if unavailable)
    private final double verticalRate;  // feet per minute (positive = climbing)
    private final String squawk;        // transponder code
    private final String emergency;     // "none", "general", "lifeguard", "minfuel", "nordo", "unlawful", "downed"
    private final String adsbCategory;  // ADS-B emitter category ("A1"-"A7", "B1"-"B7", etc.)
    private final boolean military;     // flagged as military by data source
    private final long timestamp;       // epoch millis of this observation

    public AircraftPosition(String hex, String callsign, String registration,
        String typeCode, String typeDesc, String operator,
        double lat, double lon, double altitudeFeet, boolean onGround,
        double groundSpeed, double track, double trueHeading, double verticalRate,
        String squawk, String emergency, String adsbCategory, boolean military,
        long timestamp)
    {
        this.hex = hex != null ? hex : "";
        this.callsign = callsign != null ? callsign.trim() : "";
        this.registration = registration != null ? registration.trim() : "";
        this.typeCode = typeCode != null ? typeCode.trim() : "";
        this.typeDesc = typeDesc != null ? typeDesc.trim() : "";
        this.operator = operator != null ? operator.trim() : "";
        this.lat = lat;
        this.lon = lon;
        this.altitudeFeet = altitudeFeet;
        this.onGround = onGround;
        this.groundSpeed = groundSpeed;
        this.track = track;
        this.trueHeading = trueHeading;
        this.verticalRate = verticalRate;
        this.squawk = squawk != null ? squawk : "";
        this.emergency = emergency != null ? emergency : "none";
        this.adsbCategory = adsbCategory != null ? adsbCategory : "";
        this.military = military;
        this.timestamp = timestamp;
    }

    public String getHex()          { return hex; }
    public String getCallsign()     { return callsign; }
    public String getRegistration() { return registration; }
    public String getTypeCode()     { return typeCode; }
    public String getTypeDesc()     { return typeDesc; }
    public String getOperator()     { return operator; }
    public double getLat()          { return lat; }
    public double getLon()          { return lon; }
    public double getAltitudeFeet() { return altitudeFeet; }
    public boolean isOnGround()     { return onGround; }
    public double getGroundSpeed()  { return groundSpeed; }
    public double getTrack()        { return track; }
    public double getTrueHeading()  { return trueHeading; }
    public double getVerticalRate() { return verticalRate; }
    public String getSquawk()       { return squawk; }
    public String getEmergency()    { return emergency; }
    public String getAdsbCategory() { return adsbCategory; }
    public boolean isMilitary()     { return military; }
    public long getTimestamp()       { return timestamp; }

    /** Returns the best heading: true heading if available, else track (COG). */
    public double getEffectiveHeading()
    {
        return Double.isNaN(trueHeading) ? track : trueHeading;
    }

    /** Returns altitude in metres for WorldWind positioning. */
    public double getAltitudeMeters()
    {
        return altitudeFeet * 0.3048;
    }

    /** True if this aircraft is squawking an emergency code (7500, 7600, 7700). */
    public boolean isEmergency()
    {
        return !"none".equals(emergency) ||
            "7500".equals(squawk) || "7600".equals(squawk) || "7700".equals(squawk);
    }

    /** Short display label — callsign if available, else registration, else hex. */
    public String getDisplayLabel()
    {
        if (!callsign.isEmpty()) return callsign;
        if (!registration.isEmpty()) return registration;
        return hex.toUpperCase();
    }

    /** Category for rendering. */
    public AircraftCategory getCategory()
    {
        return AircraftCategory.fromAdsb(adsbCategory, military);
    }
}
