

![Javadoc](https://github.com/aia-uclouvain/maxicp/actions/workflows/javadoc.yml/badge.svg)
![Userguide](https://github.com/aia-uclouvain/maxicp/actions/workflows/userguide.yml/badge.svg)
![Coverage](https://raw.githubusercontent.com/aia-uclouvain/maxicp/refs/heads/gh-pages/badges/coverbadge.svg)

**MaxiCP** is an open-source (MIT licence) Java-based Constraint Programming (CP) solver.

It is an extended version of the ['wwww.minicp.org'](https://www.minicp.org) a lightweight, 
open-source CP solver mostly used for teaching constraint programming.

The key features of MaxiCP compared to MiniCP are:
- **Improved performances** (support for delta-based propagation, more efficient data structures, etc.). 
- **Symbolic modeling layer** also enabling search declaration.
- **Support for Embarrasingly Parallel Search**.
- **More global constraints** (e.g., bin-packing, gcc, etc.).
- **Sequence variables with optional visits** for modeling complex vehicle routing and insertion based search heuristics, including LNS.
- **Conditional task interval variables** including support for modeling with cumulative function expressions for scheduling problem.

## Examples

The project contains two sets of example models located in different packages:

- **Raw Implementation Examples**:
    - Located in: [`org.maxicp.cp.examples.raw`](https://github.com/<user>/maxicp/tree/master/src/main/java/org/maxicp/cp/examples/raw)
    - These examples demonstrate how to use MaxiCP's **raw implementation objects** directly, giving you full control over the CP solver internals.

- **Modeling API Examples**:
    - Located in: [`org.maxicp.cp.examples.modeling`](https://github.com/<user>/maxicp/tree/master/src/main/java/org/maxicp/cp/examples/modeling)
    - These examples use the **high-level modeling API**, which is then instantiated into raw API objects. This abstraction allows for a simpler and more expressive way to define constraint problems, while still leveraging the underlying raw API for solving.

## Getting Started with MaxiCP

### Recommended IDE: IntelliJ IDEA

We recommend using **IntelliJ IDEA** to develop and run the MaxiCP project.

#### Steps to Import MaxiCP into IntelliJ:

1. **Clone the Repository**:
   Open a terminal and run the following command to clone the repository:
   ```bash
   git clone https://github.com/aia-uclouvain/maxicp.git
    ```

2. **Open project in IDEA**:
   Launch IntelliJ IDEA.
   Select File > Open and navigate to the maxicp folder you cloned. 
   Open the `pom.xml` file.

3. **Running the tests**:

    From the IntelliJ IDEA editor, navigate to the `src/test/java` directory.
    Right-click then select `Run 'All Tests'` to run all the tests.

    From the terminal, navigate to the root directory of the project and run the following command:
    ```bash
    mvn test
    ```



