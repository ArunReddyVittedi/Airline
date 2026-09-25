package com.telusko.airline.config;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Configures an MCP client for the bundled operations server. The backend launches the
 * server as a child {@code java -jar} process and communicates over standard input/output.
 */
@Configuration
@ConditionalOnProperty(name = "airline.mcp.enabled", havingValue = "true", matchIfMissing = true)
public class McpConfig {

    private static final Logger log = LoggerFactory.getLogger(McpConfig.class);

    /**
     * Maximum MCP tool-result length included in a prompt, in characters.
     */
    private static final int MAX_TOOL_RESULT = 20_000;

    @Bean
    public McpClient opsMcpClient(@Value("${airline.mcp.server-jar}") String serverJar) {
        McpTransport transport = StdioMcpTransport.builder()
                .command(List.of("java", "-jar", serverJar))
                // Make JSON-RPC frames available at DEBUG level.
                .logEvents(true)
                .build();

        return DefaultMcpClient.builder()
                .key("airline-ops")
                .transport(transport)
                // Allow time for a cold JVM to start the child process.
                .initializationTimeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * Exposes MCP server tools to AI services. MCP failure degrades dependent features
     * without preventing unrelated backend features from starting.
     */
    @Bean
    public ToolProvider mcpToolProvider(McpClient opsMcpClient) {
        return McpToolProvider.builder()
                .mcpClients(opsMcpClient)
                .failIfOneServerFails(false)
                .toolWrapper(executor -> (request, memoryId) -> {
                    String result = executor.execute(request, memoryId);
                    if (result.length() <= MAX_TOOL_RESULT) {
                        return result;
                    }
                    log.warn("Truncated an MCP tool result of {} characters", result.length());
                    return result.substring(0, MAX_TOOL_RESULT) + " ... [truncated]";
                })
                .build();
    }
}
