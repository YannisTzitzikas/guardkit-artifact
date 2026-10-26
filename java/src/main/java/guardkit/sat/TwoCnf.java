package guardkit.sat;

/**
 * Witness that a formula is in 2-CNF (every clause has at most two literals).
 * Instances are created only by {@link Sat#asTwoCnf(Cnf)}, so a solver taking this type
 * cannot be applied to an unchecked formula.
 */
public final class TwoCnf {
    private final Cnf formula;

    TwoCnf(Cnf formula) { this.formula = formula; }

    public Cnf formula() { return formula; }
}
