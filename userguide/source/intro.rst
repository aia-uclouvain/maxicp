.. _intro:



*******
Preface
*******

This document is made for anyone who is looking for documentation on MaxiCP


What is MaxiCP
==============

**MaxiCP** is an open-source (MIT licence) Java-based Constraint Programming (CP) solver
for solving scheduling and vehicle routing problems.
It is an extended version of the [MiniCP](https://www.minicp.org), a lightweight,
open-source CP solver mostly used for teaching constraint programming.

The key features of MaxiCP are:

- **Improved performances** (support for delta-based propagation, more efficient data structures, etc.).
- **Symbolic modeling layer** also enabling search declaration.
- **Support for Embarrasingly Parallel Search**.
- **More global constraints** (e.g., bin-packing, gcc, etc.).
- **Sequence variables with optional visits** for modeling complex vehicle routing and insertion based search heuristics, including LNS.
- **Conditional task interval variables** including support for modeling with cumulative function expressions for scheduling problem.


MaxiCP is aimed to be used in real-life project and research in Constraint Programming.


Javadoc
=======

The `Javadoc API <https://aia-uclouvain.github.io/maxicp/javadoc/>`_.

.. _install:

Install MaxiCP
==============

MaxiCP source code is available from github_.

**Using an IDE**

We recommend using IntelliJ_.

From IntelliJ_ you can import the project:

.. code-block:: none

    Open > (select pom.xml in the minicp directory and open as new project)



**From the command line**

Using maven_ command line you can do:


.. code-block:: none

    $mvn compile # compile all the project
    $mvn test    # run all the test suite

Some other useful commands:

.. code-block:: none

    $mvn jacoco:report          # creates a cover report in target/site/jacoco/index.html
    $mvn javadoc:javadoc        # creates javadoc in target/site/apidocs/index.html


.. _github: https://github.com/aia-uclouvain/maxicp
.. _IntelliJ: https://www.jetbrains.com/idea/
.. _maven: https://maven.apache.org


Getting Help with MaxiCP
========================

Contact the authors by email, enter a bug report on github.

Who Uses MaxiCP?
=================

If you use it for teaching or for research, please let us know and we will add you in this list.

* UCLouvain, `AIA <https://aia.info.ucl.ac.be/people/>`_ Researchers in the Group of Pierre Schaus and Hélène Verhaeghe.


Citing MaxiCP and Contributors
==============================

If you use MaxiCP in your research,
you may want to cite the library to acknowledge the contributions of the main developers.

.. code-block:: latex

        @misc{MaxiCP2024,
          author       = {Pierre Schaus and Guillaume Derval and Augustin Delecluse and Laurent Michel and Pascal Van Hentenryck},
          title        = {MaxiCP: A Constraint Programming Solver for Scheduling and Vehicle Routing},
          year         = {2024},
          url          = {https://github.com/aia-uclouvain/maxicp},
        }


Other Contributors to the project are: Hélène Verhaeghe, Charles Thomas, Roger Kameugne, Alice Burlats.


