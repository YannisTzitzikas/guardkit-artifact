package guardkit.sat;

/**
 * Witness that a formula has a strong backdoor set with respect to 2-CNF: assigning the variables
 * in {@link #variables()} in any way leaves a 2-CNF formula. Instances are created only by
 * {@link Sat#findBackdoor(Cnf, int)}.
 */
public final class Backdoor {
    private final Cnf formula;
    private final int[] variables;

    Backdoor(Cnf formula, int[] variables) {
        this.formula = formula;
        this.variables = variables.clone();
    }

    public Cnf formula() { return formula; }
    public int[] variables() { return variables.clone(); }
    int[] vars() { return variables; }
}
