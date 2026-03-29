# WorldWind Reforged - Modernization Plan

## Project Overview

This document outlines the modernization roadmap for the NASA WorldWind Java SDK ("WorldWind Reforged"). The project is a fork of the original NASA WorldWind Java 2.2 SDK, targeting Java 17+ with modern build tooling (Maven), updated dependencies, and a long-term goal of migrating from the legacy OpenGL fixed-function pipeline to modern GPU-driven rendering.

**Current State (Completed):**
- Maven build system with proper dependency management
- Java 17 compilation with required `--add-opens` flags
- JOGL 2.6.0, GDAL 3.10.0, Jackson 2.18.3
- Jackson migrated from embedded 1.x to Maven-managed 2.18.3
- Dead NASA WMS server errors downgraded from SEVERE to WARNING
- 1652 source files compiling, 452 tests passing
- SurfaceShapes example running successfully
- Phase 1 Java modernization (see below) - COMPLETED

---

## Phase 1: Java Language Modernization

Modernize the codebase to idiomatic Java 17. These changes are safe, incremental, and improve readability and maintainability. Each category can be tackled independently.

### 1.1 Raw Types (Priority: High) -- COMPLETED
- **Scope:** ~66 files with raw generic types
- **Key areas:**
  - `HashMap<Object, ArrayList>` → `HashMap<Object, ArrayList<ActionAttributes>>` (ViewInputAttributes.java - done)
  - `List`, `Map`, `Set`, `Collection` used without type parameters throughout
  - `Iterator` without type parameters in loops
  - `Class` used as raw type (should be `Class<?>`)
- **Files with highest density:**
  - `util/WWIO.java`, `util/WWUtil.java`
  - `awt/ViewInputAttributes.java` (partially done)
  - `ogc/` package (OGC protocol handling)
  - `formats/shapefile/` package
  - `layers/` package

### 1.2 Diamond Operator (Priority: Medium) -- COMPLETED (~1,138 instances converted)
- **Scope:** ~627 instances across the codebase
- **Pattern:** `new ArrayList<SomeType>()` → `new ArrayList<>()`
- **Approach:** Automated find-and-replace, safe transformation
- **Densest files:** `AbstractShape.java`, `BasicDragger.java`, various layer classes

### 1.3 Try-with-Resources (Priority: Medium) -- COMPLETED (31 blocks converted)
- **Scope:** ~18 manual try/finally blocks with stream closing
- **Pattern:** Manual `finally { WWIO.closeStream(stream) }` → `try (var stream = ...) { }`
- **Key files:**
  - `util/WWIO.java` (partially done - `saveBuffer()` converted)
  - `util/BufferWrapper.java`
  - `retrieve/URLRetriever.java`
  - `formats/shapefile/ShapefileUtils.java`
  - Various I/O utility classes

### 1.4 Lambda Expressions (Priority: Medium) -- COMPLETED (43 conversions)
- **Scope:** ~148 anonymous inner classes convertible to lambdas
- **Common patterns:**
  - `Runnable` implementations → `() -> { ... }`
  - `ActionListener` → `e -> { ... }`
  - `Comparator` → `Comparator.comparing(...)` or lambda
  - `PropertyChangeListener`, `MouseListener`, etc.
- **Approach:** Start with single-method interfaces, convert outer→inner

### 1.5 Pattern Matching instanceof (Priority: Low) -- COMPLETED (54 conversions)
- **Scope:** ~498 instances of `instanceof` + cast
- **Pattern:** `if (x instanceof Foo) { Foo f = (Foo) x; ... }` → `if (x instanceof Foo f) { ... }`
- **Densest areas:**
  - `render/` package (~60 instances)
  - `layers/` package (~50 instances)
  - `ogc/` package (~40 instances)
- **Approach:** Automated but review each for readability

### 1.6 Switch Expressions (Priority: Low) -- COMPLETED (~30 conversions across 17 files)
- **Scope:** ~106 switch statements potentially convertible
- **Pattern:** Traditional switch → enhanced switch with arrow syntax
- **Approach:** Only convert where it improves readability; skip complex fall-through cases

