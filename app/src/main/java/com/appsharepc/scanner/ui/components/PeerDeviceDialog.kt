package com.appsharepc.scanner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appsharepc.scanner.data.PeerDevice
import com.appsharepc.scanner.data.PeerDeviceType
import com.appsharepc.scanner.service.P2PTransferManager
import com.appsharepc.scanner.service.PeerTestResult
import com.appsharepc.scanner.ui.theme.ErrorRed
import com.appsharepc.scanner.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

@Composable
fun PeerDeviceDialog(
    device: PeerDevice,
    onDismiss: () -> Unit,
    onSave: (PeerDevice) -> Unit
) {
    var name by remember(device.id) { mutableStateOf(device.name) }
    var lanIp by remember(device.id) {
        mutableStateOf(
            if (device.lanIp.isNotBlank()) device.lanIp
            else if (device.ip.startsWith("192.") || device.ip.startsWith("10.") || device.ip.startsWith("172.")) device.ip
            else ""
        )
    }
    var tailscaleIp by remember(device.id) {
        mutableStateOf(
            if (device.tailscaleIp.isNotBlank()) device.tailscaleIp
            else if (device.ip.startsWith("100.") || device.ip.contains(".ts.net")) device.ip
            else if (device.lanIp.isBlank() && device.ip.isNotBlank()) device.ip
            else ""
        )
    }
    var selectedType by remember(device.id) { mutableStateOf(device.type) }
    var note by remember(device.id) { mutableStateOf(device.note) }
    var showTypeDropdown by remember { mutableStateOf(false) }

    // State thử kết nối
    val coroutineScope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<PeerTestResult?>(null) }

    val isEditing = device.primaryIp.isNotBlank() || (device.name.isNotBlank() && device.name != "Thiết bị mới")
    val canSave = (lanIp.isNotBlank() || tailscaleIp.isNotBlank()) && name.isNotBlank()

    fun doSave() {
        val trimmedLan = lanIp.trim()
        val trimmedTs = tailscaleIp.trim()
        val primary = if (trimmedLan.isNotBlank()) trimmedLan else trimmedTs
        val trimmedName = name.trim().ifBlank { selectedType.label }
        onSave(
            device.copy(
                name = trimmedName,
                ip = primary,
                lanIp = trimmedLan,
                tailscaleIp = trimmedTs,
                type = selectedType,
                note = note.trim()
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                // Title Row + Save Button trên header (luôn bấm được trên tablet/landscape)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = when (selectedType) {
                                PeerDeviceType.TABLET -> Icons.Default.TabletAndroid
                                PeerDeviceType.PC, PeerDeviceType.LAPTOP -> Icons.Default.Computer
                                else -> Icons.Default.PhoneAndroid
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEditing) "Cài đặt ĐT / Tablet" else "Thêm ĐT / Tablet mới",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Nút Lưu nhanh ở Header
                    Button(
                        onClick = { if (canSave) doSave() },
                        enabled = canSave,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lưu", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Banner thông tin P2P
                val effectivePort = if (device.port > 0) device.port else P2PTransferManager.DEFAULT_PORT
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚡ Kết nối P2P truyền file/link trực tiếp giữa 2 thiết bị di động (Cổng $effectivePort). Không cần chia sẻ thư mục Windows.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Loại thiết bị (dropdown)
                Text(
                    "Loại thiết bị:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box {
                    OutlinedCard(
                        onClick = { showTypeDropdown = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${selectedType.emoji} ${selectedType.label}",
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                    DropdownMenu(
                        expanded = showTypeDropdown,
                        onDismissRequest = { showTypeDropdown = false }
                    ) {
                        PeerDeviceType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(type.emoji, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(type.label, fontWeight = FontWeight.Medium)
                                    }
                                },
                                onClick = {
                                    selectedType = type
                                    showTypeDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tên hiển thị
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên hiển thị *") },
                    placeholder = { Text("VD: Điện thoại Ngân, iPad Phòng khách...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // IP Mạng LAN / WiFi
                OutlinedTextField(
                    value = lanIp,
                    onValueChange = { lanIp = it },
                    label = { Text("IP mạng LAN / WiFi (Ưu tiên khi chung WiFi)") },
                    placeholder = { Text("VD: 192.168.1.50") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // IP Tailscale (Từ xa)
                OutlinedTextField(
                    value = tailscaleIp,
                    onValueChange = { tailscaleIp = it },
                    label = { Text("IP Tailscale / VPN (Tùy chọn - Dùng khi ra ngoài)") },
                    placeholder = { Text("VD: 100.x.y.z") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Ghi chú (tùy chọn)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Ghi chú (tùy chọn)") },
                    placeholder = { Text("VD: Phòng làm việc / Tailscale") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Notes, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Nút Thử kết nối ngay tới ĐT này
                OutlinedButton(
                    onClick = {
                        val testIp = if (lanIp.isNotBlank()) lanIp.trim() else tailscaleIp.trim()
                        if (testIp.isNotBlank()) {
                            coroutineScope.launch {
                                isTesting = true
                                testResult = null
                                testResult = P2PTransferManager.testPeerConnection(testIp)
                                isTesting = false
                            }
                        }
                    },
                    enabled = !isTesting && (lanIp.isNotBlank() || tailscaleIp.isNotBlank()),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đang thử kết nối...")
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Thử kết nối tới thiết bị này", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Banner kết quả test
                testResult?.let { res ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (res.success) SuccessGreen.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (res.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (res.success) SuccessGreen else ErrorRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = res.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (res.success) SuccessGreen else ErrorRed,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { testResult = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Đóng", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Buttons: Hủy & Lưu thiết bị
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Hủy")
                    }
                    Button(
                        onClick = { if (canSave) doSave() },
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lưu thiết bị", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
}
