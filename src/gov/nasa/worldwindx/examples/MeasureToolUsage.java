/*
 * Copyright 2006-2009, 2017, 2020 United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The NASA World Wind Java (WWJ) platform is licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * NASA World Wind Java (WWJ) also contains the following 3rd party Open Source
 * software:
 *
 *     Jackson Parser – Licensed under Apache 2.0
 *     GDAL – Licensed under MIT
 *     JOGL – Licensed under  Berkeley Software Distribution (BSD)
 *     Gluegen – Licensed under Berkeley Software Distribution (BSD)
 *
 * A complete listing of 3rd Party software notices and licenses included in
 * NASA World Wind Java (WWJ)  can be found in the WorldWindJava-v2.2 3rd-party
 * notices and licenses PDF found in code directory.
 *
 * Modifications and additions by seaglassfoundry.com — WorldWind Reforged project.
 * MeasureToolUsage.java: enhanced with closeable tabs, terrain-profile integration,
 * and the new WWStyle-based MeasureToolPanel.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.layers.TerrainProfileLayer;
import gov.nasa.worldwind.util.measure.MeasureTool;
import gov.nasa.worldwind.util.measure.MeasureToolController;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Interactive distance, area, and heading measurement tool with terrain-profile
 * overlay. Click the "+" tab to create a new measurement; each tab is
 * independently configurable and closeable.
 * <p>
 * Demonstrates:
 * <ul>
 *   <li>Multiple concurrent {@link MeasureTool} instances via tabbed UI</li>
 *   <li>{@link TerrainProfileLayer} synchronised to the active path</li>
 *   <li>Per-segment distance, cumulative distance, bearing, and elevation
 *       readout in the enhanced {@link MeasureToolPanel}</li>
 *   <li>Undo-last-point and copy-to-clipboard actions</li>
 * </ul>
 *
 * @see MeasureTool
 * @see MeasureToolController
 * @see MeasureToolPanel
 *
 * seaglassfoundry.com — rewritten for WorldWind Reforged
 */
public class MeasureToolUsage extends ApplicationTemplate {

    public static class AppFrame extends ApplicationTemplate.AppFrame {
        private static final long serialVersionUID = 1L;

        private int lastTabIndex = -1;
        private int tabCounter = 0;
        private final JTabbedPane tabbedPane = new JTabbedPane();
        private final TerrainProfileLayer profile = new TerrainProfileLayer();
        private final PropertyChangeListener measureToolListener = new MeasureToolListener();

        @SuppressWarnings("deprecation")
        public AppFrame() {
            super(true, true, false);

            // Terrain profile layer
            profile.setEventSource(getWwd());
            profile.setFollow(TerrainProfileLayer.FOLLOW_PATH);
            profile.setShowProfileLine(false);
            insertBeforePlacenames(getWwd(), profile);

            // Style the tabbed pane
            tabbedPane.setBackground(WWStyle.BG_DARK);
            tabbedPane.setForeground(WWStyle.FG_PRIMARY);

            // "+" tab for adding new measurements
            tabbedPane.add(new JPanel());
            tabbedPane.setTitleAt(0, "+");
            tabbedPane.addChangeListener(e -> {
                if (tabbedPane.getSelectedIndex() == 0) {
                    addMeasureTab();
                } else {
                    switchMeasureTool();
                }
            });

            // First measurement tab
            addMeasureTab();

            // Outer tabbed pane: Layers vs Measure Tool
            JTabbedPane outerTabs = new JTabbedPane();
            outerTabs.setBackground(WWStyle.BG_DARK);
            outerTabs.setForeground(WWStyle.FG_PRIMARY);

            this.getControlPanel().remove(this.getLayerPanel());
            outerTabs.addTab("Layers", this.getLayerPanel());
            outerTabs.addTab("Measure Tool", tabbedPane);
            outerTabs.setSelectedIndex(1); // Default to Measure Tool

            this.getControlPanel().add(outerTabs, BorderLayout.CENTER);
            this.pack();
        }

        private void addMeasureTab() {
            tabCounter++;
            MeasureTool mt = new MeasureTool(getWwd());
            mt.setController(new MeasureToolController());
            MeasureToolPanel panel = new MeasureToolPanel(getWwd(), mt);

            int index = tabbedPane.getTabCount();
            tabbedPane.add(panel);
            tabbedPane.setTitleAt(index, "M" + tabCounter);

            // Closeable tab header
            tabbedPane.setTabComponentAt(index, createTabHeader("M" + tabCounter, index));

            tabbedPane.setSelectedIndex(index);
            switchMeasureTool();
        }

