.. _xcsp3:

*******************************************
Solving XCSP3 Instances (CLI and Java API)
*******************************************

`XCSP3 <https://www.xcsp.org/>`__ :cite:`lecoutre2020xcsp3` is the standard XML-based format for representing combinatorial constrained problems — both satisfaction (**CSP**) and optimization (**COP**) — used in the International XCSP3 Solver Competitions.

MaxiCP includes a built-in XCSP3 parser and competition runner in the ``org.maxicp.modeling.xcsp3`` package that translates XCSP3 instances (plain ``.xml`` or compressed ``.xml.lzma``) into a symbolic ``ModelDispatcher`` and solves them either with **Black-Box Search** (:ref:`maxicp_search`) or with a custom search strategy.

Source:

* `XCSP3 <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/modeling/xcsp3/XCSP3.java>`__ — XCSP3 parser and ``XCSP3LoadedInstance`` loader
* `XCSP3Cli <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/modeling/xcsp3/XCSP3Cli.java>`__ — Competition CLI entrypoint


Running an XCSP3 Instance from the Command Line
=================================================

There are two ways to solve an XCSP3 instance from the terminal: using the standalone competition bundle (recommended) or invoking ``XCSP3Cli`` directly via Java/Maven.

Option 1 — Standalone ``maxicp-mini`` Bundle (Recommended)
-----------------------------------------------------------

From the repository root, build the self-contained competition bundle:

.. code-block:: bash

    ./scripts/package-mini-xcsp3.sh

This script compiles MaxiCP, copies all runtime JARs into ``mini-xcsp3/lib/``, and generates the executable launcher ``mini-xcsp3/maxicp-mini``.

You can then run any ``.xml`` or ``.xml.lzma`` XCSP3 instance:

.. code-block:: bash

    # Solve a satisfaction (CSP) instance
    ./mini-xcsp3/maxicp-mini src/test/resources/XCSP3/CSP/HyperSudoku-03_c24.xml

    # Optimize a COP instance with a 10-second time limit and fixed random seed
    ./mini-xcsp3/maxicp-mini --time-limit=10 --seed=42 src/test/resources/XCSP3/COP/LitPuzzle-10_c24.xml

    # Run a compressed .xml.lzma instance
    ./mini-xcsp3/maxicp-mini --time-limit=30 data/XCSP3/2024_V3/MiniCOP/Fortress1-02-mini_c25.xml.lzma

Supported CLI arguments:

.. list-table::
   :widths: 32 68
   :header-rows: 1

   * - Argument
     - Description
   * - ``<path>``
     - Path to the XCSP3 instance file (``.xml`` or ``.xml.lzma``).
   * - ``--time-limit=<seconds>``
     - Wall-clock time limit in seconds (defaults to unlimited if omitted).
   * - ``--seed=<long>`` *(or 2nd positional arg)*
     - Random seed used for tie-breaking, restarts, and LNS neighborhoods.
   * - ``--mem-limit=...`` / ``--tmpdir=...``
     - Accepted for compatibility with XCSP3 competition evaluation wrappers.

Option 2 — Running ``XCSP3Cli`` Directly with ``java``
-------------------------------------------------------

Once packaged (or after ``mvn compile dependency:copy-dependencies``), you can also invoke the ``org.maxicp.modeling.xcsp3.XCSP3Cli`` class directly:

.. code-block:: bash

    java -cp "mini-xcsp3/lib/*" org.maxicp.modeling.xcsp3.XCSP3Cli \
        --time-limit=10 \
        src/test/resources/XCSP3/COP/LitPuzzle-10_c24.xml

Reading the Competition Output Protocol
----------------------------------------

``XCSP3Cli`` follows the official XCSP3 competition output stream protocol:

* ``o <value>`` — printed each time a new improving objective value ``<value>`` is found (for COP instances).
* ``s <status>`` — final status line:

  * ``s SATISFIABLE`` — at least one feasible solution was found (and optimality was not proven for COP).
  * ``s OPTIMUM FOUND`` — an optimal solution was found and proven (for COP).
  * ``s UNSATISFIABLE`` — the instance was proven infeasible.
  * ``s UNKNOWN`` — no solution was found within the time limit.
  * ``s UNSUPPORTED`` — the instance uses an XCSP3 feature not supported by the parser.

* ``v <instantiation> ... </instantiation>`` — the variable assignment of the best solution found, formatted as a valid XCSP3 ``<instantiation>`` element.

Example output on an optimization instance::

    o 18
    o 15
    o 14
    s OPTIMUM FOUND
    v <instantiation> <list> x[0] x[1] x[2] ... </list> <values> 1 0 1 ... </values> </instantiation>


Running an XCSP3 Instance in Java
===================================

You can also load and solve XCSP3 instances directly from your Java code using ``XCSP3.load(...)`` or ``XCSP3.runWithBlackBox(...)``.

Quick One-Liner with Validation (``XCSP3.runWithBlackBox``)
------------------------------------------------------------

For quick experiments, ``XCSP3.runWithBlackBox(path, timeoutInSeconds)`` loads the instance, runs ``BlackBoxSearch`` with progress logging, and automatically validates the resulting ``<instantiation>`` with the official XCSP3 ``SolutionChecker``:

.. code-block:: java

    import org.maxicp.modeling.xcsp3.XCSP3;

    public class RunXCSP3Demo {
        public static void main(String[] args) throws Exception {
            XCSP3.runWithBlackBox("src/test/resources/XCSP3/COP/LitPuzzle-10_c24.xml", 10);
        }
    }

