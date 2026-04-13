/*
 * WorldWind MCP stdio-to-HTTP bridge.
 * seaglassfoundry.com
 *
 * Claude Code spawns this lightweight process via stdio transport.
 * It forwards JSON-RPC messages to the WorldWind MCP HTTP server
 * running in Eclipse (with full GPU access), and relays responses back.
 */
package gov.nasa.worldwindx.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Stdio-to-HTTP bridge for the WorldWind MCP server.
 * Reads newline-delimited JSON-RPC from stdin, POSTs each message
 * to the WorldWind HTTP server, and writes responses to stdout.
 *
 * <p>Usage: java McpStdioBridge [--port PORT]</p>
 */
public final class McpStdioBridge {

    private static final int DEFAULT_PORT = 8384;
    private static final String DEFAULT_HOST = "127.0.0.1";

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            }
        }

        String baseUrl = "http://" + DEFAULT_HOST + ":" + port + "/mcp";

        // Redirect System.out to stderr so only JSON-RPC goes to stdout.
        PrintStream mcpOut = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        System.setOut(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        log("Bridge starting, forwarding to " + baseUrl);

        String sessionId = null;

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;

                try {
                    String[] result = forward(baseUrl, line, sessionId);
                    String response = result[0];
                    if (result[1] != null) sessionId = result[1];

                    if (response != null && !response.isBlank()) {
                        mcpOut.println(response);
                        mcpOut.flush();
                    }
                } catch (IOException e) {
                    log("Forward failed: " + e.getMessage());
                    // Return a JSON-RPC error so the client knows.
                    String errorResp = "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":"
                            + "{\"code\":-32000,\"message\":\"Bridge: cannot reach WorldWind server at "
                            + baseUrl + " — is it running in Eclipse? (" + e.getMessage() + ")\"}}";
                    mcpOut.println(errorResp);
                    mcpOut.flush();
                }
            }
        } catch (Exception e) {
            log("Fatal: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * POST a JSON-RPC message to the HTTP server.
     * Returns [responseBody, sessionId].
     */
    private static String[] forward(String urlStr, String jsonBody, String sessionId)
            throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        if (sessionId != null) {
            conn.setRequestProperty("Mcp-Session-Id", sessionId);
        }
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(60000);

        byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bodyBytes.length);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(bodyBytes);
        }

        int status = conn.getResponseCode();
        String newSessionId = conn.getHeaderField("Mcp-Session-Id");

        // 202 = notification accepted, no body.
        if (status == 202) {
            return new String[]{null, newSessionId};
        }

        byte[] respBytes;
        if (status >= 200 && status < 300) {
            respBytes = conn.getInputStream().readAllBytes();
        } else {
            respBytes = conn.getErrorStream() != null
                    ? conn.getErrorStream().readAllBytes()
                    : new byte[0];
        }

        return new String[]{new String(respBytes, StandardCharsets.UTF_8), newSessionId};
    }

    private static void log(String msg) {
        System.err.println("[mcp-bridge] " + msg);
    }
}
