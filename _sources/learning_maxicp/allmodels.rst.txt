*****************************************************************
List of models
*****************************************************************

The project contains two sets of example models located in different packages:

- **Raw Implementation Examples**:

  - Located in the package `org.maxicp.cp.examples.raw <https://github.com/aia-uclouvain/maxicp/tree/main/src/main/java/org/maxicp/cp/examples/raw>`_
  - These examples demonstrate how to use MaxiCP's **raw implementation objects** directly, giving you full control over the CP solver internals.

- **Modeling API Examples**:

  - Located in the package `org.maxicp.cp.examples.modeling <https://github.com/aia-uclouvain/maxicp/tree/main/src/main/java/org/maxicp/cp/examples/modeling>`_
  - These examples use the **high-level modeling API**, which is then instantiated into raw API objects. This abstraction allows for a simpler and more expressive way to define constraint problems, while still using the underlying raw API for solving.

We recommend using the modeling API for most use cases,
as it is more user-friendly and gives you access to the full range of MaxiCP's features,
including parallelization.