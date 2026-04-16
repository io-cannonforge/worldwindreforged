/*
 * WorldWind Reforged — WCSTiledImageLayer
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * New layer that renders WCS (Web Coverage Service) scalar data as draped imagery
 * on the globe.  Unlike WCSElevationModel, which feeds raw height values into the
 * terrain mesh, this layer converts single-band GeoTIFF data (slope, aspect, etc.)
 * into coloured ARGB textures using a configurable colour ramp.
 */
package gov.nasa.worldwind.layers;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.ogc.wcs.wcs100.WCS100Capabilities;
import gov.nasa.worldwind.ogc.wcs.wcs100.WCS100DescribeCoverage;
import gov.nasa.worldwind.util.DataConfigurationUtils;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.TileUrlBuilder;

/**
 * A tiled image layer backed by a WCS 1.0.0 endpoint.  Fetches single-band scalar
 * GeoTIFF tiles (e.g. slope, aspect, hillshade) and converts them to ARGB imagery
 * using a colour ramp before uploading to the GPU.
 * <p>
 * Tile grid and caching conventions match {@link gov.nasa.worldwind.terrain.WCSElevationModel}
 * so that tiles already present in the local cache are reused immediately.
 *
 * seaglassfoundry.com — new class for WorldWind Reforged
 */
public class WCSTiledImageLayer extends BasicTiledImageLayer
{
    // Default colour ramp: green → yellow → orange → red  (for slope-like 0-90 data).
    private static final int[] RAMP = buildDefaultRamp();

    private static final double DEFAULT_MISSING_SIGNAL = -9999.0;

    private final double missingDataSignal;

    // ── Construction ──────────────────────────────────────────────────────────

    /**
     * Creates a WCS imagery layer from pre-built parameters.
     *
     * @param params AVList containing at minimum: DISPLAY_NAME, DATASET_NAME,
     *               DATA_CACHE_NAME, SERVICE (GetCoverage URL), SECTOR, IMAGE_FORMAT,
     *               COVERAGE_IDENTIFIERS, WCS_VERSION, and TILE_URL_BUILDER.
     */
    public WCSTiledImageLayer(AVList params)
    {
        super(params);

        Double ms = (Double) params.getValue(AVKey.MISSING_DATA_SIGNAL);
        this.missingDataSignal = ms != null ? ms : DEFAULT_MISSING_SIGNAL;

        this.setUseTransparentTextures(true);
    }

    /**
     * Convenience factory that builds an imagery layer from WCS capabilities and a
     * DescribeCoverage document, mirroring the
     * {@link gov.nasa.worldwind.terrain.WCSElevationModel#WCSElevationModel(WCS100Capabilities, AVList)}
     * constructor pattern.
     */
    public static WCSTiledImageLayer fromWCS(WCS100Capabilities caps, AVList params)
    {
        if (caps == null) {
			throw new IllegalArgumentException(Logging.getMessage("nullValue.WCSCapabilities"));
		}
        if (params == null) {
			throw new IllegalArgumentException(Logging.getMessage("nullValue.ParametersIsNull"));
		}

        WCS100DescribeCoverage coverage = (WCS100DescribeCoverage) params.getValue(AVKey.DOCUMENT);
        if (coverage == null) {
			throw new IllegalArgumentException(Logging.getMessage("nullValue.WCSDescribeCoverage"));
		}

        // Reuse the same config-extraction pipeline as WCSElevationModel.
        DataConfigurationUtils.getWCSConfigParameters(caps, coverage, params);
        setWCSFallbacks(params);

        // Ensure required params are present.
        requireParam(params, AVKey.DATASET_NAME, "WCS.NoCoverageName");
        requireParam(params, AVKey.SERVICE, "WCS.NoGetCoverageURL");
        requireParam(params, AVKey.DATA_CACHE_NAME, "nullValue.DataCacheIsNull");
        requireParam(params, AVKey.IMAGE_FORMAT, "WCS.NoImageFormats");

        params.setValue(AVKey.TILE_URL_BUILDER,
            new WCSURLBuilder(caps.getVersion(), params));

        return new WCSTiledImageLayer(params);
    }

    // ── Tile grid defaults (must match WCSElevationModel for cache compat) ───

