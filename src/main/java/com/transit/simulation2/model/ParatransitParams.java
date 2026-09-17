package com.transit.simulation2.model;

/** Para-transit (taxi / three-wheeler) service parameters. */
public record ParatransitParams(
        double baseFare,
        double perKmRate,
        double speedKmh,
        double meanWaitMin,
        double driverDensityPeak,
        double driverDensityOffpeak
) {
    public static ParatransitParams defaultParams() {
        return new ParatransitParams(80.0, 45.0, 28.0, 4.5, 1.0, 0.6);
    }
}
