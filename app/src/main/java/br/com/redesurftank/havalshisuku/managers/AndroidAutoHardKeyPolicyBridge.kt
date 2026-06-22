package br.com.redesurftank.havalshisuku.managers

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object AndroidAutoHardKeyPolicyBridge {
    private const val TAG = "AAHardKeyPolicy"
    private const val ANDROID_AUTO_PACKAGE = "com.ts.androidauto.app"
    private const val ANDROID_AUTO_SERVICE =
        "com.ts.androidauto.app.AndroidAutoRemoteUiService"
    private const val ACTION_MEDIA_COMMAND =
        "br.com.redesurftank.havalshisuku.AA_MEDIA_COMMAND"
    private const val ACTION_REMOTE_UI_SERVICE =
        "com.ts.androidauto.action.AndroidAutoRemoteUiService"
    private const val EXTRA_KEY_CODE = "keyCode"
    private const val EXTRA_TARGET_DISPLAY = "targetDisplay"
    private const val EXTRA_TOKEN = "token"
    private const val TOKEN = "impulse-aa-media-v1"
    private const val RESULT_DISPATCHED = 1
    private const val ACK_TIMEOUT_MS = 900L

    suspend fun sendMediaKeySequence(
        context: Context,
        keyCode: Int,
        targetDisplayId: Int,
        reason: String
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (!isSupportedHardKey(keyCode)) {
                Log.w(TAG, "[$reason] Unsupported patched Android Auto hardkey keyCode=$keyCode")
                return@withContext false
            }
            if (targetDisplayId != 0 && targetDisplayId != 3) {
                Log.w(
                    TAG,
                    "[$reason] Unsupported patched Android Auto hardkey display=$targetDisplayId"
                )
                return@withContext false
            }

            val appContext = context.applicationContext
            val serviceIntent =
                Intent(ACTION_REMOTE_UI_SERVICE).apply {
                    component = ComponentName(ANDROID_AUTO_PACKAGE, ANDROID_AUTO_SERVICE)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
            val serviceComponent =
                try {
                    appContext.startService(serviceIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "[$reason] Failed to start Android Auto service before hardkey", e)
                    null
                }
            if (serviceComponent == null) {
                Log.w(TAG, "[$reason] Android Auto service unavailable before hardkey")
                return@withContext false
            }

            delay(120)

            val ack = CompletableDeferred<Boolean>()
            val mediaIntent =
                Intent(ACTION_MEDIA_COMMAND).apply {
                    setPackage(ANDROID_AUTO_PACKAGE)
                    putExtra(EXTRA_TOKEN, TOKEN)
                    putExtra(EXTRA_KEY_CODE, keyCode)
                    putExtra(EXTRA_TARGET_DISPLAY, targetDisplayId)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND)
                }
            val resultReceiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        val dispatched = resultCode == RESULT_DISPATCHED
                        Log.w(
                            TAG,
                            "[$reason] Patched Android Auto hardkey ack resultCode=$resultCode " +
                                "dispatched=$dispatched keyCode=$keyCode " +
                                "targetDisplay=$targetDisplayId"
                        )
                        ack.complete(dispatched)
                    }
                }

            try {
                appContext.sendOrderedBroadcast(
                    mediaIntent,
                    null,
                    resultReceiver,
                    Handler(Looper.getMainLooper()),
                    0,
                    null,
                    null
                )
            } catch (e: Exception) {
                Log.e(TAG, "[$reason] Failed to send patched Android Auto hardkey broadcast", e)
                return@withContext false
            }

            val dispatched = withTimeoutOrNull(ACK_TIMEOUT_MS) { ack.await() } ?: false
            Log.w(
                TAG,
                "[$reason] Patched Android Auto hardkey broadcast keyCode=$keyCode " +
                    "targetDisplay=$targetDisplayId dispatched=$dispatched"
            )
            dispatched
        }

    private fun isSupportedHardKey(keyCode: Int): Boolean {
        return keyCode == 1002 || keyCode == 1003 || keyCode == 1004
    }
}
