package com.mokhtarxz.sora.project

import java.io.File

/**
 * Represents a Flutter project hosted in [rootDir] on the Android filesystem,
 * mapped to [rootfsPath] inside the Debian PRoot environment.
 */
data class FlutterProject(
    val name: String,
    val rootDir: File,
    val rootfsPath: String = "/home/flide/projects/$name",
    val description: String = "",
    val version: String = "1.0.0",
    val lastModified: Long = System.currentTimeMillis()
) {
    val mainDartFile: File
        get() = File(rootDir, "lib/main.dart")

    val pubspecFile: File
        get() = File(rootDir, "pubspec.yaml")

    val libDir: File
        get() = File(rootDir, "lib")

    val isValid: Boolean
        get() = pubspecFile.exists()
}
