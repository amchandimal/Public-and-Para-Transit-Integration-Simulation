package com.transit.simulation2.itinerary;

/** Estimated time/cost for a single para-transit leg between two coordinates. */
public record ParatransitLeg(double waitMin, double travelMin, double fare, double km) {
}
