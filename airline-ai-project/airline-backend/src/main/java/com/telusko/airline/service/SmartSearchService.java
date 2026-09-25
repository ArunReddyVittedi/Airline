package com.telusko.airline.service;

import com.telusko.airline.agent.SearchIntentAgent;
import com.telusko.airline.config.AiMetrics;
import com.telusko.airline.dto.FlightViews.FlightOption;
import com.telusko.airline.dto.FlightViews.FlightSearchResult;
import com.telusko.airline.dto.FlightViews.SearchIntent;
import com.telusko.airline.enums.CabinClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

/**
 * Plain English flight search: the model reads the sentence, the database finds the flights.
 * <p>
 * This split is the whole idea. "Cheapest morning flight to Goa next Friday" is trivial for a
 * person and impossible for a query parser, so the model is used for the one thing it is
 * genuinely better at, which is reading intent out of a sentence. It is then given no say at
 * all in the answer: the flights come from {@link FlightService}, so a fare can never be
 * invented and a flight that does not exist can never be listed.
 */
@Service
public class SmartSearchService {

    private static final Logger log = LoggerFactory.getLogger(SmartSearchService.class);

    private static final String FEATURE = "smart-search";

    private final SearchIntentAgent intentAgent;
    private final FlightService flightService;
    private final AiMetrics metrics;

    public SmartSearchService(SearchIntentAgent intentAgent, FlightService flightService, AiMetrics metrics) {
        this.intentAgent = intentAgent;
        this.flightService = flightService;
        this.metrics = metrics;
    }

    public FlightSearchResult search(String sentence) {
        SearchIntent intent;

        try {
            // Today's date is passed in because the model has no clock. Without it, "next
            // Friday" gets resolved against the training data and the search silently
            // returns nothing, which looks like an empty database.
            intent = metrics.record(FEATURE,
                    () -> intentAgent.extract(LocalDate.now().toString(), sentence));

        } catch (RuntimeException ex) {
            metrics.recordDegraded(FEATURE, ex.getClass().getSimpleName());
            log.warn("Could not read the search sentence: {}", ex.getMessage());
            return new FlightSearchResult(
                    "Could not understand that. Try: flights from Mumbai to Goa on 2026-10-15.",
                    List.of());
        }

        LocalDate date = parseDate(intent.departureDate());

        if (date == null || isBlank(intent.destinationCity())) {
            return new FlightSearchResult(
                    "I could not tell where or when you want to fly. Try naming the city and the date.",
                    List.of());
        }

        List<FlightOption> flights = flightService.search(
                intent.originCity(), intent.destinationCity(), date,
                parseCabin(intent.cabinClass()), intent.timeOfDay());

        return new FlightSearchResult(describe(intent, date), sortBy(flights, intent.sortBy()));
    }

    /**
     * Sorting happens here rather than in SQL because the model chose the order.
     * <p>
     * The repository always returns cheapest first, which is the right default. Re-sorting a
     * list of at most eight rows in memory is free, and it keeps the query simple instead of
     * building a dynamic ORDER BY from model output, which is a place nobody should be
     * putting model output.
     */
    private static List<FlightOption> sortBy(List<FlightOption> flights, String sortBy) {
        if (sortBy != null && sortBy.equalsIgnoreCase("duration")) {
            return flights.stream()
                    .sorted(Comparator.comparingLong(FlightOption::durationMinutes))
                    .toList();
        }
        return flights;
    }

    /**
     * Describes the interpreted search criteria so empty results can be distinguished from
     * an incorrectly interpreted request.
     */
    private static String describe(SearchIntent intent, LocalDate date) {
        StringBuilder text = new StringBuilder("Flights from ")
                .append(intent.originCity())
                .append(" to ")
                .append(intent.destinationCity())
                .append(" on ")
                .append(date);

        if (!isBlank(intent.timeOfDay()) && !intent.timeOfDay().equalsIgnoreCase("any")) {
            text.append(", ").append(intent.timeOfDay().toLowerCase());
        }
        if (!isBlank(intent.cabinClass()) && !intent.cabinClass().equalsIgnoreCase("ANY")) {
            text.append(", ").append(intent.cabinClass().toLowerCase().replace('_', ' '));
        }
        return text.toString();
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException | NullPointerException ex) {
            return null;
        }
    }

    private static CabinClass parseCabin(String value) {
        if (isBlank(value) || value.equalsIgnoreCase("ANY")) {
            return null;
        }
        try {
            return CabinClass.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
