"""
Generates all missing RST pages for the MaxiCP user guide.
Run once from any directory:  python3 userguide/write_docs.py
"""
import os

BASE = os.path.join(os.path.dirname(__file__), "source", "learning_maxicp")
os.makedirs(BASE, exist_ok=True)

files = {}

# ── propagation ──────────────────────────────────────────────────────────────
files["propagation.rst"] = """\
.. _propagation:

*****************
Propagation Engine
*****************

The propagation engine is the heart of any CP solver.
In MaxiCP it is implemented in the ``MaxiCP`` class and follows a priority-based
fixed-point algorithm.

Source:
`MaxiCP.java <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/core/MaxiCP.java>`_

Priority-Based Scheduling
==========================

Constraints implement the ``CPConstraint`` interface, which exposes a ``propagate()``
method and a ``priority()`` method.
When a variable domain changes, every constraint subscribed to that event is *scheduled*
for propagation.
The engine drains a priority queue so that cheap constraints (bounds, equality) run before
expensive global constraints (``AllDifferent``, ``Cumulative``).

.. code-block:: java

    public void fixPoint() {
        notifyFixPoint();
        while (!propagationQueue.isEmpty()) {
            CPConstraint c = propagationQueue.poll();
            c.setScheduled(false);
            if (c.isActive()) c.propagate();
        }
    }

    public void schedule(CPConstraint c) {
        if (c.isActive() && !c.isScheduled()) {
            c.setScheduled(true);
            propagationQueue.add(c, c.priority());
        }
    }

If ``propagate()`` throws ``InconsistencyException`` the queue is cleared and the
exception propagates to the search layer, which handles backtracking.

Event-Driven Constraint Activation
====================================

Constraints register to specific domain events.
Only the relevant constraints are re-scheduled when a variable changes.

**Integer variables (``CPIntVar``)**

- ``propagateOnFix`` — variable assigned a single value.
- ``propagateOnBoundChange`` — minimum or maximum changes.
- ``propagateOnDomainChange`` — any value removed.

**Interval variables (``CPIntervalVar``)**

- ``propagateOnChange`` — any attribute (start, end, length, presence) changes.
- ``propagateOnFix`` — interval fully fixed.

**Sequence variables (``CPSeqVar``)**

- ``propagateOnInsert`` — a node is inserted into the partial sequence.
- ``propagateOnExclude`` — a node is excluded.
- ``propagateOnRequire`` — a node becomes required.

Registering a constraint to an event is a *reversible* operation and is automatically
undone on backtrack.

Posting Constraints
====================

``cp.post(constraint)`` invokes ``post()``, which typically:

1. Registers the constraint to the relevant variable events.
2. Calls ``propagate()`` for an initial domain reduction.

By default, a full fixed-point is computed immediately.
Use ``cp.post(constraint, false)`` to defer it and trigger it later via ``cp.fixPoint()``.

Because constraint registration is reversible, branching decisions can simply be implemented
by posting constraints that define each alternative branch.

Implementing a Custom Constraint
==================================

Extend ``AbstractCPConstraint`` and implement ``post()`` and ``propagate()``.

Source:
`LessOrEqual.java <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/LessOrEqual.java>`_

.. code-block:: java

    public class LessOrEqual extends AbstractCPConstraint {
        private final CPIntVar x, y;

        public LessOrEqual(CPIntVar x, CPIntVar y) {
            super(x.getSolver());
            this.x = x; this.y = y;
        }

        @Override
        public void post() {
            x.propagateOnBoundChange(this);
            y.propagateOnBoundChange(this);
            propagate();
        }

        @Override
        public void propagate() {
            x.removeAbove(y.max());
            y.removeBelow(x.min());
        }
    }

``removeAbove`` / ``removeBelow`` throw ``InconsistencyException`` when the domain
becomes empty, triggering backtracking.

Delta-Based Incremental Propagation
=====================================

For higher performance, a constraint can subscribe to *delta* events and react only to
the values removed since its last execution, instead of re-scanning full domains.

The ``Element`` constraint (``z = t[y]``) below uses this approach.
``y.delta(this)`` returns a ``DeltaCPIntVar``; calling ``yDelta.fillArray(buf)`` fills
``buf`` with the values removed from ``y`` since the last propagation call
and returns their count.

Source:
`Element1D.java <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/Element1D.java>`_

.. code-block:: java

    public class Element extends AbstractCPConstraint {

        private final int[] t;
        private final CPIntVar y, z;
        private DeltaCPIntVar yDelta, zDelta;
        private int[] yValues, zValues;
        private int offZ;
        private StateInt[] supportCounter;

        public Element(int[] array, CPIntVar y, CPIntVar z) {
            super(y.getSolver()); this.t = array; this.y = y; this.z = z;
        }

        @Override
        public void post() {
            y.removeBelow(0); y.removeAbove(t.length - 1);
            y.propagateOnDomainChange(this);
            yDelta = y.delta(this);
            yValues = new int[y.size()];

            z.removeBelow(Arrays.stream(t).min().getAsInt());
            z.removeAbove(Arrays.stream(t).max().getAsInt());
            z.propagateOnDomainChange(this);
            zDelta = z.delta(this);
            zValues = new int[z.size()];

            supportCounter = new StateInt[z.size()];
            offZ = z.min();
            for (int i = 0; i < z.size(); i++)
                supportCounter[i] = getSolver().getStateManager().makeStateInt(0);
            for (int i = 0; i < t.length; i++)
                if (y.contains(i)) supportCounter[t[i] - offZ].increment();

            propagate();
        }

        @Override
        public void propagate() {
            if (zDelta.size() > 0) {
                int sz = zDelta.fillArray(zValues);
                for (int i = 0; i < sz; i++) {
                    int v = zValues[i];
                    int sy = y.fillArray(yValues);
                    for (int j = 0; j < sy; j++)
                        if (t[yValues[j]] == v) y.remove(yValues[j]);
                }
            }
            if (yDelta.size() > 0) {
                int sy = yDelta.fillArray(yValues);
                for (int i = 0; i < sy; i++) {
                    int v = yValues[i];
                    if (supportCounter[t[v] - offZ].decrement() == 0)
                        z.remove(t[v]);
                }
            }
        }
    }

``supportCounter`` is an array of ``StateInt`` objects whose values are automatically
restored on backtrack, keeping the counts consistent with the current search state.

Global Constraints
===================

Source:
`org.maxicp.cp.engine.constraints <https://github.com/aia-uclouvain/maxicp/tree/main/src/main/java/org/maxicp/cp/engine/constraints>`_

.. list-table::
   :widths: 35 65
   :header-rows: 1

   * - Constraint
     - Semantics
   * - ``AllDifferent``
     - All variables take pairwise distinct values (Régin's arc-consistent filtering).
   * - ``TableCT``
     - Extensional constraint — assignment must match a row of a table (compact-table filtering).
   * - ``BinPacking``
     - Links item-to-bin variables, item weights, and per-bin load variables.
   * - ``GCC`` / ``CostCardinalityMaxDC``
     - Global Cardinality Constraint with optional per-value costs (efficient flow-based filtering).
   * - ``SoftCardinalityDC``
     - Soft GCC — measures total violation of cardinality bounds in a violation variable.
   * - ``NoOverlap`` / ``Cumulative`` / ``GeneralizedCumulative``
     - Scheduling constraints for interval variables.
   * - ``Sum``
     - Linear sum constraint.
   * - ``Element1D`` / ``Element2D``
     - Array indexing with decision variables: ``z = t[y]`` and ``z = t[x][y]``.
"""

