package com.flide.data.update

import android.content.Context
import android.util.Log
import com.flide.env.StorageLayout
import com.flide.github.CloudBuildManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val apkDownloadUrl: String?,
    val isNewer: Boolean
)

class AppUpdateManager(
    private val storageLayout: StorageLayout,
    private val currentVersion: String = "0.1.0"
) {
    private val tag = "AppUpdateManager"

    suspend fun checkForUpdates(owner: String = "mkdevofficial", repo: String = "sora"): Result<AppRelease?> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Flide-Mobile-IDE")
                connectTimeout = 10000
                readTimeout = 10000
            }

            val code = conn.responseCode
            if (code == 404) {
                return@withContext Result.success(null) // No release published yet
            }

            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""

            if (code in 200..299) {
                val json = JSONObject(text)
                val tagName = json.getString("tag_name")
                val cleanTag = tagName.removePrefix("v").trim()
                val isNewer = isVersionNewer(cleanTag, currentVersion)

                // Find .apk asset if available
                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }

                Result.success(
                    AppRelease(
                        tagName = tagName,
                        name = json.optString("name", tagName),
                        body = json.optString("body", "No release notes available."),
                        apkDownloadUrl = apkUrl,
                        isNewer = isNewer
                    )
                )
            } else {
                Result.failure(RuntimeException("Check update failed (HTTP $code)"))
            }
        } catch (e: Exception) {
            Log.e(tag, "checkForUpdates error", e)
            Result.failure(e)
        }
    }

    private fun isVersionNewer(remote: String, local: String): Boolean {
        try {
            val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
            val localParts = local.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
        } catch (e: Exception) {}
        return false
    }
}
