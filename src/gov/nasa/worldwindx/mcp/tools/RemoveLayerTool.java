/*
 * WorldWind Reforged MCP Server
 * seaglassfoundry.com
 *
 * Removes a previously added layer, annotation, or overlay by handle.
 */
package gov.nasa.worldwindx.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.nasa.worldwindx.mcp.GlobeState;
import gov.nasa.worldwindx.mcp.Json;
import gov.nasa.worldwindx.mcp.Tool;
import gov.nasa.worldwindx.mcp.ToolResult;

/**
 * Removes a previously added overlay, annotation, or layer by its handle.
 */
public final class RemoveLayerTool implements Tool {

    @Override
    public String name() { return "remove_layer"; }

    @Override
    public String description() {
        return "Remove a previously added layer, overlay, or annotation by its handle.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = Json.objectSchema();
        Json.addProp(schema, "handle", "string",
            "The handle returned when the layer/overlay/annotation was created.");
        Json.requireProps(schema, "handle");
        return schema;
    }

    @Override
    public ToolResult call(JsonNode arguments, GlobeState state) throws Exception {
        String handle = arguments.path("handle").asText(null);
        if (handle == null || handle.isBlank()) {
            return ToolResult.error("'handle' is required.");
        }

        boolean removed = state.removeHandle(handle);
        if (!removed) {
            return ToolResult.error("Handle not found: " + handle);
        }

        return ToolResult.ok("Removed `" + handle + "`.").build();
    }
}
