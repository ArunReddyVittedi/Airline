package com.telusko.airline.controller;

import com.telusko.airline.dto.AgentViews.DisruptionOutcome;
import com.telusko.airline.security.CurrentUser;
import com.telusko.airline.service.DisruptionService;
import org.springframework.web.bind.annotation.*;

/**
 * The multi agent flow, and the endpoint worth demonstrating first.
 * <p>
 * One POST, and behind it a supervisor decides which of four specialists to consult, each one
 * reading what the last one wrote. The response carries the trace, so what came back is not
 * a paragraph you have to trust but a run you can follow.
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
     * Handles one booking.
     * <p>
     * The PNR is the only input. Everything else, including whether there is any disruption
     * at all, is for the agents to establish. Run it against a booking on a healthy flight
     * as well: the supervisor should consult the situation agent, find nothing wrong, and
     * stop without spending calls on rebooking or compensation.
     */
    @PostMapping("/{pnr}")
    public DisruptionOutcome handle(@PathVariable String pnr) {
        return disruptionService.handle(currentUser.requireEmail(), pnr);
    }
}
