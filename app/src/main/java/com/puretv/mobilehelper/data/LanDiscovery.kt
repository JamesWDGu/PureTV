package com.puretv.mobilehelper.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.coroutines.resume

enum class DiscoverySource {
    MDNS_CONNECT,
    MDNS_PAIRING,
    PORT_SCAN,
}

data class DiscoveredDevice(
    val name: String,
    val host: String,
    val port: Int,
    val source: DiscoverySource,
) {
    val key: String get() = "$host:$port"
    val requiresPairing: Boolean get() = source == DiscoverySource.MDNS_PAIRING
}

data class DiscoveryUiState(
    val running: Boolean = false,
    val completed: Boolean = false,
    val devices: List<DiscoveredDevice> = emptyList(),
    val warning: String? = null,
)

/**
 * User-triggered LAN discovery:
 * - mDNS via NsdManager for ADB wireless debugging endpoints;
 * - a short TCP probe of port 5555 on private IPv4 subnets for legacy TV/box ADB.
 *
 * It never connects automatically and never scans outside private IPv4 ranges.
 */
class LanDiscovery(context: Context) : AutoCloseable {

    private val applicationContext = context.applicationContext
    private val nsdManager: NsdManager? =
        applicationContext.getSystemService(NsdManager::class.java)

    private val _state = MutableStateFlow(DiscoveryUiState())
    val state: StateFlow<DiscoveryUiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = CopyOnWriteArrayList<NsdManager.DiscoveryListener>()

    @Volatile
    private var generation = 0

    @Volatile
    private var job: Job? = null

    @Volatile
    private var resolveJob: Job? = null

    @Volatile
    private var resolveChannel: Channel<Pair<NsdServiceInfo, DiscoverySource>>? = null

    @Volatile
    private var devices: ConcurrentHashMap<String, DiscoveredDevice> = ConcurrentHashMap()

    @Volatile
    private var localHosts: Set<String> = emptySet()

    @Volatile
    private var closed = false

    fun start() {
        if (closed || job?.isActive == true) return
        val currentGeneration = ++generation
        devices = ConcurrentHashMap()
        _state.value = DiscoveryUiState(running = true)

        val warnings = CopyOnWriteArraySet<String>()
        job = scope.launch {
            try {
                localHosts = runCatching { localIpv4Addresses() }.getOrDefault(emptySet())
                startMdns(currentGeneration, warnings)
                val scanJob = async { scanLocalSubnets(currentGeneration, warnings) }
                delay(MDNS_WINDOW_MS)
                scanJob.await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                warnings += "搜索未完成，请确认手机已连接 Wi-Fi 后再试。"
            } finally {
                if (currentGeneration == generation) {
                    stopMdns()
                    finish(currentGeneration, warnings)
                }
            }
        }
    }

    fun stop() {
        if (closed) return
        generation += 1
        job?.cancel()
        job = null
        stopMdns()
        _state.update { current ->
            current.copy(
                running = false,
                completed = true,
                devices = sortedDevices(),
            )
        }
    }

    override fun close() {
        closed = true
        generation += 1
        job?.cancel()
        job = null
        stopMdns()
        scope.cancel()
    }

