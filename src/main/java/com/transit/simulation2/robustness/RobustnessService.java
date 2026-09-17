package com.transit.simulation2.robustness;

import com.transit.simulation2.*;
import com.transit.simulation2.capabilities.Capabilities;
import com.transit.simulation2.capabilities.Capabilities.FrAspect;
import com.transit.simulation2.config.SimulationParameters;
import com.transit.simulation2.itinerary.FullItineraryResult;
import com.transit.simulation2.itinerary.ItineraryEngine;
import com.transit.simulation2.itinerary.ItineraryView;
import com.transit.simulation2.model.Connection;
import com.transit.simulation2.model.NetworkData;
import com.transit.simulation2.model.Route;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

/**
 * The repeated-seed analysis and the one-at-a-time sensitivity sweeps. The paper releases their
 * output with this implementation rather than reporting it, so they correspond to no table of the
 * paper. Every run is
 * {@link ComparisonService#evaluateScenario(NetworkData, Capabilities, SharedInputs, SimulationParameters)}
 * over a network and shared inputs built from a single {@link SimulationParameters} instance,
 * so both analyses exercise the same engines and the same evaluation code as the baseline
 * comparison.
 *
 * Runs execute in parallel and results are cached per request signature, because a full
 * repeated-seed run takes about a minute and a full sensitivity run several minutes.
 */
@Service
public class RobustnessService {

    public static final int DEFAULT_SEED_SETS = 30;
    public static final int DEFAULT_SEEDS_PER_LEVEL = 10;

    // Result types

    /** All metrics of one run (one parameter set, one seed set), as a flat map for easy tabulation. */
    public record RunResult(int seedSet, SimulationParameters parameters, Map<String, Double> metrics) {
    }

    /** Distribution of one metric across the runs it was collected from. */
    public record MetricSummary(String metric, int n, double mean, double sd, double min, double max) {
    }

    /**
     * The repeated-seed analysis: every run, the per-metric distribution across them, and how
     * many runs preserved the direction of each gain the paper reports.
     */
    public record RepeatedSeedResult(int seedSets, boolean networkFixed, List<MetricSummary> summary,
                                     Map<String, Integer> directionCounts, List<RunResult> runs) {
    }

    /** One level of one sensitivity sweep, with every metric averaged over the seed sets evaluated at that level. */
    public record SweepLevel(String parameter, String level, int seedSets, Map<String, Double> mean, Map<String, Double> sd) {
    }

    /** The sensitivity sweeps that were run, and every level of each of them. */
    public record SensitivityResult(int seedsPerLevel, List<String> parameters, List<SweepLevel> levels) {
    }

    /** Realised properties of one generated network plus the modal share of the fastest feasible itineraries. */
    public record NetworkStatistics(
            int stops, int servedStops, int unservedStops, int transferNodes, int busRailInterchanges, int railStops,
            int overlappingRoutePairs, int routePairs, double routeKm, double meanInterStopKm, int connections, int vehicleTrips,
            double railShareOfFastestItineraryPct, double meanTransfersFastestItinerary, double directItinerarySharePct,
            double meanQueryCrowFlyKm) {
    }

    // Sweep definitions

    /** One level of a sweep: its label in the results, and how it derives that level's parameter set from the baseline. */
    public record Level(String label, UnaryOperator<SimulationParameters> apply) {
    }

    /** One sensitivity sweep: the parameter it varies and the levels it varies it over. */
    public record Sweep(String parameter, String description, List<Level> levels) {
    }

