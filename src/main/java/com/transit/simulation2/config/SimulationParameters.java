package com.transit.simulation2.config;

/**
 * Every modelling constant that the repeated-seed analysis and the sensitivity sweeps can vary,
 * plus the six random seeds, collected in one immutable record.
 *
 * {@link #DEFAULTS} is the baseline configuration the paper reports, and holds the same values
 * as the engines' own public constants. A sweep level or a seed set is a {@code with...} copy
 * of the defaults, so one instance of this record fully describes a run.
 */
public record SimulationParameters(
        // Synthetic network (NetworkGenerator)
        int stops,                       // number of stops placed in the service area
        double areaXKm,                  // service-area width  (x in [0, areaXKm])
        double areaYKm,                  // service-area height (y in [0, areaYKm])
        boolean clusteredStops,          // false: uniform placement (baseline); true: 3 Gaussian clusters
        // Real-time information engine (RealtimeEngine)
        double officialFeedCoverage,     // gamma: share of trips covered by the official feed
        double officialFeedNoiseMin,     // sigma of the official feed's delay error (min)
        double disruptionProbability,    // share of trips with a large (12 +- 5 min) delay
        int contributors,                // number of crowdsourcing contributors
        double meanReportersPerTrip,     // Poisson mean of reports per trip instance
        double reliabilityBetaAlpha,     // contributor reliability ~ Beta(alpha, beta)
        double reliabilityBetaBeta,
        double spamContributorFraction,  // share of contributors that always report spam
        double perReportSpamProbability, // per-report spam probability for honest contributors
        boolean colludingSpam,           // false: random spam (baseline); true: spammers report the same false delay
        double colludeOffsetMin,         // the false delay reported by colluding spammers = truth + offset
        int consensusMinReports,         // K: minimum in-window reports for a consensus
        double consensusWindowMin,       // temporal window (min)
        double consensusAgreementThreshold,
        double consensusToleranceMin,    // reports within +- tolerance of the weighted median "agree"
        // Para-transit engine (ParatransitEngine)
        double driverRateMultiplier,     // scales the hour-dependent driver arrival rate
        // Random seeds (ComparisonService)
        long networkSeed,
        long querySeed,
        long dataSeed,
        long dispatchSeed,
        long tripEvaluationSeed,
        long paratransitEvaluationSeed
) {

    public static final SimulationParameters DEFAULTS = new SimulationParameters(
            40, 35.0, 25.0, false,
            0.55, 3.0, 0.06,
            250, 3.0, 6.0, 2.0,
            0.05, 0.03, false, 20.0,
            3, 6.0, 0.65, 3.0,
            1.0,
            42L, 555L, 777L, 999L, 1234L, 4321L);

    /**
     * Seed set {@code i} of the repeated-seed analysis: every seed shifted by 1000*i, so that seed
     * set 0 is the baseline configuration and seed sets never overlap.
     */
    public SimulationParameters forSeedSet(int i) {
        return withSeeds(42L + 1000L * i, 555L + 1000L * i, 777L + 1000L * i, 999L + 1000L * i,
                1234L + 1000L * i, 4321L + 1000L * i);
    }

    public SimulationParameters withSeeds(long network, long query, long data, long dispatch, long tripEval, long ptEval) {
        return new SimulationParameters(stops, areaXKm, areaYKm, clusteredStops, officialFeedCoverage, officialFeedNoiseMin,
                disruptionProbability, contributors, meanReportersPerTrip, reliabilityBetaAlpha, reliabilityBetaBeta,
                spamContributorFraction, perReportSpamProbability, colludingSpam, colludeOffsetMin, consensusMinReports,
                consensusWindowMin, consensusAgreementThreshold, consensusToleranceMin, driverRateMultiplier,
                network, query, data, dispatch, tripEval, ptEval);
    }

    public SimulationParameters withNetworkSeed(long v) {
        return withSeeds(v, querySeed, dataSeed, dispatchSeed, tripEvaluationSeed, paratransitEvaluationSeed);
    }

    public SimulationParameters withStops(int v) {
        return new SimulationParameters(v, areaXKm, areaYKm, clusteredStops, officialFeedCoverage, officialFeedNoiseMin,
                disruptionProbability, contributors, meanReportersPerTrip, reliabilityBetaAlpha, reliabilityBetaBeta,
                spamContributorFraction, perReportSpamProbability, colludingSpam, colludeOffsetMin, consensusMinReports,
                consensusWindowMin, consensusAgreementThreshold, consensusToleranceMin, driverRateMultiplier,
                networkSeed, querySeed, dataSeed, dispatchSeed, tripEvaluationSeed, paratransitEvaluationSeed);
    }

    public SimulationParameters withClusteredStops(boolean v) {
        return new SimulationParameters(stops, areaXKm, areaYKm, v, officialFeedCoverage, officialFeedNoiseMin,
                disruptionProbability, contributors, meanReportersPerTrip, reliabilityBetaAlpha, reliabilityBetaBeta,
                spamContributorFraction, perReportSpamProbability, colludingSpam, colludeOffsetMin, consensusMinReports,
                consensusWindowMin, consensusAgreementThreshold, consensusToleranceMin, driverRateMultiplier,
                networkSeed, querySeed, dataSeed, dispatchSeed, tripEvaluationSeed, paratransitEvaluationSeed);
    }

    public SimulationParameters withOfficialFeedCoverage(double v) {
        return new SimulationParameters(stops, areaXKm, areaYKm, clusteredStops, v, officialFeedNoiseMin,
                disruptionProbability, contributors, meanReportersPerTrip, reliabilityBetaAlpha, reliabilityBetaBeta,
                spamContributorFraction, perReportSpamProbability, colludingSpam, colludeOffsetMin, consensusMinReports,
                consensusWindowMin, consensusAgreementThreshold, consensusToleranceMin, driverRateMultiplier,
                networkSeed, querySeed, dataSeed, dispatchSeed, tripEvaluationSeed, paratransitEvaluationSeed);
    }

    public SimulationParameters withContributors(int n, double meanReporters) {
        return new SimulationParameters(stops, areaXKm, areaYKm, clusteredStops, officialFeedCoverage, officialFeedNoiseMin,
                disruptionProbability, n, meanReporters, reliabilityBetaAlpha, reliabilityBetaBeta,
                spamContributorFraction, perReportSpamProbability, colludingSpam, colludeOffsetMin, consensusMinReports,
                consensusWindowMin, consensusAgreementThreshold, consensusToleranceMin, driverRateMultiplier,
                networkSeed, querySeed, dataSeed, dispatchSeed, tripEvaluationSeed, paratransitEvaluationSeed);
    }

    public SimulationParameters withMeanReportersPerTrip(double v) {
        return withContributors(contributors, v);
    }

    public SimulationParameters withReliabilityBeta(double alpha, double beta) {
        return new SimulationParameters(stops, areaXKm, areaYKm, clusteredStops, officialFeedCoverage, officialFeedNoiseMin,
                disruptionProbability, contributors, meanReportersPerTrip, alpha, beta,
                spamContributorFraction, perReportSpamProbability, colludingSpam, colludeOffsetMin, consensusMinReports,
                consensusWindowMin, consensusAgreementThreshold, consensusToleranceMin, driverRateMultiplier,
                networkSeed, querySeed, dataSeed, dispatchSeed, tripEvaluationSeed, paratransitEvaluationSeed);
    }

    public SimulationParameters withSpam(double contributorFraction, boolean colluding) {
        return new SimulationParameters(stops, areaXKm, areaYKm, clusteredStops, officialFeedCoverage, officialFeedNoiseMin,
                disruptionProbability, contributors, meanReportersPerTrip, reliabilityBetaAlpha, reliabilityBetaBeta,
                contributorFraction, perReportSpamProbability, colluding, colludeOffsetMin, consensusMinReports,
                consensusWindowMin, consensusAgreementThreshold, consensusToleranceMin, driverRateMultiplier,
                networkSeed, querySeed, dataSeed, dispatchSeed, tripEvaluationSeed, paratransitEvaluationSeed);
    }

    public SimulationParameters withConsensus(int minReports, double agreementThreshold) {
        return new SimulationParameters(stops, areaXKm, areaYKm, clusteredStops, officialFeedCoverage, officialFeedNoiseMin,
                disruptionProbability, contributors, meanReportersPerTrip, reliabilityBetaAlpha, reliabilityBetaBeta,
                spamContributorFraction, perReportSpamProbability, colludingSpam, colludeOffsetMin, minReports,
                consensusWindowMin, agreementThreshold, consensusToleranceMin, driverRateMultiplier,
                networkSeed, querySeed, dataSeed, dispatchSeed, tripEvaluationSeed, paratransitEvaluationSeed);
    }

    public SimulationParameters withDriverRateMultiplier(double v) {
        return new SimulationParameters(stops, areaXKm, areaYKm, clusteredStops, officialFeedCoverage, officialFeedNoiseMin,
                disruptionProbability, contributors, meanReportersPerTrip, reliabilityBetaAlpha, reliabilityBetaBeta,
                spamContributorFraction, perReportSpamProbability, colludingSpam, colludeOffsetMin, consensusMinReports,
                consensusWindowMin, consensusAgreementThreshold, consensusToleranceMin, v,
                networkSeed, querySeed, dataSeed, dispatchSeed, tripEvaluationSeed, paratransitEvaluationSeed);
    }
}
