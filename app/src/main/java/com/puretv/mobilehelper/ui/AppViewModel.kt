package com.puretv.mobilehelper.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.puretv.mobilehelper.data.AdbRepository
import com.puretv.mobilehelper.data.AppDetails
import com.puretv.mobilehelper.data.AppUpdate
import com.puretv.mobilehelper.data.AppUpdater
import com.puretv.mobilehelper.data.CaptivePortalPreset
import com.puretv.mobilehelper.data.DeviceInfo
import com.puretv.mobilehelper.data.DeviceStore
import com.puretv.mobilehelper.data.DiscoveryUiState
import com.puretv.mobilehelper.data.FriendlyError
import com.puretv.mobilehelper.data.FocusedInputMissingException
import com.puretv.mobilehelper.data.InstallOptions
import com.puretv.mobilehelper.data.InstallProgress
import com.puretv.mobilehelper.data.InstallStage
import com.puretv.mobilehelper.data.InstalledApp
import com.puretv.mobilehelper.data.LanDiscovery
import com.puretv.mobilehelper.data.NetworkPresets
import com.puretv.mobilehelper.data.NetworkSettings
import com.puretv.mobilehelper.data.NetworkSpeedTest
import com.puretv.mobilehelper.data.OperationResult
import com.puretv.mobilehelper.data.SavedDevice
import com.puretv.mobilehelper.data.SpeedTestResult
import com.puretv.mobilehelper.data.StoreInstaller
import com.puretv.mobilehelper.data.StoreRelease
import com.puretv.mobilehelper.data.UnsupportedAdbInputTextException
import com.puretv.mobilehelper.data.validatedRemoteTextPrefix
import com.puretv.mobilehelper.data.toFriendlyError
import com.puretv.mobilehelper.data.toInstallFailure
import com.puretv.mobilehelper.data.toPairingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface ConnectionUiState {
    data object Disconnected : ConnectionUiState
    data class Connecting(val host: String, val port: Int) : ConnectionUiState
    data class Connected(val device: DeviceInfo) : ConnectionUiState
    data class Failed(val error: FriendlyError) : ConnectionUiState
}

sealed interface UiEvent {
    data class Message(val text: String, val detail: String? = null) : UiEvent
    data class Paired(val host: String, val connectPort: Int) : UiEvent
    data class InstallPackage(val file: java.io.File) : UiEvent
}