    /** The one-at-a-time sweeps, each of which includes the baseline level. */
    public static final List<Sweep> SWEEPS = List.of(
            new Sweep("officialFeedCoverage", "Official real-time feed coverage gamma",
                    levels(new double[]{0.25, 0.40, 0.55, 0.70, 0.85}, v -> "" + v, (p, v) -> p.withOfficialFeedCoverage(v))),
            new Sweep("contributorReliability", "Contributor reliability ~ Beta(alpha, 2); level = mean reliability",
                    levels(new double[]{2, 4, 6, 8, 12}, v -> String.format(Locale.ROOT, "%.2f", v / (v + 2)), (p, v) -> p.withReliabilityBeta(v, 2))),
            new Sweep("consensusMinReports", "Consensus rule: minimum in-window reports K",
                    levels(new double[]{2, 3, 4, 5}, v -> "" + (int) v, (p, v) -> p.withConsensus((int) v, p.consensusAgreementThreshold()))),
            new Sweep("consensusAgreementThreshold", "Consensus rule: agreement threshold",
                    levels(new double[]{0.50, 0.65, 0.80}, v -> "" + v, (p, v) -> p.withConsensus(p.consensusMinReports(), v))),
            new Sweep("meanReportersPerTrip", "Participation: mean reports per trip instance",
                    levels(new double[]{1, 2, 3, 5, 8}, v -> "" + v, (p, v) -> p.withMeanReportersPerTrip(v))),
            new Sweep("randomSpamContributorFraction", "Share of contributors that always report random spam",
                    levels(new double[]{0.05, 0.10, 0.20, 0.30, 0.50}, v -> "" + v, (p, v) -> p.withSpam(v, false))),
            new Sweep("colludingSpamContributorFraction", "Share of contributors that collude on the same false delay",
                    levels(new double[]{0.05, 0.10, 0.20, 0.30, 0.50}, v -> "" + v, (p, v) -> p.withSpam(v, true))),
            new Sweep("driverRateMultiplier", "Para-transit availability: driver arrival rate multiplier",
                    levels(new double[]{0.5, 0.75, 1.0, 1.5, 2.0}, v -> "" + v, (p, v) -> p.withDriverRateMultiplier(v))),
            new Sweep("stops", "Network topology: number of stops (same ten routes)",
                    levels(new double[]{25, 40, 60, 80}, v -> "" + (int) v, (p, v) -> p.withStops((int) v))),
            new Sweep("stopPlacement", "Network topology: stop placement",
                    List.of(new Level("uniform", p -> p.withClusteredStops(false)),
                            new Level("clustered", p -> p.withClusteredStops(true))))
    );

    private interface LevelApplier {
        SimulationParameters apply(SimulationParameters p, double v);
    }

    private static List<Level> levels(double[] values, java.util.function.DoubleFunction<String> label, LevelApplier applier) {
        List<Level> out = new ArrayList<>();
        for (double v : values) out.add(new Level(label.apply(v), p -> applier.apply(p, v)));
        return out;
    }

    // State

    private final ComparisonService comparison;
    private final ItineraryEngine itineraryEngine;
    private final Map<String, RepeatedSeedResult> repeatedCache = new ConcurrentHashMap<>();
    private final Map<String, SensitivityResult> sensitivityCache = new ConcurrentHashMap<>();

    public RobustnessService(ComparisonService comparison, ItineraryEngine itineraryEngine) {
        this.comparison = comparison;
        this.itineraryEngine = itineraryEngine;
    }

    // A single run

