/*
 * WorldWind Reforged MCP Server
 * seaglassfoundry.com
 *
 * Sets the vertical exaggeration factor for terrain rendering.
 */
package gov.nasa.worldwindx.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.nasa.worldwindx.mcp.GlobeState;
import gov.nasa.worldwindx.mcp.Json;
import gov.nasa.worldwindx.mcp.Tool;
import gov.nasa.worldwindx.mcp.ToolResult;

/**
 * Sets the vertical exaggeration multiplier on the globe's scene controller.
 * Useful for making terrain features more visible in flat regions.
 */
public final class SetTerrainExaggerationTool implements Tool {

    @Override
    public String name() { return "set_terrain_exaggeration"; }

    @Override
    public String description() {
        return "Set the vertical exaggeration factor for terrain rendering. "
             + "1.0 is true scale; higher values amplify elevation differences.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = Json.objectSchema();
        Json.addProp(schema, "factor", "number",
            "Vertical exaggeration multiplier (e.g. 1.0 = true scale, 3.0 = 3x exaggeration).");
        Json.requireProps(schema, "factor");
        return schema;
    }

    @Override
    public ToolResult call(JsonNode arguments, GlobeState state) throws Exception {
        double factor = arguments.path("factor").asDouble(1.0);
        if (factor < 0) {
            return ToolResult.error("Factor must be non-negative.");
        }

        state.runOnEdt(() ->
            state.wwd().getSceneController().setVerticalExaggeration(factor));
        state.wwd().redraw();

        return ToolResult.ok("Terrain exaggeration set to **" + factor + "x**.")
                .build();
    }
}
