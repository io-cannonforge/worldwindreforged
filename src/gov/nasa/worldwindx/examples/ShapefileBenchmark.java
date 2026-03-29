/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — Shapefile rendering benchmark harness. Loads multiple shapefile layers
 * (TM_WORLD_BORDERS, ne_10m_land, generated test polygons) and provides toggle keys
 * for A/B comparison of rendering optimizations: VBO vs client memory (V), merged vs
 * per-group draw calls (M), ear-clipping vs GLU tessellation (E), shader vs
 * fixed-function pipeline (S). Reports FPS and frame time statistics.
 */
package gov.nasa.worldwindx.examples;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.SwingUtilities;

import gov.nasa.worldwind.event.RenderingEvent;
import gov.nasa.worldwind.formats.shapefile.ShapefileLayerFactory;
import gov.nasa.worldwind.formats.shapefile.ShapefilePolygons;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwindx.examples.util.RandomShapeAttributes;

/**
 * Benchmark for shapefile polygon rendering. Loads the TM_WORLD_BORDERS shapefile and reports
 * frame rate statistics in the title bar and console.
 * <p>
 * Press <b>V</b> to toggle between VBO rendering and client-memory rendering for A/B comparison.
 * Stats reset on each toggle so you get clean measurements.
 */
public class ShapefileBenchmark extends ApplicationTemplate
{
    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;
        private final AtomicLong frameCount = new AtomicLong(0);
        private long lastReportTime = System.nanoTime();
        private long lastFrameTime = System.nanoTime();
        private double minFrameMs = Double.MAX_VALUE;
        private double maxFrameMs = 0;
        private double sumFrameMs = 0;
        private long reportFrames = 0;
        private static final long REPORT_INTERVAL_NS = 3_000_000_000L; // 3 seconds

        public AppFrame()
        {
            // Load the world borders shapefile
            ShapefileLayerFactory factory = new ShapefileLayerFactory();

            final RandomShapeAttributes randomAttrs = new RandomShapeAttributes();
            factory.setAttributeDelegate((shapefileRecord, renderableRecord) ->
                renderableRecord.setAttributes(randomAttrs.nextAttributes().asShapeAttributes()));

            factory.createFromShapefileSource("testData/shapefiles/TM_WORLD_BORDERS-0.3.shp",
                new ShapefileLayerFactory.CompletionCallback()
                {
                    @Override
                    public void completion(Object result)
                    {
                        final Layer layer = (Layer) result;
                        layer.setName("World Borders (benchmark)");
                        SwingUtilities.invokeLater(() ->
                        {
                            AppFrame.this.getWwd().getModel().getLayers().add(layer);
                            System.out.println("=== Shapefile loaded — benchmark running ===");
                            System.out.println("Press V=VBO, M=merged draws, E=ear-clipping vs GLU, S=shader vs fixed-function.");
                            System.out.println("Pan/zoom to generate frames. Stats reported every 3 seconds.");
                            System.out.println();
                            printMode();
                        });
                    }

                    @Override
                    public void exception(Exception e)
                    {
                        Logging.logger().log(java.util.logging.Level.SEVERE, e.getMessage(), e);
                    }
                });

            // Attach a rendering listener to measure frame times
            this.getWwd().addRenderingListener(event ->
            {
                if (RenderingEvent.AFTER_BUFFER_SWAP.equals(event.getStage()))
                    onFrameComplete();
            });

            // Key listener: V toggles VBO mode
            ((java.awt.Component) this.getWwd()).addKeyListener(new KeyAdapter()
            {
                @Override
                public void keyPressed(KeyEvent e)
                {
                    if (e.getKeyCode() == KeyEvent.VK_V)
                    {
                        ShapefilePolygons.forceDisableVBO = !ShapefilePolygons.forceDisableVBO;
                        resetStats();
                        printMode();
                        getWwd().redraw();
                    }
                    else if (e.getKeyCode() == KeyEvent.VK_M)
                    {
                        ShapefilePolygons.forceDisableMerged = !ShapefilePolygons.forceDisableMerged;
                        resetStats();
                        printMode();
                        getWwd().redraw();
                    }
                    else if (e.getKeyCode() == KeyEvent.VK_E)
                    {
                        ShapefilePolygons.forceDisableEarClipping = !ShapefilePolygons.forceDisableEarClipping;
                        resetStats();
                        printMode();
                        System.out.println("NOTE: Tessellation mode change takes effect on next tile load.");
                        System.out.println("      Zoom in/out to force new tiles to generate.");
                        getWwd().redraw();
                    }
                    else if (e.getKeyCode() == KeyEvent.VK_S)
                    {
                        ShapefilePolygons.forceDisableShader = !ShapefilePolygons.forceDisableShader;
                        resetStats();
                        printMode();
                        getWwd().redraw();
                    }
                }
            });

            // Also load ne_10m_land for more geometry pressure
            ShapefileLayerFactory factory2 = new ShapefileLayerFactory();
            factory2.createFromShapefileSource("testData/shapefiles/ne_10m_land.shp",
                new ShapefileLayerFactory.CompletionCallback()
                {
                    @Override
                    public void completion(Object result)
                    {
                        final Layer layer = (Layer) result;
                        layer.setName("Natural Earth 10m Land");
                        SwingUtilities.invokeLater(() ->
                            AppFrame.this.getWwd().getModel().getLayers().add(layer));
                    }

                    @Override
                    public void exception(Exception e)
                    {
                        // ne_10m_land may not exist — ignore
                    }
                });

            // Generate and load Florida coast test shapefile (1024 polygons with holes)
            String floridaTestPath = "testData/shapefiles/florida_coast_test";
            java.io.File floridaShp = new java.io.File(floridaTestPath + ".shp");
            if (!floridaShp.exists())
            {
                try
                {
                    gov.nasa.worldwindx.examples.util.ShapefileGenerator.generate(floridaTestPath, 1024);
                }
                catch (Exception e)
                {
                    System.err.println("Failed to generate Florida test shapefile: " + e.getMessage());
                }
            }

            if (floridaShp.exists())
            {
                ShapefileLayerFactory factory3 = new ShapefileLayerFactory();
                final RandomShapeAttributes randomAttrs3 = new RandomShapeAttributes();
                factory3.setAttributeDelegate((shapefileRecord, renderableRecord) ->
                    renderableRecord.setAttributes(randomAttrs3.nextAttributes().asShapeAttributes()));

                factory3.createFromShapefileSource(floridaShp.getPath(),
                    new ShapefileLayerFactory.CompletionCallback()
                    {
                        @Override
                        public void completion(Object result)
                        {
                            final Layer layer = (Layer) result;
                            layer.setName("Florida Coast Test (1024 polygons)");
                            SwingUtilities.invokeLater(() ->
                            {
                                AppFrame.this.getWwd().getModel().getLayers().add(layer);
                                System.out.println("=== Florida coast test shapefile loaded (1024 polygons) ===");
                            });
                        }

                        @Override
                        public void exception(Exception e)
                        {
                            Logging.logger().log(java.util.logging.Level.SEVERE,
                                "Failed to load Florida test shapefile", e);
                        }
                    });
            }
        }

