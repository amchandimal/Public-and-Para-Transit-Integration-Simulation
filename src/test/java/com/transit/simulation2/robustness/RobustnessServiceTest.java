package com.transit.simulation2.robustness;

import com.transit.simulation2.SimulationFixture;
import com.transit.simulation2.config.SimulationParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the shape and the internal consistency of the robustness analyses. The seed counts are
 * deliberately small: correctness of the numbers is covered by
 * {@link com.transit.simulation2.BaselineSnapshotTest}, so this test only has to show that the
 * analyses aggregate what they claim to aggregate.
 */
class RobustnessServiceTest {

    private static RobustnessService robustness;

    @BeforeAll
    static void setUp() {
        robustness = SimulationFixture.robustnessService();
    }

    @Test
    @DisplayName("runRepeatedSeeds returns one run per seed set, each on its own parameter set")
    void repeatedSeedsReturnsOneRunPerSeedSet() {
        RobustnessService.RepeatedSeedResult result = robustness.runRepeatedSeeds(3, false);

        assertThat(result.seedSets()).isEqualTo(3);
        assertThat(result.networkFixed()).isFalse();
        assertThat(result.runs()).hasSize(3);
        assertThat(result.runs()).extracting(RobustnessService.RunResult::seedSet).containsExactly(0, 1, 2);

        for (RobustnessService.RunResult run : result.runs()) {
            assertThat(run.parameters())
                    .as("run %d must use seed set %d", run.seedSet(), run.seedSet())
                    .isEqualTo(SimulationParameters.DEFAULTS.forSeedSet(run.seedSet()));
            assertThat(run.metrics()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("runRepeatedSeeds summarises every metric that has a value")
    void repeatedSeedsSummarisesEveryMetric() {
        RobustnessService.RepeatedSeedResult result = robustness.runRepeatedSeeds(3, false);

        Set<String> measured = new LinkedHashSet<>();
        for (RobustnessService.RunResult run : result.runs()) {
            run.metrics().forEach((metric, value) -> {
                if (value != null) {
                    measured.add(metric);
                }
            });
        }

        Set<String> summarised = new LinkedHashSet<>();
        for (RobustnessService.MetricSummary s : result.summary()) {
            summarised.add(s.metric());
            assertThat(s.n()).as("%s", s.metric()).isBetween(1, 3);
            assertThat(s.min()).as("%s", s.metric()).isLessThanOrEqualTo(s.mean());
            assertThat(s.mean()).as("%s", s.metric()).isLessThanOrEqualTo(s.max());
            assertThat(s.sd()).as("%s", s.metric()).isGreaterThanOrEqualTo(0.0);
        }

        assertThat(summarised)
                .as("a metric with at least one value in some run must be summarised; "
                        + "a metric that is null in every run is omitted rather than summarised as zero")
                .isEqualTo(measured);
        assertThat(summarised).contains(
                "existing.itineraryFoundRatePct", "proposed.itineraryFoundRatePct",
                "delta.fr2.itineraryFoundRatePct", "network.servedStops", "reports.meanPerTrip");
    }

    @Test
    @DisplayName("runRepeatedSeeds counts the direction of every headline gain")
    void repeatedSeedsReportsDirectionCounts() {
        RobustnessService.RepeatedSeedResult result = robustness.runRepeatedSeeds(3, false);
        Map<String, Integer> directions = result.directionCounts();

        assertThat(directions).containsOnlyKeys(
                "proposedFindsMoreItineraries", "proposedHigherEndToEndSuccess", "proposedLowerEtaError",
                "coordinatedDispatchBeatsNaive", "fr2DeltaExceedsFr1Delta", "runs");
        assertThat(directions.get("runs")).isEqualTo(3);
        directions.forEach((check, count) ->
                assertThat(count).as("%s", check).isBetween(0, 3));
    }

    @Test
    @DisplayName("runRepeatedSeeds is cached, so a repeated call returns the same instance")
    void repeatedSeedsIsCached() {
        assertThat(robustness.runRepeatedSeeds(3, false)).isSameAs(robustness.runRepeatedSeeds(3, false));
    }

    @Test
    @DisplayName("runSensitivity restricted to consensusMinReports returns its documented levels")
    void sensitivityReturnsTheDocumentedLevels() {
        RobustnessService.SensitivityResult result = robustness.runSensitivity(1, "consensusMinReports");

        assertThat(result.seedsPerLevel()).isEqualTo(1);
        assertThat(result.parameters()).containsExactly("consensusMinReports");
        assertThat(result.levels()).extracting(RobustnessService.SweepLevel::level)
                .containsExactly("2", "3", "4", "5");

        for (RobustnessService.SweepLevel level : result.levels()) {
            assertThat(level.parameter()).isEqualTo("consensusMinReports");
            assertThat(level.seedSets()).isEqualTo(1);
            assertThat(level.mean()).isNotEmpty();
            assertThat(level.sd().keySet())
                    .as("every mean must have a matching standard deviation")
                    .isEqualTo(level.mean().keySet());
        }
    }

    @Test
    @DisplayName("the sweep list matches the sweeps that are defined")
    void sweepParametersMatchTheDefinedSweeps() {
        List<String> names = robustness.sweepParameters();

        assertThat(names).containsExactly(
                "officialFeedCoverage", "contributorReliability", "consensusMinReports",
                "consensusAgreementThreshold", "meanReportersPerTrip", "randomSpamContributorFraction",
                "colludingSpamContributorFraction", "driverRateMultiplier", "stops", "stopPlacement");
        assertThat(RobustnessService.SWEEPS).extracting(RobustnessService.Sweep::parameter)
                .containsExactlyElementsOf(names);
        assertThat(RobustnessService.SWEEPS.stream().mapToInt(s -> s.levels().size()).sum())
                .as("the ten sweeps have 43 levels between them, so the default run is 430 evaluations")
                .isEqualTo(43);
    }

    @Test
    @DisplayName("an unknown sweep name is rejected")
    void unknownSweepNameIsRejected() {
        assertThat(
                org.assertj.core.api.Assertions.catchThrowable(() -> robustness.runSensitivity(1, "nosuchsweep")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nosuchsweep");
    }
}
