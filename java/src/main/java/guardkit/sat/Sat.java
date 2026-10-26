package guardkit.sat;

import guardkit.ComplexityContract;
import guardkit.Guard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/** Boolean satisfiability with structural guards: 2-CNF, Horn, and small strong 2-CNF backdoors. */
public final class Sat {
    private Sat() { }

    public static final int BACKDOOR_K = 10;
    public static final int DPLL_MAX_N = 120;

    // ---------------------------------------------------------------- 2-SAT, O(n + m)
    private static int lit(int l) { return 2 * (Math.abs(l) - 1) + (l < 0 ? 1 : 0); }

    /** 2-SAT via strongly connected components of the implication graph (iterative Tarjan). */
    public static boolean solve2Sat(int n, List<int[]> clauses) {
        int N = 2 * n;
        int[] deg = new int[N];
        for (int[] c : clauses) {
            if (c.length == 0) return false;
            if (c.length == 1) deg[lit(c[0]) ^ 1]++;
            else { deg[lit(c[0]) ^ 1]++; deg[lit(c[1]) ^ 1]++; }
        }
        int[][] adj = new int[N][];
        for (int i = 0; i < N; i++) adj[i] = new int[deg[i]];
        int[] fill = new int[N];
        for (int[] c : clauses) {
            if (c.length == 1) { int a = lit(c[0]); adj[a ^ 1][fill[a ^ 1]++] = a; }
            else { int a = lit(c[0]), b = lit(c[1]); adj[a ^ 1][fill[a ^ 1]++] = b; adj[b ^ 1][fill[b ^ 1]++] = a; }
        }
        int[] index = new int[N], low = new int[N], comp = new int[N];
        Arrays.fill(index, -1);
        boolean[] onStack = new boolean[N];
        int[] stack = new int[N]; int sp = 0;
        int[] workV = new int[N], workI = new int[N];
        int counter = 0, ncomp = 0;
        for (int root = 0; root < N; root++) {
            if (index[root] != -1) continue;
            int wp = 0;
            workV[wp] = root; workI[wp] = 0; wp++;
            index[root] = low[root] = counter++;
            stack[sp++] = root; onStack[root] = true;
            while (wp > 0) {
                int v = workV[wp - 1], i = workI[wp - 1];
                if (i < adj[v].length) {
                    workI[wp - 1] = i + 1;
                    int w = adj[v][i];
                    if (index[w] == -1) {
                        index[w] = low[w] = counter++;
                        stack[sp++] = w; onStack[w] = true;
                        workV[wp] = w; workI[wp] = 0; wp++;
                    } else if (onStack[w]) {
                        low[v] = Math.min(low[v], index[w]);
                    }
                } else {
                    wp--;
                    if (wp > 0) { int u = workV[wp - 1]; low[u] = Math.min(low[u], low[v]); }
                    if (low[v] == index[v]) {
                        int w;
                        do { w = stack[--sp]; onStack[w] = false; comp[w] = ncomp; } while (w != v);
                        ncomp++;
                    }
                }
            }
        }
        for (int i = 0; i < n; i++) if (comp[2 * i] == comp[2 * i + 1]) return false;
        return true;
    }

    /** Certifies that f is in 2-CNF, in O(m). */
    public static Optional<TwoCnf> asTwoCnf(Cnf f) {
        for (int[] c : f.clauses) if (c.length > 2) return Optional.empty();
        return Optional.of(new TwoCnf(f));
    }

    public static boolean is2Cnf(Cnf f) { return asTwoCnf(f).isPresent(); }

    /** O(n + m); accepts only certified 2-CNF formulas. */
    public static boolean solve2Cnf(TwoCnf t) { return solve2Sat(t.formula().n, Arrays.asList(t.formula().clauses)); }

    // ---------------------------------------------------------------- Horn-SAT, O(n + m)
    /** Certifies that f is a Horn formula, in O(m). */
    public static Optional<HornCnf> asHorn(Cnf f) {
        for (int[] c : f.clauses) {
            int pos = 0;
            for (int l : c) if (l > 0 && ++pos > 1) return Optional.empty();
        }
        return Optional.of(new HornCnf(f));
    }

    public static boolean isHorn(Cnf f) { return asHorn(f).isPresent(); }

