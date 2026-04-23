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