    /**
     * Evaluates Existing, Proposed and the five differentiating single-factor ablations for one
     * parameter set, and returns everything as a flat metric map. With
     * {@link SimulationParameters#DEFAULTS} it yields the same values as the baseline endpoints.
     */
    public Map<String, Double> evaluateAll(SimulationParameters p) {
        NetworkData net = comparison.buildNetwork(p);
        SharedInputs shared = comparison.buildSharedInputs(p);

        ScenarioResult existing = comparison.evaluateScenario(net, Capabilities.EXISTING, shared, p);
        ScenarioResult proposed = comparison.evaluateScenario(net, Capabilities.PROPOSED, shared, p);
        ScenarioResult noFr1 = comparison.evaluateScenario(net, Capabilities.PROPOSED.toggled(FrAspect.FR1_MULTIMODAL, false), shared, p);
        ScenarioResult noFr2 = comparison.evaluateScenario(net, Capabilities.PROPOSED.toggled(FrAspect.FR2_AGGREGATION, false), shared, p);
        ScenarioResult noFr5 = comparison.evaluateScenario(net, Capabilities.PROPOSED.toggled(FrAspect.FR5_CROWDSOURCING, false), shared, p);
        ScenarioResult noFr6 = comparison.evaluateScenario(net, Capabilities.PROPOSED.toggled(FrAspect.FR6_PARATRANSIT, false), shared, p);
        ScenarioResult noFr8 = comparison.evaluateScenario(net, Capabilities.PROPOSED.toggled(FrAspect.FR8_LASTMILE_FALLBACK, false), shared, p);

        Map<String, Double> m = new LinkedHashMap<>();
        putScenario(m, "existing", existing);
        putScenario(m, "proposed", proposed);

        m.put("ablation.fr1Removed.itineraryFoundRatePct", noFr1.tripPlanning().itineraryFoundRatePct());
        m.put("ablation.fr2Removed.itineraryFoundRatePct", noFr2.tripPlanning().itineraryFoundRatePct());
        m.put("ablation.fr5Removed.crowdingSystemWideAccuracyPct", noFr5.realtime().crowdingSystemWideAccuracyPct());
        m.put("ablation.fr6Removed.endToEndSuccessRatePct", noFr6.tripPlanning().endToEndSuccessRatePct());
        m.put("ablation.fr8Removed.paratransitMatchRatePct", noFr8.paratransitDirect().matchRatePct());
        m.put("delta.fr1.itineraryFoundRatePct", proposed.tripPlanning().itineraryFoundRatePct() - noFr1.tripPlanning().itineraryFoundRatePct());
        m.put("delta.fr2.itineraryFoundRatePct", proposed.tripPlanning().itineraryFoundRatePct() - noFr2.tripPlanning().itineraryFoundRatePct());
        m.put("delta.fr5.crowdingSystemWideAccuracyPct", proposed.realtime().crowdingSystemWideAccuracyPct() - noFr5.realtime().crowdingSystemWideAccuracyPct());
        m.put("delta.fr6.endToEndSuccessRatePct", proposed.tripPlanning().endToEndSuccessRatePct() - noFr6.tripPlanning().endToEndSuccessRatePct());
        m.put("delta.fr8.paratransitMatchRatePct", proposed.paratransitDirect().matchRatePct() - noFr8.paratransitDirect().matchRatePct());

        NetworkStatistics ns = networkStatistics(net, shared, p);
        m.put("network.servedStops", (double) ns.servedStops());
        m.put("network.transferNodes", (double) ns.transferNodes());
        m.put("network.busRailInterchanges", (double) ns.busRailInterchanges());
        m.put("network.overlappingRoutePairs", (double) ns.overlappingRoutePairs());
        m.put("network.routeKm", ns.routeKm());
        m.put("network.meanInterStopKm", ns.meanInterStopKm());
        m.put("network.railShareOfFastestItineraryPct", ns.railShareOfFastestItineraryPct());
        m.put("network.meanTransfersFastestItinerary", ns.meanTransfersFastestItinerary());
        m.put("reports.meanPerTrip", (double) shared.reports().size() / shared.tripIds().size());
        return m;
    }

    private static void putScenario(Map<String, Double> m, String prefix, ScenarioResult s) {
        m.put(prefix + ".itineraryFoundRatePct", s.tripPlanning().itineraryFoundRatePct());
        m.put(prefix + ".endToEndSuccessRatePct", s.tripPlanning().endToEndSuccessRatePct());
        m.put(prefix + ".meanAlternativesShown", s.tripPlanning().meanAlternativesShown());
        m.put(prefix + ".pctNeedingWholeTripParatransit", s.tripPlanning().pctNeedingWholeTripParatransit());
        m.put(prefix + ".meanAbsEtaErrorMin", s.realtime().meanAbsEtaErrorMin());
        m.put(prefix + ".crowdingKnownRatePct", s.realtime().crowdingKnownRatePct());
        m.put(prefix + ".crowdingAccuracyWhenKnownPct", s.realtime().crowdingAccuracyWhenKnownPct());
        m.put(prefix + ".crowdingSystemWideAccuracyPct", s.realtime().crowdingSystemWideAccuracyPct());
        m.put(prefix + ".paratransitMatchRatePct", s.paratransitDirect().matchRatePct());
        m.put(prefix + ".paratransitMeanWaitMin", s.paratransitDirect().meanWaitMin());   // null when unavailable
    }

    // Repeated-seed analysis

    /**
     * Re-runs the complete experiment for {@code seedSets} seed sets, of which set 0 is the
     * baseline configuration. With {@code networkFixed} the network seed stays at its baseline
     * value so that only the shared stochastic inputs vary; otherwise the topology varies too.
     */
    public RepeatedSeedResult runRepeatedSeeds(int seedSets, boolean networkFixed) {
        String key = seedSets + ":" + networkFixed;
        return repeatedCache.computeIfAbsent(key, k -> computeRepeatedSeeds(seedSets, networkFixed));
    }

