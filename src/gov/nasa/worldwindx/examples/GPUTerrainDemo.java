/*
 * WorldWind Reforged — GPUTerrainDemo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New example demonstrating Phase 4 GPU terrain rendering modes.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Collection;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import gov.nasa.worldwind.event.RenderingEvent;
import gov.nasa.worldwind.event.RenderingListener;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.render.GLRuntimeCapabilities;
import gov.nasa.worldwind.util.PerformanceStatistic;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Demonstrates the Phase 4 GPU terrain rendering pipeline, allowing interactive
 * comparison of all four rendering modes at runtime.
 * <p>
 * <b>Rendering modes (highest to lowest GL requirement):</b>
 * <ol>
 *   <li><b>Compute + Tessellation</b> (GL 4.3+) — GPU-side frustum culling via
 *       compute shader + hardware tessellation. {@code glDrawElementsIndirect} with
 *       no CPU readback; maximum triangle density where visible.</li>
 *   <li><b>Tessellation only</b> (GL 4.0+) — Hardware tessellation with screen-space
 *       adaptive LOD, sub-grid heightmap displacement in the TES, and crack-free
 *       stitching between adjacent tiles.</li>
 *   <li><b>TerrainShader</b> (GL 3.0+) — GLSL 1.30 vertex shader with heightmap
 *       sampling; no tessellation.</li>
 *   <li><b>CPU Baseline</b> — Original fixed-function path: all vertex positions
 *       computed on the CPU, no shader terrain displacement.</li>
 * </ol>
 * Modes unavailable on the current GPU (insufficient OpenGL version) are shown
 * greyed out. The stats panel updates every frame with FPS, frame time,
 * terrain tile count, and estimated triangle count.
 *
 * @see GLRuntimeCapabilities#isUseComputeMesh()
 * @see GLRuntimeCapabilities#isUseTessellation()
 * @see GLRuntimeCapabilities#isUseTerrainShader()
 *
 * seaglassfoundry.com — new example for WorldWind Reforged Phase 4
 */
public class GPUTerrainDemo extends ApplicationTemplate
{
    // ── Rendering mode constants ──────────────────────────────────────────────
    private static final int MODE_CPU       = 0;
    private static final int MODE_SHADER    = 1;
    private static final int MODE_TESS      = 2;
    private static final int MODE_COMPUTE   = 3;

    private static final String[] MODE_LABELS = {
        "CPU Baseline (fixed-function)",
        "TerrainShader  (GL 3.0+)",
        "Tessellation   (GL 4.0+)",
        "Compute + Tess (GL 4.3+)",
    };

    // ── Camera presets [label, lat, lon, altitudeMetres] ─────────────────────
    private static final Object[][] CAMERA_PRESETS = {
        {"Grand Canyon",     36.10,  -112.10,   45_000d},
        {"Rocky Mountains",  39.50,  -106.00,  200_000d},
        {"Mount Everest",    27.99,    86.93,  120_000d},
        {"Alps Overview",    46.50,     8.50,  500_000d},
        {"World",            20.00,     0.00, 20_000_000d},
    };

    // ── AppFrame ──────────────────────────────────────────────────────────────

    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;
        private int currentMode = MODE_COMPUTE;  // attempt highest mode by default

        // Stats labels — updated each frame via RenderingListener
        private JLabel modeStatLabel;
        private JLabel fpsLabel;
        private JLabel frameTimeLabel;
        private JLabel tileCountLabel;
        private JLabel triEstLabel;

        // Radio buttons — may be disabled if hardware insufficient
        private final JRadioButton[] modeRadios = new JRadioButton[4];

