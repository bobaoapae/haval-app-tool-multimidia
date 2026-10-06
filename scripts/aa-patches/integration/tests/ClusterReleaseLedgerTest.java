package br.com.redesurftank.havalshisuku.api;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Actual ledger plus barrier; no Android, Binder or OEM classes are executed. */
public final class ClusterReleaseLedgerTest {
    private interface Check { void run() throws Exception; }
    private static int total;
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
    private static void test(String name, Check action) throws Exception {
        action.run(); total++; System.out.println("PASS " + name);
    }
    private static void await(CountDownLatch latch) throws Exception {
        check(latch.await(5, TimeUnit.SECONDS), "latch timeout");
    }
    private static void join(Thread thread) throws Exception {
        thread.join(5000); check(!thread.isAlive(), "thread stuck");
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
    private static final class Lease implements AutoCloseable {
        final AtomicInteger released = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        final ClusterLeaseBarrier barrier = new ClusterLeaseBarrier(released::incrementAndGet);
        final ClusterLeaseBarrier.Token remote = barrier.borrow();
        Check beforeClose;
        @Override public void close() throws Exception {
            closes.incrementAndGet();
            if (beforeClose != null) beforeClose.run();
            remote.close();
        }
    }
    private static final class EqualConnection {
        @Override public boolean equals(Object other) { return other instanceof EqualConnection; }
        @Override public int hashCode() { return 1; }
    }
    private static final class Fixture {
        final Object connection = new Object();
        final ClusterReleaseLedger<Lease> ledger = new ClusterReleaseLedger<>();
        final Lease lease = new Lease();
        final ClusterReleaseLedger.Entry<Lease> entry = ledger.claim(connection, 10, 50, lease);
    }

    public static void main(String[] args) throws Exception {
        test("exact authenticated identity closes only its borrow", () -> {
            Fixture f = new Fixture(); f.lease.barrier.closeOwner();
            check(f.entry.connection == f.connection && f.entry.request == 10 && f.entry.revision == 50, "claim identity changed");
            check(f.ledger.released(f.connection, 10), "exact release rejected");
            check(f.ledger.active() == null && f.lease.closes.get() == 1 && f.lease.barrier.isQuiescent(), "exact release incomplete");
        });
        test("connection identity is not equals equality", () -> {
            ClusterReleaseLedger<Lease> ledger = new ClusterReleaseLedger<>();
            EqualConnection a = new EqualConnection(), b = new EqualConnection(); Lease lease = new Lease();
            ledger.claim(a, 1, 1, lease); lease.barrier.closeOwner();
            check(!ledger.released(b, 1) && ledger.active() != null && lease.closes.get() == 0, "wrong equal connection released");
            check(ledger.released(a, 1), "original connection rejected");
        });
        test("wrong request cannot release a matching connection", () -> {
            Fixture f = new Fixture(); f.lease.barrier.closeOwner();
            check(!f.ledger.released(f.connection, 9) && !f.ledger.released(f.connection, 11), "wrong request released");
            check(f.ledger.active() == f.entry && f.lease.closes.get() == 0, "wrong request changed ownership");
        });
        test("duplicate terminal callback closes exactly once", () -> {
            Fixture f = new Fixture(); f.lease.barrier.closeOwner();
            check(f.ledger.released(f.connection, 10), "first ACK failed");
            check(!f.ledger.released(f.connection, 10) && f.lease.closes.get() == 1, "duplicate ACK retried close");
        });
        test("stale release cannot affect a newer output", () -> {
            Fixture f = new Fixture(); f.lease.barrier.closeOwner(); f.ledger.released(f.connection, 10);
            Lease next = new Lease(); next.barrier.closeOwner();
            ClusterReleaseLedger.Entry<Lease> current = f.ledger.claim(f.connection, 11, 51, next);
            check(!f.ledger.released(f.connection, 10) && f.ledger.active() == current && next.closes.get() == 0, "stale ACK closed replacement");
            check(f.ledger.released(f.connection, 11), "replacement ACK failed");
        });
        test("retirement is idempotent and not settlement", () -> {
            Fixture f = new Fixture(); f.lease.barrier.closeOwner();
            check(f.ledger.beginRetirement(f.entry) && !f.ledger.beginRetirement(f.entry), "retirement not idempotent");
            check(f.ledger.isRetiring(f.entry) && !f.ledger.isUncertain(f.entry), "retirement state wrong");
            check(f.ledger.active() == f.entry && f.lease.closes.get() == 0 && !f.lease.barrier.isQuiescent(), "retirement released consumer");
        });
        test("stale entry cannot retire or quarantine replacement", () -> {
            Fixture f = new Fixture(); f.lease.barrier.closeOwner(); f.ledger.released(f.connection, 10);
            Lease next = new Lease(); ClusterReleaseLedger.Entry<Lease> current = f.ledger.claim(f.connection, 11, 51, next);
            check(!f.ledger.beginRetirement(f.entry), "stale retirement accepted"); f.ledger.quarantine(f.entry);
            check(!f.ledger.isRetiring(current) && !f.ledger.isUncertain(current), "stale entry changed replacement");
        });
        test("uncertainty timeout disconnect and death retain ownership", () -> {
            Fixture f = new Fixture(); f.lease.barrier.closeOwner();
            for (int i = 0; i < 100; i++) f.ledger.quarantine(f.entry);
            check(f.ledger.active() == f.entry && f.ledger.isRetiring(f.entry) && f.ledger.isUncertain(f.entry), "uncertain entry lost");
            check(f.lease.closes.get() == 0 && f.lease.barrier.referenceCount() == 1 && !f.lease.barrier.isQuiescent(), "uncertainty treated as proof");
        });
        test("later exact terminal proof may settle quarantined output", () -> {
            Fixture f = new Fixture(); f.lease.barrier.closeOwner(); f.ledger.quarantine(f.entry);
            check(f.ledger.released(f.connection, 10) && f.lease.barrier.isQuiescent(), "exact proof cannot recover quarantine");
        });
        test("terminal ACK does not detach renderer ownership", () -> {
            Fixture f = new Fixture(); f.ledger.released(f.connection, 10);
            check(f.lease.barrier.referenceCount() == 1 && f.lease.released.get() == 0, "ACK released attached owner");
            f.lease.barrier.closeOwner(); check(f.lease.released.get() == 1, "renderer detach did not complete");
        });
        test("forced detach still waits for remote proof", () -> {
            Fixture f = new Fixture(); f.lease.barrier.closeOwner();
            check(f.lease.released.get() == 0, "forced detach released remote consumer");
            f.ledger.released(f.connection, 10); check(f.lease.released.get() == 1, "terminal proof missing");
        });
        test("early ACK waits for the transport fork to return", () -> {
            Fixture f = new Fixture(); ClusterLeaseBarrier.Token inFlight = f.lease.remote.fork();
            f.lease.barrier.closeOwner(); f.ledger.released(f.connection, 10);
            check(f.ledger.active() == null && f.lease.barrier.referenceCount() == 1 && f.lease.released.get() == 0, "early ACK released transport");
            inFlight.close(); check(f.lease.released.get() == 1 && f.lease.barrier.isQuiescent(), "transport did not settle");
        });
        test("occupied claim preserves caller ownership of rejected value", () -> {
            Fixture f = new Fixture(); Lease rejected = new Lease();
            check(f.ledger.claim(new Object(), 12, 52, rejected) == null, "second remote claim admitted");
            check(f.ledger.active() == f.entry && rejected.closes.get() == 0, "rejected claim consumed ownership");
            rejected.barrier.closeOwner(); rejected.close(); check(rejected.released.get() == 1, "caller could not dispose rejected value");
        });
        test("invalid identity cannot acquire ownership", () -> {
            ClusterReleaseLedger<Lease> ledger = new ClusterReleaseLedger<>(); Lease lease = new Lease();
            Object connection = new Object(); int refused = 0;
            try { ledger.claim(null, 1, 1, lease); } catch (IllegalArgumentException expected) { refused++; }
            try { ledger.claim(connection, 0, 1, lease); } catch (IllegalArgumentException expected) { refused++; }
            try { ledger.claim(connection, -1, 1, lease); } catch (IllegalArgumentException expected) { refused++; }
            try { ledger.claim(connection, 1, 1, null); } catch (IllegalArgumentException expected) { refused++; }
            check(refused == 4 && ledger.active() == null && lease.closes.get() == 0, "invalid identity acquired ownership");
        });
        test("throwing disposal is retained without retry or replacement", () -> {
            Fixture f = new Fixture(); f.lease.barrier.closeOwner();
            Exception expected = new Exception("uncertain disposal"); f.lease.beforeClose = () -> { throw expected; };
            boolean success = false;
            try { success = f.ledger.released(f.connection, 10); }
            catch (Exception failure) { check(failure == expected, "original failure changed"); }
            check(!success && f.ledger.active() == f.entry && f.ledger.isUncertain(f.entry), "failed disposal lost quarantine");
            check(!f.ledger.released(f.connection, 10) && f.lease.closes.get() == 1, "failed disposal retried");
            check(f.ledger.claim(new Object(), 11, 51, new Lease()) == null && f.lease.released.get() == 0, "failed disposal admitted replacement");
        });
        test("disposal Error also retains ownership without retry", () -> {
            Fixture f = new Fixture(); f.lease.barrier.closeOwner();
            AssertionError expected = new AssertionError("uncertain native wrapper"); f.lease.beforeClose = () -> { throw expected; };
            boolean success = false;
            try { success = f.ledger.released(f.connection, 10); }
            catch (AssertionError failure) { check(failure == expected, "original Error changed"); }
            check(!success && f.ledger.active() == f.entry && f.ledger.isUncertain(f.entry), "Error lost quarantine");
            check(!f.ledger.released(f.connection, 10) && f.lease.closes.get() == 1, "Error retried disposal");
        });
        test("duplicate ACK during disposal does not retry close", () -> {
            Fixture f = new Fixture(); f.lease.barrier.closeOwner();
            CountDownLatch entered = new CountDownLatch(1), complete = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            f.lease.beforeClose = () -> { entered.countDown(); await(complete); };
            Thread closer = worker(failure, () -> check(f.ledger.released(f.connection, 10), "first ACK failed"));
            await(entered);
            try { check(!f.ledger.released(f.connection, 10) && f.lease.closes.get() == 1, "concurrent ACK retried close"); }
            finally { complete.countDown(); }
            join(closer); healthy(failure); check(f.lease.released.get() == 1, "settlement did not finish");
        });
        test("claim remains blocked until disposal finishes", () -> {
            Fixture f = new Fixture(); f.lease.barrier.closeOwner();
            CountDownLatch entered = new CountDownLatch(1), complete = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>(); Lease next = new Lease();
            f.lease.beforeClose = () -> { entered.countDown(); await(complete); };
            Thread closer = worker(failure, () -> f.ledger.released(f.connection, 10)); await(entered);
            try {
                check(f.ledger.claim(new Object(), 11, 51, next) == null && f.ledger.active() == f.entry, "replacement raced disposal");
                check(f.ledger.isRetiring(f.entry), "terminal release still permits LIVE status during disposal");
            }
            finally { complete.countDown(); }
            join(closer); healthy(failure); check(f.ledger.claim(new Object(), 11, 51, next) != null, "settled slot not reusable");
        });
        test("disposal executes outside ledger monitor", () -> {
            Fixture f = new Fixture(); f.lease.barrier.closeOwner();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            f.lease.beforeClose = () -> {
                Thread reader = worker(failure, () -> check(f.ledger.active() == f.entry, "active disposal entry missing"));
                join(reader); healthy(failure);
            };
            check(f.ledger.released(f.connection, 10) && f.lease.released.get() == 1, "outside-lock disposal failed");
        });
        test("disposal may reenter inspection but cannot claim or retry", () -> {
            Fixture f = new Fixture(); f.lease.barrier.closeOwner();
            f.lease.beforeClose = () -> {
                check(f.ledger.active() == f.entry, "reentrant inspection lost entry");
                check(!f.ledger.released(f.connection, 10), "reentrant release retried");
                check(f.ledger.claim(new Object(), 11, 51, new Lease()) == null, "reentrant claim raced disposal");
            };
            check(f.ledger.released(f.connection, 10) && f.lease.closes.get() == 1, "reentrant settlement failed");
        });
        test("contending claims admit exactly one remote owner", () -> {
            ClusterReleaseLedger<Lease> ledger = new ClusterReleaseLedger<>();
            CountDownLatch ready = new CountDownLatch(8), start = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>(); AtomicInteger winners = new AtomicInteger();
            Thread[] threads = new Thread[8]; Lease[] leases = new Lease[8];
            for (int i = 0; i < threads.length; i++) {
                final int index = i; leases[i] = new Lease(); leases[i].barrier.closeOwner();
                threads[i] = worker(failure, () -> {
                    ready.countDown(); await(start);
                    if (ledger.claim(new Object(), index + 1, index + 1, leases[index]) != null) winners.incrementAndGet();
                });
            }
            await(ready); start.countDown(); for (Thread thread : threads) join(thread); healthy(failure);
            check(winners.get() == 1, "multiple remote owners admitted");
            ClusterReleaseLedger.Entry<Lease> winner = ledger.active();
            for (Lease lease : leases) check(lease.closes.get() == 0, "claim closed a caller-owned loser");
            ledger.released(winner.connection, winner.request);
            for (Lease lease : leases) if (lease != winner.value) lease.close();
            for (Lease lease : leases) check(lease.released.get() == 1, "claim ownership leaked or double-disposed");
        });
        System.out.println("PASS total=" + total + " release ledger checks");
    }
}
