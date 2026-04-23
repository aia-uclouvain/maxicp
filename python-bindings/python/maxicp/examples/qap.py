"""
Quadratic Assignment Problem (QAP).

Given n facilities and n locations, and two n×n matrices:
- flow[i][j]: flow between facility i and facility j
- dist[a][b]: distance between location a and location b

Find a bijection f: facilities → locations that minimises
  sum_{i,j} flow[i][j] * dist[f[i]][f[j]]

This example uses the small Nugent-5 instance as built-in data.
"""

from __future__ import annotations
import sys
from maxicp import Model, allDifferent, sum_expr, element2d


# --- Nug05 instance (n=5) ---
_NUG05_N = 5
_NUG05_FLOW = [
    [ 0,  1,  1,  0,  0],
    [ 1,  0,  0,  1,  1],
    [ 1,  0,  0,  0,  1],
    [ 0,  1,  0,  0,  0],
    [ 0,  1,  1,  0,  0],
]
_NUG05_DIST = [
    [ 0,  1,  2,  3,  1],
    [ 1,  0,  1,  2,  2],
    [ 2,  1,  0,  1,  3],
    [ 3,  2,  1,  0,  4],
    [ 1,  2,  3,  4,  0],
]
_NUG05_OPT = 14   # optimal for the illustrative 5-facility instance below


def qap(n=None, flow=None, dist=None) -> None:
    n = n or _NUG05_N
    flow = flow or _NUG05_FLOW
    dist = dist or _NUG05_DIST

    with Model() as m:
        # assignment[i] = location assigned to facility i
        assignment = m.int_var_array(n, 0, n - 1)

        # All facilities must be assigned different locations
        m.add(allDifferent(assignment))

        # Objective: sum_{i,j} flow[i][j] * dist[assignment[i]][assignment[j]]
        cost_terms = []
        for i in range(n):
            for j in range(n):
                f_ij = flow[i][j]
                if f_ij == 0:
                    continue
                # dist[assignment[i]][assignment[j]] via element2d
                d_expr = element2d(dist, assignment[i], assignment[j])
                cost_terms.append(d_expr * f_ij)

        total_cost = sum_expr(cost_terms)
        obj = m.minimize(total_cost)

        result = m.optimize(
            objective_handle=obj,
            decision_vars=assignment,
            strategy="first_fail",
        )

        print(f"QAP (n={n}): {result.solution_count} improving solution(s)")
        print(f"Stats: {result.stats}")

        if result.optimal:
            sol = result.optimal
            assign = [sol[assignment[i]] for i in range(n)]
            cost = sum(
                flow[i][j] * dist[assign[i]][assign[j]]
                for i in range(n) for j in range(n)
            )
            print(f"  Best assignment: {assign}  (cost={cost})")
            if n == _NUG05_N:
                print(f"  Known optimum: {_NUG05_OPT}")
        else:
            print("  No solution found.")


if __name__ == "__main__":
    qap()

