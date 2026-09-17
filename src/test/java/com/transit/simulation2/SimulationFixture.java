package com.transit.simulation2;

import com.transit.simulation2.itinerary.ItineraryEngine;
import com.transit.simulation2.network.NetworkGenerator;
import com.transit.simulation2.paratransit.ParatransitEngine;
import com.transit.simulation2.realtime.RealtimeEngine;
import com.transit.simulation2.robustness.RobustnessService;

/**
 * Wires the engines by hand for the tests that do not need a Spring context. The engines hold no
 * mutable state, so one instance of each can be shared; only {@link RobustnessService} caches, and
 * a fresh one is handed out per call so that caching cannot leak between tests.
 */
public final class SimulationFixture {

    public static final NetworkGenerator NETWORK_GENERATOR = new NetworkGenerator();
    public static final ItineraryEngine ITINERARY_ENGINE = new ItineraryEngine();
    public static final RealtimeEngine REALTIME_ENGINE = new RealtimeEngine();
    public static final ParatransitEngine PARATRANSIT_ENGINE = new ParatransitEngine();

    private SimulationFixture() {
    }

    public static ComparisonService comparisonService() {
        return new ComparisonService(NETWORK_GENERATOR, ITINERARY_ENGINE, REALTIME_ENGINE, PARATRANSIT_ENGINE);
    }

    public static RobustnessService robustnessService() {
        return new RobustnessService(comparisonService(), ITINERARY_ENGINE);
    }
}