    private RepeatedSeedResult computeRepeatedSeeds(int seedSets, boolean networkFixed) {
        List<RunResult> runs = IntStream.range(0, seedSets).parallel().mapToObj(i -> {
            SimulationParameters p = SimulationParameters.DEFAULTS.forSeedSet(i);
            if (networkFixed) p = p.withNetworkSeed(SimulationParameters.DEFAULTS.networkSeed());
            return new RunResult(i, p, evaluateAll(p));
        }).sorted(Comparator.comparingInt(RunResult::seedSet)).toList();

        List<MetricSummary> summary = summarise(runs.stream().map(RunResult::metrics).toList());

        Map<String, Integer> directions = new LinkedHashMap<>();
        directions.put("proposedFindsMoreItineraries", count(runs, m -> m.get("proposed.itineraryFoundRatePct") > m.get("existing.itineraryFoundRatePct")));
        directions.put("proposedHigherEndToEndSuccess", count(runs, m -> m.get("proposed.endToEndSuccessRatePct") > m.get("existing.endToEndSuccessRatePct")));
        directions.put("proposedLowerEtaError", count(runs, m -> m.get("proposed.meanAbsEtaErrorMin") < m.get("existing.meanAbsEtaErrorMin")));
        directions.put("coordinatedDispatchBeatsNaive", count(runs, m -> m.get("proposed.paratransitMatchRatePct") > m.get("ablation.fr8Removed.paratransitMatchRatePct")));
        directions.put("fr2DeltaExceedsFr1Delta", count(runs, m -> m.get("delta.fr2.itineraryFoundRatePct") > m.get("delta.fr1.itineraryFoundRatePct")));
        directions.put("runs", runs.size());

        return new RepeatedSeedResult(seedSets, networkFixed, summary, directions, runs);
    }

    private static int count(List<RunResult> runs, java.util.function.Predicate<Map<String, Double>> test) {
        return (int) runs.stream().filter(r -> test.test(r.metrics())).count();
    }

    // Sensitivity sweep execution

    /**
     * One-at-a-time sweeps around the baseline. Each level is evaluated for seed sets
     * 0..seedsPerLevel-1 and reported as mean and SD. {@code parameter} restricts the run to
     * one sweep (null or blank = all sweeps).
     */
    public SensitivityResult runSensitivity(int seedsPerLevel, String parameter) {
        String key = seedsPerLevel + ":" + (parameter == null ? "" : parameter);
        return sensitivityCache.computeIfAbsent(key, k -> computeSensitivity(seedsPerLevel, parameter));
    }

    private SensitivityResult computeSensitivity(int seedsPerLevel, String parameter) {
        List<Sweep> sweeps = SWEEPS.stream()
                .filter(s -> parameter == null || parameter.isBlank() || s.parameter().equalsIgnoreCase(parameter))
                .toList();
        if (sweeps.isEmpty()) {
            throw new IllegalArgumentException("Unknown sweep parameter '" + parameter + "'. Known: " + sweepParameters());
        }

        record Job(Sweep sweep, Level level, int seedSet) {
        }
        List<Job> jobs = new ArrayList<>();
        for (Sweep s : sweeps) for (Level l : s.levels()) for (int i = 0; i < seedsPerLevel; i++) jobs.add(new Job(s, l, i));

        Map<Job, Map<String, Double>> results = new ConcurrentHashMap<>();
        jobs.parallelStream().forEach(j -> results.put(j, evaluateAll(j.level().apply().apply(SimulationParameters.DEFAULTS.forSeedSet(j.seedSet())))));

        List<SweepLevel> out = new ArrayList<>();
        for (Sweep s : sweeps) {
            for (Level l : s.levels()) {
                List<Map<String, Double>> perSeed = new ArrayList<>();
                for (int i = 0; i < seedsPerLevel; i++) perSeed.add(results.get(new Job(s, l, i)));
                Map<String, Double> mean = new LinkedHashMap<>();
                Map<String, Double> sd = new LinkedHashMap<>();
                for (MetricSummary ms : summarise(perSeed)) {
                    mean.put(ms.metric(), ms.mean());
                    sd.put(ms.metric(), ms.sd());
                }
                out.add(new SweepLevel(s.parameter(), l.label(), seedsPerLevel, mean, sd));
            }
        }
        return new SensitivityResult(seedsPerLevel, sweeps.stream().map(Sweep::parameter).toList(), out);
    }

