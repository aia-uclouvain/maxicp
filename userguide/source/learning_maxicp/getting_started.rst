.. _getting_started:

***************
Getting Started
***************

This chapter walks you through MaxiCP from scratch.
We will model and solve the N-Queens problem — the canonical *"hello world"* of
Constraint Programming — using **both** APIs that MaxiCP provides, explain every step in
detail, and show you how to read the solver's output.

The CP Paradigm in One Sentence
================================

Constraint Programming = **Model** + **Search**.

You declare *variables* (the decisions you want to make) and *constraints* (the rules they
must respect).
The solver then searches for assignments that satisfy all constraints,
pruning large portions of the search space by propagating the constraints at every step.

Two APIs at a Glance
=====================

MaxiCP offers two complementary ways to write a model:

.. list-table::
   :widths: 20 40 40
   :header-rows: 1

   * -
     - **Raw API**
     - **Modeling (symbolic) API**
   * - Entry point
     - ``CPSolver cp = CPFactory.makeSolver()``
     - ``ModelDispatcher model = makeModelDispatcher()``
   * - Variables
     - ``CPIntVar``, ``CPBoolVar``, ``CPIntervalVar``, ``CPSeqVar``
     - ``IntVar``, ``IntervalVar``, ``SeqVar``
   * - Constraints
     - ``cp.post(allDifferent(q))``
     - ``model.add(allDifferent(q))``
   * - Branching closure adds to
     - The **concrete** solver state
     - The **symbolic** model (immutable list)
   * - Solve
     - ``makeDfs(cp, branching).solve()``
     - ``model.cpInstantiate()`` then ``dfSearch(...).solve()``
   * - Parallelism / LNS
     - Manual save/restore with ``optimizeSubjectTo``
     - Native: immutable model branches, ``symbolicCopy()``

Both APIs share the same constraint library, the same search heuristics
(``firstFailBinary``, ``fds``, ``setTimes``, …), and the same ``SearchStatistics`` output.

**When to use which?**

- Use the **Raw API** when you need maximum control: implementing custom constraints,
  experimenting with propagation internals, or following along with MiniCP exercises.
- Use the **Modeling API** for most production use-cases: it is more concise, supports
  model transformations, and enables embarrassingly parallel search out of the box.

The N-Queens Problem
======================

Place *n* queens on an *n×n* chessboard so that no two queens threaten each other.
Two queens threaten each other when they share a **row**, **column**, or **diagonal**.

**Variables.** Assign one queen per row. Let ``q[i]`` ∈ ``{0, …, n-1}`` be the
*column* of the queen in row ``i``.
By construction, no two queens share a row.

**Constraints.**

- *Different columns*: ``allDifferent(q)``  — no two queens in the same column.
- *Different left diagonals*: ``allDifferent(q[i] − i)``  — no two queens on the same ``/`` diagonal.
- *Different right diagonals*: ``allDifferent(q[i] + i)``  — no two queens on the same ``\`` diagonal.

This gives a compact, complete model with three global constraints.

Step-by-Step: Raw API
======================

Full source:
`NQueens (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/nqueens/NQueens.java>`__

**Step 1 — Create the solver and the variables**

.. code-block:: java

    int n = 8;
    CPSolver cp = CPFactory.makeSolver();     // (1)
    CPIntVar[] q  = CPFactory.makeIntVarArray(cp, n, n);  // (2)
    CPIntVar[] qL = CPFactory.makeIntVarArray(n, i -> minus(q[i], i));  // (3)
    CPIntVar[] qR = CPFactory.makeIntVarArray(n, i -> plus(q[i], i));   // (4)

1. ``CPFactory.makeSolver()`` creates a fresh solver backed by a ``Trailer``
   (trail-based state manager; see :ref:`state_management`).
2. ``makeIntVarArray(cp, n, n)`` creates ``n`` integer variables each with domain
   ``{0, 1, …, n-1}``.  ``q[i]`` represents the column of the queen in row ``i``.
3. ``minus(q[i], i)`` creates a *view* — a new ``CPIntVar`` whose domain is
   ``{v − i | v ∈ dom(q[i])}``, with no extra memory allocation.
   ``qL[i]`` encodes the left-diagonal index.
4. Similarly, ``qR[i] = q[i] + i`` encodes the right-diagonal index.

Views are first-class ``CPIntVar`` objects; any constraint can be posted on them.

**Step 2 — Post the constraints**

.. code-block:: java

    cp.post(allDifferent(q));   // no two queens in the same column
    cp.post(allDifferent(qL));  // no two queens on the same '/' diagonal
    cp.post(allDifferent(qR));  // no two queens on the same '\' diagonal

