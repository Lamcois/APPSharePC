package com.appsharepc.scanner.service

import android.content.Context
import com.appsharepc.scanner.data.SmbSettings
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

data class SmbTestResult(
    val success: Boolean,
    val message: String,
    val isWritable: Boolean = false,
    val routeType: String = "",
    val activeIp: String = ""
)

data class ResolvedSmbTarget(
    val settings: SmbSettings,
    val effectiveIp: String,
    val routeType: String
)

data class SmbUploadResult(
    val success: Boolean,
    val message: String,
    val remotePath: String = ""
)

data class SmbDownloadResult(
    val success: Boolean,
    val message: String
)

data class RemoteSmbItem(
    val name: String,
    val relativePath: String = "",
    val size: Long = 0L,
    val isDirectory: Boolean = false,
    val lastModified: Long = 0L
)

data class StreamUploadItem(
    val name: String,
    val size: Long,
    val openInputStream: () -> InputStream?
)

object SmbManager {

    private var cachedTarget: Pair<Long, ResolvedSmbTarget>? = null

    fun invalidateCache() {
        cachedTarget = null
    }

    suspend fun resolveEffectiveTarget(
        settings: SmbSettings,
        forceRefresh: Boolean = false
    ): ResolvedSmbTarget = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cachedTarget?.let { (time, target) ->
                if (now - time < 30_000L &&
                    (target.effectiveIp == settings.lanIp.trim() ||
                     target.effectiveIp == settings.tailscaleIp.trim() ||
                     target.effectiveIp == settings.pcIp.trim())) {
                    // Chỉ dùng lại IP/tuyến đã dò; luôn lấy share/tài khoản từ settings hiện tại
                    // (2 hồ sơ cùng IP nhưng khác thư mục chia sẻ không được dùng nhầm cấu hình của nhau)
                    return@withContext target.copy(settings = settings.copy(pcIp = target.effectiveIp))
                }
            }
        }

        val lan = settings.lanIp.trim()
        val ts = settings.tailscaleIp.trim()

        // 1. Thử IP LAN trước với timeout nhanh 1500ms (ưu tiên tốc độ cao khi chung WiFi)
        if (lan.isNotBlank()) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(lan, settings.port), 1500)
                }
                val resolved = ResolvedSmbTarget(
                    settings = settings.copy(pcIp = lan),
                    effectiveIp = lan,
                    routeType = "LAN (WiFi)"
                )
                cachedTarget = Pair(now, resolved)
                return@withContext resolved
            } catch (_: Exception) {
                // LAN không phản hồi (ví dụ ở ngoài dùng 4G) -> chuyển sang thử Tailscale
            }
        }

        // 2. Thử IP Tailscale với timeout 2500ms
        if (ts.isNotBlank()) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ts, settings.port), 2500)
                }
                val resolved = ResolvedSmbTarget(
                    settings = settings.copy(pcIp = ts),
                    effectiveIp = ts,
                    routeType = "Tailscale (Từ xa)"
                )
                cachedTarget = Pair(now, resolved)
                return@withContext resolved
            } catch (_: Exception) {
                // Tailscale không phản hồi
            }
        }

        // 3. Fallback về IP chính nếu chưa kết nối được để hiển thị lỗi chuẩn đoán
        val fallbackIp = settings.primaryIp
        val route = when {
            fallbackIp == lan && lan.isNotBlank() -> "LAN (WiFi)"
            fallbackIp == ts && ts.isNotBlank() -> "Tailscale (Từ xa)"
            else -> "Mặc định"
        }
        val resolved = ResolvedSmbTarget(
            settings = settings.copy(pcIp = fallbackIp),
            effectiveIp = fallbackIp,
            routeType = route
        )
        return@withContext resolved
    }

    private fun createSmbConfig(): SmbConfig {
        return SmbConfig.builder()
            .withTimeout(30, TimeUnit.SECONDS)
            .withSoTimeout(60, TimeUnit.SECONDS)
            .withMultiProtocolNegotiate(true)
            .build()
    }

    private fun createAuthContext(settings: SmbSettings): AuthenticationContext {
        return if (settings.username.isBlank()) {
            AuthenticationContext.anonymous()
        } else {
            val domain = if (settings.domain.isNotBlank()) settings.domain else null
            AuthenticationContext(settings.username, settings.password.toCharArray(), domain)
        }
    }

    suspend fun testConnection(settings: SmbSettings): SmbTestResult = withContext(Dispatchers.IO) {
        if (!settings.isValid) {
            return@withContext SmbTestResult(false, "Vui lòng nhập Địa chỉ IP và Tên thư mục chia sẻ trên máy tính")
        }

        val lan = settings.lanIp.trim()
        val ts = settings.tailscaleIp.trim()

        // Nếu cấu hình cả 2 IP (LAN & Tailscale): Kiểm tra cả 2 và báo cáo kết quả chi tiết từng IP
        if (lan.isNotBlank() && ts.isNotBlank()) {
            val lanTest = testSingleHost(settings.copy(pcIp = lan, lanIp = lan, tailscaleIp = ""), "LAN (WiFi)")
            val tsTest = testSingleHost(settings.copy(pcIp = ts, lanIp = "", tailscaleIp = ts), "Tailscale (Từ xa)")

            val anySuccess = lanTest.success || tsTest.success
            val statusLines = mutableListOf<String>()
            if (lanTest.success) {
                statusLines.add("• 🟢 IP LAN ($lan): Kết nối OK (Tốc độ cao)")
            } else {
                statusLines.add("• 🔴 IP LAN ($lan): Không phản hồi")
            }
            if (tsTest.success) {
                statusLines.add("• 🟢 IP Tailscale ($ts): Kết nối OK (Từ xa)")
            } else {
                statusLines.add("• 🔴 IP Tailscale ($ts): Không phản hồi")
            }

            val summaryMsg = if (anySuccess) {
                "Kết quả kiểm tra kết nối PC:\n" + statusLines.joinToString("\n") +
                        "\nĐã kiểm tra quyền GHI vào: ${settings.shareName}"
            } else {
                "Không thể kết nối đến PC qua cả 2 IP:\n" + statusLines.joinToString("\n")
            }

            val chosenRoute = if (lanTest.success) "LAN (WiFi)" else if (tsTest.success) "Tailscale (Từ xa)" else "Mất kết nối"
            val chosenIp = if (lanTest.success) lan else ts

            return@withContext SmbTestResult(
                success = anySuccess,
                message = summaryMsg,
                isWritable = lanTest.isWritable || tsTest.isWritable,
                routeType = chosenRoute,
                activeIp = chosenIp
            )
        }

        // Trường hợp cấu hình 1 IP đơn
        return@withContext testSingleHost(settings)
    }

    suspend fun testSingleHost(settings: SmbSettings, forcedRoute: String = ""): SmbTestResult = withContext(Dispatchers.IO) {
        val resolved = resolveEffectiveTarget(settings, forceRefresh = true)
        val effectiveSettings = resolved.settings
        val targetIp = effectiveSettings.pcIp.trim()
        val targetShare = effectiveSettings.shareName.trim().trim('/', '\\')
        val routeLabel = if (forcedRoute.isNotBlank()) forcedRoute else resolved.routeType

        if (targetIp.isBlank()) {
            return@withContext SmbTestResult(false, "Vui lòng nhập Địa chỉ IP hoặc Tên máy tính (VD: 192.168.1.100 hoặc IP Tailscale)")
        }
        if (targetShare.isBlank()) {
            return@withContext SmbTestResult(false, "Vui lòng nhập Tên thư mục chia sẻ (Share name) trên máy tính")
        }

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(targetIp, effectiveSettings.port), 4000)
            }
        } catch (e: Exception) {
            // Kiểm tra thông minh: Nếu cổng 445 đóng nhưng cổng 8999 mở, đây là ĐIỆN THOẠI chạy APPSharePC
            val isP2pPhone = try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(targetIp, P2PTransferManager.DEFAULT_PORT), 1500)
                    true
                }
            } catch (_: Exception) { false }

            if (isP2pPhone) {
                return@withContext SmbTestResult(
                    false,
                    "💡 PHÁT HIỆN ĐÂY LÀ ĐIỆN THOẠI chạy APPSharePC (Cổng 8999)!\n" +
                            "• Điện thoại không chạy máy chủ Windows SMB (không có thư mục chia sẻ).\n" +
                            "• Để nối và gửi tệp sang điện thoại này, vui lòng vào Tab 'Chuyển P2P' ➔ chọn 'ĐT ⇄ ĐT'!",
                    routeType = routeLabel,
                    activeIp = targetIp
                )
            }

            val errorDetail = "${e.javaClass.simpleName}: ${e.localizedMessage ?: e.message}"
            return@withContext SmbTestResult(
                false,
                "Không thể kết nối đến $targetIp trên cổng ${effectiveSettings.port} (Tuyến: $routeLabel)!\n" +
                        "Chi tiết: $errorDetail",
                routeType = routeLabel,
                activeIp = targetIp
            )
        }

        val client = SMBClient(createSmbConfig())
        var connection: Connection? = null
        var session: Session? = null
        var share: DiskShare? = null

        return@withContext try {
            connection = client.connect(targetIp, effectiveSettings.port)

            try {
                val auth = createAuthContext(effectiveSettings)
                session = connection.authenticate(auth)
            } catch (authEx: Exception) {
                return@withContext SmbTestResult(
                    false,
                    "Xác thực thất bại với máy tính '$targetIp'.\n" +
                            "Tài khoản hoặc mật khẩu Windows không đúng.\n" +
                            "Chi tiết: ${authEx.localizedMessage}",
                    routeType = routeLabel,
                    activeIp = targetIp
                )
            }

            try {
                share = session.connectShare(targetShare) as? DiskShare
                    ?: return@withContext SmbTestResult(
                        false,
                        "Mục '$targetShare' không phải là thư mục chia sẻ ổ đĩa (Disk Share).",
                        routeType = routeLabel,
                        activeIp = targetIp
                    )
            } catch (shareEx: Exception) {
                val isP2pPhone = try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(targetIp, P2PTransferManager.DEFAULT_PORT), 1500)
                        true
                    }
                } catch (_: Exception) { false }

                val extraTip = if (isP2pPhone) {
                    "\n\n💡 LƯU Ý: IP này là một ĐIỆN THOẠI chạy APPSharePC!\n" +
                            "Điện thoại không có thư mục chia sẻ Windows SMB. Hãy vào Tab 'Chuyển P2P ➔ ĐT ⇄ ĐT' để gửi nhận tệp trực tiếp."
                } else {
                    "\nHãy kiểm tra lại tên chia sẻ (Share name) trong thẻ Sharing của thư mục trên PC."
                }

                return@withContext SmbTestResult(
                    false,
                    "Không tìm thấy thư mục chia sẻ '$targetShare' trên máy tính.$extraTip",
                    routeType = routeLabel,
                    activeIp = targetIp
                )
            }

            val testFileName = ".smb_test_permission_${System.currentTimeMillis()}.tmp"
            val subFolderClean = effectiveSettings.subFolder.trim().trim('/', '\\').replace('/', '\\')

            if (subFolderClean.isNotBlank()) {
                ensureDirectories(share, subFolderClean)
            }

            val remoteTestPath = if (subFolderClean.isBlank()) testFileName else "$subFolderClean\\$testFileName"

            try {
                val smbFile = share.openFile(
                    remoteTestPath,
                    setOf(AccessMask.GENERIC_WRITE, AccessMask.DELETE),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    null
                )
                smbFile.close()

                try {
                    share.rm(remoteTestPath)
                } catch (_: Exception) {}

                SmbTestResult(
                    success = true,
                    message = "Kết nối thành công qua $routeLabel ($targetIp)!\n" +
                            "Đã truy cập và kiểm tra quyền GHI vào: ${effectiveSettings.getTargetPathDescription(targetIp)}",
                    isWritable = true,
                    routeType = routeLabel,
                    activeIp = targetIp
                )
            } catch (writeEx: Exception) {
                SmbTestResult(
                    success = false,
                    message = "Kết nối được tới PC qua $routeLabel ($targetIp) nhưng thư mục '$targetShare' chưa có quyền GHI!\n" +
                            "Chi tiết: ${writeEx.localizedMessage}",
                    isWritable = false,
                    routeType = routeLabel,
                    activeIp = targetIp
                )
            }
        } catch (e: Exception) {
            SmbTestResult(false, "Lỗi kết nối SMB ($targetIp): ${e.localizedMessage}", routeType = routeLabel, activeIp = targetIp)
        } finally {
            try { share?.close() } catch (_: Exception) {}
            try { session?.close() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Tải lên danh sách các tệp qua Stream trực tiếp (hỗ trợ cả tệp con trong cây thư mục).
     */
    suspend fun uploadStreams(
        settings: SmbSettings,
        itemsToUpload: List<StreamUploadItem>,
        onProgress: ((currentFile: String, bytesWritten: Long, totalBytes: Long, percent: Int) -> Unit)? = null
    ): SmbUploadResult = withContext(Dispatchers.IO) {
        if (!settings.isValid) {
            return@withContext SmbUploadResult(false, "Cấu hình IP hoặc Tên thư mục chia sẻ chưa hợp lệ")
        }

        val resolved = resolveEffectiveTarget(settings)
        val effectiveSettings = resolved.settings
        val targetIp = effectiveSettings.pcIp.trim()

        val client = SMBClient(createSmbConfig())
        var connection: Connection? = null
        var session: Session? = null
        var share: DiskShare? = null

        return@withContext try {
            connection = client.connect(targetIp, effectiveSettings.port)
            val auth = createAuthContext(effectiveSettings)
            session = connection.authenticate(auth)
            val shareNameClean = effectiveSettings.shareName.trim().trim('/', '\\')
            share = session.connectShare(shareNameClean) as? DiskShare
                ?: return@withContext SmbUploadResult(false, "Không thể mở thư mục chia sẻ '$shareNameClean'")

            val subFolderClean = effectiveSettings.subFolder.trim().trim('/', '\\').replace('/', '\\')
            if (subFolderClean.isNotBlank()) {
                ensureDirectories(share, subFolderClean)
            }

            for (item in itemsToUpload) {
                val cleanItemName = item.name.replace('/', '\\').trimStart('\\')
                val remoteFilePath = if (subFolderClean.isBlank()) {
                    cleanItemName
                } else {
                    "$subFolderClean\\$cleanItemName"
                }

                val parentDir = remoteFilePath.substringBeforeLast('\\', "")
                if (parentDir.isNotBlank()) {
                    ensureDirectories(share, parentDir)
                }

                val smbFile = share.openFile(
                    remoteFilePath,
                    setOf(AccessMask.GENERIC_WRITE, AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    null
                )

                val fileSize = item.size.coerceAtLeast(1L)
                var totalBytesWritten = 0L

                val inputStream = item.openInputStream()
                    ?: throw IllegalStateException("Không thể đọc tệp ${item.name}")

                smbFile.outputStream.use { out ->
                    inputStream.use { input ->
                        val buffer = ByteArray(256 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                            totalBytesWritten += bytesRead.toLong()
                            val percent = ((totalBytesWritten * 100L) / fileSize).toInt().coerceIn(0, 100)
                            onProgress?.invoke(cleanItemName, totalBytesWritten, fileSize, percent)
                        }
                        out.flush()
                    }
                }
                smbFile.close()
            }

            val targetPath = effectiveSettings.getTargetPathDescription(targetIp)

            SmbUploadResult(
                success = true,
                message = "Đã gửi thành công ${itemsToUpload.size} tệp sang máy tính (${resolved.routeType})!\nVị trí lưu: $targetPath",
                remotePath = targetPath
            )
        } catch (e: Exception) {
            SmbUploadResult(false, "Lỗi trong quá trình gửi file: ${e.localizedMessage}")
        } finally {
            try { share?.close() } catch (_: Exception) {}
            try { session?.close() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
        }
    }

    suspend fun uploadFiles(
        settings: SmbSettings,
        filesToUpload: List<File>,
        onProgress: ((currentFile: String, percent: Int) -> Unit)? = null
    ): SmbUploadResult {
        val streamItems = filesToUpload.map { file ->
            StreamUploadItem(
                name = file.name,
                size = file.length(),
                openInputStream = { FileInputStream(file) }
            )
        }
        return uploadStreams(settings, streamItems) { name, _, _, percent ->
            onProgress?.invoke(name, percent)
        }
    }

    /**
     * Liệt kê danh sách các tệp và thư mục đang có trên PC (hỗ trợ thư mục con).
     */
    suspend fun listRemoteFiles(settings: SmbSettings, currentSubPath: String = ""): Result<List<RemoteSmbItem>> = withContext(Dispatchers.IO) {
        if (!settings.isValid) {
            return@withContext Result.failure(IllegalStateException("Cấu hình PC chưa hợp lệ"))
        }

        val resolved = resolveEffectiveTarget(settings)
        val effectiveSettings = resolved.settings
        val targetIp = effectiveSettings.pcIp.trim()

        val client = SMBClient(createSmbConfig())
        var connection: Connection? = null
        var session: Session? = null
        var share: DiskShare? = null

        try {
            connection = client.connect(targetIp, effectiveSettings.port)
            val auth = createAuthContext(effectiveSettings)
            session = connection.authenticate(auth)
            val shareNameClean = effectiveSettings.shareName.trim().trim('/', '\\')
            share = session.connectShare(shareNameClean) as? DiskShare
                ?: return@withContext Result.failure(IllegalStateException("Không thể mở thư mục chia sẻ '$shareNameClean'"))

            val baseSubFolder = effectiveSettings.subFolder.trim().trim('/', '\\').replace('/', '\\')
            val cleanCurrentSub = currentSubPath.trim('/', '\\').replace('/', '\\')

            val fullQueryPath = when {
                baseSubFolder.isBlank() -> cleanCurrentSub
                cleanCurrentSub.isBlank() -> baseSubFolder
                else -> "$baseSubFolder\\$cleanCurrentSub"
            }

            val fileInfos = share.list(fullQueryPath)

            val resultList = mutableListOf<RemoteSmbItem>()
            for (info in fileInfos) {
                val fileName = info.fileName
                if (fileName == "." || fileName == "..") continue

                val isDirectory = (info.fileAttributes and 0x10L) != 0L
                val size = if (isDirectory) 0L else info.endOfFile
                val lastModified = try {
                    info.lastWriteTime.toEpochMillis()
                } catch (_: Exception) {
                    0L
                }

                val childRelative = if (cleanCurrentSub.isBlank()) fileName else "$cleanCurrentSub\\$fileName"

                resultList.add(
                    RemoteSmbItem(
                        name = fileName,
                        relativePath = childRelative,
                        size = size,
                        isDirectory = isDirectory,
                        lastModified = lastModified
                    )
                )
            }

            // Sắp xếp: Thư mục lên đầu, sau đó xếp theo ngày sửa đổi mới nhất
            val sortedList = resultList.sortedWith(
                compareBy<RemoteSmbItem> { !it.isDirectory }
                    .thenByDescending { it.lastModified }
            )

            Result.success(sortedList)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { share?.close() } catch (_: Exception) {}
            try { session?.close() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Tải một tệp từ PC về điện thoại.
     */
    suspend fun downloadRemoteFile(
        settings: SmbSettings,
        remoteFileName: String,
        outputStream: OutputStream,
        fileSize: Long,
        onProgress: ((bytesRead: Long, totalBytes: Long, percent: Int) -> Unit)? = null
    ): SmbDownloadResult = withContext(Dispatchers.IO) {
        if (!settings.isValid) {
            return@withContext SmbDownloadResult(false, "Cấu hình PC chưa hợp lệ")
        }

        val resolved = resolveEffectiveTarget(settings)
        val effectiveSettings = resolved.settings
        val targetIp = effectiveSettings.pcIp.trim()

        val client = SMBClient(createSmbConfig())
        var connection: Connection? = null
        var session: Session? = null
        var share: DiskShare? = null

        try {
            connection = client.connect(targetIp, effectiveSettings.port)
            val auth = createAuthContext(effectiveSettings)
            session = connection.authenticate(auth)
            val shareNameClean = effectiveSettings.shareName.trim().trim('/', '\\')
            share = session.connectShare(shareNameClean) as? DiskShare
                ?: return@withContext SmbDownloadResult(false, "Không thể mở thư mục chia sẻ '$shareNameClean'")

            val subFolderClean = effectiveSettings.subFolder.trim().trim('/', '\\').replace('/', '\\')
            val cleanRemoteName = remoteFileName.replace('/', '\\').trim('\\')
            val remotePath = if (subFolderClean.isBlank()) cleanRemoteName else "$subFolderClean\\$cleanRemoteName"

            val smbFile = share.openFile(
                remotePath,
                setOf(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )

            val totalExpected = fileSize.coerceAtLeast(1L)
            var totalRead = 0L

            outputStream.use { out ->
                smbFile.inputStream.use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        totalRead += bytesRead.toLong()
                        val percent = ((totalRead * 100L) / totalExpected).toInt().coerceIn(0, 100)
                        onProgress?.invoke(totalRead, totalExpected, percent)
                    }
                    out.flush()
                }
            }
            smbFile.close()

            SmbDownloadResult(true, "Đã tải '$cleanRemoteName' về thư mục Download của điện thoại (${resolved.routeType})!")
        } catch (e: Exception) {
            SmbDownloadResult(false, "Lỗi khi tải file: ${e.localizedMessage}")
        } finally {
            try { share?.close() } catch (_: Exception) {}
            try { session?.close() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Tải TOÀN BỘ một thư mục từ PC về điện thoại, quét đệ quy mọi file và thư mục con.
     */
    suspend fun downloadRemoteFolder(
        settings: SmbSettings,
        folderRelativePath: String,
        folderName: String,
        context: Context,
        onProgress: ((currentFile: String, fileIndex: Int, totalFiles: Int, percent: Int) -> Unit)? = null
    ): SmbDownloadResult = withContext(Dispatchers.IO) {
        if (!settings.isValid) {
            return@withContext SmbDownloadResult(false, "Cấu hình PC chưa hợp lệ")
        }

        val resolved = resolveEffectiveTarget(settings)
        val effectiveSettings = resolved.settings
        val targetIp = effectiveSettings.pcIp.trim()

        val client = SMBClient(createSmbConfig())
        var connection: Connection? = null
        var session: Session? = null
        var share: DiskShare? = null

        try {
            connection = client.connect(targetIp, effectiveSettings.port)
            val auth = createAuthContext(effectiveSettings)
            session = connection.authenticate(auth)
            val shareNameClean = effectiveSettings.shareName.trim().trim('/', '\\')
            share = session.connectShare(shareNameClean) as? DiskShare
                ?: return@withContext SmbDownloadResult(false, "Không thể mở thư mục chia sẻ '$shareNameClean'")

            val baseSubFolder = effectiveSettings.subFolder.trim().trim('/', '\\').replace('/', '\\')
            val cleanRel = folderRelativePath.trim('/', '\\').replace('/', '\\')
            val fullFolderRemotePath = when {
                baseSubFolder.isBlank() -> cleanRel
                cleanRel.isBlank() -> baseSubFolder
                else -> "$baseSubFolder\\$cleanRel"
            }

            // 1. Quét đệ quy tìm tất cả file bên trong thư mục PC
            val filesToDownload = mutableListOf<Pair<String, Long>>()
            fun scanRecursive(currentRemoteDir: String) {
                val infos = share.list(currentRemoteDir)
                for (info in infos) {
                    if (info.fileName == "." || info.fileName == "..") continue
                    val isDir = (info.fileAttributes and 0x10L) != 0L
                    val childPath = if (currentRemoteDir.isBlank()) info.fileName else "$currentRemoteDir\\${info.fileName}"
                    if (isDir) {
                        scanRecursive(childPath)
                    } else {
                        filesToDownload.add(Pair(childPath, info.endOfFile))
                    }
                }
            }

            scanRecursive(fullFolderRemotePath)

            if (filesToDownload.isEmpty()) {
                return@withContext SmbDownloadResult(true, "Thư mục '$folderName' trên PC trống, không có file nào để tải.")
            }

            // 2. Tải lần lượt từng file và ghi vào Download/APPSharePC/<folderName>/...
            val buffer = ByteArray(256 * 1024)
            val totalFiles = filesToDownload.size

            for ((index, fileEntry) in filesToDownload.withIndex()) {
                val (remotePath, fileSize) = fileEntry

                val relativeFromFolder = if (remotePath.startsWith(fullFolderRemotePath)) {
                    remotePath.removePrefix(fullFolderRemotePath).trimStart('\\')
                } else {
                    remotePath.substringAfterLast('\\')
                }
                val localRelativePath = if (relativeFromFolder.isBlank()) {
                    "$folderName\\${remotePath.substringAfterLast('\\')}"
                } else {
                    "$folderName\\$relativeFromFolder"
                }

                val fileName = remotePath.substringAfterLast('\\')
                onProgress?.invoke(fileName, index + 1, totalFiles, 0)

                val outputStreamPair = FileUtils.openDownloadOutputStream(context, localRelativePath)
                val outStream = outputStreamPair.first

                try {
                    val smbFile = share.openFile(
                        remotePath,
                        setOf(AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                    )

                    val totalExpected = fileSize.coerceAtLeast(1L)
                    var totalRead = 0L

                    smbFile.use { file ->
                        outStream.use { out ->
                            file.inputStream.use { input ->
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    out.write(buffer, 0, bytesRead)
                                    totalRead += bytesRead.toLong()
                                    val percent = ((totalRead * 100L) / totalExpected).toInt().coerceIn(0, 100)
                                    onProgress?.invoke(fileName, index + 1, totalFiles, percent)
                                }
                                out.flush()
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Không để lại tệp tải dở (IS_PENDING) trong thư mục Download
                    try { outStream.close() } catch (_: Exception) {}
                    FileUtils.abortDownload(context, outputStreamPair.second)
                    throw e
                }
                FileUtils.finishDownload(context, outputStreamPair.second)
            }

            SmbDownloadResult(
                success = true,
                message = "Đã tải thành công toàn bộ thư mục '$folderName' ($totalFiles tệp) về thư mục Download/APPSharePC/$folderName!"
            )
        } catch (e: Exception) {
            SmbDownloadResult(false, "Lỗi khi tải thư mục '$folderName': ${e.localizedMessage}")
        } finally {
            try { share?.close() } catch (_: Exception) {}
            try { session?.close() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Chuyển tệp/thư mục trực tiếp giữa 2 máy tính PC (PC nguồn -> PC đích) qua giao thức SMB.
     * Dữ liệu được truyền trực tiếp qua bộ nhớ đệm Stream 256KB, không tốn bộ nhớ điện thoại.
     */
    suspend fun transferBetweenPcs(
        sourceSettings: SmbSettings,
        targetSettings: SmbSettings,
        itemsToTransfer: List<RemoteSmbItem>,
        targetSubFolder: String = "",
        sourceName: String = "PC Nguồn",
        targetName: String = "PC Đích",
        onProgress: ((currentFile: String, currentIndex: Int, totalFiles: Int, percent: Int) -> Unit)? = null
    ): SmbUploadResult = withContext(Dispatchers.IO) {
        if (!sourceSettings.isValid || !targetSettings.isValid) {
            return@withContext SmbUploadResult(false, "Cấu hình PC nguồn hoặc PC đích chưa hợp lệ.")
        }

        val resolvedSource = resolveEffectiveTarget(sourceSettings)
        val resolvedTarget = resolveEffectiveTarget(targetSettings)

        val clientSource = SMBClient(createSmbConfig())
        val clientTarget = SMBClient(createSmbConfig())

        var sourceConn: Connection? = null
        var sourceSession: Session? = null
        var sourceShare: DiskShare? = null

        var targetConn: Connection? = null
        var targetSession: Session? = null
        var targetShare: DiskShare? = null

        try {
            // Kết nối PC Nguồn
            sourceConn = clientSource.connect(resolvedSource.settings.pcIp.trim(), resolvedSource.settings.port)
            sourceSession = sourceConn.authenticate(createAuthContext(resolvedSource.settings))
            val sourceShareName = resolvedSource.settings.shareName.trim().trim('/', '\\')
            sourceShare = sourceSession.connectShare(sourceShareName) as? DiskShare
                ?: return@withContext SmbUploadResult(false, "Không thể mở thư mục chia sẻ PC nguồn '$sourceShareName'")

            // Kết nối PC Đích
            targetConn = clientTarget.connect(resolvedTarget.settings.pcIp.trim(), resolvedTarget.settings.port)
            targetSession = targetConn.authenticate(createAuthContext(resolvedTarget.settings))
            val targetShareName = resolvedTarget.settings.shareName.trim().trim('/', '\\')
            targetShare = targetSession.connectShare(targetShareName) as? DiskShare
                ?: return@withContext SmbUploadResult(false, "Không thể mở thư mục chia sẻ PC đích '$targetShareName'")

            val sourceBaseSub = resolvedSource.settings.subFolder.trim().trim('/', '\\').replace('/', '\\')
            val targetBaseSub = resolvedTarget.settings.subFolder.trim().trim('/', '\\').replace('/', '\\')
            val effectiveTargetSub = when {
                targetBaseSub.isBlank() -> targetSubFolder.trim().trim('/', '\\').replace('/', '\\')
                targetSubFolder.isBlank() -> targetBaseSub
                else -> "$targetBaseSub\\${targetSubFolder.trim().trim('/', '\\').replace('/', '\\')}"
            }

            if (effectiveTargetSub.isNotBlank()) {
                ensureDirectories(targetShare, effectiveTargetSub)
            }

            // 1. Quét danh sách các file cần chuyển (duyệt đệ quy nếu chọn folder)
            val filesList = mutableListOf<Pair<String, Long>>() // Pair<sourceRelativePath, size>

            fun scanRecursive(currentPath: String) {
                val infos = sourceShare.list(currentPath)
                for (info in infos) {
                    if (info.fileName == "." || info.fileName == "..") continue
                    val isDir = (info.fileAttributes and 0x10L) != 0L
                    val childPath = if (currentPath.isBlank()) info.fileName else "$currentPath\\${info.fileName}"
                    if (isDir) {
                        scanRecursive(childPath)
                    } else {
                        filesList.add(Pair(childPath, info.endOfFile))
                    }
                }
            }

            for (item in itemsToTransfer) {
                val fullItemPath = when {
                    sourceBaseSub.isBlank() -> item.relativePath.ifBlank { item.name }
                    item.relativePath.isBlank() -> "$sourceBaseSub\\${item.name}"
                    item.relativePath.startsWith(sourceBaseSub) -> item.relativePath
                    else -> "$sourceBaseSub\\${item.relativePath}"
                }.trim('\\')

                if (item.isDirectory) {
                    scanRecursive(fullItemPath)
                } else {
                    filesList.add(Pair(fullItemPath, item.size))
                }
            }

            if (filesList.isEmpty()) {
                return@withContext SmbUploadResult(false, "Không có tệp tin nào để chuyển giữa 2 PC.")
            }

            // 2. Truyền từng tệp qua bộ nhớ đệm Stream trực tiếp
            val buffer = ByteArray(256 * 1024)
            val totalFiles = filesList.size

            for ((index, fileEntry) in filesList.withIndex()) {
                val (sourcePath, fileSize) = fileEntry
                val fileName = sourcePath.substringAfterLast('\\')

                val relativePart = if (sourceBaseSub.isNotBlank() && sourcePath.startsWith(sourceBaseSub)) {
                    sourcePath.removePrefix(sourceBaseSub).trimStart('\\')
                } else {
                    sourcePath
                }

                val destPath = if (effectiveTargetSub.isBlank()) relativePart else "$effectiveTargetSub\\$relativePart"
                val destParent = destPath.substringBeforeLast('\\', "")
                if (destParent.isNotBlank()) {
                    ensureDirectories(targetShare, destParent)
                }

                onProgress?.invoke(fileName, index + 1, totalFiles, 0)

                val sourceFile = sourceShare.openFile(
                    sourcePath,
                    setOf(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )

                val targetFile = targetShare.openFile(
                    destPath,
                    setOf(AccessMask.GENERIC_WRITE, AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    null
                )

                var written = 0L
                val totalExpected = fileSize.coerceAtLeast(1L)
                sourceFile.inputStream.use { input ->
                    targetFile.outputStream.use { output ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            written += bytesRead.toLong()
                            val pct = ((written * 100L) / totalExpected).toInt().coerceIn(0, 100)
                            onProgress?.invoke(fileName, index + 1, totalFiles, pct)
                        }
                        output.flush()
                    }
                }
                sourceFile.close()
                targetFile.close()
            }

            val targetDesc = resolvedTarget.settings.getTargetPathDescription(resolvedTarget.effectiveIp)
            SmbUploadResult(
                success = true,
                message = "Đã chuyển thành công $totalFiles tệp từ '$sourceName' sang '$targetName'!\nVị trí lưu: $targetDesc"
            )
        } catch (e: Exception) {
            SmbUploadResult(false, "Lỗi chuyển file giữa 2 PC: ${e.localizedMessage}")
        } finally {
            try { sourceShare?.close() } catch (_: Exception) {}
            try { sourceSession?.close() } catch (_: Exception) {}
            try { sourceConn?.close() } catch (_: Exception) {}

            try { targetShare?.close() } catch (_: Exception) {}
            try { targetSession?.close() } catch (_: Exception) {}
            try { targetConn?.close() } catch (_: Exception) {}
        }
    }

    private fun ensureDirectories(share: DiskShare, path: String) {
        val parts = path.split('\\')
        var current = ""
        for (part in parts) {
            if (part.isBlank()) continue
            current = if (current.isEmpty()) part else "$current\\$part"
            if (!share.folderExists(current)) {
                share.mkdir(current)
            }
        }
    }
}
