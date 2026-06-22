package br.com.redesurftank.havalshisuku.managers;

public final class ClusterCardSyncPolicy {
    private static final int MAIN_MENU_CARD = 1;
    private static final int AIRCON_CARD = 3;
    private static final int CLUSTER_KEY_LEFT = 1026;
    private static final int CLUSTER_KEY_RIGHT = 1027;
    private static final long NATIVE_CLUSTER_CARD_INPUT_WINDOW_MS = 2500L;
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
        if (isRecentSyntheticClusterCardNavigation(sinceSyntheticMs, lastSyntheticTarget)) {
            return nextCard != lastSyntheticTarget;
        }

        if (nextCard != 0) return false;
        if (previousCard != MAIN_MENU_CARD && previousCard != AIRCON_CARD) return false;
        return !isRecentClusterCardNavigationInput(lastInputKeyCode, sinceInputMs);
    }

    private static boolean isRecentSyntheticClusterCardNavigation(long sinceSyntheticMs, int lastSyntheticTarget) {
        return lastSyntheticTarget >= 0
                && sinceSyntheticMs >= 0L
                && sinceSyntheticMs <= SYNTHETIC_CLUSTER_CARD_ECHO_WINDOW_MS;
    }

    private static boolean isRecentClusterCardNavigationInput(int lastInputKeyCode, long sinceInputMs) {
        return (lastInputKeyCode == CLUSTER_KEY_LEFT || lastInputKeyCode == CLUSTER_KEY_RIGHT)
                && sinceInputMs >= 0L
                && sinceInputMs <= NATIVE_CLUSTER_CARD_INPUT_WINDOW_MS;
    }
}