``cp.post(c)`` calls ``c.post()``, which registers the constraint to the relevant variable
events and runs an initial fixed-point propagation
(see :ref:`propagation`).
If propagation detects an empty domain, an ``InconsistencyException`` is thrown immediately.

``allDifferent`` uses Régin's arc-consistent filtering: it removes any value ``v`` from
``q[i]`` if assigning ``v`` to ``q[i]`` would force another variable to be empty.

**Step 3 — Define the search strategy**

.. code-block:: java

    DFSearch search = CPFactory.makeDfs(cp, () -> {         // (1)
        CPIntVar qs = selectMin(q,                          // (2)
            qi -> qi.size() > 1,                           // (3)
            qi -> qi.size());                              // (4)
        if (qs == null) return EMPTY;                      // (5)
        int v = qs.min();                                  // (6)
        return branch(
            () -> cp.post(eq(qs, v)),                      // (7)
            () -> cp.post(neq(qs, v)));                    // (8)
    });

1. ``makeDfs`` wraps the branching supplier into a ``DFSearch`` engine.
2. ``selectMin`` scans the array and returns the variable minimising the criterion (4).
3. The *filter* ``qi.size() > 1`` skips already-fixed variables.
4. The *criterion* ``qi.size()`` implements the **first-fail** heuristic: branch on the
   variable with the *smallest* domain to detect failures early.
5. ``null`` means all variables are fixed → a solution has been found; return ``EMPTY``
   to signal the search engine.
6. ``qs.min()`` picks the smallest value in the domain — the value selector.
7. **Left branch**: post ``qs = v``.  ``cp.post`` runs propagation immediately; an
   ``InconsistencyException`` triggers backtracking.
8. **Right branch**: post ``qs ≠ v``, then try the next value on the next call to the
   branching function.

This binary branching (assign / remove) is the most common pattern in MaxiCP.

**Step 4 — Run and read the output**

.. code-block:: java

    search.onSolution(() -> System.out.println(Arrays.toString(q)));
    SearchStatistics stats = search.solve();
    System.out.println(stats);

``onSolution`` registers a callback that fires every time the branching function returns
``EMPTY`` (all variables are fixed and no inconsistency was detected).
``solve()`` exhausts the search tree (use ``solve(stats -> stats.numberOfSolutions() >= 1)``
to stop after the first solution).

A typical ``SearchStatistics`` output::

    #Solutions: 92
    #Failures: 1307
    #Nodes: 1490
    Time(ms): 12

Step-by-Step: Modeling (Symbolic) API
======================================

Full source:
`NQueens (modeling) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/nqueens/NQueens.java>`__

The modeling API separates *model definition* from *resolution*.
The model is an immutable linked list of constraints; adding a constraint returns a **new**
model node without modifying the original.

**Step 1 — Create the ModelDispatcher and the symbolic variables**

.. code-block:: java

    int n = 12;
    ModelDispatcher model = makeModelDispatcher();   // (1)

    IntVar[] q  = model.intVarArray(n, n);           // (2)
    IntExpression[] qL = model.intVarArray(n, i -> q[i].plus(i));   // (3)
    IntExpression[] qR = model.intVarArray(n, i -> q[i].minus(i));  // (4)

1. ``makeModelDispatcher()`` (static import from ``org.maxicp.modeling.Factory``) creates
   the dispatcher that acts as both a *variable factory* and a *model proxy*.
2. ``model.intVarArray(n, n)`` creates ``n`` symbolic integer variables, each with domain
   ``{0, …, n-1}``.  These are ``IntVar`` objects — they carry no concrete domain yet.
3. ``q[i].plus(i)`` builds a symbolic *expression*.  Unlike the raw ``minus`` / ``plus``
   functions (which create concrete views), these are algebraic expression nodes in the
   symbolic tree.
4. Same for the right-diagonal expressions.

**Step 2 — Add the constraints to the symbolic model**

.. code-block:: java

    model.add(allDifferent(q));
    model.add(allDifferent(qL));
    model.add(allDifferent(qR));

``model.add(c)`` appends constraint ``c`` to the current symbolic model (an *O(1)*
immutable list prepend).  No solver, no domain, no propagation happens yet.

**Step 3 — Define the branching strategy**

.. code-block:: java

    Supplier<Runnable[]> branching = () -> {
        IntExpression qs = selectMin(q,
                qi -> qi.size() > 1,
                qi -> qi.size());
        if (qs == null) return EMPTY;
        int v = qs.min();
        return branch(
            () -> model.add(eq(qs, v)),    // left: add eq to the symbolic model
            () -> model.add(neq(qs, v))); // right: add neq to the symbolic model
    };

