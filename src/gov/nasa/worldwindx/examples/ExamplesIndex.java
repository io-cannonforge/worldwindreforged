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
 * Modifications and additions by seaglassfoundry.com — WorldWind Reforged project.
 * ExamplesIndex.java: single entry-point launcher for all WorldWind Reforged examples.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;

import com.formdev.flatlaf.FlatDarkLaf;

import gov.nasa.worldwindx.examples.util.ExampleDocs;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Single entry-point for browsing and launching all WorldWind Reforged examples.
 *
 * <p>Displays a category tree on the left and a scrollable example list on the right.
 * Selecting an example shows its description. Double-clicking or pressing Launch starts
 * the example in-process via reflection.</p>
 *
 * <p>Badges:</p>
 * <ul>
 *   <li><b>New</b> — new example added in the Reforged project</li>
 *   <li><b>Ph2</b> — showcases Phase 2 WMS / time-series engine</li>
 *   <li><b>Ph3</b> — showcases Phase 3 GLSL shader rendering</li>
 *   <li><b>Ph4</b> — showcases Phase 4 GPU tessellation terrain</li>
 * </ul>
 *
 * @author seaglassfoundry.com
 */
public class ExamplesIndex extends JFrame {
    private static final long serialVersionUID = 1L;

    // ── Example entry ────────────────────────────────────────────────────────

    private static final class ExampleEntry {
        final String displayName;
        final String className;
        final String description;
        final String badge; // null, "New", "Ph2", "Ph3", "Ph4"

        ExampleEntry(String displayName, String className, String description, String badge) {
            this.displayName = displayName;
            this.className = className;
            this.description = description;
            this.badge = badge;
        }

