package com.transit.simulation2.itinerary;

/** Result of a full GPS-to-GPS query: the PT option if one was found (null if not), plus whether FR6 makes para-transit available as a fallback. */
public record FullItineraryResult(
        String originAccessStop,
        String destAccessStop,
        ParatransitLeg firstMile,
        ParatransitLeg lastMile,
        GenerateAndRankResult ptOption,   // null if no scheduled PT itinerary was feasible
        boolean fallbackAvailable          // FR6: whether para-transit exists as an option if PT fails
) {
}
