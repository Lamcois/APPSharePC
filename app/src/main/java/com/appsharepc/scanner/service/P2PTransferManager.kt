package com.appsharepc.scanner.service

import android.content.Context
import android.net.Uri
import com.appsharepc.scanner.data.PeerDevice
import com.appsharepc.scanner.data.PeerDeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PeerTestResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long = 0L,
    val deviceName: String = "",
    val routeType: String = "", // "WiFi LAN" hoặc "Tailscale"
    val activeIp: String = ""
)

data class DiscoveredPeer(
    val ip: String,
    val port: Int = P2PTransferManager.DEFAULT_PORT,
    val deviceName: String = "Điện thoại",
    val deviceType: PeerDeviceType = PeerDeviceType.PHONE,
    val latencyMs: Long = 0L
)

data class P2pReceivedItem(
    val fileName: String,
    val size: Long,
    val formattedSize: String,
    val timestamp: Long = System.currentTimeMillis(),
    val uri: Uri? = null,
    val isLink: Boolean = false,
    val content: String = ""
)

object P2PTransferManager {

    const val DEFAULT_PORT = 8999

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var isRunning: Boolean = false
        private set

    /**
     * Dò tìm địa chỉ IP của thiết bị hiện tại:
     * - LAN IP: địa chỉ WiFi cục bộ (192.168.x.x hoặc 10.x.x.x)
     * - Tailscale IP: địa chỉ mạng Tailscale (100.x.y.z)
     */
    fun getLocalIpAddresses(): Pair<String?, String?> {
        var lanIp: String? = null
        var tailscaleIp: String? = null

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return Pair(null, null)
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                for (addr in addresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("100.")) {
                            tailscaleIp = host
                        } else if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            lanIp = host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair(lanIp, tailscaleIp)
    }

    /**
     * Khởi động máy chủ HTTP Receiver mini để nhận tệp từ điện thoại khác hoặc từ trình duyệt web.
     */
    fun startReceiver(
        context: Context,
        port: Int = DEFAULT_PORT,
        onReceived: (P2pReceivedItem) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isRunning) return

