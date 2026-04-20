# WorldWind Reforged - Task Tracker

## Completed

- [x] Maven build system with JOGL 2.6.0, GDAL 3.12.0, Jackson 2.18.6
- [x] Java 17 compilation with `--add-opens` flags
- [x] Jackson 1.x → 2.18.6 migration (75 embedded files removed)
- [x] Dead NASA WMS server errors downgraded SEVERE → WARNING
- [x] Phase 1.1: Raw types fixed (~35 files)
- [x] Phase 1.2: Diamond operator (~1,138 instances)
- [x] Phase 1.3: Try-with-resources (31 blocks converted)
- [x] Phase 1.4: Lambda expressions (43 conversions)
- [x] Phase 1.5: Pattern matching instanceof (54 conversions)
- [x] Phase 1.6: Switch expressions (~30 conversions)
- [x] Phase 1.7: StringBuffer → StringBuilder (31 instances, 20 files)
- [x] Dependencies bumped to latest (Jackson 2.18.6, GDAL 3.12.0, Maven plugins)

## Phase 2: WMS Overhaul

### 2.1 Core Engine
- [x] **TIME parameter in GetMap requests** - URLBuilder includes TIME when set via AVKey.WMS_TIME_STRING
- [x] **Time extent parsing** - Parse ISO 8601 intervals (e.g., `2020-01-01/2024-12-31/P1D`) into discrete steps
- [x] **Retry logic** - URLRetriever retries SocketException/SocketTimeoutException up to 3× with 500/1000/2000 ms backoff; configurable via AVKey.URL_MAX_RETRIES / URL_RETRY_BASE_DELAY
- [x] **Replace dead NASA endpoints** - BMNGWMSLayer → GIBS BlueMarble_ShadedRelief_Bathymetry; LandsatI3WMSLayer → GIBS Landsat_WELD; OpenStreetMap → ows.terrestris.de OSM-WMS
- [x] **WMTS support** - WMTSTiledImageLayer with REST + KVP bindings, TIME parameter, configurable TileMatrixSet and level offset
- [x] **Connection improvements** - Default pool 5→10; HTTPRetriever adds Connection:keep-alive header; sets http.maxConnections to match pool size

### 2.2 Weather / Time-Series
- [x] **Time slider UI component** - Scrub through time-enabled WMS/WMTS layers
- [x] **Time slider wired to layers** - Slider changes update WMSTiledImageLayer URLBuilder TIME param + force tile refresh
- [x] **Animation playback** - Play/pause/speed controls for weather sequences
- [x] **Time-aware caching** - Per-time-step DataCacheName subdirectories; LRU layer cache (30 steps); background prefetch of N±1, N+2
- [x] **Temporal layer presets** - One-click MODIS Terra, VIIRS S-NPP, MODIS Fires, Snow Cover with auto-layer-select

### 2.3 UI / UX
- [x] **New WMS Explorer example** - Modern dark-themed Swing UI with server browser, time controls, layer management
- [x] **Server health indicators** - Coloured dot per preset server; background HEAD-ping every 60 s
- [x] **Layer opacity slider** - Per-layer transparency control in active layers panel
- [x] **Layer search/filter** - Filter layer list by keyword with live updating
- [x] **Legend display** - "Legend" button per active layer fetches GetLegendGraphic PNG and shows in scrollable dialog
- [ ] **Layer preview thumbnails** - Small preview image before adding layer
- [x] **GetFeatureInfo** - Right-click on globe queries all active WMS layers; result shown in floating dismissable popup

### 2.4 Data Sources to Add
- [x] **NASA GIBS** - gibs.earthdata.nasa.gov (preset in WMS Explorer)
- [x] **USGS National Map** - basemap.nationalmap.gov (preset in WMS Explorer)
- [x] **NOAA NWS** - opengeo.ncep.noaa.gov (preset in WMS Explorer)
- [x] **OpenStreetMap** - OpenStreetMap.xml updated to terrestris WMS (ows.terrestris.de/osm/service)

