package guardkit;

/** One guard evaluation: which guard, how long the check took (seconds), and whether it matched. */
public record GuardCheck(String guard, double seconds, boolean matched) { }
