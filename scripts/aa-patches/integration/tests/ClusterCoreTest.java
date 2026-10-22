package com.ts.androidauto.impulse.cluster;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises actual platform-independent sources; no Android/OEM classes. */
public final class ClusterCoreTest {
    private interface Check { void run() throws Exception; }
    private static int total;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    private static void test(String name, Check action) throws Exception { action.run(); total++; System.out.println("PASS " + name); }
    private static void await(CountDownLatch value) throws Exception { check(value.await(5, TimeUnit.SECONDS), "latch timeout"); }
    private static void join(Thread thread) throws Exception { thread.join(5000); check(!thread.isAlive(), "thread stuck"); }
    private static void invalid(Check action) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("expected invalid input");
    }

    private static class Registry implements PairedRegistration.Registry {
        final Map<Integer,Object> values = new HashMap<>();
        final Map<Integer,Object> legacy = new HashMap<>();
        final List<Integer> removes = new ArrayList<>();
        final List<String> events;
        int putFailure, removeFailure;
        boolean putAfterMutation, removeAfterMutation, ignoreSecondPut;
        Runnable duringSecondPut;
        Registry(List<String> events) {
            this.events=events;
            for(int id:new int[]{1,2,3,8,20,99}) { Object value=new Object(); values.put(id,value);legacy.put(id,value); }
        }
        @Override public Object get(int id) {
            if(values.containsKey(id) && values.get(id)==null) throw new IllegalStateException("occupied-null");
            return values.get(id);
        }
        @Override public void put(int id,Object value) {
            events.add("put"+id);
            if(id==putFailure && !putAfterMutation) throw new IllegalStateException("put before mutation");
            if(!(id==22 && ignoreSecondPut)) values.put(id,value);
            if(id==22 && duringSecondPut!=null) duringSecondPut.run();
            if(id==putFailure) throw new IllegalStateException("put after mutation");
        }
        @Override public void remove(int id) {
            removes.add(id);
            if(id==removeFailure && !removeAfterMutation) throw new IllegalStateException("remove before mutation");
            values.remove(id);
            if(id==removeFailure) throw new IllegalStateException("remove after mutation");
        }
        void legacyIntact() {
            for(Map.Entry<Integer,Object> e:legacy.entrySet()) check(values.get(e.getKey())==e.getValue(),"legacy identity changed");
            for(int id:removes)check(id==21||id==22,"removed a legacy ID");
        }
        void pairAbsent(){check(!values.containsKey(21)&&!values.containsKey(22),"partial pair left");legacyIntact();}
    }
    private static class Provider implements PairedRegistration.Provider {
        final List<String> events;
        Object identity=this;
        long pointer;
        int creates,destroys;
        boolean createResult=true,createThrows,destroyThrows,zeroAfterSuccess,readThrows;
        Runnable duringCreate;
        Provider(List<String> events){this.events=events;}
        @Override public Object identity(){return identity;}
        @Override public long nativeInstance(){if(readThrows)throw new IllegalStateException("native read");return pointer;}
        @Override public boolean create(int id,long receiver){
            check(receiver==123,"receiver pointer changed");creates++;events.add("create"+id);
            pointer=zeroAfterSuccess?0:id;
            if(duringCreate!=null)duringCreate.run();
            if(createThrows)throw new IllegalStateException("configuration failure");
            return createResult;
        }
        @Override public void destroy(){destroys++;pointer=0;if(destroyThrows)throw new IllegalStateException("destroy failure");}
    }
    private static class Fixture {
        final List<String> events=new ArrayList<>();
        final Registry registry=new Registry(events);
        final Provider video=new Provider(events),input=new Provider(events);
        boolean register(){return PairedRegistration.register(registry,123,false,video,input);}
        void noCreation(){check(video.creates==0&&input.creates==0&&video.destroys==0&&input.destroys==0,"precondition mutated provider");registry.legacyIntact();}
        void rolledBack(){registry.pairAbsent();check(video.destroys==1&&input.destroys==1,"both fresh cleanup attempts required");}
    }
    private static byte[] bytes(int... values){byte[] result=new byte[values.length];for(int i=0;i<values.length;i++)result[i]=(byte)values[i];return result;}

    public static void main(String[] args) throws Exception {
        test("pair success creates/configures both before insertion",()->{
            Fixture f=new Fixture();check(f.register(),"not registered");
            check(f.events.equals(Arrays.asList("create21","create22","put21","put22")),"partial discovery order");
            check(f.registry.get(21)==f.video&&f.registry.get(22)==f.input,"wrong identity");
            check(f.video.destroys==0&&f.input.destroys==0,"destroyed success");f.registry.legacyIntact();
        });
        test("occupied video slot prevents all creation",()->{Fixture f=new Fixture();Object old=new Object();f.registry.values.put(21,old);check(!f.register(),"collision accepted");f.noCreation();check(f.registry.get(21)==old,"old video replaced");});
        test("occupied input slot prevents all creation",()->{Fixture f=new Fixture();Object old=new Object();f.registry.values.put(22,old);check(!f.register(),"collision accepted");f.noCreation();check(f.registry.get(22)==old,"old input replaced");});
        test("occupied-null adapter refuses before creation",()->{Fixture f=new Fixture();f.registry.values.put(22,null);check(!f.register(),"null key accepted");f.noCreation();});
        test("zero receiver pointer refuses before creation",()->{Fixture f=new Fixture();check(!PairedRegistration.register(f.registry,0,false,f.video,f.input),"zero accepted");f.noCreation();});
        test("stopping receiver refuses before creation",()->{Fixture f=new Fixture();check(!PairedRegistration.register(f.registry,123,true,f.video,f.input),"stopping accepted");f.noCreation();});
        test("already-created video is never destroyed",()->{Fixture f=new Fixture();f.video.pointer=1;check(!f.register(),"borrowed video accepted");f.noCreation();});
        test("already-created input is never destroyed",()->{Fixture f=new Fixture();f.input.pointer=2;check(!f.register(),"borrowed input accepted");f.noCreation();});
        test("duplicate underlying identity refuses before creation",()->{Fixture f=new Fixture();f.input.identity=f.video;check(!f.register(),"alias accepted");f.noCreation();});
        test("same provider wrapper refuses before creation",()->{Fixture f=new Fixture();check(!PairedRegistration.register(f.registry,123,false,f.video,f.video),"same provider accepted");f.noCreation();});
        test("null identities and parameters refuse before creation",()->{Fixture f=new Fixture();f.video.identity=null;check(!f.register(),"null identity accepted");check(!PairedRegistration.register(null,123,false,f.video,f.input),"null registry accepted");check(!PairedRegistration.register(f.registry,123,false,null,f.input),"null provider accepted");f.noCreation();});
        test("native-pointer precondition exception mutates nothing",()->{Fixture f=new Fixture();f.input.readThrows=true;check(!f.register(),"read failure accepted");f.noCreation();});
        test("video create false destroys only fresh pair",()->{Fixture f=new Fixture();f.video.createResult=false;check(!f.register(),"create false accepted");check(f.input.creates==0,"created unnecessary input");f.rolledBack();});
        test("input create false rolls video back",()->{Fixture f=new Fixture();f.input.createResult=false;check(!f.register(),"input false accepted");f.rolledBack();});
        test("video configuration exception rolls back",()->{Fixture f=new Fixture();f.video.createThrows=true;check(!f.register(),"config exception accepted");f.rolledBack();});
        test("input configuration exception rolls back",()->{Fixture f=new Fixture();f.input.createThrows=true;check(!f.register(),"config exception accepted");f.rolledBack();});
        test("success with zero native pointer is refused",()->{Fixture f=new Fixture();f.input.zeroAfterSuccess=true;check(!f.register(),"zero initialized pointer committed");f.rolledBack();});
        test("first put failure before mutation rolls back",()->{Fixture f=new Fixture();f.registry.putFailure=21;check(!f.register(),"put failure accepted");f.rolledBack();});
        test("first put failure after mutation rolls back",()->{Fixture f=new Fixture();f.registry.putFailure=21;f.registry.putAfterMutation=true;check(!f.register(),"put failure accepted");f.rolledBack();});
        test("second put failure before mutation removes first",()->{Fixture f=new Fixture();f.registry.putFailure=22;check(!f.register(),"partial pair accepted");f.rolledBack();});
        test("second put failure after mutation removes both",()->{Fixture f=new Fixture();f.registry.putFailure=22;f.registry.putAfterMutation=true;check(!f.register(),"partial pair accepted");f.rolledBack();});
        test("silently missing insertion cannot report success",()->{Fixture f=new Fixture();f.registry.ignoreSecondPut=true;check(!f.register(),"incomplete commit accepted");f.rolledBack();});
        test("rollback preserves a foreign replacement identity",()->{Fixture f=new Fixture();Object other=new Object();f.registry.duringSecondPut=()->{f.registry.values.put(21,other);throw new IllegalStateException("replacement");};check(!f.register(),"replacement accepted");check(f.registry.get(21)==other,"foreign identity removed");check(!f.registry.values.containsKey(22),"own input left");check(!f.registry.removes.contains(21),"removed foreign ID");f.registry.legacyIntact();});
        test("reentrant create occupancy is not overwritten",()->{Fixture f=new Fixture();Object other=new Object();f.video.duringCreate=()->f.registry.values.put(22,other);check(!f.register(),"reentrant collision accepted");check(f.registry.get(22)==other,"collision overwritten");check(f.video.destroys==1&&f.input.destroys==1,"fresh pair not cleaned");f.registry.legacyIntact();});
        test("destroy exceptions do not skip sibling cleanup",()->{Fixture f=new Fixture();f.input.createThrows=true;f.input.destroyThrows=true;f.video.destroyThrows=true;check(!f.register(),"cleanup errors masked as success");f.rolledBack();});
        test("remove exception after effect continues cleanup",()->{Fixture f=new Fixture();f.registry.putFailure=22;f.registry.putAfterMutation=true;f.registry.removeFailure=22;f.registry.removeAfterMutation=true;check(!f.register(),"cleanup failure accepted");f.rolledBack();});
        test("broken remove before effect is failure and cannot skip other cleanup",()->{Fixture f=new Fixture();f.registry.putFailure=22;f.registry.putAfterMutation=true;f.registry.removeFailure=22;f.input.destroyThrows=true;check(!f.register(),"broken cleanup reported success");check(f.registry.removes.contains(21)&&f.registry.removes.contains(22),"sibling removal skipped");check(f.video.destroys==1&&f.input.destroys==1,"sibling destroy skipped");f.registry.legacyIntact();});
        test("guard focus waits for setup then preserves request arguments",()->{
            AtomicInteger calls=new AtomicInteger();GuardedSink g=new GuardedSink(new GuardedSink.Calls(){public void acknowledge(int id,int n){throw new AssertionError("unexpected ACK");}public void focus(int mode,int reason,boolean unsolicited){check(mode==2&&reason==-1&&!unsolicited,"focus arguments changed");calls.incrementAndGet();}});
            check(!g.focus(2,false)&&calls.get()==0,"focus before setup");g.onSetup();check(g.focus(2,false)&&calls.get()==1,"focus missing");g.retire();g.onSetup();check(!g.focus(2)&&!g.acknowledge(7)&&!g.isReady(),"retired sink reactivated");
        });
        test("guard retirement waits for in-flight ACK and blocks every later native access",()->{
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),retireStarted=new CountDownLatch(1),retired=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();AtomicReference<Throwable> error=new AtomicReference<>();
            GuardedSink g=new GuardedSink(new GuardedSink.Calls(){public void acknowledge(int id,int n){check(id==7&&n==1,"ACK arguments changed");calls.incrementAndGet();entered.countDown();try{await(release);}catch(Exception e){throw new RuntimeException(e);}}public void focus(int mode,int reason,boolean u){calls.incrementAndGet();}});
            Thread ack=new Thread(()->{try{check(g.acknowledge(7),"ACK rejected");}catch(Throwable t){error.set(t);}});ack.start();await(entered);
            Thread end=new Thread(()->{try{retireStarted.countDown();g.retire();retired.countDown();}catch(Throwable t){error.set(t);}});end.start();await(retireStarted);check(!retired.await(75,TimeUnit.MILLISECONDS),"retired during native call");release.countDown();await(retired);join(ack);join(end);check(error.get()==null,"worker assertion failed");g.onSetup();check(!g.acknowledge(7)&&!g.focus(1)&&calls.get()==1,"native call after retirement");
        });
        test("Annex-B recognizes SPS PPS IDR and ordinary slices",()->{
            check(H264AccessUnit.hasSpsAndPps(bytes(0,0,0,1,0x67,0x42,0,0,1,0x68,0xce)),"parameter set presence missed");
            check(H264AccessUnit.hasIdr(ByteBuffer.wrap(bytes(0,0,1,0x65,0x88))),"IDR missed");
            check(!H264AccessUnit.hasIdr(ByteBuffer.wrap(bytes(0,0,0,1,0x61,0x88))),"non-IDR accepted");
        });
        test("Annex-B inspection preserves buffer identity bytes position limit and mark",()->{
            byte[] data=bytes(99,0,0,1,0x65,0x88,98),copy=data.clone();ByteBuffer b=ByteBuffer.wrap(data);b.position(1);b.limit(6);b.mark();check(H264AccessUnit.hasIdr(b),"window IDR missed");check(b.position()==1&&b.limit()==6&&Arrays.equals(data,copy),"input mutated");b.reset();check(b.position()==1,"mark lost");check(H264AccessUnit.hasIdr(b.asReadOnlyBuffer()),"read-only view unsupported");
        });
        test("Annex-B malformed framing and headers reject",()->{
            for(byte[] bad:new byte[][]{bytes(),bytes(0,0,1),bytes(0,0,0,1),bytes(1,2,3,4),bytes(9,0,0,1,0x65),bytes(0,0,1,0x80),bytes(0,0,1,0),bytes(0,0,1,24)}) invalid(()->H264AccessUnit.nalTypes(ByteBuffer.wrap(bad)));
        });
        test("Annex-B byte and NAL-count bounds reject excessive input",()->{
            invalid(()->H264AccessUnit.nalTypes(ByteBuffer.allocate(2*1024*1024+1)));
            byte[] many=new byte[257*5];for(int i=0;i<257;i++){many[i*5+2]=1;many[i*5+3]=0x61;many[i*5+4]=0x40;}
            invalid(()->H264AccessUnit.nalTypes(ByteBuffer.wrap(many)));
            ByteBuffer bounded=ByteBuffer.wrap(many);bounded.limit(256*5);check(H264AccessUnit.nalTypes(bounded)==(1<<1),"valid NAL bound rejected");
        });
        System.out.println("PASS total="+total+" core checks; no Android/OEM execution");
    }
}
