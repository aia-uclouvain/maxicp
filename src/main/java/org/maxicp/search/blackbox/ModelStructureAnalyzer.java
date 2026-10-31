package org.maxicp.search.blackbox;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Constraint;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.bool.Eq;
import org.maxicp.modeling.algebra.bool.GreaterOrEq;
import org.maxicp.modeling.algebra.bool.LessOrEq;
import org.maxicp.modeling.algebra.integer.CstMul;
import org.maxicp.modeling.algebra.integer.CstOffset;
import org.maxicp.modeling.algebra.integer.Element1D;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.integer.Max;
import org.maxicp.modeling.algebra.integer.Min;
import org.maxicp.modeling.algebra.integer.Sum;
import org.maxicp.modeling.algebra.integer.UnaryMinus;
import org.maxicp.modeling.algebra.integer.WeightedSum;
import org.maxicp.modeling.constraints.BinPacking;
import org.maxicp.modeling.constraints.ExpressionIsTrue;
import org.maxicp.modeling.constraints.Table;
import org.maxicp.modeling.symbolic.Maximization;
import org.maxicp.modeling.symbolic.Minimization;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.modeling.symbolic.SharedMaximization;
import org.maxicp.modeling.symbolic.SharedMinimization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generic, problem-independent structural analyzer for a constraint model.
 *
 * <p>Inspects the constraint hypergraph, functional equality definitions, and objective AST to derive:
 * <ul>
 *   <li>Primary vs. secondary (functionally defined or degree-&le;1 objective-slack) decision variables,</li>
 *   <li>Initial structural variable weights and primal constraint-graph adjacency lists,</li>
 *   <li>Per-variable objective polarities ({@code -1} prefer min, {@code +1} prefer max, {@code 0} neutral),</li>
 *   <li>Dominated degree-&le;1 table-slack variables that can be assigned their optimal bound once primary variables are fixed.</li>
 * </ul>
 */
public final class ModelStructureAnalyzer {

    private static final int MAX_NEIGHBORS_PER_VAR = 48;

    private final int nVars;
    private final int[] primaryIndices;
    private final int[] secondaryIndices;
    private final boolean[] isSecondary;
    private final int[] dominantSlackIndices;
    private final int[] constraintDegree;
    private final double[] structuralWeights;
    private final int[] objectivePolarity;
    private final long[][] objectiveValueCosts;
    private final int[][] neighbors;

    private ModelStructureAnalyzer(
            int nVars,
            int[] primaryIndices,
            int[] secondaryIndices,
            boolean[] isSecondary,
            int[] dominantSlackIndices,
            int[] constraintDegree,
            double[] structuralWeights,
            int[] objectivePolarity,
            long[][] objectiveValueCosts,
            int[][] neighbors) {
        this.nVars = nVars;
        this.primaryIndices = primaryIndices;
        this.secondaryIndices = secondaryIndices;
        this.isSecondary = isSecondary;
        this.dominantSlackIndices = dominantSlackIndices;
        this.constraintDegree = constraintDegree;
        this.structuralWeights = structuralWeights;
        this.objectivePolarity = objectivePolarity;
        this.objectiveValueCosts = objectiveValueCosts;
        this.neighbors = neighbors;
    }

    public static ModelStructureAnalyzer analyze(ModelDispatcher model, List<IntExpression> vars, Objective objective) {
        int n = vars.size();
        if (n == 0) {
            return new ModelStructureAnalyzer(
                    0, new int[0], new int[0], new boolean[0], new int[0],
                    new int[0], new double[0], new int[0], new long[0][], new int[0][]);
        }

        IdentityHashMap<IntExpression, Integer> varToIndex = new IdentityHashMap<>(n * 2);
        Map<IntExpression, Integer> eqVarToIndex = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            IntExpression v = vars.get(i);
            varToIndex.put(v, i);
            eqVarToIndex.putIfAbsent(v, i);
        }

        int[] ctrDegree = new int[n];
        double[] structWeights = new double[n];
        Arrays.fill(structWeights, 1.0);

