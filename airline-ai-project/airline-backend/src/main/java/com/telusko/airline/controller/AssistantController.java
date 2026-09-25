package com.telusko.airline.controller;

import com.telusko.airline.dto.AgentViews.AssistantReply;
import com.telusko.airline.dto.ApiDtos.AskRequest;
import com.telusko.airline.security.CurrentUser;
import com.telusko.airline.service.AssistantService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * The chat window.
 * <p>
 * Note that neither endpoint takes a memory id from the caller. It comes from the signed in
 * user, which means a passenger cannot ask to continue somebody else's conversation by
 * passing their email as a parameter. Chat history is as private as a booking.
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final CurrentUser currentUser;

    public AssistantController(AssistantService assistantService, CurrentUser currentUser) {
        this.assistantService = assistantService;
        this.currentUser = currentUser;
    }

    /** Returns a complete answer with trace steps and retrieved sources. */
    @PostMapping("/ask")
    public AssistantReply ask(@Valid @RequestBody AskRequest request) {
        return assistantService.ask(currentUser.requireEmail(), request.question());
    }

    /**
     * The same conversation, streamed as server sent events.
     * <p>
     * TEXT_EVENT_STREAM rather than JSON, because the browser needs to render each token as
     * it arrives. Streaming is about how fast the answer feels, not what it costs.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@Valid @RequestBody AskRequest request) {
        return assistantService.askStreaming(currentUser.requireEmail(), request.question());
    }
}
