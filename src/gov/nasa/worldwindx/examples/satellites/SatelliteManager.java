/*
 * WorldWind Reforged — Satellite Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: manages satellite renderables — 3D placemarks at orbital altitude,
 * full orbit paths with altitude-gradient colouring, ground tracks, footprint circles,
 * drop lines, speed leaders, filtering, and follow camera.
 */
package gov.nasa.worldwindx.examples.satellites;

import java.awt.Color;
import java.awt.Font;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
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
import gov.nasa.worldwind.pick.PickSupport;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.Offset;
import gov.nasa.worldwind.render.Path;
import gov.nasa.worldwind.render.PointPlacemark;
import gov.nasa.worldwind.render.PointPlacemarkAttributes;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwind.render.SurfaceCircle;

/**
 * Manages all satellite renderables: 3D placemarks at orbital altitude, full orbit
 * ellipses with altitude-gradient colouring, ground tracks, radio-horizon footprint
 * circles, vertical drop lines, and speed leader lines.
 * <p>
 * Supports real-time propagation via {@link #propagateTick(Instant)}, category/altitude
 * filtering, level-of-detail based on eye altitude, and follow-camera tracking.
 *
 * seaglassfoundry.com
 */
public class SatelliteManager
{
    private static final double LEADER_SECONDS = 60.0;
    private static final double KM_TO_METERS = 1000.0;

    private final WorldWindow wwd;
    private final RenderableLayer satelliteLayer;
    private final RenderableLayer orbitLayer;
    private final RenderableLayer groundTrackLayer;
    private final RenderableLayer footprintLayer;
    private final RenderableLayer dropLineLayer;
    private final RenderableLayer leaderLayer;

    // ── Per-satellite state ──────────────────────────────────────────────────

    private final Map<Integer, TleRecord> tleData = new ConcurrentHashMap<>();
    private final Map<Integer, Sgp4Propagator> propagators = new ConcurrentHashMap<>();
    private final Map<Integer, SatellitePosition> latestPositions = new ConcurrentHashMap<>();
    private final Map<Integer, PointPlacemark> placemarks = new ConcurrentHashMap<>();
    private final Map<Integer, Path> orbits = new ConcurrentHashMap<>();
    private final Map<Integer, Path> groundTracks = new ConcurrentHashMap<>();
    private final Map<Integer, SurfaceCircle> footprints = new ConcurrentHashMap<>();
    private final Map<Integer, Path> dropLines = new ConcurrentHashMap<>();
    private final Map<Integer, Path> leaders = new ConcurrentHashMap<>();

    // ── Filters & options ────────────────────────────────────────────────────

    private volatile Predicate<Integer> filterPredicate = id -> true;
    private volatile boolean showOrbits = false;
    private volatile boolean showGroundTracks = false;
    private volatile boolean showFootprints = false;
    private volatile boolean showDropLines = false;
    private volatile boolean showLeaders = false;
    private volatile boolean showLabels = true;

    /** Currently followed satellite NORAD ID, or null. */
    private volatile Integer followId = null;

    /** Eye altitude threshold for level-of-detail. */
    private static final double LOD_FAR = 10_000_000;   // > 10,000 km: placemarks only
    private static final double LOD_MED = 2_000_000;    // > 2,000 km: add labels, orbits, tracks

    public SatelliteManager(WorldWindow wwd,
        RenderableLayer satelliteLayer, RenderableLayer orbitLayer,
        RenderableLayer groundTrackLayer, RenderableLayer footprintLayer,
        RenderableLayer dropLineLayer, RenderableLayer leaderLayer)
    {
        this.wwd = wwd;
        this.satelliteLayer = satelliteLayer;
        this.orbitLayer = orbitLayer;
        this.groundTrackLayer = groundTrackLayer;
        this.footprintLayer = footprintLayer;
        this.dropLineLayer = dropLineLayer;
        this.leaderLayer = leaderLayer;
    }

    // ── TLE update (called when new TLE data arrives) ────────────────────────

