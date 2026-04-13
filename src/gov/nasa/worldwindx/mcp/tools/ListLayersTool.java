/*
 * WorldWind Reforged MCP Server
 * seaglassfoundry.com
 *
 * Lists all active MCP-managed handles (overlays, annotations, layers).
 */
package gov.nasa.worldwindx.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.nasa.worldwindx.mcp.GlobeState;
import gov.nasa.worldwindx.mcp.Json;
import gov.nasa.worldwindx.mcp.Tool;
import gov.nasa.worldwindx.mcp.ToolResult;

import java.util.Map;

/**
 * Lists all handles currently managed by the MCP server (overlays, layers,
 * annotations). Useful for the AI to discover what can be removed or updated.
 */
public final class ListLayersTool implements Tool {

    @Override
    public String name() { return "list_layers"; }

    @Override
    public String description() {
        return "List all MCP-managed overlays, layers, and annotations with their handles.";
    }

    @Override
    public ObjectNode inputSchema() {
        return Json.objectSchema();
    }

    @Override
    public ToolResult call(JsonNode arguments, GlobeState state) throws Exception {
        Map<String, GlobeState.HandleEntry> handles = state.handles();

        if (handles.isEmpty()) {
            return ToolResult.ok("No active MCP layers or overlays.").build();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**Active handles** (").append(handles.size()).append("):\n\n");

        ArrayNode arr = Json.arr();
        for (var entry : handles.entrySet()) {
            String handle = entry.getKey();
            String type = entry.getValue().type();
            sb.append("- `").append(handle).append("` (").append(type).append(")\n");

            ObjectNode item = Json.obj();
            item.put("handle", handle);
            item.put("type", type);
            arr.add(item);
        }

        ObjectNode data = Json.obj();
        data.set("handles", arr);

        return ToolResult.ok(sb.toString()).data(data).build();
    }
}
