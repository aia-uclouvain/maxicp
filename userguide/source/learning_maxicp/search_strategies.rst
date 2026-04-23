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

Following the design of MiniCP :cite:`Michel2021MiniCP`, the search in MaxiCP is defined by a *branching function*:
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
  the bound the least — biasing the search towards high-quality subtrees :cite:`fages2017making`.

Conflict-Aware Heuristics
===========================

- ``lastConflict`` — after a failure, the variable that caused it is retried *before*
  consulting the fallback variable selector :cite:`lecoutre2009reasoning`.
- ``conflictOrderingSearch`` — maintains a conflict counter per variable; the most conflicted
  variable is selected first :cite:`conflictOrderingSearch`.

Scheduling and Sequencing Heuristics
======================================

- ``fds(tasks)`` — **Failure-Directed Search** :cite:`failureDirectedSearch` for interval variables. Very effective for
  proving optimality; supports optional tasks. Branches on status, start time, and duration.
- ``setTimes(tasks)`` — branches on the start time of the task with the smallest slack :cite:`setTimes`.
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
  branches (Limited Discrepancy Search) :cite:`harvey1995limited`.

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
current best values (the *freeze* phase) then re-solving the relaxed sub-problem :cite:`shaw1998using`.

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
`QAPLNS.java (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/QAPLNS.java>`__

Variable Objective LNS (VOLNS)
================================

Standard LNS applies to well-constrained problems where a feasible solution always
exists and the goal is purely to minimise an objective.
Many real-world problems are **over-constrained**: a fully feasible solution may not exist,
and the goal is to find an assignment that violates as few constraints as possible.

**Variable Objective LNS** (VOLNS) :cite:`volns` handles this by reformulating each
hard constraint that may be violated as a *soft constraint* with an individual
violation variable, then minimising the sum of those violations.
The key insight is that tightening the individual violations during the LNS loop drives
the search towards globally feasible solutions more effectively than simply minimising
the total violation sum alone.

The ``MinimizeObjectiveSum`` class encapsulates this strategy.

Source:
`MinimizeObjectiveSum.java <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/core/MinimizeObjectiveSum.java>`__

Example: Magic Square
----------------------

The Magic Square problem requires placing the numbers 1 … n² in an n×n grid so that
every row, column, and diagonal sums to the same target S = n(n²+1)/2.
It is a pure feasibility problem, but for large *n* it is extremely hard to find a
feasible solution directly.
VOLNS turns it into a minimisation problem by treating each *row* sum constraint as a
soft constraint; columns and diagonals remain hard.
The violation of row *i* is ``|sum(row_i) − S|``; the total violation is their sum.
The search succeeds when the total violation reaches 0.

Full source:
`MagicSquareVOLNS.java <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/magicsquare/MagicSquareVOLNS.java>`__

**Step 1 — Model hard and soft constraints**

.. code-block:: java

    int sumResult = n * (n * n + 1) / 2;

    // Hard: all-different, column sums, diagonal sums
    cp.post(allDifferent(xFlat));
    for (int j = 0; j < n; j++) {
        CPIntVar[] col = new CPIntVar[n];
        for (int i = 0; i < n; i++) col[i] = x[i][j];
        cp.post(sum(col, sumResult));
    }
    cp.post(sum(diagonalLeft,  sumResult));
    cp.post(sum(diagonalRight, sumResult));

    // Soft: row sums — violation = |sum(row_i) - sumResult|
    CPIntVar[] rowViolation = new CPIntVar[n];
    for (int i = 0; i < n; i++)
        rowViolation[i] = abs(minus(sum(x[i]), sumResult));

    CPIntVar totalViolation = sum(rowViolation);

**Step 2 — Create the VOLNS objective**

.. code-block:: java

    MinimizeObjectiveSum globalObjective = new MinimizeObjectiveSum(rowViolation);

``MinimizeObjectiveSum`` internally wraps each individual ``rowViolation[i]`` and their
sum in separate ``IntObjective`` objects.
On construction, only the sum objective is *filtered* (i.e., actively used for pruning);
the individual terms are tracked but not yet enforced.

**Step 3 — Find an initial solution**

.. code-block:: java

    int[] xFlatSol = new int[n * n];
    int[] totalViolationSol = new int[]{0};

    DFSearch dfs = makeDfs(cp, firstFailBinary(xFlat));
    dfs.onSolution(() -> {
        totalViolationSol[0] = totalViolation.min();
        for (int j = 0; j < n * n; j++) xFlatSol[j] = xFlat[j].min();
    });

    // stop as soon as one feasible solution (or best approximation) is found
    dfs.optimize(globalObjective, stats -> stats.numberOfSolutions() >= 1);

**Step 4 — VOLNS loop**

The loop applies three tightening strategies each iteration before re-running ``optimizeSubjectTo``:

.. code-block:: java

    Random random = new Random(42);
    for (int iter = 0; iter < maxIter && globalObjective.getBound() > 0; iter++) {

        // (1) Weak-tighten all terms: next solution must ≥ each current bound
        globalObjective.weakTightenAll();
        // (2) Strong-tighten the sum: next solution must strictly improve the total
        globalObjective.strongTightenSum();
        // (3) Strong-tighten the worst individual term: force improvement of the
        //     row that currently contributes the most violation
        globalObjective.strongTigthenWorseTerm();

        double relaxValue = random.nextDouble();
        dfs.optimizeSubjectTo(globalObjective,
            s -> s.numberOfFailures() >= 100,
            () -> {
                for (int j = 0; j < n * n; j++)
                    if (random.nextDouble() > relaxValue)
                        cp.post(eq(xFlat[j], xFlatSol[j]));
            });
    }

The three tightening modes create a progressive pressure:

.. list-table::
   :widths: 25 75
   :header-rows: 1

   * - Method
     - Effect
   * - ``weakTightenAll()``
     - Every individual violation and the sum must be **at most equal** to their current best
       values — no term may get worse.
   * - ``strongTightenSum()``
     - The total violation must **strictly decrease** (delta = 1).
   * - ``strongTigthenWorseTerm()``
     - The individual violation that is currently largest must also **strictly decrease**,
       focusing search on the hardest-to-satisfy row.

The combination forces the search to simultaneously avoid global regression,
improve the aggregate quality, and attack the most violated constraint.
As iterations proceed, more rows reach violation 0 and the surviving violated rows
are progressively tightened until the total violation reaches 0 — a valid magic square.

