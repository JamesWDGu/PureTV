package com.puretv.mobilehelper.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val update = state.available ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本") },
        text = {
            Column(Modifier.heightIn(max = 430.dp)) {
                Text(
                    "当前版本 ${state.currentVersion}，新版本 ${update.versionName}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                if (update.notes.isNotBlank()) {
                    Text("更新内容", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Column(
                        Modifier
                            .heightIn(max = 190.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            update.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.downloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { state.progress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.readyToInstall) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "安装包已下载，点击“安装更新”后按系统提示完成安装。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                state.error?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = if (state.readyToInstall) onInstall else onDownload,
                enabled = !state.downloading,
            ) {
                Text(
                    when {
                        state.readyToInstall -> "安装更新"
                        state.downloading -> "下载中…"
                        else -> "下载并更新"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (state.downloading) "取消下载" else "稍后") }
        },
    )
}
