package com.telusko.airline.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Flight response records grouped by domain. Returning records instead of entities prevents
 * lazy Hibernate associations from entering API and tool serialization.
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
