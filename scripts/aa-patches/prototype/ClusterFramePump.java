package impulse.cluster.prototype;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Research prototype, NOT wired into the app or OEM Service.
 *
 * One pump owns one connection/Surface generation and one independent decoder.
 * The supplied endpoint must be CLUSTER's, never MAIN's. Codec setup/config and
 * focus/registration are deliberately outside this data-frame-only boundary.
 * A decoder call consumes/copies input synchronously before returning; it must
 * not retain the pooled buffer. No method here claims that pixels rendered.
 */
public final class ClusterFramePump implements AutoCloseable {
    /**
     * A per-frame lifetime lease. The adapter must keep this frame's captured
     * endpoint valid until frame.disposal() settles after BOTH recycle and ACK
     * attempts, including exceptions and late/rejected frames. Never release
     * the endpoint lease from recycle alone. Pump termination cannot prevent
     * new upstream callbacks after it completes.
     */
    public interface FrameOwner {
        void acknowledge(int sessionId) throws Exception;
        void recycle(ByteBuffer buffer) throws Exception;
    }

    public interface Decoder {
        void consume(int sessionId, long timestamp, ByteBuffer buffer) throws Exception;
        void close() throws Exception;
    }

    public enum OfferResult {
        ACCEPTED, STOPPED, WRONG_GENERATION, CAPACITY_EXCEEDED, ALREADY_SUBMITTED
    }

    /** Ownership transfers on the first offer, including a rejected offer. */
    public static final class Frame {
        private final Object generation;
        private final int sessionId;
        private final long timestamp;
        private final ByteBuffer buffer;
        private final int bytes;
        private final FrameOwner owner;
        private final AtomicBoolean submitted = new AtomicBoolean();
        private final CompletableFuture<Void> disposed = new CompletableFuture<>();
        private boolean ackAttempted;
        private boolean recycleAttempted;

        public Frame(Object generation, int sessionId, long timestamp,
                     ByteBuffer buffer, FrameOwner owner) {
            this.generation = Objects.requireNonNull(generation, "generation");
            this.buffer = Objects.requireNonNull(buffer, "buffer");
            this.owner = Objects.requireNonNull(owner, "owner");
            this.sessionId = sessionId;
            this.timestamp = timestamp;
            if (!buffer.hasArray()) {
                throw new IllegalArgumentException("Expected the observed Java array-backed pool buffer");
            }
            // Charge the entire retained allocation, including sliced windows.
            // Position/limit changes before first offer cannot evade the budget.
            this.bytes = buffer.array().length;
        }

        /** Completes after ACK/recycle attempts, not after physical rendering. */
        public CompletionStage<Void> disposal() {
            return disposed.thenApply(value -> null);
        }
    }

    public static final class CapacityExceededException extends RuntimeException {
        private CapacityExceededException() {
            super("CLUSTER queue capacity exceeded; require a clean stream restart");
        }
    }

    private final Object lock = new Object();
    private final Object generation;
    private final int maxFrames;
    private final long maxBytes;
    private final Decoder decoder;
    private final ArrayDeque<Frame> queue = new ArrayDeque<>();
    private final CompletableFuture<Void> terminated = new CompletableFuture<>();
    private boolean accepting = true;
    private int outstandingFrames;
    private long outstandingBytes;
    private Throwable failure;

