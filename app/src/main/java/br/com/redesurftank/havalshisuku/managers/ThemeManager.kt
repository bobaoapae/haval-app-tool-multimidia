package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.util.Log
import android.util.Xml
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.ThemeMetadata
import br.com.redesurftank.havalshisuku.models.ThemeVersionInfo
import br.com.redesurftank.havalshisuku.models.ThemeConfig
import br.com.redesurftank.havalshisuku.models.ThemeDisplayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class ThemeManager private constructor(val context: Context) {
    private val TAG = "ThemeManager"
    private val themesDir = File(context.filesDir, "themes")

    init {
        if (!themesDir.exists()) {
            themesDir.mkdirs()
        }
    }

    companion object {
        private const val TAG = "ThemeManager"
        /** GitHub tree where packaged themes are listed (Telas → fetch/download). Path stays Themes/v1.0; branch is feature/new-screen-enhancements-v7. */
        const val THEME_REPO_URL = "https://github.com/netseek/haval-app-tool-multimidia/tree/feature/new-screen-enhancements-v7/cluster-widgets/Themes/v1.0"
        const val CURRENT_CONTRACT_VERSION = "v1.0"

        /** HttpURLConnection defaults to no timeout at all, so a stalled car connection
         *  would spin the refresh indicator forever with nothing shown to the user. */
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000

        @Volatile
        private var instance: ThemeManager? = null

        fun getInstance(context: Context): ThemeManager {
            return instance ?: synchronized(this) {
                instance ?: ThemeManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun getLocalThemes(): List<ThemeMetadata> {
        val results = mutableListOf<ThemeMetadata>()
        
        // Scan for subdirectories in themesDir
        themesDir.listFiles { file -> file.isDirectory }?.forEach { dir ->
            val xmlFile = File(dir, "theme.xml")
            if (xmlFile.exists()) {
                val metadata = parseThemeXml(xmlFile.inputStream(), dir.name, true)
                if (metadata != null && isContractCompatible(metadata.contractVersion)) {
                    results.add(metadata.copy(isLocal = true, isDownloaded = true))
                }
            }
        }
        
        // Handle legacy flat HTML files if any (fallback)
        themesDir.listFiles { file -> file.extension == "html" }?.forEach { file ->
            val metadata = ThemeMetadata(
                name = file.nameWithoutExtension,
                description = "Arraste e solte para instalar",
                version = "1.0.0",
                thumbnailUrl = "",
                mainFile = file.name,
                folderName = "",
                isLocal = true,
                isDownloaded = true,
                contractVersion = "legacy"
            )
            if (isContractCompatible(metadata.contractVersion)) {
                results.add(metadata)
            }
        }
        
        return results
    }

    fun getThemeMetadata(folderName: String): ThemeMetadata? {
        val dir = File(themesDir, folderName)
        if (!dir.exists()) return null
        val xmlFile = File(dir, "theme.xml")
        return if (xmlFile.exists()) {
            parseThemeXml(xmlFile.inputStream(), folderName, true)
        } else null
    }

    /**
     * Reads and parses theme.xml for the embedded Default theme directly from assets/Default/theme.xml.
     */
    fun getEmbeddedDefaultTheme(): ThemeMetadata {
        return try {
            val inputStream = context.assets.open("Default/theme.xml")
            parseThemeXml(inputStream, "Default", true)?.copy(
                isLocal = true,
                isDownloaded = true,
                hasUpdate = false
            ) ?: fallbackDefaultTheme()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing embedded assets/Default/theme.xml resource, falling back", e)
            fallbackDefaultTheme()
        }
    }

    private fun fallbackDefaultTheme(): ThemeMetadata {
        return ThemeMetadata(
            name = "Default",
            description = "Tema principal com o novo design Sport e suporte completo a telemetria descentralizada.",
            version = "1.4.34",
            thumbnailUrl = "",
            mainFile = "index.html",
            folderName = "Default",
            isLocal = true,
            isDownloaded = true,
            hasUpdate = false,
            contractVersion = "v1.0",
            // Mirrors assets/Default/theme.xml - same entries, same order, same option
            // order. Only reached when that descriptor fails to parse.
            configurations = listOf(
                ThemeConfig("theme_mode", "Modo Visual", "combo", "Dark", "mode", listOf("Dark", "Light"), "Geral"),
                ThemeConfig("gauge_style", "Estilo dos Marcadores", "combo", "Esportivo", "gaugeStyle", listOf("Esportivo", "Clássico"), "Geral"),
                ThemeConfig("accent_color", "Cor de Destaque", "color", "#00A0FF", "accentColor", listOf("#00A0FF", "#FF0033", "#00E676", "#FF6D00", "#0033CC", "#000000", "#FFFFFF", "#808080"), "Cores"),
                ThemeConfig("icon_color", "Cor dos Ícones", "color", "#00A0E0", "iconColor", listOf("#00A0E0", "#FF0033", "#00E676", "#FF6D00", "#FFC400", "#B388FF", "#FFFFFF", "#808080"), "Cores"),
                ThemeConfig("drive_mode_colors", "Cor por Modo de Condução", "boolean", "false", "driveModeColors", emptyList(), "Cores"),
                ThemeConfig("fuel_color", "Cor do Medidor de Combustível", "color", "#3B82F6", "fuelColor", listOf("#3B82F6", "#00A0FF", "#00E676", "#FFC400", "#FF6D00", "#FF0033", "#FFFFFF", "#808080"), "Cores"),
                ThemeConfig("battery_color", "Cor do Medidor de Bateria", "color", "#10B981", "batteryColor", listOf("#10B981", "#00E676", "#00A0FF", "#FFC400", "#FF6D00", "#FF0033", "#FFFFFF", "#808080"), "Cores"),
                ThemeConfig("bar_images", "Imagens das Barras", "boolean", "true", "barImages", emptyList(), "Barras"),
                ThemeConfig("hidden_bars", "Ocultar Barras", "combo", "Nenhuma", "hiddenBars", listOf("Nenhuma", "Superior", "Inferior", "Ambas"), "Barras"),
                ThemeConfig("hidden_bars_on_projection", "Ocultar Barras na Projeção", "combo", "Nenhuma", "hiddenBarsOnProjection", listOf("Nenhuma", "Superior", "Inferior", "Ambas"), "Barras"),
                ThemeConfig("navigation_display_mode", "Modo de Exibição na Navegação", "combo", "Mapa", "navigationDisplayMode", listOf("Normal", "Reduzido", "Clean", "Mapa"), "Exibição"),
                ThemeConfig("app_display_mode", "Modo de Exibição de Outros Apps", "combo", "Normal", "appDisplayMode", listOf("Normal", "Reduzido", "Clean", "Mapa"), "Exibição")
            )
        )
    }

    fun isContractCompatible(contractVersion: String?): Boolean {
        if (contractVersion.isNullOrBlank() || contractVersion.equals("legacy", ignoreCase = true)) return false
        val normalized = contractVersion.trim().lowercase().let {
            if (it.startsWith("v")) it else "v$it"
        }
        return normalized == CURRENT_CONTRACT_VERSION || normalized == "v1.0" || normalized == "1.0"
    }

    fun sanitizeActiveThemeContract(context: Context) {
        val prefs = context.getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
        val activeFolder = prefs.getString(br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key, "") ?: ""
        val virtualTheme = prefs.getString(br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.VIRTUAL_CLUSTER_THEME.key, "Default") ?: "Default"

        var isFolderInvalid = false
        if (activeFolder.isNotBlank() && !activeFolder.equals("Default", ignoreCase = true)) {
            val metadata = getThemeMetadata(activeFolder)
            if (metadata == null || !isContractCompatible(metadata.contractVersion)) {
                isFolderInvalid = true
            }
        }

        var isVirtualInvalid = false
        if (!virtualTheme.equals("Default", ignoreCase = true) && !virtualTheme.equals("Minimalist", ignoreCase = true)) {
            // Check if virtualTheme metadata is contract compatible
            val metadata = getLocalThemes().firstOrNull { it.name.equals(virtualTheme, ignoreCase = true) }
            if (metadata == null || !isContractCompatible(metadata.contractVersion)) {
                isVirtualInvalid = true
            }
        }

        if (isFolderInvalid || isVirtualInvalid) {
            Log.w(TAG, "Active theme (folder='$activeFolder', virtual='$virtualTheme') is incompatible with contract $CURRENT_CONTRACT_VERSION. Falling back to Default.")
            prefs.edit()
                .putString(br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key, "")
                .putString(br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.VIRTUAL_CLUSTER_THEME.key, "Default")
                .apply()
        }
    }

    private fun parseThemeXml(inputStream: InputStream, folderName: String, isLocal: Boolean): ThemeMetadata? {
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)
            
            var name = ""
            var description = ""
            var version = ""
            var thumbnail = ""
            var mainFile = "index.html"
            var background = ""
            var x: Int? = null
            var y: Int? = null
            var width: Int? = null
            var height: Int? = null
            var decentralized = false
            var minBridgeVersion: String? = null
            var contractVersion = ""
            val configurations = mutableListOf<ThemeConfig>()
            val displayModes = mutableListOf<ThemeDisplayMode>()

            var inConfigurations = false
            var inConfiguration = false
            var inAppDefaultPosition = false
            
            var configId = ""
            var configLabel = ""
            var configType = ""
            var configDefault = ""
            var configStateVar = ""
            var configOptions = ""
            var configGroup = ""

            var fuelMask = br.com.redesurftank.havalshisuku.models.NativeMaskSpec(x = 0, y = 520, width = 380, height = 200)
            var batteryMask = br.com.redesurftank.havalshisuku.models.NativeMaskSpec(x = 1540, y = 520, width = 380, height = 200)
            var speedMask = br.com.redesurftank.havalshisuku.models.NativeMaskSpec(x = 0, y = 0, width = 600, height = 720)
            var infoMask = br.com.redesurftank.havalshisuku.models.NativeMaskSpec(x = 1320, y = 0, width = 600, height = 720)
            var topCenterMask = br.com.redesurftank.havalshisuku.models.NativeMaskSpec(x = 710, y = 0, width = 500, height = 67)
            var hasNativeMasks = false

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                val eventType = parser.eventType
                val tagName = parser.name
                
                if (eventType == XmlPullParser.START_TAG) {
                    when (tagName) {
                        "name" -> name = parser.nextText()
                        "description" -> description = parser.nextText()
                        "version" -> version = parser.nextText()
                        "thumbnail" -> thumbnail = parser.nextText()
                        "mainFile" -> mainFile = parser.nextText()
                        "background" -> background = parser.nextText().trim()
                        "AppDefaultPosition" -> inAppDefaultPosition = true
                        // Attributes, not child elements, on purpose: this parser matches bare
                        // tag names globally, so a <name> child here would overwrite the theme's
                        // own <name> and <width>/<height> would collide with AppDefaultPosition.
                        "DisplayMode" -> {
                            val modeName = parser.getAttributeValue(null, "name")?.trim().orEmpty()
                            val modeX = parser.getAttributeValue(null, "x")?.trim()?.toIntOrNull()
                            val modeY = parser.getAttributeValue(null, "y")?.trim()?.toIntOrNull()
                            val modeWidth = parser.getAttributeValue(null, "width")?.trim()?.toIntOrNull()
                            val modeHeight = parser.getAttributeValue(null, "height")?.trim()?.toIntOrNull()
                            if (modeName.isNotEmpty() && modeX != null && modeY != null && modeWidth != null && modeHeight != null) {
                                displayModes.add(ThemeDisplayMode(modeName, modeX, modeY, modeWidth, modeHeight))
                            } else {
                                Log.w(TAG, "Ignoring <DisplayMode> in $folderName: needs name, x, y, width and height attributes")
                            }
                        }
                        "fuelMask", "batteryMask", "speedMask", "infoMask", "topCenterMask" -> {
                            hasNativeMasks = true
                            val enabled = (parser.getAttributeValue(null, "enabled") ?: "true").trim().lowercase() == "true"
                            val image = parser.getAttributeValue(null, "image")?.trim().orEmpty()
                            val opacity = parser.getAttributeValue(null, "opacity")?.toFloatOrNull() ?: 1.0f
                            val defaultX = when (tagName) { "batteryMask" -> 1540; "infoMask" -> 1320; "topCenterMask" -> 710; else -> 0 }
                            val defaultY = when (tagName) { "fuelMask", "batteryMask" -> 520; else -> 0 }
                            val defaultW = when (tagName) { "fuelMask", "batteryMask" -> 380; "topCenterMask" -> 500; else -> 600 }
                            val defaultH = when (tagName) { "fuelMask", "batteryMask" -> 200; "topCenterMask" -> 67; else -> 720 }
                            val maskX = parser.getAttributeValue(null, "x")?.trim()?.toIntOrNull() ?: defaultX
                            val maskY = parser.getAttributeValue(null, "y")?.trim()?.toIntOrNull() ?: defaultY
                            val maskW = parser.getAttributeValue(null, "width")?.trim()?.toIntOrNull() ?: defaultW
                            val maskH = parser.getAttributeValue(null, "height")?.trim()?.toIntOrNull() ?: defaultH
                            val spec = br.com.redesurftank.havalshisuku.models.NativeMaskSpec(enabled, image, maskX, maskY, maskW, maskH, opacity)
                            when (tagName) {
                                "fuelMask" -> fuelMask = spec
                                "batteryMask" -> batteryMask = spec
                                "speedMask" -> speedMask = spec
                                "infoMask" -> infoMask = spec
                                "topCenterMask" -> topCenterMask = spec
                            }
                        }
                        "x" -> if (inAppDefaultPosition) x = parser.nextText().toIntOrNull()
                        "y" -> if (inAppDefaultPosition) y = parser.nextText().toIntOrNull()
                        "width" -> if (inAppDefaultPosition) width = parser.nextText().toIntOrNull()
                        "height" -> if (inAppDefaultPosition) height = parser.nextText().toIntOrNull()
                        "decentralized" -> decentralized = parser.nextText().trim().lowercase() == "true"
                        "minBridgeVersion" -> minBridgeVersion = parser.nextText().trim()
                        "contractVersion" -> contractVersion = parser.nextText().trim()
                        
                        "configurations" -> inConfigurations = true
                        "configuration" -> {
                            inConfiguration = true
                            configId = ""; configLabel = ""; configType = ""; configDefault = ""; configStateVar = ""; configOptions = ""; configGroup = ""
                        }
                        "id" -> if (inConfiguration) configId = parser.nextText().trim()
                        "label" -> if (inConfiguration) configLabel = parser.nextText().trim()
                        "type" -> if (inConfiguration) configType = parser.nextText().trim().lowercase()
                        "default" -> if (inConfiguration) configDefault = parser.nextText().trim()
                        "stateVariable" -> if (inConfiguration) configStateVar = parser.nextText().trim()
                        "options" -> if (inConfiguration) configOptions = parser.nextText().trim()
                        // Optional. Themes written before this existed simply omit it and
                        // land in the default "Geral" tab.
                        "group" -> if (inConfiguration) configGroup = parser.nextText().trim()
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    when (tagName) {
                        "AppDefaultPosition" -> inAppDefaultPosition = false
                        "configurations" -> inConfigurations = false
                        "configuration" -> {
                            inConfiguration = false
                            if (configId.isNotEmpty() && configStateVar.isNotEmpty()) {
                                val opts = if (configOptions.isNotEmpty()) configOptions.split(",").map { it.trim() } else emptyList()
                                configurations.add(ThemeConfig(configId, configLabel, configType, configDefault, configStateVar, opts, configGroup))
                            }
                        }
                    }
                }
            }
            
            if (name.isEmpty()) return null
            
            // Resolve thumbnail path
            val resolvedThumbnail = if (isLocal && !thumbnail.startsWith("http")) {
                File(File(themesDir, folderName), thumbnail).absolutePath
            } else {
                thumbnail
            }

            val resolvedBackgroundAbs = if (isLocal && background.isNotEmpty()) {
                File(File(themesDir, folderName), background).takeIf { it.exists() }?.absolutePath ?: ""
            } else {
                ""
            }
            
            val resolvedContractVersion = if (contractVersion.isNotBlank()) {
                contractVersion
            } else if (folderName.equals("Default", ignoreCase = true) || folderName.equals("minimalist", ignoreCase = true)) {
                "v1.0"
            } else {
                "legacy"
            }
            
            val nativeMasksConfig = if (hasNativeMasks) {
                br.com.redesurftank.havalshisuku.models.NativeMaskConfig(fuelMask, batteryMask, speedMask, infoMask, topCenterMask)
            } else null

            return ThemeMetadata(
                name = name,
                description = description,
                version = version,
                thumbnailUrl = resolvedThumbnail,
                mainFile = mainFile,
                folderName = folderName,
                isLocal = isLocal,
                x = x,
                y = y,
                width = width,
                height = height,
                decentralized = decentralized,
                minBridgeVersion = minBridgeVersion,
                contractVersion = resolvedContractVersion,
                configurations = configurations,
                displayModes = displayModes,
                background = background,
                backgroundAbsolutePath = resolvedBackgroundAbs,
                nativeMasks = nativeMasksConfig
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing theme.xml in $folderName", e)
            return null
        } finally {
            inputStream.close()
        }
    }

    /**
     * Raised when the theme catalogue cannot be read. Carries a message meant for the
     * user: previously every failure here collapsed into an empty list, so a rate-limited
     * or timed-out refresh looked identical to "no themes available".
     */
    class ThemeFetchException(val userMessage: String, cause: Throwable? = null) :
            Exception(userMessage, cause)

    suspend fun fetchThemesFromGithub(repoUrl: String): List<ThemeMetadata> {
        return withContext(Dispatchers.IO) {
            try {
                val apiUrl = convertToGithubApiUrl(repoUrl)
                Log.i(TAG, "Fetching themes from API: $apiUrl")

                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS

                if (conn.responseCode != 200) {
                    val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e(TAG, "Failed to fetch themes: ${conn.responseCode} - $errorBody")
                    throw ThemeFetchException(
                            when (conn.responseCode) {
                                // The API is called unauthenticated: 60 req/h per IP, and one
                                // refresh costs 1 + one request per theme folder.
                                403, 429 -> "Limite de consultas do GitHub atingido. Aguarde alguns minutos e tente de novo."
                                404 -> "Repositório de temas não encontrado."
                                in 500..599 -> "GitHub indisponível no momento (${conn.responseCode})."
                                else -> "GitHub respondeu ${conn.responseCode}."
                            }
                    )
                }

                val jsonString = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonString)
                val results = mutableListOf<ThemeMetadata>()
                
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    if (obj.getString("type") == "dir") {
                        val folderName = obj.getString("name")
                        val folderUrl = obj.getString("url")
                        
                        // Fetch theme.xml for this folder
                        val metadata = fetchThemeMetadataFromGithub(folderUrl, folderName)
                        if (metadata != null) {
                            results.add(metadata)
                        }
                    }
                }
                results
            } catch (e: ThemeFetchException) {
                throw e
            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "Timeout fetching themes from GitHub", e)
                throw ThemeFetchException(
                        "Tempo esgotado ao consultar o GitHub. Verifique a conexão do carro.", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching themes from GitHub", e)
                throw ThemeFetchException(
                        "Falha ao consultar atualizações: ${e.message ?: e.javaClass.simpleName}", e)
            }
        }
    }

    private suspend fun fetchThemeMetadataFromGithub(folderApiUrl: String, folderName: String): ThemeMetadata? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(folderApiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS

                if (conn.responseCode != 200) return@withContext null
                
                val jsonString = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonString)
                var themeXmlDownloadUrl = ""
                
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    if (obj.getString("name") == "theme.xml") {
                        themeXmlDownloadUrl = obj.getString("download_url")
                        break
                    }
                }
                
                if (themeXmlDownloadUrl.isEmpty()) return@withContext null
                
                // Fetch the theme.xml content
                val xmlUrl = URL(themeXmlDownloadUrl)
                val xmlConn = xmlUrl.openConnection() as HttpURLConnection
                xmlConn.connectTimeout = CONNECT_TIMEOUT_MS
                xmlConn.readTimeout = READ_TIMEOUT_MS
                val metadata = parseThemeXml(xmlConn.inputStream, folderName, false)
                
                if (metadata != null) {
                    // Resolve relative thumbnail URL to absolute GitHub raw URL if needed
                    val resolvedThumbnail = if (!metadata.thumbnailUrl.startsWith("http")) {
                        // Assuming thumbnail is in the same folder
                        themeXmlDownloadUrl.replace("theme.xml", metadata.thumbnailUrl)
                    } else {
                        metadata.thumbnailUrl
                    }
                    
                    var mainFileSha = ""
                    var mainFileSize = 0L
                    val mainFileName = metadata.mainFile.ifEmpty { "index.html" }
                    
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        if (obj.getString("name") == mainFileName) {
                            mainFileSha = obj.optString("sha", "")
                            mainFileSize = obj.optLong("size", 0L)
                            break
                        }
                    }
                    
                    return@withContext metadata.copy(
                        thumbnailUrl = resolvedThumbnail,
                        remoteSha = mainFileSha,
                        remoteSize = mainFileSize
                    )
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching metadata for $folderName", e)
                null
            }
        }
    }

    fun isEmbeddedDifferent(remoteSha: String?, remoteSize: Long?): Boolean {
        if (remoteSha.isNullOrEmpty() || remoteSize == null || remoteSize <= 0) return false
        return try {
            val inputStream = context.resources.openRawResource(br.com.redesurftank.havalshisuku.R.raw.app)
            val bytes = inputStream.readBytes()
            val localSize = bytes.size.toLong()
            if (localSize != remoteSize) {
                Log.d("ThemeManager", "Embedded size ($localSize) differs from remote ($remoteSize)")
                return true
            }
            
            // Calculate Git SHA-1 of local bytes: SHA-1("blob " + size + "\0" + content)
            val header = "blob $localSize\u0000".toByteArray(Charsets.UTF_8)
            val gitBytes = ByteArray(header.size + bytes.size)
            System.arraycopy(header, 0, gitBytes, 0, header.size)
            System.arraycopy(bytes, 0, gitBytes, header.size, bytes.size)
            
            val messageDigest = java.security.MessageDigest.getInstance("SHA-1")
            val hashBytes = messageDigest.digest(gitBytes)
            val localSha = hashBytes.joinToString("") { "%02x".format(it) }
            
            val matches = localSha.equals(remoteSha, ignoreCase = true)
            Log.d("ThemeManager", "Embedded SHA-1: $localSha, Remote SHA-1: $remoteSha. Matches: $matches")
            !matches
        } catch (e: Exception) {
            Log.e("ThemeManager", "Error comparing embedded app with remote", e)
            false
        }
    }

    data class GithubRepoInfo(
        val owner: String,
        val repo: String,
        val branch: String,
        val path: String
    )

    fun parseGithubUrl(webUrl: String): GithubRepoInfo? {
        var url = webUrl.trim()
        if (url.endsWith("/")) url = url.substring(0, url.length - 1)
        if (!url.startsWith("https://github.com/")) return null
        
        val parts = url.replace("https://github.com/", "").split("/")
        if (parts.size < 2) return null
        
        val owner = parts[0]
        val repo = parts[1]
        
        var branch = "main"
        var path = ""
        
        if (parts.size >= 5 && parts[2] == "tree") {
            if (parts.size >= 6 && (parts[3] == "feature" || parts[3] == "fix" || parts[3] == "release")) {
                branch = "${parts[3]}/${parts[4]}"
                path = parts.subList(5, parts.size).joinToString("/")
            } else {
                branch = parts[3]
                path = parts.subList(4, parts.size).joinToString("/")
            }
        }
        
        return GithubRepoInfo(owner, repo, branch, path)
    }

    private fun convertToGithubApiUrl(webUrl: String): String {
        val info = parseGithubUrl(webUrl) ?: return webUrl
        return if (info.path.isNotEmpty()) {
            "https://api.github.com/repos/${info.owner}/${info.repo}/contents/${info.path}?ref=${info.branch}"
        } else {
            "https://api.github.com/repos/${info.owner}/${info.repo}/contents?ref=${info.branch}"
        }
    }

    suspend fun downloadTheme(metadata: ThemeMetadata): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val destDir = File(themesDir, metadata.folderName)
                if (!destDir.exists()) destDir.mkdirs()
                
                // Read preference for custom theme repo or use defaults
                val sharedPrefs = context.getSharedPreferences("MainPrefs", Context.MODE_PRIVATE)
                val customUrl = sharedPrefs.getString("customThemeRepoUrlProd", null)
                val repoUrl = if (!customUrl.isNullOrBlank()) customUrl else THEME_REPO_URL
                
                val info = parseGithubUrl(repoUrl)
                val apiUrl = if (info != null) {
                    val fullPath = if (info.path.isNotEmpty()) "${info.path}/${metadata.folderName}" else metadata.folderName
                    "https://api.github.com/repos/${info.owner}/${info.repo}/contents/$fullPath?ref=${info.branch}"
                } else {
                    "https://api.github.com/repos/netseek/haval-app-tool-multimidia/contents/cluster-widgets/Themes/v1.0/${metadata.folderName}?ref=feature/new-screen-enhancements-v7"
                }
                
                Log.d(TAG, "Downloading theme from API: $apiUrl")
                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS

                if (conn.responseCode != 200) {
                    Log.e(TAG, "Download failed with status: ${conn.responseCode}")
                    return@withContext false
                }
                
                val jsonString = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonString)
                
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val fileName = obj.getString("name")
                    val downloadUrl = obj.getString("download_url")
                    
                    // Download each file
                    val destFile = File(destDir, fileName)
                    val fileUrl = URL(downloadUrl)
                    val fileConn = fileUrl.openConnection() as HttpURLConnection
                    fileConn.connectTimeout = CONNECT_TIMEOUT_MS
                    fileConn.readTimeout = READ_TIMEOUT_MS
                    BufferedInputStream(fileConn.inputStream).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading theme: ${metadata.name}", e)
                false
            }
        }
    }

    fun getThemeFile(folderName: String, filename: String): File? {
        val file = File(File(themesDir, folderName), filename)
        return if (file.exists()) file else null
    }

    /**
     * Resolve the active virtual-cluster theme's background image file, if any.
     * [relativeHint] is an optional relative path (e.g. car-bg.png) from prefs or the bridge.
     */
    fun getActiveThemeBackgroundFile(relativeHint: String? = null): File? {
        val prefs = App.getDeviceProtectedContext()
            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
        val folder = prefs.getString(br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key, "")
            ?.takeIf { it.isNotBlank() && !it.equals("Default", ignoreCase = true) }
            ?: return null

        val meta = getThemeMetadata(folder)
        val relative = relativeHint?.takeIf { it.isNotBlank() }
            ?: meta?.background?.takeIf { it.isNotBlank() }
            ?: return null

        // Allow only relative paths inside the theme folder (no path traversal)
        val safeName = relative
            .replace('\\', '/')
            .removePrefix("./")
            .substringAfterLast('/') // flatten nested hints to basename if needed
        if (safeName.isBlank() || safeName.contains("..")) return null

        // Prefer exact relative path first, then basename fallback
        getThemeFile(folder, relative.replace('\\', '/').removePrefix("./"))?.let { return it }
        return getThemeFile(folder, safeName)
    }

    fun getThemeBackgroundAbsolutePath(folderName: String): String {
        if (folderName.isBlank() || folderName.equals("Default", ignoreCase = true)) return ""
        val meta = getThemeMetadata(folderName) ?: return ""
        return meta.backgroundAbsolutePath
    }
    
    fun isNewerVersion(current: String, remote: String): Boolean {
        if (current == remote) return false
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until maxOf(currentParts.size, remoteParts.size)) {
            val c = currentParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (c > r) return false
        }
        return false
    }

    fun deleteTheme(folderName: String): Boolean {
        val dir = File(themesDir, folderName)
        return if (dir.exists()) dir.deleteRecursively() else false
    }
}
