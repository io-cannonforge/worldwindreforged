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
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.Model;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.exception.WWAbsentRequirementException;
import gov.nasa.worldwind.layers.CompassLayer;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwind.layers.ViewControlsLayer;
import gov.nasa.worldwind.layers.ViewControlsSelectListener;
import gov.nasa.worldwind.layers.WorldMapLayer;
import gov.nasa.worldwind.layers.placename.PlaceNameLayer;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.StatisticsPanel;
import gov.nasa.worldwind.util.StatusBar;
import gov.nasa.worldwind.util.WWUtil;
import gov.nasa.worldwindx.examples.util.HighlightController;
import gov.nasa.worldwindx.examples.util.ToolTipController;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Provides a base application framework for simple WorldWind examples. Examine other examples in this package to see
 * how it's used.
 *
 * @version $Id: ApplicationTemplate.java 2115 2014-07-01 17:58:16Z tgaskins $
 */
public class ApplicationTemplate {

    public static class AppPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        protected WorldWindow wwd;
        protected StatusBar statusBar;
        protected ToolTipController toolTipController;
        protected HighlightController highlightController;

        public AppPanel(Dimension canvasSize, boolean includeStatusBar) {
            super(new BorderLayout());
            this.setBackground(WWStyle.BG_BASE);

            this.wwd = this.createWorldWindow();
            ((Component) this.wwd).setPreferredSize(canvasSize);

            // Create the default model as described in the current worldwind properties.
            Model m = (Model) WorldWind.createConfigurationComponent(AVKey.MODEL_CLASS_NAME);
            this.wwd.setModel(m);

            // Setup a select listener for the worldmap click-and-go feature
            this.wwd.addSelectListener(new ClickAndGoSelectListener(this.getWwd(), WorldMapLayer.class));

            this.add((Component) this.wwd, BorderLayout.CENTER);
            if (includeStatusBar) {
                this.statusBar = new StatusBar();
                this.add(statusBar, BorderLayout.PAGE_END);
                this.statusBar.setEventSource(wwd);
            }

            // Add controllers to manage highlighting and tool tips.
            this.toolTipController = new ToolTipController(this.getWwd(), AVKey.DISPLAY_NAME, null);
            this.highlightController = new HighlightController(this.getWwd(), SelectEvent.ROLLOVER);
        }

        protected WorldWindow createWorldWindow() {
            return new WorldWindowGLCanvas();
        }

        public WorldWindow getWwd() {
            return wwd;
        }

