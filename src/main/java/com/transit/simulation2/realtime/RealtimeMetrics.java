package com.transit.simulation2.realtime;

/**
 * Quality of the real-time information a scenario delivers, scored against the simulated
 * ground truth: the mean absolute ETA error, how often a crowding level is known at all, and
 * crowding accuracy both among the trips where it is known and across the whole system.
 */
public record RealtimeMetrics(
        double meanAbsEtaErrorMin,
        double crowdingKnownRatePct,
        double crowdingAccuracyWhenKnownPct,
        double crowdingSystemWideAccuracyPct
) {
}
