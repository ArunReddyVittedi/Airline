package com.telusko.airline.service;

import com.telusko.airline.dto.FlightViews.DestinationFare;
import com.telusko.airline.dto.FlightViews.FlightOption;
import com.telusko.airline.enums.CabinClass;
import com.telusko.airline.enums.FlightStatus;
import com.telusko.airline.model.Airport;
import com.telusko.airline.model.Flight;
import com.telusko.airline.repository.AirportRepository;
import com.telusko.airline.repository.FlightRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Everything about finding flights. No AI in this file.
 * <p>
 * That separation is deliberate and it is the point of the whole design. The tools and agents
 * are a thin layer over this service, so a flight search behaves identically whether it came
 * from the search box or from a sentence typed at the chatbot. It also means these rules can
 * be tested without spending a single token.
 */
@Service
public class FlightService {

    /** Never hand a model an unbounded list. It has to fit in a prompt and be paid for. */
    private static final int MAX_RESULTS = 8;

    private final FlightRepository flights;
    private final AirportRepository airports;

    public FlightService(FlightRepository flights, AirportRepository airports) {
        this.flights = flights;
        this.airports = airports;
    }

    /**
     * Resolves whatever the caller said into an airport.
     * <p>
     * Passengers and models both write "Mumbai" as often as "BOM", so accepting only one of
     * them would make half the questions fail.
     * <p>
     * This started out branching on length: three characters meant a code, anything longer
     * meant a city. That is wrong, and "Goa" is exactly the case that proves it. Three
     * letters, plainly a city, and its airport code is GOI. The length version looked up an
     * airport called GOA, found nothing, and every search for Goa came back empty while the
     * same search for GOI worked, which is a maddening thing to debug.
     * <p>
     * Trying the code first and falling back to the city is both shorter and always right.
     * There is no city whose name collides with a different airport's code in this data set,
     * and if one ever appears the code is the more specific of the two and should win.
     */
    @Transactional(readOnly = true)
    public Optional<Airport> resolveAirport(String cityOrCode) {
        if (cityOrCode == null || cityOrCode.isBlank()) {
            return Optional.empty();
        }
        String value = cityOrCode.trim();

        Optional<Airport> byCode = airports.findById(value.toUpperCase());
        return byCode.isPresent() ? byCode : airports.findByCityIgnoreCase(value);
    }

    /**
     * The one search that every caller goes through.
     *
     * @param date      the day of departure. A whole day, not an instant, because that is how
     *                  people book: nobody searches for flights at 09:14.
     * @param cabin     null means any cabin. Not an overload, so "they did not say" is a
     *                  value the query can handle rather than a branch every caller repeats.
     * @param timeOfDay morning, afternoon, evening or null. Filtered here rather than in SQL
     *                  because it is a presentation idea, not a column.
     */
    @Transactional(readOnly = true)
    public List<FlightOption> search(String originCityOrCode, String destinationCityOrCode,
                                     LocalDate date, CabinClass cabin, String timeOfDay) {

        Airport origin = resolveAirport(originCityOrCode).orElse(null);
        Airport destination = resolveAirport(destinationCityOrCode).orElse(null);

        if (origin == null || destination == null || date == null) {
            return List.of();
        }

        // Searching today starts from now, not from midnight. Otherwise this morning's
        // departures are still listed as bookable, and a passenger picks one before anything
        // tells them the plane has gone.
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = startOfDay.isBefore(now) ? now : startOfDay;
        LocalDateTime to = date.atTime(LocalTime.MAX);

        if (from.isAfter(to)) {
            // The date is entirely in the past.
            return List.of();
        }

        List<Flight> found = flights.search(origin.getCode(), destination.getCode(),
                from, to, cabin, PageRequest.of(0, MAX_RESULTS));

        return found.stream()
                .filter(f -> matchesTimeOfDay(f, timeOfDay))
                .map(FlightService::toOption)
                .toList();
    }

