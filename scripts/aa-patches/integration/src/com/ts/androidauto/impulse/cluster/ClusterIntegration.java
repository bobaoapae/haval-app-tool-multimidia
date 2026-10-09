package com.ts.androidauto.impulse.cluster;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import br.com.redesurftank.havalshisuku.api.AaClusterProtocol;
import br.com.redesurftank.havalshisuku.api.ClusterLeaseBarrier;
import com.google.android.projection.common.BufferPool;
import com.google.android.projection.proto.Protos;
import com.google.android.projection.protocol.CarServiceProvider;
import com.google.android.projection.protocol.GalReceiver;
import com.google.android.projection.protocol.InputSource;
import com.google.android.projection.protocol.VideoFrame;
import com.google.android.projection.protocol.VideoSink;
import impulse.cluster.prototype.ClusterFramePump;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Compiled into the exact-profile unsigned lab Service, never MAIN's managers. */
public final class ClusterIntegration {
    private static final String TAG = "ImpulseAaCluster";
    private static final Object LOCK = new Object();
    private static final AtomicLong GENERATIONS = new AtomicLong();
    // One independent CLUSTER codec at a time, across receiver generations.
    private static final java.util.concurrent.Semaphore DECODER_SLOT = new java.util.concurrent.Semaphore(1, true);
    // At most one uncertain codec owns this retained Surface lease until restart.
    private static final java.util.concurrent.atomic.AtomicReference<Request> QUARANTINED_OUTPUT = new java.util.concurrent.atomic.AtomicReference<>();
    private static final AtomicBoolean WAKE_QUEUED = new AtomicBoolean();
    private static final HandlerThread WORKER = new HandlerThread("Impulse-Cluster-Control");
    private static final Handler CONTROL;
    private static volatile Session active;
    private static volatile Request requested;
    private static volatile int status = AaClusterProtocol.DISABLED;
    // Lab-only: true sends PROJECTED focus on setup without host demand (frames
    // are ACKed and dropped). Milestones log at WARN
    // because this unit drops Log.i/Log.d.
    static final boolean LAB_SELF_FOCUS = false;
    static { WORKER.start(); CONTROL = new Handler(WORKER.getLooper()); }
    private ClusterIntegration() {}

    /** Exact pre-session hook. Failure leaves the existing OEM services intact. */
    public static void register(GalReceiver receiver, int viewingDistance) {
        Log.w(TAG, "V2DIAG register hook entered handoff=" + GeneratedTrust.HANDOFF_ENABLED + " pins=" + GeneratedTrust.CLIENT_CERTIFICATES.length + " viewingDistance=" + viewingDistance);
        if (!GeneratedTrust.HANDOFF_ENABLED || GeneratedTrust.CLIENT_CERTIFICATES.length == 0) return;
        Session created = null;
        try {
            synchronized (LOCK) {
                if (active != null && !active.retired.get()) {
                    Log.w(TAG, "CLUSTER registration refused: another receiver is active");
                    return;
                }
                created = new Session(receiver, viewingDistance);
                if (!receiver.registerImpulseClusterPair(created.video, created.input)) {
                    created.guard.retire();
                    created.retired.set(true);
                    Log.w(TAG, "CLUSTER pair registration refused");
                    return;
                }
                active = created;
                Log.w(TAG, "V2DIAG CLUSTER pair registered video=" + PairedRegistration.VIDEO_ID + " input=" + PairedRegistration.INPUT_ID + " gen=" + created.generation);
            }
            wake();
        } catch (Throwable problem) {
            if (created != null) { created.guard.retire(); created.retired.set(true); }
            Log.e(TAG, "CLUSTER registration failed; MAIN unchanged", problem);
        }
    }

