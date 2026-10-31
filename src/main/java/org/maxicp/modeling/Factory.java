/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.modeling;

import org.maxicp.Constants;
import org.maxicp.ModelDispatcher;
import org.maxicp.cp.engine.constraints.scheduling.CPCumulFunction;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.algebra.bool.*;
import org.maxicp.modeling.algebra.integer.*;
import org.maxicp.modeling.algebra.scheduling.CumulFunction;
import org.maxicp.modeling.constraints.*;
import org.maxicp.modeling.constraints.scheduling.AlwaysIn;
import org.maxicp.modeling.constraints.scheduling.LessOrEqual;
import org.maxicp.modeling.constraints.seqvar.*;
import org.maxicp.modeling.symbolic.*;

import static org.maxicp.cp.CPFactory.makeIntervalVar;

import java.util.Optional;
import java.util.Set;

/**
 * Modeling factory providing static helper methods to create model dispatchers, objectives,
 * symbolic integer/boolean/cumulative expressions, and high-level modeling constraints.
 */
public final class Factory {
    private Factory() {
    }

    // -------------- instantiation of the model -----------------------

    /**
     * Creates a new {@link ModelDispatcher} to build and solve constraint programming models.
     *
     * @return a new {@link ModelDispatcher} instance
     */
    static public ModelDispatcher makeModelDispatcher() {
        return new ModelDispatcher();
    }

    // -------------- objectives -----------------------

    /**
     * Creates a minimization objective for the given integer expression.
     *
     * @param x      the expression to minimize
     * @param shared {@code true} if the objective bound should be shared across parallel searches
     * @return the minimization {@link Objective}
     */
    public static Objective minimize(IntExpression x, boolean shared) {
        if (shared)
            return new SharedMinimization(x);
        return new Minimization(x);
    }

    /**
     * Creates a minimization objective for the given integer expression.
     *
     * @param x the expression to minimize
     * @return the minimization {@link Objective}
     */
    public static Objective minimize(IntExpression x) {
        return minimize(x, false);
    }

    /**
     * Creates a maximization objective for the given integer expression.
     *
     * @param x      the expression to maximize
     * @param shared {@code true} if the objective bound should be shared across parallel searches
     * @return the maximization {@link Objective}
     */
    public static Objective maximize(IntExpression x, boolean shared) {
        if (shared)
            return new SharedMaximization(x);
        return new Maximization(x);
    }

    /**
     * Creates a maximization objective for the given integer expression.
     *
     * @param x the expression to maximize
     * @return the maximization {@link Objective}
     */
    public static Objective maximize(IntExpression x) {
        return maximize(x, false);
    }

    // -------------- constraints -----------------------

    // ********************
    // Arithmetic constraints (+, -, *, abs)
    // ********************

    /**
     * Creates a constant integer expression with value {@code v}.
     *
     * @param modelProxy the model proxy
     * @param v          the constant integer value
     * @return a constant {@link IntExpression} equal to {@code v}
     */
    public static IntExpression cst(ModelProxy modelProxy, int v) {
        return new Constant(modelProxy, v);
    }

    /**
     * Returns an integer expression representing {@code a + v}.
     *
     * @param a an integer expression
     * @param v a constant offset
     * @return the expression {@code a + v}
     */
    static public IntExpression plus(IntExpression a, int v) {
        return new Sum(a, new Constant(a.getModelProxy(), v));
    }

    /**
     * Returns an integer expression representing {@code a + b}.
     *
     * @param a the first integer expression
     * @param b the second integer expression
     * @return the expression {@code a + b}
     */
    static public IntExpression plus(IntExpression a, IntExpression b) {
        return new Sum(a, b);
    }

    /**
     * Returns an integer expression representing {@code a - v}.
     *
     * @param a an integer expression
     * @param v a constant offset to subtract
     * @return the expression {@code a - v}
     */
    static public IntExpression minus(IntExpression a, int v) {
        return new Sum(a, new Constant(a.getModelProxy(), -v));
    }

    /**
     * Returns an integer expression representing {@code a - b}.
     *
     * @param a the first integer expression
     * @param b the second integer expression
     * @return the expression {@code a - b}
     */
    static public IntExpression minus(IntExpression a, IntExpression b) {
        return new Sum(a, minus(b));
    }

