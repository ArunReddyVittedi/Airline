package com.telusko.airline.tools;

import com.telusko.airline.config.AiMetrics;
import com.telusko.airline.dto.FlightViews.FlightOption;
import com.telusko.airline.enums.CabinClass;
import com.telusko.airline.service.FlightService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Flight lookups, exposed to the agents as callable tools.
 * <p>
 * This is deliberately not RAG. Retrieval is for questions about text. "Which flights go to
 * Goa on Friday" is a question about rows, and a model reading a sample of flight documents
 * would invent a flight number. Every tool here runs a real query, so the assistant reports
 * flights that exist.
 * <p>
 * Note what the parameters are: Strings and ints. A {@code LocalDate} parameter becomes a
 * nested {@code year, month, day} object in the JSON schema, which does not match what the
 * description promises and is harder for the model to fill in correctly. Taking the date as
 * text and parsing it here is the version that works.
 */
@Component
public class FlightTools {

    private final FlightService flightService;
    private final AiMetrics metrics;

    public FlightTools(FlightService flightService, AiMetrics metrics) {
        this.flightService = flightService;
        this.metrics = metrics;
    }

    @Tool("""
            Searches bookable flights on a route for one day.
            Origin and destination may be a city name or a three letter IATA code.
            date must be yyyy-MM-dd. cabin may be ECONOMY, PREMIUM_ECONOMY, BUSINESS or ANY.
            timeOfDay may be morning, afternoon, evening, night or any.
            Returns at most eight flights, cheapest first. Cancelled and full flights are excluded.
            """)
    public List<FlightOption> searchFlights(
            @P("departure city or IATA code, for example Mumbai or BOM") String origin,
            @P("arrival city or IATA code, for example Goa or GOI") String destination,
            @P("date of departure as yyyy-MM-dd") String date,
            @P("cabin class, or ANY if the passenger did not say") String cabin,
            @P("morning, afternoon, evening, night, or any") String timeOfDay) {

        metrics.recordToolCall("searchFlights", "local");

        LocalDate departure = parseDate(date);
        if (departure == null) {
            // An empty result allows the model to request a valid date without a tool error.
            return List.of();
        }

        return flightService.search(origin, destination, departure, parseCabin(cabin), timeOfDay);
    }

    @Tool("""
            Current operational status of one flight: scheduled, delayed, cancelled, departed
            or landed. Use this whenever a passenger asks whether their flight is on time.
            """)
    public FlightOption flightStatus(@P("flight number, for example TL401") String flightNumber) {
        metrics.recordToolCall("flightStatus", "local");
        return flightService.byNumber(flightNumber).orElse(null);
    }

    @Tool("""
            Later flights on the same route as a disrupted flight, earliest departure first.
            Use this when a flight is cancelled or badly delayed and the passenger needs to
            get to the same destination.
            """)
    public List<FlightOption> alternativeFlights(
            @P("the flight number that was cancelled or delayed") String flightNumber) {

        metrics.recordToolCall("alternativeFlights", "local");

        return flightService.entityByNumber(flightNumber)
                .map(flightService::alternativesFor)
                .orElseGet(List::of);
    }

    /**
     * Models write dates confidently and not always correctly, so a bad value has to be
     * survivable. Returning null lets the caller ask the passenger instead of failing.
     */
    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException | NullPointerException ex) {
            return null;
        }
    }

    /** ANY, an empty string and a word we do not recognise all mean "no cabin filter". */
    private static CabinClass parseCabin(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("ANY")) {
            return null;
        }
        try {
            return CabinClass.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
