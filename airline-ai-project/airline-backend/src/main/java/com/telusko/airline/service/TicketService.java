package com.telusko.airline.service;

import com.telusko.airline.agent.TicketTriageAgent;
import com.telusko.airline.config.AiMetrics;
import com.telusko.airline.dto.ApiDtos.CreateTicketRequest;
import com.telusko.airline.dto.TicketViews.TicketView;
import com.telusko.airline.dto.TicketViews.TriageResult;
import com.telusko.airline.enums.TicketCategory;
import com.telusko.airline.enums.TicketPriority;
import com.telusko.airline.enums.TicketStatus;
import com.telusko.airline.model.AppUser;
import com.telusko.airline.model.SupportTicket;
import com.telusko.airline.repository.SupportTicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Support tickets, triaged on arrival.
 * <p>
 * The important behaviour here is what happens when triage fails. The ticket is still saved.
 * A passenger raising a support ticket has a real problem, and losing it because OpenAI is
 * having a bad afternoon would be indefensible. It lands in the queue as OTHER and NORMAL
 * with {@code aiTriaged} false, so the desk can see it was never classified rather than
 * believing a machine looked at it and decided it was ordinary.
 */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private static final String FEATURE = "ticket-triage";

    private final SupportTicketRepository tickets;
    private final TicketTriageAgent triageAgent;
    private final AiMetrics metrics;

    public TicketService(SupportTicketRepository tickets, TicketTriageAgent triageAgent, AiMetrics metrics) {
        this.tickets = tickets;
        this.triageAgent = triageAgent;
        this.metrics = metrics;
    }

    @Transactional
    public TicketView create(AppUser raisedBy, CreateTicketRequest request) {
        SupportTicket ticket = new SupportTicket(
                raisedBy, request.subject(), request.message(), blankToNull(request.pnr()));

        applyTriage(ticket);

        return toView(tickets.save(ticket));
    }

    @Transactional(readOnly = true)
    public List<TicketView> myTickets(String email) {
        return tickets.findByRaisedByEmailOrderByCreatedAtDesc(email).stream()
                .map(TicketService::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketView> openQueue() {
        return tickets.findByStatusOrderByPriorityDescCreatedAtAsc(TicketStatus.OPEN).stream()
                .map(TicketService::toView)
                .toList();
    }

    @Transactional
    public TicketView updateStatus(Long id, TicketStatus status) {
        SupportTicket ticket = tickets.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No ticket " + id));
        ticket.setStatus(status);
        return toView(tickets.save(ticket));
    }

    /**
     * Classifies the ticket, or leaves it honestly unclassified.
     * <p>
     * The subject and body are sent together because a subject on its own is usually too
     * short to classify. "Urgent" as a subject means nothing; "Urgent" plus a body about a
     * wheelchair booking is SPECIAL_ASSISTANCE and URGENT.
     */
    private void applyTriage(SupportTicket ticket) {
        String text = """
                Subject: %s

                %s
                """.formatted(ticket.getSubject(), ticket.getMessage());

        try {
            TriageResult result = metrics.record(FEATURE, () -> triageAgent.triage(text));

            ticket.setCategory(parseCategory(result.category()));
            ticket.setPriority(parsePriority(result.priority()));
            ticket.setTriageReason(result.reason());
            ticket.setAiTriaged(true);

            // A ticket the agent itself says needs a person should not sit at NORMAL behind
            // twenty ordinary ones. The agent is allowed to escalate, never to de-escalate.
            if (result.needsHumanAgent() && ticket.getPriority() == TicketPriority.NORMAL) {
                ticket.setPriority(TicketPriority.HIGH);
            }

        } catch (RuntimeException ex) {
            metrics.recordDegraded(FEATURE, ex.getClass().getSimpleName());
            log.warn("Triage failed, filing the ticket unclassified: {}", ex.getMessage());

            ticket.setTriageReason("Automatic triage was unavailable. Needs manual review.");
            ticket.setAiTriaged(false);
        }
    }

    /**
     * The prompt lists the allowed values, and a model still occasionally returns something
     * else. Falling back to OTHER keeps the ticket rather than losing it to an enum error.
     */
    private static TicketCategory parseCategory(String value) {
        try {
            return TicketCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            return TicketCategory.OTHER;
        }
    }

    private static TicketPriority parsePriority(String value) {
        try {
            return TicketPriority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            return TicketPriority.NORMAL;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private static TicketView toView(SupportTicket t) {
        return new TicketView(
                t.getId(), t.getSubject(), t.getMessage(), t.getPnr(),
                t.getCategory().name(), t.getPriority().name(), t.getStatus().name(),
                t.getTriageReason(), t.isAiTriaged(), t.getCreatedAt());
    }
}
