package com.telusko.airline.model;

import com.telusko.airline.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The six character reference an airline calls a PNR. This is the handle the passenger
     * gives the assistant, so every booking tool looks a booking up by this and never by id.
     */
    @Column(nullable = false, unique = true, length = 6)
    private String pnr;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AppUser passenger;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Flight flight;

    @Column(nullable = false)
    private String seatNumber;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPaid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(nullable = false)
    private LocalDateTime bookedAt = LocalDateTime.now();

    /**
     * Set when the disruption agent moves a passenger to another flight. Keeping the old PNR
     * means the passenger can still ask "what happened to my original booking" and get an
     * answer, instead of the row silently changing under them.
     */
    private String rebookedToPnr;

    public Booking(String pnr, AppUser passenger, Flight flight, String seatNumber, BigDecimal amountPaid) {
        this.pnr = pnr;
        this.passenger = passenger;
        this.flight = flight;
        this.seatNumber = seatNumber;
        this.amountPaid = amountPaid;
    }
}
