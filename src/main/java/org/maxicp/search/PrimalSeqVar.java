/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.search;

import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.concrete.ConcreteModel;
import org.maxicp.util.TriFunction;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.search.Searches.EMPTY;

/**
 * Constructive Primal Heuristic Search for Sequence Variables ({@link SeqVar} and {@link CPSeqVar}).
 *
 * <p>Whereas {@link FDSSeqVar} is a conflict-driven search designed to drive the solver into tight
 * contradictions to prove optimality or infeasibility, {@code PrimalSeqVar} is a primal constructive
 * branching strategy designed to rapidly discover high-quality feasible solutions (and re-optimize
 * Large Neighborhood Search fragments).</p>
 *
 * <p>At each search node, {@code PrimalSeqVar} operates as follows:
 * <ol>
 *   <li><b>Immediate Domain Reduction &amp; Wipeout Detection:</b> Any {@code POSSIBLE} node {@code u}
 *       with {@code nInsert(u) == 0} in a sequence {@code s} is immediately excluded from {@code s};
 *       any {@code REQUIRED} uninserted node with {@code nInsert(u) == 0} triggers an immediate failure
 *       and increments the conflict weight of {@code u}.</li>
 *   <li><b>Two-Tier Priority (Required First):</b> Uninserted nodes that are already {@code REQUIRED}
 *       in at least one sequence ({@code INSERTABLE_REQUIRED}) are prioritized ahead of {@code POSSIBLE}
 *       nodes.</li>
 *   <li><b>Conflict-Weighted First-Fail with 2-Regret Node Selection:</b> Candidate nodes {@code u}
 *       are scored by their total feasible insertion count {@code I(u) = sum_s s.nInsert(u)} (First-Fail),
 *       tie-broken by highest 2-regret {@code Delta d_2(u) - Delta d_1(u)} (the detour increase between
 *       the best and second-best insertion position across all sequences) and smallest best detour
 *       {@code Delta d_1(u)} when transition costs are available (or conflict weight when no transition
 *       costs are present).</li>
 *   <li><b>Binary Constructive Insertion with Active Refutation:</b> For the selected node {@code u*}
 *       and its best feasible insertion {@code (s*, pred*)} with {@code succ* = s*.memberAfter(pred*)},
 *       branches binary on:
 *       <ul>
 *         <li>Left branch: {@code insert(s*, pred*, u*)}</li>
 *         <li>Right branch: {@code notBetween(s*, pred*, u*, succ*)}</li>
 *       </ul>
 *       Posting {@code notBetween} on the refutation branch actively propagates time-window and
 *       domain reductions in {@link CPSeqVar}, automatically excluding {@code u*} when its last
 *       insertion in {@code s*} is eliminated or automatically inserting {@code u*} when it is
 *       {@code REQUIRED} and only one insertion remains.</li>
 * </ol>
 */
public class PrimalSeqVar implements Supplier<Runnable[]> {

    private static final double DEFAULT_CONFLICT_DECAY = 0.99;

    private final SeqVar[] seqVars;
    private final ModelProxy model;
    private final int nTotalNodes;
    private final boolean autoDiscoverDistMatrix;
    private int[][] distMatrix;
    private final TriFunction<Integer, Integer, Integer, Integer> detourCost;
    private int maxDetour = 1;

    private final int[] insertBuf;
    private final double[] nodeWeight;

    private Random rng;
    private double noiseProb;

    private ConcreteModel cachedConcreteModel;
    private CPSolver cpSolver;
    private CPSeqVar[] cpSeqVars;

    /**
     * Creates a constructive primal heuristic search for the given sequence variables.
     * Automatically discovers any transition distance matrix associated with the sequence variables
     * via {@code Distance} or {@code TransitionTimes} constraints.
     *
     * @param seqVars the sequence variables to decide
     */
    public PrimalSeqVar(SeqVar... seqVars) {
        this(seqVars, null, null, true);
    }

    /**
     * Creates a constructive primal heuristic search guided by an explicit transition distance matrix.
     *
     * @param seqVars    the sequence variables to decide
     * @param distMatrix the transition distance matrix between nodes (may be null)
     */
    public PrimalSeqVar(SeqVar[] seqVars, int[][] distMatrix) {
        this(seqVars, distMatrix, null, false);
    }

