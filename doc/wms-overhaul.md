# WMS Overhaul — Phase 2

Comprehensive improvements to the WMS tile retrieval stack, the WMS Explorer UI, and
data-source breadth. Work by seaglassfoundry.com.

---

## 2.1 Core Engine

### Retry logic with exponential backoff

`URLRetriever.call()` now retries transient failures up to a configurable limit instead of
failing immediately.

| Config key | AVKey constant | Default | Description |
|---|---|---|---|
| `gov.nasa.worldwind.avkey.URLMaxRetries` | `AVKey.URL_MAX_RETRIES` | `3` | Max retry attempts per request |
| `gov.nasa.worldwind.avkey.URLRetryBaseDelay` | `AVKey.URL_RETRY_BASE_DELAY` | `500` ms | Base delay; doubles each attempt (500 → 1 000 → 2 000 ms) |

Retryable exceptions: `SocketException`, `SocketTimeoutException`.
Non-retryable: `UnknownHostException` (DNS failure), `ClosedByInterruptException`.

**Modified files**

| File | What changed |
|---|---|
| `src/gov/nasa/worldwind/avlist/AVKey.java` | Added `URL_MAX_RETRIES`, `URL_RETRY_BASE_DELAY`, `WMTS_*` constants |
| `src/gov/nasa/worldwind/retrieve/URLRetriever.java` | `call()` replaced with retry loop; `end()` moved outside loop so post-processor runs exactly once |

### Connection reuse and pool sizing

HTTP keep-alive is now explicitly requested on every tile request, reducing TCP handshake
overhead when a server supports persistent connections.  The JVM connection pool is sized
to match the retrieval thread pool.

| Before | After |
|---|---|
| Pool size default: **5** | Pool size default: **10** |
| No keep-alive header | `Connection: keep-alive` + `Keep-Alive: timeout=30, max=100` |
| `http.maxConnections` not set | Set to match `AVKey.RETRIEVAL_POOL_SIZE` |

**Modified files**

| File | What changed |
|---|---|
| `src/gov/nasa/worldwind/retrieve/BasicRetrievalService.java` | `DEFAULT_POOL_SIZE` 5 → 10 |
| `src/gov/nasa/worldwind/retrieve/HTTPRetriever.java` | Added `openConnection()` override; sets keep-alive headers and `http.maxConnections` system property |

### Replace dead NASA endpoints

The `data.worldwind.arc.nasa.gov` WMS/elevation servers are offline.

| Config file | Old URL | New URL | Layer |
|---|---|---|---|
| `BMNGWMSLayer.xml` | `data.worldwind.arc.nasa.gov/wms` | `gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi` | `BlueMarble_ShadedRelief_Bathymetry` |
| `LandsatI3WMSLayer.xml` | `data.worldwind.arc.nasa.gov/wms` | `gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi` | `Landsat_WELD_CorrectedReflectance_TrueColor_Global_Annual` |
| `OpenStreetMap.xml` | `worldwind20.arc.nasa.gov/mapcache` | `ows.terrestris.de/osm/service` | `OSM-WMS` |

> **Note**: The elevation model XMLs (`EarthElevationModel*.xml`) reference
> `data.worldwind.arc.nasa.gov/elev` for `mergedAsterElevations` in BIL16/BIL32 format.
> A suitable public replacement that serves the same binary elevation format was not
> identified; those files are left with a comment noting the dead endpoint.  USGS 3DEP
> (`elevation.nationalmap.gov`) serves the same data but only via WCS, not WMS BIL.

### WMTS support

New class `WMTSTiledImageLayer` extends `BasicTiledImageLayer` with a WMTS 1.0.0 tile
URL builder.  Both REST and KVP bindings are supported.

```
BasicTiledImageLayer
    └── WMTSTiledImageLayer
              └── WMTSTiledImageLayer.URLBuilder   (REST or KVP tile URLs)
```

**New files**

| File | Purpose |
|---|---|
| `src/gov/nasa/worldwind/wms/WMTSTiledImageLayer.java` | WMTS layer + URLBuilder |

**URL patterns**

REST (e.g. NASA GIBS):
```
{serviceUrl}/{layer}/{style}/{date}/{tileMatrixSet}/{Z}/{Y}/{X}.{ext}
```
KVP:
```
{serviceUrl}?SERVICE=WMTS&REQUEST=GetTile&LAYER=…&TILEMATRIXSET=…&TILEMATRIX=Z&TILEROW=Y&TILECOL=X&TIME=…
```

**Required params**

| `AVKey` | Description |
|---|---|
| `SERVICE` | Base URL |
| `LAYER_NAMES` | WMTS layer id |
| `WMTS_TILE_MATRIX_SET` | TileMatrixSet id (e.g. `250m`) |
| `WMTS_BINDING` | `REST` or `KVP` |
| `WMTS_TILE_MATRIX_OFFSET` | WorldWind level 0 → WMTS TileMatrix N (default 0) |
| `WMS_TIME_STRING` | ISO 8601 date for time-series layers |

---

## 2.2 Weather / Time-Series

### Time-aware caching (per-time-step tile directories)

Previously, all time steps for a WMS layer shared the same on-disk tile cache directory,
so switching time steps would overwrite cached tiles.  Now each time step gets its own
`DataCacheName` subdirectory:

```
Earth/GIBS/MODIS_Terra/              ← base (non-time-aware or non-time layers)
Earth/GIBS/MODIS_Terra/t_2024-01-01/ ← tiles for Jan 1 2024
Earth/GIBS/MODIS_Terra/t_2024-01-02/ ← tiles for Jan 2 2024
...
```

Scrubbing backward in the time slider reuses cached tiles (no re-download).  The
in-memory layer cache is capped at 30 time steps per entry; older entries are evicted.

### Prefetch for adjacent time steps

After switching to time step N, background threads pre-create `WMSTiledImageLayer`
instances for steps N-1, N+1, N+2.  Creating the layer object pre-initialises the
`LevelSet` and disk cache directory, eliminating initialisation latency when those
steps are later selected.

**Modified files**

| File | What changed |
|---|---|
| `WMSExplorer.java` | `LayerEntry.timeLayerCache`, `getOrCreateTimeLayer()`, `prefetchAdjacentTimeSteps()`, revised `updateLayerTime()` |

### Temporal layer presets

One-click buttons in the WMS Explorer automatically connect to NASA GIBS and enable a
preconfigured time-series layer (MODIS Terra, VIIRS S-NPP, MODIS Fires, Snow Cover).

---

## 2.3 UI / UX

### Server health indicators

Each preset server button shows a coloured dot:

| Colour | Meaning |
|---|---|
| Grey | Not yet checked |
| Green | HTTP 200–399 response within 6 s |
| Yellow | Unexpected status code |
| Red | Connection failed / timeout |

A `ScheduledExecutorService` probes each preset server with a lightweight
`GetCapabilities` `HEAD` request every 60 seconds.

### Legend display

Each entry in the Active Layers panel has a **Legend** button.  Clicking it sends a
`GetLegendGraphic` request (PNG) to the WMS server and displays the returned image in a
scrollable dialog.

### GetFeatureInfo click-to-query

Right-clicking on the globe issues a `GetFeatureInfo` request for every active WMS layer.
The result HTML is stripped of tags and shown in a floating popup near the click point.
The popup auto-dismisses after 30 seconds.

---

## 2.4 Data Sources

### OpenStreetMap base map

`OpenStreetMap.xml` now points to the terrestris WMS (`ows.terrestris.de/osm/service`,
layer `OSM-WMS`) instead of the defunct NASA WorldWind OSM proxy.
