package br.com.redesurftank.havalshisuku.models;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps OEM BeanInput cluster keyCodes (1024+) to {@link ClusterKey}.
 * Source of truth for both physical InputService callbacks and test inject.
 */
public final class ClusterKeyMap {
    private static final Map<Integer, ClusterKey> BY_CODE;
    private static final Map<ClusterKey, Integer> BY_KEY;

    static {
        Map<Integer, ClusterKey> byCode = new LinkedHashMap<>();
        byCode.put(1024, ClusterKey.UP);
        byCode.put(1025, ClusterKey.DOWN);
        byCode.put(1026, ClusterKey.LEFT);
        byCode.put(1027, ClusterKey.RIGHT);
        byCode.put(1028, ClusterKey.ENTER);
        byCode.put(1029, ClusterKey.HOME);
        byCode.put(1030, ClusterKey.BACK);
        byCode.put(1033, ClusterKey.UP_LONG);
        byCode.put(1034, ClusterKey.DOWN_LONG);
        byCode.put(1037, ClusterKey.ENTER_LONG);
        byCode.put(1039, ClusterKey.BACK_LONG);
        BY_CODE = Collections.unmodifiableMap(byCode);

        Map<ClusterKey, Integer> byKey = new LinkedHashMap<>();
        for (Map.Entry<Integer, ClusterKey> e : byCode.entrySet()) {
            byKey.put(e.getValue(), e.getKey());
        }
        BY_KEY = Collections.unmodifiableMap(byKey);
    }

    private ClusterKeyMap() {}

    public static ClusterKey fromKeyCode(int keyCode) {
        return BY_CODE.get(keyCode);
    }

    public static Integer toKeyCode(ClusterKey key) {
        return key == null ? null : BY_KEY.get(key);
    }

    /**
     * Accepts enum name ({@code UP}) or keyCode decimal string ({@code 1024}).
     */
    public static ClusterKey fromNameOrCode(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return fromKeyCode(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            // fall through to name
        }
        try {
            return ClusterKey.valueOf(value.toUpperCase(Locale.US));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static Map<Integer, ClusterKey> allByCode() {
        return BY_CODE;
    }
}
