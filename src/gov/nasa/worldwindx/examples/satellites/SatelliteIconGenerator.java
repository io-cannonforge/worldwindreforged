/*
 * WorldWind Reforged — Satellite Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: generates satellite icon PNGs for each SatelliteCategory. Run main()
 * once to create the icon files, or call getIconImage() at runtime for on-the-fly icons.
 */
package gov.nasa.worldwindx.examples.satellites;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Generates 32x32 satellite icon PNGs for each {@link SatelliteCategory}.
 * All icons point north (up) at heading 0; WorldWind rotates them via
 * {@code PointPlacemarkAttributes.setHeading()}.
 * <p>
 * Shapes:
 * <ul>
 *   <li>Space Station: cross / module shape (larger, distinctive)</li>
 *   <li>Navigation: diamond</li>
 *   <li>Weather: circle with crosshair</li>
 *   <li>Communication: arrow pointing up</li>
 *   <li>Earth Observation: square with corner notch</li>
 *   <li>Starlink: small flat rectangle (panel shape)</li>
 *   <li>Military: triangle</li>
 *   <li>Debris: small dot</li>
 *   <li>Other: circle</li>
 * </ul>
 *
 * seaglassfoundry.com
 */
public class SatelliteIconGenerator
{
    private static final int SIZE = 32;

    /** Create a satellite icon image for the given category. */
    public static BufferedImage getIconImage(SatelliteCategory category)
    {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color fill = category.getColor();
        Color outline = Color.WHITE;
        int cx = SIZE / 2;
        int cy = SIZE / 2;

        switch (category)
        {
            case SPACE_STATION ->
            {
                // Cross / module shape — distinctive and larger
                g.setColor(fill);
                g.fillRect(cx - 3, 4, 6, 24);    // vertical body
                g.fillRect(3, cy - 2, 26, 4);     // solar panels (horizontal)
                g.setColor(outline);
                g.setStroke(new BasicStroke(1.5f));
                g.drawRect(cx - 3, 4, 6, 24);
                g.drawRect(3, cy - 2, 26, 4);
                // Panel lines
                g.setStroke(new BasicStroke(0.5f));
                g.drawLine(9, cy - 2, 9, cy + 2);
                g.drawLine(15, cy - 2, 15, cy + 2);
                g.drawLine(17, cy - 2, 17, cy + 2);
                g.drawLine(23, cy - 2, 23, cy + 2);
            }
            case NAVIGATION ->
            {
                // Diamond shape
                int[] xPoints = {cx, cx + 10, cx, cx - 10};
                int[] yPoints = {3, cy, 29, cy};
                g.setColor(fill);
                g.fillPolygon(xPoints, yPoints, 4);
                g.setColor(outline);
                g.setStroke(new BasicStroke(1.5f));
                g.drawPolygon(xPoints, yPoints, 4);
                // Center dot
                g.setColor(Color.WHITE);
                g.fillOval(cx - 2, cy - 2, 4, 4);
            }
            case WEATHER ->
            {
                // Circle with crosshair (weather/radar motif)
                g.setColor(fill);
                g.fillOval(5, 5, 22, 22);
                g.setColor(outline);
                g.setStroke(new BasicStroke(1.5f));
                g.drawOval(5, 5, 22, 22);
                // Crosshair
                g.setStroke(new BasicStroke(1.0f));
                g.drawLine(cx, 7, cx, 25);
                g.drawLine(7, cy, 25, cy);
            }
            case COMMUNICATION ->
            {
                // Arrow pointing up (signal / uplink motif)
                int[] xPoints = {cx, cx + 10, cx + 4, cx + 4, cx - 4, cx - 4, cx - 10};
                int[] yPoints = {3, 15, 15, 28, 28, 15, 15};
                g.setColor(fill);
                g.fillPolygon(xPoints, yPoints, 7);
                g.setColor(outline);
                g.setStroke(new BasicStroke(1.5f));
                g.drawPolygon(xPoints, yPoints, 7);
            }
            case EARTH_OBS ->
            {
                // Square with corner notch (camera/sensor motif)
                g.setColor(fill);
                g.fillRect(5, 5, 22, 22);
                g.setColor(outline);
                g.setStroke(new BasicStroke(1.5f));
                g.drawRect(5, 5, 22, 22);
                // Lens circle
                g.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 120));
                g.fillOval(9, 9, 14, 14);
                g.setColor(outline);
                g.drawOval(9, 9, 14, 14);
            }
            case STARLINK ->
            {
                // Flat rectangle (solar panel shape) — small
                g.setColor(fill);
                g.fillRect(6, 12, 20, 8);
                g.setColor(outline);
                g.setStroke(new BasicStroke(1.0f));
                g.drawRect(6, 12, 20, 8);
                // Panel divider
                g.drawLine(cx, 12, cx, 20);
                // Direction tick at top
                g.drawLine(cx, 12, cx, 8);
            }
            case MILITARY ->
            {
                // Upward triangle
                int[] xPoints = {cx, cx + 11, cx - 11};
                int[] yPoints = {3, 28, 28};
                g.setColor(fill);
                g.fillPolygon(xPoints, yPoints, 3);
                g.setColor(outline);
                g.setStroke(new BasicStroke(1.5f));
                g.drawPolygon(xPoints, yPoints, 3);
            }
            case DEBRIS ->
            {
                // Small dot (insignificant)
                g.setColor(fill);
                g.fillOval(10, 10, 12, 12);
                g.setColor(outline);
                g.setStroke(new BasicStroke(1.0f));
                g.drawOval(10, 10, 12, 12);
            }
            case OTHER ->
            {
                // Circle
                g.setColor(fill);
                g.fillOval(6, 6, 20, 20);
                g.setColor(outline);
                g.setStroke(new BasicStroke(1.5f));
                g.drawOval(6, 6, 20, 20);
                // Direction tick
                g.drawLine(cx, 6, cx, 2);
            }
        }

        g.dispose();
        return img;
    }

    /**
     * Run this main method to generate the icon PNGs into the icons directory.
     * Only needs to be run once during development.
     */
    public static void main(String[] args) throws IOException
    {
        String baseDir = "src/gov/nasa/worldwindx/examples/satellites/icons";
        File dir = new File(baseDir);
        if (!dir.exists()) dir.mkdirs();

        for (SatelliteCategory cat : SatelliteCategory.values())
        {
            BufferedImage img = getIconImage(cat);
            String fileName = cat.getIconPath().substring(cat.getIconPath().lastIndexOf('/') + 1);
            File outFile = new File(dir, fileName);
            ImageIO.write(img, "PNG", outFile);
            System.out.println("Generated: " + outFile.getAbsolutePath());
        }
    }
}
