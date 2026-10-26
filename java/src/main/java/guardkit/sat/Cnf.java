package guardkit.sat;

/** An immutable CNF formula over variables 1..n; literals are DIMACS-style (v or -v). */
public final class Cnf {
    public final int n;
    public final int[][] clauses;

    public Cnf(int n, int[][] clauses) {
        this.n = n;
        this.clauses = clauses;
    }
}
