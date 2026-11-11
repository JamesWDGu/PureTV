package com.puretv.mobilehelper.data

data class DeviceInfo(
    val host: String,
    val port: Int,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val abi: String,
    val serial: String,
) {
    val displayName: String
        get() = listOf(manufacturer, model)
            .filter { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
            .distinct()
            .joinToString(" ")
            .ifBlank { host }

    val address: String get() = "$host:$port"
}

data class InstalledApp(
    val packageName: String,
    val versionCode: Long?,
    val isSystem: Boolean,
    val isEnabled: Boolean = true,
)

data class AppDetails(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val firstInstallTime: String?,
    val lastUpdateTime: String?,
    val installerPackageName: String?,
    val codePath: String?,
    val dataDir: String?,
    val flags: String?,
    val isSystem: Boolean,
)

data class InstallOptions(
    val replaceExisting: Boolean = true,
    val allowDowngrade: Boolean = false,
    val grantPermissions: Boolean = false,
) {
    fun toAdbArguments(): Array<String> = buildList {
        if (replaceExisting) add("-r")
        if (allowDowngrade) add("-d")
        if (grantPermissions) add("-g")
    }.toTypedArray()
}

enum class InstallStage { COPYING, INSTALLING }

data class InstallProgress(
    val stage: InstallStage,
    val fileName: String,
    val fileIndex: Int,
    val fileCount: Int,
    val bytesDone: Long,
    val bytesTotal: Long,
) {
    val fraction: Float?
        get() = if (bytesTotal > 0) (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f) else null
}

data class OperationResult(
    val message: String,
    val detail: String? = null,
)
