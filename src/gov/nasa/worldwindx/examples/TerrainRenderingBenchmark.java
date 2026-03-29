/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — Interactive benchmark showcasing the Phase 4 terrain GPU rendering
 * pipeline. Demonstrates all four rendering tiers and allows live switching between
 * them so the performance impact of each tier is immediately visible.
 *
 * The four tiers (Tasks 4.1–4.3):
 *   [1] Fixed-Function  — legacy OpenGL 1.x, CPU-side everything
 *   [2] TerrainShader   — GLSL 1.30 heightmap shader (GL 3.0+, Task 4.1)
 *   [3] Tessellation    — adaptive GPU LOD + crack-free stitching (GL 4.0+, Tasks 4.2/4.4/4.5)
 *   [4] Compute Mesh    — GPU frustum culling + indirect draw (GL 4.3+, Task 4.3)
 *
 * A semi-transparent HUD overlay (ScreenAnnotation) shows live FPS / frame time
 * for each mode. The camera automatically orbits the globe (FPSAnimator, 60 fps)
 * to produce a steady stream of terrain tiles for a fair comparison.
 *
 * Keys:
 *   1-4     — switch directly to that rendering mode
 *   Space   — cycle to the next available mode
 *   F       — toggle auto-fly orbit on / off
 *   R       — reset frame-time statistics for the current mode
 */
package gov.nasa.worldwindx.examples;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import com.jogamp.opengl.GLAnimatorControl;
import com.jogamp.opengl.util.FPSAnimator;

import gov.nasa.worldwind.SceneController;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;
import gov.nasa.worldwind.event.RenderingEvent;
import gov.nasa.worldwind.event.RenderingListener;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.AnnotationLayer;
import gov.nasa.worldwind.render.AnnotationAttributes;
import gov.nasa.worldwind.render.GLRuntimeCapabilities;
import gov.nasa.worldwind.render.ScreenAnnotation;
import gov.nasa.worldwind.util.PerformanceStatistic;

/**
 * Interactive benchmark that demonstrates the Phase 4 terrain GPU rendering pipeline.
 * <p>
 * Run the application, navigate or let auto-fly orbit the globe, and use keys 1–4 to
 * switch between rendering tiers. The on-screen HUD updates every ~3 seconds with FPS,
 * frame time, and terrain tile count so you can compare each tier side-by-side.
 * <p>
 * Expected performance on a GL 4.3+ GPU (looking down at dense terrain from ~500 km):
 * <ul>
 *   <li><b>Fixed-function</b>: baseline; all tessellation work on CPU</li>
 *   <li><b>TerrainShader</b>: similar FPS to fixed-function; benefit is heightmap detail</li>
 *   <li><b>Tessellation</b>: lower triangle budget at distance → noticeably higher FPS as
 *       the GPU adaptively subdivides only close-up patches</li>
 *   <li><b>Compute Mesh</b>: GPU frustum-culls invisible patches before drawing → fewest
 *       draw commands; biggest win in scenes with many off-screen tiles</li>
 * </ul>
 */
public class TerrainRenderingBenchmark extends ApplicationTemplate
{
    // =========================================================================
    // Rendering mode descriptor
    // =========================================================================

    /**
     * Encodes the GLRuntimeCapabilities flags for each rendering tier. The boolean
     * fields map directly to {@code setTerrainShaderEnabled}, {@code setTessellationEnabled},
     * and {@code setComputeMeshEnabled}.
     */
    enum RenderMode
    {
        FIXED_FUNCTION ("Fixed-Function  (GL 1.x)",  false, false, false),
        TERRAIN_SHADER ("TerrainShader   (GL 3.0+)",  true,  false, false),
        TESSELLATION   ("Tessellation    (GL 4.0+)",  true,  true,  false),
        COMPUTE_MESH   ("Compute Mesh    (GL 4.3+)",  true,  true,  true);

        final String  label;
        final boolean enableShader;
        final boolean enableTessellation;
        final boolean enableComputeMesh;

        RenderMode(String label, boolean s, boolean t, boolean c)
        {
            this.label = label;
            this.enableShader = s;
            this.enableTessellation = t;
            this.enableComputeMesh = c;
        }

        /** Returns the next entry in declaration order, wrapping to the first. */
        RenderMode next()
        {
            RenderMode[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }
    }

    // =========================================================================
    // AppFrame
    // =========================================================================

