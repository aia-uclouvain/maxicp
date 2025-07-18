/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.core;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.Sum;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.state.State;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateStack;

import java.util.Arrays;
import java.util.function.Consumer;

import static org.maxicp.Constants.HORIZON;
import static org.maxicp.util.exception.InconsistencyException.INCONSISTENCY;


/**
 * TODO
 *
 * @author Pierre Schaus
 */
public class CPIntervalVarImplNaiveDecomp implements CPIntervalVar {

    private CPSolver cp;
    private StateStack<CPConstraint> onChange;


    // start + length = end

    CPIntVar start, end, length;

    State<Boolean> isPresent, isAbsent;

    CPBoolVar b; // status variable (true if present, false if absent)

    public CPIntervalVarImplNaiveDecomp(CPSolver cp) {
        this.cp = cp;
        start = CPFactory.makeIntVar(cp, 0, HORIZON);
        length = CPFactory.makeIntVar(cp, 0, HORIZON);
        end = CPFactory.makeIntVar(cp, 0, HORIZON);
        cp.post(new Sum(new CPIntVar[]{start,length}, end));
        isPresent = cp.getStateManager().makeStateRef(Boolean.FALSE);
        isAbsent = cp.getStateManager().makeStateRef(Boolean.FALSE);

        onChange = new StateStack<>(cp.getStateManager());

        this.b = new CPBoolVar() {
            @Override
            public boolean isTrue() {
                return isPresent.value();
            }

            @Override
            public boolean isFalse() {
                return isAbsent.value();
            }

            @Override
            public void fix(boolean b) {
                if (b) {
                    setPresent();
                } else {
                    setAbsent();
                }
            }

            @Override
            public CPSolver getSolver() {
                return cp;
            }

            @Override
            public void whenFixed(Runnable f) {
                onChange.push(constraintClosure(f));
            }

            @Override
            public void whenBoundChange(Runnable f) {
                onChange.push(constraintClosure(f));
            }

            @Override
            public void whenDomainChange(Runnable f) {
                onChange.push(constraintClosure(f));
            }

            @Override
            public void whenDomainChange(Consumer<DeltaCPIntVar> f) {
                CPConstraint c = new CPConstraintClosureWithDelta(cp,this,f);
                getSolver().post(c, false);
            }

            @Override
            public void propagateOnDomainChange(CPConstraint c) {
                onChange.push(c);
            }

            @Override
            public void propagateOnFix(CPConstraint c) {
                onChange.push(c);
            }

            @Override
            public void propagateOnBoundChange(CPConstraint c) {
                onChange.push(c);
            }

            @Override
            public int min() {
                if (isPresent.value()) {
                    return 1;
                } else {
                    return 0;
                }
            }

            @Override
            public int max() {
                if (isAbsent.value()) {
                    return 0;
                } else {
                    return 1;
                }
            }

            @Override
            public boolean isFixed() {
                return isPresent.value() || isAbsent.value();
            }

            @Override
            public void remove(int v) {
                if (v == 0) {
                    setPresent();
                } else if (v == 1) {
                    setAbsent();
                }
            }

            @Override
            public void fix(int v) {
                if (v == 0) {
                    setAbsent();
                } else if (v == 1) {
                    setPresent();
                } else {
                    throw INCONSISTENCY;
                }
            }

            @Override
            public void removeBelow(int v) {
                if (v >= 1) {
                    if (v == 1) {
                        setPresent();
                    } else {
                        throw INCONSISTENCY;
                    }
                }
            }

            @Override
            public void removeAbove(int v) {
                if (v <= 0) {
                    if (v == 0) {
                        setAbsent();
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
            public ModelProxy getModelProxy() {
                return cp.getModelProxy();
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
    public CPSolver getSolver() {
        return cp;
    }

    @Override
    public void propagateOnChange(CPConstraint c) {
        onChange.push(c);
    }

    protected void scheduleAll() {
        for (int i = 0; i < onChange.size(); i++) {
            cp.schedule(onChange.get(i));
        }
    }

    private CPConstraint constraintClosure(Runnable f) {
        CPConstraint c = new CPConstraintClosure(cp, f);
        getSolver().post(c, false);
        return c;
    }

    @Override
    public int startMin() {
        return start.min();
    }

    @Override
    public int startMax() {
        return start.max();
    }

    @Override
    public int endMin() {
        /*
        int trueEndMin = endMin.value();
        int badEndMin = startMin.value() + lengthMin.value();
        if (badEndMin != trueEndMin) {
            System.out.println("badEndMin: " + badEndMin + " != " + trueEndMin);
        }*/
        return end.min();
        //return endMin.value();
    }

    @Override
    public int endMax() {
        //int res = startMax.value() + lengthMax.value();
        //System.out.println(res);
        return end.max();
    }

    @Override
    public int lengthMin() {
        return length.min();
    }

    @Override
    public int lengthMax() {
        return length.max();
    }

    @Override
    public boolean isPresent() {
        return isPresent.value();
    }

    @Override
    public boolean isAbsent() {
        return isAbsent.value();
    }

    @Override
    public boolean isOptional() {
        return !isPresent() && !isAbsent();
    }

    @Override
    public boolean isFixed() {
        return isAbsent() || (isPresent() && (startMin() == startMax()) && (lengthMin() == lengthMax()));
    }

    @Override
    public void setStartMin(int v) {
        start.removeBelow(v);
        scheduleAll();
    }

    @Override
    public void setStartMax(int v) {
        start.removeAbove(v);
        scheduleAll();
    }

    @Override
    public void setStart(int v) {
        setStartMax(v);
        setStartMin(v);
    }

    @Override
    public void setEndMin(int v) {
        end.removeBelow(v);
        scheduleAll();
    }

    @Override
    public void setEndMax(int v) {
        end.removeAbove(v);
        scheduleAll();
    }

    @Override
    public void setEnd(int v) {
        setEndMax(v);
        setEndMin(v);
    }

    @Override
    public void setLengthMin(int v) {
        length.removeBelow(v);
        scheduleAll();
    }

    @Override
    public void setLengthMax(int v) {
        length.removeAbove(v);
        scheduleAll();
    }

    @Override
    public void setLength(int v) {
        setLengthMin(v);
        setLengthMax(v);
    }

    @Override
    public void setPresent() {
        if (isAbsent.value()) {
            throw INCONSISTENCY;
        }
        if (!isPresent.value()) {
            isPresent.setValue(true);
            isAbsent.setValue(false);
            scheduleAll();
        }
    }

    @Override
    public void setAbsent() {
        if (isPresent.value()) {
            throw INCONSISTENCY;
        }
        if (!isAbsent.value()) {
            isPresent.setValue(false);
            isAbsent.setValue(true);
            scheduleAll();
        }
    }

    public CPBoolVar status() {
        return b;
    }

    @Override
    public int slack() {
        return end.max()-start.min()- length.min();
    }

    @Override
    public String toString() {
        return show();
        //return "present ? "+isPresent+" absent ? "+isAbsent+" start ∈ ["+startMin+","+startMax+"],  length ∈ ["+lengthMin+","+lengthMax+"], end ∈ ["+endMin+","+endMax+"]";
    }

    @Override
    public ModelProxy getModelProxy() {
        return getSolver().getModelProxy();
    }
}
