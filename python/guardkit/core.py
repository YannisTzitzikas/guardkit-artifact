"""Predicate-based algorithm dispatch with complexity contracts.

A *guard* couples a checkable structural property K of the input with a solver
that is correct and efficient on inputs satisfying K. Each guard carries a
*complexity contract*: declared asymptotic bounds for checking K and for
solving under K. A *dispatcher* evaluates guards in some order and runs the
solver of the first guard that matches; if none matches, it applies an
admission policy and either runs a general fallback solver or rejects.
"""
from __future__ import annotations

import random
import time
from dataclasses import dataclass, field
from typing import Any, Callable, List, Optional


@dataclass
class Guard:
    """A checkable property K together with a solver for inputs satisfying K."""
    name: str
    check: Callable[[Any], bool]          # decides x |= K
    solve: Callable[[Any], Any]           # correct for every x |= K
    verify_bound: str                     # declared bound of `check`, e.g. "O(n)"
    solve_bound: str                      # declared bound of `solve` under K
    prior_hit: float = 0.5                # prior estimate of Pr[x |= K]
    prior_cost: float = 1e-6              # prior estimate of the cost of `check` (seconds)
    # online statistics (maintained by the dispatcher)
    n_checks: int = 0
    n_hits: int = 0
    total_check_time: float = 0.0

    def hit_rate(self, weight: float = 1.0) -> float:
        """Smoothed estimate of Pr[x |= K] from observed checks."""
        return (self.n_hits + weight * self.prior_hit) / (self.n_checks + weight)

    def mean_cost(self) -> float:
        """Mean observed cost of `check` (prior until observed)."""
        return self.total_check_time / self.n_checks if self.n_checks else self.prior_cost

    def reset_stats(self) -> None:
        self.n_checks = self.n_hits = 0
        self.total_check_time = 0.0


class Rejected(Exception):
    """Raised when no guard matches and the admission policy refuses the input."""


@dataclass
class Outcome:
    answer: Any
    branch: str                                   # guard name, "fallback", or "rejected"
    guard_log: List[tuple] = field(default_factory=list)   # (guard name, seconds, matched)
    verify_time: float = 0.0
    solve_time: float = 0.0

    @property
    def total_time(self) -> float:
        return self.verify_time + self.solve_time


ORDER_POLICIES = ("declared", "ratio-static", "ratio-adaptive", "random")


class Dispatcher:
    """Evaluates guards in a chosen order and dispatches to the first match.

    Ordering policies:
      declared        the order in which guards were registered;
      ratio-static    decreasing prior_hit / prior_cost (Smith's ratio rule with priors);
      ratio-adaptive  decreasing estimated hit rate / measured mean cost, with
                      epsilon-exploration so that every guard keeps being observed;
      random          a fresh random permutation for every request.
    """

    def __init__(self, guards: List[Guard], fallback: Optional[Callable[[Any], Any]] = None,
                 admit: Optional[Callable[[Any], bool]] = None, order: str = "declared",
                 explore: float = 0.05, seed: int = 0):
        if order not in ORDER_POLICIES:
            raise ValueError(f"unknown order policy {order!r}")
        self.guards = list(guards)
        self.fallback = fallback
        self.admit = admit or (lambda x: True)
        self.order = order
        self.explore = explore
        self.rng = random.Random(seed)

    def _ordered(self) -> List[Guard]:
        if self.order == "declared":
            return list(self.guards)
        if self.order == "ratio-static":
            return sorted(self.guards, key=lambda g: g.prior_hit / g.prior_cost, reverse=True)
        if self.order == "ratio-adaptive":
            return sorted(self.guards, key=lambda g: g.hit_rate() / max(g.mean_cost(), 1e-12), reverse=True)
        perm = list(self.guards)
        self.rng.shuffle(perm)
        return perm

    def solve(self, x: Any) -> Outcome:
        out = Outcome(answer=None, branch="")
        exploring = self.order == "ratio-adaptive" and self.rng.random() < self.explore
        matched: Optional[Guard] = None
        for g in self._ordered():
            t0 = time.perf_counter()
            hit = bool(g.check(x))
            dt = time.perf_counter() - t0
            g.n_checks += 1
            g.total_check_time += dt
            g.n_hits += hit
            out.guard_log.append((g.name, dt, hit))
            out.verify_time += dt
            if hit and matched is None:
                matched = g
                if not exploring:
                    break
        t0 = time.perf_counter()
        if matched is not None:
            out.answer, out.branch = matched.solve(x), matched.name
        elif self.fallback is not None and self.admit(x):
            out.answer, out.branch = self.fallback(x), "fallback"
        else:
            out.solve_time = time.perf_counter() - t0
            out.branch = "rejected"
            raise Rejected(out)
        out.solve_time = time.perf_counter() - t0
        return out


def expected_verification_cost(order: List[str], p: dict, v: dict) -> float:
    """Expected total guard cost of an order for DISJOINT guards:
    sum_j v[pi(j)] * (1 - sum_{l<j} p[pi(l)])."""
    total, reach = 0.0, 1.0
    for name in order:
        total += v[name] * reach
        reach -= p[name]
    return total


def ratio_order(names: List[str], p: dict, v: dict) -> List[str]:
    """Smith's ratio rule: decreasing p/v (optimal for disjoint guards)."""
    return sorted(names, key=lambda s: p[s] / v[s], reverse=True)