    public static class AppFrame extends ApplicationTemplate.AppFrame
        implements RenderingListener
    {
        private static final long serialVersionUID = 1L;
        // ---- Mode state -----------------------------------------------------
        private volatile RenderMode currentMode = RenderMode.FIXED_FUNCTION;
        /** Set to true on the first BEFORE_RENDERING so we can detect capabilities once. */
        private final AtomicBoolean capabilitiesDetected = new AtomicBoolean(false);

        // ---- Frame-time statistics ------------------------------------------
        private long lastFrameNanos  = System.nanoTime();
        private long lastReportNanos = System.nanoTime();
        private double sumFrameMs    = 0;
        private double minFrameMs    = Double.MAX_VALUE;
        private double maxFrameMs    = 0;
        private long   reportFrames  = 0;
        private static final long REPORT_INTERVAL_NS = 3_000_000_000L; // 3 s

        // ---- Auto-fly orbit -------------------------------------------------
        private volatile boolean autoFly = true;
        private long lastFlyTimeMs       = System.currentTimeMillis();
        /** Orbit altitude in metres — low enough for terrain tessellation to engage. */
        private static final double FLY_ALTITUDE_M = 500_000;
        private static final double FLY_DEG_PER_S  = 25.0;
        private Position eyePosition = Position.fromDegrees(30.0, 0.0, FLY_ALTITUDE_M);
        private GLAnimatorControl animator;

        // ---- HUD ------------------------------------------------------------
        private AnnotationLayer hudLayer;
        private ScreenAnnotation hudAnnotation;
        // =====================================================================
        // Constructor
        // =====================================================================

        public AppFrame()
        {
            // Use a larger canvas so there is more terrain to render.
            super(new Dimension(1280, 800));

            // Enable all performance statistics so we can read terrain tile count.
            getWwd().setPerFrameStatisticsKeys(PerformanceStatistic.ALL_STATISTICS_SET);

            setupHud();
            setupKeyBindings();

            // Single RenderingListener drives both orbit (BEFORE_RENDERING) and
            // frame-time measurement (AFTER_BUFFER_SWAP).
            getWwd().addRenderingListener(this);

            // FPSAnimator keeps rendering continuously regardless of user input,
            // giving stable frame-time measurements.
            animator = new FPSAnimator((WorldWindowGLCanvas) getWwd(), 60);
            animator.start();

            printStartupInstructions();
        }

        // =====================================================================
        // RenderingListener
        // =====================================================================

        @Override
        public void stageChanged(RenderingEvent event)
        {
            if (RenderingEvent.BEFORE_RENDERING.equals(event.getStage()))
            {
                onBeforeRendering();
            }
            else if (RenderingEvent.AFTER_BUFFER_SWAP.equals(event.getStage()))
            {
                onAfterSwap();
            }
        }

        private void onBeforeRendering()
        {
            // One-time capability detection: read the actual GL version flags and
            // apply the best mode the hardware supports.
            if (!capabilitiesDetected.getAndSet(true))
            {
                RenderMode best = detectBestMode();
                applyMode(best);
            }

            // Advance the orbit camera.
            if (autoFly && getWwd().getView() != null)
            {
                long nowMs = System.currentTimeMillis();
                double elapsedS = (nowMs - lastFlyTimeMs) * 1e-3;
                lastFlyTimeMs = nowMs;

                double newLon = Angle.normalizedDegreesLongitude(
                    eyePosition.getLongitude().degrees + FLY_DEG_PER_S * elapsedS);
                eyePosition = Position.fromDegrees(
                    eyePosition.getLatitude().degrees, newLon, FLY_ALTITUDE_M);

                getWwd().getView().stopAnimations();
                getWwd().getView().setEyePosition(eyePosition);
            }
        }

        private void onAfterSwap()
        {
            long now = System.nanoTime();
            double frameMs = (now - lastFrameNanos) / 1_000_000.0;
            lastFrameNanos = now;

            reportFrames++;
            sumFrameMs += frameMs;
            if (frameMs < minFrameMs) minFrameMs = frameMs;
            if (frameMs > maxFrameMs) maxFrameMs = frameMs;

            long elapsed = now - lastReportNanos;
            if (elapsed >= REPORT_INTERVAL_NS && reportFrames > 0)
            {
                double fps    = reportFrames / (elapsed / 1_000_000_000.0);
                double avgMs  = sumFrameMs / reportFrames;
                int    tiles  = readTerrainTileCount();

                String statsLine = String.format(
                    "[%-14s]  FPS: %5.1f  |  avg %5.1fms  |  min %5.1fms  |  max %5.1fms  |  tiles: %d",
                    currentMode.name(), fps, avgMs, minFrameMs, maxFrameMs, tiles);

                System.out.println(statsLine);

                // Update HUD and title bar on the EDT.
                final String hudText  = buildHudText(fps, avgMs, minFrameMs, maxFrameMs, tiles);
                final String titleSuffix = String.format("  [%s]  %.1f fps  avg %.1fms",
                    currentMode.name(), fps, avgMs);
                SwingUtilities.invokeLater(() ->
                {
                    hudAnnotation.setText(hudText);
                    setTitle("Phase 4 Terrain Benchmark —" + titleSuffix);
                });

                // Reset accumulators.
                lastReportNanos = now;
                reportFrames    = 0;
                sumFrameMs      = 0;
                minFrameMs      = Double.MAX_VALUE;
                maxFrameMs      = 0;
            }
        }

        // =====================================================================
        // Mode switching
        // =====================================================================

        private void applyMode(RenderMode mode)
        {
            SceneController sc = getWwd().getSceneController();
            if (sc == null) return;
            GLRuntimeCapabilities glrc = sc.getGLRuntimeCapabilities();
            if (glrc == null) return;

            glrc.setTerrainShaderEnabled(mode.enableShader);
            glrc.setTessellationEnabled(mode.enableTessellation);
            glrc.setComputeMeshEnabled(mode.enableComputeMesh);

            currentMode = mode;
            resetStats();
            getWwd().redraw();

            System.out.println();
            System.out.println(">>> Mode: " + mode.label + (isModeAvailable(mode) ? "" : "  [NOT AVAILABLE on this GPU]"));
        }

        /** Returns the highest tier whose hardware availability flag is set. */
        private RenderMode detectBestMode()
        {
            SceneController sc = getWwd().getSceneController();
            if (sc == null) return RenderMode.FIXED_FUNCTION;
            GLRuntimeCapabilities glrc = sc.getGLRuntimeCapabilities();
            if (glrc == null) return RenderMode.FIXED_FUNCTION;

            if (glrc.isComputeMeshAvailable())   return RenderMode.COMPUTE_MESH;
            if (glrc.isTessellationAvailable())  return RenderMode.TESSELLATION;
            if (glrc.isTerrainShaderAvailable()) return RenderMode.TERRAIN_SHADER;
            return RenderMode.FIXED_FUNCTION;
        }

        /** Returns true if the hardware availability flag for this mode's highest requirement is set. */
        private boolean isModeAvailable(RenderMode mode)
        {
            SceneController sc = getWwd().getSceneController();
            if (sc == null) return mode == RenderMode.FIXED_FUNCTION;
            GLRuntimeCapabilities glrc = sc.getGLRuntimeCapabilities();
            if (glrc == null) return mode == RenderMode.FIXED_FUNCTION;

            switch (mode)
            {
                case FIXED_FUNCTION: return true;
                case TERRAIN_SHADER: return glrc.isTerrainShaderAvailable();
                case TESSELLATION:   return glrc.isTessellationAvailable();
                case COMPUTE_MESH:   return glrc.isComputeMeshAvailable();
                default:             return false;
            }
        }

        private void resetStats()
        {
            lastFrameNanos  = System.nanoTime();
            lastReportNanos = System.nanoTime();
            reportFrames    = 0;
            sumFrameMs      = 0;
            minFrameMs      = Double.MAX_VALUE;
            maxFrameMs      = 0;
        }

        // =====================================================================
        // HUD
        // =====================================================================

        private void setupHud()
        {
            // Style: dark translucent panel, monospaced font, no border.
            AnnotationAttributes attrs = new AnnotationAttributes();
            attrs.setBackgroundColor(new Color(0, 0, 0, 180));
            attrs.setTextColor(Color.WHITE);
            attrs.setFont(Font.decode("Monospaced-PLAIN-13"));
            attrs.setInsets(new Insets(10, 14, 10, 14));
            attrs.setBorderColor(new Color(255, 255, 255, 60));
            attrs.setCornerRadius(4);

            // Position in the top-left; Y = viewport height - annotation height.
            // We use a generous Y value (530) that keeps the HUD visible on a 800-high canvas.
            hudAnnotation = new ScreenAnnotation(buildInitialHudText(), new Point(14, 530), attrs);

            hudLayer = new AnnotationLayer();
            hudLayer.setName("Phase 4 HUD");
            hudLayer.addAnnotation(hudAnnotation);

            // Insert before compass so the HUD renders on top.
            insertBeforeCompass(getWwd(), hudLayer);
        }

        private String buildInitialHudText()
        {
            return "Phase 4 Terrain Rendering Benchmark\n"
                 + "------------------------------------\n"
                 + "Initialising GPU capabilities...\n"
                 + "\n"
                 + "[1] Fixed-fn  [2] Shader  [3] Tess  [4] Compute\n"
                 + "[Space] cycle mode   [F] auto-fly   [R] reset";
        }

        private String buildHudText(double fps, double avgMs, double minMs, double maxMs, int tiles)
        {
            SceneController sc = getWwd().getSceneController();
            GLRuntimeCapabilities glrc = (sc != null) ? sc.getGLRuntimeCapabilities() : null;

            StringBuilder sb = new StringBuilder();
            sb.append("Phase 4 Terrain Rendering Benchmark\n");
            sb.append("------------------------------------\n");

            // Active mode line with availability indicator.
            sb.append(String.format("Mode   : %s %s\n",
                currentMode.label,
                isModeAvailable(currentMode) ? "" : "[not available]"));

            sb.append(String.format("FPS    : %5.1f   Frame: %5.1f ms avg\n", fps, avgMs));
            sb.append(String.format("Min    : %5.1f ms   Max: %5.1f ms\n", minMs, maxMs));
            sb.append(String.format("Tiles  : %d\n", tiles));
            sb.append("------------------------------------\n");

            // Availability matrix.
            if (glrc != null)
            {
                sb.append(String.format("[1] Fixed-fn    always\n"));
                sb.append(String.format("[2] Shader      %s\n", glrc.isTerrainShaderAvailable() ? "available" : "unavailable"));
                sb.append(String.format("[3] Tessellation %s\n", glrc.isTessellationAvailable()  ? "available" : "unavailable"));
                sb.append(String.format("[4] Compute Mesh %s\n", glrc.isComputeMeshAvailable()   ? "available" : "unavailable"));
                sb.append("------------------------------------\n");
            }

            sb.append("[Space] cycle  [F] fly:" + (autoFly ? "ON " : "OFF") + "  [R] reset");
            return sb.toString();
        }

        // =====================================================================
        // Terrain tile count
        // =====================================================================

        private int readTerrainTileCount()
        {
            Collection<PerformanceStatistic> stats = getWwd().getSceneController().getPerFrameStatistics();
            if (stats == null) return 0;
            for (PerformanceStatistic ps : stats)
            {
                if (PerformanceStatistic.TERRAIN_TILE_COUNT.equals(ps.getKey()))
                {
                    Object val = ps.getValue();
                    if (val instanceof Number)
                        return ((Number) val).intValue();
                }
            }
            return 0;
        }

        // =====================================================================
        // Key bindings
        // =====================================================================

        private void setupKeyBindings()
        {
            ((Component) getWwd()).addKeyListener(new KeyAdapter()
            {
                @Override
                public void keyPressed(KeyEvent e)
                {
                    switch (e.getKeyCode())
                    {
                        case KeyEvent.VK_1:
                            applyMode(RenderMode.FIXED_FUNCTION);
                            break;
                        case KeyEvent.VK_2:
                            applyMode(RenderMode.TERRAIN_SHADER);
                            break;
                        case KeyEvent.VK_3:
                            applyMode(RenderMode.TESSELLATION);
                            break;
                        case KeyEvent.VK_4:
                            applyMode(RenderMode.COMPUTE_MESH);
                            break;
                        case KeyEvent.VK_SPACE:
                            applyMode(currentMode.next());
                            break;
                        case KeyEvent.VK_F:
                            autoFly = !autoFly;
                            lastFlyTimeMs = System.currentTimeMillis();
                            System.out.println("Auto-fly: " + (autoFly ? "ON" : "OFF"));
                            break;
                        case KeyEvent.VK_R:
                            resetStats();
                            System.out.println("Stats reset.");
                            break;
                    }
                }
            });
        }

        // =====================================================================
        // Startup message
        // =====================================================================

        private static void printStartupInstructions()
        {
            System.out.println();
            System.out.println("=== Phase 4 Terrain Rendering Benchmark ===");
            System.out.println("Auto-fly orbit active. Stats reported every 3 seconds.");
            System.out.println();
            System.out.println("  1  — Fixed-Function  (GL 1.x, baseline)");
            System.out.println("  2  — TerrainShader   (GL 3.0+, Task 4.1)");
            System.out.println("  3  — Tessellation    (GL 4.0+, Tasks 4.2/4.4/4.5)");
            System.out.println("  4  — Compute Mesh    (GL 4.3+, Task 4.3)");
            System.out.println("  Space — cycle to next mode");
            System.out.println("  F     — toggle auto-fly");
            System.out.println("  R     — reset statistics");
            System.out.println();
        }
    }

    // =========================================================================
    // Entry point
    // =========================================================================

    public static void main(String[] args)
    {
        start("Phase 4 Terrain Rendering Benchmark", AppFrame.class);
    }
}
