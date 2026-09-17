package com.transit.simulation2;

/**
 * Para-transit dispatch measured directly over the shared dispatch hours, independently of
 * trip planning: whether FR6 makes para-transit available at all, the share of requests that
 * were matched to a driver, and the mean wait of the matched requests.
 */
public record ParatransitDirectMetrics(
        boolean available,
        double matchRatePct,
        Double meanWaitMin
) {
}
