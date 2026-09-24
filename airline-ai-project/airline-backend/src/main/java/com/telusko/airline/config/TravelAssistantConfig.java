package com.telusko.airline.config;

import com.telusko.airline.agent.TravelAssistant;
import com.telusko.airline.guardrail.NoInventedFlightGuardrail;
import com.telusko.airline.guardrail.PromptInjectionGuardrail;
import com.telusko.airline.tools.BookingTools;
import com.telusko.airline.tools.FlightTools;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the travel assistant by hand, because its guardrails have dependencies.
 * <p>
 * The other three agents use {@code @AiService} and are a single annotation each, which is
 * the nicer way to write one. This one cannot, and the reason is specific rather than
 * stylistic: {@code @OutputGuardrails} takes a class and LangChain4j instantiates it through
 * a no argument constructor, with no Spring involved.
 * {@link NoInventedFlightGuardrail} needs the flight repository to check whether a flight
 * number is real, so that reflection fails with {@code NoSuchMethodException: <init>()} on
 * the first request. {@code AiServices.builder()} takes guardrail <em>instances</em>, so the
 * beans Spring already built can be handed straight in.
 * <p>
 * Everything else here is what the annotation would have done anyway, only visible. That is
 * arguably a fair trade for the front door of the application: the tool list, the memory,
 * the retrieval and both guardrails are all in one readable place.
 */
@Configuration
public class TravelAssistantConfig {

    /**
     * How many times a rejected answer is sent back to the model.
     * <p>
     * Two, and it has to be bounded. Each retry is a full billed call, and a model that will
     * not stop naming a flight that does not exist would otherwise loop for as long as the
     * passenger is willing to wait.
     */
    private static final int OUTPUT_GUARDRAIL_RETRIES = 2;

    @Bean
    public TravelAssistant travelAssistant(ChatModel chatModel,
                                           StreamingChatModel streamingChatModel,
                                           ChatMemoryProvider chatMemoryProvider,
                                           RetrievalAugmentor retrievalAugmentor,
                                           ToolProvider mcpToolProvider,
                                           FlightTools flightTools,
                                           BookingTools bookingTools,
                                           PromptInjectionGuardrail promptInjectionGuardrail,
                                           NoInventedFlightGuardrail noInventedFlightGuardrail) {

        return AiServices.builder(TravelAssistant.class)
                .chatModel(chatModel)
                // Needed for the Flux returning method. Without it, calling chatStream
                // fails at runtime rather than at startup, which is a worse place to learn.
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .retrievalAugmentor(retrievalAugmentor)

                // Only these two tool beans, never every @Tool bean in the context. The
                // analytics tools are admin only, and a passenger assistant that could
                // reach them would hand out the airline's revenue to anyone who asked.
                .tools(flightTools, bookingTools)

                // The MCP tools come as a provider rather than instances, because they live
                // in another process and are discovered at runtime.
                .toolProviders(mcpToolProvider)

                .inputGuardrails(promptInjectionGuardrail)
                .outputGuardrails(noInventedFlightGuardrail)
                .outputGuardrailsConfig(OutputGuardrailsConfig.builder()
                        .maxRetries(OUTPUT_GUARDRAIL_RETRIES)
                        .build())

                .build();
    }
}