# ── search ───────────────────────────────────────────────────────────────────
files["search.rst"] = """\
.. _search:

**************
Search
**************

MaxiCP provides a flexible, compositional search framework.
The core engine is depth-first search (``DFSearch``) with branch-and-bound for optimization.

Source:
`org.maxicp.search <https://github.com/aia-uclouvain/maxicp/tree/main/src/main/java/org/maxicp/search>`_

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
`QAP.java (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/qap/QAP.java>`_
"""

# ── scheduling ───────────────────────────────────────────────────────────────
files["scheduling.rst"] = """\
.. _scheduling:

*************************************
Scheduling with Conditional Intervals
*************************************

Scheduling is a major application area for constraint programming.
MaxiCP supports optional tasks, alternative resources, and complex resource interactions
through **conditional time-interval variables** and **cumulative function expressions**,
following the modeling paradigm of CP Optimizer and implemented via the
Generalized Cumulative constraint.

Conditional Time-Interval Variables
=====================================

A ``CPIntervalVar`` represents a task that may or may not be executed.
Its domain is a subset of ``{absent} ∪ {[s, e) | s ≤ e}``.

Each interval variable has four attributes:

- **presence** ``p ∈ {true, false}`` — ``false`` means the task is absent.
- **start** ``s ∈ [minS, maxS]``
- **end** ``e ∈ [minE, maxE]``
- **duration** ``d ∈ [minD, maxD]``, with ``s + d = e`` maintained automatically.

Source:
`CPIntervalVar.java <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/core/CPIntervalVar.java>`_

Creating Interval Variables
=============================

.. code-block:: java

    CPSolver cp = makeSolver();

    // Mandatory task with fixed duration
    CPIntervalVar task = makeIntervalVar(cp, false, duration);

    // Optional task with flexible duration
    CPIntervalVar opt = makeIntervalVar(cp);

    // Mandatory task with duration in [minD, maxD]
    CPIntervalVar t = makeIntervalVar(cp, false, minD, maxD);

    // Array of mandatory tasks with known durations
    CPIntervalVar[] tasks = makeIntervalVarArray(cp, n);
    for (int i = 0; i < n; i++) {
        tasks[i].setLength(duration[i]);
        tasks[i].setPresent();
    }

The second argument of ``makeIntervalVar`` controls optionality:
``false`` = mandatory (present), ``true`` = optional.

Job-Shop Scheduling
====================

The classical Job-Shop Scheduling Problem (JSP) consists of *n* jobs,
each comprising a sequence of operations that must be processed on specific machines.

Constraints: (i) *precedence* — within each job, operations execute in order;
(ii) *no-overlap* — no two operations on the same machine can overlap.
Objective: minimize makespan.

Full source:
`JobShop.java (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/jobshop/JobShop.java>`_

.. code-block:: java

    CPSolver cp = makeSolver();

    CPIntervalVar[][] activities = new CPIntervalVar[nJobs][nMachines];
    for (int j = 0; j < nJobs; j++)
        for (int m = 0; m < nMachines; m++)
            activities[j][m] = makeIntervalVar(cp, false, duration[j][m]);

    // Precedences within each job
    for (int j = 0; j < nJobs; j++)
        for (int m = 1; m < nMachines; m++)
            cp.post(endBeforeStart(activities[j][m-1], activities[j][m]));

    // No-overlap on each machine
    CPIntervalVar[][] toRank = new CPIntervalVar[nMachines][];
    for (int m = 0; m < nMachines; m++) {
        ArrayList<CPIntervalVar> onMachine = new ArrayList<>();
        for (int j = 0; j < nJobs; j++)
            for (int i = 0; i < nMachines; i++)
                if (machine[j][i] == m) onMachine.add(activities[j][i]);
        toRank[m] = onMachine.toArray(new CPIntervalVar[0]);
        cp.post(noOverlap(toRank[m]));
    }

    CPIntVar makespan = makespan(
        Arrays.stream(activities).map(job -> job[nMachines-1])
              .toArray(CPIntervalVar[]::new));

    Objective obj = cp.minimize(makespan);
    DFSearch dfs = makeDfs(cp, new Rank(toRank));
    dfs.optimize(obj);

``endBeforeStart(a, b)`` enforces that operation *a* ends before *b* starts.
``noOverlap`` ensures activities on the same machine do not overlap (Vilim's edge-finding).
``Rank`` is a search heuristic that selects the no-overlap group with the least slack
and decides which activity goes next on that group's machine.

Cumulative Function Expressions
=================================

MaxiCP models cumulative resources through *cumulative function expressions*,
built compositionally from elementary functions:

- ``pulse(x, h)`` — contributes height *h* during task *x*.
- ``stepAtStart(x, h)`` — adds height *h* from the start of *x* until the horizon.
- ``stepAtEnd(x, h)`` — adds height *h* from the end of *x* until the horizon.
- ``flat()`` — the zero function (identity for summation).

Functions are combined with:

- ``sum(f1, f2, ...)`` — sum.
- ``minus(f1, f2)`` — difference.

The resulting function is bounded by:

- ``le(f, maxCapa)`` — profile must not exceed ``maxCapa`` where at least one task executes.
- ``alwaysIn(f, minCapa, maxCapa)`` — profile stays between ``minCapa`` and ``maxCapa``.

Internally these compile to a single **Generalized Cumulative** constraint, which accepts
variable and possibly negative heights.

RCPSP (Resource-Constrained Project Scheduling)
=================================================

Full source:
`RCPSP.java (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/rcpsp/RCPSP.java>`_

.. code-block:: java

    CPSolver cp = makeSolver();

    CPIntervalVar[] tasks = makeIntervalVarArray(cp, nActivities);
    for (int i = 0; i < nActivities; i++) {
        tasks[i].setLength(duration[i]);
        tasks[i].setPresent();
    }

    // Build cumulative resource profiles
    CPCumulFunction[] resources = new CPCumulFunction[nResources];
    for (int r = 0; r < nResources; r++)
        resources[r] = new CPFlatCumulFunction();

    for (int i = 0; i < nActivities; i++)
        for (int r = 0; r < nResources; r++)
            if (consumption[r][i] > 0)
                resources[r] = new CPPlusCumulFunction(
                    resources[r],
                    new CPPulseCumulFunction(tasks[i], consumption[r][i]));

    for (int r = 0; r < nResources; r++)
        cp.post(le(resources[r], capa[r]));

    // Precedences
    for (int i = 0; i < nActivities; i++)
        for (int k : successors[i])
            cp.post(endBeforeStart(tasks[i], tasks[k]));

    CPIntVar makespan = makespan(tasks);
    Objective obj = cp.minimize(makespan);
    DFSearch dfs = makeDfs(cp, fds(tasks));   // Failure-Directed Search
    dfs.optimize(obj);

Producer-Consumer Scheduling
==============================

Cumulative functions naturally model reservoirs: producers add to the level at their
completion, consumers remove from it at their start.

.. code-block:: java

    CPCumulFunction reservoir = new CPFlatCumulFunction();

    for (int i = 0; i < nProducers; i++)
        reservoir = new CPPlusCumulFunction(
            reservoir, stepAtEnd(producerTask[i], producerQty[i]));

    for (int i = 0; i < nConsumers; i++)
        reservoir = new CPPlusCumulFunction(
            reservoir, stepAtStart(consumerTask[i], -consumerQty[i]));

    cp.post(alwaysIn(reservoir, 0, capacity));

``stepAtEnd(task, qty)`` adds ``qty`` at the task's end (producer delivers).
``stepAtStart(task, -qty)`` subtracts ``qty`` at the task's start (consumer takes).
``alwaysIn`` keeps the level between 0 and ``capacity`` at all times.

Full example:
`ProducerConsumer.java (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/producerconsumer/ProducerConsumer.java>`_
"""