        ExampleEntry(String displayName, String className, String description) {
            this(displayName, className, description, null);
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // ── Catalogue ────────────────────────────────────────────────────────────

    private static final String BASE = "gov.nasa.worldwindx.examples.";

    private static final LinkedHashMap<String, ExampleEntry[]> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("Basics", new ExampleEntry[]{
            new ExampleEntry("Simplest Possible Example",    BASE + "SimplestPossibleExample",    "The minimal WorldWind application — a globe with default imagery layers."),
            new ExampleEntry("Hello World Wind",             BASE + "HelloWorldWind",             "Basic WorldWind window with default Earth imagery and navigation."),
            new ExampleEntry("Stereo",                       BASE + "Stereo",                    "Stereoscopic rendering: red/blue anaglyph and hardware stereo modes."),
            new ExampleEntry("OpenGL Support Test",          BASE + "OpenGLSupportTest",          "Detects and reports OpenGL hardware capabilities and supported extensions."),
            new ExampleEntry("World Wind Diagnostics",       BASE + "WorldWindDiagnostics",       "Full system diagnostic report useful for troubleshooting rendering issues."),
            new ExampleEntry("Configuring GL Capabilities",  BASE + "ConfiguringGLRuntimeCapabilities", "Configures JOGL runtime GL capabilities (multisampling, depth buffer) at startup."),
            new ExampleEntry("Logging Control",              BASE + "LoggingControl",             "Demonstrates WorldWind logging levels and java.util.logging configuration."),
            new ExampleEntry("Cache Location Configuration", BASE + "CacheLocationConfiguration", "Shows how to set a custom tile-cache directory for downloaded data."),
            new ExampleEntry("Screen Shots",                 BASE + "ScreenShots",               "Captures the WorldWind canvas to a PNG image file on demand."),
            new ExampleEntry("Shutdown",                     BASE + "Shutdown",                  "Demonstrates proper WorldWind application shutdown and resource cleanup."),
        });

        CATEGORIES.put("Surface Shapes", new ExampleEntry[]{
            new ExampleEntry("Shapes",                          BASE + "Shapes",                       "Overview of all 2D surface shape types in one scene."),
            new ExampleEntry("Surface Shapes",                  BASE + "SurfaceShapes",                "SurfacePolygon, SurfacePolyline, SurfaceCircle, and more with attributes."),
            new ExampleEntry("Polygons",                        BASE + "Polygons",                     "3D Polygon with fill colours, texturing, rotation, holes, and dateline spanning."),
            new ExampleEntry("Extruded Polygons",               BASE + "ExtrudedPolygons",             "Polygons extruded from the surface with textured sides and caps."),
            new ExampleEntry("Extruded Shapes",                 BASE + "ExtrudedShapes",               "Extruded polygons loaded from shapefile data on the globe."),
            new ExampleEntry("Extruded Polygon With Base Depth",BASE + "ExtrudedPolygonWithBaseDepth", "Extruded polygon whose base is embedded below the terrain surface."),
            new ExampleEntry("Paths",                           BASE + "Paths",                        "3D paths with various width, colour, and stipple attributes."),
            new ExampleEntry("Paths With Direction",            BASE + "PathsWithDirection",            "Paths with chevron direction arrows at regular intervals."),
            new ExampleEntry("Path Position Colors",            BASE + "PathPositionColors",            "Paths coloured with per-vertex gradients."),
            new ExampleEntry("Parallel Paths",                  BASE + "ParallelPaths",                "Multiple parallel offset paths rendered side-by-side."),
            new ExampleEntry("Paths On Dateline",               BASE + "PathsOnDateline",              "Paths that correctly cross the anti-meridian (±180°)."),
            new ExampleEntry("Paths With Labels",               BASE + "PathsWithLabels",              "Paths with inline text labels placed along the route."),
            new ExampleEntry("Multi-Res Path",                  BASE + "MultiResPath",                 "Path with level-of-detail tessellation controlled by view distance."),
            new ExampleEntry("Dashed Lines",                    BASE + "DashedLinesExample",           "Phase 3 GPU dashed/dotted line shader — all stipple patterns.", "Ph3"),
            new ExampleEntry("Procedural Fill Patterns",        BASE + "ProceduralFillPatternsExample","Phase 3 procedural fill shader: hatch, crosshatch, and dot patterns.", "Ph3"),
            new ExampleEntry("Surface Shape Showcase",          BASE + "SurfaceShapeShowcase",         "Every surface shape type with Phase 3 fill patterns and colour pickers.", "Ph3"),
            new ExampleEntry("Surface Text",                    BASE + "SurfaceTextUsage",             "Text strings rendered directly on the globe surface."),
            new ExampleEntry("Line Builder",                    BASE + "LineBuilder",                  "Interactive mouse-driven line-drawing tool on the globe."),
            new ExampleEntry("Contour Lines",                   BASE + "ContourLines",                 "Terrain contour lines derived from the active elevation model."),
            new ExampleEntry("Contour Builder",                 BASE + "ContourBuilderExample",        "Programmatic construction of contour line layers from data arrays."),
            new ExampleEntry("Shape Editing",                   BASE + "ShapeEditing",                 "Interactive control-point editing for surface shapes."),
            new ExampleEntry("Shape Editing Extension",         BASE + "ShapeEditingExtension",        "Custom shape-editing handles added via the extension API."),
            new ExampleEntry("Dragging Shapes",                 BASE + "DraggingShapes",               "Drag surface shapes across the globe with the mouse."),
            new ExampleEntry("Simple Shape Dragging",           BASE + "SimpleShapeDragging",          "Minimal example: drag a polygon or path with mouse events."),
            new ExampleEntry("Shape Clipping",                  BASE + "ShapeClipping",                "Clip shapes to land or water areas using elevation data."),
            new ExampleEntry("Shape Combining",                 BASE + "ShapeCombining",               "Boolean geometry operations: union and intersection of shapes."),
            new ExampleEntry("Rotated Sector",                  BASE + "RotatedSector",                "Rectangular sector shape with arbitrary azimuth rotation."),
            new ExampleEntry("Line Background",                 BASE + "LineBackground",               "Path rendered with a wide background halo line for visibility."),
        });

        CATEGORIES.put("3D Shapes", new ExampleEntry[]{
            new ExampleEntry("Boxes",          BASE + "Boxes",          "3D box shapes with varying dimensions and material attributes."),
            new ExampleEntry("Cones",          BASE + "Cones",          "3D cone shapes with height and radius controls."),
            new ExampleEntry("Cylinders",      BASE + "Cylinders",      "3D cylinder shapes with top and bottom radii."),
            new ExampleEntry("Ellipsoids",     BASE + "Ellipsoids",     "3D ellipsoid shapes with three independent semi-axes."),
            new ExampleEntry("Pyramids",       BASE + "Pyramids",       "3D pyramid shapes with rectangular base."),
            new ExampleEntry("Wedges",         BASE + "Wedges",         "3D wedge shapes (truncated pyramid)."),
            new ExampleEntry("Rigid Shapes",   BASE + "RigidShapes",    "All 3D rigid shape types displayed together."),
            new ExampleEntry("Airspaces",      BASE + "Airspaces",      "Airspace polygons: box, orbit, corridor, curtain, cake, and sphere."),
            new ExampleEntry("Airspace Builder",BASE + "AirspaceBuilder","Interactive builder for complex airspace geometry."),
            new ExampleEntry("Radar Volume",   BASE + "RadarVolume",    "Radar coverage volume rendered as a line-of-sight cone."),
            new ExampleEntry("Radar Volume Example", BASE + "RadarVolumeExample", "Radar volume with terrain intersection and configurable azimuth/elevation ranges."),
        });

        CATEGORIES.put("Layers / WMS", new ExampleEntry[]{
            new ExampleEntry("WMS Explorer",            BASE + "WMSExplorer",             "Browse, preview, and load WMS layers using the Phase 2 WMS engine.", "Ph2"),
            new ExampleEntry("WMS Layer Manager",       BASE + "WMSLayerManager",         "Manage active WMS layers with visibility, ordering, and opacity controls."),
            new ExampleEntry("WMS Time-Series Demo",    BASE + "WMSTimeSeriesDemo",       "Animate GIBS time-series layers (MODIS Terra, VIIRS S-NPP, fires, snow).", "Ph2"),
            new ExampleEntry("Layer Opacity & Blending",BASE + "LayerOpacityAndBlendingDemo","Per-layer opacity sliders with GIBS BlueMarble and Landsat blending.", "Ph2"),
            new ExampleEntry("WCS Elevations",          BASE + "WCSElevations",           "Load elevation data from a WCS 1.0.0 service endpoint."),
            new ExampleEntry("Place Names",             BASE + "PlaceNames",              "NASA place-name label layer with density controls."),
            new ExampleEntry("On-Screen Layer Manager", BASE + "OnScreenLayerManager",    "Layer manager rendered as an in-globe overlay widget."),
            new ExampleEntry("Layer Tree Usage",        BASE + "LayerTreeUsage",          "Hierarchical layer tree panel with group and leaf nodes."),
        });

        CATEGORIES.put("Terrain", new ExampleEntry[]{
            new ExampleEntry("GPU Terrain Demo",         BASE + "GPUTerrainDemo",          "Phase 4 GPU terrain: CPU / Shader / Tessellation / Compute+Tess modes.", "Ph4"),
            new ExampleEntry("Terrain Profiler",         BASE + "TerrainProfiler",         "Interactive cross-section elevation profile along a user-drawn path."),
            new ExampleEntry("Terrain Intersections",    BASE + "TerrainIntersections",     "Ray-terrain intersection calculations for line-of-sight analysis."),
            new ExampleEntry("Terrain Benchmark",        BASE + "TerrainRenderingBenchmark","Benchmarks terrain tile loading and rendering throughput."),
            new ExampleEntry("High-Resolution Terrain",  BASE + "HighResolutionTerrainTest","Sample high-resolution elevation at many positions asynchronously."),
            new ExampleEntry("Custom Elevation Model",   BASE + "CustomElevationModel",     "Plug in a custom elevation model implementation via the API."),
            new ExampleEntry("Get Best Elevations",      BASE + "GetBestElevations",        "Query the best available elevation for a set of geographic positions."),
            new ExampleEntry("Elevations All Zero",      BASE + "ElevationsAllZero",        "Demonstrates behaviour when the elevation model returns all zeros."),
            new ExampleEntry("Bathymetry Removal",       BASE + "BathymetryRemoval",        "Strip bathymetry (negative elevations) from the active model."),
            new ExampleEntry("EGM96 Offsets",            BASE + "EGM96Offsets",             "Apply EGM96 geoid-undulation offsets to elevation queries."),
            new ExampleEntry("EGM2008 Offsets",          BASE + "EGM2008Offsets",           "Apply EGM2008 geoid-undulation offsets to elevation queries."),
        });

        CATEGORIES.put("Data Formats", new ExampleEntry[]{
            new ExampleEntry("Shapefiles",                    BASE + "Shapefiles",                   "Load and render ESRI shapefile point, polyline, and polygon features."),
            new ExampleEntry("Shapefile Viewer",              BASE + "ShapefileViewer",              "Interactive viewer for shapefiles with attribute-table inspection."),
            new ExampleEntry("Shapefile Attribute Groups",    BASE + "ShapefileAttributeGroups",     "Style shapefile features into groups by attribute value."),
            new ExampleEntry("Shapefile Benchmark",           BASE + "ShapefileBenchmark",           "Benchmark shapefile loading and rendering performance."),
            new ExampleEntry("Extruded Polygons From Shapefile",BASE + "ExtrudedPolygonsFromShapefile","Extrude shapefile polygon features into 3D building volumes."),
            new ExampleEntry("GeoJSON Viewer",                BASE + "GeoJSONViewer",               "Load and inspect GeoJSON files; click features to see attributes.", "New"),
            new ExampleEntry("COLLADA Viewer",                BASE + "ColladaViewer",               "Load and display COLLADA 3D model files on the globe."),
            new ExampleEntry("Surface Images",                BASE + "SurfaceImages",               "Drape georeferenced images on terrain using SurfaceImage."),
            new ExampleEntry("Surface Image Viewer",          BASE + "SurfaceImageViewer",          "Interactive viewer for georeferenced surface image layers."),
            new ExampleEntry("Rubber Sheet Image",            BASE + "RubberSheetImage",            "Stretch and reposition a surface image interactively."),
            new ExampleEntry("VPF Layer Demo",                BASE + "VPFLayerDemo",                "Render VPF (Vector Product Format) vector data layers."),
            new ExampleEntry("KML Viewer",                    BASE + "kml.KMLViewer",               "Load and display KML and KMZ files from disk or URL."),
        });

        CATEGORIES.put("Picking", new ExampleEntry[]{
            new ExampleEntry("Deep Picking",         BASE + "DeepPicking",         "Pick shapes that are visually obscured by other shapes."),
            new ExampleEntry("Icon Picking",         BASE + "IconPicking",         "Efficiently pick from a large icon layer with thousands of icons."),
            new ExampleEntry("Pick Frustum",         BASE + "PickFrustum",         "Frustum-cull shape candidates before performing pick testing."),
            new ExampleEntry("Sector Selection",     BASE + "SectorSelection",     "Select all shapes that fall within a dragged geographic sector."),
            new ExampleEntry("Screen Selection",     BASE + "ScreenSelection",     "Select shapes within a rubber-band screen rectangle."),
            new ExampleEntry("Screen Image Dragging",BASE + "ScreenImageDragging", "Drag screen-space image overlays with the mouse."),
            new ExampleEntry("Context Menus on Shapes",BASE + "ContextMenusOnShapes","Right-click context menus attached to picked shapes."),
        });

        CATEGORIES.put("Annotations", new ExampleEntry[]{
            new ExampleEntry("Annotations",             BASE + "Annotations",          "All annotation types: text, image, HTML, and screen-space balloons."),
            new ExampleEntry("Annotation Controls",     BASE + "AnnotationControls",   "Annotations with embedded Swing controls in the rendered balloon."),
            new ExampleEntry("Balloons",                BASE + "Balloons",             "HTML balloon annotations attached to globe positions."),
            new ExampleEntry("Web Browser Balloons",    BASE + "WebBrowserBalloons",   "Balloon annotations with a full embedded web browser."),
            new ExampleEntry("Globe Annotation",        BASE + "GlobeAnnotationExample","Basic globe-positioned text annotation with attribute styling."),
            new ExampleEntry("Alarm Icons",             BASE + "AlarmIcons",           "Icons that cycle through visual alarm states (normal, caution, warning)."),
            new ExampleEntry("Placemarks",              BASE + "Placemarks",           "Placemark icons at globe positions with configurable labels."),
            new ExampleEntry("Placemark Decluttering",  BASE + "PlacemarkDecluttering","Automatic decluttering of dense placemark layers for readability."),
            new ExampleEntry("Placemark Label Editing", BASE + "PlacemarkLabelEditing","In-place label editing on picked placemarks."),
            new ExampleEntry("Markers",                 BASE + "Markers",              "Simple 3D marker shapes positioned at globe coordinates."),
            new ExampleEntry("Markers Order",           BASE + "MarkersOrder",         "Control marker draw order and overlap handling."),
            new ExampleEntry("Keeping Objects In View", BASE + "KeepingObjectsInView", "Auto-pan and zoom the view to keep a set of shapes visible."),
            new ExampleEntry("GPS Tracks",              BASE + "GPSTracks",            "Animated GPS track playback with position interpolation."),
            new ExampleEntry("Coordinate Search",       BASE + "CoordinateSearchExample","Search for places via Nominatim (OSM) geocoder and fly to results.", "New"),
        });

        CATEGORIES.put("Military", new ExampleEntry[]{
            new ExampleEntry("Symbology",                BASE + "symbology.Symbology",              "MIL-STD-2525 tactical symbol rendering overview."),
            new ExampleEntry("Tactical Symbols",         BASE + "symbology.TacticalSymbols",        "Full MIL-STD-2525 symbol set with modifier text fields."),
            new ExampleEntry("Tactical Graphics",        BASE + "symbology.TacticalGraphics",       "MIL-STD-2525 tactical graphic overlays (phase lines, boundaries)."),
            new ExampleEntry("Declutter Tactical Symbols",BASE + "symbology.DeclutterTacticalSymbols","Automatic decluttering of dense tactical symbol layers."),
        });

        CATEGORIES.put("Advanced / GPU", new ExampleEntry[]{
            new ExampleEntry("Analytic Surface Demo",  BASE + "analytics.AnalyticSurfaceDemo",  "Color-mapped analytic surface rendered from 2D numeric data grids."),
            new ExampleEntry("Measure Tool",           BASE + "MeasureToolUsage",               "Interactive measurement tool with per-segment distances, cumulative totals, bearings, elevation, terrain profile, multi-tab support, and clipboard export.", "New"),
            new ExampleEntry("Lines Of Sight",         BASE + "lineofsight.LinesOfSight",       "Compute terrain line-of-sight coverage for an observer position."),
            new ExampleEntry("Grid Of Points",         BASE + "lineofsight.GridOfPoints",       "Sample terrain intersection for a grid of observer positions."),
            new ExampleEntry("Flat World",             BASE + "FlatWorld",                      "Flat (2D projected) globe with selectable map projections."),
            new ExampleEntry("Flat World Earthquakes", BASE + "FlatWorldEarthquakes",           "Live USGS earthquake feed rendered on a flat-projected globe."),
            new ExampleEntry("Animated Globe",         BASE + "AnimatedGlobe",                  "Smooth globe-rotation animation loop using the JOGL Animator."),
            new ExampleEntry("Graticule",              BASE + "Graticule",                      "Latitude/longitude graticule grid overlay with style controls."),
            new ExampleEntry("GARS Graticule",         BASE + "GARSGraticule",                  "Global Airspace Reference System (GARS) cell graticule."),
            new ExampleEntry("MGRS Graticule",         BASE + "MGRSGraticule",                  "Military Grid Reference System (MGRS) graticule overlay."),
            new ExampleEntry("Dim Globe Surface",      BASE + "DimGlobeSurface",                "Darken the globe surface to increase overlay contrast."),
            new ExampleEntry("View Controls",          BASE + "ViewControls",                   "On-screen pan, zoom, tilt, and heading view control widget."),
            new ExampleEntry("View Iteration",         BASE + "ViewIteration",                  "Programmatic camera animation along a pre-defined path."),
            new ExampleEntry("View Limits",            BASE + "ViewLimits",                     "Constrain the camera to a geographic bounding box and altitude range."),
            new ExampleEntry("View Look Around",       BASE + "ViewLookAround",                 "First-person look-around navigation mode."),
            new ExampleEntry("Detail Hints",           BASE + "DetailHints",                    "Adjust level-of-detail hints for terrain tessellation and shapes."),
            new ExampleEntry("Multi Frame",            BASE + "multiwindow.MultiFrame",         "Multiple independent globe windows sharing a single WorldWind instance."),
            new ExampleEntry("Flat And Round Globes",  BASE + "multiwindow.FlatAndRoundGlobes", "Side-by-side flat-projected and round globe in separate windows."),
            new ExampleEntry("Shared Shapes",          BASE + "multiwindow.SharedShapes",       "Share rendered layer data across multiple WorldWindow instances."),
            new ExampleEntry("Tabbed Pane Usage",      BASE + "multiwindow.TabbedPaneUsage",    "Multiple globes hosted in a JTabbedPane."),
            new ExampleEntry("Card Layout Usage",      BASE + "multiwindow.CardLayoutUsage",    "Multiple globes swapped via CardLayout."),
            new ExampleEntry("Export Image / Elevations",BASE + "ExportImageOrElevations",      "Export the globe view or elevation data to a georeferenced image file."),
            new ExampleEntry("Persist Session State",  BASE + "PersistSessionState",            "Save and restore camera position and layer state between sessions."),
        });
    }