    /**
     * Returns an integer expression representing the unary negation {@code -a}.
     *
     * @param a an integer expression
     * @return the expression {@code -a}
     */
    static public IntExpression minus(IntExpression a) {
        return new UnaryMinus(a);
    }

    /**
     * Returns an integer expression representing {@code a * b}.
     *
     * @param a an integer expression
     * @param b a constant multiplier
     * @return the expression {@code a * b}
     */
    static public IntExpression mul(IntExpression a, int b) {
        return new CstMul(a, b);
    }

    /**
     * Returns an integer expression representing the integer division {@code a / b}.
     *
     * @param a the dividend expression
     * @param b the constant divisor
     * @return the expression {@code a / b}
     */
    static public IntExpression div(IntExpression a, int b) {
        return div(a, new Constant(a.getModelProxy(), b));
    }

    /**
     * Returns an integer expression representing {@code b * a}.
     *
     * @param b a constant multiplier
     * @param a an integer expression
     * @return the expression {@code b * a}
     */
    static public IntExpression mul(int b, IntExpression a) {
        return new CstMul(a, b);
    }

    /**
     * Returns an integer expression representing {@code x * b} where {@code b} is a boolean expression.
     *
     * @param x an integer expression
     * @param b a boolean expression (0 or 1)
     * @return the expression {@code x * b}
     */
    static public IntExpression mul(IntExpression x, BoolExpression b) {
        return new MulBinary(x, b);
    }

    /**
     * Returns an integer expression representing the product of the given expressions.
     *
     * @param x the integer expressions to multiply
     * @return the product expression {@code x[0] * x[1] * ... * x[n-1]}
     */
    static public IntExpression mul(IntExpression... x) {
        return new Mul(x);
    }

    /**
     * Returns an integer expression representing the absolute value {@code |x|}.
     *
     * @param x an integer expression
     * @return the expression {@code |x|}
     */
    public static IntExpression abs(IntExpression x) {
        return new Abs(x);
    }

    /**
     * Returns an integer expression representing the integer division {@code x / y}.
     *
     * @param x the dividend expression
     * @param y the divisor expression
     * @return the expression {@code x / y}
     */
    public static IntExpression div(IntExpression x, IntExpression y) {
        return new Div(x, y);
    }

    // ********************
    // Arithmetic constraints (sum, min, max)
    // ********************

    /**
     * Returns an integer expression representing the sum of the given expressions.
     *
     * @param x the integer expressions to sum
     * @return the sum expression {@code x[0] + x[1] + ... + x[n-1]}
     */
    static public IntExpression sum(IntExpression... x) {
        return new Sum(x);
    }

    /**
     * Returns an integer expression representing the maximum of the given expressions.
     *
     * @param x the integer expressions
     * @return the maximum expression {@code max(x)}
     */
    public static IntExpression max(IntExpression... x) {
        return new Max(x);
    }

    /**
     * Returns an integer expression representing the minimum of the given expressions.
     *
     * @param x the integer expressions
     * @return the minimum expression {@code min(x)}
     */
    public static IntExpression min(IntExpression... x) {
        return new Min(x);
    }

    // ********************
    // Comon constraints (=, !=, <=, <, >=, >)
    // ********************

    /**
     * Returns a boolean expression representing {@code a == b}.
     *
     * @param a an integer expression
     * @param b a constant value
     * @return a boolean expression true iff {@code a == b}
     */
    static public BoolExpression eq(IntExpression a, int b) {
        return new Eq(a, new Constant(a.getModelProxy(), b));
    }

    /**
     * Returns a boolean expression representing {@code a == b}.
     *
     * @param a a constant value
     * @param b an integer expression
     * @return a boolean expression true iff {@code a == b}
     */
    static public BoolExpression eq(int a, IntExpression b) {
        return new Eq(a, b);
    }

    /**
     * Returns a boolean expression representing {@code a == b}.
     *
     * @param a the first integer expression
     * @param b the second integer expression
     * @return a boolean expression true iff {@code a == b}
     */
    static public BoolExpression eq(IntExpression a, IntExpression b) {
        return new Eq(a, b);
    }

