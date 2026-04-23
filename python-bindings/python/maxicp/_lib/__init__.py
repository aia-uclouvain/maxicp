"""
maxicp._lib  –  cffi ABI-mode loader for libmaxicp.

At import time this module:
  1. Locates libmaxicp.{dylib,so} bundled in maxicp/_lib/
  2. Declares all C function signatures via cffi
  3. Opens the library with ffi.dlopen()
  4. Exposes `ffi`, `lib`, and a ready-to-use `thread` (isolate thread)

The calling code never needs to touch the isolate directly – just use
`lib.*` functions and pass `_lib.thread` as the first argument.
"""

from __future__ import annotations

import ctypes
import os
import sys
from pathlib import Path

import cffi  # pip install cffi>=1.15

# ---------------------------------------------------------------------------
# Locate the shared library
# ---------------------------------------------------------------------------
_HERE = Path(__file__).parent

def _find_lib() -> str:
    candidates = [
        _HERE / "libmaxicp.dylib",
        _HERE / "libmaxicp.so",
        _HERE / "libmaxicp.dll",
    ]
    for p in candidates:
        if p.exists():
            return str(p)
    # Fallback: search LD_LIBRARY_PATH / DYLD_LIBRARY_PATH
    for name in ("libmaxicp.dylib", "libmaxicp.so", "libmaxicp.dll"):
        try:
            found = ctypes.util.find_library(name)
            if found:
                return found
        except Exception:
            pass
    raise OSError(
        "libmaxicp not found. "
        "Run 'build.sh' from python-bindings/ to compile the native library "
        "and place it in maxicp/_lib/."
    )

