package com.puretv.mobilehelper.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.shell.AdbShellResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

class AdbRepository(private val context: Context) {

    private val mutex = Mutex()
    private var kadb: Kadb? = null

    @Volatile
    private var currentDevice: DeviceInfo? = null

    val connectedDevice: DeviceInfo? get() = currentDevice

    suspend fun connect(host: String, port: Int): DeviceInfo = withContext(Dispatchers.IO) {
        require(host.isNotBlank()) { "请输入电视的 IP 地址" }
        require(port in 1..65535) { "连接端口应为 1 到 65535 之间的数字" }
        mutex.withLock {
            disconnectLocked()
            val connection = Kadb.create(
                host = host.trim(),
                port = port,
                connectTimeout = CONNECT_TIMEOUT_MS,
                socketTimeout = IO_TIMEOUT_MS,
            )
            try {
                val info = readDeviceInfo(connection, host.trim(), port)
                kadb = connection
                currentDevice = info
                info
            } catch (throwable: Throwable) {
                connection.close()
                throw throwable
            }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        mutex.withLock { disconnectLocked() }
    }

    /** Used when the ViewModel is cleared; safe to call from a non-suspending context. */
    fun closeNow() {
        runCatching { kadb?.close() }
        kadb = null
        currentDevice = null
    }

    suspend fun pair(host: String, pairingPort: Int, pairingCode: String) =
        withContext(Dispatchers.IO) {
            require(host.isNotBlank()) { "请输入配对地址" }
            require(pairingPort in 1..65535) { "配对端口无效" }
            require(pairingCode.length == 6 && pairingCode.all(Char::isDigit)) {
                "配对码应为 6 位数字"
            }
            check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                "无线调试配对需要 Android 9 或更高版本"
            }
            mutex.withLock {
                Kadb.pair(
                    host = host.trim(),
                    port = pairingPort,
                    pairingCode = pairingCode,
                    name = "PureTV Helper",
                )
            }
        }