## Phase 3: GLSL Shaders for Surface Shapes
- [x] **Shader loading/compilation infrastructure** - ShaderProgram class (compile, link, uniforms, cleanup)
- [x] **ShaderProgram abstraction** - compile, link, uniform caching, dispose
- [x] **Dashed line shader** - DashLineShader replaces deprecated glLineStipple with GLSL fragment discard
- [x] **GL2 fallback path** - Automatic fallback to glLineStipple if shader init fails
- [x] **Dashed lines example** - DashedLinesExample with 10 shapes showing various patterns
- [x] **Interior fill shader** - SurfaceShapeFillShader replaces GLU tessellator immediate-mode with GLSL + VBO-cached ear-clipping triangulation
- [x] **Interior VBO cache** - InteriorVBOData caches triangulated geometry in GPU-resident buffers, eliminating per-frame retessellation
- [x] **Texture fill via shader** - Uniform texture matrix replicates GL_OBJECT_LINEAR coord generation with latitude correction and pixel-to-texel scaling
- [x] **GL2 fallback for fills** - Automatic fallback to GLU tessellator if GLSL 1.30 unavailable
- [x] **Per-shape migration** - SurfacePolygon and SurfacePolygons migrated to shader+VBO path; GLU tessellator eliminated from interior rendering (retained as fallback only)
  - SurfacePolygon: buildInteriorVBOs() handles holes via GpuTriangulator.bridgeHoles(), explicit per-vertex UVs via interleaved VBO + shader mode 2
  - SurfacePolygons: buildInteriorVBOs() groups rings by polygonRingGroups or winding order; dateline double-render via drawInterior() override; pole-wrapping falls back to GLU
  - SurfaceShapeFillShader extended with beginExplicitTextured() for per-vertex texture coordinates
- [x] **Anti-aliased line rendering** - GL_LINE_SMOOTH already enabled by default via BasicShapeAttributes.enableAntialiasing=true; toggled in applyOutlineState()
- [x] **Pattern/stipple rendering for complex dash patterns** - DashLineShader u_stipplePattern uniform maps 16-bit glLineStipple pattern to bit-exact GLSL discard; convertStippleToGapRatio() removed
- [x] **Procedural fill patterns in fragment shader** - ProceduralFillPattern record (HATCH/CROSSHATCH/DOTS); SurfaceShapeFillShader u_patternMode/scale/lineWidth/angle uniforms; AbstractSurfaceShape.setFillPattern() API; no texture needed — patterns computed from geographic position in GLSL

## Phase 4: Compute Shaders for Tessellation

> **REVERTED (seaglassfoundry.com, 2026-04-14):** All terrain GPU rendering (Tasks 4.1–4.5)
> has been removed due to persistent tile stitching/cracking artifacts. Terrain now uses
> the original fixed-function pipeline. Files deleted: TerrainShader.java,
> TessellationTerrainShader.java, ComputeMeshShader.java, GPUTerrainDemo.java,
> TerrainRenderingBenchmark.java. GLRuntimeCapabilities terrain shader flags removed.
> Phase 3 surface shape GPU work (GpuTessellator, GpuTriangulator) is retained.

## Phase 5: GPU Wiring — Connect Compute Paths to Render Thread

These tasks wire already-complete GPU infrastructure into the active render pipeline. Each is medium effort with high payoff.

- [x] **Wire GpuTessellator into AbstractSurfaceShape** — Already complete (found during scan); `generateIntermediateLocations()` tries GPU compute path first with CPU fallback
- [x] **Wire GpuTriangulator.triangulateBatch() into ShapefilePolygons** — Two-phase split: background thread collects merged ring data into `GpuTessellationData` (stored in `geom.gpuPendingData`); render thread calls `dispatchGpuTriangulation()` from `addTile()` to run GL 4.3 compute shader and populate `RecordIndices`; CPU ear-clipping fallback when GL 4.3 unavailable. Added `GpuTriangulator.isGpuViable()` predicate. Files: `ShapefilePolygons.java`, `GpuTriangulator.java`
- ~~[x] **Per-tile VAO binding in RectangularTessellator**~~ — REVERTED (terrain shader removal)

