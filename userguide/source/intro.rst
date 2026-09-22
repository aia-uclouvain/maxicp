.. _intro:



*******
Preface
*******

This document is the user guide for MaxiCP. It covers installation, core concepts, and
a tour of all major features through annotated code examples. Readers looking for an
introduction to constraint programming in general are referred to the
`MiniCP paper <https://doi.org/10.1007/s12532-020-00190-7>`_ and related literature.


What is MaxiCP?
===============

**MaxiCP** is an open-source (MIT licence) Java-based Constraint Programming (CP) solver
for solving scheduling and vehicle routing problems.
It is an extended version of `MiniCP <http://www.minicp.org>`_, a lightweight,
open-source CP solver mostly used for teaching constraint programming.

The key features of MaxiCP are:

- **Improved performances** (support for delta-based propagation, more efficient data structures, etc.).
- **Symbolic modeling layer** also enabling search declaration.
- **Support for Embarrassingly Parallel Search**.
- **More global constraints** (e.g., bin-packing, gcc, soft-gcc, etc.).
- **Sequence variables with optional visits** for modeling complex vehicle routing and insertion-based search heuristics, including LNS.
- **Conditional task interval variables** including support for modeling with cumulative function expressions for scheduling problems.

MaxiCP is simultaneously well-suited for education, research, and practical deployment.

If you use MaxiCP in your research, please cite:

.. code-block:: bibtex

    @misc{maxicp,
      title        = {{MaxiCP: A Not So Mini Constraint Programming Solver}},
      author       = {Pierre Schaus and Guillaume Derval and Augustin Delecluse and Laurent Michel and Pascal Van Hentenryck},
      year         = {2026},
      howpublished = {\url{http://www.maxicp.org/}},
    }

Other people having contributed to the project are: Hélène Verhaeghe, Charles Thomas, Roger Kameugne, Emma Legrand, Alice Burlats, Margaux Schmied, Amaury Guichard.


Javadoc
=======

- `Javadoc of the main branch <http://www.maxicp.org/javadoc/org.maxicp/module-summary.html>`_
- `Javadoc of stable releases <https://javadoc.io/doc/org.maxicp/maxicp/latest/org.maxicp/module-summary.html>`_

Tech Report
===========

A detailed description of MaxiCP's architecture, features, and design decisions is available
as a downloadable tech report:

`MaxiCP: A Not So Mini Constraint Programming Solver (PDF) <https://aia-uclouvain.github.io/maxicp/tech-report/maxicp.pdf>`_

The tech report covers state management, the propagation engine, all variable types
(integer, interval, sequence), global constraints, search strategies, scheduling,
routing, and the symbolic modeling layer.

.. _install:

Install MaxiCP
==============

MaxiCP source code is available on `GitHub <https://github.com/aia-uclouvain/maxicp>`_.

Using MaxiCP as a Maven Dependency
------------------------------------

Stable releases are published on `Maven Central <https://central.sonatype.com/artifact/org.maxicp/maxicp>`_.
Add the following to your ``pom.xml``:

.. code-block:: xml

    <dependency>
        <groupId>org.maxicp</groupId>
        <artifactId>maxicp</artifactId>
        <version>0.0.2</version>
    </dependency>

If you need the latest development version from the ``main`` branch, use
`JitPack <https://jitpack.io/#aia-uclouvain/maxicp>`_.

Cloning and Building from Source
----------------------------------

.. code-block:: bash

    git clone https://github.com/aia-uclouvain/maxicp.git
    cd maxicp
    mvn compile   # compile the project
    mvn test      # run the full test suite

Useful Maven commands:

.. code-block:: bash

    mvn jacoco:report       # coverage report → target/site/jacoco/index.html
    mvn javadoc:javadoc     # javadoc        → target/site/apidocs/index.html

Recommended IDE: IntelliJ IDEA
---------------------------------

1. **Clone the repository** (see above).
2. Launch **IntelliJ IDEA**, choose *File → Open*, navigate to the ``maxicp`` folder,
   and open ``pom.xml`` as a new project.
3. To run all tests: right-click on ``src/test/java`` → *Run 'All Tests'*.


Getting Help
============

- File a bug report or feature request on the `GitHub issue tracker <https://github.com/aia-uclouvain/maxicp/issues>`_.
- Contact the authors by email.

Who Uses MaxiCP?
=================

If you use MaxiCP for teaching or research, please let us know and we will add you to this list.

* UCLouvain, `AIA <https://aia.info.ucl.ac.be/people/>`_ — Researchers in the group of Pierre Schaus and Hélène Verhaeghe.
