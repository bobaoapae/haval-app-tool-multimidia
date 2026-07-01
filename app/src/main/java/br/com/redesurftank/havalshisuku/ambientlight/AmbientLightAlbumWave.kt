package br.com.redesurftank.havalshisuku.ambientlight

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import br.com.redesurftank.havalshisuku.models.BottomBarState
import br.com.redesurftank.havalshisuku.services.AlbumBackgroundColors
import br.com.redesurftank.havalshisuku.services.AlbumBackgroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

data class AlbumWaveMediaSnapshot(
    val packageName: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val artwork: Bitmap?,
    val isPlaying: Boolean
) {
    val hasAlbumContent: Boolean
        get() =
            !title.isNullOrBlank() ||
                !artist.isNullOrBlank() ||
                !album.isNullOrBlank() ||
                artwork != null

    val hasMusic: Boolean
        get() =
            hasAlbumContent ||
                !packageName.isNullOrBlank()

    val shouldAnimateAlbumWave: Boolean
        get() = hasAlbumContent && isPlaying

    val shouldHoldAlbumColor: Boolean
        get() = hasAlbumContent && !isPlaying

    val signature: String
        get() =
            listOfNotNull(
                packageName?.takeIf { it.isNotBlank() },
                title?.takeIf { it.isNotBlank() },
                artist?.takeIf { it.isNotBlank() },
                album?.takeIf { it.isNotBlank() },
                artwork?.width?.toString(),
                artwork?.height?.toString(),
                artwork?.byteCount?.toString(),
                artwork?.config?.name
            ).joinToString("|")
}

