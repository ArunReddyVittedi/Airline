package com.telusko.airline.dto;

import java.time.LocalDateTime;

public final class TicketViews {

    private TicketViews() {
    }

    /**
     * What the triage agent produces for an incoming ticket.
     * <p>
     * {@code reason} is not optional. An agent that files a ticket as URGENT without saying
     * why cannot be corrected, and the desk staff need something they can disagree with.
     */
    public record TriageResult(
            String category,
            String priority,
            String reason,
            boolean needsHumanAgent) {
    }

    public record TicketView(
            Long id,
            String subject,
            String message,
            String pnr,
            String category,
            String priority,
            String status,
            String triageReason,
            boolean aiTriaged,
            LocalDateTime createdAt) {
    }
}