    public ClusterFramePump(Object generation, int maxFrames, long maxBytes,
                            Decoder decoder) {
        this.generation = Objects.requireNonNull(generation, "generation");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        if (maxFrames <= 0 || maxBytes <= 0) {
            throw new IllegalArgumentException("Positive frame and byte limits required");
        }
        this.maxFrames = maxFrames;
        this.maxBytes = maxBytes;
        // Dedicated worker: neither decoder.consume nor decoder.close runs on
        // the GAL callback thread, even for an empty pump or normal shutdown.
        Thread worker = new Thread(this::run, "Impulse-Cluster-FramePump");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Nonblocking queue submission, retaining the exact Java buffer reference.
     * Count/allocation-byte budgets include the in-flight frame. The caller must
     * stop touching the buffer after first offer. Rejected frames are recycled
     * and ACKed inline, so those two injected operations must be short.
     * Capacity overflow terminates this generation: arbitrary H.264 dropping
     * followed by continued decoding would require a recovery strategy.
     */
    public OfferResult offer(Frame frame) {
        Objects.requireNonNull(frame, "frame");
        if (!frame.submitted.compareAndSet(false, true)) return OfferResult.ALREADY_SUBMITTED;
        OfferResult result;
        synchronized (lock) {
            if (frame.generation != generation) {
                result = OfferResult.WRONG_GENERATION;
            } else if (!accepting) {
                result = OfferResult.STOPPED;
            } else if (outstandingFrames >= maxFrames || frame.bytes > maxBytes - outstandingBytes) {
                failLocked(new CapacityExceededException());
                result = OfferResult.CAPACITY_EXCEEDED;
            } else {
                queue.addLast(frame);
                outstandingFrames++;
                outstandingBytes += frame.bytes;
                lock.notifyAll();
                return OfferResult.ACCEPTED;
            }
        }
        // The frame carries its original endpoint/pool owner. A late frame must
        // never be ACKed through the newer session's endpoint.
        Throwable disposalFailure = dispose(frame, null);
        if (disposalFailure != null && result != OfferResult.WRONG_GENERATION) {
            fail(disposalFailure);
        }
        completeDisposal(frame, disposalFailure);
        return result;
    }

    /**
     * Invalidates pending work immediately. Already in-flight consumption may
     * finish. Await termination BEFORE releasing/reusing its Surface or sink.
     * Re-enable/session/Surface replacement requires a fresh pump + generation;
     * this object never resumes after overflow, failure or close.
     */
    @Override public void close() {
        synchronized (lock) {
            accepting = false;
            lock.notifyAll();
        }
    }

    public CompletionStage<Void> termination() {
        return terminated.thenApply(value -> null);
    }

    public int outstandingFrames() {
        synchronized (lock) { return outstandingFrames; }
    }

    public long outstandingBytes() {
        synchronized (lock) { return outstandingBytes; }
    }

    private void run() {
        while (true) {
            Frame frame;
            boolean consume;
            synchronized (lock) {
                while (queue.isEmpty() && accepting) {
                    try {
                        lock.wait();
                    } catch (InterruptedException interrupted) {
                        failLocked(interrupted);
                    }
                }
                if (queue.isEmpty()) break;
                frame = queue.removeFirst();
                // This is the in-flight boundary. close() cannot preempt an
                // already selected decoder call; all remaining queued frames
                // are disposal-only, and termination waits for this one.
                consume = accepting;
            }
            Throwable problem = null;
            try {
                if (consume) decoder.consume(frame.sessionId, frame.timestamp, frame.buffer);
            } catch (Throwable callbackFailure) {
                problem = callbackFailure;
            }
            problem = recycle(frame, problem);
            synchronized (lock) {
                outstandingFrames--;
                outstandingBytes -= frame.bytes;
                if (problem != null) failLocked(problem);
            }
            // Release local capacity BEFORE replenishing the sender's credit.
            // The OEM player ACKs early; this prototype deliberately does not.
            // This is an offline accounting invariant, not phone validation.
            Throwable ackFailure = acknowledge(frame);
            if (ackFailure != null) {
                problem = combine(problem, ackFailure);
                fail(ackFailure);
            }
            // Callbacks may submit another frame into a one-frame budget.
            completeDisposal(frame, problem);
        }
        try {
            decoder.close();
        } catch (Throwable closeFailure) {
            fail(closeFailure);
        }
        Throwable terminalFailure;
        synchronized (lock) { terminalFailure = failure; }
        if (terminalFailure == null) terminated.complete(null);
        else terminated.completeExceptionally(terminalFailure);
    }

    private static Throwable acknowledge(Frame frame) {
        if (!frame.ackAttempted) {
            frame.ackAttempted = true;
            try {
                frame.owner.acknowledge(frame.sessionId);
            } catch (Throwable ackFailure) {
                return ackFailure;
            }
        }
        return null;
    }

    private static Throwable recycle(Frame frame, Throwable problem) {
        if (!frame.recycleAttempted) {
            frame.recycleAttempted = true;
            try {
                frame.owner.recycle(frame.buffer);
            } catch (Throwable recycleFailure) {
                problem = combine(problem, recycleFailure);
            }
        }
        return problem;
    }

    private static Throwable dispose(Frame frame, Throwable problem) {
        problem = recycle(frame, problem);
        Throwable ackFailure = acknowledge(frame);
        return ackFailure == null ? problem : combine(problem, ackFailure);
    }

    private static void completeDisposal(Frame frame, Throwable problem) {
        if (problem == null) frame.disposed.complete(null);
        else frame.disposed.completeExceptionally(problem);
    }

    private void fail(Throwable problem) {
        synchronized (lock) { failLocked(problem); }
    }

    private void failLocked(Throwable problem) {
        failure = combine(failure, problem);
        accepting = false;
        lock.notifyAll();
    }

    private static Throwable combine(Throwable first, Throwable next) {
        if (first == null) return next;
        // Bounded error accounting; never retry a callback that may have acted.
        if (first != next && first.getSuppressed().length < 16) first.addSuppressed(next);
        return first;
    }
}