        public StatusBar getStatusBar() {
            return statusBar;
        }
    }

    protected static class AppFrame extends JFrame {
        private static final long serialVersionUID = 1L;

        private Dimension canvasSize = new Dimension(900, 600);

        protected AppPanel wwjPanel;
        protected JPanel controlPanel;
        protected LayerPanel layerPanel;
        protected StatisticsPanel statsPanel;

        public AppFrame() {
            this.initialize(true, true, false);
        }

        public AppFrame(Dimension size) {
            this.canvasSize = size;
            this.initialize(true, true, false);
        }

        public AppFrame(boolean includeStatusBar, boolean includeLayerPanel, boolean includeStatsPanel) {
            this.initialize(includeStatusBar, includeLayerPanel, includeStatsPanel);
        }

        protected void initialize(boolean includeStatusBar, boolean includeLayerPanel, boolean includeStatsPanel) {
            // Clamp canvas to available screen space so examples fit on small/laptop displays.
            Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            int maxW = screen.width  - WWStyle.SIDEBAR_WIDTH - 40;  // sidebar + window chrome
            int maxH = screen.height - 120;                           // title bar + taskbar
            canvasSize = new Dimension(Math.min(canvasSize.width, maxW), Math.min(canvasSize.height, maxH));

            // Apply FlatLaf dark theme and colour overrides before any component is created.
            try {
                com.formdev.flatlaf.FlatDarkLaf.setup();
            } catch (Exception ignored) {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e2) {
                    Logging.logger().warning("Could not set look and feel: " + e2.getMessage());
                }
            }
            WWStyle.applyUIManagerOverrides();

            // Create the WorldWindow.
            this.wwjPanel = this.createAppPanel(this.canvasSize, includeStatusBar);
            this.wwjPanel.setPreferredSize(canvasSize);

            // Put the pieces together.
            this.getContentPane().setBackground(WWStyle.BG_BASE);
            this.getContentPane().add(wwjPanel, BorderLayout.CENTER);
            if (includeLayerPanel) {
                this.controlPanel = new JPanel(new BorderLayout(0, 0));
                this.controlPanel.setBackground(WWStyle.BG_DARK);
                this.controlPanel.setPreferredSize(new Dimension(WWStyle.SIDEBAR_WIDTH, canvasSize.height));
                this.layerPanel = new LayerPanel(this.getWwd());
                this.controlPanel.add(this.layerPanel, BorderLayout.CENTER);
                this.getContentPane().add(this.controlPanel, BorderLayout.EAST);
            }

            if (includeStatsPanel || System.getProperty("gov.nasa.worldwind.showStatistics") != null) {
                this.statsPanel = new StatisticsPanel(this.wwjPanel.getWwd(), new Dimension(250, canvasSize.height));
                this.getContentPane().add(this.statsPanel, BorderLayout.WEST);
            }

            // Create and install the view controls layer and register a controller for it with the WorldWindow.
            ViewControlsLayer viewControlsLayer = new ViewControlsLayer();
            insertBeforeCompass(getWwd(), viewControlsLayer);
            this.getWwd().addSelectListener(new ViewControlsSelectListener(this.getWwd(), viewControlsLayer));

            // Register a rendering exception listener that's notified when exceptions occur during rendering.
            this.wwjPanel.getWwd().addRenderingExceptionListener((Throwable t) -> {
                if (t instanceof WWAbsentRequirementException) {
                    String message = "Computer does not meet minimum graphics requirements.\n";
                    message += "Please install up-to-date graphics driver and try again.\n";
                    message += "Reason: " + t.getMessage() + "\n";
                    message += "This program will end when you press OK.";

                    JOptionPane.showMessageDialog(AppFrame.this, message, "Unable to Start Program",
                            JOptionPane.ERROR_MESSAGE);
                    System.exit(-1);
                }
            });

            // Search the layer list for layers that are also select listeners and register them with the World
            // Window. This enables interactive layers to be included without specific knowledge of them here.
            for (Layer layer : this.wwjPanel.getWwd().getModel().getLayers()) {
                if (layer instanceof SelectListener) {
                    this.getWwd().addSelectListener((SelectListener) layer);
                }
            }

            this.pack();

            // Center the application on the screen.
            WWUtil.alignComponent(null, this, AVKey.CENTER);
            this.setResizable(true);
        }

        protected AppPanel createAppPanel(Dimension canvasSize, boolean includeStatusBar) {
            return new AppPanel(canvasSize, includeStatusBar);
        }

        public Dimension getCanvasSize() {
            return canvasSize;
        }

        public AppPanel getWwjPanel() {
            return wwjPanel;
        }

        public WorldWindow getWwd() {
            return this.wwjPanel.getWwd();
        }

        public StatusBar getStatusBar() {
            return this.wwjPanel.getStatusBar();
        }

        /**
         * @deprecated Use getControlPanel instead.
         * @return This application's layer panel.
         */
        @Deprecated
        public LayerPanel getLayerPanel() {
            return this.layerPanel;
        }

        public JPanel getControlPanel() {
            return this.controlPanel;
        }

        public StatisticsPanel getStatsPanel() {
            return statsPanel;
        }

        public void setToolTipController(ToolTipController controller) {
            if (this.wwjPanel.toolTipController != null) {
                this.wwjPanel.toolTipController.dispose();
            }

            this.wwjPanel.toolTipController = controller;
        }

        public void setHighlightController(HighlightController controller) {
            if (this.wwjPanel.highlightController != null) {
                this.wwjPanel.highlightController.dispose();
            }

            this.wwjPanel.highlightController = controller;
        }
    }

    /**
     * Inserts a layer into the WorldWindow's layer list just before the {@link CompassLayer}.
     * Use this for overlay layers (WMS imagery, analytic surfaces, renderable layers) that should
     * appear beneath the compass and HUD decorations.
     *
     * <pre>
     *     ApplicationTemplate.insertBeforeCompass(getWwd(), myLayer);
     * </pre>
     *
     * @param wwd   the WorldWindow whose layer list will be modified
     * @param layer the layer to insert
     */
    public static void insertBeforeCompass(WorldWindow wwd, Layer layer) {
        int compassPosition = 0;
        LayerList layers = wwd.getModel().getLayers();
        for (Layer l : layers) {
            if (l instanceof CompassLayer) {
                compassPosition = layers.indexOf(l);
            }
        }
        layers.add(compassPosition, layer);
    }

    /**
     * Inserts a layer into the WorldWindow's layer list just before the {@link PlaceNameLayer}.
     * Use this for imagery and shape layers that should render beneath place-name labels so that
     * labels remain readable on top.
     *
     * <pre>
     *     ApplicationTemplate.insertBeforePlacenames(getWwd(), myImageLayer);
     * </pre>
     *
     * @param wwd   the WorldWindow whose layer list will be modified
     * @param layer the layer to insert
     */
    public static void insertBeforePlacenames(WorldWindow wwd, Layer layer) {
        int compassPosition = 0;
        LayerList layers = wwd.getModel().getLayers();
        for (Layer l : layers) {
            if (l instanceof PlaceNameLayer) {
                compassPosition = layers.indexOf(l);
            }
        }
        layers.add(compassPosition, layer);
    }

    /**
     * Inserts a layer into the WorldWindow's layer list just after the {@link PlaceNameLayer}.
     * Use this for overlay layers that must draw on top of place-name labels (e.g. UI annotations,
     * pick-highlight layers).
     *
     * @param wwd   the WorldWindow whose layer list will be modified
     * @param layer the layer to insert
     */
    public static void insertAfterPlacenames(WorldWindow wwd, Layer layer) {
        int compassPosition = 0;
        LayerList layers = wwd.getModel().getLayers();
        for (Layer l : layers) {
            if (l instanceof PlaceNameLayer) {
                compassPosition = layers.indexOf(l);
            }
        }
        layers.add(compassPosition + 1, layer);
    }

    /**
     * Inserts a layer into the WorldWindow's layer list just before the first layer whose name
     * contains {@code targetName}. Useful when inserting relative to a known named layer without
     * holding a direct reference to it.
     *
     * @param wwd        the WorldWindow whose layer list will be modified
     * @param layer      the layer to insert
     * @param targetName substring to match against existing layer names
     */
    public static void insertBeforeLayerName(WorldWindow wwd, Layer layer, String targetName) {
        int targetPosition = 0;
        LayerList layers = wwd.getModel().getLayers();
        for (Layer l : layers) {
            if (l.getName().contains(targetName)) {
                targetPosition = layers.indexOf(l);
                break;
            }
        }
        layers.add(targetPosition, layer);
    }

    static {
        System.setProperty("java.net.useSystemProxies", "true");
        if (Configuration.isMacOS()) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", "WorldWind Application");
            System.setProperty("com.apple.mrj.application.growbox.intrudes", "false");
            System.setProperty("apple.awt.brushMetalLook", "true");
        } else if (Configuration.isWindowsOS()) {
            System.setProperty("sun.awt.noerasebackground", "true"); // prevents flashing during window resizing
        }
    }

    public static AppFrame start(String appName, Class<?> appFrameClass) {
        if (Configuration.isMacOS() && appName != null) {
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", appName);
        }

        try {
            final AppFrame frame = (AppFrame) appFrameClass.getConstructor().newInstance();
            frame.setTitle(appName);
            // When launched from ExamplesIndex, dispose only — do not exit the JVM.
            int closeOp = Boolean.getBoolean("gov.nasa.worldwind.examplesLauncher")
                ? WindowConstants.DISPOSE_ON_CLOSE : WindowConstants.EXIT_ON_CLOSE;
            frame.setDefaultCloseOperation(closeOp);
            java.awt.EventQueue.invokeLater(() -> {
                frame.setVisible(true);
            });

            return frame;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        // Call the static start method like this from the main method of your derived class.
        // Substitute your application's name for the first argument.
        ApplicationTemplate.start("WorldWind Application", AppFrame.class);
    }
}
