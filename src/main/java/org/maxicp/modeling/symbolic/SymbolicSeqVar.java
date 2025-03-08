package org.maxicp.modeling.symbolic;

import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.algebra.sequence.SeqStatus;

public interface SymbolicSeqVar extends SeqVar, SymbolicVar {

    int defaultStart();

    int defaultEnd();

    int defaultNNode();

    int defaultFillNode(int[] dest, SeqStatus status);

    int defaultNNode(SeqStatus status);

    boolean defaultIsNode(int node, SeqStatus status);

    int defaultMemberAfter(int node);

    int defaultMemberBefore(int node);

    int defaultFillPred(int node, int[] dest, SeqStatus status);

    int defaultNPred(int node);

    int defaultFillSucc(int node, int[] dest, SeqStatus status);

    int defaultNSucc(int node);

    int defaultFillInsert(int node, int[] dest);

    int defaultNInsert(int node);

    boolean defaultHasEdge(int from, int to);

    boolean defaultHasInsert(int prev, int node);

    default int defaultFillPred(int node, int[] dest) {
        return defaultFillPred(node, dest, SeqStatus.NOT_EXCLUDED);
    }

    default int defaultFillSucc(int node, int[] dest) {
        return defaultFillSucc(node, dest, SeqStatus.NOT_EXCLUDED);
    }

    default int start() {
        return defaultStart();
    }

    default int end() {
        return defaultEnd();
    }

    default int nNode() {
        return defaultNNode();
    }

    default int fillNode(int[] dest, SeqStatus status) {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).fillNode(dest, status);
        return defaultFillNode(dest, status);
    }

    default int nNode(SeqStatus status) {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).nNode(status);
        return defaultNNode(status);
    }

    default boolean isNode(int node, SeqStatus status) {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).isNode(node, status);
        return defaultIsNode(node, status);
    }

    default int memberAfter(int node) {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).memberAfter(node);
        return defaultMemberAfter(node);
    }

    default int memberBefore(int node) {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).memberBefore(node);
        return defaultMemberBefore(node);
    }

    default int fillPred(int node, int[] dest, SeqStatus status) {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).fillPred(node, dest, status);
        return defaultFillPred(node, dest, status);
    }

    default int nPred(int node) {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).nPred(node);
        return defaultNPred(node);
    }

    default int fillSucc(int node, int[] dest, SeqStatus status) {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).fillSucc(node, dest, status);
        return defaultFillSucc(node, dest, status);
    }

    default int nSucc(int node) {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).nSucc(node);
        return defaultNSucc(node);
    }

    default int fillInsert(int node, int[] dest) {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).fillInsert(node, dest);
        return defaultFillInsert(node, dest);
    }

    default int nInsert(int node) {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).nInsert(node);
        return defaultNInsert(node);
    }

    default boolean hasEdge(int from, int to) {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).hasEdge(from, to);
        return defaultHasEdge(from, to);
    }

    default boolean hasInsert(int prev, int node) {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).hasInsert(prev, node);
        return defaultHasInsert(prev, node);
    }
}