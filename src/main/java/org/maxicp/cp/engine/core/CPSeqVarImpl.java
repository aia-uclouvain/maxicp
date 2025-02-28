package org.maxicp.cp.engine.core;

import org.maxicp.modeling.BoolVar;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.sequence.SeqStatus;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateSparseSet;
import org.maxicp.state.datastructures.StateStack;
import org.maxicp.state.datastructures.StateTriPartition;

import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.util.exception.InconsistencyException.INCONSISTENCY;

public class CPSeqVarImpl implements CPSeqVar {

    private final StateInt[] succ; // successors in the current partial sequence
    private final StateInt[] pred; // predecessors in the current partial sequence
    private final int start;
    private final int end;
    private final int nNodes;
    private final StateInt nMember;
    private final CPSolver cp;
    private final Node[] nodes;
    private final StateTriPartition domain;  // split between required, possible and excluded
    private final StateSparseSet insertable; // holds the insertable nodes

    private final StateStack<CPConstraint> onInsert;        // a node has been inserted into the sequence
    private final StateStack<CPConstraint> onFix;           // all nodes are members or excluded: no possible node remain
    private final StateStack<CPConstraint> onExclude;       // a node has been excluded from the sequence
    private final StateStack<CPConstraint> onRequire;       // a node has been required within the sequence
    private final StateStack<CPConstraint> onInsertRemoved; // an insertion has been removed from the sequence

    private final int[] values;

    public CPSeqVarImpl(CPSolver cp, int nNodes, int start, int end) {
        if (nNodes < 2) {
            throw new IllegalArgumentException("at least two nodes required since start and end are included in the sequence");
        }
        if (start < 0 | end < 0 | start >= nNodes | end >= nNodes) {
            throw new IllegalArgumentException("start and end nodes must be in the range [" + 0 + ".." + (nNodes - 1) + "]" + " start=" + start + " end=" + end);
        }
        this.cp = cp;
        this.nNodes = nNodes;
        this.start = start;
        this.end = end;
        nodes = new CPSeqVarImpl.Node[nNodes];
        succ = new StateInt[nNodes];
        pred = new StateInt[nNodes];
        for (int i = 0; i < nNodes; ++i) {
            nodes[i] = new CPSeqVarImpl.Node(i);
            succ[i] = cp.getStateManager().makeStateInt(i);
            pred[i] = cp.getStateManager().makeStateInt(i);
        }
        succ[start].setValue(end); // the sequence is a closed loop between the start and end nodes
        succ[end].setValue(start);
        pred[start].setValue(end);
        pred[end].setValue(start);
        nMember = cp.getStateManager().makeStateInt(2); // only the start and end nodes are member

        insertable = new StateSparseSet(cp.getStateManager(), nNodes, 0);
        insertable.remove(start);
        insertable.remove(end);
        domain = new StateTriPartition(cp.getStateManager(), nNodes);
        domain.include(start);
        domain.include(end);

        // start and end nodes initialization
        nodes[start].pred.removeAll();
        nodes[end].succ.removeAll();

        onInsert = new StateStack<>(cp.getStateManager());
        onFix = new StateStack<>(cp.getStateManager());
        onExclude = new StateStack<>(cp.getStateManager());
        onRequire = new StateStack<>(cp.getStateManager());
        onInsertRemoved = new StateStack<>(cp.getStateManager());
        values = new int[nNodes];
    }

    /**
     * Listener for the whole sequence.
     * For more information about the changes (i.e. what insertion has occurred?),
     * use the listener within the nodes
     */
    private final SeqListener listener = new SeqListener() {
        @Override
        public void fix() {
            scheduleAll(onFix);
        }

        @Override
        public void insert() {
            scheduleAll(onInsert);
        }

        @Override
        public void exclude() {
            scheduleAll(onExclude);
        }

        @Override
        public void require() {
            scheduleAll(onRequire);
        }

        @Override
        public void insertRemoved() {
            scheduleAll(onInsertRemoved);
        }
    };

    private class Node implements CPNodeVar {

        private final int me;
        private StateSparseSet pred;
        private StateSparseSet succ;
        private StateInt nInsert;

        // constraints registered for this node
        private StateStack<CPConstraint> onInsert;
        private StateStack<CPConstraint> onExclude;
        private StateStack<CPConstraint> onRequire;
        private StateStack<CPConstraint> onInsertRemoved;

