/*
 * WorldWind Reforged MCP Server
 * seaglassfoundry.com
 *
 * Overlays a GeoTIFF image on the globe as a SurfaceImage.  Tries WorldWind's
 * native GeotiffReader first; falls back to javax.imageio (which handles DEFLATE
 * compression, grayscale, etc.) while still extracting the sector from GeoTIFF
 * metadata.
 *
 * Modified by seaglassfoundry.com -- ImageIO fallback for ForgeGIS-produced
 * GeoTIFFs that use DEFLATE compression or PhotometricInterpretation values
 * not fully supported by the legacy WorldWind TIFF reader.
 */
package gov.nasa.worldwindx.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.data.BufferedImageRaster;
import gov.nasa.worldwind.data.DataRaster;
import gov.nasa.worldwind.formats.tiff.GeotiffReader;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.render.SurfaceImage;
import gov.nasa.worldwindx.mcp.GlobeState;
import gov.nasa.worldwindx.mcp.Json;
import gov.nasa.worldwindx.mcp.Tool;
import gov.nasa.worldwindx.mcp.ToolResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.io.File;

/**
 * Loads a GeoTIFF as a surface image overlay on the globe using WorldWind's
 * native {@link GeotiffReader}. The GeoTIFF should be a pre-rendered
 * color-mapped image (e.g. output from ForgeGIS analysis tools).
 *
 * <p>No ForgeGIS dependency; pure WorldWind.</p>
 */
public final class AddRasterLayerTool implements Tool {

    @Override
    public String name() { return "add_raster_layer"; }

    @Override
    public String description() {
        return "Overlay a GeoTIFF image on the globe as a surface layer. "
             + "The file should be a pre-rendered color-mapped image (e.g. from ForgeGIS analysis).";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = Json.objectSchema();
        Json.addProp(schema, "path", "string",
            "Absolute path to a GeoTIFF file to overlay on the globe.");
        Json.addProp(schema, "opacity", "number",
            "Layer opacity from 0.0 (transparent) to 1.0 (opaque). Default 0.7.");
        Json.addProp(schema, "name", "string",
            "Display name for this layer (default: filename).");
        Json.requireProps(schema, "path");
        return schema;
    }

    @Override
    public ToolResult call(JsonNode arguments, GlobeState state) throws Exception {
        String path = arguments.path("path").asText(null);
        if (path == null || path.isBlank()) {
            return ToolResult.error("'path' is required.");
        }

        File file = new File(path);
        if (!file.isFile()) {
            return ToolResult.error("File not found: " + path);
        }

        double opacity = arguments.path("opacity").asDouble(0.7);
        String displayName = arguments.path("name").asText(file.getName());

        // Read GeoTIFF -- try WorldWind-native reader first, fall back to ImageIO.
        BufferedImage image = null;
        Sector sector = null;

        try (GeotiffReader reader = new GeotiffReader(file)) {
            // Always extract sector from GeoTIFF metadata (works even when pixel
            // reading fails, since metadata is parsed during construction).
            try {
                AVList meta = reader.copyMetadataTo(0, null);
                if (meta != null) {
                    sector = (Sector) meta.getValue(AVKey.SECTOR);
                }
            } catch (Exception ignored) { }

            if (sector == null) {
                return ToolResult.error("GeoTIFF has no georeferencing (no sector).");
            }

            // Attempt 1: WorldWind-native pixel reading (handles RGB, palette).
            try {
                DataRaster[] rasters = reader.readDataRaster();
                if (rasters != null && rasters.length > 0) {
                    DataRaster raster = rasters[0];
                    DataRaster subRaster = raster.getSubRaster(
                            raster.getWidth(), raster.getHeight(), sector, null);
                    raster.dispose();
                    if (subRaster instanceof BufferedImageRaster bir) {
                        image = bir.getBufferedImage();
                        subRaster.dispose();
                    } else {
                        subRaster.dispose();
                    }
                }
            } catch (Exception nativeErr) {
                // Fall through to ImageIO.
            }
        }

        // Attempt 2: javax.imageio (handles DEFLATE, grayscale, etc.).
        if (image == null) {
            image = ImageIO.read(file);
        }

        if (image == null) {
            return ToolResult.error("Could not read image data from: " + file.getName());
        }

        // JOGL's AWTTextureIO cannot handle float-backed or TYPE_CUSTOM images
        // (throws GLException for GL_RGB + GL_FLOAT pixel attributes).
        // Graphics2D.drawImage also fails on scientific float data (overflow).
        // Manually normalize float pixels to 0-255 grayscale ARGB.
        // seaglassfoundry.com -- fix GLException on ForgeGIS float GeoTIFFs
        int dbType = image.getRaster().getDataBuffer().getDataType();
        if (image.getType() == BufferedImage.TYPE_CUSTOM
                || dbType == DataBuffer.TYPE_FLOAT
                || dbType == DataBuffer.TYPE_DOUBLE) {
            image = convertFloatImageToARGB(image);
        }

        // Create surface overlay.
        SurfaceImage surfaceImage = new SurfaceImage(image, sector);
        surfaceImage.setOpacity(opacity);

        state.runOnEdt(() ->
            state.overlayLayer().addRenderable(surfaceImage));
        state.wwd().redraw();

        String handle = state.newHandle("raster");
        state.registerHandle(handle, surfaceImage, state.overlayLayer());

        return ToolResult.ok(String.format(
                "Overlaid **%s** (%dx%d) at opacity %.0f%%.\n\nSector: %s",
                displayName, image.getWidth(), image.getHeight(),
                opacity * 100, sector.toString()))
                .handle("raster_layer", handle)
                .build();
    }

    /**
     * Converts a float/double-backed BufferedImage to TYPE_INT_ARGB by
     * normalizing pixel values per band to the 0-255 range.  Handles
     * single-band (grayscale) and multi-band (RGB) float rasters.
     * seaglassfoundry.com
     */
    private static BufferedImage convertFloatImageToARGB(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        Raster raster = src.getRaster();
        int bands = raster.getNumBands();

        // Find min/max per band for normalization.
        double[] min = new double[bands];
        double[] max = new double[bands];
        java.util.Arrays.fill(min, Double.MAX_VALUE);
        java.util.Arrays.fill(max, -Double.MAX_VALUE);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                for (int b = 0; b < bands; b++) {
                    double v = raster.getSampleDouble(x, y, b);
                    if (Double.isNaN(v) || v <= -32768) continue; // skip nodata
                    if (v < min[b]) min[b] = v;
                    if (v > max[b]) max[b] = v;
                }
            }
        }

        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double v0 = raster.getSampleDouble(x, y, 0);
                if (Double.isNaN(v0) || v0 <= -32768) {
                    dst.setRGB(x, y, 0x00000000); // transparent nodata
                    continue;
                }

                int r, g, b2;
                if (bands >= 3) {
                    r = scaleBand(raster.getSampleDouble(x, y, 0), min[0], max[0]);
                    g = scaleBand(raster.getSampleDouble(x, y, 1), min[1], max[1]);
                    b2 = scaleBand(raster.getSampleDouble(x, y, 2), min[2], max[2]);
                } else {
                    int gray = scaleBand(v0, min[0], max[0]);
                    r = g = b2 = gray;
                }
                dst.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b2);
            }
        }
        return dst;
    }

    private static int scaleBand(double v, double min, double max) {
        if (max <= min) return 128;
        return Math.max(0, Math.min(255, (int) ((v - min) / (max - min) * 255.0)));
    }
}
