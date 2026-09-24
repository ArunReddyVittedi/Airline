package com.telusko.airline.controller;

import com.telusko.airline.dto.ApiDtos.CreateTicketRequest;
import com.telusko.airline.dto.TicketViews.TicketView;
import com.telusko.airline.security.CurrentUser;
import com.telusko.airline.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final CurrentUser currentUser;

    public TicketController(TicketService ticketService, CurrentUser currentUser) {
        this.ticketService = ticketService;
        this.currentUser = currentUser;
    }

    /**
     * Raises a ticket and triages it on the way in.
     * <p>
     * The response includes the category, the priority and the reason the agent gave, which
     * is unusual for a create endpoint and deliberate. A passenger who can see that their
     * wheelchair request was filed as SPECIAL_ASSISTANCE and URGENT stops worrying about
     * whether anybody read it.
     */
    @PostMapping
    public ResponseEntity<TicketView> create(@Valid @RequestBody CreateTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketService.create(currentUser.require(), request));
    }

    @GetMapping
    public List<TicketView> myTickets() {
        return ticketService.myTickets(currentUser.requireEmail());
    }
}
