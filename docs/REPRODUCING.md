# Reproducing the results

Every number in the paper that comes from this code can be reproduced in two
ways: by reading it off the dashboard, or by fetching it as CSV. The CSV files
are also committed under [`results/`](../results), so the numbers can be
inspected without running anything at all.

## Start the application

```bash
mvn spring-boot:run
```

Requirements: a JDK 21 or later and Maven 3.9 or later. The first build needs
network access to download the Spring Boot dependencies, and the dashboard loads
Chart.js from a CDN at runtime.

## Read the numbers from the dashboard

Open <http://localhost:8080>. The dashboard is the quickest way to see the whole
simulation, and every panel corresponds to something in the paper.

| Dashboard panel | Paper item |
|---|---|
| Comparison Aspects | Table I, the eight capability flags with their Existing and Proposed settings |
| Isolated Single-Factor Ablation | Table VII, as a chart and a table; the greyed rows are the three aspects enabled in both configurations |
| Trip Planning & Last-Mile | Table VIII |
| Real-Time Information Quality | Table IX |
| Para-transit Dispatch | the para-transit match rate and mean wait |
| Synthetic Network Properties | the realised properties of the seed-42 network of Fig. 1 |
| Repeated-Seed Robustness | the repeated-seed analysis, which the paper does not report. Set the seed-set count and press Run. |
| Sensitivity Sweeps | the one-at-a-time sweeps, which the paper does not report. Choose a sweep and an outcome and press Run. |

The first four panels load automatically. The two robustness panels run on
demand, because they re-run the whole experiment many times.

## Fetch the numbers as CSV

Each analysis has a read-only CSV view next to its JSON endpoint. These five
requests regenerate every file under `results/`, and are exactly what produced
the committed copies:

```bash
curl -s "http://localhost:8080/api/comparison/csv/stacked"              -o results/stacked.csv
curl -s "http://localhost:8080/api/comparison/csv/ablation"             -o results/ablation.csv
curl -s "http://localhost:8080/api/comparison/network-stats"            -o results/network-stats.json
curl -s "http://localhost:8080/api/comparison/csv/repeated-seeds?n=30"  -o results/repeated-seeds.csv
curl -s "http://localhost:8080/api/comparison/csv/sensitivity?seeds=10" -o results/sensitivity.csv
```

Opening the same URLs in a browser works too; the CSV is served as a plain
download.

The first three return in under a second. On a Windows 11 desktop with JDK
21.0.10 the fourth took about 11 s and the fifth about 120 s, because they
re-run the experiment 30 and 430 times respectively; both scale with core count,
since the runs execute in parallel, and both are cached, so a repeated request
with the same parameters returns immediately.

`/csv/repeated-seeds` accepts `n` (clamped to 1..200) and `fixNetwork`, and
`/csv/sensitivity` accepts `seeds` (clamped to 1..100) and `parameter`. Use a
smaller `n` or `seeds` for a quick look.

## What produces what

| Paper item | Dashboard panel | Endpoint | Committed file | Columns to compare |
|---|---|---|---|---|
| **Table VII**, isolated single-factor ablation | Isolated Single-Factor Ablation | `/csv/ablation` | `results/ablation.csv` | `proposedValue`, `withCapabilityRemoved`, `delta`, on the rows where `sharedNotDifferentiator` is `false`. The paper's Δ column is `delta`, in percentage points. |
| **Table VIII**, trip planning and last mile | Trip Planning & Last-Mile | `/csv/stacked` | `results/stacked.csv` | `existing` and `proposed`, on the rows where `metricGroup` is `tripPlanning` |
| **Table IX**, real-time information quality | Real-Time Information Quality | `/csv/stacked` | `results/stacked.csv` | `existing` and `proposed`, on the rows where `metricGroup` is `realtime`. The paper's "crowding accuracy" row is `crowdingSystemWideAccuracyPct`, not `crowdingAccuracyWhenKnownPct`. |
| **Fig. 1**, network topology for seed 42 | Synthetic Network Properties | `/network-stats` | `results/network-stats.json` | Realised properties of the plotted network. To redraw the figure itself, fetch `/network` and plot `stops` by `x` and `y`, joining each route's `stopIds` in order. |
| Table III, network generation parameters | — | `/parameters` | — | `stops`, `areaXKm`, `areaYKm`, `networkSeed`. Headways, speeds and fares are the `ROUTE_SPECS` table in `NetworkGenerator`. |
| Table IV, real-time engine parameters | — | `/parameters` | — | `officialFeedCoverage`, `officialFeedNoiseMin`, `contributors`, `reliabilityBetaAlpha`, `reliabilityBetaBeta`, `consensusMinReports`, `consensusWindowMin`, `consensusAgreementThreshold` |
| Table V, para-transit engine parameters | — | — | — | The public constants of `ParatransitEngine` and `ParatransitParams.defaultParams()` |
| Table VI, shared inputs and seeds | — | `/parameters` | — | The six seeds. Sample sizes are `N_QUERIES`, `N_TRIPS` and `N_DISPATCH` in `ComparisonService`. |
| Repeated-seed analysis (not in the paper) | Repeated-Seed Robustness | `/csv/repeated-seeds?n=30` | `results/repeated-seeds.csv` | The `summary` and `direction` sections |
| Sensitivity sweeps (not in the paper) | Sensitivity Sweeps | `/csv/sensitivity?seeds=10` | `results/sensitivity.csv` | `mean` and `sd` per `parameter`, `level` and `metric` |

