/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.state.StateInt;
import org.maxicp.util.exception.InconsistencyException;

/**
 * Global lexicographic ordering constraint between two vectors x and y:
 * enforces {@code x <=_lex y} (when {@code strict == false}) or {@code x <_lex y} (when {@code strict == true}).
 * Achieves Generalized Arc Consistency (GAC) based on Frisch et al. (CP 2002).
 */
public class LexLe extends AbstractCPConstraint {

    private final CPIntVar[] x;
    private final CPIntVar[] y;
    private final boolean strict;
    private final int n;
    private final StateInt alpha;

    /**
     * Creates a lexicographic ordering constraint between two arrays of variables {@code x} and {@code y}.
     *
     * @param x      the first array of variables
     * @param y      the second array of variables (must have the same length as {@code x})
     * @param strict if {@code true}, enforces {@code x <_lex y}; otherwise enforces {@code x <=_lex y}
     */
    public LexLe(CPIntVar[] x, CPIntVar[] y, boolean strict) {
        super(x[0].getSolver());
        if (x.length != y.length) {
            throw new IllegalArgumentException("LexLe requires arrays of equal length");
        }
        this.x = x;
        this.y = y;
        this.strict = strict;
        this.n = x.length;
        this.alpha = getSolver().getStateManager().makeStateInt(0);
    }

    /**
     * Creates a non-strict lexicographic ordering constraint {@code x <=_lex y}.
     *
     * @param x the first array of variables
     * @param y the second array of variables (must have the same length as {@code x})
     */
    public LexLe(CPIntVar[] x, CPIntVar[] y) {
        this(x, y, false);
    }

    @Override
    public void post() {
        for (int i = 0; i < n; i++) {
            x[i].propagateOnBoundChange(this);
            y[i].propagateOnBoundChange(this);
        }
        propagate();
    }

    @Override
    public void propagate() {
        if (!isActive()) return;

        int a = alpha.value();
        while (true) {
            while (a < n) {
                if (x[a] == y[a] || (x[a].isFixed() && y[a].isFixed() && x[a].min() == y[a].min())) {
                    a++;
                } else {
                    break;
                }
            }
            alpha.setValue(a);

            if (a == n) {
                if (strict) {
                    throw InconsistencyException.INCONSISTENCY;
                }
                setActive(false);
                return;
            }

            if (x[a].max() < y[a].min()) {
                setActive(false);
                return;
            }

            // Determine whether the suffix (a + 1 .. n - 1) can be satisfied if x[a] == y[a]
            int k = a + 1;
            while (k < n && x[k].min() == y[k].max()) {
                k++;
            }
            boolean suffixViolated = (k == n) ? strict : (x[k].min() > y[k].max());

            if (suffixViolated) {
                // Suffix cannot be satisfied when x[a] == y[a], so enforce x[a] < y[a]
                x[a].removeAbove(y[a].max() - 1);
                y[a].removeBelow(x[a].min() + 1);
                if (x[a].max() < y[a].min()) {
                    setActive(false);
                }
                return;
            } else {
                // Suffix is satisfiable, so enforce x[a] <= y[a]
                x[a].removeAbove(y[a].max());
                y[a].removeBelow(x[a].min());
                if (x[a].max() < y[a].min()) {
                    setActive(false);
                    return;
                }
                if (x[a].isFixed() && y[a].isFixed() && x[a].min() == y[a].min()) {
                    a++;
                    alpha.setValue(a);
                } else {
                    return;
                }
            }
        }
    }
}

