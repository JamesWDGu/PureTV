package com.puretv.mobilehelper.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.puretv.mobilehelper.data.SavedDevice
import com.puretv.mobilehelper.ui.ConnectionUiState
import com.puretv.mobilehelper.ui.ErrorPanel
import com.puretv.mobilehelper.ui.SectionTitle
import com.puretv.mobilehelper.ui.UiState
import com.puretv.mobilehelper.ui.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onConnect: (String, String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenDevice: () -> Unit,
    onPairClick: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDiscovered: (host: String, port: Int) -> Unit,
    onPairDiscovered: (host: String, port: Int) -> Unit,
    onRenameDevice: (SavedDevice) -> Unit,
    onRemoveSavedDevice: (SavedDevice) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PureTV 手机助手") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ConnectionCard(state, onConnect, onDisconnect, onOpenDevice, onPairClick) }

            item {
                DiscoveryCard(
                    state = state.discovery,
                    savedDevices = state.savedDevices,
                    onScan = onScan,
                    onStop = onStopScan,
                    onConnect = onConnectDiscovered,
                    onPair = onPairDiscovered,
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle("已保存的电视")
            }

            if (state.savedDevices.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "连接成功后会自动保存电视。下次点击即可快速连接。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            } else {
                items(state.savedDevices, key = { it.id }) { device ->
                    val isCurrent = state.connectedDevice?.address == device.address
                    SavedDeviceRow(
                        device = device,
                        enabled = state.connection !is ConnectionUiState.Connecting && !isCurrent,
                        isCurrent = isCurrent,
                        onConnect = { onConnect(device.host, device.port.toString()) },
                        onRename = { onRenameDevice(device) },
                        onDelete = { onRemoveSavedDevice(device) },
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                HelpCard()
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    state: UiState,
    onConnect: (String, String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenDevice: () -> Unit,
    onPairClick: () -> Unit,
) {
    var host by rememberSaveable { mutableStateOf(state.lastHost) }
    var port by rememberSaveable { mutableStateOf(state.lastPort) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        when (val connection = state.connection) {
            is ConnectionUiState.Connected -> {
                val device = connection.device
                Column(Modifier.padding(20.dp)) {
                    Text("已连接", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(state.connectedDisplayName, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Android ${device.androidVersion} · ${device.address}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenDevice, modifier = Modifier.weight(1f)) {
                            Text("管理应用")
                        }
                        OutlinedButton(onClick = onDisconnect) {
                            Text("断开")
                        }
                    }
                }
            }

            is ConnectionUiState.Connecting -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Text("正在连接…", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${connection.host}:${connection.port}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "若电视弹出调试授权提示，请选择允许。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            is ConnectionUiState.Disconnected, is ConnectionUiState.Failed -> {
                Column(Modifier.padding(20.dp)) {
                    Text("连接电视", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "手机和电视连接同一网络即可连接，无需数据线。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("电视 IP 地址") },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = { Text("连接端口") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (connection is ConnectionUiState.Failed) {
                        Spacer(Modifier.height(12.dp))
                        ErrorPanel(connection.error.title, connection.error.hint)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onConnect(host, port) },
                        enabled = host.isNotBlank() && port.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("连接")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onPairClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("无线调试配对（Android 11+）")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "首次连接需在电视的“开发者选项”中开启“网络调试”或“ADB 调试”，允许手机管理电视。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedDeviceRow(
    device: SavedDevice,
    enabled: Boolean,
    isCurrent: Boolean,
    onConnect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(device.displayName, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    device.address + " · " + formatTimestamp(device.lastConnectedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onConnect, enabled = enabled) {
                Text(if (isCurrent) "已连接" else "连接")
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "重命名")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }
}

@Composable
private fun HelpCard() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text("连接准备", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))
            HelpStep("1", "在电视上打开“设置 → 关于”，连续点击版本号开启开发者模式；不同电视的菜单可能不同。")
            HelpStep("2", "进入“开发者选项”，开启“网络调试”或“ADB 调试”，允许手机管理电视。")
            HelpStep("3", "确认手机和电视连接同一 Wi-Fi，填写电视 IP 地址和连接端口（网络调试通常为 5555，无线调试请以电视显示为准）。")
            HelpStep("4", "首次连接时在电视上允许这台手机的调试授权。")
        }
    }
}

@Composable
private fun HelpStep(index: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(20.dp),
        ) {
            Text(
                index,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
