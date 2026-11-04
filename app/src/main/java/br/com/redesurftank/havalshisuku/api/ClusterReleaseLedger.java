package br.com.redesurftank.havalshisuku.api;

/** One possibly submitted output; only its exact terminal proof removes it. */
public final class ClusterReleaseLedger<T extends AutoCloseable> {
    public static final class Entry<T> {
        public final Object connection;
        public final long request;
        public final long revision;
        public final T value;
        private boolean retiring;
        private boolean uncertain;
        private boolean settlementStarted;
        private Entry(Object connection, long request, long revision, T value) {
            this.connection=connection; this.request=request; this.revision=revision; this.value=value;
        }
    }
    private Entry<T> active;
    /** Ownership transfers only on a nonnull result; never replaces an old lease. */
    public synchronized Entry<T> claim(Object connection, long request, long revision, T value) {
        if (connection==null || value==null || request<=0) throw new IllegalArgumentException("Invalid output identity");
        if (active!=null) return null;
        active=new Entry<>(connection,request,revision,value);
        return active;
    }
    public synchronized Entry<T> active() { return active; }
    public synchronized boolean isRetiring(Entry<T> entry) { return active==entry && entry.retiring; }
    public synchronized boolean isUncertain(Entry<T> entry) { return active==entry && entry.uncertain; }
    public synchronized boolean beginRetirement(Entry<T> entry) {
        if (active!=entry || entry.retiring) return false;
        entry.retiring=true; return true;
    }
    /** Disconnect/error/death/timeout are uncertainty, never quiescence proof. */
    public synchronized void quarantine(Entry<T> entry) {
        if (active==entry) { entry.retiring=true; entry.uncertain=true; }
    }
    /** The caller must authenticate the callback before invoking this method. */
    public boolean released(Object connection, long request) throws Exception {
        Entry<T> settled;
        synchronized(this) {
            if (active==null || active.connection!=connection || active.request!=request || active.settlementStarted) return false;
            settled=active; settled.settlementStarted=true; settled.retiring=true;
        }
        try { settled.value.close(); }
        catch(Throwable failure) {
            synchronized(this) { settled.retiring=true; settled.uncertain=true; }
            // No retry and no new claim while local disposal is uncertain.
            if(failure instanceof Exception) throw (Exception)failure;
            if(failure instanceof Error) throw (Error)failure;
            throw new IllegalStateException(failure);
        }
        synchronized(this) { if(active==settled) active=null; }
        return true;
    }
}
