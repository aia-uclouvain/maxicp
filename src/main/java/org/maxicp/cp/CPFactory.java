/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp;

import org.maxicp.Constants;
import org.maxicp.cp.engine.constraints.*;
import org.maxicp.cp.engine.constraints.scheduling.*;
import org.maxicp.cp.engine.constraints.seqvar.Exclude;
import org.maxicp.cp.engine.constraints.seqvar.Insert;
import org.maxicp.cp.engine.constraints.seqvar.RemoveDetour;
import org.maxicp.cp.engine.constraints.seqvar.Require;
import org.maxicp.cp.engine.constraints.setvar.IsIncluded;
import org.maxicp.cp.engine.constraints.seqvar.*;
import org.maxicp.cp.engine.core.*;
import org.maxicp.cp.engine.constraints.scheduling.Activity;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.state.copy.Copier;
import org.maxicp.state.trail.Trailer;
import org.maxicp.util.NumberUtils;
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.util.exception.IntOverFlowException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Factory to create {@link CPSolver}, {@link CPIntVar}, {@link CPConstraint}, {@link CPSeqVar}
 * and some modeling utility methods.
 * <p>
 * CPFactory may need to post new constraints to the solver when creating some variables. If it needs to do so,
 * it *nevers* run the fixpoint.
 * <p>
 * Example for the n-queens problem:
 * <pre>
 * {@code
 *  Solver cp = Factory.makeSolver(false);
 *  IntVar[] q = Factory.makeIntVarArray(cp, n, n);
 *  for (int i = 0; i < n; i++)
 *    for (int j = i + 1; j < n; j++) {
 *      cp.post(Factory.neq(q[i], q[j]));
 *      cp.post(Factory.neqs(q[i], q[j], j - i));
 *      cp.post(Factory.neq(q[i], q[j], i - j));
 *    }
 *  search.onSolution(() ->
 *    System.out.println("solution:" + Arrays.toString(q))
 *  );
 *  DFSearch search = Factory.makeDfs(cp,firstFail(q));
 *  SearchStatistics stats = search.solve();
 * }
 * </pre>
 */
public final class CPFactory {

    private CPFactory() {
        throw new UnsupportedOperationException();
    }

    // -------------- instantiation of the solver -----------------------

    /**
     * Creates a constraint programming solver
     *
     * @return a constraint programming solver with trail-based memory management
     */
    public static CPSolver makeSolver() {
        return new MaxiCP(new Trailer());
    }

    /**
     * Creates a constraint programming solver
     *
     * @param byCopy a value that should be true to specify
     *               copy-based state management
     *               or falso for a trail-based memory management
     * @return a constraint programming solver
     */
    public static CPSolver makeSolver(boolean byCopy) {
        return new MaxiCP(byCopy ? new Copier() : new Trailer());
    }

    // -------------- variables creation -----------------------

    // ********************
    // Integer variables
    // ********************

    /**
     * Creates a variable with a domain of specified arity.
     *
     * @param cp the solver in which the variable is created
     * @param sz a positive value that is the size of the domain
     * @return a variable with domain equal to the set {0,...,sz-1}
     */
    public static CPIntVar makeIntVar(CPSolver cp, int sz) {
        return makeIntVar(cp, 0, sz - 1);
    }

    /**
     * Creates a variable with a domain equal to the specified range.
     *
     * @param cp  the solver in which the variable is created
     * @param min the lower bound of the domain (included)
     * @param max the upper bound of the domain (included) {@code max > min}
     * @return a variable with domain equal to the set {min,...,max}
     */
    public static CPIntVar makeIntVar(CPSolver cp, int min, int max) {
        if (min == max)
            return new CPIntVarConstant(cp, min);
        else
            return new CPIntVarImpl(cp, min, max);
    }

    /**
     * Creates a variable with a domain equal to the specified set of values.
     *
     * @param cp     the solver in which the variable is created
     * @param values a set of values
     * @return a variable with domain equal to the set of values
     */
    public static CPIntVar makeIntVar(CPSolver cp, Set<Integer> values) {
        if (values.size() == 1) {
            int value = values.iterator().next();
            return new CPIntVarConstant(cp, value);
        } else
            return new CPIntVarImpl(cp, values);
    }

    /**
     * Creates an array of variables with specified lambda function
     *
     * @param n    the number of variables to create
     * @param body the function that given the index i in the array creates/map the corresponding {@link CPIntVar}
     * @return an array of n variables
     * with variable at index <i>i</i> generated as {@code body.get(i)}
     */
    public static CPIntVar[] makeIntVarArray(int n, Function<Integer, CPIntVar> body) {
        CPIntVar[] t = new CPIntVar[n];
        for (int i = 0; i < n; i++)
            t[i] = body.apply(i);
        return t;
    }

    /**
     * Creates an array of variables with specified domain size.
     *
     * @param cp the solver in which the variables are created
     * @param n  the number of variables to create
     * @param sz a positive value that is the size of the domain
     * @return an array of n variables, each with domain equal to the set {0,...,sz-1}
     */
    public static CPIntVar[] makeIntVarArray(CPSolver cp, int n, int sz) {
        return makeIntVarArray(n, i -> makeIntVar(cp, sz));
    }

    /**
     * Creates an array of variables with specified domain bounds.
     *
     * @param cp  the solver in which the variables are created
     * @param n   the number of variables to create
     * @param min the lower bound of the domain (included)
     * @param max the upper bound of the domain (included) {@code max > min}
     * @return an array of n variables each with a domain equal to the set {min,...,max}
     */
    public static CPIntVar[] makeIntVarArray(CPSolver cp, int n, int min, int max) {
        return makeIntVarArray(n, i -> makeIntVar(cp, min, max));
    }

    // ********************
    // Boolean variables
    // ********************

    /**
     * Creates a boolean variable.
     *
     * @param cp the solver in which the variable is created
     * @return an uninstantiated boolean variable
     */
    public static CPBoolVar makeBoolVar(CPSolver cp) {
        return new CPBoolVarImpl(cp);
    }

    /**
     * Creates a boolean variable.
     *
     * @param cp            the solver in which the variable is created
     * @param containsFalse whether the value false is contained within the domain
     * @param containsTrue  whether the value true is contained within the domain
     * @return an uninstantiated boolean variable
     */
    public static CPBoolVar makeBoolVar(CPSolver cp, boolean containsTrue, boolean containsFalse) {
        CPBoolVarImpl boolVar = new CPBoolVarImpl(cp);
        if (!containsFalse)
            boolVar.fix(true);
        if (!containsTrue)
            boolVar.fix(false);
        return boolVar;
    }

    /**
     * Creates an array of variables with specified lambda function
     *
     * @param n    the number of variables to create
     * @param body the function that given the index i in the array creates/map the corresponding {@link CPBoolVar}
     * @return an array of n variables
     * with variable at index <i>i</i> generated as {@code body.get(i)}
     */
    public static CPBoolVar[] makeBoolVarArray(int n, Function<Integer, CPBoolVar> body) {
        CPBoolVar[] t = new CPBoolVar[n];
        for (int i = 0; i < n; i++)
            t[i] = body.apply(i);
        return t;
    }

    /**
     * Creates an array of boolean variables with specified domain size.
     *
     * @param cp the solver in which the variables are created
     * @param n  the number of variables to create
     * @return an array of n boolean variables
     */
    public static CPBoolVar[] makeBoolVarArray(CPSolver cp, int n) {
        return makeBoolVarArray(n, i -> makeBoolVar(cp));
    }

    // ********************
    // Sequence variables
    // ********************

    /**
     * Creates a sequence variable.
     *
     * @param cp the solver in which the variable is created
     * @return an uninstantiated boolean variable
     */
    public static CPSeqVar makeSeqVar(CPSolver cp, int nNodes, int start, int end) {
        return new CPSeqVarImpl(cp, nNodes, start, end);
    }

    // ********************
    // Interval variables
    // ********************

    /**
     * Creates a new optional interval variable with a startMin of 0, an unbounded end and unfixed length
     *
     * @param cp the solver
     * @return a new interval variable
     */
    public static CPIntervalVar makeIntervalVar(CPSolver cp) {
        return new CPIntervalVarImpl(cp);
    }