Endpoints are relative to `http://localhost:8080/api/comparison`.

The repeated-seed analysis and the sensitivity sweeps are **not reported in the
paper**. The paper states that they are released with this implementation, and
they are what `results/repeated-seeds.csv` and `results/sensitivity.csv` contain.

## Expected values for the default seeds

With the seeds in `SimulationParameters.DEFAULTS` (42, 555, 777, 999, 1234,
4321) the values below are exact and deterministic on any JVM. They are shown
rounded; the committed files carry full precision.

### `results/stacked.csv`

| `metricGroup` | `metric` | `existing` | `proposed` |
|---|---|---|---|
| tripPlanning | itineraryFoundRatePct | 46.00 | 96.67 |
| tripPlanning | endToEndSuccessRatePct | 46.00 | 100.00 |
| tripPlanning | meanAlternativesShown | 1.05 | 1.97 |
| tripPlanning | pctNeedingWholeTripParatransit | 54.00 | 3.33 |
| tripPlanning | meanWaitWhenParatransitNeededMin | *(empty)* | 5.19 |
| realtime | meanAbsEtaErrorMin | 2.44 | 2.12 |
| realtime | crowdingKnownRatePct | 0.00 | 25.90 |
| realtime | crowdingAccuracyWhenKnownPct | 0.00 | 85.71 |
| realtime | crowdingSystemWideAccuracyPct | 33.33 | 46.90 |
| paratransitDirect | available | false | true |
| paratransitDirect | matchRatePct | 0.00 | 94.30 |
| paratransitDirect | meanWaitMin | *(empty)* | 6.49 |

The two headline values are the itinerary found rate, **46.0 %** for Existing
and **96.67 %** for Proposed. If a run does not produce those two, it is not
reproducing the baseline configuration and nothing else below will match either.

### `results/ablation.csv`

| FR | `metric` | `proposedValue` | `withCapabilityRemoved` | `delta` |
|---|---|---|---|---|
| FR1 | itineraryFoundRatePct | 96.67 | 96.67 | 0.00 |
| FR2 | itineraryFoundRatePct | 96.67 | 60.33 | 36.33 |
| FR3 | meanAlternativesShown | 1.97 | 1.97 | 0.00 |
| FR4 | meanAbsEtaErrorMin | 2.12 | 2.12 | 0.00 |
| FR5 | crowdingSystemWideAccuracyPct | 46.90 | 33.33 | 13.57 |
| FR6 | endToEndSuccessRatePct | 100.00 | 96.67 | 3.33 |
| FR7 | itineraryFoundRatePct | 96.67 | 96.67 | 0.00 |
| FR8 | matchRatePct | 94.30 | 72.93 | 21.37 |

FR3, FR4 and FR7 are enabled in both configurations, so their delta is zero by
construction; they carry `sharedNotDifferentiator = true` and are not rows of the
paper's Table VII. FR1's delta is also zero here, which is a property of this
network rather than of the flag: see the note below.

### `results/network-stats.json`

40 stops of which 32 are served and 8 unserved, 25 transfer nodes, 16 bus-rail
interchange stops, 16 stops served by rail, 36 of 45 route pairs sharing at least
one stop, 493.27 route-km, 8.09 km mean inter-stop spacing, 7 750 timetabled
connections, 1 268 vehicle trips per day, rail used by 62.76 % of the fastest
feasible itineraries, 0.98 mean transfers, 28.28 % direct itineraries, and a mean
query crow-fly distance of 15.33 km.

### `results/repeated-seeds.csv`

All five direction checks are 30 out of 30: Proposed finds more itineraries,
reaches higher end-to-end success, has lower ETA error, coordinated dispatch
beats naive hailing, and the FR2 gain exceeds the FR1 gain. Selected summary
rows, over 30 seed sets:

