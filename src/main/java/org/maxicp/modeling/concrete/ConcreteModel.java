/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.modeling.concrete;

import org.maxicp.modeling.Constraint;
import org.maxicp.modeling.Model;
import org.maxicp.modeling.algebra.bool.BoolExpression;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.scheduling.IntervalExpression;
import org.maxicp.modeling.algebra.sequence.SeqExpression;
import org.maxicp.modeling.symbolic.SymbolicModel;
import org.maxicp.search.BestFirstSearch;
import org.maxicp.search.ConcurrentDFSearch;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.state.StateManaged;

import java.util.function.Supplier;

public interface ConcreteModel extends Model, StateManaged {

    ConcreteIntVar getConcreteVar(IntExpression expr);

    ConcreteBoolVar getConcreteVar(BoolExpression expr);

    ConcreteSeqVar getConcreteVar(SeqExpression expr);

    ConcreteIntervalVar getConcreteVar(IntervalExpression expr);

    default void add(Constraint c) { add(c, true); }

    void add(Constraint c, boolean enforceFixPoint);

    /**
     * Jump to a specific constraint node.
     *
     * The new list should have a node in common with current model,
     * and this common node must be either the node at which the model was concretized, or a node created after that.
     *
     * @param m the model to jump to
     */
    void jumpTo(SymbolicModel m, boolean enforceFixPoint);
    default void jumpTo(SymbolicModel m) { jumpTo(m, true); }

    /**
     * Similar function to jumpTo, but enforces a jump to a child model.
     * @param m
     * @param enforceFixPoint
     */
    void jumpToChild(SymbolicModel m, boolean enforceFixPoint);

    default void jumpToChild(SymbolicModel m) { jumpToChild(m, true); }

    Objective createObjective(org.maxicp.modeling.symbolic.Objective obj);

    default ConcurrentDFSearch concurrentDFSearch(Supplier<SymbolicModel[]> symbolicBranching) {
        return new ConcurrentDFSearch(getModelProxy(), symbolicBranching);
    }

    default DFSearch dfSearch(Supplier<Runnable[]> branching) {
        return new DFSearch(getStateManager(), branching);
    }

    default <U extends Comparable<U>> BestFirstSearch<U> bestFirstSearch(Supplier<Runnable[]> branching, Supplier<U> nodeEvaluator) {
        return new BestFirstSearch<U>(getModelProxy(), branching, nodeEvaluator);
    }
}