class AlbumWaveMusicController(
    private val controller: AmbientLightBleController,
    private val scope: CoroutineScope,
    private val settingsProvider: () -> AmbientLightConfig,
    private val baseColorProvider: () -> LedColor = { AmbientLightProtocol.SOFT_BLUE },
    private val mediaSnapshotProvider: suspend () -> AlbumWaveMediaSnapshot = { readBottomBarMediaSnapshot() }
) {
    private var waveJob: Job? = null
    private var lastPaletteSignature: String? = null
    private var lastSettingsSignature: String? = null
    private var lastDmxStaticKey: String? = null
    private var lastBleStaticKey: String? = null
    private var lastDmxNativeKey: String? = null
    private var lastIdleBaseKey: String? = null
    private var lastMediaPlaying: Boolean? = null
    private var currentPalette = AlbumWaveColorMapper.paletteFor(AlbumBackgroundService.fallbackColors)
    private var stepIndex = 0

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        stepIndex = 0
        lastMediaPlaying = null
        controller.updateMusicDebug(
            active = true,
            bassLevel = 0f,
            lastBeatElapsedMs = null,
            error = "Aguardando musica tocando",
            captureSource = SOURCE_ALBUM
        )
        Log.w(TAG, "album wave music effect started")
        waveJob =
            scope.launch {
                while (isActive && running) {
                    val settings = settingsProvider()
                    if (!settings.enabled || !settings.musicAnimationEnabled || settings.musicMode != AmbientLightMusicMode.ALBUM_WAVE) {
                        delay(IDLE_POLL_MS)
                        continue
                    }

                    val media = mediaSnapshotProvider()
                    if (!media.hasAlbumContent) {
                        lastMediaPlaying = null
                        restoreIdleBaseColorIfNeeded(settings, "sem album ativo")
                        controller.updateMusicDebug(
                            active = true,
                            bassLevel = 0f,
                            lastBeatElapsedMs = null,
                            error = "Aguardando album ativo",
                            captureSource = SOURCE_ALBUM
                        )
                        delay(IDLE_POLL_MS)
                        continue
                    }
                    lastIdleBaseKey = null

                    updatePaletteIfNeeded(media)
                    resetStepIfSettingsChanged(settings)
                    resetOutputCacheIfPlaybackStateChanged(media.isPlaying)
                    if (!controller.isConnected()) {
                        controller.updateMusicDebug(
                            active = true,
                            bassLevel = 0f,
                            error = "BLE nao conectado",
                            captureSource = SOURCE_ALBUM
                        )
                        delay(IDLE_POLL_MS)
                        continue
                    }

                    val animatedColor = AlbumWaveColorMapper.waveColorAt(currentPalette, stepIndex, settings.albumEffect)
                    val staticColor = AlbumWaveColorMapper.staticAlbumColor(currentPalette)
                    if (media.shouldHoldAlbumColor) {
                        val sent = sendPausedAlbumColorIfNeeded(settings, staticColor)
                        controller.updateMusicDebug(
                            active = true,
                            bassLevel = AlbumWaveColorMapper.intensity(staticColor),
                            lastBeatElapsedMs = SystemClock.elapsedRealtime(),
                            error = "Album pausado: cor fixa",
                            captureSource = SOURCE_ALBUM
                        )
                        if (sent) {
                            Log.w(
                                TAG,
                                "album wave paused hold effect=${settings.albumEffect.name} output=${settings.albumEffectOutput.name} color=${staticColor.r},${staticColor.g},${staticColor.b} title=${media.title ?: "-"}"
                            )
                        }
                        delay(STATIC_POLL_MS)
                        continue
                    }

                    val hasFrameDrivenOutput = hasFrameDrivenOutput(settings)
                    val sent = sendAlbumFrame(settings, animatedColor, staticColor)
                    if (sent) {
                        controller.updateMusicDebug(
                            active = true,
                            bassLevel = AlbumWaveColorMapper.intensity(if (hasFrameDrivenOutput) animatedColor else staticColor),
                            lastBeatElapsedMs = SystemClock.elapsedRealtime(),
                            error = null,
                            captureSource = SOURCE_ALBUM
                        )
                        if (!hasFrameDrivenOutput || stepIndex % LOG_EVERY_STEPS == 0) {
                            Log.w(
                                TAG,
                                "album wave step=$stepIndex effect=${settings.albumEffect.name} speed=${settings.albumEffectSpeed} output=${settings.albumEffectOutput.name} bleMode=${settings.albumBleMode.name} dmxMode=${settings.albumDmxMode.name} animated=${animatedColor.r},${animatedColor.g},${animatedColor.b} static=${staticColor.r},${staticColor.g},${staticColor.b} title=${media.title ?: "-"}"
                            )
                        }
                    }
                    if (hasFrameDrivenOutput) {
                        stepIndex += 1
                    }
                    delay(
                        if (hasFrameDrivenOutput) {
                            AlbumWaveColorMapper.stepDelayMs(settings.albumEffectSpeed)
                        } else {
                            STATIC_POLL_MS
                        }
                    )
                }
            }
    }

    fun stop() {
        if (!running && waveJob == null) return
        running = false
        waveJob?.cancel()
        waveJob = null
        controller.updateMusicDebug(active = false)
        Log.i(TAG, "album wave music effect stopped")
    }

    fun isRunning(): Boolean = running

    private suspend fun updatePaletteIfNeeded(media: AlbumWaveMediaSnapshot) {
        val signature = media.signature
        if (signature.isBlank() || signature == lastPaletteSignature) return
        val colors =
            withContext(Dispatchers.Default) {
                AlbumBackgroundService.extractColors(media.artwork, signature)
            }
        currentPalette = AlbumWaveColorMapper.paletteFor(colors)
        lastPaletteSignature = signature
        stepIndex = 0
        clearAlbumOutputCache()
        Log.w(TAG, "album wave palette updated signature=${signature.take(80)} colors=${currentPalette.joinToString { "${it.r},${it.g},${it.b}" }}")
    }

    private fun resetStepIfSettingsChanged(settings: AmbientLightConfig) {
        val signature =
            listOf(
                settings.albumEffect.name,
                settings.albumEffectSpeed.toString(),
                settings.albumEffectOutput.name,
                settings.albumBleMode.name,
                settings.albumDmxMode.name,
                settings.colorOrder.name,
                settings.bleColorOrder.name
            ).joinToString("|")
        if (signature == lastSettingsSignature) return
        lastSettingsSignature = signature
        stepIndex = 0
        clearAlbumOutputCache()
        Log.w(TAG, "album wave settings changed $signature")
    }

    private fun resetOutputCacheIfPlaybackStateChanged(isPlaying: Boolean) {
        if (lastMediaPlaying == isPlaying) return
        lastMediaPlaying = isPlaying
        clearAlbumOutputCache()
        Log.w(TAG, "album wave playback state changed isPlaying=$isPlaying")
    }

    private suspend fun sendAlbumFrame(
        settings: AmbientLightConfig,
        animatedColor: LedColor,
        staticColor: LedColor
    ): Boolean {
        var sentAny = false
        if (settings.albumEffectOutput.includesDmx()) {
            sentAny = sendDmxFrame(settings, animatedColor, staticColor) || sentAny
        }
        if (settings.albumEffectOutput.includesBle()) {
            sentAny = sendBleFrame(settings, animatedColor, staticColor) || sentAny
        }
        return sentAny
    }

    private suspend fun sendPausedAlbumColorIfNeeded(
        settings: AmbientLightConfig,
        staticColor: LedColor
    ): Boolean {
        var sentAny = false
        if (settings.albumEffectOutput.includesDmx()) {
            sentAny = sendDmxStaticIfNeeded(settings, staticColor) || sentAny
        }
        if (settings.albumEffectOutput.includesBle()) {
            sentAny = sendBleStaticIfNeeded(settings, staticColor) || sentAny
        }
        return sentAny
    }

    private suspend fun restoreIdleBaseColorIfNeeded(
        settings: AmbientLightConfig,
        reason: String
    ): Boolean {
        if (!controller.isConnected()) return false
        val color = baseColorProvider().coerce()
        val key =
            listOf(
                reason,
                color.r.toString(),
                color.g.toString(),
                color.b.toString(),
                settings.output.name,
                settings.colorOrder.name,
                settings.bleColorOrder.name
            ).joinToString("|")
        if (key == lastIdleBaseKey) return false
        lastIdleBaseKey = key
        clearAlbumOutputCache()
        Log.w(TAG, "album wave idle restore reason=$reason output=${settings.output.name} color=${color.r},${color.g},${color.b}")
        return controller.setRgb(
            color.r,
            color.g,
            color.b,
            settings.colorOrder,
            settings.bleColorOrder,
            settings.output
        )
    }

    private suspend fun sendDmxFrame(
        settings: AmbientLightConfig,
        animatedColor: LedColor,
        staticColor: LedColor
    ): Boolean {
        return when (settings.albumDmxMode) {
            AmbientLightAlbumOutputMode.STATIC ->
                sendDmxStaticIfNeeded(settings, staticColor)
            AmbientLightAlbumOutputMode.ANIMATED ->
                sendDmxAnimated(settings, animatedColor, staticColor)
        }
    }

    private suspend fun sendBleFrame(
        settings: AmbientLightConfig,
        animatedColor: LedColor,
        staticColor: LedColor
    ): Boolean {
        return when (settings.albumBleMode) {
            AmbientLightAlbumOutputMode.STATIC ->
                sendBleStaticIfNeeded(settings, staticColor)
            AmbientLightAlbumOutputMode.ANIMATED ->
                controller.setRgb(
                    animatedColor.r,
                    animatedColor.g,
                    animatedColor.b,
                    settings.colorOrder,
                    settings.bleColorOrder,
                    AmbientLightOutput.BLE
                )
        }
    }

    private suspend fun sendDmxAnimated(
        settings: AmbientLightConfig,
        animatedColor: LedColor,
        staticColor: LedColor
    ): Boolean {
        val modeId = settings.albumEffect.ledLampModeId
        if (modeId != null) {
            val key = albumOutputKey(settings, staticColor, "dmx-native-${settings.albumEffect.name}-${settings.albumEffectSpeed}")
            if (key == lastDmxNativeKey) return false
            lastDmxNativeKey = key
            val payload =
                AmbientLightProtocol.setDmxLedLampCustomEffectPayload(
                    r = staticColor.r,
                    g = staticColor.g,
                    b = staticColor.b,
                    modeId = modeId,
                    speed = settings.albumEffectSpeed,
                    colorOrder = settings.colorOrder
                )
            return controller.sendHex(AmbientLightProtocol.bytesToHex(payload))
        }
        return controller.setRgb(
            animatedColor.r,
            animatedColor.g,
            animatedColor.b,
            settings.colorOrder,
            settings.bleColorOrder,
            AmbientLightOutput.DMX
        )
    }

    private suspend fun sendDmxStaticIfNeeded(
        settings: AmbientLightConfig,
        staticColor: LedColor
    ): Boolean {
        val key = albumOutputKey(settings, staticColor, "dmx-static")
        if (key == lastDmxStaticKey) return false
        lastDmxStaticKey = key
        return controller.setRgb(
            staticColor.r,
            staticColor.g,
            staticColor.b,
            settings.colorOrder,
            settings.bleColorOrder,
            AmbientLightOutput.DMX
        )
    }

    private suspend fun sendBleStaticIfNeeded(
        settings: AmbientLightConfig,
        staticColor: LedColor
    ): Boolean {
        val key = albumOutputKey(settings, staticColor, "ble-static")
        if (key == lastBleStaticKey) return false
        lastBleStaticKey = key
        return controller.setRgb(
            staticColor.r,
            staticColor.g,
            staticColor.b,
            settings.colorOrder,
            settings.bleColorOrder,
            AmbientLightOutput.BLE
        )
    }

    private fun hasFrameDrivenOutput(settings: AmbientLightConfig): Boolean =
        (settings.albumEffectOutput.includesBle() && settings.albumBleMode == AmbientLightAlbumOutputMode.ANIMATED) ||
            (
                settings.albumEffectOutput.includesDmx() &&
                    settings.albumDmxMode == AmbientLightAlbumOutputMode.ANIMATED &&
                    settings.albumEffect.ledLampModeId == null
            )

    private fun clearAlbumOutputCache() {
        lastDmxStaticKey = null
        lastBleStaticKey = null
        lastDmxNativeKey = null
    }

    private fun albumOutputKey(
        settings: AmbientLightConfig,
        color: LedColor,
        mode: String
    ): String =
        listOf(
            lastPaletteSignature.orEmpty(),
            mode,
            color.r.toString(),
            color.g.toString(),
            color.b.toString(),
            settings.albumEffectOutput.name,
            settings.albumBleMode.name,
            settings.albumDmxMode.name,
            settings.colorOrder.name,
            settings.bleColorOrder.name
        ).joinToString("|")

    companion object {
        private const val TAG = "AmbientLight"
        private const val SOURCE_ALBUM = "Album"
        private const val IDLE_POLL_MS = 1_000L
        private const val STATIC_POLL_MS = 1_000L
        private const val LOG_EVERY_STEPS = 12

        private suspend fun readBottomBarMediaSnapshot(): AlbumWaveMediaSnapshot =
            withContext(Dispatchers.Main.immediate) {
                AlbumWaveMediaSnapshot(
                    packageName = BottomBarState.mediaPackageName,
                    title = BottomBarState.mediaTitle,
                    artist = BottomBarState.mediaArtist,
                    album = BottomBarState.mediaAlbum,
                    artwork = BottomBarState.mediaArtwork,
                    isPlaying = BottomBarState.mediaIsPlaying
                )
            }
    }
}

