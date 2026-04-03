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
import java.awt.Color;
import java.awt.Rectangle;

import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;

import gov.nasa.worldwind.layers.Earth.MGRSGraticuleLayer;
import gov.nasa.worldwind.util.StatusBar;
import gov.nasa.worldwind.util.StatusBarMGRS;

/**
 * Displays the globe with a MGRS/UTM graticule. The graticule is its own layer and can be turned on and off independent
 * of other layers. As the view zooms in, the graticule adjusts to display a finer grid. The example provides controls
 * to customize the color and opacity of the grid.
 *
 * @author Patrick Murris
 * @version $Id: MGRSGraticule.java 2109 2014-06-30 16:52:38Z tgaskins $
 */
public class MGRSGraticule extends ApplicationTemplate
{

    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;
        public AppFrame()
        {
            super(true, true, false);

            MGRSGraticuleLayer layer = new MGRSGraticuleLayer();

            // Add MGRS/UTM Graticule layer
            insertBeforePlacenames(this.getWwd(), layer);

            // Replace status bar with MGRS version
            this.getStatusBar().setEventSource(null);
            this.getWwjPanel().remove(this.getStatusBar());
            StatusBar sb = new StatusBarMGRS();
            sb.setEventSource(this.getWwd());
            this.getWwjPanel().add(sb, BorderLayout.SOUTH);

            // Add go to coordinate input panel
            GoToCoordinatePanel coordPanel = new GoToCoordinatePanel(this.getWwd());

            // Add MGRS graticule properties frame
            JDialog dialog = MGRSAttributesPanel.showDialog(this, "MGRS Graticule Properties", layer);
            Rectangle bounds = this.getBounds();
            dialog.setLocation(bounds.x + bounds.width, bounds.y);

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

                JScrollPane controlScroll = new JScrollPane(coordPanel);
                controlScroll.setBorder(null);
                tabs.addTab("Coordinates", controlScroll);

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
    }


    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind UTM/MGRS Graticule", AppFrame.class);
    }
}