package com.telusko.airline.tools;

import com.telusko.airline.config.AiMetrics;
import com.telusko.airline.enums.FlightStatus;
import com.telusko.airline.repository.BookingRepository;
import com.telusko.airline.repository.FlightRepository;
import com.telusko.airline.repository.SupportTicketRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Airline numbers, exposed to the admin assistant as tools.
 * <p>
 * Same reasoning as {@link FlightTools}: this is not RAG. "How much did we make this week" is
 * a question about arithmetic, and a model reading a sample of booking documents would guess
 * at the total and sound certain about it. Each tool runs a real aggregate query, so the
 * numbers the admin sees are the numbers in the database.
 * <p>
 * Only reachable through the admin endpoint, which is guarded by {@code hasRole("ADMIN")}.
 * Revenue and load factor are not passenger facing data.
 */
@Component
public class OpsAnalyticsTools {

    private final BookingRepository bookings;
    private final FlightRepository flights;
    private final SupportTicketRepository tickets;
    private final AiMetrics metrics;

    public OpsAnalyticsTools(BookingRepository bookings, FlightRepository flights,
                             SupportTicketRepository tickets, AiMetrics metrics) {
        this.bookings = bookings;
        this.flights = flights;
        this.tickets = tickets;
        this.metrics = metrics;
    }

    @Tool("""
            Revenue and booking count for a recent period.
            period must be one of: today, week, month, year, all.
            Revenue excludes cancelled and refunded bookings.
            """)
    public Map<String, Object> revenueSummary(@P("today, week, month, year or all") String period) {
        metrics.recordToolCall("revenueSummary", "local");

        LocalDateTime since = since(period);
        BigDecimal revenue = bookings.revenueSince(since);
        long count = bookings.countByBookedAtAfter(since);

        // A LinkedHashMap-ordered map reads better in the answer than a record here, because
        // the model quotes the keys back and "period" first is the natural sentence order.
        return Map.of(
                "period", normalise(period),
                "bookings", count,
                "revenue", revenue == null ? BigDecimal.ZERO : revenue,
                "averageFare", count == 0 || revenue == null
                        ? BigDecimal.ZERO
                        : revenue.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP));
    }

    @Tool("""
            The busiest routes by booking count for a recent period.
            period must be one of: today, week, month, year, all.
            """)
    public List<Map<String, Object>> busiestRoutes(@P("today, week, month, year or all") String period) {
        metrics.recordToolCall("busiestRoutes", "local");

        return bookings.busiestRoutesSince(since(period)).stream()
                // Five is enough to answer the question and keeps the prompt small. An
                // unbounded list here is how a tool result blows the context window.
                .limit(5)
                .map(row -> Map.<String, Object>of(
                        "route", row[0] + " to " + row[1],
                        "bookings", row[2]))
                .toList();
    }

    @Tool("""
            How many flights are currently cancelled or delayed, and which ones.
            Use this to answer questions about today's disruption.
            """)
    public Map<String, Object> disruptionSnapshot() {
        metrics.recordToolCall("disruptionSnapshot", "local");

        return Map.of(
                "cancelled", flights.countByStatus(FlightStatus.CANCELLED),
                "delayed", flights.countByStatus(FlightStatus.DELAYED),
                "cancelledFlights", flights.findByStatus(FlightStatus.CANCELLED).stream()
                        .map(f -> f.getFlightNumber() + " " + f.getOrigin().getCode()
                                + " to " + f.getDestination().getCode())
                        .limit(10)
                        .toList());
    }

    @Tool("""
            The support queue broken down by category and priority, plus how many tickets the
            triage agent failed to classify.
            """)
    public Map<String, Object> supportQueue() {
        metrics.recordToolCall("supportQueue", "local");

        List<Map<String, Object>> breakdown = tickets.queueBreakdown().stream()
                .limit(10)
                .map(row -> Map.<String, Object>of(
                        "category", String.valueOf(row[0]),
                        "priority", String.valueOf(row[1]),
                        "count", row[2]))
                .toList();

        return Map.of(
                "total", tickets.count(),
                // Surfaced on purpose. A queue full of OTHER and NORMAL looks like real
                // classification until you see how many of them triage never touched.
                "notTriaged", tickets.countByAiTriagedFalse(),
                "breakdown", breakdown);
    }

    private static LocalDateTime since(String period) {
        LocalDateTime now = LocalDateTime.now();
        return switch (period == null ? "" : period.trim().toLowerCase()) {
            case "today" -> now.toLocalDate().atStartOfDay();
            case "week" -> now.minusDays(7);
            case "month" -> now.minusMonths(1);
            case "year" -> now.minusYears(1);
            // Anything unrecognised falls back to all time rather than returning nothing.
            // A wrong period should give a wide answer, not an empty one.
            default -> LocalDateTime.of(2000, 1, 1, 0, 0);
        };
    }

    private static String normalise(String period) {
        return period == null || period.isBlank() ? "all" : period.trim().toLowerCase();
    }
}