    /**
     * Creates a constructive primal heuristic search guided by a custom detour cost function.
     *
     * @param seqVars    the sequence variables to decide
     * @param detourCost function computing the detour cost for {@code (pred, node, succ)} (may be null)
     */
    public PrimalSeqVar(SeqVar[] seqVars, TriFunction<Integer, Integer, Integer, Integer> detourCost) {
        this(seqVars, null, detourCost, false);
    }

    private PrimalSeqVar(SeqVar[] seqVars,
                         int[][] distMatrix,
                         TriFunction<Integer, Integer, Integer, Integer> detourCost,
                         boolean autoDiscoverDistMatrix) {
        if (seqVars == null || seqVars.length == 0) {
            throw new IllegalArgumentException("PrimalSeqVar requires at least one sequence variable");
        }
        for (SeqVar sv : seqVars) {
            Objects.requireNonNull(sv, "Sequence variable cannot be null");
        }
        this.seqVars = seqVars;
        this.distMatrix = distMatrix;
        this.detourCost = detourCost;
        this.autoDiscoverDistMatrix = autoDiscoverDistMatrix;
        this.model = resolveModelProxy(seqVars);

        int maxNodes = 0;
        for (SeqVar sv : seqVars) {
            maxNodes = Math.max(maxNodes, sv.nNode());
        }
        this.nTotalNodes = maxNodes;
        this.insertBuf = new int[nTotalNodes];
        this.nodeWeight = new double[nTotalNodes];
        Arrays.fill(this.nodeWeight, 1.0);

        if (distMatrix != null) {
            updateMaxDetourFromMatrix(distMatrix);
        } else if (detourCost != null) {
            updateMaxDetourFromFunction(detourCost);
        }

        if (this.model == null || this.model.isConcrete()) {
            ensureConcreteCache();
        }
    }

    /**
     * Configures optional randomization noise for restart diversification and LNS neighborhood exploration.
     *
     * @param rng       random number generator
     * @param noiseProb noise intensity in {@code [0, 1]} ({@code 0.0} for deterministic search)
     * @return this {@code PrimalSeqVar} instance
     */
    public PrimalSeqVar withRandomNoise(Random rng, double noiseProb) {
        this.rng = rng;
        this.noiseProb = Math.max(0.0, Math.min(1.0, noiseProb));
        return this;
    }

    private void updateMaxDetourFromMatrix(int[][] matrix) {
        int maxD = 1;
        for (int[] row : matrix) {
            for (int v : row) {
                if (v > maxD) {
                    maxD = v;
                }
            }
        }
        this.maxDetour = Math.max(1, 2 * maxD);
    }

    private void updateMaxDetourFromFunction(TriFunction<Integer, Integer, Integer, Integer> fn) {
        int maxD = 1;
        int limit = Math.min(nTotalNodes, 32);
        for (int i = 0; i < limit; i++) {
            for (int j = 0; j < limit; j++) {
                if (i == j) continue;
                int c = Math.abs(fn.apply(i, j, i));
                if (c > maxD) {
                    maxD = c;
                }
            }
        }
        this.maxDetour = Math.max(1, 2 * maxD);
    }

    private static ModelProxy resolveModelProxy(SeqVar[] seqVars) {
        boolean allRaw = true;
        for (SeqVar sv : seqVars) {
            if (!(sv instanceof CPSeqVar)) {
                allRaw = false;
                break;
            }
        }
        if (allRaw) {
            CPSolver solver = ((CPSeqVar) seqVars[0]).getSolver();
            for (int i = 1; i < seqVars.length; i++) {
                if (((CPSeqVar) seqVars[i]).getSolver() != solver) {
                    throw new IllegalArgumentException("All sequence variables must belong to the same solver");
                }
            }
            return null;
        }
        ModelProxy proxy = null;
        for (SeqVar sv : seqVars) {
            if (proxy == null) {
                proxy = sv.getModelProxy();
            } else if (sv.getModelProxy() != null && proxy != sv.getModelProxy()) {
                throw new IllegalArgumentException("All sequence variables must belong to the same model");
            }
        }
        return proxy;
    }

