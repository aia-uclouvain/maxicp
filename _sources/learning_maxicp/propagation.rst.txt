.. _propagation:

*******************
Propagation Engine
*******************

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

Source of the constraints package:
`org.maxicp.cp.engine.constraints <https://github.com/aia-uclouvain/maxicp/tree/main/src/main/java/org/maxicp/cp/engine/constraints>`_

**Integer / Boolean constraints**

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Constraint (source)
     - Semantics
   * - `AllDifferentDC <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/AllDifferentDC.java>`__
     - All variables take pairwise distinct values. Uses Régin's arc-consistent filtering based on maximum bipartite matching :cite:`regin1996generalized`.
   * - `AllDifferentFWC <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/AllDifferentFWC.java>`__
     - Forward-checking (bounds-consistent) variant of AllDifferent, cheaper but weaker than DC.
   * - `Among <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/Among.java>`__
     - Constrains the number of variables in ``x`` whose value belongs to a given set ``S``: ``lb ≤ |{i | x[i] ∈ S}| ≤ ub``.
   * - `AtLeastNValueDC <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/AtLeastNValueDC.java>`__ / `AtLeastNValueFWC <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/AtLeastNValueFWC.java>`__
     - The number of distinct values taken by the variables is at least ``n``. DC and FWC variants.
   * - `BinPacking <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/BinPacking.java>`__
     - ``x[i]`` is the bin of item ``i`` with weight ``w[i]``; ``load[b]`` equals the total weight in bin ``b``: ``load[b] = Σ{i | x[i]=b} w[i]`` :cite:`shaw2004constraint`.
   * - `BinaryKnapsack <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/BinaryKnapsack.java>`__
     - Binary knapsack: each item is either taken (``x[i]=1``) or not; total weight ``Σ w[i]·x[i] ≤ capa`` and total profit ``Σ p[i]·x[i] = profit``.
   * - `CardinalityMaxFWC <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/CardinalityMaxFWC.java>`__ / `CardinalityMinFWC <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/CardinalityMinFWC.java>`__
     - Upper / lower cardinality bounds on how many variables take each value (forward-checking).
   * - `Circuit <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/Circuit.java>`__
     - ``succ`` defines a Hamiltonian circuit: ``succ[i]`` is the successor of node ``i`` and the graph forms a single cycle covering all nodes.
   * - `SubCircuit <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/SubCircuit.java>`__
     - Weaker form of Circuit allowing sub-tours; used for open routing models.
   * - `CostAllDifferentDC <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/CostAllDifferentDC.java>`__
     - AllDifferent with an assignment cost matrix; filters both the all-different and the total-cost variable using min-cost matching.
   * - `CostCardinalityMaxDC <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/CostCardinalityMaxDC.java>`__
     - Global Cardinality Constraint (upper bounds only) combined with a cost variable: ``|{i | x[i]=v}| ≤ ub[v]`` and ``Σ cost[i][x[i]] ≤ H``. Filtering uses min-cost flow :cite:`schmied2025efficient`.
   * - `SoftCardinalityDC <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/SoftCardinalityDC.java>`__
     - Soft GCC: for each value ``v`` with bounds ``[lb[v], ub[v]]``, the per-value violation is ``max(0, lb[v]-c[v], c[v]-ub[v])``; a global ``viol`` variable equals the sum of violations :cite:`van2006global` :cite:`schaus2010revisiting`.
   * - `Element1D <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/Element1D.java>`__ / `Element1DDC <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/Element1DDC.java>`__
     - ``z = t[y]``: ``z`` equals the element of constant array ``t`` at index ``y``. DC variant achieves domain consistency.
   * - `Element1DVar <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/Element1DVar.java>`__
     - ``z = t[y]``: same as Element1D but ``t`` is an array of ``CPIntVar`` instead of constants.
   * - `Element2D <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/Element2D.java>`__
     - ``z = t[x][y]``: ``z`` equals the element of a 2-D constant matrix at row ``x``, column ``y``.
   * - `Equal <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/Equal.java>`__
     - ``x = y`` (or ``x = c``): equality between two integer variables or between a variable and a constant.
   * - `NotEqual <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/NotEqual.java>`__
     - ``x ≠ y`` (or ``x ≠ c``).
   * - `LessOrEqual <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/LessOrEqual.java>`__
     - ``x ≤ y`` (bounds-consistent).
   * - `Sum <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/Sum.java>`__
     - ``x[0] + x[1] + … + x[n-1] = s``: sum of an array of variables equals ``s`` (or a constant).
   * - `Maximum <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/Maximum.java>`__
     - ``m = max(x[0], …, x[n-1])``.
   * - `Mul <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/Mul.java>`__ / `MulVar <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/MulVar.java>`__ / `MulCte <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/MulCte.java>`__ / `MulCteRes <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/MulCteRes.java>`__
     - ``x · y = z`` and specialised variants: ``MulVar`` (three variables), ``MulCte`` (constant multiplier ``c·x = y``), ``MulCteRes`` (``x · y = c`` where ``c`` is fixed).
   * - `Square <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/Square.java>`__
     - ``y = x²``.
   * - `Absolute <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/Absolute.java>`__
     - ``y = |x|``.
   * - `Sorted <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/Sorted.java>`__
     - ``x[0] ≤ x[1] ≤ … ≤ x[n-1]`` and ``y`` is a sorted permutation of ``x``.
   * - `InversePerm <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/InversePerm.java>`__
     - ``y[x[i]] = i`` for all ``i``: ``x`` and ``y`` are inverse permutations.
   * - `Or <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/Or.java>`__
     - At least one Boolean variable in the array is true: ``x[0] ∨ x[1] ∨ … ∨ x[n-1]``.
   * - `IsOr <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/IsOr.java>`__
     - Reified disjunction: ``b ↔ (x[0] ∨ … ∨ x[n-1])``.
   * - `IsEqual <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/IsEqual.java>`__ / `IsEqualVar <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/IsEqualVar.java>`__
     - Reified equality: ``b ↔ (x = c)`` and ``b ↔ (x = y)``.
   * - `IsLessOrEqual <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/IsLessOrEqual.java>`__ / `IsLessOrEqualVar <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/IsLessOrEqualVar.java>`__
     - Reified inequality: ``b ↔ (x ≤ c)`` and ``b ↔ (x ≤ y)``.
   * - `TableCT <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/TableCT.java>`__
     - Positive extensional constraint: the assignment of ``x`` must match at least one row in table ``T``. Uses the Compact-Table (CT) filtering algorithm :cite:`demeulenaere2016compact`.
   * - `NegTableCT <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/NegTableCT.java>`__
     - Negative extensional constraint (forbidden tuples): the assignment must *not* match any row in ``T``.
   * - `ShortTableCT <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/ShortTableCT.java>`__
     - Short (starred) table constraint with wildcard ``*`` entries, filtered via CT.

