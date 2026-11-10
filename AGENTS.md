# AGENTS.md

面向在本仓库中工作的编码代理。修改架构、依赖、构建方式或隐私策略时，同步更新本文件。

## 1. 仓库范围

- `PureTVMobileHelper/` 是当前唯一已实现的模块：Android 手机客户端，通过局域网 ADB 连接
  Android TV，管理 TV 应用，核心功能是发现、安装、冻结/解冻、卸载 APK，以及调整 NTP /
  Captive Portal。
- `PureTVStore/` 目前是空目录/占位目录。不要根据目录名猜测功能，也不要把实现错误地放进该目录。
- 应用 ID：`com.puretv.mobilehelper`，界面语言为简体中文。
- 技术栈：Kotlin + Jetpack Compose Material 3，单 Activity + ViewModel，无 Navigation 库。
- ADB 协议库：[Kadb](https://github.com/flyfishxu/Kadb) 2.1.5。**不要**改为调用外部 `adb`
  二进制，也不要自己重写 ADB/SYNC 协议。

## 2. 固定版本与构建约束

| 项目 | 值 |
| --- | --- |
| Android Gradle Plugin | 9.5.1（内置 Kotlin） |
| Gradle | 9.6.0 |
| Kotlin / Compose 编译器插件 | 2.4.21 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 23 |
| Java 兼容级别 | 17 |
| Kadb | `com.flyfishxu:kadb:2.1.5` |

关键约束：

- Kadb AAR 的 `minCompileSdk=37`，**不要降低 `compileSdk`**。
- AGP 9 已内置 Kotlin。**不要**再应用 `org.jetbrains.kotlin.android` 或 `kotlin-kapt`。
- 只保留 Compose 编译器插件 `org.jetbrains.kotlin.plugin.compose`，版本必须与 Kotlin 对齐。
- Kadb 的配对依赖 `com.github.Flyfish233:spake2-java` 发布在 JitPack。**不要删除**
  `settings.gradle.kts` 中的 `maven("https://jitpack.io")`。
- 构建需要 JDK 17+、Android SDK Platform 37、Build Tools 36+。
- `targetSdk` 保持 36。升级到 37（Android 17）前必须实现 `ACCESS_LOCAL_NETWORK` 运行时权限，
  否则 mDNS 发现、端口扫描和局域网 ADB 连接都会被系统阻断；详见第 8 节。

## 3. 代码结构与数据流

| 文件 | 职责 |
| --- | --- |
| `PureTVHelperApp.kt` | 进程启动时用持久化文件存储配置 `KadbCert` |
| `MainActivity.kt` | `enableEdgeToEdge()`、注入 `AppViewModel.Factory` |
| `data/AdbRepository.kt` | Kadb 封装：连接、配对、设备信息、包列表/详情、安装、冻结/解冻、卸载、启动、强停 |
| `data/FocusedInputText.kt` | 聚焦可编辑节点解析，与当前输入法 hint/包名核验；可疑内容拒绝回填 |
| `data/RemoteTextEdit.kt` | 实时输入变化范围与可打印 ASCII 预检，必须在任何 DEL 之前完成 |
| `data/LanDiscovery.kt` | 用户触发的 mDNS 发现 + 私有 IPv4 `/24` 的 5555 端口扫描 |
| `data/NetworkControl.kt` | NTP / Captive Portal 设置模型、预设列表、匹配规则 |
| `data/NetworkSpeedTest.kt` | 手机侧 SNTP / HTTP 测速实现 |
| `data/DeviceStore.kt` | **所有设备相关数据的唯一存储**：连接信息、自定义名称、首次 NTP/Captive 默认基线（`puretv_devices.xml`，最多 20 条） |
| `data/FilePrivateKeyStore.kt` | ADB 私钥原子写入 `filesDir/adb/adbkey` |
| `data/FriendlyError.kt` | ADB/配对/安装错误到中文提示的映射 |
| `data/Models.kt` | `DeviceInfo`、`InstalledApp`、`AppDetails`、安装进度/选项；网络模型在 `NetworkControl.kt` |
| `ui/AppViewModel.kt` | 连接状态、应用列表、配对、安装、详情、冻结/解冻、发现状态、网络设置、事件 Channel |
| `ui/PureTvApp.kt` | 页面切换、Snackbar、配对/安装/冻结/卸载对话框 |
| `ui/screens/HomeScreen.kt` | 连接表单、自动发现入口、已保存设备、连接帮助 |
| `ui/screens/DiscoveryCard.kt` | 发现进度、结果列表、连接/配对操作 |
| `ui/screens/DeviceControlScreen.kt` | 设备控制面板：功能列表、页面路由（`ControlPanelFeature` 分发） |
| `ui/screens/ControlPanelCommon.kt` | 功能注册表（`ControlPanelFeature`）与共享组件（预设行、默认摘要、测速栏） |
| `ui/screens/NtpControlPage.kt` | 自动校时功能页（NTP）：当前/原设置、可选服务器、测速、应用/恢复 |
| `ui/screens/CaptivePortalControlPage.kt` | 联网检测功能页：当前/原设置、可选检测地址、测速、应用/恢复 |
| `ui/screens/RemoteInputControlPage.kt` | 遥控输入功能页：独立的手动发送/实时输入模式、遥控按键 |
| `ui/screens/RemoteKeypad.kt` | 遥控/数字/字母面板、大小写与空格/删除、系统按键轻震 |
| `ui/screens/DeviceScreen.kt` | 设备信息、控制面板入口、安装卡片、应用列表、冻结状态、详情 BottomSheet |
| `ui/theme/Theme.kt` | 自定义明暗色板，包含 `surfaceContainer*` |

主链路：

1. `KadbCert.configure(FilePrivateKeyStore(...))` 必须在任何 ADB 操作前执行，只在 Application
   中执行一次，不能换成默认的内存存储，否则每次启动都要在 TV 上重新授权。
2. 首页连接或配对 → `AppViewModel` → `AdbRepository`。
3. `AdbRepository` 持有唯一 `Kadb` 实例，所有操作在 `Dispatchers.IO` 上执行，并用 `Mutex`
   串行化。
4. 连接成功 → 读取设备信息 → 自动保存设备（保留已有自定义名称/默认配置）→ 刷新应用列表。
5. 自定义名称由 `SavedDevice.customName` 保存；`displayName = customName.ifBlank { name }`。
   首页列表、已连接卡片、设备页标题、控制面板标题统一使用 `displayName`。
6. 自动发现由首页按钮触发：`LanDiscovery` 同时运行 5 秒 mDNS 发现和私有 IPv4 `/24` 的 5555
   端口探测。结果展示在 `DiscoveryCard`，由用户选择连接或配对；**不允许自动连接**。
   发现项按 IP + 端口匹配 `SavedDevice`，匹配后显示 `${displayName}（已保存）` 并隐藏连接按钮，
   从下方已保存列表连接；配对端点保留配对操作。名称旁的信息图标支持点击/长按 tooltip，明确
   地址匹配未核验设备身份，跨网络同址或 DHCP 重分配可能误认；不得为识别而自动建立 ADB 连接。
7. 应用列表会查询当前 TV 用户的禁用包集合（`pm list packages -d`），并合并带版本号查询与
   完整包列表；原因是部分 Android 版本的 `--show-versioncode` 会隐藏已禁用包。冻结应用
   必须保留在列表中，才能再次解冻。
8. 冻结/解冻：`pm disable-user --user <current> <pkg>` / `pm enable --user <current> <pkg>`。
   系统应用允许冻结，但必须经过两级确认（风险提示 → 最终确认）；第三方应用单次确认。
9. 安装：SAF 返回 `Uri` → 复制到 `cacheDir/apk-install`（带进度）→ ZIP/APK 头校验 →
   `Kadb.install(ProgressSource, size, "-r"/"-d"/"-g")` → 清理临时文件 → 刷新列表。
10. 详情：`dumpsys package <pkg>` 解析有限字段；启动用 `monkey`，强停用 `am force-stop`，
   卸载用 `Kadb.uninstall()`。
11. 设备控制面板是**功能列表**：`ControlPanelFeature` 注册表（`ControlPanelCommon.kt`）定义标题/
    描述/图标，`DeviceControlScreen` 渲染列表并路由到各功能页（`NtpControlPage`、
    `CaptivePortalControlPage`）。读取 `ntp_server` 与 `captive_portal_*` 全局设置；首次打开时把
    当前值写进该设备在 `DeviceStore` 中的 `networkDefaults` 字段，列表用“（原设置）”标注基线。
    应用预设只写对应 settings key，不修改 `captive_portal_mode`；恢复原设置按基线 put/delete 对应 key。
12. 测速是**功能页职责**：控制面板列表不放测速入口，只负责展示与导航。每个功能页顶部“一键测速”
    只测本页预设（`testNtpSpeedTests` / `testCaptivePortalSpeedTests`），用户点击触发（不自动、
    不后台），信号量限 4 并发；NTP 走 SNTP UDP/123，Captive Portal 走 HTTPS GET 期望 204。
    结果内联显示在各预设行，只保存在 `NetworkControlUiState.speedTests` 内存中。
13. 遥控输入默认是**手动发送**：编辑不发出命令；每次发送把完整草稿追加到 TV 光标处；读取仅
    更新手动草稿，不修改 TV 或实时输入基线。**实时输入**使用独立文本，进入时从空白开始，
    不读取、不清空 TV 原文；350ms 等待后由单个协程串行执行差异重放（DEL ×N + 新尾部），
    键入不得取消正在执行的删除/插入操作。切换模式保留手动草稿、结束本轮实时输入且不发送。
    `RemoteTextEdit` 在任何删除前核验待插入文本，只接受可打印 ASCII；不支持的字符不发命令。
    其他输入失败后暂停实时输入，需核对 TV 内容并切换模式后重新开始，避免沿用不可靠基线。
    `readFocusedInputText` 用 `uiautomator dump` 聚焦可编辑节点与 `dumpsys input_method` 当前
    编辑器包名/hint 核验；非空文本等于 hint 或元数据不可用时拒绝回填、不推断为空、不写 TV。
    页面用“手动发送”“实时输入”“读取电视文字”等普通用户用语，不暴露基线/hint/ADB 堆栈。
14. 遥控区有“遥控 / 数字 / 字母”面板，统一调用系统 `KeyboardTap` 触感反馈，遵从系统触感设置，
    不新增振动权限。数字/字母与方向键同路，经 `inputKey` 直接发送 `KEYCODE_0..9`、`KEYCODE_A..Z`；
    空格/删除/确定/大小写分别发送 `SPACE` / `DEL` / `ENTER` / `CAPS_LOCK`。不得调用 `inputText`，
    不得改写手机草稿或借用手动/实时文字发送；字母大小写由电视输入法处理，不推测 Caps Lock 状态。
    所有键名必须进入 `INPUT_KEYS` 白名单。直接按键先等待本轮文字发送完成；改变光标/焦点的按键
    会暂停实时输入，音量键不暂停，避免继续用旧位置改字。
    原生按键按点击顺序排队；文字模式切换不能丢弃已接受的按键，仅连接对象变化才作废旧按键。

UI 事件通过 `UiEvent`（`Message` / `Paired`）发给 `PureTvApp` 的 Snackbar。详情 BottomSheet
中的启动/强停/冻结/解冻操作必须先关闭 Sheet，否则 Snackbar 会被模态层遮挡。

## 4. 构建与验证

```bash
cd PureTVMobileHelper
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
```

- Debug 产物：`app/build/outputs/apk/debug/app-debug.apk`
- Release：本机存在 `PureTVMobileHelper/signing/signing.properties` 时输出已签名的
  `app/build/outputs/apk/release/app-release.apk`；缺失时回退为未签名的
  `app-release-unsigned.apk`。`signing/`、keystore 与密码**不要**提交到仓库；签名目录包含
  `puretv-release.jks` 和 `signing.properties`，整体备份即可。
- GitHub Actions：`.github/workflows/release.yml` 在 `release` 分支推送或手动触发时运行单元测试、
  构建签名 APK 并创建预发布 Release。签名由 `RELEASE_KEYSTORE_BASE64` 等 Secrets 注入，**禁止**
  为该工作流添加 `pull_request` 触发；CI 用 `-PappVersionCode=<run_number>` 递增版本号。
  所有 `uses:` 必须固定到完整 commit SHA（保留版本注释），仓库可启用 SHA 固定策略。
  发布任务限制在 `release` 分支并绑定 `release` Environment；签名 Secrets 放 Environment，
  不要放仓库级，同时关闭 fork 工作流选项。
- `FocusedInputTextTest`、`RemoteTextEditTest` 使用 JUnit 4.13.2 做本地 JVM 回归测试，覆盖
  提示语误读、真实正文、元数据不匹配、待插入字符校验与变化范围；没有 instrumentation 测试。
  ADB/UI 行为仍需实际运行或 emulator smoke test。
- Lint 预期剩余警告：`OldTargetApi`、Gradle/AGP 有新版本、Kadb/BouncyCastle 的
  `TrustAllX509TrustManager`。最后一项是 ADB 配对协议要求，见第 6 节，不要“修复”。

推荐 smoke test（不依赖真实 TV）：

1. 启动任意 Android 模拟器，执行 `adb tcpip 5555`。
2. 安装 debug APK，App 内连接模拟器自己的回环 ADB 端点（`127.0.0.1:5555`）。
3. 确认设备信息、应用列表、系统应用开关、详情、启动/强停。
4. 把测试 APK 放进模拟器 `Download`，通过 App 的文件选择器安装；
   用 `adb shell dumpsys package <pkg> | grep lastUpdateTime` 验证。
5. 测试卸载、错误端口、连接失败提示、明暗主题。
6. 在局域网另一台设备监听 `5555`，点击“查找同一网络的电视”，确认结果出现；只在用户点击后连接。
7. 打开“设备控制面板”：确认首次读取值进入 Preferences、列表出现“（原设置）”；应用其他
   服务器/检测地址后主机端 `settings get global ...` 变化，恢复原设置后回到基线。
8. 重命名已保存设备：列表/设备页/控制面板标题同步更新；重启或重连后保留；留空恢复自动名称。
9. 打开“设备控制面板”：确认功能列表只显示功能入口（NTP / Captive Portal，无测速栏）；进入各
   功能页点击“一键测速”，确认只测本页预设、各行显示延迟/204/失败，且结果在进程重启后不保留。
10. 遥控输入页：手动模式编辑不发送，发送每次追加草稿、读取只更新手机；提示语不回填。
    实时模式不显示手动按钮，只改本轮输入且保留 TV 原文；切换不发命令、手动草稿独立保留。
    不支持的字符必须在任何删除前被拦截，输入异常不得把 TV 堆栈展示到 UI。
    数字/字母键必须在两种文字模式下都直接发往电视，手机草稿保持不变；辅助键同样发原生事件。
    轻震遵从系统设置；无实体震感时可查系统振动记录，不得将模拟器记录宣称为实机手感验证。
11. 真实 TV 还需验证：首次授权弹窗、安装后启动、卸载、Android 11+ 配对握手、mDNS 发现、
    NTP/Captive Portal 修改是否生效、测速结果与实际链路差异、遥控输入中文/光标场景。

不要把包含真实设备序列号、真实局域网 IP、账号信息的截图、`adb devices` 输出、logcat 提交到仓库。
README 界面预览图必须使用模拟器/占位数据并脱敏后放到 `docs/images/`。

### README 界面截图

- 截图目录：`PureTVMobileHelper/docs/images/`，由 `README.md` 的“界面预览”章节引用。
- 当前覆盖：首页、自动发现、配对、设备概览、应用列表/冻结、应用详情、系统应用冻结两次确认、
  安装进度、控制面板功能列表、NTP 功能页、Captive Portal 功能页（各页一键测速）、遥控输入页、重命名。
- 截图统一为 480px 宽的 PNG；使用模拟器、占位名称和保留地址（如 `10.0.2.x`），隐藏设备序列号。
- **仅完整功能或独立功能页新增截图**；小增强、提示或交互细节不单独加图，避免影响阅读。
  改动影响已有截图的内容时，只更新对应旧图；未影响截图内容的小改动仅更新说明，不重拍无关页面。
- 不要放进被 `.gitignore` 忽略的 `screenshots/`/`captures/` 目录。

## 5. 必须保持的不变量

- **全部用户可见文案用普通用户语言**：页面、列表介绍、表单、弹窗、按钮、状态、错误、tooltip
  和使用说明均先讲“做什么 / 会发生什么 / 下一步怎么操作”，避免基线、端点、mDNS、TLS 握手、
  进程、链路等内部术语。一般称“电视”，而非 TV。确需对照系统菜单时保留原名并作简短解释。
- 功能介绍必须符合当前全部功能与实际行为，尤其列表顶部不能只描述其中一类功能；实现细节移到
  开发者文档，不堆在普通说明中。保留安全风险与失败事实，不为通俗化删掉确认步骤或模糊后果。
- 地址、包名、文件格式、版本、返回码等必要诊断值可以保留，但标签先解释含义；不要原样展示
  底层异常、堆栈或内部英文错误。错误要给出可执行的下一步，未知原因不得编造具体原因。
- 所有 tooltip 统一使用常驻交互（`rememberTooltipState(isPersistent = true)`），不得定时自动消失。
  再次点击触发图标、提示内容或页面其他位置时关闭；保留长按打开与系统返回关闭。
- 所有 Kadb/阻塞网络操作必须在 `Dispatchers.IO`；共享连接必须经过 `AdbRepository` 的 `Mutex`。
- 只维护一个 `Kadb` 连接；退出/`onCleared` 时调用 `closeNow()`。不要在 ViewModel 之外持有连接。
- 不记录、不导出、不上传 ADB 私钥、APK 内容、TV 文件路径或完整 `dumpsys` 输出。
- 包名进入 shell 前必须通过 `PACKAGE_NAME` 正则校验，防止命令注入。
- 冻结状态必须来自 TV 当前用户的 `pm list packages -d`；不要只依赖 `dumpsys` 的全局
  `enabled` 字段。已冻结应用必须继续出现在列表中（合并带版本号/完整列表查询）。
- 系统应用允许冻结，但必须保留两级确认：第一级说明 TV 桌面/设置/SystemUI 可能异常，
  第二级要求再次确认；第三方应用单次确认。不要把系统应用冻结改回无确认。
- 冻结用 `pm disable-user`，解冻用 `pm enable`；保留数据，不要用 `pm uninstall`/`pm clear`。
- 冻结确认对话框必须说明“应用会从 TV 桌面消失、进程会停止、数据保留”。解冻可直接执行。
- 冻结/解冻后刷新应用列表；操作失败时用 Snackbar 显示 TV 返回的错误。
- 自动发现必须由用户点击触发，**不允许自动连接或后台定期扫描**。mDNS 服务类型固定为
  `_adb._tcp`、`_adb-tls-connect._tcp`、`_adb-tls-pairing._tcp`。
- 发现列表的“已保存”是 IP + 端口地址匹配，不是身份认证；必须保留误认风险 tooltip。
  显示名称复用 `SavedDevice.displayName`，不另存发现结果或名称副本。
- 端口扫描只允许本机 Wi-Fi/以太网所在的私有 IPv4（`isSiteLocalAddress`）、`/24` 上限、
  端口 `5555`；必须过滤本机 IP 和回环地址。不要在公网/蜂窝网段扫描。
- 发现结果只保存在当前界面的内存中；不要持久化、上传或用于遥测。连接/配对必须由用户确认。
- mDNS 解析使用串行队列（`resolveService` 不能并发），停止扫描时关闭队列并忽略迟到回调。
- 所有设备级数据（连接信息、NTP/Captive 默认基线，以及未来新增的设备偏好/能力）必须存进
  `DeviceStore` 的 `SavedDevice` 记录（`puretv_devices.xml`），序列化逻辑也在 `DeviceStore`。
  **不要**为设备数据新建第二个 SharedPreferences 文件/Store。
- 自定义名称必须存入 `SavedDevice.customName`，与设备记录一起序列化；不要在别处再存一份。
- 自定义名称 trim 后最多 40 字符；留空表示使用自动识别的 `name`。重连/重启必须保留名称。
- 所有设备标题使用 `SavedDevice.displayName`（customName 优先），不要在 UI 里直接拼接。
- 删除已保存设备必须连带删除其 `networkDefaults`；`DeviceStore.remove()` 负责级联。
- ADB 私钥是安全凭据，单独保存在 `filesDir/adb/adbkey`，不放进设备 JSON。
- 网络设置只允许用户点击“应用”后写入；不要自动修改 TV 的 NTP / Captive Portal。
- 新增控制面板功能必须：在 `ControlPanelFeature` 注册表加条目、新建独立功能页文件、在
  `DeviceControlScreen` 的 `when (openFeature)` 路由里加分支。不要把所有功能堆在列表页或一个
  巨型文件里；列表页只负责展示与导航。
- 测速属于功能页职责：列表页不放测速/跨功能入口。各功能页的“一键测速”是唯一入口；不要自动
  测速、后台定时测速或把结果写入设备记录。
- 遥控输入：`input keyevent` 只允许 `INPUT_KEYS` 白名单；`input text` 必须 shellQuote 且空格
  编码 `%s`。手动发送与实时输入完全独立，读取不得触发发送或设置实时基线。实时输入只重放
  本轮变化尾部，不得整框清空重输；字符预检必须先于删除，失败时不展示原始堆栈。
- 测速在手机侧执行，UI 必须明确标注“不代表 TV 的代理或实际链路”。NTP 使用 SNTP UDP/123，
  Captive Portal 使用 HTTPS GET 并以 204 为成功；超时/非 204 要显示为失败或状态码。
- 测速结果只保存在 `NetworkControlUiState.speedTests` 内存中；离开进程即丢失，不上传。
- 所有写入 `settings put` 的值必须经 `shellQuote`；不要拼接未转义的用户/设备字符串。
- 网络设置默认基线按设备保存，且**只在首次读取时写入**（`saveNetworkDefaultsIfAbsent`），之后不得被预设
  应用覆盖。列表用“（原设置）”标注基线，“（当前）”标注 TV 当前值。
- 不要修改 `captive_portal_mode`；需要关闭/开启检测时必须由用户明确操作并单独确认。
- 恢复原设置要支持删除 key：基线为空时用 `settings delete`，不要写入字符串 "null" 或空值。
- 控制面板只读写 `ntp_server`、`captive_portal_http_url`、`captive_portal_https_url`、
  `captive_portal_fallback_url`、`captive_portal_other_fallback_urls`、`captive_portal_server`。
- 安装只通过 SAF 读取；**不要**申请 `READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE`、
  `MANAGE_EXTERNAL_STORAGE` 等存储权限。
- 安装必须显示复制/上传进度，处理取消，失败时清理 cache 临时文件。
- 保持中文 UI；不要改成英文或引入多语言框架（除非用户明确要求）。
- 错误提示集中在 `FriendlyError.kt`，不要在 UI 里散落 `e.message`。
- `appsRequestId` / `detailsRequestId` 用于防止旧协程覆盖新状态；重构时保留等价保护。
- 保留 `allowBackup="false"`、`dataExtractionRules` 排除规则和最小权限（目前为
  `INTERNET` + `ACCESS_NETWORK_STATE`）。不要为局域网发现新增位置、Wi-Fi 扫描等权限。
- 不要新增遥测、崩溃上报、广告 SDK 或除“连接用户填写的 TV、用户主动触发局域网发现、用户点击
  测速产生的 NTP/HTTPS 请求”之外的网络请求。发现与测速结果只保存在内存，不上传。
- 不要加 `QUERY_ALL_PACKAGES`；包列表通过 TV 端 `pm list packages` 获取。

## 6. ADB / 配对行为说明

- 自动发现：`NsdManager` 发现 `_adb._tcp`、`_adb-tls-connect._tcp`、
  `_adb-tls-pairing._tcp`；同时探测本机私有 IPv4 `/24` 的 5555 端口。mDNS 通常是 Android 11+
  无线调试的唯一可用发现方式（端口随机）；传统 TV/盒子通常靠 5555 扫描。
- 经典连接：TV 开启网络调试，端口通常 `5555`；首次连接会弹授权，App 保存的主机密钥让后续
  连接免重复授权。
- 冻结/解冻：`pm disable-user --user <current> <pkg>` / `pm enable --user <current> <pkg>`。
  当前用户通过 `am get-current-user` 获取（失败回退 0）。`pm disable-user` 返回
  `enabled=3`（disabled-user），解冻后恢复为 `enabled=1`。部分 OEM 可能限制该命令。
- 网络设置：NTP 是 `settings get/put global ntp_server`。Captive Portal 使用
  `captive_portal_http_url`、`captive_portal_https_url`、`captive_portal_fallback_url`、
  `captive_portal_other_fallback_urls`，并兼容旧版 `captive_portal_server`。读取为空显示
  “未设置”；预设应用同时写 HTTP/HTTPS/回退/其他回退/服务器。`captive_portal_mode` 只读显示。
- 测速：NTP 发送 48 字节 SNTP client 包到 UDP/123，3 秒超时，校验响应 mode=4；Captive Portal
  对预设 HTTPS URL 发 GET，3 秒连接/读取超时，204 视为成功，其他状态码显示为异常。
- Android 11+ 无线调试：`Kadb.pair(host, pairingPort, code, name)` 使用 SPAKE2 + TLS 1.3；
  配对端口与连接端口不同。App 在配对成功后自动连接用户填写的 ADB 主端口。
- 配对需要手机 Android 9+（`Build.VERSION_CODES.P` 门禁）。更低系统提示改用 5555 直连。
- Kadb/配对路径使用自签名临时 TLS 证书 + 空 `X509TrustManager`，其真实性由 SPAKE2 配对码
  保证。**不要**改成 CA 校验或证书固定，否则会破坏 ADB pairing。
- 连接失败常见原因：TV 未开网络调试、IP/端口错误、不同局域网、未在 TV 上允许授权。
  错误映射在 `FriendlyError.kt`。
- 没有自动重连、前台服务。安装/冻结时需要保持 App 在前台。

## 7. 已知限制（不要假装已支持）

- 只安装单个基础 APK；`XAPK` / `APKS` / split APK 不支持。
- 应用列表显示包名，不显示 APK 内本地化名称（TV 端批量取 label 成本高）。
- 系统应用可冻结，但需要两次确认；冻结 SystemUI/桌面/设置等关键组件可能使 TV 界面异常，
  恢复依赖 TV 仍能建立 ADB 连接。
- 冻结使用 `pm disable-user`，不是 `pm suspend`；部分 OEM 会拒绝或限制该命令。
- 自动发现只扫描本机 Wi-Fi/以太网所在的私有 IPv4 `/24` 的 `5555` 端口；VLAN、访客 Wi-Fi、
  AP 隔离、不同子网、非 5555 端口可能发现不到。mDNS 依赖电视/路由器支持。
- 控制面板只覆盖 Android 标准 global settings；OEM 私有实现可能不生效。不修改
  `captive_portal_mode`。修改 NTP/Captive Portal 后通常需要重启 TV 或重新连接网络。
- “默认”是首次打开控制面板时读取的当前值，不是恢复出厂设置；嵌套在该设备记录中，
  保存在 `puretv_devices.xml`。
- 测速在手机侧执行，不代表 TV 的代理或实际网络链路；NTP UDP/123 可能被服务器/网络屏蔽，
  Captive Portal 以 HTTPS 204 为成功，非 204 会显示状态码。测速结果不持久化。
- 自定义名称最长 40 字符，仅保存在本机设备记录；留空恢复自动识别型号。
- 没有自动重连、前台服务。安装/冻结时需要保持 App 在前台。

## 8. 隐私与云端推送规则

本仓库将 push 到云端。提交前必须确认：

- **绝不提交**：`local.properties`、`.gradle/`、`build/`、`*.apk`、`*.aab`、`*.jks`、
  `*.keystore`、`*.p12`、`*.log`、`.env`、截图/录屏、真实密钥、ADB 私钥。
- 根级 `.gitignore` 已覆盖以上内容；模块内还有一份。不要用 `git add -f` 绕过。
- 文档、测试、示例中不要写真实家庭/公司 IP、MAC、TV 序列号、路由器信息、账号邮箱、
  Wi-Fi 名称、运营商标识或真实设备型号。统一使用占位值，例如 `192.168.1.100`、
  `com.example.app`。
- 不要粘贴完整 `adb devices`、`dumpsys`、logcat、安装日志或带主机名的构建输出。
- 自动发现结果包含真实局域网 IP/设备名；不要持久化、上传或写日志。测试截图、录屏、issue
  中必须遮蔽 IP、设备名和 MAC。扫描必须由用户触发，且只允许私有 IPv4。
- 设备连接信息、自定义名称与 NTP / Captive Portal 默认基线统一保存在
  `shared_prefs/puretv_devices.xml`（含序列号/IP 派生的 record 字段）；自定义名称也是用户数据，
  不得导出或上传，`allowBackup=false` 已排除备份。
- ADB 私钥运行时位于应用私有目录 `filesDir/adb/adbkey`，**永远不要**拷贝进仓库或附在 issue。
- Release 签名配置、keystore、密码只保存在本机/CI secret，不进入版本库。
- 如果发现密钥或个人信息已进入提交历史：立即轮换/吊销，清理历史后再推送；不要只删当前文件。
- 推送前建议只读检查：

```bash
git status --short
git ls-files | grep -E '(^|/)(build|\.gradle)/|local\.properties|\.(jks|keystore|apk|aab|log)$'
grep -rnE '/Users/|/home/|BEGIN .*PRIVATE KEY|api[_-]?key|secret|token' \
  --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle \
  --exclude=AGENTS.md --exclude=README.md .
```

`192.168.1.100` 在 UI 中是刻意保留的占位示例，不是真实地址。

## 9. 修改检查清单

1. 先读 `PureTVMobileHelper/README.md` 和本文件，再读相关源码。
2. 多文件改动先列出方案；保持现有模式，不引入第二套架构。
3. 改 ADB 行为时同时检查 `AdbRepository`、`AppViewModel`、`FriendlyError` 和相关 UI 状态；
   冻结/解冻还要验证列表仍能看到已冻结应用。
4. 完成后至少运行 `assembleDebug`；涉及依赖/资源/R8 时再跑 `assembleRelease` 和 `lintDebug`。
5. 行为变化必须做实际运行验证；ADB 链路优先用模拟器自连接 smoke test。
6. 仅完整功能新增截图；小增强不加图。影响旧图内容时只更新对应旧图与 README，使用脱敏模拟器截图。
7. 更新 README/AGENTS（命令、依赖、权限、限制）后再交付。
8. 交付前执行第 8 节的隐私检查。
