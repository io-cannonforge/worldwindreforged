/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — Test shapefile generator for benchmarking. Creates ESRI .shp/.shx/.dbf
 * files with configurable polygon counts and a mix of shapes (rectangles, triangles,
 * hexagons, L-shapes, polygons with holes) for stress-testing tessellation and
 * rendering optimizations.
 */
package gov.nasa.worldwindx.examples.util;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates test shapefiles with configurable polygon counts, shapes, and holes.
 * Writes standard ESRI Shapefile format (.shp, .shx, .dbf).
 */
public class ShapefileGenerator
{
    private static final int SHP_FILE_CODE = 9994;
    private static final int SHP_VERSION = 1000;
    private static final int SHAPE_POLYGON = 5;

    /**
     * Generate a test shapefile with polygons off Florida's east coast.
     *
     * @param basePath path without extension (e.g., "testData/shapefiles/florida_test")
     * @param count    target number of polygons (actual count may be slightly higher to fill grid)
     */
    public static void generate(String basePath, int count) throws IOException
    {
        int cols = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil((double) count / cols);
        int total = rows * cols;

        // Geographic bounds: Atlantic Ocean off Florida's east coast
        double minLon = -79.5, maxLon = -76.0;
        double minLat = 24.0, maxLat = 28.0;

        double cellW = (maxLon - minLon) / cols;
        double cellH = (maxLat - minLat) / rows;

        Random rng = new Random(42); // deterministic for reproducible tests

        List<Polygon> polygons = new ArrayList<>(total);

        for (int r = 0; r < rows; r++)
        {
            for (int c = 0; c < cols; c++)
            {
                double cx = minLon + (c + 0.5) * cellW;
                double cy = minLat + (r + 0.5) * cellH;
                double halfW = cellW * 0.4;
                double halfH = cellH * 0.4;

                int idx = r * cols + c;
                int kind = idx % 10; // deterministic variety

                Polygon poly;
                if (kind < 4)
                {
                    // 40%: rectangles with random rotation/skew
                    poly = makeRectangle(cx, cy, halfW, halfH, rng);
                }
                else if (kind < 6)
                {
                    // 20%: triangles
                    poly = makeTriangle(cx, cy, halfW, halfH, rng);
                }
                else if (kind < 8)
                {
                    // 20%: hexagons
                    poly = makeHexagon(cx, cy, Math.min(halfW, halfH), rng);
                }
                else if (kind == 8)
                {
                    // 10%: L-shapes (concave)
                    poly = makeLShape(cx, cy, halfW, halfH);
                }
                else
                {
                    // 10%: rectangles with 1-2 holes
                    poly = makeRectangleWithHoles(cx, cy, halfW, halfH, rng);
                }

                polygons.add(poly);
            }
        }

        writeShp(basePath + ".shp", polygons);
        writeShx(basePath + ".shx", polygons);
        writeDbf(basePath + ".dbf", polygons.size());

        System.out.println("Generated " + polygons.size() + " polygons -> " + basePath + ".shp");
    }

    // ---- Shape generators ----

    private static Polygon makeRectangle(double cx, double cy, double hw, double hh, Random rng)
    {
        // Slightly randomize size
        double sw = hw * (0.7 + rng.nextDouble() * 0.6);
        double sh = hh * (0.7 + rng.nextDouble() * 0.6);
        // Clockwise winding for outer ring
        double[] ring = {
            cx - sw, cy - sh,
            cx - sw, cy + sh,
            cx + sw, cy + sh,
            cx + sw, cy - sh,
            cx - sw, cy - sh // close
        };
        return new Polygon(new double[][] {ring});
    }

    private static Polygon makeTriangle(double cx, double cy, double hw, double hh, Random rng)
    {
        double s = 0.7 + rng.nextDouble() * 0.6;
        hw *= s;
        hh *= s;
        // Clockwise
        double[] ring = {
            cx, cy + hh,
            cx + hw, cy - hh,
            cx - hw, cy - hh,
            cx, cy + hh // close
        };
        return new Polygon(new double[][] {ring});
    }

