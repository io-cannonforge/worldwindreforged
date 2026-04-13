/*
 * WorldWind Reforged MCP Server
 * seaglassfoundry.com
 *
 * Ordered registry of MCP tools exposed to AI agents.
 */
package gov.nasa.worldwindx.mcp;

import gov.nasa.worldwindx.mcp.tools.AddRasterLayerTool;
import gov.nasa.worldwindx.mcp.tools.AddVectorLayerTool;
import gov.nasa.worldwindx.mcp.tools.AnnotateTool;
import gov.nasa.worldwindx.mcp.tools.FlyToTool;
import gov.nasa.worldwindx.mcp.tools.GetViewTool;
import gov.nasa.worldwindx.mcp.tools.ListLayersTool;
import gov.nasa.worldwindx.mcp.tools.RemoveLayerTool;
import gov.nasa.worldwindx.mcp.tools.ScreenshotTool;
import gov.nasa.worldwindx.mcp.tools.SetElevationTool;
import gov.nasa.worldwindx.mcp.tools.SetTerrainExaggerationTool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ordered registry of MCP tools. Tool list order matters for LLM context:
 * terrain/elevation first, then view control, then layers, then queries.
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public static ToolRegistry defaults() {
        ToolRegistry r = new ToolRegistry();
        // Terrain and elevation.
        r.register(new SetElevationTool());
        r.register(new SetTerrainExaggerationTool());
        // View control.
        r.register(new FlyToTool());
        r.register(new GetViewTool());
        // Layer management.
        r.register(new AddRasterLayerTool());
        r.register(new AddVectorLayerTool());
        r.register(new RemoveLayerTool());
        r.register(new ListLayersTool());
        // Annotation.
        r.register(new AnnotateTool());
        // Capture.
        r.register(new ScreenshotTool());
        return r;
    }

    public void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public Collection<Tool> all() {
        return tools.values();
    }
}
