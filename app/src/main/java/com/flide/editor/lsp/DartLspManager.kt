package com.flide.editor.lsp

import android.util.Log
import com.flide.env.ProotRunner
import com.flide.project.FlutterProject
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.CustomLanguageServerDefinition
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages the lifecycle of Dart Language Server (LSP) inside PRoot,
 * bridging diagnostics, completion, formatting, and lightweight mode.
 */
class DartLspManager(
    private val prootRunner: ProotRunner,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val tag = "DartLspManager"

    private var currentProject: FlutterProject? = null
    private var lspProject: LspProject? = null
    private var connectionProvider: DartLspConnectionProvider? = null

    private val _lspState = MutableStateFlow<LspServerState>(LspServerState.Disabled)
    val lspState: StateFlow<LspServerState> = _lspState.asStateFlow()

    private val _problems = MutableStateFlow<List<ProblemItem>>(emptyList())
    val problems: StateFlow<List<ProblemItem>> = _problems.asStateFlow()

    private val _isLightweightMode = MutableStateFlow(false)
    val isLightweightMode: StateFlow<Boolean> = _isLightweightMode.asStateFlow()

    private val activeEditors = mutableMapOf<String, LspEditor>()

    init {
        // Periodic diagnostics collector to update Problems panel
        coroutineScope.launch {
            while (isActive) {
                delay(1200)
                updateProblemsList()
            }
        }
    }

    /**
     * Initializes the LSP environment for the active project.
     */
    fun initializeForProject(project: FlutterProject) {
        if (currentProject?.rootDir?.path == project.rootDir.path && lspProject != null) {
            return
        }

        dispose()
        currentProject = project

        if (_isLightweightMode.value) {
            _lspState.value = LspServerState.Disabled
            return
        }

        startServer(project)
    }

    private fun startServer(project: FlutterProject) {
        try {
            _lspState.value = LspServerState.Starting
            Log.i(tag, "Initializing Dart LSP for project: ${project.name}")

            val proj = LspProject(project.rootDir.absolutePath)
            proj.init()

            val provider = DartLspConnectionProvider(
                project = project,
                prootRunner = prootRunner,
                onProgressUpdate = { title, isDone ->
                    _lspState.value = if (isDone) LspServerState.Ready else LspServerState.Indexing(title)
                },
                onServerCrash = { exitCode, reason ->
                    _lspState.value = LspServerState.Error(reason)
                }
            )
            connectionProvider = provider

            val serverDefinition = CustomLanguageServerDefinition(
                "dart",
                CustomLanguageServerDefinition.ServerConnectProvider { _ -> provider },
                "dart"
            )

            proj.addServerDefinition(serverDefinition)
            lspProject = proj
            _lspState.value = LspServerState.Ready
            Log.i(tag, "Dart LSP ready for project: ${project.name}")
        } catch (e: Throwable) {
            Log.e(tag, "Failed initializing Dart LSP: ${e.message}", e)
            _lspState.value = LspServerState.Error("Init failed: ${e.message}")
        }
    }

    /**
     * Gets or creates an LspEditor instance for a given file.
     */
    fun getOrCreateLspEditor(file: File): LspEditor? {
        if (_isLightweightMode.value) return null
        if (file.extension != "dart") return null

        val proj = lspProject ?: return null
        return try {
            activeEditors.getOrPut(file.absolutePath) {
                proj.getOrCreateEditor(file.absolutePath)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed creating LspEditor for ${file.name}: ${e.message}")
            null
        }
    }

    /**
     * Formats a Dart file using 'dart format' inside PRoot.
     */
    fun formatFile(file: File, onComplete: ((Boolean) -> Unit)? = null) {
        coroutineScope.launch {
            try {
                val proj = currentProject ?: return@launch
                val relativePath = file.relativeTo(proj.rootDir).path
                val guestPath = "${proj.rootfsPath}/$relativePath"

                val cmd = prootRunner.buildProotCommand(
                    command = listOf("/home/flide/flutter/bin/dart", "format", guestPath),
                    workingDir = proj.rootfsPath
                )
                val pb = ProcessBuilder(cmd)
                val env = pb.environment()
                env.clear()
                env.putAll(prootRunner.getProotEnvironment())
                env["HOME"] = "/home/flide"
                env["PATH"] = "/home/flide/flutter/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

                val proc = pb.start()
                val exitCode = proc.waitFor()
                val success = exitCode == 0
                Log.i(tag, "Formatted $relativePath (exit $exitCode)")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(success)
                }
            } catch (e: Exception) {
                Log.w(tag, "dart format failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    /**
     * Toggles lightweight mode (disables/enables LSP to conserve RAM).
     */
    fun setLightweightMode(enabled: Boolean) {
        _isLightweightMode.value = enabled
        if (enabled) {
            dispose()
            _lspState.value = LspServerState.Disabled
            _problems.value = emptyList()
        } else {
            currentProject?.let { startServer(it) }
        }
    }

    fun restartServer() {
        val proj = currentProject ?: return
        dispose()
        startServer(proj)
    }

    private fun updateProblemsList() {
        if (_isLightweightMode.value) return
        val problemList = mutableListOf<ProblemItem>()

        for ((path, editor) in activeEditors) {
            val file = File(path)
            try {
                val diagnostics = editor.diagnostics
                diagnostics.forEach { diag ->
                    problemList.add(ProblemItem.fromDiagnostic(file, diag))
                }
            } catch (e: Exception) {}
        }

        _problems.value = problemList
    }

    fun dispose() {
        try {
            activeEditors.clear()
            lspProject?.closeAllEditors()
            lspProject?.dispose()
            lspProject = null
            connectionProvider?.close()
            connectionProvider = null
            _problems.value = emptyList()
        } catch (e: Exception) {
            Log.w(tag, "Error disposing Dart LSP: ${e.message}")
        }
    }
}
