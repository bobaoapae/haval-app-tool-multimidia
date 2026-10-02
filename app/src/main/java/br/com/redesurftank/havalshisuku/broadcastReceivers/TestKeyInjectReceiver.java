package br.com.redesurftank.havalshisuku.broadcastReceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.managers.TestKeyInjectGate;
import br.com.redesurftank.havalshisuku.models.ClusterKey;
import br.com.redesurftank.havalshisuku.models.ClusterKeyMap;

/**
 * Test-only inject of mapped cluster keys through Impulse's BeanInput path.
 *
 * <p>Armed only while {@link TestKeyInjectGate} token file exists (adb/root). Cleared on boot.
 *
 * <pre>
 *   # arm (once per session)
 *   TOKEN=$(tr -dc a-f0-9 &lt;/dev/urandom | head -c 32)
 *   echo -n "$TOKEN" &gt; /data/local/tmp/impulse_key_inject.token
 *
 *   # inject (BeanTech uses ACTION_UP)
 *   am broadcast -a br.com.redesurftank.havalshisuku.TEST_INJECT_KEY \
 *     -n br.com.redesurftank.havalshisuku/.broadcastReceivers.TestKeyInjectReceiver \
 *     --es token "$TOKEN" --es key UP
 *
 *   # or by keyCode
 *   am broadcast ... --es token "$TOKEN" --ei keyCode 1024
 *
 *   # disarm
 *   am broadcast -a br.com.redesurftank.havalshisuku.TEST_DISARM_KEY_INJECT \
 *     -n br.com.redesurftank.havalshisuku/.broadcastReceivers.TestKeyInjectReceiver \
 *     --es token "$TOKEN"
 * </pre>
 */
public class TestKeyInjectReceiver extends BroadcastReceiver {
    private static final String TAG = "TestKeyInject";

    public static final String ACTION_INJECT =
            "br.com.redesurftank.havalshisuku.TEST_INJECT_KEY";
    public static final String ACTION_DISARM =
            "br.com.redesurftank.havalshisuku.TEST_DISARM_KEY_INJECT";

    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_KEY = "key";
    public static final String EXTRA_KEY_CODE = "keyCode";
    public static final String EXTRA_ACTION = "action";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        String token = intent.getStringExtra(EXTRA_TOKEN);
        if (!TestKeyInjectGate.matchesToken(token)) {
            Log.w(
                    TAG,
                    "Rejected "
                            + action
                            + ": gate unarmed or token mismatch (arm via "
                            + TestKeyInjectGate.TOKEN_PATH
                            + ")"
            );
            return;
        }

        if (ACTION_DISARM.equals(action)) {
            boolean ok = TestKeyInjectGate.disarm();
            Log.w(TAG, "Disarm requested ok=" + ok);
            return;
        }

        if (!ACTION_INJECT.equals(action)) {
            Log.w(TAG, "Unknown action: " + action);
            return;
        }

        ClusterKey mapped = resolveKey(intent);
        if (mapped == null) {
            Log.w(
                    TAG,
                    "Rejected inject: need --es key UP|DOWN|... or --ei keyCode 1024.. "
                            + ClusterKeyMap.allByCode().keySet()
            );
            return;
        }
        Integer keyCode = ClusterKeyMap.toKeyCode(mapped);
        if (keyCode == null) {
            Log.w(TAG, "No keyCode for " + mapped);
            return;
        }

        // BeanTech reports cluster presses as ACTION_UP only.
        int keyAction = intent.getIntExtra(EXTRA_ACTION, KeyEvent.ACTION_UP);
        boolean handled =
                ServiceManager.getInstance().injectMappedClusterKey(keyCode, keyAction);
        Log.w(
                TAG,
                "Inject key="
                        + mapped
                        + "("
                        + keyCode
                        + ") action="
                        + keyAction
                        + " handled="
                        + handled
        );
    }

    private static ClusterKey resolveKey(Intent intent) {
        if (intent.hasExtra(EXTRA_KEY_CODE)) {
            return ClusterKeyMap.fromKeyCode(intent.getIntExtra(EXTRA_KEY_CODE, -1));
        }
        String name = intent.getStringExtra(EXTRA_KEY);
        return ClusterKeyMap.fromNameOrCode(name);
    }
}