### 1.7 Deprecated API Cleanup (Priority: Medium) -- COMPLETED (StringBuffer→StringBuilder: 31 instances in 20 files)
- **Scope:** ~78 usages of deprecated Java APIs
- **Key items:**
  - `new Integer()`, `new Double()`, `new Boolean()` → `Integer.valueOf()`, etc.
  - `Thread.stop()`, `Thread.suspend()` → proper thread interruption
  - `Date` / `Calendar` → `java.time` API where feasible
  - `StringBuffer` → `StringBuilder` (thread safety not needed)
  - `Finalize()` methods → `Cleaner` or explicit close

### 1.8 Additional Modernizations
- **Text blocks** for multi-line strings (XML templates, SQL, etc.)
- **`var` local variable type inference** where types are obvious from context
- **`List.of()`, `Map.of()`, `Set.of()`** for immutable collection literals
- **`String.formatted()`** instead of `String.format()`
- **`Objects.requireNonNull()`** for null checks in constructors
- **Records** for simple data carriers (e.g., coordinate tuples, config objects)

### Phase 1 Testing Strategy
- Add unit tests for each subsystem before modifying it
- Run full test suite after each batch of changes
- Verify SurfaceShapes example still runs after each milestone

---

## Phase 2: WMS Improvements

The original NASA WorldWind WMS endpoints (worldwind25.arc.nasa.gov, worldwind27.arc.nasa.gov, etc.) are permanently offline. This phase replaces them with working alternatives and improves WMS robustness.

### 2.1 Replace Dead Endpoints (Priority: High)
- **Current broken servers:**
  - `worldwind25.arc.nasa.gov` - BlueMarble, USGS imagery
  - `worldwind26.arc.nasa.gov` - Landsat, NAIP
  - `worldwind27.arc.nasa.gov` - Urban areas, USGS topo
  - `worldwind28.arc.nasa.gov` - Additional layers
  - `neowms.sci.gsfc.nasa.gov` - NASA Earth Observations
- **Replacement candidates:**
  - NASA GIBS (Global Imagery Browse Services): `gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi`
  - USGS National Map: `basemap.nationalmap.gov/arcgis/services/`
  - OpenStreetMap tile servers (for basic imagery fallback)
  - ESA Sentinel Hub WMS
- **Configuration files to update:**
  - `src/config/Earth/` - layer definition XML files
  - `src/gov/nasa/worldwind/util/BasicDataConfiguration.java`

### 2.2 WMS Protocol Robustness (Priority: Medium)
- **Current WMS support:** 1.1.1 and 1.3.0
- **Improvements:**
  - Add connection timeout and retry logic
  - Graceful degradation when servers are unavailable
  - Cache GetCapabilities responses
  - Add support for WMTS (Web Map Tile Service) as alternative
  - Better error reporting for WMS failures (currently just logs warnings)

### 2.3 Layer Configuration System (Priority: Medium)
- Make WMS endpoints configurable without code changes
- Support user-defined layer sources
- Add layer health monitoring (detect dead endpoints at startup)
- Support TMS (Tile Map Service) as an alternative protocol

### Phase 2 Testing Strategy
- Integration tests for WMS GetCapabilities parsing
- Mock WMS server for unit testing
- Manual verification with known-good WMS endpoints

---

## Phase 3: GLSL Shaders for Surface Shapes

Migrate surface shape rendering from the GL2 fixed-function pipeline to modern GLSL shaders. This is a significant architectural change.

**Current Status (Completed):**
- ShaderProgram infrastructure (compile, link, uniform caching, dispose)
- DashLineShader for outline dashed line rendering (replaces deprecated glLineStipple)
- SurfaceShapeFillShader for interior fill rendering (replaces GLU tessellator immediate mode)
- Interior VBO cache with ear-clipping triangulation via GpuTriangulator
- Texture fill support via shader uniform matrix (replaces deprecated GL_TEXTURE_GEN)
- GL2 fallback paths for all shader features

### 3.1 Original Rendering Architecture
- **Pipeline:** GL2 fixed-function (glVertexPointer, glColorPointer, glDrawArrays)
- **Entry point:** `AbstractSurfaceShape.drawGeometry()` → `drawInterior()` / `drawOutline()`
- **Key classes:**
  - `AbstractSurfaceShape` - base class for all surface shapes
  - `SurfacePolygon`, `SurfacePolyline`, `SurfaceCircle`, `SurfaceEllipse`, `SurfaceQuad`, `SurfaceSector`
  - `DrawContext` - OpenGL state management
  - `BasicShapeAttributes` - visual properties (color, opacity, line width)
