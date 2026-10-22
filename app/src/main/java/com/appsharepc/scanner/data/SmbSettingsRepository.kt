package com.appsharepc.scanner.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SmbSettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("smb_settings_pref", Context.MODE_PRIVATE)

    fun loadProfiles(): List<PcProfile> {
        val jsonStr = prefs.getString(KEY_PROFILES_JSON, null)
        if (jsonStr.isNullOrBlank()) {
            // Check legacy single settings
            val legacy = loadLegacySettings()
            val initial = if (legacy.isValid) {
                listOf(PcProfile(id = UUID.randomUUID().toString(), name = "PC Mặc định", settings = legacy))
            } else {
                listOf(PcProfile(id = UUID.randomUUID().toString(), name = "PC Mặc định", settings = SmbSettings()))
            }
            saveProfiles(initial, initial.first().id)
            return initial
        }

        return try {
            val list = mutableListOf<PcProfile>()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val rawPcIp = obj.optString("pcIp", "")
                var loadedLanIp = obj.optString("lanIp", "")
                var loadedTailscaleIp = obj.optString("tailscaleIp", "")

                // Migration if lanIp and tailscaleIp are blank but rawPcIp exists
                if (loadedLanIp.isBlank() && loadedTailscaleIp.isBlank() && rawPcIp.isNotBlank()) {
                    if (rawPcIp.startsWith("192.") || rawPcIp.startsWith("10.") || rawPcIp.startsWith("172.")) {
                        loadedLanIp = rawPcIp
                    } else if (rawPcIp.startsWith("100.") || rawPcIp.contains(".ts.net")) {
                        loadedTailscaleIp = rawPcIp
                    } else {
                        loadedLanIp = rawPcIp
                    }
                }

                val settings = SmbSettings(
                    pcIp = rawPcIp,
                    lanIp = loadedLanIp,
                    tailscaleIp = loadedTailscaleIp,
                    shareName = obj.optString("shareName", ""),
                    subFolder = obj.optString("subFolder", ""),
                    username = obj.optString("username", ""),
                    password = obj.optString("password", ""),
                    domain = obj.optString("domain", ""),
                    port = obj.optInt("port", 445),
                    enable3dSync = obj.optBoolean("enable3dSync", false),
                    sync3dIp = obj.optString("sync3dIp", "")
                )
                val profile = PcProfile(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", "PC ${i + 1}"),
                    settings = settings
                )
                list.add(profile)
            }
            if (list.isEmpty()) {
                val defaultProfile = PcProfile(name = "PC Mặc định")
                listOf(defaultProfile)
            } else {
                list
            }
        } catch (e: Exception) {
            e.printStackTrace()
            listOf(PcProfile(name = "PC Mặc định"))
        }
    }

    fun getActiveProfileId(): String {
        return prefs.getString(KEY_ACTIVE_PROFILE_ID, "") ?: ""
    }

    fun getActiveProfile(): PcProfile {
        val profiles = loadProfiles()
        val activeId = getActiveProfileId()
        return profiles.find { it.id == activeId } ?: profiles.firstOrNull() ?: PcProfile()
    }

    fun saveProfiles(profiles: List<PcProfile>, activeId: String) {
        val jsonArray = JSONArray()
        for (p in profiles) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("pcIp", p.settings.primaryIp)
                put("lanIp", p.settings.lanIp.trim())
                put("tailscaleIp", p.settings.tailscaleIp.trim())
                put("shareName", p.settings.shareName.trim().trim('\\', '/'))
                put("subFolder", p.settings.subFolder.trim().trim('\\', '/'))
                put("username", p.settings.username.trim())
                put("password", p.settings.password)
                put("domain", p.settings.domain.trim())
                put("port", p.settings.port)
                put("enable3dSync", p.settings.enable3dSync)
                put("sync3dIp", p.settings.sync3dIp.trim())
            }
            jsonArray.put(obj)
        }

        prefs.edit()
            .putString(KEY_PROFILES_JSON, jsonArray.toString())
            .putString(KEY_ACTIVE_PROFILE_ID, activeId)
            .apply()
    }

    fun setActiveProfileId(activeId: String) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, activeId).apply()
    }

    fun addOrUpdateProfile(profile: PcProfile, makeActive: Boolean = true): List<PcProfile> {
        val current = loadProfiles().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        val targetActiveId = if (makeActive) profile.id else getActiveProfileId()
        saveProfiles(current, targetActiveId)
        return current
    }

    fun deleteProfile(id: String): List<PcProfile> {
        val current = loadProfiles().toMutableList()
        if (current.size <= 1) {
            // Don't delete last profile, just reset it
            val reset = PcProfile(id = current.first().id, name = "PC Mặc định", settings = SmbSettings())
            saveProfiles(listOf(reset), reset.id)
            return listOf(reset)
        }

        current.removeAll { it.id == id }
        val newActiveId = if (getActiveProfileId() == id) current.first().id else getActiveProfileId()
        saveProfiles(current, newActiveId)
        return current
    }

    fun exportBackupJson(): String {
        val profiles = loadProfiles()
        val activeId = getActiveProfileId()

        val jsonArray = JSONArray()
        for (p in profiles) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("pcIp", p.settings.primaryIp)
                put("lanIp", p.settings.lanIp.trim())
                put("tailscaleIp", p.settings.tailscaleIp.trim())
                put("shareName", p.settings.shareName.trim().trim('\\', '/'))
                put("subFolder", p.settings.subFolder.trim().trim('\\', '/'))
                put("username", p.settings.username.trim())
                put("password", p.settings.password)
                put("domain", p.settings.domain.trim())
                put("port", p.settings.port)
                put("enable3dSync", p.settings.enable3dSync)
                put("sync3dIp", p.settings.sync3dIp.trim())
            }
            jsonArray.put(obj)
        }

        val rootObj = JSONObject().apply {
            put("version", 1)
            put("appName", "APPSharePC")
            put("exportedAt", System.currentTimeMillis())
            put("activeProfileId", activeId)
            put("profiles", jsonArray)
        }

        return rootObj.toString(2)
    }

    fun importBackupJson(jsonStr: String): Result<Int> {
        return try {
            val trimmed = jsonStr.trim()
            val (profilesArray, loadedActiveId) = if (trimmed.startsWith("{")) {
                val rootObj = JSONObject(trimmed)
                val arr = rootObj.optJSONArray("profiles") ?: JSONArray()
                val activeId = rootObj.optString("activeProfileId", "")
                Pair(arr, activeId)
            } else if (trimmed.startsWith("[")) {
                Pair(JSONArray(trimmed), "")
            } else {
                return Result.failure(IllegalArgumentException("Định dạng tệp không hợp lệ! Vui lòng chọn tệp .json sao lưu của APPSharePC."))
            }

            if (profilesArray.length() == 0) {
                return Result.failure(IllegalArgumentException("Tệp sao lưu không chứa bất kỳ cấu hình máy tính nào."))
            }

            val list = mutableListOf<PcProfile>()
            for (i in 0 until profilesArray.length()) {
                val obj = profilesArray.getJSONObject(i)
                val rawPcIp = obj.optString("pcIp", "")
                var loadedLanIp = obj.optString("lanIp", "")
                var loadedTailscaleIp = obj.optString("tailscaleIp", "")

                if (loadedLanIp.isBlank() && loadedTailscaleIp.isBlank() && rawPcIp.isNotBlank()) {
                    if (rawPcIp.startsWith("192.") || rawPcIp.startsWith("10.") || rawPcIp.startsWith("172.")) {
                        loadedLanIp = rawPcIp
                    } else if (rawPcIp.startsWith("100.") || rawPcIp.contains(".ts.net")) {
                        loadedTailscaleIp = rawPcIp
                    } else {
                        loadedLanIp = rawPcIp
                    }
                }

                val settings = SmbSettings(
                    pcIp = rawPcIp,
                    lanIp = loadedLanIp,
                    tailscaleIp = loadedTailscaleIp,
                    shareName = obj.optString("shareName", ""),
                    subFolder = obj.optString("subFolder", ""),
                    username = obj.optString("username", ""),
                    password = obj.optString("password", ""),
                    domain = obj.optString("domain", ""),
                    port = obj.optInt("port", 445),
                    enable3dSync = obj.optBoolean("enable3dSync", false),
                    sync3dIp = obj.optString("sync3dIp", "")
                )
                val profile = PcProfile(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", "PC ${i + 1}"),
                    settings = settings
                )
                list.add(profile)
            }

            if (list.isEmpty()) {
                return Result.failure(IllegalArgumentException("Không tìm thấy cấu hình hợp lệ trong tệp sao lưu."))
            }

            val finalActiveId = if (loadedActiveId.isNotBlank() && list.any { it.id == loadedActiveId }) {
                loadedActiveId
            } else {
                list.first().id
            }

            saveProfiles(list, finalActiveId)
            Result.success(list.size)
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi đọc tệp sao lưu: ${e.localizedMessage ?: e.message}"))
        }
    }

    private fun loadLegacySettings(): SmbSettings {
        val legacyIp = prefs.getString(KEY_PC_IP, "") ?: ""
        val isLan = legacyIp.startsWith("192.") || legacyIp.startsWith("10.") || legacyIp.startsWith("172.")
        return SmbSettings(
            pcIp = legacyIp,
            lanIp = if (isLan) legacyIp else "",
            tailscaleIp = if (!isLan && legacyIp.isNotBlank()) legacyIp else "",
            shareName = prefs.getString(KEY_SHARE_NAME, "") ?: "",
            subFolder = prefs.getString(KEY_SUB_FOLDER, "") ?: "",
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            password = prefs.getString(KEY_PASSWORD, "") ?: "",
            domain = prefs.getString(KEY_DOMAIN, "") ?: "",
            port = prefs.getInt(KEY_PORT, 445)
        )
    }

    companion object {
        private const val KEY_PROFILES_JSON = "key_profiles_json"
        private const val KEY_ACTIVE_PROFILE_ID = "key_active_profile_id"
        private const val KEY_PC_IP = "key_pc_ip"
        private const val KEY_SHARE_NAME = "key_share_name"
        private const val KEY_SUB_FOLDER = "key_sub_folder"
        private const val KEY_USERNAME = "key_username"
        private const val KEY_PASSWORD = "key_password"
        private const val KEY_DOMAIN = "key_domain"
        private const val KEY_PORT = "key_port"
        private const val KEY_PEER_DEVICES_JSON = "key_peer_devices_json"
    }

    // ==========================================
    // PeerDevice CRUD (Quản lý thiết bị P2P)
    // ==========================================

    fun loadPeerDevices(): List<PeerDevice> {
        val jsonStr = prefs.getString(KEY_PEER_DEVICES_JSON, null)
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            val list = mutableListOf<PeerDevice>()
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val typeStr = obj.optString("type", PeerDeviceType.PHONE.name)
                val type = try {
                    PeerDeviceType.valueOf(typeStr)
                } catch (e: Exception) {
                    PeerDeviceType.PHONE
                }
                val rawIp = obj.optString("ip", "")
                var loadedLanIp = obj.optString("lanIp", "")
                var loadedTailscaleIp = obj.optString("tailscaleIp", "")
                if (loadedLanIp.isBlank() && loadedTailscaleIp.isBlank() && rawIp.isNotBlank()) {
                    if (rawIp.startsWith("192.") || rawIp.startsWith("10.") || rawIp.startsWith("172.")) {
                        loadedLanIp = rawIp
                    } else if (rawIp.startsWith("100.") || rawIp.contains(".ts.net")) {
                        loadedTailscaleIp = rawIp
                    } else {
                        loadedLanIp = rawIp
                    }
                }
                list.add(
                    PeerDevice(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Thiết bị"),
                        ip = rawIp.ifBlank { if (loadedLanIp.isNotBlank()) loadedLanIp else loadedTailscaleIp },
                        lanIp = loadedLanIp,
                        tailscaleIp = loadedTailscaleIp,
                        port = obj.optInt("port", 8999),
                        type = type,
                        note = obj.optString("note", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun savePeerDevices(devices: List<PeerDevice>) {
        val arr = JSONArray()
        for (d in devices) {
            val obj = JSONObject().apply {
                val primary = if (d.lanIp.isNotBlank()) d.lanIp.trim() else if (d.tailscaleIp.isNotBlank()) d.tailscaleIp.trim() else d.ip.trim()
                put("id", d.id)
                put("name", d.name)
                put("ip", primary)
                put("lanIp", d.lanIp.trim())
                put("tailscaleIp", d.tailscaleIp.trim())
                put("port", d.port)
                put("type", d.type.name)
                put("note", d.note)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PEER_DEVICES_JSON, arr.toString()).apply()
    }

    fun addOrUpdatePeerDevice(device: PeerDevice): List<PeerDevice> {
        val current = loadPeerDevices().toMutableList()
        val index = current.indexOfFirst { it.id == device.id }
        if (index >= 0) {
            current[index] = device
        } else {
            current.add(device)
        }
        savePeerDevices(current)
        return current
    }

    fun deletePeerDevice(id: String): List<PeerDevice> {
        val current = loadPeerDevices().toMutableList()
        current.removeAll { it.id == id }
        savePeerDevices(current)
        return current
    }
}

