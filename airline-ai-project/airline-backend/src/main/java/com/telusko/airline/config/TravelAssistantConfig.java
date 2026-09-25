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
 * Builds the travel assistant explicitly because its output guardrail has Spring-managed
 * dependencies. {@code @OutputGuardrails} accepts a class that LangChain4j instantiates
 * through a no-argument constructor without Spring.
 * {@link NoInventedFlightGuardrail} needs the flight repository to check whether a flight
 * number is real, so that reflection fails with {@code NoSuchMethodException: <init>()} on
 * request. {@code AiServices.builder()} accepts the injected guardrail instances instead.
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
                // Required by the Flux-returning chatStream method.
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
