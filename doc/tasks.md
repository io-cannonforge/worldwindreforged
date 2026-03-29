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
- [x] **Heightmap terrain renderer** — TerrainShader (GLSL 1.30 vert+frag); per-tile GL_R32F elevation texture on unit 3; lazy upload via fillHeightmapTexture(); u_useHeightmap=0 in 4.1 (GPU displacement enabled in next step); GL < 3.0 / picking fallback to fixed-function; GLRuntimeCapabilities.isUseTerrainShader() flag
- [x] **GPU LOD system (tessellation control shader)** — TessellationTerrainShader (GLSL 4.00 vert+TCS+TES+frag); each coarse grid cell becomes a GL_PATCHES quad patch; TCS computes screen-space edge lengths → gl_TessLevelOuter[0-3] / gl_TessLevelInner[0-1] clamped to [1,64]; TES bilinearly interpolates positions + applies gl_TextureMatrix[0/1]; fractional_even_spacing for smooth LOD morphing; patchIndexLists / patchIndexListsVboCacheKeys for per-density VBOs; GLRuntimeCapabilities.isUseTessellation() (GL 4.0+); falls back to TerrainShader → fixed-function
- [x] **Compute shader mesh generation** — ComputeMeshShader (GLSL 4.30 compute); GPU-side per-patch frustum culling via SSBOs; glDrawElementsIndirect(GL_PATCHES) with no CPU readback; vertex VBO reused as SSBO binding 0; atomic dc_count in draw-command SSBO; frustum planes adjusted to tile-local ECEF space; GLRuntimeCapabilities.isUseComputeMesh() (GL 4.3+); falls back to tessellation path
- [x] **Crack-free LOD stitching** — CPU constrainNeighbourLevels() pre-pass in tessellate(): projects tile corners to screen space, computes per-edge level estimates, constrains shared edge of same-density adjacent tiles to min(a,b); stored in RenderInfo.constrainedOuterLevels; TCS u_maxOuter[4] uniform caps gl_TessLevelOuter[k] so both sides of every shared edge agree; different-density boundaries rely on existing skirts
- [x] **Sub-grid heightmap displacement in TES** — TessellationTerrainShader TES delta-correction: h_actual from GL_R32F heightmap (unit 3, stores vertExagg × elevation) minus h_bilinear = length(worldPos) − u_earthRadius (sphere approx at tile centre); residual added along surface normal; u_earthRadius = length(refCenter) uploaded from Java; u_useHeightmap enabled when heightmap texture available; no double-displacement since vertex positions already include coarse elevation

## Phase 5: GPU Wiring — Connect Compute Paths to Render Thread

These tasks wire already-complete GPU infrastructure into the active render pipeline. Each is medium effort with high payoff.

- [x] **Wire GpuTessellator into AbstractSurfaceShape** — Already complete (found during scan); `generateIntermediateLocations()` tries GPU compute path first with CPU fallback
- [x] **Wire GpuTriangulator.triangulateBatch() into ShapefilePolygons** — Two-phase split: background thread collects merged ring data into `GpuTessellationData` (stored in `geom.gpuPendingData`); render thread calls `dispatchGpuTriangulation()` from `addTile()` to run GL 4.3 compute shader and populate `RecordIndices`; CPU ear-clipping fallback when GL 4.3 unavailable. Added `GpuTriangulator.isGpuViable()` predicate. Files: `ShapefilePolygons.java`, `GpuTriangulator.java`
- [x] **Per-tile VAO binding in RectangularTessellator** — `RenderInfo.vaoCacheKey` rotated on VBO re-upload; `bindVbos()` and `bindVbosTessellated()` lazily create a per-tile VAO capturing vertex + texcoord pointers; subsequent frames bind in one `glBindVertexArray()` call; `beginRendering()`/`endRendering()` skip `glPushClientAttrib`/`glPopClientAttrib` when `isUseVertexArrayObject()` (incompatible with VAO state), instead unbinding with `glBindVertexArray(0)` in `endRendering()`. Files: `RectangularTessellator.java`

## Phase 6: GLSL Deprecated Built-in Cleanup

