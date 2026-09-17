package com.transit.simulation2.paratransit;

/** Outcome of one simulated para-transit dispatch request. */
public record DispatchOutcome(String status, int attempts, double waitMin) {
}