    private static void setWCSFallbacks(AVList params)
    {
        if (params.getValue(AVKey.LEVEL_ZERO_TILE_DELTA) == null)
        {
            Angle delta = Angle.fromDegrees(20);
            params.setValue(AVKey.LEVEL_ZERO_TILE_DELTA, new LatLon(delta, delta));
        }
        if (params.getValue(AVKey.TILE_WIDTH) == null) {
			params.setValue(AVKey.TILE_WIDTH, 150);
		}
        if (params.getValue(AVKey.TILE_HEIGHT) == null) {
			params.setValue(AVKey.TILE_HEIGHT, 150);
		}
        if (params.getValue(AVKey.FORMAT_SUFFIX) == null) {
			params.setValue(AVKey.FORMAT_SUFFIX, ".tif");
		}
        if (params.getValue(AVKey.MISSING_DATA_SIGNAL) == null) {
			params.setValue(AVKey.MISSING_DATA_SIGNAL, -9999d);
		}
        if (params.getValue(AVKey.NUM_LEVELS) == null) {
			params.setValue(AVKey.NUM_LEVELS, 18);
		}
        if (params.getValue(AVKey.NUM_EMPTY_LEVELS) == null) {
			params.setValue(AVKey.NUM_EMPTY_LEVELS, 0);
		}
    }

    // ── Texture loading — convert scalar GeoTIFF to ARGB ─────────────────────

    @Override
    protected boolean loadTexture(TextureTile tile, URL textureURL)
    {
        try
        {
            BufferedImage image;
            synchronized (this.fileLock)
            {
                image = readTiffWithBuiltInReader(new File(textureURL.toURI()));
            }
            if (image == null) {
				return false;
			}

            BufferedImage coloured = colourize(image);
            if (coloured == null) {
				return false;
			}

            TextureData td = AWTTextureIO.newTextureData(
                Configuration.getMaxCompatibleGLProfile(), coloured, this.isUseMipMaps());
            if (td == null) {
				return false;
			}

            tile.setTextureData(td);
            if (tile.getLevelNumber() != 0 || !this.isRetainLevelZeroTiles()) {
				this.addTileToCache(tile);
			}

            return true;
        }
        catch (Exception e)
        {
            Logging.logger().log(java.util.logging.Level.FINE,
                Logging.getMessage("layers.TextureLayer.ExceptionAttemptingToReadTextureFile", textureURL), e);
            return false;
        }
    }