| `metric` | `mean` | `sd` | `min` | `max` |
|---|---|---|---|---|
| existing.itineraryFoundRatePct | 51.18 | 5.86 | 36.67 | 60.67 |
| proposed.itineraryFoundRatePct | 95.50 | 4.08 | 85.00 | 100.00 |
| proposed.endToEndSuccessRatePct | 99.78 | 0.34 | 98.67 | 100.00 |
| existing.meanAbsEtaErrorMin | 2.49 | 0.05 | 2.37 | 2.60 |
| proposed.meanAbsEtaErrorMin | 2.13 | 0.05 | 2.02 | 2.23 |
| proposed.crowdingKnownRatePct | 25.89 | 1.27 | 22.57 | 28.70 |
| proposed.crowdingAccuracyWhenKnownPct | 83.30 | 1.69 | 79.97 | 86.62 |
| proposed.crowdingSystemWideAccuracyPct | 46.28 | 0.87 | 44.31 | 47.97 |
| proposed.paratransitMatchRatePct | 94.65 | 0.38 | 93.90 | 95.27 |
| delta.fr1.itineraryFoundRatePct | 5.96 | 6.26 | 0.00 | 25.33 |
| delta.fr2.itineraryFoundRatePct | 31.42 | 5.08 | 18.67 | 42.33 |
| delta.fr5.crowdingSystemWideAccuracyPct | 12.95 | 0.87 | 10.98 | 14.63 |
| delta.fr6.endToEndSuccessRatePct | 4.28 | 3.93 | 0.00 | 13.67 |
| delta.fr8.paratransitMatchRatePct | 22.19 | 0.80 | 20.67 | 23.70 |

### `results/sensitivity.csv`

43 levels across ten sweeps, each averaged over 10 seed sets, 1 634 rows. Two
results worth checking: coordinated dispatch beats naive hailing at every driver
density, 50.75 % against 80.45 % at `driverRateMultiplier` 0.5 and 90.06 %
against 99.52 % at 2.0; and the consensus pipeline degrades gracefully under
random spam but not under collusion, with the Proposed ETA error rising from
2.15 min to 5.04 min and accuracy among published indicators falling from 83.6 %
to 43.4 % as `colludingSpamContributorFraction` goes from 0.05 to 0.50.

## The paper's single-run values come from a different implementation

The single-run numbers printed in the paper were produced by the original
**Python prototype** of this same model. This Java port implements the identical
model but draws from `java.util.Random` rather than NumPy's generator, so the
same seeds produce different number streams and the single-run values differ
slightly. They are not expected to match digit for digit, and a mismatch against
the paper is not a reproduction failure.

The sense in which the two agree is that **every single-run value in the paper's
Tables VII, VIII and IX falls inside the 30-seed range this port produces**, in
`results/repeated-seeds.csv`. Side by side:

| Metric | Paper (Python) | This port | 30-seed [min, max] |
|---|---|---|---|
| Itinerary found rate, Existing | 48.7 % | 46.0 % | [36.67, 60.67] |
| Itinerary found rate, Proposed | 99.7 % | 96.67 % | [85.00, 100.00] |
| End-to-end success, Existing | 48.7 % | 46.0 % | [36.67, 60.67] |
| End-to-end success, Proposed | 100.0 % | 100.0 % | [98.67, 100.00] |
| Mean alternatives, Existing | 1.01 | 1.05 | [1.01, 1.07] |
| Mean alternatives, Proposed | 1.96 | 1.97 | [1.70, 2.15] |
| Need whole-trip para-transit, Existing | 51.3 % | 54.0 % | [39.33, 63.33] |
| Need whole-trip para-transit, Proposed | 0.3 % | 3.33 % | [0.00, 15.00] |
| Mean absolute ETA error, Existing | 2.48 min | 2.44 min | [2.37, 2.60] |
| Mean absolute ETA error, Proposed | 2.13 min | 2.12 min | [2.02, 2.23] |
| Crowding known, Proposed | 25.6 % | 25.9 % | [22.57, 28.70] |
| Crowding accuracy system-wide, Proposed | 45.8 % | 46.90 % | [44.31, 47.97] |
| Δ FR1, itinerary found rate | 0.3 pp | 0.00 pp | [0.00, 25.33] |
| Δ FR2, itinerary found rate | 37.7 pp | 36.33 pp | [18.67, 42.33] |
| Δ FR5, crowding accuracy | 12.5 pp | 13.57 pp | [10.98, 14.63] |
| Δ FR6, end-to-end success | 0.3 pp | 3.33 pp | [0.00, 13.67] |
| Δ FR8, para-transit match rate | 20.8 pp | 21.37 pp | [20.67, 23.70] |

The paper's remark that roughly 82 % of published crowding indicators are correct
corresponds to `crowdingAccuracyWhenKnownPct`: 85.71 % in this port's baseline
run, and 83.30 ± 1.69 with range [79.97, 86.62] over 30 seeds.

The two largest divergences are FR1 and FR6, both close to zero in absolute
terms. FR1 contributes exactly nothing on the seed-42 network in this port,
because every origin-destination pair rail could serve is already reachable by
bus; the paper's 0.3 pp is the same finding at single-run resolution. The 30-seed
spread shows the contribution is strongly network-dependent, and the `stops`
sweep in `results/sensitivity.csv` confirms it directly: the FR1 gain rises from
0.33 pp at 25 stops to 18.40 pp at 80 stops. The paper's conclusion that FR2
outweighs FR1 holds in all 30 seed sets.

## A note on the CSV views

Each `/csv/…` endpoint is a read-only view of the JSON endpoint of the same
name, and shares its cache, defaults and clamping. They are served from separate
paths rather than by content negotiation, so a request with `Accept: */*` still
resolves to JSON.

See [`results/README.md`](../results/README.md) for the column definitions.
