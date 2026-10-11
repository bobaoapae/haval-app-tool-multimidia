package android.os;

import java.util.ArrayList;
import java.util.List;

/** Manually drained FIFO queue, preserving Handler identity and Runnable cancellation. */
public class Handler {
    private static final List<Entry> QUEUE = new ArrayList<>();
    private final Looper looper;
    private static final class Entry {
        final Handler handler;
        final Runnable runnable;
        Entry(Handler handler, Runnable runnable) { this.handler = handler; this.runnable = runnable; }
    }
    public Handler(Looper looper) { this.looper = looper; }
    public Looper getLooper() { return looper; }
    public boolean post(Runnable runnable) {
        synchronized (QUEUE) { QUEUE.add(new Entry(this, runnable)); }
        return true;
    }
    public void removeCallbacks(Runnable runnable) {
        synchronized (QUEUE) { QUEUE.removeIf(entry -> entry.handler == this && entry.runnable == runnable); }
    }
    public void removeCallbacksAndMessages(Object token) {
        synchronized (QUEUE) { QUEUE.removeIf(entry -> entry.handler == this); }
    }
    public static int pendingCount() { synchronized (QUEUE) { return QUEUE.size(); } }
    public static void reset() { synchronized (QUEUE) { QUEUE.clear(); } }
    public static void drain() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new AssertionError("Drain requires main thread");
        for (int count = 0; ; count++) {
            Entry entry;
            synchronized (QUEUE) {
                if (QUEUE.isEmpty()) return;
                entry = QUEUE.remove(0);
            }
            if (count > 1000) throw new AssertionError("Unbounded handler work");
            if (entry.handler.looper != Looper.getMainLooper()) throw new AssertionError("Expected main handler");
            entry.runnable.run();
        }
    }
}
