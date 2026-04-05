/*
 * WorldWind Reforged — Satellite Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: immutable value object for a parsed TLE (Two-Line Element set) record.
 * Holds both the raw TLE lines for SGP4 propagation and parsed metadata fields.
 */
package gov.nasa.worldwindx.examples.satellites;

/**
 * Immutable record of a satellite TLE (Two-Line Element set) as provided by
 * CelesTrak's GP data API. Holds parsed orbital elements plus the raw TLE lines
 * needed by {@link Sgp4Propagator}.
 *
 * seaglassfoundry.com
 */
public final class TleRecord
{
    private final int noradCatId;
    private final String objectName;
    private final String intlDesignator;
    private final double epochJd;           // Julian date of TLE epoch
    private final double meanMotionRevDay;  // revolutions per day
    private final double eccentricity;
    private final double inclinationDeg;
    private final double raanDeg;           // right ascension of ascending node
    private final double argPerigeeDeg;
    private final double meanAnomalyDeg;
    private final double bstar;             // drag coefficient
    private final int revNumber;
    private final String tleLine1;
    private final String tleLine2;
    private final SatelliteCategory category;

    public TleRecord(int noradCatId, String objectName, String intlDesignator,
        double epochJd, double meanMotionRevDay, double eccentricity,
        double inclinationDeg, double raanDeg, double argPerigeeDeg,
        double meanAnomalyDeg, double bstar, int revNumber,
        String tleLine1, String tleLine2)
    {
        this.noradCatId = noradCatId;
        this.objectName = objectName != null ? objectName.trim() : "";
        this.intlDesignator = intlDesignator != null ? intlDesignator.trim() : "";
        this.epochJd = epochJd;
        this.meanMotionRevDay = meanMotionRevDay;
        this.eccentricity = eccentricity;
        this.inclinationDeg = inclinationDeg;
        this.raanDeg = raanDeg;
        this.argPerigeeDeg = argPerigeeDeg;
        this.meanAnomalyDeg = meanAnomalyDeg;
        this.bstar = bstar;
        this.revNumber = revNumber;
        this.tleLine1 = tleLine1 != null ? tleLine1 : "";
        this.tleLine2 = tleLine2 != null ? tleLine2 : "";
        this.category = SatelliteCategory.classify(objectName, noradCatId);
    }

    public int getNoradCatId()          { return noradCatId; }
    public String getObjectName()       { return objectName; }
    public String getIntlDesignator()   { return intlDesignator; }
    public double getEpochJd()          { return epochJd; }
    public double getMeanMotionRevDay() { return meanMotionRevDay; }
    public double getEccentricity()     { return eccentricity; }
    public double getInclinationDeg()   { return inclinationDeg; }
    public double getRaanDeg()          { return raanDeg; }
    public double getArgPerigeeDeg()    { return argPerigeeDeg; }
    public double getMeanAnomalyDeg()   { return meanAnomalyDeg; }
    public double getBstar()            { return bstar; }
    public int getRevNumber()           { return revNumber; }
    public String getTleLine1()         { return tleLine1; }
    public String getTleLine2()         { return tleLine2; }
    public SatelliteCategory getCategory() { return category; }

    /** Returns the orbital period in minutes. */
    public double getPeriodMinutes()
    {
        return meanMotionRevDay > 0 ? 1440.0 / meanMotionRevDay : 0;
    }

    /** Returns the semi-major axis in km (derived from mean motion). */
    public double getSemiMajorAxisKm()
    {
        if (meanMotionRevDay <= 0) return 0;
        double n = meanMotionRevDay * 2.0 * Math.PI / 86400.0; // rad/s
        double mu = 398600.4418; // km^3/s^2
        return Math.pow(mu / (n * n), 1.0 / 3.0);
    }

    /** Returns approximate apogee altitude in km. */
    public double getApogeeKm()
    {
        double a = getSemiMajorAxisKm();
        return a > 0 ? a * (1 + eccentricity) - 6378.137 : 0;
    }

