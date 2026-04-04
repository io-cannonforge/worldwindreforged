/*
 * WorldWind Reforged — AIS Vessel Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: manages vessel renderables (placemarks and track paths) from AIS data.
 */
package gov.nasa.worldwindx.examples.ais;

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
 * Manages the set of vessel placemarks and track-history paths, updating them
 * from AIS position reports. Supports filtering by vessel category and speed.
 *
 * seaglassfoundry.com
 */
public class VesselManager
{
    /** Maximum number of positions retained per vessel track. */
    private static final int MAX_TRACK_POINTS = 200;
    /** Stale vessel timeout in milliseconds (10 minutes). */
    private static final long STALE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final WorldWindow wwd;
    private final RenderableLayer vesselLayer;
    private final RenderableLayer trackLayer;

    /** MMSI → current placemark. */
    private final Map<Integer, PointPlacemark> placemarks = new ConcurrentHashMap<>();
    /** MMSI → track path. */
    private final Map<Integer, Path> tracks = new ConcurrentHashMap<>();
    /** MMSI → ordered list of track positions. */
    private final Map<Integer, List<Position>> trackPositions = new ConcurrentHashMap<>();
    /** MMSI → last-seen timestamp (epoch ms). */
    private final Map<Integer, Long> lastSeen = new ConcurrentHashMap<>();
    /** MMSI → latest position. */
    private final Map<Integer, VesselPosition> latestPositions = new ConcurrentHashMap<>();

    /** Current vessel metadata (may lag positions). */
    private volatile Map<Integer, VesselInfo> vesselInfoMap = new HashMap<>();

    /** Active filter predicate — vessels that fail are hidden. */
    private volatile Predicate<Integer> filterPredicate = mmsi -> true;

    /** Whether track paths are visible. */
    private volatile boolean showTracks = true;

    /** Maximum track age in milliseconds (default 1 hour). */
    private volatile long maxTrackAgeMs = 60 * 60 * 1000L;

    public VesselManager(WorldWindow wwd, RenderableLayer vesselLayer, RenderableLayer trackLayer)
    {
        this.wwd = wwd;
        this.vesselLayer = vesselLayer;
        this.trackLayer = trackLayer;
    }

    // ── Update from data source ───────────────────────────────────────────────

    /**
     * Bulk-update vessel positions from the latest data fetch.
     * Creates new placemarks for unseen vessels, updates existing ones,
     * and removes stale entries.
     */
    public void updatePositions(Map<Integer, VesselPosition> positions)
    {
        long now = System.currentTimeMillis();

        for (Map.Entry<Integer, VesselPosition> entry : positions.entrySet())
        {
            int mmsi = entry.getKey();
            VesselPosition vp = entry.getValue();
            latestPositions.put(mmsi, vp);
            lastSeen.put(mmsi, now);

            Position pos = Position.fromDegrees(vp.getLat(), vp.getLon(), 0);
            boolean visible = filterPredicate.test(mmsi);

            // ── Placemark ─────────────────────────────────────────────────
            PointPlacemark pm = placemarks.get(mmsi);
            if (pm == null)
            {
                pm = createPlacemark(mmsi, vp, pos);
                placemarks.put(mmsi, pm);
                vesselLayer.addRenderable(pm);
            }
            else
            {
                pm.setPosition(pos);
                updatePlacemarkStyle(pm, mmsi, vp);
            }
            pm.setVisible(visible);

            // ── Track path ────────────────────────────────────────────────
            if (showTracks)
            {
                List<Position> posList = trackPositions.computeIfAbsent(mmsi, k -> new ArrayList<>());
                posList.add(pos);
                if (posList.size() > MAX_TRACK_POINTS)
                    posList.remove(0);

                Path path = tracks.get(mmsi);
                if (path == null)
                {
                    path = createTrackPath(mmsi, posList);
                    tracks.put(mmsi, path);
                    trackLayer.addRenderable(path);
                }
                else
                {
                    path.setPositions(posList);
                }
                path.setVisible(visible && posList.size() > 1);
            }
        }

        // ── Remove stale vessels ──────────────────────────────────────────────
        removeStaleVessels(now);

        wwd.redraw();
    }

    /** Update vessel metadata (name, type, destination, etc.). */
    public void updateVesselInfo(Map<Integer, VesselInfo> info)
    {
        this.vesselInfoMap = info;
        // Refresh labels and icons for all known placemarks
        for (Map.Entry<Integer, PointPlacemark> entry : placemarks.entrySet())
        {
            int mmsi = entry.getKey();
            VesselPosition vp = latestPositions.get(mmsi);
            if (vp != null)
                updatePlacemarkStyle(entry.getValue(), mmsi, vp);
        }
    }

    // ── Placemark creation & styling ──────────────────────────────────────────

    private PointPlacemark createPlacemark(int mmsi, VesselPosition vp, Position pos)
    {
        PointPlacemark pm = new PointPlacemark(pos);
        pm.setValue("mmsi", mmsi);
        pm.setAltitudeMode(gov.nasa.worldwind.WorldWind.CLAMP_TO_GROUND);
        updatePlacemarkStyle(pm, mmsi, vp);
        return pm;
    }

