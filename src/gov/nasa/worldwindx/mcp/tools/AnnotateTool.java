/*
 * WorldWind Reforged MCP Server
 * seaglassfoundry.com
 *
 * Places a text annotation or point marker on the globe.
 */
package gov.nasa.worldwindx.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.render.GlobeAnnotation;
import gov.nasa.worldwindx.mcp.GlobeState;
import gov.nasa.worldwindx.mcp.Json;
import gov.nasa.worldwindx.mcp.Tool;
import gov.nasa.worldwindx.mcp.ToolResult;

/**
 * Places a text annotation at a geographic position on the globe.
 * Returns a handle for later removal.
 */
public final class AnnotateTool implements Tool {

    @Override
    public String name() { return "annotate"; }

    @Override
    public String description() {
        return "Place a text annotation label at a geographic position on the globe.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = Json.objectSchema();
        Json.addProp(schema, "latitude", "number", "Latitude in degrees.");
        Json.addProp(schema, "longitude", "number", "Longitude in degrees.");
        Json.addProp(schema, "text", "string", "Annotation text to display.");
        Json.requireProps(schema, "latitude", "longitude", "text");
        return schema;
    }

    @Override
    public ToolResult call(JsonNode arguments, GlobeState state) throws Exception {
        double lat = arguments.path("latitude").asDouble();
        double lon = arguments.path("longitude").asDouble();
        String text = arguments.path("text").asText("");

        if (text.isBlank()) {
            return ToolResult.error("'text' is required.");
        }

        Position pos = Position.fromDegrees(lat, lon, 0);
        GlobeAnnotation annotation = new GlobeAnnotation(text, pos);

        state.runOnEdt(() ->
            state.annotationLayer().addRenderable(annotation));
        state.wwd().redraw();

        String handle = state.newHandle("annotation");
        state.registerHandle(handle, annotation, state.annotationLayer());

        return ToolResult.ok(String.format(
                "Annotation placed at **%.4f, %.4f**: \"%s\"", lat, lon, text))
                .handle("annotation", handle)
                .build();
    }
}
