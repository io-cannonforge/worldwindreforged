/*
 * WorldWind Reforged — Air Traffic Demo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New file: generates aircraft top-down planform icons for the air traffic demo.
 */
package gov.nasa.worldwindx.examples.airtraffic;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Generates 32×32 top-down aircraft planform silhouette icons.
 * All icons point north (up) at heading 0.
 *
 * seaglassfoundry.com
 */
public class AircraftIconGenerator
{
    private static final int SIZE = 32;
    private static final Color FILL = new Color(255, 255, 255, 230);
    private static final Color OUTLINE = new Color(40, 40, 50);

    /** Commercial jet — swept wings, narrow fuselage, tail. */
    public static BufferedImage jetIcon()
    {
        BufferedImage img = newImage();
        Graphics2D g = setup(img);

        // Fuselage
        int cx = 16;
        g.setColor(FILL);
        g.fillRect(cx - 1, 3, 3, 26);

        // Swept wings
        int[] wx = {cx, cx + 12, cx + 10, cx, cx - 10, cx - 12};
        int[] wy = {12, 18,      20,      14, 20,      18};
        g.fillPolygon(wx, wy, 6);

        // Tail
        int[] tx = {cx, cx + 5, cx + 4, cx, cx - 4, cx - 5};
        int[] ty = {25, 29,     30,     27, 30,     29};
        g.fillPolygon(tx, ty, 6);

        // Nose cone
        g.fillOval(cx - 2, 2, 5, 5);

        // Outline
        g.setColor(OUTLINE);
        g.setStroke(new BasicStroke(1.0f));
        g.drawPolygon(wx, wy, 6);
        g.drawPolygon(tx, ty, 6);
        g.drawRect(cx - 1, 3, 2, 25);

        g.dispose();
        return img;
    }

    /** Heavy jet — wider body, larger swept wings. */
    public static BufferedImage heavyIcon()
    {
        BufferedImage img = newImage();
        Graphics2D g = setup(img);

        int cx = 16;
        // Wide fuselage
        g.setColor(FILL);
        g.fillRect(cx - 2, 2, 5, 27);

        // Large swept wings
        int[] wx = {cx, cx + 14, cx + 11, cx, cx - 11, cx - 14};
        int[] wy = {10, 17,      19,      13, 19,      17};
        g.fillPolygon(wx, wy, 6);

        // Tail
        int[] tx = {cx, cx + 6, cx + 5, cx, cx - 5, cx - 6};
        int[] ty = {24, 29,     30,     26, 30,     29};
        g.fillPolygon(tx, ty, 6);

        // Nose
        g.fillOval(cx - 2, 1, 5, 5);

        // Engines (dots on wings)
        g.setColor(new Color(200, 200, 200));
        g.fillOval(cx + 5, 14, 3, 3);
        g.fillOval(cx - 7, 14, 3, 3);
        g.fillOval(cx + 9, 16, 3, 3);
        g.fillOval(cx - 11, 16, 3, 3);

        g.setColor(OUTLINE);
        g.setStroke(new BasicStroke(1.0f));
        g.drawPolygon(wx, wy, 6);

        g.dispose();
        return img;
    }

    /** General aviation — straight wings. */
    public static BufferedImage gaIcon()
    {
        BufferedImage img = newImage();
        Graphics2D g = setup(img);

        int cx = 16;
        g.setColor(FILL);
        g.fillRect(cx - 1, 4, 3, 24);

        // Straight wings
        int[] wx = {cx - 13, cx + 13, cx + 13, cx - 13};
        int[] wy = {13,      13,      16,      16};
        g.fillPolygon(wx, wy, 4);

        // Tail
        int[] tx = {cx - 6, cx + 6, cx + 6, cx - 6};
        int[] ty = {26,     26,     28,     28};
        g.fillPolygon(tx, ty, 4);

        g.fillOval(cx - 2, 3, 5, 5);

        g.setColor(OUTLINE);
        g.setStroke(new BasicStroke(1.0f));
        g.drawPolygon(wx, wy, 4);
        g.drawPolygon(tx, ty, 4);

        g.dispose();
        return img;
    }

