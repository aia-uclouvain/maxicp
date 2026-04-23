/**
 * maxicp.h – C API for the MaxiCP constraint-programming solver.
 *
 * This header declares the full native interface exported by libmaxicp
 * (produced by GraalVM native-image --shared).
 *
 * Usage pattern
 * -------------
 *   #include "graal_isolate.h"   // generated alongside libmaxicp
 *   #include "maxicp.h"
 *
 *   graal_isolate_t       *isolate = NULL;
 *   graal_isolatethread_t *thread  = NULL;
 *   graal_create_isolate(NULL, &isolate, &thread);
 *
 *   long model = maxicp_model_create(thread);
 *   long x     = maxicp_intvar_create(thread, model, 0, 9);
 *   ...
 *   maxicp_model_free(thread, model);
 *   graal_tear_down_isolate(thread);
 *
 * All objects (models, variables, expressions, constraints, search
 * contexts, objectives) are referenced via opaque `long` handles.
 * Handle 0 indicates an error / null handle.
 */
#ifndef MAXICP_H
#define MAXICP_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>

/* ---------------------------------------------------------------
 * GraalVM isolate types (also declared in the generated
 * graal_isolate.h – include either one or both).
 * --------------------------------------------------------------- */
#ifndef GRAAL_ISOLATE_DEFINED
#define GRAAL_ISOLATE_DEFINED
typedef void* graal_isolate_t;
typedef void* graal_isolatethread_t;

int graal_create_isolate(void* params,
                         graal_isolate_t** isolate,
                         graal_isolatethread_t** thread);
int graal_attach_thread(graal_isolate_t* isolate,
                        graal_isolatethread_t** thread);
int graal_detach_thread(graal_isolatethread_t* thread);
int graal_tear_down_isolate(graal_isolatethread_t* initThread);
#endif /* GRAAL_ISOLATE_DEFINED */

/* Convenience alias */
typedef graal_isolatethread_t* maxicp_thread_t;

/* ---------------------------------------------------------------
 * Search strategy constants (mirrors SolveContext.java)
 * --------------------------------------------------------------- */
#define MAXICP_FIRST_FAIL_BINARY  0
#define MAXICP_CONFLICT_ORDERING  1
#define MAXICP_STATIC_ORDER       2

/* ---------------------------------------------------------------
 * Model lifecycle
 * --------------------------------------------------------------- */

/** Create a new MaxiCP model; returns model handle or 0 on error. */
long maxicp_model_create(graal_isolatethread_t* thread);

/** Close and free the model associated with handle mh. */
void maxicp_model_free(graal_isolatethread_t* thread, long mh);

/* ---------------------------------------------------------------
 * Variable creation
 * --------------------------------------------------------------- */

/** Create an integer variable with domain [min, max]. */
long maxicp_intvar_create(graal_isolatethread_t* thread, long mh,
                          int min, int max);

/**
 * Create n integer variables each with domain [0, domSize-1].
 * out must point to an array of at least n longs.
 * Returns n on success, -1 on error.
 */
int maxicp_intvar_create_array(graal_isolatethread_t* thread, long mh,
                               int n, int domSize, long* out);

/**
 * Create n integer variables each with domain [lo, hi].
 * out must point to an array of at least n longs.
 * Returns n on success, -1 on error.
 */
int maxicp_intvar_create_array_range(graal_isolatethread_t* thread, long mh,
                                     int n, int lo, int hi, long* out);

/** Create a Boolean variable (domain {0,1}). */
long maxicp_boolvar_create(graal_isolatethread_t* thread, long mh);

/** Wrap an integer constant as an IntExpression handle. */
long maxicp_constant(graal_isolatethread_t* thread, long mh, int v);

/* ---------------------------------------------------------------
 * Arithmetic expression builders
 * All return a new expression handle (0 on error).
 * --------------------------------------------------------------- */

long maxicp_expr_plus(graal_isolatethread_t* thread, long a, long b);
long maxicp_expr_plus_cst(graal_isolatethread_t* thread, long a, int b);
long maxicp_expr_minus(graal_isolatethread_t* thread, long a, long b);
long maxicp_expr_minus_cst(graal_isolatethread_t* thread, long a, int b);
long maxicp_expr_negate(graal_isolatethread_t* thread, long a);
long maxicp_expr_mul_cst(graal_isolatethread_t* thread, long a, int b);
long maxicp_expr_abs(graal_isolatethread_t* thread, long a);

/**
 * Sum of n expressions given as an array of handles.
 */
long maxicp_expr_sum(graal_isolatethread_t* thread,
                     const long* hdls, int n);

/**
 * 1-D element: returns tbl[yh] where tbl is a C int array of length n.
 */
long maxicp_expr_element1d(graal_isolatethread_t* thread,
                           const int* tbl, int n, long yh);

/**
 * 2-D element: returns tbl[xh][yh].
 * tbl is a flat row-major array of rows*cols ints.
 */
long maxicp_expr_element2d(graal_isolatethread_t* thread,
                           const int* tbl, int rows, int cols,
                           long xh, long yh);

/* ---------------------------------------------------------------
 * Boolean / comparison expressions
 * --------------------------------------------------------------- */

