"""
maxicp.search  –  Search configuration and solution objects.
"""

from __future__ import annotations
from dataclasses import dataclass, field
from typing import Sequence, Iterator, List

from .variables import IntExpression

# Search strategy constants (mirror SolveContext.java)
FIRST_FAIL_BINARY = 0
CONFLICT_ORDERING = 1
STATIC_ORDER      = 2

_STRATEGY_MAP = {
    "first_fail":        FIRST_FAIL_BINARY,
    "first_fail_binary": FIRST_FAIL_BINARY,
    "conflict_ordering": CONFLICT_ORDERING,
    "static_order":      STATIC_ORDER,
}


@dataclass
class SearchStats:
    """Statistics collected after a solve call."""
    solutions: int = 0
    failures:  int = 0
    nodes:     int = 0

    def __repr__(self) -> str:
        return (f"SearchStats(solutions={self.solutions}, "
                f"failures={self.failures}, nodes={self.nodes})")


class Solution:
    """
    A snapshot of variable values for one solution.
    Access values with solution[variable] or solution.value(variable).
    """

    def __init__(self, values: dict):
        # values: { variable_handle (int): int_value }
        self._values = values

    def value(self, var: IntExpression) -> int:
        """Return the value of *var* in this solution."""
        return self._values[var.handle]

    def __getitem__(self, var: IntExpression) -> int:
        return self.value(var)

    def __repr__(self) -> str:
        return f"Solution({self._values})"


class SolveResult:
    """
    Returned by Model.solve() and Model.optimize().

    Contains all recorded solutions and final statistics.
    """

    def __init__(self,
                 solutions: List[Solution],
                 stats: SearchStats):
        self._solutions = solutions
        self.stats = stats

    @property
    def solution_count(self) -> int:
        return len(self._solutions)

    @property
    def optimal(self) -> Solution | None:
        """Last recorded solution (best for optimisation), or None."""
        return self._solutions[-1] if self._solutions else None

    def __iter__(self) -> Iterator[Solution]:
        return iter(self._solutions)

    def __len__(self) -> int:
        return len(self._solutions)

    def __getitem__(self, idx: int) -> Solution:
        return self._solutions[idx]

    def __repr__(self) -> str:
        return (f"SolveResult(solutions={self.solution_count}, "
                f"stats={self.stats})")


# ---------------------------------------------------------------------------
# Internal helpers used by Model
# ---------------------------------------------------------------------------

def _build_result(sh: int, decision_vars: List[IntExpression]) -> SolveResult:
    """
    Read all solutions and stats from a native search context handle.
    """
    from . import _lib as _L

    num = _L.lib.maxicp_solution_count(_L.thread, sh)
    solutions = []
    for si in range(num):
        val_map = {}
        for var in decision_vars:
            v = _L.lib.maxicp_solution_get_int(_L.thread, sh, si, var.handle)
            val_map[var.handle] = v
        solutions.append(Solution(val_map))

    stats = SearchStats(
        solutions=_L.lib.maxicp_stats_solutions(_L.thread, sh),
        failures=_L.lib.maxicp_stats_failures(_L.thread, sh),
        nodes=_L.lib.maxicp_stats_nodes(_L.thread, sh),
    )
    return SolveResult(solutions, stats)

