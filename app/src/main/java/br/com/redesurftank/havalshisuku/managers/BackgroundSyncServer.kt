package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.NetworkInterface
import java.util.Collections

class BackgroundSyncServer(private val onApplied: () -> Unit) {
    private val TAG = "BackgroundSyncServer"
    private var serverSocket: ServerSocket? = null
    private var running = false
    private var thread: Thread? = null

    companion object {
        fun getLocalIpAddress(): String? {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress) {
                            val sAddr = addr.hostAddress ?: ""
                            val isIPv4 = sAddr.indexOf(':') < 0
                            if (isIPv4) {
                                return sAddr
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                Log.e("BackgroundSyncServer", "Error getting IP address", ex)
            }
            return null
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
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream())
                
                // Read request line
                val requestLine = reader.readLine() ?: return@Thread
                Log.d(TAG, "HTTP Request: $requestLine")
                
                // Read remaining headers
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.isEmpty()) break
                }

                val parts = requestLine.split(" ")
                if (parts.size < 2) return@Thread
                val path = parts[1]

                if (path.startsWith("/apply")) {
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
                } else {
                    sendResponse(writer, getFormHtml())
                }
                
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling client connection: ${e.message}")
            }
        }.start()
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

    private fun getFormHtml(): String {
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
                        align-items: center;
                        min-height: 100vh;
                        box-sizing: border-box;
                    }
                    .container {
                        background-color: #1A1F26;
                        border-radius: 12px;
                        padding: 24px;
                        width: 100%;
                        max-width: 400px;
                        box-shadow: 0 4px 20px rgba(0,0,0,0.3);
                        border: 1px solid #2D3748;
                    }
                    h2 {
                        margin-top: 0;
                        color: #4A9EFF;
                        text-align: center;
                        font-size: 22px;
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
                    select, input[type="text"] {
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
                    <p>Cole o link da imagem, página web ou vídeo do YouTube para aplicar no cluster de instrumentos.</p>
                    <form action="/apply" method="get">
                        <label for="type">Tipo de Fundo</label>
                        <select name="type" id="type">
                            <option value="IMAGE_URL">Imagem da Web</option>
                            <option value="WEB_URL">Página da Web</option>
                            <option value="YOUTUBE">Vídeo do YouTube</option>
                        </select>
                        
                        <label for="value">Link / URL</label>
                        <input type="text" name="value" id="value" placeholder="Insira o link aqui..." required>
                        
                        <button type="submit">Aplicar no Carro</button>
                    </form>
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
