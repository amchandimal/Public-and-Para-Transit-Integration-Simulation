package com.transit.simulation2.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks that every endpoint under /api/comparison answers and that the wiring is complete.
 *
 * The two robustness endpoints are called with a small number of seed sets rather than their
 * defaults: at the default 30 seed sets and 430 sweep evaluations a single call would take longer
 * than the whole test suite is allowed to. What is under test here is that the endpoint responds,
 * not the size of the analysis, which {@link com.transit.simulation2.robustness.RobustnessServiceTest}
 * covers.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiEndpointsTest {

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest(name = "GET {0} returns 200 and JSON")
    @ValueSource(strings = {
            "/api/comparison/network",
            "/api/comparison/capabilities",
            "/api/comparison/ablation",
            "/api/comparison/stacked",
            "/api/comparison/scenario/existing",
            "/api/comparison/scenario/proposed",
            "/api/comparison/scenario/custom",
            "/api/comparison/full",
            "/api/comparison/parameters",
            "/api/comparison/network-stats",
            "/api/comparison/sensitivity/parameters",
            "/api/comparison/scenario/parameterised",
            "/api/comparison/repeated-seeds?n=2",
            "/api/comparison/sensitivity?seeds=1&parameter=consensusAgreementThreshold",
    })
    void jsonEndpointsAnswer(String url) throws Exception {
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @ParameterizedTest(name = "GET {0} returns 200 and CSV")
    @ValueSource(strings = {
            "/api/comparison/csv/stacked",
            "/api/comparison/csv/ablation",
            "/api/comparison/csv/repeated-seeds?n=2",
            "/api/comparison/csv/sensitivity?seeds=1&parameter=consensusAgreementThreshold",
    })
    void csvEndpointsAnswer(String url) throws Exception {
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    @DisplayName("the stacked endpoint serves the baseline itinerary-found rates")
    void stackedServesTheBaseline() throws Exception {
        mvc.perform(get("/api/comparison/stacked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existing.tripPlanning.itineraryFoundRatePct").value(46.0))
                .andExpect(jsonPath("$.proposed.tripPlanning.itineraryFoundRatePct").value(96.66666666666667));
    }

    @Test
    @DisplayName("the dashboard is served from the application root")
    void dashboardIsServed() throws Exception {
        // The welcome-page mapping forwards "/" to the static index.html rather than writing it
        // directly, so the forward target is what identifies the dashboard here.
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mvc.perform(get("/index.html"))
                .andExpect(status().isOk());
    }
}
