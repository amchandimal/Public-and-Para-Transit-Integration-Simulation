package com.transit.simulation2.itinerary;

import com.transit.simulation2.capabilities.Capabilities;
import com.transit.simulation2.model.Connection;
import com.transit.simulation2.model.NetworkData;
import com.transit.simulation2.model.Stop;
import com.transit.simulation2.util.RandomUtils;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Trip aggregation engine. One algorithm serves Existing, Proposed and every ablation setting,
 * because the capability flags enter only as parameters of the search.
 *
 * <ul>
 *   <li>FR1 multimodal: allowedModes restricts the network to bus, or to bus and rail.</li>
 *   <li>FR2 aggregation: maxTransfers is 0 (direct connections only) or 4 (full aggregation).</li>
 *   <li>FR3 multi-criteria ranking: ranks the whole Pareto front under three weightings, or,
 *       when disabled, returns only the first itinerary found.</li>
 *   <li>FR7 access discovery: k is 1 (the single nearest stop) or 2 (the better of two).</li>
 * </ul>
 */
@Component
public class ItineraryEngine {

    public static final Map<String, double[]> RANKING_MODES = Map.of(
            "least_cost", new double[]{0.1, 0.2, 0.7},
            "min_transfers", new double[]{0.2, 0.7, 0.1},
            "min_time", new double[]{0.7, 0.15, 0.15}
    );

    public List<Stop> nearestAccessPoints(NetworkData net, double x, double y, int k) {
        List<Stop> ranked = new ArrayList<>(net.getStops());
        ranked.sort(Comparator.comparingDouble(s -> s.distanceTo(x, y)));
        return ranked.subList(0, Math.max(1, Math.min(k, ranked.size())));
    }

    public ParatransitLeg paratransitLeg(NetworkData net, double[] fromXy, double[] toXy, Random rng) {
        var pp = net.getParatransit();
        double dx = fromXy[0] - toXy[0];
        double dy = fromXy[1] - toXy[1];
        double km = Math.sqrt(dx * dx + dy * dy);
        double travelMin = km / pp.speedKmh() * 60.0;
        double waitMin = RandomUtils.nextExponential(rng, pp.meanWaitMin());
        double fare = pp.baseFare() + pp.perKmRate() * km;
        return new ParatransitLeg(waitMin, travelMin, round2(fare), km);
    }

    /**
     * Multi-criteria Connection Scan restricted to allowedModes (FR1) and
     * maxTransfers (FR2). Returns the Pareto-optimal itinerary set.
     */
    public List<Label> multicriteriaCSA(NetworkData net, String originStop, String destStop, double departAfter,
                                         Set<String> allowedModes, int maxTransfers) {
        List<Connection> connections = new ArrayList<>();
        for (Connection c : net.getConnections()) {
            if (allowedModes.contains(c.mode())) connections.add(c);
        }

        Map<String, ParetoSet> labels = new HashMap<>();
        for (Stop s : net.getStops()) labels.put(s.id(), new ParetoSet());
        labels.get(originStop).add(new Label(departAfter, 0, 0.0, new ArrayList<>()));

        Map<String, Label> boardedTripLabel = new HashMap<>();

        for (Connection conn : connections) {
            if (conn.depTime() < departAfter - 1e-6) continue;

            List<Label> candidates = new ArrayList<>();

            Label base = boardedTripLabel.get(conn.tripId());
            if (base != null) {
                candidates.add(base.extend(conn, base.transfers, base.fare));
            }

            ParetoSet depSet = labels.get(conn.depStop());
            if (depSet != null) {
                for (Label lbl : new ArrayList<>(depSet.asList())) {
                    if (lbl.arrTime <= conn.depTime() && lbl.transfers <= maxTransfers) {
                        int isTransfer = lbl.path.isEmpty() ? 0 : 1;
                        int newTransfers = lbl.transfers + isTransfer;
                        if (newTransfers > maxTransfers) continue;
                        double newFare = lbl.fare + conn.fare();
                        Label cand = lbl.extend(conn, newTransfers, newFare);
                        candidates.add(cand);
                        Label existingBoard = boardedTripLabel.get(conn.tripId());
                        if (existingBoard == null || newTransfers <= existingBoard.transfers) {
                            boardedTripLabel.put(conn.tripId(), cand);
                        }
                    }
                }
            }

            ParetoSet arrSet = labels.get(conn.arrStop());
            if (arrSet != null) {
                for (Label cand : candidates) arrSet.add(cand);
            }
        }

        return labels.get(destStop).asList();
    }

