package com.telusko.airline.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The shapes a flight takes on the way out of the app.
 * <p>
 * Grouped in one file because they are read together and none of them is big enough to be
 * worth its own. They are records rather than entities on purpose: a tool that returned a
 * {@code Flight} would drag lazy associations into JSON serialisation, and a model reading
 * that JSON would see Hibernate proxies instead of a departure time.
 */
public final class FlightViews {

    private FlightViews() {
    }

    public record FlightOption(
            String flightNumber,
            String origin,
            String destination,
            LocalDateTime departureTime,
            LocalDateTime arrivalTime,
            long durationMinutes,
            String cabinClass,
            BigDecimal fare,
            int seatsAvailable,
            String status) {
    }

    /** A destination tile on the home page: where, and the lowest fare on sale. */
    public record DestinationFare(
            String code,
            String city,
            BigDecimal fromFare,
            long flightsAvailable) {
    }

    /** What the plain English search returns: the flights, plus what we understood. */
    public record FlightSearchResult(
            String interpretedAs,
            List<FlightOption> flights) {
    }

    /**
     * The model's reading of a sentence like "cheapest morning flight to Goa next Friday".
     * <p>
     * This is a structured output type, so every field is something the model has to fill in.
     * Nullable fields are deliberate: a passenger who did not mention a cabin should not have
     * one invented for them, and null is how "they did not say" reaches the query.
     */
    public record SearchIntent(
            String originCity,
            String destinationCity,
            String departureDate,
            String cabinClass,
            String timeOfDay,
            String sortBy) {
    }
}
