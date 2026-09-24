package com.telusko.airline.controller;

import com.telusko.airline.dto.AgentViews.TripPlan;
import com.telusko.airline.dto.ApiDtos.TripPlanRequest;
import com.telusko.airline.service.TripPlanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * The trip planner: three agents in a fixed order.
 * <p>
 * The interesting comparison is with the disruption endpoint. Same idea of several agents
 * cooperating, completely different coordination, and the reason is only that these three
 * steps never change order. Cheaper, faster, and nothing is left for a model to decide.
 */
@RestController
@RequestMapping("/api/trip-planner")
public class TripPlannerController {

    private final TripPlanService tripPlanService;

    public TripPlannerController(TripPlanService tripPlanService) {
        this.tripPlanService = tripPlanService;
    }

    @PostMapping
    public TripPlan plan(@Valid @RequestBody TripPlanRequest request) {
        return tripPlanService.plan(request);
    }
}
