package br.com.redesurftank.havalshisuku.managers;

import java.util.concurrent.atomic.AtomicBoolean;

/** Coalesces concurrent Binder-death/watchdog recovery triggers. */
final class ControlChannelRecoveryGate {
    private final AtomicBoolean acquired = new AtomicBoolean(false);

    boolean tryAcquire() {
        return acquired.compareAndSet(false, true);
    }

    void release() {
        acquired.set(false);
    }
}
