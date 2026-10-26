"""Minimum Vertex Cover (size) with structural guards.

A graph is (n, edges) with vertices 0..n-1 and an undirected edge list.
"""
from __future__ import annotations

import itertools
import random
from collections import deque
from typing import List, Tuple

from .core import Guard


class Graph:
    __slots__ = ("n", "edges", "adj", "coloring")

    def __init__(self, n: int, edges: List[Tuple[int, int]]):
        self.n = n
        self.edges = [(u, v) for u, v in edges if u != v]
        self.adj = [[] for _ in range(n)]
        for u, v in self.edges:
            self.adj[u].append(v)
            self.adj[v].append(u)
        self.coloring = None


# ---------------------------------------------------------------- forest guard, O(n + m)
def is_forest(g: Graph) -> bool:
    if len(g.edges) > g.n - 1:
        return False
    parent = list(range(g.n))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    for u, v in g.edges:
        ru, rv = find(u), find(v)
        if ru == rv:
            return False
        parent[ru] = rv
    return True

def solve_forest(g: Graph) -> int:
    """Greedy leaf rule (exact on forests): take the neighbour of every leaf."""
    deg = [len(a) for a in g.adj]
    removed = bytearray(g.n)
    leaves = deque(v for v in range(g.n) if deg[v] == 1)
    cover = 0
    while leaves:
        v = leaves.popleft()
        if removed[v] or deg[v] != 1:
            continue
        u = next(w for w in g.adj[v] if not removed[w])
        cover += 1
        removed[u] = 1
        removed[v] = 1
        for w in g.adj[u]:
            if not removed[w]:
                deg[w] -= 1
                if deg[w] == 1:
                    leaves.append(w)
    return cover


# ---------------------------------------------------------------- bipartite guard, O(n + m) check, O(m sqrt(n)) solve
def is_bipartite(g: Graph) -> bool:
    color = [-1] * g.n
    for s in range(g.n):
        if color[s] != -1:
            continue
        color[s] = 0
        q = deque([s])
        while q:
            u = q.popleft()
            for w in g.adj[u]:
                if color[w] == -1:
                    color[w] = color[u] ^ 1
                    q.append(w)
                elif color[w] == color[u]:
                    return False
    g.coloring = color
    return True

def solve_bipartite(g: Graph) -> int:
    """Konig: minimum vertex cover = maximum matching (Hopcroft-Karp)."""
    color = g.coloring
    left = [v for v in range(g.n) if color[v] == 0]
    INF = float("inf")
    match = [-1] * g.n
    dist = [0] * g.n

    def bfs():
        q = deque()
        found = False
        for u in left:
            if match[u] == -1:
                dist[u] = 0
                q.append(u)
            else:
                dist[u] = INF
        while q:
            u = q.popleft()
            for w in g.adj[u]:
                m = match[w]
                if m == -1:
                    found = True
                elif dist[m] == INF:
                    dist[m] = dist[u] + 1
                    q.append(m)
        return found

    def dfs(u):
        # iterative augmenting-path search along the BFS layering
        stack = [(u, iter(g.adj[u]))]
        path = []
        while stack:
            x, it = stack[-1]
            advanced = False
            for w in it:
                m = match[w]
                if m == -1:
                    path.append((x, w))
                    for a, b in path:
                        match[a] = b
                        match[b] = a
                    return True
                if dist[m] == dist[x] + 1:
                    path.append((x, w))
                    stack.append((m, iter(g.adj[m])))
                    advanced = True
                    break
            if not advanced:
                dist[x] = INF
                stack.pop()
                if path:
                    path.pop()
        return False

    size = 0
    while bfs():
        for u in left:
            if match[u] == -1 and dfs(u):
                size += 1
    return size


# ---------------------------------------------------------------- general solver: branch and bound
def solve_bnb(g: Graph) -> int:
    best = [g.n]
    adj = [set(a) for a in g.adj]

    def rec(adj, k):
        if k >= best[0]:
            return
        # degree-1 rule and removal of isolated vertices
        changed = True
        taken = 0
        adj = {v: set(ns) for v, ns in adj.items() if ns}
        while changed:
            changed = False
            for v in list(adj):
                if v in adj and len(adj[v]) == 1:
                    (u,) = adj[v]
                    for w in adj.pop(u):
                        adj[w].discard(u)
                        if not adj[w]:
                            adj.pop(w)
                    taken += 1
                    changed = True
        k += taken
        if not adj:
            best[0] = min(best[0], k)
            return
        m = sum(len(s) for s in adj.values()) // 2
        maxdeg = max(len(s) for s in adj.values())
        if k + (m + maxdeg - 1) // maxdeg >= best[0]:
            return
        v = max(adj, key=lambda x: len(adj[x]))
        nbrs = adj[v]
        # branch 1: take v
        a1 = {w: s - {v} for w, s in adj.items() if w != v}
        rec(a1, k + 1)
        # branch 2: take all neighbours of v
        a2 = {w: s - nbrs for w, s in adj.items() if w not in nbrs and w != v}
        rec(a2, k + len(nbrs))

    rec({v: adj[v] for v in range(g.n)}, 0)
    return best[0]

BNB_MAX_N = 120

def admit(g: Graph) -> bool:
    return g.n <= BNB_MAX_N

def brute_force(g: Graph) -> int:
    for k in range(g.n + 1):
        for S in itertools.combinations(range(g.n), k):
            s = set(S)
            if all(u in s or v in s for u, v in g.edges):
                return k
    return g.n

def guards() -> List[Guard]:
    return [
        Guard("forest", is_forest, solve_forest, "O(n+m)", "O(n+m)"),
        Guard("bipartite", is_bipartite, solve_bipartite, "O(n+m)", "O(m sqrt(n))"),
    ]


# ---------------------------------------------------------------- instance generators
def gen_tree(n: int, rng: random.Random) -> Graph:
    return Graph(n, [(v, rng.randrange(v)) for v in range(1, n)])

def gen_bipartite(n: int, avg_deg: float, rng: random.Random) -> Graph:
    a = n // 2
    m = min(int(avg_deg * n / 2), a * (n - a))
    edges = set()
    while len(edges) < m:
        edges.add((rng.randrange(a), a + rng.randrange(n - a)))
    return Graph(n, list(edges))

def gen_random(n: int, avg_deg: float, rng: random.Random) -> Graph:
    m = min(int(avg_deg * n / 2), n * (n - 1) // 2)
    edges = set()
    while len(edges) < m:
        u, v = rng.sample(range(n), 2)
        edges.add((min(u, v), max(u, v)))
    return Graph(n, list(edges))