    private void ensureConcreteCache() {
        if (model == null) {
            if (cpSolver == null) {
                cpSeqVars = new CPSeqVar[seqVars.length];
                for (int i = 0; i < seqVars.length; i++) {
                    cpSeqVars[i] = (CPSeqVar) seqVars[i];
                }
                cpSolver = cpSeqVars[0].getSolver();
                tryDiscoverDistMatrix();
            }
            return;
        }
        ConcreteModel cm = model.getConcreteModel();
        if (cm != cachedConcreteModel) {
            cachedConcreteModel = cm;
            if (cm instanceof ConcreteCPModel cpm) {
                cpSolver = cpm.solver;
                cpSeqVars = new CPSeqVar[seqVars.length];
                for (int i = 0; i < seqVars.length; i++) {
                    cpSeqVars[i] = cpm.getCPVar(seqVars[i]);
                }
                tryDiscoverDistMatrix();
            } else {
                cpSolver = null;
                cpSeqVars = null;
            }
        }
    }

    private void tryDiscoverDistMatrix() {
        if (!autoDiscoverDistMatrix || distMatrix != null || detourCost != null || cpSeqVars == null) {
            return;
        }
        for (CPSeqVar cpSeq : cpSeqVars) {
            if (cpSeq != null && cpSeq.distanceMatrix() != null) {
                this.distMatrix = cpSeq.distanceMatrix();
                updateMaxDetourFromMatrix(this.distMatrix);
                return;
            }
        }
    }

    private boolean hasDetourGuidance() {
        return distMatrix != null || detourCost != null;
    }

    private int evaluateDetour(int pred, int node, int succ) {
        if (distMatrix != null) {
            if (pred < distMatrix.length && node < distMatrix.length && succ < distMatrix.length) {
                return distMatrix[pred][node] + distMatrix[node][succ] - distMatrix[pred][succ];
            }
            return 0;
        }
        if (detourCost != null) {
            return detourCost.apply(pred, node, succ);
        }
        return 0;
    }

