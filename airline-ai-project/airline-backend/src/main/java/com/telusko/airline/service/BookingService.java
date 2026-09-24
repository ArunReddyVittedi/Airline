package com.telusko.airline.service;

import com.telusko.airline.dto.BookingViews.BookingView;
import com.telusko.airline.dto.BookingViews.RefundQuote;
import com.telusko.airline.enums.BookingStatus;
import com.telusko.airline.enums.FlightStatus;
import com.telusko.airline.model.AppUser;
import com.telusko.airline.model.Booking;
import com.telusko.airline.model.Flight;
import com.telusko.airline.repository.BookingRepository;
import com.telusko.airline.repository.FlightRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Bookings, cancellations and the refund rules. Still no AI.
 * <p>
 * The refund calculation living here rather than in a prompt is the most important decision
 * in this class. A language model asked to apply a fee table gets it right most of the time,
 * and the times it does not are a passenger being quoted a refund the airline will not pay.
 * The model's job is to explain a {@link RefundQuote}, never to produce one.
 */
@Service
public class BookingService {

    private static final String PNR_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Cancel more than a day out and it is free. Inside that window the airline charges. */
    private static final long FREE_CANCELLATION_HOURS = 24;
    private static final BigDecimal LATE_CANCELLATION_RATE = new BigDecimal("0.25");

    private final BookingRepository bookings;
    private final FlightRepository flights;

    public BookingService(BookingRepository bookings, FlightRepository flights) {
        this.bookings = bookings;
        this.flights = flights;
    }

    @Transactional
    public BookingView book(AppUser passenger, String flightNumber, String requestedSeat) {
        Flight flight = flights.findByFlightNumber(normalise(flightNumber))
                .orElseThrow(() -> new IllegalArgumentException("No flight " + flightNumber));

        if (flight.getStatus() == FlightStatus.CANCELLED) {
            throw new IllegalStateException("Flight " + flight.getFlightNumber() + " is cancelled");
        }
        // A flight that has gone cannot be sold. Without this a passenger could buy a seat on
        // a plane that left days ago, and only discover it when the refund quote told them
        // the fare was not refundable because the flight had already departed.
        if (flight.getDepartureTime().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException(
                    "Flight " + flight.getFlightNumber() + " has already departed");
        }
        if (flight.getSeatsAvailable() <= 0) {
            throw new IllegalStateException("Flight " + flight.getFlightNumber() + " is full");
        }

        String seat = (requestedSeat == null || requestedSeat.isBlank())
                ? autoAssignSeat(flight)
                : requestedSeat.trim().toUpperCase();

        Booking booking = new Booking(newPnr(), passenger, flight, seat, flight.getFare());

        // Decrementing here, inside the same transaction as the insert, is what stops two
        // passengers taking the last seat. Recounting bookings on every read would be
        // correct but far slower, and a seat count is read on every single search.
        flight.setSeatsAvailable(flight.getSeatsAvailable() - 1);
        flights.save(flight);

        return toView(bookings.save(booking));
    }

    @Transactional(readOnly = true)
    public List<BookingView> myBookings(String email) {
        return bookings.findByPassengerEmailOrderByBookedAtDesc(email).stream()
                .map(BookingService::toView)
                .toList();
    }

    /**
     * A booking by PNR, but only if it belongs to the caller.
     * <p>
     * A PNR is six characters, which is guessable. Looking one up by PNR alone would let any
     * signed in passenger read somebody else's itinerary just by asking the chatbot nicely,
     * so the ownership check sits in the query rather than in an if statement a tool might
     * one day forget.
     */
    @Transactional(readOnly = true)
    public Optional<BookingView> myBooking(String email, String pnr) {
        return bookings.findByPnrAndPassengerEmail(normalisePnr(pnr), email).map(BookingService::toView);
    }

    @Transactional(readOnly = true)
    public Optional<Booking> myBookingEntity(String email, String pnr) {
        return bookings.findByPnrAndPassengerEmail(normalisePnr(pnr), email);
    }

    /**
     * Works out what a passenger would actually get back, and why.
     * <p>
     * Every branch returns a reason, because "not refundable" with no explanation is the
     * fastest way to turn a support chat into a phone call.
     */
    @Transactional(readOnly = true)
    public RefundQuote quoteRefund(String email, String pnr) {
        Booking booking = bookings.findByPnrAndPassengerEmail(normalisePnr(pnr), email)
                .orElseThrow(() -> new IllegalArgumentException("No booking " + pnr + " for this passenger"));

        BigDecimal paid = booking.getAmountPaid();
        long hours = Duration.between(LocalDateTime.now(), booking.getFlight().getDepartureTime()).toHours();

        if (booking.getStatus() == BookingStatus.REFUNDED) {
            return quote(booking, paid, paid, BigDecimal.ZERO, hours,
                    "This booking has already been refunded.");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return quote(booking, paid, paid, BigDecimal.ZERO, hours,
                    "This booking was cancelled without a refund.");
        }

        // The airline cancelled, not the passenger. A fee here would be indefensible, and
        // this branch has to come before the time check: a cancelled flight in two hours is
        // still a full refund.
        if (booking.getFlight().getStatus() == FlightStatus.CANCELLED) {
            return quote(booking, paid, BigDecimal.ZERO, paid, hours,
                    "The airline cancelled this flight, so the fare is refunded in full.");
        }

        if (hours < 0) {
            return quote(booking, paid, paid, BigDecimal.ZERO, hours,
                    "The flight has already departed, so the fare is not refundable.");
        }
        if (hours >= FREE_CANCELLATION_HOURS) {
            return quote(booking, paid, BigDecimal.ZERO, paid, hours,
                    "Cancelled more than 24 hours before departure, so there is no fee.");
        }

        BigDecimal fee = paid.multiply(LATE_CANCELLATION_RATE).setScale(2, RoundingMode.HALF_UP);
        return quote(booking, paid, fee, paid.subtract(fee), hours,
                "Cancelled within 24 hours of departure, so a 25 percent fee applies.");
    }