    /**
     * Later flights on the same route, for a passenger whose own flight is gone.
     * <p>
     * Ordered by departure and not by fare. Somebody standing at an airport wants the next
     * flight out, and the cheapest one is often tomorrow morning.
     */
    @Transactional(readOnly = true)
    public List<FlightOption> alternativesFor(Flight disrupted) {
        List<Flight> found = flights.findAlternatives(
                disrupted.getOrigin().getCode(),
                disrupted.getDestination().getCode(),
                disrupted.getDepartureTime().minusHours(2),
                disrupted.getId(),
                PageRequest.of(0, 5));

        return found.stream().map(FlightService::toOption).toList();
    }

    /**
     * Where you can go from here, and the lowest fare on sale to each place.
     * <p>
     * Looks three weeks ahead, which is the window the seeded schedule covers. A real airline
     * would use its whole schedule; the shape of the answer is the same either way.
     */
    @Transactional(readOnly = true)
    public List<DestinationFare> popularDestinations(String originCityOrCode) {
        Airport origin = resolveAirport(originCityOrCode).orElse(null);
        if (origin == null) {
            return List.of();
        }

        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to = from.plusDays(21);

        return flights.cheapestFarePerDestination(origin.getCode(), from, to).stream()
                .map(row -> new DestinationFare(
                        (String) row[0],
                        (String) row[1],
                        (java.math.BigDecimal) row[2],
                        (Long) row[3]))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<FlightOption> byNumber(String flightNumber) {
        return flights.findByFlightNumber(normalise(flightNumber)).map(FlightService::toOption);
    }

    @Transactional(readOnly = true)
    public Optional<Flight> entityByNumber(String flightNumber) {
        return flights.findByFlightNumber(normalise(flightNumber));
    }

    @Transactional(readOnly = true)
    public List<FlightOption> disrupted() {
        return flights.findByStatus(FlightStatus.CANCELLED).stream()
                .map(FlightService::toOption)
                .toList();
    }

    /**
     * Marks a flight as cancelled or delayed. This is the admin action that gives the
     * disruption agent something real to react to during a demo.
     */
    @Transactional
    public FlightOption updateStatus(String flightNumber, FlightStatus status, int delayMinutes) {
        Flight flight = flights.findByFlightNumber(normalise(flightNumber))
                .orElseThrow(() -> new IllegalArgumentException("No flight " + flightNumber));

        flight.setStatus(status);
        flight.setDelayMinutes(status == FlightStatus.DELAYED ? delayMinutes : 0);

        return toOption(flights.save(flight));
    }

    /** Flight numbers arrive as "tl 401" as often as "TL401", from people and models alike. */
    private static String normalise(String flightNumber) {
        return flightNumber == null ? "" : flightNumber.replaceAll("\\s+", "").toUpperCase();
    }

    private static boolean matchesTimeOfDay(Flight flight, String timeOfDay) {
        if (timeOfDay == null || timeOfDay.isBlank() || timeOfDay.equalsIgnoreCase("any")) {
            return true;
        }
        int hour = flight.getDepartureTime().getHour();

        return switch (timeOfDay.toLowerCase()) {
            case "morning" -> hour >= 5 && hour < 12;
            case "afternoon" -> hour >= 12 && hour < 17;
            case "evening" -> hour >= 17 && hour < 22;
            case "night" -> hour >= 22 || hour < 5;
            // An unrecognised value must not silently drop every flight. The model is
            // allowed to invent a word here, and losing the whole result set over it would
            // look like "no flights available" rather than a bad filter.
            default -> true;
        };
    }

    private static FlightOption toOption(Flight f) {
        return new FlightOption(
                f.getFlightNumber(),
                f.getOrigin().getCode(),
                f.getDestination().getCode(),
                f.getDepartureTime(),
                f.getArrivalTime(),
                f.durationMinutes(),
                f.getCabinClass().name(),
                f.getFare(),
                f.getSeatsAvailable(),
                f.getStatus().name());
    }
}