    @Override
    public Runnable[] get() {
        ensureConcreteCache();
        SeqVar[] activeSeqs = cpSeqVars != null ? cpSeqVars : seqVars;
        int nSeq = activeSeqs.length;

        boolean allFixed = true;
        boolean anyRequiredUninserted = false;
        for (int s = 0; s < nSeq; s++) {
            SeqVar seq = activeSeqs[s];
            if (!seq.isFixed()) {
                allFixed = false;
            }
            if (seq.nNode(REQUIRED) > seq.nNode(MEMBER)) {
                anyRequiredUninserted = true;
            }
        }
        if (allFixed) {
            return EMPTY;
        }

        // Check for immediate domain wipeout or forced exclusion of 0-insertion POSSIBLE nodes
        for (int s = 0; s < nSeq; s++) {
            SeqVar seq = activeSeqs[s];
            if (seq.isFixed()) continue;
            int nIns = seq.fillNode(insertBuf, INSERTABLE);
            for (int i = 0; i < nIns; i++) {
                int u = insertBuf[i];
                if (seq.nInsert(u) == 0) {
                    if (seq.isNode(u, REQUIRED)) {
                        bumpConflictWeight(u, 1.0);
                        return new Runnable[]{() -> {
                            throw InconsistencyException.INCONSISTENCY;
                        }};
                    } else if (seq.isNode(u, POSSIBLE)) {
                        final int sIdx = s;
                        final int uNode = u;
                        return new Runnable[]{() -> executeExclude(sIdx, uNode)};
                    }
                }
            }
        }

        boolean guided = hasDetourGuidance();
        int bestNode = -1;
        double bestScore = Double.POSITIVE_INFINITY;
        int bestSeq1 = -1;
        int bestPred1 = -1;
        int bestSeq2 = -1;
        int bestPred2 = -1;

        for (int u = 0; u < nTotalNodes; u++) {
            int totalIns = 0;
            boolean isReq = false;
            boolean isInsertable = false;
            for (int s = 0; s < nSeq; s++) {
                SeqVar seq = activeSeqs[s];
                if (seq.isNode(u, INSERTABLE)) {
                    isInsertable = true;
                    totalIns += seq.nInsert(u);
                    if (seq.isNode(u, REQUIRED)) {
                        isReq = true;
                    }
                }
            }
            if (!isInsertable) continue;
            if (anyRequiredUninserted && !isReq) continue;
            if (totalIns == 0) {
                bumpConflictWeight(u, 1.0);
                return new Runnable[]{() -> {
                    throw InconsistencyException.INCONSISTENCY;
                }};
            }

            int minDet1 = Integer.MAX_VALUE;
            int minDet2 = Integer.MAX_VALUE;
            int seq1 = -1, seq2 = -1;
            int pred1 = -1, pred2 = -1;

            for (int s = 0; s < nSeq; s++) {
                SeqVar seq = activeSeqs[s];
                if (!seq.isNode(u, INSERTABLE)) continue;
                int k = seq.fillInsert(u, insertBuf);
                for (int j = 0; j < k; j++) {
                    int p = insertBuf[j];
                    int det = 0;
                    if (guided) {
                        int succ = seq.memberAfter(p);
                        det = evaluateDetour(p, u, succ);
                    }
                    if (det < minDet1) {
                        minDet2 = minDet1;
                        seq2 = seq1;
                        pred2 = pred1;
                        minDet1 = det;
                        seq1 = s;
                        pred1 = p;
                    } else if (det < minDet2) {
                        minDet2 = det;
                        seq2 = s;
                        pred2 = p;
                    }
                }
            }

            double score;
            if (guided) {
                int regret = (totalIns >= 2 && minDet2 < Integer.MAX_VALUE)
                        ? Math.max(0, minDet2 - minDet1)
                        : maxDetour;
                double normRegret = Math.min(1.0, (double) regret / maxDetour);
                double normDet1 = Math.min(1.0, Math.max(0.0, (double) minDet1) / maxDetour);
                score = totalIns - 0.45 * normRegret + 0.05 * normDet1;
            } else {
                double wBoost = 1.0 + 0.35 * Math.log(Math.max(1.0, nodeWeight[u]));
                score = totalIns / wBoost;
            }

            if (noiseProb > 0.0 && rng != null) {
                score += rng.nextDouble() * noiseProb * 0.25;
            }

            if (score < bestScore) {
                bestScore = score;
                bestNode = u;
                bestSeq1 = seq1;
                bestPred1 = pred1;
                bestSeq2 = seq2;
                bestPred2 = pred2;
            }
        }

        if (bestNode == -1 || bestSeq1 == -1) {
            return EMPTY;
        }

        final int uFinal = bestNode;
        final int chosenSeqIdx;
        final int chosenPred;
        if (noiseProb > 0.0 && rng != null && bestSeq2 != -1 && rng.nextDouble() < noiseProb * 0.25) {
            chosenSeqIdx = bestSeq2;
            chosenPred = bestPred2;
        } else {
            chosenSeqIdx = bestSeq1;
            chosenPred = bestPred1;
        }
        final int chosenSucc = activeSeqs[chosenSeqIdx].memberAfter(chosenPred);

        Runnable leftBranch = () -> {
            try {
                executeInsert(chosenSeqIdx, chosenPred, uFinal);
            } catch (InconsistencyException e) {
                bumpConflictWeight(uFinal, 1.0);
                throw e;
            }
        };

        Runnable rightBranch = () -> {
            try {
                executeNotBetween(chosenSeqIdx, chosenPred, uFinal, chosenSucc);
            } catch (InconsistencyException e) {
                bumpConflictWeight(uFinal, 0.5);
                throw e;
            }
        };

        return new Runnable[]{leftBranch, rightBranch};
    }

    private void bumpConflictWeight(int node, double delta) {
        if (node >= 0 && node < nTotalNodes) {
            nodeWeight[node] = nodeWeight[node] * DEFAULT_CONFLICT_DECAY + delta;
        }
    }

    private void executeInsert(int seqIdx, int pred, int node) {
        if (cpSeqVars != null && cpSolver != null) {
            cpSeqVars[seqIdx].insert(pred, node);
            cpSolver.fixPoint();
        } else {
            model.add(Factory.insert(seqVars[seqIdx], pred, node));
        }
    }

    private void executeNotBetween(int seqIdx, int pred, int node, int succ) {
        if (cpSeqVars != null && cpSolver != null) {
            cpSeqVars[seqIdx].notBetween(pred, node, succ);
            cpSolver.fixPoint();
        } else {
            model.add(Factory.notBetween(seqVars[seqIdx], pred, node, succ));
        }
    }

    private void executeExclude(int seqIdx, int node) {
        if (cpSeqVars != null && cpSolver != null) {
            cpSeqVars[seqIdx].exclude(node);
            cpSolver.fixPoint();
        } else {
            model.add(Factory.exclude(seqVars[seqIdx], node));
        }
    }
}

