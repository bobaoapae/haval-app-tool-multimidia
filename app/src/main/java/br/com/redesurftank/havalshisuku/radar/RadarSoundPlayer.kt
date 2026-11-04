package br.com.redesurftank.havalshisuku.radar

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays the radar alert sounds. They are generated here (short sine patterns), so there are
 * no audio assets or licences; the car has no text-to-speech engine (checked 2026-10-17:
 * no TTS_SERVICE), so there is no voice option.
 *
 * Played as navigation guidance with a transient "may duck" focus request: music dips under
 * the beep instead of stopping. Whether this car routes that usage to the speakers at a sane
 * level is a confirmar on the car (Testar button in the settings).
 */
object RadarSoundPlayer {
    private const val TAG = "Radar"
    private const val RATE = 22_050

    private val thread = HandlerThread("RadarSound").apply { start() }
    private val handler = Handler(thread.looper)
    private val cache = HashMap<RadarSound, ShortArray>()

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Plays [sound] at [volume] (0..100). Any thread; a new sound cuts a playing one short. */
    fun play(context: Context, sound: RadarSound, volume: Int) {
        if (sound == RadarSound.NONE || volume <= 0) return
        val app = context.applicationContext
        handler.post { playNow(app, sound, volume) }
    }

    private var current: AudioTrack? = null
    private var focus: AudioFocusRequest? = null

    private fun playNow(context: Context, sound: RadarSound, volume: Int) {
        stopNow(context)
        val pcm = cache.getOrPut(sound) { synth(sound) }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        am.requestAudioFocus(request)
        focus = request
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setVolume((volume / 100f).coerceIn(0f, 1f))
            track.play()
            current = track
            val durationMs = pcm.size * 1000L / RATE
            handler.postDelayed({ if (current === track) stopNow(context) }, durationMs + 150)
        } catch (t: Throwable) {
            Log.w(TAG, "sound failed: ${t.message}")
            stopNow(context)
        }
    }

    private fun stopNow(context: Context) {
        current?.let {
            runCatching { it.stop() }
            it.release()
        }
        current = null
        focus?.let { (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it) }
        focus = null
    }

    /** A tone of [hz] for [ms], with [decay] (0 = flat) and 5 ms fades so it never clicks. */
    private class Tone(val hz: Double, val ms: Int, val decay: Double = 0.0, val gapAfterMs: Int = 0)

    private fun pattern(sound: RadarSound): List<Tone> = when (sound) {
        RadarSound.NONE -> emptyList()
        RadarSound.BEEP -> listOf(Tone(1_000.0, 200))
        RadarSound.DOUBLE_BEEP -> listOf(Tone(1_200.0, 120, gapAfterMs = 90), Tone(1_200.0, 120))
        // Ding-dong: a bright note then a lower one, both ringing out.
        RadarSound.CHIME -> listOf(Tone(880.0, 320, decay = 6.0), Tone(660.0, 520, decay = 4.0))
        RadarSound.ALARM -> List(4) { i -> Tone(1_450.0, 85, gapAfterMs = if (i < 3) 55 else 0) }
    }

    private fun synth(sound: RadarSound): ShortArray {
        val tones = pattern(sound)
        val total = tones.sumOf { (it.ms + it.gapAfterMs) * RATE / 1000 }
        val out = ShortArray(total)
        var pos = 0
        val fade = RATE * 5 / 1000
        for (tone in tones) {
            val n = tone.ms * RATE / 1000
            for (i in 0 until n) {
                val t = i.toDouble() / RATE
                val env = min(1.0, min(i.toDouble() / fade, (n - i).toDouble() / fade)) * exp(-tone.decay * t)
                // Fundamental plus a soft octave: carries better over road noise than a pure sine.
                val s = sin(2 * PI * tone.hz * t) * 0.8 + sin(4 * PI * tone.hz * t) * 0.2
                out[pos + i] = (s * env * 0.85 * Short.MAX_VALUE).toInt().toShort()
            }
            pos += n + tone.gapAfterMs * RATE / 1000
        }
        return out
    }
}