- **Original geometry flow:**
  1. Shape computes lat/lon boundary
  2. GLU tessellator triangulates + draws via immediate-mode callbacks (fused)
  3. Textures applied via `GL_TEXTURE_GEN_S/T` with `GL_OBJECT_LINEAR`

### 3.2 Completed Shader Work

#### Step 1: Shader Infrastructure — COMPLETED
- `ShaderProgram` class: compile, link, uniform management, dispose
- Stored in `src/gov/nasa/worldwind/render/shaders/`

#### Step 2: Outline Shader — COMPLETED
- `DashLineShader`: GLSL 130 vertex+fragment shader for dashed lines
- Cumulative distance attribute with `mod()` dash/gap pattern
- Segment subdivision + batch rebasing for float32 precision at extreme zoom
- GL2 `glLineStipple()` fallback

#### Step 3: Interior Fill Shader — COMPLETED
- `SurfaceShapeFillShader`: GLSL 130 vertex+fragment shader for solid color and textured fills
- Interior VBO cache (`InteriorVBOData`) with ear-clipping triangulation via `GpuTriangulator`
- Texture coordinate generation via uniform `mat4 u_texMatrix` replicating `GL_OBJECT_LINEAR`
- Picking support (reads back current GL color set by tile builder)
- GLU tessellator fallback for systems without GLSL 1.30

### 3.3 Remaining Work
- Per-shape migration (`SurfacePolygon` has its own rendering path)
- Anti-aliased line rendering via geometry shader or SDF
- Procedural fill patterns in fragment shader (hatch, dots, crosshatch)
- Per-vertex coloring for data visualization

### 3.4 Compatibility Strategy
- Maintain GL2 fallback path for systems without GL3+ support
- Feature detection at shader init time; permanent fallback on failure
- Both paths coexist — shader path attempted first, GLU/glLineStipple fallback automatic

### Phase 3 Testing Strategy
- Visual regression tests (screenshot comparison)
- Shader compilation tests on CI
- Performance benchmarks (FPS with N shapes)
- Test on multiple GPU vendors (NVIDIA, AMD, Intel)

---

## Phase 4: Compute Shaders for Tessellation

Move terrain tessellation from CPU to GPU using compute shaders. This is the most complex phase and depends on Phase 3 shader infrastructure.

### 4.1 Current Tessellation Architecture
- **Tessellator:** `RectangularTessellator` (CPU-based)
- **Process:**
  1. Globe divided into rectangular tiles at multiple LOD levels
  2. Each tile tessellated into triangle mesh on CPU
  3. Elevation data sampled from `ElevationModel` (CPU)
  4. Vertex buffers uploaded to GPU each frame via `glVertexPointer()`
  5. Terrain rendered with fixed-function lighting
- **Key classes:**
  - `RectangularTessellator` - main tessellation engine
  - `RectTile` - individual terrain tile
  - `SectorGeometry` - tessellated geometry for a sector
  - `ElevationModel` / `BasicElevationModel` - elevation data source
  - `Globe` - coordinate transforms (geodetic ↔ Cartesian)

### 4.2 GPU Tessellation Strategy

#### Option A: OpenGL Tessellation Shaders (GL4.0+)
- Use tessellation control/evaluation shaders
- GPU subdivides patches based on screen-space error
- Elevation data in texture (heightmap)
- **Pros:** Standard OpenGL, well-supported
- **Cons:** Requires GL4.0, limited control over mesh topology

#### Option B: Compute Shader Tessellation (GL4.3+)
- Compute shader generates vertex/index buffers
- Full control over LOD algorithm
- Can implement adaptive tessellation based on:
  - Camera distance
  - Screen-space error metric
  - Elevation variance
- Output to SSBO (Shader Storage Buffer Object) → draw via `glDrawElements()`
- **Pros:** Maximum flexibility, can match current LOD algorithm
- **Cons:** Requires GL4.3, more complex implementation

