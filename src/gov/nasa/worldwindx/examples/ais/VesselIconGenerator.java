/*
 * WorldWind Reforged — AIS Vessel Tracker Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: generates vessel icon PNGs for the AIS demo. Run main() once to
 * create the icon files, or call getIconImage() at runtime for on-the-fly icons.
 */
package gov.nasa.worldwindx.examples.ais;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Generates 32×32 vessel icon PNGs for each {@link VesselCategory}.
 * All icons point north (up) at heading 0; WorldWind rotates them via
 * {@code PointPlacemarkAttributes.setHeading()}.
 * <p>
 * Shapes:
 * <ul>
 *   <li>Cargo / Tanker / Passenger / Military: pointed hull (ship silhouette)</li>
 *   <li>Fishing: small triangle</li>
 *   <li>Tug: diamond</li>
 *   <li>Other: circle</li>
 * </ul>
 *
 * seaglassfoundry.com
 */
public class VesselIconGenerator
{
    private static final int SIZE = 32;

    /** Create a vessel icon image for the given category. */
    public static BufferedImage getIconImage(VesselCategory category)
    {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color fill = category.getColor();
        Color outline = Color.WHITE;

        int cx = SIZE / 2;

        switch (category)
        {
            case CARGO, TANKER, PASSENGER, MILITARY ->
            {
                // Pointed hull shape — pointed at top (north), wider at bottom
                int[] xPoints = {cx, cx + 8, cx + 6, cx - 6, cx - 8};
                int[] yPoints = {3,  14,     28,     28,     14};
                g.setColor(fill);
                g.fillPolygon(xPoints, yPoints, 5);
                g.setColor(outline);
                g.setStroke(new BasicStroke(1.5f));
                g.drawPolygon(xPoints, yPoints, 5);
            }
            case FISHING ->
            {
                // Small upward triangle
                int[] xPoints = {cx, cx + 9, cx - 9};
                int[] yPoints = {4,  27,     27};
                g.setColor(fill);
                g.fillPolygon(xPoints, yPoints, 3);
                g.setColor(outline);
                g.setStroke(new BasicStroke(1.5f));
                g.drawPolygon(xPoints, yPoints, 3);
            }
            case TUG ->
            {
                // Diamond
                int[] xPoints = {cx, cx + 10, cx, cx - 10};
                int[] yPoints = {3,  cx,      29, cx};
                g.setColor(fill);
                g.fillPolygon(xPoints, yPoints, 4);
                g.setColor(outline);
                g.setStroke(new BasicStroke(1.5f));
                g.drawPolygon(xPoints, yPoints, 4);
            }
            case OTHER ->
            {
                // Circle
                g.setColor(fill);
                g.fillOval(5, 5, 22, 22);
                g.setColor(outline);
                g.setStroke(new BasicStroke(1.5f));
                g.drawOval(5, 5, 22, 22);
                // Add a direction tick at the top
                g.drawLine(cx, 5, cx, 1);
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
        String baseDir = "src/gov/nasa/worldwindx/examples/ais/icons";
        File dir = new File(baseDir);
        if (!dir.exists()) dir.mkdirs();

        for (VesselCategory cat : VesselCategory.values())
        {
            BufferedImage img = getIconImage(cat);
            File outFile = new File(dir, cat.getIconPath().substring(cat.getIconPath().lastIndexOf('/') + 1));
            ImageIO.write(img, "PNG", outFile);
            System.out.println("Generated: " + outFile.getAbsolutePath());
        }
    }
}
