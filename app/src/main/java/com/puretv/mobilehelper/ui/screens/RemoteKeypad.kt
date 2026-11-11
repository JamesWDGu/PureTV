package com.puretv.mobilehelper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

private enum class KeypadPanel(val title: String) { Remote("遥控"), Numbers("数字"), Letters("字母") }
private val numberRows = listOf("123", "456", "789").map { row ->
    row.map { digit -> digit.toString() to "KEYCODE_$digit" }
}
private val letterRows = listOf("ABCDEF", "GHIJKL", "MNOPQR", "STUVWX", "YZ").map { row ->
    row.map { letter -> letter.toString() to "KEYCODE_$letter" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoteKeypad(onSendKey: (String) -> Unit) {
    var panelName by rememberSaveable { mutableStateOf(KeypadPanel.Remote.name) }
    val panel = KeypadPanel.valueOf(panelName)
    val feedback = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            KeypadPanel.entries.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = panel == item,
                    onClick = {
                        feedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                        panelName = item.name
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, KeypadPanel.entries.size),
                    label = { Text(item.title) },
                )
            }
        }
        if (panel == KeypadPanel.Remote) {
            RemoteControlGrid(onSendKey)
        } else if (panel == KeypadPanel.Numbers) {
            numberRows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (label, key) ->
                        KeypadButton(label, { onSendKey(key) }, Modifier.weight(1f))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeypadButton("删除", { onSendKey("DEL") }, Modifier.weight(1f))
                KeypadButton("0", { onSendKey("KEYCODE_0") }, Modifier.weight(1f))
                KeypadButton("确定", { onSendKey("ENTER") }, Modifier.weight(1f))
            }
        } else {
            letterRows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { (label, key) ->
                        KeypadButton(label, { onSendKey(key) }, Modifier.weight(1f))
                    }
                    repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                KeypadButton("大小写", { onSendKey("CAPS_LOCK") }, Modifier.weight(1f))
                KeypadButton("空格", { onSendKey("SPACE") }, Modifier.weight(2f))
                KeypadButton("删除", { onSendKey("DEL") }, Modifier.weight(1f))
                KeypadButton("确定", { onSendKey("ENTER") }, Modifier.weight(1f))
            }
        }
        Text(
            "这些按键直接操作电视，不会修改上方文字框。正在实时输入时，会先完成文字传送并暂停，再执行按键。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (panel == KeypadPanel.Letters) {
            Text(
                "实际输入的字母和大小写由电视输入法处理；“大小写”键会切换电视键盘的大写锁定。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "按键轻震会遵从手机系统的触感设置。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RemoteControlGrid(onSendKey: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            KeypadButton("上", { onSendKey("DPAD_UP") }, Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadButton("左", { onSendKey("DPAD_LEFT") }, Modifier.weight(1f))
            KeypadButton("确定", { onSendKey("DPAD_CENTER") }, Modifier.weight(1f))
            KeypadButton("右", { onSendKey("DPAD_RIGHT") }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            KeypadButton("下", { onSendKey("DPAD_DOWN") }, Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadButton("返回", { onSendKey("BACK") }, Modifier.weight(1f))
            KeypadButton("主页", { onSendKey("HOME") }, Modifier.weight(1f))
            KeypadButton("菜单", { onSendKey("MENU") }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadButton("音量-", { onSendKey("VOLUME_DOWN") }, Modifier.weight(1f))
            KeypadButton("音量+", { onSendKey("VOLUME_UP") }, Modifier.weight(1f))
            KeypadButton("上一页", { onSendKey("PAGE_UP") }, Modifier.weight(1f))
            KeypadButton("下一页", { onSendKey("PAGE_DOWN") }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadButton("删前字", { onSendKey("DEL") }, Modifier.weight(1f))
            KeypadButton("删后字", { onSendKey("FORWARD_DEL") }, Modifier.weight(1f))
            KeypadButton("到开头", { onSendKey("MOVE_HOME") }, Modifier.weight(1f))
            KeypadButton("到末尾", { onSendKey("MOVE_END") }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = LocalHapticFeedback.current
    OutlinedButton(
        onClick = {
            feedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
            onClick()
        },
        modifier = modifier.height(48.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) { Text(label, maxLines = 1) }
}
