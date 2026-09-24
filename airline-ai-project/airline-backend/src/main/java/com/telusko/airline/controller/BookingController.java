package com.telusko.airline.controller;

import com.telusko.airline.dto.ApiDtos.BookRequest;
import com.telusko.airline.dto.BookingViews.BookingView;
import com.telusko.airline.dto.BookingViews.RefundQuote;
import com.telusko.airline.security.CurrentUser;
import com.telusko.airline.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final CurrentUser currentUser;

    public BookingController(BookingService bookingService, CurrentUser currentUser) {
        this.bookingService = bookingService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<BookingView> myBookings() {
        return bookingService.myBookings(currentUser.requireEmail());
    }

    @PostMapping
    public ResponseEntity<BookingView> book(@Valid @RequestBody BookRequest request) {
        BookingView booking = bookingService.book(
                currentUser.require(), request.flightNumber(), request.seatNumber());

        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    /**
     * Throws rather than returning an empty 404.
     * <p>
     * ResponseEntity.notFound().build() sends no body, so the frontend had nothing to show
     * and fell back to "Something went wrong". Letting the exception handler answer gives
     * the passenger the actual reason.
     */
    @GetMapping("/{pnr}")
    public BookingView byPnr(@PathVariable String pnr) {
        return bookingService.myBooking(currentUser.requireEmail(), pnr)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No booking " + pnr.toUpperCase() + " on your account."));
    }

    /**
     * What a cancellation would cost, without cancelling anything.
     * <p>
     * A separate endpoint from the cancel below, so the UI can show the passenger the fee and
     * the refund before they commit. Nobody should discover a 25 percent fee after the fact.
     */
    @GetMapping("/{pnr}/refund-quote")
    public RefundQuote refundQuote(@PathVariable String pnr) {
        return bookingService.quoteRefund(currentUser.requireEmail(), pnr);
    }

    /**
     * Cancels for real. A deliberate button in the UI, never a tool the assistant can call.
     */
    @PostMapping("/{pnr}/cancel")
    public RefundQuote cancel(@PathVariable String pnr) {
        return bookingService.cancel(currentUser.requireEmail(), pnr);
    }

    /** Used by the disruption flow once the passenger accepts an alternative flight. */
    @PostMapping("/{pnr}/rebook")
    public BookingView rebook(@PathVariable String pnr, @RequestParam String flightNumber) {
        return bookingService.rebook(currentUser.requireEmail(), pnr, flightNumber);
    }
}