Loading an Instance and Solving with ``BlackBoxSearch``
--------------------------------------------------------

``XCSP3.load(filename)`` parses the XML file and returns an ``XCSP3LoadedInstance`` record (which implements ``AutoCloseable``):

* ``instance.md()`` — the ``ModelDispatcher`` containing all parsed variables, variable groups, and constraints.
* ``instance.decisionVars()`` — the array of decision variables (``IntExpression[]``).
* ``instance.decisionVarIds()`` — the XCSP3 variable identifiers (``String[]``).
* ``instance.objective()`` — the symbolic ``Objective`` to optimize, or ``null`` for satisfaction (CSP) instances.
* ``instance.formatSolution(result)`` / ``instance.solutionGenerator()`` — formats a solution into an XCSP3 ``<instantiation>`` XML string.

Here is how to load an instance, solve or optimize it with ``ModelingBlackBox``, and validate the solution:

.. code-block:: java

    import org.maxicp.modeling.xcsp3.XCSP3;
    import org.maxicp.search.blackbox.ModelingBlackBox;
    import org.xcsp.parser.callbacks.SolutionChecker;
    import java.io.ByteArrayInputStream;

    String path = "src/test/resources/XCSP3/COP/LitPuzzle-10_c24.xml";
    int timeoutSeconds = 10;

    try (XCSP3.XCSP3LoadedInstance instance = XCSP3.load(path)) {
        ModelingBlackBox.Result result = instance.objective() != null
                ? ModelingBlackBox.optimize(instance.md(), instance.objective(), timeoutSeconds)
                : ModelingBlackBox.solve(instance.md(), timeoutSeconds);

        System.out.println("Status: " + result.status());
        result.objectiveValue().ifPresent(obj -> System.out.println("Best objective: " + obj));

        if (result.solution().isPresent()) {
            String solXml = instance.formatSolution(result);
            System.out.println(solXml);

            // Optional: verify the solution with the official XCSP3 SolutionChecker
            SolutionChecker checker = new SolutionChecker(
                    false, path, new ByteArrayInputStream(solXml.getBytes()));
            assert checker.invalidObjs.isEmpty() && checker.violatedCtrs.isEmpty();
        }
    }

Solving an XCSP3 Instance with a Custom Search Strategy
--------------------------------------------------------

Because ``XCSP3.load(path)`` populates a standard ``ModelDispatcher``, you can also concretize the model with ``runCP`` and apply any MaxiCP search heuristic (such as ``firstFailBinary``, ``conflictOrderingSearch``, or ``fds``) instead of ``BlackBoxSearch``:

.. code-block:: java

    import org.maxicp.modeling.xcsp3.XCSP3;
    import org.maxicp.search.DFSearch;
    import org.maxicp.search.SearchStatistics;
    import org.maxicp.search.Searches;

    String path = "src/test/resources/XCSP3/CSP/HyperSudoku-03_c24.xml";

    try (XCSP3.XCSP3LoadedInstance instance = XCSP3.load(path)) {
        instance.md().runCP(cp -> {
            DFSearch dfs = cp.dfSearch(Searches.firstFailBinary(instance.decisionVars()));

            dfs.onSolution(() -> {
                String xmlInstantiation = instance.solutionGenerator().get();
                System.out.println(xmlInstantiation);
            });

            SearchStatistics stats = instance.objective() != null
                    ? dfs.optimize(instance.objective())
                    : dfs.solve(s -> s.numberOfSolutions() >= 1);

            System.out.println(stats);
        });
    }


Supported XCSP3 Constraints and Objectives
===========================================

MaxiCP's XCSP3 parser supports the core XCSP3 / Mini-Track and Main-Track integer constraint families:

* **Intensional & Primitive**: ``intension`` (arbitrary boolean/arithmetic syntax trees including ``add``, ``sub``, ``mul``, ``div``, ``mod``, ``pow``, ``sqr``, ``abs``, ``dist``, ``min``, ``max``, ``if``, ``in``, ``notin``, relational and logical operators), ``primitive`` unary/binary/ternary constraints, ``clause``, and ``logic``.
* **Extensional**: ``extension`` (unary and *n*-ary positive and negative table constraints, including starred ``*`` tuples via ``ShortTableCT`` / ``TableCT`` / ``NegTable``).
* **Comparison & Ordering**: ``allDifferent`` (including ``except`` and ``allDifferentList``), ``allEqual``, ``notAllEqual``, ``ordered`` / ``lex`` / ``lexMatrix``.
* **Counting & Summing**: ``sum`` (weighted, unweighted, and view/expression sums with ``EQ``, ``NE``, ``LT``, ``LE``, ``GT``, ``GE``, ``IN``, ``NOTIN`` conditions), ``count``, ``atLeast``, ``atMost``, ``exactly``, ``among``, ``cardinality`` (closed/open with constant or variable occurrences), ``nValues``.
* **Connection & Indexing**: ``element`` (1D and 2D constant or variable arrays/matrices), ``minimum``, ``maximum``, ``channel`` (permutation, inverse with offset, and boolean indicator channeling), ``instantiation``.
* **Languages, Graphs & Packing**: ``regular`` (DFA), ``mdd`` (Multi-valued Decision Diagrams), ``circuit``, ``noOverlap`` (1D disjunctive and *k*-D diffn), ``cumulative``, ``binPacking``, ``knapsack``.
* **Objectives**: ``minimize`` and ``maximize`` on ``EXPRESSION``, ``SUM``, ``PRODUCT``, ``MINIMUM``, ``MAXIMUM``, and ``NVALUES``.

