package br.com.redesurftank.havalshisuku.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-minute counter of Shizuku shell commands grouped by caller + command head.
 *
 * <p>Diagnostic for the shizuku_server heap growth (see .ai-context/HANDOFF.md): every
 * {@code newProcess} costs the server memory, so we need to know who sends how many.
 * Pure Java (no Android types) so the aggregation and report format are unit-testable;
 * {@link ShizukuUtils} feeds it and logs the report.
 */
public final class ShizukuCommandStats {

    public static final long WINDOW_MS = 60_000L;
    static final int MAX_KEYS = 200;
    static final String OVERFLOW_KEY = "<other>";
    private static final int MAX_HEAD_TOKENS = 3;
    private static final int MAX_HEAD_CHARS = 48;
    private static final int CALLER_FRAMES = 3;

    private static final class Entry {
        int count;
        int timed;
        long totalMs;
        long maxMs;
        int failures;
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private long windowStartMs = -1;
    private int total;
    private int failures;

    /**
     * Records one command. Returns a finished report when this call closed a window of at
     * least {@link #WINDOW_MS}, otherwise null. No timer: a report is emitted by the first
     * command after the window ends, so an idle app logs nothing.
     *
     * @param durationMs wall time of the command, or a negative value when unknown (background)
     */
    public synchronized String record(String key, long durationMs, boolean failed, long nowMs) {
        String report = null;
        if (windowStartMs < 0) {
            windowStartMs = nowMs;
        } else if (nowMs - windowStartMs >= WINDOW_MS) {
            report = buildReport(nowMs - windowStartMs, 25);
            entries.clear();
            total = 0;
            failures = 0;
            windowStartMs = nowMs;
        }
        Entry e = entries.get(key);
        if (e == null) {
            if (entries.size() >= MAX_KEYS) key = OVERFLOW_KEY;
            e = entries.get(key);
            if (e == null) {
                e = new Entry();
                entries.put(key, e);
            }
        }
        e.count++;
        if (durationMs >= 0) {
            e.timed++;
            e.totalMs += durationMs;
            if (durationMs > e.maxMs) e.maxMs = durationMs;
        }
        if (failed) {
            e.failures++;
            failures++;
        }
        total++;
        return report;
    }

    synchronized String buildReport(long windowMs, int maxLines) {
        List<Map.Entry<String, Entry>> sorted = new ArrayList<>(entries.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue().count, a.getValue().count));
        double minutes = Math.max(windowMs, 1) / 60_000.0;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(java.util.Locale.US,
                "window=%ds total=%d rate=%.1f/min failures=%d keys=%d",
                windowMs / 1000, total, total / minutes, failures, entries.size()));
        int lines = 0;
        for (Map.Entry<String, Entry> me : sorted) {
            if (lines++ >= maxLines) break;
            Entry e = me.getValue();
            long avg = e.timed > 0 ? e.totalMs / e.timed : -1;
            sb.append(String.format(java.util.Locale.US,
                    "\n  %4d (%.1f/min) avg=%dms max=%dms fail=%d  %s",
                    e.count, e.count / minutes, avg, e.maxMs, e.failures, me.getKey()));
        }
        return sb.toString();
    }

    /**
     * Normalises a command into a short, low-cardinality head: for {@code sh -c "<script>"}
     * the script is used; first three tokens, digits collapsed to '#'.
     */
    public static String commandHead(String[] command) {
        if (command == null || command.length == 0) return "<empty>";
        String text;
        if (command.length >= 3 && "sh".equals(command[0]) && "-c".equals(command[1])) {
            text = command[2];
        } else {
            text = String.join(" ", command);
        }
        String[] tokens = text.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length && i < MAX_HEAD_TOKENS; i++) {
            if (i > 0) sb.append(' ');
            sb.append(tokens[i]);
        }
        String head = sb.toString().replaceAll("[0-9]+", "#");
        return head.length() > MAX_HEAD_CHARS ? head.substring(0, MAX_HEAD_CHARS) : head;
    }

    /**
     * First app frames outside the Shizuku helpers, e.g.
     * {@code DisplayAppLauncher.getStackList:8778 < BottomBarUIKt$...invokeSuspend:2914}.
     */
    public static String callerOf(StackTraceElement[] stack, String appPackagePrefix) {
        if (stack == null) return "<unknown>";
        StringBuilder sb = new StringBuilder();
        int found = 0;
        for (StackTraceElement f : stack) {
            String cls = f.getClassName();
            if (!cls.startsWith(appPackagePrefix)) continue;
            if (cls.endsWith(".ShizukuUtils") || cls.endsWith(".ShizukuCommandStats")) continue;
            if (found > 0) sb.append(" < ");
            String simple = cls.substring(cls.lastIndexOf('.') + 1);
            sb.append(simple).append('.').append(f.getMethodName()).append(':').append(f.getLineNumber());
            if (++found >= CALLER_FRAMES) break;
        }
        return found == 0 ? "<unknown>" : sb.toString();
    }
}