    public List<String> sweepParameters() {
        return SWEEPS.stream().map(Sweep::parameter).toList();
    }

    // Network statistics

    public NetworkStatistics networkStatistics(NetworkData net, SharedInputs shared, SimulationParameters p) {
        Map<String, Set<String>> routesAtStop = new HashMap<>();
        Map<String, Set<String>> modesAtStop = new HashMap<>();
        for (Route rt : net.getRoutes()) {
            for (String s : rt.getStopIds()) {
                routesAtStop.computeIfAbsent(s, k -> new HashSet<>()).add(rt.getId());
                modesAtStop.computeIfAbsent(s, k -> new HashSet<>()).add(rt.getMode());
            }
        }
        int served = routesAtStop.size();
        int transfer = 0, interchange = 0, railServed = 0;
        for (Set<String> routes : routesAtStop.values()) if (routes.size() >= 2) transfer++;
        for (Set<String> modes : modesAtStop.values()) {
            if (modes.size() == 2) interchange++;
            if (modes.contains("rail")) railServed++;
        }
        List<Route> rs = net.getRoutes();
        int overlapPairs = 0, pairs = 0;
        for (int i = 0; i < rs.size(); i++) {
            for (int j = i + 1; j < rs.size(); j++) {
                pairs++;
                Set<String> a = new HashSet<>(rs.get(i).getStopIds());
                a.retainAll(rs.get(j).getStopIds());
                if (!a.isEmpty()) overlapPairs++;
            }
        }
        double routeKm = 0;
        int legs = 0;
        for (Route rt : rs) {
            for (int i = 0; i < rt.getStopIds().size() - 1; i++) {
                routeKm += net.getStop(rt.getStopIds().get(i)).distanceTo(net.getStop(rt.getStopIds().get(i + 1)));
                legs++;
            }
        }
        int trips = (int) net.getConnections().stream().map(Connection::tripId).distinct().count();

        // Modal share and transfers of the fastest feasible itinerary (Proposed configuration, min_time ranking).
        Random rng = new Random(p.tripEvaluationSeed());
        int found = 0, rail = 0, direct = 0;
        double transfers = 0, crowFly = 0;
        for (Query q : shared.queries()) {
            crowFly += Math.hypot(q.ox() - q.dx(), q.oy() - q.dy());
            FullItineraryResult out = itineraryEngine.fullItineraryQuery(net, new double[]{q.ox(), q.oy()},
                    new double[]{q.dx(), q.dy()}, q.departMin(), rng, Capabilities.PROPOSED);
            if (out.ptOption() == null) continue;
            List<ItineraryView> fastest = out.ptOption().topByMode().get("min_time");
            if (fastest == null || fastest.isEmpty()) continue;
            ItineraryView top = fastest.get(0);
            found++;
            if (top.path().stream().anyMatch(c -> c.mode().equals("rail"))) rail++;
            if (top.transfers() == 0) direct++;
            transfers += top.transfers();
        }
        int n = shared.queries().size();
        return new NetworkStatistics(net.getStops().size(), served, net.getStops().size() - served, transfer, interchange, railServed,
                overlapPairs, pairs, routeKm, legs > 0 ? routeKm / legs : 0.0, net.getConnections().size(), trips,
                found > 0 ? 100.0 * rail / found : 0.0, found > 0 ? transfers / found : 0.0, found > 0 ? 100.0 * direct / found : 0.0,
                n > 0 ? crowFly / n : 0.0);
    }

    // Helpers

    private static List<MetricSummary> summarise(List<Map<String, Double>> runs) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (Map<String, Double> r : runs) keys.addAll(r.keySet());
        List<MetricSummary> out = new ArrayList<>();
        for (String k : keys) {
            double[] v = runs.stream().map(r -> r.get(k)).filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
            if (v.length == 0) continue;
            double mean = Arrays.stream(v).average().orElse(0.0);
            double var = v.length > 1 ? Arrays.stream(v).map(x -> (x - mean) * (x - mean)).sum() / (v.length - 1) : 0.0;
            out.add(new MetricSummary(k, v.length, mean, Math.sqrt(var), Arrays.stream(v).min().orElse(0.0), Arrays.stream(v).max().orElse(0.0)));
        }
        return out;
    }
}
