"""Boolean satisfiability with structural guards.

Formulas use DIMACS-style literals: variable v is the integer v, its negation -v.
"""
from __future__ import annotations

import itertools
import random
import sys
from typing import Dict, List, Optional, Sequence, Tuple

from .core import Guard

Clause = Tuple[int, ...]


class CNF:
    __slots__ = ("n", "clauses", "backdoor")

    def __init__(self, n: int, clauses: Sequence[Clause]):
        self.n = n
        self.clauses = [tuple(c) for c in clauses]
        self.backdoor: Optional[List[int]] = None   # filled by the backdoor guard


# ---------------------------------------------------------------- 2-SAT, O(n + m)
def _lit(l: int) -> int:
    return 2 * (abs(l) - 1) + (l < 0)

def solve_2sat(n: int, clauses: Sequence[Clause]) -> bool:
    N = 2 * n
    adj: List[List[int]] = [[] for _ in range(N)]
    for c in clauses:
        if len(c) == 0:
            return False
        if len(c) == 1:
            a = _lit(c[0])
            adj[a ^ 1].append(a)
        else:
            a, b = _lit(c[0]), _lit(c[1])
            adj[a ^ 1].append(b)
            adj[b ^ 1].append(a)
    # iterative Tarjan
    index = [-1] * N
    low = [0] * N
    onstack = [False] * N
    comp = [-1] * N
    stack: List[int] = []
    counter = 0
    ncomp = 0
    for root in range(N):
        if index[root] != -1:
            continue
        work = [(root, 0)]
        index[root] = low[root] = counter
        counter += 1
        stack.append(root)
        onstack[root] = True
        while work:
            v, i = work[-1]
            if i < len(adj[v]):
                work[-1] = (v, i + 1)
                w = adj[v][i]
                if index[w] == -1:
                    index[w] = low[w] = counter
                    counter += 1
                    stack.append(w)
                    onstack[w] = True
                    work.append((w, 0))
                elif onstack[w]:
                    low[v] = min(low[v], index[w])
            else:
                work.pop()
                if work:
                    u = work[-1][0]
                    low[u] = min(low[u], low[v])
                if low[v] == index[v]:
                    while True:
                        w = stack.pop()
                        onstack[w] = False
                        comp[w] = ncomp
                        if w == v:
                            break
                    ncomp += 1
    return all(comp[2 * i] != comp[2 * i + 1] for i in range(n))

def is_2cnf(f: CNF) -> bool:
    for c in f.clauses:
        if len(c) > 2:
            return False
    return True

def solve_2cnf(f: CNF) -> bool:
    return solve_2sat(f.n, f.clauses)


# ---------------------------------------------------------------- Horn-SAT, O(n + m)
def is_horn(f: CNF) -> bool:
    for c in f.clauses:
        pos = 0
        for l in c:
            if l > 0:
                pos += 1
                if pos > 1:
                    return False
    return True

def solve_horn(f: CNF) -> bool:
    """Linear-time forward chaining (Dowling-Gallier style)."""
    n = f.n
    count = []                       # number of negative literals not yet satisfied-as-true
    head = []                        # positive literal or 0
    watch: List[List[int]] = [[] for _ in range(n + 1)]
    queue: List[int] = []
    true = bytearray(n + 1)
    for ci, c in enumerate(f.clauses):
        negs = [-l for l in c if l < 0]
        poss = [l for l in c if l > 0]
        count.append(len(negs))
        head.append(poss[0] if poss else 0)
        for v in negs:
            watch[v].append(ci)
        if not negs:
            if not poss:
                return False         # empty clause
            queue.append(poss[0])
    while queue:
        v = queue.pop()
        if true[v]:
            continue
        true[v] = 1
        for ci in watch[v]:
            count[ci] -= 1
            if count[ci] == 0:
                h = head[ci]
                if h == 0:
                    return False
                if not true[h]:
                    queue.append(h)
    return True


# ---------------------------------------------------------------- strong 2-CNF backdoor, O(3^k m + 2^k (n+m))
BACKDOOR_K = 10

def _find_backdoor(clauses: List[Clause], k: int) -> Optional[List[int]]:
    """Smallest search: a set B (|B| <= k) hitting every clause with three distinct variables."""
    big = [tuple(sorted({abs(l) for l in c})) for c in clauses]
    big = [c for c in big if len(c) >= 3]
    if any(len(c) > 3 for c in big):
        return None                  # guard restricted to clauses of width <= 3

    def rec(chosen: set, budget: int) -> Optional[set]:
        for c in big:
            if not (chosen.intersection(c)):
                if budget == 0:
                    return None
                for v in c:
                    chosen.add(v)
                    r = rec(chosen, budget - 1)
                    if r is not None:
                        return r
                    chosen.discard(v)
                return None
        return set(chosen)

    for budget in range(k + 1):      # iterative deepening: find a minimum-size backdoor
        r = rec(set(), budget)
        if r is not None:
            return sorted(r)
    return None

