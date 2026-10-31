package org.maxicp.search.blackbox;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.concrete.ConcreteModel;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Large Neighborhood Search (LNS) improvement phase for scheduling and hybrid scheduling+integer models.
 *
 * <p>Rotates across three scheduling neighborhood operators:</p>
 * <ol>
 *   <li><b>Prefix time-window freeze</b>: fixes tasks starting before a cutoff time {@code tCut}
 *       while relaxing the suffix so later tasks can be re-sequenced and left-shifted.</li>
 *   <li><b>Random subset freeze with left-shift allowance</b>: freezes a random subset of non-critical
 *       tasks (either fixing {@code start == incStart} or bounding {@code start <= incStart}).</li>
 *   <li><b>Optional-presence / contiguous-block relaxation</b>: for optional-interval models
 *       (such as Flexible Job Shop), freezes the presence/machine assignment of most tasks while
 *       re-optimizing the remaining alternatives and start times; for mandatory-interval models,
 *       relaxes a contiguous block of tasks.</li>
 * </ol>
 */
public class SchedulingLNSRunnableSearch extends RunnableSearch {

    private static final double[] WINDOW_RATIOS = {0.35, 0.50, 0.65, 0.25, 0.75};

    private final ModelDispatcher model;
    private final List<IntervalVar> intervals;
    private final List<IntExpression> vars;
    private final org.maxicp.modeling.symbolic.Objective objective;
    private final int failureLimitPerRestart;
    private final int initialFreezeRatePercent;
    private final Random random;
    private final boolean hasOptionalIntervals;

    private ConcreteModel cachedConcreteModel;
    private CPIntervalVar[] cpIntervals;
    private CPIntVar[] cpVars;

    public SchedulingLNSRunnableSearch(
            BlackBoxSearch blackBoxSearch,
            ModelDispatcher model,
            List<IntervalVar> intervals,
            List<IntExpression> vars,
            org.maxicp.modeling.symbolic.Objective objective,
            int failureLimitPerRestart,
            int initialFreezeRatePercent,
            long randomSeed) {
        super(blackBoxSearch);
        this.model = model;
        this.intervals = List.copyOf(intervals);
        this.vars = List.copyOf(vars);
        this.objective = objective;
        this.failureLimitPerRestart = Math.max(16, failureLimitPerRestart);
        this.initialFreezeRatePercent = Math.max(20, Math.min(90, initialFreezeRatePercent));
        this.random = new Random(randomSeed);
        boolean anyOpt = false;
        for (IntervalVar iv : this.intervals) {
            if (iv.isOptional()) {
                anyOpt = true;
                break;
            }
        }
        this.hasOptionalIntervals = anyOpt;
    }

    @Override
    void updateSolution(List<Integer> solution) {
        // Incumbent snapshots are queried from BlackBoxSearch
    }

    private void ensureCpCache() {
        if (!model.isConcrete()) {
            return;
        }
        ConcreteModel cm = model.getConcreteModel();
        if (cm != cachedConcreteModel) {
            cachedConcreteModel = cm;
            if (cm instanceof ConcreteCPModel cpm) {
                cpIntervals = new CPIntervalVar[intervals.size()];
                for (int i = 0; i < intervals.size(); i++) {
                    cpIntervals[i] = cpm.getCPVar(intervals.get(i));
                }
                cpVars = new CPIntVar[vars.size()];
                for (int i = 0; i < vars.size(); i++) {
                    cpVars[i] = cpm.getCPVar(vars.get(i));
                }
            } else {
                cpIntervals = null;
                cpVars = null;
            }
        }
    }

    @Override
    public SearchStatus run(long timeLimitInMillis) {
        if (!hasFeasibleSolution() || blackBoxSearch.bestIntervalSolution().isEmpty()) {
            return SearchStatus.UNKNOWN;
        }
        ensureCpCache();
        if (cpIntervals == null || !(cachedConcreteModel instanceof ConcreteCPModel cpm)) {
            return SearchStatus.UNKNOWN;
        }

        AtomicBoolean improved = new AtomicBoolean(false);
        long t0 = System.currentTimeMillis();
        int iteration = 0;
        int freezeRate = initialFreezeRatePercent;

        blackBoxSearch.logPhase("[phase scheduling-lns] start budget=%dms".formatted(timeLimitInMillis));

        while ((System.currentTimeMillis() - t0) < timeLimitInMillis && !blackBoxSearch.isLimitReached()) {
            Optional<List<BlackBoxSearch.IntervalSnapshot>> optSnap = blackBoxSearch.bestIntervalSolution();
            if (optSnap.isEmpty()) {
                break;
            }
            iteration++;
            final List<BlackBoxSearch.IntervalSnapshot> incIntervals = optSnap.get();
            final List<Integer> incVars = blackBoxSearch.bestSolution().orElse(List.of());
            final int iter = iteration;
            final int currentFreezeRate = freezeRate;
            final int failLimit = (iteration % 6 == 0) ? failureLimitPerRestart * 2 : failureLimitPerRestart;

            Supplier<Runnable[]> branching = blackBoxSearch.schedulingFdsBranching(random);
            DFSearch dfs = blackBoxSearch.createSubSearch(branching);
            dfs.onSolution(() -> {
                improved.set(true);
                blackBoxSearch.recordSolutionFromCurrentState();
            });

            SearchStatistics stats;
            if (objective == null) {
                stats = dfs.solveSubjectTo(
                        s -> blackBoxSearch.shouldAbortSubSearch(s, t0, timeLimitInMillis, failLimit),
                        () -> applySchedulingFragment(cpm, incIntervals, incVars, iter, currentFreezeRate, false));
            } else {
                stats = dfs.optimizeSubjectTo(objective,
                        s -> blackBoxSearch.shouldAbortSubSearch(s, t0, timeLimitInMillis, failLimit),
                        () -> applySchedulingFragment(cpm, incIntervals, incVars, iter, currentFreezeRate, true));
            }
            blackBoxSearch.accumulateSubSearchStats(stats);

            boolean iterImproved = stats != null && stats.numberOfSolutions() > 0;
            boolean iterExhausted = stats != null && stats.isCompleted() && !iterImproved
                    && stats.numberOfFailures() < failLimit;
            boolean hitFailLimit = stats != null && !iterImproved && stats.numberOfFailures() >= failLimit;

            if (iterImproved) {
                freezeRate = Math.min(85, freezeRate + 3);
            } else if (iterExhausted) {
                freezeRate = Math.max(25, freezeRate - 8);
            } else if (hitFailLimit) {
                freezeRate = Math.min(88, freezeRate + 5);
            }
        }

        return improved.get() ? SearchStatus.IMPROVED : SearchStatus.NOT_IMPROVED;
    }

