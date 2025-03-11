*****************************************************************
N-Queens: The Hello World of Constraint Programming
*****************************************************************


The model
============

The complete `NQueens <https://github.com/aia-uclouvain/maxicp/blob/main/src/main/java/org/maxicp/cp/examples/modeling/NQueens.java>`_ model.

First the symbolic model is created.

.. code-block:: java

        int n = 12;

        ModelDispatcher model = makeModelDispatcher();

        IntVar[] q = model.intVarArray(n, n);
        IntExpression[] qL = model.intVarArray(n,i -> q[i].plus(i));
        IntExpression[] qR = model.intVarArray(n,i -> q[i].minus(i));

        model.add(allDifferent(q));
        model.add(allDifferent(qL));
        model.add(allDifferent(qR));


Then the search is defined.
Notice that it adds the the constraints to the symbolic model.

.. code-block:: java


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


The symbolic model is concretized and the search is executed.

.. code-block:: java


        ConcreteCPModel cp = model.cpInstantiate();
        DFSearch dfs = cp.dfSearch(branching);
        dfs.onSolution(() -> {
            System.out.println(Arrays.toString(q));
        });
        SearchStatistics stats = dfs.solve();
        System.out.println(stats);
