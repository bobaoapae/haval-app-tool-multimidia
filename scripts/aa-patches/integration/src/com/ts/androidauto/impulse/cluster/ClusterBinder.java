package com.ts.androidauto.impulse.cluster;

import android.content.Context;
import android.os.BadParcelableException;
import android.os.IBinder;
import android.os.Parcel;
import android.view.Surface;
import br.com.redesurftank.havalshisuku.api.AaClusterProtocol;

/** Handler for transaction55 ONLY. All legacy transactions remain in OEM super. */
public final class ClusterBinder {
    private ClusterBinder() {}
    public static boolean dispatch(Context context, Parcel data, Parcel reply, int flags) {
        int caller = ClusterAuthorization.requireTrustedClient(context); // Before Surface/untrusted parsing.
        if (data == null || reply == null || flags != 0 || data.dataSize() > AaClusterProtocol.MAX_PARCEL_BYTES) {
            throw new BadParcelableException("Synchronous bounded CLUSTER request required");
        }
        data.enforceInterface(AaClusterProtocol.DESCRIPTOR);
        if (readInt(data) != AaClusterProtocol.VERSION) throw new BadParcelableException("Unsupported CLUSTER protocol");
        int operation = readInt(data);
        if (operation == AaClusterProtocol.QUERY) {
            requireEnd(data);
            reply.writeNoException();
            reply.writeInt(AaClusterProtocol.VERSION);
            reply.writeString(AaClusterProtocol.PROFILE);
            reply.writeInt(ClusterIntegration.status());
            return true;
        }
        if (operation != AaClusterProtocol.SET_OUTPUT) throw new BadParcelableException("Unknown CLUSTER operation");
        requireBytes(data, 8);
        long request = data.readLong();
        int enabledValue = readInt(data);
        requireBytes(data, 4);
        IBinder callback = data.readStrongBinder();
        int surfacePresent = readInt(data);
        if (request <= 0 || (enabledValue != 0 && enabledValue != 1) || callback == null ||
                (surfacePresent != 0 && surfacePresent != 1) || (enabledValue == 1) != (surfacePresent == 1)) {
            throw new BadParcelableException("Invalid CLUSTER ownership/generation fields");
        }
        Surface surface = null;
        boolean transferred = false;
        try {
            if (surfacePresent == 1) {
                surface = Surface.CREATOR.createFromParcel(data);
                if (surface == null || !surface.isValid()) throw new BadParcelableException("Invalid output Surface");
            }
            requireEnd(data);
            ClusterIntegration.setOutput(caller, request, enabledValue == 1, callback, surface);
            transferred = true;
            reply.writeNoException();
            reply.writeInt(AaClusterProtocol.VERSION);
            // Accepted for asynchronous processing. NEVER reports live pixels.
            reply.writeInt(enabledValue == 1 ? AaClusterProtocol.WAITING_SESSION : AaClusterProtocol.DISABLED);
            return true;
        } finally {
            if (!transferred && surface != null) surface.release();
        }
    }
    private static int readInt(Parcel data) {
        requireBytes(data, 4);
        return data.readInt();
    }
    private static void requireBytes(Parcel data, int count) {
        if (data.dataAvail() < count) throw new BadParcelableException("Truncated CLUSTER parcel");
    }
    private static void requireEnd(Parcel data) {
        if (data.dataAvail() != 0) throw new BadParcelableException("Unexpected trailing CLUSTER parcel data");
    }
}