# ── seqvar ───────────────────────────────────────────────────────────────────
files["seqvar.rst"] = """\
.. _seqvar:

************************************
Sequence Variables for Routing
************************************

MaxiCP introduces **sequence variables** (``CPSeqVar``), a dedicated computational domain
for routing and sequencing problems.
Unlike the classical successor-array model
(integer variable ``succ[i]`` giving the next node after *i*),
sequence variables represent a *partial, growing path* through node insertions
and natively support optional visits and insertion-based branching.

Source:
`CPSeqVar.java <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/core/CPSeqVar.java>`_

Domain of a Sequence Variable
================================

A sequence variable ``S`` represents an ordered path over pairwise distinct nodes
from a set *N*, starting at origin *first* and ending at destination *last*.
Its domain is represented as a tuple ``(R, X, s, NB)`` where:

- ``R`` — **required** nodes that must appear in every feasible sequence.
- ``X`` — **excluded** nodes that may not appear in any feasible sequence.
- ``s`` — mandatory **partial sequence**: a sub-sequence that must appear in every feasible sequence.
- ``NB`` — **NotBetween** triples: ``(i, j, k)`` forbids node *j* appearing between *i* and *k*.

Nodes fall into four categories:

- **Member** — currently in the partial sequence ``s``.
- **Possible** — may or may not enter the final sequence.
- **Required** — must be in the final sequence but not yet inserted.
- **Excluded** — removed from all feasible sequences.

Domain Operations
==================

.. code-block:: java

    seqVar.insert(pred, node);            // insert node after pred
    seqVar.exclude(node);                 // remove node from all sequences
    seqVar.require(node);                 // mark node as required
    seqVar.removeInsert(pred, node);      // forbid inserting node after pred

All operations are reversible through the state manager.

Querying the Domain
====================

.. code-block:: java

    seqVar.isFixed();                          // true when all nodes are member or excluded
    seqVar.fillNode(buf, INSERTABLE);          // fill buf with insertable nodes
    seqVar.nInsert(node);                      // number of feasible insertion points for node
    seqVar.fillInsert(node, preds);            // fill preds with feasible predecessors of node
    seqVar.memberAfter(pred);                  // current successor of pred in the partial sequence

Global Constraints for Sequence Variables
==========================================

Source:
`org.maxicp.cp.engine.constraints (seqvar) <https://github.com/aia-uclouvain/maxicp/tree/main/src/main/java/org/maxicp/cp/engine/constraints>`_

``distance(SeqVar, int[][] dist, IntExpression totalDist)``
  Links the total route distance to a variable using a distance matrix.

``transitionTimes(SeqVar, IntExpression[] time, int[][] dist)``
  Enforces temporal consistency: for each consecutive pair ``(u, v)`` in the sequence,
  ``time[u] + dist[u][v] ≤ time[v]``.

``cumulative(SeqVar, int[] pickups, int[] drops, int[] load, int capacity)``
  Simultaneously enforces: (i) capacity at every node; (ii) pickup precedes delivery;
  (iii) both nodes of a request are assigned to the same vehicle.

Modeling the TSPTW with a Sequence Variable
=============================================

Full source:
`TSPTW.java (raw, SeqVar) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/tsptw/TSPTW.java>`_

.. code-block:: java

    CPSolver cp = makeSolver();

    CPSeqVar tour = makeSeqVar(cp, n, 0, n - 1);
    for (int i = 0; i < n; i++) tour.require(i); // all nodes mandatory

    CPIntVar[] time = makeIntVarArray(cp, n, horizon);
    cp.post(eq(time[0], 0));
    for (int i = 1; i < n; i++) {
        time[i].removeAbove(latest[i]);
        time[i].removeBelow(earliest[i]);
    }

    cp.post(new TransitionTimes(tour, time, distMatrix));

    CPIntVar totDist = makeIntVar(cp, 0, 100_000);
    cp.post(new Distance(tour, distMatrix, totDist));

    DFSearch dfs = makeDfs(cp, /* insertion search, see below */);
    dfs.optimize(cp.minimize(totDist));

Custom Insertion-Based Search
================================

.. code-block:: java

    int[] nodes = new int[n];
    DFSearch dfs = makeDfs(cp, () -> {
        if (tour.isFixed()) return EMPTY;

        // Variable selection: node with fewest insertion points (first-fail)
        int nIns = tour.fillNode(nodes, INSERTABLE);
        int node = selectMin(nodes, nIns,
            i -> true, tour::nInsert).getAsInt();

        // Value selection: insertion with smallest detour cost
        int nPreds = tour.fillInsert(node, nodes);
        int bestPred = selectMin(nodes, nPreds,
            pred -> true,
            pred -> {
                int succ = tour.memberAfter(pred);
                return dist[pred][node] + dist[node][succ] - dist[pred][succ];
            }).getAsInt();
        int succ = tour.memberAfter(bestPred);

        return branch(
            () -> cp.post(insert(tour, bestPred, node)),
            () -> cp.post(notBetween(tour, bestPred, node, succ))
        );
    });

Left branch: insert ``node`` after ``bestPred``.
Right branch: ``notBetween`` forbids that placement without committing to an alternative.

Multi-Vehicle Routing (CVRPTW)
================================

Full source:
`CVRPTW.java (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/cvrptw/CVRPTW.java>`_

.. code-block:: java

    CPSolver cp = makeSolver();
    CPSeqVar[] vehicles = new CPSeqVar[nVehicle];
    CPIntVar[] time     = new CPIntVar[nNode];
    CPIntVar[] distance = new CPIntVar[nVehicle];

    for (int v = 0; v < nVehicle; v++) {
        vehicles[v] = makeSeqVar(cp, nNode, nRequest + v*2, nRequest + v*2 + 1);
        distance[v] = makeIntVar(cp, 0, depotEnd);
    }
    for (int i = 0; i < nRequest; i++) time[i] = makeIntVar(cp, twStart[i], twEnd[i]);
    for (int i = nRequest; i < nNode; i++) time[i] = makeIntVar(cp, depotStart, depotEnd);

    for (int v = 0; v < nVehicle; v++) {
        cp.post(new TransitionTimes(vehicles[v], time, distMatrix, duration));
        cp.post(new Cumulative(vehicles[v], pickups, drops, load, capacity));
        cp.post(new Distance(vehicles[v], distMatrix, distance[v]));
    }

    CPIntVar sumDist = sum(distance);

    // Each node visited by exactly one vehicle
    for (int node = 0; node < nNode; node++) {
        CPIntVar[] visits = new CPIntVar[nVehicle];
        for (int v = 0; v < nVehicle; v++)
            visits[v] = vehicles[v].getNodeVar(node).isRequired();
        cp.post(new Sum(visits, 1));
    }

    DFSearch search = makeDfs(cp, /* insertion search */);
    search.optimize(cp.minimize(sumDist));

LNS with Sequence Variables
=============================

The ``RelaxedSequence`` constraint fixes non-relaxed nodes in their best-known order,
leaving relaxed nodes free for re-insertion.

.. code-block:: java

    int[] bestTour = IntStream.range(0, n + 1).toArray();

    dfs.onSolution(() -> tour.fillNode(bestTour, SeqStatus.MEMBER_ORDERED));

    Random random = new Random(42);
    for (int iter = 0; iter < 100; iter++) {
        dfs.optimizeSubjectTo(obj, s -> false, () -> {
            Set<Integer> relaxed = randomSubset(random, 0, n, 5);
            cp.post(new RelaxedSequence(tour, bestTour, relaxed));
        });
    }
"""

