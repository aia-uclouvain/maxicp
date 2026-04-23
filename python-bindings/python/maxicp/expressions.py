"""
maxicp.expressions  –  Functional expression builders.

These are module-level helpers that mirror the static factory methods in
org.maxicp.modeling.Factory.
"""

from __future__ import annotations
from typing import Sequence, List

from .variables import IntExpression, IntVar, BoolExpression


def _model_of(exprs):
    """Extract model reference from a collection of expressions."""
    for e in exprs:
        if isinstance(e, IntExpression):
            return e._model
    raise ValueError("No IntExpression found in the argument list.")


# ---------------------------------------------------------------------------
# Arithmetic
# ---------------------------------------------------------------------------

def sum_expr(exprs: Sequence[IntExpression]) -> IntExpression:
    """Sum of a list of IntExpressions."""
    from . import _lib as _L
    model = _model_of(exprs)
    handles = [e._handle for e in exprs]
    arr = _L.mk_long_array(handles)
    h = _L.lib.maxicp_expr_sum(_L.thread, arr, len(handles))
    return IntExpression(h, model)


def element1d(table: Sequence[int], index: IntExpression) -> IntExpression:
    """
    Returns the expression table[index] where table is a Python list of ints.
    """
    from . import _lib as _L
    arr = _L.mk_int_array(list(table))
    h = _L.lib.maxicp_expr_element1d(_L.thread, arr, len(table), index._handle)
    return IntExpression(h, index._model)


def element2d(table: Sequence[Sequence[int]],
              row: IntVar,
              col: IntVar) -> IntExpression:
    """
    Returns the expression table[row][col].
    table is a 2-D list of ints (rows × cols).
    """
    from . import _lib as _L
    rows = len(table)
    cols = len(table[0])
    flat = [table[r][c] for r in range(rows) for c in range(cols)]
    arr = _L.mk_int_array(flat)
    h = _L.lib.maxicp_expr_element2d(
        _L.thread, arr, rows, cols, row._handle, col._handle
    )
    return IntExpression(h, row._model)

