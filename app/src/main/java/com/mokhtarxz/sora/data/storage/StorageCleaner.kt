package com.mokhtarxz.sora.data.storage

import android.util.Log
import com.mokhtarxz.sora.env.ProotRunner
import com.mokhtarxz.sora.env.StorageLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages cache cleanup: Flutter pub cache, project build outputs, and downloaded archives.
 */
class StorageCleaner(
    private val storageLayout: StorageLayout,
    private val prootRunner: ProotRunner
) {
    private val tag = "StorageCleaner"

    data class StorageUsage(
        val pubCacheBytes: Long,
        val downloadsBytes: Long,
        val projectBuildsBytes: Long,
        val totalCleanableBytes: Long
    )

    suspend fun calculateCleanableStorage(): StorageUsage = withContext(Dispatchers.IO) {
        val pubCacheDir = File(storageLayout.rootfsHomeDir, ".pub-cache")
        val pubCacheSize = getFolderSize(pubCacheDir)
        val downloadsSize = getFolderSize(storageLayout.downloadsDir)

        var buildsSize = 0L
        if (storageLayout.projectsDir.exists()) {
            storageLayout.projectsDir.listFiles()?.forEach { projectDir ->
                val buildDir = File(projectDir, "build")
                if (buildDir.exists()) {
                    buildsSize += getFolderSize(buildDir)
                }
            }
        }

        StorageUsage(
            pubCacheBytes = pubCacheSize,
            downloadsBytes = downloadsSize,
            projectBuildsBytes = buildsSize,
            totalCleanableBytes = pubCacheSize + downloadsSize + buildsSize
        )
    }

    suspend fun cleanDownloads(): Long = withContext(Dispatchers.IO) {
        val size = getFolderSize(storageLayout.downloadsDir)
        storageLayout.downloadsDir.listFiles()?.forEach { file ->
            // Keep rootfs archive if present, remove temp apk/zip files
            if (file.name.endsWith(".zip") || file.name.endsWith(".apk")) {
                file.deleteRecursively()
            }
        }
        size
    }

    suspend fun cleanProjectBuilds(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        if (storageLayout.projectsDir.exists()) {
            storageLayout.projectsDir.listFiles()?.forEach { projectDir ->
                val buildDir = File(projectDir, "build")
                if (buildDir.exists()) {
                    freed += getFolderSize(buildDir)
                    buildDir.deleteRecursively()
                }
            }
        }
        freed
    }

    suspend fun cleanPubCache(): Long = withContext(Dispatchers.IO) {
        val pubCacheDir = File(storageLayout.rootfsHomeDir, ".pub-cache")
        val size = getFolderSize(pubCacheDir)
        if (pubCacheDir.exists()) {
            val (code, _) = prootRunner.executeCapture(
                listOf("flutter", "pub", "cache", "clean", "--force"),
                workingDir = "/home/flide"
            )
            if (code != 0) {
                // Fallback delete
                pubCacheDir.deleteRecursively()
                pubCacheDir.mkdirs()
            }
        }
        size
    }

    private fun getFolderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var bytes = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                bytes += file.length()
            }
        }
        return bytes
    }
}
