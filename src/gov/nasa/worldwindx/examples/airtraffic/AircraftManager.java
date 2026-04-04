/*
 * WorldWind Reforged — Air Traffic Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: manages aircraft renderables — 3D placemarks at real altitude, smooth
 * interpolation between updates, altitude-colored contrail paths, speed leaders,
 * emergency pulse animation, and vertical drop lines.
 */
package gov.nasa.worldwindx.examples.airtraffic;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.Offset;
import gov.nasa.worldwind.render.Path;
import gov.nasa.worldwind.render.PointPlacemark;
import gov.nasa.worldwind.render.PointPlacemarkAttributes;
import gov.nasa.worldwind.render.ShapeAttributes;

/**
 * Manages the set of aircraft placemarks, 3D contrail paths, speed-leader lines,
 * and vertical drop lines. Supports smooth interpolation between API updates and
 * altitude-based colour grading.
 *
 * seaglassfoundry.com
 */
public class AircraftManager
{
    private static final int MAX_TRAIL_POINTS = 80;
    private static final long STALE_TIMEOUT_MS = 90_000; // 90 seconds
    private static final double FEET_TO_METERS = 0.3048;
    private static final double KTS_TO_MS = 0.514444;
    /** Speed leader line length in seconds of flight time. */
    private static final double LEADER_SECONDS = 60.0;

    private final WorldWindow wwd;
    private final RenderableLayer aircraftLayer;
    private final RenderableLayer trailLayer;
    private final RenderableLayer leaderLayer;
    private final RenderableLayer dropLineLayer;

    // ── Per-aircraft state ────────────────────────────────────────────────────

    private final Map<String, PointPlacemark> placemarks = new ConcurrentHashMap<>();
    private final Map<String, Path> trails = new ConcurrentHashMap<>();
    private final Map<String, List<Position>> trailPositions = new ConcurrentHashMap<>();
    private final Map<String, Path> leaders = new ConcurrentHashMap<>();
    private final Map<String, Path> dropLines = new ConcurrentHashMap<>();
    private final Map<String, AircraftPosition> latestData = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();

    // ── Interpolation state ───────────────────────────────────────────────────

    /** Previous position for interpolation. */
    private final Map<String, AircraftPosition> prevData = new ConcurrentHashMap<>();

    // ── Filters & options ─────────────────────────────────────────────────────

    private volatile Predicate<String> filterPredicate = hex -> true;
    private volatile boolean showTrails = true;
    private volatile boolean showLeaders = true;
    private volatile boolean showDropLines = true;
    private volatile boolean showOnGround = true;
    private volatile double minAltitudeFeet = 0;
    private volatile double maxAltitudeFeet = 60_000;

    /** Currently followed aircraft (hex), or null. */
    private volatile String followHex = null;

    /** Emergency pulse animation counter (0–1, cycles). */
    private float emergencyPulse = 0f;

    public AircraftManager(WorldWindow wwd,
        RenderableLayer aircraftLayer, RenderableLayer trailLayer,
        RenderableLayer leaderLayer, RenderableLayer dropLineLayer)
    {
        this.wwd = wwd;
        this.aircraftLayer = aircraftLayer;
        this.trailLayer = trailLayer;
        this.leaderLayer = leaderLayer;
        this.dropLineLayer = dropLineLayer;
    }

    // ── Bulk update from data source ──────────────────────────────────────────

    public void updateAircraft(Map<String, AircraftPosition> aircraft)
    {
        long now = System.currentTimeMillis();

        for (Map.Entry<String, AircraftPosition> entry : aircraft.entrySet())
        {
            String hex = entry.getKey();
            AircraftPosition ac = entry.getValue();

            // Save previous for interpolation
            AircraftPosition prev = latestData.get(hex);
            if (prev != null) prevData.put(hex, prev);

            latestData.put(hex, ac);
            lastSeen.put(hex, now);

            double altMeters = ac.getAltitudeMeters();
            Position pos = Position.fromDegrees(ac.getLat(), ac.getLon(), altMeters);
            boolean visible = shouldShow(hex, ac);

            // ── Placemark (3D at altitude) ────────────────────────────────
            PointPlacemark pm = placemarks.get(hex);
            if (pm == null)
            {
                pm = createPlacemark(hex, ac, pos);
                placemarks.put(hex, pm);
                aircraftLayer.addRenderable(pm);
            }
            else
            {
                pm.setPosition(pos);
                stylePlacemark(pm, ac);
            }
            pm.setVisible(visible);

            // ── Contrail path (3D at altitude) ────────────────────────────
            if (showTrails && visible)
            {
                List<Position> posList = trailPositions.computeIfAbsent(hex, k -> new ArrayList<>());
                posList.add(pos);
                if (posList.size() > MAX_TRAIL_POINTS)
                    posList.remove(0);

                Path trail = trails.get(hex);
                if (trail == null)
                {
                    trail = createTrailPath(ac, posList);
                    trails.put(hex, trail);
                    trailLayer.addRenderable(trail);
                }
                else
                {
                    trail.setPositions(posList);
                    trail.setPositionColors(new AltitudeTrailColors(posList));
                }
                trail.setVisible(posList.size() > 1);
            }

            // ── Speed leader line ─────────────────────────────────────────
            if (showLeaders && visible && ac.getGroundSpeed() > 10)
            {
                updateLeaderLine(hex, ac, pos);
            }
            else
            {
                Path leader = leaders.get(hex);
                if (leader != null) leader.setVisible(false);
            }

            // ── Vertical drop line to ground ──────────────────────────────
            if (showDropLines && visible && altMeters > 100)
            {
                updateDropLine(hex, pos);
            }
            else
            {
                Path drop = dropLines.get(hex);
                if (drop != null) drop.setVisible(false);
            }
        }

        removeStale(now);
        wwd.redraw();
    }