    /**
     * Creates a new optional interval variable with a startMin of 0, an unbounded end and fixed length
     *
     * @param cp     the solver
     * @param length the length of the interval variable
     * @return a new interval variable
     */
    public static CPIntervalVar makeIntervalVar(CPSolver cp, int length) {
        CPIntervalVar var = makeIntervalVar(cp);
        var.setLength(length);
        return var;
    }

    /**
     * Creates a new interval optional or mandatory variable with a startMin of 0,
     * an unbounded end and fixed length
     *
     * @param cp       the solver
     * @param optional whether the interval variable is optional (true), or mandatory (false)
     * @param length   the length of the interval variable
     * @return a new interval variable
     */
    public static CPIntervalVar makeIntervalVar(CPSolver cp, boolean optional, int length) {
        CPIntervalVar var = makeIntervalVar(cp, length);
        if (!optional) var.setPresent();
        return var;
    }

    /**
     * Creates a new optional interval variable with a startMin of 0, a length between lengthMin and lengthMax
     *
     * @param cp        the solver
     * @param lengthMin the length of the interval variable
     * @param lengthMax the length of the interval variable
     * @return a new interval variable
     */
    public static CPIntervalVar makeIntervalVar(CPSolver cp, int lengthMin, int lengthMax) {
        CPIntervalVar var = makeIntervalVar(cp);
        var.setLengthMin(lengthMin);
        var.setLengthMax(lengthMax);
        return var;
    }

    /**
     * Creates a new interval variable with a startMin of 0, a length between lengthMin and lengthMax
     *
     * @param cp        the solver
     * @param optional  whether the interval variable is optional
     * @param lengthMin the length of the interval variable
     * @param lengthMax the length of the interval variable
     * @return a new interval variable
     */
    public static CPIntervalVar makeIntervalVar(CPSolver cp, boolean optional, int lengthMin, int lengthMax) {
        CPIntervalVar var = makeIntervalVar(cp, lengthMin, lengthMax);
        if (!optional) var.setPresent();
        return var;
    }

