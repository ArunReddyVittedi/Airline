package com.telusko.airline.repository;

import com.telusko.airline.model.Airport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AirportRepository extends JpaRepository<Airport, String> {

    /**
     * Passengers and the model both say "Mumbai" as often as "BOM", so a city lookup is not
     * optional. Case insensitive because nothing guarantees the casing of model output.
     */
    @Query("select a from Airport a where lower(a.city) = lower(?1)")
    Optional<Airport> findByCityIgnoreCase(String city);
}
