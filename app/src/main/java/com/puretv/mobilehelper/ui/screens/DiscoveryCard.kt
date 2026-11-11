package com.puretv.mobilehelper.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.mobilehelper.data.DiscoveredDevice
import com.puretv.mobilehelper.data.DiscoverySource
import com.puretv.mobilehelper.data.DiscoveryUiState
import com.puretv.mobilehelper.data.SavedDevice
import kotlinx.coroutines.launch

@Composable
fun DiscoveryCard(
    state: DiscoveryUiState,
    savedDevices: List<SavedDevice>,
    onScan: () -> Unit,
    onStop: () -> Unit,
    onConnect: (host: String, port: Int) -> Unit,
    onPair: (host: String, port: Int) -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("查找同一网络的电视", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "查找同一网络中已开启调试的电视，不会自动连接。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScan, enabled = !state.running) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.completed || state.devices.isNotEmpty()) "重新查找" else "开始查找")
                }
                if (state.running) {
                    OutlinedButton(onClick = onStop) {
                        Text("停止")
                    }
                }
            }

            if (state.running) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "正在查找… 使用 Android 11 及以上版本的无线调试时，通常需要先配对。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.warning?.let { warning ->
                Spacer(Modifier.height(10.dp))
                Text(
                    warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.devices.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                state.devices.forEachIndexed { index, device ->
                    if (index > 0) HorizontalDivider()
                    DiscoveredDeviceRow(
                        device = device,
                        savedDevice = savedDevices.firstOrNull {
                            it.host == device.host && it.port == device.port
                        },
                        onConnect = { onConnect(device.host, device.port) },
                        onPair = { onPair(device.host, device.port) },
                    )
                }
            } else if (!state.running && state.completed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "未发现设备。请确认手机与电视连接同一 Wi-Fi，电视已开启网络调试；" +
                        "使用 Android 11 及以上版本的无线调试时，请在首页选择“无线调试配对”。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "只查找手机当前 Wi-Fi 或有线网络中的设备；查找结果只留在本次页面，不会上传。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiscoveredDeviceRow(
    device: DiscoveredDevice,
    savedDevice: SavedDevice?,
    onConnect: () -> Unit,
    onPair: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        savedDevice != null -> "${savedDevice.displayName}（已保存）"
                        device.source == DiscoverySource.PORT_SCAN -> "未命名设备"
                        else -> device.name
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (savedDevice != null) SavedDeviceMatchTooltip()
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatAddress(device.host, device.port),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = if (device.requiresPairing) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        when (device.source) {
                            DiscoverySource.MDNS_CONNECT -> "无线调试"
                            DiscoverySource.MDNS_PAIRING -> "需要配对"
                            DiscoverySource.PORT_SCAN -> "连接端口 5555"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (device.requiresPairing) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
        if (savedDevice == null || device.requiresPairing) {
            TextButton(
                onClick = if (device.requiresPairing) onPair else onConnect,
            ) {
                Text(if (device.requiresPairing) "配对" else "连接")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedDeviceMatchTooltip() {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(modifier = Modifier.clickable { tooltipState.dismiss() }) {
                Text("IP 地址和连接端口与保存记录相同，不一定还是原来的电视。换了网络，或其他设备用了这个地址，都可能认错；请核对后再从“已保存的电视”连接。再次点击图标、此提示或页面其他位置可关闭。")
            }
        },
        state = tooltipState,
        focusable = true,
        onDismissRequest = { tooltipState.dismiss() },
    ) {
        IconButton(onClick = {
            if (tooltipState.isVisible) {
                tooltipState.dismiss()
            } else {
                scope.launch { tooltipState.show() }
            }
        }) {
            Icon(Icons.Default.Info, contentDescription = "已保存设备识别说明")
        }
    }
}

private fun formatAddress(host: String, port: Int): String =
    if (host.contains(':')) "[$host]:$port" else "$host:$port"
