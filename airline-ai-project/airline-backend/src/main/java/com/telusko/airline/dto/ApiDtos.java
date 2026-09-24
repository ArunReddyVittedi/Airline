package com.telusko.airline.dto;

import jakarta.validation.constraints.NotBlank;

public final class ApiDtos {

    private ApiDtos() {
    }

    /** A free text question for the assistant. */
    public record AskRequest(@NotBlank String question) {
    }

    public record BookRequest(@NotBlank String flightNumber, String seatNumber) {
    }

    public record CreateTicketRequest(
            @NotBlank String subject,
            @NotBlank String message,
            String pnr) {
    }

    public record TripPlanRequest(
            @NotBlank String destinationCity,
            @NotBlank String originCity,
            int days,
            String interests) {
    }

    public record ApiMessage(String message) {
    }

    public record ApiError(String error, String detail) {
    }
}