        Constraint[] singleConstraintOfVar = new Constraint[n];
        List<int[]> functionalDefs = new ArrayList<>(); // [0] = targetVar, [1..k] = dependency vars
        boolean[] hasCandidateDef = new boolean[n];

        @SuppressWarnings("unchecked")
        Map<Integer, Double>[] neighborWeights = new Map[n];

        // Also record Eq/Le/Ge links for objective polarity propagation
        List<Expression> equalityExpressions = new ArrayList<>();

        for (Constraint c : model.getConstraints()) {
            int[] scope = extractConstraintScope(c, varToIndex, eqVarToIndex);
            if (scope.length == 0) {
                continue;
            }
            if (scope.length == 1) {
                // Unary constraint filters domain at root; does not couple variables
                continue;
            }
            double w = 1.0 / Math.max(1, scope.length - 1);
            for (int idx : scope) {
                ctrDegree[idx]++;
                structWeights[idx] += w;
                if (ctrDegree[idx] == 1) {
                    singleConstraintOfVar[idx] = c;
                } else {
                    singleConstraintOfVar[idx] = null;
                }
            }

            // Record adjacency for moderate-arity constraints to avoid O(n^2) blowup on global constraints
            if (scope.length <= 32) {
                for (int i = 0; i < scope.length; i++) {
                    int u = scope[i];
                    Map<Integer, Double> map = neighborWeights[u];
                    if (map == null) {
                        map = new HashMap<>();
                        neighborWeights[u] = map;
                    }
                    for (int j = 0; j < scope.length; j++) {
                        if (i == j) continue;
                        int v = scope[j];
                        if (map.size() < MAX_NEIGHBORS_PER_VAR * 2 || map.containsKey(v)) {
                            map.merge(v, w, Double::sum);
                        }
                    }
                }
            }

            if (c instanceof ExpressionIsTrue eit) {
                Expression boolExpr = eit.expr();
                if (boolExpr instanceof Eq || boolExpr instanceof LessOrEq || boolExpr instanceof GreaterOrEq) {
                    equalityExpressions.add(boolExpr);
                }
                if (boolExpr instanceof Eq eq) {
                    tryExtractFunctionalDefinition(eq.a(), eq.b(), varToIndex, eqVarToIndex, hasCandidateDef, functionalDefs);
                }
            } else if (c instanceof BinPacking bp) {
                tryExtractBinPackingDefinitions(bp, varToIndex, eqVarToIndex, hasCandidateDef, functionalDefs);
            } else if (c instanceof Table table && table.starred().isEmpty()) {
                tryExtractTableFunctionalDefinition(table, varToIndex, eqVarToIndex, hasCandidateDef, functionalDefs);
            }
        }

        // Objective polarity analysis
        boolean[] inObjective = new boolean[n];
        boolean[] positiveInObj = new boolean[n];
        boolean[] negativeInObj = new boolean[n];
        long[] objSignSum = new long[n];
        long[][] objValCosts = new long[n][];
        boolean isLinearObjective = false;