**Scheduling constraints** (``org.maxicp.cp.engine.constraints.scheduling``)

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Constraint (source)
     - Semantics
   * - `NoOverlap <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/scheduling/NoOverlap.java>`__
     - No two interval variables in the group execute at the same time. Filtering uses Vilim's edge-finding and not-first/not-last algorithms (Theta-Lambda tree) :cite:`vilim2007global`.
   * - `NoOverlapBC <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/scheduling/NoOverlapBC.java>`__
     - Bounds-consistent (overload-check + detectable precedences only) variant of NoOverlap; faster but weaker.
   * - `NoOverlapBinary <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/scheduling/NoOverlapBinary.java>`__
     - Pairwise disjunctive constraint over exactly two interval variables: ``end(a) ≤ start(b) ∨ end(b) ≤ start(a)``.
   * - `NoOverlapBinaryWithTransitionTime <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/scheduling/NoOverlapBinaryWithTransitionTime.java>`__
     - Pairwise disjunctive constraint with a sequence-dependent setup time between two tasks.
   * - `CumulativeDecomposition <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/scheduling/CumulativeDecomposition.java>`__
     - Decomposition-based cumulative constraint (reference implementation).
   * - `EndBeforeStart <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/scheduling/EndBeforeStart.java>`__ and temporal ordering family
     - Temporal precedence constraints between interval variables. The family covers all combinations of ``{Start,End}Before/At{Start,End}``: e.g. ``EndBeforeStart(a,b)`` enforces ``end(a) ≤ start(b)``.
   * - `Alternative <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/scheduling/Alternative.java>`__
     - ``a`` is executed on exactly one of the alternative tasks ``b[0], …, b[k-1]``: the selected alternative mirrors the presence, start, and end of ``a``.

**Sequence-variable constraints** (``org.maxicp.cp.engine.constraints.seqvar``)

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Constraint (source)
     - Semantics
   * - `Insert <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/seqvar/Insert.java>`__
     - Inserts ``node`` immediately after ``pred`` in the partial sequence.
   * - `Exclude <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/seqvar/Exclude.java>`__
     - Removes ``node`` from all feasible extensions of the sequence.
   * - `Require <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/seqvar/Require.java>`__
     - Marks ``node`` as required: it must appear in every feasible sequence.
   * - `NotBetween <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/seqvar/NotBetween.java>`__
     - Forbids ``node`` from appearing between ``pred`` and ``succ`` in the sequence.
   * - `Precedence <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/seqvar/Precedence.java>`__
     - Node ``i`` must appear before node ``j`` whenever both are present in the sequence.
   * - `Distance <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/seqvar/Distance.java>`__
     - ``totalDist = Σ dist[pred(v)][v]`` over all consecutive pairs in the sequence :cite:`Schmied2024Distance`.
   * - `TransitionTimes <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/seqvar/TransitionTimes.java>`__
     - For each consecutive pair ``(u, v)`` in the sequence: ``time[u] + dist[u][v] ≤ time[v]``.
   * - `Cumulative <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/seqvar/Cumulative.java>`__
     - Pickup-and-delivery load constraint on a sequence variable: enforces capacity at every node, pickup-before-delivery ordering, and that both nodes of a request belong to the same sequence :cite:`thomas2020insertion`.
   * - `SubSequence <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/seqvar/SubSequence.java>`__
     - A given partial sequence ``sub`` must appear as a sub-sequence of the sequence variable.
   * - `RelaxedSequence <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/engine/constraints/seqvar/RelaxedSequence.java>`__
     - Fixes all non-relaxed nodes to follow their order in a reference solution while leaving relaxed nodes free for re-insertion. Used in LNS.