        /**
         * Creates a tab header with a title label and a small close button.
         */
        private JPanel createTabHeader(String title, int tabIndex) {
            JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            header.setOpaque(false);

            JLabel label = new JLabel(title);
            label.setFont(WWStyle.FONT_SMALL);
            label.setForeground(WWStyle.FG_PRIMARY);
            header.add(label);

            JButton closeBtn = new CloseTabButton();
            closeBtn.addActionListener(e -> {
                int idx = tabbedPane.indexOfTabComponent(header);
                if (idx > 0) {
                    // Dispose the measure tool before removing
                    MeasureToolPanel panel = (MeasureToolPanel) tabbedPane.getComponentAt(idx);
                    panel.getMeasureTool().setArmed(false);
                    panel.getMeasureTool().dispose();

                    if (lastTabIndex == idx) lastTabIndex = -1;
                    tabbedPane.removeTabAt(idx);

                    // Select another tab if needed
                    if (tabbedPane.getTabCount() > 1) {
                        tabbedPane.setSelectedIndex(Math.min(idx, tabbedPane.getTabCount() - 1));
                        if (tabbedPane.getSelectedIndex() == 0 && tabbedPane.getTabCount() > 1) {
                            tabbedPane.setSelectedIndex(1);
                        }
                    }
                }
            });
            header.add(closeBtn);

            return header;
        }

        private class MeasureToolListener implements PropertyChangeListener {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                if (event.getPropertyName().equals(MeasureTool.EVENT_POSITION_ADD)
                        || event.getPropertyName().equals(MeasureTool.EVENT_POSITION_REMOVE)
                        || event.getPropertyName().equals(MeasureTool.EVENT_POSITION_REPLACE)) {
                    updateProfile(((MeasureTool) event.getSource()));
                }
            }
        }

        private void switchMeasureTool() {
            // Disarm previous tool
            if (lastTabIndex > 0 && lastTabIndex < tabbedPane.getTabCount()) {
                Component comp = tabbedPane.getComponentAt(lastTabIndex);
                if (comp instanceof MeasureToolPanel) {
                    MeasureTool mt = ((MeasureToolPanel) comp).getMeasureTool();
                    mt.setArmed(false);
                    mt.removePropertyChangeListener(measureToolListener);
                }
            }
            // Arm current tool
            int idx = tabbedPane.getSelectedIndex();
            if (idx > 0 && idx < tabbedPane.getTabCount()) {
                lastTabIndex = idx;
                Component comp = tabbedPane.getComponentAt(idx);
                if (comp instanceof MeasureToolPanel) {
                    MeasureTool mt = ((MeasureToolPanel) comp).getMeasureTool();
                    mt.addPropertyChangeListener(measureToolListener);
                    updateProfile(mt);
                }
            }
        }

        private void updateProfile(MeasureTool mt) {
            ArrayList<? extends LatLon> positions = mt.getPositions();
            if (positions != null && positions.size() > 1) {
                profile.setPathPositions(positions);
                profile.setEnabled(true);
            } else {
                profile.setEnabled(false);
            }
            getWwd().redraw();
        }

        /**
         * Small "x" close button rendered inline in the tab header.
         */
        private static class CloseTabButton extends JButton {
            private static final long serialVersionUID = 1L;
            private static final int SIZE = 16;

            CloseTabButton() {
                setPreferredSize(new Dimension(SIZE, SIZE));
                setContentAreaFilled(false);
                setFocusPainted(false);
                setBorderPainted(false);
                setBorder(BorderFactory.createEmptyBorder());
                setRolloverEnabled(true);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("Close this measurement");
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { repaint(); }
                    @Override public void mouseExited(MouseEvent e) { repaint(); }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(getModel().isRollover()
                    ? WWStyle.STATUS_ERROR : WWStyle.FG_SECONDARY);
                int pad = 4;
                g2.drawLine(pad, pad, getWidth() - pad - 1, getHeight() - pad - 1);
                g2.drawLine(getWidth() - pad - 1, pad, pad, getHeight() - pad - 1);
                g2.dispose();
            }
        }
    }

    public static void main(String[] args) {
        ApplicationTemplate.start("WorldWind Measure Tool", MeasureToolUsage.AppFrame.class);
    }
}