object AlbumWaveColorMapper {
    private val FALLBACK_PALETTE =
        listOf(
            LedColor(66, 227, 255),
            LedColor(118, 96, 255),
            LedColor(255, 110, 64),
            LedColor(32, 180, 120)
        )

    fun paletteFor(colors: AlbumBackgroundColors): List<LedColor> {
        val primary = fromColorInt(colors.primary)
        val secondary = fromColorInt(colors.secondary)
        val accent = fromColorInt(colors.accent)
        val dark = fromColorInt(colors.dark)
        val palette =
            listOf(
                primary,
                mix(primary, accent, 0.45f),
                accent,
                mix(accent, secondary, 0.50f),
                secondary,
                mix(secondary, dark, 0.30f),
                mix(dark, primary, 0.40f)
            )
                .map { enhanceForLed(it) }
                .distinctBy { "${it.r / 8}:${it.g / 8}:${it.b / 8}" }
        return if (palette.size >= MIN_PALETTE_SIZE) palette else FALLBACK_PALETTE
    }

    fun waveColorAt(
        palette: List<LedColor>,
        step: Int,
        effect: AmbientLightAlbumEffect = AmbientLightAlbumEffect.DEFAULT
    ): LedColor {
        val safePalette = if (palette.isEmpty()) FALLBACK_PALETTE else palette
        return when (effect.pattern) {
            AmbientLightAlbumEffectPattern.DIGITAL_WAVE ->
                digitalWaveColorAt(safePalette, step, effect.direction.multiplier)
            AmbientLightAlbumEffectPattern.PULSE ->
                pulseColorAt(safePalette, step)
            AmbientLightAlbumEffectPattern.SIX_COLOR ->
                ledLampSixColorAt(safePalette, step, effect)
            AmbientLightAlbumEffectPattern.RUN ->
                ledLampRunColorAt(safePalette, step, effect)
            AmbientLightAlbumEffectPattern.FLOW ->
                digitalWaveColorAt(safePalette, step, effect.direction.multiplier, FLOW_PHASE_STEP)
        }
    }

