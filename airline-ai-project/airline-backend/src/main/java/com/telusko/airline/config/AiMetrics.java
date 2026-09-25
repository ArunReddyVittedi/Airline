package com.telusko.airline.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Metrics for the AI features, recorded per feature rather than per model call.
 * <p>
 * LangChain4j does not publish Spring AI's {@code gen_ai.*} meters, so application-specific
 * metrics identify feature failures, retrieval outcomes, and agent handoffs.
 * <p>
 * Token counting lives in {@link TokenUsageListener}, because tokens are a property of a
 * model call and everything here is a property of a feature.
 */
@Component
public class AiMetrics {

    /** Latency of a whole feature, split by success or failure. */
    private static final String FEATURE_TIMER = "airline.ai.feature";

    /** Latency of one agent inside a multi agent run. */
    private static final String AGENT_TIMER = "airline.ai.agent";

    /** How many chunks retrieval returned for a request. */
    private static final String RETRIEVAL_DOCS = "airline.ai.retrieval.documents";

    /** Requests where retrieval found nothing at all. */
    private static final String RETRIEVAL_EMPTY = "airline.ai.retrieval.empty";

    /** Tool calls, tagged with the tool name and where it ran. */
    private static final String TOOL_CALLS = "airline.ai.tool.calls";

    /** One agent passing work to another. */
    private static final String HANDOFFS = "airline.ai.agent.handoff";

    /** Times a guardrail refused something. */
    private static final String GUARDRAIL_BLOCKS = "airline.ai.guardrail.blocked";

    /** Times an AI call failed and the app served a working but degraded answer. */
    private static final String DEGRADED = "airline.ai.degraded";

    private final MeterRegistry registry;

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Times an AI feature and tags the outcome.
     * <p>
     * The exception is re-thrown untouched, so this only observes. Failures still land as
     * {@code outcome=failure}, which matters because most callers catch the error and
     * fall back. Without this the dashboard would show a healthy system while every answer
     * came from the fallback path.
     */
    public <T> T record(String feature, Supplier<T> call) {
        Timer.Sample sample = Timer.start(registry);
        String outcome = "success";
        try {
            return call.get();
        } catch (RuntimeException ex) {
            outcome = "failure";
            throw ex;
        } finally {
            sample.stop(Timer.builder(FEATURE_TIMER)
                    .description("Latency of an end to end AI feature, by outcome")
                    .tag("feature", feature)
                    .tag("outcome", outcome)
                    .register(registry));
        }
    }

    /**
     * Times one agent within a multi-agent run for per-stage latency analysis.
     */
    public void recordAgent(String agent, long millis, boolean success) {
        Timer.builder(AGENT_TIMER)
                .description("Latency of a single agent inside a multi agent run")
                .tag("agent", agent)
                .tag("outcome", success ? "success" : "failure")
                .register(registry)
                .record(java.time.Duration.ofMillis(millis));
    }

    /**
     * Records how much grounding a request actually got.
     * <p>
     * Empty retrievals do not raise an error, so this signal identifies indexing failures or
     * an overly strict score floor.
     */
    public void recordRetrieval(String feature, int documentsFound) {
        DistributionSummary.builder(RETRIEVAL_DOCS)
                .description("Number of chunks returned by vector search")
                .tag("feature", feature)
                .register(registry)
                .record(documentsFound);

        if (documentsFound == 0) {
            Counter.builder(RETRIEVAL_EMPTY)
                    .description("Retrievals that returned nothing")
                    .tag("feature", feature)
                    .register(registry)
                    .increment();
        }
    }

    /**
     * Counts a tool call.
     *
     * @param source {@code local} for an in-process tool or {@code mcp} for an operations-
     *               server tool crossing a process boundary
     */
    public void recordToolCall(String toolName, String source) {
        Counter.builder(TOOL_CALLS)
                .description("Tool invocations, by tool and where the tool runs")
                .tag("tool", toolName)
                .tag("source", source)
                .register(registry)
                .increment();
    }

    /**
     * Counts one agent handing work to another.
     * <p>
     * This is the metric that makes a multi agent system debuggable. When the supervisor
     * starts calling the rebooking agent on questions about baggage, the handoff counters
     * show it long before anyone reads a transcript.
     */
    public void recordHandoff(String from, String to) {
        Counter.builder(HANDOFFS)
                .description("Work passed from one agent to another")
                .tag("from", from)
                .tag("to", to)
                .register(registry)
                .increment();
    }

    /** Counts a guardrail refusal, so a spike in blocked input is visible. */
    public void recordGuardrailBlock(String guardrail, String reason) {
        Counter.builder(GUARDRAIL_BLOCKS)
                .description("Requests or answers rejected by a guardrail")
                .tag("guardrail", guardrail)
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    /**
     * Counts a fallback: the AI call failed and the app served a plain answer instead.
     * These are invisible in HTTP metrics, because the request still returns 200.
     */
    public void recordDegraded(String feature, String reason) {
        Counter.builder(DEGRADED)
                .description("AI calls that failed and fell back to a non AI response")
                .tag("feature", feature)
                .tag("reason", reason)
                .register(registry)
                .increment();
    }
}
