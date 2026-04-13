/*
 * WorldWind Reforged MCP Server
 * seaglassfoundry.com
 *
 * Loads a local DEM file as a custom elevation model on the globe.
 */
package gov.nasa.worldwindx.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.nasa.worldwind.globes.ElevationModel;
import gov.nasa.worldwind.terrain.CompoundElevationModel;
import gov.nasa.worldwind.terrain.LocalElevationModel;
import gov.nasa.worldwindx.mcp.GlobeState;
import gov.nasa.worldwindx.mcp.Json;
import gov.nasa.worldwindx.mcp.Tool;
import gov.nasa.worldwindx.mcp.ToolResult;

import java.io.File;

/**
 * Loads a local DEM (GeoTIFF, BIL, DTED) as a custom elevation model.
 * The model is inserted into WorldWind's CompoundElevationModel so terrain
 * rendering reflects the new heights.
 */
public final class SetElevationTool implements Tool {

    @Override
    public String name() { return "set_elevation"; }

    @Override
    public String description() {
        return "Load a local DEM file (GeoTIFF, BIL, DTED) as a custom elevation model "
             + "on the globe, overriding default terrain in the covered area.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = Json.objectSchema();
        Json.addProp(schema, "path", "string",
            "Absolute path to the DEM file (GeoTIFF, .bil, .dt0/.dt1/.dt2).");
        Json.requireProps(schema, "path");
        return schema;
    }

    @Override
    public ToolResult call(JsonNode arguments, GlobeState state) throws Exception {
        String path = arguments.path("path").asText(null);
        if (path == null || path.isBlank()) {
            return ToolResult.error("'path' is required.");
        }

        File file = new File(path);
        if (!file.isFile()) {
            return ToolResult.error("File not found: " + path);
        }

        LocalElevationModel lem = new LocalElevationModel();
        lem.addElevations(file);

        state.runOnEdt(() -> {
            ElevationModel current = state.wwd().getModel().getGlobe().getElevationModel();
            if (current instanceof CompoundElevationModel cem) {
                cem.addElevationModel(lem);
            } else {
                CompoundElevationModel cem = new CompoundElevationModel();
                cem.addElevationModel(current);
                cem.addElevationModel(lem);
                state.wwd().getModel().getGlobe().setElevationModel(cem);
            }
        });

        state.wwd().redraw();

        String handle = state.newHandle("elevation");
        state.registerLayerHandle(handle, null);

        return ToolResult.ok("Loaded elevation model from `" + file.getName() + "`.")
                .handle("elevation", handle)
                .build();
    }
}