        private void resetStats()
        {
            frameCount.set(0);
            lastReportTime = System.nanoTime();
            lastFrameTime = System.nanoTime();
            reportFrames = 0;
            sumFrameMs = 0;
            minFrameMs = Double.MAX_VALUE;
            maxFrameMs = 0;
        }

        private static void printMode()
        {
            String vboMode = ShapefilePolygons.forceDisableVBO ? "CLIENT MEMORY" : "VBO";
            String mergeMode = ShapefilePolygons.forceDisableMerged ? "PER-GROUP (2N calls)" : "MERGED (2 calls)";
            String tessMode = ShapefilePolygons.forceDisableEarClipping ? "GLU (legacy)" : "EAR-CLIP (new)";
            String shaderMode = ShapefilePolygons.forceDisableShader ? "FIXED-FUNC" : "SHADER";
            System.out.println(">>> Rendering: " + vboMode + " | " + mergeMode + " | Tess: " + tessMode + " | " + shaderMode);
            System.out.println();
        }

        private void onFrameComplete()
        {
            long now = System.nanoTime();
            double frameMs = (now - lastFrameTime) / 1_000_000.0;
            lastFrameTime = now;

            frameCount.incrementAndGet();
            reportFrames++;
            sumFrameMs += frameMs;
            if (frameMs < minFrameMs) minFrameMs = frameMs;
            if (frameMs > maxFrameMs) maxFrameMs = frameMs;

            long elapsed = now - lastReportTime;
            if (elapsed >= REPORT_INTERVAL_NS && reportFrames > 0)
            {
                double avgMs = sumFrameMs / reportFrames;
                double fps = reportFrames / (elapsed / 1_000_000_000.0);

                String vbo = ShapefilePolygons.forceDisableVBO ? "CLT" : "VBO";
                String merge = ShapefilePolygons.forceDisableMerged ? "2N" : "MRG";
                String shader = ShapefilePolygons.forceDisableShader ? "FF" : "SH";
                String mode = vbo + "+" + merge + "+" + shader;
                String stats = String.format(
                    "[%s] FPS: %.1f | avg %.1fms | min %.1fms | max %.1fms | frames: %d",
                    mode, fps, avgMs, minFrameMs, maxFrameMs, frameCount.get());

                System.out.println(stats);
                SwingUtilities.invokeLater(() -> setTitle("Shapefile Benchmark — " + stats));

                // Reset for next interval
                lastReportTime = now;
                reportFrames = 0;
                sumFrameMs = 0;
                minFrameMs = Double.MAX_VALUE;
                maxFrameMs = 0;
            }
        }
    }

    public static void main(String[] args)
    {
        System.out.println("=== ShapefilePolygons VBO Rendering Benchmark ===");
        System.out.println("Loading shapefile data...");
        System.out.println();
        start("Shapefile Benchmark", AppFrame.class);
    }
}
