"""
maxicp.variables  –  Python wrappers for IntVar, BoolVar, and IntExpression.

Variables and expressions are thin wrappers around an opaque long handle
returned by the native library. Arithmetic operators (__add__, __sub__, etc.)
build new expression handles rather than evaluating immediately.
"""

from __future__ import annotations
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from .model import Model


class IntExpression:
    """
    Base class for all integer expressions (variables and derived expressions).
    Supports operator overloading to build composite expressions.
    """

    def __init__(self, handle: int, model: "Model"):
        self._handle = handle
        self._model = model

    @property
    def handle(self) -> int:
        return self._handle

    # --- Arithmetic operators -----------------------------------------------

    def __add__(self, other) -> "IntExpression":
        from . import _lib as _L
        if isinstance(other, int):
            h = _L.lib.maxicp_expr_plus_cst(_L.thread, self._handle, other)
        elif isinstance(other, IntExpression):
            h = _L.lib.maxicp_expr_plus(_L.thread, self._handle, other._handle)
        else:
            return NotImplemented
        return IntExpression(h, self._model)

    def __radd__(self, other) -> "IntExpression":
        return self.__add__(other)

    def __sub__(self, other) -> "IntExpression":
        from . import _lib as _L
        if isinstance(other, int):
            h = _L.lib.maxicp_expr_minus_cst(_L.thread, self._handle, other)
        elif isinstance(other, IntExpression):
            h = _L.lib.maxicp_expr_minus(_L.thread, self._handle, other._handle)
        else:
            return NotImplemented
        return IntExpression(h, self._model)

    def __rsub__(self, other) -> "IntExpression":
        from . import _lib as _L
        # other - self  ==  (-self) + other
        neg = _L.lib.maxicp_expr_negate(_L.thread, self._handle)
        if isinstance(other, int):
            h = _L.lib.maxicp_expr_plus_cst(_L.thread, neg, other)
        elif isinstance(other, IntExpression):
            h = _L.lib.maxicp_expr_plus(_L.thread, neg, other._handle)
        else:
            return NotImplemented
        return IntExpression(h, self._model)

    def __neg__(self) -> "IntExpression":
        from . import _lib as _L
        h = _L.lib.maxicp_expr_negate(_L.thread, self._handle)
        return IntExpression(h, self._model)

    def __mul__(self, other) -> "IntExpression":
        from . import _lib as _L
        if isinstance(other, int):
            h = _L.lib.maxicp_expr_mul_cst(_L.thread, self._handle, other)
        else:
            return NotImplemented
        return IntExpression(h, self._model)

    def __rmul__(self, other) -> "IntExpression":
        return self.__mul__(other)

    def abs(self) -> "IntExpression":
        """Absolute value expression."""
        from . import _lib as _L
        h = _L.lib.maxicp_expr_abs(_L.thread, self._handle)
        return IntExpression(h, self._model)

    # --- Comparison operators (return BoolExpression handles) ---------------

    def __eq__(self, other) -> "BoolExpression":  # type: ignore[override]
        from . import _lib as _L
        if isinstance(other, int):
            h = _L.lib.maxicp_expr_eq_cst(_L.thread, self._handle, other)
        elif isinstance(other, IntExpression):
            h = _L.lib.maxicp_expr_eq(_L.thread, self._handle, other._handle)
        else:
            return NotImplemented
        return BoolExpression(h, self._model)

    def __ne__(self, other) -> "BoolExpression":  # type: ignore[override]
        from . import _lib as _L
        if isinstance(other, int):
            h = _L.lib.maxicp_expr_neq_cst(_L.thread, self._handle, other)
        elif isinstance(other, IntExpression):
            h = _L.lib.maxicp_expr_neq(_L.thread, self._handle, other._handle)
        else:
            return NotImplemented
        return BoolExpression(h, self._model)

    def __le__(self, other) -> "BoolExpression":
        from . import _lib as _L
        if isinstance(other, int):
            h = _L.lib.maxicp_expr_le_cst(_L.thread, self._handle, other)
        elif isinstance(other, IntExpression):
            h = _L.lib.maxicp_expr_le(_L.thread, self._handle, other._handle)
        else:
            return NotImplemented
        return BoolExpression(h, self._model)

    def __lt__(self, other) -> "BoolExpression":
        from . import _lib as _L
        if isinstance(other, int):
            h = _L.lib.maxicp_expr_lt_cst(_L.thread, self._handle, other)
        else:
            return NotImplemented
        return BoolExpression(h, self._model)

    def __ge__(self, other) -> "BoolExpression":
        from . import _lib as _L
        if isinstance(other, int):
            h = _L.lib.maxicp_expr_ge_cst(_L.thread, self._handle, other)
        else:
            return NotImplemented
        return BoolExpression(h, self._model)

    def __gt__(self, other) -> "BoolExpression":
        from . import _lib as _L
        if isinstance(other, int):
            h = _L.lib.maxicp_expr_gt_cst(_L.thread, self._handle, other)
        else:
            return NotImplemented
        return BoolExpression(h, self._model)

    # --- Domain info ---------------------------------------------------------

    @property
    def min(self) -> int:
        from . import _lib as _L
        return _L.lib.maxicp_expr_min(_L.thread, self._handle)

    @property
    def max(self) -> int:
        from . import _lib as _L
        return _L.lib.maxicp_expr_max(_L.thread, self._handle)

    def __repr__(self) -> str:
        return f"IntExpression(handle={self._handle})"


class IntVar(IntExpression):
    """An integer decision variable."""

    def __repr__(self) -> str:
        return f"IntVar(handle={self._handle})"


class BoolVar(IntVar):
    """A Boolean decision variable (domain {{0, 1}})."""

    def __repr__(self) -> str:
        return f"BoolVar(handle={self._handle})"


class BoolExpression(IntExpression):
    """
    A Boolean expression produced by a comparison (e.g. x == 3, x <= y).
    Can be passed directly to Model.add() to post the constraint.
    """

    def __repr__(self) -> str:
        return f"BoolExpression(handle={self._handle})"

