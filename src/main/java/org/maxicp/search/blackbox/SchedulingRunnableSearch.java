package org.maxicp.search.blackbox;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Constructive and exhaustive scheduling search phase for models containing {@link IntervalVar}s.
 *
 * <p>Combines an initial heuristic pass ({@link org.maxicp.search.SetTimesModeling} when all intervals
 * are mandatory and fixed-length), Luby-restarted Failure-Directed Search ({@link org.maxicp.search.FDSModeling}),
 * and an unbounded exhaustive FDS stage to prove optimality or infeasibility.</p>
 */
public class SchedulingRunnableSearch extends RunnableSearch {

    private final ModelDispatcher model;
    private final List<IntervalVar> intervals;
    private final List<IntExpression> vars;
    private final org.maxicp.modeling.symbolic.Objective objective;
    private final Random random;

    public SchedulingRunnableSearch(
            BlackBoxSearch blackBoxSearch,
            ModelDispatcher model,
            List<IntervalVar> intervals,
            List<IntExpression> vars,
            org.maxicp.modeling.symbolic.Objective objective,
            long randomSeed) {
        super(blackBoxSearch);
        this.model = model;
        this.intervals = List.copyOf(intervals);
        this.vars = List.copyOf(vars);
        this.objective = objective;
        this.random = new Random(randomSeed);
    }

    @Override
    void updateSolution(List<Integer> solution) {
        // Incumbent state is managed on BlackBoxSearch
    }

    @Override
    public SearchStatus run(long timeLimitInMillis) {
        long t0 = System.currentTimeMillis();
        blackBoxSearch.logPhase("[phase scheduling-fds] start budget=%dms".formatted(timeLimitInMillis));

        AtomicBoolean improvedInPhase = new AtomicBoolean(false);

        // Stage 1: Quick SetTimes constructive pass if no incumbent yet and all intervals are mandatory & fixed-length
        if (!hasFeasibleSolution() && allMandatoryFixedLength() && !blackBoxSearch.isLimitReached()) {
            final int setTimesCutoff = 64;
            Supplier<Runnable[]> setTimesBranching = blackBoxSearch.schedulingSetTimesBranching(random);
            DFSearch stDfs = blackBoxSearch.createSubSearch(setTimesBranching);
            stDfs.onSolution(() -> {
                improvedInPhase.set(true);
                blackBoxSearch.recordSolutionFromCurrentState();
            });

            SearchStatistics stStats;
            if (objective == null) {
                stStats = stDfs.solve(s ->
                        blackBoxSearch.shouldAbortSubSearch(s, t0, timeLimitInMillis, setTimesCutoff)
                                || hasFeasibleSolution());
            } else {
                stStats = stDfs.optimizeSubjectTo(objective,
                        s -> blackBoxSearch.shouldAbortSubSearch(s, t0, timeLimitInMillis, setTimesCutoff),
                        blackBoxSearch::postIncumbentCut);
            }
            blackBoxSearch.accumulateSubSearchStats(stStats);

            if (objective == null && hasFeasibleSolution()) {
                return SearchStatus.SAT;
            }
            if (stStats.isCompleted() && stStats.numberOfFailures() < setTimesCutoff && !blackBoxSearch.isLimitReached()) {
                return completedStatus(improvedInPhase.get());
            }
        }

        // Stage 2: Luby restarts with FDSModeling
        long stage2Budget = timeLimitInMillis >= 80 ? Math.max(30L, (timeLimitInMillis * 40L) / 100L) : timeLimitInMillis;
        int restart = 1;
        while ((System.currentTimeMillis() - t0) < stage2Budget && !blackBoxSearch.isLimitReached()) {
            final int failCutoff = 64 * luby(restart);
            Supplier<Runnable[]> fdsBranching = blackBoxSearch.schedulingFdsBranching(random);
            DFSearch fdsDfs = blackBoxSearch.createSubSearch(fdsBranching);
            fdsDfs.onSolution(() -> {
                improvedInPhase.set(true);
                blackBoxSearch.recordSolutionFromCurrentState();
            });

            SearchStatistics rStats;
            if (objective == null) {
                rStats = fdsDfs.solve(s ->
                        blackBoxSearch.shouldAbortSubSearch(s, t0, stage2Budget, failCutoff)
                                || hasFeasibleSolution());
            } else {
                rStats = fdsDfs.optimizeSubjectTo(objective,
                        s -> blackBoxSearch.shouldAbortSubSearch(s, t0, stage2Budget, failCutoff),
                        blackBoxSearch::postIncumbentCut);
            }
            blackBoxSearch.accumulateSubSearchStats(rStats);

            if (objective == null && hasFeasibleSolution()) {
                return SearchStatus.SAT;
            }
            if (rStats.isCompleted() && rStats.numberOfFailures() < failCutoff && !blackBoxSearch.isLimitReached()) {
                return completedStatus(improvedInPhase.get());
            }
            restart++;
        }

        // Stage 3: Unbounded FDSModeling for the remaining phase budget
        if ((System.currentTimeMillis() - t0) < timeLimitInMillis && !blackBoxSearch.isLimitReached()) {
            Supplier<Runnable[]> fdsBranching = blackBoxSearch.schedulingFdsBranching(random);
            DFSearch fdsDfs = blackBoxSearch.createSubSearch(fdsBranching);
            fdsDfs.onSolution(() -> {
                improvedInPhase.set(true);
                blackBoxSearch.recordSolutionFromCurrentState();
            });

            SearchStatistics fdsStats;
            if (objective == null) {
                fdsStats = fdsDfs.solve(s ->
                        blackBoxSearch.shouldAbortSubSearch(s, t0, timeLimitInMillis, Integer.MAX_VALUE)
                                || hasFeasibleSolution());
            } else {
                fdsStats = fdsDfs.optimizeSubjectTo(objective,
                        s -> blackBoxSearch.shouldAbortSubSearch(s, t0, timeLimitInMillis, Integer.MAX_VALUE),
                        blackBoxSearch::postIncumbentCut);
            }
            blackBoxSearch.accumulateSubSearchStats(fdsStats);

            if (objective == null && hasFeasibleSolution()) {
                return SearchStatus.SAT;
            }
            if (fdsStats.isCompleted() && !blackBoxSearch.isLimitReached()) {
                return completedStatus(improvedInPhase.get());
            }
        }

        return improvedInPhase.get() ? SearchStatus.IMPROVED : SearchStatus.NOT_IMPROVED;
    }

    private boolean allMandatoryFixedLength() {
        for (IntervalVar iv : intervals) {
            if (!iv.isPresent() || iv.lengthMin() != iv.lengthMax()) {
                return false;
            }
        }
        return !intervals.isEmpty();
    }

    private SearchStatus completedStatus(boolean foundInPhase) {
        if (objective == null) {
            return (foundInPhase || hasFeasibleSolution()) ? SearchStatus.SAT : SearchStatus.UNSAT;
        }
        return (foundInPhase || hasFeasibleSolution()) ? SearchStatus.PROVEN_OPTIMAL : SearchStatus.UNSAT;
    }

    private static int luby(int i) {
        int k = 1;
        while ((1 << k) - 1 < i) {
            k++;
        }
        if (i == (1 << k) - 1) {
            return 1 << (k - 1);
        }
        return luby(i - (1 << (k - 1)) + 1);
    }
}

