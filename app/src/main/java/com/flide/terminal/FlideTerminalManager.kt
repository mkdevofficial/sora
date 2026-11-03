package com.flide.terminal

import com.flide.env.ProotRunner
import com.flide.env.StorageLayout
import com.termux.terminal.TerminalSession
import java.io.File

/**
 * Manages creation of PRoot-backed native Termux TerminalSessions.
 */
class FlideTerminalManager(
    private val storageLayout: StorageLayout,
    private val prootRunner: ProotRunner
) {

    fun buildSessionArgs(
        shell: String = "/bin/bash",
        workingDir: String = "/home/flide",
        asRoot: Boolean = true
    ): Array<String> {
        val args = mutableListOf<String>()
        args.add("proot")
        if (asRoot) {
            args.add("-0")
        }
        args.add("--link2symlink")
        args.add("-r")
        args.add(storageLayout.rootfsDir.absolutePath)
        args.add("-b")
        args.add("/dev")
        args.add("-b")
        args.add("/proc")
        args.add("-b")
        args.add("/sys")
        args.add("-b")
        args.add("${storageLayout.tmpDir.absolutePath}:/tmp")
        args.add("-b")
        args.add("${storageLayout.projectsDir.absolutePath}:/home/flide/projects")
        args.add("-w")
        args.add(workingDir)
        args.add(shell)
        args.add("-l")
        return args.toTypedArray()
    }

    fun createSession(
        client: FlideTerminalClient,
        workingDir: String = "/home/flide",
        asRoot: Boolean = true
    ): TerminalSession {
        val prootPath = prootRunner.getExecutableProotPath()

        val bashFile = File(storageLayout.rootfsDir, "usr/bin/bash")
        val altBashFile = File(storageLayout.rootfsDir, "bin/bash")
        val shell = if (bashFile.exists() || altBashFile.exists()) {
            "/bin/bash"
        } else {
            "/bin/sh"
        }

        val args = buildSessionArgs(shell, workingDir, asRoot)
        val envMap = prootRunner.getProotEnvironment()
        val envArray = envMap.map { "${it.key}=${it.value}" }.toTypedArray()

        return TerminalSession(
            prootPath,
            storageLayout.baseDir.absolutePath,
            args,
            envArray,
            2000,
            client
        )
    }
}
