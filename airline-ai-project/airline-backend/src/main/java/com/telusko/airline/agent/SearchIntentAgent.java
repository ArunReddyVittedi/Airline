package com.telusko.airline.agent;

import com.telusko.airline.dto.FlightViews.SearchIntent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * Turns a sentence into search parameters. One model call, no tools, no memory.
 * <p>
 * This is structured output doing the job it is best at. "Cheapest morning flight to Goa next
 * Friday" is easy for a person and impossible for a query parser, and it is also not a
 * question anybody should answer with a language model: the model reads the sentence, and the
 * database finds the flights. Splitting it that way means the model cannot invent a fare and
 * the search cannot misread a date.
 * <p>
 * No memory on purpose. This runs on one sentence at a time and has nothing to remember, and
 * attaching a memory would quietly record every search into a conversation nobody reads.
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel")
public interface SearchIntentAgent {

    /**
     * Today's date has to be passed in.
     * <p>
     * The model has no clock. Without this, "next Friday" is resolved against whenever the
     * model was trained, which is how a search for a flight next week returns nothing and
     * looks like an empty database. This is the single most common bug in date handling with
     * an LLM, and the fix is one template variable.
     */
    @SystemMessage("""
            You read a traveller's sentence and extract flight search parameters. Nothing else.

            Today is {{today}}. Resolve every relative date against that, and return
            departureDate as yyyy-MM-dd.

            Rules:
            - originCity and destinationCity: the city name or IATA code the traveller used.
              If they did not say where they are flying from, return "Mumbai".
            - cabinClass: ECONOMY, PREMIUM_ECONOMY or BUSINESS. Return "ANY" if they did not say.
            - timeOfDay: morning, afternoon, evening, night, or "any" if they did not say.
            - sortBy: "fare" if they asked for cheap, "duration" if they asked for fast or
              direct, otherwise "fare".
            - Never guess a city they did not mention. If there is no destination at all,
              return an empty string for destinationCity.
            """)
    SearchIntent extract(@V("today") String today, @UserMessage String sentence);
}
