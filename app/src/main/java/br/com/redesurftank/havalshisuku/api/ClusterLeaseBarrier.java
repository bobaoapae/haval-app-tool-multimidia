package br.com.redesurftank.havalshisuku.api;

/**
 * Platform-independent lifetime barrier for one consumer and its borrowers.
 *
 * The owner starts with one reference. Retiring it forbids new owner borrows,
 * but an existing live token may fork to cover work already in progress. The
 * final callback runs once, outside the internal lock, after every reference
 * has closed. Callers must retain uncertain remote/native borrows: a timeout,
 * failed transaction or local disconnect is not permission to close a token.
 *
 * This is accounting only, not evidence that a decoder or renderer has stopped.
 * There is deliberately no finalizer, forced release or timeout path.
 */
public final class ClusterLeaseBarrier {
    private final Object lock = new Object();
    private final Runnable onQuiescent;
    private int references = 1;
    private boolean ownerClosed;
    private boolean callbackStarted;
    private boolean callbackSucceeded;
    private Throwable callbackFailure;

    public ClusterLeaseBarrier(Runnable onQuiescent) {
        if (onQuiescent == null) throw new NullPointerException("onQuiescent");
        this.onQuiescent = onQuiescent;
    }

    /** Acquire from the owner, only while it remains open. */
    public Token borrow() {
        synchronized (lock) {
            if (ownerClosed) throw new IllegalStateException("Lease owner retired");
            return newTokenLocked();
        }
    }

    /** Idempotently retire the owner; does not wait for outstanding tokens. */
    public void closeOwner() {
        boolean run;
        synchronized (lock) {
            if (ownerClosed) return;
            ownerClosed = true;
            run = releaseLocked();
        }
        if (run) finish();
    }

    /** Counts the owner, while open, plus every live token. */
    public int referenceCount() {
        synchronized (lock) { return references; }
    }

    /** True only after the final callback completed successfully. */
    public boolean isQuiescent() {
        synchronized (lock) { return callbackSucceeded; }
    }

    /** The original final-callback failure, if any; never retried or hidden as success. */
    public Throwable callbackFailure() {
        synchronized (lock) { return callbackFailure; }
    }

    private Token newTokenLocked() {
        if (references == Integer.MAX_VALUE) throw new IllegalStateException("Too many lease references");
        Token token = new Token(this); // Allocation failure cannot leak a counted borrow.
        references++;
        return token;
    }

    private boolean releaseLocked() {
        references--;
        if (ownerClosed && references == 0 && !callbackStarted) {
            callbackStarted = true;
            return true;
        }
        return false;
    }

    private void finish() {
        Throwable failure = null;
        try { onQuiescent.run(); }
        catch (Throwable problem) { failure = problem; }
        synchronized (lock) {
            callbackFailure = failure;
            callbackSucceeded = failure == null;
        }
    }

    /** One independently closeable borrow; duplicate or stale close is harmless. */
    public static final class Token implements AutoCloseable {
        private final ClusterLeaseBarrier barrier;
        private boolean closed; // Guarded by barrier.lock, including fork/close races.

        private Token(ClusterLeaseBarrier barrier) { this.barrier = barrier; }

        /**
         * Extend an existing borrow even after owner retirement. A closed token
         * cannot resurrect a consumer, including while its final callback runs.
         */
        public Token fork() {
            synchronized (barrier.lock) {
                if (closed) throw new IllegalStateException("Lease token closed");
                return barrier.newTokenLocked();
            }
        }

        @Override public void close() {
            boolean run;
            synchronized (barrier.lock) {
                if (closed) return;
                closed = true;
                run = barrier.releaseLocked();
            }
            if (run) barrier.finish();
        }
    }
}
