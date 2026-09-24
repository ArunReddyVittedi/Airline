package com.telusko.airline.repository;

import com.telusko.airline.enums.TicketCategory;
import com.telusko.airline.enums.TicketPriority;
import com.telusko.airline.enums.TicketStatus;
import com.telusko.airline.model.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByRaisedByEmailOrderByCreatedAtDesc(String email);

    List<SupportTicket> findByStatusOrderByPriorityDescCreatedAtAsc(TicketStatus status);

    long countByCategory(TicketCategory category);

    long countByPriority(TicketPriority priority);

    /** How much of the queue the triage agent actually managed to classify. */
    long countByAiTriagedFalse();

    @Query("""
            select t.category, t.priority, count(t)
            from SupportTicket t
            group by t.category, t.priority
            order by count(t) desc
            """)
    List<Object[]> queueBreakdown();
}
