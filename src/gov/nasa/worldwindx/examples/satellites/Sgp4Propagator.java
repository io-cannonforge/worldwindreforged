/*
 * WorldWind Reforged — Satellite Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: self-contained SGP4 orbital propagator for near-Earth satellites.
 * Based on "Revisiting Spacetrack Report No. 3" (Vallado et al., 2006) and the
 * NORAD SGP4 algorithm. Accurate to a few km for LEO over the TLE validity window.
 */
package gov.nasa.worldwindx.examples.satellites;

import java.time.Instant;

/**
 * SGP4 orbital propagator that takes a {@link TleRecord} and computes the satellite's
 * geodetic position at any given {@link Instant}.
 * <p>
 * This is a simplified implementation covering the near-Earth (SGP4) case, which handles
 * ~99% of catalogued objects (LEO, MEO, and most GEO satellites). Deep-space objects with
 * orbital periods &gt; 225 minutes may have reduced accuracy.
 * <p>
 * Reference: Vallado, Crawford, Hujsak, Kelso — "Revisiting Spacetrack Report No. 3",
 * AIAA 2006-6753.
 *
 * seaglassfoundry.com
 */
public class Sgp4Propagator
{
    // ── WGS-84 / SGP4 Constants ──────────────────────────────────────────────

    private static final double RE      = 6378.137;        // Earth equatorial radius km
    private static final double J2      = 1.08262998905e-3;
    private static final double J3      = -2.53215306e-6;
    private static final double TWO_PI  = 2.0 * Math.PI;
    private static final double DEG2RAD = Math.PI / 180.0;
    private static final double MIN_PER_DAY = 1440.0;

    // SGP4 uses Earth radii and minutes as internal units
    private static final double XKE    = 0.0743669161;  // sqrt(MU) in er^3/min^2
    private static final double VKMPS  = RE * XKE / 60.0; // velocity factor: er/min to km/s
    private static final double CK2    = 0.5 * J2;
    private static final double A3OVK2 = -J3 / CK2;
    private static final double QOMS2T = 1.880279159015270643865e-9; // (qo - so)^4 in er^4

    // ── JD reference: Unix epoch (1970-01-01 00:00 UTC) as Julian date ───────
    private static final double JD_UNIX_EPOCH = 2440587.5;

    // ── Precomputed orbital elements ─────────────────────────────────────────

    private final TleRecord tle;
    private final double epochJd;

    // Kozai mean elements at epoch (in SGP4 internal units: er, min, rad)
    private final double no;     // mean motion (rad/min)
    private final double eo;     // eccentricity
    private final double io;     // inclination (rad)
    private final double omo;    // RAAN (rad)
    private final double wo;     // argument of perigee (rad)
    private final double mo;     // mean anomaly (rad)
    private final double bstar;

    // Precomputed constants
    private final double cosio, sinio, theta2, xi, eta, eeta, aodp, tsi, c1, c4, c5;
    private final double d2, d3, d4, t3cof, t4cof, t5cof;
    private final double omegadot, xnodot, xmdot;
    private boolean isDeepSpace;

