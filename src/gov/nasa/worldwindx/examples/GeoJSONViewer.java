/*
 * WorldWind Reforged — GeoJSONViewer
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New example — interactive GeoJSON file viewer with feature attribute inspection.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.formats.geojson.GeoJSONGeometry;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.PointPlacemarkAttributes;
import gov.nasa.worldwind.render.Renderable;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Interactive GeoJSON file viewer.
 * <p>
 * <b>Usage:</b>
 * <ol>
 *   <li>Click <b>Open GeoJSON…</b> and select a {@code .geojson} or {@code .json} file.</li>
 *   <li>The features are added to a {@link RenderableLayer} and the view flies to the data extent.</li>
 *   <li>Click any feature on the globe to see its GeoJSON {@code properties} in the attribute table.</li>
 * </ol>
 * Loading runs in a background thread so the globe stays responsive during parsing.
 * Each call to Open replaces the previous data set.
 *
 * @see GeoJSONLoader
 *
 * seaglassfoundry.com — new example for WorldWind Reforged
 */
public class GeoJSONViewer extends ApplicationTemplate
{
    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;
        private RenderableLayer dataLayer;
        private JLabel          statusLabel;
        private DefaultTableModel tableModel;
        private JLabel          noSelectionHint;
        private JScrollPane     tableScroll;

        public AppFrame()
        {
            super(true, true, false);
            dataLayer = new RenderableLayer();
            dataLayer.setName("GeoJSON");
            insertBeforePlacenames(getWwd(), dataLayer);

            getWwd().addSelectListener(this::onPick);
            JPanel geojsonControlPanel = buildControlPanel();

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

                JScrollPane controlScroll = new JScrollPane(geojsonControlPanel);
                controlScroll.setBorder(null);
                tabs.addTab("GeoJSON", controlScroll);

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

        // ── File loading ──────────────────────────────────────────────────────

        private void openFile()
        {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("GeoJSON files (*.geojson, *.json)",
                "geojson", "json"));
            fc.setDialogTitle("Open GeoJSON");
            if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
                return;

            File file = fc.getSelectedFile();
            setStatus("Loading " + file.getName() + "…");
            clearAttributeTable();

            new SwingWorker<LoadResult, Void>()
            {
                @Override
                protected LoadResult doInBackground()
                {
                    TrackingLoader loader = new TrackingLoader();
                    RenderableLayer layer = new RenderableLayer();
                    loader.addSourceGeometryToLayer(file, layer);
                    return new LoadResult(layer, loader);
                }

                @Override
                protected void done()
                {
                    try {
                        LoadResult result = get();
                        dataLayer.removeAllRenderables();
                        for (Renderable r : result.layer.getRenderables())
                            dataLayer.addRenderable(r);
                        getWwd().redraw();

                        int n = result.loader.featureCount;
                        setStatus(file.getName() + " — " + n + " feature" + (n != 1 ? "s" : ""));

                        if (result.loader.hasData())
                            flyToSector(result.loader.getBoundingSector());
                    } catch (Exception ex) {
                        setStatus("Error: " + ex.getMessage());
                    }
                }
            }.execute();
        }

        // ── Picking ───────────────────────────────────────────────────────────

        private void onPick(SelectEvent event)
        {
            if (!SelectEvent.LEFT_CLICK.equals(event.getEventAction()))
                return;

            Object top = event.getTopObject();
            if (!(top instanceof AVList avl))
                return;

            Object props = avl.getValue(AVKey.PROPERTIES);
            if (props instanceof AVList propList)
                showAttributes(propList);
        }

        // ── Fly to extent ─────────────────────────────────────────────────────

        private void flyToSector(Sector sector)
        {
            LatLon center = sector.getCentroid();
            // Altitude: enough to see the full sector; roughly scale with its larger dimension
            double spanDeg = Math.max(sector.getDeltaLatDegrees(), sector.getDeltaLonDegrees());
            double altMetres = Math.max(spanDeg * 111_000 * 1.5, 50_000);  // at least 50 km
            getWwd().getView().setEyePosition(
                Position.fromDegrees(center.getLatitude().degrees,
                                     center.getLongitude().degrees,
                                     altMetres));
        }

        // ── Attribute table ───────────────────────────────────────────────────