# ── symbolic modeling ────────────────────────────────────────────────────────
files["symbolic_modeling.rst"] = """\
.. _symbolic_modeling:

************************************
Symbolic Modeling (MaxiCP-Modelling)
************************************

Having covered the raw layer — state management, propagation, search, scheduling,
and sequence variables — we now turn to the **symbolic modeling layer**.

MaxiCP provides a higher-level API that separates *model definition* from *resolution*.
Models are treated as first-class, immutable data structures, enabling model
transformations, LNS neighborhoods, and embarrassingly parallel search.

Source:
`org.maxicp.modeling <https://github.com/aia-uclouvain/maxicp/tree/main/src/main/java/org/maxicp/modeling>`_

Models as Functional Linked Lists
====================================

A ``SymbolicModel`` is an immutable record:

.. code-block:: java

    public record SymbolicModel(
        Constraint   constraint,
        SymbolicModel parent,
        ModelProxy   modelProxy
    ) implements Model, Iterable<Constraint> {

        public SymbolicModel add(Constraint c) {
            return new SymbolicModel(c, this, modelProxy);
        }
    }

Adding a constraint is an *O(1)* operation that returns a **new** model node,
leaving the original unchanged.
The full constraint list is recovered by traversing the chain to the root.
This design enables *model trees* where each branch is a different sub-problem,
reformulation, or LNS neighborhood.

The ``ModelDispatcher``
==========================

``ModelDispatcher`` is the primary user-facing class.
It maintains a reference to the current symbolic model and acts as both a
variable factory and a model proxy.

.. code-block:: java

    ModelDispatcher model = Factory.makeModelDispatcher();

    IntVar[] q   = model.intVarArray(n, n);
    IntExpression[] qL = model.intVarArray(n, i -> q[i].plus(i));
    IntExpression[] qR = model.intVarArray(n, i -> q[i].minus(i));

    model.add(allDifferent(q));
    model.add(allDifferent(qL));
    model.add(allDifferent(qR));

Variables are symbolic objects; they do not belong to any concrete solver until
concretization, so the same variables can be shared across multiple solver instances.

Concretization
===============

To solve a model it must be *concretized* into a solver engine:

.. code-block:: java

    model.runCP(cp -> {
        DFSearch search = cp.dfSearch(firstFailBinary(q));
        SearchStatistics stats = search.solve();
    });

``runCP`` internally:

1. Creates a fresh ``MaxiCP`` instance with a ``Trailer``.
2. Iterates over the symbolic constraint list and instantiates each one in the CP engine.
3. Runs the initial fixed-point propagation.

Alternatively, ``model.cpInstantiate()`` returns a ``ConcreteCPModel``
that can be used directly:

.. code-block:: java

    ConcreteCPModel cp = model.cpInstantiate();
    DFSearch dfs = cp.dfSearch(branching);
    SearchStatistics stats = dfs.solve();

Model Transformations
======================

**LNS neighborhoods.** A neighborhood is a model extension:

.. code-block:: java

    Model relaxed = base;
    for (IntVar x : variables) {
        if (rand.nextDouble() > 0.1)
            relaxed = relaxed.add(eq(x, solution.get(x)));
    }
    relaxed.runCP(cp -> { /* solve the neighborhood */ });

The base model is never modified. Each neighborhood is a separate branch of the model tree.

This complements the raw-level ``optimizeSubjectTo`` technique:
the raw approach saves/restores solver state; the symbolic approach creates
entirely independent model branches that can be solved concurrently.

Embarrassingly Parallel Search (EPS)
=======================================

EPS decomposes the original problem into independent sub-problems by exploring the
search tree to a fixed depth *d*. Each leaf defines a sub-problem (the base model plus
the branching decisions along the path), which is solved concurrently by a thread pool.

.. code-block:: java

    ExecutorService executor = Executors.newFixedThreadPool(8);

    Function<Model, SearchStatistics> epsSolve = (m) ->
        model.runAsConcrete(CPModelInstantiator.withTrailing, m, (cp) -> {
            DFSearch search = cp.dfSearch(branching);
            return search.solve();
        });

    LinkedList<Future<SearchStatistics>> results = new LinkedList<>();
    model.runCP((cp) -> {
        DFSearch search = cp.dfSearch(new LimitedDepthBranching(branching, 10));
        search.onSolution(() -> {
            Model m = cp.symbolicCopy(); // capture the sub-problem
            results.add(executor.submit(() -> epsSolve.apply(m)));
        });
        search.solve(); // decomposition phase
    });

    int total = 0;
    for (var f : results) total += f.get().numberOfSolutions();
    System.out.println("Total solutions: " + total);
    executor.shutdown();

``symbolicCopy()`` returns a lightweight, immutable snapshot of the current model.
Because each sub-problem is concretized independently there is no shared mutable state
and therefore no synchronization overhead.

Portfolio Parallel Search
===========================

Since symbolic models are immutable, the same model can be solved concurrently by
*N* threads, each using a different heuristic or random seed:

.. code-block:: java

    IntStream.range(0, nThreads).parallel().forEach(t -> {
        model.runCP(cp -> {
            DFSearch search = cp.dfSearch(
                randomizedFirstFail(x, new Random(t)));
            search.optimize(minimize(cost));
        });
    });

Mixing Both Layers
=====================

Inside the ``runCP`` callback the user can access both the concrete model and the
symbolic variables.
The concrete model exposes the underlying ``CPVar`` objects, which is useful for
custom search heuristics that need direct access to domain internals:

.. code-block:: java

    model.runCP(cp -> {
        // Access the underlying CPIntVar for symbolic variable q[0]
        CPIntVar cpQ0 = cp.of(q[0]);
        DFSearch dfs = cp.dfSearch(() -> {
            if (cpQ0.isFixed()) return EMPTY;
            int v = cpQ0.min();
            return branch(() -> cp.post(eq(cpQ0, v)),
                          () -> cp.post(neq(cpQ0, v)));
        });
        dfs.solve();
    });
"""

for filename, content in files.items():
    path = os.path.join(BASE, filename)
    with open(path, "w") as f:
        f.write(content)
    print(f"Written: {path}")

print("All done.")

