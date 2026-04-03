/*
 * Copyright 2025-2026 seaglassfoundry.com. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Part of the WorldWind Reforged project — seaglassfoundry.com
 * WMSLayerManager.java: complete rewrite of NASA's original WMSLayerManager
 * example. Replaces the old separate-JFrame / raw-Thread / hardcoded-servers
 * approach with a modern split-pane layout that reuses WMSServerPanel,
 * WMSLayerEntry, and WWStyle for a polished, integrated WMS browsing and
 * layer management experience.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.globes.ElevationModel;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwind.ogc.wms.WMSCapabilities;
import gov.nasa.worldwind.ogc.wms.WMSLayerCapabilities;
import gov.nasa.worldwind.terrain.CompoundElevationModel;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Demonstrates WMS server browsing and layer management integrated into
 * WorldWind's layer system. Connect to any WMS server, browse its catalog,
 * add layers to the globe, and manage them with opacity and ordering controls.
 *
 * <p>This example showcases the reusable {@link WMSServerPanel} and
 * {@link WMSLayerEntry} components from the WorldWind Reforged project.</p>
 *
 * seaglassfoundry.com — complete rewrite of original NASA WMSLayerManager
 */
public class WMSLayerManager extends ApplicationTemplate
{
    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;

        private final List<WMSLayerEntry> catalogEntries = new ArrayList<>();
        private final List<WMSLayerEntry> activeEntries = new ArrayList<>();
        private JPanel catalogListPanel;
        private JPanel activeListPanel;
        private JTextField searchField;
        private JLabel catalogStatusLabel;
        private JLabel activeCountLabel;

