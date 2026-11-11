package com.puretv.mobilehelper.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.mobilehelper.data.CaptivePortalPreset
import com.puretv.mobilehelper.data.DeviceInfo
import com.puretv.mobilehelper.data.NetworkSettings
import com.puretv.mobilehelper.ui.ErrorPanel
import com.puretv.mobilehelper.ui.NetworkControlUiState
import com.puretv.mobilehelper.ui.RemoteInputMode

/**
 * Device control panel: a list of features. Each feature opens its own page so future
 * features can be added without growing a single screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControlScreen(
    uiState: NetworkControlUiState,
    device: DeviceInfo?,
    deviceName: String,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onApplyNtp: (String) -> Unit,
    onApplyCaptive: (CaptivePortalPreset) -> Unit,
    onTestNtp: () -> Unit,
    onTestCaptive: () -> Unit,
    onRestoreNtpDefault: () -> Unit,
    onRestoreCaptiveDefault: () -> Unit,
    onSetRemoteInputText: (String) -> Unit,
    onSetRemoteInputMode: (RemoteInputMode) -> Unit,
    onSendRemoteInputNow: () -> Unit,
    onPullRemoteInput: () -> Unit,
    onSendRemoteInputKey: (String) -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }

    var openFeatureId by rememberSaveable { mutableStateOf<String?>(null) }
    val openFeature = ControlPanelFeature.fromId(openFeatureId)
    val current = uiState.current

    BackHandler(enabled = openFeature != null) {
        openFeatureId = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (openFeature != null) {
                            openFeatureId = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Column {
                        Text(
                            openFeature?.title ?: "设备控制面板",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (device != null) {
                            Text(
                                "${deviceName.ifBlank { device.displayName }} · ${device.address}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.applying) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (openFeature == ControlPanelFeature.REMOTE_INPUT) {
                RemoteInputControlPage(
                    remoteInput = uiState.remoteInput,
                    onTextChange = onSetRemoteInputText,
                    onModeChange = onSetRemoteInputMode,
                    onSendNow = onSendRemoteInputNow,
                    onPull = onPullRemoteInput,
                    onSendKey = onSendRemoteInputKey,
                )
            } else when {
                uiState.loading && current == null -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("正在读取电视网络设置…")
                    }
                }

                uiState.error != null && current == null -> {
                    Column(Modifier.padding(16.dp)) {
                        ErrorPanel("网络设置操作失败", uiState.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onLoad) {
                            Text("重新读取")
                        }
                    }
                }

                current == null -> {
                    Text(
                        "尚未读取到设置。请确认电视仍处于连接状态。",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                openFeature == ControlPanelFeature.NTP -> NtpControlPage(
                    uiState = uiState,
                    onApplyNtp = onApplyNtp,
                    onTestNtp = onTestNtp,
                    onRestoreNtpDefault = onRestoreNtpDefault,
                )

                openFeature == ControlPanelFeature.CAPTIVE_PORTAL -> CaptivePortalControlPage(
                    uiState = uiState,
                    onApplyCaptive = onApplyCaptive,
                    onTestCaptive = onTestCaptive,
                    onRestoreCaptiveDefault = onRestoreCaptiveDefault,
                )

                else -> ControlPanelList(
                    current = current,
                    onOpenFeature = { openFeatureId = it.id },
                )
            }
        }
    }
}

@Composable
private fun ControlPanelList(
    current: NetworkSettings,
    onOpenFeature: (ControlPanelFeature) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "选择要使用的功能：调整电视自动校时和联网检测设置，或向电视发送文字和遥控按键。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "点击下方功能查看详情和操作说明。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(ControlPanelFeature.entries) { feature ->
            FeatureRow(
                feature = feature,
                currentValue = feature.currentValue(current),
                onClick = { onOpenFeature(feature) },
            )
        }
    }
}

@Composable
private fun FeatureRow(
    feature: ControlPanelFeature,
    currentValue: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                feature.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(feature.title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (currentValue.isBlank()) {
                        feature.description
                    } else {
                        "$currentValue · ${feature.description}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ControlPanelFeature.currentValue(settings: NetworkSettings): String = when (this) {
    ControlPanelFeature.NTP ->
        "当前：" + settings.ntpServer.ifBlank { "未设置" }

    ControlPanelFeature.CAPTIVE_PORTAL -> {
        val primary = settings.captiveHttpsUrl
            .ifBlank { settings.captiveHttpUrl }
            .ifBlank { settings.captiveServer }
        "当前：" + primary.ifBlank { "未设置" }
    }

    ControlPanelFeature.REMOTE_INPUT -> ""
}
