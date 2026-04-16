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
 */
/*
 * seaglassfoundry.com: WMTSTiledImageLayer — new WMTS protocol client supporting
 * REST and KVP tile bindings with TIME parameter for time-series layers (e.g. NASA GIBS).
 * Added 2026-03-27.
 */
package gov.nasa.worldwind.wms;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.layers.BasicTiledImageLayer;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.Tile;
import gov.nasa.worldwind.util.TileUrlBuilder;

/**
 * Tiled image layer that fetches tiles from an OGC Web Map Tile Service (WMTS 1.0.0).
 *
 * <p>Supports both the REST (ResourceURL template) binding and the KVP (GetTile) binding.
 * The tile matrix mapping follows the standard EPSG:4326 / CRS:84 scheme used by NASA GIBS
 * and most other public WMTS providers:
 * <ul>
 *   <li>TileMatrix 0 → 2 tiles wide × 1 tile tall (360° × 180° at 256 px/tile)</li>
 *   <li>WorldWind level N → WMTS TileMatrix (N + matrixOffset)</li>
 * </ul>
 *
 * <h3>Required AVList parameters</h3>
 * <table>
 *   <tr><th>Key</th><th>Description</th></tr>
 *   <tr><td>{@link AVKey#SERVICE}</td><td>Base URL of the WMTS endpoint</td></tr>
 *   <tr><td>{@link AVKey#LAYER_NAMES}</td><td>WMTS layer identifier</td></tr>
 *   <tr><td>{@link AVKey#WMTS_TILE_MATRIX_SET}</td><td>TileMatrixSet identifier (e.g. {@code 250m})</td></tr>
 *   <tr><td>{@link AVKey#STYLE_NAMES}</td><td>Style identifier (default: {@code default})</td></tr>
 *   <tr><td>{@link AVKey#IMAGE_FORMAT}</td><td>MIME type (e.g. {@code image/png})</td></tr>
 *   <tr><td>{@link AVKey#WMTS_BINDING}</td><td>{@code REST} or {@code KVP} (default: {@code KVP})</td></tr>
 *   <tr><td>{@link AVKey#WMTS_TILE_MATRIX_OFFSET}</td><td>Integer offset: WorldWind level 0 maps to
 *       TileMatrix {@code offset} (default: 0)</td></tr>
 *   <tr><td>{@link AVKey#WMS_TIME_STRING}</td><td>Optional ISO 8601 date/time string for time-series layers</td></tr>
 * </table>
 *
 * <h3>REST binding URL template</h3>
 * <pre>
 *   {serviceUrl}/{layer}/{style}/{tileMatrixSet}/{tileMatrix}/{tileRow}/{tileCol}.{ext}
 * </pre>
 * For time-series (when WMS_TIME_STRING is set):
 * <pre>
 *   {serviceUrl}/{layer}/default/{date}/{tileMatrixSet}/{tileMatrix}/{tileRow}/{tileCol}.{ext}
 * </pre>
 *
 * <h3>Example — NASA GIBS MODIS Terra (REST)</h3>
 * <pre>
 *   AVListImpl params = new AVListImpl();
 *   params.setValue(AVKey.SERVICE,             "https://gibs.earthdata.nasa.gov/wmts/epsg4326/best");
 *   params.setValue(AVKey.LAYER_NAMES,         "MODIS_Terra_CorrectedReflectance_TrueColor");
 *   params.setValue(AVKey.WMTS_TILE_MATRIX_SET,"250m");
 *   params.setValue(AVKey.IMAGE_FORMAT,        "image/jpeg");
 *   params.setValue(AVKey.WMTS_BINDING,        "REST");
 *   params.setValue(AVKey.WMS_TIME_STRING,     "2024-01-01");
 *   params.setValue(AVKey.DATA_CACHE_NAME,     "Earth/GIBS/MODIS_Terra_TrueColor");
 *   // ... add tile-grid parameters (NumLevels, LevelZeroTileDelta, TileSize, Sector) ...
 *   WMTSTiledImageLayer layer = new WMTSTiledImageLayer(params);
 * </pre>
 */
public class WMTSTiledImageLayer extends BasicTiledImageLayer
{
    public WMTSTiledImageLayer(AVList params)
    {
        super(initParams(params));
    }

    /** Injects the WMTS-specific URL builder into the params before passing to the superclass. */
    private static AVList initParams(AVList params)
    {
        if (params == null) {
			throw new IllegalArgumentException(Logging.getMessage("nullValue.AVListIsNull"));
		}
        params.setValue(AVKey.TILE_URL_BUILDER, new URLBuilder(params));
        params.setValue(AVKey.USE_TRANSPARENT_TEXTURES, true);
        return params;
    }

