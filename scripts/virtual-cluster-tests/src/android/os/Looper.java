package android.os;

/** Deterministic test looper. A real worker thread has no looper. */
public final class Looper {
    private static final Thread MAIN_THREAD = Thread.currentThread();
    private static final Looper MAIN = new Looper();
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return Thread.currentThread() == MAIN_THREAD ? MAIN : null; }
}
