package br.com.redesurftank.havalshisuku.services

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class AlbumBackgroundColors(
        val primary: Int,
        val secondary: Int,
        val accent: Int,
        val dark: Int
)

object AlbumBackgroundService {
    val fallbackColors =
            AlbumBackgroundColors(
                    primary = Color.rgb(31, 51, 58),
                    secondary = Color.rgb(65, 53, 31),
                    accent = Color.rgb(102, 227, 255),
                    dark = Color.rgb(5, 7, 10)
            )

    private const val MAX_CACHE_SIZE = 24
    private const val MAX_SAMPLE_SIDE = 96
    private const val MIN_ALPHA = 180

    private val cache =
            object : LinkedHashMap<String, AlbumBackgroundColors>(MAX_CACHE_SIZE, 0.75f, true) {
                override fun removeEldestEntry(
                        eldest: MutableMap.MutableEntry<String, AlbumBackgroundColors>?
                ): Boolean = size > MAX_CACHE_SIZE
            }

    fun extractColors(bitmap: Bitmap?, keyHint: String?): AlbumBackgroundColors {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return fallbackColors
        val cacheKey = buildCacheKey(bitmap, keyHint)
        synchronized(cache) { cache[cacheKey]?.let { return it } }

        val colors =
                runCatching { extractColorsInternal(bitmap) }
                        .getOrDefault(fallbackColors)
        synchronized(cache) { cache[cacheKey] = colors }
        return colors
    }

    private fun buildCacheKey(bitmap: Bitmap, keyHint: String?): String {
        return listOfNotNull(
                        keyHint?.takeIf { it.isNotBlank() },
                        bitmap.width.toString(),
                        bitmap.height.toString(),
                        bitmap.generationId.toString()
                )
                .joinToString("|")
    }

    private fun extractColorsInternal(bitmap: Bitmap): AlbumBackgroundColors {
        val step = calculateSampleStep(bitmap)
        var averageR = 0L
        var averageG = 0L
        var averageB = 0L
        var count = 0

        var vibrant: Candidate? = null
        var darkVibrant: Candidate? = null
        var muted: Candidate? = null
        var darkMuted: Candidate? = null

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) >= MIN_ALPHA) {
                    val r = Color.red(color)
                    val g = Color.green(color)
                    val b = Color.blue(color)
                    val hsl = rgbToHsl(r, g, b)
                    val saturation = hsl[1]
                    val luminance = hsl[2]
                    if (luminance in 0.06f..0.94f) {
                        averageR += r
                        averageG += g
                        averageB += b
                        count++
                    }

                    val vibrantScore = saturation * (1f - abs(luminance - 0.52f))
                    if (saturation >= 0.44f && luminance in 0.24f..0.78f) {
                        vibrant = bestCandidate(vibrant, color, vibrantScore)
                    }
                    if (saturation >= 0.42f && luminance in 0.08f..0.48f) {
                        darkVibrant = bestCandidate(darkVibrant, color, vibrantScore + 0.16f)
                    }
                    if (saturation in 0.12f..0.5f && luminance in 0.26f..0.74f) {
                        muted =
                                bestCandidate(
                                        muted,
                                        color,
                                        (1f - saturation) * (1f - abs(luminance - 0.5f))
                                )
                    }
                    if (saturation in 0.08f..0.48f && luminance in 0.08f..0.46f) {
                        darkMuted =
                                bestCandidate(
                                        darkMuted,
                                        color,
                                        (1f - saturation) * (0.62f - luminance).coerceAtLeast(0f)
                                )
                    }
                }
                x += step
            }
            y += step
        }

        if (count == 0) return fallbackColors

        val average =
                Color.rgb(
                        (averageR / count).toInt().coerceIn(0, 255),
                        (averageG / count).toInt().coerceIn(0, 255),
                        (averageB / count).toInt().coerceIn(0, 255)
                )
        val primary = readableAccent(vibrant?.color ?: average)
        val secondary = readableAccent(darkVibrant?.color ?: muted?.color ?: average)
        val accent = readableAccent(muted?.color ?: vibrant?.color ?: average)
        val dark = darken(darkMuted?.color ?: darkVibrant?.color ?: average, 0.34f)

        return AlbumBackgroundColors(
                primary = primary,
                secondary = secondary,
                accent = accent,
                dark = dark
        )
    }

    private fun calculateSampleStep(bitmap: Bitmap): Int {
        val largest = max(bitmap.width, bitmap.height)
        return max(1, (largest.toFloat() / MAX_SAMPLE_SIDE.toFloat()).roundToInt())
    }

    private fun bestCandidate(current: Candidate?, color: Int, score: Float): Candidate {
        return if (current == null || score > current.score) Candidate(color, score) else current
    }

    private fun readableAccent(color: Int): Int {
        val hsl = rgbToHsl(Color.red(color), Color.green(color), Color.blue(color))
        hsl[1] = hsl[1].coerceIn(0.28f, 0.82f)
        hsl[2] = hsl[2].coerceIn(0.26f, 0.66f)
        return hslToColor(hsl)
    }

    private fun darken(color: Int, factor: Float): Int {
        val hsl = rgbToHsl(Color.red(color), Color.green(color), Color.blue(color))
        hsl[1] = hsl[1].coerceAtLeast(0.16f)
        hsl[2] = (hsl[2] * factor).coerceIn(0.04f, 0.2f)
        return hslToColor(hsl)
    }

    private fun rgbToHsl(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max = max(rf, max(gf, bf))
        val min = min(rf, min(gf, bf))
        val delta = max - min
        val luminance = (max + min) / 2f
        val saturation =
                if (delta == 0f) {
                    0f
                } else {
                    delta / (1f - abs((2f * luminance) - 1f))
                }
        val hue =
                when {
                    delta == 0f -> 0f
                    max == rf -> ((gf - bf) / delta + if (gf < bf) 6f else 0f) / 6f
                    max == gf -> ((bf - rf) / delta + 2f) / 6f
                    else -> ((rf - gf) / delta + 4f) / 6f
                }
        return floatArrayOf(hue, saturation.coerceIn(0f, 1f), luminance.coerceIn(0f, 1f))
    }

    private fun hslToColor(hsl: FloatArray): Int {
        val hue = hsl[0]
        val saturation = hsl[1]
        val luminance = hsl[2]
        if (saturation == 0f) {
            val gray = (luminance * 255f).roundToInt().coerceIn(0, 255)
            return Color.rgb(gray, gray, gray)
        }

        val q =
                if (luminance < 0.5f) {
                    luminance * (1f + saturation)
                } else {
                    luminance + saturation - luminance * saturation
                }
        val p = 2f * luminance - q
        val r = hueToRgb(p, q, hue + 1f / 3f)
        val g = hueToRgb(p, q, hue)
        val b = hueToRgb(p, q, hue - 1f / 3f)
        return Color.rgb(
                (r * 255f).roundToInt().coerceIn(0, 255),
                (g * 255f).roundToInt().coerceIn(0, 255),
                (b * 255f).roundToInt().coerceIn(0, 255)
        )
    }

    private fun hueToRgb(p: Float, q: Float, rawT: Float): Float {
        var t = rawT
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }

    private data class Candidate(val color: Int, val score: Float)
}
