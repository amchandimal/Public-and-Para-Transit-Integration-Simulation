package com.transit.simulation2.itinerary;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The non-dominated {@link Label}s reached at one stop. Adding a label discards it when an
 * existing label already dominates it, and otherwise removes every existing label the new one
 * dominates, so the set always holds the Pareto front for that stop.
 */
public class ParetoSet {
    private final List<Label> labels = new ArrayList<>();

    public boolean add(Label newLabel) {
        for (Label existing : labels) {
            if (existing.dominates(newLabel)) {
                return false;
            }
        }
        Iterator<Label> it = labels.iterator();
        while (it.hasNext()) {
            if (newLabel.dominates(it.next())) {
                it.remove();
            }
        }
        labels.add(newLabel);
        return true;
    }

    public List<Label> asList() {
        return labels;
    }
}
