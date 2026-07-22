package org.maxicp.search.blackbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Legacy LNS fragment selection: each decision variable is frozen independently.
 */
public class RandomFragmentSelector implements FragmentSelector {

    private final Random random;

    public RandomFragmentSelector(Random random) {
        this.random = random;
    }

    @Override
    public List<Integer> selectFrozenIndices(FragmentSelectionContext context) {
        List<Integer> frozen = new ArrayList<>();
        for (int idx : context.decisionVarIndices()) {
            if (random.nextInt(100) < context.freezeRatePercent()) {
                frozen.add(idx);
            }
        }
        if (context.decisionVarIndices().size() > 1 && frozen.size() == context.decisionVarIndices().size()) {
            // Avoid degenerate neighborhoods: keep at least one decision variable relaxed.
            frozen.remove(random.nextInt(frozen.size()));
        }
        return List.copyOf(frozen);
    }

    @Override
    public String name() {
        return "random-uniform";
    }
}

