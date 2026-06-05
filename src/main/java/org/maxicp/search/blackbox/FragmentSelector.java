package org.maxicp.search.blackbox;

import java.util.List;
import java.util.Set;

/**
 * Selects which decision-variable indices are frozen for one LNS restart.
 */
public interface FragmentSelector {

    List<Integer> selectFrozenIndices(FragmentSelectionContext context);

    default void onRestartCompleted(FragmentSelectionFeedback feedback) {
        // Optional hook for adaptive selectors.
    }

    String name();

    record FragmentSelectionContext(List<Integer> decisionVarIndices,
                                    int freezeRatePercent,
                                    int iteration) {
    }

    record FragmentSelectionFeedback(List<Integer> decisionVarIndices,
                                     List<Integer> frozenDecisionVarIndices,
                                     Set<Integer> changedDecisionVarIndices,
                                     boolean restartImproved,
                                     boolean restartExhausted,
                                     boolean reachedFailureLimit) {
    }
}

