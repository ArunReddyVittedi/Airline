package com.telusko.airline.dto;

import java.util.List;

public final class AgentViews {

    private AgentViews() {
    }

    /**
     * One line of the trace: which agent or tool ran, and where.
     * <p>
     * This is what makes the whole system explainable. Without it a multi agent answer is a
     * paragraph you either believe or do not, and the demo is unteachable. With it you can
     * point at the screen and say "the supervisor called the rebooking agent, which called a
     * tool on the MCP server".
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
     * What the disruption supervisor returns.
     * <p>
     * Deliberately not just a String. The supervisor coordinates three sub agents, and the
     * useful part of the demo is seeing what each one contributed, not only the final summary.
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