        private Node(int i) {
            me = i;
            pred = new StateSparseSet(cp.getStateManager(), nNodes, 0);
            pred.remove(me);
            pred.remove(end);

            succ = new StateSparseSet(cp.getStateManager(), nNodes, 0);
            succ.remove(me);
            succ.remove(start);
            // the node can be inserted between start and end at initialization
            nInsert = cp.getStateManager().makeStateInt(1);

            onInsert = new StateStack<>(cp.getStateManager());
            onRequire = new StateStack<>(cp.getStateManager());
            onExclude = new StateStack<>(cp.getStateManager());
            onInsertRemoved = new StateStack<>(cp.getStateManager());
        }


        private final NodeListener listener = new NodeListener() {
            @Override
            public void insert() {
                scheduleAll(onInsert);
            }

            @Override
            public void exclude() {
                scheduleAll(onExclude);
            }

            @Override
            public void insertRemoved() {
                scheduleAll(onInsertRemoved);
            }

            @Override
            public void require() {
                scheduleAll(onRequire);
            }

        };

        @Override
        public CPSolver getSolver() {
            return cp;
        }

        @Override
        public CPSeqVar getSeqVar() {
            return CPSeqVarImpl.this;
        }

        @Override
        public int node() {
            return me;
        }

        @Override
        public boolean isNode(SeqStatus status) {
            return CPSeqVarImpl.this.isNode(me, status);
        }

        @Override
        public CPBoolVar isRequired() {
            return new CPBoolVar() {
                @Override
                public ModelProxy getModelProxy() {
                    return CPSeqVarImpl.this.getModelProxy();
                }

                @Override
                public boolean isTrue() {
                    return isNode(REQUIRED);
                }

                @Override
                public boolean isFalse() {
                    return isNode(EXCLUDED);
                }

                @Override
                public void fix(boolean b) {
                    if (b) {
                        require(me);
                    } else {
                        exclude(me);
                    }
                }

                @Override
                public CPSolver getSolver() {
                    return cp;
                }

                @Override
                public void whenFixed(Runnable f) {
                    whenExclude(f);
                    whenRequire(f);
                }

                @Override
                public void whenBoundChange(Runnable f) {
                    whenFixed(f);
                }

                @Override
                public void whenDomainChange(Runnable f) {
                    whenFixed(f);
                }

                @Override
                public void whenDomainChange(Consumer<DeltaCPIntVar> f) {
                    throw new UnsupportedOperationException("Not implemented");
                }

                @Override
                public void propagateOnDomainChange(CPConstraint c) {
                    propagateOnFix(c);
                }

                @Override
                public void propagateOnFix(CPConstraint c) {
                    propagateOnRequire(c);
                    propagateOnExclude(c);
                }

                @Override
                public void propagateOnBoundChange(CPConstraint c) {
                    propagateOnFix(c);
                }

                @Override
                public int min() {
                    if (isNode(REQUIRED)) {
                        return 1;
                    } else {
                        return 0; // possible (unfixed) or excluded (fixed)
                    }
                }

                @Override
                public int max() {
                    if (isNode(EXCLUDED)) {
                        return 0;
                    } else {
                        return 1; //  possible (unfixed) or required (fixed)
                    }
                }

                @Override
                public int size() {
                    if (isNode(POSSIBLE)) {
                        return 2;
                    } else {
                        return 1;
                    }
                }

                @Override
                public int fillArray(int[] dest) {
                    if (isNode(POSSIBLE)) {
                        dest[0] = 0;
                        dest[1] = 1;
                        return 2;
                    } else if (isNode(REQUIRED)) {
                        dest[0] = 1;
                    } else { // excluded
                        dest[0] = 0;
                    }
                    return 1;
                }

                @Override
                public boolean isFixed() {
                    return !isNode(POSSIBLE);
                }

                @Override
                public boolean contains(int v) {
                    if (v < 0 || v > 1)
                        return false;
                    if (v == 0) {
                        return !isNode(REQUIRED); // (possible or excluded) == (!required) contain 0
                    } else { // v == 1
                        return !isNode(EXCLUDED); // (possible or required) == (!excluded) contain 1
                    }
                }

                @Override
                public void remove(int v) {
                    if (v == 0) {
                        require(me);
                    }
                    if (v == 1) {
                        exclude(me);
                    }
                }

                @Override
                public void fix(int v) {
                    if (v == 0) {
                        exclude(me);
                    } else if (v == 1) {
                        require(me);
                    } else {
                        throw INCONSISTENCY;
                    }
                }

                @Override
                public void removeBelow(int v) {
                    if (v >= 1) {
                        if (v == 1) {
                            require(me);
                        } else {
                            throw INCONSISTENCY;
                        }
                    }
                }

                @Override
                public void removeAbove(int v) {
                    if (v <= 0) {
                        if (v == 0) {
                            exclude(me);
                        } else {
                            throw INCONSISTENCY;
                        }
                    }
                }

                @Override
                public int fillDeltaArray(int oldMin, int oldMax, int oldSize, int[] dest) {
                    int size = size();
                    if (oldSize == size) {
                        return 0; // no difference
                    } else { // either the min or the max differ
                        // the current domain must have shrink since the last call
                        if (oldMin != min()) { // min has changed
                            dest[0] = 0;
                        } else { // max has changed
                            dest[0] = 1;
                        }
                        return 1;
                    }
                }

                @Override
                public DeltaCPIntVar delta(CPConstraint c) {
                    DeltaCPIntVar delta = new DeltaCPIntVarImpl(this);
                    c.registerDelta(delta);
                    return delta;
                }

                @Override
                public String toString() {
                    if (isTrue()) return "true";
                    else if (isFalse()) return "false";
                    else return "{false,true}";
                }
            };
        }


