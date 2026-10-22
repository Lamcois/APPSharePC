package com.appsharepc.scanner.data

import java.util.UUID

enum class PeerDeviceType(val label: String, val emoji: String) {
    PHONE("Điện thoại", "📱"),
    TABLET("Máy tính bảng", "📟"),
    PC("Máy tính PC", "🖥️"),
    LAPTOP("Laptop", "💻")
}

data class PeerDevice(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Thiết bị mới",
    val ip: String = "",
    val lanIp: String = "",
    val tailscaleIp: String = "",
    val port: Int = 8999,
    val type: PeerDeviceType = PeerDeviceType.PHONE,
    val note: String = ""
) {
    val displayTitle: String
        get() = "${type.emoji} $name"

    val primaryIp: String
        get() = when {
            lanIp.isNotBlank() -> lanIp.trim()
            tailscaleIp.isNotBlank() -> tailscaleIp.trim()
            else -> ip.trim()
        }

    val isValid: Boolean
        get() = primaryIp.isNotBlank() && name.isNotBlank()
}
