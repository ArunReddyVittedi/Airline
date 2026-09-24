package com.telusko.airline.model;

import com.telusko.airline.enums.CabinClass;
import com.telusko.airline.enums.FlightStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "flight")
@Getter
@Setter
@NoArgsConstructor
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** What the passenger reads out on the phone, for example TL401. */
    @Column(nullable = false, unique = true)
    private String flightNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Airport origin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Airport destination;

    @Column(nullable = false)
    private LocalDateTime departureTime;

    @Column(nullable = false)
    private LocalDateTime arrivalTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CabinClass cabinClass = CabinClass.ECONOMY;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fare;

    @Column(nullable = false)
    private int totalSeats;

    /**
     * Kept as a column rather than counted from bookings on every read. A seat count is
     * asked for on every search, and the assistant asks several times in one conversation.
     * BookingService is the only writer, inside the same transaction as the booking.
     */
    @Column(nullable = false)
    private int seatsAvailable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlightStatus status = FlightStatus.SCHEDULED;

    /** Minutes late. Only meaningful when status is DELAYED, and zero otherwise. */
    @Column(nullable = false)
    private int delayMinutes = 0;

    public Flight(String flightNumber, Airport origin, Airport destination,
                  LocalDateTime departureTime, LocalDateTime arrivalTime,
                  CabinClass cabinClass, BigDecimal fare, int totalSeats) {
        this.flightNumber = flightNumber;
        this.origin = origin;
        this.destination = destination;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.cabinClass = cabinClass;
        this.fare = fare;
        this.totalSeats = totalSeats;
        this.seatsAvailable = totalSeats;
    }

    public long durationMinutes() {
        return Duration.between(departureTime, arrivalTime).toMinutes();
    }
}
