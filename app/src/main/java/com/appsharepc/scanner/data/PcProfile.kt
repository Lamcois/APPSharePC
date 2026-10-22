package com.appsharepc.scanner.data

import java.util.UUID

data class PcProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "PC Mặc định",
    val settings: SmbSettings = SmbSettings()
)