## Phase 6: GLSL Deprecated Built-in Cleanup

Replaced deprecated GLSL built-ins where feasible. GL profile upgraded to GL3bc for GLSL 3.30+ support. Terrain shader entries reverted (see Phase 4 note).

- ~~[ ] **Replace deprecated built-ins in TerrainShader**~~ — REVERTED (terrain shader removal)
- ~~[x] **Replace deprecated built-ins in TessellationTerrainShader**~~ — REVERTED (terrain shader removal)
- [x] **Replace gl_ModelViewProjectionMatrix with explicit u_mvp in DashLineShader and SurfaceShapeFillShader** — Both shaders now use `uniform mat4 u_mvp` populated by `ShaderProgram.setUniformMvp()` (reads GL matrix stack, computes P×MV column-major). `gl_Color` was never used — shaders already had `uniform vec4 u_color`. Files: `DashLineShader.java`, `SurfaceShapeFillShader.java`, `ShaderProgram.java`
- [x] **GL profile upgrade to GL3bc** — `Configuration.getMaxCompatibleGLProfile()` now prefers `GLProfile.GL3bc` (GL 3.x/4.x compatibility) over `getMaxFixedFunc()`. Guarantees GLSL 3.30+, `layout(location=N)`, and better-tested driver paths. All fixed-function APIs still available. Falls back to `getMaxFixedFunc(true)` on old hardware. File: `Configuration.java`
- ~~[x] **AMD vendor detection for VAO workaround**~~ — REVERTED (terrain shader removal; VAOs no longer used by tessellator)
- [x] **ShaderProgram.bindAttribLocation()** — Pre-link `glBindAttribLocation()` API added for shaders needing explicit attrib locations without `layout(location=N)` in GLSL source. File: `ShaderProgram.java`

## Phase 7: Java Language Modernization (Phase 1.8)

Low-effort cleanup improving code quality across the codebase.

- [ ] **String.format() → String.formatted()** — ~64 files use `String.format(fmt, args)`. Migrate to `fmt.formatted(args)` (Java 15+) for call-site readability. Automated search-and-replace; skip hot-path render methods. Files: widespread (WWXML.java, logging statements, UI labels)
- [x] **Boxed constructor removal** — Scanned codebase; no deprecated `new Integer(n)` / `new Double(d)` / `new Boolean(b)` constructors found. Codebase already uses `valueOf()` or unboxing throughout.
- [ ] **Date/Calendar → java.time** — ~35 files use `Calendar.getInstance()` or `new Date()` for timestamps in tile caching, WMS time strings, and data loaders. Migrate to `Instant` / `ZonedDateTime` for thread safety and ISO 8601 alignment. Files: WMS URL builders, BasicElevationModel cache paths, time-series layer code
- [x] **var local type inference** — Applied `var` to 68 `for (Map.Entry<...>)` loops (46 files) and ~228 `Iterator<T>` local declarations. Fixed 3 edge cases: `java.util.`-qualified prefix (stripped), field in anonymous class (reverted), null-initializer in CircularRangeFan (reverted to `Iterator<?>`).
- [x] **List.of() / Map.of() for immutable literals** — Converted 27 `Arrays.asList()` calls in `SymbologyConstants.java` (interface constants, implicitly final) and 1 `static final` field in `BasicDataFileStore.java` to `List.of()`.

## Phase 8: Test Coverage for Phase 3–4 Additions

New GPU infrastructure from Phases 3 and 4 has almost no test coverage. Each item below is a focused test class.