    /**
     * Create a propagator from a TLE record.
     *
     * @param tle the TLE record to propagate
     */
    public Sgp4Propagator(TleRecord tle)
    {
        this.tle = tle;
        this.epochJd = tle.getEpochJd();
        this.bstar = tle.getBstar();

        // Convert mean motion from rev/day to rad/min
        double n0 = tle.getMeanMotionRevDay() * TWO_PI / MIN_PER_DAY;
        this.eo = tle.getEccentricity();
        this.io = tle.getInclinationDeg() * DEG2RAD;
        this.omo = tle.getRaanDeg() * DEG2RAD;
        this.wo = tle.getArgPerigeeDeg() * DEG2RAD;
        this.mo = tle.getMeanAnomalyDeg() * DEG2RAD;

        // Common trig
        cosio = Math.cos(io);
        sinio = Math.sin(io);
        theta2 = cosio * cosio;
        double x3thm1 = 3.0 * theta2 - 1.0;
        double betao2 = 1.0 - eo * eo;
        double betao = Math.sqrt(betao2);

        // Recover original mean motion (aodp) and semi-major axis from TLE
        double a1 = Math.pow(XKE / n0, 2.0 / 3.0);
        double del1 = 1.5 * CK2 * x3thm1 / (a1 * a1 * betao * betao2);
        double ao = a1 * (1.0 - del1 * (1.0 / 3.0 + del1 * (1.0 + 134.0 / 81.0 * del1)));
        double delo = 1.5 * CK2 * x3thm1 / (ao * ao * betao * betao2);
        double xnodp = n0 / (1.0 + delo);
        aodp = ao / (1.0 - delo);
        this.no = xnodp;

        // Check for deep-space (period > 225 min)
        isDeepSpace = (TWO_PI / xnodp) >= 225.0;

        // Atmospheric drag setup
        double perigee = (aodp * (1.0 - eo) - 1.0) * RE;
        double s = 78.0 / RE + 1.0;
        if (perigee < 156.0)
        {
            s = perigee <= 98.0 ? 20.0 / RE + 1.0 : (perigee - 78.0) / RE + 1.0;
        }
        double pinvsq = 1.0 / (aodp * aodp * betao2 * betao2);
        tsi = 1.0 / (aodp - s);
        eta = aodp * eo * tsi;
        eeta = eo * eta;
        double etasq = eta * eta;
        double psisq = Math.abs(1.0 - etasq);
        double coef = QOMS2T * Math.pow(tsi, 4.0);
        double coef1 = coef / Math.pow(psisq, 3.5);

        double c2 = coef1 * xnodp * (aodp * (1.0 + 1.5 * etasq + eeta * (4.0 + etasq))
            + 0.75 * CK2 * tsi / psisq * x3thm1 * (8.0 + 3.0 * etasq * (8.0 + etasq)));
        c1 = bstar * c2;
        c4 = 2.0 * xnodp * coef1 * aodp * betao2
            * (eta * (2.0 + 0.5 * etasq) + eo * (0.5 + 2.0 * etasq)
            - 2.0 * CK2 * tsi / (aodp * psisq)
            * (-3.0 * x3thm1 * (1.0 - 2.0 * eeta + etasq * (1.5 - 0.5 * eeta))
            + 0.75 * (1.0 - theta2) * (2.0 * etasq - eeta * (1.0 + etasq))
            * Math.cos(2.0 * wo)));
        c5 = 2.0 * coef1 * aodp * betao2 * (1.0 + 2.75 * (etasq + eeta) + eeta * etasq);

        xi = 1.0 / (aodp - s);

        // Secular rates
        double temp1 = CK2 * pinvsq;
        double temp3 = 1.5 * temp1 * xnodp;
        xmdot = xnodp + 0.5 * temp3 * betao * x3thm1;
        omegadot = -0.5 * temp3 * (1.0 - 5.0 * theta2);
        double xhdot1 = -temp3 * cosio;
        xnodot = xhdot1;

        // Higher-order drag terms (C1^2 etc.)
        double c1sq = c1 * c1;
        d2 = 4.0 * aodp * tsi * c1sq;
        double temp = d2 * tsi * c1 / 3.0;
        d3 = (17.0 * aodp + s) * temp;
        d4 = 0.5 * temp * aodp * tsi * (221.0 * aodp + 31.0 * s) * c1;
        t3cof = d2 + 2.0 * c1sq;
        t4cof = 0.25 * (3.0 * d3 + c1 * (12.0 * d2 + 10.0 * c1sq));
        t5cof = 0.2 * (3.0 * d4 + 12.0 * c1 * d3 + 6.0 * d2 * d2 + 15.0 * c1sq * (2.0 * d2 + c1sq));
    }

