package com.mokhtarxz.sora.flutter

import android.content.Context
import android.util.Log
import com.mokhtarxz.sora.data.settings.SettingsRepository
import com.mokhtarxz.sora.env.ProotRunner
import com.mokhtarxz.sora.env.StorageLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages Flutter SDK lifecycle inside the Debian arm64 PRoot userland:
 * - Installing required Debian packages (git, curl, clang, cmake...)
 * - Shallow cloning Flutter SDK (default: stable channel)
 * - First-run Dart SDK bootstrap for linux-arm64
 * - Disabling analytics and precaching Web artifacts
 * - Verifying toolchain via `flutter doctor`
 */
class FlutterManager(
    private val context: Context,
    private val storageLayout: StorageLayout,
    private val prootRunner: ProotRunner,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope
) {

    private val _state = MutableStateFlow<FlutterState>(FlutterState.NotInstalled)
    val state: StateFlow<FlutterState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var installJob: Job? = null

    val installLogFile: File
        get() = File(storageLayout.logsDir, "flutter_install.log")

    init {
        checkInitialStatus()
    }

    fun checkInitialStatus() {
        if (!storageLayout.rootfsDir.exists()) {
            _state.value = FlutterState.NotInstalled
            return
        }

        ensureEnvironmentConfigured()

        val flutterBin = storageLayout.rootfsFlutterBin
        if (flutterBin.exists()) {
            val versionFile = File(storageLayout.rootfsFlutterDir, "version")
            val version = if (versionFile.exists()) {
                versionFile.readText().trim()
            } else {
                "installed"
            }
            _state.value = FlutterState.Ready(
                version = version,
                channel = "stable",
                doctorReport = "Flutter SDK is ready inside Debian rootfs."
            )
        } else {
            _state.value = FlutterState.NotInstalled
        }
    }

    fun installFlutter(channel: String = FlutterConfig.DEFAULT_CHANNEL) {
        if (installJob?.isActive == true) return

        installJob = coroutineScope.launch(Dispatchers.IO) {
            executeFlutterInstallation(channel = channel, isUpdate = false)
        }
    }

    fun updateFlutter() {
        if (installJob?.isActive == true) return

        installJob = coroutineScope.launch(Dispatchers.IO) {
            executeFlutterInstallation(channel = FlutterConfig.DEFAULT_CHANNEL, isUpdate = true)
        }
    }

    fun cancelInstallation() {
        installJob?.cancel()
        installJob = null
        log("[!] Flutter installation cancelled by user.")
        _state.value = FlutterState.Error("Installation was cancelled.")
    }

    private suspend fun executeFlutterInstallation(channel: String, isUpdate: Boolean) = withContext(Dispatchers.IO) {
        clearLogs()
        log("=== Starting Flutter SDK Setup ($channel channel) ===")
        val startTime = System.currentTimeMillis()

        // 1. Storage check
        val freeBytes = storageLayout.getAvailableStorageBytes()
        val freeGb = freeBytes / (1024f * 1024f * 1024f)
        log("[1/6] Checking storage space...")
        log("    Available: ${String.format(Locale.US, "%.2f", freeGb)} GB (Required: >= ${FlutterConfig.MIN_FREE_STORAGE_GB} GB)")
        if (freeGb < FlutterConfig.MIN_FREE_STORAGE_GB) {
            val err = "Insufficient storage space: %.2f GB available, at least %.2f GB required".format(
                freeGb, FlutterConfig.MIN_FREE_STORAGE_GB
            )
            log("[x] $err")
            _state.value = FlutterState.Error(err)
            return@withContext
        }
        log("[✓] Storage space verified.")

        // 2. Prepare environment and APT configuration
        ensureEnvironmentConfigured()

        // 3. Install required Debian build packages
        _state.value = FlutterState.InstallingPackages("Updating APT package lists...")
        log("[2/6] Installing required Debian tools (git, curl, clang, cmake, ninja)...")

        val aptUpdateExit = prootRunner.execute(
            command = listOf("apt-get", "update"),
            asRoot = true,
            onOutput = { log("    [apt] $it") }
        )
        if (aptUpdateExit != 0) {
            val err = "apt-get update failed with code $aptUpdateExit. Check network connection."
            log("[x] $err")
            _state.value = FlutterState.Error(err)
            return@withContext
        }

        _state.value = FlutterState.InstallingPackages("Installing git, curl, build essentials...")
        val pkgList = FlutterConfig.REQUIRED_DEBIAN_PACKAGES
        val aptInstallCmd = mutableListOf("apt-get", "install", "-y", "--no-install-recommends")
        aptInstallCmd.addAll(pkgList)

        val aptInstallExit = prootRunner.execute(
            command = aptInstallCmd,
            asRoot = true,
            onOutput = { log("    [apt] $it") }
        )
        if (aptInstallExit != 0) {
            val err = "Failed to install required Debian packages (code $aptInstallExit)."
            log("[x] $err")
            _state.value = FlutterState.Error(err)
            return@withContext
        }
        log("[✓] Required Debian packages installed.")

        // 4. Configure Git Safe Directory
        prootRunner.execute(
            command = listOf("git", "config", "--global", "--add", "safe.directory", "*"),
            asRoot = true
        )

        // 5. Clone or Update Flutter SDK
        val flutterTarget = storageLayout.rootfsFlutterDir
        _state.value = FlutterState.Cloning(channel, "Shallow cloning Flutter repository...")
        log("[3/6] Fetching Flutter SDK from official repository...")

        if (isUpdate && flutterTarget.exists()) {
            log("    Updating existing Flutter installation...")
            val pullExit = prootRunner.execute(
                command = listOf("git", "-C", FlutterConfig.FLUTTER_DIR_IN_ROOTFS, "pull"),
                asRoot = true,
                onOutput = { log("    [git] $it") }
            )
            if (pullExit != 0) {
                log("[!] git pull failed, re-cloning repository...")
                flutterTarget.deleteRecursively()
                cloneFlutterRepo(channel)
            }
        } else {
            if (flutterTarget.exists()) {
                flutterTarget.deleteRecursively()
            }
            val cloneExit = cloneFlutterRepo(channel)
            if (cloneExit != 0) {
                val err = "git clone failed with code $cloneExit. Check network connectivity."
                log("[x] $err")
                _state.value = FlutterState.Error(err)
                return@withContext
            }
        }
        log("[✓] Flutter SDK repository ready at ${flutterTarget.absolutePath}")

        // 6. First-Run Bootstrap (Downloads Dart SDK for linux-arm64)
        _state.value = FlutterState.Bootstrapping("Downloading Dart SDK for linux-arm64...")
        log("[4/6] Bootstrapping Flutter engine and Dart SDK (linux-arm64)...")

        val versionOutput = StringBuilder()
        val versionExit = prootRunner.execute(
            command = listOf(FlutterConfig.FLUTTER_BIN_IN_ROOTFS, "--version"),
            asRoot = true,
            onOutput = { line ->
                versionOutput.appendLine(line)
                log("    [flutter] $line")
            }
        )
        if (versionExit != 0) {
            val err = "Flutter first-run bootstrap failed with exit code $versionExit."
            log("[x] $err")
            _state.value = FlutterState.Error(err)
            return@withContext
        }
        log("[✓] Dart SDK and Flutter engine bootstrapped.")

        // 7. Telemetry & Web Precache Configuration
        _state.value = FlutterState.Configuring("Configuring analytics & precaching Web artifacts...")
        log("[5/6] Disabling analytics and enabling Web support...")

        // Disable telemetry
        prootRunner.execute(
            command = listOf(FlutterConfig.FLUTTER_BIN_IN_ROOTFS, "config", "--no-analytics"),
            asRoot = true,
            onOutput = { log("    [config] $it") }
        )
        prootRunner.execute(
            command = listOf(FlutterConfig.DART_BIN_IN_ROOTFS, "--disable-analytics"),
            asRoot = true,
            onOutput = { log("    [config] $it") }
        )

        // Enable web
        prootRunner.execute(
            command = listOf(FlutterConfig.FLUTTER_BIN_IN_ROOTFS, "config", "--enable-web"),
            asRoot = true,
            onOutput = { log("    [config] $it") }
        )

        // Precache web artifacts only (skipping Android and iOS heavy binaries)
        log("    Precaching Web artifacts (skipping Android/iOS SDKs)...")
        val precacheExit = prootRunner.execute(
            command = listOf(FlutterConfig.FLUTTER_BIN_IN_ROOTFS, "precache", "--web"),
            asRoot = true,
            onOutput = { log("    [precache] $it") }
        )
        if (precacheExit != 0) {
            log("[!] Precache completed with warnings (exit code $precacheExit), continuing...")
        } else {
            log("[✓] Web artifacts precached.")
        }

        // 8. Toolchain Verification (`flutter doctor`)
        _state.value = FlutterState.RunningDoctor("Verifying environment with flutter doctor...")
        log("[6/6] Running flutter doctor verification...")

        val doctorOutput = StringBuilder()
        prootRunner.execute(
            command = listOf(FlutterConfig.FLUTTER_BIN_IN_ROOTFS, "doctor", "-v"),
            asRoot = true,
            onOutput = { line ->
                doctorOutput.appendLine(line)
                log("    [doctor] $line")
            }
        )

        val durationSec = (System.currentTimeMillis() - startTime) / 1000
        val versionFile = File(storageLayout.rootfsFlutterDir, "version")
        val detectedVersion = if (versionFile.exists()) versionFile.readText().trim() else "stable"

        settingsRepository.updateFlutterChannel(channel)
        _state.value = FlutterState.Ready(
            version = detectedVersion,
            channel = channel,
            doctorReport = doctorOutput.toString()
        )
        log("=== Flutter SDK Installation Finished Successfully in ${durationSec}s! ===")
    }

    private suspend fun cloneFlutterRepo(channel: String): Int {
        return prootRunner.execute(
            command = listOf(
                "git",
                "clone",
                "-b", channel,
                "--depth", "1",
                FlutterConfig.GIT_REPO_URL,
                FlutterConfig.FLUTTER_DIR_IN_ROOTFS
            ),
            asRoot = true,
            onOutput = { log("    [git] $it") }
        )
    }

    suspend fun runDoctor(): String = withContext(Dispatchers.IO) {
        val report = StringBuilder()
        log("--- Running flutter doctor ---")
        prootRunner.execute(
            command = listOf(FlutterConfig.FLUTTER_BIN_IN_ROOTFS, "doctor", "-v"),
            asRoot = true,
            onOutput = { line ->
                report.appendLine(line)
                log("    [doctor] $line")
            }
        )
        return@withContext report.toString()
    }

    /**
     * Ensures APT sandbox configuration, .dockerenv flag, and persistent PATH profiles exist.
     */
    fun ensureEnvironmentConfigured() {
        if (!storageLayout.rootfsDir.exists()) return

        try {
            val etcDir = File(storageLayout.rootfsDir, "etc")
            etcDir.mkdirs()

            // 1. Dockerenv indicator to avoid container root warnings
            val dockerEnv = File(storageLayout.rootfsDir, ".dockerenv")
            if (!dockerEnv.exists()) {
                dockerEnv.createNewFile()
            }

            // 2. APT sandbox root override
            val aptConfDir = File(etcDir, "apt/apt.conf.d")
            aptConfDir.mkdirs()
            val sandboxFile = File(aptConfDir, "99sandbox")
            if (!sandboxFile.exists()) {
                sandboxFile.writeText("APT::Sandbox::User \"root\";\n")
            }

            // 3. /etc/profile.d/flide_env.sh
            val profileD = File(etcDir, "profile.d")
            profileD.mkdirs()
            File(profileD, "flide_env.sh").writeText(
                """
                export PATH="/home/flide/flutter/bin:${'$'}PATH"
                export FLUTTER_ROOT="/home/flide/flutter"
                export PUB_CACHE="/home/flide/.pub-cache"
                """.trimIndent() + "\n"
            )

            // 4. ~/.bashrc
            val homeDir = storageLayout.rootfsHomeDir
            homeDir.mkdirs()
            val bashrc = File(homeDir, ".bashrc")
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
        } catch (e: Throwable) {
            Log.w(TAG, "Failed configuring profile/apt settings: ${e.message}")
        }
    }

    private fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val formatted = "[$timestamp] $message"
        Log.i(TAG, formatted)

        _logs.value = _logs.value + formatted

        // Append to persistent log file
        try {
            installLogFile.parentFile?.mkdirs()
            FileOutputStream(installLogFile, true).use { out ->
                out.write((formatted + "\n").toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {}
    }

    private fun clearLogs() {
        _logs.value = emptyList()
        try {
            if (installLogFile.exists()) {
                installLogFile.delete()
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "FlutterManager"
    }
}
