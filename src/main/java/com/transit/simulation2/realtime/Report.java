package com.transit.simulation2.realtime;

/** FR5: a single simulated passenger-contributed observation. */
public record Report(
        String tripId,
        int contributorId,
        double obsDelay,
        String obsCrowding,
        double tOffsetMin
) {
}