Replaced deprecated GLSL built-ins where feasible. TerrainShader retains deprecated built-ins as fallback for AMD (no VAOs). TessellationTerrainShader uses explicit attribs (requires VAOs). GL profile upgraded to GL3bc for GLSL 3.30+ support. AMD vendor detection auto-disables VAOs in compatibility profile.

- [ ] **Replace gl_Vertex / gl_MultiTexCoord0 / gl_Color / gl_ModelViewProjectionMatrix / gl_TextureMatrix in TerrainShader** — DEFERRED: TerrainShader is the fallback path when VAOs are unavailable (AMD compatibility profile). Without VAOs, glVertexAttribPointer conflicts with fixed-function glVertexPointer/glTexCoordPointer client state. TerrainShader must use deprecated built-ins (`gl_Vertex`, `gl_MultiTexCoord0`, `gl_Color`, `gl_ModelViewProjectionMatrix`, `gl_TextureMatrix`) to read from fixed-function vertex data. Requires core profile migration (Option C) to fully modernize.
- [x] **Replace gl_Vertex / gl_Color / gl_ModelViewProjectionMatrix / gl_TextureMatrix in TessellationTerrainShader** — Vertex shader uses `layout(location=0/1)` explicit attribs + `u_primaryColor`; TCS uses `u_mvp` (declared in TCS); TES uses `u_mvp`, `u_texMatrix0/1`. `bindVbosTessellated()` always uses `glVertexAttribPointer(0/1)` with dedicated `tessShaderVaoCacheKey`. All new uniforms uploaded in `activate()` (GL_CURRENT_COLOR + GL texture matrix reads). Requires VAOs (gated by `vaoAvailable` check). Files: `TessellationTerrainShader.java`, `RectangularTessellator.java`
- [x] **Replace gl_ModelViewProjectionMatrix with explicit u_mvp in DashLineShader and SurfaceShapeFillShader** — Both shaders now use `uniform mat4 u_mvp` populated by `ShaderProgram.setUniformMvp()` (reads GL matrix stack, computes P×MV column-major). `gl_Color` was never used — shaders already had `uniform vec4 u_color`. Files: `DashLineShader.java`, `SurfaceShapeFillShader.java`, `ShaderProgram.java`
- [x] **GL profile upgrade to GL3bc** — `Configuration.getMaxCompatibleGLProfile()` now prefers `GLProfile.GL3bc` (GL 3.x/4.x compatibility) over `getMaxFixedFunc()`. Guarantees GLSL 3.30+, `layout(location=N)`, and better-tested driver paths. All fixed-function APIs still available. Falls back to `getMaxFixedFunc(true)` on old hardware. File: `Configuration.java`
- [x] **AMD vendor detection for VAO workaround** — `GLRuntimeCapabilities.initialize()` auto-disables VAOs when GL_VENDOR contains "ati" or "amd" and profile is not core-only. Logs info message. NVIDIA/Intel get full VAOs + tessellation + compute. Tessellation/compute gated on `vaoAvailable` in render path. Files: `GLRuntimeCapabilities.java`, `RectangularTessellator.java`
- [x] **ShaderProgram.bindAttribLocation()** — Pre-link `glBindAttribLocation()` API added for shaders needing explicit attrib locations without `layout(location=N)` in GLSL source. Used by TessellationTerrainShader path. File: `ShaderProgram.java`

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
- [x] **TerrainShader / TessellationTerrainShader compile tests** — 31 tests: TerrainShader (isValid, program null, vertex uniforms, fragment fragColor/discard/samplers); TessellationTerrainShader (DEFAULT_PIXELS_PER_TRIANGLE, UNCONSTRAINED_OUTER, TCS u_maxOuter/tessLevel output, TES u_earthRadius/u_useHeightmap/h_bilinear); tessellation arithmetic (clamp, u_maxOuter cap). File: `TerrainShaderTest.java`
- [ ] **RectangularTessellator heightmap upload test** — Verify `fillHeightmapTexture()` uploads a correctly sized GL_R32F texture and that `u_useHeightmap` is set to 1 when data is available. Requires headless JOGL context. File: new `HeightmapUploadTest.java`
