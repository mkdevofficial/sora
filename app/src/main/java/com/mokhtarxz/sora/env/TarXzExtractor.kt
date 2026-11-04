package com.mokhtarxz.sora.env

import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

object TarXzExtractor {

    private const val TAG = "TarXzExtractor"

    suspend fun extract(
        archiveFile: File,
        targetDir: File,
        onProgress: (progress: Float, entryName: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        var extractedCount = 0
        var lastLogTime = System.currentTimeMillis()

        FileInputStream(archiveFile).use { fileIn ->
            BufferedInputStream(fileIn, 64 * 1024).use { bufferedIn ->
                XZCompressorInputStream(bufferedIn).use { xzIn ->
                    TarArchiveInputStream(xzIn).use { tarIn ->
                        var entry: TarArchiveEntry? = tarIn.nextTarEntry

                        while (entry != null) {
                            coroutineContext.ensureActive()

                            val relativePath = stripLeadingComponent(entry.name)
                            if (relativePath == null) {
                                // Top-level container directory entry, skip
                                entry = tarIn.nextTarEntry
                                continue
                            }

                            val destFile = File(targetDir, relativePath)

                            // Security check: path traversal prevention
                            val canonicalDest = destFile.canonicalPath
                            if (!canonicalDest.startsWith(targetDir.canonicalPath)) {
                                Log.w(TAG, "Skipping suspicious entry outside target dir: ${entry.name}")
                                entry = tarIn.nextTarEntry
                                continue
                            }

                            when {
                                entry.isDirectory -> {
                                    destFile.mkdirs()
                                    applyPermissions(destFile, entry.mode)
                                }
                                entry.isSymbolicLink -> {
                                    createSymlink(destFile, entry.linkName)
                                }
                                entry.isLink -> {
                                    createHardLink(targetDir, destFile, entry.linkName)
                                }
                                entry.isFile -> {
                                    destFile.parentFile?.mkdirs()
                                    FileOutputStream(destFile).use { out ->
                                        tarIn.copyTo(out, 32 * 1024)
                                    }
                                    applyPermissions(destFile, entry.mode)
                                }
                                else -> {
                                    // Other types (character/block devices, FIFO) ignored in userland proot
                                    Log.d(TAG, "Ignoring entry type for ${entry.name}")
                                }
                            }

                            extractedCount++
                            val now = System.currentTimeMillis()
                            if (now - lastLogTime >= 250) {
                                onProgress(0.5f, relativePath)
                                lastLogTime = now
                            }

                            entry = tarIn.nextTarEntry
                        }
                    }
                }
            }
        }
        onProgress(1.0f, "Completed")
    }

    /**
     * Strips top-level root folder prefix (e.g. 'debian-bookworm-aarch64/bin' -> 'bin')
     * so that files extract directly into the target rootfs directory.
     */
    fun stripLeadingComponent(rawName: String): String? {
        val clean = rawName.removePrefix("./").trimStart('/')
        if (clean.isEmpty()) return null

        val firstSlash = clean.indexOf('/')
        return if (firstSlash == -1) {
            // It's a top-level file or directory at the archive root
            if (clean.startsWith("debian-")) null else clean
        } else {
            val topDir = clean.substring(0, firstSlash)
            if (topDir.startsWith("debian-")) {
                val sub = clean.substring(firstSlash + 1).trimStart('/')
                sub.ifEmpty { null }
            } else {
                clean
            }
        }
    }

    private fun createSymlink(linkFile: File, targetPath: String) {
        linkFile.parentFile?.mkdirs()
        try {
            Os.remove(linkFile.absolutePath)
        } catch (_: Throwable) {
            if (linkFile.isDirectory) {
                linkFile.deleteRecursively()
            } else {
                linkFile.delete()
            }
        }
        try {
            Os.symlink(targetPath, linkFile.absolutePath)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to create symlink ${linkFile.absolutePath} -> $targetPath", e)
        }
    }

    private fun createHardLink(targetDir: File, linkFile: File, linkName: String) {
        val srcRelative = stripLeadingComponent(linkName) ?: linkName
        val srcFile = File(targetDir, srcRelative)
        linkFile.parentFile?.mkdirs()
        try {
            Os.remove(linkFile.absolutePath)
        } catch (_: Throwable) {
            if (linkFile.isDirectory) {
                linkFile.deleteRecursively()
            } else {
                linkFile.delete()
            }
        }
        try {
            Os.link(srcFile.absolutePath, linkFile.absolutePath)
        } catch (_: Throwable) {
            try {
                srcFile.copyTo(linkFile, overwrite = true)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to create hard link for ${linkFile.name}: ${e.message}")
            }
        }
    }

    private fun applyPermissions(file: File, mode: Int) {
        if (mode <= 0) return
        try {
            var unixPerms = mode and 0x1FF
            if (file.isDirectory) {
                // Ensure owner read/write/execute (0700) so contents can be extracted
                unixPerms = unixPerms or 0x1C0
            }
            Os.chmod(file.absolutePath, unixPerms)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed setting chmod on ${file.name}", e)
        }
    }
}