    /**
     * Returns a boolean expression representing {@code a != b}.
     *
     * @param a an integer expression
     * @param b a constant value
     * @return a boolean expression true iff {@code a != b}
     */
    static public BoolExpression neq(IntExpression a, int b) {
        return new NotEq(a, new Constant(a.getModelProxy(), b));
    }

    /**
     * Returns a boolean expression representing {@code a != b}.
     *
     * @param a a constant value
     * @param b an integer expression
     * @return a boolean expression true iff {@code a != b}
     */
    static public BoolExpression neq(int a, IntExpression b) {
        return new NotEq(a, b);
    }

    /**
     * Returns a boolean expression representing {@code a != b}.
     *
     * @param a the first integer expression
     * @param b the second integer expression
     * @return a boolean expression true iff {@code a != b}
     */
    static public BoolExpression neq(IntExpression a, IntExpression b) {
        return new NotEq(a, b);
    }

    /**
     * Returns a boolean expression representing {@code a <= b}.
     *
     * @param a an integer expression
     * @param b a constant value
     * @return a boolean expression true iff {@code a <= b}
     */
    static public BoolExpression le(IntExpression a, int b) {
        return new LessOrEq(a, new Constant(a.getModelProxy(), b));
    }

    /**
     * Returns a boolean expression representing {@code a <= b}.
     *
     * @param a a constant value
     * @param b an integer expression
     * @return a boolean expression true iff {@code a <= b}
     */
    static public BoolExpression le(int a, IntExpression b) {
        return new LessOrEq(a, b);
    }

    /**
     * Returns a boolean expression representing {@code a <= b}.
     *
     * @param a the first integer expression
     * @param b the second integer expression
     * @return a boolean expression true iff {@code a <= b}
     */
    static public BoolExpression le(IntExpression a, IntExpression b) {
        return new LessOrEq(a, b);
    }

    /**
     * Returns a boolean expression representing {@code a < b}.
     *
     * @param a a constant value
     * @param b an integer expression
     * @return a boolean expression true iff {@code a < b}
     */
    static public BoolExpression lt(int a, IntExpression b) {
        return new LessOrEq(new Constant(b.getModelProxy(), a + 1), b);
    }

    /**
     * Returns a boolean expression representing {@code a < b}.
     *
     * @param a an integer expression
     * @param b a constant value
     * @return a boolean expression true iff {@code a < b}
     */
    static public BoolExpression lt(IntExpression a, int b) {
        return new LessOrEq(a, new Constant(a.getModelProxy(), b - 1));
    }

    /**
     * Returns a boolean expression representing {@code a < b}.
     *
     * @param a the first integer expression
     * @param b the second integer expression
     * @return a boolean expression true iff {@code a < b}
     */
    static public BoolExpression lt(IntExpression a, IntExpression b) {
        return new LessOrEq(a, Factory.minus(b, 1));
    }

    /**
     * Returns a boolean expression representing {@code a >= b}.
     *
     * @param a an integer expression
     * @param b a constant value
     * @return a boolean expression true iff {@code a >= b}
     */
    static public BoolExpression ge(IntExpression a, int b) {
        return new GreaterOrEq(a, new Constant(a.getModelProxy(), b));
    }

    /**
     * Returns a boolean expression representing {@code a >= b}.
     *
     * @param a a constant value
     * @param b an integer expression
     * @return a boolean expression true iff {@code a >= b}
     */
    static public BoolExpression ge(int a, IntExpression b) {
        return new GreaterOrEq(a, b);
    }

    /**
     * Returns a boolean expression representing {@code a >= b}.
     *
     * @param a the first integer expression
     * @param b the second integer expression
     * @return a boolean expression true iff {@code a >= b}
     */
    static public BoolExpression ge(IntExpression a, IntExpression b) {
        return new GreaterOrEq(a, b);
    }

    /**
     * Returns a boolean expression representing {@code a > b}.
     *
     * @param a an integer expression
     * @param b a constant value
     * @return a boolean expression true iff {@code a > b}
     */
    static public BoolExpression gt(IntExpression a, int b) {
        return new GreaterOrEq(a, new Constant(a.getModelProxy(), b + 1));
    }

    /**
     * Returns a boolean expression representing {@code a > b}.
     *
     * @param a a constant value
     * @param b an integer expression
     * @return a boolean expression true iff {@code a > b}
     */
    static public BoolExpression gt(int a, IntExpression b) {
        return new GreaterOrEq(new Constant(b.getModelProxy(), a - 1), b);
    }

