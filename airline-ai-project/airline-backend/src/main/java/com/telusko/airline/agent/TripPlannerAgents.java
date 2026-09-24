package com.telusko.airline.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * The trip planner, built as a fixed sequence rather than a supervisor.
 * <p>
 * This is the other half of the agent story, and the contrast with {@link DisruptionAgents}
 * is the lesson. Planning a trip always needs the same three steps in the same order:
 * research the destination, check what it costs to fly there, then write the itinerary. There
 * is nothing for a supervisor to decide, so paying a model to decide it would be waste.
 * <p>
 * A sequence is cheaper, faster and completely predictable. Reach for a supervisor only when
 * the right next step genuinely depends on what the previous step found, which is exactly the
 * case for disruption and exactly not the case here.
 */
public final class TripPlannerAgents {

    private TripPlannerAgents() {
    }

    /**
     * What the destination is actually like. Retrieval, plus the ops server for weather.
     * <p>
     * The weather tool is an MCP tool, and that is deliberate. Climate is not something an
     * airline owns, so it sits behind the protocol along with airport congestion and peak
     * travel dates. Swap the ops server for a real weather API and nothing in this agent
     * changes, which is the whole argument for MCP in one example.
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
     * What it costs and when to fly. Real flights from the database, never invented.
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
     * Writes the day by day plan from what the first two agents found.
     * <p>
     * No tools and no retrieval, which is the point of putting it last. By this stage
     * everything factual is already in the scope, so this agent only has to write. Giving it
     * tools as well would let it go back and contradict the flight advice it was handed.
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
