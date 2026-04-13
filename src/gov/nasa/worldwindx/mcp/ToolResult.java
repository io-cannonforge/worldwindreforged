/*
 * WorldWind Reforged MCP Server
 * seaglassfoundry.com
 *
 * LLM-friendly result envelope returned by every MCP tool.
 */
package gov.nasa.worldwindx.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result envelope returned by every {@link Tool}.
 *
 * <p>{@code summary} is markdown text for the model. {@code imageBase64} is
 * an optional PNG screenshot or thumbnail. {@code outputHandles} maps
 * friendly names to opaque layer/annotation handles for chained tool calls.
 * {@code data} carries any structured payload.</p>
 */
public final class ToolResult {

    private final boolean success;
    private final String summary;
    private final String imageBase64;
    private final Map<String, String> outputHandles;
    private final ObjectNode data;

    private ToolResult(boolean success, String summary, String imageBase64,
                       Map<String, String> outputHandles, ObjectNode data) {
        this.success = success;
        this.summary = summary == null ? "" : summary;
        this.imageBase64 = imageBase64;
        this.outputHandles = outputHandles == null ? Map.of() : Map.copyOf(outputHandles);
        this.data = data;
    }

    public boolean success() { return success; }
    public String summary() { return summary; }
    public String imageBase64() { return imageBase64; }
    public Map<String, String> outputHandles() { return outputHandles; }
    public ObjectNode data() { return data; }

    public static Builder ok(String summary) {
        return new Builder().success(true).summary(summary);
    }

    public static ToolResult error(String message) {
        return new Builder().success(false).summary("**Error:** " + message).build();
    }

    public static final class Builder {
        private boolean success = true;
        private String summary;
        private String imageBase64;
        private final Map<String, String> outputHandles = new LinkedHashMap<>();
        private ObjectNode data;

        public Builder success(boolean v) { this.success = v; return this; }
        public Builder summary(String s) { this.summary = s; return this; }
        public Builder image(String base64Png) { this.imageBase64 = base64Png; return this; }
        public Builder handle(String label, String handle) { this.outputHandles.put(label, handle); return this; }
        public Builder data(ObjectNode d) { this.data = d; return this; }

        public ObjectNode dataNode() {
            if (data == null) data = Json.obj();
            return data;
        }

        public ToolResult build() {
            return new ToolResult(success, summary, imageBase64, outputHandles, data);
        }
    }
}