#### Recommended: Hybrid Approach
1. **Phase 4a:** Tessellation shaders for basic terrain rendering
   - Heightmap textures from elevation data
   - LOD via tessellation levels
   - Replace `glVertexPointer` path with shader-based rendering
2. **Phase 4b:** Compute shaders for advanced terrain features
   - Adaptive mesh refinement
   - View-dependent LOD computation
   - Terrain morphing between LOD levels
   - Crack-free LOD transitions

### 4.3 Elevation Data Pipeline
- Upload elevation tiles as GL textures (R32F format)
- Use texture atlas or array textures for multiple tiles
- Stream elevation data asynchronously (current system already does this on CPU)
- Normal map generation in compute shader for lighting

### 4.4 Implementation Steps

#### Step 1: Heightmap Terrain Renderer — COMPLETED (Task 4.1)
- `TerrainShader` (GLSL 1.30 vert+frag) replaces fixed-function `glTexEnvi(GL_MODULATE)` for terrain tile rendering
- Per-tile `GL_R32F` heightmap texture (unit 3) uploaded lazily via `RenderInfo.fillHeightmapTexture()`
- `u_useHeightmap=0` in Task 4.1; GPU vertex displacement enabled in Task 4.2
- Fallback to fixed-function during picking and on GL < 3.0
- `GLRuntimeCapabilities.isUseTerrainShader()` capability flag added

#### Step 2: GPU LOD System
- Tessellation control shader computes LOD per-patch
- Screen-space error metric in shader
- Smooth LOD transitions (morphing)

#### Step 3: Compute Shader Mesh Generation
- Compute shader generates optimized index buffers
- Crack-free stitching between LOD levels
- Frustum culling in compute shader

#### Step 4: Advanced Features
- Terrain-following for surface shapes (Phase 3 + Phase 4)
- Atmospheric scattering shader
- Ocean rendering with displacement
- Shadow mapping for terrain

### Phase 4 Testing Strategy
- Performance benchmarks (frame time, triangle count, memory usage)
- Visual quality comparison with CPU tessellation
- LOD transition smoothness tests
- Multi-GPU vendor testing
- Stress tests with extreme elevation data

---

## Implementation Priority & Dependencies

```
Phase 1: Java Modernization (no dependencies)
    │
    ├── 1.1 Raw Types ──────────┐
    ├── 1.2 Diamond Operator ───┤
    ├── 1.3 Try-with-Resources ─┤── Can be done in parallel
    ├── 1.4 Lambdas ────────────┤
    ├── 1.5 Pattern Matching ───┤
    ├── 1.6 Switch Expressions ─┤
    ├── 1.7 Deprecated APIs ────┘
    │
Phase 2: WMS Improvements (independent of Phase 1)
    │
    ├── 2.1 Replace Endpoints ──── High priority
    ├── 2.2 Protocol Robustness
    └── 2.3 Layer Configuration
    │
Phase 3: GLSL Shaders (depends on Phase 1 completion)
    │
    ├── 3.1 Shader Infrastructure
    ├── 3.2 Basic Surface Shader
    ├── 3.3 Per-Shape Migration
    └── 3.4 Advanced Features
    │
Phase 4: Compute Tessellation (depends on Phase 3)
    │
    ├── 4a Tessellation Shaders
    └── 4b Compute Shaders
```

## Risk Factors

| Risk | Impact | Mitigation |
|------|--------|------------|
| GL2 removal in future JOGL | Breaks all rendering | Phase 3 provides modern path |
| JOGL project maintenance | No upstream fixes | Consider LWJGL as alternative |
| WMS endpoint instability | No imagery at startup | Local cache + multiple fallbacks |
| GPU compatibility (GL4.3) | Phase 4 won't run on old hardware | Keep CPU tessellation as fallback |
| Tessellation visual parity | GPU output differs from CPU | Side-by-side comparison testing |

## Metrics to Track

- **Compilation warnings:** Target zero (currently many raw type warnings)
- **Test coverage:** Add tests for each subsystem before modifying
- **Frame rate:** Benchmark before/after shader migration
- **Memory usage:** Monitor GPU memory with shader-based rendering
- **Startup time:** Track impact of WMS endpoint changes
