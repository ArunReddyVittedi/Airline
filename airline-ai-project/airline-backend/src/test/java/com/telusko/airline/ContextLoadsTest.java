package com.telusko.airline;

import com.telusko.airline.agent.OpsAnalyticsAgent;
import com.telusko.airline.agent.SearchIntentAgent;
import com.telusko.airline.agent.TicketTriageAgent;
import com.telusko.airline.agent.TravelAssistant;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.tool.ToolProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the wiring holds together, without spending a single token.
 * <p>
 * Everything asserted here is something that silently breaks at runtime rather than at
 * compile time. An {@code @AiService} with a misspelled bean name in its EXPLICIT wiring
 * compiles perfectly and then fails on the first request; a supervisor whose sub agents are
 * not beans fails when a passenger clicks the button. This test moves both of those failures
 * to the build.
 * <p>
 * Needs Postgres, which spring-boot-docker-compose starts, so Docker has to be running.
 * It does not need a real OpenAI key: the models are built from the properties and never
 * called, and building one does not validate the key.
 */
@SpringBootTest
class ContextLoadsTest {

    @Autowired
    private TravelAssistant travelAssistant;

    @Autowired
    private SearchIntentAgent searchIntentAgent;

    @Autowired
    private TicketTriageAgent ticketTriageAgent;

    @Autowired
    private OpsAnalyticsAgent opsAnalyticsAgent;

    @Autowired
    private SupervisorAgent disruptionSupervisor;

    @Autowired
    private UntypedAgent tripPlanner;

    @Autowired
    private RetrievalAugmentor retrievalAugmentor;

    @Autowired
    private ToolProvider mcpToolProvider;

    @Autowired
    private McpClient opsMcpClient;

    /**
     * The four AI services exist as beans.
     * <p>
     * This is the assertion that catches a typo in {@code tools = {"flightTools"}}. EXPLICIT
     * wiring resolves bean names, and a name that does not exist is not a compile error.
     */
    @Test
    void aiServicesAreWired() {
        assertThat(travelAssistant).isNotNull();
        assertThat(searchIntentAgent).isNotNull();
        assertThat(ticketTriageAgent).isNotNull();
        assertThat(opsAnalyticsAgent).isNotNull();
    }

    @Test
    void multiAgentSystemsAreBuilt() {
        assertThat(disruptionSupervisor).isNotNull();
        assertThat(tripPlanner).isNotNull();
    }

    @Test
    void ragIsWired() {
        assertThat(retrievalAugmentor).isNotNull();
    }

    /**
     * The MCP server started, handshook, and offered its tools.
     * <p>
     * Four is the number {@code OpsTools} declares, and asserting the exact count is
     * deliberate: adding a tool to the server and forgetting to mention it in the docs is a
     * failure worth being told about. This also proves the jar was built and the main class
     * in its manifest is correct, which is the single most common way this module breaks.
     */
    @Test
    void mcpServerIsReachableAndOffersItsTools() {
        assertThat(mcpToolProvider).isNotNull();

        assertThat(opsMcpClient.listTools())
                .as("the ops MCP server should offer its four tools")
                .hasSize(4)
                .extracting(tool -> tool.name())
                .containsExactlyInAnyOrder(
                        "cityWeather", "airportCongestion", "gateInfo", "peakTravelPeriods");
    }
}