    private static Polygon makeHexagon(double cx, double cy, double radius, Random rng)
    {
        double r = radius * (0.7 + rng.nextDouble() * 0.6);
        double[] ring = new double[14]; // 6 vertices + close = 7 points * 2
        for (int i = 0; i < 6; i++)
        {
            double angle = Math.PI / 3.0 * i - Math.PI / 6.0; // start at -30 deg for clockwise
            ring[i * 2] = cx + r * Math.cos(angle);
            ring[i * 2 + 1] = cy + r * Math.sin(angle);
        }
        ring[12] = ring[0]; // close
        ring[13] = ring[1];
        return new Polygon(new double[][] {ring});
    }

    private static Polygon makeLShape(double cx, double cy, double hw, double hh)
    {
        // L-shape (concave polygon) — clockwise
        double x0 = cx - hw, x1 = cx, x2 = cx + hw;
        double y0 = cy - hh, y1 = cy, y2 = cy + hh;
        double[] ring = {
            x0, y0,
            x0, y2,
            x1, y2,
            x1, y1,
            x2, y1,
            x2, y0,
            x0, y0 // close
        };
        return new Polygon(new double[][] {ring});
    }

    private static Polygon makeRectangleWithHoles(double cx, double cy, double hw, double hh, Random rng)
    {
        // Outer rectangle (clockwise)
        double[] outer = {
            cx - hw, cy - hh,
            cx - hw, cy + hh,
            cx + hw, cy + hh,
            cx + hw, cy - hh,
            cx - hw, cy - hh
        };

        int numHoles = 1 + rng.nextInt(2); // 1-2 holes
        double[][] rings = new double[1 + numHoles][];
        rings[0] = outer;

        for (int h = 0; h < numHoles; h++)
        {
            // Small hole inside the rectangle — counter-clockwise
            double holeR = Math.min(hw, hh) * 0.15;
            double holeCx, holeCy;
            if (numHoles == 1)
            {
                holeCx = cx;
                holeCy = cy;
            }
            else
            {
                holeCx = cx + (h == 0 ? -hw * 0.35 : hw * 0.35);
                holeCy = cy;
            }

            // Counter-clockwise square hole
            rings[1 + h] = new double[] {
                holeCx - holeR, holeCy - holeR,
                holeCx + holeR, holeCy - holeR,
                holeCx + holeR, holeCy + holeR,
                holeCx - holeR, holeCy + holeR,
                holeCx - holeR, holeCy - holeR // close
            };
        }

        return new Polygon(rings);
    }

    // ---- Shapefile writing ----

    private static void writeShp(String path, List<Polygon> polygons) throws IOException
    {
        // Calculate bounding box
        double fileMinX = Double.MAX_VALUE, fileMinY = Double.MAX_VALUE;
        double fileMaxX = -Double.MAX_VALUE, fileMaxY = -Double.MAX_VALUE;
        for (Polygon p : polygons)
        {
            for (double[] ring : p.rings)
            {
                for (int i = 0; i < ring.length; i += 2)
                {
                    fileMinX = Math.min(fileMinX, ring[i]);
                    fileMinY = Math.min(fileMinY, ring[i + 1]);
                    fileMaxX = Math.max(fileMaxX, ring[i]);
                    fileMaxY = Math.max(fileMaxY, ring[i + 1]);
                }
            }
        }

        // Build all records first to know total file length
        List<byte[]> records = new ArrayList<>();
        for (int i = 0; i < polygons.size(); i++)
        {
            records.add(buildPolygonRecord(i + 1, polygons.get(i)));
        }

        int contentLength = 50; // header = 100 bytes = 50 words
        for (byte[] rec : records)
            contentLength += rec.length / 2;

        // Write file
        try (FileOutputStream fos = new FileOutputStream(path);
             FileChannel ch = fos.getChannel())
        {
            // Header (100 bytes)
            ByteBuffer header = ByteBuffer.allocate(100);
            header.order(ByteOrder.BIG_ENDIAN);
            header.putInt(SHP_FILE_CODE);      // file code
            header.putInt(0); header.putInt(0); header.putInt(0); header.putInt(0); header.putInt(0); // unused
            header.putInt(contentLength);       // file length in 16-bit words

            header.order(ByteOrder.LITTLE_ENDIAN);
            header.putInt(SHP_VERSION);         // version
            header.putInt(SHAPE_POLYGON);       // shape type
            header.putDouble(fileMinX);         // bounding box
            header.putDouble(fileMinY);
            header.putDouble(fileMaxX);
            header.putDouble(fileMaxY);
            header.putDouble(0); header.putDouble(0); // Z range
            header.putDouble(0); header.putDouble(0); // M range

            header.flip();
            ch.write(header);

            // Records
            for (byte[] rec : records)
            {
                ch.write(ByteBuffer.wrap(rec));
            }
        }
    }

