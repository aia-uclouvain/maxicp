/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 */

package org.maxicp.search;

import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.concrete.ConcreteModel;
import org.maxicp.state.StateManager;
import org.maxicp.util.TriFunction;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

/**
 * Failure-Directed Search (FDS) adapted for Sequence Variables ({@link SeqVar} and {@link CPSeqVar}).
 * <p>
 * This search strategy adapts the principles of Failure-Directed Search (Vilím, Laborie, Shaw, CPAIOR 2015)
 * to sequence variables using binary insertion decisions:
 * <ul>
 *   <li><b>Positive branch (+):</b> {@code insert(seq, pred, node)} — inserts {@code node} inside the current
 *       member interval {@code (pred, succ)}, where {@code succ = seq.memberAfter(pred)}.</li>
 *   <li><b>Negative branch (-):</b> {@code notBetween(seq, pred, node, succ)} — forbids {@code node} from ever
 *       being inserted inside the interval {@code (pred, succ)}.</li>
 * </ul>
 * <p>
 * Key features of Sequence FDS:
 * <ol>
 *   <li><b>Exact Binary Partition &amp; Active Refutation Propagation:</b> Unlike N-ary branching over all feasible
 *       insertions of a node, refuting the positive branch explicitly posts {@code notBetween(seq, pred, node, succ)}
 *       and runs {@code fixPoint()} on the negative branch. This triggers {@code propagateOnInsertRemoved} in sequence
 *       constraints ({@code Cumulative}, {@code Precedence}), automatically excludes {@code node} from {@code seq}
 *       when its insertion count drops to 0, and automatically inserts {@code node} when it is required and only
 *       1 valid insertion remains.</li>
 *   <li><b>Dynamic Choice Activation &amp; Transition Sharing:</b> Choices {@code (pred, node)} become active
 *       dynamically as {@code pred} becomes a member of a sequence ({@code seq.hasInsert(pred, node)}). Indexing
 *       ratings by transition {@code (pred, node)} shares learned conflict ratings across symmetrical vehicles
 *       while keeping vehicle-specific depot transitions distinct.</li>
 *   <li><b>Slot-Normalized Search Space Reduction ({@code R}):</b> Because inserting a member splits one interval
 *       {@code (pred, succ)} into two intervals {@code (pred, node)} and {@code (node, succ)}, raw insertion counts
 *       {@code nInsert(u)} naturally grow by 1 for unfiltered nodes. {@code FDSSeqVar} normalizes each node's
 *       insertion count by the total number of member intervals across all sequences so that {@code R} accurately
 *       measures constraint filtering in {@code [0, 1]}.</li>
 *   <li><b>Predecessor Conflict Blame:</b> When {@code insert(seq, pred, node)} fails, conflict blame is also
 *       propagated to {@code nodeRating[pred]}, accelerating failure detection on coupled pairs (such as
 *       pickup-and-delivery requests in DARP).</li>
 *   <li><b>Optional Detour Cost Guidance:</b> For routing optimization problems (DARP, VRPTW, TSPTW), an optional
 *       distance matrix or detour cost function {@code (pred, node, succ) -> cost} can be supplied to guide choice
 *       selection and branch ordering towards low-detour insertions while retaining FDS conflict learning.</li>
 * </ol>
 *
 * @see FDS
 * @see CPSeqVar
 * @see SeqVar
 */
public class FDSSeqVar implements Supplier<Runnable[]> {

    private static final double DEFAULT_ALPHA = 0.95;
    private static final double DEFAULT_DETOUR_WEIGHT = 0.20;

    private final SeqVar[] seqVars;
    private final ModelProxy model;
    private final double alpha;
    private final double detourWeight;
    private final int[][] distMatrix;
    private final TriFunction<Integer, Integer, Integer, Integer> detourCost;
    private final int nTotalNodes;

    /** Positive branch ratings indexed by [pred][node]. */
    private final double[][] ratingPos;
    /** Negative branch ratings indexed by [pred][node]. */
    private final double[][] ratingNeg;
    /** Predecessor conflict rating indexed by [pred], tracking downstream failures after pred is inserted. */
    private final double[] predRating;
    /** Running average of local ratings per search depth: [0] = mean, [1] = count. */
    private final Map<Integer, double[]> avgRatingByDepth;

