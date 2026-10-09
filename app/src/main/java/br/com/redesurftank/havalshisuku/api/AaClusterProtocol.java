package br.com.redesurftank.havalshisuku.api;

/** Private, versioned extension to the exact supported OEM LinkCommand binder. */
public final class AaClusterProtocol {
    private AaClusterProtocol() {}
    public static final int TRANSACTION = 55;
    public static final int VERSION = 2;
    public static final String DESCRIPTOR = "com.ts.androidauto.impulse.cluster.v2";
    public static final String CALLBACK_DESCRIPTOR = "br.com.redesurftank.havalshisuku.cluster.callback.v2";
    public static final String PROFILE = "stock48ff-cluster-v2";
    public static final String CLIENT_PACKAGE = "br.com.redesurftank.havalshisuku";
    public static final String SERVICE_PACKAGE = "com.ts.androidauto.projectionservice";
    public static final String SERVICE_ACTION = "com.ts.androidauto.action.AndroidAutoService";
    public static final String OEM_SIGNER_SHA256 = "7be3a99482e3f2f7f4f411f0a5a571ac97a505e500f9e05863fa8574e00baeb0";
    /**
     * Coded CLUSTER stream the Service advertises: VIDEO_1920x1080 with a 360 px
     * total height margin, so the phone draws only the middle 1920x720 band —
     * pixel-exact for the D3 panel. The host crops the margins away.
     */
    public static final int STREAM_WIDTH = 1920;
    public static final int STREAM_HEIGHT = 1080;
    public static final int STREAM_HEIGHT_MARGIN = 360;
    public static final int QUERY = 1;
    public static final int SET_OUTPUT = 2;
    public static final int CALLBACK_STATE = 1;
    public static final int CALLBACK_RELEASED = 2;
    public static final int MAX_PARCEL_BYTES = 8192;
    public static final int DISABLED = 0;
    public static final int WAITING_SESSION = 1;
    public static final int WAITING_SETUP = 2;
    public static final int WAITING_CONFIG = 3;
    public static final int WAITING_KEYFRAME = 4;
    public static final int LIVE = 5;
    public static final int FAILED = 6;
}