def has_small_backdoor(f: CNF, k: int = BACKDOOR_K) -> bool:
    # Recomputed on every call (no caching across requests, so timings stay honest);
    # the backdoor found is handed to solve_backdoor through the instance.
    f.backdoor = _find_backdoor(f.clauses, k)
    return f.backdoor is not None

def solve_backdoor(f: CNF) -> bool:
    B = f.backdoor or []
    for bits in itertools.product((False, True), repeat=len(B)):
        val = dict(zip(B, bits))
        reduced = []
        ok = True
        for c in f.clauses:
            sat = False
            rest = []
            for l in c:
                v = abs(l)
                if v in val:
                    if val[v] == (l > 0):
                        sat = True
                        break
                else:
                    rest.append(l)
            if sat:
                continue
            if not rest:
                ok = False
                break
            reduced.append(tuple(rest))
        if ok and solve_2sat(f.n, reduced):
            return True
    return False


# ---------------------------------------------------------------- general solver: DPLL
def solve_dpll(f: CNF) -> bool:
    sys.setrecursionlimit(max(10000, 4 * f.n + 100))
    clauses = [list(c) for c in f.clauses]

    def simplify(cls, lit):
        out = []
        for c in cls:
            if lit in c:
                continue
            if -lit in c:
                c = [l for l in c if l != -lit]
                if not c:
                    return None
            out.append(c)
        return out

    def dpll(cls):
        while True:
            unit = next((c[0] for c in cls if len(c) == 1), None)
            if unit is None:
                break
            cls = simplify(cls, unit)
            if cls is None:
                return False
        if not cls:
            return True
        counts: Dict[int, int] = {}
        for c in cls:
            for l in c:
                counts[l] = counts.get(l, 0) + 1
        lit = max(counts, key=counts.get)
        for choice in (lit, -lit):
            nxt = simplify(cls, choice)
            if nxt is not None and dpll(nxt):
                return True
        return False

    return dpll(clauses)

DPLL_MAX_N = 120

def admit(f: CNF) -> bool:
    return f.n <= DPLL_MAX_N

def brute_force(f: CNF) -> bool:
    for bits in itertools.product((False, True), repeat=f.n):
        if all(any((l > 0) == bits[abs(l) - 1] for l in c) for c in f.clauses):
            return True
    return False

def guards() -> List[Guard]:
    return [
        Guard("2-CNF", is_2cnf, solve_2cnf, "O(m)", "O(n+m)"),
        Guard("Horn", is_horn, solve_horn, "O(m)", "O(n+m)"),
        Guard("backdoor<=k", has_small_backdoor, solve_backdoor, "O(3^k m)", "O(2^k (n+m))"),
    ]


# ---------------------------------------------------------------- instance generators
def _rand_lit(rng, v):
    return v if rng.random() < 0.5 else -v

def gen_2cnf(n: int, m: int, rng: random.Random) -> CNF:
    cls = []
    for _ in range(m):
        a, b = rng.sample(range(1, n + 1), 2)
        cls.append((_rand_lit(rng, a), _rand_lit(rng, b)))
    return CNF(n, cls)

def gen_horn(n: int, m: int, rng: random.Random, width: int = 3) -> CNF:
    cls = []
    for _ in range(m):
        vs = rng.sample(range(1, n + 1), width)
        c = [-v for v in vs]
        if rng.random() < 0.8:
            c[0] = vs[0]             # at most one positive literal
        cls.append(tuple(c))
    for v in rng.sample(range(1, n + 1), max(1, n // 20)):
        cls.append((v,))             # some facts, so that chaining happens
    return CNF(n, cls)

def gen_backdoor(n: int, m: int, k: int, rng: random.Random) -> CNF:
    """3-CNF whose 3-literal clauses all contain one of k planted backdoor variables."""
    B = rng.sample(range(1, n + 1), k)
    others = [v for v in range(1, n + 1) if v not in set(B)]
    cls = []
    for _ in range(m):
        if rng.random() < 0.5:
            b = rng.choice(B)
            x, y = rng.sample(others, 2)
            cls.append((_rand_lit(rng, b), _rand_lit(rng, x), _rand_lit(rng, y)))
        else:
            x, y = rng.sample(others, 2)
            cls.append((_rand_lit(rng, x), _rand_lit(rng, y)))
    return CNF(n, cls)

def gen_random_3cnf(n: int, ratio: float, rng: random.Random) -> CNF:
    m = int(ratio * n)
    return CNF(n, [tuple(_rand_lit(rng, v) for v in rng.sample(range(1, n + 1), 3)) for _ in range(m)])
