package com.ts.androidauto.impulse.cluster;

/**
 * Serializes every access to this CLUSTER native endpoint against retirement.
 * retire() is synchronous and MUST precede OEM provider/native destruction.
 * It does not destroy the endpoint or stop the shared GAL session.
 */
public final class GuardedSink {
    public interface Calls {
        void acknowledge(int sessionId, int count);
        void focus(int mode, int reason, boolean unsolicited);
    }
    private final Calls calls;
    private boolean retired;
    private boolean setup;

    public GuardedSink(Calls calls) {
        if (calls == null) throw new NullPointerException("calls");
        this.calls = calls;
    }
    public synchronized void onSetup() { if (!retired) setup = true; }
    public synchronized boolean isReady() { return !retired && setup; }
    public synchronized boolean acknowledge(int sessionId) {
        if (retired) return false; // Connection is gone: never ACK freed native memory.
        calls.acknowledge(sessionId, 1);
        return true;
    }
    public boolean focus(int mode) { return focus(mode, true); }
    public synchronized boolean focus(int mode, boolean unsolicited) {
        if (retired || !setup) return false; // Caller retains pending demand until setup.
        calls.focus(mode, -1, unsolicited);
        return true; // Request sent only, not phone acknowledgment.
    }
    public synchronized void retire() { retired = true; setup = false; }
    public synchronized boolean isRetired() { return retired; }
}
