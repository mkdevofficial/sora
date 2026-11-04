package com.flide.project

import com.flide.env.StorageLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manages exporting and importing Flutter projects as ZIP archives.
 */
class ProjectBackupManager(private val storageLayout: StorageLayout) {

    suspend fun exportProjectToZip(project: FlutterProject, destZip: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            destZip.parentFile?.mkdirs()
            ZipOutputStream(FileOutputStream(destZip)).use { zos ->
                project.rootDir.walkTopDown()
                    .onEnter { dir ->
                        val name = dir.name
                        name != "build" && name != ".dart_tool" && name != ".git" && !name.startsWith(".")
                    }
                    .forEach { file ->
                        val relativePath = file.relativeTo(project.rootDir).path
                        if (file.isFile) {
                            zos.putNextEntry(ZipEntry(relativePath))
                            file.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
            }
            Result.success(destZip)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importProjectFromZip(zipFile: File, projectName: String): Result<FlutterProject> = withContext(Dispatchers.IO) {
        try {
            val destDir = File(storageLayout.projectsDir, projectName)
            destDir.mkdirs()

            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val targetFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { out ->
                            zis.copyTo(out)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val project = FlutterProject(
                name = projectName,
                rootDir = destDir,
                rootfsPath = "/home/flide/projects/$projectName"
            )
            Result.success(project)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
