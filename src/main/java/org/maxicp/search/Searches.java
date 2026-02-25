/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.search;


import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.*;
import org.maxicp.modeling.algebra.bool.Eq;
import org.maxicp.modeling.algebra.bool.NotEq;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.SymbolicModel;
import org.maxicp.util.TriFunction;
import org.maxicp.util.exception.InconsistencyException;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

/**
 * Factory for search procedures.
 *
 * @see DFSearch
 */
public final class Searches {

    private Searches() {
        throw new UnsupportedOperationException();
    }

    /**
     * Constant that should be returned
     * to notify the solver that there are no branches
     * to create any more and that the current state should
     * be considered as a solution.
     *
     * @see DFSearch
     */
    public static final Runnable[] EMPTY = new Runnable[0];
    public static final SymbolicBranching[] EMPTY_SYMB = new SymbolicBranching[0];

    /**
     * @param branches the ordered closures for the child branches
     *                 ordered from left to right in the depth first search.
     * @return an array with those branches
     * @see DFSearch
     */
    public static Runnable[] branch(Runnable... branches) {
        return branches;
    }

    public static SymbolicModel[] branch(SymbolicModel... branches) {
        return branches;
    }

    /**
     * Minimum selector.
     * <p>Example of usage.
     * <pre>
     * {@code
     * IntVar xs = selectMin(x,xi -> xi.size() > 1,xi -> xi.size());
     * }
     * </pre>
     *
     * @param x   the array on which the minimum value is searched
     * @param p   the predicate that filters the element eligible for selection
     * @param f   the evaluation function that returns a comparable when applied on an element of x
     * @param <T> the type of the elements in x, for instance {@link org.maxicp.modeling.IntVar}
     * @param <N> the type on which the minimum is computed, for instance {@link Integer}
     * @return the minimum element in x that satisfies the predicate p
     * or null if no element satisfies the predicate.
     */
    public static <T, N extends Comparable<N>> T selectMin(T[] x, Predicate<T> p, Function<T, N> f) {
        T sel = null;
        for (T xi : x) {
            if (p.test(xi)) {
                sel = sel == null || f.apply(xi).compareTo(f.apply(sel)) < 0 ? xi : sel;
            }
        }
        return sel;
    }

    /**
     * Minimum selector.
     * <p>Example of usage.
     * <pre>
     *     {@code
     *     int i = selectMin(
     *       IntStream.range(0, n).boxed().toList(),
     *       qi -> q[qi].size() > 1,
     *       qi -> q[qi].size()
     *     );
     *     }
     * </pre>
     *
     * @param items the iterable on which the minimum value is searched
     * @param p the predicate that filters the element eligible for selection
     * @param f the evaluation function that returns a comparable when applied on an element of items
     * @return the minimum element in items that satisfies the predicate p
     * @param <T>
     * @param <N>
     *
     */
    public static <T, N extends Comparable<N>> T selectMin(
            Iterable<T> items, Predicate<T> p, Function<T, N> f) {
        T sel = null;
        for (T xi : items) {
            if (p.test(xi)) {
                sel = sel == null || f.apply(xi).compareTo(f.apply(sel)) < 0 ? xi : sel;
            }
        }
        return sel;
    }

    /**
     * Minimum selector.
     * <p>Example of usage.
     * <pre>
     * {@code
     * IntVar xs = selectMin(x,n, xi -> xi.size() > 1,xi -> xi.size());
     * }
     * </pre>
     *
     * @param x   the array on which the minimum value is searched
     * @param n   the n first elements from x that must be considered
     * @param p   the predicate that filters the element eligible for selection
     * @param f   the evaluation function that returns a comparable when applied on an element of x
     * @param <T> the type of the elements in x, for instance {@link org.maxicp.modeling.IntVar}
     * @param <N> the type on which the minimum is computed, for instance {@link Integer}
     * @return the minimum element in x that satisfies the predicate p
     * or null if no element satisfies the predicate.
     */
    public static <T, N extends Comparable<N>> T selectMin(T[] x, int n, Predicate<T> p, Function<T, N> f) {
        T sel = null;
        Iterable<T> it = Arrays.stream(x).limit(n)::iterator;
        for (T xi : it) {
            if (p.test(xi)) {
                sel = sel == null || f.apply(xi).compareTo(f.apply(sel)) < 0 ? xi : sel;
            }
        }
        return sel;
    }

