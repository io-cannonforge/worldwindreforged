/*
 * WorldWind Reforged MCP Server
 * seaglassfoundry.com
 *
 * Animates the globe view to a specified position and orientation.
 */
package gov.nasa.worldwindx.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.view.orbit.BasicOrbitView;
import gov.nasa.worldwindx.mcp.GlobeState;
import gov.nasa.worldwindx.mcp.Json;
import gov.nasa.worldwindx.mcp.Tool;
import gov.nasa.worldwindx.mcp.ToolResult;

/**
 * Animates the globe camera to a latitude/longitude/altitude with optional
 * heading and pitch. Uses WorldWind's built-in smooth fly-to animation.
 */
public final class FlyToTool implements Tool {

    @Override
    public String name() { return "fly_to"; }

    @Override
    public String description() {
        return "Animate the globe view to a geographic position with optional heading and pitch.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = Json.objectSchema();
        Json.addProp(schema, "latitude", "number", "Target latitude in degrees.");
        Json.addProp(schema, "longitude", "number", "Target longitude in degrees.");
        Json.addProp(schema, "altitude", "number",
            "Eye altitude in meters above the target position (default 50000).");
        Json.addProp(schema, "heading", "number",
            "Camera heading in degrees clockwise from north (default unchanged).");
        Json.addProp(schema, "pitch", "number",
            "Camera pitch in degrees from vertical, 0 = straight down, 90 = horizon (default unchanged).");
        Json.requireProps(schema, "latitude", "longitude");
        return schema;
    }

    @Override
    public ToolResult call(JsonNode arguments, GlobeState state) throws Exception {
        double lat = arguments.path("latitude").asDouble();
        double lon = arguments.path("longitude").asDouble();
        double alt = arguments.path("altitude").asDouble(50_000);

        Position target = Position.fromDegrees(lat, lon, 0);

        JsonNode headingNode = arguments.path("heading");
        JsonNode pitchNode = arguments.path("pitch");
        boolean hasOrientation = (!headingNode.isMissingNode() && !headingNode.isNull())
                || (!pitchNode.isMissingNode() && !pitchNode.isNull());

        state.runOnEdt(() -> {
            BasicOrbitView view = (BasicOrbitView) state.wwd().getView();

            if (hasOrientation) {
                // Use addPanToAnimator so heading/pitch animate together
                // with position instead of being overwritten by goTo.
                // seaglassfoundry.com
                Angle h = (!headingNode.isMissingNode() && !headingNode.isNull())
                        ? Angle.fromDegrees(headingNode.asDouble())
                        : view.getHeading();
                Angle p = (!pitchNode.isMissingNode() && !pitchNode.isNull())
                        ? Angle.fromDegrees(pitchNode.asDouble())
                        : view.getPitch();
                view.addPanToAnimator(target, h, p, alt);
            } else {
                view.goTo(target, alt);
            }
        });

        return ToolResult.ok(String.format(
                "Flying to **%.4f, %.4f** at %.0f m altitude.", lat, lon, alt))
                .build();
    }
}
