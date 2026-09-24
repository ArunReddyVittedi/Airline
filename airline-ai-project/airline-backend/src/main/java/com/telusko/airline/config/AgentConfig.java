package com.telusko.airline.config;

import com.telusko.airline.agent.DisruptionAgents.CompensationAgent;
import com.telusko.airline.agent.DisruptionAgents.PassengerMessageAgent;
import com.telusko.airline.agent.DisruptionAgents.RebookingAgent;
import com.telusko.airline.agent.DisruptionAgents.SituationAgent;
import com.telusko.airline.agent.TripPlannerAgents.DestinationResearchAgent;
import com.telusko.airline.agent.TripPlannerAgents.FlightAdvisorAgent;
import com.telusko.airline.agent.TripPlannerAgents.ItineraryAgent;
import com.telusko.airline.tools.BookingTools;
import com.telusko.airline.tools.FlightTools;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.tool.ToolProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the multi agent systems.
 * <p>
 * These are not {@code @AiService} interfaces, and it is worth saying why. An AI Service is
 * one interface backed by one model call chain. An agentic system is several of those wired
 * together with a coordination strategy, and the strategy has to be chosen in code. So the
 * sub agents are built with {@link AgenticServices} here and handed to Spring as beans, which
 * gives us the best of both: the composition is explicit and readable, and controllers still
 * just inject what they need.
 * <p>
 * Notice how differently the two systems are put together. The disruption handler is a
 * supervisor, because which specialist to consult depends on what the first one found. The
 * trip planner is a fixed sequence, because it is the same three steps every single time.
 * Using a supervisor for the second would mean paying a model to make a decision that has
 * already been made.
 */
@Configuration
public class AgentConfig {

    // ================================================================ disruption

    /**
     * Facts first. Gets the booking and flight tools, and nothing else.
     * <p>
     * No retrieval on purpose: this agent answers "what happened", which is a question about
     * rows in a table, and giving it policy text would only invite it to start advising.
     */
    @Bean
    public SituationAgent situationAgent(ChatModel chatModel, FlightTools flightTools,
                                         BookingTools bookingTools,
                                         AgentMetricsListener agentMetricsListener) {
        return AgenticServices.agentBuilder(SituationAgent.class)
                .chatModel(chatModel)
                .listener(agentMetricsListener)
                .tools(flightTools, bookingTools)
                .build();
    }

    /**
     * Alternatives. Flight tools only.
     * <p>
     * Deliberately not given the booking tools. This agent recommends a flight; it must not
     * be able to look up unrelated bookings, and a narrower tool list is a narrower blast
     * radius if a prompt goes wrong.
     */
    @Bean
    public RebookingAgent rebookingAgent(ChatModel chatModel, FlightTools flightTools,
                                         AgentMetricsListener agentMetricsListener) {
        return AgenticServices.agentBuilder(RebookingAgent.class)
                .chatModel(chatModel)
                .listener(agentMetricsListener)
                .tools(flightTools)
                .build();
    }

    /**
     * Entitlement. Retrieval plus the refund tool.
     * <p>
     * The only sub agent with retrieval attached, because compensation is a written rule that
     * changes. Reading the current policy text beats having last year's thresholds baked into
     * a prompt that nobody remembers to update.
     */
    @Bean
    public CompensationAgent compensationAgent(ChatModel chatModel,
                                               RetrievalAugmentor retrievalAugmentor,
                                               BookingTools bookingTools,
                                               AgentMetricsListener agentMetricsListener) {
        return AgenticServices.agentBuilder(CompensationAgent.class)
                .chatModel(chatModel)
                .listener(agentMetricsListener)
                .retrievalAugmentor(retrievalAugmentor)
                .tools(bookingTools)
                .build();
    }

    /** Writing only. No tools, so it cannot contradict the notes it was handed. */
    @Bean
    public PassengerMessageAgent passengerMessageAgent(ChatModel chatModel,
                                                       AgentMetricsListener agentMetricsListener) {
        return AgenticServices.agentBuilder(PassengerMessageAgent.class)
                .chatModel(chatModel)
                .listener(agentMetricsListener)
                .build();
    }

