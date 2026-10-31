package org.maxicp.search.blackbox;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.concrete.ConcreteModel;
import org.maxicp.util.exception.InconsistencyException;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

/**
 * Conflict-driven adaptive branching strategy ({@code dom/wdeg} + {@code Last-Conflict} +
 * objective-polarity / phase-saving / solution-guided value selection + primary/secondary variable tiering).
 *
 * <p>Learned conflict weights, phase-saved values, and incumbent solution values persist across restarts.</p>
 */
public final class ConflictDrivenBranching implements Supplier<Runnable[]> {

    public enum ValueStrategy {
        OBJECTIVE_AND_PHASE,
        SOLUTION_AND_OBJECTIVE,
        PHASE_SAVING,
        MIN_FIRST,
        MAX_FIRST,
        RANDOM_BOUND
    }

    private static final int DOMAIN_SPLIT_THRESHOLD = 64;
    private static final int DECAY_PERIOD = 500;
    private static final double WEIGHT_DECAY = 0.95;

    private final ModelDispatcher model;
    private final IntExpression[] vars;
    private final ModelStructureAnalyzer structure;
    private final Random random;

    private final double[] varWeights;
    private final int[] savedPhase;
    private final boolean[] hasSavedPhase;
    private final int[] incumbentValues;
    private boolean hasIncumbent;

    private int lastConflictVar = -1;
    private long conflictCount = 0;

    private ValueStrategy valueStrategy = ValueStrategy.OBJECTIVE_AND_PHASE;
    private double noise = 0.0;
    private boolean useLastConflict = true;
    private double solutionGuideProbability = 0.65;

    private ConcreteModel cachedConcreteModel;
    private CPSolver cpSolver;
    private CPIntVar[] cpVars;

    public ConflictDrivenBranching(
            ModelDispatcher model,
            List<IntExpression> vars,
            ModelStructureAnalyzer structure,
            Random random) {
        this.model = model;
        this.vars = vars.toArray(IntExpression[]::new);
        this.structure = structure;
        this.random = random;
        int n = this.vars.length;
        this.varWeights = new double[n];
        for (int i = 0; i < n; i++) {
            this.varWeights[i] = Math.max(1.0, structure.structuralWeight(i));
        }
        this.savedPhase = new int[n];
        this.hasSavedPhase = new boolean[n];
        this.incumbentValues = new int[n];
    }

    public void configureRestart(ValueStrategy strategy, double noise, boolean useLastConflict) {
        this.valueStrategy = strategy == null ? ValueStrategy.OBJECTIVE_AND_PHASE : strategy;
        this.noise = Math.max(0.0, noise);
        this.useLastConflict = useLastConflict;
        this.lastConflictVar = -1;
    }

    public void updateIncumbent(List<Integer> solution) {
        if (solution == null || solution.size() != vars.length) {
            return;
        }
        for (int i = 0; i < vars.length; i++) {
            incumbentValues[i] = solution.get(i);
        }
        hasIncumbent = true;
    }

    public double getVarWeight(int varIndex) {
        return varWeights[varIndex];
    }

    private void ensureConcreteCache() {
        if (!model.isConcrete()) {
            return;
        }
        ConcreteModel cm = model.getConcreteModel();
        if (cm != cachedConcreteModel) {
            cachedConcreteModel = cm;
            if (cm instanceof ConcreteCPModel cpm) {
                cpSolver = cpm.solver;
                cpVars = new CPIntVar[vars.length];
                for (int i = 0; i < vars.length; i++) {
                    cpVars[i] = cpm.getCPVar(vars[i]);
                }
            } else {
                cpSolver = null;
                cpVars = null;
            }
        }
    }

    @Override
    public Runnable[] get() {
        if (vars.length == 0) {
            return EMPTY;
        }
        ensureConcreteCache();

        // Tier 1: Select among unfixed primary decision variables
        int chosen = selectPrimaryVariable();
        if (chosen >= 0) {
            return buildBranches(chosen);
        }

        // All primary variables are fixed.
        // Fast-path: if any dominated degree-<=1 Table/unconstrained slack variables are unfixed, fix them to their optimal bound
        int[] domSlacks = structure.dominantSlackIndices();
        if (domSlacks.length > 0 && hasUnfixedIn(domSlacks)) {
            return branch(this::assignDominantSlacks);
        }

        // Tier 2: Select among remaining unfixed secondary variables
        chosen = selectSecondaryVariable();
        if (chosen >= 0) {
            return buildBranches(chosen);
        }

        return EMPTY;
    }

