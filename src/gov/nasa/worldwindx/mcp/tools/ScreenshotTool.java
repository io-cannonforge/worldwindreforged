/*
 * WorldWind Reforged MCP Server
 * seaglassfoundry.com
 *
 * Captures a screenshot of the current globe view.
 */
package gov.nasa.worldwindx.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jogamp.opengl.GLAutoDrawable;
import gov.nasa.worldwindx.mcp.GlobeState;
import gov.nasa.worldwindx.mcp.Json;
import gov.nasa.worldwindx.mcp.Tool;
import gov.nasa.worldwindx.mcp.ToolResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Captures a PNG screenshot of the current globe view via glReadPixels.
 * Returns the image as base64-encoded PNG in the MCP response.
 */
public final class ScreenshotTool implements Tool {

    @Override
    public String name() { return "screenshot"; }

    @Override
    public String description() {
        return "Capture a PNG screenshot of the current globe view and return it as a base64 image.";
    }

    @Override
    public ObjectNode inputSchema() {
        return Json.objectSchema();
    }

    @Override
    public ToolResult call(JsonNode arguments, GlobeState state) throws Exception {
        final BufferedImage[] imageRef = new BufferedImage[1];
        final Exception[] errorRef = new Exception[1];
        CountDownLatch latch = new CountDownLatch(1);

        // Must capture on the GL thread via invoke().
        state.wwd().invoke(false, (GLAutoDrawable drawable) -> {
            try {
                int w = drawable.getSurfaceWidth();
                int h = drawable.getSurfaceHeight();
                var gl = drawable.getGL().getGL2();

                ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4);
                gl.glReadPixels(0, 0, w, h, com.jogamp.opengl.GL.GL_RGBA,
                        com.jogamp.opengl.GL.GL_UNSIGNED_BYTE, buffer);

                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        // OpenGL origin is bottom-left; flip vertically.
                        int srcIdx = ((h - 1 - y) * w + x) * 4;
                        int r = buffer.get(srcIdx) & 0xFF;
                        int g = buffer.get(srcIdx + 1) & 0xFF;
                        int b = buffer.get(srcIdx + 2) & 0xFF;
                        int a = buffer.get(srcIdx + 3) & 0xFF;
                        img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                    }
                }
                imageRef[0] = img;
            } catch (Exception e) {
                errorRef[0] = e;
            } finally {
                latch.countDown();
            }
            return true;
        });

        if (!latch.await(10, TimeUnit.SECONDS)) {
            return ToolResult.error("Screenshot timed out waiting for GL thread.");
        }
        if (errorRef[0] != null) {
            return ToolResult.error("GL capture failed: " + errorRef[0].getMessage());
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(imageRef[0], "PNG", baos);
        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

        return ToolResult.ok(String.format("Screenshot captured (%dx%d).",
                imageRef[0].getWidth(), imageRef[0].getHeight()))
                .image(base64)
                .build();
    }
}
