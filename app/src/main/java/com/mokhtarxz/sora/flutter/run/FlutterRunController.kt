package com.mokhtarxz.sora.flutter.run

import android.content.Context
import android.util.Log
import com.mokhtarxz.sora.env.ProotRunner
import com.mokhtarxz.sora.env.StorageLayout
import com.mokhtarxz.sora.flutter.FlutterConfig
import com.mokhtarxz.sora.project.FlutterProject
import com.mokhtarxz.sora.service.IdeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Controller for running `flutter run -d web-server` inside PRoot:
 * - Spawns and manages the interactive background process
 * - Parses stdout to detect when the local web server is live
 * - Attached stdin pipes hot reload ('r'), hot restart ('R'), and quit ('q')
 * - Integrates with IdeService to maintain wake lock and foreground status
 */
class FlutterRunController(
    private val context: Context,
    private val storageLayout: StorageLayout,
    private val prootRunner: ProotRunner,
    private val coroutineScope: CoroutineScope
) {

    private val _runState = MutableStateFlow<FlutterRunState>(FlutterRunState.Idle)
    val runState: StateFlow<FlutterRunState> = _runState.asStateFlow()

    private val _logs = MutableStateFlow<List<RunLogEntry>>(emptyList())
    val logs: StateFlow<List<RunLogEntry>> = _logs.asStateFlow()

    private val _activeWebUrl = MutableStateFlow<String?>(null)
    val activeWebUrl: StateFlow<String?> = _activeWebUrl.asStateFlow()

    private var activeProcess: Process? = null
    private var stdinWriter: BufferedWriter? = null
    private var processJob: Job? = null
    private var currentPort: Int = FlutterConfig.DEFAULT_WEB_PORT

    val isRunning: Boolean
        get() = _runState.value is FlutterRunState.Running ||
                _runState.value is FlutterRunState.Starting ||
                _runState.value is FlutterRunState.Compiling ||
                _runState.value is FlutterRunState.Reloading ||
                _runState.value is FlutterRunState.Restarting

    /**
     * Starts `flutter run -d web-server` for the given [project].
     */
    fun startRun(project: FlutterProject, requestedPort: Int = FlutterConfig.DEFAULT_WEB_PORT) {
        if (isRunning) {
            logSystem("A Flutter run session is already active. Please stop it first.")
            return
        }

        currentPort = findAvailablePort(requestedPort)
        _runState.value = FlutterRunState.Starting(project, currentPort)
        _activeWebUrl.value = null
        clearLogs()

        logSystem("=== Starting '${project.name}' on Web Server (port $currentPort) ===")
        IdeService.start(context, "Running ${project.name} on :$currentPort")

        processJob = coroutineScope.launch(Dispatchers.IO) {
            executeRunSession(project, currentPort)
        }
    }

    private suspend fun executeRunSession(project: FlutterProject, port: Int) = withContext(Dispatchers.IO) {
        val flutterBin = storageLayout.rootfsFlutterBin
        if (!flutterBin.exists()) {
            val err = "Flutter SDK is not found in rootfs (/home/flide/flutter/bin/flutter)."
            logError(err)
            _runState.value = FlutterRunState.Error(project, err)
            IdeService.updateStatus(context, "Flide: Idle")
            return@withContext
        }

        val projectRootfsDir = "/home/flide/projects/${project.name}"
        val flutterCommand = listOf(
            FlutterConfig.FLUTTER_BIN_IN_ROOTFS,
            "run",
            "-d", "web-server",
            "--web-hostname", FlutterConfig.DEFAULT_WEB_HOSTNAME,
            "--web-port", port.toString()
        )

        val fullProotCmd = prootRunner.buildProotCommand(
            command = flutterCommand,
            workingDir = projectRootfsDir,
            asRoot = true
        )

        logSystem("$ ${flutterCommand.joinToString(" ")}")

        val pb = ProcessBuilder(fullProotCmd)
        pb.redirectErrorStream(true)
        val env = pb.environment()
        env.putAll(prootRunner.getProotEnvironment())

        try {
            val process = pb.start()
            activeProcess = process
            stdinWriter = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))

            val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                line?.let { rawLine ->
                    handleProcessOutput(project, port, rawLine)
                }
            }

            val exitCode = process.waitFor()
            logSystem("Process exited with code $exitCode")

            val currentState = _runState.value
            if (currentState !is FlutterRunState.Error) {
                _runState.value = FlutterRunState.Stopped(project, exitCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in flutter run session", e)
            val err = "Run session failed: ${e.message}"
            logError(err)
            _runState.value = FlutterRunState.Error(project, err)
        } finally {
            cleanupSession()
            IdeService.updateStatus(context, "Flide: Idle")
        }
    }

    /**
     * Parses real-time output from `flutter run -d web-server`.
     */
    private fun handleProcessOutput(project: FlutterProject, port: Int, rawLine: String) {
        val clean = stripAnsi(rawLine)
        val trimmed = clean.trim()

        // Categorize log entry
        val level = when {
            trimmed.contains("Error") || trimmed.contains("Exception") || trimmed.contains("failed") -> RunLogLevel.ERROR
            trimmed.contains("Reloaded") || trimmed.contains("Restarted") || trimmed.contains("is running at") -> RunLogLevel.SUCCESS
            trimmed.contains("warning", ignoreCase = true) -> RunLogLevel.WARNING
            trimmed.startsWith("$") -> RunLogLevel.SYSTEM
            else -> RunLogLevel.STDOUT
        }
        appendLog(trimmed, level)

        // Web Server URL Detection
        // Common outputs:
        // "Flutter Web Server is running at http://127.0.0.1:8080"
        // "Serving at http://127.0.0.1:8080"
        // "Web server available at http://127.0.0.1:8080"
        val serverUrlPattern = Pattern.compile("https?://(?:127\\.0\\.0\\.1|localhost):$port(?:/[^\\s]*)?")
        val matcher = serverUrlPattern.matcher(clean)
        if (matcher.find()) {
            val detectedUrl = matcher.group(0)
            if (detectedUrl != null && !detectedUrl.contains("ws://")) {
                _activeWebUrl.value = detectedUrl
                val currentState = _runState.value
                val devToolsUrl = if (currentState is FlutterRunState.Running) currentState.devToolsUrl else null
                _runState.value = FlutterRunState.Running(project, detectedUrl, port, devToolsUrl)
                IdeService.updateStatus(context, "Running ${project.name} on $detectedUrl")
            }
        }

        // DevTools URL Detection
        if (clean.contains("Flutter DevTools", ignoreCase = true) || clean.contains("The Flutter DevTools debugger")) {
            val devToolsPattern = Pattern.compile("https?://[a-zA-Z0-9.:_-]+(?:/[^\\s]*)?")
            val dtMatcher = devToolsPattern.matcher(clean)
            if (dtMatcher.find()) {
                val dtUrl = dtMatcher.group(0)
                val currentState = _runState.value
                if (currentState is FlutterRunState.Running) {
                    _runState.value = currentState.copy(devToolsUrl = dtUrl)
                }
            }
        }

        // Compiling Detection
        if (clean.contains("Compiling lib/main.dart") || clean.contains("Building Linux web application") || clean.contains("Waiting for connection")) {
            if (_runState.value !is FlutterRunState.Running) {
                _runState.value = FlutterRunState.Compiling(project, port, trimmed)
            }
        }

        // Hot Reload Confirmation Detection
        if (clean.contains("Reloaded") && clean.contains("in") && clean.contains("ms")) {
            val currentUrl = _activeWebUrl.value ?: "http://127.0.0.1:$port"
            _runState.value = FlutterRunState.Running(project, currentUrl, port)
        }

        // Hot Restart Confirmation Detection
        if (clean.contains("Restarted application") || clean.contains("Restarted in")) {
            val currentUrl = _activeWebUrl.value ?: "http://127.0.0.1:$port"
            _runState.value = FlutterRunState.Running(project, currentUrl, port)
        }
    }

    /**
     * Sends Hot Reload ('r') command through stdin.
     */
    fun sendHotReload() {
        val currentState = _runState.value
        if (currentState is FlutterRunState.Running) {
            _runState.value = FlutterRunState.Reloading(currentState.project, currentState.url, currentState.port)
            logSystem("⚡ Triggering Hot Reload (sending 'r')...")
            sendCommand("r\n")
        } else {
            logWarning("Cannot hot reload: App is not currently running.")
        }
    }

    /**
     * Sends Hot Restart ('R') command through stdin.
     */
    fun sendHotRestart() {
        val currentState = _runState.value
        if (currentState is FlutterRunState.Running) {
            _runState.value = FlutterRunState.Restarting(currentState.project, currentState.url, currentState.port)
            logSystem("🔄 Triggering Hot Restart (sending 'R')...")
            sendCommand("R\n")
        } else {
            logWarning("Cannot hot restart: App is not currently running.")
        }
    }

    /**
     * Stops the active Flutter run session gracefully by sending 'q', then forcefully if required.
     */
    fun stopRun() {
        if (!isRunning) return

        logSystem("Stopping Flutter run session (sending 'q')...")
        coroutineScope.launch(Dispatchers.IO) {
            sendCommand("q\n")

            // Allow 2.5 seconds for graceful shutdown
            var count = 0
            while (activeProcess?.isAlive == true && count < 25) {
                delay(100)
                count++
            }

            if (activeProcess?.isAlive == true) {
                logWarning("Process did not exit gracefully, terminating forcefully.")
                activeProcess?.destroyForcibly()
            }

            cleanupSession()
            _runState.value = FlutterRunState.Idle
            IdeService.updateStatus(context, "Flide: Idle")
        }
    }

    private fun sendCommand(command: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                stdinWriter?.let { writer ->
                    writer.write(command)
                    writer.flush()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed sending command to process stdin: ${e.message}")
            }
        }
    }

    private fun cleanupSession() {
        try {
            stdinWriter?.close()
        } catch (_: Exception) {}
        stdinWriter = null
        activeProcess = null
        processJob = null
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun appendLog(msg: String, level: RunLogLevel) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val entry = RunLogEntry(time, msg, level)
        _logs.value = _logs.value + entry
    }

    private fun logSystem(msg: String) = appendLog("[SYSTEM] $msg", RunLogLevel.SYSTEM)
    private fun logError(msg: String) = appendLog("[ERROR] $msg", RunLogLevel.ERROR)
    private fun logWarning(msg: String) = appendLog("[WARNING] $msg", RunLogLevel.WARNING)

    /**
     * Checks if [port] is available on 127.0.0.1; if bound, finds the next available port.
     */
    private fun findAvailablePort(preferredPort: Int): Int {
        for (port in preferredPort..(preferredPort + 50)) {
            try {
                ServerSocket(port).use {
                    return it.localPort
                }
            } catch (_: Exception) {
                // Port occupied, try next
            }
        }
        return preferredPort
    }

    private fun stripAnsi(str: String): String {
        return str.replace(ANSI_REGEX, "")
    }

    companion object {
        private const val TAG = "FlutterRunController"
        private val ANSI_REGEX = Regex("\u001B\\[[;\\d]*[ -/]*[@-~]")
    }
}