    /** Helicopter — circle with rotor cross. */
    public static BufferedImage heloIcon()
    {
        BufferedImage img = newImage();
        Graphics2D g = setup(img);

        int cx = 16, cy = 14;
        g.setColor(FILL);
        g.fillOval(cx - 7, cy - 7, 14, 14);

        // Tail boom
        g.fillRect(cx - 1, cy + 5, 3, 12);

        // Tail rotor
        g.fillRect(cx - 4, 24, 9, 3);

        // Main rotor cross
        g.setColor(new Color(255, 255, 255, 160));
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(cx - 12, cy, cx + 12, cy);
        g.drawLine(cx, cy - 12, cx, cy + 8);

        g.setColor(OUTLINE);
        g.setStroke(new BasicStroke(1.0f));
        g.drawOval(cx - 7, cy - 7, 14, 14);

        g.dispose();
        return img;
    }

    /** Military — delta wing fighter. */
    public static BufferedImage militaryIcon()
    {
        BufferedImage img = newImage();
        Graphics2D g = setup(img);

        int cx = 16;
        g.setColor(FILL);

        // Delta fuselage + wings
        int[] fx = {cx, cx + 11, cx + 8, cx + 2, cx - 2, cx - 8, cx - 11};
        int[] fy = {2,  24,      26,     28,     28,     26,     24};
        g.fillPolygon(fx, fy, 7);

        // Tail fins
        int[] t1x = {cx + 7, cx + 12, cx + 10};
        int[] t1y = {22,     28,      26};
        g.fillPolygon(t1x, t1y, 3);
        int[] t2x = {cx - 7, cx - 12, cx - 10};
        g.fillPolygon(t2x, t1y, 3);

        g.setColor(OUTLINE);
        g.setStroke(new BasicStroke(1.0f));
        g.drawPolygon(fx, fy, 7);

        g.dispose();
        return img;
    }

    /** Ground vehicle — small square. */
    public static BufferedImage groundIcon()
    {
        BufferedImage img = newImage();
        Graphics2D g = setup(img);
        g.setColor(FILL);
        g.fillRoundRect(10, 10, 12, 12, 3, 3);
        g.setColor(OUTLINE);
        g.setStroke(new BasicStroke(1.0f));
        g.drawRoundRect(10, 10, 12, 12, 3, 3);
        // Direction arrow
        g.setColor(FILL);
        g.drawLine(16, 10, 16, 5);
        g.drawLine(14, 7, 16, 5);
        g.drawLine(18, 7, 16, 5);
        g.dispose();
        return img;
    }

    /** Generic other. */
    public static BufferedImage otherIcon()
    {
        BufferedImage img = newImage();
        Graphics2D g = setup(img);
        g.setColor(FILL);
        int[] dx = {16, 24, 16, 8};
        int[] dy = {4, 16, 28, 16};
        g.fillPolygon(dx, dy, 4);
        g.setColor(OUTLINE);
        g.setStroke(new BasicStroke(1.0f));
        g.drawPolygon(dx, dy, 4);
        g.dispose();
        return img;
    }

    private static BufferedImage newImage()
    {
        return new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    }

    private static Graphics2D setup(BufferedImage img)
    {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g;
    }

    public static void main(String[] args) throws IOException
    {
        String dir = "src/gov/nasa/worldwindx/examples/airtraffic/icons";
        new File(dir).mkdirs();

        write(heavyIcon(),    dir + "/heavy.png");
        write(jetIcon(),      dir + "/jet.png");
        write(gaIcon(),       dir + "/ga.png");
        write(heloIcon(),     dir + "/helo.png");
        write(militaryIcon(), dir + "/military.png");
        write(groundIcon(),   dir + "/ground.png");
        write(otherIcon(),    dir + "/other.png");
    }

    private static void write(BufferedImage img, String path) throws IOException
    {
        ImageIO.write(img, "PNG", new File(path));
        System.out.println("Generated: " + new File(path).getAbsolutePath());
    }
}
