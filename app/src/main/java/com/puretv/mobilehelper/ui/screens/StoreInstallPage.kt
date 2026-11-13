package com.puretv.mobilehelper.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.puretv.mobilehelper.ui.ErrorPanel
import com.puretv.mobilehelper.ui.SectionTitle
import com.puretv.mobilehelper.ui.StoreInstallUiState

/**
 * Installs the latest PureTVStore release on the connected TV. The APK is downloaded on the
 * phone first, then pushed over the existing ADB connection.
 */
@Composable
fun StoreInstallPage(
    uiState: StoreInstallUiState,
    onLoad: (Boolean) -> Unit,
    onInstall: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { onLoad(false) }
    val busy = uiState.downloading || uiState.installing

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
                        "PureTV 商店安装在电视上，打开后可以直接选择并安装常用开源应用，并在有新版本时提示更新。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "请保持电视连接。安装包会先从 GitHub 下载到手机，再由手机发送到电视安装。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    SectionTitle("安装 PureTV 商店")
                    Spacer(Modifier.height(8.dp))

                    when {
                        uiState.loading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("正在获取商店最新版本…")
                            }
                        }

                        uiState.release == null -> {
                            Text(
                                "暂时没有获取到商店版本信息。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            uiState.error?.let {
                                Spacer(Modifier.height(10.dp))
                                ErrorPanel("获取商店版本失败", it)
                            }
                            Spacer(Modifier.height(10.dp))
                            TextButton(onClick = { onLoad(true) }, enabled = !busy) {
                                Text("重新获取")
                            }
                        }

                        else -> {
                            val release = uiState.release
                            ControlValueRow("最新版本", release.versionName)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (uiState.onTv) {
                                    "电视上已安装商店，可以重新安装或更新到最新版本。"
                                } else {
                                    "电视上还没有安装商店。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            if (uiState.downloading) {
                                Spacer(Modifier.height(14.dp))
                                val progress = uiState.progress
                                if (progress != null) {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "正在下载商店安装包 " +
                                        "${Formatter.formatFileSize(context, uiState.downloadedBytes)}" +
                                        " / ${Formatter.formatFileSize(context, uiState.totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (uiState.installing) {
                                Spacer(Modifier.height(14.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("正在安装到电视…")
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = onInstall,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    when {
                                        uiState.installing -> "正在安装…"
                                        uiState.downloading -> "正在下载…"
                                        uiState.onTv -> "重新安装到电视"
                                        else -> "下载并安装到电视"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
