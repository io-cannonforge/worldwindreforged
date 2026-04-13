/*
 * WorldWind Reforged MCP Server
 * seaglassfoundry.com
 *
 * MCP (Model Context Protocol) server speaking Streamable HTTP transport.
 * Opens a WorldWind 3D globe window and exposes tools for AI agents to
 * control the view, load elevation data, overlay analysis results, place
 * annotations, and capture screenshots.
 *
 * Launch from Eclipse or command line; Claude Code connects via the URL
 * configured in .mcp.json.
 */
package gov.nasa.worldwindx.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * MCP server for WorldWind Reforged using Streamable HTTP transport.
 * Launches a 3D globe window and accepts JSON-RPC POST requests on
 * {@code /mcp} to control the display.
 *
 * <p>seaglassfoundry.com</p>
 */
public final class WorldWindMcpServer {

    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "worldwind-mcp";
    private static final String SERVER_VERSION = "0.2.0";
    private static final int DEFAULT_PORT = 8384;

    private final ToolRegistry tools;
    private final GlobeState state;
    private final int port;

    /** Active SSE session IDs (for future bidirectional messaging). */
    private final Map<String, Boolean> sessions = new ConcurrentHashMap<>();

    public WorldWindMcpServer(ToolRegistry tools, GlobeState state, int port) {
        this.tools = tools;
        this.state = state;
        this.port = port;
    }

