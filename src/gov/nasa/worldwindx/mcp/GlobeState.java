/*
 * WorldWind Reforged MCP Server
 * seaglassfoundry.com
 *
 * Holds the live WorldWind globe window and all session state for MCP tools.
 */
package gov.nasa.worldwindx.mcp;

import com.formdev.flatlaf.FlatDarkLaf;
import gov.nasa.worldwind.BasicModel;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.layers.ViewControlsLayer;
import gov.nasa.worldwind.layers.ViewControlsSelectListener;
import gov.nasa.worldwind.render.Renderable;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds the live WorldWind globe window and all session state (layers, handles,
 * annotations). Created once at server startup and shared across all tool calls.
 *
 * <p>All WorldWind mutations go through helper methods that ensure EDT safety.</p>
 */
public final class GlobeState implements AutoCloseable {

    private JFrame frame;
    private WorldWindowGLCanvas wwd;
    private RenderableLayer overlayLayer;
    private RenderableLayer annotationLayer;

    /** handle -> renderable or layer for removal. */
    private final Map<String, HandleEntry> handles = new ConcurrentHashMap<>();

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile String initError;

    public GlobeState() {
        // Lazy: globe window is created on first tool call via ensureInitialized().
    }

    /**
     * Initializes the globe window on demand. Called before any tool that needs
     * the WorldWind canvas. Throws if the OpenGL context cannot be created.
     */
    public synchronized void ensureInitialized() throws Exception {
        if (initialized.get()) {
            if (initError != null)
                throw new IllegalStateException("Globe failed to initialize: " + initError);
            return;
        }

        WorldWindMcpServer.log("Initializing globe window...");

        CountDownLatch ready = new CountDownLatch(1);
        final JFrame[] frameRef = new JFrame[1];
        final WorldWindowGLCanvas[] wwdRef = new WorldWindowGLCanvas[1];

        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } catch (Exception ignored) { }

            JFrame f = new JFrame("WorldWind MCP Server");
            f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

            WorldWindowGLCanvas canvas = new WorldWindowGLCanvas();
            canvas.setPreferredSize(new Dimension(1024, 768));
            canvas.setModel(new BasicModel());

            f.getContentPane().add(canvas);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);

            frameRef[0] = f;
            wwdRef[0] = canvas;
            ready.countDown();
        });

        ready.await();
        this.frame = frameRef[0];
        this.wwd = wwdRef[0];

        // Create overlay layers on EDT.
        this.overlayLayer = new RenderableLayer();
        this.overlayLayer.setName("MCP Overlays");
        this.overlayLayer.setPickEnabled(false);

        this.annotationLayer = new RenderableLayer();
        this.annotationLayer.setName("MCP Annotations");
        this.annotationLayer.setPickEnabled(false);

        SwingUtilities.invokeAndWait(() -> {
            wwd.getModel().getLayers().add(overlayLayer);
            wwd.getModel().getLayers().add(annotationLayer);

            // Add interactive view controls (pan/zoom/tilt/heading buttons).
            ViewControlsLayer viewControlsLayer = new ViewControlsLayer();
            // Insert before the compass so it renders beneath the compass rose.
            LayerList layers = wwd.getModel().getLayers();
            int compassIdx = -1;
            for (int i = 0; i < layers.size(); i++) {
                if (layers.get(i).getClass().getSimpleName().contains("Compass")) {
                    compassIdx = i;
                    break;
                }
            }
            if (compassIdx >= 0) {
                layers.add(compassIdx, viewControlsLayer);
            } else {
                layers.add(viewControlsLayer);
            }
            wwd.addSelectListener(new ViewControlsSelectListener(wwd, viewControlsLayer));
        });

        initialized.set(true);
        WorldWindMcpServer.log("Globe window initialized");
    }

    public WorldWindowGLCanvas wwd() { return wwd; }
    public JFrame frame() { return frame; }
    public RenderableLayer overlayLayer() { return overlayLayer; }
    public RenderableLayer annotationLayer() { return annotationLayer; }

    /** Generate a unique handle with a friendly prefix. */
    public String newHandle(String prefix) {
        String safe = (prefix == null || prefix.isBlank()) ? "item" : prefix;
        return safe + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Register a renderable with a handle for later removal. */
    public void registerHandle(String handle, Renderable renderable, RenderableLayer layer) {
        handles.put(handle, new HandleEntry(renderable, layer, null));
    }

    /** Register a standalone layer with a handle. */
    public void registerLayerHandle(String handle, Layer layer) {
        handles.put(handle, new HandleEntry(null, null, layer));
    }

    /** Remove a handle and its associated renderable or layer. Returns true if found. */
    public boolean removeHandle(String handle) {
        HandleEntry entry = handles.remove(handle);
        if (entry == null) return false;
        try {
            SwingUtilities.invokeAndWait(() -> {
                if (entry.renderable != null && entry.parentLayer != null) {
                    entry.parentLayer.removeRenderable(entry.renderable);
                }
                if (entry.standaloneLayer != null) {
                    wwd.getModel().getLayers().remove(entry.standaloneLayer);
                }
            });
        } catch (Exception ignored) { }
        wwd.redraw();
        return true;
    }

    public Map<String, HandleEntry> handles() { return handles; }

    /** Convenience: run on EDT and wait. */
    public void runOnEdt(Runnable action) throws InvocationTargetException, InterruptedException {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeAndWait(action);
        }
    }

    @Override
    public void close() {
        if (frame == null) return;
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(false);
            frame.dispose();
        });
    }

    /** Tracks what was added so it can be removed by handle. */
    public record HandleEntry(Renderable renderable, RenderableLayer parentLayer, Layer standaloneLayer) {
        public String type() {
            if (standaloneLayer != null) return "layer";
            if (renderable != null) return renderable.getClass().getSimpleName();
            return "unknown";
        }
    }
}