    /**
     * Minimum selector.
     * <p>Example of usage.
     * <pre>
     * {@code
     * OptionalInt i = selectMin(values,n, i -> x[i].isFixed() > 1, i -> x[i].size());
     * }
     * </pre>
     *
     * @param x the array on which the minimum value is searched
     * @param n the n first elements from x that must be considered
     * @param p the predicate that filters the element eligible for selection
     * @param f the evaluation function that returns a comparable when applied on an element of x
     * @return the minimum element in x that satisfies the predicate p
     * or null if no element satisfies the predicate.
     */
    public static <N extends Comparable<N>> OptionalInt selectMin(int[] x, int n, Predicate<Integer> p, Function<Integer, N> f) {
        return Arrays.stream(x).limit(n).filter(p::test).reduce((i, j) -> f.apply(i).compareTo(f.apply(j)) < 0 ? i : j);
    }

    /**
     * Binary Branching with custom variable heuristic and natural value ordering.
     *
     * @param variableSelector returns the next variable to bind, null if all variables are fixed
     * @return a static branching strategy, the minimum value of the variable domain is attempted on the left,
     *          and removed on the right
     */
    public static Supplier<Runnable[]> heuristicBinary(Supplier<IntExpression> variableSelector) {
        return () -> {
            IntExpression xs = variableSelector.get();
            if (xs == null)
                return EMPTY;
            else {
                ModelProxy model = xs.getModelProxy();
                int v = xs.min();
                return branch(() -> model.add(new Eq(xs, v)),
                        () -> model.add(new NotEq(xs, v)));
            }
        };
    }

    /**
     * Binary Branching with custom variable and value heuristics.
     *
     * @param variableSelector the variable heuristic, returns the variable on which the branching is applied
     *                         null if all variables are fixed
     * @param valueSelector given the variable,
     *                       returns the value assigned in the left branch,
     *                       and removed fin the right branch
     */
    public static Supplier<Runnable[]> heuristicBinary(Supplier<IntExpression> variableSelector,
                                                       Function<IntExpression, Integer> valueSelector) {

        return () -> {
            IntExpression xs = variableSelector.get();
            if (xs == null)
                return EMPTY;
            else {
                ModelProxy model = xs.getModelProxy();
                int v = valueSelector.apply(xs);
                return branch(() -> model.add(new Eq(xs, v)),
                        () -> model.add(new NotEq(xs, v)));
            }
        };
    }

    /**
     * N-ary Branching with custom variable heuristic and natural value ordering.
     *
     * @param variableSelector returns the variable on which the n-ary branching is applied
     *                          null if all variables are fixed
     * @return an n-ary branching strategy with the variable heuristic and natural
     *          value ordering (increasing order).
     */
    public static Supplier<Runnable[]> heuristicNary(Supplier<IntExpression> variableSelector) {
        return () -> {
            IntExpression xs = variableSelector.get();
            if (xs == null)
                return EMPTY;
            else {
                ModelProxy model = xs.getModelProxy();
                // create one branch for each value in increasing order
                ArrayList<Runnable> branches  = new ArrayList<>();
                for (int v = xs.min(); v < xs.max(); v++) {
                    if (xs.contains(v)) {
                        int value = v;
                        branches.add(() -> model.add(new Eq(xs, value)));
                    }
                }
                return branch(branches.toArray(new Runnable[0]));
            }
        };
    }

