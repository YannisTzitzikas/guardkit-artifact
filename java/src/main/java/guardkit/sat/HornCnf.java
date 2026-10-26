package guardkit.sat;

/**
 * Witness that a formula is a Horn formula (every clause has at most one positive literal).
 * Instances are created only by {@link Sat#asHorn(Cnf)}, so a solver taking this type
 * cannot be applied to an unchecked formula.
 */
public final class HornCnf {
    private final Cnf formula;

    HornCnf(Cnf formula) { this.formula = formula; }

    public Cnf formula() { return formula; }
}
