/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.modeling.symbolic;

import org.maxicp.modeling.Constraint;
import org.maxicp.modeling.Model;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.util.exception.NotImplementedException;

import java.util.Iterator;

public record SymbolicModel(Constraint constraint, SymbolicModel parent, ModelProxy modelProxy) implements Model, Iterable<Constraint> {
    @Override
    public SymbolicModel symbolicCopy() {
        return this;
    }

    @Override
    public Iterable<Constraint> getConstraints() {
        return this;
    }

    @Override
    public ModelProxy getModelProxy() {
        return modelProxy;
    }


    public static SymbolicModel emptyModel(ModelProxy modelProxy) {
        return new SymbolicModel(null, null, modelProxy);
    }

    public boolean isEmpty() {
        return constraint == null && parent == null;
    }

    public SymbolicModel add(Constraint c) {
        return new SymbolicModel(c, this, modelProxy);
    }
    public Objective minimize(Expression expr) {
        return switch (expr) {
            case IntExpression iexpr -> new Minimization(iexpr);
            default -> throw new NotImplementedException();
        };
    }

    public Objective maximize(Expression expr) {
        return switch (expr) {
            case IntExpression iexpr -> new Maximization(iexpr);
            default -> throw new NotImplementedException();
        };
    }

    /**
     * Returns the SymbolicModels that would be created by branching on the constraints given in parameters
     */
    public SymbolicModel[] branch(Constraint... constraints) {
        SymbolicModel[] out = new SymbolicModel[constraints.length];
        for (int i = 0; i < constraints.length; i++) {
            out[i] = add(constraints[i]);
        }
        return out;
    }

    private static class ConstraintIterator implements Iterator<Constraint> {
        private SymbolicModel cur;

        public ConstraintIterator(SymbolicModel start) {
            cur = start;
        }

        @Override
        public boolean hasNext() {
            return cur.parent != null;
        }

        @Override
        public Constraint next() {
            Constraint c = cur.constraint;
            cur = cur.parent;
            return c;
        }
    }

    @Override
    public Iterator<Constraint> iterator() {
        return new ConstraintIterator(this);
    }
}