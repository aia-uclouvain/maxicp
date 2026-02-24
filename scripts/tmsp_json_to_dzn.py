#!/usr/bin/env python3
"""
Convert JSON instances (one per file) into MiniZinc .dzn files.

For each file foo.json in --in_dir, write foo.dzn in --out_dir (defaults to same dir).

MiniZinc data produced:

int: n;
int: start;
int: end;
int: H;
set of int: allNodes = 1..n;
set of int: mandatory;
array[allNodes, allNodes] of int: d;
array[allNodes] of int: score;

int: maxDistance = H - d[end, start];

Mapping rules from JSON:
- JSON uses 0-based node indices; MiniZinc uses 1-based indices.
  => start := json.start + 1; end := json.end + 1; mandatory elements +1
- score[i] := json.rewards[i] (same order), as ints.
- service duration array := json.service_time_min (ints)
- travel-only matrix := json.travel_time_min (ints; diagonal is 0)
- d[i,i] := 0
- d[i,j] := travel_time_min[i][j] + service_time_min[i]  for i != j
- H := json.time_budget - service_time_min[end_index]
  (end_index is 0-based in JSON)

All values are integers.

Usage:
  python json_to_dzn.py --in_dir data/out --out_dir data/dzn
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Dict, List


def to_dzn_set(ints_1based: List[int]) -> str:
    if not ints_1based:
        return "{}"
    return "{%s}" % ",".join(str(x) for x in sorted(ints_1based))


def to_dzn_1d(arr: List[int]) -> str:
    return "[%s]" % ",".join(str(x) for x in arr)


def to_dzn_2d(matrix: List[List[int]]) -> str:
    # MiniZinc array2d(1..n, 1..n, [flattened])
    n = len(matrix)
    flat = []
    for i in range(n):
        if len(matrix[i]) != n:
            raise ValueError(f"Non-square matrix row {i}: expected {n}, got {len(matrix[i])}")
        flat.extend(matrix[i])
    return f"array2d(1..{n}, 1..{n}, [{','.join(map(str, flat))}])"


def compute_d_matrix(travel: List[List[int]], service: List[int]) -> List[List[int]]:
    n = len(travel)
    if len(service) != n:
        raise ValueError(f"service_time_min length {len(service)} != n {n}")

    d = [[0] * n for _ in range(n)]
    for i in range(n):
        if len(travel[i]) != n:
            raise ValueError(f"travel_time_min row {i} length {len(travel[i])} != n {n}")
        for j in range(n):
            if i == j:
                d[i][j] = 0
            else:
                d[i][j] = int(travel[i][j]) + int(service[i])
    return d


def convert_one(json_path: Path, out_path: Path) -> None:
    data: Dict[str, Any] = json.loads(json_path.read_text(encoding="utf-8"))

    n = int(data["n_nodes"])

    start0 = int(data["start"])
    end0 = int(data["end"])

    # 1-based for MiniZinc
    start = start0 + 1
    end = end0 + 1

    mandatory0 = [int(x) for x in data.get("mandatory", [])]
    mandatory = [x + 1 for x in mandatory0]

    score0 = [int(x) for x in data["rewards"]]
    if len(score0) != n:
        raise ValueError(f"{json_path.name}: rewards length {len(score0)} != n {n}")

    service0 = [int(x) for x in data["service_time_min"]]
    if len(service0) != n:
        raise ValueError(f"{json_path.name}: service_time_min length {len(service0)} != n {n}")

    travel0 = [[int(x) for x in row] for row in data["travel_time_min"]]
    if len(travel0) != n:
        raise ValueError(f"{json_path.name}: travel_time_min rows {len(travel0)} != n {n}")

    d0 = compute_d_matrix(travel0, service0)

    # H = time_budget - duration(end)
    time_budget = int(data["time_budget"])
    H = time_budget - int(service0[end0])
    if H < 0:
        raise ValueError(f"{json_path.name}: computed H < 0 (time_budget={time_budget}, duration(end)={service0[end0]})")

    # Write .dzn
    out_path.parent.mkdir(parents=True, exist_ok=True)
    lines = []
    lines.append(f"n = {n};")
    lines.append(f"start = {start};")
    lines.append(f"end = {end};")
    lines.append(f"H = {H};")
    lines.append(f"mandatory = {to_dzn_set(mandatory)};")
    lines.append(f"d = {to_dzn_2d(d0)};")
    lines.append(f"score = {to_dzn_1d(score0)};")

    out_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--in_dir", type=str, required=True, help="Directory containing .json instances.")
    ap.add_argument("--out_dir", type=str, required=True, help="Directory to write .dzn files.")
    args = ap.parse_args()

    in_dir = Path(args.in_dir)
    if not in_dir.exists():
        raise FileNotFoundError(f"in_dir not found: {in_dir}")

    out_dir = Path(args.out_dir)
    if not out_dir.exists():
        raise FileNotFoundError(f"out_dir not found: {out_dir}")

    json_files = sorted([p for p in in_dir.rglob("*.json") if p.is_file()])
    if not json_files:
        raise FileNotFoundError(f"No .json files found under: {in_dir}")

    converted = 0
    for jp in json_files:
        op = out_dir / (jp.stem + ".dzn")
        try:
            convert_one(jp, op)
            converted += 1
        except Exception as e:
            print(f"[WARN] Failed converting {jp}: {e}")

    print(f"Done. Converted {converted} file(s) into {out_dir}")


if __name__ == "__main__":
    main()