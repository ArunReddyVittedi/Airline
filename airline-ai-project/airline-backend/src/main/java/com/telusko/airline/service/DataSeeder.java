package com.telusko.airline.service;

import com.telusko.airline.enums.BookingStatus;
import com.telusko.airline.enums.CabinClass;
import com.telusko.airline.enums.FlightStatus;
import com.telusko.airline.enums.Role;
import com.telusko.airline.model.Airport;
import com.telusko.airline.model.Booking;
import com.telusko.airline.model.AppUser;
import com.telusko.airline.model.Flight;
import com.telusko.airline.model.KnowledgeArticle;
import com.telusko.airline.repository.AirportRepository;
import com.telusko.airline.repository.AppUserRepository;
import com.telusko.airline.repository.BookingRepository;
import com.telusko.airline.repository.FlightRepository;
import com.telusko.airline.repository.KnowledgeArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Seeds the data required by application features on a clean database. Flight dates are
 * relative to the current date so search and disruption scenarios remain current. Each
 * section checks for existing rows to keep restarts idempotent.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AirportRepository airports;
    private final FlightRepository flights;
    private final AppUserRepository users;
    private final BookingRepository bookings;
    private final KnowledgeArticleRepository articles;
    private final KnowledgeIndexService knowledgeIndexService;
    private final BookingService bookingService;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(AirportRepository airports, FlightRepository flights, AppUserRepository users,
                      BookingRepository bookings, KnowledgeArticleRepository articles,
                      KnowledgeIndexService knowledgeIndexService, BookingService bookingService,
                      PasswordEncoder passwordEncoder) {
        this.airports = airports;
        this.flights = flights;
        this.users = users;
        this.bookings = bookings;
        this.articles = articles;
        this.knowledgeIndexService = knowledgeIndexService;
        this.bookingService = bookingService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Runs seed stages in independent repository transactions. An outer transaction would
     * allow a caught failure from {@link BookingService#book} to mark the complete seed run
     * rollback-only.
     */
    @Override
    public void run(ApplicationArguments args) {
        seedAirports();
        seedUsers();
        seedFlights();
        seedBookings();
        refreshDisruption();
        seedKnowledge();
    }

    /**
     * Maintains at least one future cancelled flight and one future delayed flight. When the
     * seeded disruption expires, a later flight is selected so the disruption workflow keeps
     * valid operational data.
     */
    private void refreshDisruption() {
        LocalDateTime now = LocalDateTime.now();

        Flight cancelled = earliestFuture(FlightStatus.CANCELLED, now);
        Flight delayed = earliestFuture(FlightStatus.DELAYED, now);

        if (cancelled == null || delayed == null) {
            // Select an afternoon or evening flight at least five days out. This preserves
            // morning search results while keeping the booking inside the refund window.
            List<Flight> candidates = bookableAfter(now.plusDays(5).toLocalDate().atStartOfDay());

            if (candidates.isEmpty()) {
                log.warn("No future flights left to disrupt. The schedule has run out: drop the "
                        + "database volume and restart to reseed.");
                return;
            }

            if (cancelled == null) {
                cancelled = candidates.get(0);
                cancelled.setStatus(FlightStatus.CANCELLED);
                flights.save(cancelled);
                log.info("Refreshed the disruption: {} is now cancelled", cancelled.getFlightNumber());
            }

            if (delayed == null && candidates.size() > 1) {
                delayed = candidates.get(1);
                delayed.setStatus(FlightStatus.DELAYED);
                delayed.setDelayMinutes(260);
                flights.save(delayed);
            }
        }

        // Ensure the cancelled flight has a passenger on every startup. This remains
        // idempotent if a prior seed run stopped after updating the flight status.
        if (cancelled != null) {
            bookDemoPassengerOnto(cancelled);
        }
    }

    private Flight earliestFuture(FlightStatus status, LocalDateTime now) {
        return flights.findByStatus(status).stream()
                .filter(f -> f.getDepartureTime().isAfter(now))
                .min(java.util.Comparator.comparing(Flight::getDepartureTime))
                .orElse(null);
    }

    private List<Flight> bookableAfter(LocalDateTime from) {
        return flights.findAll().stream()
                .filter(f -> f.getStatus() == FlightStatus.SCHEDULED)
                .filter(f -> f.getDepartureTime().isAfter(from))
                .filter(f -> f.getOrigin().getCode().equals("BOM"))
                .filter(f -> f.getDestination().getCode().equals("GOI"))
                // Preserve morning departures for the default natural-language search.
                .filter(f -> f.getDepartureTime().getHour() >= 12)
                .sorted(java.util.Comparator.comparing(Flight::getDepartureTime))
                .toList();
    }

    /**
     * Adds a seeded passenger to the cancelled flight when it has no confirmed bookings.
     * A direct repository query avoids loading lazy flight proxies outside a transaction.
     */
    private void bookDemoPassengerOnto(Flight cancelled) {
        AppUser ramesh = users.findByEmail("ramesh@example.com").orElse(null);
        if (ramesh == null) {
            return;
        }

        boolean somebodyIsOnIt =
                !bookings.findByFlightIdAndStatus(cancelled.getId(), BookingStatus.CONFIRMED).isEmpty();

        if (somebodyIsOnIt) {
            return;
        }

        bookings.save(new Booking(seedPnr(), ramesh, cancelled, "12A", cancelled.getFare()));
        log.info("Booked the demo passenger onto the cancelled {}", cancelled.getFlightNumber());
    }

    private void seedAirports() {
        if (airports.count() > 0) {
            return;
        }

        airports.saveAll(List.of(
                new Airport("BOM", "Mumbai", "Chhatrapati Shivaji Maharaj International", "India"),
                new Airport("DEL", "Delhi", "Indira Gandhi International", "India"),
                new Airport("GOI", "Goa", "Manohar International", "India"),
                new Airport("BLR", "Bengaluru", "Kempegowda International", "India"),
                new Airport("MAA", "Chennai", "Chennai International", "India"),
                new Airport("CCU", "Kolkata", "Netaji Subhas Chandra Bose International", "India"),
                new Airport("SXR", "Srinagar", "Sheikh ul-Alam International", "India"),
                new Airport("HYD", "Hyderabad", "Rajiv Gandhi International", "India")));

        log.info("Seeded {} airports", airports.count());
    }

    private void seedUsers() {
        if (users.count() > 0) {
            return;
        }

        // Shared local-development password documented in the README.
        String password = passwordEncoder.encode("telusko123");

        AppUser ramesh = new AppUser("ramesh@example.com", password, "Ramesh Kumar", Role.USER);
        ramesh.setTier("GOLD");

        AppUser priya = new AppUser("priya@example.com", password, "Priya Sharma", Role.USER);
        priya.setTier("SILVER");

        AppUser admin = new AppUser("admin@telusko.com", password, "Ops Desk", Role.ADMIN);

        users.saveAll(List.of(ramesh, priya, admin));
        log.info("Seeded {} users", users.count());
    }

    /**
     * Seeds a small fleet across supported routes for the next three weeks, including one
     * cancelled and one delayed flight for disruption handling.
     */
    private void seedFlights() {
        if (flights.count() > 0) {
            return;
        }

        Map<String, Airport> byCode = airports.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Airport::getCode, a -> a));

        LocalDate today = LocalDate.now();

        // Routes as origin, destination, and a base fare in rupees.
        List<Object[]> routes = List.of(
                new Object[]{"BOM", "GOI", 3200},
                new Object[]{"GOI", "BOM", 3400},
                new Object[]{"BOM", "DEL", 5600},
                new Object[]{"DEL", "BOM", 5800},
                new Object[]{"BOM", "BLR", 4100},
                new Object[]{"BLR", "BOM", 4200},
                new Object[]{"DEL", "SXR", 6400},
                new Object[]{"BOM", "MAA", 4600});

        // Three departures a day: morning, afternoon, evening.
        List<LocalTime> departures = List.of(
                LocalTime.of(7, 15), LocalTime.of(14, 30), LocalTime.of(20, 45));

        int flightNumber = 401;

        for (int dayOffset = 1; dayOffset <= 21; dayOffset++) {
            LocalDate date = today.plusDays(dayOffset);

            for (Object[] route : routes) {
                Airport origin = byCode.get((String) route[0]);
                Airport destination = byCode.get((String) route[1]);
                int baseFare = (int) route[2];

                for (int slot = 0; slot < departures.size(); slot++) {
                    LocalDateTime departure = date.atTime(departures.get(slot));

                    // Evening flights priced a little higher and morning a little lower, so
                    // "cheapest morning flight" is a question with a real answer rather than
                    // a tie between identical rows.
                    int fare = baseFare + (slot == 2 ? 700 : slot == 0 ? -300 : 0);

                    Flight flight = new Flight(
                            "TL" + flightNumber++,
                            origin, destination,
                            departure,
                            departure.plusMinutes(durationFor(origin.getCode(), destination.getCode())),
                            slot == 1 ? CabinClass.BUSINESS : CabinClass.ECONOMY,
                            BigDecimal.valueOf(slot == 1 ? fare * 3L : fare),
                            slot == 1 ? 12 : 60);

                    flights.save(flight);

                    // Reset so flight numbers stay four characters and readable.
                    if (flightNumber > 999) {
                        flightNumber = 401;
                    }
                }
            }
        }

        seedDisruption();
        log.info("Seeded {} flights", flights.count());
    }

    /**
     * Cancels one flight and delays another, both a few days out.
     * <p>
     * Picked from the middle of the range rather than tomorrow, so a passenger booked on
     * them is inside the refund window and the compensation agent has something interesting
     * to decide rather than the trivial "already departed" case.
     */
    private void seedDisruption() {
        LocalDateTime from = LocalDate.now().plusDays(4).atStartOfDay();
        LocalDateTime to = from.plusDays(1);

        List<Flight> candidates = flights.findAll().stream()
                .filter(f -> f.getDepartureTime().isAfter(from) && f.getDepartureTime().isBefore(to))
                .filter(f -> f.getOrigin().getCode().equals("BOM") && f.getDestination().getCode().equals("GOI"))
                .toList();

        if (candidates.isEmpty()) {
            return;
        }

        Flight cancelled = candidates.get(0);
        cancelled.setStatus(FlightStatus.CANCELLED);
        flights.save(cancelled);

        if (candidates.size() > 1) {
            Flight delayed = candidates.get(1);
            delayed.setStatus(FlightStatus.DELAYED);
            // Over four hours, which is the threshold where the policy grants a full refund
            // as well as rebooking. Chosen so the compensation agent has to read the policy
            // rather than falling into the "no compensation" branch.
            delayed.setDelayMinutes(260);
            flights.save(delayed);
        }

        log.info("Seeded disruption on {}", cancelled.getFlightNumber());
    }

    /**
     * Seeds passenger bookings, including a cancelled flight for Ramesh and a delayed flight
     * for Priya, so disruption handling is available on a clean database.
     */
    private void seedBookings() {
        if (bookings.count() > 0) {
            return;
        }

        AppUser ramesh = users.findByEmail("ramesh@example.com").orElseThrow();
        AppUser priya = users.findByEmail("priya@example.com").orElseThrow();

        flights.findByStatus(FlightStatus.CANCELLED).stream().findFirst()
                .ifPresent(cancelled -> bookOn(ramesh, cancelled));

        flights.findByStatus(FlightStatus.DELAYED).stream().findFirst()
                .ifPresent(delayed -> bookOn(priya, delayed));

        // One healthy booking each, so "my trips" is not a list of nothing but problems,
        // and so the supervisor can be shown finding no disruption at all.
        flights.findAll().stream()
                .filter(f -> f.getStatus() == FlightStatus.SCHEDULED)
                .filter(f -> f.getCabinClass() == CabinClass.ECONOMY)
                .limit(2)
                .forEach(flight -> bookOn(flight.getFare().intValue() % 2 == 0 ? ramesh : priya, flight));

        log.info("Seeded {} bookings", bookings.count());
    }

    /**
     * Books a passenger onto a flight, going around the booking rules when it has to.
     * <p>
     * A cancelled flight is refused by {@link BookingService#book}, and rightly so: nobody
     * should be able to buy a seat on a flight that is not operating. But the whole point of
     * the seed data is a passenger who was already booked when the airline cancelled, so for
     * those the row is written directly.
     * <p>
     * The status is checked rather than the exception being caught. Catching it would work
     * and would be the wrong shape, because {@code book} is transactional: by the time the
     * exception arrives the transaction is already marked rollback only, and the catch
     * cannot undo that.
     */
    private void bookOn(AppUser passenger, Flight flight) {
        if (flight.getStatus() == FlightStatus.CANCELLED || flight.getSeatsAvailable() <= 0) {
            bookings.save(new Booking(seedPnr(), passenger, flight, "12A", flight.getFare()));
            return;
        }

        bookingService.book(passenger, flight.getFlightNumber(), null);
    }

    /** Readable and obviously synthetic, so a seeded booking is recognisable in the data. */
    private String seedPnr() {
        return "SEED" + String.format("%02d", bookings.count() + 1);
    }

    /**
     * Loads the markdown files from resources and indexes them.
     * <p>
     * The policy text lives in files rather than in a Java string so that it can be edited
     * without recompiling, and so the diff on a policy change reads like a policy change.
     */
    private void seedKnowledge() {
        if (articles.count() > 0) {
            log.info("Knowledge base already present, reindexing {} articles", articles.count());
            knowledgeIndexService.reindexAll();
            return;
        }

        try {
            Resource[] files = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:knowledge/*.md");

            for (Resource file : files) {
                String slug = file.getFilename().replace(".md", "");
                String body = new String(file.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

                articles.save(new KnowledgeArticle(slug, titleOf(body, slug), topicOf(slug), body));
            }

            log.info("Seeded {} knowledge articles", articles.count());
            knowledgeIndexService.reindexAll();

        } catch (IOException ex) {
            // The app is still usable: tools, bookings and the agents all work, and only the
            // policy answers are missing. Failing startup over it would be worse.
            log.warn("Could not load the knowledge base: {}", ex.getMessage());
        }
    }

    /** The first markdown heading, which is a better title than the filename. */
    private static String titleOf(String body, String fallback) {
        for (String line : body.split("\n")) {
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        return fallback;
    }

    private static String topicOf(String slug) {
        return slug.startsWith("destination-") ? "DESTINATION" : slug.toUpperCase().replace('-', '_');
    }

    /**
     * Rough block times in minutes. Only has to be plausible, not accurate: it decides
     * arrival times in the UI and whether a "fastest flight" question has an answer.
     */
    private static long durationFor(String origin, String destination) {
        String route = origin + destination;
        return switch (route) {
            case "BOMGOI", "GOIBOM" -> 65;
            case "BOMDEL", "DELBOM" -> 135;
            case "BOMBLR", "BLRBOM" -> 95;
            case "DELSXR" -> 80;
            case "BOMMAA" -> 110;
            default -> 120;
        };
    }
}
