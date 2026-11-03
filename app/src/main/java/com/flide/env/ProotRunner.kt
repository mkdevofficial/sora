package com.flide.env

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Handles executing commands and interactive processes inside the Debian rootfs using PRoot.
 */
class ProotRunner(
    private val storageLayout: StorageLayout,
    private val nativeLibraryDir: String? = null
) {
    constructor(context: Context, storageLayout: StorageLayout) : this(
        storageLayout = storageLayout,
        nativeLibraryDir = try { context.applicationInfo.nativeLibraryDir } catch (_: Exception) { null }
    )

    fun getExecutableProotPath(): String {
        return if (storageLayout.prootBin.exists() && storageLayout.prootBin.canExecute()) {
            storageLayout.prootBin.absolutePath
        } else {
            // Documented fallback: native library directory
            val fallback = nativeLibraryDir?.let { File(it, "libproot.so") }
            if (fallback != null && fallback.exists()) fallback.absolutePath else storageLayout.prootBin.absolutePath
        }
    }

    fun buildProotCommand(
        command: List<String>,
        workingDir: String = "/home/flide",
        asRoot: Boolean = false
    ): List<String> {
        val proot = getExecutableProotPath()
        val cmd = mutableListOf<String>()

        cmd.add(proot)
        if (asRoot) {
            cmd.add("-0") // Fake root
        }
        cmd.add("--link2symlink")
        cmd.add("-r")
        cmd.add(storageLayout.rootfsDir.absolutePath)
        cmd.add("-b")
        cmd.add("/dev")
        cmd.add("-b")
        cmd.add("/proc")
        cmd.add("-b")
        cmd.add("/sys")
        cmd.add("-b")
        cmd.add("${storageLayout.projectsDir.absolutePath}:/home/flide/projects")
        cmd.add("-w")
        cmd.add(workingDir)

        cmd.addAll(command)
        return cmd
    }

    fun getProotEnvironment(): Map<String, String> {
        val env = mutableMapOf<String, String>()
        val libDir = storageLayout.libDir.absolutePath
        val nativeDir = nativeLibraryDir ?: ""

        env["LD_LIBRARY_PATH"] = if (nativeDir.isNotEmpty()) "$libDir:$nativeDir" else libDir
        if (storageLayout.loaderBin.exists()) {
            env["PROOT_LOADER"] = storageLayout.loaderBin.absolutePath
        } else if (nativeDir.isNotEmpty()) {
            val fallbackLoader = File(nativeDir, "libloader.so")
            if (fallbackLoader.exists()) {
                env["PROOT_LOADER"] = fallbackLoader.absolutePath
            }
        }

        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        env["HOME"] = "/home/flide"
        env["USER"] = "flide"
        env["TERM"] = "xterm-256color"
        env["LANG"] = "C.UTF-8"
        env["TMPDIR"] = "/tmp"

        return env
    }

    suspend fun execute(
        command: List<String>,
        workingDir: String = "/home/flide",
        asRoot: Boolean = false,
        onOutput: (String) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        val fullCmd = buildProotCommand(command, workingDir, asRoot)
        Log.i(TAG, "Executing: ${fullCmd.joinToString(" ")}")

        val pb = ProcessBuilder(fullCmd)
        pb.redirectErrorStream(true)
        val env = pb.environment()
        env.putAll(getProotEnvironment())

        try {
            val process = pb.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { onOutput(it) }
            }
            process.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "Error running proot command", e)
            onOutput("[x] Failed to launch command: ${e.message}")
            -1
        }
    }

    fun startInteractiveShell(
        shell: String = "/bin/bash",
        workingDir: String = "/home/flide",
        asRoot: Boolean = false
    ): Process {
        val fullCmd = buildProotCommand(listOf(shell), workingDir, asRoot)
        val pb = ProcessBuilder(fullCmd)
        val env = pb.environment()
        env.putAll(getProotEnvironment())
        return pb.start()
    }

    companion object {
        private const val TAG = "ProotRunner"
    }
}
