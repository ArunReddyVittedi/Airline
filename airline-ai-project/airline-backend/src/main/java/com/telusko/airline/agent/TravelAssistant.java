package com.telusko.airline.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * Passenger-facing assistant with policy retrieval, local and MCP tools, per-passenger
 * memory, and input/output guardrails.
 * <p>
 * This interface is built in {@code TravelAssistantConfig} instead of using
 * {@code @AiService}. {@code @InputGuardrails} and {@code @OutputGuardrails} take a class, and
 * LangChain4j instantiates it by reflection through a no argument constructor. Spring is not
 * consulted. The output guardrail needs the flight repository to check whether a flight
 * number is real, so a no argument constructor cannot give it what it needs, and the
 * annotation fails at runtime with {@code NoSuchMethodException: <init>()}. Building the
 * service with {@code AiServices.builder()} accepts guardrail <em>instances</em> that
 * Spring has already injected.
 */
public interface TravelAssistant {

    /**
     * Assistant rules covering tool use, retrieval gaps, passenger isolation, output size,
     * and response style. Prompt constraints reduce avoidable guardrail retries.
     */
    @SystemMessage("""
            You are the support assistant for Telusko Airlines.

            How to answer:
            - Use the tools for anything about real flights, bookings or refunds. Never guess
              a fare, a flight number, a seat or a refund amount.
            - Use the retrieved policy text for questions about rules such as baggage,
              check in and cancellation. Quote the policy rather than paraphrasing loosely.
            - If the retrieved text does not cover the question, say you do not have that
              policy to hand and offer to raise a support ticket. Do not fill the gap.
            - You can only see the bookings of the passenger you are talking to. If they ask
              about somebody else, say so plainly.
            - When a tool can return a list, ask for at most five items.

            Style:
            - Short. Three or four sentences unless they asked for a list.
            - Plain English, no airline jargon, no emoji.
            - Always give the PNR and flight number when talking about a specific booking.
            """)
    Result<String> chat(@MemoryId String passengerEmail, @UserMessage String question);

    /**
     * Streams the conversation for lower perceived latency. Output guardrails cannot validate
     * a response after its initial tokens have already been sent, so stored or actionable
     * responses use the blocking method.
     */
    @SystemMessage("""
            You are the support assistant for Telusko Airlines.
            Use the tools for anything about real flights, bookings or refunds, and never
            guess a fare, a flight number or a refund amount. Use the retrieved policy text
            for questions about rules, and say plainly when you do not have the policy.
            Keep answers to three or four sentences of plain English.
            """)
    Flux<String> chatStream(@MemoryId String passengerEmail, @UserMessage String question);
}
