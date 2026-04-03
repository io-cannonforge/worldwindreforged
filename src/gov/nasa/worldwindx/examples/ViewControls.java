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
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.ViewControlsLayer;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Shows the {@link gov.nasa.worldwind.layers.ViewControlsLayer} and allows you to adjust its size, orientation, and
 * available controls.
 *
 * @author Patrick Murris
 * @version $Id: ViewControls.java 2109 2014-06-30 16:52:38Z tgaskins $
 * @see gov.nasa.worldwind.layers.ViewControlsLayer
 * @see gov.nasa.worldwind.layers.ViewControlsSelectListener
 * @see gov.nasa.worldwind.layers.CompassLayer
 */
public class ViewControls extends ApplicationTemplate {

    public static class AppFrame extends ApplicationTemplate.AppFrame {
        private static final long serialVersionUID = 1L;

        protected ViewControlsLayer viewControlsLayer;

        public AppFrame() {
            super(true, true, false);

            // Find ViewControls layer and keep reference to it
            for (Layer layer : this.getWwd().getModel().getLayers()) {
                if (layer instanceof ViewControlsLayer) {
                    viewControlsLayer = (ViewControlsLayer) layer;
                }
            }

            JPanel viewControls = makeControlPanel();

            // Modified by seaglassfoundry.com - put the layers panel and controls panel in a
            // tabbed pane so they don't overlap. Each tab gets a scroll pane for small windows.
            // Use a split pane between the map and the side panel so it can be resized.
            if (this.controlPanel != null)
            {
                this.getContentPane().remove(this.controlPanel);
                this.getContentPane().remove(this.wwjPanel);

                JTabbedPane tabs = new JTabbedPane();
                tabs.setBackground(new java.awt.Color(45, 45, 48));

                JScrollPane layerScroll = new JScrollPane(this.layerPanel);
                layerScroll.setBorder(null);
                tabs.addTab("Layers", layerScroll);

                JScrollPane controlScroll = new JScrollPane(viewControls);
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

        private JPanel makeControlPanel() {
            JPanel controlPanel = new JPanel();
            controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
            controlPanel.setBorder(WWStyle.sectionBorder("View Controls"));
            controlPanel.setToolTipText("Select active view controls");

            // Radio buttons - layout
            JPanel layoutPanel = new JPanel(new GridLayout(0, 2, 0, 0));
            layoutPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            ButtonGroup group = new ButtonGroup();
            JRadioButton button = new JRadioButton("Horizontal", true);
            group.add(button);
            button.addActionListener((ActionEvent actionEvent) -> {
                viewControlsLayer.setLayout(AVKey.HORIZONTAL);
                getWwd().redraw();
            });
            layoutPanel.add(button);
            button = new JRadioButton("Vertical", false);
            group.add(button);
            button.addActionListener((ActionEvent actionEvent) -> {
                viewControlsLayer.setLayout(AVKey.VERTICAL);
                getWwd().redraw();
            });
            layoutPanel.add(button);

            // Scale slider
            JPanel scalePanel = new JPanel(new GridLayout(0, 1, 0, 0));
            scalePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            scalePanel.add(new JLabel("Scale:"));
            JSlider scaleSlider = new JSlider(1, 20, 10);
            scaleSlider.addChangeListener((ChangeEvent event) -> {
                viewControlsLayer.setScale(((JSlider) event.getSource()).getValue() / 10d);
                getWwd().redraw();
            });
            scalePanel.add(scaleSlider);

            // Check boxes
            JPanel checkPanel = new JPanel(new GridLayout(0, 2, 0, 0));
            checkPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            JCheckBox check = new JCheckBox("Pan");
            check.setSelected(true);
            check.addActionListener((ActionEvent actionEvent) -> {
                viewControlsLayer.setShowPanControls(((JCheckBox) actionEvent.getSource()).isSelected());
                getWwd().redraw();
            });
            checkPanel.add(check);

            check = new JCheckBox("Look");
            check.setSelected(false);
            check.addActionListener((ActionEvent actionEvent) -> {
                viewControlsLayer.setShowLookControls(((JCheckBox) actionEvent.getSource()).isSelected());
                getWwd().redraw();
            });
            checkPanel.add(check);

            check = new JCheckBox("Zoom");
            check.setSelected(true);
            check.addActionListener((ActionEvent actionEvent) -> {
                viewControlsLayer.setShowZoomControls(((JCheckBox) actionEvent.getSource()).isSelected());
                getWwd().redraw();
            });
            checkPanel.add(check);

            check = new JCheckBox("Heading");
            check.setSelected(true);
            check.addActionListener((ActionEvent actionEvent) -> {
                viewControlsLayer.setShowHeadingControls(((JCheckBox) actionEvent.getSource()).isSelected());
                getWwd().redraw();
            });
            checkPanel.add(check);

            check = new JCheckBox("Pitch");
            check.setSelected(true);
            check.addActionListener((ActionEvent actionEvent) -> {
                viewControlsLayer.setShowPitchControls(((JCheckBox) actionEvent.getSource()).isSelected());
                getWwd().redraw();
            });
            checkPanel.add(check);

            check = new JCheckBox("Field of view");
            check.setSelected(false);
            check.addActionListener((ActionEvent actionEvent) -> {
                viewControlsLayer.setShowFovControls(((JCheckBox) actionEvent.getSource()).isSelected());
                getWwd().redraw();
            });
            checkPanel.add(check);

            controlPanel.add(layoutPanel);
            controlPanel.add(scalePanel);
            controlPanel.add(checkPanel);
            return controlPanel;
        }
    }

    public static void main(String[] args) {
        ApplicationTemplate.start("WorldWind View Controls", AppFrame.class);
    }
}