    /** Runs under the original GalReceiver monitor via injected wrapper. */
    public static boolean registerPair(final SparseArray<CarServiceProvider> services, long receiverPointer,
            boolean stopping, CarServiceProvider video, CarServiceProvider input) {
        if (services == null || video == null || input == null) return false;
        PairedRegistration.Registry registry = new PairedRegistration.Registry() {
            @Override public Object get(int id) {
                int index = services.indexOfKey(id);
                if (index < 0) return null;
                CarServiceProvider value = services.valueAt(index);
                if (value == null) throw new IllegalStateException("Occupied null CLUSTER service slot");
                return value;
            }
            @Override public void put(int id, Object value) { services.put(id, (CarServiceProvider) value); }
            @Override public void remove(int id) { services.remove(id); }
        };
        final class Adapter implements PairedRegistration.Provider {
            private final CarServiceProvider provider;
            Adapter(CarServiceProvider provider) { this.provider = provider; }
            @Override public Object identity() { return provider; }
            @Override public long nativeInstance() { return provider.getNativeInstance(); }
            @Override public boolean create(int id, long pointer) { return provider.create(id, pointer); }
            @Override public void destroy() { provider.destroy(); }
        }
        boolean ok = PairedRegistration.register(registry, receiverPointer, stopping,
                new Adapter(video), new Adapter(input));
        Log.w(TAG, "V2DIAG registerPair ok=" + ok + " stopping=" + stopping + " receiverPtr=" + (receiverPointer != 0) + " services=" + services.size());
        return ok;
    }

    /** Synchronous native-access barrier, BEFORE OEM provider destruction. */
    public static void retire(GalReceiver receiver) {
        Session old;
        Request prior;
        synchronized (LOCK) {
            old = active != null && active.receiver == receiver ? active : null;
            if (old == null) return;
            old.retired.set(true);
            active=null;
            prior=requested;requested=null;status=AaClusterProtocol.WAITING_SESSION;
        }
        old.guard.retire(); // Before OEM provider destruction; no decoder join on GAL.
        synchronized(old){old.notifyAll();}
        postSafe(() -> {
            old.stopRendering("session retired");
            if(prior!=null){sendStatus(prior,AaClusterProtocol.WAITING_SESSION,"AA session ended",old.generation);prior.closeBase();}
        });
        wake();
    }

    public static int status() { return status; }

    /** Takes ownership of Surface only on successful return. */
    public static void setOutput(int uid, long id, boolean enabled, IBinder callback, Surface surface) {
        Request next = new Request(uid, id, enabled, callback, surface);
        Request old;
        try {
            callback.linkToDeath(next, 0);
            synchronized (LOCK) {
                if (next.dead || !callback.isBinderAlive()) throw new IllegalStateException("CLUSTER client already dead");
                old = requested;
                if (old != null && old.callback == callback && id <= old.id) {
                    throw new IllegalArgumentException("Stale CLUSTER request generation");
                }
                requested = next;
                Log.w(TAG, "V2DIAG setOutput accepted uid=" + uid + " id=" + id + " enabled=" + enabled + " surface=" + (surface != null));
            }
        } catch (Exception problem) {
            try { callback.unlinkToDeath(next, 0); } catch (Throwable ignored) {}
            // Binder still owns/reclaims surface on this failed transfer.
            throw new IllegalStateException("Cannot accept CLUSTER output", problem);
        }
        // Ownership is committed now: nothing may throw back through Binder,
        // whose finally would otherwise release the adopted Surface again.
        try { if (old != null) old.closeBase(); } catch (Throwable failure) { Log.e(TAG,"Previous client cleanup failed",failure); }
        try { wake(); } catch (Throwable failure) { status=AaClusterProtocol.FAILED;Log.e(TAG,"CLUSTER control queue failed",failure); }
    }

    private static void postSafe(Runnable action) {
        CONTROL.post(() -> {
            try { action.run(); } catch (Throwable failure) { Log.e(TAG,"Isolated CLUSTER control failure",failure); }
        });
    }

    private static void wake() {
        if (!WAKE_QUEUED.compareAndSet(false, true)) return;
        if (!CONTROL.post(() -> {
            WAKE_QUEUED.set(false);
            Session session = active;
            Request request = requested;
            if (session == null || session.retired.get()) {
                publish(request, request != null && request.enabled ? AaClusterProtocol.WAITING_SESSION : AaClusterProtocol.DISABLED, "waiting for AA session", 0);
            } else {
                try { session.apply(request); } catch(Throwable problem){session.problem(problem,request,session.job);}
            }
        })) WAKE_QUEUED.set(false);
    }

    private static void publish(Request request, int state, String reason, long generation) {
        if (request != requested) return;
        status = state;
        sendStatus(request,state,reason,generation);
    }
    private static void sendStatus(Request request,int state,String reason,long generation) {
        if (request == null || request.dead) return;
        Parcel event = Parcel.obtain();
        try {
            event.writeInterfaceToken(AaClusterProtocol.CALLBACK_DESCRIPTOR);
            event.writeInt(AaClusterProtocol.VERSION);
            event.writeLong(request.id);
            event.writeLong(generation);
            event.writeInt(state);
            event.writeString(reason.length() <= 120 ? reason : reason.substring(0, 120));
            if (!request.callback.transact(AaClusterProtocol.CALLBACK_STATE, event, null, IBinder.FLAG_ONEWAY)) request.binderDied();
        } catch (Throwable dead) { request.binderDied(); }
        finally { event.recycle(); }
    }

