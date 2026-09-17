# Capability-Flag Transit Comparison Simulation

[![build](https://github.com/amchandimal/Public-and-Para-Transit-Integration-Simulation/actions/workflows/build.yml/badge.svg)](https://github.com/amchandimal/Public-and-Para-Transit-Integration-Simulation/actions/workflows/build.yml)

A discrete-event simulation of a multimodal public and para-transit journey
planning platform, used to evaluate the differentiating capabilities of a
proposed software architecture against a status-quo baseline. Eight
functional-requirement aspects (FR1-FR8) are expressed as independent capability
flags; **Existing** and **Proposed** are two named settings of those same flags,
and the same engines evaluate any setting of them. The simulation answers one
question: *which capabilities actually produce the end-to-end gap between the
two configurations, and how much does each contribute?* It answers it in two
ways, an **isolated single-factor ablation** that flips one flag at a time from
its Proposed value to its Existing value while holding every other flag and
every stochastic input fixed, and a **fully stacked comparison** of Existing
against Proposed that quantifies the realistic end-to-end gap. Because both
configurations share one network, one query set, one set of ground-truth
observations and one official feed, every measured difference is attributable to
the capability flags rather than to sampling variation. See "Use of AI
assistance" for the tools used in preparing this repository.

## Citation

> C. Adikari, "Bridging the Connectivity Gap: A Crowdsourced Software
> Architecture for Multimodal Public and Para-Transit Integration", in *Proc.
> 2026 IEEE International Conference on Electrical Engineering and Emerging
> Technologies (ICEEET 2026)*, to appear.

To cite the software itself, see [CITATION.cff](CITATION.cff).

---

## 1. Model overview

### 1.1 Capability flags

Each flag is one component of the `Capabilities` record
(`capabilities/Capabilities.java`). FR3, FR4 and FR7 are enabled in both
configurations and are retained as shared baseline capabilities, so the measured
gap is attributable only to FR1, FR2, FR5, FR6 and FR8.

| Flag | Field | Aspect | Existing | Proposed |
|---|---|---|---|---|
| FR1 | `multimodal` | Route planning across modes; restricts the search to bus only, or to bus and rail | `false` | `true` |
| FR2 | `aggregation` | Trip aggregation; bounds the itinerary search at 0 transfers (direct rides only) or 4 | `false` | `true` |
| FR3 | `multiCriteriaRanking` | Ranks the Pareto front under three weightings, or returns only the first itinerary found | `true` | `true` |
| FR4 | `realtimeStatus` | Consumes the official real-time feed | `true` | `true` |
| FR5 | `crowdsourcing` | Consumes the crowdsourced consensus; the only source of crowding indicators | `false` | `true` |
| FR6 | `paratransit` | Whether para-transit exists as an option at all | `false` | `true` |
| FR7 | `accessDiscovery` | Number of nearest access points considered per endpoint: 1 or 2 | `true` | `true` |
| FR8 | `lastmileFallback` | Dispatch discipline once para-transit is available: naive hailing or coordinated dispatch | `false` | `true` |

### 1.2 Engines

| Engine | Flags | Produces |
|---|---|---|
| `itinerary/ItineraryEngine` | FR1, FR2, FR3, FR7 | Multi-criteria Connection Scan over the timetable. Access and egress legs are para-transit rides; the Pareto front is over (arrival time, transfers, fare); ranking uses three fixed weightings over min-max normalised criteria: `min_time` (0.70, 0.15, 0.15), `min_transfers` (0.20, 0.70, 0.10), `least_cost` (0.10, 0.20, 0.70). Feeds `TripPlanningMetrics`. |
| `realtime/RealtimeEngine` | FR4, FR5 | An official feed covering a fraction γ of trips with Gaussian delay error and no crowding signal, plus a three-layer crowdsourcing pipeline: plausibility filter (delay in [-15, 90] min), credibility scoring (0.5 × historical accuracy + 0.5 × agreement with the trip's median), and consensus formation (≥ K in-window reports, credibility-weighted agreement ≥ threshold). Feeds `RealtimeMetrics`. |
| `paratransit/ParatransitEngine` | FR6, FR8 | Poisson driver arrival at the pickup point, rate λ = base × density: 0.35 × 1.0 = 0.35 min⁻¹ in the peaks (07:00-09:00, 17:00-19:00) and 0.20 × 0.6 = 0.12 min⁻¹ off-peak, so the wait is exponential with mean 2.9 min (peak) or 8.3 min (off-peak). Naive hailing draws once and fails past an 8 min patience threshold; coordinated dispatch searches 5 min per attempt with the radius widened so the effective rate is λ(1 + 0.4a) on attempt *a*, adds a 4 min escalation penalty per timeout and fails after 2 retries. Feeds `ParatransitDirectMetrics`. |

### 1.3 Synthetic network

`network/NetworkGenerator` places `stops` stops uniformly at random over a
35 × 25 km service area, then builds ten routes (eight bus, two rail). Each route
draws a random subset of stops (5-9 for bus, 8-10 for rail) and chains them in
nearest-neighbour order, so route overlap, transfer nodes and bus-rail
interchanges emerge from the geometry rather than being placed deliberately.
Every route is timetabled independently in both directions over 05:00-23:00 with
its own headway (bus 10-25 min, rail 30-40 min), speed (bus 18-24 km/h, rail
45 km/h) and fare tariff, a Gaussian first-departure jitter of σ = 1.5 min and a
half-headway offset on the reverse direction. The whole network is generated once
from **seed 42** and shared by every scenario.

Realised properties of the seed-42 network, as returned by `/network-stats`:

| Property | Value |
|---|---|
| Stops (served / unserved) | 40 (32 / 8) |
| Transfer nodes (stops on ≥ 2 routes) | 25 |
| Bus-rail interchange stops | 16 |
| Stops served by rail | 16 |
| Route pairs sharing ≥ 1 stop | 36 of 45 |
| Total route length | 493.3 km |
| Mean inter-stop spacing | 8.09 km |
| Timetabled connections | 7 750 |
| Vehicle trips per day | 1 268 |
| Fastest itinerary uses rail | 62.8 % of feasible queries |
| Mean transfers on the fastest itinerary | 0.98 |
| Direct (0-transfer) itineraries | 28.3 % of feasible queries |
| Mean query crow-fly distance | 15.33 km |

Eight of the forty stops lie on no route. They are still valid access points, so
a query whose nearest stop is unserved must reach a served stop to travel at all.

### 1.4 Shared stochastic inputs and seeds

All six seeds live in `config/SimulationParameters`. The first four generate the
inputs shared by every scenario; the last two are consumed during evaluation.

| Input | Size | Seed | Field |
|---|---|---|---|
| Synthetic network | 40 stops, 10 routes | 42 | `networkSeed` |
| GPS-to-GPS queries | 300 | 555 | `querySeed` |
| Ground truth, official feed, crowdsourced reports, consensus | 3 000 trip instances | 777 | `dataSeed` |
| Dispatch-hour distribution | 3 000 requests | 999 | `dispatchSeed` |
| Trip-planning evaluation draws | per scenario | 1234 | `tripEvaluationSeed` |
| Direct para-transit evaluation draws | per scenario | 4321 | `paratransitEvaluationSeed` |

Queries are uniform over the service area with a departure time uniform on
06:00-21:00; dispatch hours are uniform integers on 05-22.

### 1.5 Metric definitions

Let *Q* be the 300 queries and *T* the 3 000 trip instances.

**Trip planning** (`TripPlanningMetrics`, computed over *Q*)

- **Itinerary found rate** — percentage of *Q* for which a scheduled
  public-transport itinerary exists under the scenario's flags. The search tries
  access-point pairs in nearest-first order and stops at the **first** pair that
  yields a non-empty Pareto front, so the result is a feasibility test, not a
  search for the best access pair.
- **End-to-end success rate** — percentage of *Q* served either by a scheduled
  itinerary or, when none exists, by a whole-trip para-transit ride that was
  matched to a driver. With FR6 disabled no fallback exists, so this equals the
  itinerary found rate.
- **Mean alternatives shown** — mean, over queries with an itinerary, of the
  **size of the Pareto front** when FR3 is enabled, or 1 when it is disabled.
  Since FR3 is enabled in both configurations, this is the Pareto front size in
  both. Note that this counts the Pareto-optimal set, not the three-per-criterion
  list the API actually returns.
- **Whole-trip para-transit share** — percentage of *Q* with no scheduled
  itinerary. This is exactly 100 % minus the itinerary found rate.
- **Mean wait when para-transit was needed** — mean wait of the matched
  whole-trip fallback rides, or `null` if none matched.

**Real-time information** (`RealtimeMetrics`, computed over *T*)

For each trip the estimated delay starts at **0 min**. If FR4 is enabled and the
official feed covers the trip, the feed's noisy estimate replaces it. If FR5 is
enabled and a consensus was reached, the consensus estimate replaces that in
turn. A crowding level is set only by an FR5 consensus.

- **Mean absolute ETA error** — mean of |estimated delay − true delay| over *T*.
  A trip with no feed coverage and no consensus is therefore scored as an
  estimate of "no delay", not excluded.
- **Crowding known rate** — percentage of *T* with a published crowding
  indicator. Zero whenever FR5 is disabled, because the official feed carries no
  crowding signal.
- **Crowding accuracy when known** — percentage of the *published* indicators
  that match the ground truth. Reported as 0 when nothing is published.
- **System-wide crowding accuracy** — mean over all of *T* of a per-trip score
  that is 1 for a published and correct indicator, 0 for a published and wrong
  one, and **1/3 for a trip with no indicator at all**. The 1/3 credits an
  uninformed passenger with the expected accuracy of guessing uniformly among
  low, medium and high, so a system that publishes nothing scores exactly
  33.33 % rather than 0. This is why the Existing configuration scores 33.33 %
  and why the FR5 gain is smaller than the accuracy-when-known figure suggests.

**Para-transit dispatch** (`ParatransitDirectMetrics`, computed over the 3 000
shared dispatch hours, independently of trip planning)

- **Match rate** — percentage of requests matched to a driver. With FR6 disabled
  the engine reports `available: false` and a match rate of 0.
- **Mean wait** — mean total wait of the matched requests, including the
  escalation penalties accumulated by coordinated dispatch, or `null` if none
  matched.

---

## 2. Architecture of the code

```
com.transit.simulation2
├── SimulationApplication            Spring Boot entry point
├── ComparisonService                orchestrates everything below
├── Query, SharedInputs, ScenarioResult, AblationRow,
│   TripPlanningMetrics, ParatransitDirectMetrics       data carriers
├── capabilities.Capabilities        the eight flags, EXISTING/PROPOSED, FrAspect
├── config.SimulationParameters      every varyable constant and the six seeds
├── model                            Stop, Route, Connection, ParatransitParams, NetworkData
├── network.NetworkGenerator         synthetic network generation
├── itinerary                        Label, ParetoSet, ItineraryEngine and its DTOs
├── realtime                         RealtimeEngine and its DTOs
├── paratransit                      ParatransitEngine, DispatchOutcome
├── robustness.RobustnessService     repeated-seed analysis, sensitivity sweeps, network statistics
├── controller.ComparisonController  REST API
└── util.RandomUtils                 exponential, gamma, beta, Poisson, sampling
```

**`ComparisonService`** is the single evaluation path. It builds the network and
the shared inputs, evaluates one `Capabilities` setting into a `ScenarioResult`,
and derives the ablation and the stacked comparison from repeated calls to that
one method. Every entry point exists twice: once using the baseline
configuration, and once taking an explicit `SimulationParameters`.

**`SimulationParameters`** is an immutable record holding every constant the
analyses vary plus the six seeds, so a run is fully described by one instance.
`DEFAULTS` is the baseline; a sweep level or seed set is a `with…` copy of it.

**`RobustnessService`** re-runs the same evaluation under different
`SimulationParameters` instances: a repeated-seed analysis over shifted seed
sets, and one-at-a-time sweeps of individual parameters. It contains no model of
its own.

### Request flow of one scenario evaluation

```
GET /api/comparison/stacked
  ComparisonController.stackedComparison()
    net()      -> ComparisonService.buildNetwork()        [cached in the controller]
                    NetworkGenerator.generate(new Random(42), DEFAULTS)
    shared()   -> ComparisonService.buildSharedInputs()   [cached in the controller]
                    new Random(555)  -> 300 queries
                    new Random(777)  -> ground truth -> official feed -> reports -> consensus
                    new Random(999)  -> 3000 dispatch hours
    runStackedComparison(net, shared, DEFAULTS)
      evaluateScenario(net, EXISTING,  shared, p)
      evaluateScenario(net, PROPOSED, shared, p)
        evaluateTripPlanning      new Random(1234); ItineraryEngine per query,
                                  ParatransitEngine when no itinerary exists
        evaluateRealtime          RealtimeEngine scores the precomputed consensus
        evaluateParatransitDirect new Random(4321); ParatransitEngine over the dispatch hours
```

### Where randomness enters, and how determinism is guaranteed

Randomness enters at exactly six points, each from a fresh `java.util.Random`
constructed from one seed: network generation (stop coordinates, route stop
subsets, departure jitter), query generation, the ground-truth/feed/report chain,
the dispatch hours, the trip-planning evaluation, and the direct para-transit
evaluation. The consensus pipeline itself is deterministic given the reports.

Determinism rests on three properties. The network and shared inputs are built
once and reused by every scenario, so no scenario resamples them. Every
evaluation constructs its own `Random` from a fixed seed rather than sharing a
generator, so the repeated-seed and sensitivity runs can execute in parallel
without their results depending on thread scheduling. And `java.util.Random` is
specified exactly by the Java language, so results are reproducible across JVMs
and platforms.

One consequence is worth knowing when interpreting results: the evaluation-time
generators start from the same seed for every scenario, but the *number* of draws
taken depends on the flags — FR7 changes how many access-point pairs are tried
and therefore how many access-leg waits are drawn, and FR6 decides whether a
fallback dispatch is drawn at all. Two scenarios therefore share the same inputs
but not the same sequence of evaluation-time draws. The ablation is still a
like-for-like comparison because the inputs under test are identical; the
residual noise this introduces is what the repeated-seed analysis quantifies.

---

## 3. Getting started

**Requirements**

- JDK 21 or later
- Maven 3.9 or later
- Network access on the first build, to download the Spring Boot dependencies.
  The dashboard also loads Chart.js from a CDN at runtime.
- Optionally `jq`, used only by the JSON-to-CSV recipe in section 5. It is not
  needed for the build, the application or the CSV endpoints.

**Build**

```bash
mvn -B verify
```

This compiles, runs the test suite and packages the application. The suite takes
about 25 s and includes a snapshot test that fails if any published metric
changes; see [CONTRIBUTING.md](CONTRIBUTING.md).

**Run**

```bash
mvn spring-boot:run
```

Then open <http://localhost:8080> for the dashboard. The port is `server.port` in
`src/main/resources/application.properties`.

**Check that the build reproduces the baseline**

```bash
curl -s http://localhost:8080/api/comparison/stacked
```

The itinerary found rate must be **46.0 %** for Existing and
**96.67 %** for Proposed. If it is not, the build is not reproducing the
baseline configuration.

**Other example calls**

```bash
curl -s http://localhost:8080/api/comparison/ablation
```

```bash
curl -s "http://localhost:8080/api/comparison/scenario/custom?aggregation=false"
```

```bash
curl -s "http://localhost:8080/api/comparison/repeated-seeds?n=30" -o repeated30.json
```

---

## 4. REST API reference

All endpoints are GET under `/api/comparison` and return JSON. Run times below
were measured on a Windows 11 desktop with JDK 21.0.10; they scale with core
count because the robustness runs execute in parallel.

### `/network`

The generated network: stops, routes, all 7 750 connections, para-transit
parameters and the service window. No parameters. About 1.7 MB.

```json
{ "stops": [ { "id": "S00", "x": 25.46, "y": 17.08 }, ... ],
  "routes": [ ... ], "connections": [ ... ], "paratransit": { ... } }
```

### `/capabilities`

The two flag settings. No parameters.

```json
{ "existing": { "multimodal": false, "aggregation": false, "multiCriteriaRanking": true,
                "realtimeStatus": true, "crowdsourcing": false, "paratransit": false,
                "accessDiscovery": true, "lastmileFallback": false },
  "proposed": { "multimodal": true, ... "lastmileFallback": true } }
```

### `/ablation`

One row per FR aspect: the metric that aspect governs, its value under Proposed,
its value with that single flag set to its Existing value, and the difference.
`sharedNotDifferentiator` marks the three aspects that are enabled in both
configurations. No parameters. Under a second.

```json
[ { "frAspect": "FR2 Trip aggregation and itinerary generation",
    "field": "aggregation", "metric": "itineraryFoundRatePct",
    "proposedValue": 96.67, "withCapabilityRemoved": 60.33,
    "delta": 36.33, "sharedNotDifferentiator": false }, ... ]
```

### `/stacked`

Existing against Proposed, each as a full `ScenarioResult`. No parameters. Under
a second.

```json
{ "existing": { "tripPlanning": { "itineraryFoundRatePct": 46.0, ... },
                "realtime": { "meanAbsEtaErrorMin": 2.444, ... },
                "paratransitDirect": { "available": false, "matchRatePct": 0.0, "meanWaitMin": null } },
  "proposed": { "tripPlanning": { "itineraryFoundRatePct": 96.67, ... }, ... } }
```

### `/scenario/existing`, `/scenario/proposed`

One `ScenarioResult` for that setting. No parameters.

### `/scenario/custom`

Evaluates an arbitrary flag combination. Eight optional boolean parameters:
`multimodal`, `aggregation`, `multiCriteriaRanking`, `realtimeStatus`,
`crowdsourcing`, `paratransit`, `accessDiscovery`, `lastmileFallback`. **Each
defaults to `true`**, so omitting all of them evaluates Proposed. Returns a
`ScenarioResult`.

### `/full`

`capabilities`, `isolatedAblation` and `stacked` in one response. No parameters.
About 4.5 KB.

### `/parameters`

The baseline `SimulationParameters`: all 20 constants and the six seeds. No
parameters. See section 6 for the field list.

### `/network-stats`

Realised properties of the baseline network, plus the modal share and transfer
counts of the fastest feasible itinerary over the 300 queries under Proposed. No
parameters. Under a second. Fields as tabulated in section 1.3.

### `/repeated-seeds`

| Parameter | Default | Range | Meaning |
|---|---|---|---|
| `n` | 30 | clamped to 1..200 | number of seed sets; set 0 is the baseline, set *i* shifts all six seeds by 1000·*i* |
| `fixNetwork` | `false` | boolean | hold `networkSeed` at 42 so only the shared inputs vary |

Cached per `(n, fixNetwork)`. Measured: `n=30` took **11.0 s** cold and 4 ms
cached, returning 106 KB. Returns per-run metrics, a mean/SD/min/max summary per
metric, and `directionCounts`, which reports how many runs preserved the
direction of each headline gain.

```json
{ "seedSets": 30, "networkFixed": false,
  "directionCounts": { "proposedFindsMoreItineraries": 30, "proposedHigherEndToEndSuccess": 30,
                       "proposedLowerEtaError": 30, "coordinatedDispatchBeatsNaive": 30,
                       "fr2DeltaExceedsFr1Delta": 30, "runs": 30 },
  "summary": [ { "metric": "proposed.itineraryFoundRatePct", "n": 30,
                 "mean": 95.50, "sd": 4.08, "min": 85.0, "max": 100.0 }, ... ],
  "runs": [ { "seedSet": 0, "parameters": { ... }, "metrics": { ... } }, ... ] }
```

Metric keys are `existing.*` and `proposed.*` (`itineraryFoundRatePct`,
`endToEndSuccessRatePct`, `meanAlternativesShown`,
`pctNeedingWholeTripParatransit`, `meanAbsEtaErrorMin`, `crowdingKnownRatePct`,
`crowdingAccuracyWhenKnownPct`, `crowdingSystemWideAccuracyPct`,
`paratransitMatchRatePct`, `paratransitMeanWaitMin` which is `null` when
para-transit is unavailable), `ablation.frXRemoved.*`, `delta.frX.*` in
percentage points, `network.*` and `reports.meanPerTrip`.

### `/sensitivity`

| Parameter | Default | Range | Meaning |
|---|---|---|---|
| `seeds` | 10 | clamped to 1..100 | seed sets averaged at each level |
| `parameter` | all sweeps | a name from `/sensitivity/parameters` | restrict to one sweep |

An unrecognised `parameter` raises `IllegalArgumentException` and returns
**HTTP 500**, not 400. Cached per `(seeds, parameter)`. The ten sweeps have 43
levels between them, so the default is 430 runs. Measured: all sweeps at
`seeds=10` took **120 s** and returned 206 KB; the single `officialFeedCoverage`
sweep (50 runs) took 15.8 s.

```json
{ "seedsPerLevel": 10, "parameters": [ "officialFeedCoverage" ],
  "levels": [ { "parameter": "officialFeedCoverage", "level": "0.25", "seedSets": 10,
                "mean": { "proposed.meanAbsEtaErrorMin": 2.189, ... },
                "sd":   { "proposed.meanAbsEtaErrorMin": 0.041, ... } }, ... ] }
```

### `/sensitivity/parameters`

The ten sweep names, in sweep order. No parameters.

```json
[ "officialFeedCoverage", "contributorReliability", "consensusMinReports",
  "consensusAgreementThreshold", "meanReportersPerTrip", "randomSpamContributorFraction",
  "colludingSpamContributorFraction", "driverRateMultiplier", "stops", "stopPlacement" ]
```

### `/csv/stacked`, `/csv/ablation`, `/csv/repeated-seeds`, `/csv/sensitivity`

Read-only CSV views of `/stacked`, `/ablation`, `/repeated-seeds` and
`/sensitivity`, served as `text/csv`. They delegate to those endpoints, so they
share the same caches, parameter defaults, clamping and run times:
`/csv/repeated-seeds` takes `n` and `fixNetwork`, `/csv/sensitivity` takes
`seeds` and `parameter`. They are separate paths rather than content negotiation
on the existing ones, so a request with `Accept: */*` still resolves to JSON.
Column definitions are in [results/README.md](results/README.md).

### `/scenario/parameterised`

Evaluates any flag combination under any parameter set, rebuilding the network
and shared inputs from the given parameters. Not cached. Returns `parameters`,
`capabilities`, `result` and `networkStats`.

Accepts the eight capability flags (each defaulting to `true`) and these optional
parameters, each defaulting to its baseline value: `stops`, `clusteredStops`,
`officialFeedCoverage`, `contributors`, `meanReportersPerTrip`,
`reliabilityBetaAlpha`, `reliabilityBetaBeta`, `spamContributorFraction`,
`colludingSpam`, `consensusMinReports`, `consensusAgreementThreshold`,
`driverRateMultiplier`, `networkSeed`, `querySeed`, `dataSeed`, `dispatchSeed`.

`tripEvaluationSeed` and `paratransitEvaluationSeed` are always the baseline
values, so two calls differing only in the other parameters stay comparable. The
eight parameters marked † in section 6 have no query parameter and can only be
changed by editing `SimulationParameters.DEFAULTS`.

```bash
curl -s "http://localhost:8080/api/comparison/scenario/parameterised?officialFeedCoverage=0.25&consensusMinReports=2&colludingSpam=true&spamContributorFraction=0.2"
```

---

## 5. Reproducing the paper

Everything in this section can be read off the dashboard at
<http://localhost:8080> once the application is running, or fetched as CSV from
the `/csv/…` endpoints. The CSV files are also committed under `results/`, so
the numbers can be checked without running anything.

[docs/REPRODUCING.md](docs/REPRODUCING.md) maps each paper table to its
dashboard panel, endpoint, output file and columns;
[results/README.md](results/README.md) defines the columns. The rest of this
section explains the mapping in prose.

### Which endpoint produces which table

| Paper item | Endpoint | Notes |
|---|---|---|
| Fig. 1, network topology for seed 42 | `/network` | Plot `stops` by `x`/`y` and draw each route's `stopIds` in order |
| Table III, network generation parameters | `/parameters` | `stops`, `areaXKm`, `areaYKm`, `networkSeed`; headways, speeds and fares are the `ROUTE_SPECS` table in `NetworkGenerator` |
| Table IV, real-time engine parameters | `/parameters` | `officialFeedCoverage`, `officialFeedNoiseMin`, `contributors`, `reliabilityBeta*`, `consensus*` |
| Table V, para-transit engine parameters | — | The public constants of `ParatransitEngine`, plus `ParatransitParams.defaultParams()` |
| Table VI, shared inputs and seeds | `/parameters` | The six seeds; sample sizes are `N_QUERIES`, `N_TRIPS`, `N_DISPATCH` in `ComparisonService` |
| **Table VII**, isolated single-factor ablation | **`/ablation`** | Rows FR1, FR2, FR5, FR6, FR8; the three rows with `sharedNotDifferentiator: true` are not in the paper table |
| **Table VIII**, trip planning and last mile | **`/stacked`** | The `tripPlanning` block of each configuration |
| **Table IX**, real-time information quality | **`/stacked`** | The `realtime` block; the paper's "crowding accuracy" row is `crowdingSystemWideAccuracyPct` |

The repeated-seed analysis and the sensitivity sweeps are **not reported in the
paper**; the paper states that they are released with this implementation. Use
`/repeated-seeds?n=30` and `/sensitivity?seeds=10` for them.

### The paper's values come from a different implementation

The single-run values printed in the paper were produced by the original Python
prototype of this same model. **That prototype is not part of this release**, and
this Java implementation is the reference one: everything reproducible is
reproducible from here. The port implements the identical model but draws from
`java.util.Random` rather than NumPy's generator, so identical seeds produce
different number streams and the single-run values differ slightly.
**Every single-run value in the paper falls inside the 30-seed range produced by
this port**, which is the sense in which the two agree. The tables below give
both, with the range from `/repeated-seeds?n=30`.

**Table VIII, trip planning and last mile (n = 300)**

| Metric | Paper (Python) | This port | 30-seed mean ± SD | 30-seed [min, max] |
|---|---|---|---|---|
| Itinerary found rate, Existing | 48.7 % | 46.0 % | 51.18 ± 5.86 | [36.67, 60.67] |
| Itinerary found rate, Proposed | 99.7 % | 96.67 % | 95.50 ± 4.08 | [85.00, 100.00] |
| End-to-end success, Existing | 48.7 % | 46.0 % | 51.18 ± 5.86 | [36.67, 60.67] |
| End-to-end success, Proposed | 100.0 % | 100.0 % | 99.78 ± 0.34 | [98.67, 100.00] |
| Mean alternatives, Existing | 1.01 | 1.05 | 1.03 ± 0.02 | [1.01, 1.07] |
| Mean alternatives, Proposed | 1.96 | 1.97 | 1.97 ± 0.10 | [1.70, 2.15] |
| Need whole-trip para-transit, Existing | 51.3 % | 54.0 % | 48.82 ± 5.86 | [39.33, 63.33] |
| Need whole-trip para-transit, Proposed | 0.3 % | 3.33 % | 4.50 ± 4.08 | [0.00, 15.00] |

**Table IX, real-time information quality (n = 3 000)**

| Metric | Paper (Python) | This port | 30-seed mean ± SD | 30-seed [min, max] |
|---|---|---|---|---|
| Mean absolute ETA error, Existing | 2.48 min | 2.44 min | 2.49 ± 0.05 | [2.37, 2.60] |
| Mean absolute ETA error, Proposed | 2.13 min | 2.12 min | 2.13 ± 0.05 | [2.02, 2.23] |
| Crowding known at all, Existing | 0.0 % | 0.0 % | 0.00 ± 0.00 | [0.00, 0.00] |
| Crowding known at all, Proposed | 25.6 % | 25.9 % | 25.89 ± 1.27 | [22.57, 28.70] |
| Crowding accuracy (system-wide), Existing | 33.3 % | 33.33 % | 33.33 ± 0.00 | [33.33, 33.33] |
| Crowding accuracy (system-wide), Proposed | 45.8 % | 46.90 % | 46.28 ± 0.87 | [44.31, 47.97] |

The paper's remark that roughly 82 % of published indicators are correct
corresponds to `crowdingAccuracyWhenKnownPct`: 85.71 % in this port's baseline
run, 83.30 ± 1.69 over 30 seeds, range [79.97, 86.62].

**Table VII, isolated single-factor ablation**

| FR | Metric | Paper Δ | This port Δ | 30-seed Δ mean ± SD | 30-seed Δ [min, max] |
|---|---|---|---|---|---|
| FR1 | Itinerary found rate | 0.3 pp | 0.00 pp | 5.96 ± 6.26 | [0.00, 25.33] |
| FR2 | Itinerary found rate | 37.7 pp | 36.33 pp | 31.42 ± 5.08 | [18.67, 42.33] |
| FR5 | Crowding accuracy (system-wide) | 12.5 pp | 13.57 pp | 12.95 ± 0.87 | [10.98, 14.63] |
| FR6 | End-to-end success | 0.3 pp | 3.33 pp | 4.28 ± 3.93 | [0.00, 13.67] |
| FR8 | Para-transit match rate | 20.8 pp | 21.37 pp | 22.19 ± 0.80 | [20.67, 23.70] |

On the seed-42 network FR1 contributes exactly nothing in this port: removing
multimodal routing leaves the itinerary found rate unchanged at 96.67 %, because
every origin-destination pair that rail could serve is already reachable by bus.
The paper's 0.3 pp is the same finding at the resolution of a single run. The
30-seed spread shows that FR1's contribution is strongly network-dependent
(SD 6.26 pp against a mean of 5.96 pp), which the `stops` sweep confirms
directly: the FR1 gain rises from 0.33 pp at 25 stops to 18.40 pp at 80 stops
while the FR2 gain rises from 6.13 pp to 45.70 pp. The paper's conclusion that
FR2 outweighs FR1 holds in all 30 seed sets (`fr2DeltaExceedsFr1Delta: 30`), and
the paper reports it as a property of this topology rather than a general result.

### Direction of every headline gain

`directionCounts` from `/repeated-seeds?n=30` is 30 out of 30 for all five
checks: Proposed finds more itineraries, achieves higher end-to-end success, has
lower ETA error, coordinated dispatch beats naive hailing, and the FR2 gain
exceeds the FR1 gain.

### Exporting results to CSV

Each analysis has a read-only CSV view alongside its JSON endpoint. Fetching the
five of them regenerates every file under `results/`; the exact requests are in
[docs/REPRODUCING.md](docs/REPRODUCING.md).

```bash
curl -s "http://localhost:8080/api/comparison/csv/repeated-seeds?n=30" -o repeated-seeds.csv
```

To convert a JSON response that has no CSV view, on Windows:

```powershell
$r = Invoke-RestMethod "http://localhost:8080/api/comparison/repeated-seeds?n=30"
$r.summary | Export-Csv repeated30-summary.csv -NoTypeInformation -Encoding utf8
```

For the sweeps, flatten the metric of interest out of each level:

```powershell
$s = Invoke-RestMethod "http://localhost:8080/api/comparison/sensitivity?seeds=10"
$s.levels | ForEach-Object { [pscustomobject]@{
    parameter = $_.parameter; level = $_.level
    etaProposed = $_.mean.'proposed.meanAbsEtaErrorMin'
    sysWideProposed = $_.mean.'proposed.crowdingSystemWideAccuracyPct' } } |
  Export-Csv sensitivity.csv -NoTypeInformation -Encoding utf8
```

The equivalent with `jq` on Linux or macOS:

```bash
curl -s "http://localhost:8080/api/comparison/repeated-seeds?n=30" \
  | jq -r '["metric","n","mean","sd","min","max"], (.summary[] | [.metric,.n,.mean,.sd,.min,.max]) | @csv' \
  > repeated30-summary.csv
```

---

## 6. Configuration

Every field of `config/SimulationParameters`. "Sweep levels" are the values
`RobustnessService.SWEEPS` visits; a dash means the field is not swept. Fields
marked † have no query parameter on `/scenario/parameterised` and can only be
changed by editing `DEFAULTS`.

| Field | Meaning | Unit | Default | Sweep levels |
|---|---|---|---|---|
| `stops` | Stops placed in the service area, for the same ten routes | count | 40 | 25, 40, 60, 80 |
| `areaXKm` † | Service-area width | km | 35.0 | — |
| `areaYKm` † | Service-area height | km | 25.0 | — |
| `clusteredStops` | Stop placement: uniform, or three Gaussian clusters | boolean | `false` | uniform, clustered |
| `officialFeedCoverage` | Share of trips the official feed covers (γ) | fraction | 0.55 | 0.25, 0.40, 0.55, 0.70, 0.85 |
| `officialFeedNoiseMin` † | σ of the official feed's delay error | min | 3.0 | — |
| `disruptionProbability` † | Share of trips given a large delay, 12 ± 5 min instead of 1.5 ± 2 min | fraction | 0.06 | — |
| `contributors` | Crowdsourcing contributors in the population | count | 250 | — |
| `meanReportersPerTrip` | Poisson mean of reports per trip instance | count | 3.0 | 1, 2, 3, 5, 8 |
| `reliabilityBetaAlpha` | α of contributor reliability ~ Beta(α, β) | — | 6.0 | 2, 4, 6, 8, 12, reported as the mean α/(α+2) |
| `reliabilityBetaBeta` | β of contributor reliability | — | 2.0 | held at 2 by the reliability sweep |
| `spamContributorFraction` | Contributors who always report spam | fraction | 0.05 | 0.05, 0.10, 0.20, 0.30, 0.50 in both spam sweeps |
| `perReportSpamProbability` † | Chance an otherwise honest report is spam | fraction | 0.03 | — |
| `colludingSpam` | Spam style: random, or all spammers reporting the same false delay | boolean | `false` | `false` and `true`, one spam sweep each |
| `colludeOffsetMin` † | False delay colluding spammers report, as truth + offset | min | 20.0 | — |
| `consensusMinReports` | Minimum in-window reports for a consensus (K) | count | 3 | 2, 3, 4, 5 |
| `consensusWindowMin` † | Temporal window for in-window reports | min | 6.0 | — |
| `consensusAgreementThreshold` | Credibility-weighted agreement needed for a consensus | fraction | 0.65 | 0.50, 0.65, 0.80 |
| `consensusToleranceMin` † | Reports within ± this of the weighted median count as agreeing | min | 3.0 | — |
| `driverRateMultiplier` | Scales the hour-dependent driver arrival rate | factor | 1.0 | 0.5, 0.75, 1.0, 1.5, 2.0 |
| `networkSeed` | Network generation | seed | 42 | shifted by 1000·*i* per seed set |
| `querySeed` | Query generation | seed | 555 | shifted by 1000·*i* per seed set |
| `dataSeed` | Ground truth, feed and reports | seed | 777 | shifted by 1000·*i* per seed set |
| `dispatchSeed` | Dispatch hours | seed | 999 | shifted by 1000·*i* per seed set |
| `tripEvaluationSeed` | Trip-planning evaluation draws | seed | 1234 | shifted by 1000·*i* per seed set |
| `paratransitEvaluationSeed` | Direct para-transit evaluation draws | seed | 4321 | shifted by 1000·*i* per seed set |

Two results from the sweeps are worth citing when tuning these. Coordinated
dispatch (FR8) dominates naive hailing at every driver density, but the margin
shrinks as supply rises: match rates are 50.8 % against 80.5 % at
`driverRateMultiplier=0.5`, and 90.1 % against 99.5 % at 2.0. And the consensus
pipeline degrades gracefully under random spam but not under collusion: at a
colluding fraction of 0.5 the ETA error rises from 2.15 min to 5.04 min and
accuracy among published indicators falls from 83.6 % to 43.4 %.

---

## 7. Extending the model

**Add a capability flag.** Add a component to the `Capabilities` record and set
it in `EXISTING` and `PROPOSED`; add a constant with its label to
`Capabilities.FrAspect`; extend the `toggled` and `get` switches, which are
exhaustive and will fail to compile until you do. Add an `FrMetricSpec` row to
`ComparisonService.FR_METRICS` naming the metric the aspect is scored on, and add
the flag as a parameter on `/scenario/custom` and `/scenario/parameterised`. Read
the flag in whichever engine it governs. Finally add a row to `FR_ROWS` in
`index.html`. Adding an `FrAspect` constant adds a row to `/ablation`, which is
an additive change to that response.

**Add a metric.** Add a component to the relevant metrics record
(`TripPlanningMetrics`, `RealtimeMetrics` or `ParatransitDirectMetrics`) and
populate it in the corresponding `ComparisonService.evaluate…` method. Add it to
`RobustnessService.putScenario` so it appears in the repeated-seed and
sensitivity output. If an FR aspect should be scored on it, add a case to
`ComparisonService.extractMetric`. To surface it on the dashboard, add it to
`REP_METRICS` or `SENS_COLS` in `index.html`.

**Add a parameter.** Add a component to `SimulationParameters` and a value to
`DEFAULTS`. The `with…` copy methods enumerate every component positionally, so
each must be updated; adding a `with…` helper for the new field is what lets a
sweep vary it. Read it in the engine that needs it, and expose it on
`/scenario/parameterised` if it should be settable per request.

**Add a sweep.** Append a `Sweep` to `RobustnessService.SWEEPS` with a name, a
description and its levels. Numeric sweeps use the `levels(values, label,
applier)` helper, where `applier` is typically an existing `with…` method;
non-numeric ones list `Level` instances directly, as `stopPlacement` does. The
new name appears automatically in `/sensitivity/parameters` and in the
dashboard's sweep selector, and the default `/sensitivity` run grows by
`levels × seeds` runs.

---

## 8. Limitations

These are the limitations the paper states, plus the modelling simplifications
that are visible in the code.

- **Internal, relative feasibility only.** The results establish relative
  feasibility under a shared set of modelling assumptions. They are not an
  empirical validation with real passengers, drivers or operators, and no
  real-world dataset is used.
- **The Existing configuration is a construct.** It operationalises a fragmented
  status-quo system, not a measured characterisation of any deployed system.
  Reported gaps illustrate a mechanism rather than predict a field result.
- **Synthetic network.** A single generated 40-stop, 10-route topology. The
  finding that FR2 outweighs FR1 is a property of this network's connectivity and
  may not generalise to a topology with more geographically separated bus and rail
  coverage; the paper reports it as a testable hypothesis. The `stops` and
  `stopPlacement` sweeps quantify how far it moves.
- **No walking links.** Access and egress legs are always para-transit rides
  charged at the para-transit tariff, and transfers only occur at a stop shared by
  two routes. There is no pedestrian network and no walking transfer between
  nearby stops.
- **No minimum transfer time.** A transfer is feasible whenever the arrival time
  is at or before the next departure, so connections with zero slack are accepted.
- **Uniform demand.** Query origins and destinations are uniform over the service
  area and departure times are uniform on 06:00-21:00, so there is no centre-bound
  demand, no peak concentration and no origin-destination structure. Dispatch
  hours are uniform on 05-22, independent of the query distribution.
- **First feasible access pair, not best.** The end-to-end query accepts the first
  access-point pair that yields any itinerary rather than comparing pairs, which
  limits what FR7 can contribute.
- **Single-run values in the paper.** The paper's tables report one run each.
  Use `/repeated-seeds` for the distribution; the direction of every headline gain
  holds in all 30 seed sets, but individual magnitudes move considerably, most of
  all for FR1 and FR6.
- **Crowding indicator coverage.** The crowdsourcing module publishes a crowding
  indicator for only about 26 % of trips. The 45.8 % system-wide figure in the
  paper credits unpublished trips with 1/3; among published indicators roughly
  82 % are correct. The operational value of that coverage is untested.
- **Malicious reports are only modelled as spam.** The pipeline's robustness is
  exercised only by the two spam sweeps, one with independent random spam and one
  with spammers colluding on a fixed false delay. Adaptive adversaries, Sybil
  identities, reputation-farming and targeted attacks on specific trips are not
  modelled, and the paper lists robustness to malicious reports as future work.

---

## 9. Repository layout

```
.
├── pom.xml                     Maven build (Spring Boot 3.3.4, Java 21)
├── README.md                   this file
├── CONTRIBUTING.md             how to build, conventions, the determinism contract
├── CHANGELOG.md                released versions
├── LICENSE                     MIT
├── CITATION.cff                citation metadata for the software and the paper
├── .editorconfig               UTF-8, LF, 4-space Java indentation
├── .github/workflows/build.yml CI: mvn verify, then check the determinism contract
├── docs/REPRODUCING.md         which panel and endpoint reproduce which paper table
├── results/                    the generated result files, committed
│   ├── README.md               column definitions
│   ├── stacked.csv             Tables VIII and IX
│   ├── ablation.csv            Table VII
│   ├── network-stats.json      the Fig. 1 network
│   ├── repeated-seeds.csv      30 seed sets
│   └── sensitivity.csv         43 sweep levels
└── src/main/
    ├── java/com/transit/simulation2/    see section 2
    └── resources/
        ├── application.properties       application name, port, JSON formatting, logging
        └── static/index.html            dashboard, Chart.js from a CDN
```

## Use of AI assistance

Parts of this repository were developed with the assistance of an AI coding
assistant (Anthropic Claude, used through Claude Code, July to September 2026).
AI assistance was used for: the parameterised overloads of the engines, the
robustness-analysis module (SimulationParameters, RobustnessService, the related
REST endpoints and dashboard panels), the reproduction harness, and the drafting
of this documentation. The simulation model, its assumptions, parameter values
and the interpretation of results are the author's own. All AI-assisted code and
text were reviewed, executed and verified by the author, who takes full
responsibility for their correctness. No AI tool is an author of the associated
paper, and no confidential or personal data were provided to the tool.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the build, the coding conventions and
the determinism contract that every change must preserve.
[CHANGELOG.md](CHANGELOG.md) records released versions.

## Licence

MIT. See [LICENSE](LICENSE).

## How to cite the software

Citation metadata is in [CITATION.cff](CITATION.cff), which GitHub renders as a
"Cite this repository" panel. It carries both the software entry and, under
`preferred-citation`, the conference paper.
