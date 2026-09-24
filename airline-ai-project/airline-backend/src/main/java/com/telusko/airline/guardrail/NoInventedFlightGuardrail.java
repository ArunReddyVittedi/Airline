package com.telusko.airline.guardrail;

import com.telusko.airline.config.AiMetrics;
import com.telusko.airline.repository.FlightRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks that every flight number in the answer is a flight that exists.
 * <p>
 * This is the guardrail that earns its place. A model that has just seen five real flights
 * will occasionally produce a sixth, and a fabricated flight number is the single most
 * damaging thing this app could say: a passenger writes it down, arrives at the airport and
 * finds the flight was never real. No amount of prompting reduces that to zero.
 * <p>
 * Note that it reprompts rather than failing. A rejected answer with the reason attached
 * usually comes back correct on the retry, because the model is being told exactly what it
 * got wrong. Failing outright would send the passenger an error for a problem the model is
 * perfectly capable of fixing.
 */
@Component
public class NoInventedFlightGuardrail implements OutputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(NoInventedFlightGuardrail.class);

    /**
     * Our flight numbers are two letters and three digits, for example TL401.
     * <p>
     * Word boundaries matter here. Without them this matches the "TL401" inside a longer
     * token and reports numbers the answer never really contained.
     */
    private static final Pattern FLIGHT_NUMBER = Pattern.compile("\\b([A-Z]{2}\\d{3})\\b");

    private final FlightRepository flights;
    private final AiMetrics metrics;

    public NoInventedFlightGuardrail(FlightRepository flights, AiMetrics metrics) {
        this.flights = flights;
        this.metrics = metrics;
    }

    @Override
    public OutputGuardrailResult validate(AiMessage response) {
        String text = response.text();
        if (text == null || text.isBlank()) {
            return success();
        }

        Set<String> invented = new LinkedHashSet<>();
        Matcher matcher = FLIGHT_NUMBER.matcher(text);

        while (matcher.find()) {
            String flightNumber = matcher.group(1);
            if (flights.findByFlightNumber(flightNumber).isEmpty()) {
                invented.add(flightNumber);
            }
        }

        if (invented.isEmpty()) {
            return success();
        }

        metrics.recordGuardrailBlock("invented-flight", "unknown_flight_number");
        log.warn("Output guardrail rejected an answer naming unknown flights: {}", invented);

        return reprompt(
                "The answer mentioned flight numbers that do not exist: " + invented,
                "Do not name any flight number unless a tool returned it. If you are unsure of "
                        + "the flight number, describe the flight by its route and departure time instead.");
    }
}