    fun stepDelayMs(speed: Int): Long {
        val safeSpeed = speed.coerceIn(1, 100)
        val ratio = (safeSpeed - 1) / 99f
        return (MAX_STEP_DELAY_MS - ((MAX_STEP_DELAY_MS - MIN_STEP_DELAY_MS) * ratio)).roundToInt().toLong()
    }

    fun staticAlbumColor(palette: List<LedColor>): LedColor {
        val safePalette = if (palette.isEmpty()) FALLBACK_PALETTE else palette
        return ensureVisible(safePalette.first(), MIN_WAVE_VISIBLE_CHANNEL).coerce()
    }

    private fun digitalWaveColorAt(
        safePalette: List<LedColor>,
        step: Int,
        directionMultiplier: Float,
        phaseStep: Float = WAVE_PHASE_STEP
    ): LedColor {
        if (safePalette.size == 1) {
            return applyWaveLight(safePalette.first(), step)
        }

        val phase = step.toFloat() * phaseStep * directionMultiplier
        val trail = samplePalette(safePalette, phase - WAVE_TRAIL_OFFSET)
        val body = samplePalette(safePalette, phase)
        val crest = samplePalette(safePalette, phase + WAVE_CREST_OFFSET)
        val crestAmount = smoothStep(sine01(step.toFloat() * WAVE_CREST_SPEED))
        val crestBlend = MIN_CREST_BLEND + ((MAX_CREST_BLEND - MIN_CREST_BLEND) * crestAmount)
        val brightness = MIN_WAVE_BRIGHTNESS + ((MAX_WAVE_BRIGHTNESS - MIN_WAVE_BRIGHTNESS) * crestAmount)
        val bodyWithTrail = mix(scaleColor(trail, TRAIL_BRIGHTNESS), body, BODY_BLEND)
        return ensureVisible(
            scaleColor(
                mix(bodyWithTrail, crest, crestBlend),
                brightness
            ),
            MIN_WAVE_VISIBLE_CHANNEL
        ).coerce()
    }