data class UpdateUiState(
    val currentVersion: String = "",
    val currentVersionCode: Long = 0,
    val available: AppUpdate? = null,
    val visible: Boolean = false,
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val readyToInstall: Boolean = false,
    val error: String? = null,
) {
    val progress: Float?
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

data class StoreInstallUiState(
    val loading: Boolean = false,
    val release: StoreRelease? = null,
    val downloading: Boolean = false,
    val installing: Boolean = false,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val onTv: Boolean = false,
    val error: String? = null,
) {
    val progress: Float?
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

data class SpeedTestUiState(
    val ntpResults: Map<String, SpeedTestResult> = emptyMap(),
    val captiveResults: Map<String, SpeedTestResult> = emptyMap(),
    val testingNtp: Set<String> = emptySet(),
    val testingCaptive: Set<String> = emptySet(),
) {
    val isTesting: Boolean
        get() = testingNtp.isNotEmpty() || testingCaptive.isNotEmpty()
}

enum class RemoteInputMode { Manual, Live }
enum class RemoteInputStatus { Idle, Sending, Reading, Sent, Read }

data class RemoteInputUiState(
    val mode: RemoteInputMode = RemoteInputMode.Manual,
    val manualText: String = "",
    val liveText: String = "",
    val status: RemoteInputStatus = RemoteInputStatus.Idle,
    val liveBlocked: Boolean = false,
    val error: String? = null,
) {
    val text: String get() = if (mode == RemoteInputMode.Manual) manualText else liveText
    val busy: Boolean get() = status == RemoteInputStatus.Sending || status == RemoteInputStatus.Reading
}

/** Aggregate state of the device control panel: per-feature state lives here. */
data class NetworkControlUiState(
    val loading: Boolean = false,
    val applying: Boolean = false,
    val current: NetworkSettings? = null,
    val defaults: NetworkSettings? = null,
    val error: String? = null,
    val speedTests: SpeedTestUiState = SpeedTestUiState(),
    val remoteInput: RemoteInputUiState = RemoteInputUiState(),
)

data class UiState(
    val savedDevices: List<SavedDevice> = emptyList(),
    val lastHost: String = "",
    val lastPort: String = "5555",
    val discovery: DiscoveryUiState = DiscoveryUiState(),
    val connection: ConnectionUiState = ConnectionUiState.Disconnected,
    val networkControl: NetworkControlUiState = NetworkControlUiState(),
    val apps: List<InstalledApp> = emptyList(),
    val appsLoading: Boolean = false,
    val appsError: String? = null,
    val query: String = "",
    val includeSystem: Boolean = false,
    val installOptions: InstallOptions = InstallOptions(),
    val installProgress: InstallProgress? = null,
    val pairing: Boolean = false,
    val pairingError: FriendlyError? = null,
    val detailsLoadingFor: String? = null,
    val detailsPackage: String? = null,
    val appDetails: AppDetails? = null,
    val detailsError: String? = null,
    val uninstallTarget: InstalledApp? = null,
    val freezeTarget: InstalledApp? = null,
    val freezeConfirmStep: Int = 0,
    val update: UpdateUiState = UpdateUiState(),
    val storeInstall: StoreInstallUiState = StoreInstallUiState(),
) {
    val connectedDevice: DeviceInfo?
        get() = (connection as? ConnectionUiState.Connected)?.device

    val connectedSavedDevice: SavedDevice?
        get() {
            val device = connectedDevice ?: return null
            val serial = device.serial.takeIf {
                it.isNotBlank() && !it.equals("unknown", ignoreCase = true)
            }
            if (serial != null) {
                savedDevices.firstOrNull { it.serial == serial }?.let { return it }
            }
            return savedDevices.firstOrNull { it.address == device.address }
        }

    val connectedDisplayName: String
        get() = connectedSavedDevice?.displayName
            ?: connectedDevice?.displayName.orEmpty()

    val visibleApps: List<InstalledApp>
        get() {
            val term = query.trim()
            if (term.isEmpty()) return apps
            return apps.filter { it.packageName.contains(term, ignoreCase = true) }
        }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AdbRepository(application.applicationContext)
    private val deviceStore = DeviceStore(application.applicationContext)
    private val lanDiscovery = LanDiscovery(application.applicationContext)
    private val networkSpeedTest = NetworkSpeedTest()
    private val appUpdater = AppUpdater(application.applicationContext)
    private val storeInstaller = StoreInstaller(application.applicationContext)

    private val recent = deviceStore.mostRecent()
    private val _state = MutableStateFlow(
        UiState(
            savedDevices = deviceStore.load(),
            lastHost = recent?.host.orEmpty(),
            lastPort = (recent?.port ?: DEFAULT_ADB_PORT).toString(),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val eventChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            lanDiscovery.state.collect { discovery ->
                _state.update { it.copy(discovery = discovery) }
            }
        }
        _state.update {
            it.copy(
                update = it.update.copy(
                    currentVersion = appUpdater.currentVersionName(),
                    currentVersionCode = appUpdater.currentVersionCode(),
                )
            )
        }
        checkForUpdates(manual = false)
    }

    private var appsRequestId = 0
    private var detailsRequestId = 0
    private var networkControlRequestId = 0
    private var speedTestGeneration = 0
    private var remoteInputGeneration = 0
    private var sentRemoteInputText = ""
    private var remoteInputSyncJob: Job? = null
    private var remoteKeyJob: Job? = null
    private var updateCheckJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var storeInstallJob: Job? = null

    fun connect(hostInput: String, portInput: String) {
        if (_state.value.connection is ConnectionUiState.Connecting) return
        val address = parseAddress(hostInput, portInput)
        if (address == null) {
            _state.update {
                it.copy(
                    connection = ConnectionUiState.Failed(
                        FriendlyError("请检查连接信息", "请输入电视上的 IP 地址，连接端口应为 1 到 65535 之间的数字。")
                    )
                )
            }
            return
        }
        val (host, port) = address
        lanDiscovery.stop()
        appsRequestId += 1
        detailsRequestId += 1
        networkControlRequestId += 1
        speedTestGeneration += 1
        resetRemoteInputSession()
        _state.update {
            it.copy(
                connection = ConnectionUiState.Connecting(host, port),
                networkControl = NetworkControlUiState(),
                apps = emptyList(),
                appsError = null,
                appsLoading = false,
                installProgress = null,
                detailsLoadingFor = null,
                detailsPackage = null,
                appDetails = null,
                detailsError = null,
                uninstallTarget = null,
                freezeTarget = null,
                freezeConfirmStep = 0,
                lastHost = host,
                lastPort = port.toString(),
            )
        }
        viewModelScope.launch {
            try {
                val info = repository.connect(host, port)
                val saved = deviceStore.upsert(host, port, info.displayName, info.serial)
                _state.update {
                    it.copy(
                        connection = ConnectionUiState.Connected(info),
                        savedDevices = saved,
                        lastHost = host,
                        lastPort = port.toString(),
                    )
                }
                loadApps()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        connection = ConnectionUiState.Failed(throwable.toFriendlyError()),
                        apps = emptyList(),
                        appsLoading = false,
                    )
                }
            }
        }
    }

    fun disconnect() {
        appsRequestId += 1
        detailsRequestId += 1
        networkControlRequestId += 1
        speedTestGeneration += 1
        resetRemoteInputSession()
        viewModelScope.launch {
            repository.disconnect()
            _state.update {
                it.copy(
                    connection = ConnectionUiState.Disconnected,
                    apps = emptyList(),
                    appsError = null,
                    installProgress = null,
                    detailsLoadingFor = null,
                    detailsPackage = null,
                    appDetails = null,
                    detailsError = null,
                    uninstallTarget = null,
                    freezeTarget = null,
                    freezeConfirmStep = 0,
                )
            }
        }
    }

    fun removeSavedDevice(device: SavedDevice) {
        val saved = deviceStore.remove(device.id)
        _state.update { it.copy(savedDevices = saved) }
    }

    fun renameSavedDevice(device: SavedDevice, customName: String) {
        val saved = deviceStore.setCustomName(device.id, customName)
        _state.update { it.copy(savedDevices = saved) }
        eventChannel.trySend(
            UiEvent.Message(
                if (customName.isBlank()) "已恢复使用设备原名" else "已重命名为 ${customName.trim()}"
            )
        )
    }

    fun pairAndConnect(
        host: String,
        pairingPort: String,
        pairingCode: String,
        connectPort: String,
    ) {
        val port = pairingPort.toIntOrNull()
        val adbPort = connectPort.toIntOrNull()
        if (host.isBlank() || port == null || port !in 1..65535) {
            _state.update {
                it.copy(
                    pairingError = FriendlyError(
                        "配对信息不完整",
                        "请输入配对地址和电视上显示的配对端口。",
                    )
                )
            }
            return
        }
        if (pairingCode.length != 6 || !pairingCode.all(Char::isDigit)) {
            _state.update {
                it.copy(pairingError = FriendlyError("配对码无效", "配对码应为电视上显示的 6 位数字。"))
            }
            return
        }
        if (adbPort == null || adbPort !in 1..65535) {
            _state.update {
                it.copy(
                    pairingError = FriendlyError(
                        "请检查连接端口",
                        "请填写电视无线调试主页面显示的连接端口，不要填写临时配对窗口里的端口。",
                    )
                )
            }
            return
        }
        _state.update { it.copy(pairing = true, pairingError = null) }
        viewModelScope.launch {
            try {
                repository.pair(host.trim(), port, pairingCode)
                _state.update {
                    it.copy(
                        pairing = false,
                        pairingError = null,
                        lastHost = host.trim(),
                        lastPort = adbPort.toString(),
                    )
                }
                eventChannel.trySend(UiEvent.Paired(host.trim(), adbPort))
                connect(host, adbPort.toString())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(pairing = false, pairingError = throwable.toPairingError())
                }
            }
        }
    }

    fun clearPairingError() {
        _state.update { it.copy(pairingError = null) }
    }

    fun refreshApps() {
        viewModelScope.launch { loadApps() }
    }

    fun startDiscovery() {
        lanDiscovery.start()
    }

    fun stopDiscovery() {
        lanDiscovery.stop()
    }

    fun loadNetworkControl() {
        val device = _state.value.connectedDevice ?: return
        val currentControl = _state.value.networkControl
        if (currentControl.loading || currentControl.applying) return
        val requestId = ++networkControlRequestId
        _state.update {
            it.copy(
                networkControl = it.networkControl.copy(loading = true, error = null)
            )
        }
        viewModelScope.launch {
            try {
                val current = repository.readNetworkSettings()
                val defaults = deviceStore.loadNetworkDefaults(device)
                    ?: deviceStore.saveNetworkDefaultsIfAbsent(device, current)
                if (requestId != networkControlRequestId) return@launch
                _state.update {
                    it.copy(
                        networkControl = it.networkControl.copy(
                            loading = false,
                            current = current,
                            defaults = defaults,
                            error = null,
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                if (requestId != networkControlRequestId) return@launch
                val error = throwable.toFriendlyError()
                _state.update {
                    it.copy(
                        networkControl = it.networkControl.copy(
                            loading = false,
                            error = listOfNotNull(error.title, error.hint).joinToString("\n"),
                        )
                    )
                }
            }
        }
    }

    fun applyNtp(server: String) {
        if (server.isBlank()) return
        applyNetworkChange { repository.setNtpServer(server) }
    }

    fun applyCaptivePortal(preset: CaptivePortalPreset) {
        applyNetworkChange {
            repository.setCaptivePortal(
                httpUrl = preset.httpUrl,
                httpsUrl = preset.httpsUrl,
                fallbackUrl = preset.httpUrl,
                otherFallbackUrls = preset.httpUrl,
                server = preset.server,
            )
        }
    }

    fun restoreNtpDefault() {
        val defaults = _state.value.networkControl.defaults ?: return
        applyNetworkChange { repository.setNtpServer(defaults.ntpServer) }
    }

    fun restoreCaptiveDefault() {
        val defaults = _state.value.networkControl.defaults ?: return
        applyNetworkChange {
            repository.setCaptivePortal(
                httpUrl = defaults.captiveHttpUrl,
                httpsUrl = defaults.captiveHttpsUrl,
                fallbackUrl = defaults.captiveFallbackUrl,
                otherFallbackUrls = defaults.captiveOtherFallbackUrls,
                server = defaults.captiveServer,
            )
        }
    }

    fun testNtpSpeedTests() {
        runSpeedTests(
            ntpServers = NetworkPresets.ntp.map { it.server },
            captivePresets = emptyList(),
        )
    }

    fun testCaptivePortalSpeedTests() {
        runSpeedTests(
            ntpServers = emptyList(),
            captivePresets = NetworkPresets.captivePortal,
        )
    }

    private fun runSpeedTests(
        ntpServers: List<String>,
        captivePresets: List<CaptivePortalPreset>,
    ) {
        val speedTests = _state.value.networkControl.speedTests
        if (speedTests.isTesting) return
        val generation = speedTestGeneration
        updateSpeedTests {
            it.copy(
                ntpResults = if (ntpServers.isEmpty()) it.ntpResults else emptyMap(),
                captiveResults = if (captivePresets.isEmpty()) it.captiveResults else emptyMap(),
                testingNtp = ntpServers.toSet(),
                testingCaptive = captivePresets.map { preset -> preset.id }.toSet(),
            )
        }
        viewModelScope.launch {
            coroutineScope {
                val semaphore = Semaphore(MAX_SPEED_TEST_CONCURRENCY)
                ntpServers.forEach { server ->
                    launch {
                        semaphore.withPermit {
                            val result = networkSpeedTest.testNtp(server)
                            if (generation == speedTestGeneration) {
                                updateSpeedTests {
                                    it.copy(
                                        ntpResults = it.ntpResults + (server to result),
                                        testingNtp = it.testingNtp - server,
                                    )
                                }
                            }
                        }
                    }
                }
                captivePresets.forEach { preset ->
                    launch {
                        semaphore.withPermit {
                            val result = networkSpeedTest.testCaptivePortal(preset.httpsUrl)
                            if (generation == speedTestGeneration) {
                                updateSpeedTests {
                                    it.copy(
                                        captiveResults = it.captiveResults + (preset.id to result),
                                        testingCaptive = it.testingCaptive - preset.id,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateSpeedTests(transform: (SpeedTestUiState) -> SpeedTestUiState) {
        _state.update {
            it.copy(
                networkControl = it.networkControl.copy(
                    speedTests = transform(it.networkControl.speedTests)
                )
            )
        }
    }

    // region Remote input (adb input)

    fun setRemoteInputText(text: String) {
        val remote = _state.value.networkControl.remoteInput
        if ((remote.mode == RemoteInputMode.Manual && remote.busy) || remote.liveBlocked) return
        val cleaned = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
        if (remote.mode == RemoteInputMode.Manual) {
            updateRemoteInput { it.copy(manualText = cleaned, status = RemoteInputStatus.Idle, error = null) }
        } else {
            updateRemoteInput { it.copy(liveText = cleaned, error = null) }
            scheduleLiveInput()
        }
    }

    fun setRemoteInputMode(mode: RemoteInputMode) {
        val remote = _state.value.networkControl.remoteInput
        if (remote.mode == mode || remote.busy) return
        resetRemoteInputSession()
        updateRemoteInput {
            it.copy(mode = mode, liveText = "", status = RemoteInputStatus.Idle, liveBlocked = false, error = null)
        }
    }

    fun sendRemoteInputNow() {
        val remote = _state.value.networkControl.remoteInput
        if (remote.mode != RemoteInputMode.Manual || remote.busy || remote.manualText.isEmpty()) return
        val generation = remoteInputGeneration
        updateRemoteInput { it.copy(status = RemoteInputStatus.Sending, error = null) }
        viewModelScope.launch {
            try {
                repository.inputText(remote.manualText)
                if (generation == remoteInputGeneration) {
                    updateRemoteInput { it.copy(status = RemoteInputStatus.Sent) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                failRemoteInput(throwable, generation)
            }
        }
    }

    fun pullRemoteInputText() {
        val remote = _state.value.networkControl.remoteInput
        if (remote.mode != RemoteInputMode.Manual || remote.busy) return
        val generation = remoteInputGeneration
        updateRemoteInput { it.copy(status = RemoteInputStatus.Reading, error = null) }
        viewModelScope.launch {
            try {
                val tvText = repository.readFocusedInputText() ?: throw FocusedInputMissingException()
                if (generation != remoteInputGeneration) return@launch
                updateRemoteInput { it.copy(manualText = tvText, status = RemoteInputStatus.Read) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                failRemoteInput(throwable, generation)
            }
        }
    }

    fun sendRemoteInputKey(key: String) {
        val device = _state.value.connectedDevice ?: return
        val previousKey = remoteKeyJob
        val pendingTyping = remoteInputSyncJob
        remoteKeyJob = viewModelScope.launch {
            try {
                previousKey?.join()
                pendingTyping?.join()
                if (_state.value.connectedDevice !== device) return@launch
                if (_state.value.networkControl.remoteInput.mode == RemoteInputMode.Live &&
                    key != "VOLUME_UP" && key != "VOLUME_DOWN"
                ) {
                    updateRemoteInput {
                        it.copy(liveBlocked = true, status = RemoteInputStatus.Idle, error = null)
                    }
                }
                repository.inputKey(key)
                if (_state.value.connectedDevice !== device) return@launch
                updateRemoteInput { it.copy(error = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                if (_state.value.connectedDevice !== device) return@launch
                val error = throwable.toFriendlyError()
                eventChannel.trySend(UiEvent.Message(error.title, error.hint))
            }
        }
    }

    /** One writer: typing never cancels a partially applied DEL + insertion operation. */
    private fun scheduleLiveInput() {
        if (remoteInputSyncJob?.isActive == true) return
        val generation = remoteInputGeneration
        remoteInputSyncJob = viewModelScope.launch {
            while (generation == remoteInputGeneration) {
                val target = _state.value.networkControl.remoteInput.liveText
                delay(REMOTE_INPUT_DEBOUNCE_MS)
                if (target != _state.value.networkControl.remoteInput.liveText) continue
                if (!syncRemoteInput(target, generation)) return@launch
                if (target == _state.value.networkControl.remoteInput.liveText) return@launch
            }
        }
    }

    private suspend fun syncRemoteInput(target: String, generation: Int): Boolean {
        if (target == sentRemoteInputText) return true
        updateRemoteInput { it.copy(status = RemoteInputStatus.Sending, error = null) }
        try {
            val sent = sentRemoteInputText
            val commonPrefix = validatedRemoteTextPrefix(sent, target)
            val deletes = sent.length - commonPrefix
            val insert = target.substring(commonPrefix)
            if (deletes > 0) repository.inputKey("DEL", deletes)
            if (insert.isNotEmpty()) repository.inputText(insert)
            if (generation != remoteInputGeneration) return false
            sentRemoteInputText = target
            updateRemoteInput { it.copy(status = RemoteInputStatus.Sent) }
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            failRemoteInput(throwable, generation, blockLive = throwable !is UnsupportedAdbInputTextException)
            return false
        }
    }

    private fun failRemoteInput(throwable: Throwable, generation: Int, blockLive: Boolean = false) {
        if (generation != remoteInputGeneration) return
        val error = throwable.toFriendlyError()
        updateRemoteInput {
            it.copy(
                status = RemoteInputStatus.Idle,
                error = listOfNotNull(error.title, error.hint).joinToString("\n"),
                liveBlocked = blockLive,
            )
        }
    }

    private fun resetRemoteInputSession() {
        remoteInputGeneration += 1
        remoteInputSyncJob?.cancel()
        remoteInputSyncJob = null
        sentRemoteInputText = ""
    }

    private fun updateRemoteInput(transform: (RemoteInputUiState) -> RemoteInputUiState) {
        _state.update {
            it.copy(
                networkControl = it.networkControl.copy(
                    remoteInput = transform(it.networkControl.remoteInput)
                )
            )
        }
    }


    // endregion

    private fun applyNetworkChange(block: suspend () -> OperationResult) {
        val requestId = ++networkControlRequestId
        _state.update {
            it.copy(
                networkControl = it.networkControl.copy(applying = true, error = null)
            )
        }
        viewModelScope.launch {
            try {
                val result = block()
                eventChannel.trySend(UiEvent.Message(result.message, result.detail))
                val current = repository.readNetworkSettings()
                if (requestId != networkControlRequestId) return@launch
                _state.update {
                    it.copy(
                        networkControl = it.networkControl.copy(
                            applying = false,
                            current = current,
                            error = null,
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                if (requestId != networkControlRequestId) return@launch
                val error = throwable.toFriendlyError()
                _state.update {
                    it.copy(
                        networkControl = it.networkControl.copy(
                            applying = false,
                            error = listOfNotNull(error.title, error.hint).joinToString("\n"),
                        )
                    )
                }
                eventChannel.trySend(UiEvent.Message(error.title, error.hint))
            }
        }
    }

    private suspend fun loadApps() {
        if (_state.value.connection !is ConnectionUiState.Connected) return
        val requestId = ++appsRequestId
        val includeSystem = _state.value.includeSystem
        _state.update { it.copy(appsLoading = true, appsError = null) }
        try {
            val apps = repository.listApps(includeSystem)
            if (requestId != appsRequestId) return
            _state.update { it.copy(apps = apps, appsLoading = false, appsError = null) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            if (requestId != appsRequestId) return
            val error = throwable.toFriendlyError()
            _state.update {
                it.copy(
                    appsLoading = false,
                    appsError = listOfNotNull(error.title, error.hint).joinToString("\n"),
                )
            }
        }
    }

    fun setIncludeSystem(includeSystem: Boolean) {
        if (_state.value.includeSystem == includeSystem) return
        _state.update { it.copy(includeSystem = includeSystem) }
        viewModelScope.launch { loadApps() }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun setInstallOptions(options: InstallOptions) {
        _state.update { it.copy(installOptions = options) }
    }

    fun install(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (_state.value.installProgress != null) return
        if (_state.value.connection !is ConnectionUiState.Connected) {
            eventChannel.trySend(UiEvent.Message("请先连接电视"))
            return
        }
        val options = _state.value.installOptions
        _state.update {
            it.copy(
                installProgress = InstallProgress(
                    stage = InstallStage.COPYING,
                    fileName = "准备安装…",
                    fileIndex = 1,
                    fileCount = uris.size,
                    bytesDone = 0,
                    bytesTotal = 0,
                )
            )
        }
        viewModelScope.launch {
            try {
                val result = repository.install(uris, options) { progress ->
                    _state.update { it.copy(installProgress = progress) }
                }
                _state.update { it.copy(installProgress = null) }
                eventChannel.trySend(UiEvent.Message(result.message, result.detail))
                loadApps()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _state.update { it.copy(installProgress = null) }
                val error = throwable.toInstallFailure()
                eventChannel.trySend(UiEvent.Message(error.title, error.hint))
            } finally {
                _state.update { it.copy(installProgress = null) }
            }
        }
    }

    fun openAppDetails(app: InstalledApp) {
        val requestId = ++detailsRequestId
        _state.update {
            it.copy(
                detailsLoadingFor = app.packageName,
                detailsPackage = app.packageName,
                appDetails = null,
                detailsError = null,
            )
        }
        viewModelScope.launch {
            try {
                val details = repository.appDetails(app.packageName)
                if (requestId != detailsRequestId) return@launch
                _state.update {
                    it.copy(
                        appDetails = details,
                        detailsLoadingFor = null,
                        detailsError = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                if (requestId != detailsRequestId) return@launch
                val error = throwable.toFriendlyError()
                _state.update {
                    it.copy(
                        detailsLoadingFor = null,
                        detailsError = listOfNotNull(error.title, error.hint).joinToString("\n"),
                    )
                }
            }
        }
    }

    fun closeAppDetails() {
        detailsRequestId += 1
        _state.update {
            it.copy(
                detailsLoadingFor = null,
                detailsPackage = null,
                appDetails = null,
                detailsError = null,
            )
        }
    }

    fun launchApp(packageName: String) {
        runOperation { repository.launchApp(packageName) }
    }

    fun forceStop(packageName: String) {
        runOperation { repository.forceStop(packageName) }
    }

    fun requestUninstall(app: InstalledApp) {
        _state.update { it.copy(uninstallTarget = app) }
    }

    fun dismissUninstall() {
        _state.update { it.copy(uninstallTarget = null) }
    }

    fun requestFreeze(app: InstalledApp) {
        _state.update { it.copy(freezeTarget = app, freezeConfirmStep = 1) }
    }

    fun dismissFreeze() {
        _state.update { it.copy(freezeTarget = null, freezeConfirmStep = 0) }
    }

    fun confirmFreeze() {
        val target = _state.value.freezeTarget ?: return
        val step = _state.value.freezeConfirmStep
        if (target.isSystem && step < 2) {
            _state.update { it.copy(freezeConfirmStep = 2) }
            return
        }
        _state.update { it.copy(freezeTarget = null, freezeConfirmStep = 0) }
        closeAppDetails()
        applyFrozen(target.packageName, frozen = true)
    }

    fun unfreezeApp(app: InstalledApp) {
        closeAppDetails()
        applyFrozen(app.packageName, frozen = false)
    }

    private fun applyFrozen(packageName: String, frozen: Boolean) {
        viewModelScope.launch {
            try {
                val result = repository.setAppFrozen(packageName, frozen)
                eventChannel.trySend(UiEvent.Message(result.message))
                loadApps()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                val error = throwable.toFriendlyError()
                eventChannel.trySend(UiEvent.Message(error.title, error.hint))
            }
        }
    }

    fun confirmUninstall() {
        val target = _state.value.uninstallTarget ?: return
        _state.update { it.copy(uninstallTarget = null) }
        closeAppDetails()
        viewModelScope.launch {
            try {
                val result = repository.uninstall(target.packageName)
                eventChannel.trySend(UiEvent.Message(result.message))
                loadApps()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                val error = throwable.toFriendlyError()
                eventChannel.trySend(UiEvent.Message(error.title, error.hint))
            }
        }
    }

    private fun runOperation(block: suspend () -> com.puretv.mobilehelper.data.OperationResult) {
        viewModelScope.launch {
            try {
                val result = block()
                eventChannel.trySend(UiEvent.Message(result.message, result.detail))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                val error = throwable.toFriendlyError()
                eventChannel.trySend(UiEvent.Message(error.title, error.hint))
            }
        }
    }

    // region App updates

    fun checkForUpdates(manual: Boolean = false) {
        if (updateCheckJob?.isActive == true) return
        updateCheckJob = viewModelScope.launch {
            updateUpdate { it.copy(checking = true, error = null) }
            try {
                val update = appUpdater.findUpdate()
                if (update == null) {
                    updateUpdate {
                        it.copy(
                            checking = false,
                            available = null,
                            visible = false,
                            downloading = false,
                            readyToInstall = false,
                            error = null,
                        )
                    }
                    if (manual) eventChannel.trySend(UiEvent.Message("已是最新版本"))
                } else {
                    updateUpdate {
                        it.copy(
                            checking = false,
                            available = update,
                            visible = true,
                            downloading = false,
                            readyToInstall = false,
                            downloadedBytes = 0,
                            totalBytes = update.apkSize,
                            error = null,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                updateUpdate { it.copy(checking = false, error = "检查更新失败，请确认网络后重试。") }
                if (manual) {
                    eventChannel.trySend(UiEvent.Message("检查更新失败", "请确认手机网络可用后再试。"))
                }
            }
        }
    }

    fun downloadUpdate() {
        val update = _state.value.update.available ?: return
        val current = _state.value.update
        if (current.downloading || current.readyToInstall) return
        updateDownloadJob = viewModelScope.launch {
            updateUpdate { it.copy(downloading = true, error = null, downloadedBytes = 0) }
            try {
                val file = appUpdater.download(update) { downloaded, total ->
                    updateUpdate { it.copy(downloadedBytes = downloaded, totalBytes = total) }
                }
                updateUpdate {
                    it.copy(
                        downloading = false,
                        readyToInstall = true,
                        downloadedBytes = file.length(),
                        totalBytes = file.length(),
                    )
                }
                eventChannel.trySend(UiEvent.Message("新版本已下载", "点击“安装更新”完成安装。"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                updateUpdate { it.copy(downloading = false, error = "下载失败，请检查网络后重试。") }
            }
        }
    }

    fun installUpdate() {
        val current = _state.value.update
        val version = current.available?.versionName ?: return
        if (!current.readyToInstall) return
        val file = appUpdater.apkFile(version)
        if (!file.exists()) {
            updateUpdate { it.copy(readyToInstall = false, error = "安装包已失效，请重新下载。") }
            return
        }
        eventChannel.trySend(UiEvent.InstallPackage(file))
    }

    fun dismissUpdateDialog() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        updateUpdate {
            it.copy(visible = false, downloading = false, downloadedBytes = 0, error = null)
        }
    }

    private fun updateUpdate(transform: (UpdateUiState) -> UpdateUiState) {
        _state.update { it.copy(update = transform(it.update)) }
    }

    // endregion

    // region PureTVStore installation

    fun loadStoreRelease(force: Boolean = false) {
        if (!force && _state.value.storeInstall.release != null) return
        if (storeInstallJob?.isActive == true) return
        storeInstallJob = viewModelScope.launch {
            updateStoreInstall { it.copy(loading = true, error = null) }
            try {
                val release = storeInstaller.findLatest()
                updateStoreInstall {
                    it.copy(
                        loading = false,
                        release = release,
                        onTv = it.onTv || storeInstalledOnTv(),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                updateStoreInstall { it.copy(loading = false, error = storeErrorMessage(throwable)) }
            }
        }
    }

    fun installStoreOnTv() {
        val current = _state.value.storeInstall
        val release = current.release ?: return
        if (current.downloading || current.installing) return
        if (storeInstallJob?.isActive == true) return
        if (_state.value.connection !is ConnectionUiState.Connected) {
            eventChannel.trySend(UiEvent.Message("请先连接电视"))
            return
        }
        storeInstallJob = viewModelScope.launch {
            updateStoreInstall {
                it.copy(
                    downloading = true,
                    downloadedBytes = 0,
                    totalBytes = release.apkSize,
                    error = null,
                )
            }
            val file = try {
                storeInstaller.download(release) { downloaded, total ->
                    updateStoreInstall { it.copy(downloadedBytes = downloaded, totalBytes = total) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                updateStoreInstall {
                    it.copy(downloading = false, error = storeErrorMessage(throwable))
                }
                eventChannel.trySend(
                    UiEvent.Message("商店安装包下载失败", "请确认手机网络可用后重试。")
                )
                return@launch
            }
            updateStoreInstall {
                it.copy(
                    downloading = false,
                    installing = true,
                    downloadedBytes = file.length(),
                    totalBytes = file.length(),
                )
            }
            try {
                repository.install(listOf(storeInstaller.apkUri(file)), InstallOptions()) { }
                updateStoreInstall { it.copy(installing = false, onTv = true) }
                eventChannel.trySend(
                    UiEvent.Message("商店已安装到电视", "在电视上打开 PureTV 商店即可安装和更新应用。")
                )
                loadApps()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                updateStoreInstall { it.copy(installing = false) }
                val error = throwable.toInstallFailure()
                eventChannel.trySend(UiEvent.Message(error.title, error.hint))
            }
        }
    }

    private fun updateStoreInstall(transform: (StoreInstallUiState) -> StoreInstallUiState) {
        _state.update { it.copy(storeInstall = transform(it.storeInstall)) }
    }

    private fun storeInstalledOnTv(): Boolean =
        _state.value.apps.any { it.packageName == STORE_PACKAGE }

    private fun storeErrorMessage(throwable: Throwable): String {
        val raw = throwable.message.orEmpty()
        return if (raw.any { it in '\u4e00'..'\u9fff' }) {
            raw
        } else {
            "获取商店最新版本失败，请确认手机网络后重试。"
        }
    }

    // endregion

    override fun onCleared() {
        repository.closeNow()
        lanDiscovery.close()
    }

    private fun parseAddress(hostInput: String, portInput: String): Pair<String, Int>? {
        var host = hostInput.trim()
            .removePrefix("adb://")
            .removeSuffix("/")
        var port = portInput.trim().toIntOrNull() ?: return null
        if (host.contains(':')) {
            val pieces = host.split(':')
            if (pieces.size == 2) {
                val explicitPort = pieces[1].toIntOrNull()
                if (explicitPort != null && explicitPort in 1..65535) {
                    host = pieces[0]
                    port = explicitPort
                }
            }
        }
        if (host.isBlank() || host.any { it.isWhitespace() }) return null
        if (port !in 1..65535) return null
        return host to port
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AppViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private companion object {
        const val DEFAULT_ADB_PORT = 5555
        const val STORE_PACKAGE = "com.puretv.store"
        const val MAX_SPEED_TEST_CONCURRENCY = 4
        const val REMOTE_INPUT_DEBOUNCE_MS = 350L
    }
}
