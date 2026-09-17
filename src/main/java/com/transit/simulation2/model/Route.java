package com.transit.simulation2.model;

import java.util.List;

/** A scheduled bus or rail route: an ordered stop sequence plus service parameters. */
public class Route {
    private final String id;
    private final String mode; // "bus" | "rail"
    private final List<String> stopIds;
    private final int headwayMin;
    private final double speedKmh;
    private final double fareBase;
    private final double farePerKm;

    public Route(String id, String mode, List<String> stopIds, int headwayMin,
                 double speedKmh, double fareBase, double farePerKm) {
        this.id = id;
        this.mode = mode;
        this.stopIds = stopIds;
        this.headwayMin = headwayMin;
        this.speedKmh = speedKmh;
        this.fareBase = fareBase;
        this.farePerKm = farePerKm;
    }

    public String getId() { return id; }
    public String getMode() { return mode; }
    public List<String> getStopIds() { return stopIds; }
    public int getHeadwayMin() { return headwayMin; }
    public double getSpeedKmh() { return speedKmh; }
    public double getFareBase() { return fareBase; }
    public double getFarePerKm() { return farePerKm; }
}
