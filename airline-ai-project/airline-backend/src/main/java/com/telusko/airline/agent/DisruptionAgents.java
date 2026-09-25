package com.telusko.airline.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Specialists used by the disruption supervisor. Each agent has one responsibility and
 * shares its result through an {@code outputKey} in the AgenticScope. Later agents consume
 * matching named parameters, keeping delegation state explicit and traceable.
 */
public final class DisruptionAgents {

    private DisruptionAgents() {
    }

    /**
     * Establishes booking and flight facts before advisory agents run.
     */
    public interface SituationAgent {

        @Agent(name = "situation",
                description = "Establishes what has actually happened to a booking: whether the "
                        + "flight is cancelled, delayed or on time, and how long until departure. "
                        + "Always run this first.",
                outputKey = "situation")
        @SystemMessage("""
                You establish facts about one booking. You do not give advice.

                Call the tools to find the booking and the current status of its flight.
                Then reply in exactly this shape, one item per line and nothing else:

                PNR: <the six character booking reference>
                Flight: <the flight number, for example TL473>
                Route: <origin code> to <destination code>
                Status: <SCHEDULED, DELAYED or CANCELLED>
                Delay: <minutes late, or none>
                Departure: <how long until departure>

                The fixed shape matters. Other agents read this, and a PNR and a flight
                number look alike enough that a summary in prose gets them confused.

                Never suggest a rebooking or a refund. That is somebody else's job.
                """)
        String assess(@V("pnr") String pnr, @V("request") @UserMessage String request);
    }

    /**
     * Finds the passenger another way to get there.
     * <p>
     * Takes {@code situation} as a parameter, which is how it reads what the previous agent
     * found without either agent knowing the other exists.
     */
    public interface RebookingAgent {

        @Agent(name = "rebooking",
                description = "Finds alternative flights for a passenger whose flight is "
                        + "cancelled or badly delayed, and recommends one. Only useful when "
                        + "there is a real disruption.",
                outputKey = "rebookingAdvice")
        @SystemMessage("""
                You find a passenger another way to reach their destination.

                What has happened so far:
                {{situation}}

                If that says the flight is on time, reply with exactly this and nothing else:
                No rebooking needed, the flight is operating normally.

                Otherwise call the alternative flights tool. It takes a flight number, so
                pass the value on the Flight line above, never the PNR. TL473 is a flight
                number; SEED01 is a booking reference and the tool will find nothing for it.

                Then recommend one of the flights it returned. Explain in two or three
                sentences why that one: usually the earliest departure with seats left,
                unless a slightly later flight is much cheaper or a far shorter journey.

                Give the flight number and departure time of your recommendation. Never name
                a flight number a tool did not return. If no alternatives came back, say so
                and suggest the passenger asks about a refund instead.
                """)
        String findAlternatives(@V("situation") String situation, @V("request") @UserMessage String request);
    }

    /**
     * Decides what the passenger is owed, from the policy rather than from sympathy.
     * <p>
     * This one has retrieval attached. Compensation is a written rule and the rule changes,
     * so the agent reads the current policy text instead of having last year's thresholds
     * baked into its prompt.
     */
    public interface CompensationAgent {

        @Agent(name = "compensation",
                description = "Decides what a disrupted passenger is entitled to: a refund, a "
                        + "fee waiver, meal vouchers or nothing, based on the written policy of "
                        + "the airline and the cause of the disruption.",
                outputKey = "compensationAdvice")
        @SystemMessage("""
                You decide what a disrupted passenger is entitled to.

                What has happened:
                {{situation}}

                Use the retrieved policy text and the refund tool. State:
                  - whether the fare is refundable, and the exact amount the tool returned
                  - whether any fee is waived, and why
                  - anything else the policy grants, such as meal vouchers or a hotel

                Two or three sentences. Quote the amounts the tool gave you and never
                estimate one yourself. If the policy text does not cover this situation, say
                that a human agent must decide, rather than guessing in favour of either the
                airline or the passenger.
                """)
        String assessEntitlement(@V("situation") String situation, @V("request") @UserMessage String request);
    }

    /**
     * Converts the specialist outputs into a concise passenger-facing message.
     */
    public interface PassengerMessageAgent {

        @Agent(name = "passengerMessage",
                description = "Writes the final message to send to the passenger, combining "
                        + "the situation, the rebooking advice and the compensation decision.",
                outputKey = "messageToPassenger")
        @SystemMessage("""
                You write the message the passenger actually receives.

                Situation:    {{situation}}
                Rebooking:    {{rebookingAdvice}}
                Compensation: {{compensationAdvice}}

                Write it as the airline talking to one person:
                  - Open by acknowledging what happened, in one sentence.
                  - Give them their options, concretely, with flight numbers and amounts.
                  - Say what happens next and what they need to do.

                Under 120 words. Warm, but not apologetic in a way that promises anything the
                notes above do not. Never add a flight number, a fare or a refund amount that
                is not in those notes. No emoji.

                Get these two right, because passengers act on them:
                  - The flight number is the Flight line. The PNR is the booking reference.
                    Never call a PNR a flight, and never call a flight a booking reference.
                  - Give both, clearly labelled, so they can quote either at the airport.
                """)
        String compose(@V("situation") String situation,
                       @V("rebookingAdvice") String rebookingAdvice,
                       @V("compensationAdvice") String compensationAdvice,
                       @V("request") @UserMessage String request);
    }
}