    @Transactional
    public RefundQuote cancel(String email, String pnr) {
        RefundQuote quote = quoteRefund(email, pnr);

        Booking booking = bookings.findByPnrAndPassengerEmail(normalisePnr(pnr), email)
                .orElseThrow(() -> new IllegalArgumentException("No booking " + pnr));

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.REFUNDED) {
            return quote;
        }

        booking.setStatus(quote.refundAmount().signum() > 0 ? BookingStatus.REFUNDED : BookingStatus.CANCELLED);
        bookings.save(booking);

        // The seat goes back on sale. Skipping this is how an airline ends up flying empty
        // seats it believes are sold.
        Flight flight = booking.getFlight();
        flight.setSeatsAvailable(Math.min(flight.getTotalSeats(), flight.getSeatsAvailable() + 1));
        flights.save(flight);

        return quote;
    }

    /**
     * Moves a passenger to another flight, keeping a link to the booking they came from.
     * <p>
     * Used by the disruption agent. The old booking is marked REBOOKED rather than deleted,
     * so a passenger asking "what happened to my original flight" still gets an answer.
     */
    @Transactional
    public BookingView rebook(String email, String pnr, String newFlightNumber) {
        Booking original = bookings.findByPnrAndPassengerEmail(normalisePnr(pnr), email)
                .orElseThrow(() -> new IllegalArgumentException("No booking " + pnr));

        Flight target = flights.findByFlightNumber(normalise(newFlightNumber))
                .orElseThrow(() -> new IllegalArgumentException("No flight " + newFlightNumber));

        if (target.getSeatsAvailable() <= 0) {
            throw new IllegalStateException("Flight " + target.getFlightNumber() + " has no seats left");
        }

        Booking moved = new Booking(newPnr(), original.getPassenger(), target,
                autoAssignSeat(target), original.getAmountPaid());
        moved = bookings.save(moved);

        original.setStatus(BookingStatus.REBOOKED);
        original.setRebookedToPnr(moved.getPnr());
        bookings.save(original);

        target.setSeatsAvailable(target.getSeatsAvailable() - 1);
        flights.save(target);

        return toView(moved);
    }

    /**
     * Six characters, no vowels and no zero or one.
     * <p>
     * Vowels are excluded so a generated PNR cannot spell something unfortunate, and 0, 1,
     * I and O are excluded because passengers read these out over the phone. Retried on
     * collision rather than trusted, since 32^6 is large but not unique by decree.
     */
    private String newPnr() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder pnr = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                pnr.append(PNR_ALPHABET.charAt(RANDOM.nextInt(PNR_ALPHABET.length())));
            }
            if (!bookings.existsByPnr(pnr.toString())) {
                return pnr.toString();
            }
        }
        throw new IllegalStateException("Could not generate a unique PNR");
    }

    /** Simple and predictable: fill from the front. Real seat maps are a different project. */
    private String autoAssignSeat(Flight flight) {
        int taken = flight.getTotalSeats() - flight.getSeatsAvailable();
        int row = (taken / 6) + 1;
        char letter = (char) ('A' + (taken % 6));
        return row + String.valueOf(letter);
    }

    private static RefundQuote quote(Booking booking, BigDecimal paid, BigDecimal fee,
                                     BigDecimal refund, long hours, String reason) {
        return new RefundQuote(booking.getPnr(), refund.signum() > 0, paid, fee, refund,
                Math.max(hours, 0), reason);
    }

    private static String normalise(String flightNumber) {
        return flightNumber == null ? "" : flightNumber.replaceAll("\\s+", "").toUpperCase();
    }

    private static String normalisePnr(String pnr) {
        return pnr == null ? "" : pnr.replaceAll("\\s+", "").toUpperCase();
    }

    private static BookingView toView(Booking b) {
        Flight f = b.getFlight();
        return new BookingView(
                b.getPnr(),
                b.getPassenger().getFullName(),
                f.getFlightNumber(),
                f.getOrigin().getCode(),
                f.getDestination().getCode(),
                f.getDepartureTime(),
                b.getSeatNumber(),
                f.getCabinClass().name(),
                b.getAmountPaid(),
                b.getStatus().name(),
                f.getStatus().name(),
                b.getRebookedToPnr());
    }
}