    /**
     * Returns a boolean expression representing {@code a > b}.
     *
     * @param a the first integer expression
     * @param b the second integer expression
     * @return a boolean expression true iff {@code a > b}
     */
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

    /**
     * Returns the logical negation {@code !a}.
     *
     * @param a a boolean expression
     * @return the negated boolean expression {@code !a}
     */
    public static BoolExpression not(BoolExpression a) {
        return new Not(a);
    }

    /**
     * Returns the logical conjunction (AND) of the given boolean expressions.
     *
     * @param t the boolean expressions
     * @return a boolean expression true iff all expressions in {@code t} are true
     */
    public static BoolExpression and(BoolExpression... t) {
        return new And(t);
    }

    /**
     * Returns the logical disjunction (OR) of the given boolean expressions.
     *
     * @param t the boolean expressions
     * @return a boolean expression true iff at least one expression in {@code t} is true
     */
    public static BoolExpression or(BoolExpression... t) {
        return new Or(t);
    }

    /**
     * Returns the logical implication of a and b
     * 
     * @param a a boolean expression
     * @param b a boolean expression
     * @return {@code a => b}
     */
    public static BoolExpression implies(BoolExpression a, BoolExpression b) {
        return or(not(a), b);
    }

    /**
     * Returns a boolean expression representing whether interval {@code a} ends at or before the start of {@code b}.
     *
     * @param a the first interval variable
     * @param b the second interval variable
     * @return a boolean expression true iff {@code end(a) <= start(b)} when both are present
     */
    public static BoolExpression endBeforeStart(IntervalVar a, IntervalVar b) {
        return new EndBeforeStart(a, b);
    }

    // ********************
    // Element constraints
    // ********************

    /**
     * Returns an integer expression representing {@code T[y]}.
     *
     * @param T the array of constant values
     * @param y the index expression
     * @return the element expression {@code T[y]}
     */
    public static IntExpression get(int[] T, IntExpression y) {
        return new Element1D(T, y);
    }

    /**
     * Returns an integer expression representing {@code T[y]}.
     *
     * @param T the array of integer expressions
     * @param y the index expression
     * @return the element expression {@code T[y]}
     */
    public static IntExpression get(IntExpression[] T, IntExpression y) {
        return new Element1DVar(T, y);
    }

    /**
     * Returns an integer expression representing {@code T[x][y]}.
     *
     * @param T the 2D matrix of constant values
     * @param x the row index expression
     * @param y the column index expression
     * @return the element expression {@code T[x][y]}
     */
    public static IntExpression get(int[][] T, IntExpression x, IntExpression y) {
        return new Element2D(T, x, y);
    }

    /**
     * Returns an integer expression representing {@code T[x][y]}.
     *
     * @param T the 2D matrix of integer expressions
     * @param x the row index expression
     * @param y the column index expression
     * @return the element expression {@code T[x][y]}
     */
    public static IntExpression get(IntExpression[][] T, IntExpression x, IntExpression y) {
        return new Element2DVar(T, x, y);
    }

    /**
     * Creates a non-strict lexicographic ordering constraint {@code x <=_lex y}.
     *
     * @param x the first array of integer expressions
     * @param y the second array of integer expressions
     * @return a {@link LexLe} constraint enforcing {@code x <=_lex y}
     */
    public static Constraint lexLe(IntExpression[] x, IntExpression[] y) {
        return new LexLe(x, y, false);
    }

    /**
     * Creates a strict lexicographic ordering constraint {@code x <_lex y}.
     *
     * @param x the first array of integer expressions
     * @param y the second array of integer expressions
     * @return a {@link LexLe} constraint enforcing {@code x <_lex y}
     */
    public static Constraint lexLt(IntExpression[] x, IntExpression[] y) {
        return new LexLe(x, y, true);
    }

    /**
     * Creates an {@link Among} constraint ensuring that {@code count} expressions in {@code x}
     * take a value in {@code values}.
     *
     * @param x      the array of integer expressions
     * @param values the set of target values
     * @param count  the expression counting how many elements of {@code x} belong to {@code values}
     * @return an {@link Among} constraint
     */
    public static Constraint among(IntExpression[] x, Set<Integer> values, IntExpression count) {
        return new Among(x, values, count);
    }

