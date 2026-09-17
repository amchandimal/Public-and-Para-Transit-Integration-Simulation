package com.transit.simulation2.itinerary;

import java.util.List;
import java.util.Map;

/**
 * The itineraries produced for one origin-destination pair: the size of the Pareto front, the
 * time the Connection Scan took, and the highest-ranked itineraries per ranking criterion.
 * With FR3 ranking disabled the map holds a single "default" entry instead.
 */
public record GenerateAndRankResult(
        int paretoFrontSize,
        double latencyMs,
        Map<String, List<ItineraryView>> topByMode
) {
}
