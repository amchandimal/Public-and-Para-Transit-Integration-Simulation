package com.transit.simulation2;

import com.transit.simulation2.capabilities.Capabilities;
import com.transit.simulation2.capabilities.Capabilities.FrAspect;
import com.transit.simulation2.config.SimulationParameters;
import com.transit.simulation2.itinerary.FullItineraryResult;
import com.transit.simulation2.itinerary.ItineraryEngine;
import com.transit.simulation2.model.NetworkData;
import com.transit.simulation2.network.NetworkGenerator;
import com.transit.simulation2.paratransit.DispatchOutcome;
import com.transit.simulation2.paratransit.ParatransitEngine;
import com.transit.simulation2.realtime.RealtimeEngine;
import com.transit.simulation2.realtime.RealtimeMetrics;
import com.transit.simulation2.util.RandomUtils;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Orchestrates the capability-flag comparison: builds the shared stochastic inputs once,
 * evaluates any {@link Capabilities} setting through
 * {@link #evaluateScenario(NetworkData, Capabilities, SharedInputs, SimulationParameters)},
 * and derives from that both the isolated single-factor ablation and the fully stacked
 * Existing vs Proposed comparison.
 *
 * Each entry point comes in two forms: one that runs the baseline configuration and one that
 * takes an explicit {@link SimulationParameters}, which the repeated-seed analysis and the
 * sensitivity sweeps supply. The constants below hold the baseline values and match
 * {@link SimulationParameters#DEFAULTS}.
 */
@Service
public class ComparisonService {

    public static final long NETWORK_SEED = 42;
    public static final long DATA_SEED = 777;
    public static final long QUERY_SEED = 555;
    public static final long DISPATCH_SEED = 999;

    public static final int N_QUERIES = 300;
    public static final int N_TRIPS = 3000;
    public static final int N_DISPATCH = 3000;

    public static final long TRIP_EVALUATION_SEED = 1234;
    public static final long PARATRANSIT_EVALUATION_SEED = 4321;

    /**
     * The metric one FR aspect is scored on in the ablation: the aspect, the capability-flag
     * field it toggles, the metric group and metric to read, and whether the aspect is shared
     * by Existing and Proposed rather than a differentiator between them.
     */
    private record FrMetricSpec(FrAspect aspect, String field, String metricGroup, String metric, boolean sharedNotDifferentiator) {
    }

    private static final List<FrMetricSpec> FR_METRICS = List.of(
            new FrMetricSpec(FrAspect.FR1_MULTIMODAL, "multimodal", "trip", "itineraryFoundRatePct", false),
            new FrMetricSpec(FrAspect.FR2_AGGREGATION, "aggregation", "trip", "itineraryFoundRatePct", false),
            new FrMetricSpec(FrAspect.FR3_RANKING, "multiCriteriaRanking", "trip", "meanAlternativesShown", true),
            new FrMetricSpec(FrAspect.FR4_REALTIME_STATUS, "realtimeStatus", "realtime", "meanAbsEtaErrorMin", true),
            new FrMetricSpec(FrAspect.FR5_CROWDSOURCING, "crowdsourcing", "realtime", "crowdingSystemWideAccuracyPct", false),
            new FrMetricSpec(FrAspect.FR6_PARATRANSIT, "paratransit", "trip", "endToEndSuccessRatePct", false),
            new FrMetricSpec(FrAspect.FR7_ACCESS_DISCOVERY, "accessDiscovery", "trip", "itineraryFoundRatePct", true),
            new FrMetricSpec(FrAspect.FR8_LASTMILE_FALLBACK, "lastmileFallback", "paratransit", "matchRatePct", false)
    );

    private final NetworkGenerator networkGenerator;
    private final ItineraryEngine itineraryEngine;
    private final RealtimeEngine realtimeEngine;
    private final ParatransitEngine paratransitEngine;

    public ComparisonService(NetworkGenerator networkGenerator, ItineraryEngine itineraryEngine,
                              RealtimeEngine realtimeEngine, ParatransitEngine paratransitEngine) {
        this.networkGenerator = networkGenerator;
        this.itineraryEngine = itineraryEngine;
        this.realtimeEngine = realtimeEngine;
        this.paratransitEngine = paratransitEngine;
    }

    // Shared inputs

    public NetworkData buildNetwork() {
        return buildNetwork(SimulationParameters.DEFAULTS);
    }

    public NetworkData buildNetwork(SimulationParameters p) {
        return networkGenerator.generate(new Random(p.networkSeed()), p);
    }

    public SharedInputs buildSharedInputs() {
        return buildSharedInputs(SimulationParameters.DEFAULTS);
    }

    public SharedInputs buildSharedInputs(SimulationParameters p) {
        Random rngQ = new Random(p.querySeed());
        List<Query> queries = new ArrayList<>();
        for (int i = 0; i < N_QUERIES; i++) {
            double ox = RandomUtils.uniform(rngQ, 0, p.areaXKm());
            double oy = RandomUtils.uniform(rngQ, 0, p.areaYKm());
            double dx = RandomUtils.uniform(rngQ, 0, p.areaXKm());
            double dy = RandomUtils.uniform(rngQ, 0, p.areaYKm());
            double depart = RandomUtils.uniform(rngQ, 6 * 60, 21 * 60);
            queries.add(new Query(ox, oy, dx, dy, depart, (int) (depart / 60)));
        }

        Random rngD = new Random(p.dataSeed());
        List<String> tripIds = new ArrayList<>();
        for (int i = 0; i < N_TRIPS; i++) tripIds.add("TRIP" + i);
        var groundTruth = realtimeEngine.simulateGroundTruth(tripIds, rngD, p.disruptionProbability());
        var officialFeed = realtimeEngine.simulateOfficialFeed(tripIds, groundTruth, rngD, p);
        var reports = realtimeEngine.simulateCrowdsourcedReports(tripIds, groundTruth, rngD, p);
        var consensus = realtimeEngine.runConsensusPipeline(tripIds, reports, p);

        Random rngH = new Random(p.dispatchSeed());
        int[] dispatchHours = new int[N_DISPATCH];
        for (int i = 0; i < N_DISPATCH; i++) dispatchHours[i] = RandomUtils.uniformInt(rngH, 5, 23);

        return new SharedInputs(queries, tripIds, groundTruth, officialFeed, reports, consensus, dispatchHours);
    }

    // Scenario evaluation

    public TripPlanningMetrics evaluateTripPlanning(NetworkData net, Capabilities caps, List<Query> queries, long seed) {
        return evaluateTripPlanning(net, caps, queries, seed, 1.0);
    }

    public TripPlanningMetrics evaluateTripPlanning(NetworkData net, Capabilities caps, List<Query> queries, long seed,
                                                    double driverRateMultiplier) {
        Random rng = new Random(seed);
        int found = 0, needingParatransit = 0, successes = 0;
        double altSum = 0;
        List<Double> ptWaits = new ArrayList<>();

        for (Query q : queries) {
            FullItineraryResult out = itineraryEngine.fullItineraryQuery(net, new double[]{q.ox(), q.oy()},
                    new double[]{q.dx(), q.dy()}, q.departMin(), rng, caps);
            if (out.ptOption() != null) {
                found++;
                successes++;
                altSum += caps.multiCriteriaRanking() ? out.ptOption().paretoFrontSize()
                        : (out.ptOption().paretoFrontSize() > 0 ? 1 : 0);
            } else {
                needingParatransit++;
                DispatchOutcome disp = paratransitEngine.requestParatransit(q.hour(), rng, caps, driverRateMultiplier);
                if (disp != null && disp.status().equals("MATCHED")) {
                    successes++;
                    ptWaits.add(disp.waitMin());
                }
            }
        }

        int n = queries.size();
        Double meanWait = ptWaits.isEmpty() ? null : ptWaits.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return new TripPlanningMetrics(
                (double) found / n * 100,
                (double) successes / n * 100,
                found > 0 ? altSum / found : 0.0,
                (double) needingParatransit / n * 100,
                meanWait
        );
    }

    public RealtimeMetrics evaluateRealtime(Capabilities caps, SharedInputs shared) {
        return realtimeEngine.evaluateRealtimeLayer(shared.tripIds(), shared.groundTruth(),
                shared.officialFeed(), shared.consensus(), caps);
    }

    public ParatransitDirectMetrics evaluateParatransitDirect(Capabilities caps, int[] hours, long seed) {
        return evaluateParatransitDirect(caps, hours, seed, 1.0);
    }

    public ParatransitDirectMetrics evaluateParatransitDirect(Capabilities caps, int[] hours, long seed, double driverRateMultiplier) {
        if (!caps.paratransit()) {
            return new ParatransitDirectMetrics(false, 0.0, null);
        }
        Random rng = new Random(seed);
        List<DispatchOutcome> outcomes = new ArrayList<>();
        for (int h : hours) outcomes.add(paratransitEngine.requestParatransit(h, rng, caps, driverRateMultiplier));
        List<DispatchOutcome> matched = outcomes.stream().filter(o -> o.status().equals("MATCHED")).toList();
        Double meanWait = matched.isEmpty() ? null : matched.stream().mapToDouble(DispatchOutcome::waitMin).average().orElse(0.0);
        return new ParatransitDirectMetrics(true, (double) matched.size() / outcomes.size() * 100, meanWait);
    }

    public ScenarioResult evaluateScenario(NetworkData net, Capabilities caps, SharedInputs shared) {
        return evaluateScenario(net, caps, shared, SimulationParameters.DEFAULTS);
    }

    public ScenarioResult evaluateScenario(NetworkData net, Capabilities caps, SharedInputs shared, SimulationParameters p) {
        TripPlanningMetrics trip = evaluateTripPlanning(net, caps, shared.queries(), p.tripEvaluationSeed(), p.driverRateMultiplier());
        RealtimeMetrics realtime = evaluateRealtime(caps, shared);
        ParatransitDirectMetrics pt = evaluateParatransitDirect(caps, shared.dispatchHours(), p.paratransitEvaluationSeed(), p.driverRateMultiplier());
        return new ScenarioResult(trip, realtime, pt);
    }

    // Isolated single-factor ablation

    public List<AblationRow> runIsolatedAblation(NetworkData net, SharedInputs shared) {
        return runIsolatedAblation(net, shared, SimulationParameters.DEFAULTS);
    }

    public List<AblationRow> runIsolatedAblation(NetworkData net, SharedInputs shared, SimulationParameters p) {
        ScenarioResult proposedEval = evaluateScenario(net, Capabilities.PROPOSED, shared, p);
        List<AblationRow> rows = new ArrayList<>();

        for (FrMetricSpec spec : FR_METRICS) {
            boolean existingValue = Capabilities.EXISTING.get(spec.aspect());
            Capabilities ablated = Capabilities.PROPOSED.toggled(spec.aspect(), existingValue);
            ScenarioResult ablatedEval = evaluateScenario(net, ablated, shared, p);

            double proposedVal = extractMetric(proposedEval, spec);
            double ablatedVal = extractMetric(ablatedEval, spec);

            rows.add(new AblationRow(spec.aspect().label, spec.field(), spec.metric(),
                    proposedVal, ablatedVal, proposedVal - ablatedVal, spec.sharedNotDifferentiator()));
        }
        return rows;
    }

    private double extractMetric(ScenarioResult result, FrMetricSpec spec) {
        return switch (spec.metricGroup()) {
            case "trip" -> switch (spec.metric()) {
                case "itineraryFoundRatePct" -> result.tripPlanning().itineraryFoundRatePct();
                case "meanAlternativesShown" -> result.tripPlanning().meanAlternativesShown();
                case "endToEndSuccessRatePct" -> result.tripPlanning().endToEndSuccessRatePct();
                default -> throw new IllegalArgumentException("Unknown trip metric: " + spec.metric());
            };
            case "realtime" -> switch (spec.metric()) {
                case "meanAbsEtaErrorMin" -> result.realtime().meanAbsEtaErrorMin();
                case "crowdingSystemWideAccuracyPct" -> result.realtime().crowdingSystemWideAccuracyPct();
                default -> throw new IllegalArgumentException("Unknown realtime metric: " + spec.metric());
            };
            case "paratransit" -> switch (spec.metric()) {
                case "matchRatePct" -> result.paratransitDirect().matchRatePct();
                default -> throw new IllegalArgumentException("Unknown paratransit metric: " + spec.metric());
            };
            default -> throw new IllegalArgumentException("Unknown metric group: " + spec.metricGroup());
        };
    }

    // Fully stacked comparison

    public Map<String, ScenarioResult> runStackedComparison(NetworkData net, SharedInputs shared) {
        return runStackedComparison(net, shared, SimulationParameters.DEFAULTS);
    }

    public Map<String, ScenarioResult> runStackedComparison(NetworkData net, SharedInputs shared, SimulationParameters p) {
        Map<String, ScenarioResult> result = new LinkedHashMap<>();
        result.put("existing", evaluateScenario(net, Capabilities.EXISTING, shared, p));
        result.put("proposed", evaluateScenario(net, Capabilities.PROPOSED, shared, p));
        return result;
    }
}
