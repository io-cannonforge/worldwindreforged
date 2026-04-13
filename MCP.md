# WorldWind Reforged MCP Server

seaglassfoundry.com

The MCP (Model Context Protocol) server lets AI agents like Claude Code
control the WorldWind 3D globe: fly to locations, toggle layers, adjust
terrain, place annotations, and capture screenshots.

## Architecture

```
Claude Code  <--stdio-->  McpStdioBridge  <--HTTP POST-->  WorldWindMcpServer
 (terminal)               (lightweight)                    (runs inside Eclipse
                                                            with full GPU access)
```

Two processes work together:

1. **WorldWindMcpServer** — the real server. Opens a WorldWind globe
   window and listens for JSON-RPC on `http://localhost:8384/mcp`.
   Runs inside Eclipse (or standalone) so it has access to the GPU.

2. **McpStdioBridge** — a tiny forwarder. Claude Code spawns it via
   stdio transport; it POSTs each JSON-RPC message to the HTTP server
   and relays responses back. This is needed because Claude Code
   expects stdio, but the globe must run in a separate JVM with a
   display.

## Prerequisites

- Java 11+
- Maven (for dependency resolution)
- Project compiled: `mvn compile`

## Setup

### 1. Compile the project

```
mvn compile
```

This produces `target/classes`. On first run the launcher scripts will
also generate `target/cp.txt` (the runtime classpath) automatically.

### 2. Start the MCP HTTP server

Run `WorldWindMcpServer` from Eclipse (Run As > Java Application) or
from a terminal:

```
mcp-server.bat
```

A WorldWind globe window opens and the server listens on port 8384.
You can specify a custom port:

```
mcp-server.bat --port 9090
```

Leave this running for the duration of your session.

### 3. Configure Claude Code

Create a `.mcp.json` file in the project root to tell Claude Code how
to connect:

```json
{
  "mcpServers": {
    "worldwind": {
      "command": "cmd",
      "args": ["/c", "<PROJECT_DIR>\\mcp-bridge.bat"],
      "timeout": 30
    }
  }
}
```

Replace `<PROJECT_DIR>` with the absolute path to your project
directory. This file is gitignored because it contains
machine-specific paths.

### 4. Connect from Claude Code

Start Claude Code in the project directory. Use `/mcp` to verify the
`worldwind` server is listed and connected. Once connected, the globe
tools are available.

## Available Tools

| Tool | Description |
|------|-------------|
| `fly_to` | Animate the view to a lat/lon with optional altitude, heading, and pitch |
| `get_view` | Return the current eye position and orientation |
| `list_layers` | List all layers and their enabled state |
| `add_raster_layer` | Overlay a GeoTIFF on the globe as a surface layer. Handles float-backed rasters from ForgeGIS by normalizing to 8-bit ARGB |
| `add_vector_layer` | Add a shapefile or GeoJSON layer (lines drape on terrain via SurfacePolyline) |
| `remove_layer` | Remove a layer by handle |
| `set_elevation` | Configure elevation data source |
| `set_terrain_exaggeration` | Scale vertical terrain exaggeration |
| `annotate` | Place a text annotation on the globe |
| `screenshot` | Capture a PNG screenshot of the current globe view |

## ForgeGIS / ForgeData Integration

The WorldWind MCP server works alongside the ForgeGIS and ForgeData MCP
servers to form a three-server pipeline for geospatial analysis and
visualization:

```
ForgeData (catalog)  -->  ForgeGIS (analysis)  -->  WorldWind (display)
 find datasets             compute slope, etc.       overlay on 3D globe
```

Typical workflow:

1. **ForgeData** `search_by_category` to find elevation data
2. **ForgeGIS** `load_raster` + `analyze_dem` to compute slope/hillshade
3. **ForgeGIS** `write_raster` with `colormap=slope` to produce an
   8-bit RGB GeoTIFF
4. **WorldWind** `add_raster_layer` to overlay the result on the globe

### Float GeoTIFF Handling

ForgeGIS analysis tools produce float-precision GeoTIFFs (32-bit float
pixel data). JOGL's texture pipeline cannot consume these directly.
`AddRasterLayerTool` detects float-backed or `TYPE_CUSTOM`
`BufferedImage` instances and normalizes them to `TYPE_INT_ARGB` before
texture creation. For best results, use ForgeGIS `write_raster` with a
`colormap` parameter to produce display-ready RGB output.

## Troubleshooting

- **"Connection refused"** — The HTTP server is not running. Start it
  with `mcp-server.bat` or from Eclipse before connecting Claude Code.
- **"target\classes not found"** — Run `mvn compile` first.
- **Globe window does not appear** — Make sure you are not running the
  server in a headless environment. The globe requires a display and
  GPU access.
- **Tools not showing in Claude Code** — Run `/mcp` in Claude Code to
  check connection status. Ensure `.mcp.json` path matches your project
  location.

## Source Files

```
src/gov/nasa/worldwindx/mcp/
  WorldWindMcpServer.java   — HTTP server + globe window
  McpStdioBridge.java       — stdio-to-HTTP forwarder
  ToolRegistry.java         — tool discovery and dispatch
  Tool.java                 — tool interface
  ToolResult.java           — tool response wrapper
  GlobeState.java           — shared globe state
  Json.java                 — Jackson helpers
  tools/                    — one class per tool
    FlyToTool.java
    GetViewTool.java
    ListLayersTool.java
    AddRasterLayerTool.java
    AddVectorLayerTool.java
    RemoveLayerTool.java
    SetElevationTool.java
    SetTerrainExaggerationTool.java
    AnnotateTool.java
    ScreenshotTool.java
```

## Launcher Scripts

- **`mcp-server.bat`** — starts the HTTP server (the globe)
- **`mcp-bridge.bat`** — started automatically by Claude Code via `.mcp.json`
