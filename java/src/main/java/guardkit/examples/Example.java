package guardkit.examples;

import guardkit.Dispatcher;
import guardkit.OrderPolicy;
import guardkit.Outcome;
import guardkit.partition.Partition;

import java.util.Arrays;

/** Minimal usage example: a guarded dispatcher for Partition. */
public final class Example {
    public static void main(String[] args) {
        System.out.println("Starting..(yannis)");
        Dispatcher<long[], Boolean> d = new Dispatcher<>(
            Partition.guards(), Partition::solveMitm, Partition::admit, OrderPolicy.RATIO_ADAPTIVE);
        long[] input = new long[1_000_000];
        Arrays.fill(input, 7);
        Outcome<Boolean> out = d.solve(input);
        System.out.printf("answer=%s branch=%s verify=%.4f s solve=%.6f s%n",
            out.answer(), out.branch(), out.verifyTime(), out.solveTime());
        System.out.println("Completed.");
    }
}
