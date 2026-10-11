package br.com.redesurftank.havalshisuku.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Deterministic JVM checks of the production policy; no Android/OEM execution. */
public final class AaClusterNavigationDebouncerHarness {
    private static final long DELAY = 5_000;
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Entry {
        final Runnable callback;
        final long due;
        boolean removed;
        boolean delivered;

        Entry(Runnable callback, long due) {
            this.callback = callback;
            this.due = due;
        }
    }

    private static final class ClockScheduler implements AndroidAutoClusterNavigationDebouncer.Scheduler {
        long now;
        int removals;
        boolean invokeDuringRemoval;
        final List<Entry> entries = new ArrayList<>();

        @Override public void postDelayed(Runnable callback, long delayMs) {
            entries.add(new Entry(callback, now + delayMs));
        }

        @Override public void removeCallbacks(Runnable callback) {
            removals++;
            for (Entry entry : entries) {
                if (entry.callback == callback) entry.removed = true;
            }
            // Exercise invalidation even if a scheduler delivers while removing.
            if (invokeDuringRemoval) callback.run();
        }

        void advanceTo(long target) {
            check(target >= now, "clock stays monotonic");
            for (;;) {
                Entry next = null;
                for (Entry entry : entries) {
                    if (!entry.removed && !entry.delivered && entry.due <= target
                            && (next == null || entry.due < next.due)) next = entry;
                }
                if (next == null) break;
                now = next.due;
                next.delivered = true;
                next.callback.run();
            }
            now = target;
        }

        int pendingCount() {
            int count = 0;
            for (Entry entry : entries) if (!entry.removed && !entry.delivered) count++;
            return count;
        }
    }

    private static final class Fixture {
        final ClockScheduler scheduler = new ClockScheduler();
        final List<Boolean> transitions = new ArrayList<>();
        final AndroidAutoClusterNavigationDebouncer policy = new AndroidAutoClusterNavigationDebouncer(
                DELAY, scheduler, transitions::add);

        void expect(boolean active, int transitionCount, String message) {
            check(policy.isActive() == active, message + ": active");
            check(transitions.size() == transitionCount, message + ": transitions");
            check(scheduler.pendingCount() <= 1, message + ": at most one pending timer");
        }
    }

    private static void initialAndDuplicateActive() {
        Fixture f = new Fixture();
        f.expect(false, 0, "initial baseline");
        for (int i = 0; i < 10; i++) f.policy.onNavigationActive(false);
        f.expect(false, 0, "inactive baseline is not navigation evidence");
        check(f.scheduler.entries.isEmpty(), "baseline has no timer");
        for (int i = 0; i < 10; i++) f.policy.onNavigationActive(true);
        f.expect(true, 1, "duplicate guidance publishes one transition");
        check(f.transitions.get(0), "first transition active");
        check(f.scheduler.entries.isEmpty(), "active has no timer");
    }

    private static void repeatedInactiveKeepsDeadline() {
        Fixture f = new Fixture();
        f.scheduler.advanceTo(100);
        f.policy.onNavigationActive(true);
        f.scheduler.advanceTo(200);
        f.policy.onNavigationActive(false);
        check(f.scheduler.entries.get(0).due == 5_200, "deadline begins at first inactive callback");
        for (long time : new long[] {200, 201, 500, 1_000, 2_000, 3_000, 4_000, 5_000, 5_199}) {
            f.scheduler.advanceTo(time);
            f.policy.onNavigationActive(false);
            f.expect(true, 1, "grace period at " + time);
        }
        f.scheduler.advanceTo(5_200);
        f.expect(false, 2, "hidden at original deadline despite sub-five-second callbacks");
        check(f.scheduler.entries.size() == 1, "inactive stream schedules exactly once");
        check(f.scheduler.removals == 0, "inactive stream never cancels its deadline");
        check(!f.transitions.get(1), "expiry publishes inactive");
        for (int i = 0; i < 100; i++) {
            f.scheduler.advanceTo(5_200 + i * 1_000L);
            f.policy.onNavigationActive(false);
        }
        f.expect(false, 2, "continuous inactive stream stays hidden");
        check(f.scheduler.entries.size() == 1, "post-expiry inactive callbacks never rearm");
    }

    private static void reactivationCancelsAndStartsFreshGap() {
        Fixture f = new Fixture();
        f.policy.onNavigationActive(true);
        f.policy.onNavigationActive(false);
        Entry old = f.scheduler.entries.get(0);
        f.scheduler.advanceTo(4_999);
        f.policy.onNavigationActive(true);
        f.expect(true, 1, "short gap never toggles demand");
        check(old.removed, "reactivation removes pending timer");
        old.callback.run();
        f.expect(true, 1, "dequeued cancelled callback cannot hide resumed guidance");
        f.scheduler.advanceTo(5_000);
        f.policy.onNavigationActive(false);
        check(f.scheduler.entries.get(1).due == 10_000, "new gap receives a fresh deadline");
        old.callback.run();
        f.scheduler.advanceTo(9_999);
        f.expect(true, 1, "older timer cannot affect a newer gap");
        f.scheduler.advanceTo(10_000);
        f.expect(false, 2, "new gap still expires once");
    }