        IntExpression objExpr = extractObjectiveExpression(objective);
        int objDir = extractObjectiveDirection(objective);
        if (objExpr != null && objDir != 0) {
            isLinearObjective = analyzeObjectiveExpression(
                    objExpr, objDir, varToIndex, eqVarToIndex,
                    inObjective, positiveInObj, negativeInObj, objSignSum, objValCosts);

            // If objective is a single variable (or has proxy variables), propagate polarity through Eq/Le/Ge
            for (Expression rel : equalityExpressions) {
                if (rel instanceof Eq eq) {
                    Integer idxA = lookupVar(eq.a(), varToIndex, eqVarToIndex);
                    Integer idxB = lookupVar(eq.b(), varToIndex, eqVarToIndex);
                    if (idxA != null && inObjective[idxA] && objSignSum[idxA] != 0 && idxB == null) {
                        int dir = Long.signum(objSignSum[idxA]);
                        analyzeObjectiveExpression(eq.b(), dir, varToIndex, eqVarToIndex,
                                inObjective, positiveInObj, negativeInObj, objSignSum, objValCosts);
                    } else if (idxB != null && inObjective[idxB] && objSignSum[idxB] != 0 && idxA == null) {
                        int dir = Long.signum(objSignSum[idxB]);
                        analyzeObjectiveExpression(eq.a(), dir, varToIndex, eqVarToIndex,
                                inObjective, positiveInObj, negativeInObj, objSignSum, objValCosts);
                    }
                } else if (rel instanceof LessOrEq le) {
                    // le.a() <= le.b()
                    Integer idxB = lookupVar(le.b(), varToIndex, eqVarToIndex);
                    Integer idxA = lookupVar(le.a(), varToIndex, eqVarToIndex);
                    if (idxB != null && inObjective[idxB] && objSignSum[idxB] < 0 && idxA == null) {
                        // Minimizing b where a <= b -> smaller a helps smaller b
                        analyzeObjectiveExpression(le.a(), -1, varToIndex, eqVarToIndex,
                                inObjective, positiveInObj, negativeInObj, objSignSum, objValCosts);
                    } else if (idxA != null && inObjective[idxA] && objSignSum[idxA] > 0 && idxB == null) {
                        // Maximizing a where a <= b -> larger b helps larger a
                        analyzeObjectiveExpression(le.b(), 1, varToIndex, eqVarToIndex,
                                inObjective, positiveInObj, negativeInObj, objSignSum, objValCosts);
                    }
                } else if (rel instanceof GreaterOrEq ge) {
                    // ge.a() >= ge.b()
                    Integer idxA = lookupVar(ge.a(), varToIndex, eqVarToIndex);
                    Integer idxB = lookupVar(ge.b(), varToIndex, eqVarToIndex);
                    if (idxA != null && inObjective[idxA] && objSignSum[idxA] < 0 && idxB == null) {
                        analyzeObjectiveExpression(ge.b(), -1, varToIndex, eqVarToIndex,
                                inObjective, positiveInObj, negativeInObj, objSignSum, objValCosts);
                    } else if (idxB != null && inObjective[idxB] && objSignSum[idxB] > 0 && idxA == null) {
                        analyzeObjectiveExpression(ge.a(), 1, varToIndex, eqVarToIndex,
                                inObjective, positiveInObj, negativeInObj, objSignSum, objValCosts);
                    }
                }
            }
        }

        int[] polarity = new int[n];
        for (int i = 0; i < n; i++) {
            polarity[i] = Long.signum(objSignSum[i]);
        }

        // Identify acyclic functionally defined variables
        boolean[] defined = computeAcyclicDefinedVariables(n, functionalDefs);

        // Identify degree-<=1 objective slack variables (only if there exist higher-degree primary variables)
        boolean hasHighDegreeNonDefined = false;
        for (int i = 0; i < n; i++) {
            if (!defined[i] && ctrDegree[i] > 1) {
                hasHighDegreeNonDefined = true;
                break;
            }
        }

        boolean[] secondary = new boolean[n];
        int primaryCount = 0;
        for (int i = 0; i < n; i++) {
            if (defined[i]) {
                secondary[i] = true;
            } else if (hasHighDegreeNonDefined && ctrDegree[i] <= 1 && inObjective[i]) {
                secondary[i] = true;
            } else if (hasHighDegreeNonDefined && ctrDegree[i] == 0) {
                secondary[i] = true;
            } else {
                primaryCount++;
            }
        }

        // Guard: never allow all variables to be secondary
        if (primaryCount == 0) {
            Arrays.fill(secondary, false);
            primaryCount = n;
        }

        int[] primary = new int[primaryCount];
        int[] sec = new int[n - primaryCount];
        int pIdx = 0;
        int sIdx = 0;
        for (int i = 0; i < n; i++) {
            if (secondary[i]) {
                sec[sIdx++] = i;
            } else {
                primary[pIdx++] = i;
            }
        }

