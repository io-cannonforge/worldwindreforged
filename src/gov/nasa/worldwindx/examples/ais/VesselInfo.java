/*
 * WorldWind Reforged — AIS Vessel Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: immutable value object for AIS vessel static/voyage metadata.
 */
package gov.nasa.worldwindx.examples.ais;

/**
 * Immutable vessel metadata from AIS message types 5 / 24 (static and voyage data).
 *
 * seaglassfoundry.com
 */
public final class VesselInfo
{
    private final int mmsi;
    private final String name;
    private final String callSign;
    private final int imo;
    private final int shipType;
    private final int draught;       // decimetres
    private final String destination;
    private final int lengthMeters;
    private final int widthMeters;

    public VesselInfo(int mmsi, String name, String callSign, int imo, int shipType,
        int draught, String destination, int lengthMeters, int widthMeters)
    {
        this.mmsi = mmsi;
        this.name = name != null ? name.trim() : "";
        this.callSign = callSign != null ? callSign.trim() : "";
        this.imo = imo;
        this.shipType = shipType;
        this.draught = draught;
        this.destination = destination != null ? destination.trim() : "";
        this.lengthMeters = lengthMeters;
        this.widthMeters = widthMeters;
    }

    public int getMmsi()          { return mmsi; }
    public String getName()       { return name; }
    public String getCallSign()   { return callSign; }
    public int getImo()           { return imo; }
    public int getShipType()      { return shipType; }
    public int getDraught()       { return draught; }
    public String getDestination() { return destination; }
    public int getLengthMeters()  { return lengthMeters; }
    public int getWidthMeters()   { return widthMeters; }

    /** Returns the draught in metres (AIS reports decimetres). */
    public double getDraughtMeters()
    {
        return draught / 10.0;
    }

    /** Classifies this vessel into a display category based on AIS ship-type code. */
    public VesselCategory getCategory()
    {
        return VesselCategory.fromShipType(shipType);
    }

    /** Returns a short display label — vessel name if available, otherwise MMSI. */
    public String getDisplayName()
    {
        return name.isEmpty() ? String.valueOf(mmsi) : name;
    }
}