    /**
     * N-ary Branching with custom variable and value heuristics.
     *
     * @param variableSelector returns the variable on which the n-ary branching is applied
     * @param valueHeuristic the branches for each value are ordered according to the value
     *                       of this function (in increasing order).
     *                       This function is called once for each value in the variable domain to
     *                       sort them according to the heuristic before creating the branches in the sorted order.
     * @return an n-ary branching strategy
     */
    public static Supplier<Runnable[]> heuristicNary(Supplier<IntExpression> variableSelector,
                                                     Function<Integer, Integer> valueHeuristic) {
        return () -> {
            IntExpression xs = variableSelector.get();
            if (xs == null)
                return EMPTY;
            else {
                ModelProxy model = xs.getModelProxy();
                // create one branch for each value sorted by the valueHeuristic
                int[] values = new int[xs.size()];
                xs.fillArray(values);
                Integer[] boxed = Arrays.stream(values)
                        .boxed()
                        .toArray(Integer[]::new);
                Arrays.sort(boxed, Comparator.comparingInt(valueHeuristic::apply));
                ArrayList<Runnable> branches  = new ArrayList<>();
                for (int v : boxed) {
                    int value = v;
                    branches.add(() -> model.add(new Eq(xs, v)));
                }
                return branch(branches.toArray(new Runnable[0]));
            }
        };
    }

    /**
     * N-ary Branching with static variable ordering and natural value ordering.
     *
     * @param xs the variable array to fix
     * @return an n-ary branching strategy with static variable ordering and
     *             natural value ordering (increasing order).
     */
    public static Supplier<Runnable[]> staticOrderNary(IntExpression... xs) {
        return heuristicNary(staticOrderVariableSelector(xs));
    }


    /**
     * Binary Branching with static variable ordering and natural value ordering.
     *
     * @param xs the variable array to fix
     * @return a binary static branching strategy, min value on the left, remove it on the right
     */
    public static Supplier<Runnable[]> staticOrderBinary(IntExpression... xs) {
        return heuristicBinary(staticOrderVariableSelector(xs));
    }

    /**
     * Binary Branching with static variable ordering and custom value heuristic.
     *
     * @param valueSelector the value heuristic, given a variable, returns the value to which
     *                      it must be assigned on the left branch, and removed on the right branch
     * @param xs the variable array to fix,
     * @return a binary static branching strategy on the variable,
     *        but the value is selected by the heuristic on the left, removed on the right branches
     */
    public static Supplier<Runnable[]> staticOrderBinary(Function<IntExpression, Integer> valueSelector,
                                                         IntExpression... xs) {
        return heuristicBinary(staticOrderVariableSelector(xs), valueSelector);
    }


    /**
     * First-Fail Binary search strategy.
     * Selects the first not fixed variable with smallest domain.
     * Then it creates two branches. The left branch
     * assigning the variable to its minimum value.
     * The right branch removing this minimum value from the domain.
     *
     * @param x the variable on which the first fail strategy is applied.
     * @return a first-fail branching strategy
     */
    public static Supplier<Runnable[]> firstFailBinary(IntExpression... x) {
        return heuristicBinary(minDomVariableSelector(x));
    }

    /**
     * First-Fail N-Ary search strategy.
     * It selects the first variable with a domain larger than one.
     * Then it creates one branch for each value in increasing order.
     *
     * @param x the variable on which the first fail strategy is applied.
     * @return a first-fail n-ary branching strategy
     */
    public static Supplier<Runnable[]> firstFailNary(IntExpression... x) {
        return heuristicNary(minDomVariableSelector());
    }

    /**
     * Sequential Search combinator that linearly
     * considers a list of branching generator.
     * One branching of this list is executed
     * when all the previous ones are exhausted (they return an empty array).
     *
     * @param choices the branching schemes considered sequentially in the sequential by
     *                path in the search tree
     * @return a branching scheme implementing the sequential search
     * @see Sequencer
     */
    public static Supplier<Runnable[]> and(Supplier<Runnable[]>... choices) {
        return new Sequencer(choices);
    }

    /**
     * Limited Discrepancy Search combinator
     * that limits the number of right decisions
     *
     * @param branching      a branching scheme
     * @param maxDiscrepancy a discrepancy limit (non negative number)
     * @return a branching scheme that cuts off any path accumulating
     * a discrepancy beyond the limit maxDiscrepancy
     * @see LimitedDiscrepancyBranching
     */
    public static Supplier<Runnable[]> limitedDiscrepancy(Supplier<Runnable[]> branching, int maxDiscrepancy) {
        return new LimitedDiscrepancyBranching(branching, maxDiscrepancy);
    }