        public AppFrame()
        {
            super(true, true, false);
            JPanel gpuControlPanel = buildControlPanel();

            // Start at Rocky Mountains — good for seeing LOD transitions
            getWwd().getView().setEyePosition(
                Position.fromDegrees(39.5, -106.0, 200_000));

            // After the first frame, update radio-button availability based on
            // actual GL version, then detach this one-shot listener.
            getWwd().addRenderingListener(new RenderingListener()
            {
                @Override
                public void stageChanged(RenderingEvent event)
                {
                    if (!RenderingEvent.BEFORE_BUFFER_SWAP.equals(event.getStage()))
                        return;
                    SwingUtilities.invokeLater(() -> updateRadioAvailability());
                    getWwd().removeRenderingListener(this);
                }
            });

            // seaglassfoundry.com: opt in to per-frame statistics collection. Without this,
            // DrawContextImpl.setPerFrameStatistic() short-circuits and TERRAIN_TILE_COUNT is
            // never published, so the demo's tile/triangle readouts always read zero.
            getWwd().setPerFrameStatisticsKeys(
                java.util.Set.of(PerformanceStatistic.TERRAIN_TILE_COUNT));

            // Per-frame stats update
            getWwd().addRenderingListener(event -> {
                if (!RenderingEvent.BEFORE_BUFFER_SWAP.equals(event.getStage()))
                    return;
                SwingUtilities.invokeLater(this::updateStats);
            });

            // Apply initial mode immediately (will be re-checked once GL is live)
            applyMode(currentMode);

            // Modified by seaglassfoundry.com - put the layers panel and controls panel in a
            // tabbed pane so they don't overlap. Each tab gets a scroll pane for small windows.
            // Use a split pane between the map and the side panel so it can be resized.
            if (this.controlPanel != null)
            {
                this.getContentPane().remove(this.controlPanel);
                this.getContentPane().remove(this.wwjPanel);

                JTabbedPane tabs = new JTabbedPane();
                tabs.setBackground(new Color(45, 45, 48));

                JScrollPane layerScroll = new JScrollPane(this.layerPanel);
                layerScroll.setBorder(null);
                tabs.addTab("Layers", layerScroll);

                JScrollPane controlScroll = new JScrollPane(gpuControlPanel);
                controlScroll.setBorder(null);
                tabs.addTab("Controls", controlScroll);

                this.controlPanel.add(tabs, BorderLayout.CENTER);

                JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    this.wwjPanel, this.controlPanel);
                splitPane.setResizeWeight(0.67);
                splitPane.setDividerSize(5);
                splitPane.setContinuousLayout(true);
                this.getContentPane().add(splitPane, BorderLayout.CENTER);

                this.addComponentListener(new java.awt.event.ComponentAdapter() {
                    private boolean initialized;
                    @Override
                    public void componentResized(java.awt.event.ComponentEvent e) {
                        if (!initialized) {
                            splitPane.setDividerLocation(getWidth() * 2 / 3);
                            initialized = true;
                        }
                    }
                });
            }
        }

        // ── Mode management ───────────────────────────────────────────────────

        private void applyMode(int mode)
        {
            currentMode = mode;
            GLRuntimeCapabilities caps = getWwd().getSceneController().getGLRuntimeCapabilities();
            if (caps == null) return;  // GL not ready yet — will apply on first frame

            switch (mode) {
                case MODE_CPU -> {
                    caps.setTerrainShaderEnabled(false);
                    caps.setTessellationEnabled(false);
                    caps.setComputeMeshEnabled(false);
                }
                case MODE_SHADER -> {
                    caps.setTerrainShaderEnabled(true);
                    caps.setTessellationEnabled(false);
                    caps.setComputeMeshEnabled(false);
                }
                case MODE_TESS -> {
                    caps.setTerrainShaderEnabled(true);
                    caps.setTessellationEnabled(true);
                    caps.setComputeMeshEnabled(false);
                }
                case MODE_COMPUTE -> {
                    caps.setTerrainShaderEnabled(true);
                    caps.setTessellationEnabled(true);
                    caps.setComputeMeshEnabled(true);
                }
            }
            getWwd().redraw();
        }

        /** Called once after GL initialises to enable/disable radio buttons. */
        private void updateRadioAvailability()
        {
            GLRuntimeCapabilities caps = getWwd().getSceneController().getGLRuntimeCapabilities();
            if (caps == null) return;

            modeRadios[MODE_CPU].setEnabled(true);
            modeRadios[MODE_SHADER].setEnabled(caps.isTerrainShaderAvailable());
            modeRadios[MODE_TESS].setEnabled(caps.isTessellationAvailable());
            modeRadios[MODE_COMPUTE].setEnabled(caps.isComputeMeshAvailable());

            // Fall back to highest available mode
            int best = MODE_CPU;
            if (caps.isTerrainShaderAvailable()) best = MODE_SHADER;
            if (caps.isTessellationAvailable())  best = MODE_TESS;
            if (caps.isComputeMeshAvailable())   best = MODE_COMPUTE;

            modeRadios[best].setSelected(true);
            applyMode(best);
        }

        // ── Stats update ──────────────────────────────────────────────────────

        private void updateStats()
        {
            GLRuntimeCapabilities caps = getWwd().getSceneController().getGLRuntimeCapabilities();

            // Active mode name
            String modeName;
            if (caps != null && caps.isUseComputeMesh())      modeName = "Compute + Tess";
            else if (caps != null && caps.isUseTessellation()) modeName = "Tessellation";
            else if (caps != null && caps.isUseTerrainShader()) modeName = "TerrainShader";
            else                                                modeName = "CPU Baseline";
            modeStatLabel.setText(modeName);

            // FPS and frame time from scene controller
            double fps       = getWwd().getSceneController().getFramesPerSecond();
            double frameTime = fps > 0 ? 1000.0 / fps : 0;
            fpsLabel.setText(String.format("%.1f", fps));
            frameTimeLabel.setText(String.format("%.1f ms", frameTime));

            // Terrain tile count from per-frame statistics
            int tileCount = 0;
            Collection<PerformanceStatistic> stats = getWwd().getSceneController().getPerFrameStatistics();
            if (stats != null) {
                for (PerformanceStatistic ps : stats) {
                    if (PerformanceStatistic.TERRAIN_TILE_COUNT.equals(ps.getKey())) {
                        Object val = ps.getValue();
                        if (val instanceof Number n) tileCount = n.intValue();
                        break;
                    }
                }
            }
            tileCountLabel.setText(String.valueOf(tileCount));

            // Triangle estimate: base mesh is (density+2)² × 2 triangles per tile ≈ 578 at density=16
            // Tessellation adds roughly tess_level² × 2 additional (estimate avg level 8 → ×64)
            long triEst;
            if (caps != null && (caps.isUseComputeMesh() || caps.isUseTessellation()))
                triEst = (long) tileCount * 578 * 32;   // ~32× amplification estimate
            else
                triEst = (long) tileCount * 578;
            String triStr = triEst >= 1_000_000
                ? String.format("~%.1f M", triEst / 1_000_000.0)
                : String.format("~%d K",   triEst / 1_000);
            triEstLabel.setText(triStr);
        }

        // ── Control panel ─────────────────────────────────────────────────────

        private JPanel buildControlPanel()
        {
            JPanel root = new JPanel();
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            root.setBackground(WWStyle.BG_DARK);

            root.add(buildModeSection());
            root.add(vgap(WWStyle.GAP_XS));
            root.add(buildDebugSection());
            root.add(vgap(WWStyle.GAP_XS));
            root.add(buildCameraSection());
            root.add(vgap(WWStyle.GAP_XS));
            root.add(buildStatsSection());

            return root;
        }

        private JPanel buildModeSection()
        {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(WWStyle.BG_DARK);
            panel.setBorder(WWStyle.sectionBorder("Rendering Mode"));

            ButtonGroup group = new ButtonGroup();
            for (int i = 0; i < MODE_LABELS.length; i++) {
                JRadioButton rb = new JRadioButton(MODE_LABELS[i]);
                rb.setFont(WWStyle.FONT_BASE);
                rb.setForeground(WWStyle.FG_PRIMARY);
                rb.setBackground(WWStyle.BG_DARK);
                rb.setFocusPainted(false);
                rb.setAlignmentX(Component.LEFT_ALIGNMENT);
                rb.setSelected(i == currentMode);
                rb.setEnabled(false);  // disabled until GL caps are known

                final int modeIdx = i;
                rb.addActionListener(e -> applyMode(modeIdx));

                group.add(rb);
                panel.add(rb);
                modeRadios[i] = rb;
            }
            return panel;
        }

        // seaglassfoundry.com: debug toggles for diagnosing tessellator/seam artifacts.
        // Wireframe makes terrain triangulation visible — a stray dark line that lights up
        // as a wireframe edge is a tessellator skirt/seam; one that stays solid over the
        // wireframe is being drawn after the terrain (post-process or stray surface shape).
        private JPanel buildDebugSection()
        {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(WWStyle.BG_DARK);
            panel.setBorder(WWStyle.sectionBorder("Debug"));

            JCheckBox wfInterior = new JCheckBox("Wireframe interior");
            wfInterior.setFont(WWStyle.FONT_BASE);
            wfInterior.setForeground(WWStyle.FG_PRIMARY);
            wfInterior.setBackground(WWStyle.BG_DARK);
            wfInterior.setFocusPainted(false);
            wfInterior.setAlignmentX(Component.LEFT_ALIGNMENT);
            wfInterior.addActionListener(e -> {
                getWwd().getModel().setShowWireframeInterior(wfInterior.isSelected());
                getWwd().redraw();
            });
            panel.add(wfInterior);

            JCheckBox wfExterior = new JCheckBox("Wireframe exterior");
            wfExterior.setFont(WWStyle.FONT_BASE);
            wfExterior.setForeground(WWStyle.FG_PRIMARY);
            wfExterior.setBackground(WWStyle.BG_DARK);
            wfExterior.setFocusPainted(false);
            wfExterior.setAlignmentX(Component.LEFT_ALIGNMENT);
            wfExterior.addActionListener(e -> {
                getWwd().getModel().setShowWireframeExterior(wfExterior.isSelected());
                getWwd().redraw();
            });
            panel.add(wfExterior);

            JCheckBox tileBoundaries = new JCheckBox("Show tile boundaries");
            tileBoundaries.setFont(WWStyle.FONT_BASE);
            tileBoundaries.setForeground(WWStyle.FG_PRIMARY);
            tileBoundaries.setBackground(WWStyle.BG_DARK);
            tileBoundaries.setFocusPainted(false);
            tileBoundaries.setAlignmentX(Component.LEFT_ALIGNMENT);
            tileBoundaries.addActionListener(e -> {
                getWwd().getModel().setShowTessellationBoundingVolumes(tileBoundaries.isSelected());
                getWwd().redraw();
            });
            panel.add(tileBoundaries);

            return panel;
        }

        private JPanel buildCameraSection()
        {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(WWStyle.BG_DARK);
            panel.setBorder(WWStyle.sectionBorder("Camera Presets"));

            for (Object[] preset : CAMERA_PRESETS) {
                String label = (String) preset[0];
                double lat   = (double) preset[1];
                double lon   = (double) preset[2];
                double alt   = (double) preset[3];

                JButton btn = WWStyle.button(label);
                btn.setAlignmentX(Component.LEFT_ALIGNMENT);
                btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
                btn.addActionListener(e ->
                    getWwd().getView().setEyePosition(Position.fromDegrees(lat, lon, alt)));
                panel.add(btn);
                panel.add(vgap(WWStyle.GAP_XS));
            }
            return panel;
        }

        private JPanel buildStatsSection()
        {
            JPanel panel = new JPanel(new GridLayout(0, 2, WWStyle.GAP_XS, WWStyle.GAP_XS));
            panel.setBackground(WWStyle.BG_DARK);
            panel.setBorder(WWStyle.sectionBorder("Render Stats"));

            modeStatLabel    = statsValue("—");
            fpsLabel         = statsValue("—");
            frameTimeLabel   = statsValue("—");
            tileCountLabel   = statsValue("—");
            triEstLabel      = statsValue("—");

            panel.add(statsKey("Mode:"));      panel.add(modeStatLabel);
            panel.add(statsKey("FPS:"));       panel.add(fpsLabel);
            panel.add(statsKey("Frame time:")); panel.add(frameTimeLabel);
            panel.add(statsKey("Tiles:"));     panel.add(tileCountLabel);
            panel.add(statsKey("Triangles:")); panel.add(triEstLabel);

            return panel;
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private static JLabel statsKey(String text)
        {
            JLabel l = WWStyle.label(text, false);
            l.setHorizontalAlignment(SwingConstants.RIGHT);
            return l;
        }

        private static JLabel statsValue(String text)
        {
            JLabel l = new JLabel(text);
            l.setFont(WWStyle.FONT_BOLD);
            l.setForeground(WWStyle.ACCENT);
            return l;
        }

        private static JPanel vgap(int height)
        {
            JPanel p = new JPanel();
            p.setBackground(WWStyle.BG_DARK);
            p.setPreferredSize(new Dimension(0, height));
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            return p;
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind — GPU Terrain Demo (Phase 4)", AppFrame.class);
    }
}
