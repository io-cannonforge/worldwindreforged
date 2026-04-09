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
 * ExampleDocs.java: comprehensive developer documentation for all WorldWind examples.
 */
package gov.nasa.worldwindx.examples.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides rich HTML documentation for each example in the ExamplesIndex.
 * <p>
 * Each entry maps a fully-qualified class name to a styled HTML string with
 * four sections: Overview, What You'll See, How to Interact, and Key APIs.
 * The HTML uses inline CSS matched to the {@link WWStyle} dark-theme palette.
 * </p>
 *
 * @author seaglassfoundry.com
 */
public final class ExampleDocs {

    private ExampleDocs() {}

    private static final String BASE = "gov.nasa.worldwindx.examples.";

    // CSS matched to WWStyle dark palette
    private static final String CSS =
        "<style>"
        + "body { font-family: 'Segoe UI', 'SF Pro Text', sans-serif; font-size: 11px; "
        + "  color: #a0a0a0; background: #3c3f41; margin: 0; padding: 4px; }"
        + "h2 { color: #dcdcdc; font-size: 12px; margin: 10px 0 4px 0; padding-bottom: 3px; "
        + "  border-bottom: 1px solid #505355; }"
        + "h2:first-child { margin-top: 2px; }"
        + "ul { margin: 2px 0 6px 16px; padding: 0; }"
        + "li { margin-bottom: 3px; }"
        + "code { color: #dcdcdc; background: #454749; padding: 1px 4px; font-size: 11px; }"
        + "p { margin: 2px 0 6px 0; line-height: 1.5; }"
        + ".api-class { color: #7ab0df; }"
        + ".api-method { color: #b5cea8; }"
        + ".note { color: #e6b432; font-style: italic; }"
        + "</style>";

    private static final Map<String, String> DOCS = new HashMap<>();

