package com.transit.simulation2.realtime;

/** FR4 shared baseline: an official real-time feed entry for one trip (may be uncovered). */
public record OfficialFeedEntry(boolean covered, Double estDelay) {
}
