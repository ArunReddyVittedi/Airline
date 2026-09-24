package com.telusko.airline.config;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Counts tokens and model latency for every single call, whoever made it.
 * <p>
 * This is the one thing that has to be a listener rather than a wrapper. Tools, agents and
 * RAG all cause extra model calls that no controller ever sees, so a counter placed in a
 * service would miss most of the spend. A tool calling chain of four steps is four billed
 * calls, and only the listener is told about all four.
 * <p>
 * A LangChain4j detail worth knowing: this bean is picked up by the OpenAI starter simply by
 * existing. Any {@code ChatModelListener} bean is attached to the auto configured models, so
 * there is nothing to register.
 */
@Component
public class TokenUsageListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageListener.class);

    private static final String TOKENS = "airline.ai.tokens";
    private static final String MODEL_TIMER = "airline.ai.model.call";
    private static final String MODEL_ERRORS = "airline.ai.model.errors";

    /** Key we stash the start time under. The attribute map exists exactly for this. */
    private static final String START_TIME = "airline.start.nanos";

    private final MeterRegistry registry;

    public TokenUsageListener(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onRequest(ChatModelRequestContext context) {
        context.attributes().put(START_TIME, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        String model = String.valueOf(context.chatResponse().modelName());
        recordLatency(context.attributes().get(START_TIME), model, "success");

        TokenUsage usage = context.chatResponse().tokenUsage();
        if (usage == null) {
            // Streaming responses do not always carry usage. Nothing to record, and it is
            // not an error, so do not let a null here take down the request.
            return;
        }

        count(model, "input", usage.inputTokenCount());
        count(model, "output", usage.outputTokenCount());
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        String model = String.valueOf(context.chatRequest().modelName());
        recordLatency(context.attributes().get(START_TIME), model, "failure");

        Counter.builder(MODEL_ERRORS)
                .description("Model calls that threw")
                .tag("model", model)
                .tag("exception", context.error().getClass().getSimpleName())
                .register(registry)
                .increment();

        // The message, never the prompt. Prompts hold passenger names, PNRs and payment
        // amounts, and logs are not access controlled.
        log.warn("Model call failed: {}", context.error().getMessage());
    }

    /**
     * Input and output are tagged rather than summed, because they cost different amounts.
     * A feature that sends a huge prompt and gets back one line is a different problem from
     * one that writes essays, and a single total hides which of the two you have.
     */
    private void count(String model, String type, Integer tokens) {
        if (tokens == null) {
            return;
        }
        Counter.builder(TOKENS)
                .description("Tokens billed, by model and direction")
                .tag("model", model)
                .tag("type", type)
                .register(registry)
                .increment(tokens);
    }

    private void recordLatency(Object startNanos, String model, String outcome) {
        if (!(startNanos instanceof Long start)) {
            return;
        }
        Timer.builder(MODEL_TIMER)
                .description("Latency of a single model call")
                .tag("model", model)
                .tag("outcome", outcome)
                .register(registry)
                .record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
    }
}
