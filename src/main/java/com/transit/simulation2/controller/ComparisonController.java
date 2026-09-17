package com.transit.simulation2.controller;

import com.transit.simulation2.*;
import com.transit.simulation2.capabilities.Capabilities;
import com.transit.simulation2.config.SimulationParameters;
import com.transit.simulation2.model.NetworkData;
import com.transit.simulation2.robustness.RobustnessService;
import com.transit.simulation2.util.Csv;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for the capability-flag Existing vs Proposed comparison. The network and the shared
 * stochastic inputs are built once from fixed seeds and cached, so every endpoint sees exactly
 * the same inputs and repeated calls return identical results.
 *
 * The robustness endpoints (/network-stats, /repeated-seeds, /sensitivity and
 * /scenario/parameterised) run the same engines with an explicit {@link SimulationParameters} in
 * place of the baseline one. The paper releases their output with this implementation rather than
 * reporting it.
 */
@RestController
@RequestMapping("/api/comparison")
@CrossOrigin(origins = "*")
public class ComparisonController {

    private final ComparisonService comparisonService;
    private final RobustnessService robustnessService;
    private volatile NetworkData network;
    private volatile SharedInputs sharedInputs;

    public ComparisonController(ComparisonService comparisonService, RobustnessService robustnessService) {
        this.comparisonService = comparisonService;
        this.robustnessService = robustnessService;
    }

    private synchronized NetworkData net() {
        if (network == null) network = comparisonService.buildNetwork();
        return network;
    }

    private synchronized SharedInputs shared() {
        if (sharedInputs == null) sharedInputs = comparisonService.buildSharedInputs();
        return sharedInputs;
    }

    @GetMapping("/network")
    public NetworkData getNetwork() {
        return net();
    }

    @GetMapping("/capabilities")
    public Map<String, Capabilities> capabilities() {
        return Map.of("existing", Capabilities.EXISTING, "proposed", Capabilities.PROPOSED);
    }

    @GetMapping("/ablation")
    public List<AblationRow> isolatedAblation() {
        return comparisonService.runIsolatedAblation(net(), shared());
    }

    @GetMapping("/stacked")
    public Map<String, ScenarioResult> stackedComparison() {
        return comparisonService.runStackedComparison(net(), shared());
    }

    @GetMapping("/scenario/existing")
    public ScenarioResult evaluateExisting() {
        return comparisonService.evaluateScenario(net(), Capabilities.EXISTING, shared());
    }

    @GetMapping("/scenario/proposed")
    public ScenarioResult evaluateProposed() {
        return comparisonService.evaluateScenario(net(), Capabilities.PROPOSED, shared());
    }

    /** Evaluate an arbitrary custom capability configuration, e.g. to explore combinations beyond Existing/Proposed. */
    @GetMapping("/scenario/custom")
    public ScenarioResult evaluateCustom(
            @RequestParam(defaultValue = "true") boolean multimodal,
            @RequestParam(defaultValue = "true") boolean aggregation,
            @RequestParam(defaultValue = "true") boolean multiCriteriaRanking,
            @RequestParam(defaultValue = "true") boolean realtimeStatus,
            @RequestParam(defaultValue = "true") boolean crowdsourcing,
            @RequestParam(defaultValue = "true") boolean paratransit,
            @RequestParam(defaultValue = "true") boolean accessDiscovery,
            @RequestParam(defaultValue = "true") boolean lastmileFallback) {
        Capabilities caps = new Capabilities(multimodal, aggregation, multiCriteriaRanking, realtimeStatus,
                crowdsourcing, paratransit, accessDiscovery, lastmileFallback);
        return comparisonService.evaluateScenario(net(), caps, shared());
    }

    @GetMapping("/full")
    public Map<String, Object> full() {
        return Map.of(
                "capabilities", capabilities(),
                "isolatedAblation", isolatedAblation(),
                "stacked", stackedComparison()
        );
    }

    // Robustness analysis

    /** The baseline parameter set: every constant the sweeps can vary, plus the six seeds. */
    @GetMapping("/parameters")
    public SimulationParameters parameters() {
        return SimulationParameters.DEFAULTS;
    }

