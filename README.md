

![Javadoc](https://github.com/aia-uclouvain/maxicp/actions/workflows/javadoc.yml/badge.svg)
![Userguide](https://github.com/aia-uclouvain/maxicp/actions/workflows/userguide.yml/badge.svg)
![Coverage](https://raw.githubusercontent.com/aia-uclouvain/maxicp/refs/heads/gh-pages/badges/coverbadge.svg)
<!-- ![Test coverage](https://raw.githubusercontent.com/<username>/<repository>/badges/badges/<branch>/badge.svg) -->

# MaxiCP

**MaxiCP** is a Java-based project that provides a powerful Constraint Programming (CP) solver. 
It includes both raw and high-level modeling APIs for users to define and solve constraint satisfaction problems efficiently.

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
   git clone https://github.com/<user>/maxicp.git
    ```

2. **Clone the Repository**:
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



