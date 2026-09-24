package com.telusko.airline.model;

import com.telusko.airline.enums.TicketCategory;
import com.telusko.airline.enums.TicketPriority;
import com.telusko.airline.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "support_ticket")
@Getter
@Setter
@NoArgsConstructor
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AppUser raisedBy;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String message;

    /** The PNR the ticket is about, when there is one. Free text tickets leave this null. */
    private String pnr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketCategory category = TicketCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketPriority priority = TicketPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.OPEN;

    /**
     * One line from the triage agent explaining why it chose that category and priority.
     * An agent that routes a ticket without saying why is impossible to correct, and the
     * desk staff need something to disagree with.
     */
    @Column(length = 1000)
    private String triageReason;

    /**
     * False when triage failed and the ticket was filed with the defaults above. Without
     * this flag a queue full of NORMAL / OTHER tickets looks like real classification.
     */
    @Column(nullable = false)
    private boolean aiTriaged = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public SupportTicket(AppUser raisedBy, String subject, String message, String pnr) {
        this.raisedBy = raisedBy;
        this.subject = subject;
        this.message = message;
        this.pnr = pnr;
    }
}