    /** Realised properties of the baseline network (served stops, transfer nodes, bus/rail interchanges, route overlap, modal share...). */
    @GetMapping("/network-stats")
    public RobustnessService.NetworkStatistics networkStats() {
        return robustnessService.networkStatistics(net(), shared(), SimulationParameters.DEFAULTS);
    }

    /**
     * Repeated-seed analysis: the complete experiment re-run for n seed sets, of which set 0 is
     * the baseline configuration. With fixNetwork=true the network seed stays at its baseline
     * value so that only the shared stochastic inputs vary. n is clamped to 1..200. The runs execute in
     * parallel and the result is cached after the first call.
     */
    @GetMapping("/repeated-seeds")
    public RobustnessService.RepeatedSeedResult repeatedSeeds(
            @RequestParam(defaultValue = "30") int n,
            @RequestParam(defaultValue = "false") boolean fixNetwork) {
        return robustnessService.runRepeatedSeeds(Math.max(1, Math.min(n, 200)), fixNetwork);
    }

    /**
     * One-at-a-time sensitivity sweeps. Each level is averaged over {@code seeds} seed
     * sets, clamped to 1..100; {@code parameter} restricts the run to a single sweep, named as in
     * /sensitivity/parameters, and an unknown name is rejected. The runs execute in parallel and
     * the result is cached after the first call.
     */
    @GetMapping("/sensitivity")
    public RobustnessService.SensitivityResult sensitivity(
            @RequestParam(defaultValue = "10") int seeds,
            @RequestParam(required = false) String parameter) {
        return robustnessService.runSensitivity(Math.max(1, Math.min(seeds, 100)), parameter);
    }

    @GetMapping("/sensitivity/parameters")
    public List<String> sensitivityParameters() {
        return robustnessService.sweepParameters();
    }

    /**
     * Evaluate any capability setting under any parameter set. Every query parameter is optional:
     * the eight capability flags default to their Proposed values, and each modelling constant
     * and seed defaults to its baseline value. The trip and para-transit evaluation seeds are
     * always the baseline ones, so two calls that differ only in the other parameters stay
     * comparable. Example:
     * /scenario/parameterised?officialFeedCoverage=0.25&consensusMinReports=2&networkSeed=1042
     */
    @GetMapping("/scenario/parameterised")
    public Map<String, Object> evaluateParameterised(
            @RequestParam(defaultValue = "true") boolean multimodal,
            @RequestParam(defaultValue = "true") boolean aggregation,
            @RequestParam(defaultValue = "true") boolean multiCriteriaRanking,
            @RequestParam(defaultValue = "true") boolean realtimeStatus,
            @RequestParam(defaultValue = "true") boolean crowdsourcing,
            @RequestParam(defaultValue = "true") boolean paratransit,
            @RequestParam(defaultValue = "true") boolean accessDiscovery,
            @RequestParam(defaultValue = "true") boolean lastmileFallback,
            @RequestParam(required = false) Integer stops,
            @RequestParam(required = false) Boolean clusteredStops,
            @RequestParam(required = false) Double officialFeedCoverage,
            @RequestParam(required = false) Integer contributors,
            @RequestParam(required = false) Double meanReportersPerTrip,
            @RequestParam(required = false) Double reliabilityBetaAlpha,
            @RequestParam(required = false) Double reliabilityBetaBeta,
            @RequestParam(required = false) Double spamContributorFraction,
            @RequestParam(required = false) Boolean colludingSpam,
            @RequestParam(required = false) Integer consensusMinReports,
            @RequestParam(required = false) Double consensusAgreementThreshold,
            @RequestParam(required = false) Double driverRateMultiplier,
            @RequestParam(required = false) Long networkSeed,
            @RequestParam(required = false) Long querySeed,
            @RequestParam(required = false) Long dataSeed,
            @RequestParam(required = false) Long dispatchSeed) {
        SimulationParameters d = SimulationParameters.DEFAULTS;
        SimulationParameters p = d;
        if (stops != null) p = p.withStops(stops);
        if (clusteredStops != null) p = p.withClusteredStops(clusteredStops);
        if (officialFeedCoverage != null) p = p.withOfficialFeedCoverage(officialFeedCoverage);
        if (contributors != null || meanReportersPerTrip != null)
            p = p.withContributors(contributors != null ? contributors : d.contributors(),
                    meanReportersPerTrip != null ? meanReportersPerTrip : d.meanReportersPerTrip());
        if (reliabilityBetaAlpha != null || reliabilityBetaBeta != null)
            p = p.withReliabilityBeta(reliabilityBetaAlpha != null ? reliabilityBetaAlpha : d.reliabilityBetaAlpha(),
                    reliabilityBetaBeta != null ? reliabilityBetaBeta : d.reliabilityBetaBeta());
        if (spamContributorFraction != null || colludingSpam != null)
            p = p.withSpam(spamContributorFraction != null ? spamContributorFraction : d.spamContributorFraction(),
                    colludingSpam != null ? colludingSpam : d.colludingSpam());
        if (consensusMinReports != null || consensusAgreementThreshold != null)
            p = p.withConsensus(consensusMinReports != null ? consensusMinReports : d.consensusMinReports(),
                    consensusAgreementThreshold != null ? consensusAgreementThreshold : d.consensusAgreementThreshold());
        if (driverRateMultiplier != null) p = p.withDriverRateMultiplier(driverRateMultiplier);
        if (networkSeed != null || querySeed != null || dataSeed != null || dispatchSeed != null)
            p = p.withSeeds(networkSeed != null ? networkSeed : d.networkSeed(),
                    querySeed != null ? querySeed : d.querySeed(),
                    dataSeed != null ? dataSeed : d.dataSeed(),
                    dispatchSeed != null ? dispatchSeed : d.dispatchSeed(),
                    d.tripEvaluationSeed(), d.paratransitEvaluationSeed());

        Capabilities caps = new Capabilities(multimodal, aggregation, multiCriteriaRanking, realtimeStatus,
                crowdsourcing, paratransit, accessDiscovery, lastmileFallback);
        NetworkData n = comparisonService.buildNetwork(p);
        SharedInputs s = comparisonService.buildSharedInputs(p);
        return Map.of(
                "parameters", p,
                "capabilities", caps,
                "result", comparisonService.evaluateScenario(n, caps, s, p),
                "networkStats", robustnessService.networkStatistics(n, s, p)
        );
    }