- [x] **GpuTriangulator unit tests** — 18 tests: `triangulateCPU()` for triangle/quad/pentagon/L-shape/collinear/32-gon/non-identity ring; `bridgeHoles()` with 0/1/2 holes; `generateOutlineIndices()` counts and offsets. No GL context. File: `GpuTriangulatorTest.java`
- [x] **DashLineShader compile test** — 17 tests: initial state (isValid/distAttribLocation/program null); GLSL source content (u_mvp, a_dist, v_dist, u_stipplePattern, mod-based dash, bit-shift stipple, explicit fragColor); stipple bit-mask arithmetic; bit-index formula. File: `DashLineShaderTest.java`
- [x] **SurfaceShapeFillShader compile + mode test** — 20 tests: pattern constants (NONE/HATCH/CROSSHATCH/DOTS distinct, semantics); initial state; vertex source (u_mvp, a_position, a_texCoord, u_texMatrix, branching); fragment source (all uniforms, hatch/crosshatch/dots modes, discard, explicit fragColor). File: `SurfaceShapeFillShaderTest.java`
- ~~[x] **TerrainShader / TessellationTerrainShader compile tests**~~ — REVERTED (terrain shader removal; test file deleted)
- ~~[ ] **RectangularTessellator heightmap upload test**~~ — REVERTED (terrain shader removal; heightmap upload removed)

## Phase 9: Example Fixes & UI Consistency (2026-04-03)

Bug fixes, camera corrections, and unified split-pane layout across all examples.

### 9.1 Core Rendering Fixes
- [x] **OrbitView per-frame terrain collision resolution** — `BasicOrbitView.doApply()` now calls `resolveCollisionsWithCenterPosition()` and `resolveCollisionsWithPitch()` every frame, plus a direct eye-altitude safety check. Fixes camera starting underground when `dc` is null during construction (all setter-time collision resolution was skipped). File: `BasicOrbitView.java`
- ~~[x] **TerrainShader double displacement fix**~~ — REVERTED (terrain shader removal)
- ~~[x] **TessellationTerrainShader TCS w-clamping fix**~~ — REVERTED (terrain shader removal)
- [x] **setFillPattern() missing updateModifiedTime()** — `AbstractSurfaceShape.setFillPattern()` now calls `updateModifiedTime()` so the surface tile builder re-renders FBO tiles when procedural fill pattern parameters change. Without this, slider controls in ProceduralFillPatternsExample had no visible effect. File: `AbstractSurfaceShape.java`

