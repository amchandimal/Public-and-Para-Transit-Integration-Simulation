package com.transit.simulation2.model;

import java.util.List;
import java.util.Map;

/** The full generated synthetic multimodal network. */
public class NetworkData {
    private final List<Stop> stops;
    private final List<Route> routes;
    private final List<Connection> connections; // sorted by depTime
    private final ParatransitParams paratransit;
    private final double serviceStartMin;
    private final double serviceEndMin;
    private final Map<String, Stop> stopLookup;

    public NetworkData(List<Stop> stops, List<Route> routes, List<Connection> connections,
                        ParatransitParams paratransit, double serviceStartMin, double serviceEndMin,
                        Map<String, Stop> stopLookup) {
        this.stops = stops;
        this.routes = routes;
        this.connections = connections;
        this.paratransit = paratransit;
        this.serviceStartMin = serviceStartMin;
        this.serviceEndMin = serviceEndMin;
        this.stopLookup = stopLookup;
    }

    public List<Stop> getStops() { return stops; }
    public List<Route> getRoutes() { return routes; }
    public List<Connection> getConnections() { return connections; }
    public ParatransitParams getParatransit() { return paratransit; }
    public double getServiceStartMin() { return serviceStartMin; }
    public double getServiceEndMin() { return serviceEndMin; }
    public Stop getStop(String id) { return stopLookup.get(id); }
}
