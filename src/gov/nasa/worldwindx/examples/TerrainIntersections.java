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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

import com.jogamp.opengl.GL4;

import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.event.RenderingEvent;
import gov.nasa.worldwind.event.RenderingListener;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.Intersection;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.geom.Vec4;
import gov.nasa.worldwind.globes.Globe;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.GLRuntimeCapabilities;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.Path;
import gov.nasa.worldwind.render.PointPlacemark;
import gov.nasa.worldwind.render.PointPlacemarkAttributes;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwind.render.shaders.TerrainIntersectionCompute;
import gov.nasa.worldwind.terrain.HighResolutionTerrain;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Shows how to compute terrain intersections using the highest resolution terrain data available from a globe's
 * elevation model.
 * <p>
 * To generate and show intersections, Shift + LeftClick anywhere on the globe. The program forms a grid of locations
 * around the selected location. The grid points are shown in yellow. It then determines whether a line between the
 * selected location and each grid point intersects the terrain. If it does, the intersection nearest the selected
 * location is shown in cyan and a line is drawn from the selected location to the intersection. If there is no
 * intersection, a line is drawn from the selected location to the grid position.
 * <p>
 * If the highest resolution terrain is not available for the area around the selected location, it is retrieved from
 * the elevation model's source, which is most likely a remote server. Since the high-res data must be retrieved and
 * then loaded from the local disk cache, it will take some time to compute and show the intersections.
 * <p>
 * This example uses a {@link gov.nasa.worldwind.terrain.Terrain} object to perform the terrain retrieval, generation
 * and intersection calculations.s
 *
 * @author tag
 * @version $Id: TerrainIntersections.java 2109 2014-06-30 16:52:38Z tgaskins $
 */
public class TerrainIntersections extends ApplicationTemplate {

    /**
     * The width and height in degrees of the grid used to calculate intersections.
     */
    protected static final Angle GRID_RADIUS = Angle.fromDegrees(0.05);

    /**
     * The number of cells along each edge of the grid.
     */
    protected static final int GRID_DIMENSION = 10; // cells per side

    /**
     * The desired terrain resolution to use in the intersection calculations.
     */
    protected static final Double TARGET_RESOLUTION = 10d; // meters, or null for globe's highest resolution

    protected static final int NUM_THREADS = 4; // set to 1 to run synchronously

    public static class AppFrame extends ApplicationTemplate.AppFrame {
        private static final long serialVersionUID = 1L;

        private static final Cursor WaitCursor = new Cursor(Cursor.WAIT_CURSOR);

        protected HighResolutionTerrain terrain;
        protected RenderableLayer gridLayer;
        protected RenderableLayer intersectionsLayer;
        protected RenderableLayer sightLinesLayer;
        protected RenderableLayer tilesLayer;
        protected Thread calculationDispatchThread;
        protected JProgressBar progressBar;
        protected ThreadPoolExecutor threadPool;
        protected List<Position> grid;
        protected int numGridPoints; // used to monitor percentage progress
        protected long startTime, endTime; // for reporting calculation duration
        protected Position previousCurrentPosition;

        // Modified by seaglassfoundry.com — GPU compute shader intersection path with toggle UI
        protected volatile boolean useGpu;
        protected JRadioButton cpuRadio;
        protected JRadioButton gpuRadio;
        protected JLabel cpuTimeLabel;
        protected JLabel gpuTimeLabel;
        protected JLabel speedupLabel;
        protected TerrainIntersectionCompute gpuCompute;
        protected long cpuLastTime = -1;
        protected long gpuLastTime = -1;

        /** Maximum heightmap dimension per axis for the GPU path. */
        private static final int MAX_HEIGHTMAP_DIM = 1024;

