package com.flide.env

import android.content.Context
import android.system.Os
import android.util.Log
import com.flide.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

class EnvironmentManager(
    private val context: Context,
    private val storageLayout: StorageLayout,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope
) {
    val prootRunner = ProotRunner(context, storageLayout)

    private val _state = MutableStateFlow<EnvironmentState>(EnvironmentState.Idle(isInstalled = false))
    val state: StateFlow<EnvironmentState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var activeJob: Job? = null

    init {
        checkInitialStatus()
    }

    private fun checkInitialStatus() {
        val rootfsExists = storageLayout.rootfsDir.exists() &&
                (File(storageLayout.rootfsDir, "bin/sh").exists() ||
                 File(storageLayout.rootfsDir, "usr/bin/sh").exists())
        _state.value = EnvironmentState.Idle(isInstalled = rootfsExists)
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        _logs.value = _logs.value + message
    }

    fun startSetup() {
        if (activeJob?.isActive == true) return

        activeJob = coroutineScope.launch {
            try {
                runBootstrapPipeline()
            } catch (e: CancellationException) {
                log("[!] Setup was cancelled by the user.")
                _state.value = EnvironmentState.Idle(isInstalled = false)
            } catch (t: Throwable) {
                log("[x] Setup failed: ${t.localizedMessage}")
                _state.value = EnvironmentState.Error(t.localizedMessage ?: "Unknown error occurred", t)
            }
        }
    }

    fun cancelSetup() {
        activeJob?.cancel()
        activeJob = null
    }

    fun resetEnvironment() {
        cancelSetup()
        coroutineScope.launch(Dispatchers.IO) {
            log("[*] Resetting Linux environment...")
            try {
                if (storageLayout.rootfsDir.exists()) {
                    storageLayout.rootfsDir.deleteRecursively()
                }
                settingsRepository.setEnvironmentConfigured(false)
                log("[✓] Environment reset successfully.")
                _state.value = EnvironmentState.Idle(isInstalled = false)
            } catch (t: Throwable) {
                log("[x] Failed to reset environment: ${t.message}")
                _state.value = EnvironmentState.Error("Reset failed: ${t.message}", t)
            }
        }
    }

    private suspend fun runBootstrapPipeline() = withContext(Dispatchers.IO) {
        log("=== Starting Flide Debian arm64 Bootstrap ===")
        storageLayout.ensureDirectoriesExist()

        // 1. Storage Check
        _state.value = EnvironmentState.CheckingStorage
        log("[1/6] Checking free device storage...")
        val freeBytes = storageLayout.getAvailableStorageBytes()
        val freeGb = freeBytes / (1024f * 1024f * 1024f)
        log(String.format(Locale.US, "    Available: %.2f GB (Required: >= 2.50 GB)", freeGb))

        if (freeBytes < EnvironmentConfig.MIN_STORAGE_REQUIRED_BYTES) {
            val errorMsg = String.format(
                Locale.US,
                "Insufficient storage: %.2f GB available, but 2.5 GB is required.",
                freeGb
            )
            log("[x] $errorMsg")
            _state.value = EnvironmentState.Error(errorMsg)
            return@withContext
        }
        log("[✓] Storage check passed.")

        // 2. Stage PRoot Binaries
        _state.value = EnvironmentState.StagingProot
        log("[2/6] Staging PRoot and support binaries...")
        stageProotBinaries()
        log("[✓] PRoot binaries staged at: ${storageLayout.prootBin.absolutePath}")

        // 3. Download Debian arm64 Rootfs Archive
        log("[3/6] Downloading Debian arm64 rootfs...")
        log("    Source: ${EnvironmentConfig.ROOTFS_URL}")
        val archiveFile = storageLayout.rootfsTarArchive
        downloadArchive(archiveFile)
        log("[✓] Rootfs archive downloaded (${archiveFile.length()} bytes).")

        // 4. Verify Checksum
        _state.value = EnvironmentState.VerifyingChecksum(0f)
        log("[4/6] Verifying SHA-256 archive integrity...")
        val verified = verifyChecksum(archiveFile)
        if (!verified) {
            archiveFile.delete()
            val errorMsg = "Checksum verification failed! Corrupt archive removed."
            log("[x] $errorMsg")
            _state.value = EnvironmentState.Error(errorMsg)
            return@withContext
        }
        log("[✓] Checksum matches: ${EnvironmentConfig.ROOTFS_SHA256}")

        // 5. Extract Rootfs
        _state.value = EnvironmentState.Extracting(0f, "Extracting packages...")
        log("[5/6] Extracting root filesystem to: ${storageLayout.rootfsDir.absolutePath}...")
        
        // Clean any stale or nested extraction from prior attempts
        if (storageLayout.rootfsDir.exists()) {
            storageLayout.rootfsDir.deleteRecursively()
        }
        storageLayout.rootfsDir.mkdirs()
        storageLayout.tmpDir.mkdirs()
        try {
            Os.chmod(storageLayout.tmpDir.absolutePath, 493) // 0755
        } catch (_: Throwable) {}

        TarXzExtractor.extract(archiveFile, storageLayout.rootfsDir) { progress, name ->
            _state.value = EnvironmentState.Extracting(progress, name)
        }
        log("[✓] Extraction completed successfully.")

        // 6. Configure Environment
        _state.value = EnvironmentState.Configuring("Configuring DNS, networking, and system tools")
        log("[6/6] Configuring rootfs environment...")
        configureNetworkAndUser()

        log("    Running smoke test inside PRoot...")
        val exitCode = prootRunner.execute(
            command = listOf("/bin/sh", "-c", "echo 'Debian Userland Smoke Test OK' && uname -a"),
            asRoot = true,
            onOutput = { log("    [proot] $it") }
        )

        if (exitCode == 0) {
            settingsRepository.setEnvironmentConfigured(true)
            _state.value = EnvironmentState.Ready
            log("=== Debian arm64 Environment is Ready! ===")
        } else {
            val errorMsg = "PRoot smoke test exited with code $exitCode"
            log("[x] $errorMsg")
            _state.value = EnvironmentState.Error(errorMsg)
        }
    }

    private fun stageProotBinaries() {
        val assetManager = context.assets
        val assetDir = "proot/arm64-v8a"
        val binaryFiles = listOf(
            "proot" to storageLayout.prootBin,
            "loader" to storageLayout.loaderBin,
            "libtalloc.so.2" to File(storageLayout.libDir, "libtalloc.so.2"),
            "libandroid-shmem.so" to File(storageLayout.libDir, "libandroid-shmem.so")
        )

        for ((assetName, targetFile) in binaryFiles) {
            targetFile.parentFile?.mkdirs()
            val assetPath = "$assetDir/$assetName"
            try {
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                setExecutablePermissions(targetFile)
                log("    Staged: ${targetFile.name}")
            } catch (e: Throwable) {
                // Check nativeLibraryDir fallback
                val fallbackLib = File(context.applicationInfo.nativeLibraryDir, "lib${assetName.removeSuffix(".2")}")
                if (fallbackLib.exists()) {
                    log("    Using fallback native library: ${fallbackLib.name}")
                } else {
                    log("[!] Warning: Could not stage $assetName: ${e.message}")
                }
            }
        }
    }

    private fun setExecutablePermissions(file: File) {
        try {
            file.setReadable(true, false)
            file.setExecutable(true, false)
            Os.chmod(file.absolutePath, 493) // 0755
        } catch (e: Throwable) {
            Log.w(TAG, "Failed setting 0755 on ${file.name}", e)
        }
    }

    private suspend fun downloadArchive(targetFile: File) = withContext(Dispatchers.IO) {
        val existingLength = if (targetFile.exists()) targetFile.length() else 0L

        val url = URL(EnvironmentConfig.ROOTFS_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 30000
            if (existingLength > 0) {
                setRequestProperty("Range", "bytes=$existingLength-")
            }
        }

        connection.connect()
        val responseCode = connection.responseCode

        val isResuming = responseCode == HttpURLConnection.HTTP_PARTIAL
        val totalBytes = if (isResuming) {
            existingLength + connection.contentLengthLong
        } else {
            connection.contentLengthLong
        }

        val append = isResuming && existingLength > 0
        if (!append && targetFile.exists()) {
            targetFile.delete()
        }

        var downloadedBytes = if (append) existingLength else 0L
        val inputStream: InputStream = connection.inputStream
        val outputStream = FileOutputStream(targetFile, append)

        val buffer = ByteArray(32 * 1024)
        var lastLogTime = System.currentTimeMillis()
        var lastDownloadedBytes = downloadedBytes

        try {
            inputStream.use { input ->
                outputStream.use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsedMs = now - lastLogTime
                        if (elapsedMs >= 500) {
                            val speed = ((downloadedBytes - lastDownloadedBytes) * 1000L) / elapsedMs
                            val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                            _state.value = EnvironmentState.Downloading(
                                progress = progress,
                                bytesDownloaded = downloadedBytes,
                                totalBytes = totalBytes,
                                speedBytesPerSec = speed
                            )
                            lastLogTime = now
                            lastDownloadedBytes = downloadedBytes
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun verifyChecksum(file: File): Boolean = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        val totalBytes = file.length()
        var processedBytes = 0L

        FileInputStream(file).use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
                processedBytes += bytesRead
                val progress = if (totalBytes > 0) processedBytes.toFloat() / totalBytes else 0f
                _state.value = EnvironmentState.VerifyingChecksum(progress)
            }
        }

        val calculatedHex = digest.digest().joinToString("") { "%02x".format(it) }
        log("    Calculated SHA-256: $calculatedHex")
        log("    Expected SHA-256:   ${EnvironmentConfig.ROOTFS_SHA256}")
        return@withContext calculatedHex.equals(EnvironmentConfig.ROOTFS_SHA256, ignoreCase = true)
    }

    private suspend fun configureNetworkAndUser() = withContext(Dispatchers.IO) {
        // 1. /etc/resolv.conf
        val etcDir = File(storageLayout.rootfsDir, "etc")
        etcDir.mkdirs()

        val resolvConf = File(etcDir, "resolv.conf")
        val resolvContent = EnvironmentConfig.DEFAULT_DNS_SERVERS.joinToString("\n") { "nameserver $it" } + "\n"
        resolvConf.writeText(resolvContent)
        log("    Configured /etc/resolv.conf with DNS: ${EnvironmentConfig.DEFAULT_DNS_SERVERS.joinToString(", ")}")

        // 2. /etc/hosts
        val hostsFile = File(etcDir, "hosts")
        hostsFile.writeText(EnvironmentConfig.DEFAULT_HOSTS_CONTENT)
        log("    Configured /etc/hosts")

        // 3. Configure APT Sandbox for PRoot
        val aptConfDir = File(etcDir, "apt/apt.conf.d")
        aptConfDir.mkdirs()
        File(aptConfDir, "99sandbox").writeText("APT::Sandbox::User \"root\";\n")

        // 4. Ensure user home and projects directories
        storageLayout.rootfsHomeDir.mkdirs()
        storageLayout.rootfsProjectsDir.mkdirs()
        log("    Prepared /home/flide/projects directory")

        // 5. Configure PATH with Flutter SDK in /etc/profile.d and ~/.bashrc
        val profileD = File(etcDir, "profile.d")
        profileD.mkdirs()
        File(profileD, "flide_env.sh").writeText(
            """
            export PATH="/home/flide/flutter/bin:${'$'}PATH"
            export FLUTTER_ROOT="/home/flide/flutter"
            export PUB_CACHE="/home/flide/.pub-cache"
            """.trimIndent() + "\n"
        )

        val bashrc = File(storageLayout.rootfsHomeDir, ".bashrc")
        val bashrcContent = if (bashrc.exists()) bashrc.readText() else ""
        if (!bashrcContent.contains("/home/flide/flutter/bin")) {
            bashrc.appendText(
                """

                # Flide Flutter environment
                export PATH="/home/flide/flutter/bin:${'$'}PATH"
                export FLUTTER_ROOT="/home/flide/flutter"
                export PUB_CACHE="/home/flide/.pub-cache"
                """.trimIndent() + "\n"
            )
        }
        log("    Configured persistent Flutter PATH in /etc/profile.d and ~/.bashrc")
    }

    companion object {
        private const val TAG = "EnvironmentManager"
    }
}
