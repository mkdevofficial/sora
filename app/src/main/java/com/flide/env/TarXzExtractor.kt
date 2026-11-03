package com.flide.env

import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZCompressorInputStream
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

        val totalArchiveBytes = archiveFile.length()
        var extractedCount = 0
        var lastLogTime = System.currentTimeMillis()

        FileInputStream(archiveFile).use { fileIn ->
            BufferedInputStream(fileIn, 64 * 1024).use { bufferedIn ->
                XZCompressorInputStream(bufferedIn).use { xzIn ->
                    TarArchiveInputStream(xzIn).use { tarIn ->
                        var entry: TarArchiveEntry? = tarIn.nextTarEntry

                        while (entry != null) {
                            coroutineContext.ensureActive()

                            val destFile = File(targetDir, entry.name)

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
                                onProgress(0.5f, entry.name)
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

    private fun createSymlink(linkFile: File, targetPath: String) {
        linkFile.parentFile?.mkdirs()
        if (linkFile.exists() || isBrokenSymlink(linkFile)) {
            linkFile.delete()
        }
        try {
            Os.symlink(targetPath, linkFile.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create symlink ${linkFile.absolutePath} -> $targetPath", e)
        }
    }

    private fun isBrokenSymlink(file: File): Boolean {
        return try {
            Os.lstat(file.absolutePath)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun applyPermissions(file: File, mode: Int) {
        if (mode <= 0) return
        try {
            // Apply lowest 9 bits (Unix rwxrwxrwx permissions)
            val unixPerms = mode and 0x1FF
            Os.chmod(file.absolutePath, unixPerms)
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting chmod on ${file.name}", e)
        }
    }
}
