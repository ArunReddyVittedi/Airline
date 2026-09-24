package com.telusko.airline.enums;

/**
 * The operational state of a flight, which is what drives the disruption agent.
 * Anything other than SCHEDULED or LANDED means a passenger has a problem.
 */
public enum FlightStatus {
    SCHEDULED,
    DELAYED,
    CANCELLED,
    DEPARTED,
    LANDED
}
