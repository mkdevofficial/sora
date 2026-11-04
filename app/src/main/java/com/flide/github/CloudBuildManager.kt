package com.flide.github

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.flide.data.settings.SettingsRepository
import com.flide.env.StorageLayout
import com.flide.git.GitManager
import com.flide.project.FlutterProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

class CloudBuildManager(
    private val storageLayout: StorageLayout,
    private val gitManager: GitManager,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val tag = "CloudBuildManager"
    val apiClient = GitHubApiClient()

    private val _buildState = MutableStateFlow<CloudBuildState>(CloudBuildState.Idle)
    val buildState: StateFlow<CloudBuildState> = _buildState.asStateFlow()

    private var pollingJob: Job? = null

    /**
     * Generates a GitHub Actions workflow to build release APKs in the cloud.
     */
    fun generateWorkflowFile(project: FlutterProject): File {
        val workflowsDir = File(project.rootDir, ".github/workflows")
        workflowsDir.mkdirs()
        val workflowFile = File(workflowsDir, "build_apk.yml")

        val content = """
            name: Build Flutter APK
            on:
              push:
                branches: [ main, master ]
              workflow_dispatch:

            jobs:
              build:
                name: Build Release APK
                runs-on: ubuntu-latest
                steps:
                  - name: Checkout Repository
                    uses: actions/checkout@v4

                  - name: Set up JDK 17
                    uses: actions/setup-java@v4
                    with:
                      distribution: 'temurin'
                      java-version: '17'

                  - name: Set up Flutter
                    uses: subosito/flutter-action@v2
                    with:
                      channel: 'stable'
                      cache: true

                  - name: Install Dependencies
                    run: flutter pub get

                  - name: Build Release APK
                    run: flutter build apk --release --split-per-abi

                  - name: Upload Release APK Artifact
                    uses: actions/upload-artifact@v4
                    with:
                      name: app-arm64-v8a-release
                      path: build/app/outputs/flutter-apk/app-arm64-v8a-release.apk
                      if-no-files-found: error
        """.trimIndent()

        workflowFile.writeText(content)
        return workflowFile
    }

    /**
     * Starts the Cloud APK Build pipeline:
     * 1. Generates the workflow file.
     * 2. Commits and pushes changes.
     * 3. Triggers workflow_dispatch.
     * 4. Polls until completed.
     * 5. Downloads and extracts the APK artifact.
     */
    fun startCloudBuild(
        project: FlutterProject,
        token: String,
        owner: String,
        repo: String,
        ref: String = "main"
    ) {
        pollingJob?.cancel()
        pollingJob = coroutineScope.launch {
            try {
                _buildState.value = CloudBuildState.Preparing

                // 1. Ensure workflow file exists
                generateWorkflowFile(project)

                // 2. Commit and push workflow file
                gitManager.stageFiles(project, listOf(".github/workflows/build_apk.yml"))
                gitManager.commit(project, "ci: add GitHub Actions workflow for release APK builds")

                // 3. Trigger workflow dispatch
                val triggerResult = apiClient.triggerWorkflowDispatch(
                    token = token,
                    owner = owner,
                    repo = repo,
                    workflowFileName = "build_apk.yml",
                    ref = ref
                )
                if (triggerResult.isFailure) {
                    _buildState.value = CloudBuildState.Error("Failed to trigger workflow: ${triggerResult.exceptionOrNull()?.message}")
                    return@launch
                }

                // 4. Poll for active run
                var activeRun: WorkflowRun? = null
                for (attempt in 0..10) {
                    delay(3000)
                    val runsRes = apiClient.getWorkflowRuns(token, owner, repo)
                    if (runsRes.isSuccess) {
                        val runs = runsRes.getOrNull() ?: emptyList()
                        val latest = runs.firstOrNull()
                        if (latest != null) {
                            activeRun = latest
                            break
                        }
                    }
                }

                if (activeRun == null) {
                    _buildState.value = CloudBuildState.Error("Workflow triggered but could not find active run")
                    return@launch
                }

                var currentRun: WorkflowRun = activeRun

                // 5. Poll run until completion
                while (isActive) {
                    _buildState.value = CloudBuildState.Building(
                        runId = currentRun.id,
                        status = currentRun.status,
                        conclusion = currentRun.conclusion,
                        logs = "Status: ${currentRun.status}"
                    )

                    if (currentRun.status == "completed") {
                        if (currentRun.conclusion == "success") {
                            break
                        } else {
                            _buildState.value = CloudBuildState.Error("Build finished with conclusion: ${currentRun.conclusion}")
                            return@launch
                        }
                    }

                    delay(5000)
                    val updateRes = apiClient.getWorkflowRuns(token, owner, repo)
                    val latest = updateRes.getOrNull()?.firstOrNull { it.id == currentRun.id }
                    if (latest != null) {
                        currentRun = latest
                    }
                }

                // 6. Download Artifact
                _buildState.value = CloudBuildState.Downloading(0.05f)
                val artifactsRes = apiClient.getArtifacts(token, owner, repo, currentRun.id)
                val artifact = artifactsRes.getOrNull()?.firstOrNull { it.name.contains("arm64") || it.name.contains("release") }
                    ?: artifactsRes.getOrNull()?.firstOrNull()

                if (artifact == null) {
                    _buildState.value = CloudBuildState.Error("Build completed, but no APK artifact found")
                    return@launch
                }

                val downloadZip = File(storageLayout.downloadsDir, "apk_artifact_${currentRun.id}.zip")
                val downloadRes = apiClient.downloadArtifactZip(
                    token = token,
                    downloadUrl = artifact.archiveDownloadUrl,
                    destZipFile = downloadZip,
                    onProgress = { p -> _buildState.value = CloudBuildState.Downloading(p) }
                )

                if (downloadRes.isFailure) {
                    _buildState.value = CloudBuildState.Error("Failed downloading APK artifact: ${downloadRes.exceptionOrNull()?.message}")
                    return@launch
                }

                // 7. Extract APK from ZIP
                val apkFile = extractApkFromZip(downloadZip, project.name)
                if (apkFile != null && apkFile.exists()) {
                    _buildState.value = CloudBuildState.ReadyToInstall(apkFile)
                } else {
                    _buildState.value = CloudBuildState.Error("Failed extracting .apk from downloaded artifact")
                }

            } catch (e: Exception) {
                Log.e(tag, "startCloudBuild failed", e)
                _buildState.value = CloudBuildState.Error(e.message ?: "Build error")
            }
        }
    }

    private fun extractApkFromZip(zipFile: File, projectName: String): File? {
        try {
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".apk")) {
                        val outFile = File(storageLayout.downloadsDir, "${projectName}_release.apk")
                        outFile.outputStream().use { out ->
                            zis.copyTo(out)
                        }
                        return outFile
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "extractApkFromZip error", e)
        }
        return null
    }

    /**
     * Prompts the user to install the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch package installer", e)
        }
    }

    fun reset() {
        pollingJob?.cancel()
        _buildState.value = CloudBuildState.Idle
    }
}
