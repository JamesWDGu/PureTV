package com.puretv.mobilehelper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.mobilehelper.data.NetworkPresets
import com.puretv.mobilehelper.ui.NetworkControlUiState
import com.puretv.mobilehelper.ui.SectionTitle

@Composable
fun NtpControlPage(
    uiState: NetworkControlUiState,
    onApplyNtp: (String) -> Unit,
    onTestNtp: () -> Unit,
    onRestoreNtpDefault: () -> Unit,
) {
    val current = uiState.current ?: return
    val defaults = uiState.defaults
    val speedTests = uiState.speedTests
    var selected by rememberSaveable(current.ntpServer) { mutableStateOf(current.ntpServer) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SpeedTestBar(
                description = "由手机测试下方校时服务器的响应速度，不代表电视的检测结果。",
                total = NetworkPresets.ntp.size,
                done = speedTests.ntpResults.size,
                success = speedTests.ntpResults.values.count { it.success },
                testing = speedTests.testingNtp.isNotEmpty(),
                onTest = onTestNtp,
            )
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    SectionTitle("自动校时")
                    Spacer(Modifier.height(8.dp))
                    ControlValueRow("当前服务器", current.ntpServer.ifBlank { "未设置" })

                    if (defaults != null) {
                        Spacer(Modifier.height(10.dp))
                        DefaultSummary(lines = listOf(defaults.ntpServer.ifBlank { "未设置" }))
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("常用服务器", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    NetworkPresets.ntp.forEach { preset ->
                        PresetRow(
                            selected = selected == preset.server,
                            title = preset.server,
                            supporting = null,
                            isDefault = defaults?.ntpServer == preset.server,
                            isCurrent = current.ntpServer == preset.server,
                            speedTest = speedTests.ntpResults[preset.server],
                            onClick = { selected = preset.server },
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onApplyNtp(selected) },
                            enabled = selected.isNotBlank() &&
                                selected != current.ntpServer &&
                                !uiState.applying,
                        ) {
                            Text("应用所选")
                        }
                        OutlinedButton(
                            onClick = onRestoreNtpDefault,
                            enabled = defaults != null &&
                                current.ntpServer != defaults.ntpServer &&
                                !uiState.applying,
                        ) {
                            Text("恢复原设置")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "选择电视自动校准时间使用的服务器。修改后需等待下一次自动校时，或重启电视后生效。" +
                            "首次读取时的设置保存在本手机中；恢复原设置会恢复这份记录，并非恢复出厂设置。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
