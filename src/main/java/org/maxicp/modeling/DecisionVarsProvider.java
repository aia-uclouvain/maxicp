/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.modeling;

import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.Collection;

/**
 * Implemented by constraints and expression nodes that know which of their
 * sub-expressions play an "index / choice" role and are therefore candidate
 * decision variables for search and LNS.
 *
 * <p>The contract is:</p>
 * <ul>
 *   <li>Only <em>leaf</em> {@link IntVar} instances should be returned.
 *       Derived expressions (sums, element results, views, …) must be excluded.</li>
 *   <li>The returned collection must not be modified by callers.</li>
 * </ul>
 *
 * <p>Custom constraints that know which of their arguments are decision
 * variables should implement this interface directly.  The
 * {@link DecisionVarCollector} utility then harvests these declarations
 * from the full constraint/expression graph.</p>
 *
 * @see DecisionVarCollector
 */
public interface DecisionVarsProvider {

    /**
     * Returns the sub-expressions of this node that should be considered
     * decision variables (i.e., variables to branch on / freeze in LNS).
     *
     * @return immutable collection of candidate decision variables; never {@code null},
     *         may be empty
     */
    Collection<IntExpression> decisionVariables();
}

