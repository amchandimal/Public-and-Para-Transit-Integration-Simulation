package com.transit.simulation2.realtime;

import com.transit.simulation2.capabilities.Capabilities;
import com.transit.simulation2.config.SimulationParameters;
import com.transit.simulation2.util.RandomUtils;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Real-time information engine (FR4, FR5).
 *
 * FR4 is a shared baseline present in both Existing and Proposed: an official feed that covers
 * a fraction of trips with a moderately accurate delay estimate and carries no crowding signal.
 * FR5 layers crowdsourced reports on top and is the differentiator: independent passenger
 * reports pass a three-layer validation pipeline of plausibility filtering, credibility scoring
 * and consensus formation.
 *
 * The public constants hold the baseline values. Every method also has an overload taking a
 * {@link SimulationParameters}, through which the sensitivity sweeps vary feed coverage,
 * contributor reliability, spam, participation and the consensus thresholds.
 */
@Component
public class RealtimeEngine {

    public static final double OFFICIAL_FEED_COVERAGE = 0.55;
    public static final double OFFICIAL_FEED_NOISE_MIN = 3.0;

    public static final int K_MIN_REPORTS = 3;
    public static final double TEMPORAL_WINDOW_MIN = 6.0;
    public static final double CONSENSUS_AGREEMENT_THRESHOLD = 0.65;
    public static final double TOLERANCE_MIN = 3.0;
    public static final double HIST_ACCURACY_ALPHA = 0.3;
    public static final double W_HIST = 0.5, W_CONTEXT = 0.5;

    private static final List<String> CROWD_LEVELS = List.of("low", "medium", "high");

    public Map<String, GroundTruthEntry> simulateGroundTruth(List<String> tripIds, Random rng, double disruptionProb) {
        Map<String, GroundTruthEntry> truth = new LinkedHashMap<>();
        for (String tid : tripIds) {
            double delay = rng.nextDouble() < disruptionProb
                    ? 12 + rng.nextGaussian() * 5
                    : 1.5 + rng.nextGaussian() * 2.0;
            delay = Math.max(delay, -2);
            boolean peak = rng.nextDouble() < 0.35;
            double[] crowdProbs = peak ? new double[]{0.2, 0.35, 0.45} : new double[]{0.55, 0.3, 0.15};
            truth.put(tid, new GroundTruthEntry(delay, weightedChoice(rng, CROWD_LEVELS, crowdProbs)));
        }
        return truth;
    }

    public Map<String, OfficialFeedEntry> simulateOfficialFeed(List<String> tripIds, Map<String, GroundTruthEntry> groundTruth, Random rng) {
        return simulateOfficialFeed(tripIds, groundTruth, rng, SimulationParameters.DEFAULTS);
    }

    public Map<String, OfficialFeedEntry> simulateOfficialFeed(List<String> tripIds, Map<String, GroundTruthEntry> groundTruth,
                                                               Random rng, SimulationParameters p) {
        Map<String, OfficialFeedEntry> feed = new LinkedHashMap<>();
        for (String tid : tripIds) {
            if (rng.nextDouble() < p.officialFeedCoverage()) {
                double noisyDelay = groundTruth.get(tid).delay() + rng.nextGaussian() * p.officialFeedNoiseMin();
                feed.put(tid, new OfficialFeedEntry(true, noisyDelay));
            } else {
                feed.put(tid, new OfficialFeedEntry(false, null));
            }
        }
        return feed;
    }

    public List<Report> simulateCrowdsourcedReports(List<String> tripIds, Map<String, GroundTruthEntry> groundTruth,
                                                      Random rng, int nContributors, double meanReportersPerTrip) {
        return simulateCrowdsourcedReports(tripIds, groundTruth, rng,
                SimulationParameters.DEFAULTS.withContributors(nContributors, meanReportersPerTrip));
    }

