package org.maxicp.modeling;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.sequence.SeqExpression;
import org.maxicp.modeling.algebra.sequence.SeqStatus;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

public interface SeqVar extends Var, SeqExpression {

    default boolean isFixed() {
        return nNode(SeqStatus.REQUIRED) == nNode(SeqStatus.MEMBER) && nNode(SeqStatus.POSSIBLE) == 0;
    }

    @Override
    default SeqVar mapSubexpressions(Function<Expression, Expression> f) {
        return this;
    }

    @Override
    default Collection<Expression> computeSubexpressions() {
        return Collections.emptyList();
    }

    /**
     * Copies the nodes matching a status into an array.
     *
     * @param dest   an array large enough {@code dest.length >= nNode(status)}.
     * @param status status that must be matched by the nodes.
     * @return the number of nodes matching the status and {@code dest[0,...,nNode(status)-1]} contains
     * the nodes in an arbitrary order.
     */
    int fillNode(int[] dest, SeqStatus status);

    /**
     * Gives the number of nodes matching a status.
     *
     * @param status status that must be matched by the nodes.
     * @return the number of nodes matching the status.
     */
    int nNode(SeqStatus status);

    /**
     * Gives the total number of nodes.
     *
     * @return the total number of nodes.
     */
    int nNode();

    /**
     * Tells if a node is matching a status.
     *
     * @param node   node.
     * @param status status that must be matched by the node.
     * @return true if the node is matching the status.
     */
    boolean isNode(int node, SeqStatus status);

    /**
     * First member of the sequence
     *
     * @return first member of the sequence
     */
    int start(); // alpha

    /**
     * Last member of the sequence
     *
     * @return last member of the sequence
     */
    int end(); // omega

    /**
     * Returns the member following another member in the sequence.
     *
     * @param node member within the sequence.
     * @return member following the given node in the sequence.
     */
    int memberAfter(int node);

    /**
     * Returns the member preceding another member in the sequence.
     *
     * @param node member within the sequence.
     * @return member preceding the given node in the sequence.
     */
    int memberBefore(int node);

    // edges

    /**
     * Copies the predecessors of a node matching a status into an array.
     *
     * @param node   node.
     * @param dest   an array large enough
     * @param status status that must be matched by the predecessors.
     * @return the number of predecessors matching the status and {@code dest[0,...,nPred(node, status)-1]}
     * contains the predecessors in an arbitrary order.
     */
    int fillPred(int node, int[] dest, SeqStatus status);

    /**
     * Copies the predecessors of a node into an array.
     *
     * @param node   node.
     * @param dest   an array large enough {@code dest.length >= nPred(node)}.
     * @return the number of predecessors and {@code dest[0,...,nPred(node)-1]}
     * contains the predecessors in an arbitrary order.
     */
    default int fillPred(int node, int[] dest) {
        return fillPred(node, dest, SeqStatus.NOT_EXCLUDED);
    }

    /**
     * Returns the number of predecessors of a node.
     *
     * @param node   node.
     * @return the number of predecessors.
     */
    int nPred(int node);

    /**
     * Copies the successors of a node matching a status into an array.
     *
     * @param node   node.
     * @param dest   an array large enough {@code dest.length >= nSucc(node, status)}.
     * @param status status that must be matched by the nodes.
     * @return the number of successors matching the status and {@code dest[0,...,nSucc(node, status)-1]}
     * contains the successors in an arbitrary order.
     */
    int fillSucc(int node, int[] dest, SeqStatus status);

    /**
     * Copies the successors of a node into an array.
     *
     * @param node   node.
     * @param dest   an array large enough {@code dest.length >= nSucc(node)}.
     * @return the number of successors and {@code dest[0,...,nSucc(node)-1]}
     * contains the successors in an arbitrary order.
     */
    default int fillSucc(int node, int[] dest) {
        return fillSucc(node, dest, SeqStatus.NOT_EXCLUDED);
    }

    /**
     * Returns the number of successors of a node.
     *
     * @param node   node.
     * @return the number of successors matching the status.
     */
    int nSucc(int node);

    /**
     * Fills an array with the insertions for a node.
     * Equivalent to {@code fillPred(node, dest, MEMBER)}.
     *
     * @param node node.
     * @param dest an array large enough {@code dest.length >= nInsert(node)}.
     * @return the number of insertions and {@code dest[0,...,nInsert(node)-1]}
     * contains the insertions in an arbitrary order.
     */
    int fillInsert(int node, int[] dest);

    /**
     * Returns the number of insertions of a node.
     *
     * @param node node.
     * @return the number of insertions of a node.
     */
    int nInsert(int node);

    /**
     * Tells if a directed edge exists between two nodes.
     *
     * @param from origin of the edge.
     * @param to   destination of the edge.
     * @return true if the directed edge exists between two nodes.
     */
    boolean hasEdge(int from, int to);

    /**
     * Tells if an insertion is valid.
     * An insert operation is valid iff
     * - prev is a member node ;
     * - node is an insertable node (either possible or a required node not yet inserted) ;
     * - {@code hasEdge(prev, node)} ;
     * - {@code hasEdge(node, memberAfter(prev))}.
     *
     * @param prev a member node.
     * @param node an insertable node.
     * @return true if the node can be inserted after the given member.
     */
    boolean hasInsert(int prev, int node);

    /**
     * Gives a variable telling if a given node must be visited
     * @param node node in the sequence
     * @return variable set to true if the node must be visited
     */
    BoolVar isNodeRequired(int node);

}
