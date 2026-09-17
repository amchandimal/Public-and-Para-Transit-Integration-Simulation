package com.transit.simulation2;

import com.transit.simulation2.capabilities.Capabilities;
import com.transit.simulation2.config.SimulationParameters;
import com.transit.simulation2.model.Connection;
import com.transit.simulation2.model.NetworkData;
import com.transit.simulation2.model.Stop;
import com.transit.simulation2.realtime.ConsensusEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the reproducibility guarantees the whole comparison rests on: the same seeds must give the
 * same results, and each seed must govern only the inputs it is supposed to govern.
 */
class DeterminismTest {

    private static final SimulationParameters DEFAULTS = SimulationParameters.DEFAULTS;

    private static ComparisonService service;

    @BeforeAll
    static void setUp() {
        service = SimulationFixture.comparisonService();
    }

    @Test
    @DisplayName("two consecutive evaluations with the same seeds are identical")
    void consecutiveEvaluationsAreIdentical() {
        NetworkData network = service.buildNetwork();
        SharedInputs shared = service.buildSharedInputs();

        ScenarioResult first = service.evaluateScenario(network, Capabilities.PROPOSED, shared);
        ScenarioResult second = service.evaluateScenario(network, Capabilities.PROPOSED, shared);

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("rebuilding the network and the shared inputs from the same seeds reproduces them exactly")
    void rebuildingFromTheSameSeedsIsIdentical() {
        assertThat(networkFingerprint(service.buildNetwork()))
                .isEqualTo(networkFingerprint(service.buildNetwork()));
        assertThat(sharedFingerprint(service.buildSharedInputs()))
                .isEqualTo(sharedFingerprint(service.buildSharedInputs()));
    }

    @Test
    @DisplayName("changing only the network seed changes the network but leaves the shared inputs untouched")
    void networkSeedGovernsOnlyTheNetwork() {
        SimulationParameters other = DEFAULTS.withNetworkSeed(DEFAULTS.networkSeed() + 1000);

        assertThat(networkFingerprint(service.buildNetwork(other)))
                .as("a different network seed must produce a different network")
                .isNotEqualTo(networkFingerprint(service.buildNetwork(DEFAULTS)));

        assertThat(sharedFingerprint(service.buildSharedInputs(other)))
                .as("the shared stochastic inputs must not depend on the network seed")
                .isEqualTo(sharedFingerprint(service.buildSharedInputs(DEFAULTS)));
    }

    @Test
    @DisplayName("forSeedSet(i) shifts every seed by 1000 times i and changes nothing else")
    void forSeedSetShiftsEverySeed() {
        for (int i : new int[]{0, 1, 7}) {
            SimulationParameters p = DEFAULTS.forSeedSet(i);
            long shift = 1000L * i;

            assertThat(p.networkSeed()).isEqualTo(DEFAULTS.networkSeed() + shift);
            assertThat(p.querySeed()).isEqualTo(DEFAULTS.querySeed() + shift);
            assertThat(p.dataSeed()).isEqualTo(DEFAULTS.dataSeed() + shift);
            assertThat(p.dispatchSeed()).isEqualTo(DEFAULTS.dispatchSeed() + shift);
            assertThat(p.tripEvaluationSeed()).isEqualTo(DEFAULTS.tripEvaluationSeed() + shift);
            assertThat(p.paratransitEvaluationSeed()).isEqualTo(DEFAULTS.paratransitEvaluationSeed() + shift);

            assertThat(ParameterDiff.changedComponents(DEFAULTS, p))
                    .as("seed set %d must differ from the baseline in the seeds alone", i)
                    .isEqualTo(i == 0 ? java.util.Set.<String>of() : ParameterDiff.SEEDS);
        }
    }

    @Test
    @DisplayName("seed set 0 is the baseline configuration")
    void seedSetZeroIsTheBaseline() {
        assertThat(DEFAULTS.forSeedSet(0)).isEqualTo(DEFAULTS);
    }

    /**
     * {@link NetworkData} and {@link ConsensusEntry} are classes without value equality, and
     * {@link SharedInputs} holds an array, so the tests compare explicit fingerprints rather than
     * calling equals on them.
     */
    private static Object networkFingerprint(NetworkData n) {
        List<Stop> stops = n.getStops();
        List<String> routes = n.getRoutes().stream()
                .map(r -> r.getId() + "|" + r.getMode() + "|" + r.getStopIds())
                .toList();
        List<Connection> connections = n.getConnections();
        return List.of(stops, routes, connections);
    }

    private static Object sharedFingerprint(SharedInputs s) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("queries", s.queries());
        f.put("tripIds", s.tripIds());
        f.put("groundTruth", s.groundTruth());
        f.put("officialFeed", s.officialFeed());
        f.put("reports", s.reports());
        f.put("dispatchHours", Arrays.toString(s.dispatchHours()));
        Map<String, String> consensus = new LinkedHashMap<>();
        s.consensus().forEach((tripId, c) ->
                consensus.put(tripId, c.reached + "|" + c.estDelay + "|" + c.estCrowding + "|" + c.confidence));
        f.put("consensus", consensus);
        return f;
    }
}
