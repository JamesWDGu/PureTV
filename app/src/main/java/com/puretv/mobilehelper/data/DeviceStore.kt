package com.puretv.mobilehelper.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class SavedDevice(
    val serial: String = "",
    val host: String,
    val port: Int,
    val name: String,
    val customName: String = "",
    val lastConnectedAt: Long,
    val networkDefaults: NetworkSettings? = null,
) {
    val id: String get() = "$host:$port"
    val address: String get() = "$host:$port"
    val displayName: String get() = customName.ifBlank { name }
}

/**
 * Single source of truth for all saved-device data. Each record carries connection metadata and
 * the first-seen NTP / Captive Portal baseline, so deleting a device removes its associated data.
 *
 * All future per-device metadata should be added to [SavedDevice] and serialized here; do not
 * create a second device preferences file.
 */
class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<SavedDevice> {
        val raw = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        SavedDevice(
                            serial = item.optString("serial", ""),
                            host = item.getString("host"),
                            port = item.getInt("port"),
                            name = item.optString("name").ifBlank { item.getString("host") },
                            customName = item.optString("customName", ""),
                            lastConnectedAt = item.optLong("lastConnectedAt"),
                            networkDefaults = item.optJSONObject("networkDefaults")
                                ?.let(::networkSettingsFromJson),
                        )
                    )
                }
            }.sortedByDescending { it.lastConnectedAt }
                .distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    /** Inserts or updates a successful connection and returns the updated list. */
    fun upsert(
        host: String,
        port: Int,
        name: String,
        serial: String = "",
    ): List<SavedDevice> {
        val address = "$host:$port"
        val usableSerial = usableSerial(serial)
        val devices = load().toMutableList()
        var index = if (usableSerial.isNotBlank()) {
            devices.indexOfFirst { it.serial == usableSerial }
        } else {
            -1
        }
        if (index < 0) {
            index = devices.indexOfFirst { it.address == address }
        }

        val existing = devices.getOrNull(index)
        val sameDevice = existing != null && (
            usableSerial.isBlank() ||
                existing.serial.isBlank() ||
                existing.serial == usableSerial
            )
        val record = SavedDevice(
            serial = usableSerial.ifBlank { existing?.serial.orEmpty() },
            host = host,
            port = port,
            name = name.ifBlank { host },
            customName = if (sameDevice) existing.customName else "",
            lastConnectedAt = System.currentTimeMillis(),
            networkDefaults = if (sameDevice) existing.networkDefaults else null,
        )
        if (index >= 0) {
            devices[index] = record
        } else {
            devices += record
        }

        val deduped = if (index >= 0) {
            devices.filterIndexed { i, device -> i == index || device.address != address }
        } else {
            devices
        }
        val updated = deduped
            .sortedByDescending { it.lastConnectedAt }
            .take(MAX_DEVICES)
        save(updated)
        return updated
    }

    fun remove(id: String): List<SavedDevice> {
        val updated = load().filterNot { it.id == id }
        save(updated)
        return updated
    }

    fun setCustomName(id: String, customName: String): List<SavedDevice> {
        val normalized = customName.trim().take(MAX_CUSTOM_NAME_LENGTH)
        val updated = load().map { device ->
            if (device.id == id) device.copy(customName = normalized) else device
        }
        save(updated)
        return updated
    }

    fun mostRecent(): SavedDevice? = load().firstOrNull()

    fun loadNetworkDefaults(device: DeviceInfo): NetworkSettings? {
        val devices = load()
        val usableSerial = usableSerial(device.serial)
        if (usableSerial.isNotBlank()) {
            devices.firstOrNull { it.serial == usableSerial }?.networkDefaults?.let { return it }
        }
        return devices.firstOrNull { candidate ->
            candidate.address == device.address && (
                candidate.serial.isBlank() ||
                    usableSerial.isBlank() ||
                    candidate.serial == usableSerial
                )
        }?.networkDefaults
    }

    /**
     * Stores [settings] only when the device has no baseline yet and returns the effective
     * baseline (existing or newly stored).
     */
    fun saveNetworkDefaultsIfAbsent(
        device: DeviceInfo,
        settings: NetworkSettings,
    ): NetworkSettings {
        val devices = load().toMutableList()
        val usableSerial = usableSerial(device.serial)
        var index = if (usableSerial.isNotBlank()) {
            devices.indexOfFirst { it.serial == usableSerial }
        } else {
            -1
        }
        if (index < 0) {
            index = devices.indexOfFirst { candidate ->
                candidate.address == device.address && (
                    candidate.serial.isBlank() ||
                        usableSerial.isBlank() ||
                        candidate.serial == usableSerial
                    )
            }
        }

        if (index >= 0) {
            val existing = devices[index]
            existing.networkDefaults?.let { return it }
            devices[index] = existing.copy(
                serial = existing.serial.ifBlank { usableSerial },
                host = device.host,
                port = device.port,
                name = device.displayName,
                networkDefaults = settings,
            )
        } else {
            devices += SavedDevice(
                serial = usableSerial,
                host = device.host,
                port = device.port,
                name = device.displayName,
                lastConnectedAt = System.currentTimeMillis(),
                networkDefaults = settings,
            )
        }

        save(devices.sortedByDescending { it.lastConnectedAt }.take(MAX_DEVICES))
        return settings
    }

    private fun save(devices: List<SavedDevice>) {
        val array = JSONArray()
        devices.forEach { device ->
            array.put(
                JSONObject()
                    .put("serial", device.serial)
                    .put("host", device.host)
                    .put("port", device.port)
                    .put("name", device.name)
                    .put("customName", device.customName)
                    .put("lastConnectedAt", device.lastConnectedAt)
                    .apply {
                        device.networkDefaults?.let {
                            put("networkDefaults", networkSettingsToJson(it))
                        }
                    }
            )
        }
        prefs.edit { putString(KEY_DEVICES, array.toString()) }
    }

    private fun networkSettingsToJson(settings: NetworkSettings): JSONObject = JSONObject()
        .put("ntp", settings.ntpServer)
        .put("http", settings.captiveHttpUrl)
        .put("https", settings.captiveHttpsUrl)
        .put("fallback", settings.captiveFallbackUrl)
        .put("otherFallback", settings.captiveOtherFallbackUrls)
        .put("server", settings.captiveServer)
        .put("mode", settings.captiveMode)

    private fun networkSettingsFromJson(json: JSONObject): NetworkSettings = NetworkSettings(
        ntpServer = json.optString("ntp", ""),
        captiveHttpUrl = json.optString("http", ""),
        captiveHttpsUrl = json.optString("https", ""),
        captiveFallbackUrl = json.optString("fallback", ""),
        captiveOtherFallbackUrls = json.optString("otherFallback", ""),
        captiveServer = json.optString("server", ""),
        captiveMode = json.optString("mode", ""),
    )

    private fun usableSerial(serial: String): String =
        serial.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }.orEmpty()

    private companion object {
        const val PREFS_NAME = "puretv_devices"
        const val KEY_DEVICES = "devices"
        const val MAX_DEVICES = 20
        const val MAX_CUSTOM_NAME_LENGTH = 40
    }
}