    /** Terminal ownership event; status/timeout/error never substitute for it. */
    private static void releaseSurfaceAndAcknowledge(Request request) {
        if(request.surface==null) return;
        try { request.surface.release(); }
        catch(Throwable failure) {
            QUARANTINED_OUTPUT.compareAndSet(null,request);
            Log.e(TAG,"Surface release uncertain; no terminal acknowledgement",failure);
            throw new IllegalStateException("Surface release uncertain",failure);
        }
        Parcel event=Parcel.obtain();
        try {
            event.writeInterfaceToken(AaClusterProtocol.CALLBACK_DESCRIPTOR);
            event.writeInt(AaClusterProtocol.VERSION);
            event.writeLong(request.id);
            // Lost events retain the client consumer. They never authorize reuse.
            request.callback.transact(AaClusterProtocol.CALLBACK_RELEASED,event,null,IBinder.FLAG_ONEWAY);
        } catch(Throwable unavailable) { Log.w(TAG,"Terminal output event unavailable",unavailable); }
        finally { event.recycle(); }
    }

    private static final class Request implements IBinder.DeathRecipient {
        final int uid;
        final long id;
        final boolean enabled;
        final IBinder callback;
        final Surface surface;
        volatile boolean dead;
        private final ClusterLeaseBarrier lifetime;
        Request(int uid, long id, boolean enabled, IBinder callback, Surface surface) {
            this.uid=uid; this.id=id; this.enabled=enabled; this.callback=callback; this.surface=surface;
            lifetime=new ClusterLeaseBarrier(()->releaseSurfaceAndAcknowledge(this));
        }
        ClusterLeaseBarrier.Token borrow() {
            if(dead) throw new IllegalStateException("Obsolete Surface owner");
            return lifetime.borrow();
        }
        void closeBase() {
            try { callback.unlinkToDeath(this,0); } catch(Throwable ignored) {}
            lifetime.closeOwner();
        }
        @Override public void binderDied() {
            boolean wasCurrent;
            synchronized (LOCK) { dead=true; wasCurrent=requested==this; if(wasCurrent)requested=null; }
            if (wasCurrent) { closeBase(); wake(); }
        }
    }