    /**
     * Contributor reliability ~ Beta(alpha, beta); a fixed share of contributors are spammers and
     * every honest report is spam with a small probability. Random spam reports a random delay and
     * a random crowding level. Colluding spam, which is off at the baseline and enabled by the
     * colluding-spam sensitivity sweep, reports the same false delay (truth + offset) and "high"
     * crowding, that is, a coordinated attack able to pass the agreement test.
     */
    public List<Report> simulateCrowdsourcedReports(List<String> tripIds, Map<String, GroundTruthEntry> groundTruth,
                                                      Random rng, SimulationParameters p) {
        int nContributors = p.contributors();
        double[] reliability = new double[nContributors];
        boolean[] spamFlags = new boolean[nContributors];
        for (int i = 0; i < nContributors; i++) {
            reliability[i] = RandomUtils.nextBeta(rng, p.reliabilityBetaAlpha(), p.reliabilityBetaBeta());
            spamFlags[i] = rng.nextDouble() < p.spamContributorFraction();
        }

        List<Report> reports = new ArrayList<>();
        for (String tid : tripIds) {
            GroundTruthEntry truth = groundTruth.get(tid);
            int nReporters = Math.min(nContributors, RandomUtils.nextPoisson(rng, p.meanReportersPerTrip()));
            int[] reporterIds = RandomUtils.sampleWithoutReplacement(rng, nContributors, nReporters);

            for (int cid : reporterIds) {
                double rel = reliability[cid];
                boolean isSpam = spamFlags[cid] || rng.nextDouble() < p.perReportSpamProbability();
                double obsDelay;
                String obsCrowd;
                if (isSpam) {
                    if (p.colludingSpam()) {
                        obsDelay = truth.delay() + p.colludeOffsetMin() + rng.nextGaussian() * 0.5;
                        obsCrowd = "high";
                    } else {
                        obsDelay = rng.nextGaussian() * 25;
                        obsCrowd = CROWD_LEVELS.get(rng.nextInt(3));
                    }
                } else {
                    double noiseSigma = (1 - rel) * 8 + 0.5;
                    obsDelay = truth.delay() + rng.nextGaussian() * noiseSigma;
                    obsCrowd = rng.nextDouble() < rel ? truth.crowding() : CROWD_LEVELS.get(rng.nextInt(3));
                }
                double tOffset = RandomUtils.uniform(rng, 0, p.consensusWindowMin() * 1.5);
                reports.add(new Report(tid, cid, obsDelay, obsCrowd, tOffset));
            }
        }
        return reports;
    }

    public Map<String, ConsensusEntry> runConsensusPipeline(List<String> tripIds, List<Report> reports) {
        return runConsensusPipeline(tripIds, reports, SimulationParameters.DEFAULTS);
    }

    public Map<String, ConsensusEntry> runConsensusPipeline(List<String> tripIds, List<Report> reports, SimulationParameters p) {
        final int kMinReports = p.consensusMinReports();
        final double windowMin = p.consensusWindowMin();
        final double agreementThreshold = p.consensusAgreementThreshold();
        final double toleranceMin = p.consensusToleranceMin();

        Map<String, List<Report>> byTrip = new LinkedHashMap<>();
        for (Report r : reports) byTrip.computeIfAbsent(r.tripId(), k -> new ArrayList<>()).add(r);

        Map<Integer, Double> contributorHist = new HashMap<>();
        Map<String, ConsensusEntry> consensus = new LinkedHashMap<>();

        for (String tid : tripIds) {
            List<Report> tripReports = byTrip.getOrDefault(tid, List.of());
            List<Report> filtered = new ArrayList<>();
            for (Report r : tripReports) {
                if (r.obsDelay() >= -15 && r.obsDelay() <= 90) filtered.add(r);
            }
            if (filtered.isEmpty()) {
                consensus.put(tid, ConsensusEntry.notReached());
                continue;
            }

            double[] delays = filtered.stream().mapToDouble(Report::obsDelay).toArray();
            double medianDelay = median(delays);
            double[] credibility = new double[filtered.size()];
            for (int i = 0; i < filtered.size(); i++) {
                Report r = filtered.get(i);
                double histAcc = contributorHist.getOrDefault(r.contributorId(), 0.5);
                double deviation = Math.abs(r.obsDelay() - medianDelay);
                double contextAgree = Math.max(0.0, 1 - deviation / 20.0);
                credibility[i] = W_HIST * histAcc + W_CONTEXT * contextAgree;
            }

            List<Report> withinWindow = new ArrayList<>();
            List<Double> withinWindowCred = new ArrayList<>();
            for (int i = 0; i < filtered.size(); i++) {
                if (filtered.get(i).tOffsetMin() <= windowMin) {
                    withinWindow.add(filtered.get(i));
                    withinWindowCred.add(credibility[i]);
                }
            }

            if (withinWindow.size() < kMinReports) {
                consensus.put(tid, ConsensusEntry.notReached());
                continue;
            }

            int n = withinWindow.size();
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            Arrays.sort(order, Comparator.comparingDouble(i -> withinWindow.get(i).obsDelay()));

            double totalW = withinWindowCred.stream().mapToDouble(Double::doubleValue).sum();
            double cumW = 0.0;
            double weightedMedian = withinWindow.get(order[n - 1]).obsDelay();
            for (int idx : order) {
                cumW += withinWindowCred.get(idx);
                if (cumW >= totalW / 2.0) {
                    weightedMedian = withinWindow.get(idx).obsDelay();
                    break;
                }
            }

            double agreeW = 0.0;
            for (int i = 0; i < n; i++) {
                if (Math.abs(withinWindow.get(i).obsDelay() - weightedMedian) <= toleranceMin) {
                    agreeW += withinWindowCred.get(i);
                }
            }
            double agreeFrac = totalW > 0 ? agreeW / totalW : 0.0;

            if (agreeFrac >= agreementThreshold) {
                double crowdWeightedSum = 0.0;
                for (int i = 0; i < n; i++) {
                    int level = CROWD_LEVELS.indexOf(withinWindow.get(i).obsCrowding());
                    crowdWeightedSum += level * withinWindowCred.get(i);
                }
                int estLevel = Math.max(0, Math.min(2, (int) Math.round(crowdWeightedSum / totalW)));
                String estCrowding = CROWD_LEVELS.get(estLevel);
                double confidence = Math.min(1.0, agreeFrac);

                for (Report r : withinWindow) {
                    boolean close = Math.abs(r.obsDelay() - weightedMedian) <= toleranceMin;
                    double prev = contributorHist.getOrDefault(r.contributorId(), 0.5);
                    contributorHist.put(r.contributorId(), (1 - HIST_ACCURACY_ALPHA) * prev + HIST_ACCURACY_ALPHA * (close ? 1.0 : 0.0));
                }
                consensus.put(tid, new ConsensusEntry(true, weightedMedian, estCrowding, confidence));
            } else {
                consensus.put(tid, ConsensusEntry.notReached());
            }
        }

        return consensus;
    }

