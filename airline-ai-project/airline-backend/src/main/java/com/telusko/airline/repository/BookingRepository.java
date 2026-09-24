package com.telusko.airline.repository;

import com.telusko.airline.enums.BookingStatus;
import com.telusko.airline.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByPnr(String pnr);

    /**
     * PNR plus owner, used by every tool the assistant can call.
     * <p>
     * A PNR is six characters and guessable, so looking one up by PNR alone would let any
     * signed in passenger read somebody else's itinerary just by asking the chatbot. The
     * ownership check belongs in the query, not in an if statement a tool might forget.
     */
    @Query("select b from Booking b where b.pnr = :pnr and b.passenger.email = :email")
    Optional<Booking> findByPnrAndPassengerEmail(@Param("pnr") String pnr, @Param("email") String email);

    List<Booking> findByPassengerEmailOrderByBookedAtDesc(String email);

    List<Booking> findByFlightIdAndStatus(Long flightId, BookingStatus status);

    boolean existsByPnr(String pnr);

    // ---------- Numbers for the admin ops assistant ----------

    /** Money that actually landed. Cancelled and refunded bookings are not revenue. */
    @Query("""
            select coalesce(sum(b.amountPaid), 0)
            from Booking b
            where b.bookedAt >= :since
              and b.status not in (com.telusko.airline.enums.BookingStatus.CANCELLED,
                                   com.telusko.airline.enums.BookingStatus.REFUNDED)
            """)
    BigDecimal revenueSince(@Param("since") LocalDateTime since);

    long countByBookedAtAfter(LocalDateTime since);

    @Query("""
            select b.flight.origin.code, b.flight.destination.code, count(b)
            from Booking b
            where b.bookedAt >= :since
            group by b.flight.origin.code, b.flight.destination.code
            order by count(b) desc
            """)
    List<Object[]> busiestRoutesSince(@Param("since") LocalDateTime since);
}
