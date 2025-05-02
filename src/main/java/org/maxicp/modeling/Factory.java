/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.algebra.bool.*;
import org.maxicp.modeling.algebra.integer.*;
import org.maxicp.modeling.algebra.scheduling.CumulFunction;
import org.maxicp.modeling.constraints.*;
import org.maxicp.modeling.constraints.scheduling.AlwaysIn;
import org.maxicp.modeling.constraints.scheduling.LessOrEqual;
import org.maxicp.modeling.constraints.seqvar.*;
import org.maxicp.modeling.symbolic.*;

import java.util.Optional;

public final class Factory {
    private Factory() {}

    // -------------- instantiation of the model -----------------------

    static public ModelDispatcher makeModelDispatcher() {
        return new ModelDispatcher();
    }

    // -------------- objectives -----------------------

    public static Objective minimize(IntExpression x, boolean shared) {
        if (shared) return new SharedMinimization(x);
        return new Minimization(x);
    }

    public static Objective minimize(IntExpression x) {
        return minimize(x, false);
    }

    public static Objective maximize(IntExpression x, boolean shared) {
        if (shared) return new SharedMaximization(x);
        return new Maximization(x);
    }

    public static Objective maximize(IntExpression x) {
        return maximize(x, false);
    }

    // -------------- constraints -----------------------

    // ********************
    // Arithmetic constraints (+, -, *, abs)
    // ********************

    public static IntExpression cst(ModelProxy modelProxy, int v) {
        return new Constant(modelProxy, v);
    }

    static public IntExpression plus(IntExpression a, int v) {
        return new Sum(a, new Constant(a.getModelProxy(), v));
    }

    static public IntExpression plus(IntExpression a, IntExpression b) {
        return new Sum(a, b);
    }

    static public IntExpression minus(IntExpression a, int v) {
        return new Sum(a, new Constant(a.getModelProxy(), -v));
    }

    static public IntExpression minus(IntExpression a, IntExpression b) {
        return new Sum(a, minus(b));
    }

    static public IntExpression minus(IntExpression a) {
        return new UnaryMinus(a);
    }

    static public IntExpression mul(IntExpression a, int b) {
        return new CstMul(a, b);
    }

    static public IntExpression mul(int b, IntExpression a) {
        return new CstMul(a, b);
    }

    static public IntExpression mul(IntExpression x, BoolExpression b) {
        return new MulBinary(x,b);
    }

    static public IntExpression mul(IntExpression... x) {
        return new Mul(x);
    }

    public static IntExpression abs(IntExpression x) {
        return new Abs(x);
    }

    // ********************
    // Arithmetic constraints (sum, min, max)
    // ********************

    static public IntExpression sum(IntExpression... x) {
        return new Sum(x);
    }

    public static IntExpression max(IntExpression... x) { return new Max(x); }

    public static IntExpression min(IntExpression... x) { return new Min(x); }

    // ********************
    // Comon constraints (=, !=, <=, <, >=, >)
    // ********************

    static public BoolExpression eq(IntExpression a, int b) {
        return new Eq(a, new Constant(a.getModelProxy(),b));
    }

    static public BoolExpression eq(int a, IntExpression b) {
        return new Eq(a, b);
    }

    static public BoolExpression eq(IntExpression a, IntExpression b) {
        return new Eq(a, b);
    }


    static public BoolExpression neq(IntExpression a, int b) {
        return new NotEq(a, new Constant(a.getModelProxy(), b));
    }

    static public BoolExpression neq(int a, IntExpression b) {
        return new NotEq(a, b);
    }

    static public BoolExpression neq(IntExpression a, IntExpression b) {
        return new NotEq(a, b);
    }


    static public BoolExpression le(IntExpression a, int b) {
        return new LessOrEq(a, new Constant(a.getModelProxy(),b));
    }

    static public BoolExpression le(int a, IntExpression b) {
        return new LessOrEq(a, b);
    }

    static public BoolExpression le(IntExpression a, IntExpression b) {
        return new LessOrEq(a, b);
    }


    static public BoolExpression lt(int a, IntExpression b) {
        return new LessOrEq(a, Factory.minus(b, 1));
    }

    static public BoolExpression lt(IntExpression a, int b) {
        return new LessOrEq(a, new Constant(a.getModelProxy(),b-1));
    }

    static public BoolExpression lt(IntExpression a, IntExpression b) {
        return new LessOrEq(a, Factory.minus(b, 1));
    }


    static public BoolExpression ge(IntExpression a, int b) {
        return new GreaterOrEq(a, new Constant(a.getModelProxy(),b));
    }

    static public BoolExpression ge(int a, IntExpression b) {
        return new GreaterOrEq(a, b);
    }

    static public BoolExpression ge(IntExpression a, IntExpression b) {
        return new GreaterOrEq(a, b);
    }