        // Identify strictly dominated degree-<=1 Table/unconstrained objective slack variables
        List<Integer> domSlacks = new ArrayList<>();
        if (sec.length > 0) {
            for (int idx : sec) {
                if (defined[idx]) continue;
                if (ctrDegree[idx] == 0 && objValCosts[idx] == null && !(positiveInObj[idx] && negativeInObj[idx])) {
                    domSlacks.add(idx);
                    continue;
                }
                if (objective == null || !isLinearObjective) continue;
                if (!inObjective[idx] || polarity[idx] == 0 || objValCosts[idx] != null) continue;
                if (positiveInObj[idx] && negativeInObj[idx]) continue;
                if (ctrDegree[idx] == 1 && singleConstraintOfVar[idx] instanceof Table table && table.starred().isEmpty()) {
                    // Check that idx appears once in table.x() and all other vars in table.x() are primary variables
                    IntExpression[] tx = table.x();
                    int occurrences = 0;
                    boolean allOthersPrimary = true;
                    for (IntExpression expr : tx) {
                        Integer vIdx = lookupVar(expr, varToIndex, eqVarToIndex);
                        if (vIdx == null) {
                            allOthersPrimary = false;
                            break;
                        }
                        if (vIdx == idx) {
                            occurrences++;
                        } else if (secondary[vIdx]) {
                            allOthersPrimary = false;
                            break;
                        }
                    }
                    if (occurrences == 1 && allOthersPrimary) {
                        domSlacks.add(idx);
                    }
                }
            }
        }
        int[] domSlackArray = domSlacks.stream().mapToInt(Integer::intValue).toArray();

        // Build compact neighbor arrays sorted by descending coupling weight
        int[][] adj = new int[n][];
        for (int i = 0; i < n; i++) {
            Map<Integer, Double> map = neighborWeights[i];
            if (map == null || map.isEmpty()) {
                adj[i] = new int[0];
            } else {
                List<Map.Entry<Integer, Double>> entries = new ArrayList<>(map.entrySet());
                entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
                int limit = Math.min(MAX_NEIGHBORS_PER_VAR, entries.size());
                int[] arr = new int[limit];
                for (int k = 0; k < limit; k++) {
                    arr[k] = entries.get(k).getKey();
                }
                adj[i] = arr;
            }
        }