    /**
     * Update TLE records and create propagators. This does NOT propagate positions —
     * call {@link #propagateTick(Instant)} after this.
     */
    public void updateTLEs(Map<Integer, TleRecord> newTles)
    {
        // Track object names we've already accepted so co-orbiting duplicates
        // (e.g. SOYUZ-MS 28 capsule + booster, PROGRESS-MS debris) don't
        // produce overlapping labels. Keep the lowest NORAD ID (primary object).
        Set<String> seenNames = new HashSet<>();

        // Process entries in NORAD ID order so the primary (lowest ID) wins
        List<Map.Entry<Integer, TleRecord>> sorted = new ArrayList<>(newTles.entrySet());
        sorted.sort(Map.Entry.comparingByKey());

        for (Map.Entry<Integer, TleRecord> entry : sorted)
        {
            int id = entry.getKey();
            TleRecord tle = entry.getValue();

            // Skip ISS co-orbiting module TLEs (NAUKA, ZARYA, etc.) — they all
            // orbit at the same position and produce overlapping labels. Keep only
            // the primary ISS object (NORAD 25544).
            if (id != 25544 && tle.getObjectName().toUpperCase().startsWith("ISS "))
                continue;

            // Skip duplicate object names — CelesTrak lists separate catalogue
            // entries for a spacecraft and its booster/debris with the same name
            String nameKey = tle.getObjectName().toUpperCase();
            if (!nameKey.isEmpty() && !seenNames.add(nameKey))
                continue;

            tleData.put(id, tle);
            propagators.put(id, new Sgp4Propagator(tle));
        }

        // Remove satellites no longer in the dataset
        for (Integer id : new ArrayList<>(tleData.keySet()))
        {
            if (!newTles.containsKey(id))
            {
                removeSatellite(id);
            }
        }
    }

    /**
     * Pre-compute orbit paths and ground tracks for all current satellites.
     * This is expensive — call from a background thread.
     *
     * @param refTime the reference time to centre orbits around
     */
    public void computeOrbits(Instant refTime)
    {
        for (Map.Entry<Integer, Sgp4Propagator> entry : propagators.entrySet())
        {
            int id = entry.getKey();
            if (!shouldShow(id)) continue;

            TleRecord tle = tleData.get(id);
            if (tle == null) continue;

            Sgp4Propagator prop = entry.getValue();

            // Orbit path (3D at altitude)
            List<Position> orbitPositions = OrbitCalculator.computeOrbitPath(prop, tle, refTime);
            if (!orbitPositions.isEmpty())
            {
                Path orbit = orbits.get(id);
                if (orbit == null)
                {
                    orbit = createOrbitPath(tle, orbitPositions);
                    orbits.put(id, orbit);
                    orbitLayer.addRenderable(orbit);
                }
                else
                {
                    orbit.setPositions(orbitPositions);
                    orbit.setPositionColors(new OrbitAltitudeColors(orbitPositions));
                }
            }

            // Ground track (surface)
            List<Position> trackPositions = OrbitCalculator.computeGroundTrack(prop, tle, refTime);
            if (!trackPositions.isEmpty())
            {
                Path track = groundTracks.get(id);
                if (track == null)
                {
                    track = createGroundTrackPath(tle, trackPositions);
                    groundTracks.put(id, track);
                    groundTrackLayer.addRenderable(track);
                }
                else
                {
                    track.setPositions(trackPositions);
                }
            }
        }
    }

    // ── Propagation tick (called every ~1 second) ────────────────────────────

