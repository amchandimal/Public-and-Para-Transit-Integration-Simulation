package com.transit.simulation2;

import com.transit.simulation2.realtime.RealtimeMetrics;

/**
 * The result of evaluating one capability setting: the three metric groups the comparison
 * reports on, namely trip planning, real-time information quality and direct para-transit
 * dispatch.
 */
public record ScenarioResult(
        TripPlanningMetrics tripPlanning,
        RealtimeMetrics realtime,
        ParatransitDirectMetrics paratransitDirect
) {
}