        return new ModelStructureAnalyzer(
                n, primary, sec, secondary, domSlackArray,
                ctrDegree, structWeights, polarity, objValCosts, adj);
    }

    private static void tryExtractBinPackingDefinitions(
            BinPacking bp,
            IdentityHashMap<IntExpression, Integer> varToIndex,
            Map<IntExpression, Integer> eqVarToIndex,
            boolean[] hasCandidateDef,
            List<int[]> functionalDefs) {
        if (bp.loads() == null || bp.x() == null) return;
        int[] deps = collectDecisionVarIndices(Arrays.asList(bp.x()), varToIndex, eqVarToIndex);
        if (deps.length == 0) return;
        for (IntExpression load : bp.loads()) {
            Integer target = lookupVar(load, varToIndex, eqVarToIndex);
            if (target != null && !hasCandidateDef[target] && !contains(deps, target)) {
                hasCandidateDef[target] = true;
                int[] record = new int[deps.length + 1];
                record[0] = target;
                System.arraycopy(deps, 0, record, 1, deps.length);
                functionalDefs.add(record);
            }
        }
    }

    private static void tryExtractTableFunctionalDefinition(
            Table table,
            IdentityHashMap<IntExpression, Integer> varToIndex,
            Map<IntExpression, Integer> eqVarToIndex,
            boolean[] hasCandidateDef,
            List<int[]> functionalDefs) {
        IntExpression[] tx = table.x();
        int[][] tuples = table.array();
        if (tx == null || tuples == null || tx.length < 2 || tx.length > 3 || tuples.length == 0 || tuples.length > 50_000) {
            return;
        }
        int arity = tx.length;
        Integer target = lookupVar(tx[arity - 1], varToIndex, eqVarToIndex);
        if (target == null || hasCandidateDef[target]) {
            return;
        }
        List<Integer> depList = new ArrayList<>(arity - 1);
        for (int i = 0; i < arity - 1; i++) {
            Integer depIdx = lookupVar(tx[i], varToIndex, eqVarToIndex);
            if (depIdx != null) {
                if (depIdx.equals(target)) return;
                depList.add(depIdx);
            } else {
                int[] subDeps = extractExpressionVars(tx[i], varToIndex, eqVarToIndex);
                if (subDeps.length == 0 && !tx[i].isFixed()) return;
                for (int sd : subDeps) {
                    if (sd == target) return;
                    depList.add(sd);
                }
            }
        }
        if (depList.isEmpty()) return;

        // Verify that (col_0, ..., col_{arity-2}) uniquely determines col_{arity-1}
        if (arity == 2) {
            Map<Integer, Integer> map = new HashMap<>(tuples.length * 2);
            for (int[] row : tuples) {
                Integer prev = map.putIfAbsent(row[0], row[1]);
                if (prev != null && prev != row[1]) {
                    return;
                }
            }
        } else {
            Map<Long, Integer> map = new HashMap<>(tuples.length * 2);
            for (int[] row : tuples) {
                long key = (((long) row[0]) << 32) | (row[1] & 0xffffffffL);
                Integer prev = map.putIfAbsent(key, row[2]);
                if (prev != null && prev != row[2]) {
                    return;
                }
            }
        }

        hasCandidateDef[target] = true;
        int[] record = new int[depList.size() + 1];
        record[0] = target;
        for (int i = 0; i < depList.size(); i++) {
            record[i + 1] = depList.get(i);
        }
        functionalDefs.add(record);
    }

    private static void tryExtractFunctionalDefinition(
            IntExpression a,
            IntExpression b,
            IdentityHashMap<IntExpression, Integer> varToIndex,
            Map<IntExpression, Integer> eqVarToIndex,
            boolean[] hasCandidateDef,
            List<int[]> functionalDefs) {
        Integer idxA = lookupVar(a, varToIndex, eqVarToIndex);
        Integer idxB = lookupVar(b, varToIndex, eqVarToIndex);
        if (idxA != null && idxB == null && isCompoundFunctionalExpr(b)) {
            int target = idxA;
            if (!hasCandidateDef[target]) {
                int[] deps = extractExpressionVars(b, varToIndex, eqVarToIndex);
                if (deps.length > 0 && !contains(deps, target)) {
                    hasCandidateDef[target] = true;
                    int[] record = new int[deps.length + 1];
                    record[0] = target;
                    System.arraycopy(deps, 0, record, 1, deps.length);
                    functionalDefs.add(record);
                }
            }
        } else if (idxB != null && idxA == null && isCompoundFunctionalExpr(a)) {
            int target = idxB;
            if (!hasCandidateDef[target]) {
                int[] deps = extractExpressionVars(a, varToIndex, eqVarToIndex);
                if (deps.length > 0 && !contains(deps, target)) {
                    hasCandidateDef[target] = true;
                    int[] record = new int[deps.length + 1];
                    record[0] = target;
                    System.arraycopy(deps, 0, record, 1, deps.length);
                    functionalDefs.add(record);
                }
            }
        }
    }

    private static boolean isCompoundFunctionalExpr(IntExpression expr) {
        return !(expr instanceof IntVar)
                && !(expr instanceof CstOffset)
                && !(expr instanceof UnaryMinus);
    }

    private static boolean contains(int[] arr, int val) {
        for (int x : arr) {
            if (x == val) return true;
        }
        return false;
    }

    private static boolean[] computeAcyclicDefinedVariables(int n, List<int[]> functionalDefs) {
        boolean[] isDefined = new boolean[n];
        if (functionalDefs.isEmpty()) {
            return isDefined;
        }
        int[][] depsOf = new int[n][];
        boolean[] candidate = new boolean[n];
        for (int[] def : functionalDefs) {
            int target = def[0];
            candidate[target] = true;
            depsOf[target] = Arrays.copyOfRange(def, 1, def.length);
        }

        // Iteratively resolve variables whose dependencies are either non-candidates or already resolved
        boolean[] resolved = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (!candidate[i]) {
                resolved[i] = true;
            }
        }

        boolean progress = true;
        while (progress) {
            progress = false;
            for (int[] def : functionalDefs) {
                int target = def[0];
                if (resolved[target]) continue;
                boolean allDepsReady = true;
                for (int k = 1; k < def.length; k++) {
                    if (!resolved[def[k]]) {
                        allDepsReady = false;
                        break;
                    }
                }
                if (allDepsReady) {
                    resolved[target] = true;
                    isDefined[target] = true;
                    progress = true;
                }
            }
        }
        return isDefined;
    }

    private static IntExpression extractObjectiveExpression(Objective objective) {
        if (objective == null) return null;
        return switch (objective) {
            case Minimization min -> min.expr();
            case Maximization max -> max.expr();
            case SharedMinimization min -> min.expr;
            case SharedMaximization max -> max.expr;
            default -> null;
        };
    }

    private static int extractObjectiveDirection(Objective objective) {
        if (objective == null) return 0;
        return switch (objective) {
            case Minimization ignored -> -1;
            case SharedMinimization ignored -> -1;
            case Maximization ignored -> 1;
            case SharedMaximization ignored -> 1;
            default -> 0;
        };
    }

    private static boolean analyzeObjectiveExpression(
            IntExpression expr,
            long weightSign,
            IdentityHashMap<IntExpression, Integer> varToIndex,
            Map<IntExpression, Integer> eqVarToIndex,
            boolean[] inObjective,
            boolean[] positiveInObj,
            boolean[] negativeInObj,
            long[] objSignSum,
            long[][] objValCosts) {
        if (expr == null || weightSign == 0) {
            return true;
        }
        Integer leafIdx = lookupVar(expr, varToIndex, eqVarToIndex);
        if (leafIdx != null) {
            inObjective[leafIdx] = true;
            objSignSum[leafIdx] += weightSign;
            if (weightSign > 0) positiveInObj[leafIdx] = true;
            if (weightSign < 0) negativeInObj[leafIdx] = true;
            return true;
        }
        return switch (expr) {
            case Sum s -> {
                boolean linear = true;
                for (IntExpression sub : s.subexprs()) {
                    linear &= analyzeObjectiveExpression(sub, weightSign, varToIndex, eqVarToIndex,
                            inObjective, positiveInObj, negativeInObj, objSignSum, objValCosts);
                }
                yield linear;
            }
            case WeightedSum ws -> {
                boolean linear = true;
                IntExpression[] subs = ws.subexprs();
                int[] w = ws.weights();
                for (int i = 0; i < subs.length; i++) {
                    if (w[i] != 0) {
                        linear &= analyzeObjectiveExpression(subs[i], weightSign * w[i], varToIndex, eqVarToIndex,
                                inObjective, positiveInObj, negativeInObj, objSignSum, objValCosts);
                    }
                }
                yield linear;
            }
            case CstMul cm -> analyzeObjectiveExpression(cm.expr(), weightSign * cm.mul(), varToIndex, eqVarToIndex,
                    inObjective, positiveInObj, negativeInObj, objSignSum, objValCosts);
            case UnaryMinus um -> analyzeObjectiveExpression(um.expr(), -weightSign, varToIndex, eqVarToIndex,
                    inObjective, positiveInObj, negativeInObj, objSignSum, objValCosts);
            case CstOffset co -> analyzeObjectiveExpression(co.expr(), weightSign, varToIndex, eqVarToIndex,
                    inObjective, positiveInObj, negativeInObj, objSignSum, objValCosts);
            case Element1D el -> {
                Integer idx = lookupVar(el.index(), varToIndex, eqVarToIndex);
                if (idx != null) {
                    inObjective[idx] = true;
                    int[] table = el.array();
                    if (objValCosts[idx] == null) {
                        objValCosts[idx] = new long[table.length];
                    }
                    long[] costs = objValCosts[idx];
                    int len = Math.min(costs.length, table.length);
                    // Lower cost in objValCosts means better for the objective
                    for (int v = 0; v < len; v++) {
                        costs[v] += (-weightSign) * (long) table[v];
                    }
                } else {
                    for (int vIdx : extractExpressionVars(el, varToIndex, eqVarToIndex)) {
                        inObjective[vIdx] = true;
                    }
                }
                yield idx != null;
            }
            case Max m -> {
                for (IntExpression sub : m.exprs()) {
                    analyzeObjectiveExpression(sub, weightSign, varToIndex, eqVarToIndex,
                            inObjective, positiveInObj, negativeInObj, objSignSum, objValCosts);
                }
                yield false;
            }
            case Min m -> {
                for (IntExpression sub : m.exprs()) {
                    analyzeObjectiveExpression(sub, weightSign, varToIndex, eqVarToIndex,
                            inObjective, positiveInObj, negativeInObj, objSignSum, objValCosts);
                }
                yield false;
            }
            default -> {
                int[] varsInExpr = extractExpressionVars(expr, varToIndex, eqVarToIndex);
                for (int vIdx : varsInExpr) {
                    inObjective[vIdx] = true;
                }
                yield false;
            }
        };
    }

    private static Integer lookupVar(
            Expression expr,
            IdentityHashMap<IntExpression, Integer> varToIndex,
            Map<IntExpression, Integer> eqVarToIndex) {
        if (!(expr instanceof IntExpression ie)) {
            return null;
        }
        Integer idx = varToIndex.get(ie);
        if (idx != null) {
            return idx;
        }
        return eqVarToIndex.get(ie);
    }

    private static int[] extractConstraintScope(
            Constraint c,
            IdentityHashMap<IntExpression, Integer> varToIndex,
            Map<IntExpression, Integer> eqVarToIndex) {
        return collectDecisionVarIndices(c.scope(), varToIndex, eqVarToIndex);
    }

    private static int[] extractExpressionVars(
            Expression root,
            IdentityHashMap<IntExpression, Integer> varToIndex,
            Map<IntExpression, Integer> eqVarToIndex) {
        return collectDecisionVarIndices(List.of(root), varToIndex, eqVarToIndex);
    }

    private static int[] collectDecisionVarIndices(
            Collection<? extends Expression> roots,
            IdentityHashMap<IntExpression, Integer> varToIndex,
            Map<IntExpression, Integer> eqVarToIndex) {
        if (roots == null || roots.isEmpty()) {
            return new int[0];
        }
        Set<Integer> involved = new HashSet<>();
        IdentityHashMap<Expression, Boolean> seen = new IdentityHashMap<>();
        Deque<Expression> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            Expression expr = stack.removeLast();
            if (seen.putIfAbsent(expr, Boolean.TRUE) != null) {
                continue;
            }
            Integer idx = lookupVar(expr, varToIndex, eqVarToIndex);
            if (idx != null) {
                involved.add(idx);
            }
            Collection<? extends Expression> children = expr.subexpressions();
            if (children != null) {
                for (Expression child : children) {
                    if (child != null) {
                        stack.addLast(child);
                    }
                }
            }
        }
        if (involved.isEmpty()) {
            return new int[0];
        }
        int[] res = new int[involved.size()];
        int p = 0;
        for (int v : involved) {
            res[p++] = v;
        }
        return res;
    }

    public int nVars() {
        return nVars;
    }

    public int[] primaryIndices() {
        return primaryIndices;
    }

    public int[] secondaryIndices() {
        return secondaryIndices;
    }

    public boolean isSecondary(int varIndex) {
        return isSecondary[varIndex];
    }

    public int[] dominantSlackIndices() {
        return dominantSlackIndices;
    }

    public int constraintDegree(int varIndex) {
        return constraintDegree[varIndex];
    }

    public double structuralWeight(int varIndex) {
        return structuralWeights[varIndex];
    }

    public int objectivePolarity(int varIndex) {
        return objectivePolarity[varIndex];
    }

    public long[] objectiveValueCosts(int varIndex) {
        return objectiveValueCosts[varIndex];
    }

    public int[] neighbors(int varIndex) {
        return neighbors[varIndex];
    }
}
