package org.maxicp.cp.engine.core;

import org.maxicp.modeling.algebra.sequence.SeqStatus;

import java.util.function.Predicate;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.MEMBER_ORDERED;

public class CPNodeVarViewFlip implements CPNodeVar {

    private final CPNodeVar nodeVar;

    public CPNodeVarViewFlip(CPNodeVar nodeVar) {
        this.nodeVar = nodeVar;
    }

    @Override
    public CPSolver getSolver() {
        return nodeVar.getSolver();
    }

    @Override
    public CPSeqVar getSeqVar() {
        return nodeVar.getSeqVar();
    }

    @Override
    public int node() {
        return nodeVar.node();
    }

    @Override
    public boolean isNode(SeqStatus status) {
        return nodeVar.isNode(status);
    }

    @Override
    public CPBoolVar isRequired() {
        return nodeVar.isRequired();
    }

    @Override
    public int fillPred(int[] dest, SeqStatus status) {
        if (status == MEMBER_ORDERED) {
            int me = node();
            return fillOrdered(dest, i -> nodeVar.getSeqVar().hasEdge(me, i));
        } else {
            return nodeVar.fillSucc(dest, status);
        }
    }

    @Override
    public int fillPred(int[] dest) {
        return nodeVar.fillSucc(dest);
    }

    @Override
    public int nPred() {
        return nodeVar.nSucc();
    }

    private int fillOrdered(int[] dest, Predicate<Integer> predicate) {
        int i = 0;
        int current = nodeVar.getSeqVar().end();
        int start = nodeVar.getSeqVar().start();
        while (current != start) {
            if (predicate.test(current)) {
                dest[i++] = current;
            }
            current = nodeVar.getSeqVar().memberBefore(current);
        }
        if (predicate.test(start)) {
            dest[i] = current;
            i++;
        }
        return i;
    }

    @Override
    public int fillSucc(int[] dest, SeqStatus status) {
        if (status == MEMBER_ORDERED) {
            int me = node();
            return fillOrdered(dest, i -> nodeVar.getSeqVar().hasEdge(i, me));
        } else {
            return nodeVar.fillPred(dest, status);
        }
    }

    @Override
    public int fillSucc(int[] dest) {
        return nodeVar.fillPred(dest);
    }

    @Override
    public int nSucc() {
        return nodeVar.nPred();
    }

    @Override
    public int fillInsert(int[] dest) {
        int nInsert = nodeVar.fillInsert(dest);
        for (int i = 0 ; i < nInsert ; i++) {
            int pred = dest[i];
            int succ = nodeVar.getSeqVar().memberAfter(pred);
            dest[i] = succ;
        }
        return nInsert;
    }

    @Override
    public int nInsert() {
        return nodeVar.nInsert();
    }

    @Override
    public void whenInsert(Runnable f) {
        nodeVar.whenInsert(f);
    }

    @Override
    public void whenExclude(Runnable f) {
        nodeVar.whenExclude(f);
    }

    @Override
    public void whenRequire(Runnable f) {
        nodeVar.whenRequire(f);
    }

    @Override
    public void whenInsertRemoved(Runnable f) {
        nodeVar.whenInsertRemoved(f);
    }

    @Override
    public void propagateOnInsert(CPConstraint c) {
        nodeVar.propagateOnInsert(c);
    }

    @Override
    public void propagateOnExclude(CPConstraint c) {
        nodeVar.propagateOnExclude(c);
    }

    @Override
    public void propagateOnRequire(CPConstraint c) {
        nodeVar.propagateOnRequire(c);
    }

    @Override
    public void propagateOnInsertRemoved(CPConstraint c) {
        nodeVar.propagateOnInsertRemoved(c);
    }
}
