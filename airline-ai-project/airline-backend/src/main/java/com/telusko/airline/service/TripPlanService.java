package com.telusko.airline.service;

import com.telusko.airline.config.AiMetrics;
import com.telusko.airline.dto.AgentViews.ItineraryDay;
import com.telusko.airline.dto.AgentViews.TraceStep;
import com.telusko.airline.dto.AgentViews.TripPlan;
import com.telusko.airline.dto.ApiDtos.TripPlanRequest;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the trip-planning sequence and parses its prompted text format into UI itinerary days.
 * Unparseable output is returned as a single day instead of being discarded.
 */
@Service
public class TripPlanService {

    private static final Logger log = LoggerFactory.getLogger(TripPlanService.class);

    private static final String FEATURE = "trip-planner";

    /** Three days if nobody says, and never more than seven. */
    private static final int DEFAULT_DAYS = 3;
    private static final int MAX_DAYS = 7;

    private final UntypedAgent tripPlanner;
    private final AiMetrics metrics;

    public TripPlanService(UntypedAgent tripPlanner, AiMetrics metrics) {
        this.tripPlanner = tripPlanner;
        this.metrics = metrics;
    }

    public TripPlan plan(TripPlanRequest request) {
        int days = clampDays(request.days());
        LocalDate departure = LocalDate.now().plusWeeks(2);

        // Keys must match the named agent parameters used by the sequence.
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("originCity", request.originCity());
        input.put("destinationCity", request.destinationCity());
        input.put("departureDate", departure.toString());
        input.put("month", String.valueOf(departure.getMonthValue()));
        input.put("days", String.valueOf(days));
        input.put("request", buildRequestText(request, days, departure));

        try {
            ResultWithAgenticScope<String> outcome =
                    metrics.record(FEATURE, () -> tripPlanner.invokeWithAgenticScope(input));

            AgenticScope scope = outcome.agenticScope();
            String notes = stateOrEmpty(scope, "destinationNotes");
            String flightAdvice = stateOrEmpty(scope, "flightAdvice");
            String itinerary = firstNonBlank(stateOrEmpty(scope, "itinerary"), outcome.result());

            return new TripPlan(
                    request.destinationCity(),
                    notes,
                    flightAdvice,
                    weatherLineFrom(notes),
                    parseDays(itinerary, days),
                    traceOf(scope));

        } catch (RuntimeException ex) {
            metrics.recordDegraded(FEATURE, ex.getClass().getSimpleName());
            log.warn("Trip planner failed for {}: {}", request.destinationCity(), ex.getMessage());

            return new TripPlan(request.destinationCity(),
                    "We could not build a plan right now.", "", "",
                    List.of(), List.of());
        }
    }

    private String buildRequestText(TripPlanRequest request, int days, LocalDate departure) {
        String interests = request.interests() == null || request.interests().isBlank()
                ? "no particular interests given"
                : request.interests();

        return """
                Plan a %d day trip to %s, flying from %s on %s.
                Traveller interests: %s.
                """.formatted(days, request.destinationCity(), request.originCity(), departure, interests);
    }

    /**
     * Pulls the weather sentence out of the research notes for the summary card.
     * <p>
     * The notes already contain it and re-asking a model to extract one line would be a
     * second billed call for something a string search does perfectly well.
     */
    private static String weatherLineFrom(String notes) {
        if (notes == null || notes.isBlank()) {
            return "";
        }
        for (String line : notes.split("\n")) {
            String lower = line.toLowerCase();

            // Match explicit weather terms or a numeric temperature; require a digit before
            // a Celsius marker to avoid unrelated words ending in "c".
            if (lower.contains("weather") || lower.contains("monsoon")
                    || lower.matches(".*\\d+\\s*(to\\s*\\d+\\s*)?(degrees|c\\b).*")) {
                return line.replaceFirst("^[-*\\s]+", "").replace("**", "").trim();
            }
        }
        return "";
    }

    /**
     * Reads the "Day 1: Title" format the itinerary agent was asked to produce.
     * <p>
     * Nonblank lines beneath a day heading become activities. Bullets, dashes, and plain
     * lines are accepted.
     */
    private static List<ItineraryDay> parseDays(String itinerary, int expectedDays) {
        if (itinerary == null || itinerary.isBlank()) {
            return List.of();
        }

        List<ItineraryDay> days = new ArrayList<>();
        List<String> activities = new ArrayList<>();
        String title = null;
        int dayNumber = 0;

        for (String rawLine : itinerary.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.toLowerCase().startsWith("day ")) {
                if (title != null) {
                    days.add(new ItineraryDay(dayNumber, title, List.copyOf(activities)));
                    activities.clear();
                }
                dayNumber = days.size() + 1;
                title = line.replaceFirst("(?i)^day\\s*\\d+\\s*[:.\\-]?\\s*", "").trim();
                if (title.isEmpty()) {
                    title = "Day " + dayNumber;
                }
            } else if (title != null) {
                activities.add(line.replaceFirst("^[-*\\d.\\s]+", "").trim());
            }
        }

        if (title != null) {
            days.add(new ItineraryDay(dayNumber, title, List.copyOf(activities)));
        }

        // Preserve unparseable model output as a single itinerary block.
        if (days.isEmpty()) {
            return List.of(new ItineraryDay(1, "Your trip", List.of(itinerary.trim())));
        }

        return days.size() > expectedDays ? days.subList(0, expectedDays) : days;
    }

    private List<TraceStep> traceOf(AgenticScope scope) {
        List<TraceStep> trace = new ArrayList<>();
        for (AgentInvocation invocation : scope.agentInvocations()) {
            trace.add(new TraceStep(
                    invocation.agentName(),
                    "agent",
                    "sequence step " + (trace.size() + 1),
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

    private static int clampDays(int requested) {
        if (requested <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.min(requested, MAX_DAYS);
    }

    private static String stateOrEmpty(AgenticScope scope, String key) {
        Object value = scope.hasState(key) ? scope.readState(key) : null;
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : (second == null ? "" : second);
    }
}
