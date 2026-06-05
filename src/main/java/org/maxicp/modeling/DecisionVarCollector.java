/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */
package org.maxicp.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.*;

/**
 * Walks the full constraint / expression graph of a {@link ModelDispatcher}
 * and collects all candidate decision variables declared by
 * {@link DecisionVarsProvider} nodes.
 *
 * <p>Walk strategy:</p>
 * <ol>
 *   <li>For every {@link Constraint}: if it implements
 *       {@link DecisionVarsProvider}, harvest its {@code decisionVariables()}.
 *   </li>
 *   <li>Descend the expression sub-tree of every scope expression via
 *       {@link Expression#subexpressions()}, collecting from any
 *       {@link DecisionVarsProvider} node found (e.g. an {@code Element1D}
 *       embedded inside a sum).
 *   </li>
 * </ol>
 *
 * <p>Results are deduplicated by identity, preserving first-seen order.</p>
 *
 * @see DecisionVarsProvider
 */
public class DecisionVarCollector {

    private DecisionVarCollector() {}

    /**
     * Collects all candidate decision variables inferred from the model's
     * constraint graph.
     *
     * @param model the model to inspect
     * @return ordered, identity-deduplicated, unmodifiable list of decision variables
     */
    public static List<IntExpression> collect(ModelDispatcher model) {
        IdentityHashMap<IntExpression, Boolean> seen = new IdentityHashMap<>();
        List<IntExpression> result = new ArrayList<>();
        for (Constraint c : model.getConstraints()) {
            if (c instanceof DecisionVarsProvider dvp) {
                for (IntExpression v : dvp.decisionVariables())
                    if (seen.putIfAbsent(v, Boolean.TRUE) == null)
                        result.add(v);
            }
            walkExpressions(c.scope(), seen, result);
        }
        return Collections.unmodifiableList(result);
    }

    /** Iterative DFS over expression trees; harvests DecisionVarsProvider nodes. */
    private static void walkExpressions(
            Collection<? extends Expression> roots,
            IdentityHashMap<IntExpression, Boolean> seen,
            List<IntExpression> result) {
        Deque<Expression> stack = new ArrayDeque<>(roots);
        IdentityHashMap<Expression, Boolean> visited = new IdentityHashMap<>();
        while (!stack.isEmpty()) {
            Expression node = stack.pop();
            if (visited.putIfAbsent(node, Boolean.TRUE) != null) continue;
            if (node instanceof DecisionVarsProvider dvp) {
                for (IntExpression v : dvp.decisionVariables())
                    if (seen.putIfAbsent(v, Boolean.TRUE) == null)
                        result.add(v);
            }
            for (Expression child : node.subexpressions())
                stack.push(child);
        }
    }
}
