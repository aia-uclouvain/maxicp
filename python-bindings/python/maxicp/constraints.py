"""
maxicp.constraints  –  Pythonic constraint constructors.

Each function returns an opaque handle (int) that can be passed to
Model.add(), or returns a BoolExpression that can also be passed to
Model.add() directly.
"""

from __future__ import annotations
from typing import Sequence, List

from .variables import IntExpression, IntVar, BoolExpression


# ---------------------------------------------------------------------------
# Internal helper
# ---------------------------------------------------------------------------

class _ConstraintHandle:
    """Thin wrapper around a native constraint handle."""
    def __init__(self, handle: int):
        self._handle = handle

    @property
    def handle(self) -> int:
        return self._handle

    def __repr__(self) -> str:
        return f"Constraint(handle={self._handle})"


# ---------------------------------------------------------------------------
# Global constraints
# ---------------------------------------------------------------------------

def allDifferent(variables: Sequence[IntExpression]) -> _ConstraintHandle:
    """All values in *variables* must be pairwise distinct."""
    from . import _lib as _L
    handles = [v._handle for v in variables]
    arr = _L.mk_long_array(handles)
    h = _L.lib.maxicp_cstr_allDifferent(_L.thread, arr, len(handles))
    return _ConstraintHandle(h)


def circuit(successors: Sequence[IntExpression]) -> _ConstraintHandle:
    """
    Circuit (Hamiltonian circuit) constraint on successor array.
    successors[i] = j means node i is followed by node j.
    """
    from . import _lib as _L
    handles = [v._handle for v in successors]
    arr = _L.mk_long_array(handles)
    h = _L.lib.maxicp_cstr_circuit(_L.thread, arr, len(handles))
    return _ConstraintHandle(h)


def table(variables: Sequence[IntExpression],
          tuples: Sequence[Sequence[int]]) -> _ConstraintHandle:
    """
    Table constraint: the assignment of *variables* must be one of the
    rows in *tuples*.
    """
    from . import _lib as _L
    nv = len(variables)
    nt = len(tuples)
    var_arr = _L.mk_long_array([v._handle for v in variables])
    flat = [tuples[i][j] for i in range(nt) for j in range(nv)]
    tup_arr = _L.mk_int_array(flat)
    h = _L.lib.maxicp_cstr_table(_L.thread, var_arr, nv, tup_arr, nt)
    return _ConstraintHandle(h)


def cumulative(starts: Sequence[IntExpression],
               durations: Sequence[int],
               demands: Sequence[int],
               capacity: int) -> _ConstraintHandle:
    """
    Cumulative scheduling constraint.

    Args:
        starts:    start-time variables for each task.
        durations: fixed duration of each task.
        demands:   resource demand of each task.
        capacity:  maximum resource capacity.
    """
    from . import _lib as _L
    n = len(starts)
    hdl_arr = _L.mk_long_array([s._handle for s in starts])
    dur_arr = _L.mk_int_array(list(durations))
    dem_arr = _L.mk_int_array(list(demands))
    h = _L.lib.maxicp_cstr_cumulative(
        _L.thread, hdl_arr, n, dur_arr, dem_arr, capacity
    )
    return _ConstraintHandle(h)


def disjunctive(starts: Sequence[IntExpression],
                durations: Sequence[int]) -> _ConstraintHandle:
    """
    Disjunctive scheduling constraint: tasks cannot overlap.

    Args:
        starts:    start-time variables for each task.
        durations: fixed duration of each task.
    """
    from . import _lib as _L
    n = len(starts)
    hdl_arr = _L.mk_long_array([s._handle for s in starts])
    dur_arr = _L.mk_int_array(list(durations))
    h = _L.lib.maxicp_cstr_disjunctive(_L.thread, hdl_arr, n, dur_arr)
    return _ConstraintHandle(h)

