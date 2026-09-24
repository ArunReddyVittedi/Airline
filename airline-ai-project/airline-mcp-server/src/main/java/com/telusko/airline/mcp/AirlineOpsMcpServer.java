package com.telusko.airline.mcp;

import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.community.mcp.server.transport.StdioMcpServerTransport;
import dev.langchain4j.mcp.protocol.McpImplementation;

import java.util.List;

/**
 * An MCP stdio server standing in for the airline operations system.
 * <p>
 * This is a plain jar and not a Spring Boot app, and it has to be. A stdio server owns
 * stdout: every byte written there is a JSON-RPC frame the client is parsing. Spring Boot
 * prints a banner and its startup log to stdout, which would arrive at the client as
 * malformed protocol and hang the handshake before the app finished booting.
 * <p>
 * That is also why the one line of output below goes to System.err. Anything this process
 * wants a human to read has to go to stderr, always.
 */
public class AirlineOpsMcpServer {

    public static void main(String[] args) throws Exception {
        McpServer server = new McpServer(
                List.of(new OpsTools()),
                new McpImplementation("airline-ops", "1.0.0"));

        System.err.println("[airline-ops] MCP server ready on stdio");

        // awaitClose blocks until the client closes the pipe, which happens when the backend
        // shuts down. A stdio server that returns from main immediately is a server that
        // exited, and the client reports it as "Process has exited" with no other clue.
        try (StdioMcpServerTransport transport = new StdioMcpServerTransport(server)) {
            transport.awaitClose();
        }
    }
}
