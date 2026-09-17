package com.transit.simulation2.paratransit;

import com.transit.simulation2.capabilities.Capabilities;
import com.transit.simulation2.util.RandomUtils;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Para-transit engine (FR6, FR8).
 *
 * FR6 controls whether para-transit is available as an option at all; with it disabled,
 * requestParatransit returns null and the trip fails. FR8 controls how a request is handled
 * once available: naive hailing (a single un-retried attempt with a fixed patience threshold)
 * or coordinated dispatch (app-based, with retries and a search radius widened on timeout).
 * Both draw from the same Poisson driver-arrival model, so FR8's marginal effect is isolated
 * from FR6's.
 *
 * Model (Section V.B.3 of the paper): driver availability at the pickup point is a
 * Poisson process with hour-dependent rate lambda = base rate x density
 * (0.35 x 1.0 = 0.35 /min in the peaks 07-09 h and 17-19 h, 0.20 x 0.6 = 0.12 /min
 * off-peak), so the wait for the first available driver is exponential with mean
 * 1/lambda. Naive hailing makes one draw and fails beyond NAIVE_PATIENCE_MIN.
 * Coordinated dispatch searches for up to COORD_SEARCH_TIMEOUT_MIN per attempt with the
 * search radius widened so that the effective rate is lambda x (1 + 0.4 a) on attempt a,
 * adds COORD_ESCALATION_PENALTY_MIN after each timeout, and fails after COORD_MAX_RETRIES.
 * The optional rate multiplier scales lambda for the driver-availability sensitivity sweep;
 * at the baseline it is 1.0.
 */
@Component
public class ParatransitEngine {

    public static final double NAIVE_PATIENCE_MIN = 8.0;
    public static final double COORD_SEARCH_TIMEOUT_MIN = 5.0;
    public static final int COORD_MAX_RETRIES = 2;
    public static final double COORD_ESCALATION_PENALTY_MIN = 4.0;
    public static final double RADIUS_WIDENING_PER_ATTEMPT = 0.4;

    public double driverArrivalRate(int hour, double densityPeak, double densityOffpeak) {
        boolean isPeak = (hour >= 7 && hour <= 9) || (hour >= 17 && hour <= 19);
        double baseRate = isPeak ? 0.35 : 0.20;
        double density = isPeak ? densityPeak : densityOffpeak;
        return baseRate * density;
    }

    /** Returns null when para-transit is unavailable (FR6 disabled), otherwise the outcome of one request, dispatched naively or coordinated according to FR8. */
    public DispatchOutcome requestParatransit(int hour, Random rng, Capabilities caps) {
        return requestParatransit(hour, rng, caps, 1.0);
    }

    /** As above, with the driver arrival rate scaled by {@code rateMultiplier}. */
    public DispatchOutcome requestParatransit(int hour, Random rng, Capabilities caps, double rateMultiplier) {
        if (!caps.paratransit()) {
            return null;
        }

        double rate = driverArrivalRate(hour, 1.0, 0.6) * rateMultiplier;

        if (!caps.lastmileFallback()) {
            double wait = RandomUtils.nextExponential(rng, 1 / rate);
            if (wait <= NAIVE_PATIENCE_MIN) {
                return new DispatchOutcome("MATCHED", 1, wait);
            }
            return new DispatchOutcome("FAILED", 1, NAIVE_PATIENCE_MIN);
        }

        double totalWait = 0.0;
        for (int attempt = 0; attempt <= COORD_MAX_RETRIES; attempt++) {
            double effectiveRate = rate * (1 + RADIUS_WIDENING_PER_ATTEMPT * attempt);
            double wait = RandomUtils.nextExponential(rng, 1 / effectiveRate);
            if (wait <= COORD_SEARCH_TIMEOUT_MIN) {
                totalWait += wait;
                return new DispatchOutcome("MATCHED", attempt + 1, totalWait);
            }
            totalWait += COORD_SEARCH_TIMEOUT_MIN + (attempt < COORD_MAX_RETRIES ? COORD_ESCALATION_PENALTY_MIN : 0);
        }
        return new DispatchOutcome("FAILED", COORD_MAX_RETRIES + 1, totalWait);
    }
}
