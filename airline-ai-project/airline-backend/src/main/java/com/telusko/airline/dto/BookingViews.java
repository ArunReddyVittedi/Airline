package com.telusko.airline.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class BookingViews {

    private BookingViews() {
    }

    public record BookingView(
            String pnr,
            String passengerName,
            String flightNumber,
            String origin,
            String destination,
            LocalDateTime departureTime,
            String seatNumber,
            String cabinClass,
            BigDecimal amountPaid,
            String bookingStatus,
            String flightStatus,
            String rebookedToPnr) {
    }

    /**
     * The answer to "can I get my money back", worked out in Java.
     * <p>
     * The refund amount is never left to the model. A language model asked to apply a fee
     * table will get it right most of the time, and the times it does not are a passenger
     * being quoted a refund the airline will not honour. The model's job is to explain this
     * record, not to produce it.
     */
    public record RefundQuote(
            String pnr,
            boolean refundable,
            BigDecimal amountPaid,
            BigDecimal cancellationFee,
            BigDecimal refundAmount,
            long hoursToDeparture,
            String reason) {
    }

    public record SeatMapRow(int row, List<String> availableSeats) {
    }
}
