package com.puretv.mobilehelper.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puretv.mobilehelper.data.AppUpdater
import com.puretv.mobilehelper.data.InstallProgress
import com.puretv.mobilehelper.data.InstallStage
import com.puretv.mobilehelper.data.InstalledApp
import com.puretv.mobilehelper.data.SavedDevice
import com.puretv.mobilehelper.ui.screens.DeviceControlScreen
import com.puretv.mobilehelper.ui.screens.DeviceScreen
import com.puretv.mobilehelper.ui.screens.HomeScreen

private enum class Screen { Home, Device, Control }

@Composable
fun PureTvApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var screenName by rememberSaveable { mutableStateOf(Screen.Home.name) }
    var showPairingDialog by rememberSaveable { mutableStateOf(false) }
    var pairingInitialHost by rememberSaveable { mutableStateOf("") }
    var pairingInitialPort by rememberSaveable { mutableStateOf("") }
    var pairingInitialConnectPort by rememberSaveable { mutableStateOf("5555") }
    var renameTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val screen = Screen.valueOf(screenName)
    val context = androidx.compose.ui.platform.LocalContext.current
    val appUpdater = remember { AppUpdater(context) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> {
                    snackbarHostState.showSnackbar(
                        message = listOfNotNull(event.text, event.detail)
                            .filter { it.isNotBlank() }
                            .joinToString("\n"),
                        withDismissAction = event.detail != null,
                        duration = if (event.detail != null) {
                            SnackbarDuration.Long
                        } else {
                            SnackbarDuration.Short
                        },
                    )
                }

                is UiEvent.Paired -> {
                    showPairingDialog = false
                    snackbarHostState.showSnackbar("配对成功，正在连接电视…")
                }

                is UiEvent.InstallPackage -> {
                    if (!appUpdater.canInstallPackages()) {
                        runCatching { context.startActivity(appUpdater.unknownSourcesSettingsIntent()) }
                        snackbarHostState.showSnackbar("请允许安装未知应用，然后返回点击“安装更新”。")
                    } else {
                        val launched = runCatching {
                            context.startActivity(appUpdater.installIntent(event.file))
                        }.isSuccess
                        if (!launched) {
                            snackbarHostState.showSnackbar("无法打开系统安装界面，请稍后重试。")
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(state.connection) {
        when (state.connection) {
            is ConnectionUiState.Connected -> {
                if (screenName == Screen.Home.name) screenName = Screen.Device.name
            }

            is ConnectionUiState.Disconnected -> screenName = Screen.Home.name
            else -> Unit
        }
    }

    BackHandler(enabled = screen != Screen.Home) {
        screenName = when (screen) {
            Screen.Control -> Screen.Device.name
            else -> Screen.Home.name
        }
    }

    when (screen) {
        Screen.Home -> HomeScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onConnect = viewModel::connect,
            onDisconnect = viewModel::disconnect,
            onOpenDevice = { screenName = Screen.Device.name },
            onPairClick = {
                viewModel.stopDiscovery()
                viewModel.clearPairingError()
                pairingInitialHost = state.lastHost
                pairingInitialPort = ""
                pairingInitialConnectPort = "5555"
                showPairingDialog = true
            },
            onScan = viewModel::startDiscovery,
            onStopScan = viewModel::stopDiscovery,
            onConnectDiscovered = { host, port ->
                viewModel.connect(host, port.toString())
            },
            onPairDiscovered = { host, port ->
                val suggestedConnectPort = state.discovery.devices
                    .firstOrNull {
                        it.host == host && it.source == com.puretv.mobilehelper.data.DiscoverySource.MDNS_CONNECT
                    }
                    ?.port
                    ?.toString()
                    ?: "5555"
                viewModel.stopDiscovery()
                viewModel.clearPairingError()
                pairingInitialHost = host
                pairingInitialPort = port.toString()
                pairingInitialConnectPort = suggestedConnectPort
                showPairingDialog = true
            },
            onRenameDevice = { renameTargetId = it.id },
            onRemoveSavedDevice = viewModel::removeSavedDevice,
            onCheckUpdate = { viewModel.checkForUpdates(manual = true) },
        )

        Screen.Device -> DeviceScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onBack = { screenName = Screen.Home.name },
            onRefresh = viewModel::refreshApps,
            onDisconnect = viewModel::disconnect,
            onSetIncludeSystem = viewModel::setIncludeSystem,
            onSetQuery = viewModel::setQuery,
            onSetInstallOptions = viewModel::setInstallOptions,
            onInstall = viewModel::install,
            onOpenApp = viewModel::openAppDetails,
            onLaunchApp = viewModel::launchApp,
            onForceStopApp = viewModel::forceStop,
            onUninstallApp = viewModel::requestUninstall,
            onFreezeApp = viewModel::requestFreeze,
            onUnfreezeApp = viewModel::unfreezeApp,
            onOpenControls = { screenName = Screen.Control.name },
            onRenameDevice = { renameTargetId = it.id },
            onCloseAppDetails = viewModel::closeAppDetails,
        )

        Screen.Control -> DeviceControlScreen(
            uiState = state.networkControl,
            device = state.connectedDevice,
            deviceName = state.connectedDisplayName,
            snackbarHostState = snackbarHostState,
            onBack = { screenName = Screen.Device.name },
            onLoad = viewModel::loadNetworkControl,
            onApplyNtp = viewModel::applyNtp,
            onApplyCaptive = viewModel::applyCaptivePortal,
            onTestNtp = viewModel::testNtpSpeedTests,
            onTestCaptive = viewModel::testCaptivePortalSpeedTests,
            onRestoreNtpDefault = viewModel::restoreNtpDefault,
            onRestoreCaptiveDefault = viewModel::restoreCaptiveDefault,
            onSetRemoteInputText = viewModel::setRemoteInputText,
            onSetRemoteInputMode = viewModel::setRemoteInputMode,
            onSendRemoteInputNow = viewModel::sendRemoteInputNow,
            onPullRemoteInput = viewModel::pullRemoteInputText,
            onSendRemoteInputKey = viewModel::sendRemoteInputKey,
        )
    }

    if (showPairingDialog) {
        PairingDialog(
            initialHost = pairingInitialHost.ifBlank { state.lastHost },
            initialPairingPort = pairingInitialPort,
            initialConnectPort = pairingInitialConnectPort,
            pairing = state.pairing,
            error = state.pairingError,
            onDismiss = {
                if (!state.pairing) {
                    showPairingDialog = false
                    viewModel.clearPairingError()
                }
            },
            onPair = viewModel::pairAndConnect,
        )
    }

    state.installProgress?.let { progress ->
        InstallProgressDialog(progress)
    }

    state.uninstallTarget?.let { target ->
        ConfirmUninstallDialog(
            app = target,
            onConfirm = viewModel::confirmUninstall,
            onDismiss = viewModel::dismissUninstall,
        )
    }

    state.freezeTarget?.let { target ->
        ConfirmFreezeDialog(
            app = target,
            step = state.freezeConfirmStep,
            onConfirm = viewModel::confirmFreeze,
            onDismiss = viewModel::dismissFreeze,
        )
    }

    if (state.update.visible) {
        UpdateDialog(
            state = state.update,
            onDownload = viewModel::downloadUpdate,
            onInstall = viewModel::installUpdate,
            onDismiss = viewModel::dismissUpdateDialog,
        )
    }

    val renameTarget = renameTargetId?.let { id ->
        state.savedDevices.firstOrNull { it.id == id }
    }
    if (renameTarget != null) {
        RenameDeviceDialog(
            device = renameTarget,
            onSave = { name ->
                viewModel.renameSavedDevice(renameTarget, name)
                renameTargetId = null
            },
            onDismiss = { renameTargetId = null },
        )
    }
}

@Composable
private fun RenameDeviceDialog(
    device: SavedDevice,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(device.id) { mutableStateOf(device.customName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名设备") },
        text = {
            Column {
                Text(
                    "自动识别名称：${device.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { value ->
                        name = value
                            .replace("\n", "")
                            .replace("\r", "")
                            .take(MAX_DEVICE_NAME_LENGTH)
                    },
                    label = { Text("自定义名称") },
                    placeholder = { Text(device.name) },
                    supportingText = { Text("留空则使用自动识别的型号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (name.isNotBlank()) {
                    TextButton(onClick = { name = "" }) {
                        Text("使用自动名称")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private const val MAX_DEVICE_NAME_LENGTH = 40

@Composable
private fun PairingDialog(
    initialHost: String,
    initialPairingPort: String,
    initialConnectPort: String,
    pairing: Boolean,
    error: com.puretv.mobilehelper.data.FriendlyError?,
    onDismiss: () -> Unit,
    onPair: (host: String, pairingPort: String, pairingCode: String, connectPort: String) -> Unit,
) {
    var host by rememberSaveable { mutableStateOf(initialHost) }
    var pairingPort by rememberSaveable { mutableStateOf(initialPairingPort) }
    var pairingCode by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf(initialConnectPort) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("无线调试配对") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "适用于 Android 11 及以上版本的电视。请在电视的“无线调试”页面选择“使用配对码配对设备”，然后填写下面的信息。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("电视 IP 地址") },
                    singleLine = true,
                    enabled = !pairing,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pairingPort,
                    onValueChange = { pairingPort = it.filter(Char::isDigit).take(5) },
                    label = { Text("配对端口") },
                    placeholder = { Text("如 37755") },
                    singleLine = true,
                    enabled = !pairing,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { pairingCode = it.filter(Char::isDigit).take(6) },
                    label = { Text("6 位配对码") },
                    singleLine = true,
                    enabled = !pairing,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = connectPort,
                    onValueChange = { connectPort = it.filter(Char::isDigit).take(5) },
                    label = { Text("连接端口") },
                    supportingText = { Text("填写电视“无线调试”主页中“IP 地址和端口”显示的端口，不是配对码弹窗中的配对端口；使用网络调试时通常为 5555。") },
                    singleLine = true,
                    enabled = !pairing,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                error.title,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (!error.hint.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    error.hint,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPair(host, pairingPort, pairingCode, connectPort) },
                enabled = !pairing,
            ) {
                if (pairing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (pairing) "正在配对…" else "开始配对")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !pairing) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun InstallProgressDialog(progress: InstallProgress) {
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("正在安装", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = progress.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                if (progress.fileCount > 1) {
                    Text(
                        "第 ${progress.fileIndex} / ${progress.fileCount} 个文件",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(20.dp))
                val fraction = progress.fraction
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    when (progress.stage) {
                        InstallStage.COPYING -> "正在读取 APK 安装包…"
                        InstallStage.INSTALLING -> "正在传到电视并安装…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (progress.bytesTotal > 0) {
                        "${formatBytes(progress.bytesDone)} / ${formatBytes(progress.bytesTotal)}"
                    } else {
                        formatBytes(progress.bytesDone)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "安装期间请保持本应用在手机屏幕上打开，并确保电视网络稳定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConfirmFreezeDialog(
    app: InstalledApp,
    step: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val systemFirstStep = app.isSystem && step < 2
    val systemFinalStep = app.isSystem && step >= 2
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { androidx.compose.material3.Icon(Icons.Default.Warning, contentDescription = null) },
        title = {
            Text(
                when {
                    systemFirstStep -> "冻结系统应用？（1/2）"
                    systemFinalStep -> "再次确认冻结系统应用（2/2）"
                    else -> "冻结应用"
                }
            )
        },
        text = {
            Text(
                when {
                    systemFirstStep ->
                        "这是系统应用。冻结后会隐藏并停止使用该应用，已打开的应用也会停止运行，但会保留数据和安装包。" +
                            "可能导致电视桌面、设置、系统界面或其他系统功能异常，严重时无法通过电视界面恢复。" +
                            "只有手机仍能连接电视时才能通过本助手解冻。下一步需要再次确认。"
                    systemFinalStep ->
                        "将冻结 ${app.packageName}。应用会从电视桌面隐藏并停止使用，已打开的应用也会停止运行，数据和安装包会保留。" +
                            "这可能导致桌面、设置、系统界面或其他系统功能异常；只有手机仍能连接电视时才能通过本助手解冻。"
                    else ->
                        "将冻结 ${app.packageName}。它会从电视桌面隐藏并停止使用，已打开的应用也会停止运行，应用数据和安装包会保留。" +
                            "需要再次使用时，可连接电视后解冻。"
                }
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (systemFinalStep) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(
                    when {
                        systemFirstStep -> "继续"
                        systemFinalStep -> "确认冻结"
                        else -> "冻结"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ConfirmUninstallDialog(
    app: InstalledApp,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { androidx.compose.material3.Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text("卸载应用") },
        text = {
            Text(
                "将从电视卸载 ${app.packageName}，应用数据会一并删除。此操作不可撤销。",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("卸载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
