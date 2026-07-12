/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.cp.engine.core.mining.SequenceDb;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.concrete.ConcreteVar;

/**
 * A sequence pattern variable represents an unknown sequential pattern
 * (a sequence of symbols) mined from a fixed {@link SequenceDb}.
 * <p>
 * Its domain is conceptually represented by:
 * <ul>
 *     <li>a committed prefix: the symbols already appended, in order;</li>
 *     <li>a candidate frontier: the admissible next symbols for the current prefix,
 *         restricted to the symbols whose projected support is positive;</li>
 *     <li>a termination status: {@link #POSSIBLE} (the pattern may terminate here or
 *         be extended), {@link #TERMINATED} (the pattern is exactly the current prefix)
 *         or {@link #FORBIDDEN} (the pattern must be a strict extension of the current prefix).</li>
 * </ul>
 * The variable is fixed when it is terminated.
 * <p>
 * The support of the prefix and of its one-symbol extensions is maintained
 * incrementally with the PPIC pseudo-projection kernel (Aoga, Guns, Schaus,
 * An efficient algorithm for mining frequent sequence with constraint programming,
 * ECML-PKDD 2016).
 */
public interface CPSeqPatternVar extends CPVar, ConcreteVar {

    /**
     * Status value: the pattern may terminate at the current prefix or be extended.
     */
    int POSSIBLE = 0;
    /**
     * Status value: the pattern is exactly the current prefix.
     */
    int TERMINATED = 1;
    /**
     * Status value: the pattern must be a strict extension of the current prefix.
     */
    int FORBIDDEN = 2;

    /**
     * Returns the sequence database over which the pattern is mined.
     *
     * @return the sequence database
     */
    SequenceDb database();

    /**
     * Returns the number of symbols of the database, i.e. the largest symbol id plus one.
     *
     * @return the number of symbols
     */
    int nSymbols();

    /**
     * Returns the length of the committed prefix.
     *
     * @return the length of the committed prefix
     */
    int prefixLength();

    /**
     * Returns a symbol of the committed prefix.
     *
     * @param j a 0-based index in {@code 0..prefixLength()-1}
     * @return the symbol in position j of the prefix
     */
    int prefixSymbol(int j);

    /**
     * Copies the committed prefix (in order) into an array.
     *
     * @param dest an array large enough, i.e. {@code dest.length >= prefixLength()}
     * @return the length of the prefix
     */
    int fillPrefix(int[] dest);

    /**
     * Returns whether a symbol is an admissible next symbol for the current prefix.
     *
     * @param a a symbol
     * @return true if a can be appended to the current prefix
     */
    boolean isCandidate(int a);

    /**
     * Returns the number of admissible next symbols.
     *
     * @return the number of candidates
     */
    int nCandidates();

    /**
     * Copies the admissible next symbols (in an arbitrary order) into an array.
     *
     * @param dest an array large enough, i.e. {@code dest.length >= nCandidates()}
     * @return the number of candidates
     */
    int fillCandidates(int[] dest);

    /**
     * Returns whether the pattern may still terminate at the current prefix,
     * i.e. the status is not {@link #FORBIDDEN}.
     *
     * @return true if the status is not {@link #FORBIDDEN}
     */
    boolean canTerminate();

    /**
     * Returns whether the pattern is terminated, i.e. equal to the current prefix.
     *
     * @return true if the status is {@link #TERMINATED}
     */
    boolean isTerminated();

    /**
     * Returns whether the variable is fixed, which is equivalent to being terminated.
     *
     * @return true if the variable is terminated
     */
    boolean isFixed();

    /**
     * Returns the support of the committed prefix, that is the number of
     * database sequences containing the prefix as a subsequence.
     * For the empty prefix this is the number of sequences of the database.
     *
     * @return the support of the committed prefix
     */
    int support();

    /**
     * Returns the projected support of the prefix extended with a symbol,
     * that is the number of database sequences containing prefix·a as a subsequence.
     *
     * @param a a symbol
     * @return the support of prefix·a
     */
    int supportAfter(int a);

    /**
     * Returns the support that the pattern would have if it were terminated at
     * the current prefix, under the complete occurrence policy of the variable.
     * <p>
     * {@link #support()} only accounts for the anti-monotone part of the
     * occurrence policy (a valid occurrence remains valid for every prefix of
     * the pattern), which makes it sound for support-based pruning of the
     * candidate frontier. Policies with non-anti-monotone conditions, such as
     * the minimum span of a time-constrained variable, additionally restrict
     * which occurrences count for the final pattern: this method counts the
     * sequences with at least one occurrence satisfying the complete policy,
     * so {@code completeSupport() <= support()}. For a variable whose policy
     * is entirely anti-monotone (e.g. the plain subsequence policy of
     * {@link CPSeqPatternVarImpl}) it is equal to {@link #support()}.
     * <p>
     * A pattern value must occur at least once under the complete policy:
     * {@link #terminate()} fails when this support is 0.
     *
     * @return the support of the current prefix under the complete policy
     */
    int completeSupport();

    /**
     * Appends a symbol to the committed prefix.
     * The symbol must be a candidate, otherwise an {@link org.maxicp.util.exception.InconsistencyException}
     * is thrown. The pseudo-projected database is extended and the candidate frontier
     * becomes the set of symbols with a positive support under the new prefix.
     * If this new frontier is empty the variable auto-terminates (or fails if
     * termination is forbidden).
     *
     * @param a the symbol to append
     */
    void append(int a);

    /**
     * Removes a symbol from the candidate frontier.
     * No-op if the symbol is not a candidate (in particular if the variable is terminated).
     * If the frontier becomes empty the variable auto-terminates (or fails if
     * termination is forbidden).
     *
     * @param a the symbol to remove from the frontier
     */
    void excludeNext(int a);

    /**
     * Terminates the pattern: the pattern is exactly the current prefix.
     * The candidate frontier is emptied.
     * Throws an {@link org.maxicp.util.exception.InconsistencyException} if the
     * status is {@link #FORBIDDEN}.
     */
    void terminate();

    /**
     * Forbids the pattern to terminate at the current prefix: the pattern must be
     * a strict extension of it. The restriction only applies to the current prefix:
     * it is reset by {@link #append(int)} and restored on backtrack.
     * Throws an {@link org.maxicp.util.exception.InconsistencyException} if the
     * variable is already terminated or if the candidate frontier is empty.
     */
    void forbidTerminate();

    /**
     * Asks that the {@link CPConstraint#propagate()} method of the constraint c
     * is called whenever the domain of the variable changes
     * (append, exclusion of a candidate or status change).
     *
     * @param c the constraint to notify
     */
    void propagateOnDomainChange(CPConstraint c);

    /**
     * Asks that the {@link CPConstraint#propagate()} method of the constraint c
     * is called when the variable becomes terminated.
     *
     * @param c the constraint to notify
     */
    void propagateOnFix(CPConstraint c);

    /**
     * Returns the solver of the variable.
     *
     * @return the solver of the variable
     */
    CPSolver getSolver();

    @Override
    ModelProxy getModelProxy();
}