### 9.2 Example Camera & Content Fixes
- [x] **ExtrudedPolygonWithBaseDepth** — Relocated to flat coastal terrain (Outer Banks, NC) with proper OrbitView (center, heading, pitch, zoom) instead of setEyePosition in Idaho mountains where terrain collision caused the camera to start underground. File: `ExtrudedPolygonWithBaseDepth.java`
- [x] **PathsOnDateline** — Added OrbitView centered at (33°, 180°) with zoom 3e6 to properly show the dateline path on launch. File: `PathsOnDateline.java`
- [x] **MultiResPath** — Changed `new Path(positions)` to `new MultiResolutionPath(positions)` so the 108k-position paths are adaptively thinned based on view distance, making them actually visible. File: `MultiResPath.java`
- [x] **ContourBuilderExample** — Added `setEyePosition(25°N, -105°W, 2000km)` to frame the contour data sector (20-30°N, -110 to -100°W) on launch. File: `ContourBuilderExample.java`
- [x] **ExamplesIndex / ExampleDocs — RadarVolume cleanup** — Removed `RadarVolume` entry from example browser (it's a shape class, not a launchable example). Merged into single "Radar Volume" entry pointing to `RadarVolumeExample`. Files: `ExamplesIndex.java`, `ExampleDocs.java`
- [x] **SurfaceShapeShowcase — SurfaceBox misuse crashes on render** — Added `SurfaceBox.setLocationsFromCenterLine(globe, begin, end, leftWidth, rightWidth, lengthSegments, widthSegments)` (and a symmetric-width overload) that expands a two-endpoint centre line into the full perimeter vertex list `createGeometry` expects (`4·widthSegments + 2·lengthSegments + 4` points) and assigns the segment counts in one call. Uses the same great-circle perpendicular-offset construction as `airspaces.Box.makeSideLocations`, but globe-package-free (no asymmetric end-azimuth knobs — those stay in the full airspace `Box`). Showcase now calls `setLocationsFromCenterLine(new Earth(), begin, end, 120 km, 8, 2)`. Files: `SurfaceShapeShowcase.java`, `SurfaceBox.java`

### 9.3 Unified Split-Pane Layout
Applied consistent tabbed split-pane layout to **37 examples** (plus DashedLinesExample as template and WMSExplorer as special case). Pattern: `JSplitPane(HORIZONTAL_SPLIT, wwjPanel, controlPanel)` with `resizeWeight(0.67)`, `JTabbedPane` with "Layers" + context-appropriate control tabs, `JScrollPane` wrapping each tab, initial divider at `getWidth() * 2/3` via `componentResized` listener.

- [x] **DashedLinesExample** — Template: tabbed pane (Layers + Legend), split pane, disabled highlight controller. File: `DashedLinesExample.java`
- [x] **WMSExplorer** — Special case (`super(false,false,false)` custom layout): added split pane between globe panel and right panel with minimum width. File: `WMSExplorer.java`
- [x] **Batch 1** — AlarmIcons, Annotations, Cones, ContourLines, CoordinateSearchExample, Cylinders, DetailHints, DimGlobeSurface
- [x] **Batch 2** — Ellipsoids, ExportImageOrElevations, GeoJSONViewer, GPUTerrainDemo, KeepingObjectsInView, LayerOpacityAndBlendingDemo, MarkersOrder, MGRSGraticule
- [x] **Batch 3** — PlaceNames, ProceduralFillPatternsExample, RubberSheetImage, ScreenSelection, SectorSelection, ShapeClipping, ShapefileAttributeGroups, SurfaceImageViewer
- [x] **Batch 4** — SurfaceShapeShowcase, TerrainProfiler, ViewControls, ViewLookAround, ViewLimits, VPFLayerDemo, Wedges, WMSTimeSeriesDemo
- [x] **Batch 5** — TacticalGraphics, TacticalSymbols, ViewSwitch, AddAnimator, InstallImageryAndElevationsDemo (RetrieveElevations skipped — fully custom layout with no controlPanel)

### 9.4 ProceduralFillPatternsExample Layout Fix
- [x] **BoxLayout slider stretch fix** — Added `setMaximumSize()` constraints to all JSliders and JComboBox in the control panel to prevent vertical stretching. Ensured consistent `LEFT_ALIGNMENT` on all labels. File: `ProceduralFillPatternsExample.java`

## Phase 10: MCP Server Enhancements

### 10.1 Rendering Fixes
- [x] **SurfacePolyline for vector lines** — `AddVectorLayerTool.addPath()` switched from `Path` (absolute altitude, buried at sea level) to `SurfacePolyline` (drapes on terrain). Lines now visible over mountainous terrain. File: `AddVectorLayerTool.java`

### 10.2 New Tools
- [ ] **fly_path tool** — Timed animation along a waypoint list. Accepts array of lat/lon/alt/heading/pitch waypoints + total duration in seconds. Paces `fly_to` animations internally with smooth interpolation. Enables cinematic flythrough demos (e.g., convoy route) without per-waypoint round-trips from the MCP client.
- [x] **add_raster_layer float GeoTIFF support** — JOGL's AWTTextureIO throws GLException on float-backed BufferedImages (GL_RGB + GL_FLOAT). Added `convertFloatImageToARGB()` in `AddRasterLayerTool` to normalize float/double/TYPE_CUSTOM pixel data to 8-bit ARGB before texture creation. Handles both ImageIO and native GeotiffReader paths. File: `AddRasterLayerTool.java`