    private void applySchedulingFragment(
            ConcreteCPModel cpm,
            List<BlackBoxSearch.IntervalSnapshot> incIntervals,
            List<Integer> incVars,
            int iteration,
            int freezeRatePercent,
            boolean withIncumbentCut) {
        if (withIncumbentCut) {
            blackBoxSearch.postIncumbentCut(false);
        }

        int n = incIntervals.size();
        int minStart = Integer.MAX_VALUE;
        int maxEnd = Integer.MIN_VALUE;
        for (int k = 0; k < n; k++) {
            BlackBoxSearch.IntervalSnapshot snap = incIntervals.get(k);
            if (snap.present()) {
                minStart = Math.min(minStart, snap.start());
                maxEnd = Math.max(maxEnd, snap.end());
            }
        }
        if (minStart > maxEnd) {
            minStart = 0;
            maxEnd = 0;
        }

        int op = (iteration - 1) % 3;
        if (op == 0 && maxEnd > minStart) {
            // Operator 0: Prefix time-window freeze
            double baseRatio = WINDOW_RATIOS[(iteration / 3) % WINDOW_RATIOS.length];
            double scaledRatio = Math.max(0.15, Math.min(0.85, baseRatio * (freezeRatePercent / 65.0)));
            int tCut = minStart + (int) Math.round((maxEnd - minStart) * scaledRatio);
            for (int k = 0; k < n; k++) {
                BlackBoxSearch.IntervalSnapshot snap = incIntervals.get(k);
                CPIntervalVar iv = cpIntervals[k];
                if (snap.present()) {
                    if (snap.start() < tCut && snap.end() < maxEnd) {
                        iv.setPresent();
                        iv.setStart(snap.start());
                        iv.setLength(snap.length());
                    }
                } else if (random.nextInt(100) < freezeRatePercent) {
                    iv.setAbsent();
                }
            }
        } else if (op == 1) {
            // Operator 1: Random subset freeze with left-shift allowance
            boolean exactStart = (iteration % 2 == 0);
            for (int k = 0; k < n; k++) {
                BlackBoxSearch.IntervalSnapshot snap = incIntervals.get(k);
                CPIntervalVar iv = cpIntervals[k];
                if (snap.present() && snap.end() >= maxEnd) {
                    // Always relax critical-end tasks so makespan can improve
                    continue;
                }
                if (random.nextInt(100) < freezeRatePercent) {
                    if (snap.present()) {
                        iv.setPresent();
                        iv.setLength(snap.length());
                        if (exactStart) {
                            iv.setStart(snap.start());
                        } else {
                            iv.setStartMax(snap.start());
                        }
                    } else {
                        iv.setAbsent();
                    }
                }
            }
        } else {
            // Operator 2: Optional-presence freeze or contiguous block relaxation
            if (hasOptionalIntervals) {
                for (int k = 0; k < n; k++) {
                    BlackBoxSearch.IntervalSnapshot snap = incIntervals.get(k);
                    CPIntervalVar iv = cpIntervals[k];
                    if (random.nextInt(100) < freezeRatePercent) {
                        if (snap.present()) {
                            iv.setPresent();
                            if (snap.end() < maxEnd && random.nextBoolean()) {
                                iv.setStartMax(snap.start());
                            }
                        } else {
                            iv.setAbsent();
                        }
                    }
                }
            } else if (n > 1) {
                int freezeCount = Math.max(0, Math.min(n - 1, (int) Math.round(n * (freezeRatePercent / 100.0))));
                int relaxCount = n - freezeCount;
                int startIdx = random.nextInt(n);
                boolean[] relaxed = new boolean[n];
                for (int r = 0; r < relaxCount; r++) {
                    relaxed[(startIdx + r) % n] = true;
                }
                for (int k = 0; k < n; k++) {
                    BlackBoxSearch.IntervalSnapshot snap = incIntervals.get(k);
                    if (!relaxed[k] && snap.present() && snap.end() < maxEnd) {
                        CPIntervalVar iv = cpIntervals[k];
                        iv.setPresent();
                        iv.setStart(snap.start());
                        iv.setLength(snap.length());
                    }
                }
            }
        }

        // Also freeze a subset of primary integer variables if present
        if (cpVars != null && cpVars.length > 0 && incVars.size() == cpVars.length) {
            ModelStructureAnalyzer structure = blackBoxSearch.structureAnalyzer();
            for (int idx : structure.primaryIndices()) {
                if (random.nextInt(100) < freezeRatePercent) {
                    cpVars[idx].fix(incVars.get(idx));
                }
            }
        }

        cpm.solver.fixPoint();
    }
}

