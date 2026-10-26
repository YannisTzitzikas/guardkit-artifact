package guardkit;

import java.util.List;

/** Result of a dispatch: the answer, the branch taken, the per-guard log and the time split. */
public record Outcome<R>(R answer, String branch, List<GuardCheck> guardLog, double verifyTime, double solveTime) {
    public double totalTime() { return verifyTime + solveTime; }
}
