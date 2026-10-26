package guardkit;

/** Thrown when no guard matches and the admission policy refuses the input. */
public final class Rejected extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final transient Outcome<?> outcome;

    public Rejected(Outcome<?> outcome) {
        super("no guard matched and the input was not admitted to the fallback");
        this.outcome = outcome;
    }

    public Outcome<?> outcome() { return outcome; }
}
