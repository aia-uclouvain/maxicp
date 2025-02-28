/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.modeling.algebra.sequence.SeqStatus;

public interface CPNodeVar {

    /**
     * Returns the solver in which this variable was created.
     *
     * @return the solver in which this variable was created.
     */
    CPSolver getSolver();

    /**
     * Returns the sequence variable to which this variable is related to.
     *
     * @return the sequence variable in which this variable was created.
     */
    CPSeqVar getSeqVar();

    /**
     * Returns the node related to this variable.
     *
     * @return node related to this
     */
    int node();

    /**
     * Tells if the node is matching a status.
     *
     * @param status status that must be matched by the node.
     * @return true if the node is matching the status.
     */
    boolean isNode(SeqStatus status);

    /**
     * Gives a boolean variable telling if the node is required
     *
     * @return boolean variable telling if the node is required
     */
    CPBoolVar isRequired();

    // edges
    /**
     * Copies the predecessors matching a status into an array.
     *
     * @param dest an array large enough.
     * @param status status that must be matched by the predecessors.
     * @return n, the number of predecessors matching the status and {@code dest[0,...,n-1]}
     *          contains the predecessors in an arbitrary order.
     */
    int fillPred(int[] dest, SeqStatus status);

    /**
     * Copies the predecessors into an array.
     *
     * @param dest an array large enough {@code dest.length >= nPred()}.
     * @return the number of predecessors and {@code dest[0,...,nPred()-1]}
     *          contains the predecessors in an arbitrary order.
     */
    int fillPred(int[] dest);

    /**
     * Returns the number of predecessors.
     *
     * @return the number of predecessors.
     */
    int nPred();

    /**
     * Copies the successors matching a status into an array.
     *
     * @param dest an array large enough.
     * @param status status that must be matched by the nodes.
     * @return n, the number of successors matching the status and {@code dest[0,...,n-1]}
     *          contains the successors in an arbitrary order.
     */
    int fillSucc(int[] dest, SeqStatus status);

    /**
     * Copies the successors matching a status into an array.
     *
     * @param dest an array large enough {@code dest.length >= nSucc()}.
     * @return the number of successors matching the status and {@code dest[0,...,nSucc()-1]}
     *          contains the successors in an arbitrary order.
     */
    int fillSucc(int[] dest);

    /**
     * Returns the number of successors.
     *
     * @return the number of successors.
     */
    int nSucc();

    /**
     * Fills an array with the insertions of the node.
     * Equivalent to {@code fillPred(node, dest, MEMBER)}.
     *
     * @param dest an array large enough {@code dest.length >= nInsert()}.
     * @return the number of insertions and {@code dest[0,...,nInsert()-1]}
     *          contains the insertions in an arbitrary order.
     */
    int fillInsert(int[] dest);

    /**
     * Returns the number of insertions of the node.
     *
     * @return the number of insertions of the node.
     */
    int nInsert();

    // listeners
    /**
     * Asks that the closure is called whenever the node is inserted.
     *
     * @param f the closure.
     */
    void whenInsert(Runnable f);

    /**
     * Asks that the closure is called whenever the node is excluded.
     *
     * @param f the closure.
     */
    void whenExclude(Runnable f);

    /**
     * Asks that the closure is called the node is required.
     *
     * @param f the closure.
     */
    void whenRequire(Runnable f);

    /**
     * Asks that the closure is called whenever the an insertion for the node has been removed
     *
     * @param f the closure
     */
    void whenInsertRemoved(Runnable f);

    /**
     * Asks that {@link CPConstraint#propagate()} is called whenever the node is inserted.
     * We say that an <i>insert</i> event occurs.
     *
     * @param c the constraint for which the {@link CPConstraint#propagate()}
     *          method should be called on insert events of this variable.
     */
    void propagateOnInsert(CPConstraint c);

    /**
     * Asks that {@link CPConstraint#propagate()} is called whenever the node is excluded.
     * We say that an <i>exclude</i> event occurs.
     *
     * @param c the constraint for which the {@link CPConstraint#propagate()}
     *          method should be called on exclude events of this variable.
     */
    void propagateOnExclude(CPConstraint c);

    /**
     * Asks that {@link CPConstraint#propagate()} is called whenever the node is required.
     * We say that an <i>require</i> event occurs.
     *
     * @param c the constraint for which the {@link CPConstraint#propagate()}
     *          method should be called on require events of this variable.
     */
    void propagateOnRequire(CPConstraint c);

    /**
     * Asks that {@link CPConstraint#propagate()} is called whenever an insertion for the node is removed.
     * We say that an <i>removeInsert</i> event occurs.
     *
     * @param c the constraint for which the {@link CPConstraint#propagate()}
     *          method should be called on removeInsert events of this variable.
     */
    void propagateOnInsertRemoved(CPConstraint c);

}