    /**
     * Propagate all visible satellites to the given instant and update renderables.
     */
    public void propagateTick(Instant now)
    {
        double eyeAlt = wwd.getView().getEyePosition().getElevation();

        for (Map.Entry<Integer, Sgp4Propagator> entry : propagators.entrySet())
        {
            int id = entry.getKey();
            boolean visible = shouldShow(id);

            SatellitePosition sp = null;
            if (visible)
            {
                sp = entry.getValue().propagate(now);
                if (sp != null)
                    latestPositions.put(id, sp);
            }

            if (sp == null || !visible)
            {
                setVisible(id, false);
                continue;
            }

            Position pos = Position.fromDegrees(sp.getLatDeg(), sp.getLonDeg(),
                sp.getAltitudeMeters());

            // ── Placemark (3D at orbital altitude) ───────────────────────
            PointPlacemark pm = placemarks.get(id);
            if (pm == null)
            {
                pm = createPlacemark(id, sp, pos);
                placemarks.put(id, pm);
                satelliteLayer.addRenderable(pm);
            }
            else
            {
                pm.setPosition(pos);
                stylePlacemark(pm, id, sp, eyeAlt);
            }
            pm.setVisible(true);

            // ── Orbit visibility (LOD-based) ─────────────────────────────
            Path orbit = orbits.get(id);
            if (orbit != null) orbit.setVisible(showOrbits && eyeAlt < LOD_FAR);

            // ── Ground track visibility ──────────────────────────────────
            Path track = groundTracks.get(id);
            if (track != null) track.setVisible(showGroundTracks && eyeAlt < LOD_FAR);

            // ── Footprint circle ─────────────────────────────────────────
            if (showFootprints && eyeAlt < LOD_MED)
            {
                updateFootprint(id, sp, pos);
            }
            else
            {
                SurfaceCircle fp = footprints.get(id);
                if (fp != null) fp.setVisible(false);
            }

            // ── Drop line ────────────────────────────────────────────────
            if (showDropLines && eyeAlt < LOD_FAR)
            {
                updateDropLine(id, pos);
            }
            else
            {
                Path drop = dropLines.get(id);
                if (drop != null) drop.setVisible(false);
            }

            // ── Speed leader ─────────────────────────────────────────────
            if (showLeaders && eyeAlt < LOD_MED && sp.getVelocityKmS() > 0)
            {
                updateLeaderLine(id, sp, pos);
            }
            else
            {
                Path leader = leaders.get(id);
                if (leader != null) leader.setVisible(false);
            }
        }

        wwd.redraw();
    }

    // ── Placemark creation / styling ─────────────────────────────────────────

    private PointPlacemark createPlacemark(int id, SatellitePosition sp, Position pos)
    {
        TleRecord tle = tleData.get(id);
        SatelliteCategory cat = tle != null ? tle.getCategory() : SatelliteCategory.OTHER;

        PointPlacemark pm = new SatellitePlacemark(pos);
        pm.setValue("noradId", id);
        pm.setAltitudeMode(WorldWind.ABSOLUTE);

        // Create attributes once — updated in place by stylePlacemark
        PointPlacemarkAttributes attrs = new PointPlacemarkAttributes();
        attrs.setImageAddress(cat.getIconPath());
        attrs.setScale(cat == SatelliteCategory.SPACE_STATION ? 1.0 : 0.65);
        attrs.setHeading(sp.getAzimuthDeg());
        attrs.setHeadingReference(AVKey.RELATIVE_TO_GLOBE);
        attrs.setImageOffset(new Offset(0.5, 0.5, AVKey.FRACTION, AVKey.FRACTION));
        attrs.setLabelOffset(new Offset(0.9, 0.5, AVKey.FRACTION, AVKey.FRACTION));
        attrs.setLabelFont(new Font("SansSerif", Font.BOLD, 11));
        attrs.setLabelColor(colorToHex(cat.getColor()));
        pm.setAttributes(attrs);

        return pm;
    }

    private void stylePlacemark(PointPlacemark pm, int id, SatellitePosition sp, double eyeAlt)
    {
        // Update heading in place — no new attributes object per tick
        PointPlacemarkAttributes attrs = pm.getAttributes();
        attrs.setHeading(sp.getAzimuthDeg());

        // Labels by distance — always show for space stations and followed satellite
        if (showLabels && (eyeAlt < LOD_MED
            || tleData.containsKey(id) && tleData.get(id).getCategory() == SatelliteCategory.SPACE_STATION
            || id == (followId != null ? followId : -1)))
        {
            TleRecord tle = tleData.get(id);
            String label = tle != null ? tle.getDisplayName() : String.valueOf(id);
            pm.setLabelText(String.format("%s  %.0f km", label, sp.getAltitudeKm()));
        }
        else
        {
            pm.setLabelText(null);
        }
    }

    // ── Orbit path creation ──────────────────────────────────────────────────

    private Path createOrbitPath(TleRecord tle, List<Position> positions)
    {
        Path path = new Path(positions);
        path.setAltitudeMode(WorldWind.ABSOLUTE);
        path.setShowPositions(false);
        path.setFollowTerrain(false);
        path.setPathType(AVKey.LINEAR);

        ShapeAttributes attrs = new BasicShapeAttributes();
        attrs.setOutlineWidth(1.5);
        attrs.setOutlineOpacity(0.6);
        path.setAttributes(attrs);

        path.setPositionColors(new OrbitAltitudeColors(positions));
        return path;
    }

