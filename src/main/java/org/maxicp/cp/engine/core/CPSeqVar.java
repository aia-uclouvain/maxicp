/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.modeling.algebra.sequence.SeqStatus;
import org.maxicp.modeling.concrete.ConcreteSeqVar;
import org.maxicp.util.exception.InconsistencyException;

import java.util.StringJoiner;
import java.util.function.Predicate;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

public interface CPSeqVar extends CPVar, ConcreteSeqVar {

    /**
     * Returns the solver in which this variable was created.
     *
     * @return the solver in which this variable was created.
     */
    CPSolver getSolver();

    /**
     * Returns true if every node is either a member or an excluded node.
     *
     * @return true if every node is either a member or an excluded node.
     */
    boolean isFixed();

    /**
     * Returns the variable related to a node.
     *
     * @param node node.
     * @return variable related to a node.
     */
    CPNodeVar getNodeVar(int node);

    /**
     * Gives a variable telling if a given node must be visited
     * @param node node in the sequence
     * @return variable set to true if the node must be visited
     */
    CPBoolVar isNodeRequired(int node);

    // operations on set domain

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

    // internal sequence

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
    int fillPred(int node, int[] dest);

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
    int fillSucc(int node, int[] dest);

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

    // getter and status

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

    // changes the state of the variable

    /**
     * Excludes a node from the sequence.
     *
     * @param node node to be excluded.
     * @throws InconsistencyException if the node is required ({@code isNode(node, REQUIRED)}).
     */
    void exclude(int node);

    /**
     * Requires a node in the sequence.
     *
     * @param node node to be required.
     * @throws InconsistencyException if the node is excluded ({@code isNode(node, EXCLUDED)}).
     */
    void require(int node);

    /**
     * Inserts a node in the sequence after a given member.
     * Requires the node if it was not required yet.
     *
     * @param prev member after which the node will be inserted.
     * @param node node inserted in the sequence.
     * @throws InconsistencyException if the insertion is invalid ({@code !hasInsert(prev, node)}).
     * @throws IllegalArgumentException if prev and node are two member nodes that are not consecutive
     *      ({@code isNode(prev, MEMBER) && isNode(node, MEMBER) && memberAfter(prev) != node})
     */
    void insert(int prev, int node);

    /**
     * Forbids a subsequence of length 3 to appear, removing from the domain all sequences containing the sub-sequence given as input.
     * For technical reasons, the two endpoints of the subsequence must belong to the current partial sequence.
     *
     * @param prev origin of the subsequence, a member node
     * @param node node in the middle of the subsequence
     * @param succ end of the subsequence, a member node
     * @throws InconsistencyException if the subsequence to remove appears within the sequence
     */
    void notBetween(int prev, int node, int succ);

    // listeners for propagation

    /**
     * Asks that the closure is called whenever the domain is fixed {@link CPSeqVar#isFixed()}.
     *
     * @param f the closure.
     */
    void whenFixed(Runnable f);

    /**
     * Asks that the closure is called whenever an insertion happens.
     *
     * @param f the closure.
     */
    void whenInsert(Runnable f);

    /**
     * Asks that the closure is called whenever an insertion is removed.
     *
     * @param f the closure.
     */
    void whenInsertRemoved(Runnable f);

    /**
     * Asks that the closure is called whenever an exclusion happens.
     *
     * @param f the closure.
     */
    void whenExclude(Runnable f);

    /**
     * Asks that the closure is called a node is required.
     *
     * @param f the closure.
     */
    void whenRequire(Runnable f);

    /**
     * Asks that {@link CPConstraint#propagate()} is called whenever the domain is fixed {@link CPSeqVar#isFixed()}
     * In such a state the variable is fixed and we say that a <i>fix</i> event occurs.
     *
     * @param c the constraint for which the {@link CPConstraint#propagate()}
     *          method should be called on fix events of this variable.
     */
    void propagateOnFix(CPConstraint c);

    /**
     * Asks that {@link CPConstraint#propagate()} is called whenever an insertion is removed
     * We say that a <i>removeInsert</i> event occurs.
     *
     * @param c the constraint for which the {@link CPConstraint#propagate()}
     *          method should be called on removeInsert events of this variable.
     */
    void propagateOnInsertRemoved(CPConstraint c);

