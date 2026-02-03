#!/usr/bin/env python3
"""
Convert TSPTW instances to PCTSPTW instances with:
- i.i.d. rewards per non-depot node (node 0 has reward 0),
- a quota constraint Q = ceil(quota_fraction * total_reward),
- optional metric closure if triangle inequality is violated.

Input directory:
  data/TSPTW/RifkiSolnon

Output directory:
  data/PCTSPTW/RifkiSolnon

Output format:
  n
  Q
  distance matrix (n lines)
  time windows (n lines: earliest latest)
  rewards (n lines, r_0 = 0)
"""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
from typing import List, Tuple

import random
import math

def with_suffix_before_extension(path: Path, suffix: str) -> Path:
    """
    Return a new Path where `suffix` is inserted before the file extension.
    Example: inst01.txt -> inst01_pctsptw.txt
    """
    return path.with_name(path.stem + suffix + path.suffix)

def tokenize_file(path: Path) -> List[str]:
    text = path.read_text(encoding="utf-8", errors="ignore")
    return [t for t in text.split() if t.strip()]


def read_tsptw_instance(path: Path) -> Tuple[int, List[List[int]], List[Tuple[int, int]]]:
    toks = tokenize_file(path)
    if not toks:
        raise ValueError(f"Empty file: {path}")

    idx = 0
    n = int(toks[idx]); idx += 1

    need = n * n
    if idx + need > len(toks):
        raise ValueError(f"Not enough tokens for {n}x{n} distance matrix in {path}")
    flat = list(map(int, toks[idx: idx + need]))
    idx += need
    dist = [flat[i * n:(i + 1) * n] for i in range(n)]

    need = 2 * n
    if idx + need > len(toks):
        raise ValueError(f"Not enough tokens for {n} time windows in {path}")
    tw: List[Tuple[int, int]] = []
    for _ in range(n):
        a = int(toks[idx]); b = int(toks[idx + 1]); idx += 2
        tw.append((a, b))

    return n, dist, tw


def violates_triangle_inequality(dist: List[List[int]]) -> bool:
    n = len(dist)
    for i in range(n):
        di = dist[i]
        for k in range(n):
            dik = di[k]
            dk = dist[k]
            for j in range(n):
                if di[j] > dik + dk[j]:
                    return True
    return False


def metric_closure_floyd_warshall(dist: List[List[int]]) -> List[List[int]]:
    n = len(dist)
    d = [row[:] for row in dist]
    for k in range(n):
        dk = d[k]
        for i in range(n):
            dik = d[i][k]
            di = d[i]
            for j in range(n):
                alt = dik + dk[j]
                if alt < di[j]:
                    di[j] = alt
    return d


def stable_seed_from_path(rel_path: str, global_seed: int) -> int:
    """
    Produce a stable 64-bit seed from instance relative path + a global seed.
    Deterministic across machines/runs.
    """
    s = f"{global_seed}::{rel_path}".encode("utf-8")
    h = hashlib.blake2b(s, digest_size=8).digest()
    return int.from_bytes(h, byteorder="big", signed=False)


def sample_rewards_beta(
    n: int,
    rng: random.Random,
    r_min: int,
    r_max: int,
    alpha: float,
    beta: float,
    normalize_mean_to: float | None = 50.0,
) -> List[int]:
    """
    Rewards are i.i.d. from a Beta(alpha, beta) on [0,1], scaled to [r_min, r_max].
    This is symmetric and bounded (moderate variance, avoids jackpots).

    If normalize_mean_to is not None, rescale rewards to have approximately that mean
    (excluding depot) before clipping, to reduce between-instance drift.
    """
    if n <= 0:
        return []

    rewards = [0] * n
    if n == 1:
        rewards[0] = 0
        return rewards

    # raw sample (float)
    raw = []
    for _ in range(n - 1):
        x = rng.betavariate(alpha, beta)  # [0,1]
        raw.append(x)

    # scale to integer range
    scaled = [r_min + x * (r_max - r_min) for x in raw]

    # optional normalization to target mean (reduces dataset-level variance)
    if normalize_mean_to is not None:
        current_mean = sum(scaled) / len(scaled)
        if current_mean > 1e-9:
            factor = normalize_mean_to / current_mean
            scaled = [v * factor for v in scaled]

    # finalize with clipping and rounding
    for i in range(1, n):
        v = int(round(scaled[i - 1]))
        v = max(r_min, min(r_max, v))
        rewards[i] = v

    rewards[0] = 0
    return rewards


