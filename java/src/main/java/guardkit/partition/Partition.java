package guardkit.partition;

import guardkit.Guard;

import java.util.Arrays;
import java.util.List;

/** Partition: can a multiset of positive integers be split into two parts of equal sum? */
public final class Partition {
    private Partition() { }

    public static final long SMALL_TOTAL = 2_000_000L;
    public static final int MITM_MAX_N = 44;

    // ---------------------------------------------------------------- guards
    /** O(n), exits at the first mismatch. */
    public static boolean isAllEqual(long[] s) {
        for (long v : s) if (v != s[0]) return false;
        return true;
    }

    /** O(1): a partition exists iff n is even. */
    public static boolean solveAllEqual(long[] s) { return s.length % 2 == 0; }

    /** O(n): is the multiset exactly {1, ..., n}? */
    public static boolean isArith(long[] s) {
        int n = s.length;
        boolean[] seen = new boolean[n + 1];
        for (long v : s) {
            if (v < 1 || v > n || seen[(int) v]) return false;
            seen[(int) v] = true;
        }
        return true;
    }

    /** O(1): n(n+1)/2 is even iff n = 0 or 3 (mod 4), and then {1..n} can be partitioned. */
    public static boolean solveArith(long[] s) { int r = s.length % 4; return r == 0 || r == 3; }

    /** O(n): is the total at most SMALL_TOTAL? */
    public static boolean isSmallTotal(long[] s) {
        long total = 0;
        for (long v : s) {
            total += v;
            if (total > SMALL_TOTAL) return false;
        }
        return true;
    }

    /** O(n B / 64) bitset dynamic programming over reachable subset sums. */
    public static boolean solveSmallTotal(long[] s) {
        long total = 0;
        for (long v : s) total += v;
        if (total % 2 != 0) return false;
        int target = (int) (total / 2);
        long[] reach = new long[target / 64 + 1];
        reach[0] = 1L;
        for (long v : s) {
            int sh = (int) v;
            if (sh > target) continue;
            int words = sh >>> 6, bits = sh & 63;
            for (int i = reach.length - 1; i >= words; i--) {   // reach |= reach << v, high words first
                long x = reach[i - words] << bits;
                if (bits != 0 && i - words - 1 >= 0) x |= reach[i - words - 1] >>> (64 - bits);
                reach[i] |= x;
            }
        }
        return ((reach[target >>> 6] >>> (target & 63)) & 1L) == 1L;
    }

    // ---------------------------------------------------------------- general solver
    /** Horowitz-Sahni meet-in-the-middle, O(2^{n/2} n). */
    public static boolean solveMitm(long[] s) {
        long total = 0;
        for (long v : s) total += v;
        if (total % 2 != 0) return false;
        long target = total / 2;
        int half = s.length / 2;
        long[] left = subsetSums(s, 0, half), right = subsetSums(s, half, s.length);
        Arrays.sort(right);
        for (long a : left) {
            long need = target - a;
            if (need >= 0 && Arrays.binarySearch(right, need) >= 0) return true;
        }
        return false;
    }

    private static long[] subsetSums(long[] s, int from, int to) {
        int k = to - from;
        long[] acc = new long[1 << k];
        int size = 1;
        for (int i = from; i < to; i++) {
            for (int j = 0; j < size; j++) acc[size + j] = acc[j] + s[i];
            size <<= 1;
        }
        return acc;
    }

    public static boolean admit(long[] s) { return s.length <= MITM_MAX_N; }

    /** Reference solver for testing (exponential). */
    public static boolean bruteForce(long[] s) {
        long total = 0;
        for (long v : s) total += v;
        if (total % 2 != 0) return false;
        for (long mask = 0; mask < (1L << s.length); mask++) {
            long sum = 0;
            for (int i = 0; i < s.length; i++) if ((mask >>> i & 1) == 1) sum += s[i];
            if (2 * sum == total) return true;
        }
        return false;
    }

    public static List<Guard<long[], Boolean>> guards() {
        return List.of(
            new Guard<>("all-equal", Partition::isAllEqual, Partition::solveAllEqual, "O(n)", "O(1)"),
            new Guard<>("arithmetic", Partition::isArith, Partition::solveArith, "O(n)", "O(1)"),
            new Guard<>("small-total", Partition::isSmallTotal, Partition::solveSmallTotal, "O(n)", "O(n*B/w)"));
    }
}
