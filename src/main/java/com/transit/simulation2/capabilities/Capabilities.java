package com.transit.simulation2.capabilities;

/**
 * The eight capability flags FR1-FR8 that parameterise every engine in the model. Existing and
 * Proposed are two named settings of the same flags, so a single code path evaluates both
 * configurations and any combination in between, which is what makes the isolated
 * single-factor ablation a comparison of like with like.
 *
 * {@link #EXISTING} and {@link #PROPOSED} encode Table I of the paper, and {@link FrAspect}
 * names the aspect that each flag stands for.
 */
public record Capabilities(
        boolean multimodal,             // FR1
        boolean aggregation,             // FR2
        boolean multiCriteriaRanking,    // FR3
        boolean realtimeStatus,          // FR4
        boolean crowdsourcing,           // FR5
        boolean paratransit,             // FR6
        boolean accessDiscovery,         // FR7
        boolean lastmileFallback         // FR8
) {

    public static final Capabilities EXISTING = new Capabilities(
            false, false, true, true, false, false, true, false);

    public static final Capabilities PROPOSED = new Capabilities(
            true, true, true, true, true, true, true, true);

    /** FR aspect identifiers, used for the isolated single-factor ablation. */
    public enum FrAspect {
        FR1_MULTIMODAL("FR1 Multimodal route planning (bus and train)"),
        FR2_AGGREGATION("FR2 Trip aggregation and itinerary generation"),
        FR3_RANKING("FR3 Multi-criteria recommendation and ranking"),
        FR4_REALTIME_STATUS("FR4 Real-time public transport status"),
        FR5_CROWDSOURCING("FR5 Crowdsourcing-enhanced ETA and crowdedness indicators"),
        FR6_PARATRANSIT("FR6 Para-transit integration (taxi/three-wheeler and carpooling)"),
        FR7_ACCESS_DISCOVERY("FR7 Custom origin-destination inputs and access-point discovery"),
        FR8_LASTMILE_FALLBACK("FR8 Last-mile continuity and para-transit fallback coordination");

        public final String label;

        FrAspect(String label) {
            this.label = label;
        }
    }

    /** Returns a copy of this configuration with a single FR aspect flipped. */
    public Capabilities toggled(FrAspect aspect, boolean value) {
        return switch (aspect) {
            case FR1_MULTIMODAL -> new Capabilities(value, aggregation, multiCriteriaRanking, realtimeStatus, crowdsourcing, paratransit, accessDiscovery, lastmileFallback);
            case FR2_AGGREGATION -> new Capabilities(multimodal, value, multiCriteriaRanking, realtimeStatus, crowdsourcing, paratransit, accessDiscovery, lastmileFallback);
            case FR3_RANKING -> new Capabilities(multimodal, aggregation, value, realtimeStatus, crowdsourcing, paratransit, accessDiscovery, lastmileFallback);
            case FR4_REALTIME_STATUS -> new Capabilities(multimodal, aggregation, multiCriteriaRanking, value, crowdsourcing, paratransit, accessDiscovery, lastmileFallback);
            case FR5_CROWDSOURCING -> new Capabilities(multimodal, aggregation, multiCriteriaRanking, realtimeStatus, value, paratransit, accessDiscovery, lastmileFallback);
            case FR6_PARATRANSIT -> new Capabilities(multimodal, aggregation, multiCriteriaRanking, realtimeStatus, crowdsourcing, value, accessDiscovery, lastmileFallback);
            case FR7_ACCESS_DISCOVERY -> new Capabilities(multimodal, aggregation, multiCriteriaRanking, realtimeStatus, crowdsourcing, paratransit, value, lastmileFallback);
            case FR8_LASTMILE_FALLBACK -> new Capabilities(multimodal, aggregation, multiCriteriaRanking, realtimeStatus, crowdsourcing, paratransit, accessDiscovery, value);
        };
    }

    /** Reads this setting's flag for one FR aspect, so an ablation row can be derived generically from {@link #EXISTING}. */
    public boolean get(FrAspect aspect) {
        return switch (aspect) {
            case FR1_MULTIMODAL -> multimodal;
            case FR2_AGGREGATION -> aggregation;
            case FR3_RANKING -> multiCriteriaRanking;
            case FR4_REALTIME_STATUS -> realtimeStatus;
            case FR5_CROWDSOURCING -> crowdsourcing;
            case FR6_PARATRANSIT -> paratransit;
            case FR7_ACCESS_DISCOVERY -> accessDiscovery;
            case FR8_LASTMILE_FALLBACK -> lastmileFallback;
        };
    }
}