    private fun pulseColorAt(palette: List<LedColor>, step: Int): LedColor {
        val base = palette.firstOrNull() ?: FALLBACK_PALETTE.first()
        val accent = if (palette.size > 1) samplePalette(palette, step.toFloat() * PULSE_ACCENT_PHASE_STEP + 1f) else base
        val pulseAmount = smoothStep(sine01(step.toFloat() * PULSE_SPEED))
        val color = mix(base, accent, MIN_PULSE_ACCENT_BLEND + ((MAX_PULSE_ACCENT_BLEND - MIN_PULSE_ACCENT_BLEND) * pulseAmount))
        val brightness = MIN_PULSE_BRIGHTNESS + ((MAX_PULSE_BRIGHTNESS - MIN_PULSE_BRIGHTNESS) * pulseAmount)
        return ensureVisible(scaleColor(color, brightness), MIN_WAVE_VISIBLE_CHANNEL).coerce()
    }

    private fun ledLampSixColorAt(
        palette: List<LedColor>,
        step: Int,
        effect: AmbientLightAlbumEffect
    ): LedColor {
        val sequence = sixColorSequence(palette, effect.anchor)
        val phase = step.toFloat() * SIX_COLOR_PHASE_STEP * effect.direction.multiplier
        val wrapped = wrap(phase, sequence.size)
        val index = floor(wrapped).toInt()
        val nextIndex = (index + 1) % sequence.size
        val ratio = smoothStep(wrapped - index)
        val shimmer = MIN_SIX_COLOR_BRIGHTNESS + ((MAX_SIX_COLOR_BRIGHTNESS - MIN_SIX_COLOR_BRIGHTNESS) * smoothStep(sine01(step.toFloat() * SIX_COLOR_SHIMMER_SPEED)))
        return ensureVisible(
            scaleColor(
                mix(sequence[index], sequence[nextIndex], ratio * SIX_COLOR_GLIDE),
                shimmer
            ),
            MIN_WAVE_VISIBLE_CHANNEL
        ).coerce()
    }

