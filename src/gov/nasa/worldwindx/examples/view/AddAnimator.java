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
package gov.nasa.worldwindx.examples.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.view.firstperson.BasicFlyView;
import gov.nasa.worldwindx.examples.ApplicationTemplate;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * This example demonstrates the use of {@link gov.nasa.worldwind.view.firstperson.BasicFlyView} with a {@link
 * gov.nasa.worldwind.view.firstperson.FlyToFlyViewAnimator}, allowing the user to fly smoothly to new locations by
 * simply specifying a location's name or lat-long coordinates.
 *
 * @author jym
 * @version $Id: AddAnimator.java 2109 2014-06-30 16:52:38Z tgaskins $
 */
public class AddAnimator extends ApplicationTemplate
{
    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;
        public BasicFlyView view;

        @SuppressWarnings("serial")
        public class ViewDisplay extends JPanel
        {
            public ViewDisplay()
            {
                super(new GridLayout(0, 1));
            }
        }

        ViewDisplay viewDisplay;

        public AppFrame()
        {
            super(true, true, true);
            JPanel viewControlPanel = makeControlPanel();

            WorldWindow wwd = this.getWwd();

            // Force the view to be a FlyView
            view = new BasicFlyView();
            wwd.setView(view);

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

                JScrollPane controlScroll = new JScrollPane(viewControlPanel);
                controlScroll.setBorder(null);
                tabs.addTab("View Controls", controlScroll);

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

        private JPanel makeControlPanel()
        {
            JPanel controlPanel = new JPanel();
            controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
            controlPanel.setBorder(WWStyle.sectionBorder("View Controls"));
            controlPanel.setToolTipText("Proved a location");
            viewDisplay = new ViewDisplay();
            controlPanel.add(viewDisplay);

            return (controlPanel);
        }
    }

    public static void main(String[] args)
    {
        // Call the static start method like this from the main method of your derived class.
        // Substitute your application's name for the first argument.
        ApplicationTemplate.start("WorldWind View Animator", AppFrame.class);
    }
}
