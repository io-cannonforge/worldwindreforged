/*
 * WorldWind Reforged MCP Server
 * seaglassfoundry.com
 *
 * MCP tool interface for AI-driven globe control.
 */
package gov.nasa.worldwindx.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * An MCP-exposed tool for controlling the WorldWind 3D globe.
 * Each tool advertises a JSON Schema for its arguments and returns
 * a {@link ToolResult} with markdown summary, optional screenshot,
 * and structured data.
 */
public interface Tool {

    String name();

    String description();

    /** JSON Schema draft-07 describing the arguments object. */
    ObjectNode inputSchema();

    /**
     * Execute the tool.
     *
     * @param arguments parsed arguments node from the MCP request
     *                  (never null; empty object if client sent none)
     * @param state     shared globe state (WorldWindow, layers, handles)
     */
    ToolResult call(JsonNode arguments, GlobeState state) throws Exception;
}
