package com.transit.simulation2;

import com.transit.simulation2.realtime.ConsensusEntry;
import com.transit.simulation2.realtime.GroundTruthEntry;
import com.transit.simulation2.realtime.OfficialFeedEntry;
import com.transit.simulation2.realtime.Report;

import java.util.List;
import java.util.Map;

/**
 * The stochastic inputs every scenario shares: queries, trip instances, ground truth, the
 * official feed, the crowdsourced reports and their consensus, and the dispatch hours. They are
 * generated once from fixed seeds and reused without regeneration, so two scenarios differ only
 * in their capability flags.
 */
public record SharedInputs(
        List<Query> queries,
        List<String> tripIds,
        Map<String, GroundTruthEntry> groundTruth,
        Map<String, OfficialFeedEntry> officialFeed,
        List<Report> reports,
        Map<String, ConsensusEntry> consensus,
        int[] dispatchHours
) {
}
