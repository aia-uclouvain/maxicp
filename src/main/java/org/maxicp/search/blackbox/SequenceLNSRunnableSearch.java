/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.search.blackbox;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.sequence.SeqStatus;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.blackbox.BlackBoxSearch.SeqSnapshot;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Large Neighborhood Search (LNS) runnable phase tailored for models with sequence variables ({@link SeqVar}).
 *
 * <p>Rotates across three complementary sequence neighborhood operators:
 * <ol>
 *   <li><b>Related / Coupled-Request Relaxation (Shaw Removal):</b> Selects a seed visit and relaxes
 *       spatially related visits (when a transition distance matrix is present) or random visits along
 *       with any coupled partner nodes (such as pickup-delivery pairs from {@code Cumulative} constraints),
 *       enabling both intra-route re-sequencing and inter-route request transfers.</li>
 *   <li><b>Contiguous Subsequence Window Relaxation:</b> Relaxes contiguous visit segments along one or
 *       two sequences (together with coupled partners) while freezing the surrounding skeleton subsequence.</li>
 *   <li><b>Worst-Detour / Route-Pair Relaxation:</b> Prioritizes relaxing visits that incur the largest
 *       transition detours in the current incumbent solution (or relaxes a pair of sequences when no
 *       distance matrix is present).</li>
 * </ol>
 *
 * <p>Non-relaxed member visits are re-inserted in their incumbent relative order
 * ({@code insert(prevFrozen, nextFrozen)}), leaving all intervals between consecutive frozen visits
 * open for re-inserting the relaxed visits. Neighborhood subproblems are re-optimized primarily with
 * {@link org.maxicp.search.PrimalSeqVar} for rapid primal improvement and periodically with
 * {@link org.maxicp.search.FDSSeqVar} for conflict-driven neighborhood closure.</p>
 */
public class SequenceLNSRunnableSearch extends RunnableSearch {

    private final BlackBoxSearch owner;
    private final ModelDispatcher model;
    private final List<SeqVar> sequences;
    private final List<IntExpression> vars;
    private final Objective objective;
    private final int baseFailureLimit;
    private final int freezeRatePercent;
    private final Random random;

    private int nTotalNodes = -1;
    private int[][] distMatrix;
    private int[] coupledPartners;

    public SequenceLNSRunnableSearch(
            BlackBoxSearch owner,
            ModelDispatcher model,
            List<SeqVar> sequences,
            List<IntExpression> vars,
            Objective objective,
            int baseFailureLimit,
            int freezeRatePercent,
            long randomSeed) {
        super(owner);
        if (objective == null) {
            throw new IllegalArgumentException("SequenceLNSRunnableSearch requires a non-null objective");
        }
        this.owner = owner;
        this.model = model;
        this.sequences = List.copyOf(sequences);
        this.vars = List.copyOf(vars);
        this.objective = objective;
        this.baseFailureLimit = Math.max(20, baseFailureLimit);
        this.freezeRatePercent = Math.max(20, Math.min(95, freezeRatePercent));
        this.random = new Random(randomSeed);
    }

    @Override
    void updateSolution(List<Integer> solution) {
        // sequence snapshots are maintained on BlackBoxSearch
    }

    @Override
    public SearchStatus run(long timeBudgetMillis) {
        if (!model.isConcrete()) {
            return model.runCP(() -> runInConcreteContext(timeBudgetMillis));
        }
        return runInConcreteContext(timeBudgetMillis);
    }