    // ------------------------------------------------------------------
    //  URL builder
    // ------------------------------------------------------------------

    public static class URLBuilder implements TileUrlBuilder
    {
        private static final String BINDING_REST = "REST";

        private final String serviceUrl;
        private final String layer;
        private final String style;
        private final String tileMatrixSet;
        private final String imageFormat;
        private final String fileExtension; // derived from imageFormat, e.g. "png"
        private final boolean restBinding;
        private final int matrixOffset;
        private volatile String timeString;

        public URLBuilder(AVList params)
        {
            this.serviceUrl   = params.getStringValue(AVKey.SERVICE);
            this.layer        = params.getStringValue(AVKey.LAYER_NAMES);
            this.style        = nvl(params.getStringValue(AVKey.STYLE_NAMES), "default");
            this.tileMatrixSet = params.getStringValue(AVKey.WMTS_TILE_MATRIX_SET);
            this.imageFormat  = nvl(params.getStringValue(AVKey.IMAGE_FORMAT), "image/png");
            this.fileExtension = mimeToExt(this.imageFormat);
            String binding    = params.getStringValue(AVKey.WMTS_BINDING);
            this.restBinding  = BINDING_REST.equalsIgnoreCase(binding);
            Object offset     = params.getValue(AVKey.WMTS_TILE_MATRIX_OFFSET);
            this.matrixOffset = (offset instanceof Number) ? ((Number) offset).intValue() : 0;
            this.timeString   = params.getStringValue(AVKey.WMS_TIME_STRING);
        }

        public String getTimeString()   { return timeString; }
        public void setTimeString(String t) { this.timeString = t; }

        @Override
        public URL getURL(Tile tile, String altImageFormat) throws MalformedURLException
        {
            int tileMatrix = tile.getLevelNumber() + matrixOffset;
            int tileRow    = tile.getRow();
            int tileCol    = tile.getColumn();

            String urlStr;
            if (restBinding) {
				urlStr = buildRestUrl(tileMatrix, tileRow, tileCol);
			} else {
				urlStr = buildKvpUrl(tileMatrix, tileRow, tileCol, altImageFormat);
			}

            return URI.create(urlStr).toURL();
        }

        private String buildRestUrl(int tileMatrix, int tileRow, int tileCol)
        {
            // REST template: {serviceUrl}/{layer}/{style}/{tileMatrixSet}/{tileMatrix}/{tileRow}/{tileCol}.{ext}
            // For time-series layers GIBS inserts the date between style and tileMatrixSet:
            //   {serviceUrl}/{layer}/default/{date}/{tileMatrixSet}/{tileMatrix}/{tileRow}/{tileCol}.{ext}
            StringBuilder sb = new StringBuilder(serviceUrl);
            if (!serviceUrl.endsWith("/")) {
				sb.append('/');
			}
            sb.append(layer).append('/');
            sb.append(style).append('/');
            if (timeString != null && !timeString.isEmpty()) {
				sb.append(timeString).append('/');
			}
            sb.append(tileMatrixSet).append('/');
            sb.append(tileMatrix).append('/');
            sb.append(tileRow).append('/');
            sb.append(tileCol).append('.').append(fileExtension);
            return sb.toString();
        }

        private String buildKvpUrl(int tileMatrix, int tileRow, int tileCol, String altFormat)
        {
            String fmt = (altFormat != null) ? altFormat : imageFormat;
            StringBuilder sb = new StringBuilder(serviceUrl);
            if (!serviceUrl.contains("?")) {
				sb.append('?');
			} else {
				sb.append('&');
			}
            sb.append("SERVICE=WMTS");
            sb.append("&REQUEST=GetTile");
            sb.append("&VERSION=1.0.0");
            sb.append("&LAYER=").append(layer);
            sb.append("&STYLE=").append(style);
            sb.append("&TILEMATRIXSET=").append(tileMatrixSet);
            sb.append("&TILEMATRIX=").append(tileMatrix);
            sb.append("&TILEROW=").append(tileRow);
            sb.append("&TILECOL=").append(tileCol);
            sb.append("&FORMAT=").append(fmt.replace("/", "%2F"));
            if (timeString != null && !timeString.isEmpty()) {
				sb.append("&TIME=").append(timeString);
			}
            return sb.toString();
        }

        // ------ helpers ------

        private static String nvl(String s, String def) { return (s != null && !s.isEmpty()) ? s : def; }

        private static String mimeToExt(String mime)
        {
            if (mime == null) {
				return "png";
			}
            return switch (mime.toLowerCase())
            {
                case "image/jpeg", "image/jpg" -> "jpg";
                case "image/tiff"              -> "tif";
                case "image/gif"               -> "gif";
                default                        -> "png";
            };
        }
    }
}
