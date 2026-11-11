package com.puretv.mobilehelper.data

import com.flyfishxu.kadb.cert.KadbCertException
import com.flyfishxu.kadb.exception.AdbPairAuthException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class FriendlyError(
    val title: String,
    val hint: String? = null,
)

fun Throwable.toFriendlyError(): FriendlyError {
    val raw = message.orEmpty()
    return when (this) {
        is UnsupportedAdbInputTextException -> FriendlyError(
            "这段文字暂时发不了",
            "请使用英文字母、数字或英文符号；中文和表情请用电视输入法。此次文字没有发送，电视内容未修改。",
        )

        is RemoteInputCommandException -> FriendlyError(
            "电视没有完成输入操作",
            "请先核对电视上的文字和光标位置，再重新输入。",
        )

        is FocusedInputMissingException -> FriendlyError(
            "没有找到电视上的输入框",
            "请先在电视上选中一个可以输入文字的位置，再点击读取。",
        )

        is FocusedInputHintException -> FriendlyError(
            "读到的可能只是电视上的提示语",
            "为避免误读，没有复制到手机；你在手机上写的文字没有改变。",
        )

        is FocusedInputMetadataException -> FriendlyError(
            "暂时无法确认电视输入框里的文字",
            "请先在电视上选中输入框后再试；你在手机上写的文字没有改变。",
        )

        is AdbPairAuthException -> FriendlyError(
            "配对失败",
            "配对码可能错误或已过期。请在电视上重新打开配对页面，填写页面显示的配对端口和 6 位配对码。",
        )

        is UnknownHostException -> FriendlyError(
            "找不到这个设备地址",
            "请核对电视上的 IP 地址，并确认手机与电视连接同一个 Wi-Fi 或路由器。",
        )

        is ConnectException, is NoRouteToHostException -> FriendlyError(
            "无法连接电视",
            "请确认电视已开启网络调试，地址和连接端口填写正确，手机与电视连接同一个路由器。",
        )

        is SocketTimeoutException -> timeoutError()

        is SSLException -> FriendlyError(
            "无法建立安全连接",
            "如果电视开启了“无线调试”，请重新配对。旧版电视可使用电视设置中显示的网络调试地址和端口。",
        )

        is KadbCertException -> FriendlyError(
            "手机的连接授权信息出错",
            "请先重启应用再试。如果仍无法连接，请在电视上重新允许这台手机调试。",
        )

        is SecurityException -> FriendlyError(
            "无法打开安装包",
            "请重新选择手机上的安装包（APK），并在文件选择器中允许本应用读取。",
        )

        is IOException -> {
            val lower = raw.lowercase()
            when {
                "timeout" in lower || "timed out" in lower ||
                    this::class.java.simpleName.contains("Timeout") -> timeoutError()

                "refused" in lower || "no route" in lower || "failed to connect" in lower ->
                    connectionRefusedError()

                "install" in lower || "package" in lower ||
                    "安装失败" in raw || "failed to finalize" in lower ->
                    installError(raw)

                else -> FriendlyError(
                    "与电视的连接中断",
                    "请确认电视仍已开机，并与手机连接同一个路由器，然后重新连接。",
                )
            }
        }

        is IllegalArgumentException, is IllegalStateException -> {
            val lower = raw.lowercase()
            if ("install" in lower || "install_failed" in lower || "安装失败" in raw) {
                installError(raw)
            } else {
                val explanation = plainExplanation(raw)
                FriendlyError(
                    explanation ?: "操作没有完成",
                    if (explanation == null) "请确认电视仍保持连接，核对当前页面后再试。" else null,
                )
            }
        }

        else -> FriendlyError("操作没有完成", "请确认电视仍保持连接，核对当前页面后再试。")
    }
}

fun Throwable.toInstallFailure(): FriendlyError = installError(message.orEmpty())

fun Throwable.toPairingError(): FriendlyError = when (this) {
    is AdbPairAuthException -> toFriendlyError()
    is SSLException -> FriendlyError(
        "手机与电视暂时无法配对",
        "手机或电视可能不支持这种无线调试方式。请查看电视是否另有网络调试开关和连接端口。",
    )
    else -> FriendlyError(
        "配对没有完成",
        "请重新打开电视的配对页面，核对地址、配对端口和 6 位配对码。配对端口与连接端口通常不同。",
    )
}

private fun timeoutError() = FriendlyError(
    "电视暂时没有响应",
    "如果电视弹出“允许调试”，请点击允许；否则请检查电视是否开机、网络调试是否开启，以及地址和连接端口。",
)

private fun connectionRefusedError() = FriendlyError(
    "电视没有接受连接",
    "请开启电视的网络调试，核对地址和连接端口，并确认手机与电视连接同一个路由器。",
)

private fun installError(raw: String): FriendlyError {
    val reason = when {
        "INSTALL_FAILED_ALREADY_EXISTS" in raw ->
            "电视上已有这个应用，请开启“覆盖已有应用”后再安装。"
        "INSTALL_FAILED_VERSION_DOWNGRADE" in raw ->
            "安装包比电视上的版本旧。如需安装，请开启“允许安装旧版本”。"
        "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in raw ->
            "电视无法确认新旧版本来自同一发布者。请换用原来源的安装包；卸载旧版会删除它的数据。"
        "INSTALL_FAILED_INSUFFICIENT_STORAGE" in raw ->
            "电视空间不足，请删除不需要的内容后再安装。"
        "INSTALL_FAILED_INVALID_APK" in raw || "INSTALL_PARSE_FAILED" in raw ->
            "安装包可能损坏，或不是可以直接安装的文件。请重新下载完整的电视版安装包（APK）。"
        "INSTALL_FAILED_NO_MATCHING_ABIS" in raw ->
            "这个安装包不适合这台电视，请下载适配电视的版本。"
        "INSTALL_FAILED_USER_RESTRICTED" in raw ->
            "电视不允许安装，请根据电视提示允许安装来自此来源的应用。"
        "INSTALL_FAILED_VERIFICATION_FAILURE" in raw ->
            "电视没有通过安装安全检查，请确认来源可信，并换用官方电视版安装包。"
        else -> {
            val code = installFailureCode.find(raw)?.value
            "请确认安装包完整且适合这台电视，并查看电视上是否有需要处理的提示。" +
                if (code == null) "" else "（错误代码：$code）"
        }
    }
    return FriendlyError("安装没有完成", reason)
}

/** Preserve short app-authored validation messages, never a backend stack trace. */
private fun plainExplanation(raw: String): String? = raw.takeIf {
    it.length <= 180 && it.any { character -> character in '\u4e00'..'\u9fff' } &&
        it.none { character -> character == '\n' || character == '\r' || character == '\t' } &&
        !technicalError.containsMatchIn(it)
}

internal fun Throwable.toNetworkTestError(): String = when (this) {
    is SocketTimeoutException -> "服务器暂时没有响应"
    is UnknownHostException -> "找不到服务器地址"
    is SSLException -> "无法安全连接到服务器"
    else -> "未能完成测试，请稍后再试"
}

private val installFailureCode = Regex("""\b(?:INSTALL_FAILED|INSTALL_PARSE_FAILED)_[A-Z0-9_]+\b""")
private val technicalError = Regex("""Exception|StackTrace|\bat [\w.]+\(""", RegexOption.IGNORE_CASE)
