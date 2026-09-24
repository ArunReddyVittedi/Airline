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
 * Times each agent inside a multi agent run.
 * <p>
 * The scope tells you afterwards which agents ran and what they said, but not how long any of
 * them took, so this listens instead. It is the metric that makes a slow supervisor
 * diagnosable: a twelve second run is almost always one sub agent doing all the waiting, and
 * without a per agent timer the only way to find which is to read a transcript and guess.
 * <p>
 * Attached once, to the supervisor and to the sequence.
 * {@link #inheritedBySubagents()} returns true so every specialist underneath is covered
 * without having to remember to add the listener to each new one.
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

    /** Sub agents are covered automatically, which is the whole point of attaching it once. */
    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    /**
     * Removes the start time as it reads it.
     * <p>
     * Without the remove this map grows for the lifetime of the process, one entry per agent
     * invocation ever made. That is a slow leak rather than a fast one, which is the kind
     * that reaches production.
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