    /** Reusable buffers for domain snapshots and insertion queries. */
    private final int[] insertions;
    private final long[] domBeforeBuffer;
    private double avgEdgeCost = 1.0;

    /** Cached concrete model and raw CP variables for direct zero-overhead access. */
    private ConcreteModel cachedConcreteModel;
    private CPSolver cpSolver;
    private CPSeqVar[] cpSeqVars;
    private StateManager sm;
    private int baseLevel;

    /**
     * Creates a generic Failure-Directed Search for the given sequence variables.
     *
     * @param seqVars the sequence variables to decide
     */
    public FDSSeqVar(SeqVar... seqVars) {
        this(DEFAULT_ALPHA, 0.0, seqVars, null, null);
    }

    /**
     * Creates a generic Failure-Directed Search for the given sequence variables with custom decay factor.
     *
     * @param alpha   exponential decay factor for rating updates (typical: 0.9 to 0.99)
     * @param seqVars the sequence variables to decide
     */
    public FDSSeqVar(double alpha, SeqVar... seqVars) {
        this(alpha, 0.0, seqVars, null, null);
    }

    /**
     * Creates a Failure-Directed Search for the given sequence variables guided by a distance matrix.
     * The detour cost of inserting {@code node} between {@code pred} and {@code succ} is computed as
     * {@code distMatrix[pred][node] + distMatrix[node][succ] - distMatrix[pred][succ]}.
     *
     * @param seqVars    the sequence variables to decide
     * @param distMatrix the distance matrix between nodes
     */
    public FDSSeqVar(SeqVar[] seqVars, int[][] distMatrix) {
        this(DEFAULT_ALPHA, DEFAULT_DETOUR_WEIGHT, seqVars, distMatrix, null);
    }

    /**
     * Creates a Failure-Directed Search for the given sequence variables guided by a custom detour cost function.
     *
     * @param seqVars    the sequence variables to decide
     * @param detourCost function computing the detour cost for {@code (pred, node, succ)}
     */
    public FDSSeqVar(SeqVar[] seqVars, TriFunction<Integer, Integer, Integer, Integer> detourCost) {
        this(DEFAULT_ALPHA, DEFAULT_DETOUR_WEIGHT, seqVars, null, detourCost);
    }

    /**
     * Creates a Failure-Directed Search for the given sequence variables with custom parameters and distance matrix.
     *
     * @param alpha        exponential decay factor for FDS rating updates (typical: 0.9 to 0.99)
     * @param detourWeight weight of the normalized detour cost relative to the FDS rating (typical: 0.1 to 0.3)
     * @param seqVars      the sequence variables to decide
     * @param distMatrix   the distance matrix between nodes (may be null)
     */
    public FDSSeqVar(double alpha, double detourWeight, SeqVar[] seqVars, int[][] distMatrix) {
        this(alpha, detourWeight, seqVars, distMatrix, null);
    }

    /**
     * Creates a Failure-Directed Search for the given sequence variables with custom parameters and detour function.
     *
     * @param alpha        exponential decay factor for FDS rating updates (typical: 0.9 to 0.99)
     * @param detourWeight weight of the normalized detour cost relative to the FDS rating (typical: 0.1 to 0.3)
     * @param seqVars      the sequence variables to decide
     * @param detourCost   function computing the detour cost for {@code (pred, node, succ)} (may be null)
     */
    public FDSSeqVar(double alpha, double detourWeight, SeqVar[] seqVars,
                     TriFunction<Integer, Integer, Integer, Integer> detourCost) {
        this(alpha, detourWeight, seqVars, null, detourCost);
    }

