/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.concrete.ConcreteVar;

public interface CPSetVar extends CPVar, ConcreteVar {

    CPIntVar card();

    boolean isFixed();

    int nIncluded();
    int nPossible();
    int nExcluded();

    boolean isPossible(int v);
    boolean isIncluded(int v);
    boolean isExcluded(int v);

    void exclude(int v);
    void include(int v);

    void includeAll();
    void excludeAll();

    int fillPossible(int[] dest);
    int fillIncluded(int[] dest);
    int fillExcluded(int[] dest);

    int size();

    void propagateOnDomainChange(CPConstraint c);

    CPSolver getSolver();
    @Override
    ModelProxy getModelProxy();




}