        @Override
        public int fillPred(int[] dest, SeqStatus status) {
            // same effect but is not always the most efficient method
            //return pred.fillArrayWithFilter(dest, i -> isNode(i, status));
            return switch (status) {
                case REQUIRED -> pred.fillArrayWithFilter(dest, i -> getSeqVar().isNode(i, REQUIRED));
                case MEMBER -> pred.fillArrayWithFilter(dest, i -> getSeqVar().isNode(i, MEMBER));
                case POSSIBLE -> pred.fillArrayWithFilter(dest, i -> getSeqVar().isNode(i, POSSIBLE));
                case EXCLUDED -> 0;
                case NOT_EXCLUDED -> pred.fillArray(dest);
                case INSERTABLE -> pred.fillArrayWithFilter(dest, i -> getSeqVar().isNode(i, INSERTABLE));
                case INSERTABLE_REQUIRED -> pred.fillArrayWithFilter(dest, i -> getSeqVar().isNode(i, INSERTABLE_REQUIRED));
                case MEMBER_ORDERED -> fillOrdered(dest, i -> hasEdge(i, me));
            };
        }

        @Override
        public int fillPred(int[] dest) {
            return fillPred(dest, NOT_EXCLUDED);
        }

        @Override
        public int nPred() {
            return pred.size();
        }

        @Override
        public int fillSucc(int[] dest, SeqStatus status) {
            return switch (status) {
                case REQUIRED -> succ.fillArrayWithFilter(dest, i -> getSeqVar().isNode(i, REQUIRED));
                case MEMBER -> succ.fillArrayWithFilter(dest, i -> getSeqVar().isNode(i, MEMBER));
                case POSSIBLE -> succ.fillArrayWithFilter(dest, i -> getSeqVar().isNode(i, POSSIBLE));
                case EXCLUDED -> 0;
                case NOT_EXCLUDED -> succ.fillArray(dest);
                case INSERTABLE -> succ.fillArrayWithFilter(dest, i -> getSeqVar().isNode(i, INSERTABLE));
                case INSERTABLE_REQUIRED ->
                        succ.fillArrayWithFilter(dest, i -> getSeqVar().isNode(i, INSERTABLE_REQUIRED));
                case MEMBER_ORDERED -> fillOrdered(dest, i -> hasEdge(me, i));
            };
        }

        @Override
        public int fillSucc(int[] dest) {
            return fillSucc(dest, NOT_EXCLUDED);
        }

        @Override
        public int nSucc() {
            return succ.size();
        }

        @Override
        public int fillInsert(int[] dest) {
            if (isNode(MEMBER) || isNode(EXCLUDED))
                return 0;
            int nPred = pred.size();
            int nMember = CPSeqVarImpl.this.nMember.value();
            if (nMember < nPred) {
                return fillOrdered(dest, i -> hasInsert(i, me));
            }
             // faster to iterate over the edges going up to this node
            return pred.fillArrayWithFilter(dest, i -> hasInsert(i, me));
        }

