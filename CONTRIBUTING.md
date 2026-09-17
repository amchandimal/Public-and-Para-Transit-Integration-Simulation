# Contributing

This repository is the companion code of a conference paper, so its first
obligation is to keep reproducing the results the paper reports. Changes are
welcome, but a change that alters the published numbers without saying so is a
defect, however good the code is.

## Build and run

```bash
mvn -B verify
```

```bash
mvn spring-boot:run
```

The dashboard is then at <http://localhost:8080> and the REST API under
`/api/comparison`. Requirements are a JDK 21 or later and Maven 3.9 or later;
the first build needs network access for the Spring Boot dependencies.

## Tests

`mvn -B verify` runs the suite. It takes about 25 s and is deliberately kept
under a minute, so it is cheap enough to run on every change.

| Test | What it locks |
|---|---|
| `BaselineSnapshotTest` | The stacked comparison and the ablation rows equal committed snapshots under `src/test/resources/baseline/` to within 1e-9. Reports every differing metric at once. |
| `DeterminismTest` | Repeated evaluations are identical; the network seed changes the network but not the shared inputs; `forSeedSet(i)` shifts the six seeds and nothing else. |
| `ParametersTest` | Every copy method of `SimulationParameters` changes exactly the fields it names, checked reflectively over the record components. |
| `RobustnessServiceTest` | The repeated-seed analysis and the sweeps aggregate what they claim to, and the ten sweeps still have 43 levels. |
| `ApiEndpointsTest` | Every endpoint under `/api/comparison` answers 200 with the right content type, and `/stacked` serves the baseline rates. |

If a change moves a published metric, `BaselineSnapshotTest` fails with a diff
naming each metric, its expected value, its new value and the size of the change.
Read that diff before updating the snapshot: it is the check that stops the
paper's numbers drifting silently.

The GitHub Actions workflow additionally starts the packaged application and
calls the live API, so the contract is checked against a running service as well
as in-process.

## The determinism contract

With the seeds in `SimulationParameters.DEFAULTS`,
`GET /api/comparison/stacked` must report an itinerary-found rate of
**46.0 %** for Existing and **96.66666666666667 %** for Proposed.

This must hold after every change. It is not a style rule: the paper's tables,
the committed files under `results/` and the ranges in `docs/REPRODUCING.md` are
all derived from this baseline, and a silent change to it invalidates them.

To check locally:

```bash
curl -s http://localhost:8080/api/comparison/csv/stacked | grep '^tripPlanning,itineraryFoundRatePct,'
```

The contract breaks if you change any of the following, so treat each as a
deliberate, documented decision rather than a refactor:

- numeric constants in the engines, or the values in `SimulationParameters.DEFAULTS`
- any of the six seeds
- the **order and number of draws** taken from a `Random`. Adding, removing or
  reordering a call to `RandomUtils` or to `Random` shifts every subsequent draw,
  even when the change looks equivalent. This is the easiest way to break the
  contract by accident.
- the iteration order of a collection that feeds a draw or an accumulation
- the structure of a response consumed by the dashboard or the export endpoints

If a change is meant to alter the results, say so explicitly in the pull request,
regenerate `results/` as below, and update the expected values in
`src/test/resources/baseline/`, `docs/REPRODUCING.md`, `README.md`,
`CONTRIBUTING.md` and `.github/workflows/build.yml`.

## Regenerating the results

Start the application, then fetch the five CSV views:

```bash
curl -s "http://localhost:8080/api/comparison/csv/stacked"              -o results/stacked.csv
curl -s "http://localhost:8080/api/comparison/csv/ablation"             -o results/ablation.csv
curl -s "http://localhost:8080/api/comparison/network-stats"            -o results/network-stats.json
curl -s "http://localhost:8080/api/comparison/csv/repeated-seeds?n=30"  -o results/repeated-seeds.csv
curl -s "http://localhost:8080/api/comparison/csv/sensitivity?seeds=10" -o results/sensitivity.csv
```

Commit the regenerated files together with the change that caused them to move,
never separately. See [docs/REPRODUCING.md](docs/REPRODUCING.md), which also
maps each file to the dashboard panel that shows the same numbers.

## Coding conventions

Formatting is described by `.editorconfig` and most editors will apply it: UTF-8,
LF line endings, four-space indentation in Java, two spaces in XML, HTML, YAML
and Markdown, a trailing newline, no trailing whitespace.

Beyond formatting:

- **Terminology follows the paper.** Use capability flag, FR1-FR8, Existing and
  Proposed, isolated single-factor ablation, fully stacked comparison, official
  feed, crowdsourced report, consensus, para-transit, naive hailing, coordinated
  dispatch, repeated-seed analysis, sensitivity sweep. Consistency matters more
  than elegance here, because readers arrive from the paper.
- **Every public type carries a class Javadoc** of one to three sentences saying
  what it does in the model. Method Javadoc is for behaviour that is not obvious
  from the signature; do not write comments that restate the code.
- **Write plain technical English** aimed at a reader who has never seen this
  project's history. No decorative separators or banners, no emoji, no notes
  addressed to a particular person, and no references to how or when a piece of
  code came to be written.
- **Keep the engines free of presentation concerns.** Formatting belongs in the
  controller or in `util/Csv`, not in a metric.
- **New parameters go in `SimulationParameters`**, not as constants scattered
  through an engine, so the robustness analysis can vary them.

## Pull requests

State what changed and why, and whether the determinism contract still holds.
The build workflow must be green. If a change touches the model rather than the
plumbing, describe the effect on the results and include the regenerated
`results/` files.
