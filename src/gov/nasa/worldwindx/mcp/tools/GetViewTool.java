/*
 * WorldWind Reforged MCP Server
 * seaglassfoundry.com
 *
 * Queries the current globe camera position and orientation.
 */
package gov.nasa.worldwindx.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.view.BasicView;
import gov.nasa.worldwindx.mcp.GlobeState;
import gov.nasa.worldwindx.mcp.Json;
import gov.nasa.worldwindx.mcp.Tool;
import gov.nasa.worldwindx.mcp.ToolResult;

/**
 * Returns the current camera position, heading, pitch, and field of view.
 * No arguments required.
 */
public final class GetViewTool implements Tool {

    @Override
    public String name() { return "get_view"; }

    @Override
    public String description() {
        return "Query the current globe camera position, heading, pitch, and field of view.";
    }

    @Override
    public ObjectNode inputSchema() {
        return Json.objectSchema();
    }

    @Override
    public ToolResult call(JsonNode arguments, GlobeState state) throws Exception {
        BasicView view = (BasicView) state.wwd().getView();
        Position eye = view.getEyePosition();
        Angle heading = view.getHeading();
        Angle pitch = view.getPitch();
        Angle fov = view.getFieldOfView();

        ObjectNode data = Json.obj();
        data.put("latitude", eye.getLatitude().degrees);
        data.put("longitude", eye.getLongitude().degrees);
        data.put("altitude", eye.getAltitude());
        data.put("heading", heading.degrees);
        data.put("pitch", pitch.degrees);
        data.put("fov", fov.degrees);

        return ToolResult.ok(String.format(
                "Eye at **%.4f, %.4f**, altitude %.0f m, heading %.1f, pitch %.1f, FOV %.1f",
                eye.getLatitude().degrees, eye.getLongitude().degrees,
                eye.getAltitude(), heading.degrees, pitch.degrees, fov.degrees))
                .data(data)
                .build();
    }
}
