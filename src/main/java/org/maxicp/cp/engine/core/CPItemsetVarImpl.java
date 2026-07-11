/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.patternvar.ItemsetCard;
import org.maxicp.cp.engine.constraints.patternvar.ItemsetSupport;
import org.maxicp.cp.engine.core.mining.TransactionDb;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.state.datastructures.StateSparseBitSet;
import org.maxicp.state.datastructures.StateStack;
import org.maxicp.state.datastructures.StateTriPartition;
import org.maxicp.util.exception.InconsistencyException;

import java.security.InvalidParameterException;

/**
 * Implementation of an itemset pattern variable.
 * <p>
 * The structural domain is a {@link StateTriPartition} of the item universe
 * (required / possible / excluded items). The occurrence kernel is a reversible
 * cover implemented with a {@link StateSparseBitSet} over the transactions,
 * intersected with the vertical representation (tidset) of every required item,
 * as in the CoverSize global constraint.
 */
public class CPItemsetVarImpl implements CPItemsetVar {

    private final CPSolver cp;
    private final TransactionDb db;
    private final StateTriPartition domain;
    private final StateSparseBitSet cover;
    private final StateSparseBitSet.SupportBitSet[] columns;
    private final CPIntVar size;
    private CPIntVar support;
    private final StateStack<CPConstraint> onDomain;
    private final StateStack<CPConstraint> onFix;
    private final int[] items; // scratch buffer

    /**
     * Creates an itemset pattern variable over the items of a transaction database.
     * Initially all items are possible and the cover contains all transactions.
     *
     * @param cp the solver in which the variable is created
     * @param db the transaction database
     */
    public CPItemsetVarImpl(CPSolver cp, TransactionDb db) {
        if (db.nItems() < 1) throw new InvalidParameterException("at least one item in the database");
        if (db.nTrans() < 1) throw new InvalidParameterException("at least one transaction in the database");
        this.cp = cp;
        this.db = db;
        int n = db.nItems();
        this.domain = new StateTriPartition(cp.getStateManager(), n);
        this.cover = new StateSparseBitSet(cp.getStateManager(), db.nTrans());
        this.columns = new StateSparseBitSet.SupportBitSet[n];
        for (int i = 0; i < n; i++) {
            columns[i] = cover.new SupportBitSet();
        }
        for (int t = 0; t < db.nTrans(); t++) {
            for (int item : db.transaction(t)) {
                columns[item].set(t);
            }
        }
        this.onDomain = new StateStack<>(cp.getStateManager());
        this.onFix = new StateStack<>(cp.getStateManager());
        this.items = new int[n];
        this.size = CPFactory.makeIntVar(cp, 0, n);
        cp.post(new ItemsetCard(this, size));
    }

    @Override
    public TransactionDb database() {
        return db;
    }

    @Override
    public int nItems() {
        return domain.size();
    }

    @Override
    public boolean isFixed() {
        return domain.nPossible() == 0;
    }

    @Override
    public int nRequired() {
        return domain.nIncluded();
    }

    @Override
    public int nPossible() {
        return domain.nPossible();
    }

    @Override
    public int nExcluded() {
        return domain.nExcluded();
    }

    @Override
    public boolean isRequired(int i) {
        return domain.isIncluded(i);
    }

    @Override
    public boolean isPossible(int i) {
        return domain.isPossible(i);
    }

    @Override
    public boolean isExcluded(int i) {
        return domain.isExcluded(i);
    }

    @Override
    public void require(int i) {
        if (domain.isExcluded(i)) {
            throw new InconsistencyException();
        }
        if (domain.isPossible(i)) {
            domain.include(i);
            cover.intersect(columns[i]);
            scheduleAll(onDomain);
            if (isFixed()) {
                scheduleAll(onFix);
            }
        }
    }

    @Override
    public void exclude(int i) {
        if (domain.isIncluded(i)) {
            throw new InconsistencyException();
        }
        if (domain.isPossible(i)) {
            domain.exclude(i);
            scheduleAll(onDomain);
            if (isFixed()) {
                scheduleAll(onFix);
            }
        }
    }

    @Override
    public void requireAll() {
        if (domain.nPossible() > 0) {
            int n = domain.fillPossible(items);
            for (int k = 0; k < n; k++) {
                domain.include(items[k]);
                cover.intersect(columns[items[k]]);
            }
            scheduleAll(onDomain);
            scheduleAll(onFix);
        }
    }

    @Override
    public void excludeAll() {
        if (domain.nPossible() > 0) {
            domain.excludeAllPossible();
            scheduleAll(onDomain);
            scheduleAll(onFix);
        }
    }

    @Override
    public int fillRequired(int[] dest) {
        return domain.fillIncluded(dest);
    }

    @Override
    public int fillPossible(int[] dest) {
        return domain.fillPossible(dest);
    }

    @Override
    public int fillExcluded(int[] dest) {
        return domain.fillExcluded(dest);
    }

    @Override
    public int coverSize() {
        return cover.count();
    }

    @Override
    public int supportAfter(int i) {
        return cover.countIntersection(columns[i]);
    }

    @Override
    public int supportAfter(int i, int cap) {
        return cover.countIntersection(columns[i], cap);
    }

    @Override
    public boolean coverSubsetOf(int i) {
        return cover.isSubsetOf(columns[i]);
    }

    @Override
    public int minSupport() {
        int n = domain.fillPossible(items);
        return cover.countIntersectionAll(columns, items, n);
    }

    @Override
    public CPIntVar size() {
        return size;
    }

    @Override
    public CPIntVar support() {
        if (support == null) {
            support = CPFactory.makeIntVar(cp, 0, db.nTrans());
            cp.post(new ItemsetSupport(this, support));
        }
        return support;
    }

    protected void scheduleAll(StateStack<CPConstraint> constraints) {
        for (int i = 0; i < constraints.size(); i++) {
            cp.schedule(constraints.get(i));
        }
    }

    @Override
    public void propagateOnDomainChange(CPConstraint c) {
        onDomain.push(c);
    }

    @Override
    public void propagateOnFix(CPConstraint c) {
        onFix.push(c);
    }

    @Override
    public CPSolver getSolver() {
        return cp;
    }

    @Override
    public ModelProxy getModelProxy() {
        return getSolver().getModelProxy();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        int n = domain.fillIncluded(items);
        java.util.Arrays.sort(items, 0, n);
        for (int k = 0; k < n; k++) {
            if (k > 0) sb.append(" ");
            sb.append(items[k]);
        }
        sb.append("} + ").append(nPossible()).append(" possible, cover=").append(coverSize());
        return sb.toString();
    }
}
