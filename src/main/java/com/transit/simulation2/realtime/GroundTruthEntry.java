package com.transit.simulation2.realtime;

/** Simulated ground truth for one trip instance (used only for validation). */
public record GroundTruthEntry(double delay, String crowding) {
}
