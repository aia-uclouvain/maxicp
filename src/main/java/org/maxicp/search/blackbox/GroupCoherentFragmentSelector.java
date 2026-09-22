package org.maxicp.search.blackbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fragment selector that keeps variables from the same array together.
 * It selects one variable group to relax based on historical success rates,
 * while freezing all variables outside of that group.
 */
public class GroupCoherentFragmentSelector implements FragmentSelector {

    private final Random random;
    private final List<List<Integer>> groups;
    private final double[] weights;
    private int lastSelectedGroupIdx = -1;
    private final double explorationRate = 0.2;

    public GroupCoherentFragmentSelector(Random random, List<List<Integer>> groups) {
        this.random = random;
        this.groups = List.copyOf(groups);
        this.weights = new double[groups.size()];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = 1.0;
        }
    }

    @Override
    public List<Integer> selectFrozenIndices(FragmentSelectionContext context) {
        List<Integer> decisionVarIndices = context.decisionVarIndices();
        if (groups.isEmpty()) {
            // Fallback: freeze at random
            List<Integer> frozen = new ArrayList<>();
            for (int idx : decisionVarIndices) {
                if (random.nextInt(100) < context.freezeRatePercent()) {
                    frozen.add(idx);
                }
            }
            return frozen;
        }

        // Select a group
        int selectedGroupIdx;
        if (random.nextDouble() < explorationRate) {
            // Explore: select uniformly at random
            selectedGroupIdx = random.nextInt(groups.size());
        } else {
            // Exploit: roulette-wheel selection based on weights
            selectedGroupIdx = selectRouletteWheel();
        }
        lastSelectedGroupIdx = selectedGroupIdx;

        List<Integer> selectedGroup = groups.get(selectedGroupIdx);
        List<Integer> frozen = new ArrayList<>();
        for (int idx : selectedGroup) {
            if (random.nextInt(100) < context.freezeRatePercent()) {
                frozen.add(idx);
            }
        }
        if (context.decisionVarIndices().size() > 1 && frozen.size() == context.decisionVarIndices().size()) {
            // Avoid degenerate neighborhoods: keep at least one decision variable relaxed.
            frozen.remove(random.nextInt(frozen.size()));
        }
        return frozen;
    }

    private int selectRouletteWheel() {
        double sum = 0.0;
        for (double w : weights) {
            sum += w;
        }
        if (sum <= 0.0) {
            return random.nextInt(groups.size());
        }
        double r = random.nextDouble() * sum;
        double running = 0.0;
        for (int i = 0; i < weights.length; i++) {
            running += weights[i];
            if (r <= running) {
                return i;
            }
        }
        return weights.length - 1;
    }

    @Override
    public void onRestartCompleted(FragmentSelectionFeedback feedback) {
        if (groups.isEmpty() || lastSelectedGroupIdx == -1) {
            return;
        }
        if (feedback.restartImproved()) {
            // Reward: exponential increase
            weights[lastSelectedGroupIdx] = weights[lastSelectedGroupIdx] * 0.9 + 10.0;
        } else {
            // Decay
            weights[lastSelectedGroupIdx] = weights[lastSelectedGroupIdx] * 0.95;
        }
        // Avoid weight dropping too close to 0
        weights[lastSelectedGroupIdx] = Math.max(weights[lastSelectedGroupIdx], 0.01);
    }

    public List<List<Integer>> getGroups() {
        return groups;
    }

    @Override
    public String name() {
        return "group-coherent";
    }
}
