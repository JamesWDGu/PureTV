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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.mobilehelper.ui.RemoteInputMode
import com.puretv.mobilehelper.ui.RemoteInputStatus
import com.puretv.mobilehelper.ui.RemoteInputUiState
import com.puretv.mobilehelper.ui.SectionTitle

@Composable
fun RemoteInputControlPage(
    remoteInput: RemoteInputUiState,
    onTextChange: (String) -> Unit,
    onModeChange: (RemoteInputMode) -> Unit,
    onSendNow: () -> Unit,
    onPull: () -> Unit,
    onSendKey: (String) -> Unit,
) {
    val manual = remoteInput.mode == RemoteInputMode.Manual
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    SectionTitle("文字输入")
                    Spacer(Modifier.height(8.dp))
                    InputModeSelector(remoteInput, onModeChange)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (manual) "写好后点击发送；读取只会把电视文字复制到手机，不会自动发送。"
                        else "边输入边传到电视，只修改本轮输入的文字。每次进入这个模式都会从空白开始。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = remoteInput.text,
                        onValueChange = onTextChange,
                        label = { Text(if (manual) "准备发送的文字" else "本次实时输入") },
                        placeholder = { Text(if (manual) "在这里写好要发送的文字" else "在这里输入，电视会跟着变化") },
                        enabled = if (manual) !remoteInput.busy else !remoteInput.liveBlocked,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    RemoteInputStatusLabel(remoteInput)
                    if (manual) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onSendNow,
                                enabled = remoteInput.text.isNotEmpty() && !remoteInput.busy,
                                modifier = Modifier.weight(1f),
                            ) { Text("发送文字") }
                            OutlinedButton(
                                onClick = onPull,
                                enabled = !remoteInput.busy,
                                modifier = Modifier.weight(1f),
                            ) { Text("读取电视文字") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            manual -> "发送会在电视光标处追加文字，不会清空或替换电视原有内容。"
                            remoteInput.liveBlocked -> "实时输入已暂停。请先切回手动发送，核对电视内容，再重新进入实时输入。"
                            else -> "电视光标请放在文字末尾，期间不要用遥控器改字或移动光标。切换模式会结束本轮输入，已发送的文字会保留。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "请先在电视上选中输入框。暂仅支持发送英文字母、数字和英文符号，中文和表情请用电视输入法。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    SectionTitle("遥控与键盘")
                    Spacer(Modifier.height(12.dp))
                    RemoteKeypad(onSendKey)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputModeSelector(state: RemoteInputUiState, onChange: (RemoteInputMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        RemoteInputMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = state.mode == mode,
                onClick = { onChange(mode) },
                enabled = !state.busy,
                shape = SegmentedButtonDefaults.itemShape(index, RemoteInputMode.entries.size),
                label = { Text(if (mode == RemoteInputMode.Manual) "手动发送" else "实时输入") },
            )
        }
    }
}

@Composable
private fun RemoteInputStatusLabel(state: RemoteInputUiState) {
    Text(
        text = state.error ?: if (state.liveBlocked) "实时输入已暂停" else when (state.status) {
            RemoteInputStatus.Sending -> "正在发送…"
            RemoteInputStatus.Reading -> "正在读取电视文字…"
            RemoteInputStatus.Sent -> if (state.mode == RemoteInputMode.Manual) "文字已发送；再次点击会再追加一次"
                else "本次改动已传到电视"
            RemoteInputStatus.Read -> "已读取到手机，尚未发送"
            RemoteInputStatus.Idle -> if (state.mode == RemoteInputMode.Manual) "手机编辑不会自动发送"
                else "输入后会自动发送"
        },
        style = MaterialTheme.typography.labelMedium,
        color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