    private SearchStatus runInConcreteContext(long timeBudgetMillis) {
        Optional<List<SeqSnapshot>> initSnap = owner.bestSeqSolution();
        if (initSnap.isEmpty() || sequences.isEmpty()) {
            return SearchStatus.UNKNOWN;
        }
        owner.ensureCpCache();
        ensureMetadata();

        long startMillis = System.currentTimeMillis();
        long budgetMillis = Math.max(1L, timeBudgetMillis);
        Integer startBestObj = owner.bestObjectiveValue().orElse(null);
        boolean improved = false;

        int iter = 0;
        int noImproveCount = 0;

        while ((System.currentTimeMillis() - startMillis) < budgetMillis && !owner.isLimitReached()) {
            List<SeqSnapshot> snap = owner.bestSeqSolution().orElse(null);
            if (snap == null) {
                break;
            }
            Integer prevBest = owner.bestObjectiveValue().orElse(null);
            int op = iter % 3;
            boolean[] relaxed = selectRelaxedNodes(snap, op, noImproveCount);

            // Use PrimalSeqVar on 4 out of 5 iterations for fast constructive improvement,
            // and FDSSeqVar on every 5th iteration to close tight neighborhoods.
            Supplier<Runnable[]> branching = (iter % 5 == 4)
                    ? owner.sequenceFdsBranching(random)
                    : owner.sequencePrimalBranching(random, 0.15);

            int failureCutoff = baseFailureLimit + Math.min(240, (noImproveCount / 10) * 20);
            iter++;

            final boolean[] rootInconsistent = {false};
            model.getConcreteModel().getStateManager().withNewState(() -> {
                try {
                    owner.postIncumbentCut(false);
                    applySequenceRelaxation(snap, relaxed);
                } catch (InconsistencyException e) {
                    rootInconsistent[0] = true;
                    return;
                }
                DFSearch dfs = owner.createSubSearch(branching);
                dfs.onSolution(owner::recordSolutionFromCurrentState);
                try {
                    org.maxicp.search.Objective cpObj = model.getConcreteModel().createObjective(objective);
                    SearchStatistics stats = dfs.optimize(
                            cpObj,
                            s -> owner.shouldAbortSubSearch(s, startMillis, budgetMillis, failureCutoff));
                    owner.accumulateSubSearchStats(stats);
                } catch (InconsistencyException ignored) {
                }
            });

            Integer nowBest = owner.bestObjectiveValue().orElse(null);
            if (prevBest != null && nowBest != null && !nowBest.equals(prevBest)) {
                improved = true;
                noImproveCount = 0;
            } else {
                noImproveCount++;
            }
        }

        if (improved) {
            return SearchStatus.IMPROVED;
        }
        Integer finalBest = owner.bestObjectiveValue().orElse(null);
        if (startBestObj != null && finalBest != null && !finalBest.equals(startBestObj)) {
            return SearchStatus.IMPROVED;
        }
        return SearchStatus.NOT_IMPROVED;
    }

    private void ensureMetadata() {
        if (nTotalNodes > 0) {
            return;
        }
        int maxN = 0;
        for (SeqVar sv : sequences) {
            maxN = Math.max(maxN, sv.nNode());
        }
        this.nTotalNodes = Math.max(2, maxN);
        this.coupledPartners = new int[nTotalNodes];
        Arrays.fill(this.coupledPartners, -1);

        CPSeqVar[] cpSeqs = owner.cpSequences();
        if (cpSeqs != null) {
            for (CPSeqVar cpSeq : cpSeqs) {
                if (cpSeq == null) continue;
                if (this.distMatrix == null && cpSeq.distanceMatrix() != null) {
                    this.distMatrix = cpSeq.distanceMatrix();
                }
                int[] cpPartners = cpSeq.coupledPartners();
                if (cpPartners != null) {
                    int len = Math.min(nTotalNodes, cpPartners.length);
                    for (int i = 0; i < len; i++) {
                        if (cpPartners[i] >= 0) {
                            this.coupledPartners[i] = cpPartners[i];
                        }
                    }
                }
            }
        }
    }

