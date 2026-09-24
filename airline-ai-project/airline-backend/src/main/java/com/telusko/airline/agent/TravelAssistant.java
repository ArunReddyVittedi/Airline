package com.telusko.airline.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * The passenger facing assistant. The front door of the whole application.
 * <p>
 * It has everything attached at once, which is the point: retrieval for policy questions,
 * tools for anything about real flights and bookings, MCP tools for the outside world, memory
 * per passenger, and a guardrail on each side. A question like "my flight is cancelled, can I
 * get a refund and what about my checked bag" needs all of them in a single answer.
 * <p>
 * Unlike the other three agents, this interface carries no {@code @AiService} annotation. It
 * is built by hand in {@code TravelAssistantConfig}, and the reason is worth knowing before
 * you copy the annotation onto it.
 * <p>
 * {@code @InputGuardrails} and {@code @OutputGuardrails} take a <em>class</em>, and
 * LangChain4j instantiates it by reflection through a no argument constructor. Spring is not
 * consulted. Our output guardrail needs the flight repository to check whether a flight
 * number is real, so a no argument constructor cannot give it what it needs, and the
 * annotation fails at runtime with {@code NoSuchMethodException: <init>()}. Building the
 * service with {@code AiServices.builder()} lets us pass guardrail <em>instances</em> that
 * Spring has already injected.
 */
public interface TravelAssistant {

    /**
     * The rules the assistant works under.
     * <p>
     * Three of these lines exist because of something that went wrong without them. Asking
     * for at most five results stops a tool returning a list big enough to blow the context
     * window. Forbidding invented flight numbers is repeated here even though a guardrail
     * enforces it, because a prompt that asks for the right behaviour needs fewer retries
     * than a guardrail catching the wrong one. And saying "I do not know" has to be spelled
     * out, or the model fills a silent retrieval with plausible baggage allowances.
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
     * The same conversation, streamed.
     * <p>
     * Streaming is about how fast the answer feels, not how much it costs. A tool calling
     * chain can take six or seven seconds before the first word, and a passenger staring at
     * a spinner assumes it is broken.
     * <p>
     * The trade is real and worth knowing: a streamed answer cannot be checked by an output
     * guardrail, because the first tokens are already on their way to the browser by the time
     * the last one is written. The UI uses this for the conversation and the blocking method
     * above wherever the answer is stored or acted on.
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