    // ── Ground track creation ────────────────────────────────────────────────

    private Path createGroundTrackPath(TleRecord tle, List<Position> positions)
    {
        Path path = new Path(positions);
        path.setAltitudeMode(WorldWind.CLAMP_TO_GROUND);
        path.setSurfacePath(true);
        path.setShowPositions(false);
        path.setPathType(AVKey.LINEAR);

        Color catColor = tle.getCategory().getColor();
        ShapeAttributes attrs = new BasicShapeAttributes();
        attrs.setOutlineMaterial(new Material(catColor));
        attrs.setOutlineOpacity(0.35);
        attrs.setOutlineWidth(1.5);
        path.setAttributes(attrs);

        return path;
    }

    // ── Footprint circle ─────────────────────────────────────────────────────

    private void updateFootprint(int id, SatellitePosition sp, Position pos)
    {
        double radiusMeters = OrbitCalculator.computeFootprintRadiusMeters(sp.getAltitudeKm());
        if (radiusMeters <= 0) return;

        SurfaceCircle fp = footprints.get(id);
        if (fp == null)
        {
            TleRecord tle = tleData.get(id);
            Color catColor = (tle != null ? tle.getCategory() : SatelliteCategory.OTHER).getColor();

            ShapeAttributes attrs = new BasicShapeAttributes();
            attrs.setInteriorMaterial(new Material(catColor));
            attrs.setInteriorOpacity(0.08);
            attrs.setOutlineMaterial(new Material(catColor));
            attrs.setOutlineOpacity(0.25);
            attrs.setOutlineWidth(1);
            attrs.setDrawInterior(true);
            attrs.setDrawOutline(true);

            fp = new SurfaceCircle(attrs,
                gov.nasa.worldwind.geom.LatLon.fromDegrees(sp.getLatDeg(), sp.getLonDeg()),
                radiusMeters);
            footprints.put(id, fp);
            footprintLayer.addRenderable(fp);
        }
        else
        {
            fp.setCenter(gov.nasa.worldwind.geom.LatLon.fromDegrees(sp.getLatDeg(), sp.getLonDeg()));
            fp.setRadius(radiusMeters);
        }
        fp.setVisible(true);
    }

    // ── Drop line (vertical to ground) ───────────────────────────────────────

    private void updateDropLine(int id, Position airPos)
    {
        Position groundPos = Position.fromDegrees(airPos.getLatitude().degrees,
            airPos.getLongitude().degrees, 0);
        List<Position> pts = List.of(airPos, groundPos);

        Path drop = dropLines.get(id);
        if (drop == null)
        {
            drop = new Path(pts);
            drop.setAltitudeMode(WorldWind.ABSOLUTE);
            drop.setFollowTerrain(false);
            drop.setPathType(AVKey.LINEAR);
            drop.setShowPositions(false);

            ShapeAttributes attrs = new BasicShapeAttributes();
            attrs.setOutlineMaterial(new Material(new Color(255, 255, 255, 40)));
            attrs.setOutlineOpacity(0.1);
            attrs.setOutlineWidth(1);
            drop.setAttributes(attrs);

            dropLines.put(id, drop);
            dropLineLayer.addRenderable(drop);
        }
        else
        {
            drop.setPositions(pts);
        }
        drop.setVisible(true);
    }

    // ── Speed leader line ────────────────────────────────────────────────────