    /**
     * Creates a {@link SubCircuit} constraint ensuring that the successor array {@code x}
     * forms at most one circuit.
     *
     * @param x the array of successor expressions
     * @return a {@link SubCircuit} constraint
     */
    public static Constraint subCircuit(IntExpression... x) {
        return new SubCircuit(x);
    }

    /**
     * Creates a {@link CardinalityMin} constraint ensuring that each value {@code v} appears
     * at least {@code minCard[v]} times in {@code x}.
     *
     * @param x       the array of integer expressions
     * @param minCard the minimum number of occurrences for each value {@code 0..minCard.length - 1}
     * @return a {@link CardinalityMin} constraint
     */
    public static Constraint cardinalityMin(IntExpression[] x, int[] minCard) {
        return new CardinalityMin(x, minCard);
    }

    /**
     * Creates a {@link CardinalityMax} constraint ensuring that each value {@code v} appears
     * at most {@code maxCard[v]} times in {@code x}.
     *
     * @param x       the array of integer expressions
     * @param maxCard the maximum number of occurrences for each value {@code 0..maxCard.length - 1}
     * @return a {@link CardinalityMax} constraint
     */
    public static Constraint cardinalityMax(IntExpression[] x, int[] maxCard) {
        return new CardinalityMax(x, maxCard);
    }

    // ********************
    // AllDifferent constraints (and constraints regarding the number of different
    // values)
    // ********************

    /**
     * Creates an {@link AllDifferent} constraint ensuring that all expressions in {@code x}
     * take pairwise distinct values.
     *
     * @param x the integer expressions that must be pairwise distinct
     * @return an {@link AllDifferent} constraint
     */
    public static Constraint allDifferent(IntExpression... x) {
        return new AllDifferent(x);
    }

    /**
     * Creates a {@link BinPacking} constraint linking item bin assignments {@code x} and item {@code weights}
     * to bin {@code loads}.
     *
     * @param x       the bin assignment expression for each item
     * @param weights the weight of each item
     * @param loads   the total load expression for each bin
     * @return a {@link BinPacking} constraint
     */
    public static Constraint binPacking(IntExpression[] x, int[] weights, IntExpression[] loads) {
        return new BinPacking(x, weights, loads);
    }

    // ********************
    // Circuit constraints
    // ********************

    /**
     * Creates a {@link Circuit} constraint ensuring that the successor array {@code x}
     * forms a single Hamiltonian circuit.
     *
     * @param x the array of successor expressions
     * @return a {@link Circuit} constraint
     */
    public static Constraint circuit(IntExpression... x) {
        return new Circuit(x);
    }

    // ********************
    // Extensional constraints
    // ********************

    /**
     * Creates a positive {@link Table} constraint ensuring that the tuple assigned to {@code x}
     * belongs to {@code array}.
     *
     * @param x       the array of integer expressions
     * @param array   the table of allowed tuples
     * @param starred optional wildcard value representing any value in a tuple
     * @return a {@link Table} constraint
     */
    public static Constraint table(IntExpression[] x, int[][] array, Optional<Integer> starred) {
        return new Table(x, array, starred);
    }

    /**
     * Creates a negative {@link NegTable} constraint ensuring that the tuple assigned to {@code x}
     * does not match any tuple in {@code array}.
     *
     * @param x       the array of integer expressions
     * @param array   the table of forbidden tuples
     * @param starred optional wildcard value representing any value in a tuple
     * @return a {@link NegTable} constraint
     */
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

    /**
     * Creates a {@link org.maxicp.modeling.constraints.Cumulative} scheduling constraint with constant durations,
     * demands, and capacity.
     *
     * @param start    the start time expressions of the activities
     * @param duration the durations of the activities
     * @param demand   the resource demands of the activities
     * @param capa     the resource capacity
     * @return a {@link org.maxicp.modeling.constraints.Cumulative} constraint
     */
    public static Constraint cumulative(IntExpression[] start, int[] duration, int[] demand, int capa) {
        return new org.maxicp.modeling.constraints.Cumulative(start, duration, demand, capa);
    }

