package com.transit.simulation2.itinerary;

import com.transit.simulation2.model.Connection;

import java.util.List;

/** A rounded, serialisable view of one {@link Label}, as returned in API responses. */
public record ItineraryView(double arrivalMin, int transfers, double fare, int legs, List<Connection> path) {
    public static ItineraryView of(Label l) {
        return new ItineraryView(round1(l.arrTime), l.transfers, round1(l.fare), l.path.size(), l.path);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