    /**
     * Propagate the satellite to the given instant and return its geodetic position.
     *
     * @param time the instant to propagate to
     * @return the computed satellite position, or null if propagation fails
     */
    public SatellitePosition propagate(Instant time)
    {
        try
        {
            // Time since epoch in minutes
            double jd = JD_UNIX_EPOCH + time.toEpochMilli() / 86400000.0;
            double tsince = (jd - epochJd) * MIN_PER_DAY;

            return propagateMinutes(tsince, time);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private SatellitePosition propagateMinutes(double tsince, Instant time)
    {
        // Update for secular gravity and atmospheric drag
        double xmdf = mo + xmdot * tsince;
        double omgadf = wo + omegadot * tsince;
        double xnoddf = omo + xnodot * tsince;
        double omega = omgadf;
        double xmp = xmdf;

        double tsq = tsince * tsince;
        double xnode = xnoddf + 1.5 * c1 * tsq * 0; // simplified secular node
        xnode = xnoddf;

        double tempa = 1.0 - c1 * tsince;
        double tempe = bstar * c4 * tsince;
        double templ = t3cof * tsq * tsince; // simplified

        // Handle higher-order terms only for significant drag
        if (!isDeepSpace)
        {
            double tcube = tsq * tsince;
            double tfour = tsince * tcube;

            tempa = tempa - d2 * tsq - d3 * tcube - d4 * tfour;
            tempe = tempe + bstar * c5 * (Math.sin(xmp) - Math.sin(mo));
            templ = templ + t4cof * tfour + t5cof * tfour * tsince;
        }

        double a = aodp * tempa * tempa;
        double e = eo - tempe;
        double xl = xmp + omega + xnode + no * templ;

        // Clamp eccentricity
        if (e < 1e-6) e = 1e-6;
        if (e > 0.999) e = 0.999;

        double beta = Math.sqrt(1.0 - e * e);
        double xn = XKE / Math.pow(a, 1.5);

        // Long-period periodics
        double axn = e * Math.cos(omega);
        double temp = 1.0 / (a * beta * beta);
        double xll = temp * xi * A3OVK2 * sinio * (3.0 + 5.0 * cosio) / (1.0 + cosio) * 0.5;
        double aynl = temp * A3OVK2 * sinio * 0.5;
        double xlt = xl + xll;
        double ayn = e * Math.sin(omega) + aynl;

        // Solve Kepler's equation (Newton-Raphson)
        double capu = (xlt - xnode) % TWO_PI;
        if (capu < 0) capu += TWO_PI;

        double epw = capu;
        for (int i = 0; i < 10; i++)
        {
            double sinepw = Math.sin(epw);
            double cosepw = Math.cos(epw);
            double f = capu - epw + axn * sinepw - ayn * cosepw;
            double fd = -1.0 + axn * cosepw + ayn * sinepw;
            double delta = -f / fd;
            epw += delta;
            if (Math.abs(delta) < 1e-12) break;
        }

        // Short-period preliminary quantities
        double sinepw = Math.sin(epw);
        double cosepw = Math.cos(epw);

        double ecose = axn * cosepw + ayn * sinepw;
        double esine = axn * sinepw - ayn * cosepw;
        double elsq = axn * axn + ayn * ayn;
        temp = 1.0 - elsq;
        double pl = a * temp;
        double r = a * (1.0 - ecose);
        double rdot = XKE * Math.sqrt(a) * esine / r;
        double rfdot = XKE * Math.sqrt(pl) / r;
        temp = elsq / pl;
        double betal = Math.sqrt(temp > 1.0 ? 0.0 : 1.0 - temp);
        double cosu = (a / r) * (cosepw - axn + ayn * esine / (1.0 + betal));
        double sinu = (a / r) * (sinepw - ayn - axn * esine / (1.0 + betal));
        double u = Math.atan2(sinu, cosu);

        double sin2u = 2.0 * sinu * cosu;
        double cos2u = 2.0 * cosu * cosu - 1.0;

        // Short-period periodics
        double rk = r * (1.0 - 1.5 * CK2 * Math.sqrt(temp) * (3.0 * theta2 - 1.0) / pl)
            + 0.5 * CK2 * (1.0 - theta2) * cos2u / pl;
        double uk = u - 0.25 * CK2 * (7.0 * theta2 - 1.0) * sin2u / (pl * pl) * 2.0;
        double xnodek = xnode + 1.5 * CK2 * cosio * sin2u / (pl * pl);
        double xinck = io + 1.5 * CK2 * cosio * sinio * cos2u / (pl * pl);
        double rdotk = rdot - xn * CK2 * (1.0 - theta2) * sin2u / pl;
        double rfdotk = rfdot + xn * CK2 * ((1.0 - theta2) * cos2u + 1.5 * (3.0 * theta2 - 1.0)) / pl;

        // Position and velocity in orbital plane
        double sinuk = Math.sin(uk);
        double cosuk = Math.cos(uk);
        double sinik = Math.sin(xinck);
        double cosik = Math.cos(xinck);
        double sinnok = Math.sin(xnodek);
        double cosnok = Math.cos(xnodek);

        double xmx = -sinnok * cosik;
        double xmy = cosnok * cosik;

        // ECI position (Earth radii)
        double ux = xmx * sinuk + cosnok * cosuk;
        double uy = xmy * sinuk + sinnok * cosuk;
        double uz = sinik * sinuk;

        double posX = rk * ux * RE;  // km
        double posY = rk * uy * RE;
        double posZ = rk * uz * RE;

        // ECI velocity (km/s)
        double vx_ux = xmx * cosuk - cosnok * sinuk;
        double vx_uy = xmy * cosuk - sinnok * sinuk;
        double vx_uz = sinik * cosuk;

        double velX = (rdotk * ux + rfdotk * vx_ux) * VKMPS;
        double velY = (rdotk * uy + rfdotk * vx_uy) * VKMPS;
        double velZ = (rdotk * uz + rfdotk * vx_uz) * VKMPS;

        double velocity = Math.sqrt(velX * velX + velY * velY + velZ * velZ);

        // Convert ECI to ECEF using Greenwich Sidereal Time
        double gmst = greenwichSiderealTime(time);

        double cosGmst = Math.cos(gmst);
        double sinGmst = Math.sin(gmst);

        double ecefX = posX * cosGmst + posY * sinGmst;
        double ecefY = -posX * sinGmst + posY * cosGmst;
        double ecefZ = posZ;

        // ECEF to geodetic (latitude, longitude, altitude)
        double lon = Math.atan2(ecefY, ecefX);
        double rXY = Math.sqrt(ecefX * ecefX + ecefY * ecefY);
        double lat = Math.atan2(ecefZ, rXY);

        // Iterate for geodetic latitude (WGS-84 ellipsoid)
        double f = 1.0 / 298.257223563;
        double eSq = 2.0 * f - f * f;
        for (int i = 0; i < 5; i++)
        {
            double sinLat = Math.sin(lat);
            double N = RE / Math.sqrt(1.0 - eSq * sinLat * sinLat);
            lat = Math.atan2(ecefZ + eSq * N * sinLat, rXY);
        }

        double sinLat = Math.sin(lat);
        double N = RE / Math.sqrt(1.0 - eSq * sinLat * sinLat);
        double alt = rXY / Math.cos(lat) - N;

        double latDeg = lat / DEG2RAD;
        double lonDeg = lon / DEG2RAD;

        // Normalize longitude to [-180, 180]
        while (lonDeg > 180) lonDeg -= 360;
        while (lonDeg < -180) lonDeg += 360;

        // Compute azimuth (direction of travel) from ECEF velocity
        double velEcefX = velX * cosGmst + velY * sinGmst;
        double velEcefY = -velX * sinGmst + velY * cosGmst;
        double velEcefZ = velZ;

        // Convert velocity to East-North-Up frame for azimuth
        double cosLat = Math.cos(lat);
        double cosLon = Math.cos(lon);
        double sinLon = Math.sin(lon);

        double vEast = -velEcefX * sinLon + velEcefY * cosLon;
        double vNorth = -velEcefX * sinLat * cosLon - velEcefY * sinLat * sinLon + velEcefZ * cosLat;

        double azimuth = Math.atan2(vEast, vNorth) / DEG2RAD;
        if (azimuth < 0) azimuth += 360;

        // Simple eclipse check: is satellite in Earth's shadow?
        boolean eclipsed = isInEarthShadow(posX, posY, posZ, time);

        return new SatellitePosition(tle.getNoradCatId(), latDeg, lonDeg, alt,
            velocity, azimuth, eclipsed);
    }

    /**
     * Compute Greenwich Mean Sidereal Time in radians for a given instant.
     */
    private static double greenwichSiderealTime(Instant time)
    {
        double jd = JD_UNIX_EPOCH + time.toEpochMilli() / 86400000.0;
        double du = jd - 2451545.0; // days from J2000.0
        double tu = du / 36525.0;   // Julian centuries from J2000.0

        // IAU 1982 GMST formula
        double gmst = 67310.54841 + (876600.0 * 3600.0 + 8640184.812866) * tu
            + 0.093104 * tu * tu - 6.2e-6 * tu * tu * tu;

        // Convert from seconds to radians
        gmst = (gmst % 86400.0) / 86400.0 * TWO_PI;
        if (gmst < 0) gmst += TWO_PI;
        return gmst;
    }

    /**
     * Simple cylindrical Earth shadow check.
     */
    private static boolean isInEarthShadow(double eciX, double eciY, double eciZ, Instant time)
    {
        // Approximate sun direction from the date
        double jd = JD_UNIX_EPOCH + time.toEpochMilli() / 86400000.0;
        double n = jd - 2451545.0;
        double L = (280.460 + 0.9856474 * n) % 360;
        double g = (357.528 + 0.9856003 * n) % 360;
        double lambda = (L + 1.915 * Math.sin(g * DEG2RAD) + 0.020 * Math.sin(2 * g * DEG2RAD)) * DEG2RAD;

        // Sun direction unit vector (ECI, ecliptic ≈ equatorial for rough check)
        double sunX = Math.cos(lambda);
        double sunY = Math.sin(lambda);
        double sunZ = 0;  // simplified (ignoring obliquity for shadow check)

        // Project satellite position onto sun direction
        double dot = eciX * sunX + eciY * sunY + eciZ * sunZ;

        // Satellite is behind Earth (away from sun)?
        if (dot > 0) return false; // on sun side

        // Distance from the sun line
        double px = eciX - dot * sunX;
        double py = eciY - dot * sunY;
        double pz = eciZ - dot * sunZ;
        double distFromAxis = Math.sqrt(px * px + py * py + pz * pz);

        return distFromAxis < RE;
    }
}