    // CSV exports

    /*
     * Read-only CSV views of the responses above, for loading the results into a spreadsheet or a
     * statistics package. They delegate to the JSON endpoints, so they share the same caches,
     * parameter defaults and clamping, and they add no computation of their own. They are separate
     * paths rather than content negotiation on the existing ones, so that a request with
     * Accept: * / * keeps resolving to JSON exactly as before.
     */

    private static final String CSV = "text/csv;charset=UTF-8";

    /** The fully stacked comparison as one row per metric, with an Existing and a Proposed column. */
    @GetMapping(value = "/csv/stacked", produces = CSV)
    public String stackedCsv() {
        Map<String, ScenarioResult> r = stackedComparison();
        ScenarioResult e = r.get("existing");
        ScenarioResult p = r.get("proposed");
        StringBuilder out = new StringBuilder();
        Csv.row(out, "metricGroup", "metric", "existing", "proposed");
        Csv.row(out, "tripPlanning", "itineraryFoundRatePct", e.tripPlanning().itineraryFoundRatePct(), p.tripPlanning().itineraryFoundRatePct());
        Csv.row(out, "tripPlanning", "endToEndSuccessRatePct", e.tripPlanning().endToEndSuccessRatePct(), p.tripPlanning().endToEndSuccessRatePct());
        Csv.row(out, "tripPlanning", "meanAlternativesShown", e.tripPlanning().meanAlternativesShown(), p.tripPlanning().meanAlternativesShown());
        Csv.row(out, "tripPlanning", "pctNeedingWholeTripParatransit", e.tripPlanning().pctNeedingWholeTripParatransit(), p.tripPlanning().pctNeedingWholeTripParatransit());
        Csv.row(out, "tripPlanning", "meanWaitWhenParatransitNeededMin", e.tripPlanning().meanWaitWhenParatransitNeededMin(), p.tripPlanning().meanWaitWhenParatransitNeededMin());
        Csv.row(out, "realtime", "meanAbsEtaErrorMin", e.realtime().meanAbsEtaErrorMin(), p.realtime().meanAbsEtaErrorMin());
        Csv.row(out, "realtime", "crowdingKnownRatePct", e.realtime().crowdingKnownRatePct(), p.realtime().crowdingKnownRatePct());
        Csv.row(out, "realtime", "crowdingAccuracyWhenKnownPct", e.realtime().crowdingAccuracyWhenKnownPct(), p.realtime().crowdingAccuracyWhenKnownPct());
        Csv.row(out, "realtime", "crowdingSystemWideAccuracyPct", e.realtime().crowdingSystemWideAccuracyPct(), p.realtime().crowdingSystemWideAccuracyPct());
        Csv.row(out, "paratransitDirect", "available", e.paratransitDirect().available(), p.paratransitDirect().available());
        Csv.row(out, "paratransitDirect", "matchRatePct", e.paratransitDirect().matchRatePct(), p.paratransitDirect().matchRatePct());
        Csv.row(out, "paratransitDirect", "meanWaitMin", e.paratransitDirect().meanWaitMin(), p.paratransitDirect().meanWaitMin());
        return out.toString();
    }