    // ── Smooth interpolation tick ─────────────────────────────────────────────

    /**
     * Called on a fast timer (~200ms) to interpolate aircraft positions between
     * API updates, giving smooth movement.
     *
     * @param fraction how far between last update and next expected update (0–1)
     */
    public void interpolateTick(double fraction)
    {
        fraction = Math.max(0, Math.min(fraction, 1.5)); // clamp with slight overshoot

        boolean anyMoved = false;
        for (Map.Entry<String, AircraftPosition> entry : latestData.entrySet())
        {
            String hex = entry.getKey();
            AircraftPosition curr = entry.getValue();
            AircraftPosition prev = prevData.get(hex);

            if (prev == null || curr == prev) continue;

            PointPlacemark pm = placemarks.get(hex);
            if (pm == null || !pm.isVisible()) continue;

            // Linear interpolation from previous to current + extrapolation
            double lat = prev.getLat() + (curr.getLat() - prev.getLat()) * fraction;
            double lon = prev.getLon() + (curr.getLon() - prev.getLon()) * fraction;
            double alt = prev.getAltitudeMeters() + (curr.getAltitudeMeters() - prev.getAltitudeMeters()) * fraction;

            pm.setPosition(Position.fromDegrees(lat, lon, Math.max(0, alt)));
            anyMoved = true;
        }

        // Emergency pulse animation
        emergencyPulse = (emergencyPulse + 0.08f) % 1.0f;

        if (anyMoved)
            wwd.redraw();
    }

    // ── Follow camera ─────────────────────────────────────────────────────────

    /** Set the hex of the aircraft to follow, or null to stop following. */
    public void setFollowHex(String hex) { this.followHex = hex; }
    public String getFollowHex() { return followHex; }

    /** Returns the current position of the followed aircraft, or null. */
    public Position getFollowPosition()
    {
        if (followHex == null) return null;
        AircraftPosition ac = latestData.get(followHex);
        if (ac == null) return null;
        return Position.fromDegrees(ac.getLat(), ac.getLon(), ac.getAltitudeMeters());
    }

    public AircraftPosition getFollowAircraft()
    {
        return followHex != null ? latestData.get(followHex) : null;
    }

    // ── Placemark creation / styling ──────────────────────────────────────────

    private PointPlacemark createPlacemark(String hex, AircraftPosition ac, Position pos)
    {
        PointPlacemark pm = new PointPlacemark(pos);
        pm.setValue("hex", hex);
        pm.setAltitudeMode(WorldWind.ABSOLUTE);
        stylePlacemark(pm, ac);
        return pm;
    }

    private void stylePlacemark(PointPlacemark pm, AircraftPosition ac)
    {
        AircraftCategory cat = ac.getCategory();
        Color color;

        if (ac.isEmergency())
        {
            // Pulsing red for emergency
            float pulse = (float) (0.5 + 0.5 * Math.sin(emergencyPulse * Math.PI * 2));
            color = new Color(255, (int)(60 * pulse), (int)(60 * pulse));
        }
        else
        {
            color = AircraftCategory.altitudeColor(ac.getAltitudeFeet());
        }

        PointPlacemarkAttributes attrs = new PointPlacemarkAttributes();
        attrs.setImageAddress(cat.getIconPath());
        attrs.setScale(ac.isEmergency() ? 1.0 : 0.65);
        attrs.setHeading(ac.getEffectiveHeading());
        attrs.setHeadingReference(AVKey.RELATIVE_TO_GLOBE);
        attrs.setImageOffset(new Offset(0.5, 0.5, AVKey.FRACTION, AVKey.FRACTION));
        attrs.setLabelOffset(new Offset(0.9, 0.5, AVKey.FRACTION, AVKey.FRACTION));
        attrs.setLabelFont(new Font("SansSerif", Font.BOLD, 11));
        attrs.setLabelColor(colorToHex(color));
        pm.setAttributes(attrs);

        // Build label
        StringBuilder label = new StringBuilder();
        label.append(ac.getDisplayLabel());
        if (ac.getAltitudeFeet() > 0)
            label.append(String.format("  FL%03d", (int)(ac.getAltitudeFeet() / 100)));
        if (ac.isEmergency())
            label.append("  \u26A0 EMERGENCY");
        else if (ac.getVerticalRate() > 300)
            label.append(" \u2191");
        else if (ac.getVerticalRate() < -300)
            label.append(" \u2193");

        pm.setLabelText(label.toString());
    }