    private void updatePlacemarkStyle(PointPlacemark pm, int mmsi, VesselPosition vp)
    {
        VesselInfo info = vesselInfoMap.get(mmsi);
        VesselCategory cat = info != null ? info.getCategory() : VesselCategory.OTHER;

        PointPlacemarkAttributes attrs = new PointPlacemarkAttributes();
        String iconPath = cat.getIconPath();
        attrs.setImageAddress(iconPath);
        attrs.setScale(0.7);
        attrs.setHeading(vp.getEffectiveHeading());
        attrs.setHeadingReference(AVKey.RELATIVE_TO_GLOBE);
        attrs.setImageOffset(new Offset(0.5, 0.5, AVKey.FRACTION, AVKey.FRACTION));
        attrs.setLabelOffset(new Offset(0.9, 0.5, AVKey.FRACTION, AVKey.FRACTION));
        attrs.setLabelFont(new Font("SansSerif", Font.PLAIN, 11));
        attrs.setLabelColor(colorToHexString(cat.getColor()));
        pm.setAttributes(attrs);

        // Label: vessel name + speed
        String name = info != null ? info.getDisplayName() : String.valueOf(mmsi);
        pm.setLabelText(String.format("%s  %.1fkt", name, vp.getSog()));
    }

    // ── Track path creation ───────────────────────────────────────────────────

    private Path createTrackPath(int mmsi, List<Position> positions)
    {
        Path path = new Path(positions);
        path.setSurfacePath(true);
        path.setShowPositions(false);

        VesselInfo info = vesselInfoMap.get(mmsi);
        VesselCategory cat = info != null ? info.getCategory() : VesselCategory.OTHER;
        Color c = cat.getColor();

        ShapeAttributes attrs = new BasicShapeAttributes();
        attrs.setOutlineMaterial(new Material(c));
        attrs.setOutlineOpacity(0.6);
        attrs.setOutlineWidth(2);
        path.setAttributes(attrs);

        // Faded version for older segments
        path.setPositionColors(new TrackPositionColors(c, positions.size()));

        return path;
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    /** Set the filter predicate. Vessels failing the filter are hidden immediately. */
    public void setFilterPredicate(Predicate<Integer> predicate)
    {
        this.filterPredicate = predicate;
        applyFilter();
    }

    /** Apply current filter to all existing placemarks and tracks. */
    public void applyFilter()
    {
        for (Map.Entry<Integer, PointPlacemark> entry : placemarks.entrySet())
        {
            boolean vis = filterPredicate.test(entry.getKey());
            entry.getValue().setVisible(vis);
            Path path = tracks.get(entry.getKey());
            if (path != null)
                path.setVisible(vis && showTracks);
        }
        wwd.redraw();
    }

    public void setShowTracks(boolean show)
    {
        this.showTracks = show;
        for (Path path : tracks.values())
            path.setVisible(show);
        wwd.redraw();
    }

    public boolean isShowTracks() { return showTracks; }

    public void setMaxTrackAgeMs(long ms) { this.maxTrackAgeMs = ms; }

    // ── Stale vessel cleanup ──────────────────────────────────────────────────

    private void removeStaleVessels(long now)
    {
        Iterator<Map.Entry<Integer, Long>> it = lastSeen.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<Integer, Long> entry = it.next();
            if (now - entry.getValue() > STALE_TIMEOUT_MS)
            {
                int mmsi = entry.getKey();
                it.remove();
                latestPositions.remove(mmsi);

                PointPlacemark pm = placemarks.remove(mmsi);
                if (pm != null) vesselLayer.removeRenderable(pm);

                Path path = tracks.remove(mmsi);
                if (path != null) trackLayer.removeRenderable(path);

                trackPositions.remove(mmsi);
            }
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Returns the total number of tracked vessels (including hidden). */
    public int getTotalVesselCount() { return placemarks.size(); }

    /** Returns the number of vessels currently passing the filter. */
    public int getVisibleVesselCount()
    {
        int count = 0;
        for (Map.Entry<Integer, PointPlacemark> entry : placemarks.entrySet())
            if (filterPredicate.test(entry.getKey()))
                count++;
        return count;
    }

    /** Get the latest position for a vessel by MMSI. */
    public VesselPosition getPosition(int mmsi) { return latestPositions.get(mmsi); }

    /** Get vessel info for a vessel by MMSI. */
    public VesselInfo getVesselInfo(int mmsi) { return vesselInfoMap.get(mmsi); }

    /** Returns the set of currently tracked MMSIs. */
    public Set<Integer> getTrackedMMSIs() { return placemarks.keySet(); }

    public Map<Integer, VesselInfo> getVesselInfoMap() { return vesselInfoMap; }

    public Map<Integer, VesselPosition> getLatestPositions() { return latestPositions; }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Convert a Color to the hex string format PointPlacemarkAttributes expects (aarrggbb). */
    private static String colorToHexString(Color c)
    {
        return String.format("%02x%02x%02x%02x", c.getAlpha(), c.getRed(), c.getGreen(), c.getBlue());
    }

    // ── Per-vertex track coloring ─────────────────────────────────────────────

    /**
     * Assigns per-vertex colors to a track path: newest positions are fully opaque,
     * oldest fade to nearly transparent.
     */
    private static class TrackPositionColors implements Path.PositionColors
    {
        private final Color baseColor;
        private int count;

        TrackPositionColors(Color baseColor, int count)
        {
            this.baseColor = baseColor;
            this.count = count;
        }

        @Override
        public Color getColor(Position position, int ordinal)
        {
            float alpha = (count <= 1) ? 1f : (float) ordinal / (count - 1);
            alpha = Math.max(0.08f, alpha); // minimum visibility
            return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
                (int) (alpha * 255));
        }
    }
}
