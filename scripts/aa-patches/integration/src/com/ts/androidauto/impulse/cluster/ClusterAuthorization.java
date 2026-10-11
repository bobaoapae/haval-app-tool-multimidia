package com.ts.androidauto.impulse.cluster;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Binder;
import br.com.redesurftank.havalshisuku.api.AaClusterProtocol;
import java.security.MessageDigest;

/** Authorizes only the new extension; never changes legacy OEM authorization. */
public final class ClusterAuthorization {
    private ClusterAuthorization() {}
    public static int requireTrustedClient(Context context) {
        int caller = Binder.getCallingUid();
        try {
            if (!GeneratedTrust.HANDOFF_ENABLED || GeneratedTrust.CLIENT_CERTIFICATES.length == 0) {
                throw new SecurityException("CLUSTER caller trust is not configured");
            }
            PackageManager pm = context.getPackageManager();
            ApplicationInfo application = pm.getApplicationInfo(AaClusterProtocol.CLIENT_PACKAGE, 0);
            String[] packages = pm.getPackagesForUid(caller);
            if (!application.enabled || application.uid != caller || packages == null || packages.length != 1 ||
                    !AaClusterProtocol.CLIENT_PACKAGE.equals(packages[0])) {
                throw new SecurityException("Unexpected or ambiguous CLUSTER caller UID");
            }
            PackageInfo info = pm.getPackageInfo(AaClusterProtocol.CLIENT_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);
            Signature[] signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
            if (signatures == null || signatures.length != 1) throw new SecurityException("Single current client signer required");
            String actual = hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()));
            boolean trusted = false;
            for (String expected : GeneratedTrust.CLIENT_CERTIFICATES) {
                if (actual.equals(expected)) { trusted = true; break; }
            }
            if (!trusted) throw new SecurityException("Untrusted CLUSTER client signing certificate");
            return caller;
        } catch (SecurityException denied) {
            throw denied;
        } catch (Exception failure) {
            throw new SecurityException("Cannot verify CLUSTER client identity", failure);
        }
    }
    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] output = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            output[i * 2] = digits[(bytes[i] & 255) >>> 4];
            output[i * 2 + 1] = digits[bytes[i] & 15];
        }
        return new String(output);
    }
}