    private fun startMdns(
        currentGeneration: Int,
        warnings: MutableSet<String>,
    ) {
        val manager = nsdManager
        if (manager == null) {
            warnings += "手机无法识别部分无线调试设备，仍会搜索其他可用的电视。"
            return
        }

        val channel = Channel<Pair<NsdServiceInfo, DiscoverySource>>(Channel.UNLIMITED)
        resolveChannel = channel
        resolveJob = scope.launch {
            for ((serviceInfo, source) in channel) {
                if (currentGeneration != generation) break
                val resolved = resolve(serviceInfo) ?: continue
                if (currentGeneration != generation) break
                val host = hostOf(resolved) ?: continue
                val port = resolved.port
                if (port !in 1..65535) continue
                val name = resolved.serviceName
                    ?.takeIf { it.isNotBlank() }
                    ?: host
                addDevice(
                    currentGeneration,
                    DiscoveredDevice(name, host, port, source),
                )
            }
        }

        SERVICE_SPECS.forEach { spec ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    warnings += "部分无线调试设备暂时无法识别（错误码：$errorCode），也可手动填写地址连接。"
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (currentGeneration == generation) {
                        channel.trySend(serviceInfo to spec.source)
                    }
                }
            }
            listeners += listener
            runCatching {
                manager.discoverServices(
                    spec.serviceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener,
                )
            }.onFailure {
                warnings += "部分无线调试设备暂时无法识别，也可手动填写地址连接。"
            }
        }
    }

    private fun stopMdns() {
        val manager = nsdManager
        listeners.forEach { listener ->
            runCatching { manager?.stopServiceDiscovery(listener) }
        }
        listeners.clear()
        resolveChannel?.close()
        resolveChannel = null
        resolveJob?.cancel()
        resolveJob = null
    }

    private suspend fun resolve(serviceInfo: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                    if (continuation.isActive) continuation.resume(resolvedInfo)
                }
            }
            try {
                val manager = nsdManager
                if (manager == null) {
                    if (continuation.isActive) continuation.resume(null)
                } else {
                    @Suppress("DEPRECATION")
                    manager.resolveService(serviceInfo, listener)
                }
            } catch (throwable: Throwable) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun hostOf(serviceInfo: NsdServiceInfo): String? {
        val addresses = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                addAll(serviceInfo.hostAddresses)
            }
            @Suppress("DEPRECATION")
            serviceInfo.host?.let(::add)
        }
        val address = addresses.firstOrNull { it is Inet4Address }
            ?: addresses.firstOrNull()
        return address?.hostAddress?.takeIf { it.isNotBlank() }
    }

    private suspend fun scanLocalSubnets(
        currentGeneration: Int,
        warnings: MutableSet<String>,
    ) {
        val hosts = try {
            localScanHosts()
        } catch (_: Throwable) {
            warnings += "暂时无法获取手机的网络信息，请连接与电视相同的 Wi-Fi 后再试。"
            return
        }
        if (hosts.isEmpty()) {
            warnings += "未找到可用于搜索的 Wi-Fi 或有线网络，请连接与电视相同的路由器。"
            return
        }

        val semaphore = Semaphore(PORT_SCAN_CONCURRENCY)
        coroutineScope {
            hosts.map { host ->
                async {
                    semaphore.withPermit {
                        if (currentGeneration == generation && isPortOpen(host, LEGACY_ADB_PORT)) {
                            addDevice(
                                currentGeneration,
                                DiscoveredDevice(
                                    name = host,
                                    host = host,
                                    port = LEGACY_ADB_PORT,
                                    source = DiscoverySource.PORT_SCAN,
                                ),
                            )
                        }
                    }
                }
            }.awaitAll()
        }
    }

    @Suppress("DEPRECATION")
    private fun localIpv4Addresses(): Set<String> {
        val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java)
            ?: return emptySet()
        val addresses = mutableSetOf<String>()
        for (network in connectivity.allNetworks) {
            val linkProperties = connectivity.getLinkProperties(network) ?: continue
            for (linkAddress in linkProperties.linkAddresses) {
                val ip = linkAddress.address as? Inet4Address ?: continue
                if (ip.isLoopbackAddress || ip.isLinkLocalAddress || !ip.isSiteLocalAddress) {
                    continue
                }
                ip.hostAddress?.takeIf { it.isNotBlank() }?.let(addresses::add)
            }
        }
        return addresses
    }

    @Suppress("DEPRECATION")
    private fun localScanHosts(): List<String> {
        val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java)
            ?: return emptyList()
        val networks = connectivity.allNetworks.toList()
        val ownIps = mutableSetOf<Int>()
        val hostIps = LinkedHashSet<Int>()

        for (network in networks) {
            val linkProperties = connectivity.getLinkProperties(network) ?: continue
            val isLan = connectivity.getNetworkCapabilities(network)?.let { capabilities ->
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } == true

            for (linkAddress in linkProperties.linkAddresses) {
                val ip = linkAddress.address as? Inet4Address ?: continue
                if (ip.isLoopbackAddress || ip.isLinkLocalAddress || !ip.isSiteLocalAddress) {
                    continue
                }
                val local = ipToInt(ip.address)
                ownIps += local
                if (!isLan) continue

                // Home LANs are usually /24. Never expand a large subnet beyond the local /24.
                val prefix = linkAddress.prefixLength.coerceIn(24, 32)
                val mask = if (prefix == 32) -1 else -1 shl (32 - prefix)
                val networkAddress = local and mask
                val hostCount = 1 shl (32 - prefix)
                val firstOffset = if (prefix <= 30) 1 else 0
                val lastOffset = if (prefix <= 30) hostCount - 2 else hostCount - 1
                for (offset in firstOffset..lastOffset) {
                    hostIps += networkAddress or offset
                }
            }
        }

        hostIps.removeAll(ownIps)
        return hostIps
            .take(MAX_SCAN_HOSTS)
            .map(::intToIp)
    }

    private fun isPortOpen(host: String, port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), PORT_SCAN_TIMEOUT_MS)
            true
        }
    }.getOrDefault(false)

    private fun addDevice(
        currentGeneration: Int,
        device: DiscoveredDevice,
    ) {
        if (currentGeneration != generation) return
        if (device.host == "127.0.0.1" || device.host == "::1") return
        if (device.host in localHosts) return
        val existing = devices[device.key]
        val shouldReplace = existing == null ||
            (existing.source == DiscoverySource.PORT_SCAN &&
                device.source != DiscoverySource.PORT_SCAN)
        if (shouldReplace) {
            devices[device.key] = device
            publish(currentGeneration)
        }
    }

    private fun publish(currentGeneration: Int) {
        if (currentGeneration != generation) return
        _state.update { it.copy(devices = sortedDevices()) }
    }

    private fun finish(
        currentGeneration: Int,
        warnings: Set<String>,
    ) {
        if (currentGeneration != generation) return
        _state.update {
            it.copy(
                running = false,
                completed = true,
                devices = sortedDevices(),
                warning = warnings
                    .filter { message -> message.isNotBlank() }
                    .distinct()
                    .joinToString(" · ")
                    .takeIf { message -> message.isNotBlank() },
            )
        }
    }

    private fun sortedDevices(): List<DiscoveredDevice> = devices.values.sortedWith(
        compareBy<DiscoveredDevice>(
            { it.source.ordinal },
            { it.name.lowercase() },
            { it.host },
            { it.port },
        )
    )

    private fun ipToInt(bytes: ByteArray): Int =
        ((bytes[0].toInt() and 0xff) shl 24) or
            ((bytes[1].toInt() and 0xff) shl 16) or
            ((bytes[2].toInt() and 0xff) shl 8) or
            (bytes[3].toInt() and 0xff)

    private fun intToIp(value: Int): String =
        "${(value ushr 24) and 0xff}.${(value ushr 16) and 0xff}." +
            "${(value ushr 8) and 0xff}.${value and 0xff}"

    private data class ServiceSpec(
        val serviceType: String,
        val source: DiscoverySource,
    )

    private companion object {
        val SERVICE_SPECS = listOf(
            ServiceSpec("_adb._tcp", DiscoverySource.MDNS_CONNECT),
            ServiceSpec("_adb-tls-connect._tcp", DiscoverySource.MDNS_CONNECT),
            ServiceSpec("_adb-tls-pairing._tcp", DiscoverySource.MDNS_PAIRING),
        )
        const val MDNS_WINDOW_MS = 5_000L
        const val LEGACY_ADB_PORT = 5555
        const val PORT_SCAN_TIMEOUT_MS = 400
        const val PORT_SCAN_CONCURRENCY = 48
        const val MAX_SCAN_HOSTS = 512
    }
}