    private static final class Session {
        final GalReceiver receiver;
        final long generation = GENERATIONS.incrementAndGet();
        final AtomicBoolean retired = new AtomicBoolean();
        final VideoSink video;
        final InputSource input;
        final GuardedSink guard;
        private volatile RenderJob job;
        private RenderJob closingJob;
        private byte[] pendingSps, pendingPps;
        private volatile Config snapshot;
        private long configVersion;
        private volatile boolean unsupportedCodec;
        private volatile boolean phoneFocusRequest;
        private Request failedRequest;
        private int lastFocus = -1;
        private long frameCount;
        Session(GalReceiver receiver, int viewingDistance) {
            this.receiver = receiver;
            VideoSink.ProjectionListener listener = new VideoSink.ProjectionListener() {
                @Override public void onCodecSetup(int codecType) {
                    Log.w(TAG, "V2DIAG onCodecSetup codecType=" + codecType);
                    try {
                        unsupportedCodec = codecType != Protos.MediaCodecType.MEDIA_CODEC_VIDEO_H264_BP.getNumber();
                        guard.onSetup(); wake();
                    } catch (Throwable problem) { problem(problem); }
                }
                @Override public void onCodecConfig(byte[] bytes) {
                    try {
                        if (retired.get()) return;
                        if (bytes == null || bytes.length == 0 || bytes.length > 65536) throw new IllegalArgumentException("Invalid codec config size");
                        int types = H264AccessUnit.nalTypes(ByteBuffer.wrap(bytes));
                        if ((types & ((1<<7)|(1<<8))) == 0) throw new IllegalArgumentException("Codec config has no SPS/PPS");
                        synchronized (Session.this) {
                            // The first fragment of a new config invalidates the
                            // previous generation for all subsequently received data.
                            snapshot=null;
                            if ((types & (1<<7)) != 0) pendingSps=bytes;
                            if ((types & (1<<8)) != 0) pendingPps=bytes;
                            if (pendingSps != null && pendingPps != null) {
                                snapshot=new Config(++configVersion,pendingSps,pendingPps);
                                pendingSps=null;pendingPps=null;Session.this.notifyAll();
                                Log.w(TAG, "V2DIAG codec config ready version=" + configVersion);
                            }
                        }
                        // Explicit OEM parity: config has synthetic session0 ACK,
                        // separate from data-frame ACK. Phone behavior unvalidated.
                        wake();
                    } catch (Throwable problem) { problem(problem); }
                    finally {
                        try { guard.acknowledge(0); }
                        catch (Throwable ackFailure) { problem(ackFailure); }
                    }
                }
                @Override public void onProjectionUpdate(VideoFrame frame) {
                    if (frame == null || frame.data == null) return;
                    long n = ++frameCount;
                    if (n == 1 || n % 3000 == 0) Log.w(TAG, "V2DIAG frame n=" + n + " session=" + frame.sessionId + " job=" + (job != null));
                    RenderJob current = job;
                    try {
                        if (retired.get() || current == null || current.request != requested) {
                            disposeRaw(frame); return;
                        }
                        ClusterFramePump.Frame owned;
                        try { owned = new ClusterFramePump.Frame(current.token, frame.sessionId, frame.timestamp, frame.data, current.owner, snapshot); }
                        catch (Throwable invalid) { disposeRaw(frame); throw invalid; }
                        current.pump.offer(owned);
                    } catch (Throwable problem) { problem(problem, current == null ? requested : current.request, current); }
                }
                @Override public void onVideoFocusRequest(int mode, int reason) {
                    Log.w(TAG, "V2DIAG phone focus request mode=" + mode + " reason=" + reason);
                    phoneFocusRequest=true; wake();
                }
            };
            video = new VideoSink(listener, false, viewingDistance) {
                @Override public boolean create(int id, long pointer) {
                    if (!super.create(id,pointer)) return false;
                    setDisplayIdAndType(1,Protos.DisplayType.DISPLAY_TYPE_CLUSTER);
                    setCodecType(Protos.MediaCodecType.MEDIA_CODEC_VIDEO_H264_BP);
                    Protos.VideoConfiguration.Builder builder=Protos.VideoConfiguration.newBuilder()
                            .setCodecResolution(Protos.VideoCodecResolutionType.VIDEO_1920x1080)
                            // Phone draws only the 1920x720 band that matches the D3 panel.
                            .setHeightMargin(AaClusterProtocol.STREAM_HEIGHT_MARGIN)
                            .setFrameRate(Protos.VideoFrameRateType.VIDEO_FPS_30)
                            .setDensity(160).setRealDensity(160).setViewingDistance(viewingDistance)
                            .setVideoCodecType(Protos.MediaCodecType.MEDIA_CODEC_VIDEO_H264_BP);
                    addSupportedConfiguration((Protos.VideoConfiguration)builder.build());
                    return true;
                }
                @Override public void destroy() { guard.retire(); super.destroy(); }
            };
            guard = new GuardedSink(new GuardedSink.Calls() {
                @Override public void acknowledge(int id,int count){video.ackFrames(id,count);}
                @Override public void focus(int mode,int reason,boolean unsolicited){video.setVideoFocus(mode,reason,unsolicited);}
            });
            input = new InputSource(new InputSource.InputInjector() {
                @Override public void onKeyEvent(KeyEvent event) {}
                @Override public void onMotionEvent(MotionEvent event) {}
                @Override public void onRelativeEvent(int x,int y) {}
            }) {
                @Override public boolean create(int id,long pointer) {
                    if(!super.create(id,pointer))return false;
                    setDisplayId(1);registerKeyCodes(new int[]{19,20,21,22,23});return true;
                }
            };
        }
        private void disposeRaw(VideoFrame frame) {
            try { BufferPool.returnBuffer(frame.data); }
            finally { guard.acknowledge(frame.sessionId); }
        }
        private void problem(Throwable problem) { problem(problem, requested, job); }
        private void problem(Throwable problem, Request expected, RenderJob expectedJob) {
            Log.e(TAG,"CLUSTER isolated failure",problem);
            postSafe(() -> {
                if (retired.get() || active!=this || requested!=expected || (expectedJob!=null && job!=expectedJob)) return;
                failedRequest=expected;
                stopRendering("decoder failure");
                try { focus(false); } catch(Throwable focusFailure){Log.w(TAG,"CLUSTER failure focus request failed",focusFailure);}
                publish(expected,AaClusterProtocol.FAILED,"CLUSTER decoder/config failed",generation);
            });
        }
        private void focus(boolean enabled) {
            int wanted=enabled?1:2;
            boolean reply=phoneFocusRequest;
            if (wanted!=lastFocus || reply) {
                boolean sent = guard.focus(wanted,!reply);
                Log.w(TAG, "V2DIAG focus mode=" + wanted + " unsolicited=" + !reply + " sent=" + sent);
                if (sent) { lastFocus=wanted;phoneFocusRequest=false; }
            }
        }
        void apply(Request request) {
            if (retired.get() || active != this) { stopRendering("obsolete session"); return; }
            boolean wanted=request!=null && request.enabled && !request.dead;
            if (!wanted && job==null && LAB_SELF_FOCUS) {
                if (guard.isReady()) focus(true);
                return;
            }
            if (job!=null && (job.request!=request || !wanted)) {
                // A fresh Surface needs a fresh stream start/IDR. Retain NATIVE
                // until the replacement ownership pump is installed below.
                try { focus(false); }
                finally { stopRendering("output replaced"); }
            }
            if (!wanted) { focus(false);publish(request,AaClusterProtocol.DISABLED,"CLUSTER disabled",generation);return; }
            if (unsupportedCodec || failedRequest==request) {
                stopRendering("request failed");focus(false);publish(request,AaClusterProtocol.FAILED,"CLUSTER request failed",generation);return;
            }
            if (!guard.isReady()) {publish(request,AaClusterProtocol.WAITING_SETUP,"waiting for CLUSTER setup",generation);return;}
            if (closingJob!=null) {publish(request,AaClusterProtocol.WAITING_KEYFRAME,"previous decoder closing",generation);return;}
            try {
                if(job==null){
                    // Install the bounded ownership queue BEFORE requesting
                    // projection. Initial IDR is retained while codec starts.
                    job=new RenderJob(this,request);
                    job.refreshDeadline(snapshot==null?0:snapshot.version);
                }
                focus(true);
                Config latest=snapshot;
                if(latest==null){job.refreshDeadline(0);publish(request,AaClusterProtocol.WAITING_CONFIG,"waiting for SPS/PPS",generation);return;}
                if(job.renderedVersion.get()!=latest.version){
                    job.refreshDeadline(latest.version);
                    publish(request,AaClusterProtocol.WAITING_KEYFRAME,"waiting for rendered config generation",generation);
                }
            } catch(Throwable failure){if(request==requested)problem(failure,request,job);}
        }
        void stopRendering(String reason) {
            RenderJob old=job;
            job=null;
            if(old==null)return;
            closingJob=old;
            old.close();
            old.pump.termination().whenComplete((ignored,failure)->postSafe(()->{
                if(closingJob==old)closingJob=null;
                wake();
            }));
        }
    }

