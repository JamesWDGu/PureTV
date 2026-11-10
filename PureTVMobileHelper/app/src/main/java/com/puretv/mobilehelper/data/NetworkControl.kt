package com.puretv.mobilehelper.data

data class NetworkSettings(
    val ntpServer: String = "",
    val captiveHttpUrl: String = "",
    val captiveHttpsUrl: String = "",
    val captiveFallbackUrl: String = "",
    val captiveOtherFallbackUrls: String = "",
    val captiveServer: String = "",
    val captiveMode: String = "",
) {
    fun matches(preset: CaptivePortalPreset): Boolean {
        val httpsMatch = captiveHttpsUrl.isNotBlank() && captiveHttpsUrl == preset.httpsUrl
        val httpMatch = captiveHttpUrl.isNotBlank() && captiveHttpUrl == preset.httpUrl
        val serverMatch = captiveServer.isNotBlank() && captiveServer == preset.server
        return when {
            httpsMatch && (captiveHttpUrl.isBlank() || httpMatch) -> true
            httpMatch && captiveHttpsUrl.isBlank() && (captiveServer.isBlank() || serverMatch) -> true
            captiveHttpUrl.isBlank() && captiveHttpsUrl.isBlank() && serverMatch -> true
            else -> false
        }
    }

    fun captiveEquals(other: NetworkSettings): Boolean =
        captiveHttpUrl == other.captiveHttpUrl &&
            captiveHttpsUrl == other.captiveHttpsUrl &&
            captiveFallbackUrl == other.captiveFallbackUrl &&
            captiveOtherFallbackUrls == other.captiveOtherFallbackUrls &&
            captiveServer == other.captiveServer
}

data class NtpPreset(
    val server: String,
)

data class CaptivePortalPreset(
    val id: String,
    val label: String,
    val httpUrl: String,
    val httpsUrl: String,
    val server: String,
) {
    val primaryUrl: String get() = httpsUrl.ifBlank { httpUrl }
}

object NetworkPresets {
    val ntp: List<NtpPreset> = listOf(
        NtpPreset("time.android.com"),
        NtpPreset("time.google.com"),
        NtpPreset("ntp.aliyun.com"),
        NtpPreset("ntp.tencent.com"),
        NtpPreset("cn.pool.ntp.org"),
        NtpPreset("time.cloudflare.com"),
    )

    val captivePortal: List<CaptivePortalPreset> = listOf(
        CaptivePortalPreset(
            id = "google_gstatic",
            label = "Google（地址一）",
            httpUrl = "http://connectivitycheck.gstatic.com/generate_204",
            httpsUrl = "https://connectivitycheck.gstatic.com/generate_204",
            server = "connectivitycheck.gstatic.com",
        ),
        CaptivePortalPreset(
            id = "google_www",
            label = "Google（地址二）",
            httpUrl = "http://www.google.com/generate_204",
            httpsUrl = "https://www.google.com/generate_204",
            server = "www.google.com",
        ),
        CaptivePortalPreset(
            id = "miui",
            label = "小米",
            httpUrl = "http://connect.rom.miui.com/generate_204",
            httpsUrl = "https://connect.rom.miui.com/generate_204",
            server = "connect.rom.miui.com",
        ),
        CaptivePortalPreset(
            id = "huawei",
            label = "华为",
            httpUrl = "http://connectivitycheck.platform.hicloud.com/generate_204",
            httpsUrl = "https://connectivitycheck.platform.hicloud.com/generate_204",
            server = "connectivitycheck.platform.hicloud.com",
        ),
        CaptivePortalPreset(
            id = "vivo",
            label = "vivo",
            httpUrl = "http://wifi.vivo.com.cn/generate_204",
            httpsUrl = "https://wifi.vivo.com.cn/generate_204",
            server = "wifi.vivo.com.cn",
        ),
    )
}
