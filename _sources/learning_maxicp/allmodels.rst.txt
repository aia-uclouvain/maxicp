.. _allmodels:

*****************************************************************
List of Example Models
*****************************************************************

The project ships two parallel sets of example models.

Raw API examples
==================

Package:
`org.maxicp.cp.examples.raw <https://github.com/aia-uclouvain/maxicp/tree/main/src/main/java/org/maxicp/cp/examples/raw>`__

These examples use MaxiCP's **raw implementation objects** directly, giving full control
over the CP solver internals. They are the closest to what you write when implementing
custom constraints or search heuristics.

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Example
     - Description
   * - `NQueens (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/nqueens/NQueens.java>`__
     - N-Queens with first-fail binary search.
   * - `QAP (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/qap/QAP.java>`__
     - Quadratic Assignment Problem with LNS.
   * - `JobShop (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/jobshop/JobShop.java>`__
     - Job-Shop scheduling with ``noOverlap`` and ``Rank`` search.
   * - `RCPSP (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/rcpsp/RCPSP.java>`__
     - Resource-Constrained Project Scheduling with cumulative functions and FDS.
   * - `ProducerConsumer (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/producerconsumer/ProducerConsumer.java>`__
     - Producer-consumer scheduling with ``alwaysIn``.
   * - `TSPTW (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/tsptw/TSPTW.java>`__
     - Traveling Salesman with Time Windows using a sequence variable.
   * - `CVRPTW (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/cvrptw/CVRPTW.java>`__
     - Capacitated Vehicle Routing with Time Windows.
   * - `DARP (raw) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/darp/DARP.java>`__
     - Dial-A-Ride Problem with ride-time constraints.

Modeling API examples
======================

Package:
`org.maxicp.cp.examples.modeling <https://github.com/aia-uclouvain/maxicp/tree/main/src/main/java/org/maxicp/cp/examples/modeling>`__

These examples use the **high-level symbolic modeling API**, which is then instantiated
into raw CP objects. They are more concise and give access to the full range of MaxiCP
features including embarrassingly parallel search.

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Example
     - Description
   * - `NQueens (modeling) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/nqueens/NQueens.java>`__
     - N-Queens using the ``ModelDispatcher`` with parallel EPS variant.
   * - `JobShop (modeling) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/jobshop/JobShop.java>`__
     - Job-Shop with the modeling API.
   * - `RCPSP (modeling) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/rcpsp/RCPSP.java>`__
     - RCPSP with the modeling API and FDS.
   * - `CVRPTW (modeling) <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/cvrptw/CVRPTW.java>`__
     - CVRPTW using the modeling API with insertion-based LNS.

We recommend using the **modeling API** for most use cases:
it is more user-friendly, supports model transformations, and enables parallelization
out of the box.
