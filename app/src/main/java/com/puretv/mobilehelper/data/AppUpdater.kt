package com.puretv.mobilehelper.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

data class AppUpdate(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
)

/**
 * Reads the latest public GitHub release and downloads its APK.
 * Only the update flow uses this client; ADB traffic never shares it.
 */
class AppUpdater(private val context: Context) {

    fun currentVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    fun apkFile(version: String): File =
        File(File(context.cacheDir, "updates"), "PureTV-$version.apk")

    fun currentVersionCode(): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrDefault(0L)

    /** Returns an update only when the release is newer than the installed version. */
    suspend fun findUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        val api = try {
            Result.success(fetchApiRelease())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
        val update = api.getOrNull() ?: try {
            fetchAtomRelease()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (update == null && api.isFailure) throw api.exceptionOrNull()!!
        update?.takeIf { isNewerVersion(it.versionName, currentVersionName()) }
    }

    private fun fetchApiRelease(): AppUpdate? {
        val response = request(LATEST_RELEASE_URL)
        if (response.code == HttpURLConnection.HTTP_NOT_FOUND) return null
        if (response.code != HttpURLConnection.HTTP_OK) {
            throw IOException("更新服务暂时不可用（${response.code}）")
        }
        return parseRelease(response.body)
    }

    private fun fetchAtomRelease(): AppUpdate? {
        val response = request(ATOM_URL, "application/atom+xml")
        if (response.code != HttpURLConnection.HTTP_OK) return null
        return parseAtomRelease(response.body)
    }

    suspend fun download(
        update: AppUpdate,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val target = apkFile(update.versionName)
        val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("下载失败（${connection.responseCode}）")
            }
            val declaredSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connection.contentLengthLong
            } else {
                connection.contentLength.toLong()
            }
            val total = declaredSize.takeIf { it > 0 } ?: update.apkSize
            val progressStep = (total / 200).coerceAtLeast(64 * 1024)
            val lastReported = AtomicLong(0)
            var downloaded = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReported.get() >= progressStep) {
                            lastReported.set(downloaded)
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            onProgress(downloaded, total)
            target
        } finally {
            connection.disconnect()
        }
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun parseRelease(json: String): AppUpdate? {
        val root = JSONObject(json)
        if (root.optBoolean("draft")) return null
        val tag = root.optString("tag_name")
        val version = parseAppVersion(tag)?.name ?: return null
        val assets = root.optJSONArray("assets") ?: return null
        var chosen: JSONObject? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            if (name.startsWith("PureTV-", ignoreCase = true)) {
                chosen = asset
                break
            }
            if (chosen == null) chosen = asset
        }
        val asset = chosen ?: return null
        return AppUpdate(
            versionName = version,
            notes = plainReleaseNotes(root.optString("body")),
            apkUrl = asset.optString("browser_download_url"),
            apkSize = asset.optLong("size"),
        )
    }

    private class Response(val code: Int, val body: String)

    private fun request(url: String, accept: String = "application/vnd.github+json"): Response {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", accept)
        }
        return try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            Response(code, body)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val USER_AGENT = "PureTV-MobileHelper"
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/$UPDATER_REPO/releases/latest"
        const val ATOM_URL = "https://github.com/$UPDATER_REPO/releases.atom"
    }
}

private const val UPDATER_REPO = "JamesWDGu/PureTVMobileHelper"

/** Parses the first entry of GitHub's releases Atom feed; no API quota is used. */
internal fun parseAtomRelease(xml: String): AppUpdate? {
    val entry = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
        .find(xml)?.groupValues?.get(1) ?: return null
    val tag = Regex("/releases/tag/([^\"]+)").find(entry)?.groupValues?.get(1) ?: return null
    val version = parseAppVersion(tag)?.name ?: return null
    val encodedNotes = Regex("<content[^>]*>(.*?)</content>", RegexOption.DOT_MATCHES_ALL)
        .find(entry)?.groupValues?.get(1).orEmpty()
    return AppUpdate(
        versionName = version,
        notes = plainReleaseNotes(htmlToText(encodedNotes)),
        apkUrl = "https://github.com/$UPDATER_REPO/releases/download/$tag/PureTV-$version.apk",
        apkSize = 0L,
    )
}

private fun htmlToText(html: String): String = html
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace(Regex("(?i)<br\\s*/?>"), "\n")
    .replace(Regex("(?i)</(p|h[1-6]|li|ul|ol)>"), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace(Regex("[ \\t]+"), " ")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()
