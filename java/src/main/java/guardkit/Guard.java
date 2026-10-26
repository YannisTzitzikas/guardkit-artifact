package guardkit;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A checkable property K of the input together with a solver that is correct and efficient on
 * inputs satisfying K, and a {@link ComplexityContract}.
 *
 * <p>Two styles are supported. A <em>predicate guard</em> checks K with a boolean predicate and then
 * solves the original input. A <em>certifying guard</em> checks K by producing a typed witness
 * {@code W} (for example a backdoor set or a 2-colouring); its solver accepts only the witness type,
 * so the specialized solver cannot be called on an input whose property has not been checked, and
 * information found by the check reaches the solver without side channels through the input.
 *
 * @param <I> input type
 * @param <R> result type
 */
public final class Guard<I, R> {
    private final String name;
    private final ComplexityContract contract;
    private final Function<I, Optional<Supplier<R>>> matcher;   // certify, and bind the solver to the witness

    private double priorHit = 0.5;
    private double priorCost = 1e-6;   // seconds

    long nChecks;
    long nHits;
    double totalCheckTime;             // seconds

    private Guard(String name, ComplexityContract contract, Function<I, Optional<Supplier<R>>> matcher) {
        this.name = Objects.requireNonNull(name);
        this.contract = Objects.requireNonNull(contract);
        this.matcher = Objects.requireNonNull(matcher);
    }

    /** Predicate guard: {@code check} decides K; {@code solve} is correct on every input satisfying K. */
    public Guard(String name, Predicate<I> check, Function<I, R> solve, String checkBound, String solveBound) {
        this(name, new ComplexityContract(checkBound, solveBound),
             x -> check.test(x) ? Optional.of(() -> solve.apply(x)) : Optional.empty());
    }

    /**
     * Certifying guard: {@code certify} returns a witness that the input satisfies K, or empty;
     * {@code solve} accepts only the witness type.
     */
    public static <I, W, R> Guard<I, R> certifying(String name, Function<I, Optional<W>> certify,
                                                  Function<W, R> solve, ComplexityContract contract) {
        Objects.requireNonNull(certify);
        Objects.requireNonNull(solve);
        return new Guard<>(name, contract, x -> certify.apply(x).map(w -> () -> solve.apply(w)));
    }

    /** Sets the priors (hit rate and mean check cost in seconds) used before observations exist. */
    public Guard<I, R> withPriors(double hitRate, double checkCostSeconds) {
        this.priorHit = hitRate;
        this.priorCost = checkCostSeconds;
        return this;
    }

    public String name() { return name; }
    public ComplexityContract contract() { return contract; }
    public double priorHit() { return priorHit; }
    public double priorCost() { return priorCost; }
    public long checks() { return nChecks; }
    public long hits() { return nHits; }

    /** Checks K; if it holds, returns the solver bound to the input (or to its witness). */
    Optional<Supplier<R>> match(I x) { return matcher.apply(x); }

    /** Smoothed estimate of Pr[x satisfies K]. */
    public double hitRate() { return (nHits + priorHit) / (nChecks + 1.0); }

    /** Mean observed cost of the check in seconds (the prior until observed). */
    public double meanCost() { return nChecks == 0 ? priorCost : totalCheckTime / nChecks; }

    public void resetStats() { nChecks = 0; nHits = 0; totalCheckTime = 0; }

    @Override public String toString() { return name + " [" + contract + "]"; }
}
