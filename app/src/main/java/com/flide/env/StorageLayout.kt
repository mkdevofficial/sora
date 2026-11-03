package com.flide.env

import java.io.File

/**
 * Manages the canonical storage layout for the Flide environment.
 *
 * Layout under [baseDir] (usually Context.filesDir):
 * - rootfs/   : The extracted Debian arm64 root filesystem
 * - downloads/: Cached rootfs tarballs, tools, and temporary download files
 * - projects/ : Flutter projects directory (accessible to both Android editor and rootfs)
 * - logs/     : Persistent operation, service, and build log files
 */
class StorageLayout(val baseDir: File) {

    val rootfsDir: File = File(baseDir, "rootfs")
    val downloadsDir: File = File(baseDir, "downloads")
    val projectsDir: File = File(baseDir, "projects")
    val logsDir: File = File(baseDir, "logs")

    /**
     * Path inside rootfs for the default non-root user 'flide'.
     */
    val rootfsHomeDir: File
        get() = File(rootfsDir, "home/flide")

    /**
     * Project directory path inside rootfs (/home/flide/projects).
     */
    val rootfsProjectsDir: File
        get() = File(rootfsHomeDir, "projects")

    /**
     * Flutter SDK installation directory inside rootfs.
     */
    val rootfsFlutterDir: File
        get() = File(rootfsHomeDir, "flutter")

    /**
     * Flutter executable binary inside rootfs.
     */
    val rootfsFlutterBin: File
        get() = File(rootfsFlutterDir, "bin/flutter")

    /**
     * Ensures all required host-side storage directories exist.
     */
    fun ensureDirectoriesExist(): Boolean {
        return (rootfsDir.exists() || rootfsDir.mkdirs()) &&
                (downloadsDir.exists() || downloadsDir.mkdirs()) &&
                (projectsDir.exists() || projectsDir.mkdirs()) &&
                (logsDir.exists() || logsDir.mkdirs())
    }

    /**
     * Returns available storage space on the partition hosting [baseDir] in bytes.
     */
    fun getAvailableStorageBytes(): Long {
        return baseDir.usableSpace
    }

    /**
     * Returns total storage space on the partition hosting [baseDir] in bytes.
     */
    fun getTotalStorageBytes(): Long {
        return baseDir.totalSpace
    }

    /**
     * Maps an Android project file to its relative path from the projects directory.
     */
    fun getProjectRelativePath(projectFile: File): String {
        return projectFile.relativeTo(projectsDir).path
    }
}
