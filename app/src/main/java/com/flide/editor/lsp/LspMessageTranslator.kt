package com.flide.editor.lsp

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Handles LSP JSON-RPC framing and translates file URIs and paths between
 * the host Android sandbox directory and the guest Debian rootfs mount.
 */
class LspMessageTranslator(
    val androidProjectPath: String,
    val guestProjectPath: String,
    val onProgressUpdate: ((title: String, isDone: Boolean) -> Unit)? = null
) {
    private val androidProjectUri: String = toFileUri(androidProjectPath)
    private val guestProjectUri: String = toFileUri(guestProjectPath)

    fun translateOutgoing(body: String): String {
        return body
            .replace(androidProjectUri, guestProjectUri)
            .replace(androidProjectPath, guestProjectPath)
    }

    fun translateIncoming(body: String): String {
        // Check for progress / indexing notifications
        detectProgress(body)

        return body
            .replace(guestProjectUri, androidProjectUri)
            .replace(guestProjectPath, androidProjectPath)
    }

    private fun detectProgress(body: String) {
        if (onProgressUpdate == null) return

        if (body.contains("\$/progress") || body.contains("workDoneProgress")) {
            val isEnd = body.contains("\"kind\":\"end\"")
            val isBegin = body.contains("\"kind\":\"begin\"")
            val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(body)
            val title = titleMatch?.groupValues?.get(1) ?: if (isEnd) "Ready" else "Indexing"

            if (isEnd) {
                onProgressUpdate.invoke(title, true)
            } else if (isBegin) {
                onProgressUpdate.invoke(title, false)
            }
        }
    }

    companion object {
        fun toFileUri(path: String): String {
            val cleanPath = if (path.startsWith("/")) path else "/$path"
            return "file://$cleanPath"
        }

        fun readMessage(input: InputStream): String? {
            val headerBytes = ByteArrayOutputStream()
            var state = 0
            while (true) {
                val b = input.read()
                if (b == -1) return null
                headerBytes.write(b)
                when (state) {
                    0 -> if (b == '\r'.code) state = 1 else state = 0
                    1 -> if (b == '\n'.code) state = 2 else if (b == '\r'.code) state = 1 else state = 0
                    2 -> if (b == '\r'.code) state = 3 else state = 0
                    3 -> if (b == '\n'.code) break else if (b == '\r'.code) state = 1 else state = 0
                }
            }

            val headerStr = headerBytes.toString(Charsets.US_ASCII.name())
            val contentLengthRegex = Regex("(?i)Content-Length:\\s*(\\d+)")
            val match = contentLengthRegex.find(headerStr) ?: return null
            val contentLength = match.groupValues[1].toInt()

            val bodyBytes = ByteArray(contentLength)
            var readOffset = 0
            while (readOffset < contentLength) {
                val read = input.read(bodyBytes, readOffset, contentLength - readOffset)
                if (read == -1) break
                readOffset += read
            }
            return String(bodyBytes, Charsets.UTF_8)
        }

        fun writeMessage(output: OutputStream, body: String) {
            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            val header = "Content-Length: ${bodyBytes.size}\r\n\r\n"
            output.write(header.toByteArray(Charsets.US_ASCII))
            output.write(bodyBytes)
            output.flush()
        }
    }
}
