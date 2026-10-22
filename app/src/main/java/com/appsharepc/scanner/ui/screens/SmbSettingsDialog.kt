package com.appsharepc.scanner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appsharepc.scanner.data.PcProfile
import com.appsharepc.scanner.data.SmbSettings
import com.appsharepc.scanner.service.SmbTestResult
import com.appsharepc.scanner.ui.theme.ErrorRed
import com.appsharepc.scanner.ui.theme.SuccessGreen

// Giữ nguyên logic cũ: tách IP cũ (pcIp) sang ô LAN / Tailscale
private fun initialLanIp(s: SmbSettings): String = when {
    s.lanIp.isNotBlank() -> s.lanIp
    s.pcIp.startsWith("192.") || s.pcIp.startsWith("10.") || s.pcIp.startsWith("172.") -> s.pcIp
    else -> ""
}

private fun initialTailscaleIp(s: SmbSettings): String = when {
    s.tailscaleIp.isNotBlank() -> s.tailscaleIp
    s.pcIp.startsWith("100.") || s.pcIp.contains(".ts.net") -> s.pcIp
    s.lanIp.isBlank() && s.pcIp.isNotBlank() -> s.pcIp
    else -> ""
}

/**
 * Màn hình Cài đặt thông tin PC (toàn trang).
 * - Nhóm "Kết nối" (bắt buộc) luôn hiện
 * - Nhóm "Đăng nhập Windows" thu gọn được
 * - Nhóm "Đồng bộ In 3D" bật/tắt bằng công tắc
 * - Nút Thử kết nối / Lưu ghim cố định ở đáy
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmbSettingsDialog(
    profile: PcProfile,
    isNew: Boolean,
    canDelete: Boolean,
    isTesting: Boolean,
    testResult: SmbTestResult?,
    onSave: (name: String, SmbSettings) -> Unit,
    onDelete: () -> Unit,
    onTestConnection: (name: String, SmbSettings) -> Unit,
    onDismissTestResult: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenHelp: () -> Unit,
    onDismiss: () -> Unit
) {
    val s = profile.settings

    // State gắn với id của profile: chỉ reset khi đổi sang máy khác,
    // không bị reset khi bấm "Thử kết nối" (trước đây làm mất tên vừa gõ).
    var profileName by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var lanIp by rememberSaveable(profile.id) { mutableStateOf(initialLanIp(s)) }
    var tailscaleIp by rememberSaveable(profile.id) { mutableStateOf(initialTailscaleIp(s)) }
    var shareName by rememberSaveable(profile.id) { mutableStateOf(s.shareName) }
    var subFolder by rememberSaveable(profile.id) { mutableStateOf(s.subFolder) }
    var username by rememberSaveable(profile.id) { mutableStateOf(s.username) }
    var password by rememberSaveable(profile.id) { mutableStateOf(s.password) }
    var domain by rememberSaveable(profile.id) { mutableStateOf(s.domain) }
    var enable3dSync by rememberSaveable(profile.id) { mutableStateOf(s.enable3dSync) }
    var sync3dIp by rememberSaveable(profile.id) { mutableStateOf(s.sync3dIp) }

    var loginExpanded by rememberSaveable(profile.id) {
        mutableStateOf(s.username.isNotBlank() || s.domain.isNotBlank())
    }
    var showErrors by rememberSaveable(profile.id) { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val cleanShare = shareName.trim().trim('\\', '/')
    val cleanSub = subFolder.trim().trim('\\', '/')

    val currentSettings = SmbSettings(
        pcIp = if (lanIp.isNotBlank()) lanIp.trim() else tailscaleIp.trim(),
        lanIp = lanIp.trim(),
        tailscaleIp = tailscaleIp.trim(),
        shareName = cleanShare,
        subFolder = cleanSub,
        username = username.trim(),
        password = password,
        domain = domain.trim(),
        enable3dSync = enable3dSync,
        sync3dIp = sync3dIp.trim()
    )

    // Kiểm tra ô bắt buộc
    val nameMissing = profileName.isBlank()
    val ipMissing = lanIp.isBlank() && tailscaleIp.isBlank()
    val shareMissing = cleanShare.isBlank()
    val canTest = !ipMissing && !shareMissing
    val canSave = canTest && !nameMissing

    // Xem trước đường dẫn mạng sẽ dùng
    val previewPath = buildString {
        append("\\\\")
        append(lanIp.trim().ifBlank { tailscaleIp.trim() }.ifBlank { "<IP>" })
        append("\\")
        append(cleanShare.ifBlank { "<Share>" })
        if (cleanSub.isNotBlank()) {
            append("\\")
            append(cleanSub.replace('/', '\\'))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng")
                        }
                    },
                    title = {
                        Text(
                            text = if (isNew) "Thêm máy tính" else "Cài đặt máy tính",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenHelp) {
                            Icon(Icons.Default.Info, contentDescription = "Hướng dẫn")
                        }
                        // Nút Lưu ghim cố định trên TopAppBar: Luôn nhìn thấy và bấm được trên máy tính bảng
                        Button(
                            onClick = {
                                if (canSave) {
                                    onSave(profileName.trim(), currentSettings)
                                    onDismiss()
                                } else {
                                    showErrors = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Lưu", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(4.dp))
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Thêm tùy chọn")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sao lưu & khôi phục") },
                                    leadingIcon = {
                                        Icon(Icons.Default.SettingsBackupRestore, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenBackup()
                                    }
                                )
                                if (canDelete && !isNew) {
                                    DropdownMenuItem(
                                        text = { Text("Xóa máy này") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                        },
                                        colors = MenuDefaults.itemColors(
                                            textColor = MaterialTheme.colorScheme.error,
                                            leadingIconColor = MaterialTheme.colorScheme.error
                                        ),
                                        onClick = {
                                            menuExpanded = false
                                            showDeleteConfirm = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        testResult?.let { res ->
                            StatusBanner(
                                success = res.success,
                                message = res.message,
                                onDismiss = onDismissTestResult
                            )
                        }
                        if (showErrors && !canSave) {
                            Text(
                                text = "Vui lòng điền các ô được đánh dấu đỏ.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (canTest) onTestConnection(profileName, currentSettings)
                                    else showErrors = true
                                },
                                enabled = !isTesting,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Đang thử...")
                                } else {
                                    Icon(
                                        Icons.Default.NetworkCheck,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Thử kết nối")
                                }
                            }
                            Button(
                                onClick = {
                                    if (canSave) {
                                        onSave(profileName.trim(), currentSettings)
                                        onDismiss()
                                    } else {
                                        showErrors = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Lưu")
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Banner nhắc nhở phân biệt PC và Điện thoại
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Màn hình này để cấu hình Máy tính PC (Windows SMB). Nếu muốn kết nối với Điện thoại / Tablet khác, hãy dùng tính năng 'Chuyển P2P' (không cần thư mục chia sẻ Windows).",
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // ===== 1. KẾT NỐI (bắt buộc) =====
                SettingsSection(
                    icon = Icons.Default.Computer,
                    title = "Kết nối",
                    subtitle = "Thông tin bắt buộc để gửi tệp tới PC"
                ) {
                    SettingsField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = "Tên gợi nhớ *",
                        icon = Icons.Default.Label,
                        placeholder = "VD: PC Nhà, PC Công ty",
                        isError = showErrors && nameMissing,
                        errorText = "Bắt buộc"
                    )
                    SettingsField(
                        value = lanIp,
                        onValueChange = { lanIp = it },
                        label = "IP mạng LAN / WiFi",
                        icon = Icons.Default.Wifi,
                        placeholder = "VD: 192.168.5.40",
                        supporting = "Ưu tiên dùng khi điện thoại chung WiFi với PC",
                        keyboardType = KeyboardType.Uri,
                        isError = showErrors && ipMissing,
                        errorText = "Cần ít nhất 1 IP (LAN hoặc Tailscale)"
                    )
                    SettingsField(
                        value = tailscaleIp,
                        onValueChange = { tailscaleIp = it },
                        label = "IP Tailscale / VPN",
                        icon = Icons.Default.Language,
                        placeholder = "VD: 100.99.195.54",
                        supporting = "Tự dùng khi ra ngoài / 4G (không vào được IP LAN)",
                        keyboardType = KeyboardType.Uri,
                        isError = showErrors && ipMissing,
                        errorText = "Cần ít nhất 1 IP (LAN hoặc Tailscale)"
                    )
                    SettingsField(
                        value = shareName,
                        onValueChange = { shareName = it },
                        label = "Tên thư mục chia sẻ (Share) *",
                        icon = Icons.Default.FolderShared,
                        placeholder = "VD: PDFs hoặc Scans",
                        supporting = "Dành cho máy tính Windows SMB (ĐT không có thư mục này, để nối ĐT hãy dùng Tab 'Chuyển P2P')",
                        isError = showErrors && shareMissing,
                        errorText = "Bắt buộc"
                    )
                    SettingsField(
                        value = subFolder,
                        onValueChange = { subFolder = it },
                        label = "Thư mục con (tùy chọn)",
                        icon = Icons.Default.Folder,
                        placeholder = "Để trống nếu lưu thẳng vào Share",
                        imeAction = ImeAction.Done
                    )
                    // Xem trước đường dẫn
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = "Tệp sẽ được gửi tới",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = previewPath,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // ===== 2. ĐĂNG NHẬP WINDOWS (thu gọn được) =====
                SettingsSection(
                    icon = Icons.Default.Lock,
                    title = "Đăng nhập Windows",
                    subtitle = if (username.isBlank()) "Không dùng tài khoản (khách)"
                               else "Tài khoản: ${username.trim()}",
                    expanded = loginExpanded,
                    onHeaderClick = { loginExpanded = !loginExpanded },
                    trailing = {
                        Icon(
                            imageVector = if (loginExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (loginExpanded) "Thu gọn" else "Mở rộng"
                        )
                    }
                ) {
                    SettingsField(
                        value = username,
                        onValueChange = { username = it },
                        label = "Tên đăng nhập",
                        icon = Icons.Default.Person,
                        placeholder = "Để trống nếu Share không cần đăng nhập"
                    )
                    SettingsField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Mật khẩu",
                        icon = Icons.Default.Key,
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility
                                                  else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu"
                                )
                            }
                        }
                    )
                    SettingsField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = "Workgroup / Domain",
                        icon = Icons.Default.Domain,
                        placeholder = "Thường để trống",
                        imeAction = ImeAction.Done
                    )
                }

                // ===== 3. ĐỒNG BỘ IN 3D (công tắc) =====
                SettingsSection(
                    icon = Icons.Default.Print,
                    title = "Đồng bộ In 3D",
                    subtitle = "Chỉ bật nếu PC này có thư mục Snap/Bambu",
                    expanded = enable3dSync,
                    onHeaderClick = { enable3dSync = !enable3dSync },
                    trailing = {
                        Switch(
                            checked = enable3dSync,
                            onCheckedChange = { enable3dSync = it }
                        )
                    }
                ) {
                    SettingsField(
                        value = sync3dIp,
                        onValueChange = { sync3dIp = it },
                        label = "IP máy chủ In 3D (tùy chọn)",
                        icon = Icons.Default.Bolt,
                        placeholder = "Để trống = dùng IP ở phần Kết nối",
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    )
                }

                Spacer(Modifier.height(72.dp))
            }
        }
    }

    // Xác nhận xóa máy
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Xóa \"${profile.name}\"?") },
            text = { Text("Cấu hình của máy này sẽ bị xóa khỏi ứng dụng. Không thể hoàn tác.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Xóa") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Hủy") }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Thành phần dùng chung
// ---------------------------------------------------------------------------

/** Một nhóm cài đặt dạng thẻ, có tiêu đề + nội dung (có thể thu gọn). */
@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    expanded: Boolean = true,
    onHeaderClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onHeaderClick != null) Modifier.clickable(onClick = onHeaderClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

/** Ô nhập chuẩn: 1 dòng, có icon, gợi ý và báo lỗi. */
@Composable
private fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    placeholder: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val supportingMessage: String? = if (isError && errorText != null) errorText else supporting
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else null,
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = trailingIcon,
        supportingText = if (supportingMessage != null) {
            { Text(supportingMessage) }
        } else null,
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        visualTransformation = visualTransformation,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

/** Dải thông báo kết quả (xanh = thành công, đỏ = lỗi). Dùng chung cho màn Sao lưu. */
@Composable
internal fun StatusBanner(
    success: Boolean,
    message: String,
    onDismiss: () -> Unit
) {
    val tint = if (success) SuccessGreen else ErrorRed
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = tint,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Đóng thông báo",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