    /**
     * Creates an array of new interval variable with a startMin of 0, an unbounded end and unfixed length
     *
     * @param cp the solver
     * @param n  the number of interval variables
     * @return an array of new interval variables each with a startMin of 0 and an unbounded end
     */
    public static CPIntervalVar[] makeIntervalVarArray(CPSolver cp, int n) {
        CPIntervalVar[] vars = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            vars[i] = new CPIntervalVarImpl(cp);
        }
        return vars;
    }

    // -------------- searches -----------------------

    /**
     * Creates a Depth First Search with custom branching heuristic
     * <pre>
     * // Example of binary search: At each node it selects
     * // the first free variable qi from the array q,
     * // and creates two branches qi=v, qi!=v where v is the min value domain
     * {@code
     * DFSearch search = Factory.makeDfs(cp, () -> {
     *     IntVar qi = Arrays.stream(q).reduce(null, (a, b) -> b.size() > 1 && a == null ? b : a);
     *     if (qi == null) {
     *        return return EMPTY;
     *     } else {
     *        int v = qi.min();
     *        Procedure left = () -> eq(qi, v); // left branch
     *        Procedure right = () -> neq(qi, v); // right branch
     *        return branch(left, right);
     *     }
     * });
     * }
     * </pre>
     *
     * @param cp        the solver that will be used for the search
     * @param branching a generator that is called at each node of the depth first search
     *                  tree to generate an array of {@link Runnable} objects
     *                  that will be used to commit to child nodes.
     *                  It should return {@link org.maxicp.search.Searches#EMPTY} whenever the current state
     *                  is a solution.
     * @return the depth first search object ready to execute with
     * {@link DFSearch#solve()} or
     * {@link DFSearch#optimize(Objective)}
     * using the given branching scheme
     * @see org.maxicp.search.Searches#firstFail(org.maxicp.modeling.algebra.integer.IntExpression...)
     * @see org.maxicp.search.Searches#branch(Runnable...)
     */
    public static DFSearch makeDfs(CPSolver cp, Supplier<Runnable[]> branching) {
        return new DFSearch(cp.getStateManager(), branching);
    }

    // -------------- constraints -----------------------

    // ********************
    // Arithmetic views (+, -, *, abs)
    // ********************

    /**
     * A variable that is a view of {@code x+v}.
     *
     * @param x a variable
     * @param v a value
     * @return a variable that is a view of {@code x+v}
     */
    public static CPIntVar plus(CPIntVar x, int v) {
        return new CPIntVarViewOffset(x, v);
    }

    /**
     * A variable that is a view of {@code -x}.
     *
     * @param x a variable
     * @return a variable that is a view of {@code -x}
     */
    public static CPIntVar minus(CPIntVar x) {
        return new CPIntVarViewOpposite(x);
    }

    /**
     * A variable that is a view of {@code x-v}.
     *
     * @param x a variable
     * @param v a value
     * @return a variable that is a view of {@code x-v}
     */
    public static CPIntVar minus(CPIntVar x, int v) {
        return new CPIntVarViewOffset(x, -v);
    }

    /**
     * A variable that is a view of {@code x*a}.
     *
     * @param x a variable
     * @param a a constant to multiply x with
     * @return a variable that is a view of {@code x*a}
     */
    public static CPIntVar mul(CPIntVar x, int a) {
        if (a == 0) return makeIntVar(x.getSolver(), 0, 0);
        else if (a == 1) return x;
        else if (a < 0) {
            return minus(new CPIntVarViewMul(x, -a));
        } else {
            return new CPIntVarViewMul(x, a);
        }
    }

    /**
     * A variable that is {@code x*b}.
     *
     * @param x a variable
     * @param b a boolvar to multiply x with
     * @return a variable that is equal to {@code x*b}
     */
    public static CPIntVar mul(CPIntVar x, CPBoolVar b) {
        if (b.isTrue()) return x;
        CPIntVar y = makeIntVar(x.getSolver(), Math.min(0, x.min()), Math.max(0, x.max()));
        x.getSolver().post(new Mul(x, b, y));
        return y;
    }

    /**
     * Computes a variable that is the absolute value of the given variable.
     * This relation is enforced by the {@link Absolute} constraint
     * posted by calling this method.
     *
     * @param x a variable
     * @return a variable that represents the absolute value of x
     */
    public static CPIntVar abs(CPIntVar x) {
        CPIntVar r = makeIntVar(x.getSolver(), 0, Math.max(Math.abs(x.max()), Math.abs(x.min())));
        x.getSolver().post(new Absolute(x, r), false);
        return r;
    }

    // ********************
    // Arithmetic constraints (sum, min, max)
    // ********************

    /**
     * Returns a variable representing
     * the sum of a given set of variables.
     * This relation is enforced by the {@link Sum} constraint
     * posted by calling this method.
     *
     * @param x the n variables to sum
     * @return a variable equal to {@code x[0]+x[1]+...+x[n-1]}
     */
    public static CPIntVar sum(CPIntVar... x) {
        if (x.length == 1)
            return x[0];
        long sumMin = 0;
        long sumMax = 0;
        for (int i = 0; i < x.length; i++) {
            sumMin += x[i].min();
            sumMax += x[i].max();
        }
        if (sumMin < (long) Integer.MIN_VALUE || sumMax > (long) Integer.MAX_VALUE) {
            throw new IntOverFlowException("domains are too large for sum constraint and would exceed Integer bounds");
        }
        CPSolver cp = x[0].getSolver();
        CPIntVar s = makeIntVar(cp, (int) sumMin, (int) sumMax);
        cp.post(new Sum(x, s), false);
        return s;
    }

    /**
     * Returns a sum constraint.
     *
     * @param x an array of variables
     * @param y a variable
     * @return a constraint so that {@code y = x[0]+x[1]+...+x[n-1]}
     */
    public static CPConstraint sum(CPIntVar[] x, CPIntVar y) {
        return new Sum(x, y);
    }

    /**
     * Returns a sum constraint.
     *
     * @param x an array of variables
     * @param y a constant
     * @return a constraint so that {@code y = x[0]+x[1]+...+x[n-1]}
     */
    public static CPConstraint sum(CPIntVar[] x, int y) {
        return new Sum(x, y);
    }

    /**
     * Returns a sum constraint.
     * <p>
     * Uses a _parameter pack_ to automatically bundle a list of IntVar as an array
     *
     * @param y the target value for the sum (a constant)
     * @param x array of variables
     * @return a constraint so that {@code y = x[0] + ... + x[n-1]}
     */
    public static CPConstraint sum(int y, CPIntVar... x) {
        return new Sum(x, y);
    }

    /**
     * Returns a variable representing
     * the multiplication of a given set of variables.
     * This relation is enforced by the {@link Mul} binary constraint
     * posted by calling this method.
     *
     * @param x the n variables to multiply
     * @return a variable equal to {@code x[0]*x[1]*...*x[n-1]}
     */
    public static CPIntVar mul(CPIntVar... x) {
        // reduce using mult
        if (x.length == 1)
            return x[0];
        CPIntVar res = x[0];
        for (int i = 1; i < x.length; i++) {
            res = mul(res, x[i]);
        }
        return res;
    }

    /**
     * @param x a variable in the same store as y
     * @param y a variable in the same store as x
     * @return a variable in the same store representing: x * y
     */
    public static CPIntVar mul(CPIntVar x, CPIntVar y) {
        int a = x.min();
        int b = x.max();
        int c = y.min();
        int d = y.max();
        int [] t = new int[] {NumberUtils.safeMul(a, c), NumberUtils.safeMul(a, d), NumberUtils.safeMul(b, c), NumberUtils.safeMul(b, d)};
        int min = Arrays.stream(t).min().getAsInt();
        int max = Arrays.stream(t).max().getAsInt();
        CPIntVar z = makeIntVar(x.getSolver(),min, max);
        x.getSolver().post(new MulVar(x, y, z));
        return z;
    }

    /**
     * Computes a variable that is the maximum of a set of variables.
     * This relation is enforced by the {@link Maximum} constraint
     * posted by calling this method.
     *
     * @param x the variables on which to compute the maximum
     * @return a variable that represents the maximum on x
     * @see CPFactory#minimum(CPIntVar...)
     */
    public static CPIntVar max(CPIntVar... x) {
        CPSolver cp = x[0].getSolver();
        int min = Arrays.stream(x).mapToInt(CPIntVar::min).min().getAsInt();
        int max = Arrays.stream(x).mapToInt(CPIntVar::max).max().getAsInt();
        CPIntVar y = makeIntVar(cp, min, max);
        cp.post(new Maximum(x, y), false);
        return y;
    }

    /**
     * Computes a variable that is the minimum of a set of variables.
     * This relation is enforced by the {@link Maximum} constraint
     * posted by calling this method.
     *
     * @param x the variables on which to compute the minimum
     * @return a variable that represents the minimum on x
     * @see CPFactory#max(CPIntVar...) (IntVar...)
     */
    public static CPIntVar minimum(CPIntVar... x) {
        CPIntVar[] minusX = Arrays.stream(x).map(CPFactory::minus).toArray(CPIntVar[]::new);
        return minus(max(minusX));
    }

    // ********************
    // Comparison constraints (=, !=, <=, <, >=, >)
    // ********************

    /**
     * Returns a constraint imposing that the variable is
     * equal to some given value.
     *
     * @param x the variable to be assigned to v
     * @param v the value that must be assigned to x
     * @return a constraint so that {@code x = v}
     */
    public static CPConstraint eq(CPIntVar x, int v) {
        return new AbstractCPConstraint(x.getSolver()) {
            @Override
            public void post() {
                x.fix(v);
            }
        };
    }

    /**
     * Returns a constraint imposing that the two different variables
     * must take the value.
     *
     * @param x a variable
     * @param y a variable
     * @return a constraint so that {@code x = y}
     */
    public static CPConstraint eq(CPIntVar x, CPIntVar y) {
        return new Equal(x, y);
    }

    /**
     * Returns a constraint imposing that the variable is different
     * from some given value.
     *
     * @param x the variable that is constrained bo be different from v
     * @param v the value that must be different from x
     * @return a constraint so that {@code x != y}
     */
    public static CPConstraint neq(CPIntVar x, int v) {
        return new AbstractCPConstraint(x.getSolver()) {
            @Override
            public void post() {
                x.remove(v);
            }
        };
    }

    /**
     * Returns a constraint imposing that the two different variables
     * must take different values.
     *
     * @param x a variable
     * @param y a variable
     * @return a constraint so that {@code x != y}
     */
    public static CPConstraint neq(CPIntVar x, CPIntVar y) {
        return new NotEqual(x, y);
    }

    /**
     * Returns a constraint imposing that the
     * the first variable differs from the second
     * one minus a constant value.
     *
     * @param x a variable
     * @param y a variable
     * @param c a constant
     * @return a constraint so that {@code x != y+c}
     */
    public static CPConstraint neq(CPIntVar x, CPIntVar y, int c) {
        return new NotEqual(x, y, c);
    }

    /**
     * Returns a constraint imposing that the variable is less or
     * equal to some given value.
     *
     * @param x the variable that is constrained bo be less or equal to v
     * @param v the value that must be the upper bound on x
     * @return a constraint so that {@code x <= v}
     */
    public static CPConstraint le(CPIntVar x, int v) {
        return new AbstractCPConstraint(x.getSolver()) {
            @Override
            public void post() {
                x.removeAbove(v);
            }
        };
    }

    /**
     * Returns a constraint imposing that the
     * first variable is less or equal to a second one.
     *
     * @param x a variable
     * @param y a variable
     * @return a constraint so that {@code x <= y}
     */
    public static CPConstraint le(CPIntVar x, CPIntVar y) {
        return new LessOrEqual(x, y);
    }

    /**
     * Returns a constraint imposing that the variable is less
     * to some given value.
     *
     * @param x the variable that is constrained bo be less to v
     * @param v the value that must be the upper bound on x
     * @return a constraint so that {@code x < v}
     */
    public static CPConstraint lt(CPIntVar x, int v) {
        return le(x, v - 1);
    }

    /**
     * Returns a constraint imposing that the
     * first variable is less to a second one.
     *
     * @param x a variable
     * @param y a variable
     * @return a constraint so that {@code x < y}
     */
    public static CPConstraint lt(CPIntVar x, CPIntVar y) {
        return le(x, minus(y, 1));
    }

    /**
     * Returns a constraint imposing that the variable is larger or
     * equal to some given value.
     *
     * @param x the variable that is constrained bo be larger or equal to v
     * @param v the value that must be the lower bound on x
     * @return a constraint so that {@code x >= v}
     */
    public static CPConstraint ge(CPIntVar x, int v) {
        return new AbstractCPConstraint(x.getSolver()) {
            @Override
            public void post() {
                x.removeBelow(v);
            }
        };
    }

    /**
     * Returns a constraint imposing that the
     * a first variable is larger or equal to a second one.
     *
     * @param x a variable
     * @param y a variable
     * @return a constraint so that {@code x >= y}
     */
    public static CPConstraint ge(CPIntVar x, CPIntVar y) {
        return le(y, x);
    }

    /**
     * Returns a constraint imposing that the variable is larger
     * to some given value.
     *
     * @param x the variable that is constrained bo be larger to v
     * @param v the value that must be the lower bound on x
     * @return a constraint so that {@code x > v}
     */
    public static CPConstraint gt(CPIntVar x, int v) {
        return ge(x, v + 1);
    }

    /**
     * Returns a constraint imposing that the
     * a first variable is larger to a second one.
     *
     * @param x a variable
     * @param y a variable
     * @return a constraint so that {@code x > y}
     */
    public static CPConstraint gt(CPIntVar x, CPIntVar y) {
        return ge(x, plus(y, 1));
    }

    // ********************
    // Comparison tests constraints (reified version of: =, !=, >, >=, <, <=)
    // ********************

    /**
     * Returns a boolean variable representing
     * whether one variable is equal to the given constant.
     * This relation is enforced by the {@link IsEqual} constraint
     * posted by calling this method.
     *
     * @param x the variable
     * @param c the constant
     * @return a boolean variable that is true if and only if x takes the value c
     * @see IsEqual
     */
    public static CPBoolVar isEq(CPIntVar x, final int c) {
        CPSolver cp = x.getSolver();
        CPBoolVar b = makeBoolVar(cp);
        cp.post(new IsEqual(b, x, c), false);
        return b;
    }

    /**
     * Returns a boolean variable representing
     * whether two variables are equal
     * This relation is enforced by the {@link IsEqualVar} constraint
     * posted by calling this method.
     *
     * @param x first variable
     * @param y second variable
     * @return boolean variable that is set to true if and only if x == y
     * @see IsEqualVar
     */
    public static CPBoolVar isEq(CPIntVar x, CPIntVar y) {
        CPSolver cp = x.getSolver();
        CPBoolVar b = makeBoolVar(cp);
        cp.post(new IsEqualVar(b, x, y), false);
        return b;
    }

    /**
     * Returns a boolean variable representing
     * whether one variable is not equal to the given constant.
     *
     * @param x the variable
     * @param c the constant
     * @return a boolean variable that is true if and only if x don't take the value c
     */
    public static CPBoolVar isNeq(CPIntVar x, final int c) {
        return not(isEq(x, c));
    }

    /**
     * Returns a boolean variable representing
     * whether two variables are not equal
     *
     * @param x first variable
     * @param y second variable
     * @return boolean variable that is set to true if and only if x != y
     */
    public static CPBoolVar isNeq(CPIntVar x, CPIntVar y) {
        return not(isEq(x, y));
    }

    /**
     * Returns a boolean variable representing
     * whether one variable is less or equal to the given constant.
     * This relation is enforced by the {@link IsLessOrEqual} constraint
     * posted by calling this method.
     *
     * @param x the variable
     * @param c the constant
     * @return a boolean variable that is true if and only if
     * x takes a value less or equal to c
     */
    public static CPBoolVar isLe(CPIntVar x, final int c) {
        CPSolver cp = x.getSolver();
        CPBoolVar b = makeBoolVar(cp);
        cp.post(new IsLessOrEqual(b, x, c), false);
        return b;
    }

    /**
     * Returns a boolean variable representing
     * whether one variable is less or equal to another.
     * This relation is enforced by the {@link IsLessOrEqualVar} constraint
     * posted by calling this method.
     *
     * @param x left hand side of less or equal operator
     * @param y right hand side of less or equal operator
     * @return boolean variable value that will be set to true if
     * {@code x <= y}, false otherwise
     */
    public static CPBoolVar isLe(CPIntVar x, CPIntVar y) {
        CPSolver cp = x.getSolver();
        CPBoolVar b = makeBoolVar(cp);
        cp.post(new IsLessOrEqualVar(b, x, y), false);
        return b;
    }

    /**
     * Returns a boolean variable representing
     * whether one variable is less than the given constant.
     * This relation is enforced by the {@link IsLessOrEqual} constraint
     * posted by calling this method.
     *
     * @param x the variable
     * @param c the constant
     * @return a boolean variable that is true if and only if
     * x takes a value less than c
     */
    public static CPBoolVar isLt(CPIntVar x, final int c) {
        return isLe(x, c - 1);
    }

    /**
     * Returns a boolean variable representing
     * whether one variable is less than the given constant.
     * This relation is enforced by the {@link IsLessOrEqual} constraint
     * posted by calling this method.
     *
     * @param x the variable
     * @param y second variable
     * @return a boolean variable that is true if and only if
     * x takes a value less than y
     */
    public static CPBoolVar isLt(CPIntVar x, CPIntVar y) {
        return isLe(x, minus(y, 1));
    }

    /**
     * Returns a boolean variable representing
     * whether one variable is larger or equal to the given constant.
     * This relation is enforced by the {@link IsLessOrEqual} constraint
     * posted by calling this method.
     *
     * @param x the variable
     * @param c the constant
     * @return a boolean variable that is true if and only if
     * x takes a value larger or equal to c
     */
    public static CPBoolVar isGe(CPIntVar x, final int c) {
        return isLe(minus(x), -c);
    }

    /**
     * Returns a boolean variable representing
     * whether one variable is larger or equal to another.
     * This relation is enforced by the {@link IsLessOrEqualVar} constraint
     * posted by calling this method.
     *
     * @param x left hand side of less or equal operator
     * @param y right hand side of less or equal operator
     * @return boolean variable value that will be set to true if
     * {@code x >= y}, false otherwise
     */
    public static CPBoolVar isGe(CPIntVar x, CPIntVar y) {
        return isLe(y, x);
    }

    /**
     * Returns a boolean variable representing
     * whether one variable is larger than the given constant.
     * This relation is enforced by the {@link IsLessOrEqual} constraint
     * posted by calling this method.
     *
     * @param x the variable
     * @param c the constant
     * @return a boolean variable that is true if and only if
     * x takes a value larger than c
     */
    public static CPBoolVar isGt(CPIntVar x, final int c) {
        return isGe(x, c + 1);
    }

    /**
     * Returns a boolean variable representing
     * whether one variable is larger than the given constant.
     * This relation is enforced by the {@link IsLessOrEqual} constraint
     * posted by calling this method.
     *
     * @param x the variable
     * @param y second variable
     * @return a boolean variable that is true if and only if
     * x takes a value larger than y
     */
    public static CPBoolVar isGt(CPIntVar x, CPIntVar y) {
        return isGe(x, plus(y, 1));
    }

    // ********************
    // Logical constraints (not, or, implies)
    // ********************

    /**
     * A boolean variable that is a view of {@code !b}.
     *
     * @param b a boolean variable
     * @return a boolean variable that is a view of {@code !b}
     */
    public static CPBoolVar not(CPBoolVar b) {
        return new CPBoolVarImpl(plus(minus(b), 1));
    }

    /**
     * Returns an or constraint.
     * <p>
     * Uses a _parameter pack_ to automatically bundle a list of IntVar as an array
     *
     * @param x array of variables
     * @return a constraint so that {@code x[0] or ... or x[n-1]}
     */
    public static CPConstraint or(CPBoolVar... x) {
        return new Or(x);
    }

    /**
     * Model the logical implication constraint
     *
     * @param b1 left-hand side of the implication
     * @param b2 right-hand side of the implication
     * @return a constraint enforcing "b1 implies b2".
     */
    public static CPConstraint implies(CPBoolVar b1, CPBoolVar b2) {
        CPIntVar notB1 = CPFactory.plus(CPFactory.minus(b1), 1);
        return CPFactory.ge(CPFactory.sum(notB1, b2), 1);
    }

    // TODO implies b -> cst (as in choco!)

    // ********************
    // Logical tests constraints (reified version of: or)
    // ********************

    /**
     * Returns a variable that is true if at least one variable in x is true, false otherwise.
     *
     * @param x an array of variables
     */
    public static CPBoolVar isOr(CPBoolVar... x) {
        CPBoolVar b = makeBoolVar(x[0].getSolver());
        CPSolver cp = x[0].getSolver();
        cp.post(new IsOr(b, x), false);
        return b;
    }

    // ********************
    // Element constraints
    // ********************

    /**
     * Returns a variable representing
     * the value in an array at the position
     * specified by the given index variable
     * This relation is enforced by the {@link Element1D} constraint
     * posted by calling this method.
     *
     * @param array the array of values
     * @param y     the variable
     * @return a variable equal to {@code array[y]}
     */
    public static CPIntVar element(int[] array, CPIntVar y) {
        CPSolver cp = y.getSolver();
        CPIntVar z = makeIntVar(cp, IntStream.of(array).min().getAsInt(), IntStream.of(array).max().getAsInt());
        cp.post(new Element1D(array, y, z), false);
        return z;
    }

    /**
     * Returns a variable representing
     * the value in an array at the position
     * specified by the given index variable
     * This relation is enforced by the {@link Element1D} constraint
     * posted by calling this method.
     * This constraint is Domain consistent.
     *
     * @param array the array of values
     * @param y     the variable
     * @return a variable equal to {@code array[y]}
     */
    public static CPIntVar elementDC(int[] array, CPIntVar y) {
        CPSolver cp = y.getSolver();
        CPIntVar z = makeIntVar(cp, IntStream.of(array).min().getAsInt(), IntStream.of(array).max().getAsInt());
        cp.post(new Element1D(array, y, z), false);
        return z;
    }

    /**
     * Returns a variable representing
     * the value in an array at the position
     * specified by the given index variable
     * This relation is enforced by the {@link Element1D} constraint
     * posted by calling this method.
     *
     * @param array the array of values
     * @param y     the variable
     * @return a variable equal to {@code array[y]}
     */
    public static CPIntVar element(CPIntVar[] array, CPIntVar y) {
        CPSolver cp = y.getSolver();
        CPIntVar z = makeIntVar(cp,
                Arrays.stream(array).mapToInt(CPIntVar::min).min().getAsInt(),
                Arrays.stream(array).mapToInt(CPIntVar::max).max().getAsInt());
        cp.post(new Element1DVar(array, y, z), false);
        return z;
    }

    /**
     * Returns a variable representing
     * the value in a matrix at the position
     * specified by the two given row and column index variables
     * This relation is enforced by the {@link Element2D} constraint
     * posted by calling this method.
     *
     * @param matrix the n x m 2D array of values
     * @param x      the row variable with domain included in 0..n-1
     * @param y      the column variable with domain included in 0..m-1
     * @return a variable equal to {@code matrix[x][y]}
     */
    public static CPIntVar element(int[][] matrix, CPIntVar x, CPIntVar y) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                min = Math.min(min, matrix[i][j]);
                max = Math.max(max, matrix[i][j]);
            }
        }
        CPIntVar z = makeIntVar(x.getSolver(), min, max);
        x.getSolver().post(new Element2D(matrix, x, y, z), false);
        return z;
    }

    // ********************
    // AllDifferent constraints (and constraints regarding the number of different values)
    // ********************

    /**
     * Returns an allDifferent constraint using forward checking algo
     *
     * @param x an array of variables
     * @return a constraint so that {@code x[i] != x[j] for all i < j}
     */
    public static CPConstraint allDifferent(CPIntVar[] x) {
        return new AllDifferentFWC(x);
    }

    /**
     * Returns an allDifferent constraint that enforces
     * global arc consistency.
     *
     * @param x an array of variables
     * @return a constraint so that {@code x[i] != x[j] for all i < j}
     */
    public static CPConstraint allDifferentDC(CPIntVar[] x) {
        return new AllDifferentDC(x);
    }

    /**
     * Returns an atLeastNValue constraint using a forward checking algo
     *
     * @param x      an array of variables
     * @param nValue the number of values
     * @return a constraint so that {@code #{x[i] | i in 0..x.length-1} >= nValue}
     */
    public static CPConstraint atLeastNValue(CPIntVar[] x, CPIntVar nValue) {
        return new AtLeastNValueFWC(x, nValue);
    }


    /**
     * Return a constraint that enforce
     * that N is the number of indices i such that x[i] is in vals
     * @param x an array of variables
     * @param vals a set of values
     * @param N a variable
     * @return a constraint so that {@code N = #{i | x[i] in vals}}
     */
    public static CPConstraint among(CPIntVar[] x, Set<Integer> vals, CPIntVar N) {
        return new Among(x, vals, N);
    }

    // ********************
    // Circuit constraints
    // ********************

    /**
     * Returns a circuit constraint (using the successor model)
     *
     * @param x an array of variables (successor array)
     * @return a constraint so that the path described by the successor array forms an hamiltonian circuit
     */
    public static CPConstraint circuit(CPIntVar[] x) {
        return new Circuit(x);
    }

    // ********************
    // Extensional constraints
    // ********************

    /**
     * Returns a table constraint
     *
     * @param x     an array of variables
     * @param table an array of tuples
     * @return a constraint the value taken by the variables in x are from a tuple from the table
     */
    public static CPConstraint table(CPIntVar[] x, int[][] table) {
        return new TableCT(x, table);
    }

    /**
     * Returns a short table constraint
     *
     * @param x     an array of variables
     * @param table an array of tuples
     * @param star  the value symbolizing the * (i.e. universal value) in the table
     * @return a constraint the value taken by the variables in x are from a tuple from the table
     */
    public static CPConstraint shortTable(CPIntVar[] x, int[][] table, int star) {
        return new ShortTableCT(x, table, star);
    }

    /**
     * Returns a short table constraint
     *
     * @param x     an array of variables
     * @param table an array of tuples
     * @return a constraint the value taken by the variables in x are not from a tuple from the table
     */
    public static CPConstraint negTable(CPIntVar[] x, int[][] table) {
        return new NegTableCT(x, table);
    }

    // ********************
    // Sequence constraints
    // ********************

    /**
     * Returns a constraint inserting a node within a sequence
     *
     * @param seqVar sequence is which the node must be inserted
     * @param prev   predecessor of the node to insert
     * @param node   node that must be inserted
     * @return a constraint so that {@code seqVar.memberAfter(prev) == node}
     */
    public static CPConstraint insert(CPSeqVar seqVar, int prev, int node) {
        return new Insert(seqVar, prev, node);
    }

    /**
     * Returns a constraint requiring a node within a sequence
     *
     * @param seqVar sequence is which the node must be required
     * @param node   node to require
     * @return a constraint so that {@code seqVar.isNode(node, REQUIRED)} holds
     */
    public static CPConstraint require(CPSeqVar seqVar, int node) {
        return new Require(seqVar, node);
    }

    /**
     * Returns a constraint excluding a node from a sequence
     *
     * @param seqVar sequence is which the node must be excluded
     * @param node   node to exclude
     * @return a constraint so that {@code seqVar.isNode(node, EXCLUDED)} holds
     */
    public static CPConstraint exclude(CPSeqVar seqVar, int node) {
        return new Exclude(seqVar, node);
    }

    /**
     * Returns a constraint removing an insertion within a sequence
     *
     * @param seqVar sequence is which the insertion must be removed
     * @param prev   member nodes after which the insertion must be removed
     * @param node   node whose insertion must be removed
     * @return a constraint so that {@code seqVar.hasInsert(prev, node)} does not holds
     */
    public static CPConstraint removeDetour(CPSeqVar seqVar, int prev, int node, int succ) {
        return new RemoveDetour(seqVar, prev, node, succ);
    }

    // ********************
    // Interval constraints
    // ********************

    /**
     * Returns a CPBoolVar that is equal to the status of the interval.
     *
     * @param var the interval variable
     * @return a CPBoolVar that is equal to the status of the interval
     */
    public static CPIntVar status(CPIntervalVar var) {
        return var.status();
    }

    /**
     * Create a constraint that enforces an interval to be present
     *
     * @param var1 the interval variable
     * @return a constraint that enforces the interval to be present
     */
    public static CPConstraint present(CPIntervalVar var1) {
        return new AbstractCPConstraint(var1.getSolver()) {
            @Override
            public void post() {
                var1.setPresent();
            }
        };
    }

    /**
     * Create a constraint that enforces an interval to be absent
     *
     * @param var1 the interval variable
     * @return a constraint that enforces the interval to be absent
     */
    public static CPConstraint absent(CPIntervalVar var1) {
        return new AbstractCPConstraint(var1.getSolver()) {
            @Override
            public void post() {
                var1.setAbsent();
            }
        };
    }

    /**
     * A variable that is a view of {@code intervalVar+v}, adding an offset to it
     *
     * @param intervalVar a variable
     * @param v           a value
     * @return a variable that is a view of {@code intervalVar+v}
     */
    public static CPIntervalVar delay(CPIntervalVar intervalVar, int v) {
        if (v == 0)
            return intervalVar;
        return new CPIntervalVarOffset(intervalVar, v);
    }

    /**
     * Returns a CPIntVar that is equal to the start of interval.
     *
     * @param var the interval variable, it must be present at the time of the posting
     * @return a CPIntVar that is equal to the start of interval
     */
    public static CPIntVar start(CPIntervalVar var) {
        CPSolver cp = var.getSolver();
        CPIntVar start = makeIntVar(cp, var.startMin(), var.startMax());
        cp.post(new IntervalVarStart(var, start));
        return start;
    }

    /**
     * Returns a CPIntVar that is equal to the start of interval if it is present, or equal to val if interval is absent
     *
     * @param interval the interval variable
     * @param val      the value taken by the returned variable if interval is absent
     * @return a CPIntVar that is equal to the start of interval if it is present, or equal to val if interval is absent
     */
    public static CPIntVar startOr(CPIntervalVar interval, int val) {
        CPSolver cp = interval.getSolver();
        int minBound = Math.min(interval.startMin(), val);
        int maxBound = Math.max(interval.startMax(), val);
        CPIntVar start = makeIntVar(cp, minBound, maxBound);
        cp.post(new IntervalVarStartOrValue(interval, start, val));
        return start;
    }

    /**
     * Create a constraint that enforces the start of var1 to be equal to start
     *
     * @param var1  the interval variable
     * @param start the start value
     * @return a constraint that enforces the start of var1 to be equal to start
     */
    public static CPConstraint startAt(CPIntervalVar var1, int start) {
        return new AbstractCPConstraint(var1.getSolver()) {
            @Override
            public void post() {
                var1.setStart(start);
            }
        };
    }

    /**
     * Create a constraint that enforces the start of var1 to be at or after start
     *
     * @param var1  the interval variable
     * @param start the start value
     * @return a constraint that enforces the start of var1 to be at or after start
     */
    public static CPConstraint startAfter(CPIntervalVar var1, int start) {
        return new AbstractCPConstraint(var1.getSolver()) {
            @Override
            public void post() {
                var1.setStartMin(start);
            }
        };
    }

    /**
     * Create a constraint that enforces the start of var1 to be before or at start
     *
     * @param var1  the interval variable
     * @param start the start value
     * @return a constraint that enforces the start of var1 to be before or at start
     */
    public static CPConstraint startBefore(CPIntervalVar var1, int start) {
        return new AbstractCPConstraint(var1.getSolver()) {
            @Override
            public void post() {
                var1.setStartMax(start);
            }
        };
    }

    /**
     * Creates a constraint that enforces that the start of var1 is equal to the start of var2
     *
     * @param var1 an interval variable
     * @param var2 an interval variable
     * @return a constraint that enforces the start of var1 to be equal to the start of var2
     */
    public static CPConstraint startAtStart(CPIntervalVar var1, CPIntervalVar var2) {
        return new StartAtStart(var1, var2);
    }

    /**
     * Creates a constraint that enforces that the start of var1 is equal to the start of var2 + delay
     *
     * @param var1  an interval variable
     * @param var2  an interval variable
     * @param delay a value
     * @return a constraint that enforces the start of var1 to be equal to the start of var2 + delay
     */
    public static CPConstraint startAtStart(CPIntervalVar var1, CPIntervalVar var2, int delay) {
        return new StartAtStart(var1, delay(var2, delay));
    }

    /**
     * Creates a constraint that enforces that the start of var1 is equal to the end of var2
     *
     * @param var1 an interval variable
     * @param var2 an interval variable
     * @return a constraint that enforces the start of var1 to be equal to the end of var2
     */
    public static CPConstraint startAtEnd(CPIntervalVar var1, CPIntervalVar var2) {
        return new StartAtEnd(var1, var2);
    }

    /**
     * Creates a constraint that enforces that the start of var1 is equal to the end of var2 + delay
     *
     * @param var1  an interval variable
     * @param var2  an interval variable
     * @param delay a value
     * @return a constraint that enforces the start of var1 to be equal to the end of var2 + delay
     */
    public static CPConstraint startAtEnd(CPIntervalVar var1, CPIntervalVar var2, int delay) {
        return new StartAtEnd(var1, delay(var2, delay));
    }

    /**
     * Creates a constraint that enforces that the start of var1 is lower or equal to the start of var2
     *
     * @param var1 an interval variable
     * @param var2 an interval variable
     * @return a constraint that enforces {@code var1.start <= var2.start}
     */
    public static CPConstraint startBeforeStart(CPIntervalVar var1, CPIntervalVar var2) {
        return new StartBeforeStart(var1, var2);
    }

    /**
     * Creates a constraint that enforces that the start of var1 is lower or equal to the start of var2 + delay
     *
     * @param var1  an interval variable
     * @param var2  an interval variable
     * @param delay a value
     * @return a constraint that enforces {@code var1.start <= var2.start}
     */
    public static CPConstraint startBeforeStart(CPIntervalVar var1, CPIntervalVar var2, int delay) {
        return new StartBeforeStart(var1, delay(var2, delay));
    }

    /**
     * Creates a constraint that enforces that the start of var1 is lower or equal to the end of var2
     *
     * @param var1 an interval variable
     * @param var2 an interval variable
     * @return a constraint that enforces {@code var1.start <= var2.end}
     */
    public static CPConstraint startBeforeEnd(CPIntervalVar var1, CPIntervalVar var2) {
        return new StartBeforeEnd(var1, var2);
    }

    /**
     * Creates a constraint that enforces that the start of var1 is lower or equal to the end of var2 + delay
     *
     * @param var1  an interval variable
     * @param var2  an interval variable
     * @param delay a value
     * @return a constraint that enforces {@code var1.start <= var2.end + delay}
     */
    public static CPConstraint startBeforeEnd(CPIntervalVar var1, CPIntervalVar var2, int delay) {
        return new StartBeforeEnd(var1, delay(var2, delay));
    }

    /**
     * Returns a boolean variable representing whether the interval variable var1 starts at or before
     * the start of the interval variable var2.
     * This relation is enforced by the {@link IsStartBeforeStart} constraint posted by calling this method.
     *
     * @param var1 An interval variable
     * @param var2 An interval variable
     * @return a boolean variable which is a reification of the constraint {@code var1.start <= var2.start}
     */
    public static CPBoolVar isStartBeforeStart(CPIntervalVar var1, CPIntervalVar var2) {
        CPBoolVar b = makeBoolVar(var1.getSolver());
        CPSolver cp = var1.getSolver();
        cp.post(new IsStartBeforeStart(var1, var2, b), false);
        return b;
    }

    /**
     * Returns a boolean variable representing whether the interval variable var1 starts at or before
     * the end of the interval variable var2.
     * This relation is enforced by the {@link IsStartBeforeEnd} constraint posted by calling this method.
     *
     * @param var1 An interval variable
     * @param var2 An interval variable
     * @return a boolean variable which is a reification of the constraint {@code var1.start <= var2.end}
     */
    public static CPBoolVar isStartBeforeEnd(CPIntervalVar var1, CPIntervalVar var2) {
        CPBoolVar b = makeBoolVar(var1.getSolver());
        CPSolver cp = var1.getSolver();
        cp.post(new IsStartBeforeEnd(var1, var2, b), false);
        return b;
    }

    /**
     * Returns a CPIntVar that is equal to the start of interval.
     *
     * @param var the interval variable, it must be present at the time of the posting
     * @return a CPIntVar that is equal to the end of interval
     */
    public static CPIntVar end(CPIntervalVar var) {
        CPSolver cp = var.getSolver();
        CPIntVar end = makeIntVar(cp, var.endMin(), var.endMax());
        cp.post(new IntervalVarEnd(var, end));
        return end;
    }

    /**
     * Returns a CPIntVar that is equal to the end of interval if it is present, or equal to val if interval is absent
     *
     * @param interval the interval variable
     * @param val      the value taken by the returned variable if interval is absent
     * @return a CPIntVar that is equal to the end of interval if it is present, or equal to val if interval is absent
     */
    public static CPIntVar endOr(CPIntervalVar interval, int val) {
        CPSolver cp = interval.getSolver();
        int minBound = Math.min(interval.endMin(), val);
        int maxBound = Math.max(interval.endMax(), val);
        CPIntVar end = makeIntVar(cp, minBound, maxBound);
        cp.post(new IntervalVarEndOrValue(interval, end, val));
        return end;
    }

    /**
     * Create a constraint that enforces that var1 ends at end
     *
     * @param var1 the interval variable
     * @param end  the end value
     * @return a constraint that enforces that var1 ends at end
     */
    public static CPConstraint endAt(CPIntervalVar var1, int end) {
        return new AbstractCPConstraint(var1.getSolver()) {
            @Override
            public void post() {
                var1.setEnd(end);
            }
        };
    }

    /**
     * Creates a constraint that enforces that the end of var1 is equal to the start of var2
     *
     * @param var1 an interval variable
     * @param var2 an interval variable
     * @return a constraint that enforces the end of var1 to be equal to the start of var2
     */
    public static CPConstraint endAtStart(CPIntervalVar var1, CPIntervalVar var2) {
        return new EndAtStart(var1, var2);
    }

    /**
     * Creates a constraint that enforces that the end of var1 is equal to the start of var2 + delay
     *
     * @param var1  an interval variable
     * @param var2  an interval variable
     * @param delay a value
     * @return a constraint that enforces the end of var1 to be equal to the start of var2 + delay
     */
    public static CPConstraint endAtStart(CPIntervalVar var1, CPIntervalVar var2, int delay) {
        return new EndAtStart(var1, delay(var2, delay));
    }

    /**
     * Creates a constraint that enforces that the end of var1 is equal to the end of var2
     *
     * @param var1 an interval variable
     * @param var2 an interval variable
     * @return a constraint that enforces the end of var1 to be equal to the end of var2
     */
    public static CPConstraint endAtEnd(CPIntervalVar var1, CPIntervalVar var2) {
        return new EndAtEnd(var1, var2);
    }

    /**
     * Creates a constraint that enforces that the end of var1 is equal to the end of var2 + delay
     *
     * @param var1  an interval variable
     * @param var2  an interval variable
     * @param delay a value
     * @return a constraint that enforces the end of var1 to be equal to the end of var2 + delay
     */
    public static CPConstraint endAtEnd(CPIntervalVar var1, CPIntervalVar var2, int delay) {
        return new EndAtEnd(var1, delay(var2, delay));
    }

    /**
     * Creates a constraint that enforces that the end of var1 is lower or equal to the start of var2
     *
     * @param var1 an interval variable
     * @param var2 an interval variable
     * @return a constraint that enforces {@code var1.end <= var2.start}
     */
    public static CPConstraint endBeforeStart(CPIntervalVar var1, CPIntervalVar var2) {
        return new EndBeforeStart(var1, var2);
    }

    /**
     * Creates a constraint that enforces that the end of var1 is lower or equal to the start of var2 + delay
     *
     * @param var1  an interval variable
     * @param var2  an interval variable
     * @param delay a value
     * @return a constraint that enforces {@code var1.end <= var2.start + delay}
     */
    public static CPConstraint endBeforeStart(CPIntervalVar var1, CPIntervalVar var2, int delay) {
        return new EndBeforeStart(var1, delay(var2, delay));
    }

    /**
     * Creates a constraint that enforces that the end of var1 is lower or equal to the end of var2
     *
     * @param var1 an interval variable
     * @param var2 an interval variable
     * @return a constraint that enforces {@code var1.end <= var2.end}
     */
    public static CPConstraint endBeforeEnd(CPIntervalVar var1, CPIntervalVar var2) {
        return new EndBeforeEnd(var1, var2);
    }

    /**
     * Creates a constraint that enforces that the end of var1 is lower or equal to the end of var2 + delay
     *
     * @param var1  an interval variable
     * @param var2  an interval variable
     * @param delay a value
     * @return a constraint that enforces {@code var1.end <= var2.end}
     */
    public static CPConstraint endBeforeEnd(CPIntervalVar var1, CPIntervalVar var2, int delay) {
        return new EndBeforeEnd(var1, delay(var2, delay));
    }

    /**
     * Returns a boolean variable representing whether the interval variable var1 ends at or before
     * the start of the interval variable var2.
     * This relation is enforced by the {@link IsEndBeforeStart} constraint posted by calling this method.
     *
     * @param var1 An interval variable
     * @param var2 An interval variable
     * @return a boolean variable which is a reification of the constraint {@code var1.end <= var2.start}
     */
    public static CPBoolVar isEndBeforeStart(CPIntervalVar var1, CPIntervalVar var2) {
        CPBoolVar b = makeBoolVar(var1.getSolver());
        CPSolver cp = var1.getSolver();
        cp.post(new IsEndBeforeStart(var1, var2, b), false);
        return b;
    }

    /**
     * Returns a boolean variable representing whether the interval variable var1 ends at or before
     * the end of the interval variable var2.
     * This relation is enforced by the {@link IsEndBeforeEnd} constraint posted by calling this method.
     *
     * @param var1 An interval variable
     * @param var2 An interval variable
     * @return a boolean variable which is a reification of the constraint {@code var1.end <= var2.end}
     */
    public static CPBoolVar isEndBeforeEnd(CPIntervalVar var1, CPIntervalVar var2) {
        CPBoolVar b = makeBoolVar(var1.getSolver());
        CPSolver cp = var1.getSolver();
        cp.post(new IsEndBeforeEnd(var1, var2, b), false);
        return b;
    }

    /**
     * Returns a CPIntVar that is equal to the length of interval.
     *
     * @param var the interval variable, it must be present at the time of the posting
     * @return a CPIntVar that is equal to the length of interval
     */
    public static CPIntVar length(CPIntervalVar var) {
        CPSolver cp = var.getSolver();
        CPIntVar length = makeIntVar(cp, var.lengthMin(), var.lengthMax());
        cp.post(new IntervalVarLength(var, length));
        return length;
    }

    /**
     * Returns a CPIntVar that is equal to the length of interval if it is present, or equal to val if interval is absent
     *
     * @param interval the interval variable
     * @param val      the value taken by the returned variable if interval is absent
     * @return a CPIntVar that is equal to the length of interval if it is present, or equal to val if interval is absent
     */
    public static CPIntVar lengthOr(CPIntervalVar interval, int val) {
        CPSolver cp = interval.getSolver();
        int minBound = Math.min(interval.lengthMin(), val);
        int maxBound = Math.max(interval.lengthMax(), val);
        CPIntVar length = makeIntVar(cp, minBound, maxBound);
        cp.post(new IntervalVarLengthOrValue(interval, length, val));
        return length;
    }

    /**
     * Returns a CPIntVar that is equal to the makespan (the latest end) of the intervals.
     *
     * @param vars the interval vars
     * @return a CPIntVar that is equal to the makespan of the intervals
     */

    public static CPIntVar makespan(CPIntervalVar... vars) {
        Stream<CPIntVar> stream = Arrays.stream(vars).map(var -> endOr(var, 0));
        CPIntVar[] ends = stream.toArray(CPIntVar[]::new);
        CPIntVar end = max(ends);
        return end;
    }

    /**
     * Creates a constraint that enforces that there is no overlap between the intervals in vars.
     *
     * @param vars one or more interval variables
     * @return a noOverlap constraint on the elements of vars.
     */
    public static NoOverlap nonOverlap(CPIntervalVar... vars) {
        return new NoOverlap(vars);
    }

    /**
     * Returns an Alternative constraint:
     * Enforces that if the interval variable interval is present, then cardinality intervals from the array alternatives
     * must be present and synchronized with a.
     * If a is not present, then all the intervals of alternatives must be absent.
     *
     * @param interval     an interval variable
     * @param alternatives an array of interval variables
     * @param cardinality  the cardinality
     * @return an {@link Alternative} constraint
     */
    public static CPConstraint alternative(CPIntervalVar interval, CPIntervalVar[] alternatives, CPIntVar cardinality) {
        CPSolver cp = interval.getSolver();
        return new Alternative(interval, alternatives, cardinality);
    }

    /**
     * Returns an Alternative constraint:
     * Enforces that if the interval variable interval is present, then cardinality intervals from the array alternatives
     * must be present and synchronized with interval.
     * If a is not present, then all the intervals of alternatives must be absent.
     *
     * @param interval     an interval variable
     * @param alternatives an array of interval variables
     * @param cardinality  the cardinality
     * @return an {@link Alternative} constraint
     */
    public static CPConstraint alternative(CPIntervalVar interval, CPIntervalVar[] alternatives, int cardinality) {
        return alternative(interval, alternatives, CPFactory.makeIntVar(interval.getSolver(), cardinality, cardinality));
    }

    /**
     * Returns an Alternative constraint:
     * Enforces that if the interval variable interval is present, then one of the intervals from the array alternatives
     * must be present and synchronized with a.
     * If a is not present, then all the intervals of alternatives must be absent.
     *
     * @param interval     an interval variable
     * @param alternatives an array of interval variables
     * @return an {@link Alternative} constraint
     */
    public static CPConstraint alternative(CPIntervalVar interval, CPIntervalVar[] alternatives) {
        return alternative(interval, alternatives, 1);
    }

    // ********************
    // Cumulative functions
    // ********************

    /**
     * Creates an elementary flat Cumulative Function.
     *
     * @return a cumulative function that is flat
     */
    public static CPCumulFunction flat() {
        return new CPFlatCumulFunction();
    }

    /**
     * Creates an elementary Cumulative Function that is a pulse of height h that happen when the Interval variable
     * var is present.
     *
     * @param var an interval variable
     * @param h an int value
     * @return a cumulative function that is a pulse of height h when var is present
     */
    public static CPCumulFunction pulse(CPIntervalVar var, int h) {
        return new CPPulseCumulFunction(var, h, h);
    }

    /**
     * Creates an elementary Cumulative Function that is a pulse that happen when the Interval variable var is
     * present. Its height is set in the interval {@code [hMin, hMax]}.
     *
     * @param var an interval variable
     * @param hMin an int value
     * @param hMax an int value
     * @return a cumulative function that is a pulse of height {@code [hMin, hMax]} when var is present
     */
    public static CPCumulFunction pulse(CPIntervalVar var, int hMin, int hMax) {
        return new CPPulseCumulFunction(var, hMin, hMax);
    }

    /**
     * Creates an elementary Cumulative Function that is a step of height h that happen at the start of the Interval
     * variable var if it is present.
     *
     * @param var an interval variable
     * @param h an int value
     * @return a cumulative function that is a step of height h at the start of var if it is present
     */
    public static CPCumulFunction stepAtStart(CPIntervalVar var, int h) {
        return new CPStepAtStartCumulFunction(var, h, h);
    }

    /**
     * Creates an elementary Cumulative Function that is a step that happen at the start of the Interval
     * variable var if it is present. Its height is set in the interval [hMin, hMax].
     *
     * @param var an interval variable
     * @param hMin an int value
     * @param hMax an int value
     * @return a cumulative function that is a step of height [hMin, hMax] at the start of var if it is
     * present
     */
    public static CPCumulFunction stepAtStart(CPIntervalVar var, int hMin, int hMax) {
        return new CPStepAtStartCumulFunction(var, hMin, hMax);
    }

    /**
     * Creates an elementary Cumulative Function that is a step of height h that happen at the end of the Interval
     * variable var if it is present.
     *
     * @param var an interval variable
     * @param h an int value
     * @return a cumulative function that is a step of height h at the end of var if it is present
     */
    public static CPCumulFunction stepAtEnd(CPIntervalVar var, int h) {
        return new CPStepAtEndCumulFunction(var, h, h);
    }

    /**
     * Creates an elementary Cumulative Function that is a step that happen at the end of the Interval
     * variable var if it is present. Its height is set in the interval [hMin, hMax].
     *
     * @param var an interval variable
     * @param hMin an int value
     * @param hMax an int value
     * @return a cumulative function that is a step of height [hMin, hMax] at the end of var if it is
     * present
     */
    public static CPCumulFunction stepAtEnd(CPIntervalVar var, int hMin, int hMax) {
        return new CPStepAtEndCumulFunction(var, hMin, hMax);
    }

    /**
     * Creates an elementary Cumulative Function that is a step of height h that happen at time from.
     *
     * @param from an int value
     * @param h an int value
     * @return a cumulative function that is a step of height h at time from
     */
    public static CPCumulFunction step(CPSolver cp, int from, int h) {
        CPIntervalVar dummy = makeIntervalVar(cp);
        dummy.setStartMin(from);
        dummy.setStartMax(from);
        dummy.setEndMin(Constants.HORIZON);
        dummy.setEndMax(Constants.HORIZON);
        dummy.setPresent();
        return pulse(dummy, h);
    }

    /**
     * Creates an elementary Cumulative Function that is a pulse of height h that happen at time from.
     *
     * @param from an int value
     * @param h an int value
     * @return a cumulative function that is a step of height h at time from
     */
    public static CPCumulFunction pulse(CPSolver cp, int from, int to, int h) {
        CPIntervalVar dummy = makeIntervalVar(cp);
        dummy.setStartMin(from);
        dummy.setStartMax(from);
        dummy.setEndMin(to);
        dummy.setEndMax(to);
        dummy.setPresent();
        return pulse(dummy, h);
    }

    /**
     * Creates a cumulative Function that is the sum of two cumulative Functions.
     *
     * @param fun1 a cumulative Function
     * @param fun2 a cumulative Function
     * @return a cumulative function that is the sum of fun1 and fun2
     */
    public static CPCumulFunction plus(CPCumulFunction fun1, CPCumulFunction fun2) {
        return new CPPlusCumulFunction(fun1, fun2);
    }

    /**
     * Creates a cumulative Function that is the difference of two cumulative Functions.
     *
     * @param fun1 a cumulative Function
     * @param fun2 a cumulative Function
     * @return a cumulative function that is the difference of fun1 and fun2
     */
    public static CPCumulFunction minus(CPCumulFunction fun1, CPCumulFunction fun2) {
        return new CPMinusCumulFunction(fun1, fun2);
    }

    /**
     * Creates a cumulative Function that is the sum of zero or more cumulative Functions.
     *
     * @param fun a cumulative Function
     * @return a cumulative function that is the sum of the cumulative Functions in fun
     */
    public static CPCumulFunction sum(CPCumulFunction... fun) {
        return new CPSumCumulFunction(fun);
    }

    // *********************************
    // Cumulative function constraints
    // *********************************

    /**
     * Requires a cumulative function to always be within the range [minValue..maxValue]
     * on the execution range of the cumulative function.
     *
     * @param fun a cumulative function
     * @param minValue an int value
     * @param maxValue an int value
     * @return a constraint which enforces the cumulative function fun to stay within the range [minValue..maxValue]
     */
    public static CPConstraint alwaysIn(CPCumulFunction fun, int minValue, int maxValue) {
        List<Activity> activities = fun.flatten(true);

        if (activities.isEmpty() && minValue > 0) {
            throw new InconsistencyException();
        }
        if (activities.isEmpty()) {
            return new DoNothingConstraint();
        }

        CPSolver cp = activities.get(0).interval().getSolver();

        return new AbstractCPConstraint(cp) {
            @Override
            public void post() {
                int minStart = Constants.HORIZON;
                int maxEnd = 0;
                for(Activity act : activities){
                    if(act.getStartMin() < minStart) minStart = act.getStartMin();
                    if(act.getEndMax() > maxEnd) maxEnd = act.getEndMax();
                }
                CPIntervalVar interval = makeIntervalVar(cp, false, maxEnd - minStart);
                interval.setStart(minStart);
                interval.setEnd(maxEnd);
                Activity dummy = new Activity(interval, makeIntVar(cp, 0, 0));
                activities.add(dummy);
                cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), minValue, maxValue));
            }
        };
    }

    /**
     * Requires a cumulative function to always be within the range [minValue..maxValue]
     * on the execution range [from..to).
     *
     * @param fun a cumulative function
     * @param minValue an int value
     * @param maxValue an int value
     * @param from an int value
     * @param to an int value
     * @return a constraint which enforces the cumulative function fun to stay within the range [minValue..maxValue]
     */
    public static CPConstraint alwaysIn(CPCumulFunction fun, int minValue, int maxValue, int from, int to) {
        List<Activity> activities = fun.flatten(true);

        if (activities.isEmpty() && minValue > 0) {
            throw new InconsistencyException();
        }
        if (activities.isEmpty()) {
            return new DoNothingConstraint();
        }

        CPSolver cp = activities.get(0).interval().getSolver();

        return new AbstractCPConstraint(cp) {
            @Override
            public void post() {
                CPIntervalVar interval = makeIntervalVar(cp, false, to - from);
                interval.setStart(from);
                interval.setEnd(to);
                Activity dummy = new Activity(interval, makeIntVar(cp, 0, 0));
                activities.add(dummy);
                cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), minValue, maxValue));
            }
        };
    }

    /**
     * Requires a cumulative function to always be equal or lesser than a value
     * on the execution range [0..Constants.HORIZON).
     *
     * @param fun a cumulative function
     * @param maxValue an int value
     * @return a constraint which ensures that fun is always lesser or equal to maxVal
     */
    public static CPConstraint le(CPCumulFunction fun, int maxValue) {
        List<Activity> activities = fun.flatten(true);

        if (activities.isEmpty()) {
            return new DoNothingConstraint();
        }

        CPSolver cp = activities.get(0).interval().getSolver();
        return new AbstractCPConstraint(cp) {
            @Override
            public void post() {
                cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), maxValue));
            }
        };
    }
}