    /**
     * Creates a {@link org.maxicp.modeling.constraints.Cumulative} scheduling constraint with variable durations,
     * ends, demands, and capacity.
     *
     * @param start    the start time expressions of the activities
     * @param duration the duration expressions of the activities
     * @param end      the end time expressions of the activities
     * @param demand   the resource demand expressions of the activities
     * @param capa     the resource capacity expression
     * @return a {@link org.maxicp.modeling.constraints.Cumulative} constraint
     */
    public static Constraint cumulative(IntExpression[] start, IntExpression[] duration, IntExpression[] end,
            IntExpression[] demand, IntExpression capa) {
        return new org.maxicp.modeling.constraints.Cumulative(start, duration, end, demand, capa);
    }

    /**
     * Creates a {@link Disjunctive} scheduling constraint with constant activity durations.
     *
     * @param start    the start time expressions of the activities
     * @param duration the durations of the activities
     * @return a {@link Disjunctive} constraint
     */
    public static Constraint disjunctive(IntExpression[] start, int[] duration) {
        return new Disjunctive(start, duration);
    }

    /**
     * Creates a {@link Disjunctive} scheduling constraint with variable activity durations.
     *
     * @param start    the start time expressions of the activities
     * @param duration the duration expressions of the activities
     * @return a {@link Disjunctive} constraint
     */
    public static Constraint disjunctive(IntExpression[] start, IntExpression[] duration) {
        return new Disjunctive(start, duration);
    }

    /**
     * Creates a {@link Distance} constraint on a sequence variable maintaining the total travel distance.
     *
     * @param seqVar         the sequence variable
     * @param distanceMatrix the distance matrix between nodes
     * @param distance       the total distance expression
     * @return a {@link Distance} constraint
     */
    public static Constraint distance(SeqVar seqVar, int[][] distanceMatrix, IntExpression distance) {
        return new Distance(seqVar, distanceMatrix, distance);
    }

    /**
     * Creates a {@link Require} constraint forcing {@code node} to be visited in {@code seqVar}.
     *
     * @param seqVar the sequence variable
     * @param node   the node to require
     * @return a {@link Require} constraint
     */
    public static Constraint require(SeqVar seqVar, int node) {
        return new Require(seqVar, node);
    }

    /**
     * Creates an {@link Insert} constraint inserting {@code node} immediately after {@code prev} in {@code seqVar}.
     *
     * @param seqVar the sequence variable
     * @param prev   the predecessor node
     * @param node   the node to insert
     * @return an {@link Insert} constraint
     */
    public static Constraint insert(SeqVar seqVar, int prev, int node) {
        return new Insert(seqVar, prev, node);
    }

    /**
     * Creates an {@link Exclude} constraint excluding {@code node} from {@code seqVar}.
     *
     * @param seqVar the sequence variable
     * @param node   the node to exclude
     * @return an {@link Exclude} constraint
     */
    public static Constraint exclude(SeqVar seqVar, int node) {
        return new Exclude(seqVar, node);
    }

    /**
     * Creates a {@link NotBetween} constraint forbidding {@code node} from being placed immediately between
     * {@code pred} and {@code succ} in {@code seqVar}.
     *
     * @param seqVar the sequence variable
     * @param pred   the predecessor node
     * @param node   the node whose insertion is forbidden
     * @param succ   the successor node
     * @return a {@link NotBetween} constraint
     */
    public static Constraint notBetween(SeqVar seqVar, int pred, int node, int succ) {
        return new NotBetween(seqVar, pred, node, succ);
    }

    /**
     * Creates a {@link TransitionTimes} constraint linking visit times with transition distances in {@code seqVar}.
     *
     * @param seqVar the sequence variable
     * @param time   the visit time expression for each node
     * @param dist   the transition distance matrix between nodes
     * @return a {@link TransitionTimes} constraint
     */
    public static Constraint transitionTimes(SeqVar seqVar, IntExpression[] time, int[][] dist) {
        return new TransitionTimes(seqVar, time, dist);
    }

    /**
     * Creates a {@link TransitionTimes} constraint linking visit times with transition distances and service durations
     * in {@code seqVar}.
     *
     * @param seqVar      the sequence variable
     * @param time        the visit time expression for each node
     * @param dist        the transition distance matrix between nodes
     * @param serviceTime the service duration at each node
     * @return a {@link TransitionTimes} constraint
     */
    public static Constraint transitionTimes(SeqVar seqVar, IntExpression[] time, int[][] dist, int[] serviceTime) {
        return new TransitionTimes(seqVar, time, dist, serviceTime);
    }