    /**
     * It selects the first not fixed variable with the smallest domain.
     *
     * @param xs the variable array to fix
     * @return a supplier that returns the first not fixed variable with the smallest domain,
     *         null if all variables are fixed
     */
    public static Supplier<IntExpression> minDomVariableSelector(IntExpression... xs) {
        return () -> {
            return selectMin(xs, xi -> !xi.isFixed(), IntExpression::size);
        };
    }

    public static Supplier<IntExpression> staticOrderVariableSelector(IntExpression... xs) {
        return () -> {
            for (IntExpression xi : xs) {
                if (!xi.isFixed()) {
                    return xi;
                }
            }
            return null;
        };
    }

    /**
     * Last conflict heuristic
     * Attempts to branch first on the last variable that caused an Inconsistency
     * <p>
     * Lecoutre, C., Saïs, L., Tabary, S.,  Vidal, V. (2009).
     * Reasoning from last conflict (s) in constraint programming.
     * Artificial Intelligence, 173(18), 1592-1614.
     *
     * @param variableSelector returns the next variable to bind
     * @param valueSelector    given a variable, returns the value to which
     *                         it must be assigned on the left branch (and excluded on the right)
     */
    public static Supplier<Runnable[]> lastConflict(Supplier<IntExpression> variableSelector, Function<IntExpression, Integer> valueSelector) {
        AtomicReference<IntExpression> lastConflictVariable = new AtomicReference<>(null);
        return () -> {
            IntExpression xs;
            if (lastConflictVariable.get() == null) {
                xs = variableSelector.get();
            } else {
                xs = lastConflictVariable.get();
                lastConflictVariable.set(null);
            }
            if (xs == null)
                return EMPTY;
            else {
                int v = valueSelector.apply(xs);
                Runnable a = () -> {
                    try {
                        xs.getModelProxy().add(Factory.eq(xs, v));
                    } catch (InconsistencyException e) {
                        lastConflictVariable.set(xs);
                        throw e;
                    }
                };
                Runnable b = () -> {
                    try {
                        xs.getModelProxy().add(Factory.neq(xs, v));
                    } catch (InconsistencyException e) {
                        lastConflictVariable.set(xs);
                        throw e;
                    }
                };
                return branch(a, b);
            }
        };
    }

    /**
     * Conflict Ordering Search
     * <p>
     * Gay, S., Hartert, R., Lecoutre, C.,  Schaus, P. (2015).
     * Conflict ordering search for scheduling problems.
     * In International conference on principles and practice of constraint programming (pp. 140-148).
     * Springer.
     *
     * @param variableSelector returns the next variable to bind (fall back euristic)
     * @param valueSelector    given a variable, returns the value to which
     *                         it must be assigned on the left branch (and excluded on the right)
     */
    public static Supplier<Runnable[]> conflictOrderingSearch(Supplier<IntExpression> variableSelector, Function<IntExpression, Integer> valueSelector) {
        HashMap<IntExpression, Integer> stamp = new HashMap<>();
        return () -> {
            int maxvalue = Integer.MIN_VALUE;
            IntExpression maxIntVar = null;
            for (Map.Entry<IntExpression, Integer> e : stamp.entrySet()) {
                if (e.getValue() > maxvalue && !e.getKey().isFixed()) {
                    maxvalue = e.getValue();
                    maxIntVar = e.getKey();
                }
            }
            IntExpression xs;
            if (maxIntVar == null) {
                xs = variableSelector.get();
                if (xs != null) stamp.put(xs, 0);
            } else {
                xs = maxIntVar;
                stamp.put(xs, stamp.get(xs) + 1);
            }
            if (xs == null)
                return EMPTY;
            else {
                int v = valueSelector.apply(xs);
                Runnable a = () -> {
                    xs.getModelProxy().add(Factory.eq(xs, v));
                };
                Runnable b = () -> {
                    xs.getModelProxy().add(Factory.neq(xs, v));
                };
                return branch(a, b);
            }
        };
    }