        private void showAttributes(AVList props)
        {
            tableModel.setRowCount(0);
            Set<Map.Entry<String, Object>> entries = props.getEntries();
            if (entries != null) {
                // Sort by key for readability
                entries.stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> tableModel.addRow(new Object[]{e.getKey(), e.getValue()}));
            }
            noSelectionHint.setVisible(false);
            tableScroll.setVisible(true);
        }

        private void clearAttributeTable()
        {
            tableModel.setRowCount(0);
            noSelectionHint.setVisible(true);
            tableScroll.setVisible(false);
        }

        // ── Control panel ─────────────────────────────────────────────────────

        private JPanel buildControlPanel()
        {
            JPanel root = new JPanel();
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            root.setBackground(WWStyle.BG_DARK);

            // ── File section ──────────────────────────────────────────────────
            JPanel fileSection = new JPanel();
            fileSection.setLayout(new BoxLayout(fileSection, BoxLayout.Y_AXIS));
            fileSection.setBackground(WWStyle.BG_DARK);
            fileSection.setBorder(WWStyle.sectionBorder("GeoJSON File"));

            JButton openBtn = WWStyle.accentButton("Open GeoJSON…");
            openBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            openBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            openBtn.addActionListener(e -> openFile());

            statusLabel = WWStyle.label("No file loaded.", false);
            statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            fileSection.add(openBtn);
            fileSection.add(vgap(WWStyle.GAP_XS));
            fileSection.add(statusLabel);
            root.add(fileSection);
            root.add(vgap(WWStyle.GAP_XS));

            // ── Attribute table section ───────────────────────────────────────
            JPanel attrSection = new JPanel(new BorderLayout());
            attrSection.setBackground(WWStyle.BG_DARK);
            attrSection.setBorder(WWStyle.sectionBorder("Feature Properties"));

            noSelectionHint = WWStyle.label("Click a feature to see its properties.", false);
            noSelectionHint.setAlignmentX(Component.LEFT_ALIGNMENT);
            noSelectionHint.setBorder(BorderFactory.createEmptyBorder(
                WWStyle.GAP_XS, 0, WWStyle.GAP_XS, 0));
            attrSection.add(noSelectionHint, BorderLayout.NORTH);

            tableModel = new DefaultTableModel(new String[]{"Property", "Value"}, 0)
            {
                private static final long serialVersionUID = 1L;
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };

            JTable table = new JTable(tableModel);
            table.setFont(WWStyle.FONT_SMALL);
            table.setForeground(WWStyle.FG_PRIMARY);
            table.setBackground(WWStyle.BG_PANEL);
            table.setSelectionBackground(WWStyle.BG_SELECTED);
            table.setSelectionForeground(WWStyle.FG_PRIMARY);
            table.setGridColor(WWStyle.BORDER);
            table.setRowHeight(20);
            table.getColumnModel().getColumn(0).setPreferredWidth(80);
            table.getColumnModel().getColumn(1).setPreferredWidth(120);
            table.getTableHeader().setFont(WWStyle.FONT_SMALL);
            table.getTableHeader().setBackground(WWStyle.BG_DARK);
            table.getTableHeader().setForeground(WWStyle.FG_SECONDARY);

            tableScroll = WWStyle.scrollPane(table);
            tableScroll.setPreferredSize(new Dimension(0, 180));
            tableScroll.setVisible(false);
            attrSection.add(tableScroll, BorderLayout.CENTER);

            root.add(attrSection);
            return root;
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private void setStatus(String msg)
        {
            SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
        }

        private static JPanel vgap(int height)
        {
            JPanel p = new JPanel();
            p.setBackground(WWStyle.BG_DARK);
            p.setPreferredSize(new Dimension(0, height));
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            return p;
        }

        // ── TrackingLoader ────────────────────────────────────────────────────

        /**
         * Extends {@link GeoJSONLoader} to track all positions seen during parsing so
         * that a bounding sector can be computed for the auto-fly-to.
         */
        private static class TrackingLoader extends GeoJSONLoader
        {
            int featureCount = 0;
            private double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
            private double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;

            @Override
            protected Renderable createPoint(GeoJSONGeometry owner, Position pos,
                PointPlacemarkAttributes attrs, AVList properties)
            {
                track(pos);
                featureCount++;
                return super.createPoint(owner, pos, attrs, properties);
            }

            @Override
            protected Renderable createPolyline(GeoJSONGeometry owner,
                Iterable<? extends Position> positions, ShapeAttributes attrs, AVList properties)
            {
                positions.forEach(this::track);
                featureCount++;
                return super.createPolyline(owner, positions, attrs, properties);
            }

            @Override
            protected Renderable createPolygon(GeoJSONGeometry owner,
                Iterable<? extends Position> outerBoundary,
                Iterable<? extends Position>[] innerBoundaries,
                ShapeAttributes attrs, AVList properties)
            {
                outerBoundary.forEach(this::track);
                featureCount++;
                return super.createPolygon(owner, outerBoundary, innerBoundaries, attrs, properties);
            }

            private void track(LatLon ll)
            {
                double lat = ll.getLatitude().degrees;
                double lon = ll.getLongitude().degrees;
                if (lat < minLat) minLat = lat;
                if (lat > maxLat) maxLat = lat;
                if (lon < minLon) minLon = lon;
                if (lon > maxLon) maxLon = lon;
            }

            boolean hasData() { return minLat != Double.MAX_VALUE; }

            Sector getBoundingSector()
            {
                return Sector.fromDegrees(minLat, maxLat, minLon, maxLon);
            }
        }

        /** Carries the result of the background load back to the EDT. */
        private record LoadResult(RenderableLayer layer, TrackingLoader loader) {}
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind — GeoJSON Viewer", AppFrame.class);
    }
}
