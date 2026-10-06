package impulse.cluster.prototype;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone executable tests of the REAL prototype, not an OEM/device test. */
public final class ClusterFramePumpTest {
    private static final long TIMEOUT_SECONDS = 5;
    private static int passed;
    private interface Check { void run() throws Exception; }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void await(CountDownLatch latch) throws Exception {
        check(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "timed out waiting for test event");
    }
    private static void complete(CompletionStage<Void> stage) throws Exception {
        stage.toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
    private static Throwable failed(CompletionStage<Void> stage) throws Exception {
        try { complete(stage); } catch (ExecutionException expected) { return expected.getCause(); }
        throw new AssertionError("expected exceptional completion");
    }
    private static void test(String name, Check body) throws Exception {
        body.run(); passed++; System.out.println("PASS " + name);
    }

    private static class Owner implements ClusterFramePump.FrameOwner {
        final int session;
        final AtomicInteger acks = new AtomicInteger();
        final AtomicInteger returns = new AtomicInteger();
        final Map<ByteBuffer, Integer> buffers = new IdentityHashMap<>();
        final CountDownLatch ackEntered = new CountDownLatch(1);
        CountDownLatch ackRelease = new CountDownLatch(0);
        volatile boolean failAck, failReturn;
        volatile Runnable onReturn, onFirstAck;
        Owner(int session) { this.session = session; }
        @Override public void acknowledge(int id) throws Exception {
            check(id == session, "ACK used wrong session endpoint");
            int n = acks.incrementAndGet();
            if (n == 1) {
                ackEntered.countDown(); await(ackRelease);
                if (failAck) throw new IllegalStateException("ack failure");
                if (onFirstAck != null) onFirstAck.run();
            }
        }
        @Override public void recycle(ByteBuffer buffer) {
            int n = returns.incrementAndGet();
            synchronized (buffers) {
                Integer old = buffers.get(buffer);
                buffers.put(buffer, old == null ? 1 : old + 1);
            }
            if (onReturn != null) onReturn.run();
            if (n == 1 && failReturn) throw new IllegalStateException("pool failure");
        }
        void exactly(int count) {
            check(acks.get() == count, "ACK count " + acks.get() + " != " + count);
            check(returns.get() == count, "pool-return count mismatch");
            synchronized (buffers) {
                check(buffers.size() == count, "buffer identity lost");
                for (Integer n : buffers.values()) check(n == 1, "buffer returned more than once");
            }
        }
    }

    private static class Decoder implements ClusterFramePump.Decoder {
        final Thread caller = Thread.currentThread();
        final AtomicInteger consumes = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        final AtomicInteger inDecoder = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release;
        volatile boolean failConsume, failClose;
        volatile ByteBuffer firstBuffer;
        volatile long firstTimestamp;
        Decoder(boolean block) { release = new CountDownLatch(block ? 1 : 0); }
        @Override public void consume(int session, long timestamp, ByteBuffer buffer) throws Exception {
            check(Thread.currentThread() != caller, "decoder ran on submitting callback thread");
            check(inDecoder.incrementAndGet() == 1, "concurrent decoder calls");
            try {
                int n = consumes.incrementAndGet();
                if (n == 1) {
                    firstBuffer = buffer; firstTimestamp = timestamp;
                    entered.countDown(); await(release);
                    if (failConsume) throw new IllegalStateException("decode failure");
                }
            } finally { inDecoder.decrementAndGet(); }
        }
        @Override public void close() {
            check(Thread.currentThread() != caller, "decoder close ran on callback thread");
            check(inDecoder.get() == 0, "decoder closed while consuming");
            check(closes.incrementAndGet() == 1, "decoder closed twice");
            if (failClose) throw new IllegalStateException("close failure");
        }
    }

    private static ClusterFramePump.Frame frame(Object generation, Owner owner, int bytes) {
        return new ClusterFramePump.Frame(generation, owner.session, 12345L,
                ByteBuffer.allocate(bytes), owner);
    }
    private static void accepted(ClusterFramePump pump, ClusterFramePump.Frame frame) {
        check(pump.offer(frame) == ClusterFramePump.OfferResult.ACCEPTED, "frame not accepted");
    }
    private static void stopped(ClusterFramePump pump) throws Exception {
        pump.close(); complete(pump.termination());
        check(pump.outstandingFrames() == 0 && pump.outstandingBytes() == 0, "budget not drained");
    }

    private static void callbackFailure(String kind) throws Exception {
        Object g = new Object(); Owner owner = new Owner(7); Decoder decoder = new Decoder(true);
        owner.failAck = kind.equals("ack") || kind.equals("all");
        owner.failReturn = kind.equals("pool") || kind.equals("all");
        decoder.failConsume = kind.equals("decode");
        decoder.failClose = kind.equals("close") || kind.equals("all");
        ClusterFramePump pump = new ClusterFramePump(g, 3, 30, decoder);
        ClusterFramePump.Frame a = frame(g, owner, 5), b = frame(g, owner, 5);
        accepted(pump, a); await(decoder.entered); accepted(pump, b);
        decoder.release.countDown();
        if (kind.equals("close")) {
            complete(a.disposal()); complete(b.disposal()); pump.close();
        }
        Throwable failure = failed(pump.termination());
        check(failure != null, "lost failure");
        owner.exactly(2);
        check(decoder.consumes.get() == (kind.equals("close") ? 2 : 1), "decoded queued frame after failure");
        check(decoder.closes.get() == 1, "close missing");
        check(pump.outstandingFrames() == 0 && pump.outstandingBytes() == 0, "failure leaked budget");
        failed(a.disposal());
    }

    public static void main(String[] args) throws Exception {
        test("zero-copy reference, timestamp, worker thread, successful disposal", () -> {
            Object g = new Object(); Owner o = new Owner(7); Decoder d = new Decoder(false);
            ClusterFramePump p = new ClusterFramePump(g, 2, 16, d);
            ByteBuffer bytes = ByteBuffer.allocate(16); bytes.position(5); bytes.limit(8);
            ClusterFramePump.Frame f = new ClusterFramePump.Frame(g, 7, 999, bytes, o);
            accepted(p, f); complete(f.disposal()); stopped(p); o.exactly(1);
            check(d.firstBuffer == bytes && d.firstTimestamp == 999, "copied/replaced frame metadata");
        });
        test("construction-to-offer window mutation cannot evade allocation budget", () -> {
            Object g=new Object();Owner o=new Owner(7);Decoder d=new Decoder(false);ClusterFramePump p=new ClusterFramePump(g,2,8,d);
            ByteBuffer bytes=ByteBuffer.allocate(16);bytes.limit(1);ClusterFramePump.Frame f=new ClusterFramePump.Frame(g,7,0,bytes,o);bytes.limit(16);
            check(p.offer(f)==ClusterFramePump.OfferResult.CAPACITY_EXCEEDED,"mutated window escaped budget");failed(p.termination());o.exactly(1);check(d.consumes.get()==0,"oversize allocation consumed");
        });
        test("sliced buffers charge full retained array allocation", () -> {
            Object g=new Object();Owner o=new Owner(7);Decoder d=new Decoder(false);ClusterFramePump p=new ClusterFramePump(g,2,8,d);
            ByteBuffer backing=ByteBuffer.allocate(16);backing.position(4);backing.limit(5);ByteBuffer slice=backing.slice();
            check(p.offer(new ClusterFramePump.Frame(g,7,0,slice,o))==ClusterFramePump.OfferResult.CAPACITY_EXCEEDED,"slice escaped retained-memory budget");failed(p.termination());o.exactly(1);
        });
        test("ACK credit is sent only after local capacity is available", () -> {
            Object g=new Object();Owner o=new Owner(7);Decoder d=new Decoder(true);ClusterFramePump p=new ClusterFramePump(g,1,5,d);
            ClusterFramePump.Frame first=frame(g,o,5),next=frame(g,o,5);AtomicReference<ClusterFramePump.OfferResult> result=new AtomicReference<>();
            o.onFirstAck=()->result.set(p.offer(next));accepted(p,first);await(d.entered);check(o.acks.get()==0,"ACK before input consumption");
            d.release.countDown();complete(next.disposal());stopped(p);check(result.get()==ClusterFramePump.OfferResult.ACCEPTED,"normal replenishment tripped overflow");o.exactly(2);
        });
        test("JVM buffers without an accessible array are rejected before ownership transfer", () -> {
            Owner o=new Owner(7);boolean rejected=false;
            try{new ClusterFramePump.Frame(new Object(),7,0,ByteBuffer.allocateDirect(4),o);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"buffer without accessible array accepted");o.exactly(0);
        });
        test("close invalidates queued work and waits for in-flight consumption", () -> {
            Object g = new Object(); Owner o = new Owner(7); Decoder d = new Decoder(true);
            ClusterFramePump p = new ClusterFramePump(g, 3, 30, d);
            accepted(p, frame(g,o,4)); await(d.entered); accepted(p,frame(g,o,5)); p.close(); p.close();
            check(!p.termination().toCompletableFuture().isDone(), "terminated before in-flight call");
            d.release.countDown(); stopped(p); o.exactly(2); check(d.consumes.get()==1,"queued frame decoded after close");
        });
        test("frame-count bound includes in-flight and overflow is terminal", () -> {
            Object g=new Object();Owner o=new Owner(7);Decoder d=new Decoder(true);ClusterFramePump p=new ClusterFramePump(g,2,100,d);
            accepted(p,frame(g,o,4));await(d.entered);accepted(p,frame(g,o,4));
            check(p.offer(frame(g,o,4))==ClusterFramePump.OfferResult.CAPACITY_EXCEEDED,"frame limit missed");
            check(p.outstandingFrames()==2,"bound exceeded");d.release.countDown();
            check(failed(p.termination()) instanceof ClusterFramePump.CapacityExceededException,"overflow not terminal");
            o.exactly(3);check(d.consumes.get()==1,"continued after H264 overflow");
        });
        test("byte bound includes in-flight", () -> {
            Object g=new Object();Owner o=new Owner(7);Decoder d=new Decoder(true);ClusterFramePump p=new ClusterFramePump(g,10,7,d);
            accepted(p,frame(g,o,4));await(d.entered);accepted(p,frame(g,o,3));
            check(p.offer(frame(g,o,1))==ClusterFramePump.OfferResult.CAPACITY_EXCEEDED,"byte limit missed");
            check(p.outstandingBytes()==7,"byte budget exceeded");d.release.countDown();failed(p.termination());o.exactly(3);
        });
        test("oversize frame rejected and disposed without decoding", () -> {
            Object g=new Object();Owner o=new Owner(7);Decoder d=new Decoder(false);ClusterFramePump p=new ClusterFramePump(g,2,3,d);
            check(p.offer(frame(g,o,4))==ClusterFramePump.OfferResult.CAPACITY_EXCEEDED,"oversize accepted");
            failed(p.termination());o.exactly(1);check(d.consumes.get()==0,"oversize decoded");
        });
        test("stale generation uses original endpoint even with reused session id", () -> {
            Object g=new Object();Owner current=new Owner(7),old=new Owner(7);Decoder d=new Decoder(false);ClusterFramePump p=new ClusterFramePump(g,2,20,d);
            check(p.offer(frame(new Object(),old,5))==ClusterFramePump.OfferResult.WRONG_GENERATION,"old epoch accepted");old.exactly(1);current.exactly(0);
            ClusterFramePump.Frame f=frame(g,current,5);accepted(p,f);complete(f.disposal());stopped(p);current.exactly(1);
        });
        test("stale-frame disposal errors do not poison the current generation", () -> {
            Object g=new Object();Owner stale=new Owner(7),current=new Owner(8);stale.failAck=true;stale.failReturn=true;
            Decoder d=new Decoder(false);ClusterFramePump p=new ClusterFramePump(g,2,20,d);ClusterFramePump.Frame old=frame(new Object(),stale,4);
            check(p.offer(old)==ClusterFramePump.OfferResult.WRONG_GENERATION,"old accepted");failed(old.disposal());stale.exactly(1);
            ClusterFramePump.Frame f=frame(g,current,4);accepted(p,f);complete(f.disposal());stopped(p);current.exactly(1);
        });
        test("post-termination disposal failure stays visible on the frame", () -> {
            Object g=new Object();Owner o=new Owner(7);o.failAck=true;o.failReturn=true;Decoder d=new Decoder(false);ClusterFramePump p=new ClusterFramePump(g,2,20,d);stopped(p);
            ClusterFramePump.Frame f=frame(g,o,4);check(p.offer(f)==ClusterFramePump.OfferResult.STOPPED,"late accepted");failed(f.disposal());complete(p.termination());o.exactly(1);
        });
        test("duplicate frame submission never repeats ownership callbacks", () -> {
            Object g=new Object();Owner o=new Owner(7);Decoder d=new Decoder(true);ClusterFramePump p=new ClusterFramePump(g,2,20,d);ClusterFramePump.Frame f=frame(g,o,5);
            accepted(p,f);await(d.entered);check(p.offer(f)==ClusterFramePump.OfferResult.ALREADY_SUBMITTED,"duplicate accepted");
            d.release.countDown();complete(f.disposal());stopped(p);o.exactly(1);
            check(p.offer(f)==ClusterFramePump.OfferResult.ALREADY_SUBMITTED,"disposed frame accepted again");o.exactly(1);
        });
        test("late stopped frame gets exactly one disposal and no decode", () -> {
            Object g=new Object();Owner o=new Owner(7);Decoder d=new Decoder(false);ClusterFramePump p=new ClusterFramePump(g,2,20,d);stopped(p);
            ClusterFramePump.Frame f=frame(g,o,5);check(p.offer(f)==ClusterFramePump.OfferResult.STOPPED,"late frame accepted");complete(f.disposal());o.exactly(1);check(d.consumes.get()==0,"late decode");
        });
        test("empty close uses worker and is idempotent", () -> {
            Decoder d=new Decoder(false);ClusterFramePump p=new ClusterFramePump(new Object(),1,1,d);p.close();p.close();stopped(p);check(d.closes.get()==1,"close count");
        });
        test("new Surface generation never resumes the old pump", () -> {
            Object old=new Object(),fresh=new Object();Owner o=new Owner(7);Decoder d1=new Decoder(false),d2=new Decoder(false);
            ClusterFramePump p1=new ClusterFramePump(old,2,20,d1);stopped(p1);ClusterFramePump p2=new ClusterFramePump(fresh,2,20,d2);
            check(p2.offer(frame(old,o,3))==ClusterFramePump.OfferResult.WRONG_GENERATION,"stale Surface frame accepted");
            ClusterFramePump.Frame f=frame(fresh,o,3);accepted(p2,f);complete(f.disposal());stopped(p2);o.exactly(2);check(d1.consumes.get()==0 && d2.consumes.get()==1,"generation isolation failed");
        });
        test("ACK exception still returns buffers and stops further decode", () -> callbackFailure("ack"));
        test("decoder exception drains pending frames", () -> callbackFailure("decode"));
        test("pool-return exception is attempted once and stops pump", () -> callbackFailure("pool"));
        test("ACK plus return plus close exceptions remain accounted", () -> callbackFailure("all"));
        test("decoder close exception is visible", () -> {
            Decoder d=new Decoder(false);d.failClose=true;ClusterFramePump p=new ClusterFramePump(new Object(),1,1,d);p.close();failed(p.termination());check(d.closes.get()==1,"close retried");
        });
        test("disposal completion can submit next frame into one-frame budget", () -> {
            Object g=new Object();Owner o=new Owner(7);Decoder d=new Decoder(false);ClusterFramePump p=new ClusterFramePump(g,1,5,d);
            ClusterFramePump.Frame first=frame(g,o,5),second=frame(g,o,5);AtomicReference<ClusterFramePump.OfferResult> result=new AtomicReference<>();
            first.disposal().thenRun(()->result.set(p.offer(second)));accepted(p,first);complete(second.disposal());stopped(p);
            check(result.get()==ClusterFramePump.OfferResult.ACCEPTED,"completion ran before budget release");o.exactly(2);check(d.consumes.get()==2,"second frame not decoded");
        });
        test("owner callback may close without deadlock", () -> {
            Object g=new Object();Owner o=new Owner(7);Decoder d=new Decoder(false);ClusterFramePump p=new ClusterFramePump(g,2,20,d);o.onReturn=p::close;
            accepted(p,frame(g,o,5));complete(p.termination());o.exactly(1);
        });
        test("concurrent producers retain bounds and dispose each buffer once", () -> {
            Object g=new Object();Owner o=new Owner(7);Decoder d=new Decoder(false);ClusterFramePump p=new ClusterFramePump(g,1000,1000,d);
            List<Thread> threads=new ArrayList<>();AtomicReference<Throwable> producerFailure=new AtomicReference<>();
            for(int i=0;i<4;i++){Thread t=new Thread(()->{try{for(int n=0;n<250;n++)accepted(p,frame(g,o,1));}catch(Throwable e){producerFailure.set(e);}});threads.add(t);t.start();}
            for(Thread t:threads){t.join(5000);check(!t.isAlive(),"producer stuck");}check(producerFailure.get()==null,"producer failed");stopped(p);o.exactly(1000);check(d.closes.get()==1,"bad close count");
        });
        System.out.println("PASS total="+passed+" prototype checks; no Android/OEM/device execution");
    }
}