    /**
     * Creates a {@link org.maxicp.modeling.constraints.seqvar.Cumulative} constraint on a sequence variable.
     *
     * @param seqVar   the sequence variable
     * @param starts   the start nodes of the activities
     * @param ends     the end nodes of the activities
     * @param load     the load of each activity
     * @param capacity the maximum resource capacity along the sequence
     * @return a sequence {@link org.maxicp.modeling.constraints.seqvar.Cumulative} constraint
     */
    public static Constraint cumulative(SeqVar seqVar, int[] starts, int[] ends, int[] load, int capacity) {
        return new org.maxicp.modeling.constraints.seqvar.Cumulative(seqVar, starts, ends, load, capacity);
    }

    /**
     * Creates a {@link org.maxicp.modeling.constraints.scheduling.NoOverlap} constraint ensuring that
     * the given interval variables do not overlap in time.
     *
     * @param intervals the interval variables
     * @return a {@link org.maxicp.modeling.constraints.scheduling.NoOverlap} constraint
     */
    public static Constraint noOverlap(IntervalVar... intervals) {
        return new org.maxicp.modeling.constraints.scheduling.NoOverlap(intervals);
    }

    /**
     * Creates a noOverlap constraint with position variables and zero transition
     * times.
     *
     * @param intervals     the interval variables
     * @param posOfInterval position of each interval
     * @param intervalInPos interval at each position
     * @return a NoOverlapWithPosition constraint
     */
    public static Constraint noOverlap(IntervalVar[] intervals, IntExpression[] posOfInterval,
            IntExpression[] intervalInPos) {
        return new org.maxicp.modeling.constraints.scheduling.NoOverlapWithPosition(intervals, posOfInterval,
                intervalInPos, null);
    }

    /**
     * Creates a noOverlap constraint with position variables and minimum transition
     * times.
     *
     * @param intervals     the interval variables
     * @param posOfInterval position of each interval
     * @param intervalInPos interval at each position
     * @param minTransition n×n transition time matrix
     * @return a NoOverlapWithPosition constraint with transition times
     */
    public static Constraint noOverlap(IntervalVar[] intervals, IntExpression[] posOfInterval,
            IntExpression[] intervalInPos, int[][] minTransition) {
        return new org.maxicp.modeling.constraints.scheduling.NoOverlapWithPosition(intervals, posOfInterval,
                intervalInPos, minTransition);
    }

    public static Constraint alternative(IntervalVar real, IntervalVar... alternatives) {
        return new org.maxicp.modeling.constraints.scheduling.Alternative(real,
                new IntVarRangeImpl(real.getModelProxy(), 0, 1), alternatives);
    }

    public static Constraint alternative(IntervalVar real, IntExpression n, IntervalVar... alternatives) {
        return new org.maxicp.modeling.constraints.scheduling.Alternative(real, n, alternatives);
    }

    public static Constraint length(IntervalVar var, int length) {
        return new org.maxicp.modeling.constraints.scheduling.Length(var, length);
    }

    // public static Constraint present(IntervalVar var) {
    // return new org.maxicp.modeling.constraints.scheduling.Present(var);
    // }

    public static BoolExpression present(IntervalVar var) {
        return new org.maxicp.modeling.algebra.bool.Present(var);
    }

    public static Constraint start(IntervalVar var, int start) {
        return new org.maxicp.modeling.constraints.scheduling.Start(var, start);
    }

    /*
     * public static Constraint startAfter(IntervalVar var, int start) {
     * return new org.maxicp.modeling.constraints.scheduling.StartAfter(var, start);
     * }
     * 
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

    public static CumulFunction pulse(IntervalVar interval, IntExpression height) {
        return new org.maxicp.modeling.algebra.scheduling.PulseCumulFunction(interval, height);
    }

    public static CumulFunction sum(CumulFunction... functions) {
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

    public static CumulFunction step(ModelDispatcher m, int from, int h) {
        IntervalVar dummy = m.intervalVar(from, Constants.HORIZON, Constants.HORIZON, true);
        return pulse(dummy, h);
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
