package com.telusko.airline.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Chat memory that survives a restart.
 * <p>
 * The default {@code InMemoryChatMemoryStore} is a map, which is fine for a demo and wrong
 * for this app. Restart the backend and every passenger loses the conversation they were
 * halfway through, which in a support context means they have to explain their cancelled
 * flight a second time. One table fixes it.
 * <p>
 * The messages are stored as the JSON LangChain4j itself produces. Writing our own schema for
 * them was tempting and would have been a mistake: a stored {@code ToolExecutionRequest} has
 * to come back with its id intact or the next model call is rejected for referring to a tool
 * result that does not match. {@link ChatMessageSerializer} already gets that right.
 */
@Component
public class JdbcChatMemoryStore implements ChatMemoryStore {

    private final JdbcTemplate jdbc;

    public JdbcChatMemoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.execute("""
                create table if not exists chat_memory (
                    memory_id  varchar(200) primary key,
                    messages   text not null,
                    updated_at timestamp not null default now()
                )
                """);
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        List<String> rows = jdbc.queryForList(
                "select messages from chat_memory where memory_id = ?", String.class, key(memoryId));

        return rows.isEmpty()
                ? List.of()
                : ChatMessageDeserializer.messagesFromJson(rows.get(0));
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // LangChain4j hands over the whole window every time, not a delta, so an upsert is
        // the natural fit. Appending would drift out of step the moment the window evicts.
        jdbc.update("""
                        insert into chat_memory (memory_id, messages, updated_at)
                        values (?, ?, now())
                        on conflict (memory_id)
                        do update set messages = excluded.messages, updated_at = now()
                        """,
                key(memoryId), ChatMessageSerializer.messagesToJson(messages));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        jdbc.update("delete from chat_memory where memory_id = ?", key(memoryId));
    }

    /**
     * The memory id arrives as {@code Object} because LangChain4j lets you use any type.
     * Ours is always a String, but converting here rather than casting means a caller who
     * passes a Long gets a working conversation instead of a ClassCastException.
     */
    private String key(Object memoryId) {
        return String.valueOf(memoryId);
    }
}