        @Override
        public int nInsert() {
            return isMember(me) ? 0 : nInsert.value();
        }

        @Override
        public void whenInsert(Runnable f) {
            onInsert.push(constraintClosure(f));
        }

        @Override
        public void whenExclude(Runnable f) {
            onExclude.push(constraintClosure(f));
        }

        @Override
        public void whenRequire(Runnable f) {
            onRequire.push(constraintClosure(f));
        }

        @Override
        public void whenInsertRemoved(Runnable f) {
            onInsertRemoved.push(constraintClosure(f));
        }

        @Override
        public void propagateOnInsert(CPConstraint c) {
            onInsert.push(c);
        }

        @Override
        public void propagateOnExclude(CPConstraint c) {
            onExclude.push(c);
        }

        @Override
        public void propagateOnRequire(CPConstraint c) {
            onRequire.push(c);
        }

        @Override
        public void propagateOnInsertRemoved(CPConstraint c) {
            onInsertRemoved.push(c);
        }

    }

    @Override
    public CPSolver getSolver() {
        return cp;
    }

    @Override
    public boolean isFixed() {
        return domain.nPossible() == 0 && domain.nIncluded() == nMember.value();
    }

    @Override
    public CPNodeVar getNodeVar(int node) {
        return nodes[node];
    }

    @Override
    public int fillNode(int[] dest, SeqStatus status) {
        return switch (status) {
            case REQUIRED -> domain.fillIncluded(dest);
            case MEMBER -> domain.fillIncluded(dest, i -> isNode(i, MEMBER));
            case POSSIBLE -> domain.fillPossible(dest);
            case EXCLUDED -> domain.fillExcluded(dest);
            case NOT_EXCLUDED -> domain.fillIncludedAndPossible(dest);
            case INSERTABLE -> // required not yet inserted and possible nodes
                    domain.fillIncludedAndPossible(dest, i -> !isNode(i, MEMBER));
            case INSERTABLE_REQUIRED -> domain.fillIncluded(dest, i -> !isNode(i, MEMBER));
            case MEMBER_ORDERED -> {
                dest[0] = start;
                int n = nMember.value();
                for (int i = 1; i < n; i++) {
                    dest[i] = memberAfter(dest[i - 1]);
                }
                yield n;
            }
        };
    }

    private int fillOrdered(int[] dest, Predicate<Integer> predicate) {
        int i = 0;
        int current = start;
        while (current != end) {
            if (predicate.test(current)) {
                dest[i++] = current;
            }
            current = memberAfter(current);
        }
        if (predicate.test(end)) {
            dest[i] = current;
            i++;
        }
        return i;
    }

    @Override
    public int nNode(SeqStatus status) {
        return switch (status) {
            case REQUIRED -> domain.nIncluded();
            case MEMBER, MEMBER_ORDERED -> nMember.value();
            case POSSIBLE -> domain.nPossible();
            case EXCLUDED -> domain.nExcluded();
            case NOT_EXCLUDED -> domain.nIncluded() + domain.nPossible();
            case INSERTABLE -> insertable.size();
            case INSERTABLE_REQUIRED -> domain.nIncluded() - nMember.value();
        };
    }

    @Override
    public int nNode() {
        return nNodes;
    }

    private boolean isMember(int node) {
        return succ[node].value() != node;// && domain.isIncluded(node);
    }

    @Override
    public boolean isNode(int node, SeqStatus status) {
        return switch (status) {
            case REQUIRED -> domain.isIncluded(node);
            case MEMBER, MEMBER_ORDERED -> isMember(node);
            case POSSIBLE -> domain.isPossible(node);
            case EXCLUDED -> domain.isExcluded(node);
            case NOT_EXCLUDED -> domain.isIncluded(node) || domain.isPossible(node);
            case INSERTABLE -> insertable.contains(node);
            case INSERTABLE_REQUIRED -> domain.isIncluded(node) && !isMember(node);
        };
    }

    @Override
    public int start() {
        return start;
    }

    @Override
    public int end() {
        return end;
    }

    @Override
    public int memberAfter(int node) {
        return succ[node].value();
    }

    @Override
    public int memberBefore(int node) {
        return pred[node].value();
    }

