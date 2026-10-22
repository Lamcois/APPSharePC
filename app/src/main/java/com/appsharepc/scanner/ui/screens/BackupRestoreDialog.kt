package com.appsharepc.scanner.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sao lưu & khôi phục toàn bộ danh sách PC.
 * Tách riêng khỏi màn Cài đặt PC vì thao tác này áp dụng cho TẤT CẢ máy,
 * không phải riêng máy đang sửa.
 *
 * 3 thao tác chính:
 *  1. Lưu tệp sao lưu (vào Download; nút phụ để chọn nơi khác)
 *  2. Khôi phục từ tệp
 *  3. Mã cấu hình (xem / copy / chia sẻ / dán mã để khôi phục)
 */
@Composable
fun BackupRestoreDialog(
    profileCount: Int,
    onAutoSaveBackup: () -> Result<Pair<String, Int>>,
    onBackup: (Uri) -> Result<Int>,
    onRestore: (Uri) -> Result<Int>,
    onRestoreFromString: (String) -> Result<Int>,
    onShareBackup: () -> Unit,
    onGetBackupJson: () -> String,
    onDismiss: () -> Unit
) {
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    // Hành động khôi phục đang chờ xác nhận (từ tệp hoặc từ mã dán)
    var pendingRestore by remember { mutableStateOf<(() -> Result<Int>)?>(null) }
    var showCodeDialog by remember { mutableStateOf(false) }

    fun handleRestoreResult(res: Result<Int>) {
        message = res.fold(
            onSuccess = { count -> true to "Khôi phục thành công $count cấu hình máy tính!" },
            onFailure = { false to "Khôi phục thất bại: ${it.localizedMessage ?: it.message}" }
        )
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            message = onBackup(uri).fold(
                onSuccess = { bytes -> true to "Đã sao lưu thành công ($bytes bytes)." },
                onFailure = { false to "Lỗi sao lưu: ${it.localizedMessage ?: it.message}" }
            )
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingRestore = { onRestore(uri) }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tiêu đề
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SettingsBackupRestore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Sao lưu & khôi phục",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "Áp dụng cho toàn bộ $profileCount máy tính đã lưu trong ứng dụng.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. Lưu tệp sao lưu
                BackupActionRow(
                    icon = Icons.Default.Save,
                    title = "Lưu tệp sao lưu",
                    subtitle = "Lưu vào thư mục Download của điện thoại",
                    onClick = {
                        message = onAutoSaveBackup().fold(
                            onSuccess = { (path, bytes) -> true to "Đã lưu ($bytes bytes) vào:\n$path" },
                            onFailure = { false to "Lỗi lưu: ${it.localizedMessage ?: it.message}" }
                        )
                    },
                    trailing = {
                        IconButton(onClick = {
                            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            backupLauncher.launch("APPSharePC_Backup_$time.json")
                        }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Chọn nơi lưu khác")
                        }
                    }
                )

                // 2. Khôi phục từ tệp
                BackupActionRow(
                    icon = Icons.Default.Restore,
                    title = "Khôi phục từ tệp",
                    subtitle = "Chọn tệp .json đã sao lưu trước đó",
                    onClick = { restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }
                )

                // 3. Mã cấu hình
                BackupActionRow(
                    icon = Icons.Default.Code,
                    title = "Mã cấu hình",
                    subtitle = "Copy / chia sẻ mã sang máy khác, hoặc dán mã để khôi phục",
                    onClick = { showCodeDialog = true }
                )

                message?.let { (success, msg) ->
                    StatusBanner(success = success, message = msg, onDismiss = { message = null })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Đóng") }
                }
            }
        }
    }

    // Hộp thoại Mã cấu hình (gộp Xem/Copy + Chia sẻ + Dán mã)
    if (showCodeDialog) {
        val clipboard = LocalClipboardManager.current
        val json = remember { onGetBackupJson() }
        var pasted by remember { mutableStateOf("") }
        var showPreview by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCodeDialog = false },
            title = { Text("Mã cấu hình") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Gửi mã này sang điện thoại khác",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(json))
                                showCodeDialog = false
                                message = true to "Đã copy mã cấu hình vào bộ nhớ tạm."
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy")
                        }
                        FilledTonalButton(
                            onClick = onShareBackup,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Chia sẻ")
                        }
                    }
                    TextButton(
                        onClick = { showPreview = !showPreview },
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        Text(if (showPreview) "Ẩn nội dung mã" else "Xem nội dung mã")
                    }
                    if (showPreview) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                        ) {
                            Text(
                                text = json,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(10.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "Khôi phục từ mã nhận được",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        placeholder = { Text("Dán mã cấu hình vào đây...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 160.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val text = pasted.trim()
                        showCodeDialog = false
                        pendingRestore = { onRestoreFromString(text) }
                    },
                    enabled = pasted.isNotBlank()
                ) { Text("Khôi phục") }
            },
            dismissButton = {
                TextButton(onClick = { showCodeDialog = false }) { Text("Đóng") }
            }
        )
    }

    // Xác nhận trước khi khôi phục (dùng chung cho tệp và mã dán)
    pendingRestore?.let { restore ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            icon = { Icon(Icons.Default.SettingsBackupRestore, contentDescription = null) },
            title = { Text("Khôi phục cấu hình?") },
            text = {
                Text("Danh sách máy tính hiện có sẽ được thay thế hoặc cập nhật theo dữ liệu khôi phục.")
            },
            confirmButton = {
                Button(onClick = {
                    pendingRestore = null
                    handleRestoreResult(restore())
                }) { Text("Khôi phục") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Hủy") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailing != null) trailing() else Spacer(Modifier.width(12.dp))
        }
    }
}
