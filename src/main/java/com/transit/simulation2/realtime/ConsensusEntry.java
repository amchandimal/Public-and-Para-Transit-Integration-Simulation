package com.transit.simulation2.realtime;

/** Result of the FR5 consensus pipeline for one trip instance. */
public class ConsensusEntry {
    public final boolean reached;
    public final Double estDelay;
    public final String estCrowding;
    public final double confidence;

    public ConsensusEntry(boolean reached, Double estDelay, String estCrowding, double confidence) {
        this.reached = reached;
        this.estDelay = estDelay;
        this.estCrowding = estCrowding;
        this.confidence = confidence;
    }

    public static ConsensusEntry notReached() {
        return new ConsensusEntry(false, null, null, 0.0);
    }
}