    private static void resetAndReconnect() {
        Fixture f = new Fixture();
        f.policy.onNavigationActive(true);
        f.policy.onNavigationActive(false);
        Entry disconnected = f.scheduler.entries.get(0);
        f.scheduler.advanceTo(1_000);
        f.policy.reset();
        f.expect(false, 1, "session stop silently clears navigation demand");
        check(disconnected.removed, "session stop cancels its timer");
        f.policy.reset();
        disconnected.callback.run();
        f.expect(false, 1, "duplicate teardown and stale timer preserve baseline");
        check(f.scheduler.removals == 1, "repeated reset does not cancel twice");
        f.policy.onNavigationActive(false);
        f.expect(false, 1, "new session needs navigation evidence");
        f.policy.onNavigationActive(true);
        f.policy.onNavigationActive(false);
        Entry current = f.scheduler.entries.get(1);
        disconnected.callback.run();
        f.expect(true, 2, "old-session callback cannot hide new-session guidance");
        f.scheduler.advanceTo(current.due - 1);
        f.expect(true, 2, "reconnected gap retains its full delay");
        f.scheduler.advanceTo(current.due);
        f.expect(false, 3, "reconnected gap expires normally");
        disconnected.callback.run();
        current.callback.run();
        f.expect(false, 3, "duplicate expired session callbacks are harmless");
    }

    private static void resetWithoutRestart() {
        Fixture f = new Fixture();
        f.policy.onNavigationActive(true);
        f.policy.onNavigationActive(false);
        Entry stopped = f.scheduler.entries.get(0);
        f.policy.reset();
        f.scheduler.advanceTo(100_000);
        stopped.callback.run();
        f.expect(false, 1, "teardown without restart leaves no live timer");
        check(f.scheduler.pendingCount() == 0, "shutdown boundary cancels pending work");
        // The controller currently has no public shutdown lifecycle. reset is
        // the tested teardown primitive, not a claim of new lifecycle wiring.
    }

    private static void duplicateExpiredCallbacks() {
        Fixture f = new Fixture();
        f.policy.onNavigationActive(true);
        f.policy.onNavigationActive(false);
        Entry expired = f.scheduler.entries.get(0);
        f.scheduler.advanceTo(DELAY);
        for (int i = 0; i < 10; i++) expired.callback.run();
        f.expect(false, 2, "expiry callback is one-shot");
        f.policy.onNavigationActive(true);
        for (int i = 0; i < 10; i++) expired.callback.run();
        f.expect(true, 3, "completed callback cannot hide later guidance");
    }

    private static void cancelInvalidatesBeforeRemoval() {
        Fixture f = new Fixture();
        f.scheduler.invokeDuringRemoval = true;
        f.policy.onNavigationActive(true);
        f.policy.onNavigationActive(false);
        f.policy.onNavigationActive(true);
        f.expect(true, 1, "reactivation invalidates before scheduler cancellation");
        f.policy.onNavigationActive(false);
        f.policy.reset();
        f.expect(false, 1, "reset invalidates before scheduler cancellation");
    }

    private static void queuedCallbackRace() throws Exception {
        for (boolean reset : new boolean[] {false, true}) {
            Fixture f = new Fixture();
            f.policy.onNavigationActive(true);
            f.policy.onNavigationActive(false);
            Runnable stale = f.scheduler.entries.get(0).callback;
            CountDownLatch started = new CountDownLatch(1);
            Thread callback = new Thread(() -> {
                started.countDown();
                stale.run();
            }, "cancelled-navigation-callback");
            synchronized (f.policy) {
                callback.start();
                check(started.await(5, TimeUnit.SECONDS), "callback reached dispatch boundary");
                if (reset) f.policy.reset();
                f.policy.onNavigationActive(true);
                f.policy.onNavigationActive(false);
            }
            callback.join(5_000);
            check(!callback.isAlive(), "cancelled callback completes without deadlock");
            f.expect(true, reset ? 2 : 1, "dequeued older callback cannot hide newer gap");
            check(f.scheduler.pendingCount() == 1, "newer timer survives old callback race");
            f.scheduler.advanceTo(DELAY);
            f.expect(false, reset ? 3 : 2, "new timer expires after old callback race");
        }
    }

    private static void modelSequences() {
        Random random = new Random(24_20261009L);
        Fixture f = new Fixture();
        boolean expectedActive = false;
        long deadline = -1;
        int transitions = 0;
        for (int step = 0; step < 10_000; step++) {
            int event = random.nextInt(5);
            if (event == 0) {
                long nextTime = f.scheduler.now + random.nextInt(6_001);
                if (deadline >= 0 && deadline <= nextTime) {
                    expectedActive = false;
                    deadline = -1;
                    transitions++;
                }
                f.scheduler.advanceTo(nextTime);
            } else if (event == 1) {
                f.policy.onNavigationActive(true);
                if (!expectedActive) transitions++;
                expectedActive = true;
                deadline = -1;
            } else if (event == 2) {
                f.policy.onNavigationActive(false);
                if (expectedActive && deadline < 0) deadline = f.scheduler.now + DELAY;
            } else if (event == 3) {
                f.policy.reset();
                expectedActive = false;
                deadline = -1;
            } else if (!f.scheduler.entries.isEmpty()) {
                Entry old = f.scheduler.entries.get(random.nextInt(f.scheduler.entries.size()));
                if (old.removed || old.delivered) old.callback.run();
            }
            f.expect(expectedActive, transitions, "model step " + step);
            check(f.scheduler.pendingCount() == (deadline < 0 ? 0 : 1), "model pending at " + step);
            for (Entry entry : f.scheduler.entries) {
                if (!entry.removed && !entry.delivered) check(entry.due == deadline, "model deadline at " + step);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        initialAndDuplicateActive();
        repeatedInactiveKeepsDeadline();
        reactivationCancelsAndStartsFreshGap();
        resetAndReconnect();
        resetWithoutRestart();
        duplicateExpiredCallbacks();
        cancelInvalidatesBeforeRemoval();
        queuedCallbackRace();
        modelSequences();
        System.out.println("PASS total=" + checks + " navigation debounce checks in 9 scenarios");
    }
}
