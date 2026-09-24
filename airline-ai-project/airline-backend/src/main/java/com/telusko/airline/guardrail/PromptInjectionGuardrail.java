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
 * Stops the obvious attempts to talk the assistant out of its own rules.
 * <p>
 * The system message already tells the assistant not to reveal other passengers' bookings.
 * A system message is a request: the model usually follows it and sometimes does not. This is
 * Java code that runs before the model, so it always runs. That difference is the entire
 * reason guardrails exist.
 * <p>
 * Be honest about what this does and does not do. Pattern matching catches the lazy attacks,
 * which is most of what a public chatbot actually receives, and it will not stop a determined
 * one. The real protection is that {@code BookingTools} never accepts an email as a parameter,
 * so even a successful injection can only ask about the caller's own data. This guardrail is
 * the cheap outer layer, not the security model.
 */
@Component
public class PromptInjectionGuardrail implements InputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionGuardrail.class);

    /**
     * Phrases whose only purpose is to override instructions. Kept deliberately narrow.
     * A broad list blocks real questions, and a support bot that refuses honest passengers
     * is worse than one that occasionally sees a silly prompt.
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
     * Someone asking about a booking that is not theirs, by naming an email.
     * <p>
     * Blocked rather than allowed and ignored, because a passenger who types this deserves a
     * clear "I can only see your own bookings" instead of a vague answer that leaves them
     * wondering whether it worked.
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

        // A very long question is either a paste of somebody's inbox or an attempt to bury
        // an instruction under filler. Either way it is not a support question, and the
        // length check is free compared with the tokens it saves.
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
     * {@code fatal} rather than {@code failure} on purpose. A failure lets the chain carry on
     * to the next guardrail; there is nothing here worth carrying on for, and stopping
     * immediately means the request costs nothing at all.
     */
    private InputGuardrailResult blocked(String reason, String messageForPassenger) {
        metrics.recordGuardrailBlock("prompt-injection", reason);
        log.info("Input guardrail blocked a message, reason={}", reason);
        return fatal(messageForPassenger);
    }
}
