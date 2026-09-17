package com.transit.simulation2.network;

import com.transit.simulation2.config.SimulationParameters;
import com.transit.simulation2.model.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Generates the synthetic multimodal network on which every scenario is evaluated: a
 * representative mid-sized, multi-operator, data-scarce transit region (Section V.A.2 of the
 * paper). The generator works in three steps.
 *
 * <ul>
 *   <li>{@code stops} stops are placed uniformly at random over the areaX x areaY km service
 *       area, or around three centres at the clustered level of the stop-placement sweep.</li>
 *   <li>Each of the ten routes samples a random subset of stops (5-9 bus, 8-10 rail) and chains
 *       them in nearest-neighbour order, so route overlap, transfer nodes and bus/rail
 *       interchanges arise wherever routes happen to share a stop.</li>
 *   <li>Every route is timetabled independently in both directions over 05:00-23:00 with its
 *       own headway, speed and fare tariff, a Gaussian first-departure jitter (sigma = 1.5 min)
 *       and a half-headway offset for the reverse direction.</li>
 * </ul>
 */
@Component
public class NetworkGenerator {

    public static final int DEFAULT_SEED = 42;

    private record RouteSpec(String mode, int nStopsOnRoute, int headwayMin, double speedKmh,
                              double fareBase, double farePerKm) {
    }

    private static final List<RouteSpec> ROUTE_SPECS = List.of(
            new RouteSpec("bus", 7, 12, 22, 20, 6),
            new RouteSpec("bus", 6, 15, 20, 20, 6),
            new RouteSpec("bus", 8, 10, 24, 20, 6),
            new RouteSpec("bus", 5, 20, 18, 20, 6),
            new RouteSpec("bus", 9, 14, 23, 20, 6),
            new RouteSpec("bus", 6, 18, 21, 20, 6),
            new RouteSpec("bus", 7, 16, 22, 20, 6),
            new RouteSpec("bus", 5, 25, 19, 20, 6),
            new RouteSpec("rail", 10, 30, 45, 40, 4),
            new RouteSpec("rail", 8, 40, 45, 40, 4)
    );

    /** Cluster centres used by the clustered level of the stop-placement sensitivity sweep (km). */
    private static final double[][] CLUSTER_CENTRES = {{8, 7}, {26, 18}, {17, 12}};

    public NetworkData generate() {
        return generate(new Random(DEFAULT_SEED));
    }

    /** Generates the network from the baseline parameter set: 40 uniformly placed stops over 35 x 25 km. */
    public NetworkData generate(Random rng) {
        return generate(rng, SimulationParameters.DEFAULTS);
    }

    public NetworkData generate(Random rng, SimulationParameters p) {
        int nStops = p.stops();
        List<Stop> stops = new ArrayList<>();
        for (int i = 0; i < nStops; i++) {
            double x, y;
            if (p.clusteredStops()) {
                double[] c = CLUSTER_CENTRES[i % CLUSTER_CENTRES.length];
                do {
                    x = c[0] + rng.nextGaussian() * 4.0;
                    y = c[1] + rng.nextGaussian() * 3.0;
                } while (x < 0 || x > p.areaXKm() || y < 0 || y > p.areaYKm());
            } else {
                x = rng.nextDouble() * p.areaXKm();
                y = rng.nextDouble() * p.areaYKm();
            }
            stops.add(new Stop(String.format("S%02d", i), x, y));
        }
        Map<String, Stop> stopLookup = new HashMap<>();
        for (Stop s : stops) stopLookup.put(s.id(), s);

        List<Route> routes = new ArrayList<>();
        for (int rIdx = 0; rIdx < ROUTE_SPECS.size(); rIdx++) {
            RouteSpec spec = ROUTE_SPECS.get(rIdx);
            List<Integer> pool = new ArrayList<>();
            for (int i = 0; i < nStops; i++) pool.add(i);
            Collections.shuffle(pool, rng);
            List<Integer> chosen = new ArrayList<>(pool.subList(0, Math.min(spec.nStopsOnRoute(), nStops)));

            List<Integer> chain = new ArrayList<>();
            chain.add(chosen.remove(0));
            while (!chosen.isEmpty()) {
                Stop last = stops.get(chain.get(chain.size() - 1));
                int bestIdx = 0;
                double bestDist = Double.MAX_VALUE;
                for (int i = 0; i < chosen.size(); i++) {
                    double d = last.distanceTo(stops.get(chosen.get(i)));
                    if (d < bestDist) {
                        bestDist = d;
                        bestIdx = i;
                    }
                }
                chain.add(chosen.remove(bestIdx));
            }
            List<String> stopIds = new ArrayList<>();
            for (int idx : chain) stopIds.add(String.format("S%02d", idx));

            routes.add(new Route(String.format("R%02d", rIdx), spec.mode(), stopIds,
                    spec.headwayMin(), spec.speedKmh(), spec.fareBase(), spec.farePerKm()));
        }

        double serviceStart = 5 * 60;
        double serviceEnd = 23 * 60;
        List<Connection> connections = new ArrayList<>();

        for (Route route : routes) {
            int nTrips = (int) ((serviceEnd - serviceStart) / route.getHeadwayMin());
            List<List<String>> directions = List.of(
                    route.getStopIds(),
                    reversed(route.getStopIds())
            );
            for (int direction = 0; direction < directions.size(); direction++) {
                List<String> seq = directions.get(direction);
                for (int t = 0; t < nTrips; t++) {
                    double depFirst = serviceStart + t * route.getHeadwayMin() + rng.nextGaussian() * 1.5
                            + (direction == 1 ? route.getHeadwayMin() / 2.0 : 0.0);
                    String tripId = route.getId() + "-D" + direction + "-T" + String.format("%03d", t);
                    double curTime = depFirst;
                    for (int i = 0; i < seq.size() - 1; i++) {
                        String a = seq.get(i);
                        String b = seq.get(i + 1);
                        double legKm = stopLookup.get(a).distanceTo(stopLookup.get(b));
                        double legMin = legKm / route.getSpeedKmh() * 60.0;
                        double depTime = curTime;
                        double arrTime = curTime + legMin;
                        double fare = route.getFareBase() + route.getFarePerKm() * legKm;
                        connections.add(new Connection(a, depTime, b, arrTime,
                                route.getId(), tripId, route.getMode(), round2(fare)));
                        curTime = arrTime;
                    }
                }
            }
        }

        connections.sort(Comparator.comparingDouble(Connection::depTime));

        ParatransitParams paratransit = ParatransitParams.defaultParams();

        return new NetworkData(stops, routes, connections, paratransit, serviceStart, serviceEnd, stopLookup);
    }

    private static List<String> reversed(List<String> in) {
        List<String> out = new ArrayList<>(in);
        Collections.reverse(out);
        return out;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
