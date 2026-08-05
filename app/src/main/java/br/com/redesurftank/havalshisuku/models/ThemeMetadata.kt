package br.com.redesurftank.havalshisuku.models

data class ThemeMetadata(
    val name: String,
    val description: String,
    val version: String,
    val thumbnailUrl: String, // Can be a local path or a remote URL
    val mainFile: String = "index.html",
    val folderName: String = "", // Used to identify the local folder or remote path
    val isLocal: Boolean = false,
    val isDownloaded: Boolean = false,
    val size: Long = 0,
    val x: Int? = null,
    val y: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val hasUpdate: Boolean = false,
    val remoteSha: String? = null,
    val remoteSize: Long? = null,
    val decentralized: Boolean = false,
    val minBridgeVersion: String? = null,
    val contractVersion: String = "v1.0",
    val configurations: List<ThemeConfig> = emptyList(),
    /** Optional per-display-mode app bounds; overrides x/y/width/height for the named modes. */
    val displayModes: List<ThemeDisplayMode> = emptyList(),
    /** Relative path inside theme package (e.g. car-bg.png). Empty = no theme bg. */
    val background: String = "",
    /** Absolute path when theme is installed locally; empty for remote-only listings. */
    val backgroundAbsolutePath: String = "",
    /** Native mask overlay configurations for Display 3 (fuel, battery, speed, info) */
    val nativeMasks: NativeMaskConfig? = null
)
