package com.appsharepc.scanner.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.appsharepc.scanner.BuildConfig
import com.appsharepc.scanner.service.ImageUtils
import com.appsharepc.scanner.ui.components.AddDeviceChoiceDialog
import com.appsharepc.scanner.ui.components.ConnectionStatusCard
import com.appsharepc.scanner.ui.components.FileTransferTab
import com.appsharepc.scanner.ui.components.FullscreenImageViewer
import com.appsharepc.scanner.ui.components.ImageThumbnailRow
import com.appsharepc.scanner.ui.components.P2PTransferTab
import com.appsharepc.scanner.ui.components.PeerDeviceDialog
import com.appsharepc.scanner.ui.components.RemotePcTab
import com.appsharepc.scanner.ui.theme.SuccessGreen
import com.appsharepc.scanner.viewmodel.ExportFormat
import com.appsharepc.scanner.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onRequestCameraPermission: () -> Unit
) {
    val context = LocalContext.current

    val selectedTabIndex by viewModel.selectedTabIndex.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val smbSettings by viewModel.smbSettings.collectAsState()

    // TAB 1 States
    val capturedPhotos by viewModel.capturedPhotos.collectAsState()
    val exportFormat by viewModel.exportFormat.collectAsState()
    val fileName by viewModel.fileName.collectAsState()

    // TAB 2 States
    val selectedFilesToSend by viewModel.selectedFilesToSend.collectAsState()

    // TAB 3 States
    val remoteFiles by viewModel.remoteFiles.collectAsState()
    val isLoadingRemoteFiles by viewModel.isLoadingRemoteFiles.collectAsState()
    val remoteFilesError by viewModel.remoteFilesError.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadStatusText by viewModel.downloadStatusText.collectAsState()
    val downloadProgressPercent by viewModel.downloadProgressPercent.collectAsState()
    val downloadResult by viewModel.downloadResult.collectAsState()
    val currentRemoteSubPath by viewModel.currentRemoteSubPath.collectAsState()

    // 3D Sync Trigger States
    val isTriggeringSync by viewModel.isTriggeringSync.collectAsState()
    val syncTriggerResult by viewModel.syncTriggerResult.collectAsState()

    // Connection & Upload States
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val isCheckingAllConnections by viewModel.isCheckingAllConnections.collectAsState()
    val allConnectionsReport by viewModel.allConnectionsReport.collectAsState()

    val isUploading by viewModel.isUploading.collectAsState()
    val uploadStatusText by viewModel.uploadStatusText.collectAsState()
    val uploadProgressPercent by viewModel.uploadProgressPercent.collectAsState()
    val uploadResult by viewModel.uploadResult.collectAsState()

    val previewPhoto by viewModel.previewPhoto.collectAsState()
    val showSettingsDialog by viewModel.showSettingsDialog.collectAsState()
    val showHelpDialog by viewModel.showHelpDialog.collectAsState()
    val showPeerDeviceDialog by viewModel.showPeerDeviceDialog.collectAsState()
    val editingPeerDevice by viewModel.editingPeerDevice.collectAsState()

    // Khi đang "Thêm máy": lưu id máy trước đó để Hủy thì xóa máy mới và quay lại
    var pendingNewFromId by rememberSaveable { mutableStateOf<String?>(null) }
    var showBackupDialog by rememberSaveable { mutableStateOf(false) }
    var showAddDeviceChoiceDialog by rememberSaveable { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    // Gallery Picker for Tab 1
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val copiedFiles = uris.mapNotNull { ImageUtils.copyUriToTempFile(context, it) }
            viewModel.addPhotos(copiedFiles)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Scan & Share to PC",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Thêm thiết bị mới") },
                                leadingIcon = { Icon(Icons.Default.Devices, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    showAddDeviceChoiceDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cài đặt máy tính") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.setShowSettingsDialog(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sao lưu & khôi phục") },
                                leadingIcon = { Icon(Icons.Default.SettingsBackupRestore, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    showBackupDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hướng dẫn sử dụng") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.setShowHelpDialog(true)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        val isRefreshing = (selectedTabIndex == 2 && isLoadingRemoteFiles) || isTestingConnection || isCheckingAllConnections

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (selectedTabIndex == 2) {
                    viewModel.loadRemoteFiles()
                } else {
                    viewModel.testConnection()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

            // 1. Connection Status Card (dùng chung cho mọi Tab)
            ConnectionStatusCard(
                profiles = profiles,
                activeProfile = activeProfile,
                onSelectProfile = { viewModel.selectProfile(it) },
                onAddNewProfile = {
                    showAddDeviceChoiceDialog = true
                },
                isTesting = isTestingConnection,
                testResult = testResult,
                onTestConnection = { viewModel.testConnection() },
                isCheckingAll = isCheckingAllConnections,
                allConnectionsReport = allConnectionsReport,
                onCheckAllConnections = { viewModel.checkAllConnections() },
                onDismissAllReport = { viewModel.dismissAllConnectionsReport() },
                onOpenSettings = { viewModel.setShowSettingsDialog(true) },
                onOpenHelp = { viewModel.setShowHelpDialog(true) },
                onDismissTestResult = { viewModel.dismissTestResult() }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Navigation Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { viewModel.setSelectedTabIndex(0) },
                    text = { Text("Quét ảnh", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { viewModel.setSelectedTabIndex(1) },
                    text = { Text("Gửi tệp & Link", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { viewModel.setSelectedTabIndex(2) },
                    text = { Text("Tệp trên PC", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.FolderShared, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTabIndex == 3,
                    onClick = { viewModel.setSelectedTabIndex(3) },
                    text = { Text("Chuyển P2P", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Main Content based on Selected Tab
            when (selectedTabIndex) {
                0 -> {
                    // TAB 1: Quét tài liệu & Xuất Word / PDF / JPG
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onRequestCameraPermission,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Chụp liên tiếp", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                galleryPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Chọn từ máy", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    ImageThumbnailRow(
                        photos = capturedPhotos,
                        onPhotoClick = { viewModel.setPreviewPhoto(it) },
                        onRemovePhoto = { viewModel.removePhoto(it) },
                        onClearAll = { viewModel.clearAllPhotos() }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Định dạng xuất file",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            ExportFormat.values().forEach { format ->
                                val selected = exportFormat == format
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                    border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { viewModel.setExportFormat(format) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = { viewModel.setExportFormat(format) }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = format.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = format.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tên tài liệu",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = { viewModel.regenerateFileName() },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Update,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tạo tên theo giờ", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = fileName,
                                onValueChange = { viewModel.setFileName(it) },
                                trailingIcon = {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = exportFormat.extension,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.exportAndUpload() },
                        enabled = !isUploading && capturedPhotos.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "GỬI SANG MÁY TÍNH (SMB)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                1 -> {
                    // TAB 2: Gửi tệp tin bất kỳ
                    FileTransferTab(
                        viewModel = viewModel,
                        selectedFiles = selectedFilesToSend
                    )
                }
                2 -> {
                    // TAB 3: Tệp trên PC (Remote Browser & Download)
                    RemotePcTab(
                        viewModel = viewModel,
                        activeProfile = activeProfile,
                        currentSubPath = currentRemoteSubPath,
                        remoteFiles = remoteFiles,
                        isLoading = isLoadingRemoteFiles,
                        error = remoteFilesError,
                        isDownloading = isDownloading,
                        downloadStatusText = downloadStatusText,
                        downloadProgressPercent = downloadProgressPercent,
                        downloadResult = downloadResult,
                        isTriggeringSync = isTriggeringSync,
                        syncTriggerResult = syncTriggerResult
                    )
                }
                3 -> {
                    // TAB 4: Chuyển dữ liệu giữa 2 PC hoặc từ ĐT sang ĐT
                    P2PTransferTab(
                        viewModel = viewModel,
                        profiles = profiles
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Phiên bản v${BuildConfig.VERSION_NAME} • SMBv2/v3 2-Way Transfer",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

    // Fullscreen Preview Dialog
    FullscreenImageViewer(
        file = previewPhoto,
        onDismiss = { viewModel.setPreviewPhoto(null) }
    )

    // Hộp thoại lựa chọn loại thiết bị: Máy tính PC (Windows SMB) vs Điện thoại / Tablet (P2P)
    if (showAddDeviceChoiceDialog) {
        AddDeviceChoiceDialog(
            onDismiss = { showAddDeviceChoiceDialog = false },
            onSelectPc = {
                showAddDeviceChoiceDialog = false
                pendingNewFromId = activeProfile.id
                viewModel.createNewProfile("PC ${profiles.size + 1}", com.appsharepc.scanner.data.SmbSettings())
                viewModel.setShowSettingsDialog(true)
            },
            onSelectPhone = {
                showAddDeviceChoiceDialog = false
                viewModel.setSelectedTabIndex(3)
                viewModel.openAddPeerDeviceDialog()
            }
        )
    }

    // Peer Device Dialog (Thêm/Sửa ĐT P2P - hiển thị toàn cục cho mọi Tab)
    if (showPeerDeviceDialog && editingPeerDevice != null) {
        PeerDeviceDialog(
            device = editingPeerDevice!!,
            onDismiss = { viewModel.closePeerDeviceDialog() },
            onSave = { viewModel.savePeerDevice(it) }
        )
    }

    // SMB Settings (toàn trang)
    if (showSettingsDialog) {
        val closeSettings: () -> Unit = {
            // Đang thêm máy mới mà bấm Đóng/Back -> bỏ máy mới, quay lại máy cũ
            pendingNewFromId?.let { prevId ->
                viewModel.deleteCurrentProfile()
                viewModel.selectProfile(prevId)
            }
            pendingNewFromId = null
            viewModel.setShowSettingsDialog(false)
        }
        SmbSettingsDialog(
            profile = activeProfile,
            isNew = pendingNewFromId != null,
            canDelete = profiles.size > 1,
            isTesting = isTestingConnection,
            testResult = testResult,
            onSave = { name, settings ->
                viewModel.saveCurrentProfile(name, settings)
                pendingNewFromId = null
            },
            onDelete = {
                viewModel.deleteCurrentProfile()
                pendingNewFromId = null
            },
            onTestConnection = { name, settings ->
                viewModel.saveCurrentProfile(name, settings)
                viewModel.testConnection()
            },
            onDismissTestResult = { viewModel.dismissTestResult() },
            onOpenBackup = { showBackupDialog = true },
            onOpenHelp = { viewModel.setShowHelpDialog(true) },
            onDismiss = closeSettings
        )
    }

    // Sao lưu & khôi phục (tách riêng, áp dụng cho tất cả máy)
    if (showBackupDialog) {
        BackupRestoreDialog(
            profileCount = profiles.size,
            onAutoSaveBackup = { viewModel.autoSaveBackupToDownloads(context) },
            onBackup = { uri -> viewModel.backupSettingsToFile(context, uri) },
            onRestore = { uri -> viewModel.restoreSettingsFromFile(context, uri) },
            onRestoreFromString = { json -> viewModel.restoreSettingsFromJsonString(json) },
            onShareBackup = { viewModel.shareBackupJson(context) },
            onGetBackupJson = { viewModel.getBackupJson() },
            onDismiss = { showBackupDialog = false }
        )
    }

    // Help Guide Dialog
    if (showHelpDialog) {
        HelpGuideDialog(
            onDismiss = { viewModel.setShowHelpDialog(false) }
        )
    }

    // Upload Progress Dialog
    if (isUploading) {
        Dialog(onDismissRequest = { /* Prevent dismiss while uploading */ }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { uploadProgressPercent / 100f },
                        modifier = Modifier.size(60.dp),
                        strokeWidth = 6.dp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = uploadStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$uploadProgressPercent%",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Upload Result Dialog
    uploadResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUploadResult() },
            icon = {
                Icon(
                    imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (result.success) SuccessGreen else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = if (result.success) "Gửi thành công!" else "Gửi thất bại",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = result.message)
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissUploadResult() }) {
                    Text("Đóng")
                }
            }
        )
    }
}
