package br.com.redesurftank.havalshisuku.managers;

import br.com.redesurftank.havalshisuku.models.ClusterKey;

final class ClusterCardNavigationPolicy {
    private static final int ACTION_UP = 1;
    private static final int[] CARD_SEQUENCE = new int[] {0, 1, 3};

    private ClusterCardNavigationPolicy() {}

    static boolean shouldHandleInputAction(int action) {
        // The BeanTech input service on this head unit reports cluster-wheel
        // presses as release-only events. Handling ACTION_DOWN drops real input.
        return action == ACTION_UP;
    }

    static boolean isCardNavigationKey(ClusterKey key) {
        return key == ClusterKey.LEFT || key == ClusterKey.RIGHT;
    }

    static int nextCard(int currentCard, ClusterKey key) {
        int currentIndex = indexOf(currentCard);
        if (currentIndex < 0) currentIndex = 0;

        int direction = key == ClusterKey.RIGHT ? 1 : -1;
        int nextIndex = (currentIndex + direction + CARD_SEQUENCE.length) % CARD_SEQUENCE.length;
        return CARD_SEQUENCE[nextIndex];
    }

    private static int indexOf(int card) {
        for (int i = 0; i < CARD_SEQUENCE.length; i++) {
            if (CARD_SEQUENCE[i] == card) return i;
        }
        return -1;
    }
}
