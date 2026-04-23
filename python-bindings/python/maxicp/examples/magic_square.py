"""
Magic Square problem.

Fill an n×n grid with the integers 1..n² so that the sum of each row,
column, and diagonal equals the magic constant n*(n²+1)/2.
"""

from __future__ import annotations
import sys
from maxicp import Model, allDifferent, sum_expr


def magic_square(n: int) -> None:
    """Solve and print one magic square of order n."""
    magic = n * (n * n + 1) // 2

    with Model() as m:
        # x[i][j] = value in cell (i, j), domain [1, n²]
        x = [[m.int_var(1, n * n) for _ in range(n)] for _ in range(n)]
        flat = [x[i][j] for i in range(n) for j in range(n)]

        # All different
        m.add(allDifferent(flat))

        # Row sums
        for i in range(n):
            row_sum = sum_expr(x[i])
            m.add(row_sum == magic)

        # Column sums
        for j in range(n):
            col_sum = sum_expr([x[i][j] for i in range(n)])
            m.add(col_sum == magic)

        # Main diagonal
        m.add(sum_expr([x[i][i] for i in range(n)]) == magic)

        # Anti-diagonal
        m.add(sum_expr([x[i][n - 1 - i] for i in range(n)]) == magic)

        # Symmetry breaking: top-left < top-right, top-left < bottom-left
        m.add(x[0][0] <= x[0][n - 1])
        m.add(x[0][0] <= x[n - 1][0])

        result = m.solve(
            decision_vars=flat,
            strategy="first_fail",
            max_solutions=1,
        )

        print(f"Magic Square (n={n}), magic={magic}")
        print(f"Stats: {result.stats}")

        if result.optimal:
            sol = result.optimal
            for i in range(n):
                row = [sol[x[i][j]] for j in range(n)]
                print("  " + "  ".join(f"{v:3d}" for v in row))
        else:
            print("  No solution found.")


if __name__ == "__main__":
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 3
    magic_square(n)