    /**
     * Conflict Ordering Search with default variable selector (first-fail min dom)
     * and value selector (min value).
     * <p>
     * Gay, S., Hartert, R., Lecoutre, C.,  Schaus, P. (2015).
     * Conflict ordering search for scheduling problems.
     * In International conference on principles and practice of constraint programming (pp. 140-148).
     * Springer.
     *
     */
    public static Supplier<Runnable[]> conflictOrderingSearch(IntExpression... xs) {
        return conflictOrderingSearch(
                minDomVariableSelector(xs),
                xi -> xi.min()
        );
    }


    public static Supplier<Runnable[]> firstFailBinary(SeqVar... seqVars) {
        int nNodes = Arrays.stream(seqVars).map(SeqVar::nNode).max(Integer::compareTo).get();
        int[] nodes = new int[nNodes];
        return () -> {
            SeqVar seqVar = selectMin(seqVars, s -> !s.isFixed(), s -> s.nNode(MEMBER));
            if (seqVar == null)
                return EMPTY;
            int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
            int branchingNode = nodes[0];
            int minInsert = seqVar.nInsert(branchingNode);
            for (int i = 1; i < nInsertable; i++) {
                int node = nodes[i];
                int nInsert = seqVar.nInsert(node);
                if (nInsert < minInsert && (nInsert > 0 || (nInsert == 0 && !seqVar.isNode(node, REQUIRED)))) {
                    minInsert = nInsert;
                    branchingNode = node;
                }
            }
            int branchingNodeFinal = branchingNode;
            boolean isRequired = seqVar.isNode(branchingNodeFinal, REQUIRED);
            int nPred = seqVar.fillInsert(branchingNodeFinal, nodes);
            Runnable[] branching = new Runnable[nPred + (isRequired ? 0 : 1)];
            for (int i = 0; i < nPred; i++) {
                int pred = nodes[i];
                branching[i] = () -> seqVar.getModelProxy().add(insert(seqVar, pred, branchingNodeFinal));
            }
            if (!isRequired) {
                branching[nPred] = () -> seqVar.getModelProxy().add(exclude(seqVar, branchingNodeFinal));
            }
            return branching;
        };
    }

    /**
     * Generic node selector for sequence variables.
     * <p>Example of usage.
     * <pre>
     * {@code
     * OptionalInt node = nodeSelector(routes, nodes, (seqvar, node) -> seqvar.nInsert(node));
     * }
     * </pre>
     *
     * @param seqVars  sequences variables in the problem
     * @param nodes    nodes over which the sequence variables are related
     * @param nodeCost heuristic cost used to select the node, summed over all sequences
     * @return node with the minimum cost, summed over all sequence variables
     */
    public static OptionalInt nodeSelector(SeqVar[] seqVars, int[] nodes, BiFunction<SeqVar, Integer, Integer> nodeCost) {
        return nodeSelector(seqVars, nodes, nodeCost, Integer::sum);
    }

    /**
     * Generic node selector for sequence variables.
     * <p>Example of usage.
     * <pre>
     * {@code
     * OptionalInt node = nodeSelector(routes, nodes, (seqvar, node) -> seqvar.nInsert(node), (c1, c2) -> c1 + c2);
     * }
     * </pre>
     *
     * @param seqVars      sequences variables in the problem
     * @param nodes        nodes over which the sequence variables are related
     * @param nodeCost     heuristic cost used to select the node, summed over all sequences
     * @param nodeCostAggr aggregator to combine the heuristic cost of two sequence variables
     * @return node with the minimum cost, summed over all sequence variables
     */
    public static OptionalInt nodeSelector(SeqVar[] seqVars, int[] nodes, BiFunction<SeqVar, Integer, Integer> nodeCost, BiFunction<Integer, Integer, Integer> nodeCostAggr) {
        OptionalInt bestNode = OptionalInt.empty();
        int bestCost = 0;
        for (int node : nodes) {
            int cost = 0;
            boolean skip = true; // consider only nodes that are insertable: the remaining ones are already decided
            for (SeqVar seqVar : seqVars) {
                if (seqVar.isNode(node, INSERTABLE)) {
                    skip = false;
                    cost = nodeCostAggr.apply(cost, nodeCost.apply(seqVar, node));
                }
            }
            if (!skip) {
                if (cost < bestCost || bestNode.isEmpty()) {
                    bestCost = cost;
                    bestNode = OptionalInt.of(node);
                }
            }
        }
        return bestNode;
    }

