package com.telusko.airline.repository;

import com.telusko.airline.enums.CabinClass;
import com.telusko.airline.enums.FlightStatus;
import com.telusko.airline.model.Flight;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FlightRepository extends JpaRepository<Flight, Long> {

    Optional<Flight> findByFlightNumber(String flightNumber);

    /**
     * The one search every flight tool goes through.
     * <p>
     * Cabin class is nullable so "any cabin" is a real option rather than three separate
     * queries, and cancelled flights are excluded here rather than in Java so paging and
     * ordering apply to bookable flights only.
     */
    @Query("""
            select f from Flight f
            where f.origin.code = :origin
              and f.destination.code = :destination
              and f.departureTime between :from and :to
              and f.seatsAvailable > 0
              and f.status <> 'CANCELLED'
              and (:cabin is null or f.cabinClass = :cabin)
            order by f.fare asc, f.departureTime asc
            """)
    List<Flight> search(@Param("origin") String origin,
                        @Param("destination") String destination,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to,
                        @Param("cabin") CabinClass cabin,
                        Pageable pageable);

    /**
     * Alternatives for a disrupted passenger: same route, still has seats, departing after
     * the flight they lost. Ordered by departure rather than fare, because somebody stuck at
     * an airport wants the next flight out, not the cheapest one tomorrow.
     */
    @Query("""
            select f from Flight f
            where f.origin.code = :origin
              and f.destination.code = :destination
              and f.departureTime > :after
              and f.seatsAvailable > 0
              and f.status = 'SCHEDULED'
              and f.id <> :excludeFlightId
            order by f.departureTime asc
            """)
    List<Flight> findAlternatives(@Param("origin") String origin,
                                  @Param("destination") String destination,
                                  @Param("after") LocalDateTime after,
                                  @Param("excludeFlightId") Long excludeFlightId,
                                  Pageable pageable);

    /**
     * The cheapest bookable fare to each destination from one origin, over a date window.
     * <p>
     * This is what a "from 2,900 rupees" tile on a home page is: not a marketing number but
     * the lowest fare actually on sale. Grouping in SQL rather than pulling every flight and
     * reducing in Java matters here, because the alternative is loading three weeks of the
     * whole schedule to display eight numbers.
     */
    @Query("""
            select f.destination.code, f.destination.city, min(f.fare), count(f)
            from Flight f
            where f.origin.code = :origin
              and f.departureTime between :from and :to
              and f.seatsAvailable > 0
              and f.status = 'SCHEDULED'
            group by f.destination.code, f.destination.city
            order by min(f.fare) asc
            """)
    List<Object[]> cheapestFarePerDestination(@Param("origin") String origin,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    List<Flight> findByStatus(FlightStatus status);

    long countByStatus(FlightStatus status);
}