    public List<Label> rankItineraries(List<Label> paretoLabels, double[] weights) {
        if (paretoLabels.isEmpty()) return List.of();

        double minTime = Double.MAX_VALUE, maxTime = -Double.MAX_VALUE;
        double minTransfers = Double.MAX_VALUE, maxTransfers = -Double.MAX_VALUE;
        double minFare = Double.MAX_VALUE, maxFare = -Double.MAX_VALUE;
        for (Label l : paretoLabels) {
            minTime = Math.min(minTime, l.arrTime); maxTime = Math.max(maxTime, l.arrTime);
            minTransfers = Math.min(minTransfers, l.transfers); maxTransfers = Math.max(maxTransfers, l.transfers);
            minFare = Math.min(minFare, l.fare); maxFare = Math.max(maxFare, l.fare);
        }
        final double fMinTime = minTime, fMaxTime = maxTime;
        final double fMinTransfers = minTransfers, fMaxTransfers = maxTransfers;
        final double fMinFare = minFare, fMaxFare = maxFare;

        List<Label> sorted = new ArrayList<>(paretoLabels);
        sorted.sort(Comparator.comparingDouble(l -> {
            double nt = fMaxTime == fMinTime ? 0.0 : (l.arrTime - fMinTime) / (fMaxTime - fMinTime);
            double ntr = fMaxTransfers == fMinTransfers ? 0.0 : (l.transfers - fMinTransfers) / (fMaxTransfers - fMinTransfers);
            double nf = fMaxFare == fMinFare ? 0.0 : (l.fare - fMinFare) / (fMaxFare - fMinFare);
            return weights[0] * nt + weights[1] * ntr + weights[2] * nf;
        }));
        return sorted;
    }

    /** FR1, FR2 and FR3 combined: generate the itinerary set under this setting's restrictions, then rank it, or return the first itinerary when ranking is disabled. */
    public GenerateAndRankResult generateAndRank(NetworkData net, String origin, String dest, double departAfter, Capabilities caps) {
        Set<String> allowedModes = caps.multimodal() ? Set.of("bus", "rail") : Set.of("bus");
        int maxTransfers = caps.aggregation() ? 4 : 0;

        long t0 = System.nanoTime();
        List<Label> pareto = multicriteriaCSA(net, origin, dest, departAfter, allowedModes, maxTransfers);
        double elapsedMs = (System.nanoTime() - t0) / 1_000_000.0;

        if (!caps.multiCriteriaRanking()) {
            List<ItineraryView> chosen = pareto.isEmpty() ? List.of() : List.of(ItineraryView.of(pareto.get(0)));
            return new GenerateAndRankResult(pareto.size(), elapsedMs, Map.of("default", chosen));
        }

        Map<String, List<ItineraryView>> topByMode = new LinkedHashMap<>();
        for (var entry : RANKING_MODES.entrySet()) {
            List<Label> ranked = rankItineraries(pareto, entry.getValue());
            List<ItineraryView> top3 = new ArrayList<>();
            for (int i = 0; i < Math.min(3, ranked.size()); i++) top3.add(ItineraryView.of(ranked.get(i)));
            topByMode.put(entry.getKey(), top3);
        }
        return new GenerateAndRankResult(pareto.size(), elapsedMs, topByMode);
    }

    /** End-to-end query combining FR7 access discovery, the FR1-FR3 public-transport search, and the FR6 flag reporting whether para-transit remains available as a fallback. */
    public FullItineraryResult fullItineraryQuery(NetworkData net, double[] originXy, double[] destXy,
                                                   double departAfter, Random rng, Capabilities caps) {
        int k = caps.accessDiscovery() ? 2 : 1;
        List<Stop> oAccess = nearestAccessPoints(net, originXy[0], originXy[1], k);
        List<Stop> dAccess = nearestAccessPoints(net, destXy[0], destXy[1], k);

        GenerateAndRankResult bestPt = null;
        String bestOriginAccess = null, bestDestAccess = null;
        ParatransitLeg bestFirstMile = null, bestLastMile = null;

        outer:
        for (Stop oa : oAccess) {
            for (Stop da : dAccess) {
                if (oa.id().equals(da.id())) continue;
                ParatransitLeg firstMile = paratransitLeg(net, originXy, new double[]{oa.x(), oa.y()}, rng);
                double effectiveDepart = departAfter + firstMile.waitMin() + firstMile.travelMin();
                GenerateAndRankResult ptOut = generateAndRank(net, oa.id(), da.id(), effectiveDepart, caps);
                if (ptOut.paretoFrontSize() == 0) continue;
                ParatransitLeg lastMile = paratransitLeg(net, new double[]{da.x(), da.y()}, destXy, rng);
                bestPt = ptOut;
                bestOriginAccess = oa.id();
                bestDestAccess = da.id();
                bestFirstMile = firstMile;
                bestLastMile = lastMile;
                break outer;
            }
        }

        return new FullItineraryResult(bestOriginAccess, bestDestAccess, bestFirstMile, bestLastMile, bestPt, caps.paratransit());
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