    /**
     * Generates all branches inserting a node, sorted by a given detour cost.
     * <p>Example of usage.
     * <pre>
     * {@code
     * int[][] d = ... // distance matrix between nodes
     * Function<Integer, Runnable[]> branchGenerator = branchesInsertingNode(seqvar, (pred, node, succ) -> d[pred][node] + d[node][succ] - d[pred][succ]).get();
     * Runnable[] branches = branchGenerator.apply(node); // all branches inserting the given node
     * }
     * </pre>
     *
     * @param seqVars    sequence over which the node may be inserted
     * @param detourCost detour cost for inserting a node between a predecessor and a successor; lower values are tried first.
     *                   Arguments are {@code (pred, node succ)},
     *                   where {@code pred} is the node after which the insertion happen, {@code node} is the node to insert and {@code succ} the current node after pred
     * @return
     */
    public static Supplier<Function<Integer, Runnable[]>> branchesInsertingNode(SeqVar[] seqVars, TriFunction<Integer, Integer, Integer, Integer> detourCost) {
        // upper bound on the number of branches generated
        int nBranchesUpperBound = Arrays.stream(seqVars).mapToInt(SeqVar::nNode).sum();
        int[] insertions = new int[nBranchesUpperBound];
        Runnable[] branches = new Runnable[nBranchesUpperBound];
        Integer[] heuristicVal = new Integer[nBranchesUpperBound];
        Integer[] branchingRange = new Integer[nBranchesUpperBound];
        return () -> (node) -> {
            int branch = 0;
            for (SeqVar route : seqVars) {
                int nInsert = route.fillInsert(node, insertions);
                for (int j = 0; j < nInsert; j++) {
                    int pred = insertions[j]; // predecessor for the node
                    int succ = route.memberAfter(pred);
                    branchingRange[branch] = branch;
                    heuristicVal[branch] = detourCost.apply(pred, node, succ);
                    branches[branch++] = () -> route.getModelProxy().add(insert(route, pred, node));
                }
            }
            int nBranches = branch;
            Runnable[] branchesSorted = new Runnable[nBranches];
            Arrays.sort(branchingRange, 0, nBranches, Comparator.comparing(j -> heuristicVal[j]));
            for (branch = 0; branch < nBranches; branch++)
                branchesSorted[branch] = branches[branchingRange[branch]];
            return branchesSorted;
        };
    }

    public static Supplier<Function<Integer, Runnable[]>> branchesInsertingNode(SeqVar[] seqVars, int[][] distMatrix) {
        return branchesInsertingNode(seqVars, (pred, node, succ) -> distMatrix[pred][node] + distMatrix[node][succ] - distMatrix[pred][succ]);
    }

