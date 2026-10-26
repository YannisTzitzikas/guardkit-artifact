package guardkit.experiments;

import guardkit.Dispatcher;
import guardkit.Guard;
import guardkit.OrderPolicy;
import guardkit.Outcome;
import guardkit.partition.Partition;
import guardkit.sat.Cnf;
import guardkit.sat.Sat;
import guardkit.vc.Graph;
import guardkit.vc.VertexCover;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Guarded dispatch vs. the general fallback on structured inputs (as in E1 of the paper).
 * Medians over REPS instances. As soon as one fallback run exceeds BUDGET seconds, the fallback is
 * no longer run (for the remaining instances or larger sizes). Timings include JIT warm-up effects;
 * use JMH for rigorous Java measurements.
 */
public final class Benchmark {
    static final int REPS = 5;
    static final double BUDGET = 5.0;
    static final Random RNG = new Random(42);

    public static void main(String[] args) throws Exception {
        Thread t = new Thread(null, () -> {           // large stack for the recursive fallbacks
            run("Partition", "all-equal", new int[]{20, 28, 36, 40, 1_000, 100_000, 1_000_000},
                n -> { long[] s = new long[n]; Arrays.fill(s, 100_000_000L + RNG.nextInt(900_000_000)); return s; },
                Partition.guards(), Partition::solveMitm, 44);
            run("SAT", "2-CNF (m=2n)", new int[]{100, 1_000, 10_000, 100_000},
                n -> Sat.gen2Cnf(n, 2 * n, RNG), Sat.guards(), Sat::solveDpll, 10_000);
            run("SAT", "Horn (m=3n)", new int[]{100, 1_000, 10_000, 100_000},
                n -> Sat.genHorn(n, 3 * n, RNG), Sat.guards(), Sat::solveDpll, 10_000);
            run("SAT", "backdoor k=6 (m=3n)", new int[]{60, 120, 240, 480},
                n -> Sat.genBackdoor(n, 3 * n, 6, RNG), Sat.guards(), Sat::solveDpll, 480);
            run("Vertex Cover", "bipartite (avg deg 6)", new int[]{50, 100, 200, 10_000, 100_000},
                n -> VertexCover.genBipartite(n, 6, RNG), VertexCover.guards(), VertexCover::solveBnb, 200);
        }, "bench", 1L << 30);
        t.start();
        t.join();
    }

    static <I, R> void run(String domain, String family, int[] sizes, IntFunction<I> gen,
                           List<Guard<I, R>> guards, Function<I, R> fallback, int fallbackMaxN) {
        Dispatcher<I, R> d = new Dispatcher<>(guards, null, null, OrderPolicy.DECLARED);
        boolean fallbackOn = true;
        System.out.printf("%n%s / %s%n%10s %16s %16s %10s%n", domain, family, "n", "guarded (s)", "fallback (s)", "speed-up");
        for (int n : sizes) {
            List<Double> g = new ArrayList<>(), f = new ArrayList<>();
            boolean runFallback = fallbackOn && n <= fallbackMaxN;
            for (int r = 0; r < REPS; r++) {
                I x = gen.apply(n);
                Outcome<R> o = d.solve(x);
                g.add(o.totalTime());
                if (runFallback && fallbackOn) {
                    long t0 = System.nanoTime();
                    R ans = fallback.apply(x);
                    double dt = (System.nanoTime() - t0) / 1e9;
                    if (!ans.equals(o.answer())) throw new AssertionError(domain + " " + family + " n=" + n);
                    f.add(dt);
                    if (dt > BUDGET) fallbackOn = false;
                }
            }
            double gm = median(g);
            if (f.isEmpty()) System.out.printf("%10d %16.6f %16s %10s%n", n, gm, "-", "-");
            else { double fm = median(f); System.out.printf("%10d %16.6f %16.6f %10.1f%n", n, gm, fm, fm / gm); }
        }
    }

    static double median(List<Double> xs) {
        List<Double> s = new ArrayList<>(xs);
        Collections.sort(s);
        int k = s.size();
        return k % 2 == 1 ? s.get(k / 2) : (s.get(k / 2 - 1) + s.get(k / 2)) / 2;
    }
}
