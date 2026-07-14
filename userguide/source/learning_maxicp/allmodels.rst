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
   * - `CVRPTW <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/CVRPTWSeqVar.java>`__
     - Capacitated Vehicle Routing with Time Windows.
   * - `DARP <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/darp/DARP.java>`__
     - Dial-A-Ride Problem with ride-time constraints.
   * - `InventoryScheduling <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/InventoryScheduling.java>`__
     - Inventory Scheduling.
   * - `JobShop <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/JobShop.java>`__
     - Job-Shop scheduling with ``noOverlap`` and ``Rank`` search.
   * - `MagicSerie <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/MagicSerie.java>`__
     - Magic Series.
   * - `MagicSquare <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/magicsquare/MagicSquare.java>`__
     - Magic Square.
   * - `MaxIndependentSet <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/MaxIndependentSet.java>`__
     - Maximum Independent Set.
   * - `NQueens <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/nqueens/NQueens.java>`__
     - N-Queens with first-fail binary search.
   * - `NurseScheduling <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/NurseScheduling.java>`__
     - Nurse Scheduling.
   * - `PerfectSquare <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/PerfectSquare.java>`__
     - Perfect Square.
   * - `Pigeonhole <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/Pigeonhole.java>`__
     - Pigeonhole Problem.
   * - `ProducerConsumer <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/ProducerConsumer.java>`__
     - Producer-consumer scheduling with ``alwaysIn``.
   * - `QAP <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/QAP.java>`__
     - Quadratic Assignment Problem with LNS.
   * - `RCPSP <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/RCPSP.java>`__
     - Resource-Constrained Project Scheduling with cumulative functions and FDS.
   * - `SMIC <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/SMIC.java>`__
     - SMIC.
   * - `SMoney <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/SMoney.java>`__
     - Send More Money.
   * - `ShipLoading <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/ShipLoading.java>`__
     - Ship Loading.
   * - `SportScheduling <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/SportScheduling.java>`__
     - Sport Scheduling.
   * - `TSP <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/tsp/TSP.java>`__
     - Traveling Salesman Problem.
   * - `TSPTW <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/raw/tsptw/TSPTW.java>`__
     - Traveling Salesman with Time Windows using a sequence variable.

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
   * - `CuSP <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/CuSP.java>`__
     - CuSP.
   * - `CVRPTW <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/VRPTWSeqVar.java>`__
     - CVRPTW using the modeling API with insertion-based LNS.
   * - `DARPSeqVar <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/DARPSeqVar.java>`__
     - Dial-A-Ride Problem.
   * - `Eternity <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/Eternity.java>`__
     - Eternity II Puzzle.
   * - `FlexibleJobShop <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/FlexibleJobShop.java>`__
     - Flexible Job-Shop.
   * - `JobShop <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/JobShop.java>`__
     - Job-Shop with the modeling API.
   * - `MagicSquare <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/MagicSquare.java>`__
     - Magic Square.
   * - `NQueens <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/nqueens/NQueens.java>`__
     - N-Queens using the ``ModelDispatcher`` with parallel EPS variant.
   * - `PDPSeqVar <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/PDPSeqVar.java>`__
     - Pickup and Delivery Problem.
   * - `PerfectSquare <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/PerfectSquare.java>`__
     - Perfect Square.
   * - `ProducerConsumer <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/ProducerConsumer.java>`__
     - Producer-Consumer.
   * - `QAP <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/QAP.java>`__
     - Quadratic Assignment Problem.
   * - `RCPSP <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/RCPSP.java>`__
     - RCPSP with the modeling API and FDS.
   * - `SMIC <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/SMIC.java>`__
     - SMIC.
   * - `ShipLoading <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/ShipLoading.java>`__
     - Ship Loading.
   * - `SportScheduling <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/SportScheduling.java>`__
     - Sport Scheduling.
   * - `StableMarriage <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/StableMarriage.java>`__
     - Stable Marriage.
   * - `Steel <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/Steel.java>`__
     - Steel Mill Slab.
   * - `TSP <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/tsp/TSP.java>`__
     - Traveling Salesman Problem.
   * - `TSPTW <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/tsptw/TSPTW.java>`__
     - Traveling Salesman with Time Windows.

We recommend using the **modeling API** for most use cases:
it is more user-friendly, supports model transformations, and enables parallelization
out of the box.
