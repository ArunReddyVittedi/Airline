package com.telusko.airline.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Trip-planning agents executed as a fixed sequence: destination research, flight advice,
 * then itinerary generation. The deterministic order avoids an unnecessary supervisor call.
 */
public final class TripPlannerAgents {

    private TripPlannerAgents() {
    }

    /**
     * Destination research backed by retrieval and MCP weather and peak-travel tools.
     */
    public interface DestinationResearchAgent {

        @Agent(name = "destinationResearch",
                description = "Researches a destination: what it is known for, the weather in "
                        + "the month of travel, and how busy it will be.",
                outputKey = "destinationNotes")
        @SystemMessage("""
                You research a destination for a traveller.

                Use the retrieved destination guides for what the place is known for, and call
                the weather and peak travel tools for the month they are going.

                Write notes, not prose. Cover:
                  - what the destination is known for, in one line
                  - the weather they should expect, with temperatures
                  - whether their dates fall in a peak or expensive period
                  - one thing that would catch a first time visitor out

                Under 120 words. If the guides do not cover this destination, say so and give
                only what the tools returned. Do not fill the gap from memory.
                """)
        String research(@V("destinationCity") String destinationCity,
                        @V("month") String month,
                        @V("request") @UserMessage String request);
    }

    /**
     * Flight and fare advice based on database-backed flight tools.
     */
    public interface FlightAdvisorAgent {

        @Agent(name = "flightAdvisor",
                description = "Finds real flights for the trip and advises on the best time to "
                        + "fly and roughly what it will cost.",
                outputKey = "flightAdvice")
        @SystemMessage("""
                You advise on getting there.

                What we know about the destination:
                {{destinationNotes}}

                Call the flight search tool for the route and the travel date. Then say, in
                three sentences at most:
                  - what the cheapest option costs and when it departs
                  - whether a different time of day is meaningfully cheaper or faster
                  - how far ahead they should book, given anything the notes said about peak dates

                Only name flights the tool returned. If the search came back empty, say there
                are no flights in the system for that route and date, and stop. Do not invent
                a flight so the plan looks complete.
                """)
        String advise(@V("destinationNotes") String destinationNotes,
                      @V("originCity") String originCity,
                      @V("destinationCity") String destinationCity,
                      @V("departureDate") String departureDate,
                      @V("request") @UserMessage String request);
    }

    /**
     * Writes the itinerary from the preceding agents' factual outputs without additional
     * tools or retrieval.
     */
    public interface ItineraryAgent {

        @Agent(name = "itinerary",
                description = "Writes the day by day itinerary from the destination research "
                        + "and the flight advice.",
                outputKey = "itinerary")
        @SystemMessage("""
                You write a day by day itinerary.

                Destination notes:
                {{destinationNotes}}

                Getting there:
                {{flightAdvice}}

                Write exactly {{days}} days. For each day give a short title and two or three
                activities. Format each day as:

                Day 1: Title
                - activity
                - activity

                Rules:
                  - Day 1 starts after the arrival time in the flight advice. If they land in
                    the evening, do not plan a full day of sightseeing.
                  - Respect the weather in the notes. Do not plan a beach day in the monsoon.
                  - Weight the plan towards the interests given, if any were given.
                  - Nothing invented about flights, fares or hotels. Activities only.
                """)
        String write(@V("destinationNotes") String destinationNotes,
                     @V("flightAdvice") String flightAdvice,
                     @V("days") String days,
                     @V("request") @UserMessage String request);
    }
}
