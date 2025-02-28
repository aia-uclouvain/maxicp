.. _intro:



*******
Preface
*******

This document is made for anyone who is looking for documentation on MaxiCP


What is MaxiCP
==============


A more complete and optimized version of MiniCP.
MiniCP was designed for pedagogical purpose. 
MaxiCP is aimed to be used in real-life project and research


Javadoc
=======

The `Javadoc API <https://aia-uclouvain.github.io/maxicp/javadoc/>`_.

.. _install:

Install MaxiCP
==============

.. raw:: html

    <iframe width="560" height="315" src="https://www.youtube.com/embed/VF_vkCnOp88?rel=0" frameborder="0" allow="autoplay; encrypted-media" allowfullscreen></iframe>


MiniCP source code is available from bitbucket_.

**Using an IDE**

We recommend using IntelliJ_ or Eclipse_.

From IntelliJ_ you can import the project:

.. code-block:: none

    Open > (select pom.xml in the minicp directory and open as new project)


From Eclipse_ you can import the project:

.. code-block:: none

    Import > Maven > Existing Maven Projects (select minicp directory)


**From the command line**

Using maven_ command line you can do:


.. code-block:: none

    $mvn compile # compile all the project
    $mvn test    # run all the test suite

Some other useful commands:

.. code-block:: none

    $mvn checkstyle:checktyle   # generates a report in target/site/checkstyle.html
    $mvn jacoco:report          # creates a cover report in target/site/jacoco/index.html
    $mvn javadoc:javadoc        # creates javadoc in target/site/apidocs/index.html


.. _bitbucket: https://bitbucket.org/minicp/minicp
.. _IntelliJ: https://www.jetbrains.com/idea/
.. _Eclipse: https://www.eclipse.org
.. _maven: https://maven.apache.org


Getting Help with MaxiCP
========================

TODO


Who Uses MaxiCP?
================

If you use it for teaching or for research, please let us know and we will add you in this list.

* UCLouvain, `AIA <https://aia.info.ucl.ac.be/people/>`_ Researchers in the Group of Pierre Schaus and Siegfried Nijssens.


Citing MaxiCP
=============

If you find MaxiCP useful for your research or teaching you can cite the Mini-CP paper:

.. code-block:: latex

        @article{cite-key,
                Author = {Michel, L. and Schaus, P. and Van Hentenryck, P.},
                Doi = {10.1007/s12532-020-00190-7},
                Id = {Michel2021},
                Isbn = {1867-2957},
                Journal = {Mathematical Programming Computation},
                Number = {1},
                Pages = {133-184},
                Title = {MiniCP: a lightweight solver for constraint programming},
                Ty = {JOUR},
                Url = {https://doi.org/10.1007/s12532-020-00190-7},
                Volume = {13},
                Year = {2021}}