    static public BoolExpression gt(IntExpression a, int b) {
        return new GreaterOrEq(a, new Constant(a.getModelProxy(),b+1));
    }

    static public BoolExpression gt(int a, IntExpression b) {
        return new GreaterOrEq(a, Factory.plus(b, 1));
    }

    static public BoolExpression gt(IntExpression a, IntExpression b) {
        return new GreaterOrEq(a, Factory.plus(b, 1));
    }

    // ********************
    // Comparison tests constraints (reified version of: =, !=, >, >=, <, <=)
    // ********************

    // TODO b <-> comparison

    // ********************
    // Logical constraints (not, and, or, implies)
    // ********************

    public static BoolExpression not(BoolExpression a) {
        return new Not(a);
    }

    public static BoolExpression and(BoolExpression... t) {
        return new And(t);
    }

    public static BoolExpression or(BoolExpression... t) {
        return new Or(t);
    }

    /**
     * Returns the logical implication of a and b
     * @param a a boolean expression
     * @param b a boolean expression
     * @return a => b
     */
    public static BoolExpression implies(BoolExpression a, BoolExpression b) {
        return or(not(a), b);
    }


    public static BoolExpression endBeforeStart(IntervalVar a, IntervalVar b) {
        return new EndBeforeStart(a, b);
    }

    // ********************
    // Element constraints
    // ********************

    public static IntExpression get(int [] T, IntExpression y) {
        return new Element1D(T, y);
    }

    public static IntExpression get(IntExpression[] T, IntExpression y) {
        return new Element1DVar(T, y);
    }

    public static IntExpression get(int [][] T, IntVar x, IntVar y) {
        return new Element2D(T, x, y);
    }

    public static IntExpression get(IntExpression [][] T, IntVar x, IntVar y) {
        return new Element2DVar(T, x, y);
    }

    // ********************
    // AllDifferent constraints (and constraints regarding the number of different values)
    // ********************

    public static Constraint allDifferent(IntExpression... x) {
        return new AllDifferent(x);
    }

    public static Constraint binPacking(IntExpression [] x, int [] weights, IntExpression [] loads) {
        return new BinPacking(x, weights, loads);
    }

    // ********************
    // Circuit constraints
    // ********************

    public static Constraint circuit(IntExpression... x) {
        return new Circuit(x);
    }

    // ********************
    // Extensional constraints
    // ********************

    public static Constraint table(IntExpression[] x, int[][] array, Optional<Integer> starred) {
        return new Table(x, array, starred);
    }

    public static Constraint negTable(IntExpression[] x, int[][] array, Optional<Integer> starred) {
        return new NegTable(x, array, starred);
    }

    // ********************
    // Sequence constraints
    // ********************

    // TODO Sequence

    // ********************
    // Scheduling constraints
    // ********************

    public static Constraint cumulative(IntExpression[] start, int[] duration, int[] demand, int capa) {
        return new org.maxicp.modeling.constraints.Cumulative(start, duration, demand, capa);
    }

    public static Constraint disjunctive(IntExpression[] start, int[] duration) {
        return new Disjunctive(start, duration);
    }

    public static Constraint distance(SeqVar seqVar, int[][] distanceMatrix, IntExpression distance) {
        return new Distance(seqVar, distanceMatrix, distance);
    }

    public static Constraint require(SeqVar seqVar, int node) {
        return new Require(seqVar, node);
    }

    public static Constraint insert(SeqVar seqVar, int prev, int node) {
        return new Insert(seqVar, prev, node);
    }

    public static Constraint exclude(SeqVar seqVar, int node) {
        return new Exclude(seqVar, node);
    }

    public static Constraint notBetween(SeqVar seqVar, int pred, int node, int succ) {
        return new NotBetween(seqVar, pred, node, succ);
    }

    public static Constraint transitionTimes(SeqVar seqVar, IntExpression[] time, int[][] dist) {
        return new TransitionTimes(seqVar, time, dist);
    }

    public static Constraint transitionTimes(SeqVar seqVar, IntExpression[] time, int[][] dist, int[] serviceTime) {
        return new TransitionTimes(seqVar, time, dist, serviceTime);
    }

    public static Constraint cumulative(SeqVar seqVar, int[] starts, int[] ends, int[] load, int capacity) {
        return new org.maxicp.modeling.constraints.seqvar.Cumulative(seqVar, starts, ends, load, capacity);
    }

    public static Constraint noOverlap(IntervalVar... intervals) {
        return new org.maxicp.modeling.constraints.scheduling.NoOverlap(intervals);
    }

    public static Constraint alternative(IntervalVar real, IntervalVar... alternatives) {
        return new org.maxicp.modeling.constraints.scheduling.Alternative(real, new IntVarRangeImpl(real.getModelProxy(), 0, 1), alternatives);
    }

