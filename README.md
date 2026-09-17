# Capability-Flag Transit Comparison Simulation

[![build](https://github.com/amchandimal/Public-and-Para-Transit-Integration-Simulation/actions/workflows/build.yml/badge.svg)](https://github.com/amchandimal/Public-and-Para-Transit-Integration-Simulation/actions/workflows/build.yml)

A discrete-event simulation of a multimodal public and para-transit journey
planning platform. Eight functional-requirement aspects (FR1-FR8) are expressed
as independent capability flags, and two named settings of those flags,
**Existing** and **Proposed**, are evaluated by the same engines on the same
synthetic network and the same stochastic inputs. The simulation measures what
each capability contributes, through an **isolated single-factor ablation** that
flips one flag at a time while holding everything else fixed, and a **fully
stacked comparison** of Existing against Proposed. See "Use of AI assistance"
for the tools used in preparing this repository.

## Capability flags

| Flag | Field | Controls | Existing | Proposed |
|---|---|---|---|---|
| FR1 | `multimodal` | bus only, or bus and rail | off | on |
| FR2 | `aggregation` | direct rides only, or up to 4 transfers | off | on |
| FR3 | `multiCriteriaRanking` | rank the Pareto front, or return the first itinerary | on | on |
| FR4 | `realtimeStatus` | use the official real-time feed | on | on |
| FR5 | `crowdsourcing` | use the crowdsourced consensus for ETA and crowding | off | on |
| FR6 | `paratransit` | para-transit available at all | off | on |
| FR7 | `accessDiscovery` | consider 1 or 2 nearest access stops | on | on |
| FR8 | `lastmileFallback` | naive hailing, or coordinated dispatch | off | on |

FR3, FR4 and FR7 are enabled in both settings, so the difference between
Existing and Proposed comes from FR1, FR2, FR5, FR6 and FR8.

Three engines evaluate a setting: `ItineraryEngine` (multi-criteria Connection
Scan, FR1-FR3, FR7), `RealtimeEngine` (official feed and three-layer
crowdsourcing pipeline, FR4-FR5) and `ParatransitEngine` (Poisson driver
arrival, FR6 and FR8). All of them run on a synthetic 40-stop, 10-route network
generated from a fixed seed.

## Requirements

- JDK 21 or later
- Maven 3.9 or later
- Network access on the first build, to download the dependencies. The dashboard
  also loads Chart.js from a CDN.

## Build and run

```bash
mvn -B verify
```

```bash
mvn spring-boot:run
```

`mvn -B verify` compiles, runs the test suite and packages the application. Once
it is running, open <http://localhost:8080> for the dashboard. The port is
`server.port` in `src/main/resources/application.properties`.

## Check the build

Every input is generated from fixed seeds, so results are identical on every run
and every JVM. With the default seeds the itinerary-found rate is 46.0 % for
Existing and 96.67 % for Proposed:

```bash
curl -s http://localhost:8080/api/comparison/csv/stacked | grep '^tripPlanning,itineraryFoundRatePct,'
```

```
tripPlanning,itineraryFoundRatePct,46.0,96.66666666666667
```

If those two values differ, the build is not reproducing the baseline.

## Dashboard

The dashboard at <http://localhost:8080> shows the capability flags, the
isolated ablation, the stacked comparison of trip planning, real-time
information quality and para-transit dispatch, and the properties of the
generated network. Two further panels run on demand: a repeated-seed analysis,
which re-runs the experiment over shifted seed sets, and one-at-a-time
sensitivity sweeps over the model's parameters.

## Metrics

| Metric | Meaning |
|---|---|
| Itinerary found rate | share of the 300 queries with a feasible scheduled itinerary |
| End-to-end success rate | share served by an itinerary or, failing that, a matched para-transit ride |
| Mean alternatives shown | mean size of the Pareto front over queries with an itinerary |
| Whole-trip para-transit share | share of queries with no scheduled itinerary |
| Mean absolute ETA error | mean of the absolute delay-estimate error over 3 000 trips; a trip with no estimate counts as "no delay" |
| Crowding known rate | share of trips with a published crowding indicator |
| Crowding accuracy when known | share of published indicators that are correct |
| System-wide crowding accuracy | per-trip score of 1 if correct, 0 if wrong, and 1/3 if nothing is published, averaged over all trips |
| Para-transit match rate and wait | share of dispatch requests matched to a driver, and the mean wait of those matched |

## REST API

All endpoints are GET under `/api/comparison` and return JSON.

| Endpoint | Returns |
|---|---|
| `/capabilities` | the Existing and Proposed flag settings |
| `/stacked` | Existing against Proposed |
| `/ablation` | one row per FR aspect: Proposed, with that flag removed, and the difference |
| `/full` | capabilities, ablation and stacked comparison together |
| `/scenario/existing`, `/scenario/proposed` | one setting |
| `/scenario/custom?multimodal=..&...` | any combination of the eight flags, each defaulting to `true` |
| `/network` | the generated network |
| `/network-stats` | realised properties of the network |
| `/parameters` | the baseline parameter set and its six seeds |
| `/repeated-seeds?n=30&fixNetwork=false` | the experiment re-run over `n` seed sets (1..200) |
| `/sensitivity?seeds=10&parameter=` | one-at-a-time sweeps, each level averaged over `seeds` seed sets (1..100) |
| `/sensitivity/parameters` | the names of the ten sweeps |
| `/scenario/parameterised?...` | any flag combination under any parameter set |

`/stacked`, `/ablation`, `/repeated-seeds` and `/sensitivity` also have
read-only CSV views under `/csv/`, for example
`/api/comparison/csv/stacked`. The repeated-seed and sensitivity endpoints
re-run the experiment many times; they execute in parallel and cache their
result.

## Results

The output of the CSV endpoints with the default seeds is committed under
[`results/`](results), so the numbers can be read without running the code.
[`results/README.md`](results/README.md) defines every column, and
[`docs/REPRODUCING.md`](docs/REPRODUCING.md) shows how to regenerate the files.

## Configuration

Every constant the analyses can vary, and the six random seeds, are fields of
the `SimulationParameters` record in `config/`. `DEFAULTS` is the baseline;
`GET /api/comparison/parameters` returns it. Most fields can be overridden per
request through `/scenario/parameterised`.

## Project layout

```
src/main/java/com/transit/simulation2/
  ComparisonService            builds the shared inputs, evaluates a setting, runs the comparisons
  capabilities/Capabilities    the eight flags, EXISTING and PROPOSED
  config/SimulationParameters  every variable constant and the six seeds
  network/NetworkGenerator     synthetic network generation
  itinerary/                   ItineraryEngine and the Pareto-front search
  realtime/                    RealtimeEngine
  paratransit/                 ParatransitEngine
  robustness/RobustnessService repeated-seed analysis and sensitivity sweeps
  controller/                  REST API
src/main/resources/static/index.html   dashboard
src/test/                              test suite and baseline snapshots
results/                               committed output of the CSV endpoints
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

See [CONTRIBUTING.md](CONTRIBUTING.md) for conventions and the determinism
contract every change must preserve. [CHANGELOG.md](CHANGELOG.md) records
released versions.

## Licence

MIT. See [LICENSE](LICENSE). To cite this software, see
[CITATION.cff](CITATION.cff).
