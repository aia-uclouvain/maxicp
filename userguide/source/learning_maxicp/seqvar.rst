.. _seqvar:

************************************
Sequence Variables for Routing
************************************

MaxiCP introduces **sequence variables** (``CPSeqVar``) :cite:`Schaus2022SeqVar` :cite:`delecluse2022sequence`,
a dedicated computational domain
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
  Links the total route distance to a variable using a distance matrix :cite:`Schmied2024Distance`.

``transitionTimes(SeqVar, IntExpression[] time, int[][] dist)``
  Enforces temporal consistency: for each consecutive pair ``(u, v)`` in the sequence,
  ``time[u] + dist[u][v] ≤ time[v]``.

``cumulative(SeqVar, int[] pickups, int[] drops, int[] load, int capacity)``
  Simultaneously enforces: (i) capacity at every node; (ii) pickup precedes delivery;
  (iii) both nodes of a request are assigned to the same vehicle :cite:`thomas2020insertion`.

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
`CVRPTWSeqVar.java (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/CVRPTWSeqVar.java>`_

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
