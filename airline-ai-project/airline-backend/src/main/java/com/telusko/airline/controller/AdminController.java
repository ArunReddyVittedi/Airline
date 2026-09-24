package com.telusko.airline.controller;

import com.telusko.airline.agent.OpsAnalyticsAgent;
import com.telusko.airline.config.AiMetrics;
import com.telusko.airline.dto.AgentViews.AssistantReply;
import com.telusko.airline.dto.AgentViews.TraceStep;
import com.telusko.airline.dto.ApiDtos.ApiMessage;
import com.telusko.airline.dto.ApiDtos.AskRequest;
import com.telusko.airline.dto.FlightViews.FlightOption;
import com.telusko.airline.dto.TicketViews.TicketView;
import com.telusko.airline.enums.FlightStatus;
import com.telusko.airline.service.FlightService;
import com.telusko.airline.service.KnowledgeIndexService;
import com.telusko.airline.service.TicketService;
import dev.langchain4j.service.Result;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The admin side. Guarded by {@code hasRole("ADMIN")} in SecurityConfig.
 * <p>
 * Everything here is either data a passenger must not see, or an action that changes the
 * world: revenue figures, the support backlog, cancelling a flight and rebuilding the
 * knowledge index.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private static final String FEATURE = "ops-analytics";

    private final OpsAnalyticsAgent opsAgent;
    private final FlightService flightService;
    private final TicketService ticketService;
    private final KnowledgeIndexService knowledgeIndexService;
    private final AiMetrics metrics;

    public AdminController(OpsAnalyticsAgent opsAgent, FlightService flightService,
                           TicketService ticketService, KnowledgeIndexService knowledgeIndexService,
                           AiMetrics metrics) {
        this.opsAgent = opsAgent;
        this.flightService = flightService;
        this.ticketService = ticketService;
        this.knowledgeIndexService = knowledgeIndexService;
        this.metrics = metrics;
    }

    /**
     * Airline numbers in plain English.
     * <p>
     * "What did we make this week and which route is busiest" is one question to a manager
     * and two aggregate queries to a database. The agent bridges that, and because every
     * figure comes from a tool it cannot round, extrapolate or flatter.
     */
    @PostMapping("/ask")
    public AssistantReply ask(@Valid @RequestBody AskRequest request) {
        try {
            Result<String> result = metrics.record(FEATURE, () -> opsAgent.ask(request.question()));

            List<TraceStep> trace = result.toolExecutions().stream()
                    .map(execution -> new TraceStep(
                            execution.request().name(), "tool", "this app", execution.request().arguments()))
                    .toList();

            int tokens = result.tokenUsage() == null || result.tokenUsage().totalTokenCount() == null
                    ? 0 : result.tokenUsage().totalTokenCount();

            return new AssistantReply(result.content(), tokens, trace, List.of());

        } catch (RuntimeException ex) {
            metrics.recordDegraded(FEATURE, ex.getClass().getSimpleName());
            log.warn("Ops analytics failed: {}", ex.getMessage());

            return new AssistantReply(
                    "The analytics assistant is unavailable. The figures are still in the "
                            + "database, and /actuator/metrics is unaffected.",
                    0, List.of(), List.of());
        }
    }

    /**
     * Cancels or delays a flight.
     * <p>
     * This exists so the disruption agents have something real to react to. Cancel a flight
     * here, then run the disruption endpoint against a booking on it, and the whole
     * supervisor flow plays out against actual data rather than a fixture.
     */
    @PostMapping("/flights/{flightNumber}/status")
    public FlightOption updateFlightStatus(@PathVariable String flightNumber,
                                           @RequestParam FlightStatus status,
                                           @RequestParam(defaultValue = "0") int delayMinutes) {
        return flightService.updateStatus(flightNumber, status, delayMinutes);
    }

    /** The open queue, highest priority first. Shows what triage actually did. */
    @GetMapping("/tickets")
    public List<TicketView> openTickets() {
        return ticketService.openQueue();
    }

    /**
     * Rebuilds the vector index from the articles table.
     * <p>
     * Needed after editing an article, because the vectors are a copy of the text and do not
     * update themselves. This costs embedding calls, which is why it is a deliberate action
     * and not something that happens on every save.
     */
    @PostMapping("/knowledge/reindex")
    public ApiMessage reindex() {
        int chunks = knowledgeIndexService.reindexAll();
        return new ApiMessage("Reindexed the knowledge base into roughly " + chunks + " chunks.");
    }
}