    private int selectPrimaryVariable() {
        if (useLastConflict && lastConflictVar >= 0 && !structure.isSecondary(lastConflictVar)) {
            if (!isFixed(lastConflictVar)) {
                return lastConflictVar;
            }
            lastConflictVar = -1;
        }

        int[] primary = structure.primaryIndices();
        int bestVar = -1;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int i = 0; i < primary.length; i++) {
            int idx = primary[i];
            int size = size(idx);
            if (size <= 1) {
                continue;
            }
            double score = (double) size / varWeights[idx];
            if (noise > 0.0) {
                score *= (1.0 + noise * random.nextDouble());
            }
            if (score < bestScore) {
                bestScore = score;
                bestVar = idx;
            }
        }
        return bestVar;
    }

    private int selectSecondaryVariable() {
        int[] secondary = structure.secondaryIndices();
        int bestVar = -1;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int i = 0; i < secondary.length; i++) {
            int idx = secondary[i];
            int size = size(idx);
            if (size <= 1) {
                continue;
            }
            double score = (double) size / varWeights[idx];
            if (score < bestScore) {
                bestScore = score;
                bestVar = idx;
            }
        }
        return bestVar;
    }

    private boolean hasUnfixedIn(int[] indices) {
        if (cpVars != null) {
            for (int idx : indices) {
                if (!cpVars[idx].isFixed()) {
                    return true;
                }
            }
            return false;
        }
        for (int idx : indices) {
            if (!vars[idx].isFixed()) {
                return true;
            }
        }
        return false;
    }

    private void assignDominantSlacks() {
        int[] domSlacks = structure.dominantSlackIndices();
        if (cpVars != null) {
            for (int idx : domSlacks) {
                CPIntVar v = cpVars[idx];
                if (!v.isFixed()) {
                    int pol = structure.objectivePolarity(idx);
                    v.fix(pol > 0 ? v.max() : v.min());
                }
            }
            cpSolver.fixPoint();
        } else {
            for (int idx : domSlacks) {
                IntExpression v = vars[idx];
                if (!v.isFixed()) {
                    int pol = structure.objectivePolarity(idx);
                    model.add(Factory.eq(v, pol > 0 ? v.max() : v.min()), false);
                }
            }
            model.add(new org.maxicp.modeling.constraints.NoOpConstraint(), true);
        }
    }

    private Runnable[] buildBranches(int varIdx) {
        int min = min(varIdx);
        int max = max(varIdx);
        int sz = size(varIdx);

        if (sz > DOMAIN_SPLIT_THRESHOLD) {
            int mid = min + ((max - min) >>> 1);
            boolean lowerFirst = chooseLowerHalfFirst(varIdx, min, max, mid);
            if (lowerFirst) {
                return branch(
                        () -> applySplitBranch(varIdx, mid, true),
                        () -> applySplitBranch(varIdx, mid, false)
                );
            } else {
                return branch(
                        () -> applySplitBranch(varIdx, mid, false),
                        () -> applySplitBranch(varIdx, mid, true)
                );
            }
        }

        int val = selectValue(varIdx, min, max);
        return branch(
                () -> applyValueBranch(varIdx, val, true),
                () -> applyValueBranch(varIdx, val, false)
        );
    }

    private boolean chooseLowerHalfFirst(int varIdx, int min, int max, int mid) {
        int pol = structure.objectivePolarity(varIdx);
        if (pol < 0) return true;
        if (pol > 0) return false;
        if (hasIncumbent && valueStrategy == ValueStrategy.SOLUTION_AND_OBJECTIVE) {
            int inc = incumbentValues[varIdx];
            if (inc >= min && inc <= max) {
                return inc <= mid;
            }
        }
        if (hasSavedPhase[varIdx]) {
            int saved = savedPhase[varIdx];
            if (saved >= min && saved <= max) {
                return saved <= mid;
            }
        }
        return true;
    }

    private int selectValue(int varIdx, int min, int max) {
        int pol = structure.objectivePolarity(varIdx);
        long[] valCosts = structure.objectiveValueCosts(varIdx);
        switch (valueStrategy) {
            case MIN_FIRST -> {
                return min;
            }
            case MAX_FIRST -> {
                return max;
            }
            case RANDOM_BOUND -> {
                return random.nextBoolean() ? min : max;
            }
            case PHASE_SAVING -> {
                if (hasSavedPhase[varIdx] && contains(varIdx, savedPhase[varIdx])) {
                    return savedPhase[varIdx];
                }
                if (valCosts != null) {
                    return selectMinCostValue(varIdx, min, max, valCosts);
                }
                if (pol < 0) return min;
                if (pol > 0) return max;
                return min;
            }
            case SOLUTION_AND_OBJECTIVE -> {
                if (hasIncumbent && contains(varIdx, incumbentValues[varIdx])) {
                    if ((pol == 0 && valCosts == null) || random.nextDouble() < solutionGuideProbability) {
                        return incumbentValues[varIdx];
                    }
                }
                if (valCosts != null) {
                    return selectMinCostValue(varIdx, min, max, valCosts);
                }
                if (pol < 0) return min;
                if (pol > 0) return max;
                if (hasSavedPhase[varIdx] && contains(varIdx, savedPhase[varIdx])) {
                    return savedPhase[varIdx];
                }
                return min;
            }
            case OBJECTIVE_AND_PHASE -> {
                if (valCosts != null) {
                    return selectMinCostValue(varIdx, min, max, valCosts);
                }
                if (pol < 0) return min;
                if (pol > 0) return max;
                if (hasSavedPhase[varIdx] && contains(varIdx, savedPhase[varIdx])) {
                    return savedPhase[varIdx];
                }
                return min;
            }
        }
        return min;
    }

    private int selectMinCostValue(int varIdx, int min, int max, long[] valCosts) {
        int bestVal = min;
        long bestCost = (min >= 0 && min < valCosts.length) ? valCosts[min] : Long.MAX_VALUE;
        int upper = Math.min(max, valCosts.length - 1);
        for (int v = Math.max(0, min); v <= upper; v++) {
            long c = valCosts[v];
            if (c < bestCost && contains(varIdx, v)) {
                bestCost = c;
                bestVal = v;
            }
        }
        return bestVal;
    }

    private void applyValueBranch(int varIdx, int val, boolean positive) {
        try {
            if (cpVars != null) {
                CPIntVar v = cpVars[varIdx];
                if (positive) {
                    v.fix(val);
                } else {
                    v.remove(val);
                }
                cpSolver.fixPoint();
            } else {
                IntExpression v = vars[varIdx];
                if (positive) {
                    model.add(Factory.eq(v, val));
                } else {
                    model.add(Factory.neq(v, val));
                }
            }
            if (positive) {
                savedPhase[varIdx] = val;
                hasSavedPhase[varIdx] = true;
            }
        } catch (InconsistencyException e) {
            recordConflict(varIdx);
            throw e;
        }
    }

    private void applySplitBranch(int varIdx, int mid, boolean lowerHalf) {
        try {
            if (cpVars != null) {
                CPIntVar v = cpVars[varIdx];
                if (lowerHalf) {
                    v.removeAbove(mid);
                } else {
                    v.removeBelow(mid + 1);
                }
                cpSolver.fixPoint();
            } else {
                IntExpression v = vars[varIdx];
                if (lowerHalf) {
                    model.add(Factory.le(v, mid));
                } else {
                    model.add(Factory.lt(mid, v));
                }
            }
            if (isFixed(varIdx)) {
                savedPhase[varIdx] = min(varIdx);
                hasSavedPhase[varIdx] = true;
            }
        } catch (InconsistencyException e) {
            recordConflict(varIdx);
            throw e;
        }
    }

    private void recordConflict(int varIdx) {
        lastConflictVar = varIdx;
        varWeights[varIdx] += 1.0;

        // Also bump unfixed frontier neighbors (and any neighbor whose domain wiped out)
        int[] adj = structure.neighbors(varIdx);
        int limit = Math.min(8, adj.length);
        for (int i = 0; i < limit; i++) {
            int nb = adj[i];
            int sz = size(nb);
            if (sz == 0) {
                varWeights[nb] += 1.0;
            } else if (sz > 1) {
                varWeights[nb] += 0.25;
            }
        }

        conflictCount++;
        if (conflictCount % DECAY_PERIOD == 0) {
            for (int i = 0; i < varWeights.length; i++) {
                varWeights[i] = Math.max(1.0, varWeights[i] * WEIGHT_DECAY);
            }
        }
    }

    private boolean isFixed(int idx) {
        return cpVars != null ? cpVars[idx].isFixed() : vars[idx].isFixed();
    }

    private int size(int idx) {
        return cpVars != null ? cpVars[idx].size() : vars[idx].size();
    }

    private int min(int idx) {
        return cpVars != null ? cpVars[idx].min() : vars[idx].min();
    }

    private int max(int idx) {
        return cpVars != null ? cpVars[idx].max() : vars[idx].max();
    }

    private boolean contains(int idx, int val) {
        return cpVars != null ? cpVars[idx].contains(val) : vars[idx].contains(val);
    }
}
