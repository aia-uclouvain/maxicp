"""
maxicp.model  –  Pythonic entry point for building and solving CP models.

Example
-------
>>> from maxicp.model import Model
>>> with Model() as m:
...     x = m.int_var(0, 9)
...     y = m.int_var(0, 9)
...     m.add(x != y)
...     result = m.solve(strategy="first_fail", max_solutions=10)
...     for sol in result:
...         print(sol[x], sol[y])
"""

from __future__ import annotations

from typing import Sequence, List, Optional, Union

from .variables import IntExpression, IntVar, BoolVar, BoolExpression
from .constraints import _ConstraintHandle
from .search import (
    SolveResult, _build_result,
    FIRST_FAIL_BINARY, CONFLICT_ORDERING, STATIC_ORDER, _STRATEGY_MAP,
)


class Model:
    """
    A MaxiCP constraint-programming model.

    Supports the context-manager protocol:

        with Model() as m:
            x = m.int_var(0, 5)
            ...

    The native model is freed when the ``with`` block exits.
    """

    def __init__(self):
        from . import _lib as _L
        self._mh: int = _L.lib.maxicp_model_create(_L.thread)
        if self._mh == 0:
            raise RuntimeError("maxicp_model_create returned 0 – model creation failed.")
        self._closed = False

    # --- Context manager ----------------------------------------------------

    def __enter__(self) -> "Model":
        return self

    def __exit__(self, *_) -> None:
        self.close()

    def close(self) -> None:
        """Release the native model."""
        if not self._closed:
            from . import _lib as _L
            _L.lib.maxicp_model_free(_L.thread, self._mh)
            self._closed = True

    # --- Variable creation --------------------------------------------------

    def int_var(self, min: int, max: int) -> IntVar:
        """Create an integer variable with domain [min, max]."""
        from . import _lib as _L
        h = _L.lib.maxicp_intvar_create(_L.thread, self._mh, min, max)
        if h == 0:
            raise RuntimeError("int_var creation failed.")
        return IntVar(h, self)

    def int_var_array(self, n: int, min: int, max: int) -> List[IntVar]:
        """Create a list of *n* integer variables each with domain [min, max]."""
        from . import _lib as _L
        out = _L.ffi.new(f"long[{n}]")
        rc = _L.lib.maxicp_intvar_create_array_range(_L.thread, self._mh, n, min, max, out)
        if rc < 0:
            raise RuntimeError("int_var_array creation failed.")
        return [IntVar(out[i], self) for i in range(n)]

    def int_var_array_dom(self, n: int, dom_size: int) -> List[IntVar]:
        """Create a list of *n* integer variables each with domain [0, dom_size-1]."""
        from . import _lib as _L
        out = _L.ffi.new(f"long[{n}]")
        rc = _L.lib.maxicp_intvar_create_array(_L.thread, self._mh, n, dom_size, out)
        if rc < 0:
            raise RuntimeError("int_var_array_dom creation failed.")
        return [IntVar(out[i], self) for i in range(n)]

    def bool_var(self) -> BoolVar:
        """Create a Boolean variable (domain {0, 1})."""
        from . import _lib as _L
        h = _L.lib.maxicp_boolvar_create(_L.thread, self._mh)
        if h == 0:
            raise RuntimeError("bool_var creation failed.")
        return BoolVar(h, self)

    def constant(self, value: int) -> IntExpression:
        """Wrap an integer constant as an IntExpression."""
        from . import _lib as _L
        h = _L.lib.maxicp_constant(_L.thread, self._mh, value)
        return IntExpression(h, self)

    # --- Constraints --------------------------------------------------------

    def add(self, constraint: Union[_ConstraintHandle, BoolExpression]) -> None:
        """
        Post a constraint to the model.

        Arguments can be:
        - A constraint returned by allDifferent(), table(), etc.
        - A BoolExpression produced by comparison operators (x == 3, x != y, …)
        """
        from . import _lib as _L
        if isinstance(constraint, _ConstraintHandle):
            _L.lib.maxicp_model_add(_L.thread, self._mh, constraint.handle)
        elif isinstance(constraint, BoolExpression):
            _L.lib.maxicp_model_add(_L.thread, self._mh, constraint.handle)
        elif isinstance(constraint, IntExpression):
            # treat any IntExpression as a BoolExpression handle
            _L.lib.maxicp_model_add(_L.thread, self._mh, constraint.handle)
        else:
            raise TypeError(f"Cannot add {type(constraint)} to model.")

    # --- Objectives ---------------------------------------------------------

    def minimize(self, expr: IntExpression) -> int:
        """Create a minimisation objective; returns native objective handle."""
        from . import _lib as _L
        oh = _L.lib.maxicp_minimize(_L.thread, expr.handle)
        if oh == 0:
            raise RuntimeError("minimize() failed.")
        return oh

    def maximize(self, expr: IntExpression) -> int:
        """Create a maximisation objective; returns native objective handle."""
        from . import _lib as _L
        oh = _L.lib.maxicp_maximize(_L.thread, expr.handle)
        if oh == 0:
            raise RuntimeError("maximize() failed.")
        return oh

    # --- Solve --------------------------------------------------------------

    def solve(
        self,
        decision_vars: Optional[List[IntExpression]] = None,
        strategy: Union[str, int] = "first_fail",
        max_solutions: int = 0,
    ) -> SolveResult:
        """
        Find solutions (satisfaction).

        Args:
            decision_vars: Variables to branch on.  If None, no solutions
                           can be retrieved (branching still runs but vals
                           are not stored). Pass the full list of decision
                           variables to retrieve their values later.
            strategy: Branching strategy – one of ``"first_fail"`` (default),
                      ``"conflict_ordering"``, ``"static_order"``, or the
                      integer constants FIRST_FAIL_BINARY, CONFLICT_ORDERING,
                      STATIC_ORDER.
            max_solutions: Stop after this many solutions (0 = find all).

        Returns:
            A :class:`SolveResult` with all recorded solutions.
        """
        from . import _lib as _L
        dvars = decision_vars or []
        strat = _STRATEGY_MAP.get(strategy, strategy) if isinstance(strategy, str) else strategy
        arr = _L.mk_long_array([v.handle for v in dvars])
        sh = _L.lib.maxicp_search_create(_L.thread, arr, len(dvars), strat)
        try:
            rc = _L.lib.maxicp_solve(_L.thread, self._mh, sh, max_solutions)
            if rc < 0:
                raise RuntimeError("maxicp_solve returned -1 (internal error).")
            return _build_result(sh, dvars)
        finally:
            _L.lib.maxicp_search_free(_L.thread, sh)

    def optimize(
        self,
        objective_handle: int,
        decision_vars: Optional[List[IntExpression]] = None,
        strategy: Union[str, int] = "first_fail",
    ) -> SolveResult:
        """
        Branch-and-bound optimisation.

        Args:
            objective_handle: Handle returned by :meth:`minimize` or
                              :meth:`maximize`.
            decision_vars: Variables whose values are recorded per solution.
            strategy: Branching strategy (same options as :meth:`solve`).

        Returns:
            A :class:`SolveResult` where ``result.optimal`` is the best
            solution found (last entry in the solution list).
        """
        from . import _lib as _L
        dvars = decision_vars or []
        strat = _STRATEGY_MAP.get(strategy, strategy) if isinstance(strategy, str) else strategy
        arr = _L.mk_long_array([v.handle for v in dvars])
        sh = _L.lib.maxicp_search_create(_L.thread, arr, len(dvars), strat)
        try:
            rc = _L.lib.maxicp_solve_optimize(_L.thread, self._mh, sh, objective_handle)
            if rc < 0:
                raise RuntimeError("maxicp_solve_optimize returned -1.")
            return _build_result(sh, dvars)
        finally:
            _L.lib.maxicp_search_free(_L.thread, sh)

