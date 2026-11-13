package com.puretv.mobilehelper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.mobilehelper.data.SpeedTestResult

/**
 * Registry of device-control features. Adding a feature:
 * 1. add an entry here;
 * 2. render its detail page from `DeviceControlScreen`;
 * 3. add speed-test presets/callbacks if needed.
 */
enum class ControlPanelFeature(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
) {
    NTP(
        id = "ntp",
        title = "自动校时",
        description = "选择电视自动校准时间使用的服务器",
        icon = Icons.Default.Refresh,
    ),
    CAPTIVE_PORTAL(
        id = "captive_portal",
        title = "联网检测",
        description = "选择电视判断网络能否上网时使用的检测地址",
        icon = Icons.Default.Lock,
    ),
    REMOTE_INPUT(
        id = "remote_input",
        title = "遥控输入",
        description = "手动发送、实时输入与遥控按键",
        icon = Icons.Default.Edit,
    ),
    STORE(
        id = "store",
        title = "安装 PureTV 商店",
        description = "在电视上安装应用商店，之后可直接安装和更新应用",
        icon = Icons.Default.ShoppingCart,
    );

    companion object {
        fun fromId(id: String?): ControlPanelFeature? = entries.firstOrNull { it.id == id }
    }
}

@Composable
fun SpeedTestBar(
    description: String,
    total: Int,
    done: Int,
    success: Int,
    testing: Boolean,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "一键测速",
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        testing -> "手机正在测试 $done / $total 项…"
                        done > 0 -> "手机已测 $done / $total 项，通过 $success 项；不代表电视结果"
                        else -> description
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onTest, enabled = !testing) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("测速中")
                } else {
                    Text("一键测速")
                }
            }
        }
    }
}

@Composable
fun ControlValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun DefaultSummary(lines: List<String>) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("原设置（首次读取时记录）", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                Marker("（原设置）", emphasized = true)
            }
            Spacer(Modifier.height(4.dp))
            lines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun PresetRow(
    selected: Boolean,
    title: String,
    supporting: String?,
    isDefault: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    speedTest: SpeedTestResult? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isDefault) {
                    Spacer(Modifier.width(6.dp))
                    Marker("（原设置）", emphasized = true)
                }
                if (isCurrent) {
                    Spacer(Modifier.width(6.dp))
                    Marker("（当前）", emphasized = false)
                }
            }
            if (!supporting.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    supporting,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            speedTest?.let { result ->
                Spacer(Modifier.height(3.dp))
                Text(
                    speedTestText(result),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (result.success) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun Marker(text: String, emphasized: Boolean) {
    Surface(
        color = if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (emphasized) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

fun speedTestText(result: SpeedTestResult): String {
    result.error?.let { return "检测失败：$it" }
    val latency = result.latencyMs ?: return "检测失败"
    val status = result.statusCode
    return when {
        status == null -> "检测通过 · 响应耗时 $latency 毫秒"
        status == 204 -> "检测通过 · 响应耗时 $latency 毫秒 · 返回码 204"
        else -> "检测异常 · 响应耗时 $latency 毫秒 · 返回码 $status"
    }
}
