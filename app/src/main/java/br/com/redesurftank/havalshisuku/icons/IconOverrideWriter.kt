package br.com.redesurftank.havalshisuku.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.os.Environment
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Publishes Impulse's per-app icon / label overrides to the shared registry at
 * `/sdcard/AppIconOverrides/`, so any other app on the MMI can pick them up
 * without an app-to-app request.
 *
 * See ICON_OVERRIDES.md (in the haval-h6-3d repo) for the format this writes
 * and the reasoning behind it. The call site and icon rasterisation are left
 * to the caller (see [publish]) — see `DisplayAppLauncher.publishIconOverrides`
 * for how Impulse drives this.
 */
object IconOverrideWriter {

    private const val TAG = "IconOverrideWriter"

    private const val DIR_NAME = "AppIconOverrides"
    private const val ICONS_SUBDIR = "icons"
    private const val MANIFEST_NAME = "manifest.json"
    private const val FORMAT_VERSION = 1

    /** Consumers downscale from this; see ICON_OVERRIDES.md. */
    private const val ICON_PX = 192

    /**
     * One published override. Both [label] and [iconSlug] are independently
     * optional — an app can be renamed without being re-iconed — but an entry
     * with neither is meaningless and is skipped.
     *
     * Map this from `displayAppConfigs`, dropping entries whose `customName` is
     * empty *and* whose `substituteIcon` is unset. Note that `displayAppConfigs`
     * is keyed by (packageName, displayId) while this registry is keyed by
     * package alone: collapse duplicates before calling, preferring displayId 0.
     */
    data class Override(
        val packageName: String,
        val label: String? = null,
        val iconSlug: String? = null,
        val iconColor: Int? = null,
    )

