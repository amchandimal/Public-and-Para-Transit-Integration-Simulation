package com.transit.simulation2;

/** A single simulated GPS-to-GPS trip planning query. */
public record Query(double ox, double oy, double dx, double dy, double departMin, int hour) {
}
