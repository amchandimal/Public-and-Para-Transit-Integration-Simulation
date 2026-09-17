# Results

Output of the `/csv/…` endpoints with the default seeds and default options
(`n=30` seed sets, `seeds=10` per sweep level), as listed in
[`../docs/REPRODUCING.md`](../docs/REPRODUCING.md). The files are committed so
that the numbers can be read without running the code. The same numbers are
shown on the dashboard at <http://localhost:8080> when the application runs.

All values are written with full `double` precision and a `.` decimal
separator, independent of the machine's locale. An empty field is a `null` in
the underlying response, not a zero. See
[`../docs/REPRODUCING.md`](../docs/REPRODUCING.md) for which file answers which
paper table and for the expected values.

## `stacked.csv`

The fully stacked Existing versus Proposed comparison. Paper Tables VIII
(`tripPlanning` rows) and IX (`realtime` rows). 12 rows.

| Column | Meaning |
|---|---|
| `metricGroup` | `tripPlanning`, `realtime` or `paratransitDirect` |
| `metric` | Metric name, as in the JSON response |
| `existing` | Value under the Existing capability setting |
| `proposed` | Value under the Proposed capability setting |

`meanWaitWhenParatransitNeededMin` and `meanWaitMin` are empty for Existing
because para-transit is unavailable there, so no request is ever matched.
`available` is a boolean, `true` or `false`; every other value is a number.

## `ablation.csv`

The isolated single-factor ablation, one row per FR aspect. Paper Table VII.
8 rows.

| Column | Meaning |
|---|---|
| `frAspect` | Full aspect label, FR1 to FR8 |
| `field` | The `Capabilities` field the aspect toggles |
| `metric` | The metric this aspect is scored on |
| `proposedValue` | The metric under the Proposed setting |
| `withCapabilityRemoved` | The metric with this one flag set back to its Existing value, everything else held at Proposed |
| `delta` | `proposedValue` minus `withCapabilityRemoved`, in percentage points for the rate metrics |
| `sharedNotDifferentiator` | `true` for FR3, FR4 and FR7, which are enabled in both configurations. Their delta is zero by construction and they are not rows of the paper's table. |

## `network-stats.json`

Realised properties of the seed-42 network, the one drawn in Fig. 1 of the paper,
plus the modal share and transfer counts of the fastest feasible itinerary over
the 300 baseline queries under the Proposed setting.

| Field | Meaning |
|---|---|
| `stops`, `servedStops`, `unservedStops` | Stops placed, and how many lie on at least one route |
| `transferNodes` | Stops served by two or more routes |
| `busRailInterchanges` | Stops served by both a bus and a rail route |
| `railStops` | Stops served by at least one rail route |
| `overlappingRoutePairs`, `routePairs` | Route pairs sharing at least one stop, out of all pairs |
| `routeKm`, `meanInterStopKm` | Total route length and mean spacing between consecutive stops on a route, in km |
| `connections`, `vehicleTrips` | Timetabled inter-stop legs, and distinct vehicle trips per service day |
| `railShareOfFastestItineraryPct` | Percentage of feasible queries whose fastest itinerary uses at least one rail leg |
| `meanTransfersFastestItinerary`, `directItinerarySharePct` | Mean transfer count of the fastest itinerary, and the percentage with none |
| `meanQueryCrowFlyKm` | Mean straight-line origin-to-destination distance over the 300 queries |

## `repeated-seeds.csv`

The complete experiment re-run for 30 seed sets, where set 0 is the baseline
configuration and set *i* shifts all six seeds by 1000·*i*. Three sections in one
table, distinguished by the first column. 1 214 rows.

| Column | Meaning |
|---|---|
| `section` | `run`, `summary` or `direction` |
| `metric` | Metric key, or the name of the direction check |
| `seedSet` | `run` only: which seed set, 0 to 29 |
| `value` | `run`: the metric in that seed set. `direction`: the number of seed sets in which the check held. |
| `n` | `summary`: seed sets contributing to the row. `direction`: total seed sets. |
| `mean`, `sd`, `min`, `max` | `summary` only: distribution of the metric across seed sets. `sd` is the sample standard deviation, dividing by *n* − 1. |

Sections: 1 170 `run` rows (30 seed sets × 39 metrics), 38 `summary` rows and
6 `direction` rows. There is one fewer summary row than metrics because
`existing.paratransitMeanWaitMin` is `null` in every run and is therefore
omitted from the summary rather than summarised as zero.

Metric keys are `existing.*` and `proposed.*` for the two configurations,
`ablation.frXRemoved.*` for Proposed with one aspect flipped to its Existing
value, `delta.frX.*` for the difference in percentage points, `network.*` for
the realised network properties of that seed set, and `reports.meanPerTrip` for
the mean number of crowdsourced reports generated per trip instance.

The `direction` rows report how many seed sets preserved the direction of each
headline gain: `proposedFindsMoreItineraries`, `proposedHigherEndToEndSuccess`,
`proposedLowerEtaError`, `coordinatedDispatchBeatsNaive` and
`fr2DeltaExceedsFr1Delta`. A sixth row, `runs`, is the total number of seed sets
rather than a check, so its `value` always equals its `n`.

## `sensitivity.csv`

One-at-a-time sweeps around the baseline, each level averaged over 10 seed sets.
43 levels across ten sweeps, 1 634 rows, one per level and metric.

| Column | Meaning |
|---|---|
| `parameter` | Sweep name, as listed by `/api/comparison/sensitivity/parameters` |
| `level` | The level's label. Numeric for most sweeps; `contributorReliability` is labelled by the mean reliability α/(α+2) rather than by α, and `stopPlacement` by `uniform` or `clustered`. |
| `seedSets` | Seed sets averaged at this level, 10 by default |
| `metric` | Metric key, the same set as in `repeated-seeds.csv` |
| `mean`, `sd` | Mean and sample standard deviation of that metric across the seed sets at that level |

Each sweep includes its baseline level, so the row where `parameter` is
`officialFeedCoverage` and `level` is `0.55` is the baseline configuration
averaged over 10 seed sets, not a perturbation of it. Only the named parameter
is varied; everything else stays at its baseline value.