        public AppFrame() {
            super(true, true, false);

            // Create a thread pool.
            this.threadPool = new ThreadPoolExecutor(NUM_THREADS, NUM_THREADS, 200, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>());

            // Modified by seaglassfoundry.com — build a Controls tab for CPU/GPU comparison
            JPanel controlPanel = buildControlPanel();

            // Progress bar (shared, shown below the tabs)
            this.progressBar = new JProgressBar(0, 100);
            this.progressBar.setBorder(new EmptyBorder(0, 10, 0, 10));
            this.progressBar.setBorderPainted(false);
            this.progressBar.setStringPainted(true);

            // Tabbed pane: Layers + Controls
            if (this.controlPanel != null) {
                this.getContentPane().remove(this.controlPanel);
                this.getContentPane().remove(this.wwjPanel);

                JTabbedPane tabs = new JTabbedPane();
                tabs.setBackground(new Color(45, 45, 48));

                JScrollPane layerScroll = new JScrollPane(this.layerPanel);
                layerScroll.setBorder(null);
                tabs.addTab("Layers", layerScroll);

                JScrollPane controlScroll = new JScrollPane(controlPanel);
                controlScroll.setBorder(null);
                tabs.addTab("Controls", controlScroll);

                // Add progress bar below the tabs
                JPanel sidePanel = new JPanel(new BorderLayout());
                sidePanel.add(tabs, BorderLayout.CENTER);
                sidePanel.add(this.progressBar, BorderLayout.SOUTH);

                this.controlPanel.add(sidePanel, BorderLayout.CENTER);

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

            // One-shot RenderingListener to check GL 4.3 compute shader availability
            getWwd().addRenderingListener(new RenderingListener() {
                @Override
                public void stageChanged(RenderingEvent event) {
                    if (!RenderingEvent.BEFORE_BUFFER_SWAP.equals(event.getStage()))
                        return;
                    GLRuntimeCapabilities caps = getWwd().getSceneController().getDrawContext()
                            .getGLRuntimeCapabilities();
                    boolean gpuAvailable = caps.getGLVersion() >= 4.3;
                    SwingUtilities.invokeLater(() -> {
                        gpuRadio.setEnabled(gpuAvailable);
                        if (!gpuAvailable)
                            gpuRadio.setToolTipText("GPU compute not available (requires GL 4.3)");
                    });
                    getWwd().removeRenderingListener(this);
                }
            });

            // Be sure to re-use the Terrain object to take advantage of its caching.
            this.terrain = new HighResolutionTerrain(getWwd().getModel().getGlobe(), TARGET_RESOLUTION);

            this.gridLayer = new RenderableLayer();
            this.gridLayer.setName("Grid");
            this.getWwd().getModel().getLayers().add(this.gridLayer);

            this.intersectionsLayer = new RenderableLayer();
            this.intersectionsLayer.setName("Intersections");
            this.getWwd().getModel().getLayers().add(this.intersectionsLayer);

            this.sightLinesLayer = new RenderableLayer();
            this.sightLinesLayer.setName("Sight Lines");
            this.getWwd().getModel().getLayers().add(this.sightLinesLayer);

            // Set up a mouse handler to generate a grid and start intersection calculations when the user shift-clicks.
            this.getWwd().getInputHandler().addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent mouseEvent) {
                    // Control-Click cancels any currently running operation.
                    if ((mouseEvent.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                        if (calculationDispatchThread != null && calculationDispatchThread.isAlive()) {
                            calculationDispatchThread.interrupt();
                        }
                        return;
                    }

                    // Alt-Click repeats the most recent calculations.
                    if ((mouseEvent.getModifiersEx() & InputEvent.ALT_DOWN_MASK) != 0) {
                        if (previousCurrentPosition == null) {
                            return;
                        }

                        mouseEvent.consume(); // tell the rest of WW that this event has been processed

                        computeAndShowIntersections(previousCurrentPosition);
                        return;
                    }

                    // Perform the intersection tests in response to Shift-Click.
                    if ((mouseEvent.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == 0) {
                        return;
                    }

                    mouseEvent.consume(); // tell the rest of WW that this event has been processed

                    final Position pos = getWwd().getCurrentPosition();
                    if (pos == null) {
                        return;
                    }

                    computeAndShowIntersections(pos);
                }
            });
        }

        protected void computeAndShowIntersections(final Position curPos) {
            this.previousCurrentPosition = curPos;

            SwingUtilities.invokeLater(() -> {
                setCursor(WaitCursor);
            });

            // Modified by seaglassfoundry.com — dispatch to CPU or GPU path based on toggle
            this.calculationDispatchThread = new Thread(() -> {
                try {
                    if (useGpu) {
                        performGpuIntersectionTests(curPos);
                    } else {
                        performIntersectionTests(curPos);
                    }
                } catch (InterruptedException e) {
                    System.out.println("Operation was interrupted");
                }
            });

            this.calculationDispatchThread.start();
        }

        // Create containers to hold the intersection points and the lines emanating from the center.
        protected List<Position> firstIntersectionPositions = new ArrayList<>();
        protected List<Position[]> sightLines = new ArrayList<>(GRID_DIMENSION * GRID_DIMENSION);

        // Make the picked location's position and model-coordinate point available to all methods.
        protected Position referencePosition;
        protected Vec4 referencePoint;

        // This is a collection of synchronized accessors to the list updated during the calculations.
        protected synchronized void addIntersectionPosition(Position position) {
            this.firstIntersectionPositions.add(position);
        }

