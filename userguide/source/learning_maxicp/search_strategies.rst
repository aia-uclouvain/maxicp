.. _maxicp_search:

**************
Search
**************

MaxiCP provides a flexible, compositional search framework.
The core engine is depth-first search (``DFSearch``) with branch-and-bound for optimization.

Source of the search package:
`org.maxicp.search <https://github.com/aia-uclouvain/maxicp/tree/main/src/main/java/org/maxicp/search/>`_

Depth-First Search with Closures
==================================

Following the design of MiniCP, the search in MaxiCP is defined by a *branching function*:
a ``Supplier`` of an array of ``Runnable`` closures, one per child branch.
An empty array signals a solution.

.. code-block:: java

    DFSearch search = makeDfs(cp, () -> {
        CPIntVar qs = selectMin(q,      // first-fail: pick the variable
            qi -> qi.size() > 1,        //   that is not yet fixed
            qi -> qi.size());           //   with the smallest domain
        if (qs == null) return EMPTY;   // all fixed -> solution found
        int v = qs.min();
        return branch(
            () -> cp.post(eq(qs, v)),   // left branch: assign v
            () -> cp.post(neq(qs, v))   // right branch: remove v
        );
    });

The ``branch`` method returns a ``Runnable[]``.
Each closure calls ``cp.post()`` which registers the constraint *and* immediately runs
a full fixed-point computation, propagating all consequences of the decision.
An ``InconsistencyException`` causes the search to backtrack.

Built-In Heuristics: Variable and Value Selectors
===================================================

``Searches`` offers a compositional alternative to writing branching closures by hand,
separating two orthogonal concerns:

1. A **variable selector** (``Supplier<IntExpression>``) — returns the next unfixed variable, or ``null`` when all are fixed.
2. A **value selector** (``Function<IntExpression, Integer>``) — given the variable, returns the value to try first.

**Binary branching** — ``heuristicBinary(varSel)`` or ``heuristicBinary(varSel, valSel)``

Left branch assigns ``x = v``; right branch removes it (``x ≠ v``).

.. code-block:: java

    // (a) most concise — built-in first-fail
    DFSearch s1 = makeDfs(cp, firstFailBinary(q));

    // (b) explicit variable selector, default value (min)
    DFSearch s2 = makeDfs(cp, heuristicBinary(minDomVariableSelector(q)));

    // (c) explicit variable AND value selectors
    DFSearch s3 = makeDfs(cp,
        heuristicBinary(minDomVariableSelector(q), x -> x.min()));

**N-ary branching** — ``heuristicNary(varSel)`` creates one child branch per domain value.
An overload accepts a scoring function ``h: int -> int`` and sorts values by increasing ``h(v)``.

.. code-block:: java

    // values tried in increasing order
    DFSearch s4 = makeDfs(cp, heuristicNary(minDomVariableSelector(q)));

    // values closer to n/2 tried first
    DFSearch s5 = makeDfs(cp,
        heuristicNary(minDomVariableSelector(q), v -> Math.abs(v - n / 2)));

**Built-in variable selectors**

- ``minDomVariableSelector(x)`` — first-fail (smallest domain).
- ``staticOrderVariableSelector(x)`` — first unfixed variable in array order.

Custom variable selectors are trivial to write:

.. code-block:: java

    Supplier<IntExpression> maxDom = () ->
        selectMin(q, qi -> !qi.isFixed(), qi -> -qi.size());
    DFSearch s6 = makeDfs(cp, heuristicBinary(maxDom));

Advanced Value Selection
=========================

- ``boundImpactValueSelector(objective)`` — for each candidate value, temporarily assigns it,
  measures the resulting objective bound, then backtracks. Returns the value that tightens
  the bound the least — biasing the search towards high-quality subtrees.

Conflict-Aware Heuristics
===========================

- ``lastConflict`` — after a failure, the variable that caused it is retried *before*
  consulting the fallback variable selector.
- ``conflictOrderingSearch`` — maintains a conflict counter per variable; the most conflicted
  variable is selected first.

Scheduling and Sequencing Heuristics
======================================

- ``fds(tasks)`` — **Failure-Directed Search** for interval variables. Very effective for
  proving optimality; supports optional tasks. Branches on status, start time, and duration.
- ``setTimes(tasks)`` — branches on the start time of the task with the smallest slack.
  Assumes all tasks are mandatory.
- ``rank(tasks)`` — imposes a total order on tasks (assumes mandatory tasks).

For sequence variables: ``firstFailBinary(seqVars)`` selects the insertable node with
fewest insertion points; ``branchesInsertingNode`` sorts branches by a user-supplied
detour cost.

Combinators
============

- ``and(b1, b2, ...)`` — composes strategies sequentially; each strategy is activated only
  when all preceding ones return ``EMPTY``.
- ``limitedDiscrepancy(branching, maxDisc)`` — prunes paths with more than ``maxDisc`` right
  branches (Limited Discrepancy Search).

Branch-and-Bound Optimization
================================

.. code-block:: java

    // Minimize with a 60-second time limit
    SearchStatistics stats = search.optimize(
        minimize(cost),
        stats -> stats.timeInMillis() > 60_000);

Each time a solution is found, the objective bound is tightened to exclude worse solutions.

Large Neighborhood Search (LNS)
=================================

LNS iteratively improves an incumbent by fixing a large subset of variables to their
current best values (the *freeze* phase) then re-solving the relaxed sub-problem.

The example below applies LNS to the Quadratic Assignment Problem (QAP).

**Model**

.. code-block:: java

    CPSolver cp = makeSolver();
    CPIntVar[] x = makeIntVarArray(cp, n, n);

    CPIntVar[] weightedDist = new CPIntVar[n * n];
    int ind = 0;
    for (int i = 0; i < n; i++)
        for (int j = 0; j < n; j++)
            weightedDist[ind++] = mul(element(d, x[i], x[j]), w[i][j]);

    CPIntVar totCost = sum(weightedDist);
    Objective obj = cp.minimize(totCost);
    DFSearch dfs = makeDfs(cp, firstFailBinary(x));

**Tracking the best solution**

.. code-block:: java

    int[] xBest = IntStream.range(0, n).toArray();  // identity permutation

    dfs.onSolution(() -> {
        for (int i = 0; i < n; i++) xBest[i] = x[i].min();
        System.out.println("objective: " + totCost.min());
    });

**LNS loop**

.. code-block:: java

    int nRestarts = 100;
    int failureLimit = 100;
    Random rand = new Random(0);

    for (int i = 0; i < nRestarts; i++) {
        dfs.optimizeSubjectTo(obj,
            stats -> stats.numberOfFailures() >= failureLimit,
            () -> {
                // Fix 95 % of variables to their best-known value
                for (int j = 0; j < n; j++) {
                    if (rand.nextInt(100) < 95)
                        cp.post(eq(x[j], xBest[j]));
                }
            }
        );
    }

After each call to ``optimizeSubjectTo`` the solver automatically restores its state,
retracting all temporary fixing constraints.
``xBest`` lives outside the reversible state and always reflects the globally best
solution found so far.

Full example:
`QAPLNS.java (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/QAPLNS.java>`_
