package com.telusko.airline.controller;

import com.telusko.airline.dto.AgentViews.DisruptionOutcome;
import com.telusko.airline.security.CurrentUser;
import com.telusko.airline.service.DisruptionService;
import org.springframework.web.bind.annotation.*;

/**
 * Multi-agent disruption endpoint. A supervisor selects from four specialists and returns
 * their contributions with the final passenger response.
 */
@RestController
@RequestMapping("/api/disruption")
public class DisruptionController {

    private final DisruptionService disruptionService;
    private final CurrentUser currentUser;

    public DisruptionController(DisruptionService disruptionService, CurrentUser currentUser) {
        this.disruptionService = disruptionService;
        this.currentUser = currentUser;
    }

    /**
     * Handles one booking. The agents determine whether a disruption exists and stop after
     * situation assessment when the flight is operating normally.
     */
    @PostMapping("/{pnr}")
    public DisruptionOutcome handle(@PathVariable String pnr) {
        return disruptionService.handle(currentUser.requireEmail(), pnr);
    }
}