        protected synchronized void addSightLine(Position positionA, Position positionB) {
            this.sightLines.add(new Position[]{positionA, positionB});
        }

        protected synchronized int getSightlinesSize() {
            return this.sightLines.size();
        }

        private long lastTime = System.currentTimeMillis();

        /**
         * Keeps the progress meter current. When calculations are complete, displays the results.
         */
        protected synchronized void updateProgress() {
            // Update the progress bar only once every 250 milliseconds to avoid stealing time from calculations.
            if (this.sightLines.size() >= this.numGridPoints) {
                endTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() < this.lastTime + 250) {
                return;
            }
            this.lastTime = System.currentTimeMillis();

            // On the EDT, update the progress bar and if calculations are complete, update the WorldWindow.
            SwingUtilities.invokeLater(() -> {
                int progress = (int) (100d * getSightlinesSize() / numGridPoints);
                progressBar.setValue(progress);

                if (progress >= 100) {
                    setCursor(Cursor.getDefaultCursor());
                    long elapsed = endTime - startTime;
                    progressBar.setString(elapsed + " ms");
                    cpuLastTime = elapsed;
                    updateTimingLabel();
                    showResults();
                    System.out.printf("CPU calculation time %d milliseconds\n", elapsed);
                }
            });
        }

        /**
         * Updates the WorldWind model with the new intersection locations and sight lines.
         */
        protected void showResults() {
            this.showIntersections(firstIntersectionPositions);
            this.showSightLines(sightLines);
//            this.showIntersectingTiles(this.grid, this.referencePosition);
            this.getWwd().redraw();
        }

        protected void performIntersectionTests(final Position curPos) throws InterruptedException {
            // Clear the results lists when the user selects a new location.
            this.firstIntersectionPositions.clear();
            this.sightLines.clear();

            // Raise the selected location and the grid points a little above ground just to show we can.
            final double height = 5; // meters

            // Form the grid.
            double gridRadius = GRID_RADIUS.degrees;
            Sector sector = Sector.fromDegrees(
                    curPos.getLatitude().degrees - gridRadius, curPos.getLatitude().degrees + gridRadius,
                    curPos.getLongitude().degrees - gridRadius, curPos.getLongitude().degrees + gridRadius);

            this.grid = buildGrid(sector, height, GRID_DIMENSION, GRID_DIMENSION);
            this.numGridPoints = grid.size();

            // Compute the position of the selected location (incorporate its height).
            this.referencePosition = new Position(curPos.getLatitude(), curPos.getLongitude(), height);
            this.referencePoint = terrain.getSurfacePoint(curPos.getLatitude(), curPos.getLongitude(), height);

//            // Pre-caching is unnecessary and is useful only when it occurs before the intersection
//            // calculations. It will incur extra overhead otherwise. The normal intersection calculations
//            // cause the same caching, making subsequent calculations on the same area faster.
//            this.preCache(grid, this.referencePosition);
            // On the EDT, show the grid.
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(0);
                progressBar.setString(null);
                clearLayers();
                showGrid(grid, referencePosition);
                getWwd().redraw();
            });

