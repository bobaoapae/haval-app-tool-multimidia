package br.com.redesurftank.havalshisuku.managers;

public final class ClusterCardSyncPolicy {
    private static final int MAIN_MENU_CARD = 1;
    private static final int AIRCON_CARD = 3;
    private static final int CLUSTER_KEY_UP = 1024;
    private static final int CLUSTER_KEY_DOWN = 1025;
    private static final int CLUSTER_KEY_LEFT = 1026;
    private static final int CLUSTER_KEY_RIGHT = 1027;
    private static final int CLUSTER_KEY_ENTER = 1028;
    private static final int CLUSTER_KEY_HOME = 1029;
    private static final int CLUSTER_KEY_BACK = 1030;
    private static final int CLUSTER_KEY_BACK_LONG = 1039;
    private static final int CLUSTER_KEY_UP_LONG = 1033;
    private static final int CLUSTER_KEY_DOWN_LONG = 1034;
    private static final int CLUSTER_KEY_ENTER_LONG = 1037;
    private static final long NATIVE_CLUSTER_CARD_INPUT_WINDOW_MS = 2500L;
    private static final long AIRCON_CONTROL_ECHO_WINDOW_MS = 3500L;
    private static final long SYNTHETIC_CLUSTER_CARD_ECHO_WINDOW_MS = 1500L;

    private ClusterCardSyncPolicy() {
    }

    public static boolean shouldReleaseSyntheticAirconOwnershipForInput(int inputKeyCode) {
        return isAirconIntentionalExitInput(inputKeyCode);
    }

    public static boolean shouldIgnoreNativeClusterCardChanged(
            int previousCard,
            int nextCard,
            long sinceInputMs,
            int lastInputKeyCode,
            long sinceSyntheticMs,
            int lastSyntheticTarget
    ) {
        return shouldIgnoreNativeClusterCardChanged(
                previousCard,
                nextCard,
                sinceInputMs,
                lastInputKeyCode,
                sinceSyntheticMs,
                lastSyntheticTarget,
                false,
                false
        );
    }

    public static boolean shouldIgnoreNativeClusterCardChanged(
            int previousCard,
            int nextCard,
            long sinceInputMs,
            int lastInputKeyCode,
            long sinceSyntheticMs,
            int lastSyntheticTarget,
            boolean protectAirconProjectionExit
    ) {
        return shouldIgnoreNativeClusterCardChanged(
                previousCard,
                nextCard,
                sinceInputMs,
                lastInputKeyCode,
                sinceSyntheticMs,
                lastSyntheticTarget,
                protectAirconProjectionExit,
                false
        );
    }

    public static boolean shouldIgnoreNativeClusterCardChanged(
            int previousCard,
            int nextCard,
            long sinceInputMs,
            int lastInputKeyCode,
            long sinceSyntheticMs,
            int lastSyntheticTarget,
            boolean protectAirconProjectionExit,
            boolean protectSyntheticAirconExit
    ) {
        if (previousCard == nextCard) return true;

        if (isProtectedSyntheticAirconExitEcho(
                previousCard,
                nextCard,
                protectSyntheticAirconExit
        )) {
            return !isRecentAirconOwnershipReleaseInput(lastInputKeyCode, sinceInputMs);
        }

        if (isRecentSyntheticClusterCardNavigation(sinceSyntheticMs, lastSyntheticTarget)) {
            return nextCard != lastSyntheticTarget;
        }

        if (isAirconProjectionTransitionEcho(
                previousCard,
                nextCard,
                lastInputKeyCode,
                sinceInputMs,
                protectAirconProjectionExit
        )) {
            return true;
        }

        if (isAirconCardExitEcho(previousCard, nextCard, lastInputKeyCode, sinceInputMs)) {
            return true;
        }

        // O menu (card 1) e o A/C (card 3) são os cards que o OEM SOBE sozinho sobre o mapa: o menu é
        // o card default; o A/C sobe quando o OEM detecta atividade de clima — inclusive auto/passiva,
        // NAO so toque do usuario. As protecoes acima cobrem SAIR do A/C (previousCard == AIRCON), mas
        // nao a RE-ENTRADA espontanea (nextCard == menu/A/C vindo do mapa), que a linha abaixo
        // (nextCard != 0 -> honra) deixava passar — o painel "voltava sozinho" sobre o mapa. Sem
        // LEFT/RIGHT/HOME/BACK recente (nem nav sintetica recente, ja tratada acima), IGNORA a subida espontanea.
        // A navegacao de proposito pro card segue honrada pela janela sintetica/de input.
        if (previousCard == 0
                && (nextCard == MAIN_MENU_CARD || nextCard == AIRCON_CARD)
                && !isRecentAirconIntentionalExitInput(lastInputKeyCode, sinceInputMs)) {
            return true;
        }

        if (nextCard != 0) return false;
        if (previousCard != MAIN_MENU_CARD && previousCard != AIRCON_CARD) return false;
        if (previousCard == AIRCON_CARD
                && isRecentAirconIntentionalExitInput(lastInputKeyCode, sinceInputMs)) {
            return false;
        }
        return !isRecentClusterCardNavigationInput(lastInputKeyCode, sinceInputMs);
    }

