

![Javadoc](https://github.com/aia-uclouvain/maxicp/actions/workflows/javadoc.yml/badge.svg)
![Userguide](https://github.com/aia-uclouvain/maxicp/actions/workflows/userguide.yml/badge.svg)
![Coverage](https://raw.githubusercontent.com/aia-uclouvain/maxicp/refs/heads/gh-pages/badges/coverbadge.svg)

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

## Installation

The official more stable releases are on [maven central](https://central.sonatype.com/artifact/org.maxicp/maxicp).

You can add this as a maven dependency to your project, those releases are committed and tagged in the releases branch: 

```xml
<dependency>
    <groupId>org.maxicp</groupId>
    <artifactId>maxicp</artifactId>
    <version>0.0.2</version>
</dependency>
```

The javadoc of stable releases can be consulted on at this [url](https://javadoc.io/doc/org.maxicp/maxicp/latest/org.maxicp/module-summary.html).

If you need a dependency on the main branch (the main working branch where the next release is prepared), you can use [jitpack](https://jitpack.io/#aia-uclouvain/maxicp).

The javadoc of the main branch can be consulted at this [url](http://www.maxicp.org/javadoc/org.maxicp/module-summary.html).

## Examples

The project contains two sets of example models located in different packages:

- **Raw Implementation Examples**:
    - Located in: [`org.maxicp.cp.examples.raw`](https://github.com/aia-uclouvain/maxicp/tree/main/src/main/java/org/maxicp/cp/examples/raw)
    - These examples demonstrate how to use MaxiCP's **raw implementation objects** directly, giving you full control over the CP solver internals.

- **Modeling API Examples**:
    - Located in: [`org.maxicp.cp.examples.modeling`](https://github.com/aia-uclouvain/maxicp/tree/main/src/main/java/org/maxicp/cp/examples/modeling)
    - These examples use the **high-level modeling API**, which is then instantiated into raw API objects. This abstraction allows for a simpler and more expressive way to define constraint problems, while still leveraging the underlying raw API for solving.

### N-Queens Example

#### Using Raw API

This example demonstrates how to solve the N-Queens problem using the raw API objects directly.

```java
package org.maxicp.cp.examples.raw.nqueens;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;
import java.util.Arrays;

public class NQueens {
    public static void main(String[] args) {
        int n = 8;

        CPSolver cp = CPFactory.makeSolver();
        CPIntVar[] q = CPFactory.makeIntVarArray(cp, n, n);
        CPIntVar[] qL = CPFactory.makeIntVarArray(n, i -> minus(q[i],i));
        CPIntVar[] qR = CPFactory.makeIntVarArray(n, i -> plus(q[i],i));

        cp.post(allDifferent(q));
        cp.post(allDifferent(qL));
        cp.post(allDifferent(qR));


        // a more compact first fail search using selectors is given next
        DFSearch search = CPFactory.makeDfs(cp, () -> {
            CPIntVar qs = selectMin(q,
                    qi -> qi.size() > 1,
                    qi -> qi.size());
            if (qs == null) return EMPTY;
            else {
                int v = qs.min();
                return branch(() -> cp.post(eq(qs, v)),
                        () -> cp.post(neq(qs, v)));
            }
        });


        search.onSolution(() ->
                System.out.println("solution:" + Arrays.toString(q))
        );
        SearchStatistics stats = search.solve(statistics -> statistics.numberOfSolutions() == 1000);

        System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
        System.out.format("Statistics: %s\n", stats);

    }
}
```

#### Using Modeling API

This example demonstrates how to solve the N-Queens problem using the high-level modeling API.

```java
package org.maxicp.cp.examples.modeling.nqueens;


import org.maxicp.ModelDispatcher;
import org.maxicp.cp.modeling.ConcreteCPModel;
import static org.maxicp.modeling.Factory.*;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.*;
import static org.maxicp.search.Searches.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;


import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

public class NQueens {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        int n = 12;

        ModelDispatcher model = makeModelDispatcher();

        IntVar[] q = model.intVarArray(n, n);
        IntExpression[] qL = model.intVarArray(n,i -> q[i].plus(i));
        IntExpression[] qR = model.intVarArray(n,i -> q[i].minus(i));

        model.add(allDifferent(q));
        model.add(allDifferent(qL));
        model.add(allDifferent(qR));

        Supplier<Runnable[]> branching = () -> {
            IntExpression qs = selectMin(q,
                    qi -> qi.size() > 1,
                    qi -> qi.size());
            if (qs == null)
                return EMPTY;
            else {
                int v = qs.min();
                return branch(() -> model.add(eq(qs, v)), () -> model.add(neq(qs, v)));
            }
        };

        ConcreteCPModel cp = model.cpInstantiate();
        DFSearch dfs = cp.dfSearch(branching);
        dfs.onSolution(() -> {
            System.out.println(Arrays.toString(q));
        });
        SearchStatistics stats = dfs.solve();
        System.out.println(stats);

    }
}
```

## Website and documentation

[`www.maxicp.org`](www.maxicp.org)

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
    