    public static Constraint alternative(IntervalVar real, IntExpression n, IntervalVar... alternatives) {
        return new org.maxicp.modeling.constraints.scheduling.Alternative(real, n, alternatives);
    }


    public static Constraint length(IntervalVar var, int length) {
        return new org.maxicp.modeling.constraints.scheduling.Length(var, length);
    }

    //public static Constraint present(IntervalVar var) {
    //    return new org.maxicp.modeling.constraints.scheduling.Present(var);
    //}

    public static BoolExpression present(IntervalVar var) {
        return new org.maxicp.modeling.algebra.bool.Present(var);
    }

    public static Constraint start(IntervalVar var, int start) {
        return new org.maxicp.modeling.constraints.scheduling.Start(var, start);
    }

    /*
    public static Constraint startAfter(IntervalVar var, int start) {
        return new org.maxicp.modeling.constraints.scheduling.StartAfter(var, start);
    }

     */

    public static IntExpression startOr(IntervalVar interval, int v) {
        return new org.maxicp.modeling.algebra.integer.IntervalStartOrValue(interval, v);
    }

    public static IntExpression start(IntervalVar interval) {
        return new org.maxicp.modeling.algebra.integer.IntervalStart(interval);
    }

    public static IntExpression endOr(IntervalVar interval, int v) {
        return new org.maxicp.modeling.algebra.integer.IntervalEndOrValue(interval, v);
    }

    public static IntExpression end(IntervalVar interval) {
        return new org.maxicp.modeling.algebra.integer.IntervalEnd(interval);
    }

    public static IntExpression length(IntervalVar interval) {
        return new org.maxicp.modeling.algebra.integer.IntervalLength(interval);
    }

    public static IntExpression lengthOr(IntervalVar interval, int v) {
        return new org.maxicp.modeling.algebra.integer.IntervalLengthOrValue(interval, v);
    }


    public static BoolExpression endBefore(IntervalVar intervalVar, IntExpression value) {
        return new org.maxicp.modeling.algebra.bool.EndBefore(intervalVar, value);
    }

    public static BoolExpression endAfter(IntervalVar intervalVar, IntExpression value) {
        return new EndAfter(intervalVar, value);
    }

    public static BoolExpression endAfter(IntervalVar intervalVar, int value) {
        return new EndAfter(intervalVar, cst(intervalVar.getModelProxy(), value));
    }

    public static BoolExpression startBefore(IntervalVar intervalVar, IntExpression value) {
        return new StartBefore(intervalVar, value);
    }

    public static BoolExpression startBefore(IntervalVar intervalVar, int value) {
        return new StartBefore(intervalVar, cst(intervalVar.getModelProxy(), value));
    }

    public static BoolExpression startAfter(IntervalVar intervalVar, IntExpression value) {
        return new StartAfter(intervalVar, value);
    }

    public static BoolExpression startAfter(IntervalVar intervalVar, int value) {
        return new StartAfter(intervalVar, cst(intervalVar.getModelProxy(), value));
    }

    public static CumulFunction flat() {
        return new org.maxicp.modeling.algebra.scheduling.FlatCumulFunction();
    }

    public static CumulFunction pulse(IntervalVar interval, int height) {
        return new org.maxicp.modeling.algebra.scheduling.PulseCumulFunction(interval, height);
    }

    public static CumulFunction pulse(IntervalVar interval, int hMin, int hMax) {
        return new org.maxicp.modeling.algebra.scheduling.PulseCumulFunction(interval, hMin, hMax);
    }

    public static CumulFunction sum(CumulFunction ... functions) {
        return new org.maxicp.modeling.algebra.scheduling.SumCumulFunction(functions);
    }

    public static CumulFunction minus(CumulFunction left, CumulFunction right) {
        return new org.maxicp.modeling.algebra.scheduling.MinusCumulFunction(left, right);
    }

    public static CumulFunction stepAtEnd(IntervalVar interval, int hMin, int hMax) {
        return new org.maxicp.modeling.algebra.scheduling.StepAtEndCumulFunction(interval, hMin, hMax);
    }

    public static CumulFunction stepAtEnd(IntervalVar interval, int height) {
        return stepAtEnd(interval, height, height);
    }

    public static CumulFunction stepAtStart(IntervalVar interval, int hMin, int hMax) {
        return new org.maxicp.modeling.algebra.scheduling.StepAtStartCumulFunction(interval, hMin, hMax);
    }

    public static CumulFunction stepAtStart(IntervalVar interval, int height) {
        return stepAtStart(interval, height, height);
    }

    public static Constraint le(CumulFunction function, int limit) {
        return new LessOrEqual(function, limit);
    }

    public static Constraint alwaysIn(CumulFunction expression, int heightMin, int heightMax) {
        return new AlwaysIn(expression, heightMin, heightMax);
    }
}
