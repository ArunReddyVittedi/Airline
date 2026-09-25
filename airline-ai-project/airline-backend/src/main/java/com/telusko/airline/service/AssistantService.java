package com.telusko.airline.service;

import com.telusko.airline.agent.TravelAssistant;
import com.telusko.airline.config.AiMetrics;
import com.telusko.airline.dto.AgentViews.AssistantReply;
import com.telusko.airline.dto.AgentViews.TraceStep;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Application wrapper for the passenger assistant, including metrics, traces, source
 * metadata, guardrail responses, and degraded behavior when the model is unavailable.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private static final String FEATURE = "travel-assistant";

    /** Marker separating LangChain4j exception text from the guardrail response. */
    private static final String GUARDRAIL_MESSAGE_MARKER = "failed with this message:";

    private static final String GENERIC_REFUSAL =
            "I can only help with flights, bookings and travel questions.";

    private final TravelAssistant assistant;
    private final AiMetrics metrics;

    /**
     * The set of tool names the MCP server offers, used only to label the trace.
     * <p>
     * An {@code ObjectProvider} because the MCP client is optional: the ops server may not be
     * built yet, and the assistant has to work without it. Injecting the client directly would
     * make a missing subprocess into a startup failure for the whole application.
     */
    private final ObjectProvider<McpClient> mcpClient;

    private volatile Set<String> mcpToolNames;

    public AssistantService(TravelAssistant assistant, AiMetrics metrics, ObjectProvider<McpClient> mcpClient) {
        this.assistant = assistant;
        this.metrics = metrics;
        this.mcpClient = mcpClient;
    }

    /**
     * Answers a question with tool-execution trace entries and retrieved sources.
     */
    public AssistantReply ask(String passengerEmail, String question) {
        try {
            Result<String> result = metrics.record(FEATURE, () -> assistant.chat(passengerEmail, question));

            List<TraceStep> trace = traceOf(result.toolExecutions());
            List<String> sources = sourcesOf(result.sources());

            metrics.recordRetrieval(FEATURE, result.sources() == null ? 0 : result.sources().size());

            return new AssistantReply(result.content(), totalTokens(result), trace, sources);

        } catch (InputGuardrailException ex) {
            // Not an error, and it must not be counted as one. A handful of blocked prompts
            // would otherwise look exactly like an outage on the dashboard.
            metrics.recordGuardrailBlock("prompt-injection", "blocked");
            return new AssistantReply(passengerMessageOf(ex), 0, List.of(), List.of());

        } catch (RuntimeException ex) {
            // The model is unreachable, out of quota, or timed out. A passenger in the middle
            // of a support conversation gets a sentence they can act on rather than a 500.
            metrics.recordDegraded(FEATURE, ex.getClass().getSimpleName());
            log.warn("Travel assistant failed: {}", ex.getMessage());

            return new AssistantReply(
                    "I cannot reach the assistant right now. Your bookings are still available "
                            + "under My Trips, and you can raise a support ticket if you need help.",
                    0, List.of(), List.of());
        }
    }

    /**
     * The streamed version, used by the chat window.
     * <p>
     * No trace here, and that is not an oversight. Tokens leave for the browser as they
     * arrive, so by the time the tool calls are known the answer is already on screen. The
     * UI calls this for the conversation and {@link #ask} wherever the answer gets stored.
     */
    public Flux<String> askStreaming(String passengerEmail, String question) {
        try {
            return assistant.chatStream(passengerEmail, question)
                    .onErrorResume(error -> {
                        metrics.recordDegraded(FEATURE, "stream_" + error.getClass().getSimpleName());
                        log.warn("Streaming assistant failed: {}", error.getMessage());
                        return Flux.just("Sorry, I lost the connection to the assistant. Please try again.");
                    });
        } catch (InputGuardrailException ex) {
            // Thrown before the stream is created, so it cannot be handled inside the Flux.
            metrics.recordDegraded(FEATURE, "input_guardrail");
            return Flux.just(ex.getMessage());
        }
    }

    /**
     * Converts tool executions into trace entries and distinguishes local tools from MCP
     * tools using the remote server's tool inventory.
     */
    private List<TraceStep> traceOf(List<ToolExecution> executions) {
        if (executions == null || executions.isEmpty()) {
            return List.of();
        }

        Set<String> remote = remoteToolNames();

        return executions.stream()
                .map(execution -> {
                    String name = execution.request().name();
                    String ranOn = remote.contains(name) ? "mcp server" : "this app";
                    return new TraceStep(name, "tool", ranOn, execution.request().arguments());
                })
                .toList();
    }

    /** Article titles behind the answer, so the UI can show what it was based on. */
    private List<String> sourcesOf(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return List.of();
        }

        // A LinkedHashSet because several chunks usually come from the same article, and
        // listing one policy four times tells the passenger nothing.
        Set<String> titles = new LinkedHashSet<>();
        for (Content content : contents) {
            Object title = content.textSegment().metadata().toMap().get("title");
            if (title != null) {
                titles.add(String.valueOf(title));
            }
        }
        return List.copyOf(titles);
    }

    /**
     * Caches MCP tool names used to label trace entries without a subprocess round trip on
     * every request.
     */
    private Set<String> remoteToolNames() {
        Set<String> cached = mcpToolNames;
        if (cached != null) {
            return cached;
        }

        McpClient client = mcpClient.getIfAvailable();
        if (client == null) {
            return mcpToolNames = Set.of();
        }

        try {
            Set<String> names = new LinkedHashSet<>();
            client.listTools().forEach(tool -> names.add(tool.name()));
            return mcpToolNames = names;
        } catch (RuntimeException ex) {
            // Preserve assistant availability if remote tool labels cannot be loaded.
            log.debug("Could not list MCP tools for the trace: {}", ex.getMessage());
            return mcpToolNames = Set.of();
        }
    }

    /**
     * Digs the guardrail's own sentence out of the exception.
     * <p>
     * LangChain4j wraps it: the message comes through as "The guardrail
     * com.telusko.airline.guardrail.PromptInjectionGuardrail failed with this message: I can
     * only help with flights...". Showing that to a passenger would be absurd, and the
     * exception offers no accessor for just the reason.
     * <p>
     * So the marker is matched, and anything unexpected falls back to a plain sentence. That
     * fallback is the important half: this depends on wording inside a library, which is
     * exactly the kind of thing that changes in a patch release. If it ever does, passengers
     * get a slightly less specific message rather than a stack trace.
     */
    private static String passengerMessageOf(InputGuardrailException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return GENERIC_REFUSAL;
        }

        int marker = message.indexOf(GUARDRAIL_MESSAGE_MARKER);
        if (marker < 0) {
            return GENERIC_REFUSAL;
        }

        String extracted = message.substring(marker + GUARDRAIL_MESSAGE_MARKER.length()).trim();
        return extracted.isEmpty() ? GENERIC_REFUSAL : extracted;
    }

    private static int totalTokens(Result<?> result) {
        return result.tokenUsage() == null || result.tokenUsage().totalTokenCount() == null
                ? 0
                : result.tokenUsage().totalTokenCount();
    }
}