    private static byte[] buildPolygonRecord(int recordNumber, Polygon poly)
    {
        int numParts = poly.rings.length;
        int numPoints = 0;
        for (double[] ring : poly.rings)
            numPoints += ring.length / 2;

        // Content: shapeType(4) + bbox(32) + numParts(4) + numPoints(4) + parts(numParts*4) + points(numPoints*16)
        int contentBytes = 4 + 32 + 4 + 4 + numParts * 4 + numPoints * 16;
        int contentWords = contentBytes / 2;

        // Record header (8 bytes, big-endian) + content (little-endian)
        ByteBuffer buf = ByteBuffer.allocate(8 + contentBytes);

        // Record header — big endian
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(recordNumber);
        buf.putInt(contentWords);

        // Record content — little endian
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(SHAPE_POLYGON);

        // Bounding box
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] ring : poly.rings)
        {
            for (int i = 0; i < ring.length; i += 2)
            {
                minX = Math.min(minX, ring[i]);
                minY = Math.min(minY, ring[i + 1]);
                maxX = Math.max(maxX, ring[i]);
                maxY = Math.max(maxY, ring[i + 1]);
            }
        }
        buf.putDouble(minX);
        buf.putDouble(minY);
        buf.putDouble(maxX);
        buf.putDouble(maxY);

        // NumParts, NumPoints
        buf.putInt(numParts);
        buf.putInt(numPoints);

        // Parts array (starting index of each ring in the points array)
        int offset = 0;
        for (double[] ring : poly.rings)
        {
            buf.putInt(offset);
            offset += ring.length / 2;
        }

        // Points array (x, y pairs)
        for (double[] ring : poly.rings)
        {
            for (int i = 0; i < ring.length; i += 2)
            {
                buf.putDouble(ring[i]);     // x (longitude)
                buf.putDouble(ring[i + 1]); // y (latitude)
            }
        }

        return buf.array();
    }

    private static void writeShx(String path, List<Polygon> polygons) throws IOException
    {
        int shxContentLength = 50 + polygons.size() * 4; // header + 8 bytes (4 words) per record

        try (FileOutputStream fos = new FileOutputStream(path);
             FileChannel ch = fos.getChannel())
        {
            // Header (same structure as .shp but with .shx file length)
            // We need the bounding box again
            double fileMinX = Double.MAX_VALUE, fileMinY = Double.MAX_VALUE;
            double fileMaxX = -Double.MAX_VALUE, fileMaxY = -Double.MAX_VALUE;
            for (Polygon p : polygons)
            {
                for (double[] ring : p.rings)
                {
                    for (int i = 0; i < ring.length; i += 2)
                    {
                        fileMinX = Math.min(fileMinX, ring[i]);
                        fileMinY = Math.min(fileMinY, ring[i + 1]);
                        fileMaxX = Math.max(fileMaxX, ring[i]);
                        fileMaxY = Math.max(fileMaxY, ring[i + 1]);
                    }
                }
            }

            ByteBuffer header = ByteBuffer.allocate(100);
            header.order(ByteOrder.BIG_ENDIAN);
            header.putInt(SHP_FILE_CODE);
            header.putInt(0); header.putInt(0); header.putInt(0); header.putInt(0); header.putInt(0);
            header.putInt(shxContentLength);

            header.order(ByteOrder.LITTLE_ENDIAN);
            header.putInt(SHP_VERSION);
            header.putInt(SHAPE_POLYGON);
            header.putDouble(fileMinX);
            header.putDouble(fileMinY);
            header.putDouble(fileMaxX);
            header.putDouble(fileMaxY);
            header.putDouble(0); header.putDouble(0);
            header.putDouble(0); header.putDouble(0);

            header.flip();
            ch.write(header);

            // Index records
            int shpOffset = 50; // starts after 100-byte header = 50 words
            for (Polygon poly : polygons)
            {
                int numParts = poly.rings.length;
                int numPoints = 0;
                for (double[] ring : poly.rings)
                    numPoints += ring.length / 2;

                int contentBytes = 4 + 32 + 4 + 4 + numParts * 4 + numPoints * 16;
                int contentWords = contentBytes / 2;

                ByteBuffer rec = ByteBuffer.allocate(8);
                rec.order(ByteOrder.BIG_ENDIAN);
                rec.putInt(shpOffset);
                rec.putInt(contentWords);
                rec.flip();
                ch.write(rec);

                shpOffset += 4 + contentWords; // 4 words for record header + content
            }
        }
    }

    private static void writeDbf(String path, int recordCount) throws IOException
    {
        // Minimal dBASE III format with one numeric field (ID)
        int fieldWidth = 10;
        int recordSize = 1 + fieldWidth; // 1 byte delete flag + field data
        int headerSize = 32 + 32 + 1; // file header + 1 field descriptor + terminator

        try (FileOutputStream fos = new FileOutputStream(path);
             DataOutputStream dos = new DataOutputStream(fos))
        {
            // File header (32 bytes)
            dos.writeByte(0x03);                    // version
            dos.writeByte(26); dos.writeByte(3); dos.writeByte(27); // date (YY MM DD)
            dos.writeInt(Integer.reverseBytes(recordCount)); // number of records (little-endian)
            dos.writeShort(Short.reverseBytes((short) headerSize)); // header size (little-endian)
            dos.writeShort(Short.reverseBytes((short) recordSize)); // record size (little-endian)
            dos.write(new byte[20]);                // reserved

            // Field descriptor (32 bytes) — "ID" field
            byte[] fieldName = new byte[11];
            fieldName[0] = 'I';
            fieldName[1] = 'D';
            dos.write(fieldName);                   // field name
            dos.writeByte('N');                      // field type (Numeric)
            dos.write(new byte[4]);                 // reserved
            dos.writeByte(fieldWidth);              // field length
            dos.writeByte(0);                       // decimal count
            dos.write(new byte[14]);                // reserved

            dos.writeByte(0x0D);                    // header terminator

            // Records
            for (int i = 0; i < recordCount; i++)
            {
                dos.writeByte(0x20); // not deleted
                String idStr = String.format("%" + fieldWidth + "d", i + 1);
                dos.writeBytes(idStr);
            }
        }
    }

    // ---- Data structures ----

    private static class Polygon
    {
        final double[][] rings; // rings[0] = outer, rings[1..n] = holes; each ring = [x0,y0, x1,y1, ..., x0,y0]

        Polygon(double[][] rings)
        {
            this.rings = rings;
        }
    }

    // ---- Main entry point ----

    public static void main(String[] args) throws IOException
    {
        String basePath = "testData/shapefiles/florida_coast_test";
        int count = 1024;

        if (args.length >= 1)
            basePath = args[0];
        if (args.length >= 2)
            count = Integer.parseInt(args[1]);

        // Ensure directory exists
        File parentDir = new File(basePath).getParentFile();
        if (parentDir != null)
            parentDir.mkdirs();

        generate(basePath, count);
    }
}
