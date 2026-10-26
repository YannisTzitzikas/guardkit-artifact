package guardkit;

/**
 * The complexity contract of a guard: declared asymptotic bounds for checking the property K
 * and for solving under K, e.g. {@code new ComplexityContract("O(n)", "O(1)")}.
 */
public record ComplexityContract(String check, String solve) {
    @Override public String toString() { return "check " + check + ", solve " + solve; }
}
