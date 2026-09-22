package org.maxicp.search.blackbox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * PGLNS-inspired selector: keep low-impact vars fixed and relax high-impact ones.
 *
 * <p>Impact scores combine a structural prior and online updates from improving
 * restarts. Selection also keeps a small exploration part to avoid stagnation.</p>
 */
public class ImpactBasedFragmentSelector implements FragmentSelector {

    private static final double SCORE_DECAY = 0.995;
    private static final double IMPROVEMENT_BONUS = 1.0;
    private static final double STAGNATION_BONUS = 0.10;
    private static final double EXPLORATION_SHARE = 0.20;

    private final Random random;
    private final Map<Integer, Double> impacts = new HashMap<>();

    public ImpactBasedFragmentSelector(Random random, Map<Integer, Double> structuralImpacts) {
        this.random = random;
        this.impacts.putAll(structuralImpacts);
    }

    @Override
    public List<Integer> selectFrozenIndices(FragmentSelectionContext context) {
        int n = context.decisionVarIndices().size();
        if (n == 0) {
            return List.of();
        }

        int freezeCount = Math.max(0, Math.min(n, (int) Math.round(n * (context.freezeRatePercent() / 100.0))));
        if (n > 1 && freezeCount >= n) {
            freezeCount = n - 1;
        }
        if (freezeCount == 0) {
            return List.of();
        }
        if (freezeCount == n) {
            return List.copyOf(context.decisionVarIndices());
        }

        List<Integer> candidates = new ArrayList<>(context.decisionVarIndices());
        Map<Integer, Double> scoreByIndex = new HashMap<>(candidates.size());
        for (int idx : candidates) {
            scoreByIndex.put(idx, noisyImpact(idx));
        }
        // Keep comparator stable within the restart to satisfy TimSort contract.
        candidates.sort(Comparator.comparingDouble((Integer idx) -> scoreByIndex.get(idx))
                .thenComparingInt(Integer::intValue));

        int deterministicCount = (int) Math.round(freezeCount * (1.0 - EXPLORATION_SHARE));
        deterministicCount = Math.max(0, Math.min(freezeCount, deterministicCount));

        List<Integer> frozen = new ArrayList<>(freezeCount);
        for (int i = 0; i < deterministicCount; i++) {
            frozen.add(candidates.get(i));
        }

        Set<Integer> chosen = new HashSet<>(frozen);
        List<Integer> remaining = new ArrayList<>(candidates.subList(deterministicCount, candidates.size()));
        while (frozen.size() < freezeCount && !remaining.isEmpty()) {
            int pos = random.nextInt(remaining.size());
            int idx = remaining.remove(pos);
            if (chosen.add(idx)) {
                frozen.add(idx);
            }
        }

        return List.copyOf(frozen);
    }

    @Override
    public void onRestartCompleted(FragmentSelectionFeedback feedback) {
        decayScores(feedback.decisionVarIndices());

        if (feedback.restartImproved()) {
            for (int idx : feedback.changedDecisionVarIndices()) {
                impacts.merge(idx, IMPROVEMENT_BONUS, Double::sum);
            }
            return;
        }

        if (feedback.reachedFailureLimit()) {
            Collection<Integer> relaxed = relaxedIndices(feedback);
            for (int idx : relaxed) {
                impacts.merge(idx, STAGNATION_BONUS, Double::sum);
            }
        }
    }

    @Override
    public String name() {
        return "impact-guided";
    }

    private void decayScores(List<Integer> decisionVarIndices) {
        for (int idx : decisionVarIndices) {
            impacts.merge(idx, 0.0, (oldV, unused) -> oldV * SCORE_DECAY);
        }
    }

    private Collection<Integer> relaxedIndices(FragmentSelectionFeedback feedback) {
        Set<Integer> frozen = new HashSet<>(feedback.frozenDecisionVarIndices());
        List<Integer> relaxed = new ArrayList<>();
        for (int idx : feedback.decisionVarIndices()) {
            if (!frozen.contains(idx)) {
                relaxed.add(idx);
            }
        }
        return relaxed;
    }

    private double noisyImpact(int idx) {
        double base = impacts.getOrDefault(idx, 0.0);
        return base + random.nextDouble() * 1e-6;
    }
}

