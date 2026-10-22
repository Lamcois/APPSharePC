package com.appsharepc.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsharepc.scanner.data.AllConnectionsReport
import com.appsharepc.scanner.data.PcProfile
import com.appsharepc.scanner.data.TargetConnectionStatus
import com.appsharepc.scanner.service.SmbTestResult
import com.appsharepc.scanner.ui.theme.ErrorRed
import com.appsharepc.scanner.ui.theme.SuccessGreen

@Composable
fun ConnectionStatusCard(
    profiles: List<PcProfile>,
    activeProfile: PcProfile,
    onSelectProfile: (String) -> Unit,
    onAddNewProfile: () -> Unit,
    isTesting: Boolean,
    testResult: SmbTestResult?,
    onTestConnection: () -> Unit,
    isCheckingAll: Boolean = false,
    allConnectionsReport: AllConnectionsReport? = null,
    onCheckAllConnections: () -> Unit = {},
    onDismissAllReport: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onDismissTestResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = activeProfile.settings

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Title & Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isTesting || isCheckingAll -> Color(0xFFFFA000)
                                    testResult?.success == true || allConnectionsReport?.smbPc?.isOnline == true -> SuccessGreen
                                    testResult?.success == false || (allConnectionsReport != null && !allConnectionsReport.smbPc.isOnline) -> ErrorRed
                                    !settings.isValid -> Color.Gray
                                    else -> Color.Gray
                                }
                            )
                    )
                    Text(
                        text = "Máy tính đích: ${activeProfile.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    IconButton(onClick = onOpenHelp, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Hướng dẫn",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cài đặt PC",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Quick Switch PC Profiles Chips
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                profiles.forEach { profile ->
                    val isSelected = profile.id == activeProfile.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectProfile(profile.id) },
                        label = {
                            Text(
                                text = profile.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                val chipDotColor = when {
                                    isTesting || isCheckingAll -> Color(0xFFFFA000)
                                    testResult?.success == true || allConnectionsReport?.smbPc?.isOnline == true -> SuccessGreen
                                    testResult?.success == false || (allConnectionsReport != null && !allConnectionsReport.smbPc.isOnline) -> ErrorRed
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(chipDotColor)
                                )
                            }
                        } else null,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Add Device Button (PC hoặc ĐT)
                AssistChip(
                    onClick = onAddNewProfile,
                    label = { Text("+ Thêm mới", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    shape = RoundedCornerShape(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (settings.isValid) {
                val effectiveIp = testResult?.activeIp?.ifBlank { null }
                    ?: allConnectionsReport?.smbPc?.routeType?.let { if (it.isNotBlank()) settings.primaryIp else null }
                    ?: settings.primaryIp
                val displayPath = settings.getTargetPathDescription(effectiveIp)
                val routeLabel = testResult?.routeType?.ifBlank { null }
                    ?: allConnectionsReport?.smbPc?.routeType?.ifBlank { null }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = displayPath,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (!routeLabel.isNullOrBlank() && (testResult?.success == true || allConnectionsReport?.smbPc?.isOnline == true)) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val isLan = routeLabel.contains("LAN")
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isLan) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isLan) Icons.Default.Wifi else Icons.Default.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (isLan) SuccessGreen else MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isLan) "LAN" else "Tailscale",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLan) SuccessGreen else MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Chưa cấu hình máy tính này. Bấm nút cài đặt để điền IP và thư mục.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Buttons: "Kiểm tra kết nối tất cả" & "Thử SMB"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onCheckAllConnections,
                    enabled = !isCheckingAll && !isTesting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    if (isCheckingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Đang kiểm tra...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kiểm tra kết nối tất cả", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onTestConnection,
                    enabled = !isTesting && !isCheckingAll && settings.isValid,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Thử SMB", fontSize = 12.sp)
                    }
                }
            }

            // Bảng hiển thị kết nối tất cả: XANH nếu kết nối được, ĐỎ nếu không
            allConnectionsReport?.let { report ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (report.allOnline) SuccessGreen else if (report.onlineCount > 0) Color(0xFFFFA000) else ErrorRed)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "KẾT QUẢ KẾT NỐI (${report.onlineCount}/${report.totalCount} HOẠT ĐỘNG)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = onDismissAllReport,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Đóng",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.Gray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            report.connectionItems.forEach { item ->
                                val icon = when {
                                    item.name.contains("LAN") || item.routeType.contains("LAN") -> Icons.Default.Wifi
                                    item.name.contains("Tailscale") || item.routeType.contains("Tailscale") -> Icons.Default.Language
                                    item.name.contains("Máy tính") -> Icons.Default.FolderShared
                                    item.name.contains("Đồng bộ 3D") -> Icons.Default.Bolt
                                    else -> Icons.Default.Print
                                }
                                ConnectionResultRow(item = item, icon = icon)
                            }
                        }
                    }
                }
            }

            // Kết quả kiểm tra kết nối SMB đơn lẻ (Test connection banner)
            testResult?.let { res ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (res.success) SuccessGreen.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (res.success) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (res.success) SuccessGreen else ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = res.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (res.success) SuccessGreen else ErrorRed,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissTestResult,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Đóng",
                                modifier = Modifier.size(16.dp),
                                tint = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionResultRow(
    item: TargetConnectionStatus,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val isOk = item.isOnline
    val statusBg = if (isOk) SuccessGreen.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.12f)
    val statusColor = if (isOk) SuccessGreen else ErrorRed
    val statusBorder = if (isOk) SuccessGreen.copy(alpha = 0.35f) else ErrorRed.copy(alpha = 0.35f)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = statusBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, statusBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.target,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (item.detail.isNotBlank()) {
                        Text(
                            text = item.detail,
                            fontSize = 10.sp,
                            color = statusColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Badge trạng thái: Xanh (KẾT NỐI) hoặc Đỏ (MẤT KẾT NỐI)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = statusColor
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOk) "KẾT NỐI" else "MẤT KẾT NỐI",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