        try {
            serverSocket = ServerSocket(port).apply {
                reuseAddress = true
            }
            isRunning = true

            serverJob = scope.launch {
                while (isActive && isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch {
                            handleClient(context, clientSocket, onReceived)
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            withContext(Dispatchers.Main) {
                                onError("Lỗi máy chủ nhận tệp: ${e.localizedMessage}")
                            }
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            isRunning = false
            onError("Không thể mở cổng $port: ${e.localizedMessage}")
        }
    }

    /**
     * Dừng máy chủ HTTP Receiver.
     */
    fun stopReceiver() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private suspend fun handleClient(
        context: Context,
        socket: Socket,
        onReceived: (P2pReceivedItem) -> Unit
    ) {
        try {
            val inStream = BufferedInputStream(socket.getInputStream())
            val outStream = BufferedOutputStream(socket.getOutputStream())

            // Đọc dòng Request-Line
            val requestLine = readLine(inStream) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val rawPath = parts[1]
            val path = if (rawPath.contains('?')) rawPath.substringBefore('?') else rawPath

            // Đọc Headers
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(inStream) ?: break
                if (line.isEmpty()) break
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L

            when {
                method == "GET" && (path == "/" || path == "/index.html") -> {
                    // Trả về giao diện Web HTML upload thân thiện
                    val html = getWebUiHtml()
                    val bytes = html.toByteArray(Charsets.UTF_8)
                    val response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                    outStream.write(response.toByteArray(Charsets.UTF_8))
                    outStream.write(bytes)
                    outStream.flush()
                }

                method == "POST" && path == "/upload" -> {
                    val encodedName = headers["x-file-name"] ?: "received_${System.currentTimeMillis()}"
                    val cleanFileName = try {
                        URLDecoder.decode(encodedName, "UTF-8")
                    } catch (_: Exception) {
                        encodedName
                    }

                    val (targetOutStream, uri) = FileUtils.openDownloadOutputStream(context, cleanFileName)
                    var totalRead = 0L
                    val buffer = ByteArray(64 * 1024)

                    try {
                        targetOutStream.use { out ->
                            var bytesToRead = contentLength
                            while (bytesToRead > 0L) {
                                val toReadNow = bytesToRead.coerceAtMost(buffer.size.toLong()).toInt()
                                val read = inStream.read(buffer, 0, toReadNow)
                                if (read == -1) break
                                out.write(buffer, 0, read)
                                totalRead += read
                                bytesToRead -= read
                            }
                            out.flush()
                        }
                        FileUtils.finishDownload(context, uri)

                        val item = P2pReceivedItem(
                            fileName = cleanFileName,
                            size = totalRead,
                            formattedSize = FileUtils.formatFileSize(totalRead),
                            uri = uri
                        )
                        withContext(Dispatchers.Main) {
                            onReceived(item)
                        }

                        val resp = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/plain; charset=utf-8\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Content-Length: 2\r\n" +
                                "Connection: close\r\n\r\nOK"
                        outStream.write(resp.toByteArray(Charsets.UTF_8))
                        outStream.flush()
                    } catch (e: Exception) {
                        sendError(outStream, 500, "Lỗi ghi file: ${e.localizedMessage}")
                    }
                }

                method == "POST" && path == "/link" -> {
                    val linkBytes = ByteArray(contentLength.coerceAtMost(1024 * 1024).toInt())
                    var totalRead = 0
                    while (totalRead < linkBytes.size) {
                        val read = inStream.read(linkBytes, totalRead, linkBytes.size - totalRead)
                        if (read == -1) break
                        totalRead += read
                    }
                    val linkText = String(linkBytes, 0, totalRead, Charsets.UTF_8).trim()

                    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    val linkFileName = "Link_${sdf.format(Date())}.txt"

                    val (targetOutStream, uri) = FileUtils.openDownloadOutputStream(context, linkFileName)
                    targetOutStream.use { out ->
                        out.write(linkText.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                    FileUtils.finishDownload(context, uri)

                    val item = P2pReceivedItem(
                        fileName = linkFileName,
                        size = totalRead.toLong(),
                        formattedSize = "${totalRead} B",
                        uri = uri,
                        isLink = true,
                        content = linkText
                    )
                    withContext(Dispatchers.Main) {
                        onReceived(item)
                    }

                    val resp = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain; charset=utf-8\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Length: 2\r\n" +
                            "Connection: close\r\n\r\nOK"
                    outStream.write(resp.toByteArray(Charsets.UTF_8))
                    outStream.flush()
                }

                method == "GET" && (path == "/ping" || path == "/status") -> {
                    val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                    val json = JSONObject().apply {
                        put("status", "ok")
                        put("app", "APPSharePC")
                        put("device", deviceModel)
                        put("port", DEFAULT_PORT)
                    }
                    val jsonBytes = json.toString().toByteArray(Charsets.UTF_8)
                    val resp = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json; charset=utf-8\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Length: ${jsonBytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                    outStream.write(resp.toByteArray(Charsets.UTF_8))
                    outStream.write(jsonBytes)
                    outStream.flush()
                }

                else -> {
                    sendError(outStream, 404, "Not Found")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var c: Int
        while (input.read().also { c = it } != -1) {
            if (c == '\r'.code) {
                // ignore
                continue
            }
            if (c == '\n'.code) {
                return sb.toString()
            }
            sb.append(c.toChar())
        }
        return if (sb.isNotEmpty()) sb.toString() else null
    }

    private fun sendError(out: OutputStream, code: Int, message: String) {
        val body = "Error $code: $message"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val resp = "HTTP/1.1 $code $message\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n$body"
        out.write(resp.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /**
     * Gửi tệp từ điện thoại này sang điện thoại nhận khác (qua IP Tailscale hoặc LAN trên cổng 8999).
     */
    suspend fun sendFilesToPeer(
        targetIp: String,
        port: Int = DEFAULT_PORT,
        items: List<StreamUploadItem>,
        onProgress: ((currentFile: String, index: Int, total: Int, percent: Int) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (targetIp.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Địa chỉ IP máy nhận không được để trống."))
        }
        if (items.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Chưa chọn tệp tin nào để gửi."))
        }

        try {
            val total = items.size
            for ((index, item) in items.withIndex()) {
                val encodedName = URLEncoder.encode(item.name, "UTF-8")
                val url = URL("http://$targetIp:$port/upload")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("X-File-Name", encodedName)
                    setFixedLengthStreamingMode(item.size)
                }

                val inStream = item.openInputStream()
                    ?: return@withContext Result.failure(IllegalStateException("Không thể đọc tệp ${item.name}"))

                val buffer = ByteArray(64 * 1024)
                var written = 0L
                val fileSize = item.size.coerceAtLeast(1L)

                onProgress?.invoke(item.name, index + 1, total, 0)

                conn.outputStream.use { out ->
                    inStream.use { input ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                            written += bytesRead.toLong()
                            val pct = ((written * 100L) / fileSize).toInt().coerceIn(0, 100)
                            onProgress?.invoke(item.name, index + 1, total, pct)
                        }
                        out.flush()
                    }
                }

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                    return@withContext Result.failure(IllegalStateException("Máy nhận báo lỗi khi gửi ${item.name}: $err"))
                }
            }

            Result.success("Đã gửi thành công $total tệp sang điện thoại '$targetIp'!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gửi liên kết sang điện thoại nhận khác (lưu thành .txt).
     */
    suspend fun sendLinkToPeer(
        targetIp: String,
        port: Int = DEFAULT_PORT,
        link: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (targetIp.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Địa chỉ IP máy nhận không được để trống."))
        }
        if (link.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Nội dung liên kết không được để trống."))
        }

        try {
            val url = URL("http://$targetIp:$port/link")
            val bytes = link.trim().toByteArray(Charsets.UTF_8)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                setFixedLengthStreamingMode(bytes.size.toLong())
            }

            conn.outputStream.use { out ->
                out.write(bytes)
                out.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                Result.success("Đã gửi liên kết sang điện thoại '$targetIp' thành công!")
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Result.failure(IllegalStateException("Máy nhận báo lỗi: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Kiểm tra / Thử kết nối tới điện thoại nhận (Ping cổng 8999 & kiểm tra APPSharePC).
     */
    suspend fun testPeerConnection(
        targetIp: String,
        port: Int = DEFAULT_PORT
    ): PeerTestResult = withContext(Dispatchers.IO) {
        val cleanIp = targetIp.trim()
        if (cleanIp.isBlank()) {
            return@withContext PeerTestResult(
                success = false,
                message = "Địa chỉ IP thiết bị không được để trống."
            )
        }

        val isLan = cleanIp.startsWith("192.168.") || cleanIp.startsWith("10.") || cleanIp.startsWith("172.")
        val routeType = if (isLan) "WiFi LAN" else "Tailscale / VPN"
        val startTime = System.currentTimeMillis()

        try {
            val socket = Socket()
            socket.connect(java.net.InetSocketAddress(cleanIp, port), 2500)
            val latency = System.currentTimeMillis() - startTime

            val out = BufferedOutputStream(socket.getOutputStream())
            val inStream = BufferedInputStream(socket.getInputStream())
            val req = "GET /ping HTTP/1.1\r\nHost: $cleanIp\r\nConnection: close\r\n\r\n"
            out.write(req.toByteArray(Charsets.UTF_8))
            out.flush()

            val respLine = readLine(inStream) ?: ""
            var deviceName = ""

            if (respLine.contains("200")) {
                while (true) {
                    val h = readLine(inStream) ?: break
                    if (h.isEmpty()) break
                }
                val bodyBytes = ByteArray(1024)
                val read = inStream.read(bodyBytes)
                if (read > 0) {
                    val bodyStr = String(bodyBytes, 0, read, Charsets.UTF_8)
                    try {
                        val json = JSONObject(bodyStr)
                        deviceName = json.optString("device", "")
                    } catch (_: Exception) {}
                }
            }
            socket.close()

            val devText = if (deviceName.isNotBlank()) " ($deviceName)" else ""
            PeerTestResult(
                success = true,
                message = "Kết nối ĐT thành công qua $routeType$devText! Độ trễ: ${latency}ms",
                latencyMs = latency,
                deviceName = deviceName,
                routeType = routeType,
                activeIp = cleanIp
            )
        } catch (e: Exception) {
            val err = when {
                e is java.net.SocketTimeoutException -> "Hết thời gian chờ (Timeout). ĐT đích chưa mở APPSharePC hoặc khác mạng."
                e is java.net.ConnectException -> "Không thể kết nối tới cổng $port. Hãy chắc chắn ĐT nhận đã GẠT BẬT 'Chế độ Nhận tệp'."
                else -> e.localizedMessage ?: "Lỗi kết nối: ${e.javaClass.simpleName}"
            }
            PeerTestResult(
                success = false,
                message = err,
                routeType = routeType,
                activeIp = cleanIp
            )
        }
    }

    /**
     * Tự động dò tuyến tối ưu giữa IP LAN (WiFi siêu tốc) và IP Tailscale cho ĐT/máy tính bảng.
     */
    suspend fun resolveEffectivePeerIp(
        device: PeerDevice,
        port: Int = DEFAULT_PORT
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val lan = device.lanIp.trim()
        val ts = device.tailscaleIp.trim()
        val fallback = device.ip.trim()

        if (lan.isNotBlank()) {
            try {
                val s = Socket()
                s.connect(java.net.InetSocketAddress(lan, port), 400)
                s.close()
                return@withContext Pair(lan, "WiFi LAN")
            } catch (_: Exception) {}
        }

        if (ts.isNotBlank()) {
            try {
                val s = Socket()
                s.connect(java.net.InetSocketAddress(ts, port), 700)
                s.close()
                return@withContext Pair(ts, "Tailscale")
            } catch (_: Exception) {}
        }

        val chosen = when {
            lan.isNotBlank() -> lan
            ts.isNotBlank() -> ts
            else -> fallback
        }
        val isLan = chosen.startsWith("192.168.") || chosen.startsWith("10.") || chosen.startsWith("172.")
        Pair(chosen, if (isLan) "WiFi LAN" else "Tailscale")
    }

    /**
     * Quét nhanh toàn bộ mạng WiFi nội bộ để tìm các thiết bị đang mở APPSharePC trên cổng 8999.
     */
    suspend fun discoverPeersOnLan(
        port: Int = DEFAULT_PORT
    ): List<DiscoveredPeer> = withContext(Dispatchers.IO) {
        val (lanIp, _) = getLocalIpAddresses()
        if (lanIp.isNullOrBlank()) return@withContext emptyList()

        val lastDot = lanIp.lastIndexOf('.')
        if (lastDot <= 0) return@withContext emptyList()

        val prefix = lanIp.substring(0, lastDot + 1)
        val myHost = lanIp.substring(lastDot + 1).toIntOrNull() ?: -1

        val found = java.util.concurrent.CopyOnWriteArrayList<DiscoveredPeer>()

        coroutineScope {
            (1..254).filter { it != myHost }.map { i ->
                async {
                    val ip = "$prefix$i"
                    try {
                        val start = System.currentTimeMillis()
                        val socket = Socket()
                        socket.connect(java.net.InetSocketAddress(ip, port), 250)
                        val latency = System.currentTimeMillis() - start

                        val out = BufferedOutputStream(socket.getOutputStream())
                        val inStream = BufferedInputStream(socket.getInputStream())
                        val req = "GET /ping HTTP/1.1\r\nHost: $ip\r\nConnection: close\r\n\r\n"
                        out.write(req.toByteArray(Charsets.UTF_8))
                        out.flush()

                        val respLine = readLine(inStream) ?: ""
                        var deviceName = "Điện thoại $ip"
                        if (respLine.contains("200")) {
                            while (true) {
                                val h = readLine(inStream) ?: break
                                if (h.isEmpty()) break
                            }
                            val buf = ByteArray(512)
                            val r = inStream.read(buf)
                            if (r > 0) {
                                val jsonStr = String(buf, 0, r, Charsets.UTF_8)
                                try {
                                    val json = JSONObject(jsonStr)
                                    val d = json.optString("device", "")
                                    if (d.isNotBlank()) deviceName = d
                                } catch (_: Exception) {}
                            }
                        }
                        socket.close()

                        val type = if (deviceName.contains("Tab", ignoreCase = true) ||
                            deviceName.contains("Pad", ignoreCase = true)) {
                            PeerDeviceType.TABLET
                        } else {
                            PeerDeviceType.PHONE
                        }

                        found.add(
                            DiscoveredPeer(
                                ip = ip,
                                port = port,
                                deviceName = deviceName,
                                deviceType = type,
                                latencyMs = latency
                            )
                        )
                    } catch (_: Exception) {
                        // Offline
                    }
                }
            }.awaitAll()
        }

        found.sortedBy { it.latencyMs }
    }

    private fun getWebUiHtml(): String {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>APPSharePC - Tiếp Nhận Tệp P2P</title>
              <style>
                * { box-sizing: border-box; }
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 20px; display: flex; justify-content: center; align-items: center; min-height: 100vh; }
                .card { background: #1e293b; border-radius: 20px; padding: 28px; width: 100%; max-width: 480px; box-shadow: 0 10px 25px -5px rgba(0,0,0,0.5); border: 1px solid #334155; text-align: center; }
                h2 { color: #38bdf8; margin: 0 0 8px 0; font-size: 22px; }
                p.sub { color: #94a3b8; font-size: 14px; margin-bottom: 24px; line-height: 1.4; }
                .section-title { font-weight: bold; font-size: 14px; text-align: left; color: #cbd5e1; margin-bottom: 8px; }
                input[type=file], textarea { width: 100%; padding: 12px; margin-bottom: 12px; border-radius: 10px; border: 1px solid #475569; background: #0f172a; color: #f8fafc; font-size: 14px; outline: none; }
                input[type=file]:focus, textarea:focus { border-color: #38bdf8; }
                button { background: #2563eb; color: #ffffff; border: none; padding: 14px; border-radius: 10px; font-weight: bold; cursor: pointer; width: 100%; font-size: 15px; transition: background 0.2s; }
                button:hover { background: #1d4ed8; }
                button.green { background: #059669; }
                button.green:hover { background: #047857; }
                hr { border: none; border-top: 1px solid #334155; margin: 24px 0; }
                #status { margin-top: 16px; font-size: 14px; min-height: 20px; word-break: break-word; font-weight: 500; }
                .success { color: #34d399; }
                .error { color: #f87171; }
                .info { color: #38bdf8; }
              </style>
            </head>
            <body>
              <div class="card">
                <h2>📲 APPSharePC - Tiếp Nhận Tệp</h2>
                <p class="sub">Gửi tệp & liên kết trực tiếp sang điện thoại này qua Tailscale hoặc WiFi mạng nội bộ</p>
                
                <div class="section-title">📁 Chọn tệp gửi sang máy:</div>
                <input type="file" id="fileInput" multiple>
                <button onclick="uploadFiles()">GỬI TỆP SANG ĐIỆN THOẠI</button>

                <hr>

                <div class="section-title">🔗 Hoặc dán liên kết (Link / URL):</div>
                <textarea id="linkInput" rows="3" placeholder="https://... hoặc đoạn ghi chú cần gửi"></textarea>
                <button class="green" onclick="sendLink()">GỬI LINK SANG ĐIỆN THOẠI (.TXT)</button>

                <div id="status"></div>
              </div>

              <script>
                async function uploadFiles() {
                  const input = document.getElementById('fileInput');
                  const files = input.files;
                  if (!files.length) return alert('Vui lòng chọn ít nhất một tệp!');
                  const status = document.getElementById('status');
                  status.className = 'info';
                  
                  try {
                    for (let i = 0; i < files.length; i++) {
                      const f = files[i];
                      status.innerText = `Đang tải lên ${'$'}{f.name} (${'$'}{i+1}/${'$'}{files.length})...`;
                      const res = await fetch('/upload', {
                        method: 'POST',
                        headers: { 'X-File-Name': encodeURIComponent(f.name) },
                        body: f
                      });
                      if (!res.ok) throw new Error(`Lỗi HTTP ${'$'}{res.status}`);
                    }
                    status.className = 'success';
                    status.innerText = '✅ Đã gửi thành công toàn bộ tệp sang điện thoại!';
                    input.value = '';
                  } catch (e) {
                    status.className = 'error';
                    status.innerText = '❌ Gửi tệp thất bại: ' + e.message;
                  }
                }

                async function sendLink() {
                  const link = document.getElementById('linkInput').value.trim();
                  if (!link) return alert('Vui lòng nhập hoặc dán liên kết!');
                  const status = document.getElementById('status');
                  status.className = 'info';
                  status.innerText = 'Đang gửi liên kết...';
                  try {
                    const res = await fetch('/link', {
                      method: 'POST',
                      headers: { 'Content-Type': 'text/plain; charset=utf-8' },
                      body: link
                    });
                    if (!res.ok) throw new Error(`Lỗi HTTP ${'$'}{res.status}`);
                    status.className = 'success';
                    status.innerText = '✅ Đã gửi liên kết thành công sang điện thoại!';
                    document.getElementById('linkInput').value = '';
                  } catch (e) {
                    status.className = 'error';
                    status.innerText = '❌ Gửi liên kết thất bại: ' + e.message;
                  }
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