    /**
     * The supervisor. Reads the request, decides which specialists to call, and in what order.
     * <p>
     * Two settings here are the difference between a demo and a runaway bill.
     * <p>
     * {@code maxAgentsInvocations(6)} is the circuit breaker. A supervisor that is not
     * satisfied with an answer will call another agent, and a confused one will keep going.
     * Six is enough for all four specialists plus two retries, and it means the worst case is
     * bounded rather than whatever the model feels like.
     * <p>
     * {@code SUMMARY} makes the supervisor compose a final answer from everything the sub
     * agents produced. {@code LAST} would return only the final agent's output, which sounds
     * equivalent and is not: if the supervisor decides to skip the message writing agent
     * because the flight turned out to be on time, LAST returns a rebooking note instead of
     * an answer to the passenger.
     */
    @Bean
    public SupervisorAgent disruptionSupervisor(ChatModel chatModel,
                                                SituationAgent situationAgent,
                                                RebookingAgent rebookingAgent,
                                                CompensationAgent compensationAgent,
                                                PassengerMessageAgent passengerMessageAgent,
                                                AgentMetricsListener agentMetricsListener) {
        return AgenticServices.supervisorBuilder()
                .chatModel(chatModel)
                // Attached once. The listener inherits down to every sub agent, so each of
                // the four gets its own timer without being wired individually.
                .listener(agentMetricsListener)
                .subAgents(situationAgent, rebookingAgent, compensationAgent, passengerMessageAgent)
                .maxAgentsInvocations(6)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .supervisorContext("""
                        You coordinate the disruption desk of an airline.

                        Always call the situation agent first. Nothing else can be decided
                        before you know whether the flight is actually cancelled, delayed or
                        on time.

                        Then:
                          - If the flight is on time, say so and stop. Do not consult the
                            rebooking or compensation agents. There is nothing to compensate.
                          - If it is cancelled, consult rebooking and compensation, then have
                            the passenger message agent write the reply.
                          - If it is delayed, consult compensation. Consult rebooking as well
                            only when the delay is over two hours.

                        Never invent a flight number, a fare or a refund amount. Only repeat
                        figures a sub agent reported.
                        """)
                .build();
    }

    // ================================================================ trip planner

    @Bean
    public DestinationResearchAgent destinationResearchAgent(ChatModel chatModel,
                                                             RetrievalAugmentor retrievalAugmentor,
                                                             ToolProvider mcpToolProvider,
                                                             AgentMetricsListener agentMetricsListener) {
        return AgenticServices.agentBuilder(DestinationResearchAgent.class)
                .chatModel(chatModel)
                .listener(agentMetricsListener)
                .retrievalAugmentor(retrievalAugmentor)
                // The weather and peak travel tools live on the MCP server, because climate
                // is not something an airline owns.
                .toolProvider(mcpToolProvider)
                .build();
    }

    @Bean
    public FlightAdvisorAgent flightAdvisorAgent(ChatModel chatModel, FlightTools flightTools,
                                                 AgentMetricsListener agentMetricsListener) {
        return AgenticServices.agentBuilder(FlightAdvisorAgent.class)
                .chatModel(chatModel)
                .listener(agentMetricsListener)
                .tools(flightTools)
                .build();
    }

    @Bean
    public ItineraryAgent itineraryAgent(ChatModel chatModel,
                                         AgentMetricsListener agentMetricsListener) {
        return AgenticServices.agentBuilder(ItineraryAgent.class)
                .chatModel(chatModel)
                .listener(agentMetricsListener)
                .build();
    }

    /**
     * Research, then flights, then the itinerary. Always in that order.
     * <p>
     * Untyped because the inputs are a handful of named values rather than a fixed signature,
     * and {@code invokeWithAgenticScope} hands back both the result and the scope. The scope
     * is what the controller turns into the trace the UI shows, so the whole run is
     * explainable rather than a paragraph you either trust or do not.
     */
    @Bean
    public UntypedAgent tripPlanner(DestinationResearchAgent destinationResearchAgent,
                                    FlightAdvisorAgent flightAdvisorAgent,
                                    ItineraryAgent itineraryAgent,
                                    AgentMetricsListener agentMetricsListener) {
        return AgenticServices.sequenceBuilder()
                .listener(agentMetricsListener)
                .subAgents(destinationResearchAgent, flightAdvisorAgent, itineraryAgent)
                .outputKey("itinerary")
                .build();
    }
}
