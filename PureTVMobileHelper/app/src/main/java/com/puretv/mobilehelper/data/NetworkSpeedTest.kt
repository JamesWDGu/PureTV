package com.puretv.mobilehelper.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

data class SpeedTestResult(
    val latencyMs: Long? = null,
    val statusCode: Int? = null,
    val error: String? = null,
) {
    val success: Boolean
        get() = error == null && (statusCode == null || statusCode == 204)
}

/**
 * Protocol-level latency checks executed on the phone:
 * - NTP: SNTP client request over UDP/123.
 * - Captive Portal: HTTP(S) request, expecting 204.
 */
class NetworkSpeedTest {

    suspend fun testNtp(server: String): SpeedTestResult = withContext(Dispatchers.IO) {
        val host = ntpHost(server)
        if (host.isBlank()) return@withContext SpeedTestResult(error = "服务器地址未填写")
        try {
            val address = InetAddress.getByName(host)
            DatagramSocket().use { socket ->
                socket.connect(address, NTP_PORT)
                socket.soTimeout = TIMEOUT_MS
                val request = ByteArray(NTP_PACKET_SIZE).also { it[0] = NTP_CLIENT_REQUEST }
                val response = DatagramPacket(ByteArray(NTP_PACKET_SIZE), NTP_PACKET_SIZE)
                val started = System.nanoTime()
                socket.send(DatagramPacket(request, request.size))
                socket.receive(response)
                val latency = ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1)
                val mode = response.data[0].toInt() and 0x07
                if (response.length < NTP_PACKET_SIZE || mode != NTP_MODE_SERVER) {
                    SpeedTestResult(latencyMs = latency, error = "服务器返回的校时结果异常")
                } else {
                    SpeedTestResult(latencyMs = latency)
                }
            }
        } catch (throwable: Throwable) {
            SpeedTestResult(error = throwable.toNetworkTestError())
        }
    }

    suspend fun testCaptivePortal(url: String): SpeedTestResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext SpeedTestResult(error = "检测地址未填写")
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "PureTV-Helper/1.0")
                val started = System.nanoTime()
                val status = connection.responseCode
                val latency = ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1)
                SpeedTestResult(latencyMs = latency, statusCode = status)
            } finally {
                connection.disconnect()
            }
        } catch (throwable: Throwable) {
            SpeedTestResult(error = throwable.toNetworkTestError())
        }
    }

    private fun ntpHost(server: String): String {
        val first = server
            .substringAfter("ntp://")
            .substringBefore('|')
            .trim()
        return when {
            first.startsWith("[") -> first.substringAfter('[').substringBefore(']')
            first.count { it == ':' } == 1 -> first.substringBefore(':')
            else -> first
        }
    }

    private companion object {
        const val NTP_PORT = 123
        const val NTP_PACKET_SIZE = 48
        const val NTP_CLIENT_REQUEST: Byte = 0x1B
        const val NTP_MODE_SERVER = 4
        const val TIMEOUT_MS = 3_000
    }
}