    // ── Contrail path ─────────────────────────────────────────────────────────

    private Path createTrailPath(AircraftPosition ac, List<Position> positions)
    {
        Path path = new Path(positions);
        path.setAltitudeMode(WorldWind.ABSOLUTE);
        path.setShowPositions(false);
        path.setFollowTerrain(false);
        path.setPathType(AVKey.LINEAR);

        ShapeAttributes attrs = new BasicShapeAttributes();
        attrs.setOutlineWidth(2);
        attrs.setOutlineOpacity(0.7);
        path.setAttributes(attrs);

        path.setPositionColors(new AltitudeTrailColors(positions));
        return path;
    }

    // ── Speed leader line ─────────────────────────────────────────────────────

    private void updateLeaderLine(String hex, AircraftPosition ac, Position pos)
    {
        // Project position forward by LEADER_SECONDS
        double speedMs = ac.getGroundSpeed() * KTS_TO_MS;
        double dist = speedMs * LEADER_SECONDS; // metres
        double trackRad = Math.toRadians(ac.getTrack());
        double dLat = (dist * Math.cos(trackRad)) / 111_320.0;
        double dLon = (dist * Math.sin(trackRad)) / (111_320.0 * Math.cos(Math.toRadians(ac.getLat())));
        double futureAlt = ac.getAltitudeMeters() + ac.getVerticalRate() * FEET_TO_METERS * (LEADER_SECONDS / 60.0);

        Position futurePos = Position.fromDegrees(
            ac.getLat() + dLat, ac.getLon() + dLon, Math.max(0, futureAlt));

        List<Position> pts = List.of(pos, futurePos);

        Path leader = leaders.get(hex);
        if (leader == null)
        {
            leader = new Path(pts);
            leader.setAltitudeMode(WorldWind.ABSOLUTE);
            leader.setFollowTerrain(false);
            leader.setPathType(AVKey.LINEAR);
            leader.setShowPositions(false);

            ShapeAttributes attrs = new BasicShapeAttributes();
            attrs.setOutlineMaterial(new Material(new Color(255, 255, 255, 100)));
            attrs.setOutlineOpacity(0.35);
            attrs.setOutlineWidth(1);
            leader.setAttributes(attrs);

            leaders.put(hex, leader);
            leaderLayer.addRenderable(leader);
        }
        else
        {
            leader.setPositions(pts);
        }
        leader.setVisible(true);
    }

    // ── Vertical drop line ────────────────────────────────────────────────────

