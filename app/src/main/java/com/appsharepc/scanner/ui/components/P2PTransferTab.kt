package com.appsharepc.scanner.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appsharepc.scanner.data.PcProfile
import com.appsharepc.scanner.data.PeerDevice
import com.appsharepc.scanner.data.PeerDeviceType
import com.appsharepc.scanner.service.DiscoveredPeer
import com.appsharepc.scanner.service.FileUtils
import com.appsharepc.scanner.service.RemoteSmbItem
import com.appsharepc.scanner.ui.theme.ErrorRed
import com.appsharepc.scanner.ui.theme.SuccessGreen
import com.appsharepc.scanner.viewmodel.MainViewModel

@Composable
fun P2PTransferTab(
    viewModel: MainViewModel,
    profiles: List<PcProfile>,
    modifier: Modifier = Modifier
) {
    var subTab by remember { mutableIntStateOf(0) } // 0: PC ➔ PC, 1: ĐT ⇄ ĐT

    Column(modifier = modifier.fillMaxWidth()) {
        // Sub-navigation: PC ➔ PC hoặc ĐT ⇄ ĐT
        TabRow(
            selectedTabIndex = subTab,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                text = { Text("PC ➔ PC (Giữa 2 máy tính)", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                text = { Text("ĐT ⇄ ĐT (P2P Điện thoại)", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (subTab == 0) {
            PcToPcSection(viewModel = viewModel, profiles = profiles)
        } else {
            PhoneToPhoneSection(viewModel = viewModel)
        }
    }
}

// ============================================================
// PC ➔ PC Section
// ============================================================

@Composable
fun PcToPcSection(
    viewModel: MainViewModel,
    profiles: List<PcProfile>
) {
    val sourceProfile by viewModel.pcToPcSourceProfile.collectAsState()
    val targetProfile by viewModel.pcToPcTargetProfile.collectAsState()
    val targetSubFolder by viewModel.pcToPcTargetSubFolder.collectAsState()
    val isTransferring by viewModel.isTransferringPcToPc.collectAsState()
    val progressPercent by viewModel.pcToPcProgressPercent.collectAsState()
    val statusText by viewModel.pcToPcStatusText.collectAsState()
    val result by viewModel.pcToPcResult.collectAsState()

    val sourceFiles by viewModel.pcToPcSourceFiles.collectAsState()
    val isLoadingSourceFiles by viewModel.isLoadingPcToPcSourceFiles.collectAsState()
    val selectedItems by viewModel.selectedPcToPcItems.collectAsState()

    var showSourceDropdown by remember { mutableStateOf(false) }
    var showTargetDropdown by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Thông tin PC Nguồn và PC Đích
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Cấu hình chuyển dữ liệu giữa 2 PC",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Hàng chọn PC Nguồn và PC Đích
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cột PC Nguồn
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedCard(
                            onClick = { showSourceDropdown = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("PC Nguồn (Gửi):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = sourceProfile?.name ?: "Chưa chọn",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showSourceDropdown,
                            onDismissRequest = { showSourceDropdown = false }
                        ) {
                            profiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.name) },
                                    onClick = {
                                        viewModel.setPcToPcSourceProfile(profile)
                                        showSourceDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(24.dp)
                    )

                    // Cột PC Đích
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedCard(
                            onClick = { showTargetDropdown = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("PC Đích (Nhận):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = targetProfile?.name ?: "Chưa chọn",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showTargetDropdown,
                            onDismissRequest = { showTargetDropdown = false }
                        ) {
                            profiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.name) },
                                    onClick = {
                                        viewModel.setPcToPcTargetProfile(profile)
                                        showTargetDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Thư mục con đích trên PC nhận (tùy chọn)
                OutlinedTextField(
                    value = targetSubFolder,
                    onValueChange = { viewModel.setPcToPcTargetSubFolder(it) },
                    label = { Text("Thư mục con lưu trên PC đích (để trống nếu lưu gốc)") },
                    placeholder = { Text("VD: FromPC1 hoặc DataSync") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Danh sách tệp từ PC nguồn
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tệp trên PC nguồn (${sourceFiles.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row {
                        TextButton(onClick = { viewModel.loadPcToPcSourceFiles() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tải lại", fontSize = 12.sp)
                        }
                        if (sourceFiles.isNotEmpty()) {
                            TextButton(onClick = {
                                if (selectedItems.size == sourceFiles.size) {
                                    viewModel.clearSelectedPcToPcItems()
                                } else {
                                    viewModel.selectAllPcToPcItems()
                                }
                            }) {
                                Text(if (selectedItems.size == sourceFiles.size) "Bỏ chọn" else "Tất cả", fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (isLoadingSourceFiles) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else if (sourceFiles.isEmpty()) {
                    Text(
                        text = "Không tìm thấy tệp nào trên PC nguồn hoặc chưa kết nối.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        sourceFiles.forEach { item ->
                            val isSelected = selectedItems.contains(item)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleSelectPcToPcItem(item) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { viewModel.toggleSelectPcToPcItem(item) }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = if (item.isDirectory) Icons.Default.Folder else FileUtils.getFileIcon(item.name),
                                        contentDescription = null,
                                        tint = if (item.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (item.isDirectory) "Thư mục" else FileUtils.formatFileSize(item.size),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tiến trình chuyển
        if (isTransferring) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = statusText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }

        // Kết quả chuyển
        result?.let { res ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (res.success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = res.message,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (res.success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.dismissPcToPcResult() }) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }
            }
        }

        // Nút bấm thực hiện
        Button(
            onClick = { viewModel.transferSelectedBetweenPcs() },
            enabled = !isTransferring && selectedItems.isNotEmpty() && sourceProfile?.id != targetProfile?.id,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CHUYỂN ${selectedItems.size} MỤC TỪ ${sourceProfile?.name ?: "PC1"} ➔ ${targetProfile?.name ?: "PC2"}",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

// ============================================================
// ĐT ⇄ ĐT Section (Phone-to-Phone P2P)
// ============================================================

@Composable
fun PhoneToPhoneSection(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val isReceiverRunning by viewModel.isP2pReceiverRunning.collectAsState()
    val localIps by viewModel.p2pLocalIps.collectAsState()
    val receivedItems by viewModel.p2pReceivedItems.collectAsState()

    val targetIp by viewModel.p2pTargetIp.collectAsState()
    val filesToSend by viewModel.p2pFilesToSend.collectAsState()
    val linkToSend by viewModel.p2pLinkToSend.collectAsState()
    val isSending by viewModel.isP2pSending.collectAsState()
    val sendProgress by viewModel.p2pSendProgressPercent.collectAsState()
    val sendStatusText by viewModel.p2pSendStatusText.collectAsState()
    val sendResult by viewModel.p2pSendResult.collectAsState()

    // PeerDevice & Connection states
    val peerDevices by viewModel.peerDevices.collectAsState()
    val selectedPeerDevice by viewModel.selectedPeerDevice.collectAsState()
    val showPeerDeviceDialog by viewModel.showPeerDeviceDialog.collectAsState()
    val editingPeerDevice by viewModel.editingPeerDevice.collectAsState()
    val isTestingPeerConnection by viewModel.isTestingPeerConnection.collectAsState()
    val peerConnectionResult by viewModel.peerConnectionResult.collectAsState()
    val isScanningLanPeers by viewModel.isScanningLanPeers.collectAsState()
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()

    var showManageDevicesSheet by remember { mutableStateOf(false) }
    var deviceToConfirmDelete by remember { mutableStateOf<PeerDevice?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addP2pFilesToSend(context, uris)
        }
    }

    // Dialog thêm/sửa thiết bị đã được nâng lên hiển thị toàn cục ở HomeScreen.kt

    // Dialog xác nhận xóa
    deviceToConfirmDelete?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceToConfirmDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Xóa thiết bị?", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc muốn xóa \"${device.name}\" (${device.primaryIp}) khỏi danh sách không?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePeerDevice(device.id)
                        deviceToConfirmDelete = null
                    }
                ) { Text("Xóa", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deviceToConfirmDelete = null }) { Text("Hủy") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        // ============================================================
        // 1. THẺ NỐI GIỮA ĐT ⇄ ĐT (CONNECTION CONTROL & STATUS)
        // ============================================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header: Tiêu đề + Nút quản lý
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SwapCalls,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Nối giữa ĐT ⇄ ĐT",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Truyền trực tiếp giữa 2 máy qua WiFi / Tailscale",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TextButton(
                        onClick = { showManageDevicesSheet = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.ManageAccounts, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Quản lý ĐT", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dải Chip chọn nhanh thiết bị ĐT + Thêm ĐT + Quét tìm
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Chip Quét tìm ĐT trong mạng LAN
                    AssistChip(
                        onClick = { viewModel.scanLanPeers() },
                        enabled = !isScanningLanPeers,
                        label = {
                            Text(
                                text = if (isScanningLanPeers) "Đang dò..." else "🔍 Quét tìm ĐT",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            if (isScanningLanPeers) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.WifiFind, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Chip Thêm ĐT mới
                    AssistChip(
                        onClick = { viewModel.openAddPeerDeviceDialog() },
                        label = { Text("+ Thêm ĐT", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Danh sách các thiết bị đã lưu
                    peerDevices.forEach { device ->
                        val isSelected = selectedPeerDevice?.id == device.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectPeerDevice(device) },
                            label = {
                                Text(
                                    text = device.displayTitle,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    val dotColor = when {
                                        isTestingPeerConnection -> Color(0xFFFFA000)
                                        peerConnectionResult?.success == true -> SuccessGreen
                                        peerConnectionResult?.success == false -> ErrorRed
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                }
                            } else null,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bảng trạng thái của ĐT đang chọn
                val activeTargetIp = selectedPeerDevice?.primaryIp ?: targetIp
                val isLanRoute = activeTargetIp.startsWith("192.168.") || activeTargetIp.startsWith("10.") || activeTargetIp.startsWith("172.")
                val routeLabel = if (isLanRoute) "WiFi LAN" else if (activeTargetIp.startsWith("100.")) "Tailscale" else "Trực tiếp"

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedPeerDevice?.let { "Máy đích: ${it.displayTitle}" } ?: "Máy đích: ĐT nhập tay",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (activeTargetIp.isNotBlank()) activeTargetIp else "Chưa chọn thiết bị đích",
                                        fontSize = 12.sp,
                                        color = if (activeTargetIp.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                    if (activeTargetIp.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isLanRoute) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = routeLabel,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isLanRoute) SuccessGreen else MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Đèn trạng thái kết nối
                            val statusText = when {
                                isTestingPeerConnection -> "Đang thử..."
                                peerConnectionResult?.success == true -> "${peerConnectionResult?.latencyMs}ms"
                                peerConnectionResult?.success == false -> "Mất kết nối"
                                else -> "Chưa thử"
                            }
                            val statusColor = when {
                                isTestingPeerConnection -> Color(0xFFFFA000)
                                peerConnectionResult?.success == true -> SuccessGreen
                                peerConnectionResult?.success == false -> ErrorRed
                                else -> Color.Gray
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(statusColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = statusText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                        }

                        // Chi tiết thông báo kết nối nếu có
                        peerConnectionResult?.let { res ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = res.message,
                                fontSize = 11.sp,
                                color = if (res.success) SuccessGreen else ErrorRed
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hai nút hành động: "Thử kết nối ĐT" & "Quét tìm ĐT lân cận"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.testPeerConnection() },
                        enabled = !isTestingPeerConnection && activeTargetIp.isNotBlank(),
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isTestingPeerConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đang thử...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thử kết nối ĐT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.scanLanPeers() },
                        enabled = !isScanningLanPeers,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isScanningLanPeers) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đang quét...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.WifiFind, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Quét tìm ĐT", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // --- Kết quả Quét tìm thiết bị lân cận (Auto-Discovery) ---
                if (isScanningLanPeers) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Đang dò tìm thiết bị mở APPSharePC trong mạng WiFi...", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)))
                        }
                    }
                }

                if (discoveredPeers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "🔍 Tìm thấy ${discoveredPeers.size} thiết bị lân cận:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(onClick = { viewModel.clearDiscoveredPeers() }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Đóng", modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                discoveredPeers.forEach { peer ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Text(peer.deviceType.emoji, fontSize = 20.sp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(peer.deviceName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text("${peer.ip} (${peer.latencyMs}ms)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Button(
                                                onClick = { viewModel.connectToDiscoveredPeer(peer) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("NỐI NGAY", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Ô nhập IP thủ công (nếu người dùng muốn gõ IP ngoài danh bạ)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = targetIp,
                    onValueChange = { newIp ->
                        viewModel.setP2pTargetIp(newIp)
                        if (selectedPeerDevice != null && newIp != selectedPeerDevice?.primaryIp) {
                            viewModel.clearSelectedPeerDevice()
                        }
                    },
                    label = { Text("Hoặc gõ IP điện thoại nhận thủ công") },
                    placeholder = { Text("VD: 100.x.y.z (Tailscale) hoặc 192.168.1.x") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (targetIp.isNotBlank()) {
                            IconButton(onClick = { viewModel.clearSelectedPeerDevice() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Xóa", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ============================================================
        // 2. MÁY NHẬN TỆP (RECEIVER MODE)
        // ============================================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isReceiverRunning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isReceiverRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chế độ Nhận tệp trên máy này",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Switch(
                        checked = isReceiverRunning,
                        onCheckedChange = { viewModel.toggleP2pReceiver() }
                    )
                }

                if (isReceiverRunning) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Máy chủ tiếp nhận đang mở trên cổng 8999.\nThiết bị khác (Android, iPhone, PC) có thể gửi tệp qua các địa chỉ sau:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    localIps.second?.let { tsIp ->
                        IpRow("IP Tailscale:", "http://$tsIp:8999", clipboardManager, context)
                    }
                    localIps.first?.let { lanIp ->
                        IpRow("IP WiFi:", "http://$lanIp:8999", clipboardManager, context)
                    }

                    if (receivedItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Tệp vừa nhận (${receivedItems.size}):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            receivedItems.take(5).forEach { item ->
                                Text(
                                    text = "• ${item.fileName} (${item.formattedSize})",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Gạt công tắc để mở cổng nhận tệp trực tiếp từ điện thoại khác hoặc từ trình duyệt web.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ============================================================
        // 3. GỬI TỆP & GỬI LINK SANG ĐIỆN THOẠI ĐÃ NỐI
        // ============================================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Gửi tệp & Link sang ĐT đã nối",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Nút chọn tệp & Gửi tệp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chọn tệp gửi", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    if (filesToSend.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.sendP2pFiles() },
                            enabled = !isSending && (targetIp.isNotBlank() || selectedPeerDevice != null),
                            modifier = Modifier.weight(1.3f).height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GỬI ${filesToSend.size} TỆP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                // Danh sách tệp đã chọn
                if (filesToSend.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filesToSend.forEachIndexed { idx, f ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${f.name} (${f.formattedSize})",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.removeP2pFileToSend(idx) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // Gửi Link sang điện thoại khác
                Text("Hoặc gửi liên kết (Link) sang điện thoại:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = linkToSend,
                    onValueChange = { viewModel.setP2pLinkToSend(it) },
                    placeholder = { Text("https://... hoặc đoạn text") },
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboardManager.getText()?.text?.let { clip ->
                                if (clip.isNotBlank()) viewModel.setP2pLinkToSend(clip)
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Dán", modifier = Modifier.size(20.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.sendP2pLink() },
                    enabled = !isSending && (targetIp.isNotBlank() || selectedPeerDevice != null) && linkToSend.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GỬI LINK SANG ĐIỆN THOẠI (.TXT)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Tiến trình gửi
        if (isSending) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = sendStatusText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { sendProgress / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }

        // Kết quả gửi
        sendResult?.let { msg ->
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = msg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.dismissP2pSendResult() }) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }
            }
        }

        // --- Sheet quản lý thiết bị ---
        if (showManageDevicesSheet) {
            ManagePeerDevicesSheet(
                devices = peerDevices,
                onAdd = {
                    showManageDevicesSheet = false
                    viewModel.openAddPeerDeviceDialog()
                },
                onEdit = { device ->
                    showManageDevicesSheet = false
                    viewModel.openEditPeerDeviceDialog(device)
                },
                onDelete = { device ->
                    deviceToConfirmDelete = device
                    showManageDevicesSheet = false
                },
                onDismiss = { showManageDevicesSheet = false }
            )
        }
    }
}

// ============================================================
// Quản lý PeerDevice Dialog
// ============================================================

@Composable
fun ManagePeerDevicesSheet(
    devices: List<PeerDevice>,
    onAdd: () -> Unit,
    onEdit: (PeerDevice) -> Unit,
    onDelete: (PeerDevice) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Quản lý thiết bị P2P",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lưu IP các thiết bị thường dùng để gửi nhanh",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (devices.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Chưa có thiết bị nào",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Thêm điện thoại/máy tính bảng để gửi nhanh",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        devices.forEach { device ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = device.displayTitle,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = device.primaryIp,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (device.note.isNotBlank()) {
                                            Text(
                                                text = device.note,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                    Row {
                                        IconButton(onClick = { onEdit(device) }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Edit, contentDescription = "Sửa", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { onDelete(device) }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Xóa", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Thêm thiết bị mới", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================
// IP Row (dùng chung cho Receiver section)
// ============================================================

@Composable
fun IpRow(
    label: String,
    url: String,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: android.content.Context
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(url, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(url))
                    Toast.makeText(context, "Đã sao chép: $url", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Sao chép link", modifier = Modifier.size(18.dp))
            }
        }
    }
}
