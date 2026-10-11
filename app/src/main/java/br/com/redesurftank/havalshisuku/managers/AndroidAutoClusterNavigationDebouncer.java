package br.com.redesurftank.havalshisuku.managers;

import java.util.Objects;

/**
 * Keeps navigation demand through a brief guidance gap. Repeated inactive
 * telemetry belongs to the same gap and must not move its original deadline.
 * This policy does not infer session or navigation evidence.
 */
public final class AndroidAutoClusterNavigationDebouncer {
    public interface Scheduler {
        void postDelayed(Runnable callback, long delayMs);
        void removeCallbacks(Runnable callback);
    }

    public interface Listener {
        void onActiveChanged(boolean active);
    }

    private final long hideDelayMs;
    private final Scheduler scheduler;
    private final Listener listener;
    private boolean active;
    private Runnable pendingHide;

    public AndroidAutoClusterNavigationDebouncer(long hideDelayMs, Scheduler scheduler, Listener listener) {
        if (hideDelayMs <= 0) throw new IllegalArgumentException("hideDelayMs must be positive");
        this.hideDelayMs = hideDelayMs;
        this.scheduler = Objects.requireNonNull(scheduler);
        this.listener = Objects.requireNonNull(listener);
    }

    public synchronized boolean isActive() {
        return active;
    }

    /** Binder updates and the main-thread timer share this lock. */
    public synchronized void onNavigationActive(boolean navigationActive) {
        if (navigationActive) {
            cancelPendingHide();
            if (!active) {
                active = true;
                listener.onActiveChanged(true);
            }
        } else if (active && pendingHide == null) {
            pendingHide = new Runnable() {
                @Override public void run() {
                    synchronized (AndroidAutoClusterNavigationDebouncer.this) {
                        // Removal alone cannot fence a callback already dequeued
                        // before reactivation, session reset or a newer gap.
                        if (pendingHide != this) return;
                        pendingHide = null;
                        active = false;
                        listener.onActiveChanged(false);
                    }
                }
            };
            scheduler.postDelayed(pendingHide, hideDelayMs);
        }
    }

    /** Session teardown/start baseline clears demand without publishing a transition. */
    public synchronized void reset() {
        cancelPendingHide();
        active = false;
    }

    private void cancelPendingHide() {
        Runnable previous = pendingHide;
        // Invalidate first, including if removal races a dequeued callback.
        pendingHide = null;
        if (previous != null) scheduler.removeCallbacks(previous);
    }
}