    private void updateDropLine(String hex, Position airPos)
    {
        Position groundPos = Position.fromDegrees(airPos.getLatitude().degrees,
            airPos.getLongitude().degrees, 0);
        List<Position> pts = List.of(airPos, groundPos);

        Path drop = dropLines.get(hex);
        if (drop == null)
        {
            drop = new Path(pts);
            drop.setAltitudeMode(WorldWind.ABSOLUTE);
            drop.setFollowTerrain(false);
            drop.setPathType(AVKey.LINEAR);
            drop.setShowPositions(false);

            ShapeAttributes attrs = new BasicShapeAttributes();
            attrs.setOutlineMaterial(new Material(new Color(255, 255, 255, 40)));
            attrs.setOutlineOpacity(0.15);
            attrs.setOutlineWidth(1);
            drop.setAttributes(attrs);

            dropLines.put(hex, drop);
            dropLineLayer.addRenderable(drop);
        }
        else
        {
            drop.setPositions(pts);
        }
        drop.setVisible(true);
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    private boolean shouldShow(String hex, AircraftPosition ac)
    {
        if (!showOnGround && ac.isOnGround()) return false;
        if (ac.getAltitudeFeet() < minAltitudeFeet) return false;
        if (ac.getAltitudeFeet() > maxAltitudeFeet) return false;
        return filterPredicate.test(hex);
    }

    public void setFilterPredicate(Predicate<String> p) { filterPredicate = p; applyVisibility(); }
    public void setShowTrails(boolean v)    { showTrails = v;    applyVisibility(); }
    public void setShowLeaders(boolean v)   { showLeaders = v;   applyVisibility(); }
    public void setShowDropLines(boolean v) { showDropLines = v; applyVisibility(); }
    public void setShowOnGround(boolean v)  { showOnGround = v;  applyVisibility(); }
    public void setAltitudeRange(double minFt, double maxFt)
    {
        minAltitudeFeet = minFt;
        maxAltitudeFeet = maxFt;
        applyVisibility();
    }

    public boolean isShowTrails()    { return showTrails; }
    public boolean isShowLeaders()   { return showLeaders; }
    public boolean isShowDropLines() { return showDropLines; }
    public boolean isShowOnGround()  { return showOnGround; }

    private void applyVisibility()
    {
        for (Map.Entry<String, PointPlacemark> entry : placemarks.entrySet())
        {
            String hex = entry.getKey();
            AircraftPosition ac = latestData.get(hex);
            boolean vis = ac != null && shouldShow(hex, ac);
            entry.getValue().setVisible(vis);

            Path trail = trails.get(hex);
            if (trail != null) trail.setVisible(vis && showTrails);
            Path leader = leaders.get(hex);
            if (leader != null) leader.setVisible(vis && showLeaders);
            Path drop = dropLines.get(hex);
            if (drop != null) drop.setVisible(vis && showDropLines && ac != null && ac.getAltitudeFeet() > 100);
        }
        wwd.redraw();
    }

    // ── Stale cleanup ─────────────────────────────────────────────────────────

    private void removeStale(long now)
    {
        Iterator<Map.Entry<String, Long>> it = lastSeen.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<String, Long> entry = it.next();
            if (now - entry.getValue() > STALE_TIMEOUT_MS)
            {
                String hex = entry.getKey();
                it.remove();
                latestData.remove(hex);
                prevData.remove(hex);
                removePlacemark(hex);
            }
        }
    }

    private void removePlacemark(String hex)
    {
        PointPlacemark pm = placemarks.remove(hex);
        if (pm != null) aircraftLayer.removeRenderable(pm);
        Path trail = trails.remove(hex);
        if (trail != null) trailLayer.removeRenderable(trail);
        trailPositions.remove(hex);
        Path leader = leaders.remove(hex);
        if (leader != null) leaderLayer.removeRenderable(leader);
        Path drop = dropLines.remove(hex);
        if (drop != null) dropLineLayer.removeRenderable(drop);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public int getTotalCount()   { return placemarks.size(); }
    public int getAirborneCount()
    {
        int n = 0;
        for (AircraftPosition ac : latestData.values())
            if (!ac.isOnGround()) n++;
        return n;
    }
    public int getEmergencyCount()
    {
        int n = 0;
        for (AircraftPosition ac : latestData.values())
            if (ac.isEmergency()) n++;
        return n;
    }
    public int getMilitaryCount()
    {
        int n = 0;
        for (AircraftPosition ac : latestData.values())
            if (ac.isMilitary()) n++;
        return n;
    }
    public int getVisibleCount()
    {
        int n = 0;
        for (Map.Entry<String, PointPlacemark> e : placemarks.entrySet())
            if (e.getValue().isVisible()) n++;
        return n;
    }

    public AircraftPosition getAircraft(String hex) { return latestData.get(hex); }
    public Set<String> getTrackedHexes() { return latestData.keySet(); }
    public Map<String, AircraftPosition> getAllAircraft() { return latestData; }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static String colorToHex(Color c)
    {
        return String.format("%02x%02x%02x%02x", c.getAlpha(), c.getRed(), c.getGreen(), c.getBlue());
    }

    // ── Altitude-graded trail colours ─────────────────────────────────────────

    /**
     * Colours each trail vertex by its altitude AND fades older positions.
     * Newest = full opacity, oldest = nearly transparent.
     */
    private static class AltitudeTrailColors implements Path.PositionColors
    {
        private final List<Position> positions;

        AltitudeTrailColors(List<Position> positions)
        {
            this.positions = positions;
        }

        @Override
        public Color getColor(Position position, int ordinal)
        {
            double altFeet = position.getElevation() / FEET_TO_METERS;
            Color base = AircraftCategory.altitudeColor(altFeet);
            float ageFraction = (positions.size() <= 1) ? 1f : (float) ordinal / (positions.size() - 1);
            float alpha = Math.max(0.05f, ageFraction * 0.8f);
            return new Color(base.getRed(), base.getGreen(), base.getBlue(), (int)(alpha * 255));
        }
    }
}