    private void updateLeaderLine(int id, SatellitePosition sp, Position pos)
    {
        double speedMs = sp.getVelocityKmS() * 1000.0;
        double dist = speedMs * LEADER_SECONDS;
        double azRad = Math.toRadians(sp.getAzimuthDeg());
        double dLat = (dist * Math.cos(azRad)) / 111_320.0;
        double dLon = (dist * Math.sin(azRad)) / (111_320.0 * Math.cos(Math.toRadians(sp.getLatDeg())));

        Position futurePos = Position.fromDegrees(
            sp.getLatDeg() + dLat, sp.getLonDeg() + dLon, sp.getAltitudeMeters());

        List<Position> pts = List.of(pos, futurePos);

        Path leader = leaders.get(id);
        if (leader == null)
        {
            leader = new Path(pts);
            leader.setAltitudeMode(WorldWind.ABSOLUTE);
            leader.setFollowTerrain(false);
            leader.setPathType(AVKey.LINEAR);
            leader.setShowPositions(false);

            ShapeAttributes attrs = new BasicShapeAttributes();
            attrs.setOutlineMaterial(new Material(new Color(255, 255, 255, 100)));
            attrs.setOutlineOpacity(0.3);
            attrs.setOutlineWidth(1);
            leader.setAttributes(attrs);

            leaders.put(id, leader);
            leaderLayer.addRenderable(leader);
        }
        else
        {
            leader.setPositions(pts);
        }
        leader.setVisible(true);
    }

    // ── Filtering ────────────────────────────────────────────────────────────

    private boolean shouldShow(int id)
    {
        return filterPredicate.test(id);
    }

    private void setVisible(int id, boolean vis)
    {
        PointPlacemark pm = placemarks.get(id);
        if (pm != null) pm.setVisible(vis);
        Path orbit = orbits.get(id);
        if (orbit != null) orbit.setVisible(vis);
        Path track = groundTracks.get(id);
        if (track != null) track.setVisible(vis);
        SurfaceCircle fp = footprints.get(id);
        if (fp != null) fp.setVisible(vis);
        Path drop = dropLines.get(id);
        if (drop != null) drop.setVisible(vis);
        Path leader = leaders.get(id);
        if (leader != null) leader.setVisible(vis);
    }

    public void setFilterPredicate(Predicate<Integer> p) { filterPredicate = p; applyVisibility(); }
    public void setShowOrbits(boolean v)       { showOrbits = v;       applyVisibility(); }
    public void setShowGroundTracks(boolean v)  { showGroundTracks = v;  applyVisibility(); }
    public void setShowFootprints(boolean v)    { showFootprints = v;    applyVisibility(); }
    public void setShowDropLines(boolean v)     { showDropLines = v;     applyVisibility(); }
    public void setShowLeaders(boolean v)       { showLeaders = v;       applyVisibility(); }
    public void setShowLabels(boolean v)        { showLabels = v;        applyVisibility(); }

    public boolean isShowOrbits()       { return showOrbits; }
    public boolean isShowGroundTracks() { return showGroundTracks; }
    public boolean isShowFootprints()   { return showFootprints; }
    public boolean isShowDropLines()    { return showDropLines; }
    public boolean isShowLeaders()      { return showLeaders; }
    public boolean isShowLabels()       { return showLabels; }

    private void applyVisibility()
    {
        for (Map.Entry<Integer, PointPlacemark> entry : placemarks.entrySet())
        {
            int id = entry.getKey();
            boolean vis = shouldShow(id);
            entry.getValue().setVisible(vis);
            Path orbit = orbits.get(id);
            if (orbit != null) orbit.setVisible(vis && showOrbits);
            Path track = groundTracks.get(id);
            if (track != null) track.setVisible(vis && showGroundTracks);
            SurfaceCircle fp = footprints.get(id);
            if (fp != null) fp.setVisible(vis && showFootprints);
            Path drop = dropLines.get(id);
            if (drop != null) drop.setVisible(vis && showDropLines);
            Path leader = leaders.get(id);
            if (leader != null) leader.setVisible(vis && showLeaders);
        }
        wwd.redraw();
    }

    // ── Remove satellite ─────────────────────────────────────────────────────

    private void removeSatellite(int id)
    {
        tleData.remove(id);
        propagators.remove(id);
        latestPositions.remove(id);

        PointPlacemark pm = placemarks.remove(id);
        if (pm != null) satelliteLayer.removeRenderable(pm);
        Path orbit = orbits.remove(id);
        if (orbit != null) orbitLayer.removeRenderable(orbit);
        Path track = groundTracks.remove(id);
        if (track != null) groundTrackLayer.removeRenderable(track);
        SurfaceCircle fp = footprints.remove(id);
        if (fp != null) footprintLayer.removeRenderable(fp);
        Path drop = dropLines.remove(id);
        if (drop != null) dropLineLayer.removeRenderable(drop);
        Path leader = leaders.remove(id);
        if (leader != null) leaderLayer.removeRenderable(leader);
    }

