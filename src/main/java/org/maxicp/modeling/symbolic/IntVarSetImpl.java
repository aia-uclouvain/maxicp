/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.modeling.symbolic;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.VariableNotFixedException;

import java.util.Set;
import java.util.TreeSet;

public class IntVarSetImpl implements SymbolicIntVar {
    public final TreeSet<Integer> dom;
    private final ModelProxy modelProxy;
    private final String id;

    public IntVarSetImpl(ModelProxy modelProxy, String id, Set<Integer> domain) {
        dom = new TreeSet<>();
        dom.addAll(domain);
        assert !domain.isEmpty();
        this.id = id;
        this.modelProxy = modelProxy;
    }

    public IntVarSetImpl(ModelProxy modelProxy, Set<Integer> domain) {
        this(modelProxy, null, domain);
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        if(dom.size() == 1) return dom.first();
        throw new VariableNotFixedException();
    }

    @Override
    public int defaultMin() {
        return dom.first();
    }

    @Override
    public int defaultMax() {
        return dom.last();
    }

    @Override
    public int defaultSize() {
        return dom.size();
    }

    @Override
    public boolean defaultContains(int v) {
        return dom.contains(v);
    }

    @Override
    public int defaultFillArray(int[] array) {
        int idx = 0;
        for(Integer i: dom) {
            array[idx] = i;
            idx++;
        }
        return idx;
    }

    @Override
    public String toString() {
        if (getModelProxy().isConcrete()) {
            return getModelProxy().getConcreteModel().getConcreteVar(this).toString();
        }
        if(id != null)
            return id;
        return show();
    }

    @Override
    public ModelProxy getModelProxy() {
        return modelProxy;
    }
}
