package com.appsharepc.scanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun HelpGuideDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hướng dẫn chia sẻ thư mục trên Windows",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Ứng dụng kết nối trực tiếp vào giao thức chia sẻ file Windows (SMB) không cần cài thêm bất kỳ phần mềm hay server phụ nào trên PC. Thực hiện theo các bước sau trên máy tính Windows:",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                StepItem(
                    stepNumber = "1",
                    title = "Tạo và chia sẻ thư mục trên PC",
                    content = "Tạo 1 thư mục (VD: C:\\Scans hoặc D:\\SharedFolder).\nChuột phải vào thư mục -> Chọn Properties -> Chuyển sang thẻ Sharing -> Bấm nút Share..."
                )

                StepItem(
                    stepNumber = "2",
                    title = "Cấp quyền ghi (Read/Write)",
                    content = "Chọn tài khoản Windows của bạn (hoặc chọn Everyone nếu muốn mở nhanh) -> Cột Permission Level chọn 'Read/Write' -> Bấm nút Share."
                )

                StepItem(
                    stepNumber = "3",
                    title = "Bật tính năng Advanced Sharing",
                    content = "Cũng trong thẻ Sharing, bấm nút 'Advanced Sharing...' -> Tích chọn 'Share this folder' -> Bấm nút 'Permissions' -> Đảm bảo đã tích 'Full Control' hoặc ít nhất là 'Change' và 'Read' -> Bấm OK."
                )

                StepItem(
                    stepNumber = "4",
                    title = "Lấy địa chỉ IP của máy tính",
                    content = "Nhấn tổ hợp phím Windows + R -> gõ cmd -> gõ lệnh: ipconfig\nTìm dòng 'IPv4 Address' (VD: 192.168.1.15). Đây chính là IP cần nhập vào ứng dụng."
                )

                StepItem(
                    stepNumber = "5",
                    title = "Đảm bảo chung mạng Wi-Fi",
                    content = "Điện thoại và máy tính phải kết nối chung mạng Wi-Fi gia đình / văn phòng (hoặc phát Wi-Fi từ điện thoại sang PC)."
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Đã hiểu")
                }
            }
        }
    }
}

@Composable
private fun StepItem(stepNumber: String, title: String, content: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = " Bước $stepNumber ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )
    }
}
