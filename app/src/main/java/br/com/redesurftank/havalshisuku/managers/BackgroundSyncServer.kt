package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

class BackgroundSyncServer(private val onApplied: () -> Unit) {
    private val TAG = "BackgroundSyncServer"
    private var serverSocket: ServerSocket? = null
    private var running = false
    private var thread: Thread? = null

    companion object {
        private const val UPLOADS_DIR_NAME = "uploaded_backgrounds"
        private const val MAX_UPLOAD_BYTES = 12 * 1024 * 1024
        private const val SOCKET_TIMEOUT_MS = 10_000

        fun getLocalIpAddress(): String? {
            data class Candidate(val ip: String, val priority: Int)

            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                val candidates = mutableListOf<Candidate>()
                for (intf in interfaces) {
                    if (!intf.isUp || intf.isLoopback || intf.isVirtual) continue
                    val name = intf.name.lowercase()
                    // This head unit runs several networks at once: wlan0 is its own WAN-style
                    // uplink (10.x, default route), vt* is its own outgoing hotspot/AP, and the
                    // internal ECU bus rides eth0's vlan* sub-interfaces (172.16.x). None of those
                    // is the phone's LAN - skip anything that looks like tunnel/tether/AP/bus traffic
                    // by name, then rank the rest by address shape below.
                    val looksLikeApOrTether = name.startsWith("rmnet") ||
                        name.startsWith("tun") ||
                        name.startsWith("ccmni") ||
                        name.startsWith("usb") ||
                        name.startsWith("rndis") ||
                        name.startsWith("p2p") ||
                        name.startsWith("ap") ||
                        name.startsWith("swlan") ||
                        name.startsWith("vt") ||
                        name.startsWith("bond")
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (addr.isLoopbackAddress) continue
                        val sAddr = addr.hostAddress ?: continue
                        if (sAddr.indexOf(':') >= 0) continue // skip IPv6
                        // A ".1" host address usually means this device IS the gateway (i.e. it's
                        // hosting its own AP/tether subnet), not a client joined to the phone's Wi-Fi.
                        val looksLikeSelfHostedGateway = sAddr.endsWith(".1")
                        val priority = when {
                            sAddr.startsWith("192.168.") && !looksLikeApOrTether && !looksLikeSelfHostedGateway -> 0
                            sAddr.startsWith("192.168.") -> 1
                            looksLikeApOrTether -> 4
                            name == "wlan0" || name.startsWith("wlan") || name.startsWith("eth") -> 2
                            else -> 3
                        }
                        candidates.add(Candidate(sAddr, priority))
                    }
                }
                return candidates.minByOrNull { it.priority }?.ip
            } catch (ex: Exception) {
                Log.e("BackgroundSyncServer", "Error getting IP address", ex)
            }
            return null
        }

        fun getUploadsDir(): File {
            val dir = File(App.getDeviceProtectedContext().filesDir, UPLOADS_DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        fun listUploadedImages(): List<File> {
            return getUploadsDir().listFiles()
                ?.filter { it.isFile }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        }

        fun deleteUploadedImage(name: String): Boolean {
            val safeName = File(name).name
            if (safeName.isBlank()) return false
            val file = File(getUploadsDir(), safeName)
            val dirCanonical = getUploadsDir().canonicalPath
            if (!file.canonicalPath.startsWith(dirCanonical)) return false
            return file.exists() && file.delete()
        }
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            try {
                serverSocket = ServerSocket(8080)
                Log.w(TAG, "Sync server started on port 8080")
                while (running) {
                    val socket = serverSocket?.accept() ?: break
                    handleClient(socket)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket exception: ${e.message}")
            } finally {
                stop()
            }
        }.apply {
            name = "BackgroundSyncServerThread"
            start()
        }
    }

    fun stop() {
        if (!running) return
        running = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket: ${e.message}")
        }
        serverSocket = null
        thread = null
        Log.w(TAG, "Sync server stopped")
    }

    private fun handleClient(socket: Socket) {
        Thread {
            try {
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                val writer = PrintWriter(output)

                val requestLine = readLine(input) ?: return@Thread
                Log.d(TAG, "HTTP Request: $requestLine")

                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(":")
                    if (idx > 0) {
                        headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                    }
                }

                val parts = requestLine.split(" ")
                if (parts.size < 2) return@Thread
                val method = parts[0]
                val path = parts[1]

                // Some clients wait for a "100 Continue" before streaming the (potentially large)
                // multipart body; without this ack they can stall until their own timeout.
                if (headers["expect"]?.contains("100-continue", ignoreCase = true) == true) {
                    output.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                    output.flush()
                }

                when {
                    method == "POST" && path.startsWith("/upload") -> handleUpload(input, headers, writer)
                    path.startsWith("/apply") -> handleApply(path, writer)
                    path.startsWith("/delete") -> handleDelete(path, writer)
                    path.startsWith("/images/") -> {
                        handleImageServe(path, output)
                        socket.close()
                        return@Thread
                    }
                    else -> sendResponse(writer, getFormHtml())
                }

                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling client connection", e)
            }
        }.start()
    }

    /** Reads a single CRLF-terminated ASCII line directly off the raw socket stream (no buffering ahead, so binary bodies read afterwards stay intact). */
    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        var readAny = false
        while (true) {
            val b = input.read()
            if (b == -1) return if (readAny) buffer.toString("ISO-8859-1") else null
            readAny = true
            if (b == '\n'.code) break
            if (b == '\r'.code) continue
            buffer.write(b)
        }
        return buffer.toString("ISO-8859-1")
    }

    private fun handleApply(path: String, writer: PrintWriter) {
        val query = path.substringAfter("?", "")
        val params = parseQueryParams(query)
        val type = params["type"] ?: "IMAGE_URL"
        val value = try {
            java.net.URLDecoder.decode(params["value"] ?: "", "UTF-8")
        } catch (e: Exception) {
            params["value"] ?: ""
        }

        if (value.isNotEmpty()) {
            val prefs = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
            prefs.edit {
                putBoolean(SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key, true)
                putString(SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, type)
                putString(SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, value)
            }
            Log.w(TAG, "Background updated via Wi-Fi: Type=$type, Value=$value")
            onApplied()
        }

        sendResponse(writer, getSuccessHtml())
    }

    private fun handleDelete(path: String, writer: PrintWriter) {
        val query = path.substringAfter("?", "")
        val params = parseQueryParams(query)
        val name = try {
            java.net.URLDecoder.decode(params["name"] ?: "", "UTF-8")
        } catch (e: Exception) {
            params["name"] ?: ""
        }
        if (name.isNotEmpty()) {
            deleteUploadedImage(name)
            Log.w(TAG, "Uploaded background deleted: $name")
        }
        sendResponse(writer, getFormHtml())
    }

    private fun handleImageServe(path: String, output: OutputStream) {
        val encodedName = path.substringAfter("/images/").substringBefore("?")
        val name = try {
            java.net.URLDecoder.decode(encodedName, "UTF-8")
        } catch (e: Exception) {
            encodedName
        }
        val safeName = File(name).name
        val file = File(getUploadsDir(), safeName)
        if (safeName.isBlank() || !file.exists() || !file.isFile) {
            output.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            output.flush()
            return
        }
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val bytes = file.readBytes()
        val header = "HTTP/1.1 200 OK\r\nContent-Type: $mime\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(bytes)
        output.flush()
    }

    private fun handleUpload(input: InputStream, headers: Map<String, String>, writer: PrintWriter) {
        val contentType = headers["content-type"] ?: ""
        if (!contentType.contains("multipart/form-data")) {
            Log.w(TAG, "Upload rejected: unexpected Content-Type '$contentType'")
            sendResponse(writer, getFormHtml())
            return
        }

        val isChunked = headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0

        if (!isChunked && contentLength !in 1..MAX_UPLOAD_BYTES) {
            Log.w(TAG, "Upload rejected: invalid Content-Length $contentLength")
            sendResponse(writer, getFormHtml("A imagem excede o limite de 12 MB."))
            return
        }

        val body: ByteArray = try {
            if (isChunked) {
                readChunkedBody(input)
            } else if (contentLength > 0) {
                val buf = ByteArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val n = input.read(buf, readTotal, contentLength - readTotal)
                    if (n == -1) break
                    readTotal += n
                }
                if (readTotal < contentLength) buf.copyOf(readTotal) else buf
            } else {
                Log.w(TAG, "Upload rejected: no Content-Length or chunked Transfer-Encoding present")
                ByteArray(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading upload body", e)
            ByteArray(0)
        }

        if (body.isEmpty()) {
            sendResponse(writer, getFormHtml())
            return
        }

        // Boundary can legally be a quoted-string per RFC 2046; strip quotes if a client sends it that way.
        val boundaryMarker = contentType.substringAfter("boundary=").trim().trim('"')
        var rejectionMessage: String? = null
        if (boundaryMarker.isNotEmpty()) {
            try {
                when (val outcome = extractAndSaveUploadedFile(body, "--$boundaryMarker".toByteArray(Charsets.ISO_8859_1))) {
                    is UploadOutcome.Saved ->
                        Log.w(TAG, "Uploaded background image saved: ${outcome.fileName} (${body.size} bytes)")
                    is UploadOutcome.Rejected -> {
                        Log.w(TAG, "Upload rejected: ${outcome.reason}")
                        rejectionMessage = outcome.reason
                    }
                    UploadOutcome.NoFile ->
                        Log.w(TAG, "Upload parsed but no file part found (body=${body.size} bytes, boundary=$boundaryMarker)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing multipart upload", e)
            }
        } else {
            Log.w(TAG, "Upload rejected: no boundary in Content-Type '$contentType'")
        }

        sendResponse(writer, getFormHtml(rejectionMessage))
    }

    private sealed class UploadOutcome {
        data class Saved(val fileName: String) : UploadOutcome()
        data class Rejected(val reason: String) : UploadOutcome()
        object NoFile : UploadOutcome()
    }

    /** Reads an HTTP chunked-transfer-encoded body: hex chunk-size line, chunk bytes, trailing CRLF, repeated until a 0-size chunk. */
    private fun readChunkedBody(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readLine(input) ?: break
            val size = sizeLine.substringBefore(";").trim().toIntOrNull(16) ?: break
            if (size <= 0) {
                readLine(input) // trailing CRLF after the terminating 0-size chunk
                break
            }
            if (size > MAX_UPLOAD_BYTES || out.size() + size > MAX_UPLOAD_BYTES) {
                throw IllegalStateException("Chunked upload exceeds 12 MB limit")
            }
            val chunk = ByteArray(size)
            var readTotal = 0
            while (readTotal < size) {
                val n = input.read(chunk, readTotal, size - readTotal)
                if (n == -1) break
                readTotal += n
            }
            out.write(chunk, 0, readTotal)
            readLine(input) // consume the CRLF that terminates each chunk
        }
        return out.toByteArray()
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int {
        if (needle.isEmpty() || from < 0) return -1
        val last = haystack.size - needle.size
        outer@ for (i in from..last) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun extractAndSaveUploadedFile(body: ByteArray, boundaryBytes: ByteArray): UploadOutcome {
        val headerBodySeparator = "\r\n\r\n".toByteArray(Charsets.ISO_8859_1)
        var partStart = indexOf(body, boundaryBytes, 0)
        if (partStart == -1) return UploadOutcome.NoFile
        partStart += boundaryBytes.size

        while (true) {
            val nextBoundary = indexOf(body, boundaryBytes, partStart)
            if (nextBoundary == -1) return UploadOutcome.NoFile

            val partEnd = if (nextBoundary >= 2 &&
                body[nextBoundary - 1] == '\n'.code.toByte() &&
                body[nextBoundary - 2] == '\r'.code.toByte()
            ) nextBoundary - 2 else nextBoundary

            val headerEnd = indexOf(body, headerBodySeparator, partStart)
            if (headerEnd != -1 && headerEnd < partEnd) {
                val headerText = String(body, partStart, headerEnd - partStart, Charsets.ISO_8859_1)
                if (headerText.contains("Content-Disposition", ignoreCase = true)) {
                    val filenameMatch = Regex("filename=\"([^\"]*)\"").find(headerText)
                    val originalName = filenameMatch?.groupValues?.get(1)?.trim()
                    if (!originalName.isNullOrBlank()) {
                        val dataStart = headerEnd + headerBodySeparator.size
                        if (dataStart < partEnd) {
                            val fileData = body.copyOfRange(dataStart, partEnd)
                            if (fileData.isNotEmpty()) {
                                return saveUploadedImage(originalName, fileData)
                            }
                        }
                    }
                }
            }

            partStart = nextBoundary + boundaryBytes.size
            if (partStart + 1 < body.size && body[partStart] == '-'.code.toByte() && body[partStart + 1] == '-'.code.toByte()) {
                return UploadOutcome.NoFile
            }
        }
    }

    /**
     * Sniffs the ISOBMFF "ftyp" box brands to catch AVIF/HEIC uploads. This head unit runs
     * Android 9 (API 28); native AVIF/HEIF decoding was only added in API 31, so BitmapFactory
     * silently fails on these and the background renders black. Reject them up front instead.
     */
    private fun detectUnsupportedFormat(data: ByteArray): String? {
        if (data.size < 12 || String(data, 4, 4, Charsets.ISO_8859_1) != "ftyp") return null

        val boxSize = ((data[0].toInt() and 0xFF) shl 24) or
            ((data[1].toInt() and 0xFF) shl 16) or
            ((data[2].toInt() and 0xFF) shl 8) or
            (data[3].toInt() and 0xFF)
        val scanEnd = if (boxSize in 12..data.size) boxSize else minOf(data.size, 64)

        val brands = mutableSetOf<String>()
        var offset = 8
        while (offset + 4 <= scanEnd) {
            brands.add(String(data, offset, 4, Charsets.ISO_8859_1))
            offset += 4
        }

        return when {
            brands.any { it == "avif" || it == "avis" } -> "AVIF"
            brands.any { it in setOf("heic", "heif", "heix", "hevc", "hevx", "mif1", "msf1") } -> "HEIC/HEIF"
            else -> null
        }
    }

    private fun saveUploadedImage(originalName: String, data: ByteArray): UploadOutcome {
        return try {
            val unsupportedFormat = detectUnsupportedFormat(data)
            if (unsupportedFormat != null) {
                return UploadOutcome.Rejected(
                    "Formato $unsupportedFormat não é suportado por este veículo (Android 9 não decodifica $unsupportedFormat). Converta para JPEG, PNG ou WEBP antes de enviar."
                )
            }
            val rawExt = originalName.substringAfterLast('.', "jpg").lowercase()
            val ext = if (rawExt.length in 1..4 && rawExt.all { it.isLetterOrDigit() }) rawExt else "jpg"
            val safeName = "bg_${System.currentTimeMillis()}.$ext"
            val file = File(getUploadsDir(), safeName)
            file.writeBytes(data)
            UploadOutcome.Saved(safeName)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving uploaded image", e)
            UploadOutcome.Rejected("Erro ao salvar a imagem no carro.")
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (query.isEmpty()) return result
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = pair.substring(0, idx)
                val value = pair.substring(idx + 1)
                result[key] = value
            }
        }
        return result
    }

    private fun sendResponse(writer: PrintWriter, htmlContent: String) {
        writer.println("HTTP/1.1 200 OK")
        writer.println("Content-Type: text/html; charset=utf-8")
        writer.println("Content-Length: ${htmlContent.toByteArray(Charsets.UTF_8).size}")
        writer.println("Connection: close")
        writer.println()
        writer.print(htmlContent)
        writer.flush()
    }

    private fun getFormHtml(errorMessage: String? = null): String {
        val errorBanner = if (errorMessage != null) {
            """<div class="error-banner">${errorMessage.replace("<", "&lt;").replace(">", "&gt;")}</div>"""
        } else {
            ""
        }
        val gallery = listUploadedImages().joinToString(separator = "") { file ->
            val encodedName = java.net.URLEncoder.encode(file.name, "UTF-8")
            val encodedPath = java.net.URLEncoder.encode(file.absolutePath, "UTF-8")
            """
            <div class="thumb">
                <img src="/images/$encodedName" alt="${file.name}">
                <div class="thumb-actions">
                    <a class="use-btn" href="/apply?type=FILE&value=$encodedPath">Usar</a>
                    <a class="del-btn" href="/delete?name=$encodedName" onclick="return confirm('Excluir esta imagem?')">Excluir</a>
                </div>
            </div>
            """.trimIndent()
        }
        val galleryBlock = if (gallery.isEmpty()) {
            """<p class="empty-hint">Nenhuma imagem enviada ainda.</p>"""
        } else {
            """<div class="gallery">$gallery</div>"""
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Haval App Tool - Background do Cluster</title>
                <style>
                    body {
                        background-color: #121418;
                        color: #E2E8F0;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        margin: 0;
                        padding: 20px;
                        display: flex;
                        justify-content: center;
                        box-sizing: border-box;
                    }
                    .container {
                        background-color: #1A1F26;
                        border-radius: 12px;
                        padding: 24px;
                        width: 100%;
                        max-width: 460px;
                        box-shadow: 0 4px 20px rgba(0,0,0,0.3);
                        border: 1px solid #2D3748;
                    }
                    h2 {
                        margin-top: 0;
                        color: #4A9EFF;
                        text-align: center;
                        font-size: 22px;
                    }
                    h3 {
                        color: #CBD5E0;
                        font-size: 15px;
                        margin: 28px 0 8px 0;
                        border-top: 1px solid #2D3748;
                        padding-top: 20px;
                    }
                    p {
                        font-size: 14px;
                        color: #A0AEC0;
                        text-align: center;
                        margin-bottom: 24px;
                    }
                    label {
                        display: block;
                        margin-top: 16px;
                        font-size: 14px;
                        font-weight: 600;
                        color: #CBD5E0;
                    }
                    select, input[type="text"], input[type="file"] {
                        width: 100%;
                        padding: 12px;
                        margin-top: 6px;
                        background-color: #2D3748;
                        border: 1px solid #4A5568;
                        border-radius: 6px;
                        color: white;
                        font-size: 15px;
                        box-sizing: border-box;
                    }
                    select:focus, input[type="text"]:focus {
                        outline: none;
                        border-color: #4A9EFF;
                    }
                    button {
                        width: 100%;
                        padding: 14px;
                        margin-top: 24px;
                        background-color: #4A9EFF;
                        color: white;
                        border: none;
                        border-radius: 6px;
                        font-size: 16px;
                        font-weight: bold;
                        cursor: pointer;
                        transition: background-color 0.2s;
                    }
                    button:hover {
                        background-color: #3182CE;
                    }
                    .empty-hint {
                        font-size: 13px;
                        color: #718096;
                        text-align: center;
                    }
                    .gallery {
                        display: grid;
                        grid-template-columns: repeat(2, 1fr);
                        gap: 10px;
                    }
                    .thumb {
                        background-color: #13151A;
                        border: 1px solid #2D3748;
                        border-radius: 8px;
                        overflow: hidden;
                    }
                    .thumb img {
                        width: 100%;
                        height: 90px;
                        object-fit: cover;
                        display: block;
                    }
                    .thumb-actions {
                        display: flex;
                    }
                    .thumb-actions a {
                        flex: 1;
                        text-align: center;
                        padding: 8px 0;
                        font-size: 12px;
                        text-decoration: none;
                        color: white;
                    }
                    .use-btn { background-color: #2F855A; }
                    .del-btn { background-color: #742A2A; }
                    .error-banner {
                        background-color: #742A2A;
                        border: 1px solid #9B2C2C;
                        border-radius: 8px;
                        padding: 12px 14px;
                        margin-bottom: 20px;
                        font-size: 13px;
                        color: #FED7D7;
                        text-align: center;
                    }
                    .footer {
                        margin-top: 30px;
                        text-align: center;
                        font-size: 11px;
                        color: #718096;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2>Enviar Fundo - Background do Cluster</h2>
                    $errorBanner
                    <p>Cole o link da imagem ou página web para aplicar no cluster de instrumentos.</p>
                    <form action="/apply" method="get">
                        <label for="type">Tipo de Fundo</label>
                        <select name="type" id="type">
                            <option value="IMAGE_URL">Imagem da Web</option>
                            <option value="WEB_URL">Página da Web</option>
                        </select>

                        <label for="value">Link / URL</label>
                        <input type="text" name="value" id="value" placeholder="Insira o link aqui..." required>

                        <button type="submit">Aplicar no Carro</button>
                    </form>

                    <h3>Enviar Imagem do Celular</h3>
                    <form action="/upload" method="post" enctype="multipart/form-data">
                        <input type="file" name="file" accept="image/jpeg,image/png,image/webp,image/gif" required>
                        <button type="submit">Enviar Imagem</button>
                    </form>

                    <h3>Imagens Enviadas</h3>
                    $galleryBlock

                    <div class="footer">Haval App Tool Multimedia</div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun getSuccessHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Aplicado com Sucesso</title>
                <style>
                    body {
                        background-color: #121418;
                        color: #E2E8F0;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        margin: 0;
                        padding: 20px;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                    }
                    .container {
                        background-color: #1A1F26;
                        border-radius: 12px;
                        padding: 30px;
                        width: 100%;
                        max-width: 400px;
                        box-shadow: 0 4px 20px rgba(0,0,0,0.3);
                        border: 1px solid #2D3748;
                        text-align: center;
                    }
                    h2 {
                        color: #48BB78;
                        margin-top: 0;
                    }
                    p {
                        color: #A0AEC0;
                        font-size: 15px;
                        margin-bottom: 24px;
                    }
                    a {
                        display: inline-block;
                        padding: 12px 24px;
                        background-color: #4A9EFF;
                        color: white;
                        text-decoration: none;
                        border-radius: 6px;
                        font-weight: bold;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2>Aplicado com Sucesso!</h2>
                    <p>O fundo personalizado foi enviado e aplicado no painel de instrumentos do carro.</p>
                    <a href="/">Enviar outro link</a>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
