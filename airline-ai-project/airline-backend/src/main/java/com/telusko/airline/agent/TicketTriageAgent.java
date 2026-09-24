package com.telusko.airline.agent;

import com.telusko.airline.dto.TicketViews.TriageResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * Classifies an incoming support ticket so it reaches the right desk.
 * <p>
 * Classification is structured output with the smallest possible shape, and it is the AI
 * feature with the clearest payoff in the whole app. A baggage claim and a refund request go
 * to different teams, and the difference between a ticket answered in an hour and one
 * answered in two days is usually just whether it was routed correctly on arrival.
 * <p>
 * Returning an enum-shaped record rather than free text is what makes it usable. A paragraph
 * saying "this looks like a refund issue, probably fairly urgent" cannot drive a queue.
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel")
public interface TicketTriageAgent {

    @SystemMessage("""
            You triage support tickets for an airline. Read the ticket and classify it.

            category must be exactly one of:
              BAGGAGE, REFUND, BOOKING_CHANGE, FLIGHT_DISRUPTION, SPECIAL_ASSISTANCE,
              FEEDBACK, OTHER

            priority must be exactly one of: LOW, NORMAL, HIGH, URGENT
              URGENT means the passenger is travelling within 24 hours, or is stranded, or
                     needs medical or mobility assistance.
              HIGH   means money is involved, or they are travelling within a week.
              NORMAL is the default.
              LOW    is feedback and general questions with no trip attached.

            needsHumanAgent is true when the ticket involves a complaint about staff, a
            medical matter, a legal threat, or anything where a wrong automated answer would
            make things worse.

            reason is one sentence explaining the category and priority you chose. Never
            leave it empty. The desk staff read it to decide whether you got it right.
            """)
    TriageResult triage(@UserMessage String ticketText);
}
