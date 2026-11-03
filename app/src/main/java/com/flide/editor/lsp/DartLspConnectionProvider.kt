package com.flide.editor.lsp

import android.util.Log
import com.flide.env.ProotRunner
import com.flide.project.FlutterProject
import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.concurrent.thread

/**
 * Bridges Sora Editor LSP client with the 'dart language-server' process inside PRoot.
 * Applies path translation between the host Android directory and guest Debian rootfs mount.
 */
class DartLspConnectionProvider(
    private val project: FlutterProject,
    private val prootRunner: ProotRunner,
    private val onProgressUpdate: ((title: String, isDone: Boolean) -> Unit)? = null,
    private val onServerCrash: ((exitCode: Int, reason: String) -> Unit)? = null
) : StreamConnectionProvider {

    private val tag = "DartLspProvider"
    private var process: Process? = null
    private var isTerminated = false

    // Client -> Server pipes
    private val clientToServerIn: PipedInputStream
    private val clientToServerOut: PipedOutputStream

    // Server -> Client pipes
    private val serverToClientIn: PipedInputStream
    private val serverToClientOut: PipedOutputStream

    private val translator: LspMessageTranslator

    init {
        clientToServerOut = PipedOutputStream()
        clientToServerIn = PipedInputStream(clientToServerOut, 128 * 1024)

        serverToClientOut = PipedOutputStream()
        serverToClientIn = PipedInputStream(serverToClientOut, 128 * 1024)

        translator = LspMessageTranslator(
            androidProjectPath = project.rootDir.absolutePath,
            guestProjectPath = project.rootfsPath,
            onProgressUpdate = onProgressUpdate
        )
    }

    @Synchronized
    override fun start() {
        if (process != null) return

        try {
            val command = prootRunner.buildProotCommand(
                command = listOf("/home/flide/flutter/bin/dart", "language-server", "--protocol=lsp"),
                workingDir = project.rootfsPath,
                asRoot = false
            )

            val pb = ProcessBuilder(command)
            val env = pb.environment()
            env.clear()
            env.putAll(prootRunner.getProotEnvironment())
            env["HOME"] = "/home/flide"
            env["USER"] = "flide"
            env["PATH"] = "/home/flide/flutter/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

            val proc = pb.start()
            process = proc
            isTerminated = false
            Log.i(tag, "Started dart language-server for project: ${project.name}")

            // Thread 1: Client -> Server pump
            thread(name = "LspClientToServerPump", isDaemon = true) {
                try {
                    val serverStdIn = proc.outputStream
                    while (!isTerminated) {
                        val message = LspMessageTranslator.readMessage(clientToServerIn) ?: break
                        val translated = translator.translateOutgoing(message)
                        LspMessageTranslator.writeMessage(serverStdIn, translated)
                    }
                } catch (e: Exception) {
                    if (!isTerminated) {
                        Log.w(tag, "Client-to-server pump ended: ${e.message}")
                    }
                }
            }

            // Thread 2: Server -> Client pump
            thread(name = "LspServerToClientPump", isDaemon = true) {
                try {
                    val serverStdOut = proc.inputStream
                    while (!isTerminated) {
                        val message = LspMessageTranslator.readMessage(serverStdOut) ?: break
                        val translated = translator.translateIncoming(message)
                        LspMessageTranslator.writeMessage(serverToClientOut, translated)
                    }
                } catch (e: Exception) {
                    if (!isTerminated) {
                        Log.w(tag, "Server-to-client pump ended: ${e.message}")
                    }
                }
            }

            // Thread 3: Monitor Stderr and Process Exit
            thread(name = "LspServerStderrMonitor", isDaemon = true) {
                try {
                    proc.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            Log.d(tag, "[dart lsp stderr] $line")
                        }
                    }
                } catch (e: Exception) {}

                val exitCode = try { proc.waitFor() } catch (e: Exception) { -1 }
                isTerminated = true
                Log.w(tag, "dart language-server exited with code $exitCode")
                if (exitCode != 0) {
                    onServerCrash?.invoke(exitCode, "Language server terminated with code $exitCode")
                }
            }

        } catch (e: Throwable) {
            Log.e(tag, "Failed starting dart language-server: ${e.message}", e)
            isTerminated = true
            throw IOException("Failed to start Dart LSP inside PRoot: ${e.message}", e)
        }
    }

    override fun getInputStream(): InputStream {
        return serverToClientIn
    }

    override fun getOutputStream(): OutputStream {
        return clientToServerOut
    }

    override fun isClosed(): Boolean {
        return isTerminated || process == null
    }

    @Synchronized
    override fun close() {
        isTerminated = true
        try {
            clientToServerOut.close()
            clientToServerIn.close()
            serverToClientOut.close()
            serverToClientIn.close()
        } catch (e: Exception) {}

        process?.let { proc ->
            try {
                proc.destroy()
            } catch (e: Exception) {
                try { proc.destroyForcibly() } catch (e2: Exception) {}
            }
        }
        process = null
        Log.i(tag, "Closed DartLspConnectionProvider")
    }
}
