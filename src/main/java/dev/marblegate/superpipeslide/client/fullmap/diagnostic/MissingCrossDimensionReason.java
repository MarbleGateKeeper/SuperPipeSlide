package dev.marblegate.superpipeslide.client.fullmap.diagnostic;

/** Why a route line cannot be drawn across a dimension boundary on the full route map. */
public enum MissingCrossDimensionReason {
    /** Path data for the cross-dimension section is missing on the client. */
    MISSING_PATH_DATA,
    /** The dimensions have no resolvable fold anchor pair to carry the route across. */
    MISSING_FOLD
}
