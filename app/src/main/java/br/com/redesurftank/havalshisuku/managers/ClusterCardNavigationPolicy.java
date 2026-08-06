package br.com.redesurftank.havalshisuku.managers;

import br.com.redesurftank.havalshisuku.models.ClusterKey;

/**
 * Classifies cluster wheel input. It does NOT decide which card comes next.
 *
 * The car owns the active card and reports it via msgId=133. A previous version of this
 * class predicted the next card from a hardcoded {0, 1, 3} ring, which turned out not to
 * match the car: card 0 has vehicle-state-dependent sub-pages (two of them while
 * charging) that all report cardId 0, so the prediction drifted from reality and the app
 * ended up rendering one card while the cluster showed another.
 */
final class ClusterCardNavigationPolicy {
    private static final int ACTION_UP = 1;

    private ClusterCardNavigationPolicy() {}

    static boolean shouldHandleInputAction(int action) {
        // The BeanTech input service on this head unit reports cluster-wheel
        // presses as release-only events. Handling ACTION_DOWN drops real input.
        return action == ACTION_UP;
    }

    /**
     * LEFT/RIGHT are reserved for cluster card navigation, which the car performs itself.
     * They are deliberately never forwarded to themes (see THEME_GUIDE).
     */
    static boolean isCardNavigationKey(ClusterKey key) {
        return key == ClusterKey.LEFT || key == ClusterKey.RIGHT;
    }
}
