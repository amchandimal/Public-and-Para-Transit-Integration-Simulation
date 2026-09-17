package com.transit.simulation2.config;

import com.transit.simulation2.ParameterDiff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that every copy method of {@link SimulationParameters} changes exactly the fields it names
 * and leaves every other field alone. A sweep that silently altered a second parameter would make
 * the sensitivity analysis a two-factor experiment rather than a one-at-a-time one.
 */
class ParametersTest {

    private static final SimulationParameters DEFAULTS = SimulationParameters.DEFAULTS;

    @Test
    @DisplayName("withSeeds changes the six seeds and nothing else")
    void withSeeds() {
        assertThat(changed(DEFAULTS.withSeeds(1L, 2L, 3L, 4L, 5L, 6L))).isEqualTo(ParameterDiff.SEEDS);
    }

    @Test
    @DisplayName("withNetworkSeed changes the network seed alone")
    void withNetworkSeed() {
        assertThat(changed(DEFAULTS.withNetworkSeed(99L))).containsExactly("networkSeed");
    }

    @Test
    @DisplayName("withStops changes the stop count alone")
    void withStops() {
        assertThat(changed(DEFAULTS.withStops(60))).containsExactly("stops");
    }

    @Test
    @DisplayName("withClusteredStops changes the placement flag alone")
    void withClusteredStops() {
        assertThat(changed(DEFAULTS.withClusteredStops(true))).containsExactly("clusteredStops");
    }

    @Test
    @DisplayName("withOfficialFeedCoverage changes the feed coverage alone")
    void withOfficialFeedCoverage() {
        assertThat(changed(DEFAULTS.withOfficialFeedCoverage(0.25))).containsExactly("officialFeedCoverage");
    }

    @Test
    @DisplayName("withContributors changes the contributor count and the participation rate")
    void withContributors() {
        assertThat(changed(DEFAULTS.withContributors(500, 8.0)))
                .isEqualTo(Set.of("contributors", "meanReportersPerTrip"));
        assertThat(changed(DEFAULTS.withContributors(500, DEFAULTS.meanReportersPerTrip())))
                .as("passing the current participation rate must leave it unchanged")
                .containsExactly("contributors");
    }

    @Test
    @DisplayName("withMeanReportersPerTrip changes the participation rate alone")
    void withMeanReportersPerTrip() {
        assertThat(changed(DEFAULTS.withMeanReportersPerTrip(8.0))).containsExactly("meanReportersPerTrip");
    }

    @Test
    @DisplayName("withReliabilityBeta changes both Beta parameters")
    void withReliabilityBeta() {
        assertThat(changed(DEFAULTS.withReliabilityBeta(12.0, 3.0)))
                .isEqualTo(Set.of("reliabilityBetaAlpha", "reliabilityBetaBeta"));
    }

    @Test
    @DisplayName("withSpam changes the spam fraction and the collusion flag")
    void withSpam() {
        assertThat(changed(DEFAULTS.withSpam(0.30, true)))
                .isEqualTo(Set.of("spamContributorFraction", "colludingSpam"));
    }

    @Test
    @DisplayName("withConsensus changes the minimum report count and the agreement threshold")
    void withConsensus() {
        assertThat(changed(DEFAULTS.withConsensus(5, 0.80)))
                .isEqualTo(Set.of("consensusMinReports", "consensusAgreementThreshold"));
    }

    @Test
    @DisplayName("withDriverRateMultiplier changes the driver rate alone")
    void withDriverRateMultiplier() {
        assertThat(changed(DEFAULTS.withDriverRateMultiplier(2.0))).containsExactly("driverRateMultiplier");
    }

    @Test
    @DisplayName("forSeedSet changes the six seeds and nothing else")
    void forSeedSet() {
        assertThat(changed(DEFAULTS.forSeedSet(3))).isEqualTo(ParameterDiff.SEEDS);
    }

    @Test
    @DisplayName("a copy method with the current value is a no-op")
    void copyingTheCurrentValueChangesNothing() {
        assertThat(changed(DEFAULTS.withStops(DEFAULTS.stops()))).isEmpty();
        assertThat(changed(DEFAULTS.withOfficialFeedCoverage(DEFAULTS.officialFeedCoverage()))).isEmpty();
        assertThat(changed(DEFAULTS.withDriverRateMultiplier(DEFAULTS.driverRateMultiplier()))).isEmpty();
    }

    @Test
    @DisplayName("the record still holds exactly the documented components")
    void componentsAreTheDocumentedOnes() {
        assertThat(ParameterDiff.componentNames()).containsExactlyInAnyOrder(
                "stops", "areaXKm", "areaYKm", "clusteredStops",
                "officialFeedCoverage", "officialFeedNoiseMin", "disruptionProbability",
                "contributors", "meanReportersPerTrip", "reliabilityBetaAlpha", "reliabilityBetaBeta",
                "spamContributorFraction", "perReportSpamProbability", "colludingSpam", "colludeOffsetMin",
                "consensusMinReports", "consensusWindowMin", "consensusAgreementThreshold", "consensusToleranceMin",
                "driverRateMultiplier",
                "networkSeed", "querySeed", "dataSeed", "dispatchSeed",
                "tripEvaluationSeed", "paratransitEvaluationSeed");
    }

    private static Set<String> changed(SimulationParameters modified) {
        return ParameterDiff.changedComponents(DEFAULTS, modified);
    }
}
