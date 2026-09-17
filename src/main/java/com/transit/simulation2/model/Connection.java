package com.transit.simulation2.model;

/** A single scheduled trip leg between two consecutive stops (mode-tagged for FR1 filtering). */
public record Connection(
        String depStop, double depTime,
        String arrStop, double arrTime,
        String routeId, String tripId,
        String mode, double fare
) {
}