    private FDSSeqVar(double alpha, double detourWeight, SeqVar[] seqVars,
                      int[][] distMatrix, TriFunction<Integer, Integer, Integer, Integer> detourCost) {
        if (seqVars == null || seqVars.length == 0) {
            throw new IllegalArgumentException("FDSSeqVar requires at least one sequence variable");
        }
        for (SeqVar sv : seqVars) {
            Objects.requireNonNull(sv, "Sequence variable cannot be null");
        }
        if (alpha <= 0.0 || alpha >= 1.0) {
            throw new IllegalArgumentException("alpha must be in (0, 1)");
        }
        this.seqVars = seqVars;
        this.alpha = alpha;
        this.detourWeight = Math.max(0.0, detourWeight);
        this.distMatrix = distMatrix;
        this.detourCost = detourCost;
        this.model = resolveModelProxy(seqVars);

        int maxNodes = 0;
        for (SeqVar sv : seqVars) {
            maxNodes = Math.max(maxNodes, sv.nNode());
        }
        this.nTotalNodes = maxNodes;

        this.ratingPos = new double[nTotalNodes][nTotalNodes];
        this.ratingNeg = new double[nTotalNodes][nTotalNodes];
        for (int i = 0; i < nTotalNodes; i++) {
            Arrays.fill(ratingPos[i], 1.0);
            Arrays.fill(ratingNeg[i], 1.0);
        }
        this.predRating = new double[nTotalNodes];
        Arrays.fill(predRating, 1.0);
        this.avgRatingByDepth = new HashMap<>();

        this.insertions = new int[nTotalNodes];
        this.domBeforeBuffer = new long[nTotalNodes + 1];

        if (distMatrix != null) {
            long sum = 0;
            int cnt = 0;
            for (int i = 0; i < distMatrix.length; i++) {
                for (int j = 0; j < distMatrix[i].length; j++) {
                    if (i != j && distMatrix[i][j] > 0) {
                        sum += distMatrix[i][j];
                        cnt++;
                    }
                }
            }
            if (cnt > 0) {
                this.avgEdgeCost = (double) sum / cnt;
            }
        } else if (detourCost != null) {
            long sum = 0;
            int cnt = 0;
            int limit = Math.min(nTotalNodes, 32);
            for (int i = 0; i < limit; i++) {
                for (int j = 0; j < limit; j++) {
                    if (i == j) continue;
                    int c = Math.abs(detourCost.apply(i, j, i));
                    if (c > 0) {
                        sum += c;
                        cnt++;
                    }
                }
            }
            if (cnt > 0) {
                this.avgEdgeCost = (double) sum / cnt;
            }
        }

        if (this.model == null || this.model.isConcrete()) {
            ensureConcreteCache();
        }
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
                sm = cpSolver.getStateManager();
                baseLevel = sm.getLevel();
            }
            return;
        }
        ConcreteModel cm = model.getConcreteModel();
        if (cm != cachedConcreteModel) {
            cachedConcreteModel = cm;
            sm = cm.getStateManager();
            baseLevel = sm.getLevel();
            if (cm instanceof ConcreteCPModel cpm) {
                cpSolver = cpm.solver;
                cpSeqVars = new CPSeqVar[seqVars.length];
                for (int i = 0; i < seqVars.length; i++) {
                    cpSeqVars[i] = cpm.getCPVar(seqVars[i]);
                }
            } else {
                cpSolver = null;
                cpSeqVars = null;
            }
        }
    }

    /**
     * Resets the depth base level to the current solver level while keeping learned choice ratings.
     */
    public void resetLevel() {
        ensureConcreteCache();
        if (sm != null) {
            this.baseLevel = sm.getLevel();
        }
    }

    private int currentDepth() {
        return sm == null ? 0 : Math.max(0, sm.getLevel() - baseLevel);
    }

    private double[] getAvgRating(int depth) {
        return avgRatingByDepth.computeIfAbsent(depth, k -> new double[]{1.0, 0.0});
    }

    private SeqVar seqAt(int idx) {
        return cpSeqVars != null ? cpSeqVars[idx] : seqVars[idx];
    }

    private long[] snapshotDomains() {
        long totalSlots = 0;
        int nSeq = seqVars.length;
        for (int s = 0; s < nSeq; s++) {
            totalSlots += Math.max(1, seqAt(s).nNode(MEMBER) - 1);
        }
        domBeforeBuffer[nTotalNodes] = Math.max(1L, totalSlots);

        for (int u = 0; u < nTotalNodes; u++) {
            long ins = 0;
            for (int s = 0; s < nSeq; s++) {
                SeqVar r = seqAt(s);
                if (u < r.nNode() && r.isNode(u, INSERTABLE)) {
                    ins += r.nInsert(u);
                }
            }
            domBeforeBuffer[u] = ins;
        }
        return domBeforeBuffer;
    }

    /**
     * Computes the slot-normalized search space reduction ratio {@code R} in {@code [0, 1]}.
     * Normalizes each unfixed node's insertion count by the total number of member intervals
     * across all sequences so that splitting an interval upon insertion does not artificially inflate {@code R}.
     */
    private double computeSlotNormalizedReduction(long[] before) {
        long sBefore = before[nTotalNodes];
        long sAfter = 0;
        int nSeq = seqVars.length;
        for (int s = 0; s < nSeq; s++) {
            sAfter += Math.max(1, seqAt(s).nNode(MEMBER) - 1);
        }
        sAfter = Math.max(1L, sAfter);

        double ratio = 1.0;
        for (int u = 0; u < nTotalNodes; u++) {
            long iBefore = before[u];
            if (iBefore > 1L) {
                long iAfter = 0;
                for (int s = 0; s < nSeq; s++) {
                    SeqVar r = seqAt(s);
                    if (u < r.nNode() && r.isNode(u, INSERTABLE)) {
                        iAfter += r.nInsert(u);
                    }
                }
                if (iAfter == 0L) {
                    ratio *= 1.0 / (double) iBefore;
                } else {
                    double nodeRatio = ((double) iAfter * (double) sBefore)
                            / ((double) iBefore * (double) sAfter);
                    if (nodeRatio < 1.0) {
                        ratio *= nodeRatio;
                    }
                }
            }
        }
        return Math.min(ratio, 1.0);
    }

    private void updateRating(int pred, int node, boolean isPositive, boolean failed, double R) {
        double localRating = failed ? 0.0 : 1.0 + R;
        int d = currentDepth();
        double[] avg = getAvgRating(d);
        avg[1] += 1.0;
        avg[0] += (localRating - avg[0]) / avg[1];
        double avgVal = avg[0] > 1e-6 ? avg[0] : 1.0;
        double norm = localRating / avgVal;

        if (isPositive) {
            ratingPos[pred][node] = alpha * ratingPos[pred][node] + (1.0 - alpha) * norm;
        } else {
            ratingNeg[pred][node] = alpha * ratingNeg[pred][node] + (1.0 - alpha) * norm;
        }
        predRating[pred] = alpha * predRating[pred] + (1.0 - alpha) * norm;
    }

    private int evaluateDetour(SeqVar route, int pred, int node, int succ) {
        if (distMatrix != null) {
            return distMatrix[pred][node] + distMatrix[node][succ] - distMatrix[pred][succ];
        }
        if (detourCost != null) {
            return detourCost.apply(pred, node, succ);
        }
        return route.nPred(node) + route.nSucc(node);
    }

    private boolean hasDetourGuidance() {
        return (distMatrix != null || detourCost != null) && detourWeight > 0.0;
    }

    @Override
    public Runnable[] get() {
        ensureConcreteCache();

        int nSeq = seqVars.length;
        boolean anyUnfixed = false;
        boolean anyRequiredUninserted = false;

        for (int u = 0; u < nTotalNodes; u++) {
            for (int s = 0; s < nSeq; s++) {
                SeqVar r = seqAt(s);
                if (u >= r.nNode()) continue;
                if (r.isNode(u, REQUIRED) && !r.isNode(u, MEMBER) && r.nInsert(u) == 0) {
                    return new Runnable[]{() -> {
                        throw InconsistencyException.INCONSISTENCY;
                    }};
                }
                if (r.isNode(u, INSERTABLE)) {
                    int nIns = r.nInsert(u);
                    if (nIns > 0) {
                        anyUnfixed = true;
                        if (r.isNode(u, REQUIRED)) {
                            anyRequiredUninserted = true;
                        }
                    }
                }
            }
        }
        if (!anyUnfixed) {
            return EMPTY;
        }

        int bestSeqIdx = -1;
        int bestPred = -1;
        int bestNode = -1;
        double bestScore = Double.MAX_VALUE;
        int bestTie = Integer.MAX_VALUE;
        boolean useDetour = hasDetourGuidance();
        double fdsWeight = 1.0 - detourWeight;

        for (int u = 0; u < nTotalNodes; u++) {
            int totalIns = 0;
            boolean isReq = false;
            boolean isIns = false;
            for (int s = 0; s < nSeq; s++) {
                SeqVar r = seqAt(s);
                if (u >= r.nNode()) continue;
                if (r.isNode(u, INSERTABLE)) {
                    int nIns = r.nInsert(u);
                    if (nIns > 0) {
                        isIns = true;
                        totalIns += nIns;
                        if (r.isNode(u, REQUIRED)) {
                            isReq = true;
                        }
                    }
                }
            }
            if (!isIns) continue;
            if (anyRequiredUninserted && !isReq) continue;

            for (int s = 0; s < nSeq; s++) {
                SeqVar r = seqAt(s);
                if (u >= r.nNode() || !r.isNode(u, INSERTABLE)) continue;
                if (isReq && !r.isNode(u, REQUIRED)) continue;
                int nIns = r.fillInsert(u, insertions);
                for (int j = 0; j < nIns; j++) {
                    int pred = insertions[j];
                    int succ = r.memberAfter(pred);
                    int tie = evaluateDetour(r, pred, u, succ);
                    double rawFds = (ratingPos[pred][u] + ratingNeg[pred][u]) * (0.9 + 0.1 * predRating[u]);
                    double cRating;
                    if (useDetour) {
                        double detourNorm = tie / avgEdgeCost;
                        cRating = totalIns * (fdsWeight * rawFds + detourWeight * detourNorm);
                    } else {
                        cRating = totalIns * rawFds;
                    }
                    if (cRating < bestScore - 1e-9 || (Math.abs(cRating - bestScore) <= 1e-9 && tie < bestTie)) {
                        bestScore = cRating;
                        bestTie = tie;
                        bestSeqIdx = s;
                        bestPred = pred;
                        bestNode = u;
                    }
                }
            }
        }

        if (bestSeqIdx == -1) {
            return new Runnable[]{() -> {
                throw InconsistencyException.INCONSISTENCY;
            }};
        }

        final int seqIdx = bestSeqIdx;
        final int pred = bestPred;
        final int node = bestNode;
        final int succ = seqAt(seqIdx).memberAfter(pred);
        boolean posFirst = useDetour || (ratingPos[pred][node] <= ratingNeg[pred][node]);
        Runnable posBranch = makeBranch(seqIdx, pred, node, succ, true);
        Runnable negBranch = makeBranch(seqIdx, pred, node, succ, false);
        return posFirst ? branch(posBranch, negBranch) : branch(negBranch, posBranch);
    }

    private Runnable makeBranch(int seqIdx, int pred, int node, int succ, boolean isPositive) {
        return () -> {
            long[] before = snapshotDomains();
            try {
                if (cpSeqVars != null) {
                    CPSeqVar route = cpSeqVars[seqIdx];
                    if (isPositive) {
                        if (!route.hasInsert(pred, node)) {
                            throw InconsistencyException.INCONSISTENCY;
                        }
                        route.insert(pred, node);
                    } else {
                        if (!route.isNode(pred, MEMBER) || !route.isNode(succ, MEMBER)) {
                            throw InconsistencyException.INCONSISTENCY;
                        }
                        route.notBetween(pred, node, succ);
                    }
                    cpSolver.fixPoint();
                } else {
                    SeqVar route = seqVars[seqIdx];
                    if (isPositive) {
                        if (!route.hasInsert(pred, node)) {
                            throw InconsistencyException.INCONSISTENCY;
                        }
                        model.add(Factory.insert(route, pred, node));
                    } else {
                        if (!route.isNode(pred, MEMBER) || !route.isNode(succ, MEMBER)) {
                            throw InconsistencyException.INCONSISTENCY;
                        }
                        model.add(Factory.notBetween(route, pred, node, succ));
                    }
                }
            } catch (InconsistencyException e) {
                updateRating(pred, node, isPositive, true, 0.0);
                throw e;
            }
            double R = computeSlotNormalizedReduction(before);
            updateRating(pred, node, isPositive, false, R);
        };
    }
}

