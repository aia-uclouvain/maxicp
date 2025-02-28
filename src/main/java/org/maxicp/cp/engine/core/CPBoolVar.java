/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.concrete.ConcreteBoolVar;
import org.maxicp.util.exception.InconsistencyException;

/**
 * Boolean variable, that can be used as a 0-1 IntVar.
 * <p>0 corresponds to false, and 1 corresponds to true
 */
public interface CPBoolVar extends CPIntVar, ConcreteBoolVar {

    /**
     * Tests if the variable is fixed to true
     * @return true if the variable is fixed to true (value 1)
     */
    boolean isTrue();

    /**
     * Tests if the variable is fixed to false
     * @return true if the variable is fixed to false (value 0)
     */
    boolean isFalse();

    /**
     * Fixes the variable
     * @param b the value to assign to this boolean variable
     * @exception InconsistencyException
     *            is thrown if the value is not in the domain
     */
    void fix(boolean b);

    @Override
    default int min() {
        if(isFixed()) {
            try {
                return evaluate();
            } catch (VariableNotFixedException ex) {
                throw new RuntimeException();
            }
        }
        return 0;
    }

    @Override
    default int max() {
        if(isFixed()) {
            try {
                return evaluate();
            } catch (VariableNotFixedException ex) {
                throw new RuntimeException();
            }
        }
        return 1;
    }

    @Override
    default int fillArray(int[] array) {
        if(isFixed()) {
            try {
                array[0] = evaluate();
                return 1;
            }
            catch (VariableNotFixedException ex) {
                //never happens
                assert(false);
            }
        }
        array[0] = 0;
        array[1] = 1;
        return 2;
    }

    @Override
    default int size() { return isFixed() ? 1 : 2; }

    @Override
    default boolean contains(int v) {
        return min() <= v && v <= max();
    }

    default boolean evaluateBool() throws VariableNotFixedException {
        if(min() != 0 || max() == 0) return min() != 0;
        throw new VariableNotFixedException();
    }
}
