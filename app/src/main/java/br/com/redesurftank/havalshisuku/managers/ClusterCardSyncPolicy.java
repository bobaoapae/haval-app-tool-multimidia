package br.com.redesurftank.havalshisuku.managers;

/**
 * Decides which msgId=133 cluster card reports may be skipped.
 *
 * The car owns the active card. Local prediction (ClusterCardNavigationPolicy) only
 * exists to keep the UI responsive while the car confirms, so the sole thing safe to
 * skip is a report restating what we already applied. A report that CONTRADICTS the
 * prediction is the car correcting us and must be applied.
 *
 * Observed when it wasn't: pressing HOME made the car report card 0, but because a
 * LEFT/RIGHT prediction to card 1 had happened within the echo window, the report was
 * dropped as "stale" — leaving the app on card 1 while the cluster showed card 0, so
 * HOME appeared dead and card 0 rendered blank.
 */
public final class ClusterCardSyncPolicy {
    private static final long SYNTHETIC_CLUSTER_CARD_ECHO_WINDOW_MS = 1500L;

    private ClusterCardSyncPolicy() {
    }

    public static boolean shouldIgnoreNativeClusterCardChanged(
            int previousCard,
            int nextCard,
            long sinceInputMs,
            int lastInputKeyCode,
            long sinceSyntheticMs,
            int lastSyntheticTarget
    ) {
        // Already on that card: nothing to apply.
        if (previousCard == nextCard) return true;

        // Redundant echo of a prediction we just applied.
        if (isRecentSyntheticClusterCardNavigation(sinceSyntheticMs, lastSyntheticTarget)
                && nextCard == lastSyntheticTarget) {
            return true;
        }

        // Everything else is ground truth from the car.
        return false;
    }

    private static boolean isRecentSyntheticClusterCardNavigation(long sinceSyntheticMs, int lastSyntheticTarget) {
        return lastSyntheticTarget >= 0
                && sinceSyntheticMs >= 0L
                && sinceSyntheticMs <= SYNTHETIC_CLUSTER_CARD_ECHO_WINDOW_MS;
    }
}