def compute_quota_fraction_of_total(rewards: List[int], quota_fraction: float) -> int:
    total = sum(rewards[1:])  # exclude depot
    if total <= 0:
        return 0
    q = int(math.ceil(quota_fraction * total))
    q = max(1, min(total, q))
    return q


def write_pctsptw_instance(
    out_path: Path,
    n: int,
    dist: List[List[int]],
    tw: List[Tuple[int, int]],
    rewards: List[int],
    quota: int,
) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", encoding="utf-8") as f:
        f.write(f"{n}\n")
        f.write(f"{quota}\n")
        for i in range(n):
            f.write(" ".join(str(dist[i][j]) for j in range(n)) + "\n")
        for i in range(n):
            a, b = tw[i]
            f.write(f"{a} {b}\n")
        for i in range(n):
            f.write(f"{rewards[i]}\n")


def iter_files(root: Path) -> List[Path]:
    return sorted([p for p in root.rglob("*") if p.is_file()])


def main() -> None:
    parser = argparse.ArgumentParser(description="Convert TSPTW to PCTSPTW with robust i.i.d. rewards and quota.")
    parser.add_argument("--in_dir", type=str, default="data/TSPTW/RifkiSolnon")
    parser.add_argument("--out_dir", type=str, default="data/PCTSPTW/RifkiSolnon")

    # Rewards: bounded, symmetric, moderate variance
    parser.add_argument("--r_min", type=int, default=10, help="Minimum reward for non-depot nodes.")
    parser.add_argument("--r_max", type=int, default=100, help="Maximum reward for non-depot nodes.")
    parser.add_argument("--alpha", type=float, default=2.0, help="Beta(alpha, beta) parameter alpha.")
    parser.add_argument("--beta", type=float, default=2.0, help="Beta(alpha, beta) parameter beta.")
    parser.add_argument("--normalize_mean_to", type=float, default=50.0,
                        help="Rescale rewards to approximately this mean (set <0 to disable).")

    # Quota
    parser.add_argument("--quota_fraction", type=float, default=0.65,
                        help="Quota Q = ceil(quota_fraction * total_reward).")

    # Metric closure
    parser.add_argument("--enforce_metric", action="store_true",
                        help="Always compute metric closure, even if triangle inequality holds.")

    # Reproducibility
    parser.add_argument("--global_seed", type=int, default=12345,
                        help="Global seed mixed with instance path to get deterministic per-instance RNG.")

    args = parser.parse_args()

    in_dir = Path(args.in_dir)
    out_dir = Path(args.out_dir)

    if not in_dir.exists():
        raise FileNotFoundError(f"Input directory not found: {in_dir}")

    files = iter_files(in_dir)
    if not files:
        raise FileNotFoundError(f"No instance files found under: {in_dir}")

    converted = 0
    for in_path in files:
        try:
            n, dist, tw = read_tsptw_instance(in_path)

            # Triangle inequality handling
            need_metric = args.enforce_metric or violates_triangle_inequality(dist)
            dist_out = metric_closure_floyd_warshall(dist) if need_metric else dist

            # Deterministic per-instance RNG
            rel = str(in_path.relative_to(in_dir)).replace("\\", "/")
            seed = stable_seed_from_path(rel, args.global_seed)
            rng = random.Random(seed)

            # Rewards
            normalize_mean_to = None if args.normalize_mean_to is None or args.normalize_mean_to < 0 else args.normalize_mean_to
            rewards = sample_rewards_beta(
                n=n,
                rng=rng,
                r_min=args.r_min,
                r_max=args.r_max,
                alpha=args.alpha,
                beta=args.beta,
                normalize_mean_to=normalize_mean_to,
            )

            # Quota
            quota = compute_quota_fraction_of_total(rewards, args.quota_fraction)

            rel = in_path.relative_to(in_dir)
            rel_with_suffix = with_suffix_before_extension(rel, "_pctsptw")
            out_path = out_dir / rel_with_suffix
            write_pctsptw_instance(out_path, n, dist_out, tw, rewards, quota)

            converted += 1
        except Exception as e:
            print(f"[WARN] Failed converting {in_path}: {e}")

    print(f"Done. Converted {converted} instance(s) into {out_dir}")


if __name__ == "__main__":
    main()
