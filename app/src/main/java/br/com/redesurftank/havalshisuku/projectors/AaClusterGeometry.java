package br.com.redesurftank.havalshisuku.projectors;

/** Pure AA-only viewport geometry. All results are panel-local LTRB rectangles. */
public final class AaClusterGeometry {
    public static final int PANEL_WIDTH = 1920;
    public static final int PANEL_HEIGHT = 720;
    public static final int NATIVE_CARD_LEFT = 1344;

    private AaClusterGeometry() { }

    /**
     * Custom LTRB takes precedence; theme XYWH supplies only the vertical window.
     * Missing geometry retains the existing full-width default. Invalid explicit
     * geometry and empty intersections fail closed, never widening to a fallback.
     */
    public static int[] resolve(String custom, int[] theme, boolean nativeCardShown) {
        // The existing preference contract uses an empty value for theme defaults.
        if (custom != null && !custom.trim().isEmpty()) {
            int[] parsed = parseCustom(custom);
            return parsed == null ? empty() : intersect(parsed, nativeCardShown);
        }
        if (theme == null) {
            return intersect(new int[] {0, 62, PANEL_WIDTH, 658}, nativeCardShown);
        }
        if (theme.length != 4 || theme[2] <= 0 || theme[3] <= 0) return empty();
        // Long arithmetic prevents malformed XYWH from wrapping before clipping.
        return intersect(0, theme[1], PANEL_WIDTH, (long) theme[1] + theme[3], nativeCardShown);
    }

    public static int[] parseCustom(String value) {
        if (value == null) return null;
        String[] parts = value.split(",", -1);
        if (parts.length != 4) return null;
        int[] bounds = new int[4];
        try {
            for (int i = 0; i < 4; i++) bounds[i] = Integer.parseInt(parts[i].trim());
        } catch (NumberFormatException invalid) {
            return null;
        }
        return bounds[0] >= 0 && bounds[1] >= 0
                && (long) bounds[2] - bounds[0] >= 100
                && (long) bounds[3] - bounds[1] >= 100 ? bounds : null;
    }

    public static int[] intersect(int[] bounds, boolean nativeCardShown) {
        if (bounds == null || bounds.length != 4) return empty();
        return intersect(bounds[0], bounds[1], bounds[2], bounds[3], nativeCardShown);
    }

    private static int[] intersect(long left, long top, long right, long bottom, boolean card) {
        long l = Math.max(0, left);
        long t = Math.max(0, top);
        long r = Math.min(card ? NATIVE_CARD_LEFT : PANEL_WIDTH, right);
        long b = Math.min(PANEL_HEIGHT, bottom);
        if (l >= r || t >= b) return empty();
        return new int[] {(int) l, (int) t, (int) r, (int) b};
    }

    private static int[] empty() { return new int[] {0, 0, 0, 0}; }
}
