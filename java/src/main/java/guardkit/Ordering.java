package guardkit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Expected checking cost of a guard order and the ratio rule (for disjoint guards). */
public final class Ordering {
    private Ordering() { }

    /** sum_j v[pi(j)] * (1 - sum_{l<j} p[pi(l)]) for disjoint guards. */
    public static double expectedVerificationCost(List<String> order, Map<String, Double> p, Map<String, Double> v) {
        double total = 0, reach = 1;
        for (String s : order) {
            total += v.get(s) * reach;
            reach -= p.get(s);
        }
        return total;
    }

    /** Smith's ratio rule: decreasing p/v, optimal for disjoint guards. */
    public static List<String> ratioOrder(List<String> names, Map<String, Double> p, Map<String, Double> v) {
        List<String> o = new ArrayList<>(names);
        o.sort(Comparator.comparingDouble((String s) -> p.get(s) / v.get(s)).reversed());
        return o;
    }
}
