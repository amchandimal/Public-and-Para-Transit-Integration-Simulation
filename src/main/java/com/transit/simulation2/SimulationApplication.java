package com.transit.simulation2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the capability-flag transit comparison service: Existing vs Proposed across
 * FR1-FR8, by isolated single-factor ablation and fully stacked comparison, exposed as a REST
 * API with a live dashboard.
 */
@SpringBootApplication
public class SimulationApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimulationApplication.class, args);
    }
}