    /** Entry point. Optional arg: port number (default 8384). */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            }
        }

        log("Starting " + SERVER_NAME + " " + SERVER_VERSION + " on port " + port);

        try {
            GlobeState globe = new GlobeState();
            // Eagerly initialize the globe window so it's visible immediately.
            globe.ensureInitialized();

            WorldWindMcpServer server = new WorldWindMcpServer(
                    ToolRegistry.defaults(), globe, port);
            server.start();

            log("MCP server listening on http://localhost:" + port + "/mcp");
            log("Press Ctrl+C to stop.");

            // Keep the main thread alive.
            Thread.currentThread().join();
        } catch (Exception e) {
            log("Fatal error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /** Start the HTTP server. */
    public void start() throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
        http.createContext("/mcp", this::handleMcp);
        http.setExecutor(Executors.newFixedThreadPool(4));
        http.start();
    }

    // ------------------------------------------------------------------
    // HTTP handler
    // ------------------------------------------------------------------

    private void handleMcp(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        // CORS preflight.
        if ("OPTIONS".equalsIgnoreCase(method)) {
            setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        setCorsHeaders(exchange);

        if ("GET".equalsIgnoreCase(method)) {
            // GET opens an SSE stream (server-to-client notifications).
            log("GET /mcp (SSE stream request)");
            handleSseOpen(exchange);
            return;
        }

        if ("DELETE".equalsIgnoreCase(method)) {
            log("DELETE /mcp");
            // DELETE closes an SSE session.
            String sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
            if (sessionId != null) sessions.remove(sessionId);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }

        if (!"POST".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        // POST: JSON-RPC request.
        byte[] body = exchange.getRequestBody().readAllBytes();
        String bodyStr = new String(body, StandardCharsets.UTF_8);
        log("POST " + bodyStr.substring(0, Math.min(bodyStr.length(), 200)));

        JsonNode request;
        try {
            request = Json.MAPPER.readTree(bodyStr);
        } catch (Exception e) {
            sendResponse(exchange, 400,
                    makeError(null, -32700, "Parse error: " + e.getMessage()));
            return;
        }

        JsonNode idNode = request.get("id");
        String rpcMethod = request.path("method").asText(null);
        JsonNode params = request.path("params");
        boolean isNotification = idNode == null || idNode.isNull();

        if (rpcMethod == null) {
            if (!isNotification) {
                sendResponse(exchange, 400,
                        makeError(idNode, -32600, "Invalid request: missing 'method'"));
            } else {
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
            }
            return;
        }

        try {
            ObjectNode response;
            switch (rpcMethod) {
                case "initialize" -> {
                    response = makeResult(idNode, handleInitialize());
                    // Return a session ID on initialize.
                    String sessionId = UUID.randomUUID().toString();
                    sessions.put(sessionId, true);
                    exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
                    sendResponse(exchange, 200, response);
                }
                case "notifications/initialized" -> {
                    exchange.sendResponseHeaders(202, -1);
                    exchange.close();
                }
                case "ping" -> {
                    sendResponse(exchange, 200, makeResult(idNode, Json.obj()));
                }
                case "tools/list" -> {
                    sendResponse(exchange, 200,
                            makeResult(idNode, handleToolsList()));
                }
                case "tools/call" -> {
                    sendResponse(exchange, 200,
                            makeResult(idNode, handleToolsCall(params)));
                }
                default -> {
                    if (isNotification) {
                        exchange.sendResponseHeaders(202, -1);
                        exchange.close();
                    } else {
                        sendResponse(exchange, 404,
                                makeError(idNode, -32601, "Method not found: " + rpcMethod));
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            sendResponse(exchange, 400,
                    makeError(idNode, -32602, e.getMessage()));
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            String msg = cause.getMessage();
            if (msg == null || msg.isEmpty()) msg = cause.getClass().getName();
            log("Error handling " + rpcMethod + ": " + msg);
            cause.printStackTrace(System.err);
            sendResponse(exchange, 500,
                    makeError(idNode, -32603, "Internal error: " + msg));
        }
    }

    /** Open an SSE stream for server-initiated messages. */
    private void handleSseOpen(HttpExchange exchange) throws IOException {
        // Accept GET regardless of session ID — Claude Code may open the SSE
        // stream before sending initialize.
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        // Keep the stream open until the client disconnects.
        // We flush an empty comment to confirm the connection is alive.
        try {
            OutputStream os = exchange.getResponseBody();
            os.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException ignored) { }
    }

    // ------------------------------------------------------------------
    // MCP method handlers (unchanged logic)
    // ------------------------------------------------------------------

    private ObjectNode handleInitialize() {
        ObjectNode result = Json.obj();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode caps = Json.obj();
        ObjectNode toolsCap = Json.obj();
        toolsCap.put("listChanged", false);
        caps.set("tools", toolsCap);
        result.set("capabilities", caps);
        ObjectNode info = Json.obj();
        info.put("name", SERVER_NAME);
        info.put("version", SERVER_VERSION);
        result.set("serverInfo", info);
        return result;
    }

    private ObjectNode handleToolsList() {
        ObjectNode result = Json.obj();
        ArrayNode arr = Json.arr();
        for (Tool tool : tools.all()) {
            ObjectNode t = Json.obj();
            t.put("name", tool.name());
            t.put("description", tool.description());
            t.set("inputSchema", tool.inputSchema());
            arr.add(t);
        }
        result.set("tools", arr);
        return result;
    }

    private ObjectNode handleToolsCall(JsonNode params) throws Exception {
        String name = params.path("name").asText(null);
        if (name == null) {
            throw new IllegalArgumentException("tools/call requires 'name'");
        }
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }

        state.ensureInitialized();
        JsonNode arguments = params.path("arguments");
        if (arguments.isMissingNode() || arguments.isNull()) {
            arguments = Json.obj();
        }

        ObjectNode result = Json.obj();
        ArrayNode content = Json.arr();
        try {
            ToolResult toolResult = tool.call(arguments, state);
            if (toolResult == null) {
                throw new IllegalStateException("Tool " + name + " returned null ToolResult");
            }

            ObjectNode textBlock = Json.obj();
            textBlock.put("type", "text");
            textBlock.put("text", toolResult.summary());
            content.add(textBlock);

            if (toolResult.imageBase64() != null) {
                ObjectNode imgBlock = Json.obj();
                imgBlock.put("type", "image");
                imgBlock.put("data", toolResult.imageBase64());
                imgBlock.put("mimeType", "image/png");
                content.add(imgBlock);
            }

            if (!toolResult.outputHandles().isEmpty() || toolResult.data() != null) {
                ObjectNode payload = Json.obj();
                if (!toolResult.outputHandles().isEmpty()) {
                    ObjectNode handles = Json.obj();
                    toolResult.outputHandles().forEach(handles::put);
                    payload.set("handles", handles);
                }
                if (toolResult.data() != null) {
                    payload.set("data", toolResult.data());
                }
                ObjectNode dataBlock = Json.obj();
                dataBlock.put("type", "text");
                dataBlock.put("text", Json.MAPPER.writeValueAsString(payload));
                content.add(dataBlock);
            }

            result.set("content", content);
            result.put("isError", !toolResult.success());
        } catch (Exception e) {
            log("Tool " + name + " failed: " + e.getMessage());
            ObjectNode textBlock = Json.obj();
            textBlock.put("type", "text");
            textBlock.put("text", e.getClass().getSimpleName() + ": " + e.getMessage());
            content.add(textBlock);
            result.set("content", content);
            result.put("isError", true);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // JSON-RPC response builders
    // ------------------------------------------------------------------

    private static ObjectNode makeResult(JsonNode id, ObjectNode result) {
        ObjectNode response = Json.obj();
        response.put("jsonrpc", "2.0");
        if (id != null && !id.isNull()) response.set("id", id);
        response.set("result", result);
        return response;
    }

    private static ObjectNode makeError(JsonNode id, int code, String message) {
        ObjectNode response = Json.obj();
        response.put("jsonrpc", "2.0");
        if (id != null && !id.isNull()) {
            response.set("id", id);
        } else {
            response.putNull("id");
        }
        ObjectNode err = Json.obj();
        err.put("code", code);
        err.put("message", message);
        response.set("error", err);
        return response;
    }

    // ------------------------------------------------------------------
    // HTTP helpers
    // ------------------------------------------------------------------

    private void sendResponse(HttpExchange exchange, int status, ObjectNode body)
            throws IOException {
        String accept = exchange.getRequestHeaders().getFirst("Accept");
        boolean wantsSse = accept != null && accept.contains("text/event-stream");

        if (wantsSse) {
            // MCP Streamable HTTP: wrap JSON-RPC in an SSE event.
            String json = Json.MAPPER.writeValueAsString(body);
            String sse = "event: message\ndata: " + json + "\n\n";
            byte[] bytes = sse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
                os.flush();
            }
        } else {
            byte[] bytes = Json.MAPPER.writeValueAsBytes(body);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static void setCorsHeaders(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers",
                "Content-Type, Mcp-Session-Id, Accept");
        headers.set("Access-Control-Expose-Headers", "Mcp-Session-Id");
    }

    /** Log to stderr. */
    static void log(String message) {
        System.err.println("[worldwind-mcp] " + message);
    }
}
