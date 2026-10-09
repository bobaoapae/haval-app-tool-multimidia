package br.com.redesurftank.havalshisuku.managers;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import br.com.redesurftank.havalshisuku.api.ClusterLeaseBarrier;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the consumer itself, not just a Surface producer handle. */
public final class ClusterSurfaceOutput {
    private static final String TAG="AaClusterOutput";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final Object POOL_LOCK=new Object();
    // One remotely exposed consumer plus at most one never-submitted view.
    private static final int MAX_OWNED=2;
    private static final IdentityHashMap<SurfaceTexture,ClusterSurfaceOutput> OWNED=new IdentityHashMap<>();
    private static Runnable capacityListener;
    public final SurfaceTexture texture;
    public final Surface surface;
    private final ClusterLeaseBarrier barrier;
    private final AtomicBoolean detached=new AtomicBoolean();

    private ClusterSurfaceOutput(SurfaceTexture texture) {
        this.texture=texture;
        this.surface=new Surface(texture);
        this.barrier=new ClusterLeaseBarrier(()->{
            if(!MAIN.post(this::disposeOnMain)) throw new IllegalStateException("Surface cleanup handler stopped");
        });
    }
    /** Called only for a fresh TextureView callback-owned consumer, on main. */
    public static ClusterSurfaceOutput adopt(SurfaceTexture texture) {
        synchronized(POOL_LOCK) {
            if(OWNED.containsKey(texture)) throw new IllegalStateException("Consumer adopted twice");
            if(OWNED.size()>=MAX_OWNED) return null;
            ClusterSurfaceOutput output=new ClusterSurfaceOutput(texture);
            OWNED.put(texture,output); return output;
        }
    }
    public static ClusterSurfaceOutput retained(SurfaceTexture texture) {
        synchronized(POOL_LOCK) { return OWNED.get(texture); }
    }
    public static void setCapacityListener(Runnable listener) {
        synchronized(POOL_LOCK) { capacityListener=listener; }
    }
    public boolean isAvailable() { return !detached.get() && surface.isValid(); }
    public Borrow borrow() { return new Borrow(this,barrier.borrow()); }
    public static final class Borrow implements AutoCloseable {
        public final ClusterSurfaceOutput output;
        private final ClusterLeaseBarrier.Token token;
        private Borrow(ClusterSurfaceOutput output,ClusterLeaseBarrier.Token token) { this.output=output; this.token=token; }
        public Borrow fork() { return new Borrow(output,token.fork()); }
        @Override public void close() { token.close(); }
    }
    /** Only after TextureView detached its hardware layer; listener returns false. */
    public void detachViewOwner() {
        if(!detached.compareAndSet(false,true)) return;
        // Framework owns GL attachment. Never call detachFromGLContext/updateTexImage here.
        try { texture.setOnFrameAvailableListener(null); }
        catch(RuntimeException failure) { Log.w(TAG,"Listener cleanup failed",failure); }
        barrier.closeOwner();
    }
    private void disposeOnMain() {
        // Both the view owner and every remote/transport borrow are closed.
        Throwable failure=null;
        try { surface.release(); } catch(Throwable problem) { failure=problem; }
        try { texture.release(); } catch(Throwable problem) { if(failure==null) failure=problem; }
        if(failure!=null) { Log.e(TAG,"Disposal uncertain; keeping bounded quarantine",failure); return; }
        Runnable listener;
        synchronized(POOL_LOCK) { OWNED.remove(texture); listener=capacityListener; }
        if(listener!=null) try { listener.run(); } catch(RuntimeException problem) { Log.w(TAG,"Capacity callback failed",problem); }
    }
}
