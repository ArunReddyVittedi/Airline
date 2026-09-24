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
 * The MCP client, pointed at our own operations server.
 * <p>
 * Two beans and that is the whole setup. There is no MCP Spring Boot starter, so unlike the
 * chat model these are not auto configured, which is actually the clearer way to learn it:
 * everything MCP does in this app is visible in this one file.
 * <p>
 * Worth being clear about what happens here, because it surprises everyone the first time.
 * Nobody starts the ops server. Building the client runs {@code java -jar} and the server
 * becomes a child process of this application, talking over its stdin and stdout. No port,
 * no URL, nothing listening. Stop the backend and the server dies with it.
 */
@Configuration
@ConditionalOnProperty(name = "airline.mcp.enabled", havingValue = "true", matchIfMissing = true)
public class McpConfig {

    private static final Logger log = LoggerFactory.getLogger(McpConfig.class);

    /**
     * Ceiling on what one MCP tool result may add to a prompt, in characters.
     * <p>
     * Somebody else's server decides how much JSON it sends back. Ours is well behaved, but
     * the whole point of MCP is that tomorrow this client might point at a server we did not
     * write. One unbounded list is all it takes to blow the context window, and that arrives
     * as a 400 from OpenAI rather than anything that names the real culprit.
     */
    private static final int MAX_TOOL_RESULT = 20_000;

    @Bean
    public McpClient opsMcpClient(@Value("${airline.mcp.server-jar}") String serverJar) {
        McpTransport transport = StdioMcpTransport.builder()
                .command(List.of("java", "-jar", serverJar))
                // Logs the JSON-RPC frames at DEBUG. Off by default in the properties file,
                // and the first thing to turn on when a tool call misbehaves.
                .logEvents(true)
                .build();

        return DefaultMcpClient.builder()
                .key("airline-ops")
                .transport(transport)
                // The default is 30 seconds, which is generous for a local jar and not
                // generous at all on a cold machine where the JVM has to start first.
                .initializationTimeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * Turns the server's tools into tools an {@code @AiService} can call.
     * <p>
     * {@code failIfOneServerFails(false)} is the line that keeps the airline running. If the
     * ops server is not built, or crashes, the trip planner loses its weather tool and every
     * other feature carries on. The alternative is a backend that will not start because a
     * subprocess is missing, which is not a trade any airline would accept.
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