    private boolean[] selectRelaxedNodes(List<SeqSnapshot> snap, int op, int noImproveCount) {
        boolean[] relaxed = new boolean[nTotalNodes];
        boolean[] isInteriorMember = new boolean[nTotalNodes];
        boolean[] isEndpoint = new boolean[nTotalNodes];
        int[] interiorNodes = new int[nTotalNodes];
        int nInterior = 0;

        for (SeqSnapshot s : snap) {
            if (s.start() >= 0 && s.start() < nTotalNodes) isEndpoint[s.start()] = true;
            if (s.end() >= 0 && s.end() < nTotalNodes) isEndpoint[s.end()] = true;
            List<Integer> members = s.members();
            for (int i = 1; i < members.size() - 1; i++) {
                int u = members.get(i);
                if (u >= 0 && u < nTotalNodes && !isInteriorMember[u]) {
                    isInteriorMember[u] = true;
                    interiorNodes[nInterior++] = u;
                }
            }
        }
        for (int u = 0; u < nTotalNodes; u++) {
            if (!isEndpoint[u] && !isInteriorMember[u]) {
                relaxed[u] = true;
            }
        }

        if (nInterior == 0) {
            Arrays.fill(relaxed, true);
            return relaxed;
        }

        // Adaptive fragment size: between 4 and 14 interior nodes
        int defaultRelaxCount = Math.max(4, (int) Math.round(nInterior * (100 - freezeRatePercent) / 100.0));
        int adaptiveTarget = Math.min(nInterior, Math.max(4, Math.min(14, defaultRelaxCount) + ((noImproveCount / 8) % 5) * 2));
        int targetRelaxed = Math.min(nInterior, Math.max(2, adaptiveTarget));

        if (op == 0) {
            // Operator 0: Shaw / Spatial Relatedness or Random Coupled Removal
            int seed = interiorNodes[random.nextInt(nInterior)];
            int count = relaxWithPartner(relaxed, seed);
            if (distMatrix != null) {
                Integer[] cands = new Integer[nInterior];
                int[] score = new int[nInterior];
                for (int i = 0; i < nInterior; i++) {
                    int u = interiorNodes[i];
                    cands[i] = u;
                    int d = (seed < distMatrix.length && u < distMatrix.length)
                            ? Math.min(distMatrix[seed][u], distMatrix[u][seed])
                            : 0;
                    score[u] = d + random.nextInt(Math.max(1, d / 2 + 50));
                }
                Arrays.sort(cands, Comparator.comparingInt(u -> score[u]));
                for (int u : cands) {
                    if (count >= targetRelaxed) break;
                    if (!relaxed[u]) {
                        count += relaxWithPartner(relaxed, u);
                    }
                }
            } else {
                int attempts = 0;
                while (count < targetRelaxed && attempts < nInterior * 2) {
                    int u = interiorNodes[random.nextInt(nInterior)];
                    if (!relaxed[u]) {
                        count += relaxWithPartner(relaxed, u);
                    }
                    attempts++;
                }
            }
        } else if (op == 1) {
            // Operator 1: Contiguous Subsequence Window across 1 or 2 routes
            int nRoutes = snap.size();
            int nRoutesToTouch = Math.min(nRoutes, 1 + random.nextInt(2));
            int perRoute = Math.max(2, targetRelaxed / Math.max(1, nRoutesToTouch));
            int count = 0;
            for (int r = 0; r < nRoutesToTouch; r++) {
                SeqSnapshot routeSnap = snap.get(random.nextInt(nRoutes));
                List<Integer> members = routeSnap.members();
                if (members.size() <= 2) continue;
                int intLen = members.size() - 2;
                int startIdx = 1 + random.nextInt(intLen);
                for (int k = 0; k < perRoute && (startIdx + k) < members.size() - 1; k++) {
                    int u = members.get(startIdx + k);
                    count += relaxWithPartner(relaxed, u);
                    if (count >= targetRelaxed) break;
                }
            }
            int attempts = 0;
            while (count < Math.min(nInterior, 4) && attempts < nInterior * 2) {
                int u = interiorNodes[random.nextInt(nInterior)];
                if (!relaxed[u]) {
                    count += relaxWithPartner(relaxed, u);
                }
                attempts++;
            }
        } else {
            // Operator 2: Worst-Detour Coupled Removal (or Route-Pair segment removal if no distance matrix)
            if (distMatrix != null) {
                int[] detour = new int[nTotalNodes];
                for (SeqSnapshot routeSnap : snap) {
                    List<Integer> members = routeSnap.members();
                    for (int i = 1; i < members.size() - 1; i++) {
                        int prev = members.get(i - 1);
                        int u = members.get(i);
                        int succ = members.get(i + 1);
                        if (u >= 0 && u < nTotalNodes
                                && prev < distMatrix.length && u < distMatrix.length && succ < distMatrix.length) {
                            detour[u] = distMatrix[prev][u] + distMatrix[u][succ] - distMatrix[prev][succ];
                        }
                    }
                }
                Integer[] cands = new Integer[nInterior];
                int[] noisyScore = new int[nTotalNodes];
                for (int i = 0; i < nInterior; i++) {
                    int u = interiorNodes[i];
                    cands[i] = u;
                    int p = coupledPartners[u];
                    int pairDet = detour[u] + (p >= 0 && p < nTotalNodes ? detour[p] : 0);
                    noisyScore[u] = -(pairDet + random.nextInt(Math.max(1, Math.abs(pairDet) / 2 + 50)));
                }
                Arrays.sort(cands, Comparator.comparingInt(u -> noisyScore[u]));
                int count = 0;
                for (int u : cands) {
                    if (count >= targetRelaxed) break;
                    if (!relaxed[u]) {
                        count += relaxWithPartner(relaxed, u);
                    }
                }
            } else {
                int count = 0;
                int attempts = 0;
                while (count < targetRelaxed && attempts < nInterior * 2) {
                    int u = interiorNodes[random.nextInt(nInterior)];
                    if (!relaxed[u]) {
                        count += relaxWithPartner(relaxed, u);
                    }
                    attempts++;
                }
            }
        }
        return relaxed;
    }

