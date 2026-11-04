package com.mokhtarxz.sora.github

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class GitHubApiClient {

    private val tag = "GitHubApiClient"

    suspend fun requestDeviceCode(clientId: String): Result<DeviceCodeResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://github.com/login/device/code")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connectTimeout = 15000
                readTimeout = 15000
            }

            val body = "client_id=$clientId&scope=repo,workflow"
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

            val responseCode = conn.responseCode
            val responseText = (if (responseCode in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""

            if (responseCode in 200..299) {
                val json = JSONObject(responseText)
                Result.success(
                    DeviceCodeResponse(
                        deviceCode = json.getString("device_code"),
                        userCode = json.getString("user_code"),
                        verificationUri = json.getString("verification_uri"),
                        expiresIn = json.optInt("expires_in", 900),
                        interval = json.optInt("interval", 5)
                    )
                )
            } else {
                Result.failure(RuntimeException("Device code request failed ($responseCode): $responseText"))
            }
        } catch (e: Exception) {
            Log.e(tag, "requestDeviceCode error", e)
            Result.failure(e)
        }
    }

    suspend fun pollAccessToken(clientId: String, deviceCode: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://github.com/login/oauth/access_token")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connectTimeout = 15000
                readTimeout = 15000
            }

            val body = "client_id=$clientId&device_code=$deviceCode&grant_type=urn:ietf:params:oauth:grant-type:device_code"
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

            val responseCode = conn.responseCode
            val responseText = (if (responseCode in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""

            if (responseCode in 200..299) {
                val json = JSONObject(responseText)
                if (json.has("access_token")) {
                    Result.success(json.getString("access_token"))
                } else {
                    val error = json.optString("error")
                    if (error == "authorization_pending") {
                        Result.success(null) // Still waiting for user
                    } else if (error == "slow_down") {
                        Result.success(null)
                    } else {
                        Result.failure(RuntimeException(json.optString("error_description", error)))
                    }
                }
            } else {
                Result.failure(RuntimeException("OAuth token request failed ($responseCode)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUser(token: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/user")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Flide-Mobile-IDE")
                connectTimeout = 15000
                readTimeout = 15000
            }

            val responseCode = conn.responseCode
            val text = (if (responseCode in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""

            if (responseCode in 200..299) {
                val json = JSONObject(text)
                Result.success(
                    GitHubUser(
                        login = json.getString("login"),
                        name = json.optString("name", null),
                        avatarUrl = json.optString("avatar_url", null)
                    )
                )
            } else {
                Result.failure(RuntimeException("Failed verifying GitHub token ($responseCode): $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWorkflowRuns(token: String, owner: String, repo: String): Result<List<WorkflowRun>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$owner/$repo/actions/runs?per_page=5")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Flide-Mobile-IDE")
                connectTimeout = 15000
                readTimeout = 15000
            }

            val responseCode = conn.responseCode
            val text = (if (responseCode in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""

            if (responseCode in 200..299) {
                val json = JSONObject(text)
                val runsArray = json.optJSONArray("workflow_runs") ?: JSONArray()
                val list = mutableListOf<WorkflowRun>()
                for (i in 0 until runsArray.length()) {
                    val obj = runsArray.getJSONObject(i)
                    list.add(
                        WorkflowRun(
                            id = obj.getLong("id"),
                            name = obj.getString("name"),
                            status = obj.getString("status"),
                            conclusion = if (obj.isNull("conclusion")) null else obj.getString("conclusion"),
                            htmlUrl = obj.getString("html_url"),
                            createdAt = obj.getString("created_at")
                        )
                    )
                }
                Result.success(list)
            } else {
                Result.failure(RuntimeException("Failed getting workflow runs ($responseCode): $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerWorkflowDispatch(
        token: String,
        owner: String,
        repo: String,
        workflowFileName: String,
        ref: String = "main"
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFileName/dispatches")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "Flide-Mobile-IDE")
                connectTimeout = 15000
                readTimeout = 15000
            }

            val body = JSONObject().apply { put("ref", ref) }.toString()
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                Result.success(true)
            } else {
                val text = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Result.failure(RuntimeException("Workflow dispatch failed ($responseCode): $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getArtifacts(token: String, owner: String, repo: String, runId: Long): Result<List<ArtifactItem>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Flide-Mobile-IDE")
                connectTimeout = 15000
                readTimeout = 15000
            }

            val responseCode = conn.responseCode
            val text = (if (responseCode in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""

            if (responseCode in 200..299) {
                val json = JSONObject(text)
                val artifactsArray = json.optJSONArray("artifacts") ?: JSONArray()
                val list = mutableListOf<ArtifactItem>()
                for (i in 0 until artifactsArray.length()) {
                    val obj = artifactsArray.getJSONObject(i)
                    list.add(
                        ArtifactItem(
                            id = obj.getLong("id"),
                            name = obj.getString("name"),
                            sizeInBytes = obj.getLong("size_in_bytes"),
                            archiveDownloadUrl = obj.getString("archive_download_url"),
                            expired = obj.optBoolean("expired", false)
                        )
                    )
                }
                Result.success(list)
            } else {
                Result.failure(RuntimeException("Failed getting artifacts ($responseCode): $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadArtifactZip(
        token: String,
        downloadUrl: String,
        destZipFile: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            var currentUrl = downloadUrl
            var conn: HttpURLConnection

            // Handle GitHub redirect to S3/Azure storage (up to 5 redirects)
            for (redirect in 0..5) {
                val uri = URL(currentUrl)
                conn = uri.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                val isGitHubHost = uri.host.endsWith("github.com")
                if (isGitHubHost) {
                    conn.setRequestProperty("Authorization", "Bearer $token")
                }
                conn.setRequestProperty("User-Agent", "Flide-Mobile-IDE")
                conn.connectTimeout = 20000
                conn.readTimeout = 30000

                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = location
                        continue
                    }
                }

                if (code in 200..299) {
                    val totalBytes = conn.contentLengthLong.coerceAtLeast(1L)
                    destZipFile.parentFile?.mkdirs()
                    destZipFile.outputStream().use { out ->
                        conn.inputStream.use { inStream ->
                            val buffer = ByteArray(32 * 1024)
                            var read: Int
                            var downloaded = 0L
                            while (inStream.read(buffer).also { read = it } != -1) {
                                out.write(buffer, 0, read)
                                downloaded += read
                                onProgress(downloaded.toFloat() / totalBytes.toFloat())
                            }
                        }
                    }
                    return@withContext Result.success(destZipFile)
                } else {
                    return@withContext Result.failure(RuntimeException("Artifact download failed with HTTP $code"))
                }
            }
            Result.failure(RuntimeException("Too many redirects during artifact download"))
        } catch (e: Exception) {
            Log.e(tag, "downloadArtifactZip error", e)
            Result.failure(e)
        }
    }
}