    /** The isolated single-factor ablation as one row per FR aspect. */
    @GetMapping(value = "/csv/ablation", produces = CSV)
    public String ablationCsv() {
        StringBuilder out = new StringBuilder();
        Csv.row(out, "frAspect", "field", "metric", "proposedValue", "withCapabilityRemoved", "delta", "sharedNotDifferentiator");
        for (AblationRow row : isolatedAblation()) {
            Csv.row(out, row.frAspect(), row.field(), row.metric(),
                    row.proposedValue(), row.withCapabilityRemoved(), row.delta(), row.sharedNotDifferentiator());
        }
        return out.toString();
    }

    /**
     * The repeated-seed analysis in one table of three sections, distinguished by the first column:
     * {@code run} carries one row per seed set and metric, {@code summary} the distribution of each
     * metric across the seed sets, and {@code direction} how many seed sets preserved the direction
     * of each gain.
     */
    @GetMapping(value = "/csv/repeated-seeds", produces = CSV)
    public String repeatedSeedsCsv(
            @RequestParam(defaultValue = "30") int n,
            @RequestParam(defaultValue = "false") boolean fixNetwork) {
        RobustnessService.RepeatedSeedResult r = repeatedSeeds(n, fixNetwork);
        StringBuilder out = new StringBuilder();
        Csv.row(out, "section", "metric", "seedSet", "value", "n", "mean", "sd", "min", "max");
        for (RobustnessService.RunResult run : r.runs()) {
            for (Map.Entry<String, Double> m : run.metrics().entrySet()) {
                Csv.row(out, "run", m.getKey(), run.seedSet(), m.getValue(), null, null, null, null, null);
            }
        }
        for (RobustnessService.MetricSummary s : r.summary()) {
            Csv.row(out, "summary", s.metric(), null, null, s.n(), s.mean(), s.sd(), s.min(), s.max());
        }
        for (Map.Entry<String, Integer> d : r.directionCounts().entrySet()) {
            Csv.row(out, "direction", d.getKey(), null, d.getValue(), r.seedSets(), null, null, null, null);
        }
        return out.toString();
    }

    /** The sensitivity sweeps as one row per sweep level and metric. */
    @GetMapping(value = "/csv/sensitivity", produces = CSV)
    public String sensitivityCsv(
            @RequestParam(defaultValue = "10") int seeds,
            @RequestParam(required = false) String parameter) {
        RobustnessService.SensitivityResult r = sensitivity(seeds, parameter);
        StringBuilder out = new StringBuilder();
        Csv.row(out, "parameter", "level", "seedSets", "metric", "mean", "sd");
        for (RobustnessService.SweepLevel l : r.levels()) {
            for (Map.Entry<String, Double> m : l.mean().entrySet()) {
                Csv.row(out, l.parameter(), l.level(), l.seedSets(), m.getKey(), m.getValue(), l.sd().get(m.getKey()));
            }
        }
        return out.toString();
    }
}