    /** Returns approximate perigee altitude in km. */
    public double getPerigeeKm()
    {
        double a = getSemiMajorAxisKm();
        return a > 0 ? a * (1 - eccentricity) - 6378.137 : 0;
    }

    /** Returns a display name — object name if available, otherwise NORAD ID. */
    public String getDisplayName()
    {
        return objectName.isEmpty() ? String.valueOf(noradCatId) : objectName;
    }

    /**
     * Parse a TLE record from the standard two-line format plus an optional name line.
     *
     * @param name   satellite name (line 0), may be null
     * @param line1  TLE line 1
     * @param line2  TLE line 2
     * @return parsed TleRecord, or null if the lines are invalid
     */
    public static TleRecord fromTleLines(String name, String line1, String line2)
    {
        try
        {
            if (line1 == null || line2 == null || line1.length() < 69 || line2.length() < 69)
                return null;

            int noradId = Integer.parseInt(line1.substring(2, 7).trim());
            String intlDes = line1.substring(9, 17).trim();

            // Epoch: columns 18-32 of line 1 = YY DDD.DDDDDDDD
            String epochStr = line1.substring(18, 32).trim();
            int epochYear = Integer.parseInt(epochStr.substring(0, 2));
            double epochDay = Double.parseDouble(epochStr.substring(2));
            // Convert 2-digit year
            int fullYear = epochYear < 57 ? 2000 + epochYear : 1900 + epochYear;
            double epochJd = julianDateOfYear(fullYear) + epochDay;

            // B* drag: column 53-61 of line 1 (decimal point assumed)
            double bstarVal = parseTleFloat(line1.substring(53, 61).trim());

            // Line 2 fields
            double inclination = Double.parseDouble(line2.substring(8, 16).trim());
            double raan = Double.parseDouble(line2.substring(17, 25).trim());
            double ecc = Double.parseDouble("0." + line2.substring(26, 33).trim());
            double argPerigee = Double.parseDouble(line2.substring(34, 42).trim());
            double meanAnomaly = Double.parseDouble(line2.substring(43, 51).trim());
            double meanMotion = Double.parseDouble(line2.substring(52, 63).trim());
            int revNum = Integer.parseInt(line2.substring(63, 68).trim());

            return new TleRecord(noradId, name, intlDes, epochJd, meanMotion,
                ecc, inclination, raan, argPerigee, meanAnomaly, bstarVal, revNum,
                line1, line2);
        }
        catch (NumberFormatException | StringIndexOutOfBoundsException e)
        {
            return null;
        }
    }

    /** Parse a TLE-formatted floating point (e.g., " 12345-4" → 0.12345e-4). */
    static double parseTleFloat(String s)
    {
        if (s == null || s.isEmpty()) return 0;
        s = s.trim();
        if (s.isEmpty() || s.equals("0") || s.equals("00000-0")) return 0;

        // Handle format like " 12345-4" or "-12345-4"
        StringBuilder sb = new StringBuilder();
        int i = 0;
        if (s.charAt(0) == '-' || s.charAt(0) == '+')
        {
            sb.append(s.charAt(0));
            i = 1;
        }
        sb.append("0.");

        // Find the exponent part (last sign character after first)
        int expIdx = -1;
        for (int j = i; j < s.length(); j++)
        {
            if (s.charAt(j) == '-' || s.charAt(j) == '+')
                expIdx = j;
        }

        if (expIdx > 0)
        {
            sb.append(s, i, expIdx);
            sb.append("E");
            sb.append(s.substring(expIdx));
        }
        else
        {
            sb.append(s.substring(i));
        }

        try
        {
            return Double.parseDouble(sb.toString());
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    /** Julian date at the start of a given year (Jan 0.0). */
    private static double julianDateOfYear(int year)
    {
        // Algorithm from Vallado's "Fundamentals of Astrodynamics"
        return 367.0 * year
            - Math.floor(7.0 * (year + Math.floor(10.0 / 12.0)) / 4.0)
            + Math.floor(275.0 / 9.0)
            + 1721013.5;
    }
}
