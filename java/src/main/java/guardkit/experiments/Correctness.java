package guardkit.experiments;

import guardkit.partition.Partition;
import guardkit.sat.Cnf;
import guardkit.sat.Sat;
import guardkit.vc.Graph;
import guardkit.vc.VertexCover;

import java.util.Arrays;
import java.util.Random;

/** Differential testing of every specialized solver and fallback against brute force (E0). */
public final class Correctness {
    public static void main(String[] args) {
        Random rng = new Random(2026);
        int bad = 0, total = 0;

        // Partition
        int pBad = 0;
        for (int t = 0; t < 3000; t++) {
            int n = 1 + rng.nextInt(14);
            long[] s = new long[n];
            switch (t % 4) {
                case 0 -> Arrays.fill(s, 1 + rng.nextInt(9));
                case 1 -> { for (int i = 0; i < n; i++) s[i] = i + 1; shuffle(s, rng); }
                case 2 -> { for (int i = 0; i < n; i++) s[i] = 1 + rng.nextInt(30); }
                default -> { for (int i = 0; i < n; i++) s[i] = 1 + rng.nextInt(1_000_000); }
            }
            boolean ref = Partition.bruteForce(s);
            boolean ok = Partition.solveMitm(s) == ref;
            if (Partition.isAllEqual(s)) ok &= Partition.solveAllEqual(s) == ref;
            if (Partition.isArith(s)) ok &= Partition.solveArith(s) == ref;
            if (Partition.isSmallTotal(s)) ok &= Partition.solveSmallTotal(s) == ref;
            if (!ok) pBad++;
        }
        System.out.printf("Partition:    3000 instances, %d disagreements%n", pBad);

        // SAT
        int sBad = 0, h2 = 0, hh = 0, hb = 0;
        double[] ratios = {2.0, 4.26, 6.0};
        for (int t = 0; t < 2000; t++) {
            int n = 3 + rng.nextInt(9);
            Cnf f = switch (t % 4) {
                case 0 -> Sat.gen2Cnf(n, 1 + rng.nextInt(3 * n), rng);
                case 1 -> Sat.genHorn(n, 1 + rng.nextInt(3 * n), rng);
                case 2 -> Sat.genBackdoor(n + 3, 1 + rng.nextInt(3 * n), 1 + rng.nextInt(3), rng);
                default -> Sat.genRandom3Cnf(n, ratios[rng.nextInt(3)], rng);
            };
            boolean ref = Sat.bruteForce(f);
            boolean ok = Sat.solveDpll(f) == ref;
            // certify, then solve the witness: the specialized solvers accept only witness types
            var two = Sat.asTwoCnf(f);
            if (two.isPresent()) { ok &= Sat.solve2Cnf(two.get()) == ref; h2++; }
            var horn = Sat.asHorn(f);
            if (horn.isPresent()) { ok &= Sat.solveHorn(horn.get()) == ref; hh++; }
            var bd = Sat.findBackdoor(f, 4);
            if (bd.isPresent()) {
                int[] B = bd.get().variables();
                ok &= hitsAllWideClauses(f, B);          // the witness must be a valid backdoor
                ok &= Sat.solveBackdoor(bd.get()) == ref;
                hb++;
            }
            if (!ok) sBad++;
        }
        System.out.printf("SAT:          2000 instances, %d disagreements (guard matches: 2-CNF %d, Horn %d, backdoor %d)%n", sBad, h2, hh, hb);

        // Vertex Cover
        int vBad = 0, hf = 0, hbp = 0;
        for (int t = 0; t < 1500; t++) {
            int n = 2 + rng.nextInt(12);
            Graph g = switch (t % 3) {
                case 0 -> VertexCover.genTree(n, rng);
                case 1 -> VertexCover.genBipartite(n, 1 + 2 * rng.nextDouble(), rng);
                default -> VertexCover.genRandom(n, 1 + 3 * rng.nextDouble(), rng);
            };
            int ref = VertexCover.bruteForce(g);
            boolean ok = VertexCover.solveBnb(g) == ref;
            if (VertexCover.isForest(g)) { ok &= VertexCover.solveForest(g) == ref; hf++; }
            var col = VertexCover.twoColor(g);
            if (col.isPresent()) {
                for (int[] e : g.edges) ok &= col.get().color(e[0]) != col.get().color(e[1]);   // valid colouring
                ok &= VertexCover.solveBipartite(col.get()) == ref;
                hbp++;
            }
            if (!ok) vBad++;
        }
        System.out.printf("Vertex Cover: 1500 instances, %d disagreements (guard matches: forest %d, bipartite %d)%n", vBad, hf, hbp);

        bad = pBad + sBad + vBad;
        System.out.println(bad == 0 ? "ALL CORRECT" : "FAILURES: " + bad);
        if (bad != 0) System.exit(1);
    }

    static boolean hitsAllWideClauses(Cnf f, int[] B) {
        java.util.Set<Integer> b = new java.util.HashSet<>();
        for (int v : B) b.add(v);
        for (int[] c : f.clauses) {
            int[] vars = Arrays.stream(c).map(Math::abs).distinct().toArray();
            if (vars.length >= 3 && Arrays.stream(vars).noneMatch(b::contains)) return false;
        }
        return true;
    }

    static void shuffle(long[] a, Random rng) {
        for (int i = a.length - 1; i > 0; i--) { int j = rng.nextInt(i + 1); long t = a[i]; a[i] = a[j]; a[j] = t; }
    }
}
