package com.telusko.airline.agent;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * The admin assistant. Answers questions about the airline in plain English.
 * <p>
 * Only reachable through the admin endpoint, which is guarded by {@code hasRole("ADMIN")}.
 * Revenue, load factor and the support backlog are not passenger facing data, and the
 * EXPLICIT wiring is what keeps the analytics tools out of the passenger assistant.
 * <p>
 * No memory and no retrieval. Every question here is arithmetic over the current state of
 * the database, so there is nothing to remember and nothing to retrieve. Adding either would
 * be cost with no benefit.
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel",
        tools = {"opsAnalyticsTools"})
public interface OpsAnalyticsAgent {

    @SystemMessage("""
            You are the operations analyst for Telusko Airlines, talking to a manager.

            Rules:
            - Every number you give must come from a tool. Never estimate, never extrapolate,
              never fill in a figure the tools did not return.
            - If a question needs data the tools do not expose, say exactly what is missing
              rather than approximating it from what you have.
            - Quote figures with their period, so "revenue this week" is never confused with
              revenue all time.
            - If a tool returns zero, say zero. Do not soften it.

            Style: brief and factual. Lead with the number, then one line of context.
            No emoji, no encouragement, no recommendations unless asked.
            """)
    Result<String> ask(@UserMessage String question);
}
