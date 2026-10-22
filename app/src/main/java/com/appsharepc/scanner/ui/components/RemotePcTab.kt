package com.appsharepc.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.appsharepc.scanner.data.PcProfile
import com.appsharepc.scanner.service.FileUtils
import com.appsharepc.scanner.service.RemoteSmbItem
import com.appsharepc.scanner.service.SmbDownloadResult
import com.appsharepc.scanner.ui.theme.ErrorRed
import com.appsharepc.scanner.ui.theme.SuccessGreen
import com.appsharepc.scanner.viewmodel.MainViewModel

@Composable
fun RemotePcTab(
    viewModel: MainViewModel,
    activeProfile: PcProfile,
    currentSubPath: String,
    remoteFiles: List<RemoteSmbItem>,
    isLoading: Boolean,
    error: String?,
    isDownloading: Boolean,
    downloadStatusText: String,
    downloadProgressPercent: Int,
    downloadResult: SmbDownloadResult?,
    isTriggeringSync: Boolean = false,
    syncTriggerResult: String? = null,
    modifier: Modifier = Modifier
) {
    val allConnectionsReport by viewModel.allConnectionsReport.collectAsState()
    val normalizedSubPath = currentSubPath.replace('/', '\\')
    val isSnapFolder = normalizedSubPath.equals("Snap", ignoreCase = true) ||
            normalizedSubPath.endsWith("\\Snap", ignoreCase = true)
    val isBambuFolder = normalizedSubPath.equals("Bambu", ignoreCase = true) ||
            normalizedSubPath.endsWith("\\Bambu", ignoreCase = true)

    Column(modifier = modifier.fillMaxWidth()) {
        // Card thông tin thư mục và nút làm mới
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Thư mục trên: ${activeProfile.name}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (currentSubPath.isBlank()) {
                            activeProfile.settings.targetPathDescription
                        } else {
                            "${activeProfile.settings.targetPathDescription}\\$currentSubPath"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { viewModel.loadRemoteFiles() },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Làm mới danh sách")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Card Kích hoạt đồng bộ 3D toàn bộ (Snapmaker + Bambu) từ xa qua PC
        if (currentSubPath.isBlank()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Đồng bộ in 3D qua PC",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "PC ➔ Snapmaker U1 & Bambuddy TVBox",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                            allConnectionsReport?.let { rep ->
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (rep.snapmaker.isOnline) SuccessGreen else ErrorRed))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Snap", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (rep.snapmaker.isOnline) SuccessGreen else ErrorRed)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (rep.bambuddy.isOnline) SuccessGreen else ErrorRed))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Bambu", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (rep.bambuddy.isOnline) SuccessGreen else ErrorRed)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (rep.pcSyncService.isOnline) SuccessGreen else ErrorRed))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("PC:8888", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (rep.pcSyncService.isOnline) SuccessGreen else ErrorRed)
                                }
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = { viewModel.triggerFullSyncOnPc() },
                        enabled = !isTriggeringSync,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Đồng bộ ngay", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Action banner riêng khi đang ở trong thư mục Snap
        if (isSnapFolder) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Thư mục Snapmaker U1",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            allConnectionsReport?.let { rep ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (rep.snapmaker.isOnline) SuccessGreen else ErrorRed))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (rep.snapmaker.isOnline) "Klipper: Đã kết nối (Online)" else "Klipper: Mất kết nối (Offline)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rep.snapmaker.isOnline) SuccessGreen else ErrorRed
                                    )
                                }
                            } ?: Text(
                                text = "Klipper / Fluidd (paxx12)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.triggerTargetSyncOnPc("snap") },
                        enabled = !isTriggeringSync,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Đẩy thư mục này", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (isBambuFolder) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Thư mục Bambuddy TVBox",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            allConnectionsReport?.let { rep ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (rep.bambuddy.isOnline) SuccessGreen else ErrorRed))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (rep.bambuddy.isOnline) "Bambuddy: Đã kết nối (Online)" else "Bambuddy: Mất kết nối (Offline)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rep.bambuddy.isOnline) SuccessGreen else ErrorRed
                                    )
                                }
                            } ?: Text(
                                text = "Bambuddy TVBox (:8000)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.triggerTargetSyncOnPc("bambu") },
                        enabled = !isTriggeringSync,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Đẩy thư mục này", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Thanh điều hướng quay lại & nút tải cả thư mục hiện tại (nếu đang ở thư mục con)
        if (currentSubPath.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.navigateBackRemoteFolder() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Quay lại thư mục trước", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(
                                text = "📁 $currentSubPath",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            val currentFolderItem = RemoteSmbItem(
                                name = currentSubPath.substringAfterLast('\\'),
                                relativePath = currentSubPath,
                                isDirectory = true
                            )
                            viewModel.downloadFolder(currentFolderItem)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tải thư mục này", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // State handling
        when {
            isLoading && remoteFiles.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Đang đọc dữ liệu từ PC...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            error != null -> {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Không thể kết nối lấy danh sách tệp",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.loadRemoteFiles() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thử lại")
                        }
                    }
                }
            }
            remoteFiles.isEmpty() -> {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Thư mục trên PC đang trống",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Chưa có tệp nào được lưu trong thư mục này.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                // Danh sách tệp & thư mục
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Có ${remoteFiles.size} mục trên PC",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    remoteFiles.forEach { item ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (item.isDirectory) Modifier.clickable { viewModel.openRemoteFolder(item) }
                                    else Modifier
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (item.isDirectory) MaterialTheme.colorScheme.secondaryContainer
                                             else MaterialTheme.colorScheme.primaryContainer
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (item.isDirectory) Icons.Default.Folder else FileUtils.getFileIcon(item.name),
                                        contentDescription = null,
                                        tint = if (item.isDirectory) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (item.isDirectory) {
                                            Text(
                                                text = "Thư mục • Chạm để mở",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        } else {
                                            Text(
                                                text = FileUtils.formatFileSize(item.size),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (item.lastModified > 0) {
                                            Text(
                                                text = " • ${FileUtils.formatDate(item.lastModified)}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }

                                if (item.isDirectory) {
                                    // Nút tải cả thư mục + icon mở
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FilledTonalIconButton(
                                            onClick = { viewModel.downloadFolder(item) },
                                            modifier = Modifier.size(38.dp),
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Tải cả thư mục",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Mở thư mục",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                } else {
                                    // File item: Gửi sang máy in (nếu ở thư mục Snap hoặc Bambu) + Tải về máy
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isSnapFolder) {
                                            FilledTonalIconButton(
                                                onClick = { viewModel.triggerFileSyncOnPc("snap", item.name) },
                                                enabled = !isTriggeringSync,
                                                modifier = Modifier.size(38.dp),
                                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Print,
                                                    contentDescription = "Đẩy sang Snapmaker U1",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        } else if (isBambuFolder) {
                                            FilledTonalIconButton(
                                                onClick = { viewModel.triggerFileSyncOnPc("bambu", item.name) },
                                                enabled = !isTriggeringSync,
                                                modifier = Modifier.size(38.dp),
                                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.secondary
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Print,
                                                    contentDescription = "Đẩy sang Bambuddy TVBox",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }

                                        FilledTonalIconButton(
                                            onClick = { viewModel.downloadFile(item) },
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Tải về máy",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Download Progress Dialog
    if (isDownloading) {
        Dialog(onDismissRequest = { /* Prevent dismiss while downloading */ }) {
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
                        progress = { downloadProgressPercent / 100f },
                        modifier = Modifier.size(60.dp),
                        strokeWidth = 6.dp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = downloadStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$downloadProgressPercent%",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Download Result Alert Dialog
    downloadResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDownloadResult() },
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
                    text = if (result.success) "Tải thành công!" else "Tải thất bại",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = result.message)
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissDownloadResult() }) {
                    Text("Đóng")
                }
            }
        )
    }

    // Sync Trigger Progress Dialog
    if (isTriggeringSync) {
        Dialog(onDismissRequest = { /* Prevent dismiss while syncing */ }) {
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
                        modifier = Modifier.size(54.dp),
                        strokeWidth = 5.dp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Đang gửi lệnh đồng bộ lên PC...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "PC đang kết nối và đẩy file sang Snapmaker / Bambuddy.\nVui lòng đợi giây lát...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }

    // Sync Trigger Result Alert Dialog
    syncTriggerResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSyncTriggerResult() },
            icon = {
                Icon(
                    imageVector = if (msg.contains("Lỗi", ignoreCase = true) || msg.contains("thất bại", ignoreCase = true) || msg.contains("Không thể", ignoreCase = true)) Icons.Default.Error else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (msg.contains("Lỗi", ignoreCase = true) || msg.contains("thất bại", ignoreCase = true) || msg.contains("Không thể", ignoreCase = true)) MaterialTheme.colorScheme.error else SuccessGreen,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "Kết quả đồng bộ 3D",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = msg)
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissSyncTriggerResult() }) {
                    Text("Đóng")
                }
            }
        )
    }
}