long maxicp_expr_eq(graal_isolatethread_t* thread, long a, long b);
long maxicp_expr_eq_cst(graal_isolatethread_t* thread, long a, int b);
long maxicp_expr_neq(graal_isolatethread_t* thread, long a, long b);
long maxicp_expr_neq_cst(graal_isolatethread_t* thread, long a, int b);
long maxicp_expr_le(graal_isolatethread_t* thread, long a, long b);
long maxicp_expr_le_cst(graal_isolatethread_t* thread, long a, int b);
long maxicp_expr_lt_cst(graal_isolatethread_t* thread, long a, int b);
long maxicp_expr_ge_cst(graal_isolatethread_t* thread, long a, int b);
long maxicp_expr_gt_cst(graal_isolatethread_t* thread, long a, int b);

/* ---------------------------------------------------------------
 * Expression domain accessors (useful before solve)
 * --------------------------------------------------------------- */

int maxicp_expr_min(graal_isolatethread_t* thread, long eh);
int maxicp_expr_max(graal_isolatethread_t* thread, long eh);

/* ---------------------------------------------------------------
 * Posting constraints to the model
 * --------------------------------------------------------------- */

/**
 * Post a constraint or Boolean expression (reified) to the model.
 * ch can be a constraint handle or a Boolean expression handle.
 */
void maxicp_model_add(graal_isolatethread_t* thread, long mh, long ch);

/**
 * AllDifferent constraint over n variables given by handles hdls[].
 * Returns constraint handle.
 */
long maxicp_cstr_allDifferent(graal_isolatethread_t* thread,
                              const long* hdls, int n);

/**
 * Circuit constraint (Hamiltonian circuit) over successor array hdls[].
 */
long maxicp_cstr_circuit(graal_isolatethread_t* thread,
                         const long* hdls, int n);

/**
 * Table constraint: nv variables in varHdls[], nt allowed tuples.
 * tupPtr is a flat row-major array of nt*nv values.
 */
long maxicp_cstr_table(graal_isolatethread_t* thread,
                       const long* varHdls, int nv,
                       const int* tupPtr, int nt);

/**
 * Cumulative scheduling constraint.
 * hdls[n]: start-time variable handles
 * dur[n]:  task durations (int)
 * dem[n]:  resource demands (int)
 * cap:     resource capacity
 */
long maxicp_cstr_cumulative(graal_isolatethread_t* thread,
                            const long* hdls, int n,
                            const int* dur, const int* dem, int cap);

/**
 * Disjunctive scheduling constraint.
 * hdls[n]: start-time variable handles
 * dur[n]:  task durations (int)
 */
long maxicp_cstr_disjunctive(graal_isolatethread_t* thread,
                             const long* hdls, int n,
                             const int* dur);

/* ---------------------------------------------------------------
 * Objectives
 * --------------------------------------------------------------- */

/** Create a minimisation objective on expression eh. */
long maxicp_minimize(graal_isolatethread_t* thread, long eh);

/** Create a maximisation objective on expression eh. */
long maxicp_maximize(graal_isolatethread_t* thread, long eh);

/* ---------------------------------------------------------------
 * Search context
 * --------------------------------------------------------------- */

/**
 * Create a search context over n decision variables (handles in hdls[]).
 * strategy: MAXICP_FIRST_FAIL_BINARY | MAXICP_CONFLICT_ORDERING | MAXICP_STATIC_ORDER
 */
long maxicp_search_create(graal_isolatethread_t* thread,
                          const long* hdls, int n, int strategy);

/** Free a search context. */
void maxicp_search_free(graal_isolatethread_t* thread, long sh);

/* ---------------------------------------------------------------
 * Solve (satisfaction)
 * --------------------------------------------------------------- */

/**
 * Run a depth-first search on model mh using search context sh.
 * maxSol > 0: stop after finding maxSol solutions; 0 = find all.
 * Returns number of solutions collected (accessible via solution APIs),
 * or -1 on internal error.
 */
int maxicp_solve(graal_isolatethread_t* thread,
                 long mh, long sh, int maxSol);

/* ---------------------------------------------------------------
 * Solve (optimisation)
 * --------------------------------------------------------------- */

/**
 * Branch-and-bound with objective oh on model mh using search context sh.
 * Each improving solution is recorded.
 * Returns number of improving solutions found, -1 on error.
 */
int maxicp_solve_optimize(graal_isolatethread_t* thread,
                          long mh, long sh, long oh);

/* ---------------------------------------------------------------
 * Solution access (after solve/solve_optimize)
 * --------------------------------------------------------------- */

/** Number of solutions recorded in search context sh. */
int maxicp_solution_count(graal_isolatethread_t* thread, long sh);

/**
 * Value of variable vh in solution si (0-indexed).
 * Returns INT_MIN on error.
 */
int maxicp_solution_get_int(graal_isolatethread_t* thread,
                            long sh, int si, long vh);

/* ---------------------------------------------------------------
 * Search statistics (after solve/solve_optimize)
 * --------------------------------------------------------------- */

long maxicp_stats_solutions(graal_isolatethread_t* thread, long sh);
long maxicp_stats_failures(graal_isolatethread_t* thread, long sh);
long maxicp_stats_nodes(graal_isolatethread_t* thread, long sh);

#ifdef __cplusplus
}
#endif

#endif /* MAXICP_H */

