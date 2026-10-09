package br.com.redesurftank.havalshisuku.api;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Executes only the real pure Java barrier; no Android or OEM implementation. */
public final class ClusterLeaseBarrierTest {
    private interface Check { void run() throws Exception; }
    private static int total;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void test(String name, Check action) throws Exception {
        action.run(); total++; System.out.println("PASS " + name);
    }
    private static void await(CountDownLatch latch) throws Exception {
        check(latch.await(5, TimeUnit.SECONDS), "latch timeout");
    }
    private static void join(Thread thread) throws Exception {
        thread.join(5000); check(!thread.isAlive(), "thread did not finish");
    }
    private static void rejected(Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Expected a retired/closed lease refusal");
    }
    private static Thread worker(AtomicReference<Throwable> failure, Check action) {
        Thread thread = new Thread(() -> {
            try { action.run(); } catch (Throwable problem) { failure.compareAndSet(null, problem); }
        });
        thread.setDaemon(true); thread.start(); return thread;
    }
    private static void healthy(AtomicReference<Throwable> failure) {
        if (failure.get() != null) throw new AssertionError("worker failed", failure.get());
    }

    public static void main(String[] args) throws Exception {
        test("owner alone releases exactly once", () -> {
            AtomicInteger callbacks = new AtomicInteger();
            ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(callbacks::incrementAndGet);
            check(barrier.referenceCount() == 1 && !barrier.isQuiescent(), "initial owner state");
            barrier.closeOwner(); barrier.closeOwner();
            check(callbacks.get() == 1 && barrier.referenceCount() == 0 && barrier.isQuiescent(), "owner completion");
        });
        test("closing tokens does not retire an open owner", () -> {
            AtomicInteger callbacks = new AtomicInteger();
            ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(callbacks::incrementAndGet);
            ClusterLeaseBarrier.Token token = barrier.borrow(); token.close(); token.close();
            check(barrier.referenceCount() == 1 && callbacks.get() == 0, "owner still protects consumer");
            ClusterLeaseBarrier.Token another = barrier.borrow(); another.close(); barrier.closeOwner();
            check(callbacks.get() == 1, "completion after owner retirement");
        });
        test("forced detach retains remote and in-flight borrows", () -> {
            AtomicInteger callbacks = new AtomicInteger();
            ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(callbacks::incrementAndGet);
            ClusterLeaseBarrier.Token remote = barrier.borrow(), inFlight = remote.fork();
            barrier.closeOwner();
            check(barrier.referenceCount() == 2 && callbacks.get() == 0, "detach released remote consumer");
            inFlight.close();
            check(barrier.referenceCount() == 1 && callbacks.get() == 0, "IPC return is not remote settlement");
            remote.close(); check(barrier.isQuiescent() && callbacks.get() == 1, "ACK did not settle");
        });
        test("ACK before transact return retains in-flight borrow", () -> {
            AtomicInteger callbacks = new AtomicInteger();
            ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(callbacks::incrementAndGet);
            ClusterLeaseBarrier.Token remote = barrier.borrow(), inFlight = remote.fork();
            barrier.closeOwner(); remote.close();
            check(callbacks.get() == 0 && barrier.referenceCount() == 1, "early ACK released IPC borrow");
            inFlight.close(); check(callbacks.get() == 1 && barrier.isQuiescent(), "IPC completion missing");
        });
        test("ACK does not release an attached consumer owner", () -> {
            AtomicInteger callbacks = new AtomicInteger();
            ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(callbacks::incrementAndGet);
            barrier.borrow().close();
            check(callbacks.get() == 0 && !barrier.isQuiescent(), "ACK released attached renderer");
            barrier.closeOwner(); check(callbacks.get() == 1, "detach did not finish");
        });
        test("live token may fork after owner retirement", () -> {
            AtomicInteger callbacks = new AtomicInteger();
            ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(callbacks::incrementAndGet);
            ClusterLeaseBarrier.Token first = barrier.borrow(); barrier.closeOwner();
            rejected(barrier::borrow);
            ClusterLeaseBarrier.Token second = first.fork(); first.close();
            check(callbacks.get() == 0 && barrier.referenceCount() == 1, "fork not retained");
            second.close(); check(callbacks.get() == 1, "fork not settled");
        });
        test("closed token cannot resurrect before or after callback", () -> {
            ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(() -> {});
            ClusterLeaseBarrier.Token token = barrier.borrow(); token.close();
            rejected(token::fork); barrier.closeOwner(); rejected(token::fork); rejected(barrier::borrow);
            check(barrier.referenceCount() == 0, "closed fork changed references");
        });
        test("stale and duplicate ACK cannot close another generation", () -> {
            AtomicInteger oldCalls = new AtomicInteger(), newCalls = new AtomicInteger();
            ClusterLeaseBarrier old = new ClusterLeaseBarrier(oldCalls::incrementAndGet);
            ClusterLeaseBarrier current = new ClusterLeaseBarrier(newCalls::incrementAndGet);
            ClusterLeaseBarrier.Token oldRemote = old.borrow(), currentRemote = current.borrow();
            old.closeOwner(); current.closeOwner(); oldRemote.close(); oldRemote.close();
            check(oldCalls.get() == 1 && newCalls.get() == 0 && current.referenceCount() == 1, "cross-generation close");
            currentRemote.close(); check(newCalls.get() == 1, "current generation settlement missing");
        });
        test("uncertain close retains until explicit settlement", () -> {
            AtomicInteger callbacks = new AtomicInteger();
            ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(callbacks::incrementAndGet);
            ClusterLeaseBarrier.Token uncertain = barrier.borrow(); barrier.closeOwner();
            for (int i = 0; i < 100; i++) {
                barrier.closeOwner();
                check(barrier.referenceCount() == 1 && !barrier.isQuiescent() && callbacks.get() == 0, "uncertainty auto-released");
            }
            uncertain.close(); check(callbacks.get() == 1, "explicit settlement missing");
        });
        test("callback may reenter barrier", () -> {
            AtomicReference<ClusterLeaseBarrier> ref = new AtomicReference<>();
            AtomicReference<ClusterLeaseBarrier.Token> token = new AtomicReference<>();
            AtomicInteger calls = new AtomicInteger();
            ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(() -> {
                calls.incrementAndGet(); ref.get().closeOwner(); token.get().close();
                rejected(ref.get()::borrow); rejected(token.get()::fork);
                check(ref.get().referenceCount() == 0 && !ref.get().isQuiescent(), "callback falsely complete during execution");
            });
            ref.set(barrier); token.set(barrier.borrow()); barrier.closeOwner(); token.get().close();
            check(calls.get() == 1 && barrier.isQuiescent(), "reentrant callback failed");
        });
        test("callback runs outside lock and success waits for completion", () -> {
            CountDownLatch entered = new CountDownLatch(1), complete = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(() -> {
                entered.countDown();
                try { await(complete); } catch (Exception problem) { throw new AssertionError(problem); }
            });
            Thread closer = worker(failure, barrier::closeOwner); await(entered);
            Thread reader = worker(failure, () -> {
                check(barrier.referenceCount() == 0 && !barrier.isQuiescent(), "callback running state");
                barrier.closeOwner(); rejected(barrier::borrow);
            });
            try { join(reader); healthy(failure); } finally { complete.countDown(); }
            join(closer); healthy(failure); check(barrier.isQuiescent(), "successful callback not published");
        });
        test("callback exception retained and never retried", () -> {
            AtomicInteger attempts = new AtomicInteger();
            RuntimeException expected = new RuntimeException("release uncertain");
            ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(() -> { attempts.incrementAndGet(); throw expected; });
            ClusterLeaseBarrier.Token token = barrier.borrow(); barrier.closeOwner(); token.close(); token.close(); barrier.closeOwner();
            check(attempts.get() == 1 && barrier.callbackFailure() == expected && !barrier.isQuiescent(), "failed disposal reported success/retried");
            check(barrier.referenceCount() == 0, "failed callback changed reference accounting");
            rejected(token::fork);
        });
        test("callback Error also remains failed without retry", () -> {
            AssertionError expected = new AssertionError("native wrapper failure");
            ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(() -> { throw expected; });
            barrier.closeOwner(); barrier.closeOwner();
            check(barrier.callbackFailure() == expected && !barrier.isQuiescent(), "Error hidden as success");
        });
        test("null callback rejected before acquisition", () -> {
            try { new ClusterLeaseBarrier(null); } catch (NullPointerException expected) { return; }
            throw new AssertionError("null callback accepted");
        });
        test("fork-before-close interleaving remains retained", () -> {
            AtomicInteger calls = new AtomicInteger(); AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicReference<ClusterLeaseBarrier.Token> fork = new AtomicReference<>();
            CountDownLatch forked = new CountDownLatch(1), closed = new CountDownLatch(1);
            ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(calls::incrementAndGet);
            ClusterLeaseBarrier.Token token = barrier.borrow(); barrier.closeOwner();
            Thread forker = worker(failure, () -> { fork.set(token.fork()); forked.countDown(); await(closed); fork.get().close(); });
            Thread closer = worker(failure, () -> { await(forked); token.close(); check(calls.get() == 0, "fork released early"); closed.countDown(); });
            join(forker); join(closer); healthy(failure); check(calls.get() == 1, "missing final callback");
        });
        test("close-before-fork interleaving rejects resurrection", () -> {
            AtomicInteger calls = new AtomicInteger(); AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch closed = new CountDownLatch(1);
            ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(calls::incrementAndGet);
            ClusterLeaseBarrier.Token token = barrier.borrow(); barrier.closeOwner();
            Thread closer = worker(failure, () -> { token.close(); closed.countDown(); });
            Thread forker = worker(failure, () -> { await(closed); rejected(token::fork); });
            join(closer); join(forker); healthy(failure); check(calls.get() == 1 && barrier.referenceCount() == 0, "resurrection race");
        });
        test("simultaneous close/fork has only linearizable outcomes", () -> {
            for (int i = 0; i < 100; i++) {
                AtomicInteger calls = new AtomicInteger(); AtomicReference<Throwable> failure = new AtomicReference<>();
                CountDownLatch start = new CountDownLatch(1), ready = new CountDownLatch(2);
                ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(calls::incrementAndGet);
                ClusterLeaseBarrier.Token token = barrier.borrow(); barrier.closeOwner();
                Thread closer = worker(failure, () -> { ready.countDown(); await(start); token.close(); });
                Thread forker = worker(failure, () -> {
                    ready.countDown(); await(start);
                    ClusterLeaseBarrier.Token fork;
                    try { fork = token.fork(); } catch (IllegalStateException alreadyClosed) { return; }
                    check(calls.get() == 0, "callback raced a successful borrow"); fork.close();
                });
                await(ready); start.countDown(); join(closer); join(forker); healthy(failure);
                check(calls.get() == 1 && barrier.referenceCount() == 0 && barrier.isQuiescent(), "non-linearizable final state");
            }
        });
        test("simultaneous duplicate close is exactly once", () -> {
            AtomicInteger calls = new AtomicInteger(); AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch start = new CountDownLatch(1);
            ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(calls::incrementAndGet);
            ClusterLeaseBarrier.Token token = barrier.borrow(); barrier.closeOwner();
            Thread a = worker(failure, () -> { await(start); token.close(); });
            Thread b = worker(failure, () -> { await(start); token.close(); });
            start.countDown(); join(a); join(b); healthy(failure);
            check(calls.get() == 1 && barrier.referenceCount() == 0, "duplicate close decremented twice");
        });
        System.out.println("PASS total=" + total + " lease barrier checks");
    }
}
