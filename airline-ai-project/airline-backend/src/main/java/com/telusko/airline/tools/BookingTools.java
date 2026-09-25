package com.telusko.airline.tools;

import com.telusko.airline.config.AiMetrics;
import com.telusko.airline.dto.BookingViews.BookingView;
import com.telusko.airline.dto.BookingViews.RefundQuote;
import com.telusko.airline.security.CurrentUser;
import com.telusko.airline.service.BookingService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Passenger booking tools scoped through {@link CurrentUser}. No method accepts an email
 * supplied by the model, so tool access remains bound to the signed JWT identity.
 * <p>
 * Cancellation is intentionally excluded because it is an irreversible financial action;
 * the assistant can quote a refund, while the UI requires explicit passenger confirmation.
 */
@Component
public class BookingTools {

    private final BookingService bookingService;
    private final CurrentUser currentUser;
    private final AiMetrics metrics;

    public BookingTools(BookingService bookingService, CurrentUser currentUser, AiMetrics metrics) {
        this.bookingService = bookingService;
        this.currentUser = currentUser;
        this.metrics = metrics;
    }

    @Tool("""
            All bookings belonging to the passenger you are currently talking to, newest first.
            Use this when they ask about "my flights" or "my trips" without giving a PNR.
            """)
    public List<BookingView> myBookings() {
        metrics.recordToolCall("myBookings", "local");
        return bookingService.myBookings(currentUser.requireEmail());
    }

    @Tool("""
            One booking by its six character PNR. Returns nothing if that PNR does not belong
            to the passenger you are talking to.
            """)
    public BookingView bookingByPnr(@P("the six character booking reference") String pnr) {
        metrics.recordToolCall("bookingByPnr", "local");
        return bookingService.myBooking(currentUser.requireEmail(), pnr).orElse(null);
    }

    @Tool("""
            Works out exactly what the passenger would get back if they cancelled this booking:
            the fee, the refund amount and the reason.
            Always call this before saying anything about a refund. Never estimate a refund
            yourself, and never quote a figure this tool did not return.
            """)
    public RefundQuote refundQuote(@P("the six character booking reference") String pnr) {
        metrics.recordToolCall("refundQuote", "local");
        return bookingService.quoteRefund(currentUser.requireEmail(), pnr);
    }
}
