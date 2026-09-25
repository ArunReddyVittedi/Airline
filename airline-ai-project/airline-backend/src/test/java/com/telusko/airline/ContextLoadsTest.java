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
 * Validates runtime wiring for AI services, agentic systems, RAG, and MCP without model calls.
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
     * Validates bean-name resolution used by explicit AI-service wiring.
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
     * Validates the packaged MCP server entry point, handshake, and documented tool set.
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
