package guardkit.vc;

import guardkit.ComplexityContract;
import guardkit.Guard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/** Minimum Vertex Cover (size) with structural guards: forests and bipartite graphs. */
public final class VertexCover {
    private VertexCover() { }

    public static final int BNB_MAX_N = 120;

    // ---------------------------------------------------------------- forest guard, O(n + m)
    public static boolean isForest(Graph g) {
        if (g.edges.length > g.n - 1) return false;
        int[] parent = new int[g.n];
        for (int i = 0; i < g.n; i++) parent[i] = i;
        for (int[] e : g.edges) {
            int ru = find(parent, e[0]), rv = find(parent, e[1]);
            if (ru == rv) return false;
            parent[ru] = rv;
        }
        return true;
    }

    private static int find(int[] p, int a) {
        while (p[a] != a) { p[a] = p[p[a]]; a = p[a]; }
        return a;
    }

    /** Greedy leaf rule, exact on forests: take the neighbour of every leaf. */
    public static int solveForest(Graph g) {
        int[] deg = new int[g.n];
        for (int v = 0; v < g.n; v++) deg[v] = g.adj[v].length;
        boolean[] removed = new boolean[g.n];
        ArrayDeque<Integer> leaves = new ArrayDeque<>();
        for (int v = 0; v < g.n; v++) if (deg[v] == 1) leaves.add(v);
        int cover = 0;
        while (!leaves.isEmpty()) {
            int v = leaves.poll();
            if (removed[v] || deg[v] != 1) continue;
            int u = -1;
            for (int w : g.adj[v]) if (!removed[w]) { u = w; break; }
            cover++;
            removed[u] = true;
            removed[v] = true;
            for (int w : g.adj[u]) {
                if (!removed[w] && --deg[w] == 1) leaves.add(w);
            }
        }
        return cover;
    }

