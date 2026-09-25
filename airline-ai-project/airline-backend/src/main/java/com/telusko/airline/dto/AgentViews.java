package com.telusko.airline.dto;

import java.util.List;

public final class AgentViews {

    private AgentViews() {
    }

    /**
     * One trace entry identifying the agent or tool, execution type, location, and detail.
     */
    public record TraceStep(String name, String kind, String ranOn, String detail) {
    }

    /** A normal assistant reply, with the trace attached. */
    public record AssistantReply(
            String answer,
            int totalTokens,
            List<TraceStep> trace,
            List<String> sources) {
    }

    /**
     * Structured disruption result containing each specialist contribution and final summary.
     */
    public record DisruptionOutcome(
            String pnr,
            String situation,
            String rebookingAdvice,
            String compensationAdvice,
            String messageToPassenger,
            List<TraceStep> trace) {
    }

    /** A day of the generated itinerary. */
    public record ItineraryDay(int day, String title, List<String> activities) {
    }

    public record TripPlan(
            String destination,
            String summary,
            String flightAdvice,
            String weatherNote,
            List<ItineraryDay> days,
            List<TraceStep> trace) {
    }
}