    /**
     * Converts a single-band scalar raster into an ARGB image using the colour ramp.
     * Missing / non-finite pixels become fully transparent.
     */
    private BufferedImage colourize(BufferedImage src)
    {
        int w = src.getWidth();
        int h = src.getHeight();
        WritableRaster raster = src.getRaster();

        // First pass: find data range within this tile (excluding missing values).
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        float signal = (float) this.missingDataSignal;
        boolean hasData = false;

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                float v = raster.getSampleFloat(x, y, 0);
                if (!Float.isFinite(v) || v == signal || v < -1e7f || v > 1e7f) {
					continue;
				}
                if (v < min) {
					min = v;
				}
                if (v > max) {
					max = v;
				}
                hasData = true;
            }
        }

        if (!hasData) {
			return null; // entirely missing — skip this tile
		}

        float range = max - min;
        if (range < 1e-6f) {
			range = 1.0f; // flat tile — avoid division by zero
		}

        // Second pass: map values to colours.
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int rampMax = RAMP.length - 1;

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                float v = raster.getSampleFloat(x, y, 0);
                if (!Float.isFinite(v) || v == signal || v < -1e7f || v > 1e7f)
                {
                    dst.setRGB(x, y, 0x00000000); // fully transparent
                    continue;
                }

                float t = (v - min) / range;
                if (t < 0f) {
					t = 0f;
				}
                if (t > 1f) {
					t = 1f;
				}
                int idx = (int) (t * rampMax);
                dst.setRGB(x, y, RAMP[idx]);
            }
        }

        return dst;
    }

    // ── GeoTIFF reading (same proven pattern as BasicElevationModel) ─────────

    private static BufferedImage readTiffWithBuiltInReader(File file) throws IOException
    {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("tiff");
        while (readers.hasNext())
        {
            ImageReader reader = readers.next();
            // Skip WorldWind's GeotiffImageReader — it can't handle tiled/compressed TIFFs.
            if (reader.getClass().getName().contains("GeotiffImageReader")) {
				continue;
			}

            try (javax.imageio.stream.ImageInputStream iis = ImageIO.createImageInputStream(file))
            {
                if (iis == null) {
					return null;
				}
                reader.setInput(iis);
                return reader.read(0);
            }
            finally
            {
                reader.dispose();
            }
        }
        return null;
    }

    // ── Colour ramp ──────────────────────────────────────────────────────────

    /**
     * Builds a 256-entry ARGB colour ramp: green → yellow → orange → red.
     * Suitable for slope, curvature, and similar 0→max derived products.
     */
    private static int[] buildDefaultRamp()
    {
        int[] ramp = new int[256];
        for (int i = 0; i < 256; i++)
        {
            float t = i / 255f;
            int r, g, b;

            if (t < 0.25f)
            {
                // green → yellow-green
                float s = t / 0.25f;
                r = (int) (0 + s * 180);
                g = (int) (160 + s * 75);
                b = (int) (50 * (1 - s));
            }
            else if (t < 0.5f)
            {
                // yellow-green → yellow
                float s = (t - 0.25f) / 0.25f;
                r = (int) (180 + s * 75);
                g = (int) (235 - s * 35);
                b = 0;
            }
            else if (t < 0.75f)
            {
                // yellow → orange
                float s = (t - 0.5f) / 0.25f;
                r = 255;
                g = (int) (200 - s * 120);
                b = 0;
            }
            else
            {
                // orange → red
                float s = (t - 0.75f) / 0.25f;
                r = 255;
                g = (int) (80 - s * 80);
                b = 0;
            }

            r = clamp(r);
            g = clamp(g);
            b = clamp(b);

            // Semi-transparent so terrain texture shows through.
            int a = 180;
            ramp[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        return ramp;
    }

    private static int clamp(int v)
    {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    // ── WCS URL builder (matches WCSElevationModel.URLBuilder) ───────────────

    protected static class WCSURLBuilder implements TileUrlBuilder
    {
        private final String layerNames;
        private final String imageFormat;
        private final String serviceVersion;
        private String urlTemplate = null;

        protected WCSURLBuilder(String version, AVList params)
        {
            this.serviceVersion = version != null ? version : "1.0.0";
            this.layerNames = params.getStringValue(AVKey.COVERAGE_IDENTIFIERS);
            this.imageFormat = params.getStringValue(AVKey.IMAGE_FORMAT);
        }

        @Override
        public URL getURL(gov.nasa.worldwind.util.Tile tile, String altImageFormat)
            throws MalformedURLException
        {
            StringBuilder sb;
            if (this.urlTemplate == null)
            {
                sb = new StringBuilder(tile.getLevel().getService());

                if (!sb.toString().toLowerCase().contains("service=wcs")) {
					sb.append("service=WCS");
				}
                sb.append("&request=GetCoverage");
                sb.append("&version=").append(this.serviceVersion);
                sb.append("&crs=EPSG:4326");
                sb.append("&coverage=").append(this.layerNames);
                sb.append("&format=").append(
                    altImageFormat != null ? altImageFormat : this.imageFormat);

                this.urlTemplate = sb.toString();
            }
            else
            {
                sb = new StringBuilder(this.urlTemplate);
            }

            sb.append("&width=").append(tile.getWidth());
            sb.append("&height=").append(tile.getHeight());

            Sector s = tile.getSector();
            sb.append("&bbox=")
                .append(s.getMinLongitude().getDegrees()).append(",")
                .append(s.getMinLatitude().getDegrees()).append(",")
                .append(s.getMaxLongitude().getDegrees()).append(",")
                .append(s.getMaxLatitude().getDegrees());

            sb.append("&"); // terminate the query string

            return URI.create(sb.toString().replace(" ", "%20")).toURL();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void requireParam(AVList params, String key, String messageKey)
    {
        if (params.getStringValue(key) == null)
        {
            String msg = Logging.getMessage(messageKey);
            Logging.logger().severe(msg);
            throw new IllegalStateException(msg);
        }
    }
}
