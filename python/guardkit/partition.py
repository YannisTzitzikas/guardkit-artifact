"""Partition: can a multiset of positive integers be split into two equal-sum parts?"""
from __future__ import annotations

from bisect import bisect_left
from itertools import combinations
from typing import List

from .core import Guard

# ---------------------------------------------------------------- guards
def is_all_equal(s: List[int]) -> bool:              # O(n), exits at the first mismatch
    first = s[0]
    for v in s:
        if v != first:
            return False
    return True

def solve_all_equal(s: List[int]) -> bool:           # O(1): a partition exists iff n is even
    return len(s) % 2 == 0

def is_arith(s: List[int]) -> bool:                  # O(n): the multiset {1, ..., n}
    n = len(s)
    seen = bytearray(n + 1)
    for v in s:
        if v < 1 or v > n or seen[v]:
            return False
        seen[v] = 1
    return True

def solve_arith(s: List[int]) -> bool:               # O(1): n(n+1)/2 even iff n = 0 or 3 (mod 4)
    return len(s) % 4 in (0, 3)

SMALL_TOTAL = 2_000_000

def is_small_total(s: List[int], limit: int = SMALL_TOTAL) -> bool:   # O(n)
    total = 0
    for v in s:
        total += v
        if total > limit:
            return False
    return True

def solve_small_total(s: List[int]) -> bool:         # O(n * B / w) bitset dynamic programming
    total = sum(s)
    if total % 2:
        return False
    reach = 1
    for v in s:
        reach |= reach << v
    return (reach >> (total // 2)) & 1 == 1

# ---------------------------------------------------------------- general solver
def solve_mitm(s: List[int]) -> bool:
    """Horowitz-Sahni meet-in-the-middle, O(2^{n/2} n)."""
    total = sum(s)
    if total % 2:
        return False
    target = total // 2
    half = len(s) // 2
    left, right = s[:half], s[half:]

    def sums(items):
        acc = [0]
        for v in items:
            acc = acc + [a + v for a in acc]
        return acc

    rs = sorted(set(sums(right)))
    for a in sums(left):
        need = target - a
        if need < 0:
            continue
        i = bisect_left(rs, need)
        if i < len(rs) and rs[i] == need:
            return True
    return False

MITM_MAX_N = 44

def admit(s: List[int]) -> bool:
    return len(s) <= MITM_MAX_N

def brute_force(s: List[int]) -> bool:
    total = sum(s)
    if total % 2:
        return False
    for r in range(len(s) + 1):
        for c in combinations(s, r):
            if 2 * sum(c) == total:
                return True
    return False

def guards() -> List[Guard]:
    return [
        Guard("all-equal", is_all_equal, solve_all_equal, "O(n)", "O(1)"),
        Guard("arithmetic", is_arith, solve_arith, "O(n)", "O(1)"),
        Guard("small-total", is_small_total, solve_small_total, "O(n)", "O(n*B/w)"),
    ]
