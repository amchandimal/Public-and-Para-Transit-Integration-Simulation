package com.transit.simulation2;

import com.transit.simulation2.config.SimulationParameters;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Diffs two {@link SimulationParameters} over their record components by reflection, so a test can
 * assert which fields a copy method changed without enumerating the other twenty-odd by hand. A
 * component added to the record is picked up automatically.
 */
public final class ParameterDiff {

    /** The six seeds, which the seed-set and seed copy methods change together. */
    public static final Set<String> SEEDS = Set.of(
            "networkSeed", "querySeed", "dataSeed", "dispatchSeed",
            "tripEvaluationSeed", "paratransitEvaluationSeed");

    private ParameterDiff() {
    }

    /** The names of the components whose values differ between the two instances. */
    public static Set<String> changedComponents(SimulationParameters a, SimulationParameters b) {
        Set<String> changed = new TreeSet<>();
        for (RecordComponent component : SimulationParameters.class.getRecordComponents()) {
            try {
                Object left = component.getAccessor().invoke(a);
                Object right = component.getAccessor().invoke(b);
                if (!Objects.equals(left, right)) {
                    changed.add(component.getName());
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("could not read component " + component.getName(), e);
            }
        }
        return changed;
    }

    /** Every component name, in declaration order. */
    public static Set<String> componentNames() {
        Set<String> names = new LinkedHashSet<>();
        for (RecordComponent component : SimulationParameters.class.getRecordComponents()) {
            names.add(component.getName());
        }
        return names;
    }
}