    /**
     * Asks that {@link CPConstraint#propagate()} is called whenever an insertion happens.
     * We say that an <i>insert</i> event occurs.
     *
     * @param c the constraint for which the {@link CPConstraint#propagate()}
     *          method should be called on insert events of this variable.
     */
    void propagateOnInsert(CPConstraint c);

    /**
     * Asks that {@link CPConstraint#propagate()} is called whenever an exclusion happens.
     * We say that an <i>exclude</i> event occurs.
     *
     * @param c the constraint for which the {@link CPConstraint#propagate()}
     *          method should be called on exclude events of this variable.
     */
    void propagateOnExclude(CPConstraint c);

    /**
     * Asks that {@link CPConstraint#propagate()} is called whenever a node is required.
     * We say that an <i>require</i> event occurs.
     *
     * @param c the constraint for which the {@link CPConstraint#propagate()}
     *          method should be called on require events of this variable.
     */
    void propagateOnRequire(CPConstraint c);

    // String representation

    /**
     * Returns a string representation of the members, ordered following the sequence
     * from {@code start()} to {@code end()}, separated by " -> ".
     *
     * @return string representation of the members, ordered following the sequence
     * from {@code start()} to {@code end()}.
     */
    String membersOrdered();

    /**
     * Returns a string representation of the members, ordered following the sequence
     * from {@code start()} to {@code end()}, separated by the given delimiter.
     *
     * @param join delimiter between two member nodes.
     * @return string representation of the members, ordered following the sequence
     * from {@code start()} to {@code end()}. The nodes are separated by the given delimiter.
     */
    String membersOrdered(String join);

    /**
     * Returns a string representation of the members, ordered following the sequence
     * from {@code start()} to {@code end()}, separated by the given delimiter.
     *
     * @param join delimiter between two member nodes.
     * @param filter filter the nodes that are valid for giving the representation.
     *               Only nodes evaluated to true are included in the representation
     * @return string representation of the members, ordered following the sequence
     * from {@code start()} to {@code end()}. The nodes are separated by the given delimiter.
     */
    String membersOrdered(String join, Predicate<Integer> filter);

    /**
     * Exports the variable into a GraphViz format
     * See <a href="http://www.webgraphviz.com/">...</a>
     *
     * @return variable encoded into a GraphViz format
     */
    default String toGraphViz() {
        String memberColor = "#0072B2";
        String requiredColor = "#56B4E9";
        String possibleColor = "#009E73";
        String excludedColor = "#D55E00";
        String possibleEdgeColor = "#969696";
        StringJoiner graphViz = new StringJoiner("\n");
        graphViz.add("digraph {");
        graphViz.add("    node [style=filled];");
        // nodes from the graph
        int n = nNode();
        for (int i = 0; i < n; i++) {
            if (isNode(i, MEMBER)) {
                if (i == start() || i ==end()) {
                    graphViz.add("    " + i + " [shape = doublecircle, color=\"" + memberColor + "\", tooltip=\"member\"];");
                } else {
                    graphViz.add("    " + i + " [shape = circle, color=\"" + memberColor + "\", tooltip=\"member\"];");
                }
            } else if (isNode(i, REQUIRED)) {
                graphViz.add("    " + i + " [shape = circle, color=\"" + requiredColor + "\", tooltip=\"required\"];");
            } else if (isNode(i, POSSIBLE)) {
                graphViz.add("    " + i + " [shape = circle, color=\"" + possibleColor + "\", tooltip=\"possible\"];");
            } else {
                graphViz.add("    " + i + " [shape = circle, color=\"" + excludedColor + "\", tooltip=\"excluded\"];");
            }
        }
        // edges
        int[] values = new int[nNode()];
        for (int i = 0 ; i < n ; i++) {
            int nPred = fillPred(i, values, NOT_EXCLUDED);
            for (int j = 0 ; j < nPred ; j++) {
                int pred = values[j];
                String style = isNode(pred, MEMBER) && isNode(i, MEMBER) ? " [penwidth=3.0]" : String.format(" [style=dashed, color=\"%s\"]", possibleEdgeColor);
                graphViz.add("    " + pred + " -> " + i + style + ";");
            }
        }
        graphViz.add("}");
        return graphViz.toString();
    }
}



