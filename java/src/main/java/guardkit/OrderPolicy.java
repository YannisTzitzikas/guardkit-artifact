package guardkit;

/** How a dispatcher orders its guards. */
public enum OrderPolicy {
    /** The order in which guards were registered. */
    DECLARED,
    /** Decreasing priorHit / priorCost (Smith's ratio rule with calibrated priors). */
    RATIO_STATIC,
    /** Decreasing estimated hit rate / measured mean cost, with epsilon-exploration. */
    RATIO_ADAPTIVE,
    /** A fresh random permutation for every request. */
    RANDOM
}
