package com.telusko.airline.guardrail;

import com.telusko.airline.config.AiMetrics;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Rejects common instruction-override patterns before model invocation. Pattern matching is
 * an outer guardrail; passenger isolation is enforced by {@code BookingTools}, which accepts
 * no caller-supplied email address.
 */
@Component
public class PromptInjectionGuardrail implements InputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionGuardrail.class);

    /**
     * Narrow set of instruction-override phrases to limit false positives on valid questions.
     */
    private static final List<Pattern> OVERRIDE_ATTEMPTS = List.of(
            Pattern.compile("ignore (all |any |the )?(previous|above|prior|earlier) (instructions?|rules?|prompts?)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard (all |any |the )?(previous|above|prior) (instructions?|rules?)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("(reveal|show|print|repeat|output) (me )?(your |the )?(system|initial) (prompt|message|instructions?)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are (now|no longer)\\s", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pretend (you are|to be)\\s", Pattern.CASE_INSENSITIVE),
            Pattern.compile("act as (if you are |a )?(an? )?(admin|administrator|developer|root)",
                    Pattern.CASE_INSENSITIVE));

    /**
     * Email addresses in prompts are rejected because booking tools are scoped to the
     * authenticated passenger.
     */
    private static final Pattern OTHER_PERSONS_EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final int MAX_QUESTION_LENGTH = 2000;

    private final AiMetrics metrics;

    public PromptInjectionGuardrail(AiMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String text = userMessage.singleText();

        // Bound prompt size before model invocation.
        if (text.length() > MAX_QUESTION_LENGTH) {
            return blocked("too_long",
                    "That message is too long for me to read. Could you ask it in a sentence or two?");
        }

        for (Pattern pattern : OVERRIDE_ATTEMPTS) {
            if (pattern.matcher(text).find()) {
                return blocked("instruction_override",
                        "I can only help with flights, bookings and travel questions.");
            }
        }

        if (OTHER_PERSONS_EMAIL.matcher(text).find()) {
            return blocked("email_in_prompt",
                    "For privacy I can only see the bookings on your own account, so there is no "
                            + "need to give me an email address. Ask me about your PNR instead.");
        }

        return success();
    }

    /**
     * Uses {@code fatal} to stop the guardrail chain and avoid a model call.
     */
    private InputGuardrailResult blocked(String reason, String messageForPassenger) {
        metrics.recordGuardrailBlock("prompt-injection", reason);
        log.info("Input guardrail blocked a message, reason={}", reason);
        return fatal(messageForPassenger);
    }
}
