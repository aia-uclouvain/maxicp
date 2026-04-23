package org.maxicp.bindings;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.*;
import org.maxicp.modeling.algebra.bool.BoolExpression;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.*;

import java.util.Optional;
import java.util.function.Supplier;

import static org.maxicp.modeling.Factory.*;

/**
 * GraalVM native-image entry points forming the C API of MaxiCP.
 * All Java objects are stored in HandleRegistry and referenced by opaque long handles.
 */
public final class MaxiCPNativeAPI {
    private MaxiCPNativeAPI() {}

    // --- Model lifecycle ---

    @CEntryPoint(name = "maxicp_model_create")
    public static long modelCreate(IsolateThread t) {
        try { return HandleRegistry.put(makeModelDispatcher()); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_model_free")
    public static void modelFree(IsolateThread t, long h) {
        try {
            ModelDispatcher m = HandleRegistry.get(h);
            if (m != null) { try { m.close(); } catch (Exception ignored) {} HandleRegistry.remove(h); }
        } catch (Exception ignored) {}
    }

    // --- Variable creation ---

    @CEntryPoint(name = "maxicp_intvar_create")
    public static long intvarCreate(IsolateThread t, long mh, int min, int max) {
        try { return HandleRegistry.put(((ModelDispatcher)HandleRegistry.get(mh)).intVar(min, max)); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_intvar_create_array")
    public static int intvarCreateArray(IsolateThread t, long mh, int n, int domSize, CLongPointer out) {
        try {
            ModelDispatcher m = HandleRegistry.get(mh);
            IntVar[] vars = m.intVarArray(n, domSize);
            for (int i = 0; i < n; i++) out.write(i, HandleRegistry.put(vars[i]));
            return n;
        } catch (Exception e) { return -1; }
    }

    @CEntryPoint(name = "maxicp_intvar_create_array_range")
    public static int intvarCreateArrayRange(IsolateThread t, long mh, int n, int lo, int hi, CLongPointer out) {
        try {
            ModelDispatcher m = HandleRegistry.get(mh);
            for (int i = 0; i < n; i++) out.write(i, HandleRegistry.put(m.intVar(lo, hi)));
            return n;
        } catch (Exception e) { return -1; }
    }

    @CEntryPoint(name = "maxicp_boolvar_create")
    public static long boolvarCreate(IsolateThread t, long mh) {
        try { return HandleRegistry.put(((ModelDispatcher)HandleRegistry.get(mh)).boolVar()); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_constant")
    public static long constant(IsolateThread t, long mh, int v) {
        try { return HandleRegistry.put(((ModelDispatcher)HandleRegistry.get(mh)).constant(v)); }
        catch (Exception e) { return 0L; }
    }

    // --- Arithmetic expressions ---

    @CEntryPoint(name = "maxicp_expr_plus")
    public static long exprPlus(IsolateThread t, long a, long b) {
        try { return HandleRegistry.put(plus((IntExpression)HandleRegistry.get(a),(IntExpression)HandleRegistry.get(b))); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_plus_cst")
    public static long exprPlusCst(IsolateThread t, long a, int b) {
        try { return HandleRegistry.put(plus((IntExpression)HandleRegistry.get(a), b)); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_minus")
    public static long exprMinus(IsolateThread t, long a, long b) {
        try { return HandleRegistry.put(minus((IntExpression)HandleRegistry.get(a),(IntExpression)HandleRegistry.get(b))); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_minus_cst")
    public static long exprMinusCst(IsolateThread t, long a, int b) {
        try { return HandleRegistry.put(minus((IntExpression)HandleRegistry.get(a), b)); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_negate")
    public static long exprNegate(IsolateThread t, long a) {
        try { return HandleRegistry.put(minus((IntExpression)HandleRegistry.get(a))); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_mul_cst")
    public static long exprMulCst(IsolateThread t, long a, int b) {
        try { return HandleRegistry.put(mul((IntExpression)HandleRegistry.get(a), b)); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_abs")
    public static long exprAbs(IsolateThread t, long a) {
        try { return HandleRegistry.put(abs((IntExpression)HandleRegistry.get(a))); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_sum")
    public static long exprSum(IsolateThread t, CLongPointer hdls, int n) {
        try {
            IntExpression[] arr = new IntExpression[n];
            for (int i = 0; i < n; i++) arr[i] = HandleRegistry.get(hdls.read(i));
            return HandleRegistry.put(sum(arr));
        } catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_element1d")
    public static long exprElement1d(IsolateThread t, CIntPointer tbl, int n, long yh) {
        try {
            int[] T = new int[n]; for (int i = 0; i < n; i++) T[i] = tbl.read(i);
            return HandleRegistry.put(get(T, (IntExpression)HandleRegistry.get(yh)));
        } catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_element2d")
    public static long exprElement2d(IsolateThread t, CIntPointer tbl, int rows, int cols, long xh, long yh) {
        try {
            int[][] T = new int[rows][cols];
            for (int i = 0; i < rows; i++) for (int j = 0; j < cols; j++) T[i][j] = tbl.read(i*cols+j);
            return HandleRegistry.put(get(T, (IntVar)HandleRegistry.get(xh), (IntVar)HandleRegistry.get(yh)));
        } catch (Exception e) { return 0L; }
    }

    // --- Boolean/comparison expressions ---

    @CEntryPoint(name = "maxicp_expr_eq")
    public static long exprEq(IsolateThread t, long a, long b) {
        try { return HandleRegistry.put(eq((IntExpression)HandleRegistry.get(a),(IntExpression)HandleRegistry.get(b))); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_eq_cst")
    public static long exprEqCst(IsolateThread t, long a, int b) {
        try { return HandleRegistry.put(eq((IntExpression)HandleRegistry.get(a), b)); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_neq")
    public static long exprNeq(IsolateThread t, long a, long b) {
        try { return HandleRegistry.put(neq((IntExpression)HandleRegistry.get(a),(IntExpression)HandleRegistry.get(b))); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_neq_cst")
    public static long exprNeqCst(IsolateThread t, long a, int b) {
        try { return HandleRegistry.put(neq((IntExpression)HandleRegistry.get(a), b)); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_le")
    public static long exprLe(IsolateThread t, long a, long b) {
        try { return HandleRegistry.put(le((IntExpression)HandleRegistry.get(a),(IntExpression)HandleRegistry.get(b))); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_le_cst")
    public static long exprLeCst(IsolateThread t, long a, int b) {
        try { return HandleRegistry.put(le((IntExpression)HandleRegistry.get(a), b)); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_lt_cst")
    public static long exprLtCst(IsolateThread t, long a, int b) {
        try { return HandleRegistry.put(lt((IntExpression)HandleRegistry.get(a), b)); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_ge_cst")
    public static long exprGeCst(IsolateThread t, long a, int b) {
        try { return HandleRegistry.put(ge((IntExpression)HandleRegistry.get(a), b)); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_expr_gt_cst")
    public static long exprGtCst(IsolateThread t, long a, int b) {
        try { return HandleRegistry.put(gt((IntExpression)HandleRegistry.get(a), b)); }
        catch (Exception e) { return 0L; }
    }

    // --- Add constraints to model ---

    @CEntryPoint(name = "maxicp_model_add")
    public static void modelAdd(IsolateThread t, long mh, long ch) {
        try {
            ModelDispatcher m = HandleRegistry.get(mh);
            Object c = HandleRegistry.get(ch);
            if (c instanceof Constraint ct)       m.add(ct);
            else if (c instanceof BoolExpression be) m.add(be);
        } catch (Exception ignored) {}
    }

    @CEntryPoint(name = "maxicp_cstr_allDifferent")
    public static long cstrAllDifferent(IsolateThread t, CLongPointer hdls, int n) {
        try {
            IntExpression[] v = new IntExpression[n];
            for (int i = 0; i < n; i++) v[i] = HandleRegistry.get(hdls.read(i));
            return HandleRegistry.put(allDifferent(v));
        } catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_cstr_circuit")
    public static long cstrCircuit(IsolateThread t, CLongPointer hdls, int n) {
        try {
            IntExpression[] v = new IntExpression[n];
            for (int i = 0; i < n; i++) v[i] = HandleRegistry.get(hdls.read(i));
            return HandleRegistry.put(circuit(v));
        } catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_cstr_table")
    public static long cstrTable(IsolateThread t, CLongPointer varHdls, int nv, CIntPointer tupPtr, int nt) {
        try {
            IntExpression[] v = new IntExpression[nv];
            for (int i = 0; i < nv; i++) v[i] = HandleRegistry.get(varHdls.read(i));
            int[][] tuples = new int[nt][nv];
            for (int i = 0; i < nt; i++) for (int j = 0; j < nv; j++) tuples[i][j] = tupPtr.read(i*nv+j);
            return HandleRegistry.put(table(v, tuples, Optional.empty()));
        } catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_cstr_cumulative")
    public static long cstrCumulative(IsolateThread t, CLongPointer hdls, int n, CIntPointer dur, CIntPointer dem, int cap) {
        try {
            IntExpression[] s = new IntExpression[n]; int[] d = new int[n], dm = new int[n];
            for (int i = 0; i < n; i++) { s[i] = HandleRegistry.get(hdls.read(i)); d[i] = dur.read(i); dm[i] = dem.read(i); }
            return HandleRegistry.put(cumulative(s, d, dm, cap));
        } catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_cstr_disjunctive")
    public static long cstrDisjunctive(IsolateThread t, CLongPointer hdls, int n, CIntPointer dur) {
        try {
            IntExpression[] s = new IntExpression[n]; int[] d = new int[n];
            for (int i = 0; i < n; i++) { s[i] = HandleRegistry.get(hdls.read(i)); d[i] = dur.read(i); }
            return HandleRegistry.put(disjunctive(s, d));
        } catch (Exception e) { return 0L; }
    }

    // --- Objectives ---

    @CEntryPoint(name = "maxicp_minimize")
    public static long mkMinimize(IsolateThread t, long eh) {
        try { return HandleRegistry.put(minimize((IntExpression)HandleRegistry.get(eh), true)); }
        catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_maximize")
    public static long mkMaximize(IsolateThread t, long eh) {
        try { return HandleRegistry.put(maximize((IntExpression)HandleRegistry.get(eh), true)); }
        catch (Exception e) { return 0L; }
    }

    // --- Search ---

    @CEntryPoint(name = "maxicp_search_create")
    public static long searchCreate(IsolateThread t, CLongPointer hdls, int n, int strategy) {
        try {
            long[] handles = new long[n]; IntExpression[] vars = new IntExpression[n];
            for (int i = 0; i < n; i++) { handles[i] = hdls.read(i); vars[i] = HandleRegistry.get(handles[i]); }
            return HandleRegistry.put(new SolveContext(handles, vars, strategy));
        } catch (Exception e) { return 0L; }
    }

    @CEntryPoint(name = "maxicp_search_free")
    public static void searchFree(IsolateThread t, long sh) { HandleRegistry.remove(sh); }

    @CEntryPoint(name = "maxicp_solve")
    public static int solve(IsolateThread t, long mh, long sh, int maxSol) {
        try {
            ModelDispatcher model = HandleRegistry.get(mh);
            SolveContext ctx = HandleRegistry.get(sh);
            ctx.solutions.clear();
            model.runCP(() -> {
                DFSearch dfs = model.dfSearch(buildBranching(ctx));
                dfs.onSolution(() -> ctx.solutions.add(snap(ctx.variables)));
                ctx.lastStats = (maxSol > 0)
                    ? dfs.solve(s -> s.numberOfSolutions() >= maxSol)
                    : dfs.solve();
            });
            return ctx.solutions.size();
        } catch (Exception e) { return -1; }
    }

    @CEntryPoint(name = "maxicp_solve_optimize")
    public static int solveOptimize(IsolateThread t, long mh, long sh, long oh) {
        try {
            ModelDispatcher model = HandleRegistry.get(mh);
            SolveContext ctx = HandleRegistry.get(sh);
            Objective obj = HandleRegistry.get(oh);
            ctx.solutions.clear();
            model.runCP(() -> {
                DFSearch dfs = model.dfSearch(buildBranching(ctx));
                dfs.onSolution(() -> ctx.solutions.add(snap(ctx.variables)));
                ctx.lastStats = dfs.optimize(obj);
            });
            return ctx.solutions.size();
        } catch (Exception e) { return -1; }
    }

    // --- Solution access ---

    @CEntryPoint(name = "maxicp_solution_count")
    public static int solutionCount(IsolateThread t, long sh) {
        try { return ((SolveContext)HandleRegistry.get(sh)).solutions.size(); }
        catch (Exception e) { return 0; }
    }

    @CEntryPoint(name = "maxicp_solution_get_int")
    public static int solutionGetInt(IsolateThread t, long sh, int si, long vh) {
        try {
            SolveContext ctx = HandleRegistry.get(sh);
            int vi = ctx.indexOf(vh);
            return (vi < 0 || si < 0 || si >= ctx.solutions.size()) ? Integer.MIN_VALUE : ctx.solutions.get(si)[vi];
        } catch (Exception e) { return Integer.MIN_VALUE; }
    }

    // --- Statistics ---

    @CEntryPoint(name = "maxicp_stats_solutions")
    public static long statsSolutions(IsolateThread t, long sh) {
        try { SolveContext c = HandleRegistry.get(sh); return c.lastStats == null ? 0 : c.lastStats.numberOfSolutions(); }
        catch (Exception e) { return 0; }
    }

    @CEntryPoint(name = "maxicp_stats_failures")
    public static long statsFailures(IsolateThread t, long sh) {
        try { SolveContext c = HandleRegistry.get(sh); return c.lastStats == null ? 0 : c.lastStats.numberOfFailures(); }
        catch (Exception e) { return 0; }
    }

    @CEntryPoint(name = "maxicp_stats_nodes")
    public static long statsNodes(IsolateThread t, long sh) {
        try { SolveContext c = HandleRegistry.get(sh); return c.lastStats == null ? 0 : c.lastStats.numberOfNodes(); }
        catch (Exception e) { return 0; }
    }

    // --- Expression domain accessors ---

    @CEntryPoint(name = "maxicp_expr_min")
    public static int exprMin(IsolateThread t, long eh) {
        try { return ((IntExpression)HandleRegistry.get(eh)).min(); }
        catch (Exception e) { return Integer.MIN_VALUE; }
    }

    @CEntryPoint(name = "maxicp_expr_max")
    public static int exprMax(IsolateThread t, long eh) {
        try { return ((IntExpression)HandleRegistry.get(eh)).max(); }
        catch (Exception e) { return Integer.MAX_VALUE; }
    }

    // --- Private helpers ---

    private static Supplier<Runnable[]> buildBranching(SolveContext ctx) {
        return switch (ctx.strategy) {
            case SolveContext.CONFLICT_ORDERING -> Searches.conflictOrderingSearch(
                    Searches.minDomVariableSelector(ctx.variables), v -> v.min());
            case SolveContext.STATIC_ORDER -> Searches.staticOrderBinary(v -> v.min(), ctx.variables);
            default -> Searches.firstFailBinary(ctx.variables);
        };
    }

    private static int[] snap(IntExpression[] vars) {
        int[] s = new int[vars.length];
        for (int i = 0; i < vars.length; i++) s[i] = vars[i].min();
        return s;
    }
}