            // Perform the intersection calculations.
            this.startTime = System.currentTimeMillis();
            for (Position gridPos : this.grid) // for each grid point.
            {
                // Modified by seaglassfoundry.com - removed dead code: NUM_THREADS > 0 is always true so else branch never executes
                this.threadPool.execute(new Intersector(gridPos));
            }
        }

        /**
         * Performs one line of sight calculation between the reference position and a specified grid position.
         *
         * @param gridPosition the grid position.
         *
         * @throws InterruptedException if the operation is interrupted.
         */
        protected void performIntersection(Position gridPosition) throws InterruptedException {
            // Intersect the line between this grid point and the selected position.
            Intersection[] intersections = this.terrain.intersect(this.referencePosition, gridPosition);
            if (intersections == null || intersections.length == 0) {
                // No intersection, so the line goes from the center to the grid point.
                this.addSightLine(this.referencePosition, gridPosition);
                this.updateProgress();
                return;
            }

            // Only the first intersection is shown.
            Vec4 iPoint = intersections[0].getIntersectionPoint();
            Vec4 gPoint = terrain.getSurfacePoint(gridPosition.getLatitude(), gridPosition.getLongitude(),
                    gridPosition.getAltitude());

            // Check to see whether the intersection is beyond the grid point.
            if (iPoint.distanceTo3(this.referencePoint) >= gPoint.distanceTo3(this.referencePoint)) {
                // Intersection is beyond the grid point; the line goes from the center to the grid point.
                this.addSightLine(this.referencePosition, gridPosition);
                this.updateProgress();
                return;
            }

            // Compute the position corresponding to the intersection.
            Position iPosition = this.terrain.getGlobe().computePositionFromPoint(iPoint);

            // The sight line goes from the user-selected position to the intersection position.
            this.addSightLine(this.referencePosition, new Position(iPosition, 0));

            // Keep track of the intersection positions.
            this.addIntersectionPosition(iPosition);

            this.updateProgress();
        }

        /**
         * Inner {@link Runnable} to perform a single line/terrain intersection calculation.
         */
        protected class Intersector implements Runnable {

            protected final Position gridPosition;

            public Intersector(Position gridPosition) {
                this.gridPosition = gridPosition;
            }

            @Override
            public void run() {
                try {
                    performIntersection(this.gridPosition);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        // ---- Modified by seaglassfoundry.com — GPU compute shader intersection path ----

        /**
         * Builds the Controls tab panel with CPU/GPU mode selection and timing comparison.
         */
        private JPanel buildControlPanel() {
            JPanel root = new JPanel();
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            root.setBackground(WWStyle.BG_DARK);

            // ── Mode selection ──
            JPanel modePanel = new JPanel();
            modePanel.setLayout(new BoxLayout(modePanel, BoxLayout.Y_AXIS));
            modePanel.setBackground(WWStyle.BG_DARK);
            modePanel.setBorder(WWStyle.sectionBorder("Intersection Mode"));

            ButtonGroup modeGroup = new ButtonGroup();

            this.cpuRadio = new JRadioButton("CPU (HighResolutionTerrain)");
            this.cpuRadio.setFont(WWStyle.FONT_BASE);
            this.cpuRadio.setForeground(WWStyle.FG_PRIMARY);
            this.cpuRadio.setBackground(WWStyle.BG_DARK);
            this.cpuRadio.setFocusPainted(false);
            this.cpuRadio.setAlignmentX(Component.LEFT_ALIGNMENT);
            this.cpuRadio.setSelected(true);
            this.cpuRadio.addActionListener(e -> this.useGpu = false);
            modeGroup.add(this.cpuRadio);
            modePanel.add(this.cpuRadio);

            JLabel cpuDesc = WWStyle.label("  Per-ray tile fetch + triangle intersection", false);
            cpuDesc.setAlignmentX(Component.LEFT_ALIGNMENT);
            modePanel.add(cpuDesc);
            modePanel.add(vgap(WWStyle.GAP_XS));

            this.gpuRadio = new JRadioButton("GPU Compute Shader");
            this.gpuRadio.setFont(WWStyle.FONT_BASE);
            this.gpuRadio.setForeground(WWStyle.FG_PRIMARY);
            this.gpuRadio.setBackground(WWStyle.BG_DARK);
            this.gpuRadio.setFocusPainted(false);
            this.gpuRadio.setAlignmentX(Component.LEFT_ALIGNMENT);
            this.gpuRadio.setEnabled(false); // enabled after GL capability check
            this.gpuRadio.setToolTipText("Requires GL 4.3 — checking...");
            this.gpuRadio.addActionListener(e -> this.useGpu = true);
            modeGroup.add(this.gpuRadio);
            modePanel.add(this.gpuRadio);

            JLabel gpuDesc = WWStyle.label("  Bulk elevation fetch + GLSL 430 ray-march", false);
            gpuDesc.setAlignmentX(Component.LEFT_ALIGNMENT);
            modePanel.add(gpuDesc);

            root.add(modePanel);
            root.add(vgap(WWStyle.GAP_XS));

            // ── Timing comparison ──
            JPanel timingPanel = new JPanel(new GridLayout(0, 2, WWStyle.GAP_XS, WWStyle.GAP_XS));
            timingPanel.setBackground(WWStyle.BG_DARK);
            timingPanel.setBorder(WWStyle.sectionBorder("Timing Comparison"));

            this.cpuTimeLabel = statsValue("—");
            this.gpuTimeLabel = statsValue("—");
            this.speedupLabel = statsValue("—");

            timingPanel.add(statsKey("CPU time:"));   timingPanel.add(this.cpuTimeLabel);
            timingPanel.add(statsKey("GPU time:"));   timingPanel.add(this.gpuTimeLabel);
            timingPanel.add(statsKey("Speedup:"));    timingPanel.add(this.speedupLabel);

            root.add(timingPanel);
            root.add(vgap(WWStyle.GAP_XS));

            // ── Instructions ──
            JPanel instrPanel = new JPanel();
            instrPanel.setLayout(new BoxLayout(instrPanel, BoxLayout.Y_AXIS));
            instrPanel.setBackground(WWStyle.BG_DARK);
            instrPanel.setBorder(WWStyle.sectionBorder("Instructions"));

            String[] instructions = {
                "Shift+Click — compute intersections",
                "Alt+Click — repeat last position",
                "Ctrl+Click — cancel operation",
                "",
                "Run both modes on the same area",
                "to compare timing."
            };
            for (String line : instructions) {
                JLabel l = WWStyle.label(line.isEmpty() ? " " : line, false);
                l.setAlignmentX(Component.LEFT_ALIGNMENT);
                instrPanel.add(l);
            }

            root.add(instrPanel);
            return root;
        }

        /**
         * Updates the timing comparison labels in the Controls tab.
         */
        protected void updateTimingLabel() {
            if (cpuLastTime >= 0)
                cpuTimeLabel.setText(cpuLastTime + " ms");
            if (gpuLastTime >= 0)
                gpuTimeLabel.setText(gpuLastTime + " ms");
            if (cpuLastTime >= 0 && gpuLastTime >= 0) {
                double speedup = (double) cpuLastTime / Math.max(1, gpuLastTime);
                speedupLabel.setText(String.format("%.1fx", speedup));
            }
        }

        /**
         * Computes the heightmap grid dimension for a sector, clamped to MAX_HEIGHTMAP_DIM.
         */
        protected int computeHeightmapDimension(double sectorWidthMeters) {
            int ideal = (int) Math.ceil(sectorWidthMeters / TARGET_RESOLUTION);
            return Math.min(Math.max(ideal, 2), MAX_HEIGHTMAP_DIM);
        }

        /**
         * GPU intersection path: fetches elevation data for the sector, uploads it with ray definitions
         * to a compute shader, and reads back intersection results.
         */
        protected void performGpuIntersectionTests(final Position curPos) throws InterruptedException {
            this.firstIntersectionPositions.clear();
            this.sightLines.clear();

            final double height = 5; // meters above ground, same as CPU path

            // Form the grid sector
            double gridRadius = GRID_RADIUS.degrees;
            Sector sector = Sector.fromDegrees(
                    curPos.getLatitude().degrees - gridRadius, curPos.getLatitude().degrees + gridRadius,
                    curPos.getLongitude().degrees - gridRadius, curPos.getLongitude().degrees + gridRadius);

            // Build the display grid (same as CPU path)
            this.grid = buildGrid(sector, height, GRID_DIMENSION, GRID_DIMENSION);
            this.numGridPoints = grid.size();
            this.referencePosition = new Position(curPos.getLatitude(), curPos.getLongitude(), height);

            // Show the grid immediately on the EDT
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(0);
                progressBar.setString("Fetching elevations...");
                clearLayers();
                showGrid(grid, referencePosition);
                getWwd().redraw();
            });

            // Compute sector dimensions in local meters (flat-earth approximation)
            Globe globe = getWwd().getModel().getGlobe();
            double metersPerDegreeLat = globe.getEquatorialRadius() * Math.PI / 180.0;
            double cosLat = Math.cos(Math.toRadians(curPos.getLatitude().degrees));
            double metersPerDegreeLon = metersPerDegreeLat * cosLat;

            double sectorCenterLat = sector.getCentroid().getLatitude().degrees;
            double sectorCenterLon = sector.getCentroid().getLongitude().degrees;

            double sectorWidthM = sector.getDeltaLonDegrees() * metersPerDegreeLon;
            double sectorHeightM = sector.getDeltaLatDegrees() * metersPerDegreeLat;

            // Determine heightmap dimensions (clamped to MAX_HEIGHTMAP_DIM)
            int hmWidth = computeHeightmapDimension(sectorWidthM);
            int hmHeight = computeHeightmapDimension(sectorHeightM);

            // Build regular lat/lon grid for elevation query
            ArrayList<LatLon> hmLatlons = new ArrayList<>(hmWidth * hmHeight);
            double dLat = sector.getDeltaLatDegrees() / (hmHeight - 1);
            double dLon = sector.getDeltaLonDegrees() / (hmWidth - 1);
            for (int row = 0; row < hmHeight; row++) {
                double lat = row == hmHeight - 1
                        ? sector.getMaxLatitude().degrees : sector.getMinLatitude().degrees + row * dLat;
                for (int col = 0; col < hmWidth; col++) {
                    double lon = col == hmWidth - 1
                            ? sector.getMaxLongitude().degrees : sector.getMinLongitude().degrees + col * dLon;
                    hmLatlons.add(LatLon.fromDegrees(lat, lon));
                }
            }

            // Fetch elevations with polling (same pattern as HighResolutionTerrain.getElevations)
            double[] elevations = new double[hmLatlons.size()];
            double[] targetRes = globe.getElevationModel().getBestResolutions(sector);
            for (int i = 0; i < targetRes.length; i++)
                targetRes[i] = Math.max(targetRes[i], TARGET_RESOLUTION / globe.getEquatorialRadius());

            long fetchStart = System.currentTimeMillis();
            double[] actualRes = new double[targetRes.length];
            for (int i = 0; i < actualRes.length; i++) actualRes[i] = Double.MAX_VALUE;
            while (true) {
                actualRes = globe.getElevations(sector, hmLatlons, targetRes, elevations);
                boolean met = true;
                for (int i = 0; i < actualRes.length; i++) {
                    if (actualRes[i] > targetRes[i]) { met = false; break; }
                }
                if (met) break;
                Thread.sleep(5);
                if (System.currentTimeMillis() - fetchStart > 30_000)
                    break; // timeout after 30s, use what we have
            }

            // Convert elevations to float heightmap
            float[] heightmap = new float[elevations.length];
            for (int i = 0; i < elevations.length; i++)
                heightmap[i] = (float) elevations[i];

            // Sector bounds in local meters (relative to sector centroid)
            float sectorMinX = (float) ((sector.getMinLongitude().degrees - sectorCenterLon) * metersPerDegreeLon);
            float sectorMinY = (float) ((sector.getMinLatitude().degrees - sectorCenterLat) * metersPerDegreeLat);
            float sectorMaxX = (float) ((sector.getMaxLongitude().degrees - sectorCenterLon) * metersPerDegreeLon);
            float sectorMaxY = (float) ((sector.getMaxLatitude().degrees - sectorCenterLat) * metersPerDegreeLat);

            // Build ray data: 6 floats per ray (origin xyz, target xyz) in local meters
            // Origin = reference position, target = each grid point
            double refElev = globe.getElevation(curPos.getLatitude(), curPos.getLongitude()) + height;
            float refX = (float) ((curPos.getLongitude().degrees - sectorCenterLon) * metersPerDegreeLon);
            float refY = (float) ((curPos.getLatitude().degrees - sectorCenterLat) * metersPerDegreeLat);
            float refZ = (float) refElev;

            int numRays = grid.size();
            float[] rayData = new float[numRays * 6];
            for (int i = 0; i < numRays; i++) {
                Position gp = grid.get(i);
                double gpElev = globe.getElevation(gp.getLatitude(), gp.getLongitude()) + gp.getAltitude();
                rayData[i * 6]     = refX;
                rayData[i * 6 + 1] = refY;
                rayData[i * 6 + 2] = refZ;
                rayData[i * 6 + 3] = (float) ((gp.getLongitude().degrees - sectorCenterLon) * metersPerDegreeLon);
                rayData[i * 6 + 4] = (float) ((gp.getLatitude().degrees - sectorCenterLat) * metersPerDegreeLat);
                rayData[i * 6 + 5] = (float) gpElev;
            }

            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(50);
                progressBar.setString("Computing on GPU...");
            });

            // Schedule GL-thread work via RenderingListener
            final float[] fHeightmap = heightmap;
            final float[] fRayData = rayData;
            final int fHmWidth = hmWidth;
            final int fHmHeight = hmHeight;

            this.startTime = System.currentTimeMillis();

            getWwd().addRenderingListener(new RenderingListener() {
                @Override
                public void stageChanged(RenderingEvent event) {
                    if (!RenderingEvent.BEFORE_BUFFER_SWAP.equals(event.getStage()))
                        return;
                    getWwd().removeRenderingListener(this);

                    try {
                        GL4 gl4 = getWwd().getSceneController().getDrawContext().getGL().getGL4();

                        // Lazy init
                        if (gpuCompute == null) {
                            gpuCompute = new TerrainIntersectionCompute();
                            if (!gpuCompute.init(gl4)) {
                                System.err.println("GPU compute shader init failed");
                                SwingUtilities.invokeLater(() -> {
                                    setCursor(Cursor.getDefaultCursor());
                                    progressBar.setString("GPU init failed");
                                    gpuRadio.setEnabled(false);
                                });
                                return;
                            }
                        }

                        TerrainIntersectionCompute.IntersectionResult[] results = gpuCompute.dispatch(
                                gl4, fHeightmap, fHmWidth, fHmHeight,
                                sectorMinX, sectorMinY, sectorMaxX, sectorMaxY,
                                fRayData, numRays);

                        long gpuEndTime = System.currentTimeMillis();

                        // Convert results back to Positions and populate the result lists
                        for (int i = 0; i < numRays; i++) {
                            Position gridPos = grid.get(i);
                            if (results[i].hit) {
                                // Convert local meters back to lat/lon
                                double hitLat = sectorCenterLat + results[i].y / metersPerDegreeLat;
                                double hitLon = sectorCenterLon + results[i].x / metersPerDegreeLon;
                                double hitElev = results[i].z;

                                Position iPos = Position.fromDegrees(hitLat, hitLon, hitElev);

                                // Check if intersection is closer than the grid point
                                double hitDistSq = (results[i].x - refX) * (results[i].x - refX)
                                        + (results[i].y - refY) * (results[i].y - refY)
                                        + (results[i].z - refZ) * (results[i].z - refZ);
                                float gpX = fRayData[i * 6 + 3];
                                float gpY = fRayData[i * 6 + 4];
                                float gpZ = fRayData[i * 6 + 5];
                                double gpDistSq = (gpX - refX) * (gpX - refX)
                                        + (gpY - refY) * (gpY - refY)
                                        + (gpZ - refZ) * (gpZ - refZ);

                                if (hitDistSq < gpDistSq) {
                                    sightLines.add(new Position[]{referencePosition, new Position(iPos, 0)});
                                    firstIntersectionPositions.add(iPos);
                                } else {
                                    sightLines.add(new Position[]{referencePosition, gridPos});
                                }
                            } else {
                                sightLines.add(new Position[]{referencePosition, gridPos});
                            }
                        }

                        long elapsed = gpuEndTime - startTime;

                        SwingUtilities.invokeLater(() -> {
                            setCursor(Cursor.getDefaultCursor());
                            progressBar.setValue(100);
                            progressBar.setString(elapsed + " ms");
                            gpuLastTime = elapsed;
                            updateTimingLabel();
                            showResults();
                            System.out.printf("GPU calculation time %d milliseconds\n", elapsed);
                        });
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        SwingUtilities.invokeLater(() -> {
                            setCursor(Cursor.getDefaultCursor());
                            progressBar.setString("GPU error: " + ex.getMessage());
                        });
                    }
                }
            });

            // Trigger a redraw so the RenderingListener fires
            getWwd().redraw();
        }

        // ---- End GPU compute shader intersection path ----

        protected List<Position> buildGrid(Sector sector, double height, int nLatCells, int nLonCells) {
            List<Position> grid = new ArrayList<>((nLatCells + 1) * (nLonCells + 1));

            double dLat = sector.getDeltaLatDegrees() / nLatCells;
            double dLon = sector.getDeltaLonDegrees() / nLonCells;

            for (int j = 0; j <= nLatCells; j++) {
                double lat = j == nLatCells
                        ? sector.getMaxLatitude().degrees : sector.getMinLatitude().degrees + j * dLat;

                for (int i = 0; i <= nLonCells; i++) {
                    double lon = i == nLonCells
                            ? sector.getMaxLongitude().degrees : sector.getMinLongitude().degrees + i * dLon;

                    grid.add(Position.fromDegrees(lat, lon, height));
                }
            }

            return grid;
        }

        protected void preCache(List<Position> grid, Position centerPosition) throws InterruptedException {
            // Pre-cache the tiles that will be needed for the intersection calculations.
            double n = 0;
            final long start = System.currentTimeMillis();
            for (Position gridPos : grid) // for each grid point.
            {
                final double progress = 100 * (n++ / grid.size());
                terrain.cacheIntersectingTiles(centerPosition, gridPos);

                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue((int) progress);
                    progressBar.setString(null);
                });
            }

            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(100);
            });

            long end = System.currentTimeMillis();
            System.out.printf("Pre-caching time %d milliseconds, cache usage %f, tiles %d\n", end - start,
                    terrain.getCacheUsage(), terrain.getNumCacheEntries());
        }

        protected void clearLayers() {
            this.intersectionsLayer.removeAllRenderables();
            this.sightLinesLayer.removeAllRenderables();
            this.gridLayer.removeAllRenderables();
        }

        protected void showIntersections(List<Position> intersections) {
            this.intersectionsLayer.removeAllRenderables();

            // Display the intersections as CYAN points.
            PointPlacemarkAttributes intersectionPointAttributes;
            intersectionPointAttributes = new PointPlacemarkAttributes();
            intersectionPointAttributes.setLineMaterial(Material.CYAN);
            intersectionPointAttributes.setScale(6d);
            intersectionPointAttributes.setUsePointAsDefaultImage(true);

            for (Position p : intersections) {
                PointPlacemark pm = new PointPlacemark(p);
                pm.setAltitudeMode(WorldWind.CLAMP_TO_GROUND);
                pm.setAttributes(intersectionPointAttributes);
                pm.setValue(AVKey.DISPLAY_NAME, p.toString());
                this.intersectionsLayer.addRenderable(pm);
            }
        }

        protected void showSightLines(List<Position[]> sightLines) {
            this.sightLinesLayer.removeAllRenderables();

            // Display the sight lines as green lines.
            ShapeAttributes lineAttributes;
            lineAttributes = new BasicShapeAttributes();
            lineAttributes.setDrawOutline(true);
            lineAttributes.setDrawInterior(false);
            lineAttributes.setOutlineMaterial(Material.GREEN);
            lineAttributes.setOutlineOpacity(0.6);

            for (Position[] pp : sightLines) {
                List<Position> endPoints = new ArrayList<>();
                endPoints.add(pp[0]);
                endPoints.add(pp[1]);

                Path path = new Path(endPoints);
                path.setAltitudeMode(WorldWind.RELATIVE_TO_GROUND);
                path.setAttributes(lineAttributes);
                this.sightLinesLayer.addRenderable(path);
            }
        }

        protected void showGridSightLines(List<Position> grid, Position cPos) {
            this.sightLinesLayer.removeAllRenderables();

            // Display lines from the center to each grid point.
            ShapeAttributes lineAttributes;
            lineAttributes = new BasicShapeAttributes();
            lineAttributes.setDrawOutline(true);
            lineAttributes.setDrawInterior(false);
            lineAttributes.setOutlineMaterial(Material.GREEN);
            lineAttributes.setOutlineOpacity(0.6);

            for (Position p : grid) {
                List<Position> endPoints = new ArrayList<>();
                endPoints.add(cPos);
                endPoints.add(new Position(p.getLatitude(), p.getLongitude(), 0));

                Path path = new Path(endPoints);
                path.setAltitudeMode(WorldWind.RELATIVE_TO_GROUND);
                path.setAttributes(lineAttributes);
                this.sightLinesLayer.addRenderable(path);
            }
        }

        protected void showGrid(List<Position> grid, Position cPos) {
            this.gridLayer.removeAllRenderables();

            // Display the grid points in yellow.
            PointPlacemarkAttributes gridPointAttributes;
            gridPointAttributes = new PointPlacemarkAttributes();
            gridPointAttributes.setLineMaterial(Material.YELLOW);
            gridPointAttributes.setScale(6d);
            gridPointAttributes.setUsePointAsDefaultImage(true);

            for (Position p : grid) {
                PointPlacemark pm = new PointPlacemark(p);
                pm.setAltitudeMode(WorldWind.RELATIVE_TO_GROUND);
                pm.setAttributes(gridPointAttributes);
                pm.setLineEnabled(true);
                pm.setValue(AVKey.DISPLAY_NAME, p.toString());
                this.gridLayer.addRenderable(pm);
            }

            showCenterPoint(cPos);
        }

        protected void showCenterPoint(Position cPos) {
            // Display the center point in red.
            PointPlacemarkAttributes selectedLocationAttributes;
            selectedLocationAttributes = new PointPlacemarkAttributes();
            selectedLocationAttributes.setLineMaterial(Material.RED);
            selectedLocationAttributes.setScale(8d);
            selectedLocationAttributes.setUsePointAsDefaultImage(true);

            PointPlacemark pm = new PointPlacemark(cPos);
            pm.setAltitudeMode(WorldWind.RELATIVE_TO_GROUND);
            pm.setAttributes(selectedLocationAttributes);
            pm.setValue(AVKey.DISPLAY_NAME, cPos.toString());
            pm.setLineEnabled(true);
            this.gridLayer.addRenderable(pm);
        }

        // ── UI helpers ──

        private static JLabel statsKey(String text) {
            JLabel l = WWStyle.label(text, false);
            l.setHorizontalAlignment(SwingConstants.RIGHT);
            return l;
        }

        private static JLabel statsValue(String text) {
            JLabel l = new JLabel(text);
            l.setFont(WWStyle.FONT_BOLD);
            l.setForeground(WWStyle.ACCENT);
            return l;
        }

        private static JPanel vgap(int height) {
            JPanel p = new JPanel();
            p.setBackground(WWStyle.BG_DARK);
            p.setPreferredSize(new Dimension(0, height));
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            return p;
        }
    }

    public static void main(String[] args) {
        // zoom to San Francisco downtown
        Configuration.setValue(AVKey.INITIAL_ALTITUDE, 34e3);
        Configuration.setValue(AVKey.INITIAL_LATITUDE, 37.9521d);
        Configuration.setValue(AVKey.INITIAL_LONGITUDE, -119.7761d);

        // Adjust configuration values before instantiation
        ApplicationTemplate.start("WorldWind Terrain Intersections", AppFrame.class);
    }
}
