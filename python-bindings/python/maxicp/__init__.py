"""
maxicp – Python bindings for the MaxiCP constraint-programming solver.

Quick start
-----------
>>> from maxicp import Model, allDifferent, sum_expr
>>> with Model() as m:
...     queens = m.int_var_array(8, 0, 7)
...     m.add(allDifferent(queens))
...     result = m.solve(decision_vars=queens, max_solutions=1)
...     print([result.optimal[q] for q in queens])

Public re-exports
-----------------
- Model
- IntVar, BoolVar, IntExpression, BoolExpression
- allDifferent, circuit, table, cumulative, disjunctive
- sum_expr, element1d, element2d
- SolveResult, Solution, SearchStats
- FIRST_FAIL_BINARY, CONFLICT_ORDERING, STATIC_ORDER
"""

from .model import Model
from .variables import IntVar, BoolVar, IntExpression, BoolExpression
from .constraints import allDifferent, circuit, table, cumulative, disjunctive
from .expressions import sum_expr, element1d, element2d
from .search import (
    SolveResult, Solution, SearchStats,
    FIRST_FAIL_BINARY, CONFLICT_ORDERING, STATIC_ORDER,
)

__all__ = [
    "Model",
    "IntVar", "BoolVar", "IntExpression", "BoolExpression",
    "allDifferent", "circuit", "table", "cumulative", "disjunctive",
    "sum_expr", "element1d", "element2d",
    "SolveResult", "Solution", "SearchStats",
    "FIRST_FAIL_BINARY", "CONFLICT_ORDERING", "STATIC_ORDER",
]

__version__ = "0.0.3"

