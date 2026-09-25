package com.telusko.airline.mcp;

import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.community.mcp.server.transport.StdioMcpServerTransport;
import dev.langchain4j.mcp.protocol.McpImplementation;

import java.util.List;

/**
 * MCP stdio server for airline operations tools. Standard output is reserved for JSON-RPC;
 * process diagnostics use standard error to preserve the protocol stream.
 */
public class AirlineOpsMcpServer {

    public static void main(String[] args) throws Exception {
        McpServer server = new McpServer(
                List.of(new OpsTools()),
                new McpImplementation("airline-ops", "1.0.0"));

        System.err.println("[airline-ops] MCP server ready on stdio");

        // Block until the backend closes the stdio transport.
        try (StdioMcpServerTransport transport = new StdioMcpServerTransport(server)) {
            transport.awaitClose();
        }
    }
}
