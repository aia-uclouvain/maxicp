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
