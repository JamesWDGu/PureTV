package com.puretv.mobilehelper.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.mobilehelper.data.AppDetails
import com.puretv.mobilehelper.data.InstallOptions
import com.puretv.mobilehelper.data.InstalledApp
import com.puretv.mobilehelper.data.SavedDevice
import com.puretv.mobilehelper.ui.AppAvatar
import com.puretv.mobilehelper.ui.ErrorPanel
import com.puretv.mobilehelper.ui.InfoRow
import com.puretv.mobilehelper.ui.SectionTitle
import com.puretv.mobilehelper.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onSetIncludeSystem: (Boolean) -> Unit,
    onSetQuery: (String) -> Unit,
    onSetInstallOptions: (InstallOptions) -> Unit,
    onInstall: (List<Uri>) -> Unit,
    onOpenApp: (InstalledApp) -> Unit,
    onLaunchApp: (String) -> Unit,
    onForceStopApp: (String) -> Unit,
    onUninstallApp: (InstalledApp) -> Unit,
    onFreezeApp: (InstalledApp) -> Unit,
    onUnfreezeApp: (InstalledApp) -> Unit,
    onOpenControls: () -> Unit,
    onRenameDevice: (SavedDevice) -> Unit,
    onCloseAppDetails: () -> Unit,
) {
    val device = state.connectedDevice
    val pickApk = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) onInstall(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Column {
                        Text(
                            state.connectedDisplayName.ifBlank { "未连接" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (device != null) {
                            Text(
                                device.address,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.appsLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    DisconnectMenu(
                        onDisconnect = onDisconnect,
                        onRename = state.connectedSavedDevice?.let { saved ->
                            { onRenameDevice(saved) }
                        },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (device == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("尚未连接电视")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DeviceInfoCard(state) }
            item { ControlPanelCard(onOpenControls) }
            item { InstallCard(state, onSetInstallOptions) { pickApk.launch(arrayOf("*/*")) } }
            item {
                SectionTitle(
                    "应用管理",
                    trailing = {
                        Text(
                            if (state.query.isBlank()) {
                                "${state.apps.size} 个应用"
                            } else {
                                "找到 ${state.visibleApps.size} 个"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSetQuery,
                    label = { Text("搜索应用标识（包名）") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onSetQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "清空")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("显示系统应用", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "系统应用不能在这里卸载；冻结会隐藏并停止使用应用，冻结系统应用需要确认两次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.includeSystem,
                        onCheckedChange = onSetIncludeSystem,
                        enabled = !state.appsLoading,
                    )
                }
            }
            if (state.appsLoading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                    )
                }
            }
            state.appsError?.let { error ->
                item {
                    Column {
                        ErrorPanel("应用列表加载失败", error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onRefresh) { Text("重试") }
                    }
                }
            }
            if (!state.appsLoading && state.visibleApps.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (state.query.isBlank()) {
                                "没有匹配的应用"
                            } else {
                                "没有找到应用标识（包名）包含“${state.query}”的应用"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            items(state.visibleApps, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    onOpen = { onOpenApp(app) },
                    onLaunch = { onLaunchApp(app.packageName) },
                    onForceStop = { onForceStopApp(app.packageName) },
                    onFreeze = { onFreezeApp(app) },
                    onUnfreeze = { onUnfreezeApp(app) },
                    onUninstall = { onUninstallApp(app) },
                )
            }
        }
    }

    if (state.detailsLoadingFor != null || state.appDetails != null || state.detailsError != null) {
        AppDetailsSheet(
            packageName = state.detailsPackage
                ?: state.detailsLoadingFor
                ?: state.appDetails?.packageName.orEmpty(),
            details = state.appDetails,
            error = state.detailsError,
            listedSystemApp = state.apps
                .firstOrNull { it.packageName == state.detailsPackage }
                ?.isSystem,
            listedFrozen = state.apps
                .firstOrNull { it.packageName == state.detailsPackage }
                ?.isEnabled == false,
            onDismiss = onCloseAppDetails,
            onLaunch = { packageName ->
                onCloseAppDetails()
                onLaunchApp(packageName)
            },
            onForceStop = { packageName ->
                onCloseAppDetails()
                onForceStopApp(packageName)
            },
            onUninstall = onUninstallApp,
            onFreeze = { app ->
                onCloseAppDetails()
                onFreezeApp(app)
            },
            onUnfreeze = { app ->
                onCloseAppDetails()
                onUnfreezeApp(app)
            },
        )
    }
}

@Composable
private fun DisconnectMenu(
    onDisconnect: () -> Unit,
    onRename: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "更多")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onRename != null) {
                DropdownMenuItem(
                    text = { Text("重命名设备") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onRename()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("断开连接") },
                onClick = {
                    expanded = false
                    onDisconnect()
                },
            )
        }
    }
}

@Composable
private fun ControlPanelCard(onOpen: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("设备控制面板", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "用手机遥控电视、输入文字，也可调整自动校时和联网检测设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpen) {
                Text("打开控制面板")
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(state: UiState) {
    val device = state.connectedDevice ?: return
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("设备信息", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            state.connectedSavedDevice?.customName
                ?.takeIf { it.isNotBlank() }
                ?.let { InfoRow("自定义名称", it) }
            InfoRow("型号", device.displayName)
            InfoRow("系统版本", "Android ${device.androidVersion}（SDK ${device.sdkInt}）")
            InfoRow("处理器类型", device.abi)
            InfoRow("序列号", device.serial)
            InfoRow("连接地址", device.address)
        }
    }
}

@Composable
private fun InstallCard(
    state: UiState,
    onSetInstallOptions: (InstallOptions) -> Unit,
    onPickApk: () -> Unit,
) {
    val options = state.installOptions
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("安装应用（APK）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "从手机选择 APK 安装包，传到电视并安装。可多选，将逐个安装。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = options.replaceExisting,
                    onClick = {
                        onSetInstallOptions(options.copy(replaceExisting = !options.replaceExisting))
                    },
                    label = { Text("覆盖已有应用") },
                )
                FilterChip(
                    selected = options.allowDowngrade,
                    onClick = {
                        onSetInstallOptions(options.copy(allowDowngrade = !options.allowDowngrade))
                    },
                    label = { Text("允许安装旧版本") },
                )
                FilterChip(
                    selected = options.grantPermissions,
                    onClick = {
                        onSetInstallOptions(options.copy(grantPermissions = !options.grantPermissions))
                    },
                    label = { Text("安装时允许所需权限") },
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onPickApk,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("选择 APK 并安装")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "请选择单个、完整的 APK 安装文件；XAPK / APKS 等多文件安装包暂不支持。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    onOpen: () -> Unit,
    onLaunch: () -> Unit,
    onForceStop: () -> Unit,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onUninstall: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = if (app.isEnabled) 0.45f else 0.25f
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppAvatar(app.packageName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.versionCode?.let { "版本号 $it" } ?: "版本号未知",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!app.isEnabled) {
                        Spacer(Modifier.width(8.dp))
                        AppStateBadge(
                            text = "已冻结",
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    if (app.isSystem) {
                        Spacer(Modifier.width(8.dp))
                        AppStateBadge(
                            text = "系统",
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "应用操作")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("应用详情") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onOpen()
                        },
                    )
                    if (app.isEnabled) {
                        DropdownMenuItem(
                            text = { Text("启动") },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onLaunch()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("强制停止") },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onForceStop()
                            },
                        )
                        if (!app.isSystem) {
                            DropdownMenuItem(
                                text = { Text("冻结") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onFreeze()
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("冻结系统应用") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onFreeze()
                                },
                            )
                        }
                    } else {
                        DropdownMenuItem(
                            text = { Text("解冻") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onUnfreeze()
                            },
                        )
                    }
                    if (!app.isSystem) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("卸载") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onUninstall()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppStateBadge(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Surface(
        color = container,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailsSheet(
    packageName: String,
    details: AppDetails?,
    error: String?,
    listedSystemApp: Boolean?,
    listedFrozen: Boolean,
    onDismiss: () -> Unit,
    onLaunch: (String) -> Unit,
    onForceStop: (String) -> Unit,
    onUninstall: (InstalledApp) -> Unit,
    onFreeze: (InstalledApp) -> Unit,
    onUnfreeze: (InstalledApp) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text("应用详情", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                packageName.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(16.dp))

            when {
                details != null -> {
                    val isSystem = details.isSystem || listedSystemApp == true
                    val isFrozen = listedFrozen
                    InfoRow("状态", if (isFrozen) "已冻结（隐藏并停止使用，数据保留）" else "未冻结")
                    InfoRow("版本名称", details.versionName.orEmpty())
                    InfoRow("版本号", details.versionCode?.toString().orEmpty())
                    InfoRow("最低系统要求（API 级别）", details.minSdk?.toString().orEmpty())
                    InfoRow("适配系统版本（API 级别）", details.targetSdk?.toString().orEmpty())
                    InfoRow("首次安装", details.firstInstallTime.orEmpty())
                    InfoRow("最近更新", details.lastUpdateTime.orEmpty())
                    InfoRow("安装来源（应用包名）", details.installerPackageName.orEmpty())
                    InfoRow("安装文件位置", details.codePath.orEmpty())
                    InfoRow("应用数据位置", details.dataDir.orEmpty())
                    InfoRow("系统应用", if (isSystem) "是" else "否")
                    details.flags?.let { InfoRow("安装信息（高级）", it) }
                    Spacer(Modifier.height(16.dp))
                    if (isFrozen) {
                        Button(
                            onClick = {
                                onUnfreeze(
                                    InstalledApp(
                                        packageName = details.packageName,
                                        versionCode = details.versionCode,
                                        isSystem = isSystem,
                                        isEnabled = false,
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("解冻应用")
                        }
                    } else {
                        Button(
                            onClick = { onLaunch(details.packageName) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("启动应用")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onForceStop(details.packageName) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("强制停止")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                onFreeze(
                                    InstalledApp(
                                        packageName = details.packageName,
                                        versionCode = details.versionCode,
                                        isSystem = isSystem,
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isSystem) "冻结系统应用" else "冻结应用")
                        }
                    }
                    if (!isSystem) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                onUninstall(
                                    InstalledApp(
                                        packageName = details.packageName,
                                        versionCode = details.versionCode,
                                        isSystem = false,
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("卸载应用")
                        }
                    }
                }

                error != null -> {
                    ErrorPanel("无法读取应用详情", error)
                }

                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("正在读取应用信息…")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("关闭")
            }
        }
    }
}
