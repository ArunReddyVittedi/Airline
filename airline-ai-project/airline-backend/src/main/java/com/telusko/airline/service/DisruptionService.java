package com.telusko.airline.service;

import com.telusko.airline.config.AiMetrics;
import com.telusko.airline.dto.AgentViews.DisruptionOutcome;
import com.telusko.airline.dto.AgentViews.TraceStep;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the disruption supervisor and turns the result into something a UI can render.
 * <p>
 * This is the file that makes multi agent work explainable, and it is the one worth reading
 * twice. The supervisor returns a paragraph; on its own that paragraph is unverifiable. What
 * makes it trustworthy is the {@link AgenticScope} that comes back with it, holding what
 * every sub agent was asked and what it answered. Both go to the browser.
 */
@Service
public class DisruptionService {

    private static final Logger log = LoggerFactory.getLogger(DisruptionService.class);

    private static final String FEATURE = "disruption-supervisor";

    private final SupervisorAgent supervisor;
    private final AiMetrics metrics;

    public DisruptionService(SupervisorAgent disruptionSupervisor, AiMetrics metrics) {
        this.supervisor = disruptionSupervisor;
        this.metrics = metrics;
    }

    /**
     * Handles one disrupted booking.
     * <p>
     * The PNR is put into the request text rather than passed as a typed argument, because
     * the supervisor decides which agent runs and therefore what arguments are needed. The
     * situation agent picks the PNR up from the request, writes its findings into the scope,
     * and from then on the other agents read the scope rather than the original sentence.
     */
    public DisruptionOutcome handle(String passengerEmail, String pnr) {
        String request = """
                Passenger %s is asking about booking %s.
                Work out what has happened to this booking and what we should tell them.
                """.formatted(passengerEmail, pnr);

        try {
            ResultWithAgenticScope<String> outcome =
                    metrics.record(FEATURE, () -> supervisor.invokeWithAgenticScope(request));

            AgenticScope scope = outcome.agenticScope();
            recordHandoffs(scope);

            return new DisruptionOutcome(
                    pnr,
                    stateOrEmpty(scope, "situation"),
                    stateOrEmpty(scope, "rebookingAdvice"),
                    stateOrEmpty(scope, "compensationAdvice"),
                    // The supervisor's own summary if the message agent never ran, which
                    // happens when the flight turns out to be on time.
                    firstNonBlank(stateOrEmpty(scope, "messageToPassenger"), outcome.result()),
                    traceOf(scope));

        } catch (RuntimeException ex) {
            metrics.recordDegraded(FEATURE, ex.getClass().getSimpleName());
            log.warn("Disruption supervisor failed for {}: {}", pnr, ex.getMessage());

            // Deliberately does not pretend to know anything. A disrupted passenger given a
            // guess is worse off than one told to talk to a human.
            return new DisruptionOutcome(pnr,
                    "We could not assess this booking automatically.",
                    "", "",
                    "We are having trouble checking your booking right now. Please contact the "
                            + "support desk and quote " + pnr + ", and they will sort this out for you.",
                    List.of());
        }
    }

    /**
     * Counts who handed work to whom.
     * <p>
     * The invocations arrive in the order the supervisor made them, so consecutive pairs are
     * the handoffs. Once these counters exist, a supervisor that starts consulting the
     * compensation agent about baggage questions shows up on a dashboard instead of in a
     * complaint three weeks later.
     */
    private void recordHandoffs(AgenticScope scope) {
        List<AgentInvocation> invocations = scope.agentInvocations();

        metrics.recordHandoff("supervisor", invocations.isEmpty() ? "none" : invocations.get(0).agentName());

        for (int i = 1; i < invocations.size(); i++) {
            metrics.recordHandoff(invocations.get(i - 1).agentName(), invocations.get(i).agentName());
        }
    }

    private List<TraceStep> traceOf(AgenticScope scope) {
        List<TraceStep> trace = new ArrayList<>();

        for (AgentInvocation invocation : scope.agentInvocations()) {
            trace.add(new TraceStep(
                    invocation.agentName(),
                    "agent",
                    "supervisor delegated",
                    // Trimmed, because a sub agent answer can be a full paragraph and the
                    // trace is a summary of what happened, not a second copy of the answer.
                    summarise(String.valueOf(invocation.output()))));
        }

        return trace;
    }

    private static String summarise(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replace('\n', ' ').trim();
        return flat.length() <= 200 ? flat : flat.substring(0, 200) + "...";
    }

    private static String stateOrEmpty(AgenticScope scope, String key) {
        Object value = scope.hasState(key) ? scope.readState(key) : null;
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : (second == null ? "" : second);
    }
}
