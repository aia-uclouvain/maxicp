.. _architecture:

*****************************
Architecture Overview
*****************************

MaxiCP is implemented in Java (version 17+) and consists of approximately 40,000 lines of code.
Its architecture is organized into several coherent packages:

.. list-table::
   :widths: 35 65
   :header-rows: 1

   * - Package
     - Role
   * - ``org.maxicp.state``
     - State management primitives and data structures for backtracking (trailing).
   * - ``org.maxicp.cp.engine.core``
     - Core CP engine: solver (``MaxiCP``), variable implementations (``CPIntVar``, ``CPBoolVar``, ``CPSeqVar``, ``CPIntervalVar``), domains, and the priority-based propagation queue.
   * - ``org.maxicp.cp.engine.constraints``
     - Constraint implementations, organized into sub-packages for specific computation domains.
   * - ``org.maxicp.modeling``
     - Symbolic modeling layer: symbolic variables (``IntVar``, ``SeqVar``, ``IntervalVar``), the ``Factory`` with constraint creation methods, and the symbolic model representation.
   * - ``org.maxicp.cp.modeling``
     - Bridge between the symbolic and concrete CP layers. ``ConcreteCPModel`` instantiates symbolic constraints into the CP engine.
   * - ``org.maxicp.search``
     - Search strategies: ``DFSearch``, ``ConcurrentDFSearch``, ``BestFirstSearch``, and a rich library of variable and value selection heuristics (``Searches``).

Browse the full source on GitHub:
`src/main/java/org/maxicp <https://github.com/aia-uclouvain/maxicp/tree/main/src/main/java/org/maxicp>`__

Two Levels of Modeling: Raw and Symbolic
=========================================

A distinguishing feature of MaxiCP is that it offers *two* ways to model and solve problems.

**1. The raw (engine-level) API** directly creates concrete CP objects (``CPIntVar``, ``CPSeqVar``, ``CPIntervalVar``), posts constraints via ``cp.post(...)``, and applies branching through closures. This API gives maximal control, mirroring the design of MiniCP :cite:`Michel2021MiniCP`.

**2. The symbolic (modeling-level) API** :cite:`Derval2023Symbolism` works with symbolic variable objects and immutable model nodes. The model is an immutable linked list; adding a constraint returns a new model node and propagation only starts at concretization. This layer enables model transformations, LNS neighborhoods as model branches, and embarrassingly parallel search :cite:`regin2013embarrassingly`.

Same Architecture as MiniCP
=============================

The core architecture of MaxiCP is derived from
`MiniCP <https://www.minicp.org>`_ :cite:`Michel2021MiniCP`.
MaxiCP also incorporates ideas from OscaR :cite:`oscar` and ObjectiveCP :cite:`van2013objective`.
To understand the lower-level design decisions (trailing, sparse sets, event-driven propagation),
we recommend reading :cite:`Michel2021MiniCP`.
