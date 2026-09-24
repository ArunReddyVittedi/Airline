package com.telusko.airline.enums;

/**
 * Where a support ticket should land. The triage agent picks one of these, which is the
 * whole point of classifying: a baggage claim and a refund request go to different desks.
 */
public enum TicketCategory {
    BAGGAGE,
    REFUND,
    BOOKING_CHANGE,
    FLIGHT_DISRUPTION,
    SPECIAL_ASSISTANCE,
    FEEDBACK,
    OTHER
}