    suspend fun listApps(includeSystem: Boolean): List<InstalledApp> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val connection = requireConnection()
            val userId = currentUserId(connection)
            val disabledPackages = queryDisabledPackages(connection, userId)
            val thirdParty = queryPackageList(connection, "-3", isSystem = false, userId = userId)
            val system = if (includeSystem) {
                queryPackageList(connection, "-s", isSystem = true, userId = userId)
            } else {
                emptyList()
            }
            (thirdParty + system)
                .associateBy { it.packageName }
                .values
                .map { app ->
                    app.copy(isEnabled = app.packageName !in disabledPackages)
                }
                .sortedBy { it.packageName }
        }
    }

    /**
     * Freezes an app for the current TV user via `pm disable-user`, or restores it with `pm enable`.
     * Data and the installed APK are preserved. System apps require two-step confirmation in the UI.
     */
    suspend fun setAppFrozen(packageName: String, frozen: Boolean): OperationResult =
        withContext(Dispatchers.IO) {
            validatePackageName(packageName)
            mutex.withLock {
                val connection = requireConnection()
                val userId = currentUserId(connection)
                val command = if (frozen) {
                    "pm disable-user --user $userId $packageName"
                } else {
                    "pm enable --user $userId $packageName"
                }
                val response = connection.shell(command)
                val output = response.allOutput.trim()
                val failed = response.exitCode != 0 ||
                    output.lineSequence().any { line ->
                        line.startsWith("Error:") ||
                            line.startsWith("Exception") ||
                            line.startsWith("Failure") ||
                            line.contains("Unknown package", ignoreCase = true) ||
                            line.contains("SecurityException") ||
                            line.contains("IllegalArgumentException")
                    }
                check(!failed) {
                    output.ifBlank { if (frozen) "冻结失败" else "解冻失败" }
                }
                OperationResult(
                    if (frozen) "已冻结 $packageName" else "已解冻 $packageName"
                )
            }
        }

    suspend fun readNetworkSettings(): NetworkSettings = withContext(Dispatchers.IO) {
        mutex.withLock {
            val connection = requireConnection()
            val keys = linkedMapOf(
                "NTP" to "ntp_server",
                "HTTP" to "captive_portal_http_url",
                "HTTPS" to "captive_portal_https_url",
                "FALLBACK" to "captive_portal_fallback_url",
                "OTHER" to "captive_portal_other_fallback_urls",
                "SERVER" to "captive_portal_server",
                "MODE" to "captive_portal_mode",
            )
            val command = keys.entries.joinToString("; ") { (marker, key) ->
                "echo \"$marker=\$(settings get global $key)\""
            }
            val output = connection.shell(command).allOutput
            val values = output.lineSequence()
                .mapNotNull { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) null
                    else line.substring(0, separator).trim() to
                        line.substring(separator + 1).trim()
                }
                .toMap()
            fun value(marker: String): String =
                values[marker].orEmpty().takeUnless { it == "null" }.orEmpty()

            NetworkSettings(
                ntpServer = value("NTP"),
                captiveHttpUrl = value("HTTP"),
                captiveHttpsUrl = value("HTTPS"),
                captiveFallbackUrl = value("FALLBACK"),
                captiveOtherFallbackUrls = value("OTHER"),
                captiveServer = value("SERVER"),
                captiveMode = value("MODE"),
            )
        }
    }

    suspend fun setNtpServer(server: String?): OperationResult = withContext(Dispatchers.IO) {
        val value = server?.trim().orEmpty()
        validateSettingValue(value)
        mutex.withLock {
            val connection = requireConnection()
            val command = if (value.isBlank()) {
                "settings delete global ntp_server"
            } else {
                "settings put global ntp_server ${shellQuote(value)}"
            }
            runSettingsCommand(connection, command)
            OperationResult(
                if (value.isBlank()) "已清除自定义校时服务器" else "已设置校时服务器：$value"
            )
        }
    }

    suspend fun setCaptivePortal(
        httpUrl: String,
        httpsUrl: String,
        fallbackUrl: String,
        otherFallbackUrls: String,
        server: String,
    ): OperationResult = withContext(Dispatchers.IO) {
        validateSettingValue(httpUrl)
        validateSettingValue(httpsUrl)
        validateSettingValue(fallbackUrl)
        validateSettingValue(otherFallbackUrls)
        validateSettingValue(server)
        validateHttpUrl(httpUrl, "兼容检测地址")
        validateHttpUrl(httpsUrl, "主要检测地址")
        validateHttpUrl(fallbackUrl, "备用地址")
        validateHttpUrl(otherFallbackUrls, "其他备用地址")
        mutex.withLock {
            val connection = requireConnection()
            val commands = listOf(
                settingsAssignment("captive_portal_http_url", httpUrl),
                settingsAssignment("captive_portal_https_url", httpsUrl),
                settingsAssignment("captive_portal_fallback_url", fallbackUrl),
                settingsAssignment("captive_portal_other_fallback_urls", otherFallbackUrls),
                settingsAssignment("captive_portal_server", server),
            )
            runSettingsCommand(connection, commands.joinToString("; "))
            OperationResult("已更新联网检测地址")
        }
    }

    /**
     * Sends text to the field currently focused on the TV via `input text`.
     * Spaces are encoded as `%s` because `input text` treats whitespace as argument separators.
     */
    suspend fun inputText(text: String): OperationResult = withContext(Dispatchers.IO) {
        validateSettingValue(text)
        requireAdbInputText(text)
        mutex.withLock {
            val connection = requireConnection()
            val encoded = text.replace(" ", "%s")
            runInputCommand(connection, "input text ${shellQuote(encoded)}")
            OperationResult("已向电视发送 ${text.length} 个字符")
        }
    }

    /**
     * Sends one or more key events to the TV (e.g. DPAD_UP, ENTER, DEL). Keys are validated
     * against a whitelist to avoid shell injection.
     */
    suspend fun inputKey(key: String, times: Int = 1): OperationResult = withContext(Dispatchers.IO) {
        require(key in INPUT_KEYS) { "暂不支持这个遥控按键" }
        require(times in 1..MAX_INPUT_KEY_REPEAT) { "一次按键次数过多，请分几次操作" }
        mutex.withLock {
            val connection = requireConnection()
            var remaining = times
            while (remaining > 0) {
                val chunk = minOf(remaining, INPUT_KEY_CHUNK)
                runInputCommand(connection, "input keyevent " + List(chunk) { key }.joinToString(" "))
                remaining -= chunk
            }
            OperationResult(if (times > 1) "已发送 $times 次按键操作" else "已发送按键操作")
        }
    }

    /** Reads an editable field, rejecting hint text or unverifiable nonempty content. */
    suspend fun readFocusedInputText(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val connection = requireConnection()
            val output = connection.shell(
                "uiautomator dump /data/local/tmp/puretv-ui.xml >/dev/null 2>&1 && " +
                    "cat /data/local/tmp/puretv-ui.xml; result=\$?; " +
                    "rm -f /data/local/tmp/puretv-ui.xml; exit \$result"
            )
            check(output.exitCode == 0) { "暂时读不到电视上的输入框，请保持电视页面打开后再试" }
            val inputMethodDump = connection.shell("dumpsys input_method").allOutput
            FocusedInputText.parse(output.allOutput, inputMethodDump)
        }
    }

    suspend fun appDetails(packageName: String): AppDetails = withContext(Dispatchers.IO) {
        validatePackageName(packageName)
        mutex.withLock {
            val output = requireConnection().shell("dumpsys package $packageName").allOutput
            check(
                !output.contains("Unable to find package") &&
                    !output.contains("Unknown package"),
            ) { "未找到应用 $packageName" }

            val versionCodeLine = Regex("""\bversionCode=(\d+)""").find(output)
            val flags = Regex("""(?m)^\s*flags=\[(.*)]""").find(output)
                ?.groupValues?.get(1)?.trim()

            AppDetails(
                packageName = packageName,
                versionName = findField(output, "versionName"),
                versionCode = versionCodeLine?.groupValues?.get(1)?.toLongOrNull(),
                minSdk = Regex("""\bminSdk=(\d+)""").find(output)?.groupValues?.get(1)?.toIntOrNull(),
                targetSdk = Regex("""\btargetSdk=(\d+)""").find(output)?.groupValues?.get(1)?.toIntOrNull(),
                firstInstallTime = findField(output, "firstInstallTime"),
                lastUpdateTime = findField(output, "lastUpdateTime"),
                installerPackageName = findField(output, "installerPackageName"),
                codePath = findField(output, "codePath"),
                dataDir = findField(output, "dataDir"),
                flags = flags,
                isSystem = flags?.contains("SYSTEM") == true,
            )
        }
    }

    suspend fun launchApp(packageName: String): OperationResult = withContext(Dispatchers.IO) {
        validatePackageName(packageName)
        mutex.withLock {
            val response = requireConnection()
                .shell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
            val output = response.allOutput
            if (output.contains("No activities found", ignoreCase = true) ||
                output.contains("monkey aborted", ignoreCase = true)
            ) {
                throw IllegalStateException("这个应用没有可以打开的电视界面")
            }
            check(response.exitCode == 0) { output.ifBlank { "电视没有打开这个应用" } }
            OperationResult("已请求电视打开应用")
        }
    }

    suspend fun forceStop(packageName: String): OperationResult = withContext(Dispatchers.IO) {
        validatePackageName(packageName)
        mutex.withLock {
            val response = requireConnection().shell("am force-stop $packageName")
            check(response.exitCode == 0) {
                response.allOutput.ifBlank { "电视没有停止这个应用" }
            }
            OperationResult("已停止 $packageName")
        }
    }

    suspend fun uninstall(packageName: String): OperationResult = withContext(Dispatchers.IO) {
        validatePackageName(packageName)
        mutex.withLock {
            requireConnection().uninstall(packageName)
            OperationResult("已卸载 $packageName")
        }
    }

    suspend fun install(
        uris: List<Uri>,
        options: InstallOptions,
        onProgress: (InstallProgress) -> Unit,
    ): OperationResult = withContext(Dispatchers.IO) {
        require(uris.isNotEmpty()) { "请先选择要安装的安装包（APK）" }
        mutex.withLock {
            val connection = requireConnection()
            val installDir = File(context.cacheDir, "apk-install").apply { mkdirs() }
            removeStaleFiles(installDir)
            val arguments = options.toAdbArguments()
            val installed = mutableListOf<String>()

            try {
                uris.forEachIndexed { index, uri ->
                    val displayName = queryDisplayName(uri)
                    val localFile = File(installDir, "${UUID.randomUUID()}.apk")
                    try {
                        copyToCache(uri, localFile, displayName, index, uris.size, onProgress)
                        currentCoroutineContext().ensureActive()

                        if (!looksLikeZip(localFile)) {
                            throw IOException("所选文件不是可以安装的 APK，请重新选择完整安装包")
                        }
                        // Let pm install perform full manifest validation; archive parsing can fail
                        // on older phones for otherwise valid APKs.
                        val archive = packageManager()
                            .getPackageArchiveInfo(localFile.absolutePath, 0)
                        val length = localFile.length()
                        onProgress(
                            InstallProgress(
                                stage = InstallStage.INSTALLING,
                                fileName = displayName,
                                fileIndex = index + 1,
                                fileCount = uris.size,
                                bytesDone = 0,
                                bytesTotal = length,
                            )
                        )

                        var lastReport = 0L
                        localFile.source().use { source ->
                            val progressSource = ProgressSource(source) { bytes ->
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                                    lastReport = now
                                    onProgress(
                                        InstallProgress(
                                            stage = InstallStage.INSTALLING,
                                            fileName = displayName,
                                            fileIndex = index + 1,
                                            fileCount = uris.size,
                                            bytesDone = bytes,
                                            bytesTotal = length,
                                        )
                                    )
                                }
                            }
                            connection.install(progressSource, length, *arguments)
                        }
                        onProgress(
                            InstallProgress(
                                stage = InstallStage.INSTALLING,
                                fileName = displayName,
                                fileIndex = index + 1,
                                fileCount = uris.size,
                                bytesDone = length,
                                bytesTotal = length,
                            )
                        )
                        installed += archive?.packageName ?: displayName
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable
                        throw IOException("$displayName 安装失败：${throwable.message ?: "未知错误"}", throwable)
                    } finally {
                        localFile.delete()
                    }
                }
            } finally {
                installDir.listFiles()
                    ?.filter { System.currentTimeMillis() - it.lastModified() > STALE_FILE_AGE_MS }
                    ?.forEach { it.delete() }
            }

            OperationResult(
                message = if (installed.size == 1) "安装成功：${installed.first()}" else "已安装 ${installed.size} 个应用",
                detail = installed.joinToString("\n").takeIf { it.isNotBlank() },
            )
        }
    }

    private fun readDeviceInfo(connection: Kadb, host: String, port: Int): DeviceInfo {
        val command = listOf(
            "MANUFACTURER=\$(getprop ro.product.manufacturer)",
            "MODEL=\$(getprop ro.product.model)",
            "RELEASE=\$(getprop ro.build.version.release)",
            "SDK=\$(getprop ro.build.version.sdk)",
            "ABI=\$(getprop ro.product.cpu.abi)",
            "SERIAL=\$(getprop ro.serialno)",
            "BOOT_SERIAL=\$(getprop ro.boot.serialno)",
        ).joinToString("; ") { "echo \"$it\"" }
        val response = connection.shell(command)
        val values = response.allOutput.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null
                else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
            }
            .toMap()
        check(values["MODEL"] != null) {
            "电视没有返回设备信息，请查看电视是否需要点击“允许调试”"
        }
        return DeviceInfo(
            host = host,
            port = port,
            manufacturer = values["MANUFACTURER"].orEmpty(),
            model = values["MODEL"].orEmpty().ifBlank { "Android TV" },
            androidVersion = values["RELEASE"].orEmpty().ifBlank { "未知" },
            sdkInt = values["SDK"]?.toIntOrNull() ?: 0,
            abi = values["ABI"].orEmpty().ifBlank { "未知" },
            serial = values["SERIAL"].orEmpty().ifBlank { values["BOOT_SERIAL"].orEmpty() },
        )
    }

    private fun currentUserId(connection: Kadb): Int =
        runCatching {
            connection.shell("am get-current-user").allOutput
                .lineSequence()
                .mapNotNull { it.trim().toIntOrNull() }
                .lastOrNull()
        }.getOrNull()?.takeIf { it >= 0 } ?: 0

    private fun queryDisabledPackages(connection: Kadb, userId: Int): Set<String> {
        val withUser = connection.shell("pm list packages --user $userId -d")
        val response: AdbShellResponse =
            if (withUser.output.contains("package:") || withUser.exitCode == 0) {
                withUser
            } else {
                connection.shell("pm list packages -d")
            }
        return response.output.lineSequence()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim().substringBefore(' ') }
            .filter { PACKAGE_NAME.matches(it) }
            .toSet()
    }

    private fun queryPackageList(
        connection: Kadb,
        flag: String,
        isSystem: Boolean,
        userId: Int,
    ): List<InstalledApp> {
        fun query(extra: String): AdbShellResponse {
            val primary = connection.shell(
                "pm list packages --user $userId $flag $extra".replace(Regex("\\s+"), " ").trim()
            )
            if (primary.output.contains("package:") || primary.exitCode == 0) return primary
            return connection.shell(
                "pm list packages $flag $extra".replace(Regex("\\s+"), " ").trim()
            )
        }

        // --show-versioncode can omit disabled packages on some Android builds, while the
        // unversioned query still lists them. Merge both so frozen apps remain visible.
        val versioned = parsePackageLines(query("--show-versioncode").output, isSystem)
        val all = parsePackageLines(query("").output, isSystem)
        val merged = LinkedHashMap<String, InstalledApp>()
        all.forEach { merged[it.packageName] = it }
        versioned.forEach { merged[it.packageName] = it }
        return merged.values.toList()
    }

    private fun parsePackageLines(
        output: String,
        isSystem: Boolean,
    ): List<InstalledApp> = output.lineSequence()
            .filter { it.startsWith("package:") }
            .mapNotNull { line ->
                val body = line.removePrefix("package:").trim()
                val packageName = body.substringBefore(' ').trim()
                if (!PACKAGE_NAME.matches(packageName)) return@mapNotNull null
                val versionCode = Regex("""versionCode[:=](\d+)""")
                    .find(body)?.groupValues?.get(1)?.toLongOrNull()
                InstalledApp(packageName, versionCode, isSystem)
            }
            .toList()

    private suspend fun copyToCache(
        uri: Uri,
        destination: File,
        displayName: String,
        index: Int,
        count: Int,
        onProgress: (InstallProgress) -> Unit,
    ) {
        val total = queryFileSize(uri)
        var copied = 0L
        var lastReport = 0L
        onProgress(
            InstallProgress(
                stage = InstallStage.COPYING,
                fileName = displayName,
                fileIndex = index + 1,
                fileCount = count,
                bytesDone = 0,
                bytesTotal = total,
            )
        )
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取所选文件")
        input.use { stream ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                        lastReport = now
                        onProgress(
                            InstallProgress(
                                stage = InstallStage.COPYING,
                                fileName = displayName,
                                fileIndex = index + 1,
                                fileCount = count,
                                bytesDone = copied,
                                bytesTotal = total,
                            )
                        )
                    }
                }
                output.flush()
                output.fd.sync()
            }
        }
        onProgress(
            InstallProgress(
                stage = InstallStage.COPYING,
                fileName = displayName,
                fileIndex = index + 1,
                fileCount = count,
                bytesDone = copied,
                bytesTotal = if (total > 0) total else copied,
            )
        )
    }

    private fun queryDisplayName(uri: Uri): String {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty()
                .ifBlank { "update.apk" }
    }

    private fun queryFileSize(uri: Uri): Long {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull() ?: 0L
    }

    private fun looksLikeZip(file: File): Boolean {
        if (file.length() < 4) return false
        return runCatching {
            file.inputStream().use { stream ->
                val header = ByteArray(4)
                if (stream.read(header) != 4) return false
                header[0] == 'P'.code.toByte() &&
                    header[1] == 'K'.code.toByte() &&
                    ((header[2] == 3.toByte() && header[3] == 4.toByte()) ||
                        (header[2] == 5.toByte() && header[3] == 6.toByte()))
            }
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun packageManager() = context.packageManager

    private fun requireConnection(): Kadb =
        kadb ?: throw IllegalStateException("尚未连接电视，请先返回首页连接")

    private fun disconnectLocked() {
        runCatching { kadb?.close() }
        kadb = null
        currentDevice = null
    }

    private fun findField(output: String, name: String): String? =
        Regex("""(?m)^\s*${Regex.escape(name)}=(.*)$""")
            .find(output)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "null" }

    private fun settingsAssignment(key: String, value: String): String =
        if (value.isBlank()) {
            "settings delete global $key"
        } else {
            "settings put global $key ${shellQuote(value)}"
        }

    private fun runSettingsCommand(connection: Kadb, command: String) {
        val response = connection.shell(command)
        val output = response.allOutput.trim()
        val failed = response.exitCode != 0 ||
            output.lineSequence().any { line ->
                line.startsWith("Error") ||
                    line.startsWith("Exception") ||
                    line.startsWith("Failure") ||
                    line.contains("Bad setting", ignoreCase = true) ||
                    line.contains("SecurityException")
            }
        check(!failed) { output.ifBlank { "电视没有完成这次设置修改" } }
    }

    private fun runInputCommand(connection: Kadb, command: String) {
        val response = connection.shell(command)
        val output = response.allOutput.trim()
        val failed = response.exitCode != 0 ||
            output.lineSequence().any { line ->
                line.startsWith("Error") ||
                    line.startsWith("Exception") ||
                    line.startsWith("Failure") ||
                    line.contains("usage", ignoreCase = true)
            }
        if (failed) throw RemoteInputCommandException()
    }

    private fun validateSettingValue(value: String) {
        require(value.none { it == '\n' || it == '\r' || it == '\u0000' }) {
            "填写内容中包含不能使用的换行或特殊字符"
        }
    }

    private fun validateHttpUrl(value: String, label: String) {
        if (value.isBlank()) return
        val urls = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        require(urls.all { it.startsWith("http://", true) || it.startsWith("https://", true) }) {
            "$label 必须是以 http:// 或 https:// 开头的完整网址"
        }
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun validatePackageName(packageName: String) {
        require(PACKAGE_NAME.matches(packageName)) { "应用标识不正确，请刷新应用列表后再试" }
    }

    private fun removeStaleFiles(directory: File) {
        val cutoff = System.currentTimeMillis() - STALE_FILE_AGE_MS
        directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private class ProgressSource(
        private val delegate: Source,
        private val onBytesRead: (Long) -> Unit,
    ) : Source {
        private var bytesRead = 0L

        override fun read(sink: Buffer, byteCount: Long): Long {
            val read = delegate.read(sink, byteCount)
            if (read > 0) {
                bytesRead += read
                onBytesRead(bytesRead)
            }
            return read
        }

        override fun timeout(): Timeout = delegate.timeout()

        override fun close() = delegate.close()
    }

    private companion object {
        val INPUT_KEYS: Set<String> = hashSetOf(
            "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "DPAD_CENTER",
            "ENTER", "BACK", "HOME", "MENU",
            "VOLUME_UP", "VOLUME_DOWN", "PAGE_UP", "PAGE_DOWN",
            "DEL", "FORWARD_DEL", "MOVE_HOME", "MOVE_END", "SPACE", "CAPS_LOCK",
        ).apply {
            for (digit in '0'..'9') add("KEYCODE_$digit")
            for (letter in 'A'..'Z') add("KEYCODE_$letter")
        }
        const val INPUT_KEY_CHUNK = 64
        const val MAX_INPUT_KEY_REPEAT = 256
        const val CONNECT_TIMEOUT_MS = 15_000
        const val IO_TIMEOUT_MS = 120_000
        const val COPY_BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 80L
        const val STALE_FILE_AGE_MS = 24 * 60 * 60 * 1000L
        val PACKAGE_NAME = Regex("""[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+""")
    }
}