    // ── Follow camera ────────────────────────────────────────────────────────

    public void setFollowId(Integer id) { this.followId = id; }
    public Integer getFollowId() { return followId; }

    public Position getFollowPosition()
    {
        if (followId == null) return null;
        SatellitePosition sp = latestPositions.get(followId);
        if (sp == null) return null;
        return Position.fromDegrees(sp.getLatDeg(), sp.getLonDeg(), sp.getAltitudeMeters());
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public int getTotalCount() { return tleData.size(); }
    public int getVisibleCount()
    {
        int n = 0;
        for (PointPlacemark pm : placemarks.values())
            if (pm.isVisible()) n++;
        return n;
    }
    public int getCountByCategory(SatelliteCategory cat)
    {
        int n = 0;
        for (TleRecord tle : tleData.values())
            if (tle.getCategory() == cat) n++;
        return n;
    }
    public int getLeoCount()
    {
        int n = 0;
        for (SatellitePosition sp : latestPositions.values())
            if (sp.getAltitudeKm() < 2000) n++;
        return n;
    }
    public int getMeoCount()
    {
        int n = 0;
        for (SatellitePosition sp : latestPositions.values())
            if (sp.getAltitudeKm() >= 2000 && sp.getAltitudeKm() < 35000) n++;
        return n;
    }
    public int getGeoCount()
    {
        int n = 0;
        for (SatellitePosition sp : latestPositions.values())
            if (sp.getAltitudeKm() >= 35000 && sp.getAltitudeKm() < 37000) n++;
        return n;
    }

    public TleRecord getTle(int id)                 { return tleData.get(id); }
    public SatellitePosition getPosition(int id)    { return latestPositions.get(id); }
    public Set<Integer> getTrackedIds()             { return tleData.keySet(); }
    public Map<Integer, TleRecord> getAllTLEs()      { return tleData; }

    // ── Utility ──────────────────────────────────────────────────────────────

    /** Encode colour as AABBGGRR hex string (KML order expected by WorldWind). */
    private static String colorToHex(Color c)
    {
        return String.format("%02x%02x%02x%02x", c.getAlpha(), c.getBlue(), c.getGreen(), c.getRed());
    }

    // ── Placemark subclass: prevent duplicate label draws per frame ────────

    /**
     * At low camera pitch WorldWind splits the view frustum and calls render()
     * once per segment, each adding an OrderedPlacemark to the draw queue.
     * The label is drawn for every queued entry, producing visible duplicates.
     * This subclass skips the redundant label draw.
     *
     * seaglassfoundry.com
     */
    private static class SatellitePlacemark extends PointPlacemark
    {
        private long lastLabelFrame = -1;

        SatellitePlacemark(Position position)
        {
            super(position);
        }

        @Override
        protected void drawLabel(DrawContext dc, PickSupport pickCandidates, OrderedPlacemark opm)
        {
            long frame = dc.getFrameTimeStamp();
            if (frame == lastLabelFrame)
                return;
            lastLabelFrame = frame;
            super.drawLabel(dc, pickCandidates, opm);
        }
    }

    // ── Altitude-graded orbit colours ────────────────────────────────────────

    /**
     * Colours each orbit vertex by its altitude using the standard LEO→GEO spectrum.
     */
    private static class OrbitAltitudeColors implements Path.PositionColors
    {
        private final List<Position> positions;

        OrbitAltitudeColors(List<Position> positions)
        {
            this.positions = positions;
        }

        @Override
        public Color getColor(Position position, int ordinal)
        {
            double altKm = position.getElevation() / KM_TO_METERS;
            Color base = SatelliteCategory.altitudeColor(altKm);
            // Slight fade at edges of the orbit to emphasise current position
            float ageFraction = (positions.size() <= 1) ? 1f
                : 1f - Math.abs(2f * ordinal / (positions.size() - 1) - 1f);
            float alpha = Math.max(0.15f, 0.3f + ageFraction * 0.5f);
            return new Color(base.getRed(), base.getGreen(), base.getBlue(), (int) (alpha * 255));
        }
    }
}