# ---------------------------------------------------------------------------
# C declarations (must match maxicp.h exactly)
# ---------------------------------------------------------------------------
_CDEF = """
/* GraalVM isolate types */
typedef void* graal_isolate_t;
typedef void* graal_isolatethread_t;

int graal_create_isolate(void* params,
                         graal_isolate_t** isolate,
                         graal_isolatethread_t** thread);
int graal_attach_thread(graal_isolate_t* isolate,
                        graal_isolatethread_t** thread);
int graal_detach_thread(graal_isolatethread_t* thread);
int graal_tear_down_isolate(graal_isolatethread_t* initThread);

/* ------------------------------------------------------------------
 * Model lifecycle
 * ------------------------------------------------------------------ */
long maxicp_model_create(graal_isolatethread_t* thread);
void maxicp_model_free(graal_isolatethread_t* thread, long mh);

/* ------------------------------------------------------------------
 * Variable creation
 * ------------------------------------------------------------------ */
long maxicp_intvar_create(graal_isolatethread_t* thread, long mh,
                          int min, int max);
int  maxicp_intvar_create_array(graal_isolatethread_t* thread, long mh,
                                int n, int domSize, long* out);
int  maxicp_intvar_create_array_range(graal_isolatethread_t* thread, long mh,
                                      int n, int lo, int hi, long* out);
long maxicp_boolvar_create(graal_isolatethread_t* thread, long mh);
long maxicp_constant(graal_isolatethread_t* thread, long mh, int v);

/* ------------------------------------------------------------------
 * Arithmetic expressions
 * ------------------------------------------------------------------ */
long maxicp_expr_plus(graal_isolatethread_t* thread, long a, long b);
long maxicp_expr_plus_cst(graal_isolatethread_t* thread, long a, int b);
long maxicp_expr_minus(graal_isolatethread_t* thread, long a, long b);
long maxicp_expr_minus_cst(graal_isolatethread_t* thread, long a, int b);
long maxicp_expr_negate(graal_isolatethread_t* thread, long a);
long maxicp_expr_mul_cst(graal_isolatethread_t* thread, long a, int b);
long maxicp_expr_abs(graal_isolatethread_t* thread, long a);
long maxicp_expr_sum(graal_isolatethread_t* thread, long* hdls, int n);
long maxicp_expr_element1d(graal_isolatethread_t* thread,
                           int* tbl, int n, long yh);
long maxicp_expr_element2d(graal_isolatethread_t* thread,
                           int* tbl, int rows, int cols, long xh, long yh);

/* ------------------------------------------------------------------
 * Boolean / comparison expressions
 * ------------------------------------------------------------------ */
long maxicp_expr_eq(graal_isolatethread_t* thread, long a, long b);
long maxicp_expr_eq_cst(graal_isolatethread_t* thread, long a, int b);
long maxicp_expr_neq(graal_isolatethread_t* thread, long a, long b);
long maxicp_expr_neq_cst(graal_isolatethread_t* thread, long a, int b);
long maxicp_expr_le(graal_isolatethread_t* thread, long a, long b);
long maxicp_expr_le_cst(graal_isolatethread_t* thread, long a, int b);
long maxicp_expr_lt_cst(graal_isolatethread_t* thread, long a, int b);
long maxicp_expr_ge_cst(graal_isolatethread_t* thread, long a, int b);
long maxicp_expr_gt_cst(graal_isolatethread_t* thread, long a, int b);

/* ------------------------------------------------------------------
 * Expression domain accessors
 * ------------------------------------------------------------------ */
int maxicp_expr_min(graal_isolatethread_t* thread, long eh);
int maxicp_expr_max(graal_isolatethread_t* thread, long eh);

/* ------------------------------------------------------------------
 * Constraints
 * ------------------------------------------------------------------ */
void maxicp_model_add(graal_isolatethread_t* thread, long mh, long ch);
long maxicp_cstr_allDifferent(graal_isolatethread_t* thread,
                              long* hdls, int n);
long maxicp_cstr_circuit(graal_isolatethread_t* thread,
                         long* hdls, int n);
long maxicp_cstr_table(graal_isolatethread_t* thread,
                       long* varHdls, int nv,
                       int* tupPtr, int nt);
long maxicp_cstr_cumulative(graal_isolatethread_t* thread,
                            long* hdls, int n,
                            int* dur, int* dem, int cap);
long maxicp_cstr_disjunctive(graal_isolatethread_t* thread,
                             long* hdls, int n, int* dur);

/* ------------------------------------------------------------------
 * Objectives
 * ------------------------------------------------------------------ */
long maxicp_minimize(graal_isolatethread_t* thread, long eh);
long maxicp_maximize(graal_isolatethread_t* thread, long eh);

/* ------------------------------------------------------------------
 * Search
 * ------------------------------------------------------------------ */
long maxicp_search_create(graal_isolatethread_t* thread,
                          long* hdls, int n, int strategy);
void maxicp_search_free(graal_isolatethread_t* thread, long sh);
int  maxicp_solve(graal_isolatethread_t* thread, long mh, long sh, int maxSol);
int  maxicp_solve_optimize(graal_isolatethread_t* thread,
                           long mh, long sh, long oh);

/* ------------------------------------------------------------------
 * Solution access
 * ------------------------------------------------------------------ */
int  maxicp_solution_count(graal_isolatethread_t* thread, long sh);
int  maxicp_solution_get_int(graal_isolatethread_t* thread,
                             long sh, int si, long vh);

/* ------------------------------------------------------------------
 * Statistics
 * ------------------------------------------------------------------ */
long maxicp_stats_solutions(graal_isolatethread_t* thread, long sh);
long maxicp_stats_failures(graal_isolatethread_t* thread, long sh);
long maxicp_stats_nodes(graal_isolatethread_t* thread, long sh);
"""

# ---------------------------------------------------------------------------
# Initialise cffi and open the library
# ---------------------------------------------------------------------------
ffi = cffi.FFI()
ffi.cdef(_CDEF)

_lib_path = _find_lib()
lib = ffi.dlopen(_lib_path)

# Create a GraalVM isolate and keep a module-level thread handle.
# All callers should use this thread unless they manage their own isolates.
_isolate_ptr = ffi.new("graal_isolate_t **")
_thread_ptr = ffi.new("graal_isolatethread_t **")
_rc = lib.graal_create_isolate(ffi.NULL, _isolate_ptr, _thread_ptr)
if _rc != 0:
    raise RuntimeError(
        f"graal_create_isolate failed with code {_rc}. "
        "Make sure libmaxicp was compiled correctly."
    )

isolate: object = _isolate_ptr[0]   # graal_isolate_t*
thread: object = _thread_ptr[0]     # graal_isolatethread_t* – pass to all lib calls

import atexit as _atexit

@_atexit.register
def _teardown():
    try:
        lib.graal_tear_down_isolate(thread)
    except Exception:
        pass


def mk_long_array(values):
    """Helper: convert a Python list of ints to a cffi long[]."""
    arr = ffi.new(f"long[{len(values)}]")
    for i, v in enumerate(values):
        arr[i] = v
    return arr


def mk_int_array(values):
    """Helper: convert a Python list of ints to a cffi int[]."""
    arr = ffi.new(f"int[{len(values)}]")
    for i, v in enumerate(values):
        arr[i] = v
    return arr