    /**
     * Rewrite the registry if anything has changed, otherwise do nothing.
     *
     * Pass the full current override set every time — this replaces the
     * registry rather than merging into it, so clearing every override in
     * Impulse's UI must publish an empty list, not skip the call, or consumers
     * keep showing the old icons forever.
     *
     * Blocking IO. Call from a background dispatcher.
     *
     * @param render rasterises one override's icon, or returns null for
     *   "label-only override". Only called for overrides that survive the
     *   unchanged-registry check, so a boot-time publish never rasterises
     *   anything.
     */
    @Synchronized
    fun publish(
        context: Context,
        overrides: List<Override>,
        render: (Override) -> Bitmap?,
    ) {
        try {
            if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
                Log.w(TAG, "external storage not mounted, skipping publish")
                return
            }

            val dir = File(Environment.getExternalStorageDirectory(), DIR_NAME)
            val iconsDir = File(dir, ICONS_SUBDIR)
            val manifestFile = File(dir, MANIFEST_NAME)

            val fingerprint = fingerprint(context, overrides)
            if (isCurrent(manifestFile, iconsDir, fingerprint)) return

            // mkdirs() builds both levels, so a first run on a car that has never
            // had a registry needs no separate setup step.
            if (!iconsDir.isDirectory && !iconsDir.mkdirs()) {
                Log.w(TAG, "cannot create $iconsDir (WRITE_EXTERNAL_STORAGE granted?)")
                return
            }

            val apps = JSONArray()
            val published = HashSet<String>()

            for (override in overrides) {
                if (override.packageName.isEmpty()) continue
                val hasLabel = !override.label.isNullOrEmpty()

                // Step 1: the PNG lands before the manifest that names it, so a
                // consumer reading mid-publish never sees a dangling reference.
                var iconRelative: String? = null
                val bitmap = try {
                    render(override)
                } catch (t: Throwable) {
                    Log.w(TAG, "cannot render icon for ${override.packageName}", t)
                    null
                }
                if (bitmap != null) {
                    val name = "${override.packageName}.png"
                    if (writePng(File(iconsDir, name), bitmap)) {
                        iconRelative = "$ICONS_SUBDIR/$name"
                        published.add(name)
                    }
                }

                if (!hasLabel && iconRelative == null) continue

                apps.put(JSONObject().apply {
                    put("packageName", override.packageName)
                    // Omit rather than write "" — an empty string would read as
                    // "rename this app to nothing".
                    if (hasLabel) put("label", override.label)
                    if (iconRelative != null) put("icon", iconRelative)
                    override.iconSlug?.takeIf { it.isNotEmpty() }?.let { put("iconSlug", it) }
                    override.iconColor?.let { put("iconColor", String.format("#%06X", it and 0xFFFFFF)) }
                })
            }

            val manifest = JSONObject().apply {
                put("version", FORMAT_VERSION)
                put("generatedAt", System.currentTimeMillis())
                put("writer", context.packageName)
                put("sourceFingerprint", fingerprint)
                put("apps", apps)
            }

            // Step 2: publish the manifest by rename, which is atomic within the
            // filesystem. Writing it in place would let a consumer read a
            // half-written file.
            val tmp = File(dir, "$MANIFEST_NAME.tmp")
            tmp.writeText(manifest.toString())
            if (!tmp.renameTo(manifestFile)) {
                // renameTo does not replace on every filesystem; fall back rather
                // than leave the registry stale.
                manifestFile.delete()
                if (!tmp.renameTo(manifestFile)) {
                    Log.w(TAG, "cannot publish manifest")
                    tmp.delete()
                    return
                }
            }

            // Step 3: orphans go last, and only ones the manifest no longer names.
            iconsDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.endsWith(".png") && !published.contains(f.name)) {
                    f.delete()
                }
            }

            Log.i(TAG, "published ${apps.length()} override(s) to $manifestFile")
        } catch (t: Throwable) {
            Log.w(TAG, "cannot publish icon overrides", t)
        }
    }

    /**
     * True when the on-disk registry was produced from exactly these inputs and
     * is still intact. Keeps the boot-time publish free: a read, a compare and
     * one stat per entry, with no rasterisation.
     */
    private fun isCurrent(manifestFile: File, iconsDir: File, fingerprint: String): Boolean {
        return try {
            if (!manifestFile.isFile) return false
            val root = JSONObject(manifestFile.readText())
            if (root.optInt("version", -1) != FORMAT_VERSION) return false
            if (root.optString("sourceFingerprint") != fingerprint) return false

            // The fingerprint only covers the inputs. Verify the outputs still
            // exist, so a hand-deleted PNG republishes instead of staying broken.
            val apps = root.optJSONArray("apps") ?: return false
            for (i in 0 until apps.length()) {
                val icon = apps.optJSONObject(i)?.optString("icon").orEmpty()
                if (icon.isNotEmpty() && !File(iconsDir.parentFile, icon).isFile) return false
            }
            true
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Hash of everything that can change what gets published. Impulse's own
     * versionCode is in here because the icon set ships inside the APK, so an
     * update can change what a slug renders to with the overrides untouched.
     */
    private fun fingerprint(context: Context, overrides: List<Override>): String {
        val sb = StringBuilder()
        sb.append(FORMAT_VERSION).append('|').append(ICON_PX).append('|')
        sb.append(versionCode(context)).append('|')
        overrides.sortedBy { it.packageName }.forEach { o ->
            sb.append(o.packageName).append('')
                .append(o.label.orEmpty()).append('')
                .append(o.iconSlug.orEmpty()).append('')
                .append(o.iconColor?.toString().orEmpty()).append('')
        }
        return try {
            MessageDigest.getInstance("SHA-256")
                .digest(sb.toString().toByteArray())
                .joinToString("") { "%02x".format(it) }
        } catch (t: Throwable) {
            sb.toString().hashCode().toString()
        }
    }

    @Suppress("DEPRECATION")
    private fun versionCode(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    } catch (t: Throwable) {
        0L
    }

    /**
     * Rasterise a slug that is backed by a plain drawable resource
     * (`ic_<slug>_default`), applying [Override.iconColor] so the published PNG
     * is already tinted — consumers treat `iconColor` as a hint, not an
     * instruction, so an untinted PNG would simply render untinted.
     *
     * Not used by Impulse's own call site (its brand-mark slugs don't follow
     * this naming convention and must not be tinted) — kept for reference /
     * any future slug that does fit the convention.
     */
    fun renderFromDrawableResource(context: Context, override: Override): Bitmap? {
        val slug = override.iconSlug?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val id = context.resources.getIdentifier(
                "ic_${slug}_default", "drawable", context.packageName
            )
            if (id == 0) return null
            val drawable: Drawable = context.getDrawable(id)?.mutate() ?: return null
            override.iconColor?.let {
                drawable.colorFilter = PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN)
            }
            Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, ICON_PX, ICON_PX)
                drawable.draw(canvas)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "cannot render drawable for $slug", t)
            null
        }
    }

    private fun writePng(target: File, bitmap: Bitmap): Boolean {
        val scaled = if (bitmap.width == ICON_PX && bitmap.height == ICON_PX) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, ICON_PX, ICON_PX, true)
        }
        val tmp = File(target.parentFile, "${target.name}.tmp")
        return try {
            tmp.outputStream().use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, out)) return false
            }
            target.delete()
            tmp.renameTo(target)
        } catch (t: Throwable) {
            Log.w(TAG, "cannot write $target", t)
            tmp.delete()
            false
        }
    }
}
