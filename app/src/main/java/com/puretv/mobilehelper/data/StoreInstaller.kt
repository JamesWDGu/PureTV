package com.puretv.mobilehelper.data

import android.content.Context
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

data class StoreRelease(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
)

/**
 * Reads the latest PureTVStore GitHub release and downloads its APK on the phone.
 * The APK is then pushed to the connected TV through [AdbRepository.install].
 */
class StoreInstaller(private val context: Context) {

    suspend fun findLatest(): StoreRelease = withContext(Dispatchers.IO) {
        val apiFailure = try {
            Result.success(fetchApiRelease())
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            Result.failure(error)
        }
        apiFailure.getOrNull()
            ?: fetchAtomRelease()
            ?: throw (apiFailure.exceptionOrNull()
                ?: IOException("商店还没有发布可安装的版本，请稍后再试。"))
    }

    private fun fetchApiRelease(): StoreRelease? {
        val response = request(LATEST_RELEASE_URL)
        if (response.code == HttpURLConnection.HTTP_NOT_FOUND) {
            throw IOException("商店还没有发布可安装的版本，请稍后再试。")
        }
        if (response.code != HttpURLConnection.HTTP_OK) {
            throw IOException("暂时无法获取商店版本（${response.code}），请稍后重试。")
        }
        return parseStoreRelease(response.body)
            ?: throw IOException("商店的最新版本里没有找到安装包，请稍后再试。")
    }

    private fun fetchAtomRelease(): StoreRelease? {
        val response = request(atomUrl(STORE_REPO), "application/atom+xml")
        if (response.code != HttpURLConnection.HTTP_OK) return null
        return storeReleaseFromAtom(response.body)
    }

    suspend fun download(
        release: StoreRelease,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "stores").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val target = File(directory, "PureTVStore-${release.versionName}.apk")
        val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("下载商店安装包失败（${connection.responseCode}）")
            }
            val declaredSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connection.contentLengthLong
            } else {
                connection.contentLength.toLong()
            }
            val total = declaredSize.takeIf { it > 0 } ?: release.apkSize
            val progressStep = (total / 200).coerceAtLeast(64 * 1024)
            val lastReported = AtomicLong(0)
            var downloaded = 0L
            connection.inputStream.use { input ->
                target.outputStream().buffered().use { output ->
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
            onProgress(downloaded, if (total > 0) total else downloaded)
            target
        } finally {
            connection.disconnect()
        }
    }

    fun apkUri(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    private fun request(
        url: String,
        accept: String = "application/vnd.github+json",
    ): Response {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
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

    private data class Response(val code: Int, val body: String)

    private companion object {
        const val USER_AGENT = "PureTV-MobileHelper"
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/$STORE_REPO/releases/latest"
    }
}

private const val STORE_REPO = "JamesWDGu/PureTVStore"

internal fun parseStoreRelease(body: String): StoreRelease? {
    val json = JSONObject(body)
    val versionName = json.optString("tag_name").removePrefix("v").removePrefix("V")
    if (versionName.isBlank()) return null
    val notes = json.optString("body")
    val assets = json.optJSONArray("assets") ?: return null
    var fallback: JSONObject? = null
    var preferred: JSONObject? = null
    for (i in 0 until assets.length()) {
        val asset = assets.optJSONObject(i) ?: continue
        val name = asset.optString("name")
        val url = asset.optString("browser_download_url")
        if (!name.endsWith(".apk", ignoreCase = true) || url.isBlank()) continue
        if (name.startsWith("PureTVStore-", ignoreCase = true)) {
            preferred = asset
            break
        }
        if (fallback == null) fallback = asset
    }
    val chosen = preferred ?: fallback ?: return null
    return StoreRelease(
        versionName = versionName,
        notes = notes,
        apkUrl = chosen.optString("browser_download_url"),
        apkSize = chosen.optLong("size"),
    )
}

/** Builds the fixed-asset release from the Atom feed when the GitHub API is rate limited. */
internal fun storeReleaseFromAtom(xml: String): StoreRelease? {
    val release = parseLatestAtomRelease(xml) ?: return null
    return StoreRelease(
        versionName = release.version,
        notes = release.notes,
        apkUrl = "https://github.com/$STORE_REPO/releases/download/${release.tag}/" +
            "PureTVStore-${release.version}.apk",
        apkSize = 0L,
    )
}