The branching logic looks identical to the raw version.
The key difference: each closure calls ``model.add(...)`` instead of ``cp.post(...)``.
During search, the ``ConcreteCPModel`` intercepts these symbolic additions, instantiates
the constraint into the underlying CP engine, and runs propagation.

**Step 4 — Concretize and solve**

.. code-block:: java

    ConcreteCPModel cp = model.cpInstantiate();   // (1)
    DFSearch dfs = cp.dfSearch(branching);         // (2)
    dfs.onSolution(() -> System.out.println(Arrays.toString(q)));
    SearchStatistics stats = dfs.solve();
    System.out.println(stats);

1. ``cpInstantiate()`` iterates over the symbolic constraint list, creates a fresh
   ``MaxiCP`` solver instance, and instantiates every constraint into it.
2. ``dfSearch(branching)`` wraps the branching supplier in a ``DFSearch`` exactly as in
   the raw API.

The output is identical to the raw version.

Side-by-Side Comparison
=========================

.. list-table::
   :widths: 50 50
   :header-rows: 1

   * - Raw API
     - Modeling API
   * - ``CPSolver cp = makeSolver();``
     - ``ModelDispatcher model = makeModelDispatcher();``
   * - ``CPIntVar[] q = makeIntVarArray(cp, n, n);``
     - ``IntVar[] q = model.intVarArray(n, n);``
   * - ``CPIntVar[] qL = makeIntVarArray(n, i -> minus(q[i],i));``
     - ``IntExpression[] qL = model.intVarArray(n, i -> q[i].minus(i));``
   * - ``cp.post(allDifferent(q));``
     - ``model.add(allDifferent(q));``
   * - ``() -> cp.post(eq(qs, v))``  (branch closure)
     - ``() -> model.add(eq(qs, v))``  (branch closure)
   * - ``makeDfs(cp, branching).solve()``
     - ``model.cpInstantiate(); cp.dfSearch(branching).solve()``

The surface syntax is deliberately close. The fundamental difference is *when* things
happen: in the raw API every ``cp.post`` call immediately modifies solver state and runs
propagation; in the modeling API, ``model.add`` merely extends an immutable linked list
and propagation only starts at concretization.

Using the Built-In First-Fail Heuristic
=========================================

Writing a branching closure by hand gives full flexibility, but MaxiCP also provides
pre-built heuristics in the ``Searches`` class.
The three snippets below all produce **the same search tree** for N-Queens:

.. code-block:: java

    // (a) most concise — one-liner
    DFSearch s1 = makeDfs(cp, firstFailBinary(q));

    // (b) explicit variable selector, default value = minimum
    DFSearch s2 = makeDfs(cp, heuristicBinary(minDomVariableSelector(q)));

    // (c) explicit variable and value selectors
    DFSearch s3 = makeDfs(cp,
        heuristicBinary(
            minDomVariableSelector(q),   // variable: smallest domain
            x -> x.min()));              // value: smallest in domain

Variant (c) is the most flexible: you can replace either selector independently.
For example, try ``x -> (x.min() + x.max()) / 2`` to branch on the domain midpoint.

Stopping Early and Limiting the Search
========================================

``solve()`` has several overloads to control how much of the tree is explored:

.. code-block:: java

    // Stop after the first solution
    search.solve(stats -> stats.numberOfSolutions() >= 1);

    // Stop after 1 000 failures
    search.solve(stats -> stats.numberOfFailures() >= 1_000);

    // Stop after 10 seconds
    search.solve(stats -> stats.timeInMillis() > 10_000);

The lambda receives live ``SearchStatistics`` and the search stops as soon as it
returns ``true``.

Optimisation
=============

To *minimise* an objective variable, wrap the search with ``optimize``:

.. code-block:: java

    CPIntVar cost = sum(weightedDist);
    Objective obj = cp.minimize(cost);

    SearchStatistics stats = search.optimize(obj);
    // or with a time limit:
    // search.optimize(obj, stats -> stats.timeInMillis() > 60_000);

Each time a new (improving) solution is found, the solver automatically adds the constraint
``cost < bestCost``, pruning all subtrees that cannot improve on the incumbent.

What Comes Next
================

Now that you understand the basic building blocks, explore the deeper sections:

- :ref:`state_management` — how the trail enables backtracking.
- :ref:`propagation` — the fixed-point engine, event-driven scheduling, and how to write
  your own propagators.
- :ref:`maxicp_search` — all built-in heuristics, combinators, LNS, and parallel search.
- :ref:`scheduling` — conditional interval variables, cumulative functions, Job-Shop, RCPSP.
- :ref:`seqvar` — sequence variables for routing (TSPTW, CVRPTW, DARP).
- :ref:`symbolic_modeling` — the symbolic layer in depth: model trees, EPS, LNS neighborhoods.

