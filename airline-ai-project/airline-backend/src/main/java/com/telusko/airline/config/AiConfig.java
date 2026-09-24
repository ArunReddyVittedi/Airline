package com.telusko.airline.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chat memory for the passenger facing assistant.
 */
@Configuration
public class AiConfig {

    /**
     * Twenty messages, which is ten exchanges.
     * <p>
     * The window is the cost control. Every call re-sends the whole history, so an unbounded
     * memory means a conversation that gets more expensive with each question until it hits
     * the context limit and starts failing. Ten exchanges is comfortably more than a support
     * conversation needs, and the {@code SystemMessage} is pinned so the assistant never
     * forgets its own rules however long the chat runs.
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore chatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }
}
