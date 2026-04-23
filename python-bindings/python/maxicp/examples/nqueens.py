"""
N-Queens problem.

Place n queens on an n×n chess board so that no two queens threaten
each other (no shared row, column, or diagonal).
"""

from __future__ import annotations
import sys
from maxicp import Model, allDifferent


def nqueens(n: int, max_solutions: int = 0) -> None:
    """Solve and print solutions to the n-queens problem."""
    with Model() as m:
        # q[i] = column of queen in row i
        q = m.int_var_array(n, 0, n - 1)

        # All in different columns
        m.add(allDifferent(q))

        # All on different diagonals (q[i]-i and q[i]+i must all differ)
        diag1 = [q[i] - i for i in range(n)]
        diag2 = [q[i] + i for i in range(n)]
        m.add(allDifferent(diag1))
        m.add(allDifferent(diag2))

        result = m.solve(
            decision_vars=q,
            strategy="first_fail",
            max_solutions=max_solutions,
        )

        print(f"N-Queens (n={n}): {result.solution_count} solution(s) found.")
        print(f"Stats: {result.stats}")

        for i, sol in enumerate(result):
            board = [sol[q[r]] for r in range(n)]
            print(f"  Solution {i + 1}: {board}")
            if i >= 4:
                print("  ... (truncated)")
                break


if __name__ == "__main__":
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 8
    nqueens(n, max_solutions=0)

