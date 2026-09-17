package com.transit.simulation2.model;

/** A network node: bus stop, train station, or derived access point. */
public record Stop(String id, double x, double y) {

    public double distanceTo(Stop other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double distanceTo(double ox, double oy) {
        double dx = this.x - ox;
        double dy = this.y - oy;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
