package com.appsharepc.scanner.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appsharepc.scanner.data.*
import com.appsharepc.scanner.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val label: String, val extension: String, val description: String) {
    DOCX("Word (.docx)", ".docx", "Gom nhiều ảnh vào 1 file Word chuẩn A4"),
    PDF("PDF (.pdf)", ".pdf", "Gom nhiều ảnh thành 1 file scan PDF sắc nét"),
    JPG("Ảnh đơn (.jpg)", ".jpg", "Lưu trực tiếp file ảnh JPG vào máy tính")
}

data class SelectedUploadItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val formattedSize: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmbSettingsRepository(application)

    // Tab Index: 0: Quét tài liệu, 1: Gửi tệp & Link, 2: Tệp trên PC, 3: Chuyển P2P (PC ⇄ PC / ĐT ⇄ ĐT)
    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    // Profiles
    private val _profiles = MutableStateFlow<List<PcProfile>>(repository.loadProfiles())
    val profiles: StateFlow<List<PcProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<PcProfile>(repository.getActiveProfile())
    val activeProfile: StateFlow<PcProfile> = _activeProfile.asStateFlow()

    private val _smbSettings = MutableStateFlow<SmbSettings>(_activeProfile.value.settings)
    val smbSettings: StateFlow<SmbSettings> = _smbSettings.asStateFlow()

    // TAB 1: Quét tài liệu
    private val _capturedPhotos = MutableStateFlow<List<File>>(emptyList())
    val capturedPhotos: StateFlow<List<File>> = _capturedPhotos.asStateFlow()

    private val _exportFormat = MutableStateFlow(ExportFormat.DOCX)
    val exportFormat: StateFlow<ExportFormat> = _exportFormat.asStateFlow()

    private val _fileName = MutableStateFlow(createDefaultFileName())
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    // TAB 2: Gửi tệp tin bất kỳ & Link
    private val _selectedFilesToSend = MutableStateFlow<List<SelectedUploadItem>>(emptyList())
    val selectedFilesToSend: StateFlow<List<SelectedUploadItem>> = _selectedFilesToSend.asStateFlow()

    private val _linkToSend = MutableStateFlow("")
    val linkToSend: StateFlow<String> = _linkToSend.asStateFlow()

    private val _linkFileName = MutableStateFlow(createDefaultLinkFileName())
    val linkFileName: StateFlow<String> = _linkFileName.asStateFlow()

    // TAB 3: Tệp trên PC (Remote File Browser)
    private val _remoteFiles = MutableStateFlow<List<RemoteSmbItem>>(emptyList())
    val remoteFiles: StateFlow<List<RemoteSmbItem>> = _remoteFiles.asStateFlow()

    private val _isLoadingRemoteFiles = MutableStateFlow(false)
    val isLoadingRemoteFiles: StateFlow<Boolean> = _isLoadingRemoteFiles.asStateFlow()

    private val _remoteFilesError = MutableStateFlow<String?>(null)
    val remoteFilesError: StateFlow<String?> = _remoteFilesError.asStateFlow()

    private val _currentRemoteSubPath = MutableStateFlow("")
    val currentRemoteSubPath: StateFlow<String> = _currentRemoteSubPath.asStateFlow()

    // Download state
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadStatusText = MutableStateFlow("")
    val downloadStatusText: StateFlow<String> = _downloadStatusText.asStateFlow()

    private val _downloadProgressPercent = MutableStateFlow(0)
    val downloadProgressPercent: StateFlow<Int> = _downloadProgressPercent.asStateFlow()

    private val _downloadResult = MutableStateFlow<SmbDownloadResult?>(null)
    val downloadResult: StateFlow<SmbDownloadResult?> = _downloadResult.asStateFlow()

    // Remote PC 3D sync trigger states
    private val _isTriggeringSync = MutableStateFlow(false)
    val isTriggeringSync: StateFlow<Boolean> = _isTriggeringSync.asStateFlow()

    private val _syncTriggerResult = MutableStateFlow<String?>(null)
    val syncTriggerResult: StateFlow<String?> = _syncTriggerResult.asStateFlow()

    // Connection Testing
    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _testResult = MutableStateFlow<SmbTestResult?>(null)
    val testResult: StateFlow<SmbTestResult?> = _testResult.asStateFlow()

    // All Connections Testing (SMB PC, PC Sync :8888, Snapmaker, Bambuddy)
    private val _isCheckingAllConnections = MutableStateFlow(false)
    val isCheckingAllConnections: StateFlow<Boolean> = _isCheckingAllConnections.asStateFlow()

    private val _allConnectionsReport = MutableStateFlow<AllConnectionsReport?>(null)
    val allConnectionsReport: StateFlow<AllConnectionsReport?> = _allConnectionsReport.asStateFlow()

    // Uploading
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _uploadStatusText = MutableStateFlow("")
    val uploadStatusText: StateFlow<String> = _uploadStatusText.asStateFlow()

    private val _uploadProgressPercent = MutableStateFlow(0)
    val uploadProgressPercent: StateFlow<Int> = _uploadProgressPercent.asStateFlow()

    private val _uploadResult = MutableStateFlow<SmbUploadResult?>(null)
    val uploadResult: StateFlow<SmbUploadResult?> = _uploadResult.asStateFlow()

    // TAB 4: PC ⇄ PC & Phone ⇄ Phone (P2P Transfer)
    // PC-to-PC states
    private val _pcToPcSourceProfile = MutableStateFlow<PcProfile?>(_activeProfile.value)
    val pcToPcSourceProfile: StateFlow<PcProfile?> = _pcToPcSourceProfile.asStateFlow()

    private val _pcToPcTargetProfile = MutableStateFlow<PcProfile?>(
        _profiles.value.firstOrNull { it.id != _activeProfile.value.id } ?: _activeProfile.value
    )
    val pcToPcTargetProfile: StateFlow<PcProfile?> = _pcToPcTargetProfile.asStateFlow()

    private val _pcToPcTargetSubFolder = MutableStateFlow("")
    val pcToPcTargetSubFolder: StateFlow<String> = _pcToPcTargetSubFolder.asStateFlow()

    private val _isTransferringPcToPc = MutableStateFlow(false)
    val isTransferringPcToPc: StateFlow<Boolean> = _isTransferringPcToPc.asStateFlow()

    private val _pcToPcProgressPercent = MutableStateFlow(0)
    val pcToPcProgressPercent: StateFlow<Int> = _pcToPcProgressPercent.asStateFlow()

    private val _pcToPcStatusText = MutableStateFlow("")
    val pcToPcStatusText: StateFlow<String> = _pcToPcStatusText.asStateFlow()

    private val _pcToPcResult = MutableStateFlow<SmbUploadResult?>(null)
    val pcToPcResult: StateFlow<SmbUploadResult?> = _pcToPcResult.asStateFlow()

    private val _pcToPcSourceFiles = MutableStateFlow<List<RemoteSmbItem>>(emptyList())
    val pcToPcSourceFiles: StateFlow<List<RemoteSmbItem>> = _pcToPcSourceFiles.asStateFlow()

    private val _isLoadingPcToPcSourceFiles = MutableStateFlow(false)
    val isLoadingPcToPcSourceFiles: StateFlow<Boolean> = _isLoadingPcToPcSourceFiles.asStateFlow()

    private val _selectedPcToPcItems = MutableStateFlow<Set<RemoteSmbItem>>(emptySet())
    val selectedPcToPcItems: StateFlow<Set<RemoteSmbItem>> = _selectedPcToPcItems.asStateFlow()

    // Phone-to-Phone (P2P) states
    private val _isP2pReceiverRunning = MutableStateFlow(false)
    val isP2pReceiverRunning: StateFlow<Boolean> = _isP2pReceiverRunning.asStateFlow()

    private val _p2pLocalIps = MutableStateFlow<Pair<String?, String?>>(P2PTransferManager.getLocalIpAddresses())
    val p2pLocalIps: StateFlow<Pair<String?, String?>> = _p2pLocalIps.asStateFlow()

    private val _p2pReceivedItems = MutableStateFlow<List<P2pReceivedItem>>(emptyList())
    val p2pReceivedItems: StateFlow<List<P2pReceivedItem>> = _p2pReceivedItems.asStateFlow()

    private val _p2pTargetIp = MutableStateFlow("")
    val p2pTargetIp: StateFlow<String> = _p2pTargetIp.asStateFlow()

    private val _p2pFilesToSend = MutableStateFlow<List<SelectedUploadItem>>(emptyList())
    val p2pFilesToSend: StateFlow<List<SelectedUploadItem>> = _p2pFilesToSend.asStateFlow()

    private val _p2pLinkToSend = MutableStateFlow("")
    val p2pLinkToSend: StateFlow<String> = _p2pLinkToSend.asStateFlow()

    private val _isP2pSending = MutableStateFlow(false)
    val isP2pSending: StateFlow<Boolean> = _isP2pSending.asStateFlow()

    private val _p2pSendProgressPercent = MutableStateFlow(0)
    val p2pSendProgressPercent: StateFlow<Int> = _p2pSendProgressPercent.asStateFlow()

    private val _p2pSendStatusText = MutableStateFlow("")
    val p2pSendStatusText: StateFlow<String> = _p2pSendStatusText.asStateFlow()

    private val _p2pSendResult = MutableStateFlow<String?>(null)
    val p2pSendResult: StateFlow<String?> = _p2pSendResult.asStateFlow()

    // Kiểm tra & Dò tìm kết nối ĐT ⇄ ĐT
    private val _isTestingPeerConnection = MutableStateFlow(false)
    val isTestingPeerConnection: StateFlow<Boolean> = _isTestingPeerConnection.asStateFlow()

    private val _peerConnectionResult = MutableStateFlow<PeerTestResult?>(null)
    val peerConnectionResult: StateFlow<PeerTestResult?> = _peerConnectionResult.asStateFlow()

    private val _isScanningLanPeers = MutableStateFlow(false)
    val isScanningLanPeers: StateFlow<Boolean> = _isScanningLanPeers.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    // PeerDevice (Danh sách thiết bị P2P đã lưu)
    private val _peerDevices = MutableStateFlow<List<PeerDevice>>(repository.loadPeerDevices())
    val peerDevices: StateFlow<List<PeerDevice>> = _peerDevices.asStateFlow()

    private val _selectedPeerDevice = MutableStateFlow<PeerDevice?>(null)
    val selectedPeerDevice: StateFlow<PeerDevice?> = _selectedPeerDevice.asStateFlow()

    private val _showPeerDeviceDialog = MutableStateFlow(false)
    val showPeerDeviceDialog: StateFlow<Boolean> = _showPeerDeviceDialog.asStateFlow()

    private val _editingPeerDevice = MutableStateFlow<PeerDevice?>(null)
    val editingPeerDevice: StateFlow<PeerDevice?> = _editingPeerDevice.asStateFlow()

    // UI Dialogs
    private val _previewPhoto = MutableStateFlow<File?>(null)
    val previewPhoto: StateFlow<File?> = _previewPhoto.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    private val _showHelpDialog = MutableStateFlow(false)
    val showHelpDialog: StateFlow<Boolean> = _showHelpDialog.asStateFlow()

    private val _showCamera = MutableStateFlow(false)
    val showCamera: StateFlow<Boolean> = _showCamera.asStateFlow()

    init {
        if (_activeProfile.value.settings.isValid) {
            testConnection()
        }
    }

    private fun createDefaultFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "Scan_${sdf.format(Date())}"
    }

    fun createDefaultLinkFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "Link_${sdf.format(Date())}"
    }

    fun setSelectedTabIndex(index: Int) {
        _selectedTabIndex.value = index
        if (index == 2 && _remoteFiles.value.isEmpty()) {
            loadRemoteFiles()
        }
        if (index == 3) {
            refreshP2pLocalIps()
            if (_pcToPcSourceFiles.value.isEmpty()) {
                loadPcToPcSourceFiles()
            }
        }
    }

    fun regenerateFileName() {
        _fileName.value = createDefaultFileName()
    }

    fun setFileName(name: String) {
        _fileName.value = name
    }

    fun setExportFormat(format: ExportFormat) {
        _exportFormat.value = format
    }

    // TAB 1 methods
    fun addPhoto(file: File) {
        _capturedPhotos.value = _capturedPhotos.value + file
    }

    fun addPhotos(files: List<File>) {
        _capturedPhotos.value = _capturedPhotos.value + files
    }

    fun removePhoto(index: Int) {
        val current = _capturedPhotos.value.toMutableList()
        if (index in current.indices) {
            val file = current.removeAt(index)
            file.delete()
            _capturedPhotos.value = current
        }
    }

    fun clearAllPhotos() {
        _capturedPhotos.value.forEach { it.delete() }
        _capturedPhotos.value = emptyList()
        _previewPhoto.value = null
    }

    fun setPreviewPhoto(file: File?) {
        _previewPhoto.value = file
    }

    fun setShowSettingsDialog(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun setShowHelpDialog(show: Boolean) {
        _showHelpDialog.value = show
    }

    fun setShowCamera(show: Boolean) {
        _showCamera.value = show
    }

    // TAB 2: File Transfer methods
    fun addFilesToSend(context: Context, uris: List<Uri>) {
        val newItems = uris.map { uri ->
            val name = FileUtils.getFileName(context, uri)
            val size = FileUtils.getFileSize(context, uri)
            SelectedUploadItem(
                uri = uri,
                name = name,
                size = size,
                formattedSize = FileUtils.formatFileSize(size)
            )
        }
        _selectedFilesToSend.value = _selectedFilesToSend.value + newItems
    }

    fun addFolderToSend(context: Context, folderUri: Uri) {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                FileUtils.traverseDocumentFolder(context, folderUri)
            }
            if (items.isNotEmpty()) {
                _selectedFilesToSend.value = _selectedFilesToSend.value + items
            }
        }
    }

    fun removeFileToSend(index: Int) {
        val list = _selectedFilesToSend.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _selectedFilesToSend.value = list
        }
    }

    fun clearAllFilesToSend() {
        _selectedFilesToSend.value = emptyList()
    }

    fun uploadSelectedFiles() {
        val items = _selectedFilesToSend.value
        if (items.isEmpty()) {
            _uploadResult.value = SmbUploadResult(false, "Chưa chọn tệp nào để gửi! Vui lòng chọn tệp từ máy.")
            return
        }

        val settings = _smbSettings.value
        if (!settings.isValid) {
            _uploadResult.value = SmbUploadResult(false, "Vui lòng cài đặt IP máy tính và Tên thư mục chia sẻ trước khi gửi.")
            _showSettingsDialog.value = true
            return
        }

        viewModelScope.launch {
            _isUploading.value = true
            _uploadProgressPercent.value = 0
            _uploadResult.value = null

            try {
                val context = getApplication<Application>()
                val streamItems = items.map { item ->
                    StreamUploadItem(
                        name = item.name,
                        size = item.size,
                        openInputStream = { context.contentResolver.openInputStream(item.uri) }
                    )
                }

                _uploadStatusText.value = "Đang kết nối tới PC và gửi ${items.size} tệp..."
                val result = SmbManager.uploadStreams(settings, streamItems) { fileName, bytesWritten, totalBytes, percent ->
                    val sentStr = FileUtils.formatFileSize(bytesWritten)
                    val totalStr = FileUtils.formatFileSize(totalBytes)
                    _uploadProgressPercent.value = percent
                    _uploadStatusText.value = "Đang gửi $fileName ($sentStr / $totalStr)"
                }

                _uploadResult.value = result
                if (result.success) {
                    _selectedFilesToSend.value = emptyList()
                }
            } catch (e: Exception) {
                _uploadResult.value = SmbUploadResult(false, "Lỗi khi gửi tệp: ${e.localizedMessage}")
            } finally {
                _isUploading.value = false
            }
        }
    }

    // Link Sharing methods
    fun setLinkToSend(link: String) {
        _linkToSend.value = link
    }

    fun setLinkFileName(name: String) {
        _linkFileName.value = name
    }

    fun regenerateLinkFileName() {
        _linkFileName.value = createDefaultLinkFileName()
    }

    fun setIncomingSharedLink(sharedText: String) {
        _linkToSend.value = sharedText.trim()
        _linkFileName.value = createDefaultLinkFileName()
        _selectedTabIndex.value = 1
    }

    fun sendLinkToPc() {
        val content = _linkToSend.value.trim()
        if (content.isBlank()) {
            _uploadResult.value = SmbUploadResult(false, "Vui lòng nhập hoặc dán liên kết cần gửi!")
            return
        }

        val settings = _smbSettings.value
        if (!settings.isValid) {
            _uploadResult.value = SmbUploadResult(false, "Vui lòng cài đặt IP máy tính và Tên thư mục chia sẻ trước khi gửi.")
            _showSettingsDialog.value = true
            return
        }

        viewModelScope.launch {
            _isUploading.value = true
            _uploadProgressPercent.value = 0
            _uploadResult.value = null

            try {
                val bytes = content.toByteArray(Charsets.UTF_8)
                val rawName = _linkFileName.value.trim().ifBlank { createDefaultLinkFileName() }
                val finalFileName = if (rawName.endsWith(".txt", ignoreCase = true)) rawName else "$rawName.txt"
                val streamItem = StreamUploadItem(
                    name = finalFileName,
                    size = bytes.size.toLong(),
                    openInputStream = { java.io.ByteArrayInputStream(bytes) }
                )

                _uploadStatusText.value = "Đang gửi liên kết '$finalFileName' sang PC..."
                val result = SmbManager.uploadStreams(settings, listOf(streamItem)) { fileName, _, _, percent ->
                    _uploadProgressPercent.value = percent
                    _uploadStatusText.value = "Đang gửi $fileName: $percent%"
                }

                _uploadResult.value = result
                if (result.success) {
                    _linkToSend.value = ""
                    _linkFileName.value = createDefaultLinkFileName()
                }
            } catch (e: Exception) {
                _uploadResult.value = SmbUploadResult(false, "Lỗi khi gửi liên kết: ${e.localizedMessage}")
            } finally {
                _isUploading.value = false
            }
        }
    }

    // TAB 3: Remote PC browser & download methods
    fun loadRemoteFiles() {
        val settings = _smbSettings.value
        if (!settings.isValid) {
            _remoteFilesError.value = "Chưa cấu hình thông tin PC."
            return
        }

        viewModelScope.launch {
            _isLoadingRemoteFiles.value = true
            _remoteFilesError.value = null
            try {
                val result = SmbManager.listRemoteFiles(settings, _currentRemoteSubPath.value)
                result.fold(
                    onSuccess = {
                        _remoteFiles.value = it
                        _remoteFilesError.value = null
                    },
                    onFailure = {
                        _remoteFilesError.value = "Không thể đọc thư mục trên PC: ${it.localizedMessage}"
                    }
                )
            } finally {
                _isLoadingRemoteFiles.value = false
            }
        }
    }

    fun openRemoteFolder(folder: RemoteSmbItem) {
        _currentRemoteSubPath.value = folder.relativePath
        loadRemoteFiles()
    }

    fun navigateBackRemoteFolder() {
        val cur = _currentRemoteSubPath.value
        _currentRemoteSubPath.value = if (cur.contains('\\')) {
            cur.substringBeforeLast('\\')
        } else {
            ""
        }
        loadRemoteFiles()
    }

    fun downloadFile(item: RemoteSmbItem) {
        val settings = _smbSettings.value
        if (!settings.isValid) {
            _downloadResult.value = SmbDownloadResult(false, "Cấu hình PC chưa hợp lệ.")
            return
        }

        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgressPercent.value = 0
            _downloadStatusText.value = "Đang chuẩn bị tải ${item.name}..."
            _downloadResult.value = null

            var outputStreamPair: Pair<java.io.OutputStream, Uri?>? = null
            val context = getApplication<Application>()

            try {
                outputStreamPair = FileUtils.openDownloadOutputStream(context, item.relativePath.ifBlank { item.name })
                val outStream = outputStreamPair.first

                val result = SmbManager.downloadRemoteFile(
                    settings = settings,
                    remoteFileName = item.relativePath.ifBlank { item.name },
                    outputStream = outStream,
                    fileSize = item.size
                ) { readBytes, totalBytes, percent ->
                    val readStr = FileUtils.formatFileSize(readBytes)
                    val totalStr = FileUtils.formatFileSize(totalBytes)
                    _downloadProgressPercent.value = percent
                    _downloadStatusText.value = "Đang tải ${item.name} ($readStr / $totalStr): $percent%"
                }

                if (result.success) {
                    FileUtils.finishDownload(context, outputStreamPair.second)
                }
                _downloadResult.value = result
            } catch (e: Exception) {
                _downloadResult.value = SmbDownloadResult(false, "Lỗi khi tải tệp: ${e.localizedMessage}")
            } finally {
                _isDownloading.value = false
            }
        }
    }

    fun downloadFolder(folder: RemoteSmbItem) {
        val settings = _smbSettings.value
        if (!settings.isValid) {
            _downloadResult.value = SmbDownloadResult(false, "Cấu hình PC chưa hợp lệ.")
            return
        }

        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgressPercent.value = 0
            _downloadStatusText.value = "Đang quét thư mục '${folder.name}' trên PC..."
            _downloadResult.value = null

            val context = getApplication<Application>()
            try {
                val result = SmbManager.downloadRemoteFolder(
                    settings = settings,
                    folderRelativePath = folder.relativePath.ifBlank { folder.name },
                    folderName = folder.name,
                    context = context
                ) { currentFile, idx, total, percent ->
                    _downloadProgressPercent.value = percent
                    _downloadStatusText.value = "Đang tải thư mục '${folder.name}' ($idx/$total tệp)\n$currentFile: $percent%"
                }
                _downloadResult.value = result
            } catch (e: Exception) {
                _downloadResult.value = SmbDownloadResult(false, "Lỗi khi tải thư mục: ${e.localizedMessage}")
            } finally {
                _isDownloading.value = false
            }
        }
    }

    fun dismissDownloadResult() {
        _downloadResult.value = null
    }

    private suspend fun getEffectiveSync3dIp(settings: SmbSettings): String {
        if (settings.sync3dIp.isNotBlank()) return settings.sync3dIp.trim()
        val resolved = SmbManager.resolveEffectiveTarget(settings)
        return resolved.effectiveIp
    }

    // Remote PC 3D Printers Sync triggers (Port 8888)
    fun triggerFullSyncOnPc() {
        val settings = _activeProfile.value.settings

        viewModelScope.launch {
            _isTriggeringSync.value = true
            val ip = getEffectiveSync3dIp(settings)
            if (ip.isBlank()) {
                _syncTriggerResult.value = "Chưa có địa chỉ IP của máy tính."
                _isTriggeringSync.value = false
                return@launch
            }
            try {
                val url = "http://$ip:8888/sync"
                val response = withContext(Dispatchers.IO) {
                    val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 10000
                        readTimeout = 180000
                    }
                    if (conn.responseCode == 200) {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        throw IllegalStateException("Máy tính phản hồi mã lỗi HTTP ${conn.responseCode}")
                    }
                }
                val msg = org.json.JSONObject(response).optString("message", "Đồng bộ thành công!")
                _syncTriggerResult.value = "Kết quả đồng bộ từ PC:\n$msg"
            } catch (e: Exception) {
                _syncTriggerResult.value = "Không thể kết nối đến cổng 8888 trên PC ($ip)!\nHãy đảm bảo file 'Chay_Dong_Bo_In3D.bat' đang chạy trên máy tính.\nChi tiết: ${e.localizedMessage}"
            } finally {
                _isTriggeringSync.value = false
            }
        }
    }

    fun triggerTargetSyncOnPc(target: String) {
        val settings = _activeProfile.value.settings

        viewModelScope.launch {
            _isTriggeringSync.value = true
            val ip = getEffectiveSync3dIp(settings)
            if (ip.isBlank()) {
                _syncTriggerResult.value = "Chưa có địa chỉ IP của máy tính."
                _isTriggeringSync.value = false
                return@launch
            }
            try {
                val url = "http://$ip:8888/sync/$target"
                val response = withContext(Dispatchers.IO) {
                    val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 10000
                        readTimeout = 180000
                    }
                    if (conn.responseCode == 200) {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        throw IllegalStateException("HTTP ${conn.responseCode}")
                    }
                }
                val msg = org.json.JSONObject(response).optString("message", "Đồng bộ thành công!")
                _syncTriggerResult.value = msg
            } catch (e: Exception) {
                _syncTriggerResult.value = "Không thể kết nối đến cổng 8888 trên PC ($ip)!\nHãy đảm bảo script đồng bộ trên PC đang chạy.\nChi tiết: ${e.localizedMessage}"
            } finally {
                _isTriggeringSync.value = false
            }
        }
    }

    fun triggerFileSyncOnPc(target: String, fileName: String) {
        val settings = _activeProfile.value.settings

        viewModelScope.launch {
            _isTriggeringSync.value = true
            val ip = getEffectiveSync3dIp(settings)
            if (ip.isBlank()) {
                _syncTriggerResult.value = "Chưa có địa chỉ IP của máy tính."
                _isTriggeringSync.value = false
                return@launch
            }
            try {
                val encodedName = java.net.URLEncoder.encode(fileName, "UTF-8")
                val url = "http://$ip:8888/sync/file?target=$target&name=$encodedName"
                val response = withContext(Dispatchers.IO) {
                    val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 10000
                        readTimeout = 180000
                    }
                    if (conn.responseCode == 200) {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        throw IllegalStateException("HTTP ${conn.responseCode}")
                    }
                }
                val msg = org.json.JSONObject(response).optString("message", "Đẩy file thành công!")
                _syncTriggerResult.value = msg
            } catch (e: Exception) {
                _syncTriggerResult.value = "Không thể kết nối đến cổng 8888 trên PC ($ip)!\nHãy đảm bảo script đồng bộ trên PC đang chạy.\nChi tiết: ${e.localizedMessage}"
            } finally {
                _isTriggeringSync.value = false
            }
        }
    }

    fun dismissSyncTriggerResult() {
        _syncTriggerResult.value = null
    }

    fun selectProfile(id: String) {
        repository.setActiveProfileId(id)
        val profile = _profiles.value.find { it.id == id } ?: return
        _activeProfile.value = profile
        _smbSettings.value = profile.settings
        _testResult.value = null
        _allConnectionsReport.value = null
        _currentRemoteSubPath.value = ""
        _remoteFiles.value = emptyList()

        if (profile.settings.isValid) {
            testConnection()
        }

        if (_selectedTabIndex.value == 2) {
            loadRemoteFiles()
        }
    }

    fun saveCurrentProfile(name: String, settings: SmbSettings) {
        val current = _activeProfile.value
        val updated = current.copy(name = name.trim().ifBlank { "PC" }, settings = settings)
        val updatedList = repository.addOrUpdateProfile(updated, makeActive = true)
        _profiles.value = updatedList
        _activeProfile.value = updated
        _smbSettings.value = settings
        _allConnectionsReport.value = null
        if (settings.isValid) {
            testConnection()
        }
    }

    fun createNewProfile(name: String, settings: SmbSettings) {
        val newProfile = PcProfile(name = name.trim().ifBlank { "PC mới" }, settings = settings)
        val updatedList = repository.addOrUpdateProfile(newProfile, makeActive = true)
        _profiles.value = updatedList
        _activeProfile.value = newProfile
        _smbSettings.value = settings
        _testResult.value = null
    }

    fun deleteCurrentProfile() {
        val currentId = _activeProfile.value.id
        val updatedList = repository.deleteProfile(currentId)
        _profiles.value = updatedList
        val newActive = repository.getActiveProfile()
        _activeProfile.value = newActive
        _smbSettings.value = newActive.settings
        _testResult.value = null
    }

    fun saveSettings(settings: SmbSettings) {
        saveCurrentProfile(_activeProfile.value.name, settings)
    }

    fun getBackupJson(): String {
        return repository.exportBackupJson()
    }

    fun shareBackupJson(context: Context) {
        val json = repository.exportBackupJson()
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "APPSharePC_Backup.json")
            putExtra(android.content.Intent.EXTRA_TEXT, json)
        }
        val chooser = android.content.Intent.createChooser(intent, "Chia sẻ hoặc lưu cấu hình APPSharePC")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun autoSaveBackupToDownloads(context: Context): Result<Pair<String, Int>> {
        return try {
            val json = repository.exportBackupJson()
            val bytes = json.toByteArray(Charsets.UTF_8)
            val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "APPSharePC_Backup_$timeStr.json"

            val (outputStream, uri) = FileUtils.openDownloadOutputStream(context, fileName)
            outputStream.use { os ->
                os.write(bytes)
                os.flush()
            }
            FileUtils.finishDownload(context, uri)
            Result.success(Pair("Download/APPSharePC/$fileName", bytes.size))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun backupSettingsToFile(context: Context, targetUri: Uri): Result<Int> {
        return try {
            val json = repository.exportBackupJson()
            val bytes = json.toByteArray(Charsets.UTF_8)
            if (bytes.isEmpty()) {
                return Result.failure(Exception("Nội dung sao lưu trống."))
            }

            // Ghi trực tiếp bằng openOutputStream
            val os = context.contentResolver.openOutputStream(targetUri, "wt")
                ?: context.contentResolver.openOutputStream(targetUri)
                ?: return Result.failure(Exception("Không thể mở tệp đích để lưu dữ liệu."))
            os.use {
                it.write(bytes)
                it.flush()
            }

            // Kiểm tra lại bằng cách đọc lại tệp
            val readBytes = context.contentResolver.openInputStream(targetUri)?.use { it.readBytes() }
            if (readBytes == null || readBytes.isEmpty()) {
                // Thử lại qua AutoCloseOutputStream
                context.contentResolver.openFileDescriptor(targetUri, "w")?.use { pfd ->
                    android.os.ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { acos ->
                        acos.write(bytes)
                        acos.flush()
                    }
                }
            }

            val finalLen = context.contentResolver.openInputStream(targetUri)?.use { it.readBytes().size } ?: bytes.size
            Result.success(finalLen)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun restoreSettingsFromJsonString(json: String): Result<Int> {
        return try {
            if (json.isBlank()) {
                return Result.failure(Exception("Nội dung cấu hình rỗng."))
            }

            val result = repository.importBackupJson(json)
            result.onSuccess {
                val updatedProfiles = repository.loadProfiles()
                val newActive = repository.getActiveProfile()
                _profiles.value = updatedProfiles
                _activeProfile.value = newActive
                _smbSettings.value = newActive.settings
                _testResult.value = null
                _allConnectionsReport.value = null
                _currentRemoteSubPath.value = ""
                _remoteFiles.value = emptyList()

                if (newActive.settings.isValid) {
                    testConnection()
                }
                if (_selectedTabIndex.value == 2) {
                    loadRemoteFiles()
                }
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun restoreSettingsFromFile(context: Context, sourceUri: Uri): Result<Int> {
        return try {
            val json = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } ?: return Result.failure(Exception("Không thể đọc tệp sao lưu đã chọn."))

            if (json.isBlank()) {
                return Result.failure(Exception("Tệp sao lưu rỗng (0 bytes). Vui lòng chọn tệp sao lưu hợp lệ."))
            }

            restoreSettingsFromJsonString(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _testResult.value = null
            try {
                val result = SmbManager.testConnection(_smbSettings.value)
                _testResult.value = result
            } finally {
                _isTestingConnection.value = false
            }
        }
    }

    fun dismissTestResult() {
        _testResult.value = null
    }

    fun checkAllConnections() {
        val settings = _smbSettings.value

        viewModelScope.launch {
            _isCheckingAllConnections.value = true
            try {
                val lan = settings.lanIp.trim()
                val ts = settings.tailscaleIp.trim()

                // 1. Kiểm tra LAN trước (ưu tiên — nhanh hơn, timeout 1500ms)
                var lanStatus: TargetConnectionStatus? = null
                var tsStatus: TargetConnectionStatus? = null
                var legacyStatus: TargetConnectionStatus? = null

                if (lan.isNotBlank()) {
                    lanStatus = withContext(Dispatchers.IO) {
                        try {
                            val singleSettings = settings.copy(pcIp = lan, lanIp = lan, tailscaleIp = "")
                            val res = SmbManager.testConnection(singleSettings)
                            TargetConnectionStatus(
                                name = "Máy tính (IP LAN / WiFi)",
                                target = "\\\\$lan\\${settings.shareName}",
                                isOnline = res.success,
                                detail = if (res.success) "Cổng 445 mở • Quyền ghi OK" else res.message,
                                routeType = "LAN"
                            )
                        } catch (e: Exception) {
                            TargetConnectionStatus(
                                name = "Máy tính (IP LAN / WiFi)",
                                target = "\\\\$lan\\${settings.shareName}",
                                isOnline = false,
                                detail = e.localizedMessage ?: "Lỗi kết nối LAN",
                                routeType = "LAN"
                            )
                        }
                    }
                }

                // 2. Chỉ check Tailscale nếu LAN KHÔNG OK (hoặc không cấu hình LAN)
                val lanOk = lanStatus?.isOnline == true
                if (ts.isNotBlank()) {
                    if (lanOk) {
                        // LAN đang chạy tốt → bỏ qua check Tailscale, thông báo ngắn gọn
                        tsStatus = TargetConnectionStatus(
                            name = "Máy tính (IP Tailscale / Từ xa)",
                            target = "\\\\$ts\\${settings.shareName}",
                            isOnline = true, // Giả định OK vì LAN đang dùng
                            detail = "⚡ Đang dùng LAN tốc độ cao — bỏ qua kiểm tra Tailscale",
                            routeType = "Tailscale"
                        )
                    } else {
                        // LAN không OK → check Tailscale thay thế
                        tsStatus = withContext(Dispatchers.IO) {
                            try {
                                val singleSettings = settings.copy(pcIp = ts, lanIp = "", tailscaleIp = ts)
                                val res = SmbManager.testConnection(singleSettings)
                                TargetConnectionStatus(
                                    name = "Máy tính (IP Tailscale / Từ xa)",
                                    target = "\\\\$ts\\${settings.shareName}",
                                    isOnline = res.success,
                                    detail = if (res.success) "Cổng 445 mở • Quyền ghi OK (VPN)" else res.message,
                                    routeType = "Tailscale"
                                )
                            } catch (e: Exception) {
                                TargetConnectionStatus(
                                    name = "Máy tính (IP Tailscale / Từ xa)",
                                    target = "\\\\$ts\\${settings.shareName}",
                                    isOnline = false,
                                    detail = e.localizedMessage ?: "Lỗi kết nối Tailscale",
                                    routeType = "Tailscale"
                                )
                            }
                        }
                    }
                }

                // 3. Legacy (chỉ có 1 IP)
                if (lan.isBlank() && ts.isBlank() && settings.pcIp.isNotBlank()) {
                    legacyStatus = withContext(Dispatchers.IO) {
                        try {
                            val res = SmbManager.testConnection(settings)
                            TargetConnectionStatus(
                                name = "Máy tính Windows (SMB)",
                                target = "\\\\${settings.pcIp}\\${settings.shareName}",
                                isOnline = res.success,
                                detail = if (res.success) "Cổng 445 mở • Quyền ghi OK" else res.message,
                                routeType = "Mặc định"
                            )
                        } catch (e: Exception) {
                            TargetConnectionStatus(
                                name = "Máy tính Windows (SMB)",
                                target = "\\\\${settings.pcIp}\\${settings.shareName}",
                                isOnline = false,
                                detail = e.localizedMessage ?: "Lỗi kết nối SMB",
                                routeType = "Mặc định"
                            )
                        }
                    }
                }

                val pcStatuses = listOfNotNull(lanStatus, tsStatus, legacyStatus)
                val finalPcStatuses = if (pcStatuses.isEmpty()) {
                    listOf(
                        TargetConnectionStatus(
                            name = "Máy tính Windows (SMB)",
                            target = "Chưa cấu hình",
                            isOnline = false,
                            detail = "Chưa điền IP và thư mục chia sẻ"
                        )
                    )
                } else pcStatuses

                // 4. Kiểm tra Dịch vụ 3D, Snapmaker, Bambuddy (song song)
                val (pcSyncRes, snapRes, bambuRes) = withContext(Dispatchers.IO) {
                    var pcSyncOk = false
                    var snapmakerOk = false
                    var bambuddyOk = false
                    var pcSyncDetail = ""
                    var snapDetail = ""
                    var bambuDetail = ""

                    val resolvedTarget = SmbManager.resolveEffectiveTarget(settings)
                    val effective3dIp = if (settings.sync3dIp.isNotBlank()) settings.sync3dIp.trim() else resolvedTarget.effectiveIp
                    val shouldCheck3d = settings.enable3dSync || settings.sync3dIp.isNotBlank()

                    if (shouldCheck3d && effective3dIp.isNotBlank()) {
                        try {
                            val url = java.net.URL("http://$effective3dIp:8888/status")
                            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                                connectTimeout = 3000
                                readTimeout = 3000
                                requestMethod = "GET"
                            }
                            if (conn.responseCode == 200) {
                                val body = conn.inputStream.bufferedReader().use { it.readText() }
                                val json = org.json.JSONObject(body)
                                pcSyncOk = true
                                pcSyncDetail = "Dịch vụ cổng 8888 đang hoạt động"
                                snapmakerOk = json.optBoolean("snapmaker_online", false)
                                bambuddyOk = json.optBoolean("bambuddy_online", false)
                                if (snapmakerOk) snapDetail = "Trực tuyến (qua PC)"
                                if (bambuddyOk) bambuDetail = "Trực tuyến (qua PC)"
                            } else {
                                pcSyncDetail = "Mã HTTP ${conn.responseCode}"
                            }
                        } catch (e: Exception) {
                            pcSyncDetail = "Chưa chạy service hoặc bị tường lửa chặn"
                        }
                    } else if (!shouldCheck3d) {
                        pcSyncOk = true
                        pcSyncDetail = "PC này dùng gửi tài liệu SMB (Đã bỏ qua cổng 8888)"
                    } else {
                        pcSyncDetail = "Chưa cấu hình IP máy tính"
                    }

                    try {
                        val sUrl = java.net.URL("http://snapmaker.taileba178.ts.net/server/info")
                        val sConn = (sUrl.openConnection() as java.net.HttpURLConnection).apply {
                            connectTimeout = 2500; readTimeout = 2500; requestMethod = "GET"
                        }
                        if (sConn.responseCode in 200..299) {
                            snapmakerOk = true
                            snapDetail = "Trực tuyến (Klipper Fluidd/Moonraker)"
                        }
                    } catch (_: Exception) {}

                    try {
                        val bUrl = java.net.URL("http://tvbox.taileba178.ts.net:8000/docs")
                        val bConn = (bUrl.openConnection() as java.net.HttpURLConnection).apply {
                            connectTimeout = 2500; readTimeout = 2500; requestMethod = "GET"
                        }
                        if (bConn.responseCode in 200..299) {
                            bambuddyOk = true
                            bambuDetail = "Trực tuyến (Bambuddy API)"
                        }
                    } catch (_: Exception) {}

                    Triple(
                        TargetConnectionStatus("Dịch vụ Đồng bộ 3D (PC)",
                            if (shouldCheck3d && effective3dIp.isNotBlank()) "http://$effective3dIp:8888" else "Bỏ qua (Không bật trên PC này)",
                            isOnline = pcSyncOk, detail = pcSyncDetail),
                        TargetConnectionStatus("Snapmaker U1 (Klipper)",
                            "http://snapmaker.taileba178.ts.net",
                            isOnline = snapmakerOk,
                            detail = snapDetail.ifBlank { if (snapmakerOk) "Trực tuyến" else "Ngoại tuyến (Không phản hồi)" }),
                        TargetConnectionStatus("Bambuddy TVBox",
                            "http://tvbox.taileba178.ts.net:8000",
                            isOnline = bambuddyOk,
                            detail = bambuDetail.ifBlank { if (bambuddyOk) "Trực tuyến" else "Ngoại tuyến (Không phản hồi)" })
                    )
                }

                _allConnectionsReport.value = AllConnectionsReport(
                    connectionItems = finalPcStatuses + listOf(pcSyncRes, snapRes, bambuRes)
                )
            } finally {
                _isCheckingAllConnections.value = false
            }
        }
    }

    fun dismissAllConnectionsReport() {
        _allConnectionsReport.value = null
    }

    fun dismissUploadResult() {
        _uploadResult.value = null
    }

    fun exportAndUpload() {
        val photos = _capturedPhotos.value
        if (photos.isEmpty()) {
            _uploadResult.value = SmbUploadResult(false, "Chưa có ảnh nào để gửi! Vui lòng chụp hoặc chọn ảnh.")
            return
        }

        val settings = _smbSettings.value
        if (!settings.isValid) {
            _uploadResult.value = SmbUploadResult(false, "Vui lòng cài đặt IP máy tính và Tên thư mục chia sẻ trước khi gửi.")
            _showSettingsDialog.value = true
            return
        }

        viewModelScope.launch {
            _isUploading.value = true
            _uploadProgressPercent.value = 0
            _uploadResult.value = null

            try {
                val context = getApplication<Application>()
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val cleanFileName = _fileName.value.trim().ifBlank { createDefaultFileName() }

                val filesToUpload: List<File> = withContext(Dispatchers.IO) {
                    when (_exportFormat.value) {
                        ExportFormat.DOCX -> {
                            _uploadStatusText.value = "Đang đóng gói file Word (.docx) chuẩn A4..."
                            val targetFile = File(exportDir, "$cleanFileName.docx")
                            val docx = WordExporter.exportToDocx(photos, targetFile) { cur, tot ->
                                _uploadStatusText.value = "Đang thêm ảnh $cur/$tot vào Word..."
                            }
                            listOf(docx)
                        }
                        ExportFormat.PDF -> {
                            _uploadStatusText.value = "Đang tạo file scan PDF sắc nét..."
                            val targetFile = File(exportDir, "$cleanFileName.pdf")
                            val pdf = PdfExporter.exportToPdf(photos, targetFile) { cur, tot ->
                                _uploadStatusText.value = "Đang vẽ trang PDF $cur/$tot..."
                            }
                            listOf(pdf)
                        }
                        ExportFormat.JPG -> {
                            _uploadStatusText.value = "Đang xử lý file ảnh JPG..."
                            JpgExporter.prepareJpgFiles(photos, exportDir, cleanFileName)
                        }
                    }
                }

                _uploadStatusText.value = "Đang kết nối và truyền sang máy tính..."
                val result = SmbManager.uploadFiles(settings, filesToUpload) { currentFile, percent ->
                    _uploadProgressPercent.value = percent
                    _uploadStatusText.value = "Đang gửi $currentFile sang PC: $percent%"
                }

                _uploadResult.value = result

                if (result.success) {
                    clearAllPhotos()
                    regenerateFileName()
                }

                // Clean temporary exported files
                filesToUpload.forEach { it.delete() }

            } catch (e: Exception) {
                _uploadResult.value = SmbUploadResult(false, "Lỗi: ${e.localizedMessage}")
            } finally {
                _isUploading.value = false
            }
        }
    }

    // ==========================================
    // TAB 4: PC-to-PC Transfer Methods
    // ==========================================
    fun setPcToPcSourceProfile(profile: PcProfile) {
        _pcToPcSourceProfile.value = profile
        loadPcToPcSourceFiles()
    }

    fun setPcToPcTargetProfile(profile: PcProfile) {
        _pcToPcTargetProfile.value = profile
    }

    fun setPcToPcTargetSubFolder(folder: String) {
        _pcToPcTargetSubFolder.value = folder
    }

    fun loadPcToPcSourceFiles() {
        val src = _pcToPcSourceProfile.value ?: return
        if (!src.settings.isValid) return

        viewModelScope.launch {
            _isLoadingPcToPcSourceFiles.value = true
            _selectedPcToPcItems.value = emptySet()
            try {
                val res = SmbManager.listRemoteFiles(src.settings)
                res.onSuccess {
                    _pcToPcSourceFiles.value = it
                }.onFailure {
                    _pcToPcResult.value = SmbUploadResult(false, "Không thể đọc tệp từ PC nguồn '${src.name}': ${it.localizedMessage}")
                }
            } finally {
                _isLoadingPcToPcSourceFiles.value = false
            }
        }
    }

    fun toggleSelectPcToPcItem(item: RemoteSmbItem) {
        val current = _selectedPcToPcItems.value.toMutableSet()
        if (current.contains(item)) {
            current.remove(item)
        } else {
            current.add(item)
        }
        _selectedPcToPcItems.value = current
    }

    fun selectAllPcToPcItems() {
        _selectedPcToPcItems.value = _pcToPcSourceFiles.value.toSet()
    }

    fun clearSelectedPcToPcItems() {
        _selectedPcToPcItems.value = emptySet()
    }

    fun transferSelectedBetweenPcs() {
        val src = _pcToPcSourceProfile.value
        val tgt = _pcToPcTargetProfile.value
        val items = _selectedPcToPcItems.value.toList()

        if (src == null || tgt == null) {
            _pcToPcResult.value = SmbUploadResult(false, "Vui lòng chọn cả PC nguồn và PC đích!")
            return
        }
        if (src.id == tgt.id) {
            _pcToPcResult.value = SmbUploadResult(false, "PC nguồn và PC đích không được trùng nhau!")
            return
        }
        if (items.isEmpty()) {
            _pcToPcResult.value = SmbUploadResult(false, "Chưa chọn tệp tin nào trên PC nguồn để chuyển!")
            return
        }

        viewModelScope.launch {
            _isTransferringPcToPc.value = true
            _pcToPcProgressPercent.value = 0
            _pcToPcStatusText.value = "Đang kết nối tới 2 máy tính (${src.name} ➔ ${tgt.name})..."
            _pcToPcResult.value = null

            try {
                val result = SmbManager.transferBetweenPcs(
                    sourceSettings = src.settings,
                    targetSettings = tgt.settings,
                    itemsToTransfer = items,
                    targetSubFolder = _pcToPcTargetSubFolder.value,
                    sourceName = src.name,
                    targetName = tgt.name
                ) { currentFile, index, total, percent ->
                    _pcToPcProgressPercent.value = percent
                    _pcToPcStatusText.value = "Đang chuyển [$index/$total]: $currentFile ($percent%)"
                }

                _pcToPcResult.value = result
                if (result.success) {
                    _selectedPcToPcItems.value = emptySet()
                }
            } catch (e: Exception) {
                _pcToPcResult.value = SmbUploadResult(false, "Lỗi khi chuyển tệp giữa 2 PC: ${e.localizedMessage}")
            } finally {
                _isTransferringPcToPc.value = false
            }
        }
    }

    fun dismissPcToPcResult() {
        _pcToPcResult.value = null
    }

    // ==========================================
    // TAB 4: Phone-to-Phone (P2P) Transfer Methods
    // ==========================================
    fun toggleP2pReceiver() {
        val context = getApplication<Application>()
        if (_isP2pReceiverRunning.value) {
            P2PTransferManager.stopReceiver()
            _isP2pReceiverRunning.value = false
        } else {
            refreshP2pLocalIps()
            P2PTransferManager.startReceiver(
                context = context,
                port = P2PTransferManager.DEFAULT_PORT,
                onReceived = { item ->
                    _p2pReceivedItems.value = listOf(item) + _p2pReceivedItems.value
                },
                onError = { err ->
                    _p2pSendResult.value = "Lỗi máy chủ nhận tệp: $err"
                    _isP2pReceiverRunning.value = false
                }
            )
            _isP2pReceiverRunning.value = P2PTransferManager.isRunning
        }
    }

    fun refreshP2pLocalIps() {
        _p2pLocalIps.value = P2PTransferManager.getLocalIpAddresses()
    }

    fun setP2pTargetIp(ip: String) {
        _p2pTargetIp.value = ip
    }

    fun addP2pFilesToSend(context: Context, uris: List<Uri>) {
        val newItems = uris.map { uri ->
            val name = FileUtils.getFileName(context, uri)
            val size = FileUtils.getFileSize(context, uri)
            SelectedUploadItem(
                uri = uri,
                name = name,
                size = size,
                formattedSize = FileUtils.formatFileSize(size)
            )
        }
        _p2pFilesToSend.value = _p2pFilesToSend.value + newItems
    }

    fun removeP2pFileToSend(index: Int) {
        val list = _p2pFilesToSend.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _p2pFilesToSend.value = list
        }
    }

    fun clearP2pFilesToSend() {
        _p2pFilesToSend.value = emptyList()
    }

    fun setP2pLinkToSend(link: String) {
        _p2pLinkToSend.value = link
    }

    fun sendP2pFiles() {
        val ip = _p2pTargetIp.value.trim()
        val items = _p2pFilesToSend.value
        if (ip.isBlank()) {
            _p2pSendResult.value = "Vui lòng nhập IP máy nhận (hoặc IP Tailscale của điện thoại kia)!"
            return
        }
        if (items.isEmpty()) {
            _p2pSendResult.value = "Chưa chọn tệp tin nào để gửi!"
            return
        }

        viewModelScope.launch {
            _isP2pSending.value = true
            _p2pSendProgressPercent.value = 0
            val targetDevice = _selectedPeerDevice.value
            val effectiveIp = targetDevice?.let {
                P2PTransferManager.resolveEffectivePeerIp(it).first
            } ?: ip
            _p2pSendStatusText.value = "Đang kết nối tới điện thoại $effectiveIp..."
            _p2pSendResult.value = null

            try {
                val context = getApplication<Application>()
                val streamItems = items.map { item ->
                    StreamUploadItem(
                        name = item.name,
                        size = item.size,
                        openInputStream = { context.contentResolver.openInputStream(item.uri) }
                    )
                }

                val res = P2PTransferManager.sendFilesToPeer(effectiveIp, items = streamItems) { file, cur, tot, pct ->
                    _p2pSendProgressPercent.value = pct
                    _p2pSendStatusText.value = "Đang gửi [$cur/$tot]: $file ($pct%)"
                }

                res.onSuccess { msg ->
                    _p2pSendResult.value = msg
                    _p2pFilesToSend.value = emptyList()
                }.onFailure { err ->
                    _p2pSendResult.value = "Lỗi gửi sang điện thoại: ${err.localizedMessage}"
                }
            } catch (e: Exception) {
                _p2pSendResult.value = "Lỗi gửi: ${e.localizedMessage}"
            } finally {
                _isP2pSending.value = false
            }
        }
    }

    fun sendP2pLink() {
        val ip = _p2pTargetIp.value.trim()
        val link = _p2pLinkToSend.value.trim()
        if (ip.isBlank()) {
            _p2pSendResult.value = "Vui lòng nhập IP máy nhận (hoặc IP Tailscale của điện thoại kia)!"
            return
        }
        if (link.isBlank()) {
            _p2pSendResult.value = "Vui lòng nhập hoặc dán nội dung liên kết!"
            return
        }

        viewModelScope.launch {
            _isP2pSending.value = true
            val targetDevice = _selectedPeerDevice.value
            val effectiveIp = targetDevice?.let {
                P2PTransferManager.resolveEffectivePeerIp(it).first
            } ?: ip
            _p2pSendStatusText.value = "Đang gửi liên kết sang điện thoại $effectiveIp..."
            _p2pSendResult.value = null

            try {
                val res = P2PTransferManager.sendLinkToPeer(effectiveIp, link = link)
                res.onSuccess { msg ->
                    _p2pSendResult.value = msg
                    _p2pLinkToSend.value = ""
                }.onFailure { err ->
                    _p2pSendResult.value = "Lỗi gửi link sang điện thoại: ${err.localizedMessage}"
                }
            } catch (e: Exception) {
                _p2pSendResult.value = "Lỗi gửi: ${e.localizedMessage}"
            } finally {
                _isP2pSending.value = false
            }
        }
    }

    fun dismissP2pSendResult() {
        _p2pSendResult.value = null
    }

    // ==========================================
    // Kiểm tra & Dò tìm kết nối ĐT ⇄ ĐT
    // ==========================================
    fun testPeerConnection(targetIp: String = "") {
        val ipToTest = targetIp.trim().ifBlank {
            _selectedPeerDevice.value?.primaryIp ?: _p2pTargetIp.value.trim()
        }
        if (ipToTest.isBlank()) {
            _peerConnectionResult.value = PeerTestResult(
                success = false,
                message = "Chưa có địa chỉ IP để thử kết nối. Vui lòng chọn thiết bị hoặc nhập IP!"
            )
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isTestingPeerConnection.value = true
            val effectiveTarget = _selectedPeerDevice.value?.let { dev ->
                P2PTransferManager.resolveEffectivePeerIp(dev).first
            } ?: ipToTest

            val res = P2PTransferManager.testPeerConnection(effectiveTarget)
            _peerConnectionResult.value = res
            _isTestingPeerConnection.value = false
            if (res.success && res.activeIp.isNotBlank()) {
                _p2pTargetIp.value = res.activeIp
            }
        }
    }

    fun dismissPeerConnectionResult() {
        _peerConnectionResult.value = null
    }

    fun scanLanPeers() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanningLanPeers.value = true
            _peerConnectionResult.value = null
            val found = P2PTransferManager.discoverPeersOnLan()
            _discoveredPeers.value = found
            _isScanningLanPeers.value = false
            if (found.isEmpty()) {
                _peerConnectionResult.value = PeerTestResult(
                    success = false,
                    message = "Không tìm thấy điện thoại/máy tính bảng nào đang mở APPSharePC trong mạng WiFi này."
                )
            }
        }
    }

    fun clearDiscoveredPeers() {
        _discoveredPeers.value = emptyList()
    }

    fun connectToDiscoveredPeer(peer: DiscoveredPeer) {
        val existing = _peerDevices.value.find { it.primaryIp == peer.ip || it.lanIp == peer.ip }
        if (existing != null) {
            selectPeerDevice(existing)
        } else {
            val newDevice = PeerDevice(
                name = peer.deviceName,
                ip = peer.ip,
                lanIp = peer.ip,
                type = peer.deviceType
            )
            savePeerDevice(newDevice)
            selectPeerDevice(newDevice)
        }
        _p2pTargetIp.value = peer.ip
        testPeerConnection(peer.ip)
    }

    // ==========================================
    // PeerDevice CRUD (Danh sách thiết bị P2P)
    // ==========================================
    fun openAddPeerDeviceDialog() {
        _editingPeerDevice.value = PeerDevice()
        _showPeerDeviceDialog.value = true
    }

    fun openEditPeerDeviceDialog(device: PeerDevice) {
        _editingPeerDevice.value = device
        _showPeerDeviceDialog.value = true
    }

    fun closePeerDeviceDialog() {
        _showPeerDeviceDialog.value = false
        _editingPeerDevice.value = null
    }

    fun savePeerDevice(device: PeerDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = repository.addOrUpdatePeerDevice(device)
            _peerDevices.value = updated
        }
        _showPeerDeviceDialog.value = false
        _editingPeerDevice.value = null
    }

    fun deletePeerDevice(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = repository.deletePeerDevice(id)
            _peerDevices.value = updated
            // Nếu thiết bị đang được chọn bị xóa, bỏ chọn
            if (_selectedPeerDevice.value?.id == id) {
                _selectedPeerDevice.value = null
                _p2pTargetIp.value = ""
                _peerConnectionResult.value = null
            }
        }
    }

    fun selectPeerDevice(device: PeerDevice) {
        _selectedPeerDevice.value = device
        _p2pTargetIp.value = device.primaryIp
        _peerConnectionResult.value = null
    }

    fun clearSelectedPeerDevice() {
        _selectedPeerDevice.value = null
        _p2pTargetIp.value = ""
        _peerConnectionResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        P2PTransferManager.stopReceiver()
    }
}