    @Override
    public int fillPred(int node, int[] dest, SeqStatus status) {
        return nodes[node].fillPred(dest, status);
    }

    @Override
    public int fillPred(int node, int[] dest) {
        return fillPred(node, dest, NOT_EXCLUDED);
    }

    @Override
    public int nPred(int node) {
        return nodes[node].pred.size();
    }

    @Override
    public int fillSucc(int node, int[] dest, SeqStatus status) {
        return nodes[node].fillSucc(dest, status);
    }

    @Override
    public int fillSucc(int node, int[] dest) {
        return fillSucc(node, dest, NOT_EXCLUDED);
    }

    @Override
    public int nSucc(int node) {
        return nodes[node].succ.size();
    }

    @Override
    public int fillInsert(int node, int[] dest) {
        return nodes[node].fillInsert(dest);
    }

    @Override
    public int nInsert(int node) {
        return nodes[node].nInsert();
    }

    @Override
    public boolean hasEdge(int from, int to) {
        boolean link1 = nodes[to].pred.contains(from);
        assert link1 == nodes[from].succ.contains(to);
        return link1;
    }

    @Override
    public boolean hasInsert(int prev, int node) {
        return isNode(prev, MEMBER) && isNode(node, INSERTABLE) && hasEdge(prev, node) && hasEdge(node, memberAfter(prev));
    }

    @Override
    public CPBoolVar isNodeRequired(int node) {
        return nodes[node].isRequired();
    }

    @Override
    public void exclude(int node) {
        if (domain.isIncluded(node)) {
            throw INCONSISTENCY;
        }
        if (domain.isPossible(node)) {
            domain.exclude(node);
            insertable.remove(node);
            nodes[node].nInsert.setValue(0);
            int nPred = fillPred(node, values);
            for (int i = 0; i < nPred ; i++) {
                int pred = values[i];
                removeEdge(pred, node);
            }
            int nSucc = fillSucc(node, values);
            for (int i = 0; i < nSucc ; i++) {
                int succ = values[i];
                removeEdge(node, succ);
            }
            listener.exclude();
            nodes[node].listener.exclude();
            if (isFixed())
                listener.fix();
        }
    }

    @Override
    public void require(int node) {
        if (domain.isExcluded(node)) {
            throw INCONSISTENCY;
        }
        if (domain.isPossible(node)) {
            domain.include(node);
            if (nInsert(node) == 1) {
                insertAtOnlyRemainingPlace(node);
            }
            listener.require();
            nodes[node].listener.require();
        }
    }

    @Override
    public void insert(int prev, int node) {
        if (!hasInsert(prev, node)) {
            if (isNode(prev, MEMBER) && isNode(node, MEMBER)) {
                if (memberBefore(node) != prev) {
                    throw new IllegalArgumentException("Cannot insert two member nodes that are not consecutive");
                }
                return;
            } else {
                throw INCONSISTENCY;
            }
        }
        int after = memberAfter(prev);
        nMember.increment();
        // reconstruct the links with predecessors and successors
        this.succ[prev].setValue(node);
        this.succ[node].setValue(after);
        this.pred[node].setValue(prev);
        this.pred[after].setValue(node);
        // destroy the links between prev and after
        removeEdge(prev, after);
        // require the node
        require(node); // does not trigger an insertion
        // update the set of insertable nodes
        insertable.remove(node);
        // update the counter of insertions and remove edges between member nodes that are not consecutive
        int nInsertable = fillNode(values, NOT_EXCLUDED);
        for (int i = 0 ; i < nInsertable ; i++) {
            int vertex = values[i];
            if (vertex == node)
                continue;
            if (isNode(vertex, MEMBER)) {
                if (vertex != prev) {
                    removeEdge(vertex, node);
                }
                if (vertex != after) {
                    removeEdge(node, vertex);
                }
            } else if (hasInsert(prev, vertex)) {
                nodes[vertex].nInsert.increment();
            } else {
                // vertex could not be inserted between prev and after
                // this prevent vertex from being inserted between those two nodes
                removeEdge(vertex, node);
                removeEdge(node, vertex);
            }
        }
        nodes[node].nInsert.setValue(0);
        // notify the listeners
        nodes[node].listener.insert();
        listener.insert();
        if (isFixed())
            listener.fix();
    }

