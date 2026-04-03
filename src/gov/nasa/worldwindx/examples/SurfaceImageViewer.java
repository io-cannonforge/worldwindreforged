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
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import gov.nasa.worldwind.layers.SurfaceImageLayer;
import gov.nasa.worldwind.terrain.CompoundElevationModel;
import gov.nasa.worldwind.terrain.LocalElevationModel;

/**
 * Open and view arbitrary surface images and elevations that have an accompanying world file.
 * <p>
 * After clicking the open button and selecting the desired image or elevations file, the program status will change to
 * Loading while WorldWind installs the selected data.  Wait until the status changes to Ready. The data will have
 * finished installing and will be ready for viewing.
 * <p>
 * Image and elevation files that you wish to load must be accompanied by a world file, or they will fail to load. The
 * world file can be identified as the file with a file extension consisting of three letters.  The first two of these
 * will be the first and last letters of the image or elevation file type, e.g. tf for a tiff file, or jg for a jpeg
 * file.  The last letter will be a double.
 * <p>
 * For example, a world file accompanying a jpeg file would have the extension .jgw :
 * <p>
 * image.jpg           // image file
 * image.jgw           // accompanying world file
 *
 * @author tag
 * @version $Id: SurfaceImageViewer.java 2109 2014-06-30 16:52:38Z tgaskins $
 */
public class SurfaceImageViewer extends ApplicationTemplate
{
    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;
        private JFileChooser fileChooser = new JFileChooser();
        private JSlider opacitySlider;
        private SurfaceImageLayer layer;
        private JLabel statusLabel = new JLabel("status: ready");

        public AppFrame()
        {
            super(true, true, false);

            try
            {
                this.layer = new SurfaceImageLayer();
                this.layer.setOpacity(1);
                this.layer.setPickEnabled(false);
                this.layer.setName("Surface Images");

                insertBeforeCompass(this.getWwd(), layer);

                JPanel customPanel = makeControlPanel();

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

                    JScrollPane controlScroll = new JScrollPane(customPanel);
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
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        Action openElevationsAction = new AbstractAction("Open Elevation File...")
        {
            private static final long serialVersionUID = 1L;
            @Override
			public void actionPerformed(ActionEvent e)
            {
                int status = fileChooser.showOpenDialog(AppFrame.this);
                if (status != JFileChooser.APPROVE_OPTION)
                    return;

                final File imageFile = fileChooser.getSelectedFile();
                if (imageFile == null)
                    return;

                Thread t = new Thread(new Runnable()
                {
                    @Override
					public void run()
                    {
                        try
                        {
                            CompoundElevationModel cem
                                = (CompoundElevationModel) getWwd().getModel().getGlobe().getElevationModel();
                            LocalElevationModel em = new LocalElevationModel();
                            em.addElevations(imageFile.getPath());
                            cem.addElevationModel(em);
                        }
                        catch (IOException e1)
                        {
                            e1.printStackTrace();
                        }
                    }
                });
                t.setPriority(Thread.MIN_PRIORITY);
                t.start();
            }
        };

        Action openImageAction = new AbstractAction("Open Image File...")
        {
            private static final long serialVersionUID = 1L;
            @Override
			public void actionPerformed(ActionEvent actionEvent)
            {
                int status = fileChooser.showOpenDialog(AppFrame.this);
                if (status != JFileChooser.APPROVE_OPTION)
                    return;

                final File imageFile = fileChooser.getSelectedFile();
                if (imageFile == null)
                    return;

                Thread t = new Thread(new Runnable()
                {
                    @Override
					public void run()
                    {
                        try
                        {
                            statusLabel.setText("status: Loading image");
                            // TODO: proper threading
                            layer.addImage(imageFile.getAbsolutePath());

                            getWwd().redraw();
                            statusLabel.setText("status: ready");
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                });
                t.setPriority(Thread.MIN_PRIORITY);
                t.start();
            }
        };

        private JPanel makeControlPanel()
        {
            JPanel controlPanel = new JPanel(new GridLayout(0, 1, 5, 5));
            JButton openImageButton = new JButton(openImageAction);
            controlPanel.add(openImageButton);

            this.opacitySlider = new JSlider();
            this.opacitySlider.setMaximum(100);
            this.opacitySlider.setValue((int) (layer.getOpacity() * 100));
            this.opacitySlider.setEnabled(true);
            this.opacitySlider.addChangeListener(new ChangeListener()
            {
                @Override
				public void stateChanged(ChangeEvent e)
                {
                    int value = opacitySlider.getValue();
                    layer.setOpacity(value / 100d);
                    getWwd().redraw();
                }
            });
            JPanel opacityPanel = new JPanel(new BorderLayout(5, 5));
            opacityPanel.setBorder(new EmptyBorder(0, 10, 0, 0));
            opacityPanel.add(new JLabel("Opacity"), BorderLayout.WEST);
            opacityPanel.add(this.opacitySlider, BorderLayout.CENTER);

            controlPanel.add(opacityPanel);

            JButton openElevationsButton = new JButton(openElevationsAction);
            controlPanel.add(openElevationsButton);

            controlPanel.add(statusLabel);
            controlPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

            return controlPanel;
        }
    }

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind Surface Images", SurfaceImageViewer.AppFrame.class);
    }
}