    private static void propagate(Throwable failure) throws Exception {
        if (failure instanceof Exception) throw (Exception) failure;
        if (failure instanceof Error) throw (Error) failure;
        throw new IllegalStateException(failure);
    }

    private static final class Config {
        final long version;
        final byte[] sps,pps;
        Config(long version,byte[] sps,byte[] pps){this.version=version;this.sps=sps;this.pps=pps;}
        byte[] combined(){
            if(sps==pps)return sps;
            if(sps.length+pps.length>65536)throw new IllegalArgumentException("Combined codec config too large");
            byte[] bytes=new byte[sps.length+pps.length];
            System.arraycopy(sps,0,bytes,0,sps.length);System.arraycopy(pps,0,bytes,sps.length,pps.length);return bytes;
        }
    }

    private static final class RenderJob {
        final Session session;
        final Request request;
        final Object token = new Object();
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicLong renderedVersion = new AtomicLong(-1);
        final ClusterFramePump pump;
        final ClusterLeaseBarrier.Token outputBorrow;
        final SwitchingDecoder decoder = new SwitchingDecoder();
        final ClusterFramePump.FrameOwner owner;
        private long deadlineVersion=-1;
        private Runnable deadline;
        RenderJob(Session session,Request request)throws Exception {
            this.session=session;this.request=request;this.outputBorrow=request.borrow();
            try {
                owner=new ClusterFramePump.FrameOwner(){
                    @Override public void acknowledge(int id){session.guard.acknowledge(id);}
                    @Override public void recycle(ByteBuffer buffer){BufferPool.returnBuffer(buffer);}
                };
                pump=new ClusterFramePump(token,16,8L*1024*1024,decoder);
                pump.termination().whenComplete((ignored,failure)->{
                    if (decoder.slotHealthy) outputBorrow.close();
                    else {
                        // A failed stop/release is not a completed borrow. Keep
                        // both slot and Surface rooted; never recycle for reuse.
                        QUARANTINED_OUTPUT.compareAndSet(null,request);
                        Log.e(TAG,"CLUSTER codec and Surface quarantined until process restart");
                    }
                    if(failure!=null&&!session.retired.get()&&session.job==RenderJob.this)session.problem(failure,request,RenderJob.this);
                });
            }catch(Throwable problem){
                outputBorrow.close();if(problem instanceof Exception)throw(Exception)problem;throw(Error)problem;
            }
        }
        void refreshDeadline(long version){
            if(deadlineVersion==version)return;
            deadlineVersion=version;
            if(deadline!=null)CONTROL.removeCallbacks(deadline);
            deadline=()->{if(session.job==this&&!cancelled.get()&&renderedVersion.get()!=version)
                session.problem(new IllegalStateException("CLUSTER first-frame timeout"),request,this);};
            CONTROL.postDelayed(deadline,6000);
        }
        void close(){
            cancelled.set(true);
            if(deadline!=null)CONTROL.removeCallbacks(deadline);
            synchronized(session){session.notifyAll();}
            pump.close();
        }
        private final class SwitchingDecoder implements ClusterFramePump.ContextualDecoder {
            private ClusterAvcDecoder decoder;
            private volatile Config configured;
            private boolean ownsSlot;
            private boolean slotHealthy=true;
            @Override public void consume(int id,long timestamp,ByteBuffer bytes)throws Exception{consume(id,timestamp,bytes,null);}
            @Override public void consume(int id,long timestamp,ByteBuffer bytes,Object context)throws Exception{
                Config config=(Config)context;
                if(config==null){
                    long until=android.os.SystemClock.elapsedRealtime()+2500;
                    synchronized(session){
                        while(config==null&&!cancelled.get()&&!session.retired.get()){
                            config=session.snapshot;if(config!=null)break;
                            long left=until-android.os.SystemClock.elapsedRealtime();if(left<=0)break;session.wait(left);
                        }
                    }
                }
                if(cancelled.get()||session.retired.get())throw new IllegalStateException("CLUSTER generation cancelled");
                if(config==null)throw new IllegalStateException("Data arrived without usable codec configuration");
                if(config!=configured){
                    if(!ownsSlot){
                        if(!DECODER_SLOT.tryAcquire(2500,java.util.concurrent.TimeUnit.MILLISECONDS))throw new IllegalStateException("Previous CLUSTER codec has not released its slot");
                        ownsSlot=true;
                    }
                    if(decoder!=null){try{decoder.close();decoder=null;}catch(Throwable failure){slotHealthy=false;propagate(failure);}}
                    if(cancelled.get()||session.retired.get())throw new IllegalStateException("CLUSTER cancelled before codec creation");
                    final Config expected=config;
                    configured=config;renderedVersion.set(-1);
                    try { decoder=new ClusterAvcDecoder(request.surface,AaClusterProtocol.STREAM_WIDTH,AaClusterProtocol.STREAM_HEIGHT,config.combined(),new ClusterAvcDecoder.Events(){
                        @Override public void firstFrameRendered(long pts){postSafe(()->{
                            if(session.job==RenderJob.this&&requested==request&&!session.retired.get()&&!cancelled.get()&&configured==expected&&session.snapshot==expected){
                                renderedVersion.set(expected.version);publish(request,AaClusterProtocol.LIVE,"CLUSTER frame rendered",session.generation);
                            }
                        });}
                        @Override public void failed(Throwable failure){
                            if(configured==expected)session.problem(failure,request,RenderJob.this);
                        }
                    }); } catch(Throwable creationFailure){slotHealthy=false;propagate(creationFailure);}
                }
                decoder.consume(id,timestamp,bytes);
            }
            @Override public void close()throws Exception{
                try{if(decoder!=null){decoder.close();decoder=null;}}
                catch(Throwable failure){slotHealthy=false;propagate(failure);}
                finally{if(ownsSlot&&slotHealthy){ownsSlot=false;DECODER_SLOT.release();}}
                // Uncertain native release quarantines this slot until process
                // restart. Never start overlapping codecs after cleanup failure.
            }
        }
    }
}