    // ---------------------------------------------------------------- bipartite guard
    /** Certifies bipartiteness by an O(n + m) breadth-first 2-colouring. */
    public static Optional<TwoColoring> twoColor(Graph g) {
        int[] color = new int[g.n];
        Arrays.fill(color, -1);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int s = 0; s < g.n; s++) {
            if (color[s] != -1) continue;
            color[s] = 0;
            q.add(s);
            while (!q.isEmpty()) {
                int u = q.poll();
                for (int w : g.adj[u]) {
                    if (color[w] == -1) { color[w] = color[u] ^ 1; q.add(w); }
                    else if (color[w] == color[u]) return Optional.empty();
                }
            }
        }
        return Optional.of(new TwoColoring(g, color));
    }

    public static boolean isBipartite(Graph g) { return twoColor(g).isPresent(); }

    /** Konig: minimum vertex cover = maximum matching (Hopcroft-Karp, O(m sqrt(n))). */
    public static int solveBipartite(TwoColoring c) {
        Graph g = c.graph();
        int[] left = java.util.stream.IntStream.range(0, g.n).filter(v -> c.color(v) == 0).toArray();
        int[] match = new int[g.n];
        Arrays.fill(match, -1);
        int[] dist = new int[g.n];
        int[][] buf = new int[4][g.n + 1];   // work buffers for dfs, allocated once (not per call)
        int size = 0;
        while (bfs(g, left, match, dist)) {
            for (int u : left) if (match[u] == -1 && dfs(g, u, match, dist, buf)) size++;
        }
        return size;
    }

    private static final int INF = Integer.MAX_VALUE;

    private static boolean bfs(Graph g, int[] left, int[] match, int[] dist) {
        ArrayDeque<Integer> q = new ArrayDeque<>();
        boolean found = false;
        for (int u : left) {
            if (match[u] == -1) { dist[u] = 0; q.add(u); } else dist[u] = INF;
        }
        while (!q.isEmpty()) {
            int u = q.poll();
            for (int w : g.adj[u]) {
                int m = match[w];
                if (m == -1) found = true;
                else if (dist[m] == INF) { dist[m] = dist[u] + 1; q.add(m); }
            }
        }
        return found;
    }

    /** Iterative augmenting-path search along the BFS layering. */
    private static boolean dfs(Graph g, int root, int[] match, int[] dist, int[][] buf) {
        int[] stackV = buf[0], stackI = buf[1], pathU = buf[2], pathW = buf[3];
        int sp = 0, pp = 0;
        stackV[sp] = root; stackI[sp] = 0; sp++;
        while (sp > 0) {
            int x = stackV[sp - 1];
            boolean advanced = false;
            while (stackI[sp - 1] < g.adj[x].length) {
                int w = g.adj[x][stackI[sp - 1]++];
                int m = match[w];
                if (m == -1) {
                    pathU[pp] = x; pathW[pp] = w; pp++;
                    for (int i = 0; i < pp; i++) { match[pathU[i]] = pathW[i]; match[pathW[i]] = pathU[i]; }
                    return true;
                }
                if (dist[m] == dist[x] + 1) {
                    pathU[pp] = x; pathW[pp] = w; pp++;
                    stackV[sp] = m; stackI[sp] = 0; sp++;
                    advanced = true;
                    break;
                }
            }
            if (!advanced) {
                dist[x] = INF;
                sp--;
                if (pp > 0) pp--;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- general solver: branch and bound
    public static int solveBnb(Graph g) {
        Map<Integer, Set<Integer>> adj = new HashMap<>();
        for (int v = 0; v < g.n; v++) {
            Set<Integer> s = new HashSet<>();
            for (int w : g.adj[v]) s.add(w);
            adj.put(v, s);
        }
        int[] best = {g.n};
        bnb(adj, 0, best);
        return best[0];
    }

    private static Map<Integer, Set<Integer>> copy(Map<Integer, Set<Integer>> adj) {
        Map<Integer, Set<Integer>> c = new HashMap<>();
        for (Map.Entry<Integer, Set<Integer>> e : adj.entrySet()) if (!e.getValue().isEmpty()) c.put(e.getKey(), new HashSet<>(e.getValue()));
        return c;
    }

    private static void bnb(Map<Integer, Set<Integer>> adj0, int k, int[] best) {
        if (k >= best[0]) return;
        Map<Integer, Set<Integer>> adj = copy(adj0);
        boolean changed = true;
        while (changed) {                                   // degree-one rule: take the neighbour
            changed = false;
            for (Integer v : new ArrayList<>(adj.keySet())) {
                Set<Integer> nv = adj.get(v);
                if (nv != null && nv.size() == 1) {
                    int u = nv.iterator().next();
                    for (int w : adj.remove(u)) {
                        Set<Integer> nw = adj.get(w);
                        nw.remove(u);
                        if (nw.isEmpty()) adj.remove(w);
                    }
                    k++;
                    changed = true;
                }
            }
        }
        if (adj.isEmpty()) { best[0] = Math.min(best[0], k); return; }
        int m = 0, maxDeg = 0, v = -1;
        for (Map.Entry<Integer, Set<Integer>> e : adj.entrySet()) {
            int d = e.getValue().size();
            m += d;
            if (d > maxDeg) { maxDeg = d; v = e.getKey(); }
        }
        m /= 2;
        if (k + (m + maxDeg - 1) / maxDeg >= best[0]) return;   // degree-based lower bound
        Set<Integer> nbrs = adj.get(v);
        Map<Integer, Set<Integer>> a1 = new HashMap<>();          // branch 1: take v
        for (Map.Entry<Integer, Set<Integer>> e : adj.entrySet()) {
            if (e.getKey() == v) continue;
            Set<Integer> s = new HashSet<>(e.getValue()); s.remove(v); a1.put(e.getKey(), s);
        }
        bnb(a1, k + 1, best);
        Map<Integer, Set<Integer>> a2 = new HashMap<>();          // branch 2: take all neighbours of v
        for (Map.Entry<Integer, Set<Integer>> e : adj.entrySet()) {
            int w = e.getKey();
            if (w == v || nbrs.contains(w)) continue;
            Set<Integer> s = new HashSet<>(e.getValue()); s.removeAll(nbrs); a2.put(w, s);
        }
        bnb(a2, k + nbrs.size(), best);
    }

    public static boolean admit(Graph g) { return g.n <= BNB_MAX_N; }

    /** Reference solver for testing (exponential). */
    public static int bruteForce(Graph g) {
        int bestSize = g.n;
        for (long mask = 0; mask < (1L << g.n); mask++) {
            int size = Long.bitCount(mask);
            if (size >= bestSize) continue;
            boolean ok = true;
            for (int[] e : g.edges) if (((mask >>> e[0]) & 1) == 0 && ((mask >>> e[1]) & 1) == 0) { ok = false; break; }
            if (ok) bestSize = size;
        }
        return bestSize;
    }

    public static List<Guard<Graph, Integer>> guards() {
        return List.of(
            new Guard<>("forest", VertexCover::isForest, VertexCover::solveForest, "O(n+m)", "O(n+m)"),
            Guard.certifying("bipartite", VertexCover::twoColor, VertexCover::solveBipartite,
                             new ComplexityContract("O(n+m)", "O(m sqrt(n))")));
    }

    // ---------------------------------------------------------------- instance generators
    public static Graph genTree(int n, Random rng) {
        int[][] e = new int[Math.max(0, n - 1)][];
        for (int v = 1; v < n; v++) e[v - 1] = new int[]{v, rng.nextInt(v)};
        return new Graph(n, e);
    }

    public static Graph genBipartite(int n, double avgDeg, Random rng) {
        int a = n / 2;
        long m = Math.min((long) (avgDeg * n / 2), (long) a * (n - a));
        Set<Long> seen = new LinkedHashSet<>();
        List<int[]> e = new ArrayList<>();
        while (e.size() < m) {
            int u = rng.nextInt(a), v = a + rng.nextInt(n - a);
            if (seen.add((long) u * n + v)) e.add(new int[]{u, v});
        }
        return new Graph(n, e.toArray(new int[0][]));
    }

    public static Graph genRandom(int n, double avgDeg, Random rng) {
        long m = Math.min((long) (avgDeg * n / 2), (long) n * (n - 1) / 2);
        Set<Long> seen = new LinkedHashSet<>();
        List<int[]> e = new ArrayList<>();
        while (e.size() < m) {
            int u = rng.nextInt(n), v = rng.nextInt(n);
            if (u == v) continue;
            int x = Math.min(u, v), y = Math.max(u, v);
            if (seen.add((long) x * n + y)) e.add(new int[]{x, y});
        }
        return new Graph(n, e.toArray(new int[0][]));
    }
}
