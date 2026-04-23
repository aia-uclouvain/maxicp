package org.maxicp.bindings;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.SearchStatistics;
import java.util.ArrayList;
import java.util.List;

public final class SolveContext {
    public static final int FIRST_FAIL_BINARY = 0;
    public static final int CONFLICT_ORDERING  = 1;
    public static final int STATIC_ORDER       = 2;

    public final long[]          varHandles;
    public final IntExpression[] variables;
    public final int             strategy;
    public final List<int[]>     solutions = new ArrayList<>();
    public volatile SearchStatistics lastStats;

    public SolveContext(long[] varHandles, IntExpression[] variables, int strategy) {
        this.varHandles = varHandles;
        this.variables  = variables;
        this.strategy   = strategy;
    }

    public int indexOf(long varHandle) {
        for (int i = 0; i < varHandles.length; i++) {
            if (varHandles[i] == varHandle) return i;
        }
        return -1;
    }
}