    static {
        // ── Basics ──────────────────────────────────────────────────────────────

        put("SimplestPossibleExample",
            section("Overview",
                "The absolute minimal WorldWind application &mdash; the \"Hello World\" of 3D globes. "
                + "This example creates a <code class='api-class'>WorldWindowGLCanvas</code> (1000&times;800), "
                + "assigns a <code class='api-class'>BasicModel</code> with all default layers, and drops it into a "
                + "<code>JFrame</code> using <code>BorderLayout.CENTER</code>. No explicit layers, no control panels, "
                + "no overlays &mdash; just the bare minimum to get the globe on screen. Use this as your starting "
                + "template when building any new WorldWind application. The pattern is: extend <code>JFrame</code>, "
                + "create the canvas, set the model, <code>pack()</code>, and show. The example uses "
                + "<code>EventQueue.invokeLater()</code> for Swing thread safety.")
            + section("What You'll See",
                "<ul>"
                + "<li>A single 1000&times;800 pixel window with an interactive 3D globe filling the entire frame</li>"
                + "<li>Default Blue Marble (BMNG) and Landsat imagery draped on the Earth via <code>BasicModel</code></li>"
                + "<li>No sidebars, control panels, toolbars, or overlays &mdash; just the raw globe</li>"
                + "<li>Standard default layers: stars, atmosphere, Blue Marble, Landsat, compass, scale bar, world map</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li><b>Left-drag</b> &mdash; rotate the globe around its centre</li>"
                + "<li><b>Right-drag</b> or <b>scroll wheel</b> &mdash; zoom in and out</li>"
                + "<li><b>Middle-drag</b> &mdash; tilt and rotate the camera (pitch and heading)</li>"
                + "<li>No additional controls or menus are provided in this minimal example</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>WorldWindowGLCanvas</code> &mdash; the core JOGL canvas that renders the globe. "
                + "Extends <code>GLCanvas</code>; add it to any Swing container like any heavyweight component.</li>"
                + "<li><code class='api-class'>BasicModel</code> &mdash; default model that bundles the Earth globe with a standard "
                + "layer list (imagery, compass, scale bar, etc.). Calling <code>new BasicModel()</code> with no "
                + "arguments loads configuration from <code>worldwind.xml</code>.</li>"
                + "<li><code class='api-method'>wwd.setModel(new BasicModel())</code> &mdash; attaches the data model to the canvas; "
                + "this must be called before the frame is made visible.</li>"
                + "<li><b>Launcher detection:</b> <code>Boolean.getBoolean(\"gov.nasa.worldwind.examplesLauncher\")</code> "
                + "controls whether the frame uses <code>DISPOSE_ON_CLOSE</code> (when launched from the examples index) "
                + "or <code>EXIT_ON_CLOSE</code> (when run standalone).</li>"
                + "<li><b>Pattern:</b> This is the foundation of every WorldWind app. All other examples extend this "
                + "pattern with additional layers, shapes, and event handlers.</li>"
                + "</ul>"));

        put("HelloWorldWind",
            section("Overview",
                "A basic WorldWind application that demonstrates the standard window setup with macOS integration. "
                + "Like <code>SimplestPossibleExample</code> but adds platform-specific initialisation: it sets "
                + "<code>com.apple.mrj.application.apple.menu.about.name</code> to \"WorldWind\" <em>before</em> any Swing "
                + "classes are loaded, ensuring the macOS menu bar shows the correct application name. Uses an inner "
                + "<code>AppFrame</code> class so that the system property is set in the static initializer before "
                + "Swing is touched. Checks <code>Configuration.isMacOS()</code> for platform detection.")
            + section("What You'll See",
                "<ul>"
                + "<li>A 3D globe with Blue Marble and Landsat imagery, identical in appearance to SimplestPossibleExample</li>"
                + "<li>On macOS: the application menu shows \"WorldWind\" instead of the class name</li>"
                + "<li>Standard navigation via mouse controls with all default layers visible</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li><b>Left-drag</b> &mdash; pan the globe</li>"
                + "<li><b>Scroll wheel</b> &mdash; zoom in and out</li>"
                + "<li><b>Middle-drag</b> &mdash; tilt and rotate the camera</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>WorldWindowGLCanvas</code> &mdash; the OpenGL canvas widget</li>"
                + "<li><code class='api-class'>BasicModel</code> &mdash; default Earth model with standard layers</li>"
                + "<li><code class='api-method'>Configuration.isMacOS()</code> &mdash; returns <code>true</code> on macOS for "
                + "platform-specific initialisation</li>"
                + "<li><b>Gotcha:</b> The Apple menu name property must be set <em>before</em> any AWT/Swing class is loaded. "
                + "Use a static initializer block or set it in <code>main()</code> before calling any Swing methods.</li>"
                + "</ul>"));

        put("Stereo",
            section("Overview",
                "Demonstrates stereoscopic 3D rendering modes including red/blue anaglyph and hardware "
                + "quad-buffered stereo. The stereo mode is controlled by the system property "
                + "<code>gov.nasa.worldwind.stereo.mode</code> which must be set to <code>\"redblue\"</code> or "
                + "<code>\"device\"</code> <em>before</em> the GL context is initialised. The example starts with an "
                + "initial view at Mt Rainier (46.7045&deg;N, -121.6242&deg;W) at 10,000m altitude, heading 342&deg;, "
                + "pitch 80&deg; &mdash; a dramatic mountain view that showcases the stereo depth effect.")
            + section("What You'll See",
                "<ul>"
                + "<li>In <b>redblue</b> mode: the globe rendered with red/cyan colour separation (use anaglyph 3D glasses)</li>"
                + "<li>In <b>device</b> mode: quad-buffered stereo for compatible hardware (3D monitors, VR headsets)</li>"
                + "<li>Initial camera positioned at Mt Rainier with a steep 80&deg; pitch, showing dramatic terrain depth</li>"
                + "<li>The stereo effect is most pronounced when tilted close to the terrain surface</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Standard globe navigation (drag, zoom, tilt) &mdash; stereo effect updates in real-time</li>"
                + "<li>Tilt the view near the surface (high pitch angle) for the strongest depth perception</li>"
                + "<li>To change stereo mode, pass <code>-Dgov.nasa.worldwind.stereo.mode=redblue</code> on the command line, "
                + "or set the property programmatically before GL context creation</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>StereoOptionSceneController</code> &mdash; scene controller that renders separate "
                + "left/right eye views with configurable eye separation</li>"
                + "<li><code class='api-method'>System.setProperty(\"gov.nasa.worldwind.stereo.mode\", \"redblue\")</code> &mdash; "
                + "must be called before the GL context is initialised; cannot be changed at runtime</li>"
                + "<li><code>AVKey.STEREO_MODE</code> &mdash; configuration key for stereo mode (\"redblue\", \"device\", or none)</li>"
                + "<li><b>Gotcha:</b> The stereo mode property is read during GL context initialisation. Setting it after "
                + "the WorldWindow is created has no effect.</li>"
                + "</ul>"));

        put("OpenGLSupportTest",
            section("Overview",
                "Probes the system's OpenGL driver to verify it supports all features WorldWind requires. "
                + "Run this first on any new machine to confirm hardware compatibility. The test creates a "
                + "hidden 200&times;200 <code>GLCanvas</code> with a <code>GLEventListener</code> that queries "
                + "capabilities in its <code>init()</code> callback. It requests a hardware-accelerated profile via "
                + "<code>GLProfile.getMaxFixedFunc(true)</code> with <code>GLCapabilities</code> set to 8-bit RGBA, "
                + "24-bit depth, and double buffered.")
            + section("What You'll See",
                "<ul>"
                + "<li>A dialog reporting whether all required OpenGL functions and extensions are available</li>"
                + "<li>Required functions: <code>glActiveTexture</code>, <code>glClientActiveTexture</code></li>"
                + "<li>Required extension: <code>GL_EXT_texture_compression_s3tc</code></li>"
                + "<li>Validates RGBA8888 colour depth, depth buffer &ge;16 bits, and double buffering</li>"
                + "<li>When launched from the examples index, shows a <code>JOptionPane</code> dialog; "
                + "when run standalone, prints to console and calls <code>System.exit()</code></li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>No interaction needed &mdash; results appear automatically</li>"
                + "<li>Green checkmarks indicate passed tests; warnings list any missing capabilities</li>"
                + "<li>If tests fail, your GPU driver may need updating or WorldWind may not run correctly</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>GLEventListener</code> &mdash; JOGL callback interface; the <code>init(GLAutoDrawable)</code> "
                + "method is the earliest point you can query GL capabilities</li>"
                + "<li><code class='api-method'>GL.isFunctionAvailable(\"glActiveTexture\")</code> &mdash; tests whether a specific "
                + "OpenGL function exists in the current driver</li>"
                + "<li><code class='api-method'>GL.isExtensionAvailable(\"GL_EXT_texture_compression_s3tc\")</code> &mdash; tests for "
                + "required GL extensions</li>"
                + "<li><code class='api-class'>GLProfile.getMaxFixedFunc(true)</code> &mdash; requests the highest available "
                + "fixed-function profile with hardware acceleration</li>"
                + "<li><code class='api-class'>GLCapabilities</code> &mdash; request specific pixel format: "
                + "<code>setRedBits(8)</code>, <code>setDepthBits(24)</code>, <code>setDoubleBuffered(true)</code></li>"
                + "</ul>"));

        put("WorldWindDiagnostics",
            section("Overview",
                "Generates a comprehensive system diagnostic report covering Java VM, OS, OpenGL driver, "
                + "WorldWind configuration, JOGL version, and available memory. Invaluable for troubleshooting "
                + "rendering issues or filing bug reports. The example creates two windows: a <code>MainFrame</code> "
                + "with a scrollable text report, and a hidden 200&times;200 <code>GLFrame</code> that probes GL "
                + "capabilities. A \"Re-run Test\" button refreshes all diagnostics.")
            + section("What You'll See",
                "<ul>"
                + "<li>A scrollable text report with all system properties and WorldWind version</li>"
                + "<li>OpenGL info: <code>GL_VERSION</code>, <code>GL_VENDOR</code>, <code>GL_RENDERER</code>, "
                + "<code>GL_EXTENSIONS</code></li>"
                + "<li>GL capabilities: <code>GL_STENCIL_BITS</code>, <code>GL_DEPTH_BITS</code>, "
                + "<code>GL_MAX_TEXTURE_UNITS</code>, <code>GL_MAX_TEXTURE_SIZE</code>, "
                + "<code>GL_MAX_ELEMENTS_INDICES</code>, <code>GL_MAX_ELEMENTS_VERTICES</code>, "
                + "<code>GL_MAX_LIGHTS</code>, <code>GL_LINE_WIDTH_RANGE</code></li>"
                + "<li>Runtime memory stats: heap size, max heap, free memory</li>"
                + "<li>JOGL specification and implementation version/vendor</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Scroll through the report to find relevant diagnostic sections</li>"
                + "<li>Copy text from the report for bug reports or support requests</li>"
                + "<li>Click <b>Re-run Test</b> to refresh all diagnostics (useful after driver updates)</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>WorldWind</code> &mdash; static accessors for version, data store path, configuration</li>"
                + "<li><code class='api-method'>GL.glGetString(GL_RENDERER)</code> &mdash; GPU identification string</li>"
                + "<li><code class='api-method'>GL.glGetString(GL_VERSION)</code> &mdash; OpenGL version string</li>"
                + "<li><code class='api-class'>Configuration</code> &mdash; WorldWind's property-based configuration system</li>"
                + "<li><code class='api-method'>Runtime.getRuntime().totalMemory()</code>, <code>maxMemory()</code>, "
                + "<code>freeMemory()</code> &mdash; JVM memory statistics</li>"
                + "</ul>"));

        put("ConfiguringGLRuntimeCapabilities",
            section("Overview",
                "Shows how to configure OpenGL runtime parameters before WorldWind initialises by creating a "
                + "custom <code>WorldWindowGLAutoDrawable</code> subclass. The key technique is setting "
                + "<code>Configuration.setValue(AVKey.WORLD_WINDOW_CLASS_NAME, className)</code> before the WorldWindow "
                + "is created, then overriding <code>init(GLAutoDrawable)</code> to call <code>super.init()</code> "
                + "followed by your custom configuration in <code>configureGLRuntimeCaps()</code>. This allows you to "
                + "enable or disable framebuffer objects, control multisample settings, and more.")
            + section("What You'll See",
                "<ul>"
                + "<li>A globe window with the configured GL capabilities active</li>"
                + "<li>Visual difference depends on settings (e.g. anti-aliased edges with multisampling, "
                + "or sharper rendering with FBOs disabled)</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Standard globe navigation &mdash; the visual effect of your GL configuration is passive</li>"
                + "<li>Compare visual quality against default settings to see the effect of your changes</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-method'>Configuration.setValue(AVKey.WORLD_WINDOW_CLASS_NAME, className)</code> &mdash; "
                + "must be called before any WorldWindow is created; tells WorldWind to instantiate your custom subclass</li>"
                + "<li>Override <code class='api-method'>init(GLAutoDrawable)</code> &rarr; call <code>super.init()</code> &rarr; "
                + "then configure via <code>getSceneController().getGLRuntimeCapabilities()</code></li>"
                + "<li><code class='api-method'>GLRuntimeCapabilities.setFramebufferObjectEnabled(boolean)</code> &mdash; "
                + "enable or disable FBO rendering</li>"
                + "<li><b>Gotcha:</b> You must check for <code>null</code> on both <code>getSceneController()</code> and "
                + "<code>getGLRuntimeCapabilities()</code> since they may not be initialized during early init stages.</li>"
                + "</ul>"));

        put("LoggingControl",
            section("Overview",
                "Demonstrates how to control WorldWind's logging output using the standard "
                + "<code>java.util.logging</code> framework. All WorldWind messages go through the logger named "
                + "<code>\"gov.nasa.worldwind\"</code>. This example shows how to adjust verbosity at runtime, "
                + "create a custom <code>ConsoleHandler</code> subclass, and prevent duplicate output by calling "
                + "<code>setUseParentHandlers(false)</code>.")
            + section("What You'll See",
                "<ul>"
                + "<li>A globe with a control panel to change the logging level</li>"
                + "<li>Console output changes in real-time as you adjust the level</li>"
                + "<li>Level options include: <code>ALL</code>, <code>FINE</code>, <code>INFO</code>, "
                + "<code>WARNING</code>, <code>SEVERE</code></li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Use the control panel to switch between logging levels</li>"
                + "<li>Watch the console for more or less verbose output as you change levels</li>"
                + "<li>At <code>FINE</code> level, you'll see tile loading, cache hits, and rendering details</li>"
                + "<li>At <code>WARNING</code>, only significant issues are shown</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-method'>Logger.getLogger(\"gov.nasa.worldwind\")</code> &mdash; the root logger for all "
                + "WorldWind messages</li>"
                + "<li><code class='api-method'>logger.setLevel(Level.FINE)</code> &mdash; set verbosity; constants include "
                + "<code>ALL</code>, <code>FINE</code>, <code>INFO</code>, <code>WARNING</code>, <code>SEVERE</code></li>"
                + "<li><code class='api-method'>logger.setUseParentHandlers(false)</code> &mdash; prevents duplicate output to "
                + "the root logger's handler</li>"
                + "<li>Create a custom <code class='api-class'>ConsoleHandler</code> subclass and override "
                + "<code>publish(LogRecord)</code> to format or filter log messages</li>"
                + "</ul>"));

        put("CacheLocationConfiguration",
            section("Overview",
                "Shows how to redirect WorldWind's tile cache to a custom directory. By default WorldWind "
                + "caches downloaded imagery and elevation tiles under the user's home directory. This example "
                + "demonstrates pointing the cache to an alternate path by setting "
                + "<code>Configuration.setValue(\"gov.nasa.worldwind.avkey.DataFileStoreConfigurationFileName\", path)</code> "
                + "where <code>path</code> is an XML configuration file on the classpath. This must be set "
                + "<em>before</em> <code>ApplicationTemplate.start()</code> is called.")
            + section("What You'll See",
                "<ul>"
                + "<li>A globe that caches imagery tiles in the specified custom directory instead of the default location</li>"
                + "<li>Console messages confirming the active cache location on startup</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Standard globe navigation &mdash; tiles are cached to the custom directory as you browse</li>"
                + "<li>Check the custom directory on disk to verify cached tile files appear as you navigate</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-method'>Configuration.setValue(\"gov.nasa.worldwind.avkey.DataFileStoreConfigurationFileName\", "
                + "\"path/to/config.xml\")</code> &mdash; redirect cache; path is relative to classpath</li>"
                + "<li><code class='api-class'>BasicDataFileStore</code> &mdash; WorldWind's disk cache implementation; "
                + "reads the XML config to determine write and read locations</li>"
                + "<li><b>Gotcha:</b> Configuration must be set <em>before</em> <code>WorldWind.getInstance()</code> is "
                + "called (which happens inside <code>ApplicationTemplate.start()</code>). Setting it after has no effect.</li>"
                + "</ul>"));

        put("ScreenShots",
            section("Overview",
                "Demonstrates how to capture the WorldWind canvas to a PNG image file. Adds a "
                + "<b>File &rarr; Save Snapshot...</b> menu item that uses <code class='api-class'>ScreenShotAction</code> "
                + "to grab the current frame buffer and save it via a file chooser dialog. The static initialiser "
                + "calls <code>ToolTipManager.setLightWeightPopupEnabled(false)</code> and "
                + "<code>JPopupMenu.setDefaultLightWeightPopupEnabled(false)</code> because "
                + "<code>WorldWindowGLCanvas</code> is a heavyweight AWT component and lightweight popups "
                + "would render behind it.")
            + section("What You'll See",
                "<ul>"
                + "<li>A globe window with a <b>File</b> menu in the menu bar</li>"
                + "<li>The File menu contains a <b>Save Snapshot...</b> menu item</li>"
                + "<li>After saving, the screenshot appears at the chosen file location as a PNG</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate the globe to the desired view you want to capture</li>"
                + "<li>Click <b>File &rarr; Save Snapshot...</b> to open a file save dialog</li>"
                + "<li>Choose a filename and location; the current frame buffer is saved as a PNG</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ScreenShotAction</code> &mdash; action that captures the canvas frame buffer and "
                + "saves it to a user-chosen file</li>"
                + "<li><code class='api-method'>new ScreenShotAction(wwd)</code> &mdash; create an action bound to a specific "
                + "WorldWindow; attach it to any <code>JMenuItem</code> or <code>JButton</code></li>"
                + "<li><code class='api-method'>ToolTipManager.setLightWeightPopupEnabled(false)</code> &mdash; required "
                + "because WorldWindowGLCanvas is a heavyweight component; without this, tooltips and popups "
                + "render behind the canvas</li>"
                + "</ul>"));

        put("Shutdown",
            section("Overview",
                "Demonstrates proper WorldWind application shutdown and resource cleanup. Shows the "
                + "difference between per-window shutdown (<code>wwd.shutdown()</code>) and global shutdown "
                + "(<code>WorldWind.shutDown()</code>). The example provides three buttons: \"Shutdown Window\" "
                + "(stops rendering, releases GL for one window), \"Create Window\" (creates a new globe window), "
                + "and \"Shutdown WorldWind\" (global shutdown of all windows, connection pools, and caches). "
                + "After a global shutdown, new windows cannot be created.")
            + section("What You'll See",
                "<ul>"
                + "<li>A globe window with three control buttons</li>"
                + "<li><b>Shutdown Window</b> &mdash; the current window goes blank and stops rendering</li>"
                + "<li><b>Create Window</b> &mdash; a new globe window appears</li>"
                + "<li><b>Shutdown WorldWind</b> &mdash; all windows shut down; further window creation fails</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Click <b>Shutdown Window</b> to dispose the current WorldWindow (stops rendering, releases GL resources)</li>"
                + "<li>Click <b>Create Window</b> to create a new WorldWindow instance</li>"
                + "<li>Click <b>Shutdown WorldWind</b> to globally shut down the entire runtime</li>"
                + "<li>After global shutdown, attempting to create new windows will fail</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-method'>wwd.shutdown()</code> &mdash; per-window shutdown: stops the rendering loop, "
                + "releases OpenGL resources, and disconnects from the model</li>"
                + "<li><code class='api-method'>WorldWind.shutDown()</code> &mdash; global shutdown: shuts down all windows, "
                + "closes all network connection pools, and releases shared resources</li>"
                + "<li>Cast <code>WorldWindow</code> to <code>Component</code> for removal from the content pane: "
                + "<code>contentPane.remove((Component) wwd)</code></li>"
                + "<li><b>Gotcha:</b> After <code>WorldWind.shutDown()</code>, the WorldWind singleton is permanently "
                + "disabled. You cannot create new windows or re-initialise the runtime without restarting the JVM.</li>"
                + "</ul>"));

        // ── Surface Shapes ──────────────────────────────────────────────────────

        put("Shapes",
            section("Overview",
                "A comprehensive showcase of all 2D surface shape types and 3D paths rendered in a single scene "
                + "with a huge control panel for attribute manipulation. Demonstrates "
                + "<code class='api-class'>SurfaceCircle</code> (100km radius, 38&deg;N -105&deg;W), "
                + "<code class='api-class'>SurfaceEllipse</code> (100&times;90km), "
                + "<code class='api-class'>SurfaceSquare</code> (100km), "
                + "<code class='api-class'>SurfaceQuad</code> (100&times;60km), "
                + "<code class='api-class'>SurfaceSector</code>, and <code class='api-class'>SurfacePolygon</code>. "
                + "Also includes paths: short, long, incremental (1000m base + 5m per point), vertical, and "
                + "small-segment (100 segments). All shapes support interactive dragging via "
                + "<code>BasicDragger</code> and a <code>SelectListener</code>.")
            + section("What You'll See",
                "<ul>"
                + "<li>Multiple surface shape types scattered across the US, all conforming to terrain</li>"
                + "<li>Paths at various altitudes and configurations with visible stipple patterns</li>"
                + "<li>A <code>TextRenderer</code> displaying the current path length at screen position (100, 100)</li>"
                + "<li>Shapes with fill colours, outlines, and varying opacity levels</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li><b>Drag shapes</b> to reposition them on the globe (via <code>BasicDragger</code> + <code>SelectListener</code>)</li>"
                + "<li><b>Path Attributes panel:</b> Follow Terrain toggle, Conformance (1&ndash;50px), Subsegments, "
                + "Type combo (great circle/rhumb/linear), Style, Width (1&ndash;10), Colour, Opacity (0&ndash;10 range normalised to 0.0&ndash;1.0), "
                + "Offset (0&ndash;1,000,000m)</li>"
                + "<li><b>Surface Attributes panel:</b> Interior/Border Style, Opacity, Colour, Width</li>"
                + "<li>Stipple pattern <code>0xAAAA</code> with factor 5 for dashed lines</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>SurfaceCircle</code>, <code class='api-class'>SurfaceEllipse</code>, "
                + "<code class='api-class'>SurfaceSquare</code>, <code class='api-class'>SurfaceQuad</code>, "
                + "<code class='api-class'>SurfaceSector</code>, <code class='api-class'>SurfacePolygon</code> &mdash; "
                + "all terrain-conforming surface shapes</li>"
                + "<li><code class='api-class'>Path</code> &mdash; 3D line with altitude modes and stipple patterns</li>"
                + "<li><code class='api-class'>BasicShapeAttributes</code> &mdash; fill, stroke, opacity, stipple pattern/factor</li>"
                + "<li><code class='api-class'>BasicDragger</code> &mdash; add to select listeners for drag support: "
                + "<code>wwd.addSelectListener(new BasicDragger(wwd))</code></li>"
                + "<li><code class='api-class'>RenderableLayer</code> &mdash; container layer for adding shapes to the globe</li>"
                + "</ul>"));

        put("SurfaceShapes",
            section("Overview",
                "Demonstrates <code>SurfacePolygon</code>, <code>SurfacePolyline</code>, "
                + "<code>SurfaceCircle</code>, and related surface-conforming shapes with "
                + "detailed attribute configuration. All shapes drape on the terrain and "
                + "remain visible at any zoom level, making them ideal for geographic boundaries, "
                + "coverage areas, and ground-level annotations.")
            + section("What You'll See",
                "<ul>"
                + "<li>A collection of surface-conforming shapes positioned over the US</li>"
                + "<li>Polygons with translucent fills and coloured outlines</li>"
                + "<li>Circles and ellipses at various radii (measured in meters on the surface)</li>"
                + "<li>Polylines with configurable width and stipple patterns</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to see all shapes; they stay draped on terrain at any zoom level</li>"
                + "<li>Zoom in to mountainous regions to see how shapes conform to terrain relief</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>SurfacePolygon</code> &mdash; filled polygon that conforms to the terrain surface</li>"
                + "<li><code class='api-class'>SurfacePolyline</code> &mdash; line that follows the terrain surface</li>"
                + "<li><code class='api-class'>SurfaceCircle</code> &mdash; circle defined by centre position + radius in meters</li>"
                + "<li><code class='api-class'>BasicShapeAttributes</code> &mdash; fill colour, outline colour, opacity, "
                + "stipple pattern (16-bit bitmask), and stipple factor (multiplier)</li>"
                + "</ul>"));

        put("Polygons",
            section("Overview",
                "Demonstrates 3D <code>Polygon</code> shapes (not surface-conforming) with fill colours, "
                + "image texturing, rotation, holes, and dateline-spanning geometry. These are altitude-aware "
                + "polygons that can be positioned above the terrain. Shows 4 specific polygons: "
                + "\"Has a hole\" (28&ndash;35&deg;N, -106&deg; to -107&deg;, rotated -170&deg;), "
                + "\"Has an image\" (textured with 32&times;32 NASA icon), "
                + "\"Spans dateline\" (&plusmn;170&deg; to 174&deg;, rotated -45&deg;), "
                + "and \"Surrounds north pole\" (80&deg; ring).")
            + section("What You'll See",
                "<ul>"
                + "<li>Coloured filled polygons at various altitudes with <code>Material.YELLOW</code>, "
                + "<code>GREEN</code>, <code>WHITE</code>, <code>RED</code></li>"
                + "<li>A textured polygon with a NASA icon image draped on its surface</li>"
                + "<li>A polygon with a hole cut out via <code>addInnerBoundary()</code></li>"
                + "<li>A polygon that correctly crosses the international dateline</li>"
                + "<li>Rotated polygons demonstrating <code>setRotation(degrees)</code></li>"
                + "<li>Semi-transparent fills (opacity 0.5&ndash;0.8) with 2-pixel outlines</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Tilt the view to see polygons elevated above the surface</li>"
                + "<li>Navigate to the dateline (~180&deg; longitude) to see the wrapping polygon</li>"
                + "<li>Zoom to the north pole to see the polar-cap polygon</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>Polygon</code> &mdash; 3D polygon with altitude, texture, and holes</li>"
                + "<li><code class='api-method'>polygon.addInnerBoundary(positions)</code> &mdash; cut a hole in the polygon; "
                + "pass a list of <code>Position</code> objects defining the hole boundary</li>"
                + "<li><code class='api-method'>polygon.setTexImageSource(path, texCoords, numPoints)</code> &mdash; apply an image "
                + "texture with explicit texture coordinates</li>"
                + "<li><code class='api-method'>polygon.setRotation(degrees)</code> &mdash; rotate the polygon around its "
                + "reference position (negative = clockwise)</li>"
                + "<li><code class='api-class'>BasicShapeAttributes</code> with <code>Material.YELLOW</code>, opacity 0.5&ndash;0.8, "
                + "outline width 2</li>"
                + "</ul>"));

        put("ExtrudedPolygons",
            section("Overview",
                "Polygons extruded vertically from the surface to a given altitude, creating 3D building-like "
                + "volumes with separate side and cap attributes. Uses altitude mode "
                + "<code>RELATIVE_TO_GROUND</code> so extrusions follow the terrain. "
                + "Demonstrates two polygons with inner holes and distinct material properties: "
                + "magenta sides via <code>setSideAttributes()</code> and yellow caps via <code>setCapAttributes()</code>. "
                + "Lighting is enabled on all attributes for realistic shading.")
            + section("What You'll See",
                "<ul>"
                + "<li>Extruded polygon volumes rising from the terrain with visible side walls and flat caps</li>"
                + "<li>Side faces in magenta material, cap faces in yellow material</li>"
                + "<li>Inner holes that cut through both the cap and side walls</li>"
                + "<li>Highlight attributes that change appearance on hover/selection</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Tilt the view to see the full 3D extrusion effect with side walls</li>"
                + "<li>Zoom in to see the separate side and cap material textures</li>"
                + "<li>Hover to see highlight attributes applied</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ExtrudedPolygon</code> &mdash; polygon with vertical side walls and a cap surface</li>"
                + "<li><code class='api-method'>setSideAttributes(attrs)</code> &mdash; set material for the vertical side faces</li>"
                + "<li><code class='api-method'>setCapAttributes(attrs)</code> &mdash; set material for the top cap face</li>"
                + "<li><code class='api-method'>setAltitudeMode(WorldWind.RELATIVE_TO_GROUND)</code> &mdash; extrusion height is "
                + "relative to terrain elevation</li>"
                + "<li><code class='api-method'>attrs.setEnableLighting(true)</code> &mdash; enable diffuse lighting on the attributes</li>"
                + "</ul>"));

        put("ExtrudedShapes",
            section("Overview",
                "Loads airspace geometry from the bundled <code>AirspaceBuilder-DemoShapes.zip</code> archive "
                + "and extrudes them as 3D building shapes. Buildings are 40m tall with "
                + "<code>build123sm.jpg</code> as the side texture and the NASA icon as the cap texture. "
                + "The view starts at Seattle (47.656&deg;N, -122.306&deg;W) at 1000m altitude.")
            + section("What You'll See",
                "<ul>"
                + "<li>3D building shapes extruded from polygon footprints at the Seattle area</li>"
                + "<li>Side faces textured with a building facade image</li>"
                + "<li>Cap faces textured with the NASA icon</li>"
                + "<li>Buildings at 40m height above the terrain</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to the Seattle area (47.656&deg;N, -122.306&deg;W) if not already there</li>"
                + "<li>Tilt the view to see the 3D building volumes in perspective</li>"
                + "<li>Zoom in to see the side and cap textures</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ExtrudedPolygon</code> &mdash; 3D volume from polygon footprint + height</li>"
                + "<li><code class='api-method'>setSideTextures(list)</code> &mdash; apply texture images to side faces</li>"
                + "<li><code class='api-method'>setCapImageSource(source)</code> &mdash; texture the top cap</li>"
                + "<li>Data loaded from ZIP archive containing serialised airspace geometry</li>"
                + "</ul>"));

        put("ExtrudedPolygonWithBaseDepth",
            section("Overview",
                "An extruded polygon whose base is embedded below the terrain surface, creating shapes that "
                + "appear to emerge from underground. Useful for geological cross-sections, underground "
                + "infrastructure visualisation, or showing foundations of structures.")
            + section("What You'll See",
                "<ul>"
                + "<li>An extruded polygon with its base below terrain level</li>"
                + "<li>Only the portion above the terrain surface is visible</li>"
                + "<li>The polygon appears to rise naturally from the ground</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Tilt the view to see the polygon rising from the terrain</li>"
                + "<li>Zoom in to see the base intersection with the surface</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ExtrudedPolygon</code> &mdash; with negative base altitude or base depth</li>"
                + "<li><code class='api-method'>setBaseDepth(meters)</code> &mdash; depth below the terrain surface for the polygon base</li>"
                + "</ul>"));

        put("Paths",
            section("Overview",
                "Demonstrates 3D <code>Path</code> shapes with four distinct configurations: a yellow outline "
                + "path at 10km altitude, a terrain-following surface path via <code>setSurfacePath(true)</code>, "
                + "a path with default attributes, and a closed extruded path using <code>LINEAR</code> path type, "
                + "<code>ABSOLUTE</code> altitude mode, with <code>RED</code> interior and <code>BLUE</code> outline. "
                + "The extruded path uses <code>setExtrude(true)</code> to create vertical faces down to the ground.")
            + section("What You'll See",
                "<ul>"
                + "<li>A yellow-outlined path floating at 10km altitude above the terrain</li>"
                + "<li>A surface path that follows the terrain contour exactly</li>"
                + "<li>A path with default WorldWind attributes (white, 1px)</li>"
                + "<li>A closed extruded path with red vertical faces reaching to the ground and blue outlines</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Tilt the view to see paths at different altitudes</li>"
                + "<li>Zoom into the terrain-following path to see how it conforms to mountains and valleys</li>"
                + "<li>View the extruded path from the side to see the vertical \"curtain\" faces</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>Path</code> &mdash; 3D line connecting geographic <code>Position</code> objects</li>"
                + "<li><code class='api-method'>path.setSurfacePath(true)</code> &mdash; makes the path follow the terrain surface</li>"
                + "<li><code class='api-method'>path.setExtrude(true)</code> &mdash; creates vertical faces from path to ground</li>"
                + "<li><code class='api-method'>path.setPathType(AVKey.LINEAR)</code> &mdash; straight segments (vs. GREAT_CIRCLE or RHUMB_LINE)</li>"
                + "<li><code class='api-method'>path.setAltitudeMode(WorldWind.ABSOLUTE)</code> &mdash; altitude above mean sea level</li>"
                + "<li><code class='api-class'>BasicShapeAttributes</code> &mdash; configure outline colour, interior colour, width</li>"
                + "</ul>"));

        put("PathsWithDirection",
            section("Overview",
                "Paths with chevron-style directional arrows showing flow direction. Uses "
                + "<code class='api-class'>DirectedPath</code> with 11 waypoints around Seattle "
                + "(49&deg;N, -122&deg;W) at altitudes ranging from 1m to 1000m. Arrows appear between "
                + "consecutive points with a <code>RED</code> outline. Useful for flight routes, rivers, "
                + "traffic flow, or any data where direction matters.")
            + section("What You'll See",
                "<ul>"
                + "<li>A path with arrowhead glyphs indicating direction of travel between consecutive points</li>"
                + "<li>11 waypoints around Seattle with varying altitudes (1&ndash;1000m)</li>"
                + "<li>Red outline colour on the path and arrows</li>"
                + "<li>Arrows are evenly spaced along the path length</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to Seattle (49&deg;N, -122&deg;W) to see the directed path</li>"
                + "<li>Zoom in to see individual arrow glyphs and altitude variation</li>"
                + "<li>Tilt to see the 3D altitude profile of the path</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>DirectedPath</code> &mdash; <code>Path</code> subclass that renders direction "
                + "indicator arrows between consecutive positions</li>"
                + "<li><code class='api-method'>setArrowLength(pixels)</code> &mdash; set the arrowhead size in screen pixels</li>"
                + "<li><code class='api-method'>setArrowAngle(radians)</code> &mdash; set the arrowhead opening angle</li>"
                + "</ul>"));

        put("PathPositionColors",
            section("Overview",
                "Paths coloured with per-vertex gradients using the <code>Path.PositionColors</code> interface. "
                + "A 15-point path with <code>setSurfacePath(true)</code> and <code>setShowPositions(true)</code> "
                + "at scale 3 displays position dots. Implements the <code>PositionColors</code> interface whose "
                + "<code>getColor(Position, int ordinal)</code> method returns interpolated colours from a "
                + "3-colour array: red &rarr; green &rarr; blue distributed across the path length.")
            + section("What You'll See",
                "<ul>"
                + "<li>A surface path with a smooth colour gradient from red through green to blue</li>"
                + "<li>Position dots visible at each of the 15 waypoints (scale factor 3)</li>"
                + "<li>Colours blend smoothly between vertices</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Zoom in to see the gradient detail between vertices</li>"
                + "<li>The position dots (small circles) show exact vertex locations</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>Path.PositionColors</code> &mdash; interface with "
                + "<code>getColor(Position position, int ordinal)</code> returning a <code>Color</code> for each vertex</li>"
                + "<li><code class='api-method'>path.setPositionColors(positionColors)</code> &mdash; assign the colour provider</li>"
                + "<li><code class='api-method'>path.setSurfacePath(true)</code> &mdash; terrain-following mode</li>"
                + "<li><code class='api-method'>path.setShowPositions(true)</code> &mdash; show dots at vertex positions</li>"
                + "<li><code class='api-method'>path.setShowPositionsScale(3)</code> &mdash; size multiplier for position dots</li>"
                + "</ul>"));

        put("ParallelPaths",
            section("Overview",
                "Multiple paths rendered in parallel with a lateral offset, creating lane-like "
                + "visualisations. Useful for roads, airways, or multi-track routes where you need "
                + "to show side-by-side parallel corridors.")
            + section("What You'll See",
                "<ul>"
                + "<li>Several paths running side by side with consistent lateral spacing</li>"
                + "<li>Different colours or widths for each parallel path</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Zoom in to see the parallel offset between paths clearly</li>"
                + "<li>Tilt to see how they maintain spacing in 3D perspective</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>Path</code> &mdash; each parallel path is a separate <code>Path</code> object</li>"
                + "<li>Offset positions are computed geometrically from a centre line using bearing and distance calculations</li>"
                + "</ul>"));

        put("PathsOnDateline",
            section("Overview",
                "Paths that correctly cross the anti-meridian (international dateline at &plusmn;180&deg; longitude). "
                + "Demonstrates WorldWind's ability to handle wrap-around geometry without visual artefacts. "
                + "Paths crossing the dateline are automatically split and rendered correctly on both sides.")
            + section("What You'll See",
                "<ul>"
                + "<li>Paths crossing the &plusmn;180&deg; line without gaps, jumps, or rendering artefacts</li>"
                + "<li>Correct great-circle routing across the Pacific Ocean</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to the Pacific to see dateline-crossing paths</li>"
                + "<li>Rotate the globe to verify paths render correctly from all viewing angles</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>Path</code> &mdash; automatically handles dateline crossing internally</li>"
                + "<li>Use <code>Position</code> objects with longitudes near &plusmn;180&deg; (e.g. 170&deg;E to 170&deg;W)</li>"
                + "</ul>"));

        put("PathsWithLabels",
            section("Overview",
                "Paths with inline text labels placed along the route. Labels are positioned at specified "
                + "geographic locations and orient themselves to follow the path bearing direction. "
                + "Useful for annotating route segments with names, distances, or identifiers.")
            + section("What You'll See",
                "<ul>"
                + "<li>Paths with text labels rendered at various points along the line</li>"
                + "<li>Labels rotate to align with the path bearing at their position</li>"
                + "<li>Labels may declutter (hide) at wider zoom levels to prevent overlap</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Zoom in to read path labels clearly</li>"
                + "<li>Zoom out to see decluttering reduce label overlap</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>LabeledPath</code> &mdash; path subclass with attached text labels</li>"
                + "<li><code class='api-class'>PointPlacemark</code> &mdash; can also serve as labelled waypoints along a path</li>"
                + "</ul>"));

        put("MultiResPath",
            section("Overview",
                "A path with level-of-detail (LOD) tessellation that adjusts vertex density based on view "
                + "distance. When zoomed out, the path uses fewer vertices for better performance; when "
                + "zoomed in, more vertices are generated for a smoother appearance.")
            + section("What You'll See",
                "<ul>"
                + "<li>A path that appears smooth at all zoom levels</li>"
                + "<li>Vertex count adapts automatically as you zoom in and out</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Zoom in and out to see the tessellation adapt in real-time</li>"
                + "<li>Tilt the view to see 3D path geometry</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>Path</code> &mdash; with LOD tessellation enabled</li>"
                + "<li><code class='api-method'>setNumSubsegments(int)</code> &mdash; controls subdivision level per segment; "
                + "higher values produce smoother curves but more vertices</li>"
                + "</ul>"));

        put("DashedLinesExample",
            section("Overview",
                "Demonstrates 10 different line stipple patterns with various dash styles using the "
                + "GPU-accelerated line rendering pipeline. Patterns use 16-bit bitmasks: <code>0xFFFF</code> (solid), "
                + "<code>0xF0F0</code> (fine dash), <code>0xFF00</code> (medium dash), <code>0xAAAA</code> (dotted), "
                + "<code>0xFFC8</code> (dash-dot), and more. Also shows dashed outlines on a pentagon, circle, and "
                + "ellipse shape. Includes a dark legend panel showing all pattern samples.")
            + section("What You'll See",
                "<ul>"
                + "<li>10 line examples with distinct dash patterns, each labelled</li>"
                + "<li>Pentagon, circle, and ellipse shapes with dashed outlines</li>"
                + "<li>A legend panel showing all available stipple patterns</li>"
                + "<li>GPU-rendered patterns that stay crisp at any zoom level</li>"
                + "<li>Multiple colours and line widths demonstrated</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Zoom in to see pattern detail &mdash; dashes maintain consistent screen-space size</li>"
                + "<li>Navigate to the shapes to see dashed outlines on 2D shapes</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-method'>attrs.setOutlineStipplePattern((short) 0xAAAA)</code> &mdash; 16-bit pattern where "
                + "each bit represents a pixel on/off in the dash cycle</li>"
                + "<li><code class='api-method'>attrs.setOutlineStippleFactor(int)</code> &mdash; multiplier that stretches "
                + "each bit in the pattern (e.g. factor 5 makes each dash/gap 5 pixels wide)</li>"
                + "<li><b>Common patterns:</b> <code>0xFFFF</code> = solid, <code>0xF0F0</code> = fine dash, "
                + "<code>0xFF00</code> = medium dash, <code>0xAAAA</code> = dotted, <code>0xFFC8</code> = dash-dot</li>"
                + "</ul>"));

        put("ProceduralFillPatternsExample",
            section("Overview",
                "GPU procedural fill patterns rendered entirely on the GPU without textures. Shows three "
                + "<code class='api-class'>SurfacePolygon</code> shapes at 38&deg;N: hatch (-115&deg;W, cornflower blue), "
                + "crosshatch (-103&deg;W, light green), and dots (-91&deg;W, salmon). Uses "
                + "<code class='api-class'>ProceduralFillPattern</code> with constants <code>HATCH</code>, "
                + "<code>CROSSHATCH</code>, and <code>DOTS</code>. Patterns tile seamlessly and remain "
                + "geographically stable (they don't shift when you pan).")
            + section("What You'll See",
                "<ul>"
                + "<li>Three polygons filled with hatch lines, crosshatch, and dot patterns</li>"
                + "<li>Patterns are crisp at any zoom level (procedurally generated, no pixelation)</li>"
                + "<li>Cornflower blue hatch, light green crosshatch, salmon dots</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Zoom in to see patterns remain perfectly sharp (resolution-independent)</li>"
                + "<li><b>UI sliders:</b> Scale (5&ndash;80%), Line width (1&ndash;70%), Angle (0&ndash;180&deg;, hatch only)</li>"
                + "<li>Adjust parameters to see how they affect pattern appearance in real-time</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ProceduralFillPattern</code> &mdash; "
                + "<code>new ProceduralFillPattern(mode, scale, width, angle)</code></li>"
                + "<li>Mode constants: <code>ProceduralFillPattern.HATCH</code>, <code>CROSSHATCH</code>, <code>DOTS</code></li>"
                + "<li><code class='api-method'>attrs.setFillPattern(pattern)</code> &mdash; assign to any shape's attributes</li>"
                + "<li>All parameters are passed as shader uniforms; no texture memory is used</li>"
                + "</ul>"));

        put("SurfaceShapeShowcase",
            section("Overview",
                "A comprehensive showcase of all 7 surface shape types combined with procedural fill patterns "
                + "and interactive colour pickers. Shapes are arranged in two rows: Row 1 at 42&deg;N and Row 2 at "
                + "34&deg;N, spread across the US. Colour palette includes cornflower blue, orange, light green, "
                + "violet, gold, turquoise, and salmon. Shape types: Polygon, Polyline, Circle, Ellipse, Quad, "
                + "Sector, and Box.")
            + section("What You'll See",
                "<ul>"
                + "<li>All 7 surface shape types displayed in a structured grid layout</li>"
                + "<li>Each shape with a distinct colour from the palette</li>"
                + "<li>Procedural fill patterns (hatch, crosshatch, dots) applied to shapes</li>"
                + "<li>An interactive control panel for customising appearance</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li><b>Shape selector</b> &mdash; choose which shape type to modify</li>"
                + "<li><b>Fill/outline colour buttons</b> &mdash; open <code>JColorChooser</code> dialog for colour selection</li>"
                + "<li><b>Opacity slider</b> (0&ndash;100%) &mdash; adjust fill transparency</li>"
                + "<li><b>Fill pattern combo</b> &mdash; select hatch, crosshatch, dots, or solid fill</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>SurfacePolygon</code>, <code class='api-class'>SurfacePolyline</code>, "
                + "<code class='api-class'>SurfaceCircle</code>, <code class='api-class'>SurfaceEllipse</code>, "
                + "<code class='api-class'>SurfaceQuad</code>, <code class='api-class'>SurfaceSector</code> &mdash; "
                + "all surface shape types</li>"
                + "<li><code class='api-class'>BasicShapeAttributes</code> &mdash; shared attributes for fill, stroke, opacity</li>"
                + "<li><code class='api-class'>ProceduralFillPattern</code> &mdash; GPU procedural fill integration</li>"
                + "</ul>"));

        put("SurfaceTextUsage",
            section("Overview",
                "Renders text strings directly on the globe surface. Text conforms to the terrain "
                + "curvature, scales with the view, and remains geographically anchored. Useful for "
                + "geographic labels, country names, region annotations, or any text that should be "
                + "part of the map surface rather than a floating overlay.")
            + section("What You'll See",
                "<ul>"
                + "<li>Text strings rendered directly on the globe surface</li>"
                + "<li>Text conforms to terrain curvature (visible when tilted)</li>"
                + "<li>Text scales appropriately with zoom level</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Zoom in to read text at higher resolution</li>"
                + "<li>Tilt to see text conform to terrain curvature</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>SurfaceText</code> &mdash; text draped on the globe surface at a geographic position</li>"
                + "<li><code class='api-method'>setText(String)</code> &mdash; set the display text</li>"
                + "<li><code class='api-method'>setFont(Font)</code> &mdash; set the text font and size</li>"
                + "<li><code class='api-method'>setColor(Color)</code> &mdash; set the text colour</li>"
                + "</ul>"));

        put("LineBuilder",
            section("Overview",
                "An interactive mouse-driven polyline construction tool. <code>LineBuilder</code> extends "
                + "<code>AVListImpl</code> and provides <code>setArmed(boolean)</code>, <code>clear()</code>, "
                + "and <code>getLine()</code>. Click-drag on the globe adds points; <b>Ctrl+Click</b> undoes "
                + "the last point. The builder fires <code>PropertyChangeEvent</code>s: "
                + "<code>AddPosition</code>, <code>ReplacePosition</code>, <code>RemovePosition</code>. "
                + "A panel provides New, Pause/Resume, and End buttons alongside a scrollable point list.")
            + section("What You'll See",
                "<ul>"
                + "<li>A cursor that follows the mouse on the globe surface</li>"
                + "<li>Click to place vertices; a polyline forms connecting them in real-time</li>"
                + "<li>A scrollable panel listing all placed point coordinates</li>"
                + "<li>New, Pause/Resume, and End buttons for controlling the builder</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li><b>Click-drag</b> &mdash; place a new vertex on the globe</li>"
                + "<li><b>Ctrl+Click</b> &mdash; undo (remove) the last placed vertex</li>"
                + "<li><b>New</b> button &mdash; clear and start a new line</li>"
                + "<li><b>Pause/Resume</b> &mdash; toggle the armed state of the builder</li>"
                + "<li><b>End</b> &mdash; finalise the line</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>LineBuilder</code> &mdash; extends <code>AVListImpl</code>; manages interactive "
                + "polyline construction with undo support</li>"
                + "<li><code class='api-method'>lineBuilder.setArmed(boolean)</code> &mdash; enable/disable vertex placement mode</li>"
                + "<li><code class='api-method'>lineBuilder.getLine()</code> &mdash; retrieve the constructed <code>Polyline</code></li>"
                + "<li>Property change events: <code>\"AddPosition\"</code>, <code>\"ReplacePosition\"</code>, "
                + "<code>\"RemovePosition\"</code> &mdash; listen for these to update UI</li>"
                + "</ul>"));

        put("ContourLines",
            section("Overview",
                "Renders terrain elevation contour lines derived from the active elevation model. "
                + "Contours follow constant-elevation paths across the terrain surface, with closer "
                + "spacing indicating steeper terrain.")
            + section("What You'll See",
                "<ul>"
                + "<li>Lines of constant elevation drawn on the terrain surface</li>"
                + "<li>Closer contour spacing indicates steeper terrain gradients</li>"
                + "<li>Contours adapt to the available elevation data resolution</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to mountainous terrain (Alps, Rockies, Himalayas) to see dense contour lines</li>"
                + "<li>Zoom in for finer contour detail at higher elevation data resolutions</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-method'>globe.getElevation(lat, lon)</code> &mdash; sample terrain elevation at a position</li>"
                + "<li>Contour generation from regular elevation grid samples using marching-squares algorithm</li>"
                + "<li><code class='api-class'>SurfacePolyline</code> &mdash; rendered contour line segments on the surface</li>"
                + "</ul>"));

        put("ContourBuilderExample",
            section("Overview",
                "Programmatic construction of contour line layers from arbitrary numeric data arrays, "
                + "not just terrain elevations. Shows how to generate contour geometry from any 2D grid of "
                + "values &mdash; such as temperature, pressure, or population density data.")
            + section("What You'll See",
                "<ul>"
                + "<li>Contour lines generated from numeric data arrays overlaid on the globe</li>"
                + "<li>Configurable contour intervals and line styling</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to the data region to see the contours</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li>Contour tracing algorithms applied to 2D data grids</li>"
                + "<li><code class='api-class'>SurfacePolyline</code> &mdash; rendered contour line segments</li>"
                + "</ul>"));

        put("ShapeEditing",
            section("Overview",
                "Interactive control-point editing for both airspace shapes and surface shapes. Demonstrates "
                + "10 airspace shapes and 7 surface shapes, all made editable via "
                + "<code class='api-class'>ShapeEditor</code>. Left-click a shape to enable its editor (showing "
                + "control points); click again to disable. Shift and Alt keys are used by ShapeEditor for "
                + "adding and removing vertices respectively.")
            + section("What You'll See",
                "<ul>"
                + "<li>17 shapes total (10 airspaces + 7 surface shapes) scattered on the globe</li>"
                + "<li>Click any shape to see its control point handles appear</li>"
                + "<li>Drag handles to reshape polygons, resize circles, move vertices</li>"
                + "<li>Real-time visual feedback as shapes are modified</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li><b>Left-click</b> a shape to select it and show control points</li>"
                + "<li><b>Drag</b> a control point to move it and reshape the geometry</li>"
                + "<li><b>Shift+Click</b> on a shape edge to add a new vertex</li>"
                + "<li><b>Alt+Click</b> on a control point to remove that vertex</li>"
                + "<li><b>Left-click</b> the background or click the shape again to deselect</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ShapeEditor</code> &mdash; "
                + "<code>new ShapeEditor(WorldWindow, Renderable)</code> with <code>setArmed(boolean)</code></li>"
                + "<li><code class='api-method'>editor.setArmed(true)</code> &mdash; activate control-point editing</li>"
                + "<li><code class='api-method'>editor.setArmed(false)</code> &mdash; deactivate and hide control points</li>"
                + "<li>Supports both <code>Airspace</code> and <code>SurfaceShape</code> subclasses</li>"
                + "</ul>"));

        put("ShapeEditingExtension",
            section("Overview",
                "Extends the shape editing framework with custom control-point handles and editing behaviours "
                + "via the extension API. Shows how to add rotation, scale, and other custom editing operations "
                + "beyond the built-in vertex dragging.")
            + section("What You'll See",
                "<ul>"
                + "<li>Shapes with custom editing handles (rotation, scale, etc.)</li>"
                + "<li>Extended editing operations beyond basic vertex dragging</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Select shapes and use custom handles for extended editing operations</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ShapeEditor</code> &mdash; subclass to add custom control-point types</li>"
                + "<li>Custom <code>EditAction</code> implementations for new editing behaviours</li>"
                + "</ul>"));

        put("DraggingShapes",
            section("Overview",
                "Demonstrates dragging 10+ different shape types across the globe with the mouse. Includes "
                + "an octagon over Florida (textured), a dateline-spanning polygon, an ellipse, circles, "
                + "a quad, a square, sectors, and a polyline. All shapes have semi-transparent fills "
                + "(0.5 opacity) with brightened outlines created via <code>WWUtil.makeColorBrighter()</code>. "
                + "Uses <code>BasicDragger(WorldWindow)</code> added as a select listener.")
            + section("What You'll See",
                "<ul>"
                + "<li>10+ shapes of various types scattered across the globe</li>"
                + "<li>Semi-transparent fills (0.5 opacity) with brightened outlines for visibility</li>"
                + "<li>Textured octagon over Florida</li>"
                + "<li>A polygon that spans the international dateline</li>"
                + "<li>Shapes highlight when the mouse hovers over them</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li><b>Hover</b> over a shape to highlight it</li>"
                + "<li><b>Left-drag</b> a shape to move it to a new position on the globe</li>"
                + "<li><b>Left-drag</b> the globe background to navigate normally</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>BasicDragger</code> &mdash; one-line drag support: "
                + "<code>wwd.addSelectListener(new BasicDragger(wwd))</code></li>"
                + "<li><code class='api-method'>WWUtil.makeColorBrighter(color)</code> &mdash; utility to brighten a colour "
                + "for outline contrast against the fill</li>"
                + "<li><code class='api-class'>SelectListener</code> &mdash; detects <code>DRAG</code> and <code>DRAG_END</code> events</li>"
                + "<li><code class='api-class'>DragContext</code> &mdash; provides drag state (start/current screen and geographic positions)</li>"
                + "</ul>"));

        put("SimpleShapeDragging",
            section("Overview",
                "A minimal example of shape dragging. Creates a single draggable polygon "
                + "and demonstrates the simplest possible drag implementation &mdash; just one line of code "
                + "to enable dragging for any shape.")
            + section("What You'll See",
                "<ul>"
                + "<li>A single polygon on the globe that can be dragged to any position</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li><b>Left-drag</b> the polygon to move it anywhere on the globe</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>BasicDragger</code> &mdash; one-line drag enablement:</li>"
                + "<li><code class='api-method'>wwd.addSelectListener(new BasicDragger(wwd))</code> &mdash; "
                + "that's all you need to make any shape draggable</li>"
                + "</ul>"));

        put("ShapeClipping",
            section("Overview",
                "Clips surface shapes to land or water areas using elevation data. Useful for creating "
                + "shapes that follow coastlines or other terrain boundaries &mdash; for example, showing a coverage "
                + "area that only covers land, or a marine zone that only covers water.")
            + section("What You'll See",
                "<ul>"
                + "<li>Shapes that are clipped at the land/water boundary</li>"
                + "<li>Only the land (or water) portion of the shape is rendered</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to coastal areas to see the clipping effect at the shoreline</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li>Elevation-based clipping: sample terrain to determine land (elevation &gt; 0) vs water (elevation &le; 0)</li>"
                + "<li><code class='api-class'>SurfacePolygon</code> &mdash; the resulting clipped output shape</li>"
                + "</ul>"));

        put("ShapeCombining",
            section("Overview",
                "Boolean geometry operations on surface shapes: union, intersection, and difference. "
                + "Demonstrates combining two <code class='api-class'>SurfaceCircle</code> shapes at 50&deg;N: "
                + "one at -105&deg;W and one at -100&deg;W, both with 500km radius and partial overlap. "
                + "Uses <code class='api-class'>ShapeCombiner</code> with the globe and a resolution parameter. "
                + "Results are displayed at 10&deg; vertical offsets below the originals: union, intersection, difference. "
                + "Each result is rendered as a <code class='api-class'>SurfaceMultiPolygon</code> from a <code>ContourList</code>.")
            + section("What You'll See",
                "<ul>"
                + "<li>Two overlapping circles at the top (the input shapes)</li>"
                + "<li>Their union (merged area) displayed 10&deg; below</li>"
                + "<li>Their intersection (shared area) displayed 20&deg; below</li>"
                + "<li>Their difference (area in one but not the other) displayed 30&deg; below</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate vertically to see all four results: original, union, intersection, difference</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ShapeCombiner</code> &mdash; "
                + "<code>new ShapeCombiner(Globe, resolution)</code></li>"
                + "<li><code class='api-method'>combiner.union(shapeA, shapeB)</code> &mdash; merged area</li>"
                + "<li><code class='api-method'>combiner.intersection(shapeA, shapeB)</code> &mdash; shared area</li>"
                + "<li><code class='api-method'>combiner.difference(shapeA, shapeB)</code> &mdash; area in A but not B</li>"
                + "<li><code class='api-class'>SurfaceMultiPolygon</code> &mdash; renders a <code>ContourList</code> result</li>"
                + "</ul>"));

        put("RotatedSector",
            section("Overview",
                "A rectangular sector shape with arbitrary azimuth rotation. Unlike a standard "
                + "<code>Sector</code> aligned to lat/lon grid lines, this can be rotated to any heading angle.")
            + section("What You'll See",
                "<ul>"
                + "<li>A rectangular sector shape rotated to a non-axis-aligned heading</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>View the rotated sector from various angles and zoom levels</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>SurfaceSector</code> &mdash; with rotation parameter for arbitrary heading</li>"
                + "</ul>"));

        put("LineBackground",
            section("Overview",
                "A path rendered with a wide background halo line for improved visibility against complex "
                + "terrain or imagery. The technique renders the same path twice: once as a wide background "
                + "colour, then again as a narrow foreground colour on top.")
            + section("What You'll See",
                "<ul>"
                + "<li>A path with a contrasting background outline (halo/glow effect)</li>"
                + "<li>Improved readability over varied terrain and imagery</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate over different terrain types to see how the halo improves line visibility</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>Path</code> &mdash; two overlapping Path objects: wide background + narrow foreground</li>"
                + "<li>Set different widths and colours to create the halo effect</li>"
                + "</ul>"));

        // ── 3D Shapes ───────────────────────────────────────────────────────────

        put("Boxes",
            section("Overview",
                "Demonstrates 3D box (rectangular prism) shapes with 8&ndash;9 instances showing every "
                + "configuration: equal axes with <code>ABSOLUTE</code>, <code>RELATIVE_TO_GROUND</code>, "
                + "and <code>CLAMP_TO_GROUND</code> altitude modes; textured with "
                + "<code>500px-Checkerboard_pattern.png</code>; scaled (1M &times; 500K &times; 100K meters); "
                + "and oriented (heading 90&deg;, pitch 45&deg;, roll 30&deg;). All instances use yellow interior "
                + "(0.7 opacity), red 2-pixel outline, with lighting enabled.")
            + section("What You'll See",
                "<ul>"
                + "<li>8&ndash;9 boxes demonstrating different altitude modes, textures, scales, and orientations</li>"
                + "<li>Yellow interior fill at 0.7 opacity with red 2-pixel outlines</li>"
                + "<li>Checkerboard texture on the textured instance</li>"
                + "<li>Large-scale boxes (up to 1M meters) for visibility from orbit</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Tilt the view for a 3D perspective to see box geometry clearly</li>"
                + "<li>Use the <b>detail hint slider</b> (-1.0 to 1.0) to adjust tessellation quality</li>"
                + "<li>Zoom in to see texture detail on the checkerboard instance</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>Box</code> &mdash; rigid shape with east-west, north-south, and vertical radii</li>"
                + "<li><code class='api-method'>setHeading(Angle.fromDegrees(90))</code>, "
                + "<code class='api-method'>setTilt(Angle.fromDegrees(45))</code>, "
                + "<code class='api-method'>setRoll(Angle.fromDegrees(30))</code> &mdash; orientation in 3 axes</li>"
                + "<li><code class='api-method'>setAltitudeMode(WorldWind.ABSOLUTE)</code> &mdash; altitude above MSL</li>"
                + "<li><code class='api-method'>setDetailHint(double)</code> &mdash; -1.0 (coarse) to 1.0 (fine) tessellation</li>"
                + "<li><code class='api-method'>attrs.setImageSource(\"500px-Checkerboard_pattern.png\")</code> &mdash; texture mapping</li>"
                + "</ul>"));

        put("Cones",
            section("Overview",
                "3D cone shapes with 8&ndash;9 instances demonstrating: equal axes with all altitude modes, "
                + "textured, scaled (1M &times; 500K &times; 100K), and oriented (heading 90&deg;, pitch 45&deg;, roll 30&deg;). "
                + "Yellow interior (0.7 opacity), red 2-pixel outline, lighting enabled. "
                + "Includes a detail hint slider from -1.0 to 1.0 for tessellation quality control.")
            + section("What You'll See",
                "<ul>"
                + "<li>Multiple cone shapes in different configurations across the globe</li>"
                + "<li>Various sizes, orientations, and altitude modes</li>"
                + "<li>Textured cone with checkerboard pattern</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Tilt and zoom to see the 3D cone geometry from different angles</li>"
                + "<li>Use the <b>detail hint slider</b> (-1.0 to 1.0) to adjust tessellation quality</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>Cone</code> &mdash; rigid shape with height and radius parameters</li>"
                + "<li><code class='api-class'>ShapeAttributes</code> &mdash; material colour, opacity, outline, lighting</li>"
                + "<li><code class='api-method'>setDetailHint(double)</code> &mdash; tessellation quality (-1.0 to 1.0)</li>"
                + "</ul>"));

        put("Cylinders",
            section("Overview",
                "3D cylinder shapes with 8&ndash;9 instances showing all altitude modes, textures, scales, "
                + "and orientations. Equal top and bottom radii produce a cylinder; different radii produce "
                + "a truncated cone (frustum). Yellow interior (0.7 opacity), red 2-pixel outline, lighting enabled.")
            + section("What You'll See",
                "<ul>"
                + "<li>Cylinders of various sizes demonstrating different configurations</li>"
                + "<li>Some with equal radii (true cylinder) and some with different top/bottom radii (frustum)</li>"
                + "<li>Textured instance with checkerboard pattern</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Tilt and rotate for 3D perspective</li>"
                + "<li>Use the <b>detail hint slider</b> (-1.0 to 1.0) to control tessellation</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>Cylinder</code> &mdash; rigid shape with separate top and bottom radii</li>"
                + "<li><code class='api-method'>setDetailHint(double)</code> &mdash; tessellation quality control</li>"
                + "</ul>"));

        put("Ellipsoids",
            section("Overview",
                "3D ellipsoid shapes with three independent semi-axes (east-west, north-south, vertical). "
                + "A sphere is a special case where all axes are equal. Shows 8&ndash;9 instances with all "
                + "altitude modes, textures, scales, and orientations. Yellow interior, red outline, lighting enabled.")
            + section("What You'll See",
                "<ul>"
                + "<li>Ellipsoids of various shapes: spheres (equal axes), oblate (flattened), prolate (elongated)</li>"
                + "<li>Textured instance with checkerboard pattern</li>"
                + "<li>Large-scale instances visible from orbit</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Tilt to see the 3D geometry and how different axis ratios affect the shape</li>"
                + "<li>Use the <b>detail hint slider</b> (-1.0 to 1.0) to control tessellation quality</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>Ellipsoid</code> &mdash; rigid shape with three independent semi-axes "
                + "(east-west, north-south, vertical)</li>"
                + "<li>A sphere: set all three radii equal</li>"
                + "<li><code class='api-method'>setDetailHint(double)</code> &mdash; tessellation quality</li>"
                + "</ul>"));

        put("Pyramids",
            section("Overview",
                "3D pyramid shapes with a rectangular base. Shows 8&ndash;9 instances demonstrating all "
                + "altitude modes, textures (checkerboard), scales, and orientations (heading 90&deg;, pitch 45&deg;, "
                + "roll 30&deg;). Yellow interior (0.7 opacity), red 2-pixel outline, lighting enabled.")
            + section("What You'll See",
                "<ul>"
                + "<li>Pyramid shapes rising to a point from a rectangular base</li>"
                + "<li>Various sizes, orientations, and altitude modes</li>"
                + "<li>Textured pyramid with checkerboard pattern</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Tilt to see the 3D pyramid geometry from different angles</li>"
                + "<li>Use the <b>detail hint slider</b> (-1.0 to 1.0) to control tessellation</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>Pyramid</code> &mdash; rigid shape with rectangular base converging to apex</li>"
                + "<li><code class='api-method'>setDetailHint(double)</code> &mdash; tessellation quality</li>"
                + "</ul>"));

        put("Wedges",
            section("Overview",
                "3D wedge shapes (angular sectors of a cylinder) positioned on the globe. Shows 8&ndash;9 "
                + "instances with all altitude modes, textures, scales, and orientations. Yellow interior, "
                + "red outline, lighting enabled. Detail hint slider for tessellation quality.")
            + section("What You'll See",
                "<ul>"
                + "<li>Wedge-shaped 3D volumes (like a slice of pie or cake) on the globe</li>"
                + "<li>Various angular extents, sizes, and orientations</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Tilt to see the 3D wedge geometry</li>"
                + "<li>Use the <b>detail hint slider</b> (-1.0 to 1.0) to control tessellation</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>Wedge</code> &mdash; rigid shape defined by two angles and radius</li>"
                + "<li><code class='api-method'>setDetailHint(double)</code> &mdash; tessellation quality</li>"
                + "</ul>"));

        put("RigidShapes",
            section("Overview",
                "All 3D rigid shape types displayed together in a single scene: Box, Cone, Cylinder, "
                + "Ellipsoid, Pyramid, and Wedge. A comprehensive reference for the rigid shape API. "
                + "Includes a multi-image Box demonstrating per-face textures via "
                + "<code>setImageSources(ArrayList)</code> where some entries can be <code>null</code> to skip faces.")
            + section("What You'll See",
                "<ul>"
                + "<li>One instance of each rigid shape type displayed together</li>"
                + "<li>A Box with different images on each face (some faces untextured where null is passed)</li>"
                + "<li>Various sizes, orientations, and material properties</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Tilt and navigate to view all shape types from different angles</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>RigidShape</code> &mdash; abstract base class for all rigid 3D shapes</li>"
                + "<li><code class='api-class'>Box</code>, <code class='api-class'>Cone</code>, <code class='api-class'>Cylinder</code>, "
                + "<code class='api-class'>Ellipsoid</code>, <code class='api-class'>Pyramid</code>, <code class='api-class'>Wedge</code></li>"
                + "<li><code class='api-method'>box.setImageSources(ArrayList&lt;Object&gt;)</code> &mdash; per-face textures; "
                + "ArrayList of 6 image sources (pass <code>null</code> entries to skip specific faces)</li>"
                + "<li><code class='api-class'>ShapeAttributes</code> &mdash; shared material/lighting model for all rigid shapes</li>"
                + "</ul>"));

        put("Airspaces",
            section("Overview",
                "Demonstrates all 11 airspace volume types: <code>CappedCylinder</code>, "
                + "<code>PartialCappedCylinder</code>, <code>Cake</code> (stacked layers), "
                + "<code>Orbit</code> (CENTER/LEFT/RIGHT attachment), <code>Curtain</code>, "
                + "<code>Polygon</code> (airspace polygon), <code>PolyArc</code>, <code>Route</code>, "
                + "<code>TrackAirspace</code>, <code>SphereAirspace</code>, and <code>CappedEllipticalCylinder</code>. "
                + "Organised into 4 layer groups: AGL (above ground level), AMSL (above mean sea level), "
                + "dateline-crossing, and intersecting airspaces. Uses <code>RandomShapeAttributes</code> for "
                + "varied appearance and includes a selection monitoring pattern with highlight swap.")
            + section("What You'll See",
                "<ul>"
                + "<li>11 different airspace volume types at various altitudes</li>"
                + "<li>Transparent fills with visible outlines in random colours</li>"
                + "<li>4 groups: AGL-conforming, AMSL, dateline-crossing, and intersecting volumes</li>"
                + "<li>Volumes that conform to specified altitude ranges</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Tilt the view to see airspace volumes in 3D perspective</li>"
                + "<li>Navigate between different airspace type groups</li>"
                + "<li>Hover over airspaces to see highlight attributes applied</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>CappedCylinder</code>, <code class='api-class'>PartialCappedCylinder</code>, "
                + "<code class='api-class'>Cake</code> &mdash; radial/cylindrical volume types</li>"
                + "<li><code class='api-class'>Orbit</code> &mdash; with <code>CENTER</code>, <code>LEFT</code>, <code>RIGHT</code> attachment modes</li>"
                + "<li><code class='api-class'>Curtain</code>, <code class='api-class'>Route</code>, "
                + "<code class='api-class'>TrackAirspace</code> &mdash; path-following volumes</li>"
                + "<li><code class='api-method'>setAltitudes(lower, upper)</code> &mdash; define the altitude range in meters</li>"
                + "<li><code class='api-method'>setTerrainConforming(top, bottom)</code> &mdash; booleans for which surfaces follow terrain</li>"
                + "<li><code class='api-class'>AirspaceAttributes</code> &mdash; fill colour, opacity, outline style</li>"
                + "</ul>"));

        put("AirspaceBuilder",
            section("Overview",
                "Interactive builder for airspace geometry with a full editor interface. Provides a File menu "
                + "(Open, Save, Load Demo), a shape selection dropdown (Polygon or Sphere), and creates "
                + "new shapes at the viewport centre with 200km diameter. Uses <code>AirspaceEntry</code> "
                + "as the data class and <code>AirspaceBuilderModel</code> as the table model. "
                + "Shape creation and editing uses the <code>AirspaceFactory</code> interface with "
                + "<code>createAirspace()</code> and <code>createEditor()</code> methods.")
            + section("What You'll See",
                "<ul>"
                + "<li>A control panel for creating and editing airspace shapes</li>"
                + "<li>A table listing all created airspaces with their properties</li>"
                + "<li>Interactive placement and sizing of airspace volumes on the globe</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Select a shape type (Polygon/Sphere) from the dropdown and click <b>New Shape</b></li>"
                + "<li>New shapes appear at viewport centre with 200km diameter</li>"
                + "<li><b>Alt+Click</b> on a shape edge to add a vertex</li>"
                + "<li><b>Ctrl+Click</b> on a vertex to remove it</li>"
                + "<li><b>Shift+Drag</b> to adjust height</li>"
                + "<li>Use <b>File &rarr; Open/Save</b> to load and save airspace definitions</li>"
                + "<li><b>File &rarr; Load Demo</b> to load a set of demo shapes</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>AirspaceEditor</code> &mdash; interactive editing for airspace shapes</li>"
                + "<li><code class='api-class'>AirspaceFactory</code> &mdash; interface with <code>createAirspace()</code> and "
                + "<code>createEditor()</code> factory methods</li>"
                + "<li><code class='api-class'>AirspaceEntry</code> &mdash; data class pairing an Airspace with its Editor</li>"
                + "<li><code class='api-class'>AirspaceBuilderModel</code> &mdash; <code>AbstractTableModel</code> for the shape table</li>"
                + "</ul>"));

        // Modified by seaglassfoundry.com - removed RadarVolume entry (shape class, not a launchable example)

        put("RadarVolumeExample",
            section("Overview",
                "An advanced radar volume with terrain intersection and configurable azimuth/elevation ranges. "
                + "Located at 36.8378&deg;N, -118.8743&deg;W (Mojave area) at 1000m altitude. Radar parameters: "
                + "inner range 100m, outer range 30km, 25&times;25 azimuth/elevation samples. Two modes: "
                + "<b>Grid mode</b> (azimuth 140&ndash;270&deg;, elevation -50&deg; to 50&deg;) and "
                + "<b>Cone mode</b> (FOV 100&deg;, elevation 20&deg;, azimuth 205&deg;). Uses "
                + "<code class='api-class'>HighResolutionTerrain</code> at 50m resolution with 150MB cache. "
                + "Terrain intersection is computed on a background thread with timing output to console.")
            + section("What You'll See",
                "<ul>"
                + "<li>A radar volume that intersects the mountainous terrain of the Mojave region</li>"
                + "<li>Terrain intersection lines where the radar cone meets the ground</li>"
                + "<li>Switchable grid and cone visualisation modes</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Use sidebar controls to switch between Grid and Cone modes</li>"
                + "<li>Adjust azimuth and elevation range parameters</li>"
                + "<li>Tilt and rotate to see the terrain intersection from different angles</li>"
                + "<li>Watch the console for terrain intersection computation timing</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>RadarVolume</code> &mdash; with terrain intersection computation</li>"
                + "<li><code class='api-class'>HighResolutionTerrain</code> &mdash; high-res terrain sampling at 50m resolution "
                + "with 150MB memory cache</li>"
                + "<li>Background thread computation for terrain intersection to avoid blocking the EDT</li>"
                + "</ul>"));

        // ── Layers / WMS ────────────────────────────────────────────────────────

        put("WMSExplorer",
            section("Overview",
                "Browse, preview, and load WMS (Web Map Service) layers from any OGC-compliant server. "
                + "Includes 6 preset servers: NASA GIBS, USGS NatMap, Canada Weather, NOAA Radar, NOAA GOES, "
                + "and DWD Germany. Parses GetCapabilities XML to build a layer tree with metadata. "
                + "Supports GetFeatureInfo on right-click (queries a 101&times;101 pixel bbox using WMS 1.1.1). "
                + "Implements a 30-entry LRU cache per temporal layer and uses a <code>ScheduledExecutor</code> "
                + "for server health monitoring (green/yellow/red status dots).")
            + section("What You'll See",
                "<ul>"
                + "<li>A panel listing WMS server URLs with health status indicators (green/yellow/red)</li>"
                + "<li>A hierarchical layer tree showing all available layers from the selected server</li>"
                + "<li>Layer metadata: abstract, coordinate reference system, bounding box, time dimensions</li>"
                + "<li>Selected layers rendered on the globe in real-time as tiled imagery</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Select a WMS server from the dropdown or enter a custom GetCapabilities URL</li>"
                + "<li>Browse the layer tree and click layers to preview them on the globe</li>"
                + "<li>Click <b>Add</b> to add a layer permanently to the globe's layer list</li>"
                + "<li><b>Right-click</b> on the globe to perform a GetFeatureInfo query at that position</li>"
                + "<li>Use the layer manager to toggle visibility and adjust opacity of added layers</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>WMSCapabilities</code> &mdash; parses WMS GetCapabilities XML response</li>"
                + "<li><code class='api-class'>WMSTiledImageLayer</code> &mdash; tiled image layer backed by a WMS endpoint</li>"
                + "<li><code class='api-method'>WMSTiledImageLayer.fromWMSCapabilities(caps, params)</code> &mdash; factory method "
                + "to create a layer from parsed capabilities</li>"
                + "<li>Supports WMS 1.1.1 and 1.3.0 with TIME dimension for temporal layers</li>"
                + "<li>30-entry LRU cache per temporal layer avoids re-fetching recent time steps</li>"
                + "</ul>"));

        put("WMSLayerManager",
            section("Overview",
                "Connect to any WMS server, browse its layer catalog, and manage active layers with "
                + "opacity and ordering controls. Demonstrates the reusable WMSServerPanel and "
                + "WMSLayerEntry components integrated with WorldWind's layer system.")
            + section("What You'll See",
                "<ul>"
                + "<li><b>WMS Browser tab</b> &mdash; server connection panel with preset servers, "
                + "layer catalog with search/filter, and add/remove buttons per layer</li>"
                + "<li><b>Active WMS tab</b> &mdash; per-layer opacity slider, up/down ordering, "
                + "and remove controls for all active WMS layers</li>"
                + "<li><b>Layers tab</b> &mdash; standard WorldWind layer list for base layers</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Select a preset server or enter a custom URL and click Connect</li>"
                + "<li>Browse the layer catalog; use the filter field to search by name</li>"
                + "<li>Click <b>Add</b> to place a WMS layer on the globe, <b>Remove</b> to take it off</li>"
                + "<li>Switch to the Active WMS tab to adjust opacity and layer ordering</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>WMSServerPanel</code> &mdash; async server connection with status indicators</li>"
                + "<li><code class='api-class'>WMSLayerEntry</code> &mdash; data model with lazy Layer/ElevationModel creation</li>"
                + "<li><code class='api-method'>ApplicationTemplate.insertBeforePlacenames()</code> &mdash; layer insertion</li>"
                + "<li><code class='api-method'>layer.setOpacity(double)</code> &mdash; per-layer transparency control</li>"
                + "</ul>"));

        put("WMSTimeSeriesDemo",
            section("Overview",
                "Animate NASA GIBS time-series layers with daily imagery for all 366 days of 2024 (leap year). "
                + "4 presets: MODIS Terra true-colour, VIIRS S-NPP, Active Fires, and Snow Cover. "
                + "The time slider steps through dates, updating the WMS TIME parameter via "
                + "<code>URLBuilder.setTimeString()</code> and forcing a cache flush with "
                + "<code>setExpiryTime()</code>. Uses a 30-entry LRU cache per time step to avoid re-fetching.")
            + section("What You'll See",
                "<ul>"
                + "<li>Date-stamped satellite imagery that changes as you step through 366 daily time steps</li>"
                + "<li>MODIS true-colour, VIIRS imagery, fire hotspot overlays, and snow cover maps</li>"
                + "<li>Smooth animation when using the play controls</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li><b>Layer dropdown</b> &mdash; select MODIS Terra, VIIRS S-NPP, Fires, or Snow Cover</li>"
                + "<li><b>Opacity slider</b> (0&ndash;100%) &mdash; adjust layer transparency</li>"
                + "<li><b>Time slider</b> (0&ndash;365) &mdash; scrub through all days of 2024</li>"
                + "<li><b>|&lt;</b> &mdash; jump to first date; <b>&gt;</b> &mdash; play forward; <b>&gt;|</b> &mdash; jump to last date</li>"
                + "<li><b>Speed buttons</b>: 1x, 2x, 4x playback speed</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>WMSTiledImageLayer</code> &mdash; with TIME dimension parameter</li>"
                + "<li><code class='api-method'>urlBuilder.setTimeString(\"2024-03-15\")</code> &mdash; set the WMS TIME parameter</li>"
                + "<li><code class='api-method'>layer.setExpiryTime(System.currentTimeMillis())</code> &mdash; forces cache flush "
                + "so tiles are re-fetched with the new time parameter</li>"
                + "<li>NASA GIBS (Global Imagery Browse Services) WMS endpoint at "
                + "<code>https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi</code></li>"
                + "</ul>"));

        put("LayerOpacityAndBlendingDemo",
            section("Overview",
                "Per-layer opacity sliders demonstrating BMNG and Landsat layer blending. Shows how to "
                + "blend multiple imagery layers with adjustable transparency. Each layer has a visibility "
                + "toggle and an opacity slider (0&ndash;100%). Calls <code>layer.setOpacity(value/100.0)</code> "
                + "and <code>setExpiryTime(now)</code> to flush cached tiles and force a re-render with "
                + "the new opacity. Also includes move up/down buttons for reordering.")
            + section("What You'll See",
                "<ul>"
                + "<li>Multiple imagery layers (BMNG + Landsat) blended with adjustable opacity</li>"
                + "<li>Smooth blending as you drag opacity sliders</li>"
                + "<li>Layer ordering controls to change compositing priority</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Toggle visibility checkboxes to show/hide individual layers</li>"
                + "<li>Drag opacity sliders (0&ndash;100%) to blend layers</li>"
                + "<li>Use move up/down buttons to change layer compositing order</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-method'>layer.setOpacity(value / 100.0)</code> &mdash; 0.0 (transparent) to 1.0 (opaque)</li>"
                + "<li><code class='api-method'>layer.setExpiryTime(System.currentTimeMillis())</code> &mdash; flush cached tiles "
                + "to force re-render with new opacity value</li>"
                + "<li>Layers are composited in list order (bottom of list drawn first, top drawn last)</li>"
                + "</ul>"));

        put("WCSElevations",
            section("Overview",
                "Loads elevation data from a WCS (Web Coverage Service) 1.0.0 endpoint and integrates it "
                + "into the terrain model. Demonstrates how to add external high-resolution elevation sources "
                + "that augment or replace the default terrain data.")
            + section("What You'll See",
                "<ul>"
                + "<li>Terrain elevation loaded from a WCS server</li>"
                + "<li>Higher-resolution terrain detail in the WCS coverage area compared to surrounding regions</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to the WCS coverage area to see enhanced terrain detail</li>"
                + "<li>Tilt the view to see the elevation differences clearly</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>WCSElevationModel</code> &mdash; elevation model backed by a WCS endpoint</li>"
                + "<li><code class='api-class'>WCSCoveragePanel</code> &mdash; UI panel for browsing available WCS coverages</li>"
                + "</ul>"));

        // Modified by seaglassfoundry.com - updated docs to note defunct NASA WFS server
        put("PlaceNames",
            section("Overview",
                "<b>Note:</b> This example is non-functional. The NASA WFS place-name server "
                + "(worldwind22.arc.nasa.gov) is no longer available, and the data format (GML2-GZIP) "
                + "was NASA-specific with no standardized replacement.<br><br>"
                + "Originally displayed the NASA place-name label layer with density controls. Labels "
                + "appeared at appropriate zoom levels based on feature importance &mdash; country names "
                + "at wide zoom, city names at medium zoom, and local features at close zoom.")
            + section("What You'll See",
                "<ul>"
                + "<li>The Place Names control panel will appear but no labels will render on the globe</li>"
                + "<li>The layer cannot fetch data because the NASA WFS server no longer exists</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>PlaceNameLayer</code> &mdash; density-controlled place name rendering "
                + "with zoom-dependent visibility</li>"
                + "</ul>"));

        put("OnScreenLayerManager",
            section("Overview",
                "A layer manager rendered as an in-globe overlay widget, providing layer visibility "
                + "controls without requiring a separate Swing panel. The overlay is rendered inside "
                + "the WorldWindow canvas itself.")
            + section("What You'll See",
                "<ul>"
                + "<li>A semi-transparent layer manager overlay on the globe canvas</li>"
                + "<li>Checkboxes for each layer's visibility</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Click checkboxes in the overlay to toggle layers on and off</li>"
                + "<li>The overlay is interactive and rendered inside the WorldWindow canvas</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>LayerManagerLayer</code> &mdash; renders layer controls as an in-canvas overlay</li>"
                + "</ul>"));

        put("LayerTreeUsage",
            section("Overview",
                "Hierarchical layer tree with expandable groups. Layers are automatically categorised "
                + "into <b>Background</b> (stars, sky, BMNG), <b>Imagery</b> (Landsat, NAIP, Bing), "
                + "<b>Overlays</b> (boundaries, Earth at Night), and <b>Controls</b> (compass, scalebar, "
                + "world map) &mdash; demonstrating how to build a real tree rather than a flat list.")
            + section("What You'll See",
                "<ul>"
                + "<li>Four expandable group nodes, each containing related layers</li>"
                + "<li>Checkboxes on leaf nodes for toggling individual layer visibility</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Expand/collapse groups by clicking the expand arrows</li>"
                + "<li>Toggle individual layers via checkboxes</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>LayerTree</code> &mdash; on-screen tree component</li>"
                + "<li><code class='api-class'>BasicTreeNode</code> &mdash; group nodes for organising layers</li>"
                + "<li><code class='api-class'>LayerTreeNode</code> &mdash; leaf nodes wrapping <code>Layer</code> objects</li>"
                + "</ul>"));

        // ── Terrain ─────────────────────────────────────────────────────────────

        put("GPUTerrainDemo",
            section("Overview",
                "Phase 4 GPU terrain rendering demonstration with four selectable modes: "
                + "<b>CPU</b> (traditional GL 1.x fixed-function), "
                + "<b>Shader</b> (GL 3.0+ vertex displacement), "
                + "<b>Tessellation</b> (GL 4.0+ hardware tessellation), and "
                + "<b>Compute+Tess</b> (GL 4.3+ compute-shader mesh generation combined with tessellation). "
                + "Includes camera presets for dramatic terrain views: Grand Canyon (36.10&deg;N, -112.10&deg;W, 45km), "
                + "Rocky Mountains (39.50&deg;N, -106.00&deg;W, 200km), Everest (27.99&deg;N, 86.93&deg;E, 120km), "
                + "Alps (46.50&deg;N, 8.50&deg;E, 500km), and World. Real-time stats show Mode, FPS, Frame time, "
                + "Tile count, and Triangle estimate (tileCount &times; 578 &times; tessMultiplier). "
                + "A one-shot <code>RenderingListener</code> detects GPU capabilities after the first frame.")
            + section("What You'll See",
                "<ul>"
                + "<li>A globe with selectable terrain rendering modes in the sidebar</li>"
                + "<li>Real-time FPS counter and performance statistics</li>"
                + "<li>Camera presets for dramatic terrain at Grand Canyon, Rockies, Everest, Alps</li>"
                + "<li>Triangle estimate: <code>tileCount &times; 578 &times; (tessellation ? 32 : 1)</code></li>"
                + "<li>Terrain quality adapts to view distance with crack-free tile stitching</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li><b>Mode selector</b> &mdash; switch between CPU, Shader, Tessellation, Compute+Tess</li>"
                + "<li><b>Camera presets</b> &mdash; jump to Grand Canyon, Rocky Mts, Everest, Alps, or World view</li>"
                + "<li><b>Wireframe toggle</b> &mdash; visualise the mesh topology and tile boundaries</li>"
                + "<li>Zoom into mountainous terrain to see tessellation detail and adaptive LOD</li>"
                + "<li>Compare FPS between modes to measure GPU terrain performance on your hardware</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>GPUTerrainTessellator</code> &mdash; Phase 4 GPU tessellation implementation "
                + "with crack-free stitching algorithm</li>"
                + "<li>Terrain mode enum: <code>CPU</code>, <code>SHADER</code>, <code>TESSELLATION</code>, "
                + "<code>COMPUTE_TESSELLATION</code></li>"
                + "<li><code class='api-class'>RenderingListener</code> &mdash; one-shot listener to detect GPU capabilities "
                + "after first frame render</li>"
                + "<li class='note'>Note: Tessellation and Compute+Tess modes require GL 4.0+ / 4.3+ respectively.</li>"
                + "</ul>"));

        put("TerrainProfiler",
            section("Overview",
                "An interactive cross-section elevation profile along a user-drawn path. Click points on "
                + "the globe to define a path, and a real-time elevation profile chart shows the terrain "
                + "cross-section. Uses <code class='api-class'>TerrainProfileLayer</code> for rendering.")
            + section("What You'll See",
                "<ul>"
                + "<li>A profile chart showing elevation along the drawn path</li>"
                + "<li>The path rendered on the globe surface</li>"
                + "<li>Elevation values at each sample point along the profile</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Draw a path on the globe to generate the elevation profile</li>"
                + "<li>Hover over the chart to see elevation at specific points along the path</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>TerrainProfileLayer</code> &mdash; renders the elevation profile chart</li>"
                + "<li><code class='api-method'>globe.getElevation(lat, lon)</code> &mdash; sample terrain elevation at any position</li>"
                + "</ul>"));

        put("TerrainIntersections",
            section("Overview",
                "Computes ray-terrain intersections for line-of-sight analysis. <b>Shift+Click</b> generates "
                + "a 10&times;10 grid of sight lines (grid radius 0.05&deg;, target resolution 10m) using "
                + "<code class='api-class'>HighResolutionTerrain</code> with a 4-thread pool. "
                + "<b>Ctrl+Click</b> cancels a running computation; <b>Alt+Click</b> reruns it. "
                + "Progress bar updates every 250ms on the EDT.")
            + section("What You'll See",
                "<ul>"
                + "<li>Yellow grid points marking the target positions</li>"
                + "<li>A red marker at the centre (observer position)</li>"
                + "<li>Green sight lines for clear line-of-sight</li>"
                + "<li>Cyan points marking terrain intersection locations</li>"
                + "<li>A progress bar during computation</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li><b>Shift+Click</b> &mdash; generate a 10&times;10 grid of terrain intersection tests from that position</li>"
                + "<li><b>Ctrl+Click</b> &mdash; cancel a running computation</li>"
                + "<li><b>Alt+Click</b> &mdash; rerun the computation at the same position</li>"
                + "<li>Navigate to view intersection results from different angles</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>HighResolutionTerrain</code> &mdash; high-resolution terrain sampling with "
                + "configurable target resolution (10m in this example)</li>"
                + "<li><code class='api-method'>terrain.intersect(Position, Position)</code> &mdash; compute where a line "
                + "segment intersects the terrain surface</li>"
                + "<li>Uses a 4-thread <code>ExecutorService</code> for parallel intersection computation</li>"
                + "<li>Progress bar updated every 250ms via <code>SwingUtilities.invokeLater()</code></li>"
                + "</ul>"));

        put("TerrainRenderingBenchmark",
            section("Overview",
                "Benchmarks terrain tile loading and rendering throughput. Measures how fast the terrain "
                + "pipeline processes tiles at various detail levels by cycling through predefined views.")
            + section("What You'll See",
                "<ul>"
                + "<li>A globe that cycles through terrain views to measure rendering performance</li>"
                + "<li>Console output with timing statistics for each view</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Let the benchmark run to completion and check console output for results</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li>Terrain tessellation and tile loading pipeline internals</li>"
                + "</ul>"));



        put("GetBestElevations",
            section("Overview",
                "Queries the best available elevation for a set of geographic positions. Demonstrates "
                + "how to programmatically obtain the highest-resolution elevation data available from "
                + "the configured elevation models.")
            + section("What You'll See",
                "<ul>"
                + "<li>Elevation values reported for specified positions in the console output</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Results are printed to the console; no interactive UI</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-method'>globe.getBestElevation(lat, lon)</code> &mdash; query the highest-resolution "
                + "elevation available at a geographic position</li>"
                + "</ul>"));

        put("ElevationsAllZero",
            section("Overview",
                "Demonstrates globe behaviour when the elevation model returns all zeros &mdash; a perfectly "
                + "flat Earth. Useful for understanding the elevation model's role in terrain rendering "
                + "and for testing overlays on flat terrain.")
            + section("What You'll See",
                "<ul>"
                + "<li>A completely flat globe with no terrain relief at all</li>"
                + "<li>All land areas appear at sea level</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate and tilt to verify all terrain is perfectly flat at sea level</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ZeroElevationModel</code> &mdash; returns 0.0 for all position queries</li>"
                + "</ul>"));

        put("BathymetryRemoval",
            section("Overview",
                "Strips bathymetry (negative elevations) from the active elevation model. Clamps all "
                + "ocean floor elevations to zero so the ocean appears flat at sea level while land "
                + "elevations remain normal. Useful for applications that don't need underwater terrain.")
            + section("What You'll See",
                "<ul>"
                + "<li>Ocean areas rendered flat at sea level (no underwater terrain detail)</li>"
                + "<li>Land elevations remain normal with full relief</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to ocean areas (e.g. mid-ocean ridges) to see the flat bathymetry effect</li>"
                + "<li>Compare with the default model which shows underwater terrain</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>BathymetryFilterElevationModel</code> &mdash; wraps another elevation model "
                + "and clamps any negative elevation values to zero</li>"
                + "</ul>"));

        put("EGM96Offsets",
            section("Overview",
                "Applies EGM96 (Earth Gravitational Model 1996) geoid-undulation offsets to elevation queries. "
                + "EGM96 provides the correction between GPS ellipsoidal heights (WGS84) and mean sea level "
                + "(orthometric) heights. Essential for applications that need accurate MSL elevations from GPS data.")
            + section("What You'll See",
                "<ul>"
                + "<li>Elevation values adjusted by the EGM96 geoid undulation at each position</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Compare raw (ellipsoidal) and EGM96-adjusted (orthometric) elevation values</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>EGM96</code> &mdash; geoid undulation model</li>"
                + "<li><code class='api-method'>EGM96.getOffset(lat, lon)</code> &mdash; returns the geoid undulation in meters "
                + "at the given position</li>"
                + "</ul>"));

        put("EGM2008Offsets",
            section("Overview",
                "Applies EGM2008 geoid-undulation offsets &mdash; a higher-resolution successor to EGM96 "
                + "with finer spatial detail. Provides more accurate GPS-to-MSL height conversions, "
                + "especially in mountainous or coastal areas.")
            + section("What You'll See",
                "<ul>"
                + "<li>Elevation values adjusted by the EGM2008 geoid model</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Compare raw and EGM2008-adjusted values; notice finer corrections than EGM96</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li>Similar API to EGM96 but with higher-resolution geoid data (2.5-arcminute grid)</li>"
                + "</ul>"));

        // ── Data Formats ────────────────────────────────────────────────────────

        put("Shapefiles",
            section("Overview",
                "Load and render ESRI shapefile point, polyline, and polygon features on the globe. "
                + "Demonstrates the full shapefile loading pipeline from file to rendered shapes, "
                + "supporting all standard geometry types including multi-part features.")
            + section("What You'll See",
                "<ul>"
                + "<li>Shapefile features rendered on the globe as points, lines, and filled polygons</li>"
                + "<li>Multiple geometry types from the shapefile data</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to the data area to view loaded features</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ShapefileLoader</code> &mdash; loads .shp, .dbf, and .shx files</li>"
                + "<li><code class='api-class'>ShapefileLayerFactory</code> &mdash; creates renderable layers from shapefiles</li>"
                + "<li>Supports geometry types: Point, PolyLine, Polygon, MultiPoint, MultiPolyLine, MultiPolygon</li>"
                + "</ul>"));

        put("ShapefileViewer",
            section("Overview",
                "Interactive viewer for shapefiles with attribute-table inspection. Load any shapefile "
                + "from disk and explore its features visually on the globe while inspecting individual "
                + "feature attributes from the DBF file.")
            + section("What You'll See",
                "<ul>"
                + "<li>A file chooser dialog to select a shapefile</li>"
                + "<li>Shapefile features rendered on the globe</li>"
                + "<li>Click any feature to see its attribute data from the DBF table</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Use <b>File &rarr; Open</b> to load a shapefile (.shp)</li>"
                + "<li>Click on features to inspect their DBF attributes in a popup</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ShapefileLoader</code> &mdash; loads shapefile geometry and DBF attributes</li>"
                + "<li><code class='api-class'>ShapefileRecord</code> &mdash; access to individual feature attributes by field name</li>"
                + "</ul>"));

        put("ShapefileAttributeGroups",
            section("Overview",
                "Style shapefile features into visual groups by attribute value. Apply different colours "
                + "or styles to features based on their DBF attribute data &mdash; for example, colour-coding "
                + "countries by continent or roads by type.")
            + section("What You'll See",
                "<ul>"
                + "<li>Shapefile features coloured by attribute category</li>"
                + "<li>A legend showing the attribute-to-colour mapping</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to see the attribute-based styling of different feature groups</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ShapefileRecord</code> &mdash; read attribute values for grouping decisions</li>"
                + "<li>Apply different <code class='api-class'>ShapeAttributes</code> (colour, fill, opacity) based on attribute values</li>"
                + "</ul>"));

        put("ShapefileBenchmark",
            section("Overview",
                "Benchmarks shapefile loading and rendering performance for large datasets. "
                + "Tests throughput to identify bottlenecks in the shapefile pipeline.")
            + section("What You'll See",
                "<ul>"
                + "<li>A large shapefile loaded and rendered on the globe</li>"
                + "<li>Console output with loading time and render statistics</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Watch the console for performance metrics (load time, feature count, render FPS)</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ShapefileLoader</code> &mdash; performance-critical loading pipeline</li>"
                + "</ul>"));

        put("ExtrudedPolygonsFromShapefile",
            section("Overview",
                "Extrude shapefile polygon features into 3D building volumes. Heights are derived from "
                + "shapefile DBF attribute data (e.g. number of floors, building height), creating a "
                + "3D cityscape from 2D footprint data.")
            + section("What You'll See",
                "<ul>"
                + "<li>Building footprints extruded into 3D volumes on the globe</li>"
                + "<li>Heights driven by attribute data from the shapefile</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Tilt the view to see the 3D building volumes in perspective</li>"
                + "<li>Zoom into the city area for detail</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ExtrudedPolygon</code> &mdash; 3D volume from polygon footprint + height</li>"
                + "<li><code class='api-class'>ShapefileLoader</code> &mdash; read height attribute from DBF fields</li>"
                + "</ul>"));

        put("GeoJSONViewer",
            section("Overview",
                "Load and inspect GeoJSON files with support for Point, LineString, Polygon, and "
                + "Multi-* geometry types. Click features to see their GeoJSON properties in a popup. "
                + "A general-purpose viewer for any GeoJSON data source.")
            + section("What You'll See",
                "<ul>"
                + "<li>GeoJSON features rendered on the globe as points, lines, and polygons</li>"
                + "<li>Click any feature to see its GeoJSON properties in a popup panel</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Load a GeoJSON file via the file chooser</li>"
                + "<li>Click features to inspect their properties</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>GeoJSONLoader</code> &mdash; parses GeoJSON FeatureCollections into WorldWind shapes</li>"
                + "<li>Uses Jackson JSON parser for GeoJSON feature/geometry parsing</li>"
                + "</ul>"));

        put("ColladaViewer",
            section("Overview",
                "Load and display COLLADA (.dae) 3D model files on the globe. COLLADA is a standard "
                + "XML-based format for exchanging 3D assets including geometry, textures, materials, "
                + "and animations.")
            + section("What You'll See",
                "<ul>"
                + "<li>A 3D model rendered at a geographic position on the globe</li>"
                + "<li>Model geometry, textures, and materials from the COLLADA file</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to the model's geographic position</li>"
                + "<li>Tilt and zoom to inspect the 3D model from different angles</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ColladaRoot</code> &mdash; root of a parsed COLLADA scene graph</li>"
                + "<li><code class='api-class'>ColladaController</code> &mdash; manages COLLADA rendering and animation</li>"
                + "</ul>"));

        put("SurfaceImages",
            section("Overview",
                "Drape georeferenced images on terrain using <code class='api-class'>SurfaceImage</code>. "
                + "Images are projected onto the terrain surface within specified geographic bounds (sector), "
                + "conforming to terrain relief.")
            + section("What You'll See",
                "<ul>"
                + "<li>Images draped on the terrain at specific geographic locations</li>"
                + "<li>Images conform to the terrain surface curvature and relief</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to the image locations on the globe</li>"
                + "<li>Zoom in to see terrain conformance detail</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>SurfaceImage</code> &mdash; georeferenced image draped on terrain surface</li>"
                + "<li><code class='api-method'>new SurfaceImage(imageSource, sector)</code> &mdash; create from image path/URL "
                + "and a <code>Sector</code> defining the geographic bounds</li>"
                + "</ul>"));

        put("SurfaceImageViewer",
            section("Overview",
                "Interactive viewer for georeferenced surface image layers. Load multiple surface images, "
                + "manage their visibility, adjust opacity, and control layer ordering.")
            + section("What You'll See",
                "<ul>"
                + "<li>A control panel for managing loaded surface images</li>"
                + "<li>Images rendered on the globe at their geographic locations</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Load surface images via the file chooser</li>"
                + "<li>Toggle visibility and adjust layer ordering</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>SurfaceImage</code> &mdash; georeferenced surface image</li>"
                + "</ul>"));

        put("RubberSheetImage",
            section("Overview",
                "Stretch and reposition a surface image interactively by dragging its corner control points. "
                + "Demonstrates interactive georeferencing of an image on the globe &mdash; useful for aligning "
                + "scanned maps or aerial photos to geographic coordinates.")
            + section("What You'll See",
                "<ul>"
                + "<li>An image on the globe with draggable corner handles</li>"
                + "<li>Drag corners to stretch, rotate, and reposition the image interactively</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Drag the corner control points to reposition and stretch the image</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>SurfaceImage</code> &mdash; with interactive corner editing</li>"
                + "<li><code class='api-class'>SurfaceImageEditor</code> &mdash; control-point editing for surface images</li>"
                + "</ul>"));

        put("VPFLayerDemo",
            section("Overview",
                "Render VPF (Vector Product Format) vector data layers. VPF is a US military standard "
                + "for geographic vector data used in VMAP (Vector MAP) and DNCP products. Includes "
                + "roads, boundaries, coastlines, and other feature types.")
            + section("What You'll See",
                "<ul>"
                + "<li>VPF vector features rendered on the globe</li>"
                + "<li>Multiple feature types: roads, boundaries, coastlines, political boundaries</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Toggle VPF feature layers on and off to isolate specific feature types</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>VPFLayer</code> &mdash; renders VPF data on the globe</li>"
                + "<li><code class='api-class'>VPFDatabase</code> &mdash; reads VPF coverage and feature tables</li>"
                + "</ul>"));

        put("kml.KMLViewer",
            section("Overview",
                "Load and display KML and KMZ files from disk or URL. Supports placemarks, ground overlays, "
                + "screen overlays, paths, polygons, network links, and other KML features. "
                + "Includes a tree panel showing the KML document structure with expand/collapse navigation.")
            + section("What You'll See",
                "<ul>"
                + "<li>KML features rendered on the globe: placemarks, paths, polygons, ground overlays</li>"
                + "<li>A tree panel showing the hierarchical KML document structure</li>"
                + "<li>Balloon popups with KML description content</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Open a KML/KMZ file or URL via the file menu</li>"
                + "<li>Click features on the globe to see their KML descriptions in a balloon popup</li>"
                + "<li>Expand the tree panel to navigate the KML document structure</li>"
                + "<li>Click tree nodes to fly to that feature's location</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>KMLRoot</code> &mdash; parses and represents a complete KML/KMZ document</li>"
                + "<li><code class='api-class'>KMLController</code> &mdash; renders KML features on the globe</li>"
                + "<li><code class='api-class'>KMLAbstractFeature</code> &mdash; base class for all KML feature types</li>"
                + "</ul>"));

        // ── Picking ─────────────────────────────────────────────────────────────

        put("DeepPicking",
            section("Overview",
                "Pick shapes that are visually obscured by other shapes. Normal picking returns only the "
                + "topmost shape at the cursor; deep picking returns <em>all</em> shapes under the cursor "
                + "regardless of visual occlusion. Requires two settings: "
                + "<code>setDeepPickEnabled(true)</code> on the <code>SceneController</code>, and "
                + "<code>setEnableBatchPicking(false)</code> on each individual airspace to prevent "
                + "batch-picking optimization from skipping occluded shapes.")
            + section("What You'll See",
                "<ul>"
                + "<li>Overlapping airspace shapes stacked on the globe</li>"
                + "<li>Information panel showing ALL shapes under the cursor, not just the topmost</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Hover over overlapping shapes to see the deep-pick results in the info panel</li>"
                + "<li><code>event.getObjects()</code> returns the complete <code>PickedObjectList</code> with all hits</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-method'>sceneController.setDeepPickEnabled(true)</code> &mdash; enable deep picking mode "
                + "on the scene controller</li>"
                + "<li><code class='api-method'>airspace.setEnableBatchPicking(false)</code> &mdash; must be set on each airspace "
                + "to prevent the batch-picking optimisation from skipping it</li>"
                + "<li><code class='api-method'>event.getObjects()</code> &mdash; returns a <code>PickedObjectList</code> containing "
                + "ALL picked objects, ordered by depth (front to back)</li>"
                + "</ul>"));

        put("IconPicking",
            section("Overview",
                "Efficiently pick from a large icon layer with thousands of icons. Demonstrates WorldWind's "
                + "optimised icon picking for high-density datasets without performance degradation.")
            + section("What You'll See",
                "<ul>"
                + "<li>Thousands of icons scattered across the globe</li>"
                + "<li>Icons highlight on hover and show details on click</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Hover over icons to highlight them</li>"
                + "<li>Click to select and see icon details in the info panel</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>IconLayer</code> &mdash; optimised layer for rendering and picking thousands of icons</li>"
                + "<li><code class='api-class'>WWIcon</code> &mdash; icon with position, image, and metadata attributes</li>"
                + "</ul>"));

        put("PickFrustum",
            section("Overview",
                "Visualises the pick frustum &mdash; the 3D volume used to determine which objects can "
                + "potentially be picked at the cursor location. Only objects within this frustum are "
                + "tested for picking, which is how WorldWind maintains picking performance.")
            + section("What You'll See",
                "<ul>"
                + "<li>Visualisation of the pick frustum volume around the cursor</li>"
                + "<li>Only objects within the frustum respond to picking</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Move the cursor to see the pick frustum follow it</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>PickSupport</code> &mdash; manages the pick frustum and colour-buffer picking pipeline</li>"
                + "</ul>"));

        put("SectorSelection",
            section("Overview",
                "Select all shapes within a dragged geographic sector. Draw a rectangular lat/lon-aligned "
                + "sector on the globe and retrieve all shapes whose bounds intersect with it.")
            + section("What You'll See",
                "<ul>"
                + "<li>Shapes scattered on the globe</li>"
                + "<li>Drag to define a rectangular selection sector</li>"
                + "<li>Shapes within the sector are highlighted</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Hold a modifier key (Shift or Ctrl) and drag to define the selection sector</li>"
                + "<li>Shapes within the selected sector become highlighted</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>SectorSelector</code> &mdash; interactive sector selection tool</li>"
                + "<li><code class='api-class'>Sector</code> &mdash; geographic bounding rectangle (min/max lat, min/max lon)</li>"
                + "</ul>"));

        put("ScreenSelection",
            section("Overview",
                "Select shapes within a rubber-band screen rectangle. Unlike sector selection, this works "
                + "in screen (pixel) coordinates. Uses a grid of 468 <code>ExtrudedPolygon</code>s "
                + "(18&times;26, 10&deg; spacing, 2&deg; square, 100km height) and a "
                + "<code class='api-class'>SelectionHighlightController</code> that handles both hover "
                + "highlighting and rectangle selection. Listens for <code>ScreenSelector.SELECTION_STARTED</code> "
                + "and <code>SELECTION_CHANGED</code> messages.")
            + section("What You'll See",
                "<ul>"
                + "<li>A grid of 468 extruded polygon pillars covering the globe</li>"
                + "<li>Drag to draw a selection rectangle on screen</li>"
                + "<li>All shapes within the rectangle become highlighted</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Hold a modifier key and drag to draw a selection rectangle</li>"
                + "<li>All extruded polygons within the rectangle are selected and highlighted</li>"
                + "<li>Hover over individual shapes to highlight them</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ScreenSelector</code> &mdash; rubber-band screen rectangle selection tool</li>"
                + "<li><code class='api-class'>SelectionHighlightController</code> &mdash; handles both hover and rectangle selection</li>"
                + "<li>Messages: <code>ScreenSelector.SELECTION_STARTED</code>, <code>SELECTION_CHANGED</code></li>"
                + "</ul>"));

        put("ScreenImageDragging",
            section("Overview",
                "Drag screen-space image overlays with the mouse. Demonstrates interactive screen-space "
                + "elements (HUD images, logos, legends) that can be repositioned by the user.")
            + section("What You'll See",
                "<ul>"
                + "<li>Image overlays in screen space that can be dragged to new positions</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Left-drag screen images to reposition them within the viewport</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ScreenImage</code> &mdash; image rendered in screen coordinates (fixed in viewport)</li>"
                + "</ul>"));

        put("ContextMenusOnShapes",
            section("Overview",
                "Right-click context menus attached to picked shapes. Demonstrates integrating Swing "
                + "<code>JPopupMenu</code> with the WorldWind pick system. Places 5 "
                + "<code class='api-class'>PointPlacemark</code>s with yellow push-pin icons at scale 0.6. "
                + "Each shape has a <code>ContextMenuInfo</code> object storing a title and menu items. "
                + "Detection uses <code>SelectEvent.RIGHT_PRESS</code> with <code>event.getAwtMousePt()</code> "
                + "for popup positioning.")
            + section("What You'll See",
                "<ul>"
                + "<li>5 placemark pins on the globe</li>"
                + "<li>Right-click any pin to open a context menu with shape-specific options</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li><b>Right-click</b> on a placemark pin to open its context menu</li>"
                + "<li>Select a menu item to perform the associated action</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>SelectListener</code> &mdash; detect <code>SelectEvent.RIGHT_PRESS</code> events</li>"
                + "<li><code class='api-method'>event.getAwtMousePt()</code> &mdash; get screen position for popup placement</li>"
                + "<li><code class='api-class'>JPopupMenu</code> &mdash; Swing popup menu shown at the pick location</li>"
                + "<li>Store custom data in shapes via <code>setValue(key, contextMenuInfo)</code></li>"
                + "</ul>"));

        // ── Annotations ─────────────────────────────────────────────────────────

        put("Annotations",
            section("Overview",
                "Demonstrates all annotation types: text, image, HTML, and screen-space balloons. "
                + "Annotations are rich-content labels attached to either globe positions "
                + "(<code>GlobeAnnotation</code>) or screen positions (<code>ScreenAnnotation</code>). "
                + "Fully configurable fonts, backgrounds, borders, sizes, and opacity.")
            + section("What You'll See",
                "<ul>"
                + "<li>Various annotation types at globe positions</li>"
                + "<li>Text annotations with configurable fonts and styled backgrounds</li>"
                + "<li>Image and HTML-content annotations</li>"
                + "<li>Screen-space annotations fixed in the viewport</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to see annotations at different geographic positions</li>"
                + "<li>Some annotations are interactive and respond to clicks</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>GlobeAnnotation</code> &mdash; annotation anchored at a geographic lat/lon/alt position</li>"
                + "<li><code class='api-class'>ScreenAnnotation</code> &mdash; annotation anchored at a screen pixel position</li>"
                + "<li><code class='api-class'>AnnotationAttributes</code> &mdash; font, background colour, border colour/width, "
                + "size, opacity, corner radius, leader line style</li>"
                + "<li><code class='api-class'>AnnotationLayer</code> &mdash; layer containing annotation renderables</li>"
                + "</ul>"));

        put("AnnotationControls",
            section("Overview",
                "Annotations with embedded Swing controls rendered inside the balloon. Demonstrates "
                + "interactive annotations containing buttons, sliders, text fields, and other widgets "
                + "that respond to user input.")
            + section("What You'll See",
                "<ul>"
                + "<li>Annotations containing interactive Swing widgets (buttons, sliders)</li>"
                + "<li>Clickable controls inside annotation balloons</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Click on controls inside annotations to interact with them</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>DialogAnnotation</code> &mdash; annotation with embedded Swing controls</li>"
                + "<li><code class='api-class'>ButtonAnnotation</code> &mdash; clickable button rendered inside an annotation</li>"
                + "</ul>"));

        put("Balloons",
            section("Overview",
                "HTML balloon annotations attached to globe positions. Balloons can display rich HTML "
                + "content including images, links, tables, and formatted text. They appear as popup "
                + "balloons with a leader line pointing to their anchor position.")
            + section("What You'll See",
                "<ul>"
                + "<li>Balloon popups with rich HTML content at globe positions</li>"
                + "<li>Rich formatting: images, tables, styled text, hyperlinks</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Click on placemarks to open their balloon</li>"
                + "<li>Interact with HTML content inside the balloon (scroll, click links)</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>GlobeBrowserBalloon</code> &mdash; HTML balloon at a geographic position</li>"
                + "<li><code class='api-class'>ScreenBrowserBalloon</code> &mdash; HTML balloon at a screen position</li>"
                + "</ul>"));

        put("WebBrowserBalloons",
            section("Overview",
                "Balloon annotations with a full embedded web browser. Navigate real web pages directly "
                + "inside a WorldWind annotation balloon.")
            + section("What You'll See",
                "<ul>"
                + "<li>A balloon containing a full web browser with page navigation</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Click links inside the balloon to navigate web pages</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>GlobeBrowserBalloon</code> &mdash; balloon with embedded web browser</li>"
                + "</ul>"));

        put("GlobeAnnotationExample",
            section("Overview",
                "A basic globe-positioned text annotation with attribute styling. The simplest possible "
                + "annotation example &mdash; just text at a geographic position with configurable font, "
                + "colour, and background.")
            + section("What You'll See",
                "<ul>"
                + "<li>A text annotation attached to a specific lat/lon position on the globe</li>"
                + "<li>Styled with configurable font, text colour, and background attributes</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to the annotation's geographic position</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>GlobeAnnotation</code> &mdash; text annotation at a lat/lon position</li>"
                + "<li><code class='api-class'>AnnotationAttributes</code> &mdash; font, colour, background, border, opacity</li>"
                + "</ul>"));

        put("AlarmIcons",
            section("Overview",
                "Icons that cycle through visual alarm states using programmatic shape generation and "
                + "timer-based animation. Uses <code class='api-method'>PatternFactory.createPattern()</code> to "
                + "generate icon shapes (CIRCLE, TRIANGLE_UP, SQUARE) and "
                + "<code class='api-method'>PatternFactory.blur()</code> with 4 passes at radius 13 for a glow effect. "
                + "Timer-based pulsing uses scale arrays and flashing uses visibility toggle arrays. "
                + "10 radio button options select different alarm state configurations.")
            + section("What You'll See",
                "<ul>"
                + "<li>Icons that change colour, size, and appearance based on alarm state</li>"
                + "<li>Pulsing animation (icons grow and shrink cyclically)</li>"
                + "<li>Flashing animation (icons toggle visibility on/off)</li>"
                + "<li>Glow effects from blurred pattern outlines</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Select one of 10 alarm states via radio buttons to see different animation patterns</li>"
                + "<li>Watch icons animate through their alarm cycle</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-method'>PatternFactory.createPattern(type, color)</code> &mdash; generates icon shapes: "
                + "<code>CIRCLE</code>, <code>TRIANGLE_UP</code>, <code>SQUARE</code></li>"
                + "<li><code class='api-method'>PatternFactory.blur(image, passes, radius)</code> &mdash; blur for glow effect "
                + "(4 passes, radius 13)</li>"
                + "<li>Timer-based animation using scale arrays for pulsing and toggle arrays for flashing</li>"
                + "</ul>"));

        put("Placemarks",
            section("Overview",
                "Demonstrates 11 <code class='api-class'>PointPlacemark</code> configurations: clamp to ground, "
                + "absolute altitude, relative to ground, heading rotation (globe-relative vs screen-relative), "
                + "no-image point mode (scale 5&ndash;10), dateline placement, and north pole placement. "
                + "Shows the full range of <code class='api-class'>PointPlacemarkAttributes</code> including "
                + "<code>ImageOffset</code>, <code>LabelOffset</code>, <code>Scale</code>, "
                + "<code>HeadingReference</code>, and <code>LineMaterial</code>.")
            + section("What You'll See",
                "<ul>"
                + "<li>11 placemarks demonstrating every major configuration option</li>"
                + "<li>Placemarks at ground level, at altitude, and at extreme locations (dateline, pole)</li>"
                + "<li>Rotated icons with both globe-relative and screen-relative heading</li>"
                + "<li>Point-mode placemarks (no image, just a coloured dot) at various scales</li>"
                + "<li>Labels positioned beside or below each placemark</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to view placemarks at different locations around the globe</li>"
                + "<li>Hover to highlight; click to select</li>"
                + "<li>Rotate the view to see how globe-relative vs screen-relative heading differs</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>PointPlacemark</code> &mdash; the primary placemark class for marking positions</li>"
                + "<li><code class='api-class'>PointPlacemarkAttributes</code> &mdash; icon image path, label font, scale, "
                + "<code>ImageOffset</code>, <code>LabelOffset</code></li>"
                + "<li><code class='api-method'>attrs.setHeadingReference(AVKey.RELATIVE_TO_GLOBE)</code> &mdash; icon rotates "
                + "with the globe vs <code>RELATIVE_TO_SCREEN</code> which stays fixed</li>"
                + "<li><code class='api-method'>attrs.setScale(5.0)</code> &mdash; for point mode (no image), controls dot size</li>"
                + "<li><code class='api-method'>attrs.setLineMaterial(material)</code> &mdash; colour of the leader line from icon to ground</li>"
                + "</ul>"));

        put("PlacemarkDecluttering",
            section("Overview",
                "Automatic decluttering of dense placemark layers for readability. When many placemarks "
                + "overlap at the current zoom level, the system automatically hides lower-priority ones "
                + "to prevent visual clutter. Zoom in to reveal more placemarks as overlap reduces.")
            + section("What You'll See",
                "<ul>"
                + "<li>Dense placemark clusters that automatically declutter with zoom</li>"
                + "<li>Higher-priority placemarks stay visible; lower-priority ones fade/hide</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Zoom in to reveal more placemarks as overlap reduces</li>"
                + "<li>Zoom out to see decluttering remove lower-priority placemarks</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-method'>placemark.setEnableDecluttering(true)</code> &mdash; enable automatic decluttering</li>"
                + "<li>Priority is determined by placemark order in the layer (earlier = higher priority)</li>"
                + "</ul>"));

        put("PlacemarkLabelEditing",
            section("Overview",
                "In-place label editing on picked placemarks. Click a placemark to enter edit mode and "
                + "type a new label directly on the globe. Press Enter to confirm the edit.")
            + section("What You'll See",
                "<ul>"
                + "<li>Placemarks with editable labels on the globe</li>"
                + "<li>Click a placemark to see its label become editable</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li><b>Click</b> a placemark to start editing its label</li>"
                + "<li>Type a new label text</li>"
                + "<li>Press <b>Enter</b> to confirm, <b>Escape</b> to cancel</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>PointPlacemark</code> &mdash; with interactive label editing</li>"
                + "<li><code class='api-method'>placemark.setLabelText(String)</code> &mdash; programmatically set the label</li>"
                + "</ul>"));

        put("Markers",
            section("Overview",
                "Simple 3D marker shapes positioned at globe coordinates. Markers are lightweight "
                + "geometric indicators (sphere, cone, cylinder, cube) designed for rendering thousands "
                + "of items efficiently.")
            + section("What You'll See",
                "<ul>"
                + "<li>Various marker shapes (sphere, cone, cylinder, cube) at globe positions</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to view markers at their positions</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>BasicMarker</code> &mdash; marker at a <code>Position</code></li>"
                + "<li><code class='api-class'>BasicMarkerAttributes</code> &mdash; shape type, colour, size</li>"
                + "<li><code class='api-class'>MarkerLayer</code> &mdash; optimised layer for rendering thousands of markers</li>"
                + "</ul>"));

        put("MarkersOrder",
            section("Overview",
                "Control marker draw order and overlap handling. Demonstrates how to manage the visual "
                + "priority of overlapping markers so that important markers render on top.")
            + section("What You'll See",
                "<ul>"
                + "<li>Overlapping markers with controlled draw order</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to overlapping markers to see draw-order effects</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>MarkerLayer</code> &mdash; with draw order control</li>"
                + "</ul>"));

        put("KeepingObjectsInView",
            section("Overview",
                "Auto-pan and zoom the view to keep a set of shapes always visible. As shapes move or "
                + "new ones appear, the camera adjusts automatically to encompass them all.")
            + section("What You'll See",
                "<ul>"
                + "<li>Shapes on the globe that are always kept in view</li>"
                + "<li>Camera automatically adjusts as shapes move or new ones appear</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Watch the camera auto-adjust to keep all tracked objects visible</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ExtentVisibilitySupport</code> &mdash; computes the view parameters needed to "
                + "encompass a set of geographic extents</li>"
                + "<li><code class='api-method'>view.goTo(position, elevation)</code> &mdash; animate camera to the computed view</li>"
                + "</ul>"));

        put("GPSTracks",
            section("Overview",
                "Animated GPS track playback with position interpolation. Load GPS track data and "
                + "watch an animated marker move along the route with configurable playback speed.")
            + section("What You'll See",
                "<ul>"
                + "<li>A GPS track rendered as a path on the globe</li>"
                + "<li>An animated marker moving along the track in real-time</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Play/pause the track animation</li>"
                + "<li>Adjust playback speed</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>Path</code> &mdash; render the GPS track route</li>"
                + "<li>Timer-based position interpolation along the track waypoints</li>"
                + "</ul>"));

        put("CoordinateSearchExample",
            section("Overview",
                "Search for places via the Nominatim (OpenStreetMap) geocoder and fly the camera to "
                + "results. Type a place name or coordinates to geocode them and animate the camera "
                + "to that location on the globe.")
            + section("What You'll See",
                "<ul>"
                + "<li>A search box in the toolbar</li>"
                + "<li>Type a place name and the globe animates to fly to that location</li>"
                + "<li>Search results listed with place details and coordinates</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Type a place name (e.g. \"Paris\", \"Grand Canyon\", \"Tokyo\") and press Enter</li>"
                + "<li>Select from multiple results to fly the camera to that location</li>"
                + "<li>Also accepts coordinates (e.g. \"48.8566, 2.3522\")</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li>Nominatim (OpenStreetMap) geocoding REST API integration</li>"
                + "<li><code class='api-method'>view.goTo(position, elevation)</code> &mdash; animate camera to the geocoded position</li>"
                + "</ul>"));

        // ── Military ────────────────────────────────────────────────────────────

        put("symbology.Symbology",
            section("Overview",
                "MIL-STD-2525 tactical symbol rendering overview. Demonstrates the military symbology "
                + "engine that renders standard tactical symbols with correct affiliation colour coding: "
                + "Friendly (blue), Hostile (red), Neutral (green), Unknown (yellow). Each symbol is "
                + "identified by a 15-character Symbol Identification Code (SIDC).")
            + section("What You'll See",
                "<ul>"
                + "<li>MIL-STD-2525 tactical symbols at globe positions</li>"
                + "<li>Standard military icon shapes with correct affiliation colour coding</li>"
                + "<li>Friendly (blue), Hostile (red), Neutral (green), Unknown (yellow) frames</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to view symbols at different positions</li>"
                + "<li>Zoom in to see symbol detail, modifier text, and echelon indicators</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>MilStd2525TacticalSymbol</code> &mdash; renders a single 2525 tactical symbol</li>"
                + "<li><code class='api-class'>TacticalSymbolAttributes</code> &mdash; scaling, opacity, text modifier visibility</li>"
                + "<li><b>SIDC:</b> 15-character Symbol Identification Code encoding symbol type, affiliation, "
                + "battle dimension, status, and function</li>"
                + "</ul>"));

        put("symbology.TacticalSymbols",
            section("Overview",
                "Full MIL-STD-2525 symbol set with modifier text fields including echelon, speed leader, "
                + "date-time group, unique designation, higher formation, and more. Shows how to configure "
                + "individual modifier fields on tactical symbols.")
            + section("What You'll See",
                "<ul>"
                + "<li>Tactical symbols with visible modifier text labels (echelon, DTG, designation)</li>"
                + "<li>Various symbol types: units, equipment, installations</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Zoom in to read modifier text around the symbols</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>MilStd2525TacticalSymbol</code> &mdash; with modifier fields</li>"
                + "<li><code class='api-method'>symbol.setModifier(SymbologyConstants.ECHELON, \"H\")</code> &mdash; "
                + "set individual modifier values by constant key</li>"
                + "<li>Modifier constants: <code>ECHELON</code>, <code>DATE_TIME_GROUP</code>, "
                + "<code>UNIQUE_DESIGNATION</code>, <code>SPEED_LEADER</code>, <code>HIGHER_FORMATION</code></li>"
                + "</ul>"));

        put("symbology.TacticalGraphics",
            section("Overview",
                "MIL-STD-2525 tactical graphic overlays &mdash; phase lines, boundaries, axes of advance, "
                + "objectives, and other operational graphics drawn on the map. These are the line and "
                + "area symbols used in military operational planning.")
            + section("What You'll See",
                "<ul>"
                + "<li>Tactical graphics (lines, areas, points) rendered on the globe</li>"
                + "<li>Phase lines, boundaries, objective markers, and axes of advance</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to the operational area to view all graphic types</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>MilStd2525GraphicFactory</code> &mdash; creates tactical graphics from SIDC strings</li>"
                + "<li><code class='api-class'>TacticalGraphic</code> &mdash; base class for all tactical graphic types "
                + "(lines, areas, points)</li>"
                + "</ul>"));

        put("symbology.DeclutterTacticalSymbols",
            section("Overview",
                "Automatic decluttering of dense tactical symbol layers, specialised for military symbols. "
                + "When many symbols overlap, lower-priority symbols are hidden to maintain readability "
                + "of the operational picture.")
            + section("What You'll See",
                "<ul>"
                + "<li>Dense clusters of tactical symbols that automatically declutter with zoom</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Zoom in and out to see decluttering adjust symbol density</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>MilStd2525TacticalSymbol</code> &mdash; with decluttering enabled</li>"
                + "<li><code class='api-method'>symbol.setEnableDecluttering(true)</code></li>"
                + "</ul>"));

        // ── Advanced / GPU ───────────────────────────────────────────────────────

        put("analytics.AnalyticSurfaceDemo",
            section("Overview",
                "Colour-mapped analytic surfaces rendered from 2D numeric data grids. Displays three surfaces: "
                + "<b>Random altitude</b> (40&times;40 grid, 25&ndash;35&deg;N, 400km altitude), "
                + "<b>Random colour</b> (ground-clamped, 0.6 opacity), and "
                + "<b>Precipitation</b> (from GeoTIFF, 5km/unit scale). Includes timer animation that mixes "
                + "between two datasets over 2000ms using <code>WWMath.mixSmooth()</code>. Colour gradient "
                + "maps hue from 240&deg;/360 (blue, low values) to 0&deg;/360 (red, high values).")
            + section("What You'll See",
                "<ul>"
                + "<li>Three analytic surfaces with colour gradients from blue (low) to red (high)</li>"
                + "<li>An elevated surface at 400km altitude showing random data</li>"
                + "<li>A ground-clamped semi-transparent surface</li>"
                + "<li>A precipitation surface loaded from GeoTIFF data</li>"
                + "<li>Animated transition mixing between two data states over 2 seconds</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate and zoom to see the analytic surface detail</li>"
                + "<li>Tilt to see the elevated 400km surface in 3D perspective</li>"
                + "<li>Watch the animation cycle between two data states</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>AnalyticSurface</code> &mdash; surface created from a 2D grid of numeric values</li>"
                + "<li><code class='api-class'>AnalyticSurfaceAttributes</code> &mdash; colour ramp, opacity, altitude exaggeration</li>"
                + "<li><code class='api-method'>surface.setValues(gridValues)</code> &mdash; set the 2D data grid</li>"
                + "<li><code class='api-method'>WWMath.mixSmooth(a, b, t)</code> &mdash; smooth interpolation between two values "
                + "for animation</li>"
                + "<li>Colour gradient: <code>Color.getHSBColor(hue, 1.0, 1.0)</code> where hue = "
                + "(1.0 - normalizedValue) * 240/360</li>"
                + "</ul>"));

        put("MeasureToolUsage",
            section("Overview",
                "Interactive measurement tool with tabbed multi-measurement support. Each tab contains an "
                + "independent <code class='api-class'>MeasureTool</code> + <code>MeasureToolPanel</code>. "
                + "Provides per-segment distances, cumulative totals, bearings, elevation data, and a "
                + "<code class='api-class'>TerrainProfileLayer</code> synced to the active path in "
                + "<code>FOLLOW_PATH</code> mode. Close buttons per tab and clipboard export.")
            + section("What You'll See",
                "<ul>"
                + "<li>A tabbed pane with a \"+\" tab for creating new measurements</li>"
                + "<li>Per-segment and total distance displayed in the measurement panel</li>"
                + "<li>Elevation profile along the measurement path</li>"
                + "<li>Bearing (azimuth) between consecutive points</li>"
                + "<li>Multiple independent measurement tabs for comparison</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Click the <b>+</b> tab to create a new measurement</li>"
                + "<li>Select a measurement mode: line, path, circle, polygon, freehand, etc.</li>"
                + "<li>Click on the globe to place measurement points</li>"
                + "<li>Double-click to finish the measurement</li>"
                + "<li>Switch between tabs to compare different measurements</li>"
                + "<li>Use clipboard export to copy measurement results</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>MeasureTool</code> &mdash; core measurement engine</li>"
                + "<li><code class='api-method'>tool.setMeasureShapeType(MeasureTool.SHAPE_LINE)</code> &mdash; "
                + "modes: SHAPE_LINE, SHAPE_PATH, SHAPE_CIRCLE, SHAPE_POLYGON, SHAPE_QUAD, etc.</li>"
                + "<li><code class='api-method'>tool.getLength()</code> &mdash; total geodetic distance in meters</li>"
                + "<li><code class='api-method'>tool.getArea()</code> &mdash; geodetic area in square meters</li>"
                + "<li><code class='api-class'>TerrainProfileLayer</code> &mdash; with <code>FOLLOW_PATH</code> mode synced to "
                + "the active measurement path</li>"
                + "</ul>"));

        put("lineofsight.GridOfPoints",
            section("Overview",
                "Sample terrain intersection for a grid of observer positions, computing line-of-sight "
                + "from each point in the grid to a reference position.")
            + section("What You'll See",
                "<ul>"
                + "<li>A grid of points coloured by their visibility from the reference position</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate to the grid area to see visibility results</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>HighResolutionTerrain</code> &mdash; high-resolution terrain for accurate "
                + "line-of-sight computation</li>"
                + "</ul>"));

        put("FlatWorld",
            section("Overview",
                "Flat (2D projected) globe with selectable map projections. Switch between Mercator, "
                + "Equirectangular, Sinusoidal, and other projections at runtime. Useful for applications "
                + "that need a 2D map view rather than a 3D globe.")
            + section("What You'll See",
                "<ul>"
                + "<li>The globe rendered as a flat 2D map</li>"
                + "<li>Selectable map projection from a dropdown control</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Select different projections from the control panel dropdown</li>"
                + "<li>Pan and zoom the flat map</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>EarthFlat</code> &mdash; flat globe implementation that uses map projections</li>"
                + "<li><code class='api-class'>ProjectionMercator</code>, <code class='api-class'>ProjectionEquirect</code>, "
                + "<code class='api-class'>ProjectionSinusoidal</code> &mdash; projection implementations</li>"
                + "<li><code class='api-method'>flatGlobe.setProjection(projection)</code> &mdash; switch projections at runtime</li>"
                + "</ul>"));

        put("FlatWorldEarthquakes",
            section("Overview",
                "Live USGS earthquake feed rendered on a flat-projected globe. Loads the USGS GeoJSON feed "
                + "for 2.5+ magnitude earthquakes with weekly updates and 5-minute refresh. Each earthquake "
                + "is an <code class='api-class'>EqAnnotation</code> (extends <code>GlobeAnnotation</code>) "
                + "colour-coded by magnitude. Includes a blinker animation for the latest earthquake, "
                + "magnitude filter combo box, and \"Zoom on latest\" / \"Reset Global View\" buttons.")
            + section("What You'll See",
                "<ul>"
                + "<li>A flat globe with earthquake markers at seismic event locations</li>"
                + "<li>Marker colour indicates magnitude (green=low, yellow=medium, red=high)</li>"
                + "<li>The latest earthquake blinks with an animation effect</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Click earthquake markers to see details (magnitude, depth, location, time)</li>"
                + "<li>Use the <b>magnitude filter</b> combo to show only earthquakes above a threshold</li>"
                + "<li><b>Zoom on latest</b> &mdash; flies the camera to the most recent earthquake</li>"
                + "<li><b>Reset Global View</b> &mdash; returns to the full-world view</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>EarthFlat</code> &mdash; flat globe for 2D map display</li>"
                + "<li><code class='api-class'>EqAnnotation</code> &mdash; extends <code>GlobeAnnotation</code> with "
                + "magnitude-based colour coding</li>"
                + "<li>USGS GeoJSON earthquake API: magnitude 2.5+ weekly feed with 5-minute refresh</li>"
                + "</ul>"));

        put("AnimatedGlobe",
            section("Overview",
                "Smooth globe-rotation animation loop using the JOGL Animator. The globe rotates "
                + "continuously around its axis, demonstrating how to integrate animation with "
                + "the WorldWind rendering loop.")
            + section("What You'll See",
                "<ul>"
                + "<li>The globe rotating smoothly around its axis in a continuous loop</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Watch the animation; mouse interaction may temporarily pause rotation</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>Animator</code> &mdash; JOGL animation loop driving continuous rendering</li>"
                + "<li><code class='api-method'>view.setHeading(Angle)</code> &mdash; programmatic camera rotation each frame</li>"
                + "</ul>"));

        put("Graticule",
            section("Overview",
                "Latitude/longitude graticule grid overlay with configurable style. Displays a geographic "
                + "coordinate grid on the globe that adapts density to the current zoom level.")
            + section("What You'll See",
                "<ul>"
                + "<li>Lat/lon grid lines overlaid on the globe</li>"
                + "<li>Grid density adapts automatically to zoom level (wider spacing when zoomed out)</li>"
                + "<li>Coordinate labels at grid intersections</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Zoom in to see finer grid lines appear (1&deg;, then 30', then 10', etc.)</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>LatLonGraticuleLayer</code> &mdash; lat/lon graticule overlay with "
                + "adaptive density</li>"
                + "</ul>"));

        put("GARSGraticule",
            section("Overview",
                "Global Airspace Reference System (GARS) cell graticule. GARS divides the Earth into "
                + "cells used for airspace coordination and management.")
            + section("What You'll See",
                "<ul>"
                + "<li>GARS grid cells overlaid on the globe</li>"
                + "<li>Cell labels showing GARS designators (e.g. \"356JN\")</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Zoom in to see progressively finer GARS cell subdivisions</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>GARSGraticuleLayer</code> &mdash; GARS grid overlay</li>"
                + "</ul>"));

        put("MGRSGraticule",
            section("Overview",
                "Military Grid Reference System (MGRS) graticule overlay. MGRS is based on UTM zones "
                + "and provides a hierarchical grid system used for military mapping and navigation. "
                + "Shows UTM zones, 100km grid squares, and finer subdivisions at close zoom.")
            + section("What You'll See",
                "<ul>"
                + "<li>MGRS grid zones (UTM zones) overlaid on the globe</li>"
                + "<li>100km grid squares with alphanumeric designators</li>"
                + "<li>Finer grid subdivisions at close zoom levels</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Zoom in to see finer MGRS grid divisions (100km, 10km, 1km, etc.)</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>MGRSGraticuleLayer</code> &mdash; MGRS/UTM grid overlay</li>"
                + "</ul>"));

        put("DimGlobeSurface",
            section("Overview",
                "Darken the globe surface to increase overlay contrast. Applies a semi-transparent dark "
                + "overlay on the globe imagery, making bright overlays (shapes, labels, symbols) more "
                + "visible against the darkened background.")
            + section("What You'll See",
                "<ul>"
                + "<li>A darkened globe surface with overlays appearing brighter by contrast</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Compare overlay visibility against the dimmed vs normal globe surface</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>SurfaceImage</code> &mdash; semi-transparent dark overlay image covering the globe</li>"
                + "</ul>"));

        put("ViewControls",
            section("Overview",
                "On-screen pan, zoom, tilt, and heading view control widget with configurable layout "
                + "(<code>HORIZONTAL</code> or <code>VERTICAL</code>) and scale. Uses "
                + "<code class='api-class'>ViewControlsLayer</code> with a "
                + "<code class='api-class'>ViewControlsSelectListener</code>. Includes a scale slider "
                + "(0.1&ndash;2.0) and toggle checkboxes for individual controls: Pan, Look, Zoom, "
                + "Heading, Pitch, and FOV.")
            + section("What You'll See",
                "<ul>"
                + "<li>On-screen view control widget with pan arrows, zoom +/-, tilt, and heading controls</li>"
                + "<li>Configurable layout and scale</li>"
                + "<li>Toggle checkboxes for showing/hiding individual control elements</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Click and hold the <b>pan arrows</b> to move the globe</li>"
                + "<li>Click <b>+/-</b> buttons to zoom in and out</li>"
                + "<li>Click <b>tilt/heading</b> controls to adjust the view angle</li>"
                + "<li>Use the <b>scale slider</b> (0.1&ndash;2.0) to resize the control widget</li>"
                + "<li>Toggle checkboxes to show/hide individual controls</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>ViewControlsLayer</code> &mdash; the on-screen widget layer</li>"
                + "<li><code class='api-class'>ViewControlsSelectListener</code> &mdash; handles mouse interaction with the controls</li>"
                + "<li><code class='api-method'>layer.setLayout(\"horizontal\")</code> &mdash; set HORIZONTAL or VERTICAL layout</li>"
                + "<li><code class='api-method'>layer.setScale(1.5)</code> &mdash; resize the widget (0.1 to 2.0)</li>"
                + "<li class='note'>Note: each WorldWindow needs its own ViewControlsLayer instance (not shareable between windows).</li>"
                + "</ul>"));

        put("ViewIteration",
            section("Overview",
                "Programmatic camera animation along a pre-defined path. Demonstrates flying the view "
                + "through a sequence of waypoints automatically, each with a specific heading, tilt, "
                + "and altitude.")
            + section("What You'll See",
                "<ul>"
                + "<li>The camera flying smoothly between pre-defined geographic positions</li>"
                + "<li>Each waypoint has a specific heading, tilt, and altitude</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Watch the automated camera flight through the waypoint sequence</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-method'>view.goTo(position, elevation)</code> &mdash; animate camera to a position</li>"
                + "<li><code class='api-method'>view.addAnimator(animator)</code> &mdash; queue camera animation sequences</li>"
                + "</ul>"));

        put("ViewLimits",
            section("Overview",
                "Constrain the camera to a geographic bounding box and altitude range. Prevents the user "
                + "from navigating outside a defined area &mdash; useful for kiosk applications or "
                + "region-focused displays.")
            + section("What You'll See",
                "<ul>"
                + "<li>The globe with navigation constrained to a specific geographic region</li>"
                + "<li>Camera stops at the boundaries when you try to pan beyond them</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Try to pan beyond the boundaries to feel the constraints</li>"
                + "<li>Try to zoom beyond the altitude limits</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>OrbitViewLimits</code> &mdash; defines min/max latitude, longitude, altitude, "
                + "heading, and pitch constraints</li>"
                + "<li><code class='api-method'>view.getOrbitViewLimits()</code> &mdash; get the current view limits object</li>"
                + "<li><code class='api-method'>limits.setCenterLatLimits(minLat, maxLat)</code> &mdash; constrain latitude range</li>"
                + "</ul>"));

        put("ViewLookAround",
            section("Overview",
                "First-person look-around navigation mode. Instead of orbiting the globe, the camera stays "
                + "at a fixed position and rotates to look around &mdash; like standing at a viewpoint and "
                + "turning your head.")
            + section("What You'll See",
                "<ul>"
                + "<li>A first-person view from a fixed geographic position</li>"
                + "<li>Mouse controls rotate the view direction (heading and pitch)</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Drag to look around from the fixed viewpoint</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>FlyView</code> &mdash; first-person navigation view (vs the default OrbitView)</li>"
                + "</ul>"));

        put("DetailHints",
            section("Overview",
                "Adjust level-of-detail (LOD) hints for terrain tessellation and 3D shapes. Fine-tune "
                + "the tradeoff between visual quality (more polygons) and rendering performance (fewer "
                + "polygons). The detail hint value ranges from -1.0 (coarsest) to 1.0 (finest).")
            + section("What You'll See",
                "<ul>"
                + "<li>Terrain and shapes with adjustable detail levels</li>"
                + "<li>Higher detail = finer terrain mesh and smoother shape geometry, but slower rendering</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Use the slider to adjust the detail hint value from -1.0 to 1.0</li>"
                + "<li>Observe the quality/performance tradeoff in real-time</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-method'>globe.getElevationModel().setDetailHint(double)</code> &mdash; terrain LOD hint</li>"
                + "<li>Value range: -1.0 (coarsest, fastest) to 1.0 (finest, slowest)</li>"
                + "</ul>"));

        put("multiwindow.MultiFrame",
            section("Overview",
                "Multiple independent globe windows sharing a single WorldWind instance. Each window has "
                + "its own view and camera state but shares layers, imagery tiles, and OpenGL resources "
                + "for memory efficiency.")
            + section("What You'll See",
                "<ul>"
                + "<li>Two or more independent globe windows on screen</li>"
                + "<li>Each window navigates independently with its own camera</li>"
                + "<li>Shared imagery and shape layers appear in all windows</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate each window independently (different zoom, position, tilt)</li>"
                + "<li>Changes to shared layers (visibility, opacity) appear in all windows simultaneously</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-method'>new WorldWindowGLCanvas(shareWith)</code> &mdash; create a new canvas that shares "
                + "OpenGL resources with an existing one</li>"
                + "<li><b>Gotcha:</b> The first window must be visible and its GL context initialised before "
                + "creating shared windows. Otherwise, resource sharing will fail silently.</li>"
                + "</ul>"));

        put("multiwindow.FlatAndRoundGlobes",
            section("Overview",
                "Side-by-side flat-projected and round globe in separate windows. Compare the same data "
                + "displayed on both globe types simultaneously &mdash; useful for understanding projection "
                + "distortion and for applications that need both 2D and 3D views.")
            + section("What You'll See",
                "<ul>"
                + "<li>A round 3D globe in one window</li>"
                + "<li>A flat 2D map in the other window</li>"
                + "<li>Same layers and data displayed on both</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate each window independently to compare 2D and 3D representations</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>Earth</code> &mdash; round globe</li>"
                + "<li><code class='api-class'>EarthFlat</code> &mdash; flat globe with map projection</li>"
                + "<li>Shared layer list between both models</li>"
                + "</ul>"));

        put("multiwindow.SharedShapes",
            section("Overview",
                "Share rendered shape layer data across multiple WorldWindow instances. Both windows "
                + "display the same airspace shapes with a shared layer state. One window uses a round "
                + "globe; the other uses a flat globe. Demonstrates layer sharing and property change "
                + "notifications.")
            + section("What You'll See",
                "<ul>"
                + "<li>Two globe windows showing the same airspace shapes</li>"
                + "<li>A shared layer panel that controls visibility for both windows</li>"
                + "<li>One window uses round globe; the other uses flat projection</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Toggle shape layers in the shared panel &mdash; both windows update simultaneously</li>"
                + "<li>Navigate each window independently</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>RenderableLayer</code> &mdash; shared layer containing airspace shapes</li>"
                + "<li><code class='api-class'>LayerList</code> &mdash; same layer list used by both globe models</li>"
                + "<li><code class='api-method'>layer.firePropertyChange()</code> &mdash; notify both windows of state changes</li>"
                + "</ul>"));

        put("multiwindow.TabbedPaneUsage",
            section("Overview",
                "Multiple globe instances hosted in a <code>JTabbedPane</code>. Switch between globe "
                + "views using tabs &mdash; only the active tab's WorldWindow renders, saving GPU resources.")
            + section("What You'll See",
                "<ul>"
                + "<li>Two tabs, each containing a globe window</li>"
                + "<li>Switch between tabs to view different globe instances</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Click tabs to switch between globes</li>"
                + "<li>Navigate each globe independently</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>JTabbedPane</code> &mdash; Swing tab container</li>"
                + "<li><code class='api-class'>WorldWindowGLCanvas</code> &mdash; shared OpenGL context between tabs</li>"
                + "</ul>"));

        put("multiwindow.CardLayoutUsage",
            section("Overview",
                "Multiple globes swapped via <code>CardLayout</code>. Like TabbedPaneUsage but with "
                + "buttons to switch between globe views instead of tabs.")
            + section("What You'll See",
                "<ul>"
                + "<li>Two globe views swapped via buttons at the bottom of the window</li>"
                + "<li>Only one globe visible at a time</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Click the buttons to switch between WorldWindow A and WorldWindow B</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>CardLayout</code> &mdash; Swing layout that shows one component at a time</li>"
                + "<li><code class='api-class'>WorldWindowGLCanvas</code> &mdash; shared OpenGL context between cards</li>"
                + "</ul>"));

        put("ExportImageOrElevations",
            section("Overview",
                "Export the globe view as a georeferenced image or export elevation data to a GeoTIFF file. "
                + "Uses <code class='api-class'>SectorSelector</code> for interactive geographic area selection. "
                + "Image export: 2048&times;2048 pixel GeoTIFF. Elevation export: 512&times;512 GeoTIFF "
                + "with missing data encoded as <code>Short.MIN_VALUE</code>. Uses <code>SwingWorker</code> "
                + "with a progress dialog for non-blocking export.")
            + section("What You'll See",
                "<ul>"
                + "<li>A globe with an interactive sector selection overlay</li>"
                + "<li>Export options for image (2048&times;2048) or elevation (512&times;512) data</li>"
                + "<li>A progress dialog during export</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Drag on the globe to select a geographic sector for export</li>"
                + "<li>Choose the output format: image GeoTIFF or elevation GeoTIFF</li>"
                + "<li>Click export and wait for the progress dialog to complete</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>SectorSelector</code> &mdash; interactive tool for selecting a geographic sector</li>"
                + "<li>Image export: 2048&times;2048 pixel GeoTIFF with full globe imagery</li>"
                + "<li>Elevation export: 512&times;512 GeoTIFF, missing data = <code>Short.MIN_VALUE</code></li>"
                + "<li><code class='api-class'>SwingWorker</code> &mdash; background export with progress dialog</li>"
                + "</ul>"));

        put("PersistSessionState",
            section("Overview",
                "Save and restore camera position and layer state between sessions. Demonstrates how to "
                + "persist the full application state so it resumes exactly where you left off when "
                + "reopened.")
            + section("What You'll See",
                "<ul>"
                + "<li>The globe restores to the last saved camera position on launch</li>"
                + "<li>Layer visibility state is also restored from the previous session</li>"
                + "</ul>")
            + section("How to Interact",
                "<ul>"
                + "<li>Navigate the globe and adjust layer visibility</li>"
                + "<li>Close and reopen the example &mdash; the view and layer state are restored</li>"
                + "</ul>")
            + section("Key APIs",
                "<ul>"
                + "<li><code class='api-class'>SessionState</code> &mdash; serialises and deserialises view and layer state</li>"
                + "<li><code class='api-method'>view.getRestorableState()</code> &mdash; serialise the current view state to XML</li>"
                + "<li><code class='api-method'>view.restoreState(stateXml)</code> &mdash; restore view from saved XML state</li>"
                + "</ul>"));
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns the full HTML documentation for the given class name, or null if none exists.
     */
    public static String getHtml(String className) {
        // Try the full class name first
        String html = DOCS.get(className);
        if (html != null) return wrapHtml(html);

        // Try stripping the base package prefix
        if (className.startsWith(BASE)) {
            html = DOCS.get(className.substring(BASE.length()));
            if (html != null) return wrapHtml(html);
        }
        return null;
    }

    /**
     * Returns a minimal HTML fallback for the short description text.
     */
    public static String fallback(String description) {
        return wrapHtml(section("Overview", "<p>" + description + "</p>"));
    }

    /**
     * Returns the welcome / landing page HTML shown when no example is selected.
     */
    public static String getWelcomeHtml() {
        return wrapHtml(
            "<h2 style='color: #7ab0df; font-size: 14px; border-bottom: 2px solid #007acc; "
            + "padding-bottom: 6px; margin-top: 6px;'>WorldWind Reforged</h2>"

            + "<p style='line-height: 1.6;'>"
            + "Welcome to the <b style='color: #dcdcdc;'>WorldWind Reforged</b> example browser "
            + "&mdash; a modernised and extended build of the NASA World Wind Java SDK. "
            + "Select a category on the left and click an example to see its full documentation here, "
            + "or double-click to launch it."
            + "</p>"

            // ── About the project ──
            + section("About This Project",
                "<p style='line-height: 1.6;'>"
                + "WorldWind Reforged is developed by "
                + "<b style='color: #7ab0df;'>seaglassfoundry.com</b>. "
                + "Our goal is to preserve, maintain, and advance the NASA World Wind Java platform "
                + "so that it remains a viable open-source 3D globe toolkit for researchers, educators, "
                + "and developers. The Reforged branch adds GPU-accelerated terrain rendering (Phase&nbsp;4), "
                + "GLSL shader-based styling (Phase&nbsp;3), a modern WMS / GIBS time-series engine "
                + "(Phase&nbsp;2), performance optimisations, and numerous bug fixes &mdash; all while "
                + "keeping full backwards compatibility with the original API."
                + "</p>")

            // ── Acknowledgements ──
            + section("Acknowledgements",
                "<p style='line-height: 1.6;'>"
                + "This project would not exist without the extraordinary work of the people who "
                + "created and maintained <b style='color: #dcdcdc;'>NASA World Wind</b>. "
                + "We owe an enormous debt of gratitude to:"
                + "</p>"
                + "<ul style='line-height: 1.7;'>"
                + "<li>The <b style='color: #dcdcdc;'>NASA Ames Research Center</b> team who conceived "
                + "and built World Wind as a free, open-source platform for exploring the Earth and beyond.</li>"
                + "<li><b style='color: #dcdcdc;'>Patrick Hogan</b> &mdash; NASA World Wind project founder and "
                + "visionary, whose leadership made this technology available to the world.</li>"
                + "<li><b style='color: #dcdcdc;'>Tom Gaskins</b>, <b style='color: #dcdcdc;'>Dave Collins</b>, "
                + "<b style='color: #dcdcdc;'>tag</b> (Tom Gaskins), and the many NASA and contractor engineers "
                + "whose names appear throughout the source code &mdash; each commit, each class, and each "
                + "carefully written Javadoc comment represents years of dedicated effort.</li>"
                + "<li>The wider <b style='color: #dcdcdc;'>World Wind open-source community</b> &mdash; "
                + "contributors, testers, and users who filed issues, submitted patches, wrote examples, "
                + "and kept the project alive long after official NASA development slowed.</li>"
                + "</ul>"
                + "<p style='line-height: 1.6;'>"
                + "Thank you, sincerely, for building something remarkable and sharing it with everyone."
                + "</p>")

            // ── Requirements ──
            + section("Requirements",
                "<ul style='line-height: 1.7;'>"
                + "<li><b style='color: #dcdcdc;'>Java 17</b> or later (JDK, not JRE &mdash; required for compilation "
                + "and the <code>--add-opens</code> flags below)</li>"
                + "<li><b style='color: #dcdcdc;'>Maven 3.8+</b> for dependency management and builds</li>"
                + "<li><b style='color: #dcdcdc;'>OpenGL 2.0+</b> capable GPU (OpenGL&nbsp;3.0+ for Phase&nbsp;3 shaders; "
                + "OpenGL&nbsp;4.0+ for Phase&nbsp;4 tessellation; OpenGL&nbsp;4.3+ for compute shaders)</li>"
                + "<li>An active internet connection for WMS/GIBS imagery tile downloads on first run</li>"
                + "</ul>")

            // ── How to build & run ──
            + section("How to Build &amp; Run",
                "<p style='line-height: 1.6;'><b style='color: #dcdcdc;'>Build with Maven:</b></p>"
                + "<div style='background: #454749; padding: 8px 10px; margin: 4px 0 8px 0; "
                + "font-family: monospace; font-size: 10px; color: #b0b0b0; line-height: 1.5;'>"
                + "mvn clean compile"
                + "</div>"

                + "<p style='line-height: 1.6;'><b style='color: #dcdcdc;'>Run an example with Maven:</b></p>"
                + "<div style='background: #454749; padding: 8px 10px; margin: 4px 0 8px 0; "
                + "font-family: monospace; font-size: 10px; color: #b0b0b0; line-height: 1.5;'>"
                + "mvn exec:java -Dexec.mainClass=\"gov.nasa.worldwindx.examples.ExamplesIndex\""
                + "</div>"

                + "<p style='line-height: 1.6;'><b style='color: #dcdcdc;'>Run from an IDE (Eclipse, IntelliJ):</b> "
                + "import as a Maven project, then run any example class. You <b>must</b> add the following "
                + "VM arguments to your run configuration:</p>"
                + "<div style='background: #454749; padding: 8px 10px; margin: 4px 0 8px 0; "
                + "font-family: monospace; font-size: 10px; color: #b0b0b0; line-height: 1.5;'>"
                + "--add-opens java.base/java.lang=ALL-UNNAMED<br>"
                + "--add-opens java.base/java.lang.reflect=ALL-UNNAMED<br>"
                + "--add-opens java.base/java.io=ALL-UNNAMED<br>"
                + "--add-opens java.base/java.nio=ALL-UNNAMED<br>"
                + "--add-opens java.base/java.util=ALL-UNNAMED<br>"
                + "--add-opens java.logging/java.util.logging=ALL-UNNAMED<br>"
                + "--add-opens java.desktop/java.awt=ALL-UNNAMED<br>"
                + "--add-opens java.desktop/java.awt.peer=ALL-UNNAMED<br>"
                + "--add-opens java.desktop/sun.awt=ALL-UNNAMED<br>"
                + "--add-opens java.desktop/sun.awt.windows=ALL-UNNAMED<br>"
                + "--add-opens java.desktop/sun.java2d=ALL-UNNAMED<br>"
                + "--add-opens java.desktop/javax.swing=ALL-UNNAMED"
                + "</div>"

                + "<h2>Why Are These Flags Required?</h2>"

                + "<p style='line-height: 1.6;'>"
                + "These flags are required by <b style='color: #dcdcdc;'>JOGL</b> (Java OpenGL), not by "
                + "WorldWind itself. JOGL must use reflective access and JNI to bind OpenGL contexts to "
                + "Java's AWT/Swing graphics system. This requires access to internal JDK packages &mdash; "
                + "<code>sun.awt</code>, <code>sun.java2d</code>, AWT peer classes, and NIO buffer internals "
                + "&mdash; that Java's module system (JPMS, introduced in Java&nbsp;9) blocks by default."
                + "</p>"

                + "<p style='line-height: 1.6;'>"
                + "Starting with Java&nbsp;16, the <code>--illegal-access=permit</code> workaround was "
                + "removed entirely, making explicit <code>--add-opens</code> flags the only way to grant "
                + "this access. This affects <b>every</b> JOGL-based application on Java&nbsp;17+, not "
                + "just WorldWind. JOGL&nbsp;2.6.0 (the latest release, actively maintained as of 2026) "
                + "still requires these flags, and there is no planned version of JOGL that eliminates "
                + "them &mdash; the constraint is architectural. Only the JDK itself could remove the need "
                + "by adding <code>opens</code> directives to the <code>java.desktop</code> module, which "
                + "Oracle/OpenJDK has no plans to do."
                + "</p>"

                + "<p style='line-height: 1.6;'><b style='color: #dcdcdc;'>What each group of flags unlocks:</b></p>"
                + "<ul style='line-height: 1.7;'>"
                + "<li><code>sun.awt</code>, <code>sun.awt.windows</code>, <code>sun.java2d</code> "
                + "&mdash; <b>JOGL</b> uses these to bind OpenGL to native window surfaces via AWT peers and Java2D</li>"
                + "<li><code>java.awt</code>, <code>java.awt.peer</code> "
                + "&mdash; <b>JOGL</b> creates and manages OpenGL-capable AWT Canvas/Panel components</li>"
                + "<li><code>javax.swing</code> "
                + "&mdash; <b>JOGL</b> Swing integration for <code>GLJPanel</code> and <code>TextRenderer</code></li>"
                + "<li><code>java.nio</code> "
                + "&mdash; <b>JOGL/Gluegen</b> allocates and manages direct buffers for GPU data transfer</li>"
                + "<li><code>java.lang</code>, <code>java.lang.reflect</code> "
                + "&mdash; <b>JOGL/Gluegen</b> and <b>GDAL</b> use reflective method dispatch for native library loading "
                + "and platform API access</li>"
                + "<li><code>java.io</code>, <code>java.util</code> "
                + "&mdash; <b>JOGL/Gluegen</b> accesses internal I/O and collection APIs during native code marshalling</li>"
                + "<li><code>java.util.logging</code> "
                + "&mdash; <b>JOGL</b> configures logging during initialization</li>"
                + "</ul>"

                + "<p style='line-height: 1.6;'>"
                + "When building with Maven, these flags are applied automatically via "
                + "<code>.mvn/jvm.config</code> and the Surefire plugin configuration in "
                + "<code>pom.xml</code> &mdash; no manual setup is needed. The JAR manifest also includes "
                + "them via the <code>Add-Opens</code> entry. You only need to add them manually when "
                + "running from an IDE."
                + "</p>"

                + "<p style='line-height: 1.6;'>"
                + "<span class='note'>Note:</span> On <b>Linux / macOS</b>, the "
                + "<code>sun.awt.windows</code> flag can be omitted &mdash; it is Windows-specific and "
                + "is harmlessly ignored on other platforms."
                + "</p>")

            // ── License ──
            + section("License",
                "<p style='line-height: 1.6;'>"
                + "The original NASA World Wind Java SDK is licensed under the "
                + "<b style='color: #dcdcdc;'>Apache License, Version&nbsp;2.0</b>, as stated in every "
                + "source file:"
                + "</p>"
                + "<div style='background: #454749; padding: 8px 10px; margin: 4px 0 8px 0; "
                + "font-family: monospace; font-size: 10px; color: #b0b0b0; line-height: 1.5;'>"
                + "Copyright 2006&ndash;2009, 2017, 2020 United States Government, as represented by the "
                + "Administrator of the National Aeronautics and Space Administration. All rights reserved.<br><br>"
                + "The NASA World Wind Java (WWJ) platform is licensed under the Apache License, "
                + "Version 2.0 (the &ldquo;License&rdquo;); you may not use this file except in compliance "
                + "with the License. You may obtain a copy of the License at<br>"
                + "<span style='color: #7ab0df;'>http://www.apache.org/licenses/LICENSE-2.0</span><br><br>"
                + "Unless required by applicable law or agreed to in writing, software distributed under "
                + "the License is distributed on an &ldquo;AS IS&rdquo; BASIS, WITHOUT WARRANTIES OR "
                + "CONDITIONS OF ANY KIND, either express or implied."
                + "</div>"

                + "<p style='line-height: 1.6;'>"
                + "WorldWind Reforged modifications and additions by <b style='color: #7ab0df;'>seaglassfoundry.com</b> "
                + "are also released under the <b style='color: #dcdcdc;'>Apache License, Version&nbsp;2.0</b>, "
                + "in full compliance with the terms of the original license. Under Section&nbsp;4 of the "
                + "Apache&nbsp;2.0 license, derivative works may be distributed provided that modified files "
                + "carry prominent notices stating that you changed the files. Every modified or new source "
                + "file in this project carries such a notice in its header."
                + "</p>"

                + "<p style='line-height: 1.6;'>"
                + "The following third-party open-source libraries are included, each under its own license:"
                + "</p>"
                + "<ul style='line-height: 1.7;'>"
                + "<li><b style='color: #dcdcdc;'>Jackson Parser</b> &mdash; Apache License 2.0</li>"
                + "<li><b style='color: #dcdcdc;'>GDAL</b> &mdash; MIT License</li>"
                + "<li><b style='color: #dcdcdc;'>JOGL</b> &mdash; BSD (Berkeley Software Distribution) License</li>"
                + "<li><b style='color: #dcdcdc;'>Gluegen</b> &mdash; BSD (Berkeley Software Distribution) License</li>"
                + "<li><b style='color: #dcdcdc;'>FlatLaf</b> &mdash; Apache License 2.0</li>"
                + "</ul>")

            // ── Getting started hint ──
            + "<hr style='border: none; border-top: 1px solid #505355; margin: 12px 0;'>"
            + "<p style='color: #808080; font-size: 10px; text-align: center;'>"
            + "Select a category and example to view its documentation, or double-click to launch."
            + "</p>"
        );
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private static void put(String shortName, String body) {
        DOCS.put(shortName, body);
    }

    private static String section(String title, String body) {
        return "<h2>" + title + "</h2>" + body;
    }

    private static String wrapHtml(String body) {
        return "<html><head>" + CSS + "</head><body>" + body + "</body></html>";
    }
}
