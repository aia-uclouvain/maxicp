package org.maxicp.cp.engine.core;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.sequence.SeqStatus;

import java.util.function.Predicate;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.MEMBER_ORDERED;

public class CPSeqVarViewFlip implements CPSeqVar {

    private final CPSeqVar seqVar;

    public CPSeqVarViewFlip(CPSeqVar seqVar) {
        this.seqVar = seqVar;
    }

    @Override
    public CPSolver getSolver() {
        return seqVar.getSolver();
    }

    @Override
    public boolean isFixed() {
        return seqVar.isFixed();
    }

    @Override
    public CPNodeVar getNodeVar(int node) {
        return new CPNodeVarViewFlip(seqVar.getNodeVar(node));
    }

    @Override
    public CPBoolVar isNodeRequired(int node) {
        return seqVar.isNodeRequired(node);
    }

    @Override
    public int fillNode(int[] dest, SeqStatus status) {
        if (status == MEMBER_ORDERED) {
            dest[0] = start();
            int n = nNode(SeqStatus.MEMBER);
            for (int i = 1; i < n; i++) {
                dest[i] = memberAfter(dest[i - 1]);
            }
            return n;
        } else{
            return seqVar.fillNode(dest, status);
        }
    }

    @Override
    public int nNode(SeqStatus status) {
        return seqVar.nNode(status);
    }

    @Override
    public int nNode() {
        return seqVar.nNode();
    }

    @Override
    public boolean isNode(int node, SeqStatus status) {
        return seqVar.isNode(node, status);
    }

    @Override
    public int start() {
        return seqVar.end();
    }

    @Override
    public int end() {
        return seqVar.start();
    }

    @Override
    public int memberAfter(int node) {
        return seqVar.memberBefore(node);
    }

    @Override
    public int memberBefore(int node) {
        return seqVar.memberAfter(node);
    }

    @Override
    public int fillPred(int node, int[] dest, SeqStatus status) {
        if (status == MEMBER_ORDERED) {
            return fillOrdered(dest, i -> hasEdge(i, node));
        } else {
            return seqVar.fillSucc(node, dest, status);
        }
    }

    @Override
    public int fillPred(int node, int[] dest) {
        return seqVar.fillSucc(node, dest);
    }

    @Override
    public int nPred(int node) {
        return seqVar.nSucc(node);
    }

    private int fillOrdered(int[] dest, Predicate<Integer> predicate) {
        int i = 0;
        int current = start();
        int end = end();
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
    public int fillSucc(int node, int[] dest, SeqStatus status) {
        if (status == MEMBER_ORDERED) {
            return fillOrdered(dest, i -> hasEdge(node, i));
        } else {
            return seqVar.fillPred(node, dest, status);
        }
    }

    @Override
    public int fillSucc(int node, int[] dest) {
        return seqVar.fillPred(node, dest);
    }

    @Override
    public int nSucc(int node) {
        return seqVar.nPred(node);
    }

    @Override
    public int fillInsert(int node, int[] dest) {
        int nInsert = seqVar.fillInsert(node, dest);
        for (int i = 0 ; i < nInsert ; i++) {
            int pred = dest[i];
            int succ = seqVar.memberAfter(pred);
            dest[i] = succ;
        }
        return nInsert;
    }

    @Override
    public int nInsert(int node) {
        return seqVar.nInsert(node);
    }

    @Override
    public boolean hasEdge(int from, int to) {
        return seqVar.hasEdge(to, from);
    }

    @Override
    public boolean hasInsert(int prev, int node) {
        return seqVar.hasInsert(seqVar.memberBefore(prev), node);
    }

    @Override
    public void exclude(int node) {
        seqVar.exclude(node);
    }

    @Override
    public void require(int node) {
        seqVar.require(node);
    }

    @Override
    public void insert(int prev, int node) {
        seqVar.insert(seqVar.memberBefore(prev), node);
    }

    @Override
    public void notBetween(int prev, int node, int succ) {
        seqVar.notBetween(succ, node, prev);
    }

    @Override
    public void whenFixed(Runnable f) {
        seqVar.whenFixed(f);
    }

    @Override
    public void whenInsert(Runnable f) {
        seqVar.whenInsert(f);
    }

    @Override
    public void whenInsertRemoved(Runnable f) {
        seqVar.whenInsertRemoved(f);
    }

    @Override
    public void whenExclude(Runnable f) {
        seqVar.whenExclude(f);
    }

    @Override
    public void whenRequire(Runnable f) {
        seqVar.whenRequire(f);
    }

    @Override
    public void propagateOnFix(CPConstraint c) {
        seqVar.propagateOnFix(c);
    }

    @Override
    public void propagateOnInsertRemoved(CPConstraint c) {
        seqVar.propagateOnInsertRemoved(c);
    }

    @Override
    public void propagateOnInsert(CPConstraint c) {
        seqVar.propagateOnInsert(c);
    }

    @Override
    public void propagateOnExclude(CPConstraint c) {
        seqVar.propagateOnExclude(c);
    }

    @Override
    public void propagateOnRequire(CPConstraint c) {
        seqVar.propagateOnRequire(c);
    }

    @Override
    public String toString() {
        return membersOrdered();
    }

    @Override
    public ModelProxy getModelProxy() {
        return seqVar.getModelProxy();
    }
}
