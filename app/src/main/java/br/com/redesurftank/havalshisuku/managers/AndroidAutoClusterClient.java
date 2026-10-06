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
import android.view.Surface;
import br.com.redesurftank.havalshisuku.api.AaClusterProtocol;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Authenticated, asynchronous client for the optional exact-profile CLUSTER extension. */
public final class AndroidAutoClusterClient {
    public interface Listener { void onStatus(int status, String reason); }
    private final Context context;
    private final Handler main = new Handler(android.os.Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r,"Impulse-Cluster-Binder");t.setDaemon(true);return t;
    });
    private final Object lock = new Object();
    private final Listener listener;
    private final AtomicBoolean syncQueued = new AtomicBoolean();
    private IBinder remote;
    private IBinder.DeathRecipient remoteDeath;
    private boolean bindingRegistered;
    private boolean closed;
    private long revision;
    private long connectionGeneration;
    private long lastSent;
    private boolean enabled;
    private SurfaceLease surface;
    private volatile int serviceUid = -1;

    public AndroidAutoClusterClient(Context context, Listener listener) {
        this.context=context.getApplicationContext();this.listener=listener;
    }
    public void start() { main.post(this::bind); }

    /** Duplicates the native handle; never releases the SurfaceHolder's handle. */
    public void setOutput(boolean enabled, Surface output) {
        SurfaceLease next = null;
        if (enabled && output != null && output.isValid()) {
            Parcel parcel = Parcel.obtain();
            try {
                output.writeToParcel(parcel, 0);
                parcel.setDataPosition(0);
                Surface copy = Surface.CREATOR.createFromParcel(parcel);
                if (copy.isValid()) next = new SurfaceLease(copy); else copy.release();
            } catch (RuntimeException unavailable) { /* Fail closed; holder may have gone away. */ }
            finally { parcel.recycle(); }
        }
        long version;
        SurfaceLease previous;
        synchronized(lock){
            if(closed){if(next!=null)next.release();return;}
            this.enabled=next!=null;
            previous=this.surface;this.surface=next;
            version=++revision;
        }
        if(previous!=null)previous.release();
        notifyCurrent(version,next!=null?AaClusterProtocol.WAITING_SESSION:AaClusterProtocol.DISABLED,
                next!=null?"waiting for authenticated CLUSTER output":"CLUSTER output disabled");
        if(next!=null)start();
        sync();
    }

    /** Reference-counted duplicate, including any in-flight Binder write. */
    private static final class SurfaceLease {
        final Surface value;
        private int references=1;
        SurfaceLease(Surface value){this.value=value;}
        synchronized void retain(){if(references<=0)throw new IllegalStateException("released Surface");references++;}
        synchronized void release(){if(--references==0)value.release();}
    }

    public void close() {
        SurfaceLease previous;
        final IBinder target;
        final long disableRequest;
        synchronized(lock){
            if(closed)return;
            closed=true;enabled=false;previous=surface;surface=null;
            target=remote;disableRequest=++revision;
            unlink(remote,remoteDeath);remoteDeath=null;
            remote=null;serviceUid=-1;connectionGeneration++;
            // Queue revocation behind any already queued enable transaction.
            // Unbinding alone does not kill the callback Binder in this process.
            execute(()->{
                if(target!=null)try{sendOutput(target,disableRequest,false,null);}catch(Throwable ignored){}
                main.post(()->{
                    boolean unbind;synchronized(lock){unbind=bindingRegistered;bindingRegistered=false;}
                    if(unbind)try{context.unbindService(connection);}catch(IllegalArgumentException ignored){}
                });
            });
            worker.shutdown();
        }
        if(previous!=null)previous.release();
    }

    private static void unlink(IBinder binder,IBinder.DeathRecipient recipient){
        if(binder!=null&&recipient!=null)try{binder.unlinkToDeath(recipient,0);}catch(RuntimeException ignored){}
    }

    private boolean execute(Runnable task) {
        try{worker.execute(task);return true;}
        catch(java.util.concurrent.RejectedExecutionException closedExecutor){return false;}
    }

    private void sendOutput(IBinder target,long request,boolean desired,Surface output)throws Exception{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(AaClusterProtocol.DESCRIPTOR);data.writeInt(AaClusterProtocol.VERSION);data.writeInt(AaClusterProtocol.SET_OUTPUT);
            data.writeLong(request);data.writeInt(desired?1:0);data.writeStrongBinder(callback);data.writeInt(desired?1:0);
            if(desired)output.writeToParcel(data,0);
            if(!target.transact(AaClusterProtocol.TRANSACTION,data,reply,0))throw new IllegalStateException("CLUSTER request unsupported");
            reply.readException();
            if(reply.readInt()!=AaClusterProtocol.VERSION)throw new IllegalStateException("CLUSTER response version mismatch");
            int accepted=reply.readInt();
            if((accepted!=AaClusterProtocol.WAITING_SESSION&&accepted!=AaClusterProtocol.DISABLED)||reply.dataAvail()!=0)throw new IllegalStateException("Invalid CLUSTER response");
        }finally{reply.recycle();data.recycle();}
    }

    private void bind() {
        synchronized(lock){if(closed||bindingRegistered)return;bindingRegistered=true;}
        boolean accepted=false;
        try {
            Intent intent=new Intent(AaClusterProtocol.SERVICE_ACTION).setPackage(AaClusterProtocol.SERVICE_PACKAGE);
            accepted=context.bindService(intent,connection,Context.BIND_AUTO_CREATE);
        } catch(Throwable failed){notifyCurrent(currentRevision(),AaClusterProtocol.FAILED,"AA Service binding failed");}
        if(!accepted){synchronized(lock){bindingRegistered=false;}retry();}
    }
    private void retry() {main.postDelayed(()->{synchronized(lock){if(closed)return;}bind();},2000);}
    private long currentRevision(){synchronized(lock){return revision;}}

    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            final long generation;
            synchronized(lock){
                if(closed)return;
                generation=++connectionGeneration;
                unlink(remote,remoteDeath);remoteDeath=null;remote=null;serviceUid=-1;
            }
            execute(()->{
                IBinder.DeathRecipient recipient=()->disconnected(binder);
                boolean linked=false;
                try {
                    int uid=verifyService(name,binder);
                    query(binder);
                    binder.linkToDeath(recipient,0);linked=true;
                    synchronized(lock){
                        if(closed||generation!=connectionGeneration){unlink(binder,recipient);return;}
                        if(!binder.isBinderAlive())throw new android.os.DeadObjectException();
                        remote=binder;remoteDeath=recipient;serviceUid=uid;lastSent=0;revision++;
                    }
                    sync();
                } catch(Throwable unavailable){
                    if(linked)unlink(binder,recipient);
                    synchronized(lock){if(closed||generation!=connectionGeneration)return;}
                    if(binder!=null&&!binder.isBinderAlive()){disconnected(null);return;}
                    notifyCurrent(currentRevision(),AaClusterProtocol.FAILED,"CLUSTER extension unavailable or caller not authorized");
                }
            });
        }
        @Override public void onServiceDisconnected(ComponentName name){disconnected(null);}
        @Override public void onBindingDied(ComponentName name){disconnected(null);}
        @Override public void onNullBinding(ComponentName name){disconnected(null);}
    };

    private void disconnected(IBinder expected) {
        long version;
        final long cleanupGeneration;
        synchronized(lock){
            if(closed||(expected!=null&&remote!=expected))return;
            unlink(remote,remoteDeath);remoteDeath=null;
            remote=null;serviceUid=-1;cleanupGeneration=++connectionGeneration;version=++revision;lastSent=0;
        }
        notifyCurrent(version,AaClusterProtocol.WAITING_SESSION,"AA Service disconnected");
        main.post(()->{
            boolean unbind;
            synchronized(lock){
                if(closed||cleanupGeneration!=connectionGeneration)return;
                unbind=bindingRegistered;bindingRegistered=false;
            }
            if(unbind)try{context.unbindService(connection);}catch(IllegalArgumentException ignored){}
            retry();
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
        if(!AaClusterProtocol.OEM_SIGNER_SHA256.equals(hex.toString()))throw new SecurityException("AA Service signer mismatch");
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
    private void sync(){
        if(!syncQueued.compareAndSet(false,true))return;
        if(!execute(()->{
            long attempted=0;
            try{
                while(true){
                    IBinder target;SurfaceLease output;boolean desired;long request;long connectionId;
                    synchronized(lock){
                        if(closed||remote==null)return;
                        target=remote;output=surface;desired=enabled;request=revision;connectionId=connectionGeneration;
                        if(request==lastSent)return;
                        if(output!=null)output.retain();
                    }
                    attempted=request;
                    try{
                        if(desired&&(output==null||!output.value.isValid())){
                            notifyCurrent(request,AaClusterProtocol.WAITING_SESSION,"output Surface changed");return;
                        }
                        synchronized(lock){if(closed||request!=revision||connectionId!=connectionGeneration)continue;}
                        sendOutput(target,request,desired,desired?output.value:null);
                        synchronized(lock){if(connectionId==connectionGeneration)lastSent=request;}
                    }finally{if(output!=null)output.release();}
                }
            }catch(Throwable failed){notifyCurrent(attempted,AaClusterProtocol.FAILED,"CLUSTER output request failed");}
            finally{
                syncQueued.set(false);
                boolean changed;synchronized(lock){changed=!closed&&remote!=null&&revision!=attempted&&revision!=lastSent;}
                if(changed)sync();
            }
        }))syncQueued.set(false);
    }

    private final Binder callback=new Binder(){
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            if(code!=AaClusterProtocol.CALLBACK_STATE)return super.onTransact(code,data,reply,flags);
            if(Binder.getCallingUid()!=serviceUid||serviceUid<0||data==null||data.dataSize()>AaClusterProtocol.MAX_PARCEL_BYTES)return false;
            try{
                data.enforceInterface(AaClusterProtocol.CALLBACK_DESCRIPTOR);
                if(data.readInt()!=AaClusterProtocol.VERSION)return false;
                long request=data.readLong(),generation=data.readLong();int state=data.readInt();String reason=data.readString();
                if(data.dataAvail()!=0||state<AaClusterProtocol.DISABLED||state>AaClusterProtocol.FAILED||reason==null||reason.length()>120||
                        state==AaClusterProtocol.LIVE&&generation<=0)return false;
                notifyCurrent(request,state,reason);return true;
            }catch(RuntimeException malformed){return false;}
        }
    };
    private void notifyCurrent(long request,int state,String reason){
        main.post(()->{
            synchronized(lock){
                if(closed||request!=revision)return;
                if(state==AaClusterProtocol.LIVE&&(!enabled||surface==null||!surface.value.isValid()))return;
            }
            listener.onStatus(state,reason);
        });
    }
}
