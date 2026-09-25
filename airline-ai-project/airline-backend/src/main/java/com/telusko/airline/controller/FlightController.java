package com.telusko.airline.controller;

import com.telusko.airline.dto.ApiDtos.AskRequest;
import com.telusko.airline.dto.FlightViews.DestinationFare;
import com.telusko.airline.dto.FlightViews.FlightOption;
import com.telusko.airline.dto.FlightViews.FlightSearchResult;
import com.telusko.airline.enums.CabinClass;
import com.telusko.airline.service.FlightService;
import com.telusko.airline.service.SmartSearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Flight browsing. Open to everyone, signed in or not, because nobody should need an account
 * to find out whether a flight exists.
 */
@RestController
@RequestMapping("/api/flights")
public class FlightController {

    private final FlightService flightService;
    private final SmartSearchService smartSearchService;

    public FlightController(FlightService flightService, SmartSearchService smartSearchService) {
        this.flightService = flightService;
        this.smartSearchService = smartSearchService;
    }

    /** The ordinary search, with the parameters spelled out. No AI involved. */
    @GetMapping
    public List<FlightOption> search(@RequestParam String origin,
                                     @RequestParam String destination,
                                     @RequestParam String date,
                                     @RequestParam(required = false) String cabin,
                                     @RequestParam(required = false) String timeOfDay) {

        return flightService.search(origin, destination, LocalDate.parse(date), parseCabin(cabin), timeOfDay);
    }

    /**
     * Runs the same database search after converting a natural-language request into
     * structured search criteria.
     */
    @PostMapping("/search/natural")
    public FlightSearchResult naturalSearch(@Valid @RequestBody AskRequest request) {
        return smartSearchService.search(request.question());
    }

    @GetMapping("/{flightNumber}")
    public FlightOption byNumber(@PathVariable String flightNumber) {
        return flightService.byNumber(flightNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No flight " + flightNumber.toUpperCase() + "."));
    }

    /**
     * Destinations you can reach from one city, cheapest first.
     * <p>
     * Feeds the home page. Public, because "where can I go and what does it cost" is the
     * question a visitor asks before they have any intention of creating an account.
     */
    @GetMapping("/destinations")
    public List<DestinationFare> destinations(
            @RequestParam(defaultValue = "BOM") String origin) {
        return flightService.popularDestinations(origin);
    }

    /** Every cancelled flight. Used by the UI to offer the disruption flow. */
    @GetMapping("/disrupted")
    public List<FlightOption> disrupted() {
        return flightService.disrupted();
    }

    private static CabinClass parseCabin(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("ANY")) {
            return null;
        }
        try {
            return CabinClass.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