    // ── UI fields ────────────────────────────────────────────────────────────

    private final DefaultTreeModel treeModel;
    private final JTree categoryTree;
    private final DefaultListModel<ExampleEntry> listModel = new DefaultListModel<>();
    private final JList<ExampleEntry> exampleList;
    private final JLabel detailName;
    private final JLabel detailBadge;
    private final JEditorPane detailDescription;
    private final JButton launchButton;

    // ── Constructor ──────────────────────────────────────────────────────────

    public ExamplesIndex() {
        super("WorldWind Reforged — Examples");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        getContentPane().setBackground(WWStyle.BG_BASE);

        // ── Category tree ────────────────────────────────────────────────────
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Examples");
        for (String category : CATEGORIES.keySet()) {
            root.add(new DefaultMutableTreeNode(category));
        }
        treeModel = new DefaultTreeModel(root);
        categoryTree = new JTree(treeModel);
        categoryTree.setRootVisible(false);
        categoryTree.setShowsRootHandles(false);
        categoryTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        categoryTree.setBackground(WWStyle.BG_DARK);
        categoryTree.setForeground(WWStyle.FG_PRIMARY);
        categoryTree.setFont(WWStyle.FONT_BASE);
        categoryTree.setBorder(WWStyle.padded(WWStyle.GAP_XS, WWStyle.GAP_S));
        categoryTree.putClientProperty("JTree.lineStyle", "None");

        DefaultTreeCellRenderer cellRenderer = new DefaultTreeCellRenderer();
        cellRenderer.setBackground(WWStyle.BG_DARK);
        cellRenderer.setBackgroundNonSelectionColor(WWStyle.BG_DARK);
        cellRenderer.setBackgroundSelectionColor(WWStyle.BG_SELECTED);
        cellRenderer.setTextNonSelectionColor(WWStyle.FG_PRIMARY);
        cellRenderer.setTextSelectionColor(WWStyle.FG_PRIMARY);
        cellRenderer.setBorderSelectionColor(null);
        cellRenderer.setLeafIcon(null);
        cellRenderer.setOpenIcon(null);
        cellRenderer.setClosedIcon(null);
        cellRenderer.setFont(WWStyle.FONT_BASE);
        categoryTree.setCellRenderer(cellRenderer);

        // Select first category by default
        categoryTree.setSelectionRow(0);

        // ── Example list ─────────────────────────────────────────────────────
        exampleList = new JList<>(listModel);
        exampleList.setBackground(WWStyle.BG_PANEL);
        exampleList.setForeground(WWStyle.FG_PRIMARY);
        exampleList.setFont(WWStyle.FONT_BASE);
        exampleList.setSelectionBackground(WWStyle.BG_SELECTED);
        exampleList.setSelectionForeground(WWStyle.FG_PRIMARY);
        exampleList.setCellRenderer(new ExampleCellRenderer());
        exampleList.setFixedCellHeight(26);

        // ── Detail panel ─────────────────────────────────────────────────────
        detailName = WWStyle.heading("");
        detailName.setBorder(BorderFactory.createEmptyBorder(0, 0, WWStyle.GAP_XS, 0));

        detailBadge = new JLabel();
        detailBadge.setFont(WWStyle.FONT_BOLD);
        detailBadge.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(WWStyle.BORDER, 1),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)));

        detailDescription = new JEditorPane();
        detailDescription.setEditable(false);
        detailDescription.setContentType("text/html");
        detailDescription.setBackground(WWStyle.BG_PANEL);
        detailDescription.setForeground(WWStyle.FG_SECONDARY);
        detailDescription.putClientProperty(javax.swing.JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        detailDescription.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        launchButton = WWStyle.accentButton("Launch");
        launchButton.setEnabled(false);
        launchButton.setPreferredSize(new Dimension(100, 32));

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, WWStyle.GAP_XS, 0));
        badgeRow.setBackground(WWStyle.BG_PANEL);
        badgeRow.add(detailBadge);

        JPanel detailTop = new JPanel(new BorderLayout(WWStyle.GAP_XS, WWStyle.GAP_XS));
        detailTop.setBackground(WWStyle.BG_PANEL);
        detailTop.add(detailName, BorderLayout.WEST);
        detailTop.add(badgeRow, BorderLayout.EAST);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonRow.setBackground(WWStyle.BG_PANEL);
        buttonRow.add(launchButton);

        JScrollPane detailScroll = new JScrollPane(detailDescription);
        detailScroll.setBorder(null);
        detailScroll.getViewport().setBackground(WWStyle.BG_PANEL);
        detailScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel detailPanel = new JPanel(new BorderLayout(0, WWStyle.GAP_S));
        detailPanel.setBackground(WWStyle.BG_PANEL);
        detailPanel.setBorder(WWStyle.padded(WWStyle.GAP_M, WWStyle.GAP_M));
        detailPanel.add(detailTop, BorderLayout.NORTH);
        detailPanel.add(detailScroll, BorderLayout.CENTER);
        detailPanel.add(buttonRow, BorderLayout.SOUTH);

        // ── Header ───────────────────────────────────────────────────────────
        JLabel title = WWStyle.heading("WorldWind Reforged");
        title.setFont(new Font(title.getFont().getFamily(), Font.BOLD, 16));
        title.setForeground(WWStyle.ACCENT);

        JLabel subtitle = WWStyle.label("Example Browser — double-click an example to launch", false);

        JPanel header = new JPanel(new BorderLayout(WWStyle.GAP_S, 0));
        header.setBackground(WWStyle.BG_BASE);
        header.setBorder(new CompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, WWStyle.BORDER),
            BorderFactory.createEmptyBorder(WWStyle.GAP_S, WWStyle.GAP_M, WWStyle.GAP_S, WWStyle.GAP_M)));
        header.add(title, BorderLayout.WEST);
        header.add(subtitle, BorderLayout.EAST);

        // ── Category panel ───────────────────────────────────────────────────
        JLabel catLabel = WWStyle.label("Categories");
        catLabel.setFont(WWStyle.FONT_SMALL);
        catLabel.setForeground(WWStyle.FG_DISABLED);
        catLabel.setBorder(BorderFactory.createEmptyBorder(WWStyle.GAP_S, WWStyle.GAP_S, WWStyle.GAP_XS, WWStyle.GAP_S));

        JScrollPane treeScroll = WWStyle.scrollPane(categoryTree);
        treeScroll.setPreferredSize(new Dimension(190, 0));

        JPanel categoryPanel = new JPanel(new BorderLayout());
        categoryPanel.setBackground(WWStyle.BG_DARK);
        categoryPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, WWStyle.BORDER));
        categoryPanel.add(catLabel, BorderLayout.NORTH);
        categoryPanel.add(treeScroll, BorderLayout.CENTER);

        // ── Example list panel ───────────────────────────────────────────────
        JScrollPane listScroll = WWStyle.scrollPane(exampleList);

        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setBackground(WWStyle.BG_PANEL);
        listPanel.add(listScroll, BorderLayout.CENTER);

        // ── Right split: list (top) + detail (bottom) ────────────────────────
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listPanel, detailPanel);
        rightSplit.setDividerLocation(400);
        rightSplit.setResizeWeight(0.65);
        rightSplit.setDividerSize(4);
        rightSplit.setBorder(null);
        rightSplit.setBackground(WWStyle.BG_BASE);

        // ── Main split: categories (left) + right split ──────────────────────
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, categoryPanel, rightSplit);
        mainSplit.setDividerLocation(190);
        mainSplit.setDividerSize(4);
        mainSplit.setBorder(null);
        mainSplit.setBackground(WWStyle.BG_BASE);

        // ── Root layout ──────────────────────────────────────────────────────
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(header, BorderLayout.NORTH);
        getContentPane().add(mainSplit, BorderLayout.CENTER);

        // ── Listeners ────────────────────────────────────────────────────────
        categoryTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) categoryTree.getLastSelectedPathComponent();
            if (node != null && !node.isRoot()) {
                String category = node.getUserObject().toString();
                populateList(category);
            }
        });

        exampleList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                ExampleEntry entry = exampleList.getSelectedValue();
                showDetail(entry);
            }
        });

        exampleList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    ExampleEntry entry = exampleList.getSelectedValue();
                    if (entry != null) launch(entry);
                }
            }
        });

        launchButton.addActionListener(e -> {
            ExampleEntry entry = exampleList.getSelectedValue();
            if (entry != null) launch(entry);
        });

        // populate with first category
        populateList(CATEGORIES.keySet().iterator().next());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void populateList(String category) {
        listModel.clear();
        ExampleEntry[] entries = CATEGORIES.get(category);
        if (entries != null) {
            for (ExampleEntry e : entries) listModel.addElement(e);
        }
        exampleList.clearSelection();
        showDetail(null);
    }

    private void showDetail(ExampleEntry entry) {
        if (entry == null) {
            detailName.setText("");
            detailBadge.setText("");
            detailBadge.setVisible(false);
            detailDescription.setText(ExampleDocs.getWelcomeHtml());
            detailDescription.setCaretPosition(0);
            launchButton.setEnabled(false);
        } else {
            detailName.setText(entry.displayName);
            String html = ExampleDocs.getHtml(entry.className);
            detailDescription.setText(html != null ? html : ExampleDocs.fallback(entry.description));
            detailDescription.setCaretPosition(0);
            if (entry.badge != null) {
                detailBadge.setText(" " + entry.badge + " ");
                detailBadge.setForeground(badgeColor(entry.badge));
                detailBadge.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(badgeColor(entry.badge), 1),
                    BorderFactory.createEmptyBorder(2, 6, 2, 6)));
                detailBadge.setVisible(true);
            } else {
                detailBadge.setVisible(false);
            }
            launchButton.setEnabled(true);
        }
    }

    private static Color badgeColor(String badge) {
        if (badge == null) return WWStyle.FG_DISABLED;
        switch (badge) {
            case "New":  return WWStyle.STATUS_OK;
            case "Ph2":  return WWStyle.ACCENT;
            case "Ph3":  return new Color(180, 100, 220);
            case "Ph4":  return WWStyle.STATUS_WARN;
            default:     return WWStyle.FG_SECONDARY;
        }
    }

    private void launch(ExampleEntry entry) {
        launchButton.setEnabled(false);
        launchButton.setText("Launching…");

        new Thread(() -> {
            try {
                Class<?> cls = Class.forName(entry.className);
                Method main = cls.getMethod("main", String[].class);
                main.invoke(null, (Object) new String[0]);
            } catch (ClassNotFoundException ex) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this,
                        "Class not found: " + entry.className + "\n\nThe example may not be compiled.",
                        "Launch Failed", JOptionPane.ERROR_MESSAGE));
            } catch (NoSuchMethodException ex) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this,
                        entry.displayName + " does not have a main() method.",
                        "Launch Failed", JOptionPane.ERROR_MESSAGE));
            } catch (Exception ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this,
                        "Error launching " + entry.displayName + ":\n" + cause.getMessage(),
                        "Launch Failed", JOptionPane.ERROR_MESSAGE));
            } finally {
                SwingUtilities.invokeLater(() -> {
                    launchButton.setEnabled(true);
                    launchButton.setText("Launch");
                });
            }
        }, "Launch-" + entry.displayName).start();
    }

    // ── Custom cell renderer ─────────────────────────────────────────────────

    private static final class ExampleCellRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            ExampleEntry entry = (ExampleEntry) value;

            setFont(WWStyle.FONT_BASE);
            setBorder(BorderFactory.createEmptyBorder(0, WWStyle.GAP_S, 0, WWStyle.GAP_S));

            if (isSelected) {
                setBackground(WWStyle.BG_SELECTED);
                setForeground(WWStyle.FG_PRIMARY);
            } else {
                setBackground(index % 2 == 0 ? WWStyle.BG_PANEL : WWStyle.BG_FIELD);
                setForeground(WWStyle.FG_PRIMARY);
            }

            if (entry.badge != null) {
                setText(entry.displayName + "  \u25CF");  // bullet after name
                setForeground(isSelected ? WWStyle.FG_PRIMARY : WWStyle.FG_PRIMARY);
                // show badge color via foreground on the bullet... simplest approach:
                // Build an HTML label instead for badge indicator
                String badgeHex = String.format("#%02x%02x%02x",
                    badgeColor(entry.badge).getRed(),
                    badgeColor(entry.badge).getGreen(),
                    badgeColor(entry.badge).getBlue());
                setText("<html>" + escapeHtml(entry.displayName)
                    + "&nbsp;&nbsp;<font color='" + badgeHex + "'><b>" + entry.badge + "</b></font></html>");
            } else {
                setText(entry.displayName);
            }

            return this;
        }

        private static String escapeHtml(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private static Color badgeColor(String badge) {
            return ExamplesIndex.badgeColor(badge);
        }
    }

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) {
        // Signal ApplicationTemplate.start() to use DISPOSE_ON_CLOSE instead of EXIT_ON_CLOSE
        // so that closing an individual example does not terminate the launcher JVM.
        System.setProperty("gov.nasa.worldwind.examplesLauncher", "true");

        SwingUtilities.invokeLater(() -> {
            try {
                FlatDarkLaf.setup();
            } catch (Exception ignored) {
                try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
                catch (Exception e) { /* best-effort */ }
            }
            UIManager.put("Panel.background", WWStyle.BG_DARK);
            UIManager.put("ScrollBar.width", 8);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.thumbColor", WWStyle.BG_HOVER);
            UIManager.put("ScrollBar.trackColor", WWStyle.BG_PANEL);
            UIManager.put("Component.focusColor", WWStyle.ACCENT);
            UIManager.put("Component.borderColor", WWStyle.BORDER);

            new ExamplesIndex().setVisible(true);
        });
    }
}
