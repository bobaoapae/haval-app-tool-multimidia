package com.ts.androidauto.impulse.cluster;

/**
 * Small platform-independent transaction for the caller's two fresh endpoints.
 * The caller MUST hold the receiver/registry lock for this entire operation.
 * Provider.create must include all native configuration before returning true.
 * This class never registers a native channel or changes any other service ID.
 */
public final class PairedRegistration {
    public static final int VIDEO_ID = 21;
    public static final int INPUT_ID = 22;

    /**
     * get returns null only for an ABSENT key. An adapter for a container allowing
     * occupied-null entries must detect and reject those rather than return null.
     * Operations must affect only the requested key. No concurrent mutation is
     * permitted while register holds the caller's external lock.
     */
    public interface Registry {
        Object get(int id);
        void put(int id, Object value);
        void remove(int id);
    }

    /** identity is the stable, caller-owned provider object, never a proxy key. */
    public interface Provider {
        Object identity();
        long nativeInstance();
        boolean create(int id, long receiverPointer);
        void destroy();
    }

    private PairedRegistration() {}

    /**
     * Returns true only after both fresh, configured providers are visible at
     * their reserved IDs. On failure each rollback operation is attempted even
     * if another throws. Only the exact captured identities may be removed.
     *
     * A callback which throws may already have acted, so it is never retried.
     * A broken remove/destroy implementation can prevent complete cleanup; false
     * reports failure, not proof such an implementation completed rollback.
     */
    public static boolean register(Registry registry, long receiverPointer, boolean stopping,
                                   Provider video, Provider input) {
        if (registry == null || video == null || input == null || video == input ||
                stopping || receiverPointer == 0) return false;
        Object videoIdentity;
        Object inputIdentity;
        try {
            videoIdentity = video.identity();
            inputIdentity = input.identity();
            if (videoIdentity == null || inputIdentity == null || videoIdentity == inputIdentity ||
                    registry.get(VIDEO_ID) != null || registry.get(INPUT_ID) != null ||
                    video.nativeInstance() != 0 || input.nativeInstance() != 0) return false;
        } catch (Throwable invalidPrecondition) {
            return false; // No creation attempted, therefore no cleanup ownership.
        }

        boolean committed = false;
        try {
            if (!video.create(VIDEO_ID, receiverPointer) || video.nativeInstance() == 0) return false;
            if (!input.create(INPUT_ID, receiverPointer) || input.nativeInstance() == 0) return false;
            // Defensive check against a reentrant/misbehaving create callback.
            // Legitimate callers already serialize all registry access.
            if (registry.get(VIDEO_ID) != null || registry.get(INPUT_ID) != null) return false;
            registry.put(VIDEO_ID, videoIdentity);
            registry.put(INPUT_ID, inputIdentity);
            if (registry.get(VIDEO_ID) != videoIdentity || registry.get(INPUT_ID) != inputIdentity) return false;
            committed = true;
            return true;
        } catch (Throwable preparationOrCommitFailure) {
            return false;
        } finally {
            if (!committed) {
                removeOwn(registry, INPUT_ID, inputIdentity);
                removeOwn(registry, VIDEO_ID, videoIdentity);
                // Both were proven fresh before any create. A not-yet-created
                // provider's destroy must safely accept a zero native pointer.
                destroyOwn(input);
                destroyOwn(video);
            }
        }
    }

    private static void removeOwn(Registry registry, int id, Object identity) {
        try { if (registry.get(id) == identity) registry.remove(id); }
        catch (Throwable cleanupFailure) { /* register already returns false; continue other cleanup. */ }
    }

    private static void destroyOwn(Provider provider) {
        try { provider.destroy(); }
        catch (Throwable cleanupFailure) { /* register already returns false; continue other cleanup. */ }
    }
}