    private fun ledLampRunColorAt(
        palette: List<LedColor>,
        step: Int,
        effect: AmbientLightAlbumEffect
    ): LedColor {
        val sequence = sixColorSequence(palette, effect.anchor)
        val phase = step.toFloat() * RUN_PHASE_STEP * effect.direction.multiplier
        val wrapped = wrap(phase, sequence.size)
        val index = floor(wrapped).toInt()
        val nextIndex = (index + 1) % sequence.size
        val tailIndex = (index - 1 + sequence.size) % sequence.size
        val local = smoothStep(wrapped - index)
        val runner = mix(sequence[index], sequence[nextIndex], local)
        val tail = scaleColor(sequence[tailIndex], RUN_TAIL_BRIGHTNESS)
        val blink = MIN_RUN_BRIGHTNESS + ((MAX_RUN_BRIGHTNESS - MIN_RUN_BRIGHTNESS) * smoothStep(sine01(step.toFloat() * RUN_SHIMMER_SPEED)))
        return ensureVisible(scaleColor(mix(tail, runner, RUN_HEAD_BLEND), blink), MIN_WAVE_VISIBLE_CHANNEL).coerce()
    }

    fun intensity(color: LedColor): Float {
        val safe = color.coerce()
        return (max(safe.r, max(safe.g, safe.b)) / 255f).coerceIn(0f, 1f)
    }

    private fun fromColorInt(color: Int): LedColor =
        LedColor(
            r = (color shr 16) and 0xFF,
            g = (color shr 8) and 0xFF,
            b = color and 0xFF
        )

    private fun enhanceForLed(color: LedColor): LedColor =
        boostSaturation(normalizeForLed(color), SATURATION_BOOST)

    private fun normalizeForLed(color: LedColor): LedColor {
        val safe = color.coerce()
        val maxChannel = max(safe.r, max(safe.g, safe.b))
        if (maxChannel <= 0) return FALLBACK_PALETTE.first()
        val scale = if (maxChannel < MIN_VISIBLE_CHANNEL) MIN_VISIBLE_CHANNEL.toFloat() / maxChannel.toFloat() else 1f
        return scaleColor(safe, scale)
    }

    private fun samplePalette(palette: List<LedColor>, position: Float): LedColor {
        val wrapped = wrap(position, palette.size)
        val fromIndex = floor(wrapped).toInt()
        val toIndex = (fromIndex + 1) % palette.size
        val ratio = smoothStep(wrapped - fromIndex)
        return mix(palette[fromIndex], palette[toIndex], ratio)
    }

    private fun wrap(position: Float, size: Int): Float {
        val mod = position % size.toFloat()
        return if (mod < 0f) mod + size.toFloat() else mod
    }

    private fun sixColorSequence(palette: List<LedColor>, anchor: AmbientLightAlbumEffectAnchor): List<LedColor> {
        val rotated = rotateForAnchor(palette, anchor)
        return (0 until SIX_COLOR_COUNT).map { index ->
            samplePalette(rotated, (index * rotated.size.toFloat()) / SIX_COLOR_COUNT.toFloat())
        }
    }

    private fun rotateForAnchor(palette: List<LedColor>, anchor: AmbientLightAlbumEffectAnchor): List<LedColor> {
        if (anchor == AmbientLightAlbumEffectAnchor.ALBUM || palette.size <= 1) return palette
        val anchorIndex =
            palette.indices.maxByOrNull { index ->
                anchorScore(palette[index], anchor)
            } ?: 0
        return palette.drop(anchorIndex) + palette.take(anchorIndex)
    }

    private fun anchorScore(color: LedColor, anchor: AmbientLightAlbumEffectAnchor): Int {
        val safe = color.coerce()
        return when (anchor) {
            AmbientLightAlbumEffectAnchor.ALBUM -> max(safe.r, max(safe.g, safe.b))
            AmbientLightAlbumEffectAnchor.GREEN -> (safe.g * 2) - safe.r - safe.b
            AmbientLightAlbumEffectAnchor.BLUE -> (safe.b * 2) - safe.r - safe.g
            AmbientLightAlbumEffectAnchor.CYAN -> safe.g + safe.b - safe.r - abs(safe.g - safe.b)
        }
    }

    private fun applyWaveLight(color: LedColor, step: Int): LedColor {
        val crestAmount = smoothStep(sine01(step.toFloat() * WAVE_CREST_SPEED))
        val brightness = MIN_WAVE_BRIGHTNESS + ((MAX_WAVE_BRIGHTNESS - MIN_WAVE_BRIGHTNESS) * crestAmount)
        return ensureVisible(scaleColor(color, brightness), MIN_WAVE_VISIBLE_CHANNEL).coerce()
    }

