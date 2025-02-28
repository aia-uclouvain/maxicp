package org.maxicp.modeling.symbolic;


import org.maxicp.modeling.BoolVar;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.sequence.SeqStatus;

import java.util.Set;

public class SeqVarImpl implements SymbolicSeqVar {

    private final ModelProxy modelProxy;
    private final int start;
    private final int end;
    private final int nNode;

    public SeqVarImpl(ModelProxy modelProxy, int nNode, int start, int end) {
        this.modelProxy = modelProxy;
        this.nNode = nNode;
        this.start = start;
        this.end = end;
    }

    /**
     * Fill an array with values between 0 and nNode, except for values marked as forbidden
     *
     * A lot of operations fill all nodes from 0 between n except a few ones.
     * This method is suited for those operations directly
     * @param dest array where values must be written
     * @param forbidden values that cannot be written in the array
     * @return number of values written in the array
     */
    private int fillAllNodesExcept(int[] dest, Set<Integer> forbidden) {
        int cnt = 0;
        for (int node = 0 ; node < nNode ; node++) {
            if (!forbidden.contains(node)) {
                dest[cnt++] = node;
            }
        }
        return cnt;
    }

    @Override
    public ModelProxy getModelProxy() {
        return modelProxy;
    }

    @Override
    public String toString() {
        if (getModelProxy().isConcrete()) {
            return getModelProxy().getConcreteModel().getConcreteVar(this).toString();
        }
        return String.format("%d -> %d", start, end);
    }

    @Override
    public int defaultStart() {
        return start;
    }

    @Override
    public int defaultEnd() {
        return end;
    }

    @Override
    public int defaultNNode() {
        return nNode;
    }

    @Override
    public int defaultFillNode(int[] dest, SeqStatus status) {
        return switch (status) {
            case REQUIRED, MEMBER, MEMBER_ORDERED -> {
                dest[0] = start;
                dest[1] = end;
                yield 2;
            }
            case POSSIBLE, INSERTABLE -> fillAllNodesExcept(dest, Set.of(start, end));
            case EXCLUDED, INSERTABLE_REQUIRED -> 0;
            case NOT_EXCLUDED -> fillAllNodesExcept(dest, Set.of());
        };
    }

    @Override
    public int defaultNNode(SeqStatus status) {
        return switch (status) {
            case REQUIRED, MEMBER, MEMBER_ORDERED -> 2;
            case POSSIBLE, INSERTABLE -> nNode - 2;
            case EXCLUDED, INSERTABLE_REQUIRED -> 0;
            case NOT_EXCLUDED -> nNode;
        };
    }

    @Override
    public boolean defaultIsNode(int node, SeqStatus status) {
        return switch (status) {
            case REQUIRED, MEMBER, MEMBER_ORDERED -> node == start || node == end;
            case POSSIBLE, INSERTABLE -> node >= 0 && node < nNode && node != start && node != end;
            case EXCLUDED, INSERTABLE_REQUIRED -> false;
            case NOT_EXCLUDED -> node >= 0 && node < nNode;
        };
    }

    @Override
    public int defaultMemberAfter(int node) {
        if (node == start)
            return end;
        if (node == end)
            return start;
        return node;
    }

    @Override
    public int defaultMemberBefore(int node) {
        if (node == start)
            return end;
        if (node == end)
            return start;
        return node;
    }

    @Override
    public int defaultFillPred(int node, int[] dest, SeqStatus status) {
        if (node == start)
            return 0;
        return switch (status) {
            case REQUIRED, MEMBER, MEMBER_ORDERED -> {
                dest[0] = start;
                yield 1;
            }
            case POSSIBLE, INSERTABLE -> fillAllNodesExcept(dest, Set.of(start, node, end));
            case EXCLUDED, INSERTABLE_REQUIRED -> 0;
            case NOT_EXCLUDED -> fillAllNodesExcept(dest, Set.of(node, end));
        };
    }

    @Override
    public int defaultNPred(int node) {
        if (node == start || node < 0 || node >= nNode)
            return 0;
        return nNode - 2;
    }

    @Override
    public int defaultFillSucc(int node, int[] dest, SeqStatus status) {
        if (node == end)
            return 0;
        return switch (status) {
            case REQUIRED, MEMBER, MEMBER_ORDERED -> {
                dest[0] = end;
                yield 1;
            }
            case POSSIBLE, INSERTABLE -> fillAllNodesExcept(dest, Set.of(start, node, end));
            case EXCLUDED, INSERTABLE_REQUIRED -> 0;
            case NOT_EXCLUDED -> fillAllNodesExcept(dest, Set.of(node, start));
        };
    }

    @Override
    public int defaultNSucc(int node) {
        if (node == end || node < 0 || node >= nNode)
            return 0;
        return nNode - 2;
    }

    @Override
    public int defaultFillInsert(int node, int[] dest) {
        if (node < 0 || node >= nNode || node == start || node == end)
            return 0;
        dest[0] = start;
        return 1;
    }

    @Override
    public int defaultNInsert(int node) {
        if (node < 0 || node >= nNode || node == start || node == end)
            return 0;
        return 1;
    }

    @Override
    public boolean defaultHasEdge(int from, int to) {
        if (to == start || to < 0 || to >= nNode || to == from)
            return false;
        if (from == end || from < 0 || from >= nNode)
            return false;
        return true;
    }

    @Override
    public boolean defaultHasInsert(int prev, int node) {
        return prev == start && isNode(node, SeqStatus.INSERTABLE);
    }

    @Override
    public BoolVar isNodeRequired(int node) {
        return new IsNodeRequired(this, node);
    }
}
