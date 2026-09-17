package com.transit.simulation2;

/**
 * One row of the isolated single-factor ablation: the metric an FR aspect is scored on, its
 * value under Proposed, its value with that single capability flag set back to its Existing
 * value, and the difference between the two.
 */
public record AblationRow(
        String frAspect,
        String field,
        String metric,
        double proposedValue,
        double withCapabilityRemoved,
        double delta,
        boolean sharedNotDifferentiator
) {
}
