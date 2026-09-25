package com.telusko.airline.mcp;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * External operations tools exposed through MCP. Deterministic local data avoids additional
 * API credentials while preserving the same client boundary as a live provider.
 */
public class OpsTools {

    /** Typical weather by month, per city. Enough to plan a trip around. */
    private static final Map<String, String[]> CLIMATE = Map.of(
            "GOI", new String[]{"warm and dry, 22 to 32 C", "monsoon, heavy rain", "humid, 26 to 33 C"},
            "BOM", new String[]{"warm and dry, 20 to 32 C", "monsoon, very heavy rain", "humid, 25 to 33 C"},
            "DEL", new String[]{"cold, 8 to 22 C", "hot then monsoon, 28 to 40 C", "mild, 18 to 30 C"},
            "BLR", new String[]{"pleasant, 16 to 28 C", "showers, 20 to 30 C", "mild, 18 to 29 C"},
            "MAA", new String[]{"warm, 22 to 30 C", "hot and humid, 28 to 38 C", "rain, 24 to 31 C"},
            "CCU", new String[]{"cool, 14 to 27 C", "monsoon, humid", "warm, 22 to 32 C"},
            "SXR", new String[]{"snow, minus 2 to 8 C", "pleasant, 15 to 30 C", "cool, 6 to 20 C"});

    /** Airports where an evening departure is realistically going to be late. */
    private static final Map<String, String> CONGESTION = Map.of(
            "DEL", "high in winter mornings, fog delays are common in December and January",
            "BOM", "high on weekday evenings, single main runway",
            "BLR", "moderate",
            "GOI", "low, but the terminal is small and check in queues build up",
            "MAA", "moderate",
            "CCU", "low",
            "SXR", "low, but weather closures happen in winter");

    @Tool("""
            Typical weather for an airport city in a given month, and whether that month is a
            good time to visit. Use this when planning a trip or advising on what to pack.
            """)
    public Map<String, Object> cityWeather(
            @P("three letter IATA code, for example GOI") String airportCode,
            @P("month number from 1 to 12") int month) {

        String code = normalise(airportCode);
        String[] seasons = CLIMATE.get(code);

        if (seasons == null) {
            return Map.of("airport", code, "known", false,
                    "note", "No climate data for this airport on the ops feed.");
        }

        int safeMonth = Math.max(1, Math.min(12, month));
        String season = seasonFor(safeMonth);
        String description = seasons[seasonIndex(safeMonth)];

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("airport", code);
        result.put("known", true);
        result.put("month", safeMonth);
        result.put("season", season);
        result.put("weather", description);
        result.put("goodTimeToVisit", !description.contains("monsoon") && !description.contains("snow"));
        return result;
    }

    @Tool("""
            How congested an airport usually is, and what tends to cause delays there.
            Use this when a passenger asks how early to arrive, or why a flight is often late.
            """)
    public Map<String, Object> airportCongestion(
            @P("three letter IATA code, for example DEL") String airportCode) {

        String code = normalise(airportCode);
        String note = CONGESTION.getOrDefault(code, "No congestion data on the ops feed.");

        return Map.of(
                "airport", code,
                "congestion", note,
                "suggestedArrivalMinutesBeforeDeparture", note.startsWith("high") ? 150 : 105);
    }

    @Tool("""
            The gate and terminal for a flight from the live airport feed, plus a plain
            language note. Use this on the day of travel.
            """)
    public Map<String, Object> gateInfo(@P("flight number, for example TL401") String flightNumber) {
        String flight = normalise(flightNumber);

        // Derived from the flight number rather than random, so the same flight always
        // reports the same gate. A tool that answers differently every time it is called
        // makes an agent loop, because the model keeps checking whether it misread.
        int hash = Math.abs(flight.hashCode());
        String terminal = "T" + (1 + hash % 3);
        String gate = (char) ('A' + hash % 4) + String.valueOf(1 + hash % 30);

        return Map.of(
                "flight", flight,
                "terminal", terminal,
                "gate", gate,
                "boardingOpens", LocalTime.of(0, 0).plusMinutes(45).toString(),
                "note", "Gates can change up to 45 minutes before departure.");
    }

    @Tool("""
            Public holidays and peak travel periods in the next few months, which is when
            fares are highest and airports are busiest. Use this when advising on when to fly.
            """)
    public List<Map<String, Object>> peakTravelPeriods() {
        int year = LocalDate.now().getYear();

        return List.of(
                Map.of("period", "Diwali week", "month", 11, "year", year,
                        "note", "Fares roughly double. Book six weeks ahead."),
                Map.of("period", "Christmas and New Year", "month", 12, "year", year,
                        "note", "Peak season for Goa. Very heavy demand."),
                Map.of("period", "Summer school holidays", "month", 5, "year", year + 1,
                        "note", "Busy on family routes and hill stations."));
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    /** Three coarse buckets, which is all the trip planner needs. */
    private static int seasonIndex(int month) {
        if (month >= 11 || month <= 2) {
            return 0;
        }
        return (month >= 6 && month <= 9) ? 1 : 2;
    }

    private static String seasonFor(int month) {
        return switch (seasonIndex(month)) {
            case 0 -> "winter";
            case 1 -> "monsoon";
            default -> "summer";
        };
    }
}
