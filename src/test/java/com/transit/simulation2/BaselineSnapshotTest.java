package com.transit.simulation2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transit.simulation2.model.NetworkData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.fail;

/**
 * Locks the baseline results the paper reports. With
 * {@link com.transit.simulation2.config.SimulationParameters#DEFAULTS} the fully stacked comparison
 * and the isolated single-factor ablation must equal the committed snapshots to within 1e-9.
 *
 * A failure here means the published numbers have moved. That is not necessarily a defect, but it
 * is never incidental: the snapshots, the files under results/ and the tables in the paper all come
 * from this one baseline.
 */
class BaselineSnapshotTest {

    private static final double TOLERANCE = 1e-9;

    private static ComparisonService service;
    private static NetworkData network;
    private static SharedInputs shared;

    @BeforeAll
    static void buildBaseline() {
        service = SimulationFixture.comparisonService();
        network = service.buildNetwork();
        shared = service.buildSharedInputs();
    }

    @Test
    @DisplayName("the fully stacked comparison equals the committed snapshot")
    void stackedComparisonEqualsSnapshot() {
        Map<String, ScenarioResult> stacked = service.runStackedComparison(network, shared);

        Map<String, Object> actual = new LinkedHashMap<>();
        actual.putAll(flatten("existing", stacked.get("existing")));
        actual.putAll(flatten("proposed", stacked.get("proposed")));

        assertMatchesSnapshot("baseline/stacked.json", actual);
    }

    @Test
    @DisplayName("the isolated single-factor ablation equals the committed snapshot")
    void ablationEqualsSnapshot() {
        Map<String, Object> actual = new LinkedHashMap<>();
        for (AblationRow row : service.runIsolatedAblation(network, shared)) {
            String fr = row.frAspect().split("\\s+")[0];
            actual.put(fr + ".field", row.field());
            actual.put(fr + ".metric", row.metric());
            actual.put(fr + ".proposedValue", row.proposedValue());
            actual.put(fr + ".withCapabilityRemoved", row.withCapabilityRemoved());
            actual.put(fr + ".delta", row.delta());
            actual.put(fr + ".sharedNotDifferentiator", row.sharedNotDifferentiator());
        }

        assertMatchesSnapshot("baseline/ablation.json", actual);
    }

    private static Map<String, Object> flatten(String prefix, ScenarioResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(prefix + ".tripPlanning.itineraryFoundRatePct", r.tripPlanning().itineraryFoundRatePct());
        m.put(prefix + ".tripPlanning.endToEndSuccessRatePct", r.tripPlanning().endToEndSuccessRatePct());
        m.put(prefix + ".tripPlanning.meanAlternativesShown", r.tripPlanning().meanAlternativesShown());
        m.put(prefix + ".tripPlanning.pctNeedingWholeTripParatransit", r.tripPlanning().pctNeedingWholeTripParatransit());
        m.put(prefix + ".tripPlanning.meanWaitWhenParatransitNeededMin", r.tripPlanning().meanWaitWhenParatransitNeededMin());
        m.put(prefix + ".realtime.meanAbsEtaErrorMin", r.realtime().meanAbsEtaErrorMin());
        m.put(prefix + ".realtime.crowdingKnownRatePct", r.realtime().crowdingKnownRatePct());
        m.put(prefix + ".realtime.crowdingAccuracyWhenKnownPct", r.realtime().crowdingAccuracyWhenKnownPct());
        m.put(prefix + ".realtime.crowdingSystemWideAccuracyPct", r.realtime().crowdingSystemWideAccuracyPct());
        m.put(prefix + ".paratransitDirect.available", r.paratransitDirect().available());
        m.put(prefix + ".paratransitDirect.matchRatePct", r.paratransitDirect().matchRatePct());
        m.put(prefix + ".paratransitDirect.meanWaitMin", r.paratransitDirect().meanWaitMin());
        return m;
    }

    /**
     * Compares against the snapshot and, on any difference, reports every differing key at once
     * rather than stopping at the first, so one run shows the whole extent of the change.
     */
    private static void assertMatchesSnapshot(String resource, Map<String, Object> actual) {
        Map<String, Object> expected = loadSnapshot(resource);
        List<String> differences = new ArrayList<>();

        for (Map.Entry<String, Object> e : expected.entrySet()) {
            String key = e.getKey();
            if (!actual.containsKey(key)) {
                differences.add("  " + key + ": in the snapshot but absent from the result");
                continue;
            }
            Object want = e.getValue();
            Object got = actual.get(key);
            if (want instanceof Number wn && got instanceof Number gn) {
                double difference = Math.abs(wn.doubleValue() - gn.doubleValue());
                if (!(difference <= TOLERANCE)) {
                    differences.add(String.format(Locale.ROOT,
                            "  %s: expected %s, was %s (difference %.3e)", key, wn, gn, difference));
                }
            } else if (!Objects.equals(want, got)) {
                differences.add("  " + key + ": expected " + want + ", was " + got);
            }
        }
        for (String key : actual.keySet()) {
            if (!expected.containsKey(key)) {
                differences.add("  " + key + ": in the result but absent from the snapshot");
            }
        }

        if (!differences.isEmpty()) {
            fail(differences.size() + " difference(s) from src/test/resources/" + resource
                    + ", tolerance " + TOLERANCE + ":\n"
                    + String.join("\n", differences)
                    + "\n\nIf the change is intended, update the snapshot, regenerate results/ from"
                    + " the /csv endpoints and update the expected values in README.md and"
                    + " docs/REPRODUCING.md.");
        }
    }

    private static Map<String, Object> loadSnapshot(String resource) {
        try (InputStream in = BaselineSnapshotTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("snapshot not on the test classpath: " + resource);
            }
            return new ObjectMapper().readValue(in, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + resource, e);
        }
    }
}
