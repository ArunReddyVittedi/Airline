package com.telusko.airline.config;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records per-agent latency within supervisor and sequence runs. The listener is inherited
 * by sub-agents so new specialists are measured automatically.
 */
@Component
public class AgentMetricsListener implements AgentListener {

    private static final Logger log = LoggerFactory.getLogger(AgentMetricsListener.class);

    /**
     * Start times, keyed by the agent invocation id rather than the agent name.
     * <p>
     * The id matters: a supervisor is allowed to call the same agent twice in one run, and
     * keying by name would have the second call overwrite the first one's start time and
     * report a nonsense duration for both.
     */
    private final Map<String, Long> startedAt = new ConcurrentHashMap<>();

    private final AiMetrics metrics;

    public AgentMetricsListener(AiMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        startedAt.put(request.agentId(), System.nanoTime());
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        record(response.agentId(), response.agentName(), true);
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        record(error.agentId(), error.agentName(), false);
        log.warn("Agent {} failed: {}", error.agentName(), error.error().getMessage());
    }

    /** Apply the listener to sub-agents automatically. */
    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    /**
     * Removes each start time after recording to keep the map bounded.
     */
    private void record(String agentId, String agentName, boolean success) {
        Long start = startedAt.remove(agentId);
        if (start == null) {
            // No matching before event. Nothing useful to record, and guessing a duration
            // would be worse than having no data point.
            return;
        }
        metrics.recordAgent(agentName, (System.nanoTime() - start) / 1_000_000, success);
    }
}
