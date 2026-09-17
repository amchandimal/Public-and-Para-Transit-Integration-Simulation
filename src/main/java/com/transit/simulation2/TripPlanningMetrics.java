package com.transit.simulation2;

/**
 * Trip-planning outcomes over the shared query set: the share of queries for which a
 * scheduled public-transport itinerary was found, the share served end to end once the
 * para-transit fallback is counted, the mean number of alternatives offered, and the load
 * that falls back to a whole-trip para-transit ride.
 */
public record TripPlanningMetrics(
        double itineraryFoundRatePct,
        double endToEndSuccessRatePct,
        double meanAlternativesShown,
        double pctNeedingWholeTripParatransit,
        Double meanWaitWhenParatransitNeededMin
) {
}