    /** Linear-time forward chaining (Dowling-Gallier style); accepts only certified Horn formulas. */
    public static boolean solveHorn(HornCnf horn) {
        Cnf f = horn.formula();
        int m = f.clauses.length;
        int[] count = new int[m], head = new int[m];
        List<List<Integer>> watch = new ArrayList<>(f.n + 1);
        for (int v = 0; v <= f.n; v++) watch.add(new ArrayList<>());
        int[] queue = new int[f.n + m + 1]; int qp = 0;
        boolean[] isTrue = new boolean[f.n + 1];
        for (int ci = 0; ci < m; ci++) {
            int negs = 0, pos = 0;
            for (int l : f.clauses[ci]) {
                if (l < 0) { negs++; watch.get(-l).add(ci); } else pos = l;
            }
            count[ci] = negs; head[ci] = pos;
            if (negs == 0) {
                if (pos == 0) return false;            // empty clause
                queue[qp++] = pos;
            }
        }
        while (qp > 0) {
            int v = queue[--qp];
            if (isTrue[v]) continue;
            isTrue[v] = true;
            for (int ci : watch.get(v)) {
                if (--count[ci] == 0) {
                    int h = head[ci];
                    if (h == 0) return false;
                    if (!isTrue[h]) {
                        if (qp == queue.length) queue = Arrays.copyOf(queue, 2 * queue.length);
                        queue[qp++] = h;
                    }
                }
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- strong 2-CNF backdoor
    /**
     * Certifies a strong 2-CNF backdoor of size at most k: a set B containing a variable of every clause
     * with three distinct variables (found by bounded 3-Hitting-Set branching, O(3^k m)).
     */
    public static Optional<Backdoor> findBackdoor(Cnf f, int k) {
        int[] b = backdoorVars(f.clauses, k);
        return b == null ? Optional.empty() : Optional.of(new Backdoor(f, b));
    }

    static int[] backdoorVars(int[][] clauses, int k) {
        List<int[]> big = new ArrayList<>();
        for (int[] c : clauses) {
            int[] vars = Arrays.stream(c).map(Math::abs).distinct().sorted().toArray();
            if (vars.length > 3) return null;          // guard restricted to clauses of width <= 3
            if (vars.length == 3) big.add(vars);
        }
        for (int budget = 0; budget <= k; budget++) {  // iterative deepening: minimum-size backdoor
            Set<Integer> chosen = new LinkedHashSet<>();
            if (search(big, chosen, budget)) return chosen.stream().mapToInt(Integer::intValue).sorted().toArray();
        }
        return null;
    }

    private static boolean search(List<int[]> big, Set<Integer> chosen, int budget) {
        for (int[] c : big) {
            if (!chosen.contains(c[0]) && !chosen.contains(c[1]) && !chosen.contains(c[2])) {
                if (budget == 0) return false;
                for (int v : c) {
                    chosen.add(v);
                    if (search(big, chosen, budget - 1)) return true;
                    chosen.remove(v);
                }
                return false;
            }
        }
        return true;
    }

    public static boolean hasSmallBackdoor(Cnf f, int k) { return findBackdoor(f, k).isPresent(); }

    /** Tries all 2^|B| assignments of the certified backdoor; each leaves a 2-CNF formula. */
    public static boolean solveBackdoor(Backdoor bd) {
        Cnf f = bd.formula();
        int[] B = bd.vars();
        Map<Integer, Integer> pos = new HashMap<>();
        for (int i = 0; i < B.length; i++) pos.put(B[i], i);
        for (long mask = 0; mask < (1L << B.length); mask++) {
            List<int[]> reduced = new ArrayList<>();
            boolean ok = true;
            for (int[] c : f.clauses) {
                boolean sat = false;
                int[] rest = new int[c.length]; int r = 0;
                for (int l : c) {
                    Integer i = pos.get(Math.abs(l));
                    if (i != null) {
                        boolean val = ((mask >>> i) & 1) == 1;
                        if (val == (l > 0)) { sat = true; break; }
                    } else rest[r++] = l;
                }
                if (sat) continue;
                if (r == 0) { ok = false; break; }
                reduced.add(Arrays.copyOf(rest, r));
            }
            if (ok && solve2Sat(f.n, reduced)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- general solver: DPLL
    public static boolean solveDpll(Cnf f) {
        List<int[]> cls = new ArrayList<>(Arrays.asList(f.clauses));
        return dpll(cls);
    }

    private static List<int[]> simplify(List<int[]> cls, int lit) {
        List<int[]> out = new ArrayList<>(cls.size());
        for (int[] c : cls) {
            boolean satisfied = false, hasNeg = false;
            for (int l : c) { if (l == lit) { satisfied = true; break; } if (l == -lit) hasNeg = true; }
            if (satisfied) continue;
            if (hasNeg) {
                int[] d = Arrays.stream(c).filter(l -> l != -lit).toArray();
                if (d.length == 0) return null;
                out.add(d);
            } else out.add(c);
        }
        return out;
    }

    private static boolean dpll(List<int[]> cls) {
        while (true) {
            int unit = 0;
            for (int[] c : cls) if (c.length == 1) { unit = c[0]; break; }
            if (unit == 0) break;
            cls = simplify(cls, unit);
            if (cls == null) return false;
        }
        if (cls.isEmpty()) return true;
        Map<Integer, Integer> counts = new HashMap<>();
        for (int[] c : cls) for (int l : c) counts.merge(l, 1, Integer::sum);
        int lit = counts.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
        for (int choice : new int[]{lit, -lit}) {
            List<int[]> next = simplify(cls, choice);
            if (next != null && dpll(next)) return true;
        }
        return false;
    }

    public static boolean admit(Cnf f) { return f.n <= DPLL_MAX_N; }

    /** Reference solver for testing (exponential). */
    public static boolean bruteForce(Cnf f) {
        for (long mask = 0; mask < (1L << f.n); mask++) {
            boolean all = true;
            for (int[] c : f.clauses) {
                boolean any = false;
                for (int l : c) if ((((mask >>> (Math.abs(l) - 1)) & 1) == 1) == (l > 0)) { any = true; break; }
                if (!any) { all = false; break; }
            }
            if (all) return true;
        }
        return false;
    }

    public static List<Guard<Cnf, Boolean>> guards() {
        return guards(BACKDOOR_K);
    }

    /** The three SAT guards, all certifying; k is the budget of the backdoor guard. */
    public static List<Guard<Cnf, Boolean>> guards(int k) {
        return List.of(
            Guard.certifying("2-CNF", Sat::asTwoCnf, Sat::solve2Cnf, new ComplexityContract("O(m)", "O(n+m)")),
            Guard.certifying("Horn", Sat::asHorn, Sat::solveHorn, new ComplexityContract("O(m)", "O(n+m)")),
            Guard.certifying("backdoor<=" + k, (Cnf f) -> findBackdoor(f, k), Sat::solveBackdoor,
                             new ComplexityContract("O(3^k m)", "O(2^k (n+m))")));
    }

    // ---------------------------------------------------------------- instance generators
    private static int randLit(Random rng, int v) { return rng.nextBoolean() ? v : -v; }

    private static int[] sample(Random rng, int n, int k) {           // k distinct values from 1..n
        Set<Integer> s = new LinkedHashSet<>();
        while (s.size() < k) s.add(1 + rng.nextInt(n));
        return s.stream().mapToInt(Integer::intValue).toArray();
    }

    public static Cnf gen2Cnf(int n, int m, Random rng) {
        int[][] cls = new int[m][];
        for (int i = 0; i < m; i++) { int[] v = sample(rng, n, 2); cls[i] = new int[]{randLit(rng, v[0]), randLit(rng, v[1])}; }
        return new Cnf(n, cls);
    }

    public static Cnf genHorn(int n, int m, Random rng) {
        List<int[]> cls = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            int[] v = sample(rng, n, 3);
            int[] c = {-v[0], -v[1], -v[2]};
            if (rng.nextDouble() < 0.8) c[0] = v[0];                 // at most one positive literal
            cls.add(c);
        }
        for (int v : sample(rng, n, Math.max(1, n / 20))) cls.add(new int[]{v});   // facts
        return new Cnf(n, cls.toArray(new int[0][]));
    }

    /** 3-CNF whose 3-literal clauses all contain one of k planted backdoor variables. */
    public static Cnf genBackdoor(int n, int m, int k, Random rng) {
        int[] B = sample(rng, n, k);
        Set<Integer> bs = new LinkedHashSet<>();
        for (int b : B) bs.add(b);
        int[] others = java.util.stream.IntStream.rangeClosed(1, n).filter(v -> !bs.contains(v)).toArray();
        int[][] cls = new int[m][];
        for (int i = 0; i < m; i++) {
            int x = others[rng.nextInt(others.length)], y;
            do { y = others[rng.nextInt(others.length)]; } while (y == x);
            if (rng.nextBoolean()) cls[i] = new int[]{randLit(rng, B[rng.nextInt(k)]), randLit(rng, x), randLit(rng, y)};
            else cls[i] = new int[]{randLit(rng, x), randLit(rng, y)};
        }
        return new Cnf(n, cls);
    }

    public static Cnf genRandom3Cnf(int n, double ratio, Random rng) {
        int m = (int) (ratio * n);
        int[][] cls = new int[m][];
        for (int i = 0; i < m; i++) { int[] v = sample(rng, n, 3); cls[i] = new int[]{randLit(rng, v[0]), randLit(rng, v[1]), randLit(rng, v[2])}; }
        return new Cnf(n, cls);
    }
}
