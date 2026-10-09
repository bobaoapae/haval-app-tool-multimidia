package br.com.redesurftank.havalshisuku.managers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import br.com.redesurftank.havalshisuku.api.AaClusterProtocol;
import br.com.redesurftank.havalshisuku.api.ClusterReleaseLedger;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Asynchronous, single-output transport with an independent terminal-release ledger. */
public final class AndroidAutoClusterClient {
    // WARN: this head unit drops Log.i/Log.d (persist.log.tag=WARN).
    private static final String TAG="AaClusterClient";
    public interface Listener { void onStatus(int status,String reason); }
    private final Context context;
    private final Handler main=new Handler(android.os.Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{
        Thread thread=new Thread(r,"Impulse-Cluster-Binder"); thread.setDaemon(true); return thread;
    });
    private final Object lock=new Object();
    private final Listener listener;
    private final ClusterReleaseLedger<RemoteOutput> ledger=new ClusterReleaseLedger<>();
    private final AtomicBoolean queued=new AtomicBoolean();
    private final AtomicBoolean dirty=new AtomicBoolean();
    private Binding remote;
    private ClusterSurfaceOutput.Borrow desired;
    private long revision;
    private long nextRequest;
    private long connectionGeneration;
    private boolean bindingRegistered;
    private boolean closed;
    private boolean finished;