    private static boolean isRecentSyntheticClusterCardNavigation(long sinceSyntheticMs, int lastSyntheticTarget) {
        return lastSyntheticTarget >= 0
                && sinceSyntheticMs >= 0L
                && sinceSyntheticMs <= SYNTHETIC_CLUSTER_CARD_ECHO_WINDOW_MS;
    }

    private static boolean isProtectedSyntheticAirconExitEcho(
            int previousCard,
            int nextCard,
            boolean protectSyntheticAirconExit
    ) {
        return protectSyntheticAirconExit
                && previousCard == AIRCON_CARD
                && (nextCard == MAIN_MENU_CARD || nextCard == 0);
    }

    private static boolean isRecentClusterCardNavigationInput(int lastInputKeyCode, long sinceInputMs) {
        return (lastInputKeyCode == CLUSTER_KEY_LEFT || lastInputKeyCode == CLUSTER_KEY_RIGHT)
                && sinceInputMs >= 0L
                && sinceInputMs <= NATIVE_CLUSTER_CARD_INPUT_WINDOW_MS;
    }

    private static boolean isAirconCardExitEcho(
            int previousCard,
            int nextCard,
            int lastInputKeyCode,
            long sinceInputMs
    ) {
        return previousCard == AIRCON_CARD
                && (nextCard == MAIN_MENU_CARD || nextCard == 0)
                && isRecentAirconControlInput(lastInputKeyCode, sinceInputMs);
    }

    private static boolean isAirconProjectionTransitionEcho(
            int previousCard,
            int nextCard,
            int lastInputKeyCode,
            long sinceInputMs,
            boolean protectAirconProjectionExit
    ) {
        return protectAirconProjectionExit
                && previousCard == AIRCON_CARD
                && (nextCard == MAIN_MENU_CARD || nextCard == 0)
                && !isRecentAirconIntentionalExitInput(lastInputKeyCode, sinceInputMs);
    }

    private static boolean isRecentAirconIntentionalExitInput(int lastInputKeyCode, long sinceInputMs) {
        return isAirconIntentionalExitInput(lastInputKeyCode)
                && sinceInputMs >= 0L
                && sinceInputMs <= NATIVE_CLUSTER_CARD_INPUT_WINDOW_MS;
    }

    private static boolean isRecentAirconOwnershipReleaseInput(int lastInputKeyCode, long sinceInputMs) {
        return (lastInputKeyCode == CLUSTER_KEY_HOME || lastInputKeyCode == CLUSTER_KEY_BACK)
                && sinceInputMs >= 0L
                && sinceInputMs <= NATIVE_CLUSTER_CARD_INPUT_WINDOW_MS;
    }

    private static boolean isAirconIntentionalExitInput(int lastInputKeyCode) {
        return lastInputKeyCode == CLUSTER_KEY_LEFT
                || lastInputKeyCode == CLUSTER_KEY_RIGHT
                || lastInputKeyCode == CLUSTER_KEY_HOME
                || lastInputKeyCode == CLUSTER_KEY_BACK;
    }

    private static boolean isRecentAirconControlInput(int lastInputKeyCode, long sinceInputMs) {
        return isAirconControlInput(lastInputKeyCode)
                && sinceInputMs >= 0L
                && sinceInputMs <= AIRCON_CONTROL_ECHO_WINDOW_MS;
    }

    private static boolean isAirconControlInput(int lastInputKeyCode) {
        return lastInputKeyCode == CLUSTER_KEY_UP
                || lastInputKeyCode == CLUSTER_KEY_DOWN
                || lastInputKeyCode == CLUSTER_KEY_ENTER
                || lastInputKeyCode == CLUSTER_KEY_UP_LONG
                || lastInputKeyCode == CLUSTER_KEY_DOWN_LONG
                || lastInputKeyCode == CLUSTER_KEY_ENTER_LONG
                || lastInputKeyCode == CLUSTER_KEY_BACK_LONG;
    }
}
