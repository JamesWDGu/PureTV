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
import com.puretv.mobilehelper.data.CaptivePortalPreset
import com.puretv.mobilehelper.data.NetworkPresets
import com.puretv.mobilehelper.data.NetworkSettings
import com.puretv.mobilehelper.ui.NetworkControlUiState
import com.puretv.mobilehelper.ui.SectionTitle

@Composable
fun CaptivePortalControlPage(
    uiState: NetworkControlUiState,
    onApplyCaptive: (CaptivePortalPreset) -> Unit,
    onTestCaptive: () -> Unit,
    onRestoreCaptiveDefault: () -> Unit,
) {
    val current = uiState.current ?: return
    val defaults = uiState.defaults
    val speedTests = uiState.speedTests
    var selectedPresetId by rememberSaveable(
        current.captiveHttpUrl,
        current.captiveHttpsUrl,
        current.captiveServer,
    ) {
        mutableStateOf(
            NetworkPresets.captivePortal
                .firstOrNull { preset -> current.matches(preset) }
                ?.id
                .orEmpty()
        )
    }
    val selectedPreset = NetworkPresets.captivePortal.firstOrNull { it.id == selectedPresetId }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SpeedTestBar(
                description = "由手机测试下方检测地址的响应速度，不代表电视的检测结果。",
                total = NetworkPresets.captivePortal.size,
                done = speedTests.captiveResults.size,
                success = speedTests.captiveResults.values.count { it.success },
                testing = speedTests.testingCaptive.isNotEmpty(),
                onTest = onTestCaptive,
            )
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    SectionTitle("联网检测")
                    Spacer(Modifier.height(8.dp))
                    ControlValueRow("加密检测", current.captiveHttpsUrl.ifBlank { "未设置" })
                    ControlValueRow("兼容检测", current.captiveHttpUrl.ifBlank { "未设置" })
                    ControlValueRow("备用地址", current.captiveFallbackUrl.ifBlank { "未设置" })
                    ControlValueRow("其他备用", current.captiveOtherFallbackUrls.ifBlank { "未设置" })
                    ControlValueRow("检测服务器", current.captiveServer.ifBlank { "未设置" })
                    ControlValueRow("检测方式", modeLabel(current.captiveMode))

                    if (defaults != null) {
                        Spacer(Modifier.height(10.dp))
                        val defaultLines = buildList {
                            if (defaults.captiveHttpsUrl.isNotBlank()) add("主要检测地址（加密）: ${defaults.captiveHttpsUrl}")
                            if (defaults.captiveHttpUrl.isNotBlank()) add("兼容检测地址: ${defaults.captiveHttpUrl}")
                            if (defaults.captiveFallbackUrl.isNotBlank()) add("备用地址: ${defaults.captiveFallbackUrl}")
                            if (defaults.captiveOtherFallbackUrls.isNotBlank()) {
                                add("其他备用地址: ${defaults.captiveOtherFallbackUrls}")
                            }
                            if (defaults.captiveServer.isNotBlank()) add("检测服务器: ${defaults.captiveServer}")
                        }
                        DefaultSummary(lines = defaultLines.ifEmpty { listOf("未设置") })
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("常用检测服务", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    NetworkPresets.captivePortal.forEach { preset ->
                        PresetRow(
                            selected = selectedPresetId == preset.id,
                            title = preset.label,
                            supporting = preset.primaryUrl,
                            isDefault = defaults?.matches(preset) == true,
                            isCurrent = current.matches(preset),
                            speedTest = speedTests.captiveResults[preset.id],
                            onClick = { selectedPresetId = preset.id },
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { selectedPreset?.let(onApplyCaptive) },
                            enabled = selectedPreset != null &&
                                !current.matches(selectedPreset) &&
                                !uiState.applying,
                        ) {
                            Text("应用所选")
                        }
                        OutlinedButton(
                            onClick = onRestoreCaptiveDefault,
                            enabled = defaults != null &&
                                !current.captiveEquals(defaults) &&
                                !uiState.applying,
                        ) {
                            Text("恢复原设置")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "电视通过访问这些地址判断网络能否上网。这里只修改检测地址，不改变检测方式；" +
                            "修改后可能需要重新连接网络或重启电视才会生效。" +
                            "首次读取时的设置保存在本手机中；恢复原设置会恢复这份记录，并非恢复出厂设置。" +
                            "\n对应系统设置项：captive_portal_http_url / https_url / fallback_url / " +
                            "other_fallback_urls / server；不修改 captive_portal_mode（检测方式）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun modeLabel(mode: String): String = when (mode) {
    "0" -> "0（关闭检测）"
    "1" -> "1（提示登录）"
    "2" -> "2（避免该网络）"
    else -> mode.ifBlank { "未设置" }
}