    @Override
    public void removeDetour(int prev, int node, int succ) {
        if (!isNode(prev, MEMBER) || !isNode(succ, MEMBER)) {
            throw new IllegalArgumentException("A detour is always defined between two members nodes that are following each other");
        }
        if (prev == node || node == succ || prev == succ) {
            return;
        }
        if (!isNode(node, EXCLUDED)) {
            // most detours are done between two consecutive nodes
            // first fast check if prev and succ are consecutive nodes
            int after = memberAfter(prev);
            boolean areFollowing = false;
            if (after == succ) { // nodes are consecutive
                areFollowing = true;
            } else {
                // check if the succ if following prev, Otherwise the function is wrongly called, for instance
                // with "remove end -> x -> begin"
                int current = after;
                boolean foundNode = current == node;
                while (!areFollowing && current != start) {
                    current = memberAfter(current);
                    areFollowing = current == succ;
                    foundNode = foundNode || current == node;
                }
            }
            // only remove the detours if the member nodes are following each other
            // if the member nodes are not following each other, no such detour can happen anyway
            if (areFollowing) {
                for (int endPoint = prev; endPoint != succ ;) {
                    if (hasInsert(endPoint, node)) {
                        removeEdge(endPoint, node);
                        endPoint = memberAfter(endPoint);
                        removeEdge(node, endPoint);
                        nodes[node].nInsert.decrement();
                        int nInsert = nodes[node].nInsert();
                        if (isNode(node, REQUIRED) && nInsert == 1) {
                            insertAtOnlyRemainingPlace(node);
                        }
                        if (nInsert == 0) {
                            exclude(node);
                        }
                        nodes[node].listener.insertRemoved();
                        listener.insertRemoved();
                    } else {
                        endPoint = memberAfter(endPoint);
                    }
                }
            }
        }
    }

    private void insertAtOnlyRemainingPlace(int node) {
        int nPred = fillInsert(node, values);
        assert nPred == 1;
        int pred = values[0];
        insert(pred, node);
    }

    /**
     * Remove a directed edge from -> to
     * @param from origin of the edge
     * @param to destination of the edge
     */
    private void removeEdge(int from, int to) {
        nodes[from].succ.remove(to);
        nodes[to].pred.remove(from);
    }

    @Override
    public void whenFixed(Runnable f) {
        onFix.push(constraintClosure(f));
    }

    @Override
    public void whenInsert(Runnable f) {
        onInsert.push(constraintClosure(f));
    }

    @Override
    public void whenInsertRemoved(Runnable f) {
        onInsertRemoved.push(constraintClosure(f));
    }

    @Override
    public void whenExclude(Runnable f) {
        onExclude.push(constraintClosure(f));
    }

    @Override
    public void whenRequire(Runnable f) {
        onRequire.push(constraintClosure(f));
    }

    @Override
    public void propagateOnFix(CPConstraint c) {
        onFix.push(c);
    }

    @Override
    public void propagateOnInsertRemoved(CPConstraint c) {
        onInsertRemoved.push(c);
    }

    @Override
    public void propagateOnInsert(CPConstraint c) {
        onInsert.push(c);
    }

    @Override
    public void propagateOnExclude(CPConstraint c) {
        onExclude.push(c);
    }

    @Override
    public void propagateOnRequire(CPConstraint c) {
        onRequire.push(c);
    }

    private CPConstraint constraintClosure(Runnable f) {
        CPConstraint c = new CPConstraintClosure(cp, f);
        getSolver().post(c, false);
        return c;
    }

    private void scheduleAll(StateStack<CPConstraint> constraints) {
        for (int i = 0; i < constraints.size(); i++)
            cp.schedule(constraints.get(i));
    }

    @Override
    public String membersOrdered() {
        return membersOrdered(" -> ");
    }

    @Override
    public String membersOrdered(String join) {
        return membersOrdered(join, i -> true);
    }

    @Override
    public String membersOrdered(String join, Predicate<Integer> filter) {
        StringJoiner joiner = new StringJoiner(join);
        int current = start;
        while (current != end) {
            if (filter.test(current))
                joiner.add(String.valueOf(current));
            current = memberAfter(current);
        }
        if (filter.test(end))
            joiner.add(String.valueOf(end));
        return joiner.toString();
    }

    @Override
    public String toString() {
        return membersOrdered();
    }

    @Override
    public ModelProxy getModelProxy() {
        return getSolver().getModelProxy();
    }
}