    /**
     * Bound-Impact value selector. Gives a value selector yielding the value leading to
     * the smallest decrease on the objective
     * <p>
     * This value selection strategy was introduced in:
     * Fages, J. G., Prud’Homme, C. Making the first solution good!
     * In 2017 IEEE 29th International Conference on Tools with Artificial Intelligence (ICTAI). IEEE.
     *
     * @param objective objective whose impact must be measured
     * @return value decreasing the less the objective lower bound. Null if no value is valid for the variable
     */
    public static Function<IntExpression, Integer> boundImpactValueSelector(IntExpression objective) {
        // black magic for java, so that the domain can be updated by the function supplied
        final int[][] domain = {new int[100]};
        return (IntExpression x) -> {
            if (x.isFixed())
                return null;
            int size = x.size();
            if (size > domain[0].length)
                domain[0] = new int[Math.max(domain.length * 2, size)]; // increase the size
            size = x.fillArray(domain[0]);
            Integer bestValue = null;
            int bestBound = Integer.MAX_VALUE;
            for (int i = 0; i < size; i++) {
                int value = domain[0][i];
                x.getModelProxy().getConcreteModel().getStateManager().saveState();
                try {
                    x.getModelProxy().add(Factory.eq(x, value));
                    int bound = objective.min();
                    if (bestValue == null || bound < bestBound || (bound == bestBound && value < bestValue)) {
                        bestValue = value;
                        bestBound = bound;
                    }
                } catch (InconsistencyException ignored) {
                }
                x.getModelProxy().getConcreteModel().getStateManager().restoreState();
            }
            if (bestValue == null) {
                // all values of x appear to be inconsistent
                throw InconsistencyException.INCONSISTENCY;
            }
            return bestValue;
        };
    }

    //TODO: adapt all the scheduling searches to modelling interface


    /**
     * The SetTimes branching search is a branching scheme for scheduling problems
     * with "regular" constraints between the tasks (e.g., precedence constraints).
     * The branching scheme is based on the following principle:
     * - Select the earliest start time among the non postponed tasks
     * - Branch on the start time of the task
     * (Le Pape, C., Couronne, P., Vergamini, D., and Gosselin, V. (1995). Time-versus-capacity compromises in project scheduling)
     *
     * @param intervals  tasks that must be decided
     * @param tieBreaker how to select the best task to branch on when several have the same earliest start time
     * @return set time branching
     */
    public static Supplier<Runnable[]> setTimes(IntervalVar[] intervals, IntFunction tieBreaker) {
        return new SetTimesModeling(intervals, tieBreaker);
    }

    public static Supplier<Runnable[]> setTimes(IntervalVar[] intervals) {
        return new SetTimesModeling(intervals);
    }

    public static Runnable[] branchOnStartMin(IntervalVar var) {
        int min = var.startMin();
        Runnable left = () -> {
            var.getModelProxy().add(start(var, min));
        };
        Runnable right = () -> {
            var.getModelProxy().add(startAfter(var, min + 1));
        };
        return new Runnable[]{left, right};
    }

    public static Runnable[] branchOnStatus(CPIntervalVar var) {
        if (var.isPresent() || var.isAbsent()) return EMPTY;
        Runnable left = () -> {
            var.setPresent();
            var.getSolver().fixPoint();
        };
        Runnable right = () -> {
            var.setAbsent();
            var.getSolver().fixPoint();
        };
        return new Runnable[]{left, right};
    }


    public static Supplier<Runnable[]> branchOnPresentStarts(IntervalVar... vars) {
        return () -> {
            IntervalVar notFixed = null;
            for (IntervalVar var : vars) {
                if (!var.isFixed() && var.isPresent()) {
                    notFixed = var;
                    break;
                }
            }
            if (notFixed == null) {
                return EMPTY;
            } else {
                return branchOnStartMin(notFixed);
            }
        };
    }

    public static Supplier<Runnable[]> branchOnStatus(CPIntervalVar... vars) {
        return () -> {
            CPIntervalVar notFixed = null;
            for (CPIntervalVar var : vars) {
                if (!var.isAbsent() && !var.isPresent()) {
                    notFixed = var;
                    break;
                }
            }
            if (notFixed == null) {
                return EMPTY;
            } else {
                return branchOnStatus(notFixed);
            }
        };
    }

    public static Supplier<Runnable[]> branchOnStatusThenStarts(CPIntervalVar... vars) {
        return () -> {
            CPIntervalVar notFixed = null;
            for (int i = 0; i < vars.length; i++) {
                if (!vars[i].isFixed()) {
                    notFixed = vars[i];
                    break;
                }
            }
            if (notFixed == null) {
                return EMPTY;
            } else {
                if (notFixed.isPresent()) return branchOnStartMin(notFixed);
                else return branchOnStatus(notFixed);
            }
        };
    }
}
