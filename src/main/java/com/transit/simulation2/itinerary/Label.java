package com.transit.simulation2.itinerary;

import com.transit.simulation2.model.Connection;

import java.util.ArrayList;
import java.util.List;

/** A Pareto-optimal itinerary state at a stop: (arrival time, transfers, fare) plus its path. */
public class Label {
    public final double arrTime;
    public final int transfers;
    public final double fare;
    public final List<Connection> path;

    public Label(double arrTime, int transfers, double fare, List<Connection> path) {
        this.arrTime = arrTime;
        this.transfers = transfers;
        this.fare = fare;
        this.path = path;
    }

    public boolean dominates(Label other) {
        boolean leAll = this.arrTime <= other.arrTime && this.transfers <= other.transfers && this.fare <= other.fare;
        boolean ltAny = this.arrTime < other.arrTime || this.transfers < other.transfers || this.fare < other.fare;
        return leAll && ltAny;
    }

    public Label extend(Connection conn, int newTransfers, double newFare) {
        List<Connection> newPath = new ArrayList<>(this.path);
        newPath.add(conn);
        return new Label(conn.arrTime(), newTransfers, newFare, newPath);
    }
}
