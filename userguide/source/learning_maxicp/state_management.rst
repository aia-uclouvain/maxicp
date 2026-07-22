.. _state_management:

*****************
State Management
*****************

CP solvers must efficiently save and restore states during search.
MaxiCP is a *trail-based* solver: rather than copying the entire state at each choice point,
it records only the incremental changes that occur during propagation and search on a stack called the *trail*.
When the solver backtracks, it replays those changes in reverse to restore the previous state.
We refer to :cite:`Michel2021MiniCP` for a comprehensive introduction to trailing and its implementation in CP solvers.

The ``StateManager`` Interface
==============================

Source:
`StateManager.java <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/state/StateManager.java>`_

.. code-block:: java

    public interface StateManager {
        void saveState();               // Push a new save point
        void restoreState();            // Restore to last save point
        int getLevel();                 // Current depth in the search tree

        StateInt    makeStateInt(int initValue);
        <T> State<T> makeStateRef(T initValue);
        <K,V> StateMap<K,V> makeStateMap();
    }

All stateful components of the solver — variable domains, propagation queues, the DFS engine,
and even the symbolic model reference — are built on top of these primitives.
For instance, the domain of a ``CPIntVar`` is backed by ``StateInt`` (a reversible integer) for
its minimum and maximum bounds, and by a reversible sparse set for the full set of values.

The default implementation is
`Trailer.java <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/state/trail/Trailer.java>`_.

Usage Example
=============

.. code-block:: java

    StateManager sm = new Trailer();
    StateInt counter = sm.makeStateInt(0);

    sm.saveState();           // Level 0 -> Level 1
    counter.setValue(10);     // Trail records: (counter, old=0)

    sm.saveState();           // Level 1 -> Level 2
    counter.setValue(42);     // Trail records: (counter, old=10)

    sm.restoreState();        // Level 2 -> Level 1: counter = 10
    sm.restoreState();        // Level 1 -> Level 0: counter = 0

Every time the solver descends in the search tree it calls ``saveState()``,
pushing a new level onto the trail stack.
Any modification performed at that level is automatically undone when the solver
calls ``restoreState()`` on backtrack.

Reversible Data Structures
===========================

Source:
`state/datastructures <https://github.com/aia-uclouvain/maxicp/tree/main/src/main/java/org/maxicp/state/datastructures>`_

The ``org.maxicp.state.datastructures`` package provides higher-level reversible
data structures built on top of ``StateManager``:

- ``StateStack<T>`` — a reversible stack.
- ``StateSparseSet`` — a reversible sparse set (used for integer variable domains) :cite:`sparsets2013domain`.
- ``StateTriPartition`` — a reversible three-way partition of a universe (required / possible / excluded); used by sequence variables.
- ``StateMap<K,V>`` — a reversible map backed by a ``HashMap``.