    private int relaxWithPartner(boolean[] relaxed, int u) {
        int added = 0;
        if (u >= 0 && u < nTotalNodes && !relaxed[u]) {
            relaxed[u] = true;
            added++;
        }
        int partner = (u >= 0 && u < coupledPartners.length) ? coupledPartners[u] : -1;
        if (partner >= 0 && partner < nTotalNodes && !relaxed[partner]) {
            relaxed[partner] = true;
            added++;
        }
        return added;
    }

    private void applySequenceRelaxation(List<SeqSnapshot> snap, boolean[] relaxed) {
        CPSeqVar[] cpSeqs = owner.cpSequences();
        int nSeq = Math.min(sequences.size(), snap.size());
        boolean[] wasMember = new boolean[nTotalNodes];
        for (int v = 0; v < nSeq; v++) {
            List<Integer> members = snap.get(v).members();
            Arrays.fill(wasMember, false);
            for (int m : members) {
                if (m >= 0 && m < nTotalNodes) {
                    wasMember[m] = true;
                }
            }
            int current = members.get(0);
            if (cpSeqs != null && cpSeqs[v] != null) {
                CPSeqVar seq = cpSeqs[v];
                for (int i = 1; i < members.size() - 1; i++) {
                    int node = members.get(i);
                    if (node >= 0 && node < relaxed.length && !relaxed[node]) {
                        if (!seq.isNode(node, SeqStatus.MEMBER)) {
                            seq.insert(current, node);
                        }
                        current = node;
                    }
                }
                int nNode = seq.nNode();
                for (int u = 0; u < nNode && u < relaxed.length; u++) {
                    if (!wasMember[u] && !relaxed[u] && seq.isNode(u, SeqStatus.POSSIBLE)) {
                        seq.exclude(u);
                    }
                }
            } else {
                SeqVar seq = sequences.get(v);
                for (int i = 1; i < members.size() - 1; i++) {
                    int node = members.get(i);
                    if (node >= 0 && node < relaxed.length && !relaxed[node]) {
                        if (!seq.isNode(node, SeqStatus.MEMBER)) {
                            model.add(Factory.insert(seq, current, node), false);
                        }
                        current = node;
                    }
                }
                int nNode = seq.nNode();
                for (int u = 0; u < nNode && u < relaxed.length; u++) {
                    if (!wasMember[u] && !relaxed[u] && seq.isNode(u, SeqStatus.POSSIBLE)) {
                        model.add(Factory.exclude(seq, u), false);
                    }
                }
            }
        }
        if (model.getConcreteModel() instanceof ConcreteCPModel cpm) {
            cpm.solver.fixPoint();
        }
    }
}