    /**
     * Combines the FR4 official feed and the FR5 consensus according to the
     * scenario's capability flags and scores the result against ground truth.
     */
    public RealtimeMetrics evaluateRealtimeLayer(List<String> tripIds, Map<String, GroundTruthEntry> groundTruth,
                                                  Map<String, OfficialFeedEntry> officialFeed,
                                                  Map<String, ConsensusEntry> consensus, Capabilities caps) {
        List<Double> etaErrors = new ArrayList<>();
        List<Boolean> crowdKnown = new ArrayList<>();
        List<Boolean> crowdCorrect = new ArrayList<>();

        for (String tid : tripIds) {
            GroundTruthEntry truth = groundTruth.get(tid);
            double estDelay = 0.0;
            String estCrowding = null;

            OfficialFeedEntry feed = officialFeed.get(tid);
            if (caps.realtimeStatus() && feed.covered()) {
                estDelay = feed.estDelay();
            }

            ConsensusEntry cons = consensus.get(tid);
            if (caps.crowdsourcing() && cons.reached) {
                estDelay = cons.estDelay;
                estCrowding = cons.estCrowding;
            }

            etaErrors.add(Math.abs(estDelay - truth.delay()));
            if (estCrowding == null) {
                crowdKnown.add(false);
                crowdCorrect.add(false);
            } else {
                crowdKnown.add(true);
                crowdCorrect.add(estCrowding.equals(truth.crowding()));
            }
        }

        double meanEtaError = etaErrors.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double crowdKnownRate = crowdKnown.stream().mapToDouble(b -> b ? 1.0 : 0.0).average().orElse(0.0) * 100;

        double knownAccSum = 0; int knownCount = 0;
        for (int i = 0; i < crowdKnown.size(); i++) {
            if (crowdKnown.get(i)) {
                knownCount++;
                if (crowdCorrect.get(i)) knownAccSum++;
            }
        }
        double accuracyWhenKnown = knownCount > 0 ? knownAccSum / knownCount * 100 : 0.0;

        double systemWideSum = 0;
        for (int i = 0; i < crowdKnown.size(); i++) {
            systemWideSum += crowdKnown.get(i) ? (crowdCorrect.get(i) ? 1.0 : 0.0) : (1.0 / 3.0);
        }
        double systemWideAccuracy = systemWideSum / crowdKnown.size() * 100;

        return new RealtimeMetrics(meanEtaError, crowdKnownRate, accuracyWhenKnown, systemWideAccuracy);
    }

    // Helpers

    private static String weightedChoice(Random rng, List<String> options, double[] probs) {
        double u = rng.nextDouble();
        double cum = 0.0;
        for (int i = 0; i < options.size(); i++) {
            cum += probs[i];
            if (u <= cum) return options.get(i);
        }
        return options.get(options.size() - 1);
    }

    private static double median(double[] arr) {
        double[] copy = arr.clone();
        Arrays.sort(copy);
        int n = copy.length;
        return n % 2 == 1 ? copy[n / 2] : (copy[n / 2 - 1] + copy[n / 2]) / 2.0;
    }
}
