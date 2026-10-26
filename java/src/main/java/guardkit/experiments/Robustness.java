package guardkit.experiments;

import guardkit.Ordering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Guard ordering under misestimated check costs (as in E4 of the paper): exact computation. */
public final class Robustness {
    static final int TRIALS = 2000, M = 6;
    static final double[] RHOS = {1.0, 1.5, 2.0, 4.0, 8.0};

    public static void main(String[] args) {
        Random rng = new Random(11);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < M; i++) names.add("g" + i);
        double maxGap = 0;
        Map<String, List<Double>> heur = new HashMap<>();
        for (String h : List.of("ratio rule", "cheapest first", "most likely first", "random")) heur.put(h, new ArrayList<>());
        Map<Double, List<Double>> perRho = new HashMap<>();
        for (double r : RHOS) perRho.put(r, new ArrayList<>());

        for (int t = 0; t < TRIALS; t++) {
            double[] w = new double[M + 1];
            double tot = 0;
            for (int i = 0; i <= M; i++) { w[i] = -Math.log(1 - rng.nextDouble()); tot += w[i]; }
            Map<String, Double> p = new HashMap<>(), v = new HashMap<>();
            for (int i = 0; i < M; i++) {
                p.put(names.get(i), w[i] / tot);
                v.put(names.get(i), Math.exp(Math.log(1e-6) + rng.nextDouble() * (Math.log(1e-2) - Math.log(1e-6))));
            }
            double opt = optimum(names, p, v);
            double rr = Ordering.expectedVerificationCost(Ordering.ratioOrder(names, p, v), p, v);
            maxGap = Math.max(maxGap, rr / opt - 1);
            heur.get("ratio rule").add(rr / opt);
            List<String> cheap = new ArrayList<>(names); cheap.sort((a, b) -> Double.compare(v.get(a), v.get(b)));
            heur.get("cheapest first").add(Ordering.expectedVerificationCost(cheap, p, v) / opt);
            List<String> likely = new ArrayList<>(names); likely.sort((a, b) -> Double.compare(p.get(b), p.get(a)));
            heur.get("most likely first").add(Ordering.expectedVerificationCost(likely, p, v) / opt);
            List<String> rnd = new ArrayList<>(names); Collections.shuffle(rnd, rng);
            heur.get("random").add(Ordering.expectedVerificationCost(rnd, p, v) / opt);
            for (double rho : RHOS) {
                Map<String, Double> vh = new HashMap<>();
                for (String s : names) vh.put(s, v.get(s) * (1 + rng.nextDouble() * (rho - 1)));
                perRho.get(rho).add(Ordering.expectedVerificationCost(Ordering.ratioOrder(names, p, vh), p, v) / opt);
            }
        }
        System.out.printf("ratio rule vs brute-force optimum, max relative gap: %.3g%n", maxGap);
        for (String h : List.of("ratio rule", "cheapest first", "most likely first", "random")) print(h, heur.get(h));
        for (double r : RHOS) print("rho=" + r, perRho.get(r));
    }

    static double optimum(List<String> names, Map<String, Double> p, Map<String, Double> v) {
        double[] best = {Double.MAX_VALUE};
        permute(new ArrayList<>(names), 0, p, v, best);
        return best[0];
    }

    static void permute(List<String> a, int k, Map<String, Double> p, Map<String, Double> v, double[] best) {
        if (k == a.size()) { best[0] = Math.min(best[0], Ordering.expectedVerificationCost(a, p, v)); return; }
        for (int i = k; i < a.size(); i++) {
            Collections.swap(a, k, i);
            permute(a, k + 1, p, v, best);
            Collections.swap(a, k, i);
        }
    }

    static void print(String label, List<Double> xs) {
        List<Double> s = new ArrayList<>(xs);
        Collections.sort(s);
        double mean = s.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        System.out.printf("%-20s mean %.3f  p95 %.3f  max %.3f%n", label, mean, s.get((int) (0.95 * s.size()) - 1), s.get(s.size() - 1));
    }
}
