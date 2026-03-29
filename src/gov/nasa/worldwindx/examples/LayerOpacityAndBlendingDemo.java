/*
 * WorldWind Reforged — LayerOpacityAndBlendingDemo
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New example demonstrating Phase 2 per-layer opacity control and layer ordering.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;

import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwind.layers.Earth.BMNGWMSLayer;
import gov.nasa.worldwind.layers.Earth.LandsatI3WMSLayer;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Demonstrates Phase 2 per-layer opacity control and interactive layer ordering.
 * <p>
 * Two imagery layers are loaded alongside the default WorldWind layer stack:
 * <ul>
 *   <li><b>BMNG Blue Marble</b> — monthly 500-m NASA BMNG via WMS</li>
 *   <li><b>Landsat i3</b> — 15-m Landsat natural color imagery via WMS</li>
 * </ul>
 * The control panel provides an individual opacity {@link JSlider} per layer wired
 * directly to {@link Layer#setOpacity(double)}, plus move-up / move-down buttons
 * to adjust layer render order in the {@link LayerList}.
 * <p>
 * This is a focused showcase of the Phase 2 opacity API; for time-animated WMS
 * layers see {@link WMSTimeSeriesDemo}.
 *
 * @see Layer#setOpacity(double)
 * @see LayerList
 *
 * seaglassfoundry.com — new example for WorldWind Reforged Phase 2
 */
public class LayerOpacityAndBlendingDemo extends ApplicationTemplate
{
    // ── AppFrame ──────────────────────────────────────────────────────────────

    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;
        // The two managed layers — created once, added to the model
        private final BMNGWMSLayer      bmng     = new BMNGWMSLayer();
        private final LandsatI3WMSLayer landsat  = new LandsatI3WMSLayer();

        // Parallel UI rows for each managed layer
        private LayerRow bmngRow;
        private LayerRow landsatRow;

        public AppFrame()
        {
            super(true, true, false);

            // Insert both layers before place names so they sit under labels
            insertBeforePlacenames(getWwd(), bmng);
            insertBeforePlacenames(getWwd(), landsat);

            getControlPanel().add(buildControlPanel(), BorderLayout.SOUTH);
        }

        // ── Control panel ─────────────────────────────────────────────────────

        private JPanel buildControlPanel()
        {
            JPanel root = new JPanel();
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            root.setBackground(WWStyle.BG_DARK);

            // ── BMNG section ──────────────────────────────────────────────────
            bmngRow = new LayerRow(bmng, "BMNG Blue Marble (500 m)");
            root.add(bmngRow.panel());
            root.add(vgap(WWStyle.GAP_XS));

            // ── Landsat section ───────────────────────────────────────────────
            landsatRow = new LayerRow(landsat, "Landsat i3 (15 m)");
            root.add(landsatRow.panel());
            root.add(vgap(WWStyle.GAP_S));

            // ── Layer ordering buttons ────────────────────────────────────────
            JPanel orderSection = new JPanel();
            orderSection.setLayout(new BoxLayout(orderSection, BoxLayout.Y_AXIS));
            orderSection.setBackground(WWStyle.BG_DARK);
            orderSection.setBorder(WWStyle.sectionBorder("Layer Order"));

            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, WWStyle.GAP_XS, 0));
            btnRow.setBackground(WWStyle.BG_DARK);
            btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            JButton bmngUpBtn   = WWStyle.button("BMNG Up");
            JButton bmngDownBtn = WWStyle.button("BMNG Down");
            JButton lsUpBtn     = WWStyle.button("Landsat Up");
            JButton lsDownBtn   = WWStyle.button("Landsat Down");

            bmngUpBtn.addActionListener(e   -> moveLayer(bmng,    -1));
            bmngDownBtn.addActionListener(e -> moveLayer(bmng,    +1));
            lsUpBtn.addActionListener(e     -> moveLayer(landsat, -1));
            lsDownBtn.addActionListener(e   -> moveLayer(landsat, +1));

            for (JButton b : new JButton[]{bmngUpBtn, bmngDownBtn, lsUpBtn, lsDownBtn}) {
                b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
                btnRow.add(b);
            }

            orderSection.add(btnRow);
            root.add(orderSection);

            return root;
        }

        // ── Layer movement ────────────────────────────────────────────────────

        /**
         * Moves {@code layer} within the model's {@link LayerList} by {@code delta}
         * positions (negative = toward index 0 = rendered first = underneath;
         * positive = toward higher index = rendered last = on top).
         */
        private void moveLayer(Layer layer, int delta)
        {
            LayerList layers = getWwd().getModel().getLayers();
            int idx = layers.indexOf(layer);
            if (idx < 0) return;
            int newIdx = Math.max(0, Math.min(layers.size() - 1, idx + delta));
            if (newIdx == idx) return;
            layers.remove(idx);
            layers.add(newIdx, layer);
            getWwd().redraw();
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private static JPanel vgap(int height)
        {
            JPanel p = new JPanel();
            p.setBackground(WWStyle.BG_DARK);
            p.setPreferredSize(new Dimension(0, height));
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            return p;
        }

        // ── Per-layer UI row ──────────────────────────────────────────────────

        /**
         * Encapsulates the visibility toggle, opacity slider, and section border
         * for one managed layer.
         */
        private class LayerRow
        {
            private final JPanel panel;

            LayerRow(Layer layer, String title)
            {
                panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                panel.setBackground(WWStyle.BG_DARK);
                panel.setBorder(WWStyle.sectionBorder(title));

                // Visibility toggle
                JToggleButton visBtn = new JToggleButton(layer.isEnabled() ? "Visible" : "Hidden",
                    layer.isEnabled());
                visBtn.setFont(WWStyle.FONT_BASE);
                visBtn.setForeground(WWStyle.FG_PRIMARY);
                visBtn.setBackground(WWStyle.BG_PANEL);
                visBtn.setFocusPainted(false);
                visBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
                visBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
                visBtn.addActionListener(e -> {
                    layer.setEnabled(visBtn.isSelected());
                    visBtn.setText(visBtn.isSelected() ? "Visible" : "Hidden");
                    getWwd().redraw();
                });

                // Opacity slider
                int initOpacity = (int) (layer.getOpacity() * 100);
                JSlider opacitySlider = WWStyle.slider(0, 100, initOpacity);
                opacitySlider.setAlignmentX(Component.LEFT_ALIGNMENT);
                opacitySlider.setToolTipText("Layer opacity");
                opacitySlider.addChangeListener(e -> {
                    layer.setOpacity(opacitySlider.getValue() / 100.0);
                    layer.setExpiryTime(System.currentTimeMillis());  // flush cached tiles
                    getWwd().redraw();
                });

                panel.add(visBtn);
                panel.add(vgap(WWStyle.GAP_XS));
                panel.add(WWStyle.label("Opacity:", false));
                panel.add(opacitySlider);
            }

            JPanel panel() { return panel; }
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind — Layer Opacity & Blending (Phase 2)", AppFrame.class);
    }
}