    private fun ensureVisible(color: LedColor, minChannel: Int): LedColor {
        val safe = color.coerce()
        val maxChannel = max(safe.r, max(safe.g, safe.b))
        if (maxChannel <= 0) return FALLBACK_PALETTE.first()
        if (maxChannel >= minChannel) return safe
        return scaleColor(safe, minChannel.toFloat() / maxChannel.toFloat())
    }

    private fun scaleColor(color: LedColor, scale: Float): LedColor =
        LedColor(
            r = (color.r * scale).roundToInt().coerceIn(0, 255),
            g = (color.g * scale).roundToInt().coerceIn(0, 255),
            b = (color.b * scale).roundToInt().coerceIn(0, 255)
        )

    private fun boostSaturation(color: LedColor, amount: Float): LedColor {
        val safe = color.coerce()
        val luma = (safe.r * 0.299f) + (safe.g * 0.587f) + (safe.b * 0.114f)
        return LedColor(
            r = (luma + ((safe.r - luma) * amount)).roundToInt().coerceIn(0, 255),
            g = (luma + ((safe.g - luma) * amount)).roundToInt().coerceIn(0, 255),
            b = (luma + ((safe.b - luma) * amount)).roundToInt().coerceIn(0, 255)
        )
    }

    private fun mix(from: LedColor, to: LedColor, ratio: Float): LedColor {
        val safeRatio = ratio.coerceIn(0f, 1f)
        return LedColor(
            r = (from.r + ((to.r - from.r) * safeRatio)).roundToInt(),
            g = (from.g + ((to.g - from.g) * safeRatio)).roundToInt(),
            b = (from.b + ((to.b - from.b) * safeRatio)).roundToInt()
        ).coerce()
    }

    private fun sine01(phase: Float): Float =
        ((Math.sin(phase.toDouble()) + 1.0) / 2.0).toFloat().coerceIn(0f, 1f)

    private fun smoothStep(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return x * x * (3f - (2f * x))
    }

    private const val MIN_VISIBLE_CHANNEL = 112
    private const val MIN_WAVE_VISIBLE_CHANNEL = 72
    private const val MIN_PALETTE_SIZE = 3
    private const val MIN_STEP_DELAY_MS = 110L
    private const val MAX_STEP_DELAY_MS = 520L
    private const val WAVE_PHASE_STEP = 0.38f
    private const val FLOW_PHASE_STEP = 0.30f
    private const val WAVE_TRAIL_OFFSET = 0.95f
    private const val WAVE_CREST_OFFSET = 0.28f
    private const val WAVE_CREST_SPEED = 0.72f
    private const val TRAIL_BRIGHTNESS = 0.55f
    private const val BODY_BLEND = 0.68f
    private const val MIN_CREST_BLEND = 0.18f
    private const val MAX_CREST_BLEND = 0.46f
    private const val MIN_WAVE_BRIGHTNESS = 0.62f
    private const val MAX_WAVE_BRIGHTNESS = 1.10f
    private const val PULSE_ACCENT_PHASE_STEP = 0.18f
    private const val PULSE_SPEED = 0.82f
    private const val MIN_PULSE_ACCENT_BLEND = 0.18f
    private const val MAX_PULSE_ACCENT_BLEND = 0.62f
    private const val MIN_PULSE_BRIGHTNESS = 0.44f
    private const val MAX_PULSE_BRIGHTNESS = 1.14f
    private const val SIX_COLOR_COUNT = 6
    private const val SIX_COLOR_PHASE_STEP = 0.72f
    private const val SIX_COLOR_GLIDE = 0.74f
    private const val SIX_COLOR_SHIMMER_SPEED = 0.92f
    private const val MIN_SIX_COLOR_BRIGHTNESS = 0.66f
    private const val MAX_SIX_COLOR_BRIGHTNESS = 1.12f
    private const val RUN_PHASE_STEP = 1.0f
    private const val RUN_TAIL_BRIGHTNESS = 0.28f
    private const val RUN_HEAD_BLEND = 0.82f
    private const val RUN_SHIMMER_SPEED = 1.18f
    private const val MIN_RUN_BRIGHTNESS = 0.72f
    private const val MAX_RUN_BRIGHTNESS = 1.16f
    private const val SATURATION_BOOST = 1.18f
}