    private static final class RemoteOutput implements AutoCloseable {
        final ClusterSurfaceOutput.Borrow borrow;
        final AtomicBoolean timeoutScheduled=new AtomicBoolean();
        long stopAttemptRevision=-1; // worker-only; retry at most once per new demand revision
        RemoteOutput(ClusterSurfaceOutput.Borrow borrow) { this.borrow=borrow; }
        @Override public void close() { borrow.close(); }
    }
    public AndroidAutoClusterClient(Context context,Listener listener) {
        this.context=context.getApplicationContext(); this.listener=listener;
    }
    public void setOutput(ClusterSurfaceOutput output) {
        ClusterSurfaceOutput.Borrow next=null;
        if(output!=null && output.isAvailable()) {
            try { next=output.borrow(); } catch(IllegalStateException detached) { /* Never resurrect an owner. */ }
        }
        ClusterSurfaceOutput.Borrow old;
        ClusterReleaseLedger.Entry<RemoteOutput> retiring;
        long current;
        synchronized(lock) {
            if(closed) { if(next!=null) next.close(); return; }
            retiring=ledger.active();
            old=desired; desired=next; current=++revision;
        }
        retireNow(retiring);
        if(old!=null) old.close();
        notifyCurrent(current,AaClusterProtocol.WAITING_SESSION,"waiting for output ownership transition");
        if(next!=null) main.post(this::bind);
        sync();
    }
    /** Retires demand; release/death callbacks remain alive until ownership is proven settled. */
    public void close() {
        ClusterSurfaceOutput.Borrow old;
        ClusterReleaseLedger.Entry<RemoteOutput> retiring;
        synchronized(lock) {
            if(closed) return;
            closed=true; revision++; old=desired; desired=null; retiring=ledger.active();
        }
        retireNow(retiring);
        if(old!=null) old.close();
        sync();
    }
    private void retireNow(ClusterReleaseLedger.Entry<RemoteOutput> active) {
        if(active!=null) {
            ledger.beginRetirement(active);
            watchRetirement(active);
        }
        // Called by demand retirement, not the possibly blocked Binder worker.
    }
    private boolean execute(Runnable action) {
        try { worker.execute(action); return true; }
        catch(java.util.concurrent.RejectedExecutionException stopped) { return false; }
    }
    private void sync() {
        dirty.set(true);
        if(!queued.compareAndSet(false,true)) return;
        if(!execute(()->{
            try {
                do { dirty.set(false); drive(); } while(dirty.get());
            } finally {
                queued.set(false);
                if(dirty.get()) sync();
            }
        })) queued.set(false);
    }
    private void drive() {
        ClusterReleaseLedger.Entry<RemoteOutput> entry;
        Binding target;
        long version;
        ClusterSurfaceOutput.Borrow output;
        synchronized(lock) {
            if(finished) return;
            version=revision; output=desired; target=remote; entry=ledger.active();
        }
        if(entry!=null) {
            boolean same=output!=null && entry.revision==version && !closed &&
                    output.output==entry.value.borrow.output && output.output.isAvailable();
            if(same && !ledger.isRetiring(entry)) return;
            Binding owner=(Binding)entry.connection;
            if(entry.value.stopAttemptRevision==version) return;
            entry.value.stopAttemptRevision=version;
            ledger.beginRetirement(entry);
            watchRetirement(entry);
            if(!owner.binder.isBinderAlive()) {
                ledger.quarantine(entry);
                notifyCurrent(version,AaClusterProtocol.FAILED,"old output quarantined after Service death");
                return; // Out-of-process codec cleanup is NOT proven by Binder death.
            }
            try { sendOutput(owner,newRequest(),false,null); }
            catch(Throwable uncertain) {
                Log.w(TAG,"CLUSTER disable transaction failed; consumer quarantined",uncertain);
                ledger.quarantine(entry);
                notifyCurrent(version,AaClusterProtocol.FAILED,"output retirement uncertain; consumer retained");
            }
            return; // Only CALLBACK_RELEASED may make room for a new output.
        }
        if(closed) { finishClose(); return; }
        if(output==null) { notifyCurrent(version,AaClusterProtocol.DISABLED,"CLUSTER output disabled"); return; }
        if(target==null || !target.binder.isBinderAlive()) return;
        ClusterSurfaceOutput.Borrow exposed;
        synchronized(lock) {
            if(closed || revision!=version || desired!=output || remote!=target) { dirty.set(true); return; }
            if(!output.output.isAvailable()) return;
            try { exposed=output.fork(); } catch(IllegalStateException retired) { return; }
            entry=ledger.claim(target,newRequest(),version,new RemoteOutput(exposed));
            if(entry==null) { exposed.close(); dirty.set(true); return; }
        }
        // Record possible remote ownership BEFORE transact. Keep a separate
        // transport fork so an early terminal callback cannot free its input.
        ClusterSurfaceOutput.Borrow inFlight=exposed.fork();
        try {
            notifyCurrent(version,AaClusterProtocol.WAITING_SESSION,"waiting for authenticated CLUSTER output");
            sendOutput(target,entry.request,true,inFlight.output);
            Log.w(TAG,"CLUSTER output accepted request="+entry.request);
        } catch(Throwable uncertain) {
            Log.w(TAG,"CLUSTER output transaction failed request="+entry.request,uncertain);
            ledger.quarantine(entry);
            notifyCurrent(version,AaClusterProtocol.FAILED,"output transaction uncertain; consumer retained");
            dirty.set(true); // One ordered disable attempt, never a release shortcut.
        } finally { inFlight.close(); }
    }
    private void watchRetirement(ClusterReleaseLedger.Entry<RemoteOutput> entry) {
        if(!entry.value.timeoutScheduled.compareAndSet(false,true)) return;
        main.postDelayed(()->{
            if(ledger.active()!=entry || !ledger.isRetiring(entry)) return;
            ledger.quarantine(entry);
            notifyCurrent(currentRevision(),AaClusterProtocol.FAILED,"decoder retirement unconfirmed; consumer retained",entry);
        },5000);
        // This bounds waiting status only. It never frees a consumer, invents
        // an ACK, interrupts a native close, or admits a second remote output.
    }
    private long newRequest() {
        synchronized(lock) {
            if(nextRequest==Long.MAX_VALUE) throw new IllegalStateException("CLUSTER request sequence exhausted");
            return ++nextRequest;
        }
    }
    private void sendOutput(Binding target,long request,boolean enabled,ClusterSurfaceOutput output)throws Exception {
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try {
            data.writeInterfaceToken(AaClusterProtocol.DESCRIPTOR);
            data.writeInt(AaClusterProtocol.VERSION); data.writeInt(AaClusterProtocol.SET_OUTPUT);
            data.writeLong(request); data.writeInt(enabled?1:0); data.writeStrongBinder(target.callback); data.writeInt(enabled?1:0);
            if(enabled) output.surface.writeToParcel(data,0);
            if(!target.binder.transact(AaClusterProtocol.TRANSACTION,data,reply,0)) throw new IllegalStateException("No CLUSTER extension");
            reply.readException();
            if(reply.dataAvail()<8 || reply.readInt()!=AaClusterProtocol.VERSION) throw new IllegalStateException("Invalid CLUSTER response");
            int accepted=reply.readInt();
            if((accepted!=AaClusterProtocol.WAITING_SESSION && accepted!=AaClusterProtocol.DISABLED) || reply.dataAvail()!=0) throw new IllegalStateException("Invalid CLUSTER acceptance");
            // Acceptance is not resource release, even for an accepted disable.
        } finally { reply.recycle(); data.recycle(); }
    }
    private void finishClose() {
        Binding previous;
        synchronized(lock) {
            if(finished || ledger.active()!=null) return;
            finished=true; previous=remote; remote=null; connectionGeneration++;
        }
        if(previous!=null) previous.unlink();
        main.post(()->{
            boolean unbind;
            synchronized(lock) { unbind=bindingRegistered; bindingRegistered=false; }
            if(unbind) try { context.unbindService(connection); } catch(IllegalArgumentException ignored) {}
        });
        worker.shutdown();
    }
    private void bind() {
        synchronized(lock) { if(closed || bindingRegistered) return; bindingRegistered=true; }
        boolean accepted=false;
        try {
            accepted=context.bindService(new Intent(AaClusterProtocol.SERVICE_ACTION).setPackage(AaClusterProtocol.SERVICE_PACKAGE),connection,Context.BIND_AUTO_CREATE);
        } catch(RuntimeException unavailable) {
            Log.w(TAG,"AA Service bind threw",unavailable);
            notifyCurrent(currentRevision(),AaClusterProtocol.FAILED,"AA Service binding failed");
        }
        if(!accepted) { Log.w(TAG,"AA Service bind not accepted; retrying"); synchronized(lock) { bindingRegistered=false; } retry(); }
    }
    private void retry() { main.postDelayed(()->{ synchronized(lock) { if(closed) return; } bind(); },2000); }
    private long currentRevision() { synchronized(lock) { return revision; } }
    private final ServiceConnection connection=new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name,IBinder binder) {
            final long generation;
            synchronized(lock) { if(closed) return; generation=++connectionGeneration; }
            execute(()->{
                Binding candidate=null;
                try {
                    int uid=verifyService(name,binder); query(binder);
                    candidate=new Binding(binder,uid); binder.linkToDeath(candidate,0);
                    Binding previous;
                    synchronized(lock) {
                        if(closed || generation!=connectionGeneration || !binder.isBinderAlive()) { candidate.unlink(); return; }
                        previous=remote; remote=candidate; revision++;
                    }
                    retireBindingIfUnused(previous);
                    Log.w(TAG,"CLUSTER Service verified uid="+uid);
                    notifyCurrent(currentRevision(),AaClusterProtocol.WAITING_SESSION,"verified new Service connection");
                    sync();
                } catch(Throwable unavailable) {
                    Log.w(TAG,"CLUSTER Service verification/query failed",unavailable);
                    if(candidate!=null) candidate.unlink();
                    synchronized(lock) { if(closed || generation!=connectionGeneration) return; }
                    notifyCurrent(currentRevision(),AaClusterProtocol.FAILED,"CLUSTER extension unavailable or caller not authorized");
                }
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) { disconnected(null); }
        @Override public void onBindingDied(ComponentName name) { disconnected(null); }
        @Override public void onNullBinding(ComponentName name) { disconnected(null); }
    };
    private void retireBindingIfUnused(Binding binding) {
        if(binding==null) return;
        synchronized(lock) {
            ClusterReleaseLedger.Entry<RemoteOutput> active=ledger.active();
            if(binding==remote || active!=null && active.connection==binding) return;
        }
        binding.unlink();
    }
    private void disconnected(Binding expected) {
        final long generation;
        Binding previous;
        synchronized(lock) {
            if(finished || expected!=null && remote!=expected) return;
            previous=remote; remote=null; generation=++connectionGeneration; revision++;
            ClusterReleaseLedger.Entry<RemoteOutput> active=ledger.active();
            if(active!=null && (expected==null || active.connection==expected)) ledger.quarantine(active);
        }
        retireBindingIfUnused(previous);
        notifyCurrent(currentRevision(),AaClusterProtocol.WAITING_SESSION,"AA Service disconnected; leases retained");
        sync();
        main.post(()->{
            boolean unbind;
            synchronized(lock) { if(generation!=connectionGeneration) return; unbind=bindingRegistered; bindingRegistered=false; }
            if(unbind) try { context.unbindService(connection); } catch(IllegalArgumentException ignored) {}
            if(!closed) retry();
        });
    }
    private final class Binding implements IBinder.DeathRecipient {
        final IBinder binder;
        final int uid;
        final Binder callback;
        Binding(IBinder binder,int uid) {
            this.binder=binder; this.uid=uid;
            callback=new Binder() {
                @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
                    if(code!=AaClusterProtocol.CALLBACK_STATE && code!=AaClusterProtocol.CALLBACK_RELEASED) return super.onTransact(code,data,reply,flags);
                    if(Binder.getCallingUid()!=Binding.this.uid || data==null || data.dataSize()>AaClusterProtocol.MAX_PARCEL_BYTES) return false;
                    try {
                        data.enforceInterface(AaClusterProtocol.CALLBACK_DESCRIPTOR);
                        if(data.dataAvail()<12 || data.readInt()!=AaClusterProtocol.VERSION) return false;
                        long request=data.readLong();
                        if(request<=0) return false;
                        if(code==AaClusterProtocol.CALLBACK_RELEASED) {
                            if(data.dataAvail()!=0) return false;
                            // Never filtered by current revision, closed or remote.
                            if(ledger.released(Binding.this,request)) {
                                notifyNoRemoteOutput(currentRevision());
                                retireBindingIfUnused(Binding.this); sync();
                            }
                            return true;
                        }
                        if(data.dataAvail()<12) return false;
                        long generation=data.readLong(); int state=data.readInt(); String reason=data.readString();
                        if(data.dataAvail()!=0 || state<AaClusterProtocol.DISABLED || state>AaClusterProtocol.FAILED || reason==null || reason.length()>120 || state==AaClusterProtocol.LIVE && generation<=0) return false;
                        ClusterReleaseLedger.Entry<RemoteOutput> active=ledger.active();
                        if(active!=null && active.connection==Binding.this && active.request==request && !ledger.isRetiring(active)) notifyCurrent(active.revision,state,reason,active);
                        return true;
                    } catch(Exception malformed) { return false; }
                }
            };
        }
        @Override public void binderDied() {
            ClusterReleaseLedger.Entry<RemoteOutput> active=ledger.active();
            if(active!=null && active.connection==this) {
                ledger.quarantine(active);
                notifyCurrent(currentRevision(),AaClusterProtocol.FAILED,"Service died; consumer remains quarantined",active);
                sync();
            }
            // Binder death is not proof of downstream codec-service quiescence.
            disconnected(this);
        }
        void unlink() { try { binder.unlinkToDeath(this,0); } catch(RuntimeException ignored) {} }
    }
    private void notifyNoRemoteOutput(long version) {
        main.post(()->{
            int state;
            synchronized(lock) {
                if(closed || revision!=version || ledger.active()!=null) return;
                state=desired==null ? AaClusterProtocol.DISABLED : AaClusterProtocol.WAITING_SESSION;
            }
            listener.onStatus(state,"previous output fully released");
        });
    }
    private void notifyCurrent(long version,int state,String reason) {
        notifyCurrent(version,state,reason,null);
    }
    private void notifyCurrent(long version,int state,String reason,ClusterReleaseLedger.Entry<RemoteOutput> expected) {
        main.post(()->{
            synchronized(lock) {
                if(closed || revision!=version || expected!=null && ledger.active()!=expected) return;
                if(state==AaClusterProtocol.LIVE && expected!=null && ledger.isRetiring(expected)) return;
                if(state==AaClusterProtocol.LIVE && (desired==null || !desired.output.isAvailable())) return;
            }
            listener.onStatus(state,reason);
        });
    }
    private int verifyService(ComponentName name,IBinder binder)throws Exception{
        if(name==null||!AaClusterProtocol.SERVICE_PACKAGE.equals(name.getPackageName())||binder==null||
                !"com.ts.androidauto.sdk.aidl.LinkCommand".equals(binder.getInterfaceDescriptor())){
            throw new SecurityException("Unexpected AA Service identity");
        }
        PackageInfo info=context.getPackageManager().getPackageInfo(AaClusterProtocol.SERVICE_PACKAGE,PackageManager.GET_SIGNING_CERTIFICATES);
        Signature[] signers=info.signingInfo==null?null:info.signingInfo.getApkContentsSigners();
        if(signers==null||signers.length!=1||info.applicationInfo==null)throw new SecurityException("Unexpected AA signer set");
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray());
        StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        if(!java.util.Arrays.asList(AaClusterProtocol.OEM_SIGNER_SHA256).contains(hex.toString()))throw new SecurityException("AA Service signer mismatch: "+hex);
        return info.applicationInfo.uid;
    }
    private void query(IBinder binder)throws Exception{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(AaClusterProtocol.DESCRIPTOR);data.writeInt(AaClusterProtocol.VERSION);data.writeInt(AaClusterProtocol.QUERY);
            if(!binder.transact(AaClusterProtocol.TRANSACTION,data,reply,0))throw new IllegalStateException("No CLUSTER extension");
            reply.readException();
            if(reply.readInt()!=AaClusterProtocol.VERSION||!AaClusterProtocol.PROFILE.equals(reply.readString()))throw new IllegalStateException("Unsupported CLUSTER profile");
            reply.readInt(); // Server status is not readiness for this client request.
            if(reply.dataAvail()!=0)throw new IllegalStateException("Unexpected CLUSTER capability response");
        }finally{reply.recycle();data.recycle();}
    }

}
