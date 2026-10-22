package com.appsharepc.scanner.data

data class SmbSettings(
    val pcIp: String = "",
    val lanIp: String = "",
    val tailscaleIp: String = "",
    val shareName: String = "",
    val subFolder: String = "",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val port: Int = 445,
    val enable3dSync: Boolean = false,
    val sync3dIp: String = ""
) {
    val primaryIp: String
        get() = when {
            lanIp.isNotBlank() -> lanIp.trim()
            tailscaleIp.isNotBlank() -> tailscaleIp.trim()
            else -> pcIp.trim()
        }

    fun getCandidateIps(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        if (lanIp.isNotBlank()) list.add(Pair(lanIp.trim(), "LAN (WiFi)"))
        if (tailscaleIp.isNotBlank()) list.add(Pair(tailscaleIp.trim(), "Tailscale (Từ xa)"))
        if (list.isEmpty() && pcIp.isNotBlank()) list.add(Pair(pcIp.trim(), "Mặc định"))
        return list
    }

    val isValid: Boolean
        get() = (lanIp.isNotBlank() || tailscaleIp.isNotBlank() || pcIp.isNotBlank()) && shareName.isNotBlank()

    fun getTargetPathDescription(ipToUse: String = primaryIp): String {
        val ip = if (ipToUse.isNotBlank()) ipToUse else primaryIp
        return if (subFolder.isBlank()) {
            "\\\\$ip\\$shareName"
        } else {
            "\\\\$ip\\$shareName\\${subFolder.trim('/', '\\')}"
        }
    }

    val targetPathDescription: String
        get() = getTargetPathDescription(primaryIp)
}

data class TargetConnectionStatus(
    val name: String,
    val target: String,
    val isOnline: Boolean,
    val detail: String = "",
    val routeType: String = ""
)

data class AllConnectionsReport(
    val connectionItems: List<TargetConnectionStatus>,
    val checkedAt: Long = System.currentTimeMillis()
) {
    val allOnline: Boolean
        get() = connectionItems.isNotEmpty() && connectionItems.all { it.isOnline }

    val onlineCount: Int
        get() = connectionItems.count { it.isOnline }

    val totalCount: Int
        get() = connectionItems.size

    val smbPc: TargetConnectionStatus
        get() = connectionItems.firstOrNull { it.name.startsWith("Máy tính") && it.isOnline }
            ?: connectionItems.firstOrNull { it.name.startsWith("Máy tính") }
            ?: TargetConnectionStatus("Máy tính Windows (SMB)", "", false)

    val pcSyncService: TargetConnectionStatus
        get() = connectionItems.firstOrNull { it.name.contains("Đồng bộ 3D") }
            ?: TargetConnectionStatus("Dịch vụ Đồng bộ 3D (PC)", "", false)

    val snapmaker: TargetConnectionStatus
        get() = connectionItems.firstOrNull { it.name.contains("Snapmaker") }
            ?: TargetConnectionStatus("Máy in Snapmaker", "", false)

    val bambuddy: TargetConnectionStatus
        get() = connectionItems.firstOrNull { it.name.contains("Bambuddy") }
            ?: TargetConnectionStatus("Máy in Bambuddy", "", false)
}
