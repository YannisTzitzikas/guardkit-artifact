package guardkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Evaluates guards in the order given by an {@link OrderPolicy}, dispatches to the solver of the
 * first guard that matches, and otherwise applies the admission policy: run the fallback solver
 * or throw {@link Rejected}.
 */
public final class Dispatcher<I, R> {
    private final List<Guard<I, R>> guards;
    private final Function<I, R> fallback;      // may be null
    private final Predicate<I> admit;
    private final OrderPolicy order;
    private final double explore;
    private final Random rng;

    public Dispatcher(List<Guard<I, R>> guards, Function<I, R> fallback, Predicate<I> admit,
                      OrderPolicy order, double explore, long seed) {
        this.guards = new ArrayList<>(guards);
        this.fallback = fallback;
        this.admit = admit == null ? x -> true : admit;
        this.order = order;
        this.explore = explore;
        this.rng = new Random(seed);
    }

    public Dispatcher(List<Guard<I, R>> guards, Function<I, R> fallback, Predicate<I> admit, OrderPolicy order) {
        this(guards, fallback, admit, order, 0.05, 0L);
    }

    public List<Guard<I, R>> guards() { return Collections.unmodifiableList(guards); }

    /** The order in which the next request would evaluate the guards. */
    public List<Guard<I, R>> ordered() {
        List<Guard<I, R>> g = new ArrayList<>(guards);
        switch (order) {
            case DECLARED -> { }
            case RATIO_STATIC -> g.sort(Comparator.comparingDouble((Guard<I, R> x) -> x.priorHit() / x.priorCost()).reversed());
            case RATIO_ADAPTIVE -> g.sort(Comparator.comparingDouble((Guard<I, R> x) -> x.hitRate() / Math.max(x.meanCost(), 1e-12)).reversed());
            case RANDOM -> Collections.shuffle(g, rng);
        }
        return g;
    }

    public Outcome<R> solve(I x) {
        List<GuardCheck> log = new ArrayList<>();
        boolean exploring = order == OrderPolicy.RATIO_ADAPTIVE && rng.nextDouble() < explore;
        double verify = 0;
        Guard<I, R> matched = null;
        Supplier<R> solver = null;
        for (Guard<I, R> g : ordered()) {
            long t0 = System.nanoTime();
            Optional<Supplier<R>> m = g.match(x);
            double dt = (System.nanoTime() - t0) / 1e9;
            boolean hit = m.isPresent();
            g.nChecks++;
            g.totalCheckTime += dt;
            if (hit) g.nHits++;
            log.add(new GuardCheck(g.name(), dt, hit));
            verify += dt;
            if (hit && matched == null) {
                matched = g;
                solver = m.get();
                if (!exploring) break;
            }
        }
        long t0 = System.nanoTime();
        if (matched != null) {
            R ans = solver.get();
            return new Outcome<>(ans, matched.name(), log, verify, (System.nanoTime() - t0) / 1e9);
        }
        if (fallback != null && admit.test(x)) {
            R ans = fallback.apply(x);
            return new Outcome<>(ans, "fallback", log, verify, (System.nanoTime() - t0) / 1e9);
        }
        throw new Rejected(new Outcome<>(null, "rejected", log, verify, 0.0));
    }
}