        public AppFrame()
        {
            super(true, true, false);

            // ── Build the tabbed control panel ──────────────────────────
            JTabbedPane tabs = new JTabbedPane();
            tabs.setBackground(WWStyle.BG_DARK);

            // Tab 1: Standard layers
            JScrollPane layerScroll = WWStyle.scrollPane(this.layerPanel);
            tabs.addTab("Layers", layerScroll);

            // Tab 2: WMS Browser
            tabs.addTab("WMS Browser", buildBrowserTab());

            // Tab 3: Active WMS
            tabs.addTab("Active WMS", buildActiveTab());

            // ── Split pane layout (Phase 9 pattern) ─────────────────────
            if (this.controlPanel != null)
            {
                this.getContentPane().remove(this.controlPanel);
                this.getContentPane().remove(this.wwjPanel);

                this.controlPanel.add(tabs, BorderLayout.CENTER);

                JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    this.wwjPanel, this.controlPanel);
                splitPane.setResizeWeight(0.67);
                splitPane.setDividerSize(5);
                splitPane.setContinuousLayout(true);
                this.getContentPane().add(splitPane, BorderLayout.CENTER);

                this.addComponentListener(new java.awt.event.ComponentAdapter()
                {
                    private boolean initialized;

                    @Override
                    public void componentResized(java.awt.event.ComponentEvent e)
                    {
                        if (!initialized)
                        {
                            splitPane.setDividerLocation(getWidth() * 2 / 3);
                            initialized = true;
                        }
                    }
                });
            }
        }

        // ────────────────────────────────────────────────────────────────
        //  WMS Browser tab
        // ────────────────────────────────────────────────────────────────

        private JPanel buildBrowserTab()
        {
            JPanel browser = new JPanel(new BorderLayout(0, WWStyle.GAP_S));
            browser.setBackground(WWStyle.BG_DARK);
            browser.setBorder(WWStyle.padded());

            // Server panel at top
            WMSServerPanel serverPanel = new WMSServerPanel();
            serverPanel.setOnConnected(this::onServerConnected);
            browser.add(serverPanel, BorderLayout.NORTH);

            // Catalog area (search + list)
            JPanel catalogArea = new JPanel(new BorderLayout(0, WWStyle.GAP_XS));
            catalogArea.setBackground(WWStyle.BG_DARK);

            // Search field
            JPanel searchRow = new JPanel(new BorderLayout(WWStyle.GAP_XS, 0));
            searchRow.setBackground(WWStyle.BG_DARK);
            searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

            JLabel searchIcon = WWStyle.label("Filter:", false);
            searchRow.add(searchIcon, BorderLayout.WEST);

            searchField = WWStyle.textField(20);
            searchField.setToolTipText("Type to filter layers by name");
            searchField.getDocument().addDocumentListener(new DocumentListener()
            {
                @Override public void insertUpdate(DocumentEvent e)  { filterCatalog(); }
                @Override public void removeUpdate(DocumentEvent e)  { filterCatalog(); }
                @Override public void changedUpdate(DocumentEvent e) { filterCatalog(); }
            });
            searchRow.add(searchField, BorderLayout.CENTER);

            catalogArea.add(searchRow, BorderLayout.NORTH);

            // Catalog list
            catalogListPanel = new JPanel();
            catalogListPanel.setLayout(new BoxLayout(catalogListPanel, BoxLayout.Y_AXIS));
            catalogListPanel.setBackground(WWStyle.BG_DARK);

            JScrollPane catalogScroll = WWStyle.scrollPane(catalogListPanel);
            catalogArea.add(catalogScroll, BorderLayout.CENTER);

            // Status label
            catalogStatusLabel = WWStyle.label("Connect to a server to browse layers", false);
            catalogArea.add(catalogStatusLabel, BorderLayout.SOUTH);

            browser.add(catalogArea, BorderLayout.CENTER);

            return browser;
        }

        // ────────────────────────────────────────────────────────────────
        //  Active WMS tab
        // ────────────────────────────────────────────────────────────────

        private JPanel buildActiveTab()
        {
            JPanel active = new JPanel(new BorderLayout(0, WWStyle.GAP_S));
            active.setBackground(WWStyle.BG_DARK);
            active.setBorder(WWStyle.padded());

            // Header with count
            activeCountLabel = WWStyle.label("No active WMS layers", false);
            active.add(activeCountLabel, BorderLayout.NORTH);

            // Active layer list
            activeListPanel = new JPanel();
            activeListPanel.setLayout(new BoxLayout(activeListPanel, BoxLayout.Y_AXIS));
            activeListPanel.setBackground(WWStyle.BG_DARK);

            JScrollPane activeScroll = WWStyle.scrollPane(activeListPanel);
            active.add(activeScroll, BorderLayout.CENTER);

            return active;
        }

        // ────────────────────────────────────────────────────────────────
        //  Server connection callback
        // ────────────────────────────────────────────────────────────────

        private void onServerConnected(WMSCapabilities caps)
        {
            catalogEntries.clear();

            List<WMSLayerCapabilities> namedLayers = caps.getNamedLayers();
            if (namedLayers == null || namedLayers.isEmpty())
            {
                catalogStatusLabel.setText("Server returned no layers");
                rebuildCatalogUI();
                return;
            }

            for (WMSLayerCapabilities lc : namedLayers)
                catalogEntries.add(new WMSLayerEntry(caps, lc));

            catalogEntries.sort(Comparator.comparing(WMSLayerEntry::getTitle, String.CASE_INSENSITIVE_ORDER));

            searchField.setText("");
            catalogStatusLabel.setText(catalogEntries.size() + " layers available");
            rebuildCatalogUI();
        }

        // ────────────────────────────────────────────────────────────────
        //  Catalog UI
        // ────────────────────────────────────────────────────────────────

        private void filterCatalog()
        {
            rebuildCatalogUI();
        }

        private void rebuildCatalogUI()
        {
            catalogListPanel.removeAll();

            String filter = searchField.getText().trim().toLowerCase();
            int shown = 0;

            for (WMSLayerEntry entry : catalogEntries)
            {
                String title = entry.getTitle();
                if (!filter.isEmpty() && !title.toLowerCase().contains(filter))
                    continue;

                catalogListPanel.add(createCatalogRow(entry));
                catalogListPanel.add(Box.createVerticalStrut(2));
                shown++;
            }

            if (shown == 0 && !catalogEntries.isEmpty())
            {
                JLabel noMatch = WWStyle.label("No layers match filter", false);
                noMatch.setAlignmentX(Component.LEFT_ALIGNMENT);
                catalogListPanel.add(noMatch);
            }

            catalogListPanel.revalidate();
            catalogListPanel.repaint();
        }

        private JPanel createCatalogRow(WMSLayerEntry entry)
        {
            JPanel row = new JPanel(new BorderLayout(WWStyle.GAP_XS, 0));
            row.setBackground(entry.isAddedToGlobe() ? WWStyle.BG_SELECTED : WWStyle.BG_PANEL);
            row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, WWStyle.BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Left: title + metadata
            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            infoPanel.setOpaque(false);

            JLabel titleLabel = WWStyle.label(truncate(entry.getTitle(), 45));
            titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(titleLabel);

            // Subtitle: layer name + badges
            StringBuilder sub = new StringBuilder();
            sub.append(entry.getName());
            if (entry.hasTimeDimension())
                sub.append("  [TIME]");
            if (entry.isElevationModel())
                sub.append("  [ELEV]");
            JLabel subLabel = WWStyle.label(truncate(sub.toString(), 50), false);
            subLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(subLabel);

            row.add(infoPanel, BorderLayout.CENTER);

            // Right: add/remove button
            boolean onGlobe = entry.isAddedToGlobe();
            javax.swing.JButton toggleBtn = onGlobe
                ? WWStyle.button("Remove")
                : WWStyle.accentButton("Add");
            toggleBtn.setPreferredSize(new Dimension(72, 28));
            toggleBtn.addActionListener(e ->
            {
                if (entry.isAddedToGlobe())
                    removeFromGlobe(entry);
                else
                    addToGlobe(entry);
                rebuildCatalogUI();
                rebuildActiveUI();
            });
            row.add(toggleBtn, BorderLayout.EAST);

            // Tooltip with abstract
            String abs = entry.getAbstract();
            if (abs != null && !abs.isEmpty())
                row.setToolTipText("<html><body style='width:300px'>" + abs + "</body></html>");

            // Hover effect
            row.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent e)
                {
                    if (!entry.isAddedToGlobe())
                        row.setBackground(WWStyle.BG_HOVER);
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    row.setBackground(entry.isAddedToGlobe() ? WWStyle.BG_SELECTED : WWStyle.BG_PANEL);
                }
            });

            return row;
        }

        // ────────────────────────────────────────────────────────────────
        //  Active WMS UI
        // ────────────────────────────────────────────────────────────────

        private void rebuildActiveUI()
        {
            activeListPanel.removeAll();

            if (activeEntries.isEmpty())
            {
                activeCountLabel.setText("No active WMS layers");
                JLabel hint = WWStyle.label("Add layers from the WMS Browser tab", false);
                hint.setAlignmentX(Component.LEFT_ALIGNMENT);
                activeListPanel.add(hint);
            }
            else
            {
                activeCountLabel.setText(activeEntries.size() + " active WMS layer"
                    + (activeEntries.size() != 1 ? "s" : ""));

                for (int i = 0; i < activeEntries.size(); i++)
                    activeListPanel.add(createActiveRow(activeEntries.get(i), i));
            }

            activeListPanel.revalidate();
            activeListPanel.repaint();
        }

        private JPanel createActiveRow(WMSLayerEntry entry, int index)
        {
            JPanel row = new JPanel(new BorderLayout(WWStyle.GAP_XS, 0));
            row.setBackground(WWStyle.BG_PANEL);
            row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, WWStyle.BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Left: title + opacity slider
            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            infoPanel.setOpaque(false);

            JLabel titleLabel = WWStyle.label(truncate(entry.getTitle(), 40));
            titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(titleLabel);

            // Opacity slider (only for Layer components, not ElevationModel)
            Object comp = entry.getComponent();
            if (comp instanceof Layer layer)
            {
                JPanel opacityRow = new JPanel(new FlowLayout(FlowLayout.LEFT, WWStyle.GAP_XS, 0));
                opacityRow.setOpaque(false);
                opacityRow.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel opLabel = WWStyle.label("Opacity:", false);
                opacityRow.add(opLabel);

                javax.swing.JSlider opSlider = WWStyle.slider(0, 100, (int) (layer.getOpacity() * 100));
                opSlider.setPreferredSize(new Dimension(120, 20));
                opSlider.setMaximumSize(new Dimension(120, 20));
                JLabel opValue = WWStyle.label(opSlider.getValue() + "%", false);
                opSlider.addChangeListener(e ->
                {
                    double opacity = opSlider.getValue() / 100.0;
                    layer.setOpacity(opacity);
                    opValue.setText(opSlider.getValue() + "%");
                    getWwd().redraw();
                });
                opacityRow.add(opSlider);
                opacityRow.add(opValue);
                infoPanel.add(opacityRow);
            }

            row.add(infoPanel, BorderLayout.CENTER);

            // Right: ordering + remove buttons
            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
            buttonPanel.setOpaque(false);

            // Move up / move down
            JPanel orderPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            orderPanel.setOpaque(false);

            javax.swing.JButton upBtn = WWStyle.flatButton("\u25B2");
            upBtn.setToolTipText("Move up (render later / on top)");
            upBtn.setEnabled(index > 0);
            upBtn.addActionListener(e ->
            {
                swapLayers(index, index - 1);
                rebuildActiveUI();
            });
            orderPanel.add(upBtn);

            javax.swing.JButton downBtn = WWStyle.flatButton("\u25BC");
            downBtn.setToolTipText("Move down (render earlier / below)");
            downBtn.setEnabled(index < activeEntries.size() - 1);
            downBtn.addActionListener(e ->
            {
                swapLayers(index, index + 1);
                rebuildActiveUI();
            });
            orderPanel.add(downBtn);
            buttonPanel.add(orderPanel);

            // Remove button
            javax.swing.JButton removeBtn = WWStyle.button("Remove");
            removeBtn.setPreferredSize(new Dimension(72, 24));
            removeBtn.addActionListener(e ->
            {
                removeFromGlobe(entry);
                rebuildCatalogUI();
                rebuildActiveUI();
            });
            buttonPanel.add(removeBtn);

            row.add(buttonPanel, BorderLayout.EAST);

            return row;
        }

        // ────────────────────────────────────────────────────────────────
        //  Globe integration
        // ────────────────────────────────────────────────────────────────

        private void addToGlobe(WMSLayerEntry entry)
        {
            Object comp = entry.getOrCreateComponent();
            if (comp == null)
                return;

            if (comp instanceof Layer layer)
            {
                layer.setEnabled(true);
                ApplicationTemplate.insertBeforePlacenames(getWwd(), layer);
            }
            else if (comp instanceof ElevationModel model)
            {
                CompoundElevationModel compound =
                    (CompoundElevationModel) getWwd().getModel().getGlobe().getElevationModel();
                if (!compound.getElevationModels().contains(model))
                    compound.addElevationModel(model);
            }

            entry.setAddedToGlobe(true);
            if (!activeEntries.contains(entry))
                activeEntries.add(entry);

            getWwd().redraw();
        }

        private void removeFromGlobe(WMSLayerEntry entry)
        {
            Object comp = entry.getComponent();
            if (comp instanceof Layer layer)
            {
                layer.setEnabled(false);
                getWwd().getModel().getLayers().remove(layer);
            }
            else if (comp instanceof ElevationModel model)
            {
                CompoundElevationModel compound =
                    (CompoundElevationModel) getWwd().getModel().getGlobe().getElevationModel();
                compound.getElevationModels().remove(model);
            }

            entry.setAddedToGlobe(false);
            activeEntries.remove(entry);

            getWwd().redraw();
        }

        private void swapLayers(int indexA, int indexB)
        {
            Collections.swap(activeEntries, indexA, indexB);

            // Also swap in the WorldWind layer list so rendering order matches
            Object compA = activeEntries.get(indexA).getComponent();
            Object compB = activeEntries.get(indexB).getComponent();
            if (compA instanceof Layer layerA && compB instanceof Layer layerB)
            {
                LayerList layers = getWwd().getModel().getLayers();
                int posA = layers.indexOf(layerA);
                int posB = layers.indexOf(layerB);
                if (posA >= 0 && posB >= 0)
                {
                    layers.remove(layerA);
                    layers.remove(layerB);
                    // Re-insert in swapped order: the one that was lower goes to the higher position
                    int lo = Math.min(posA, posB);
                    int hi = Math.max(posA, posB);
                    Layer first = (posA < posB) ? layerB : layerA;
                    Layer second = (posA < posB) ? layerA : layerB;
                    layers.add(lo, first);
                    layers.add(hi, second);
                }
            }

            getWwd().redraw();
        }

        // ────────────────────────────────────────────────────────────────
        //  Utilities
        // ────────────────────────────────────────────────────────────────

        private static String truncate(String s, int max)
        {
            if (s == null)
                return "";
            return s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
        }
    }

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind WMS Layer Manager", AppFrame.class);
    }
}
