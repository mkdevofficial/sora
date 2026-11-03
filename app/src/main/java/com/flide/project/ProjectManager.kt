package com.flide.project

import android.util.Log
import com.flide.env.ProotRunner
import com.flide.env.StorageLayout
import com.flide.flutter.FlutterConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Manages Flutter project discovery, creation, deletion, and selection.
 */
class ProjectManager(
    private val storageLayout: StorageLayout,
    private val prootRunner: ProotRunner,
    private val coroutineScope: CoroutineScope
) {

    private val _projects = MutableStateFlow<List<FlutterProject>>(emptyList())
    val projects: StateFlow<List<FlutterProject>> = _projects.asStateFlow()

    private val _activeProject = MutableStateFlow<FlutterProject?>(null)
    val activeProject: StateFlow<FlutterProject?> = _activeProject.asStateFlow()

    init {
        scanProjects()
    }

    /**
     * Scans the host projects directory and refreshes the projects list.
     */
    fun scanProjects() {
        coroutineScope.launch(Dispatchers.IO) {
            val list = mutableListOf<FlutterProject>()
            val dir = storageLayout.projectsDir
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        val pubspec = File(file, "pubspec.yaml")
                        if (pubspec.exists()) {
                            val parsed = parsePubspec(file.name, file, pubspec)
                            list.add(parsed)
                        }
                    }
                }
            }
            list.sortByDescending { it.lastModified }
            _projects.value = list

            val currentActive = _activeProject.value
            if (currentActive != null) {
                _activeProject.value = list.find { it.name == currentActive.name }
            }
        }
    }

    /**
     * Validates a candidate Flutter project name against Dart identifier conventions.
     */
    fun validateProjectName(name: String): String? {
        if (name.isBlank()) {
            return "Project name cannot be empty."
        }
        val trimmed = name.trim()
        if (!Regex("^[a-z][a-z0-9_]*$").matches(trimmed)) {
            return "Project name must begin with a lowercase letter and contain only lowercase letters, digits, and underscores."
        }
        val reservedKeywords = setOf(
            "assert", "break", "case", "catch", "class", "const", "continue", "default",
            "do", "else", "enum", "extends", "false", "final", "finally", "for", "if",
            "in", "is", "new", "null", "rethrow", "return", "super", "switch", "this",
            "throw", "true", "try", "var", "void", "while", "with", "flutter", "dart"
        )
        if (reservedKeywords.contains(trimmed.lowercase(Locale.US))) {
            return "'$trimmed' is a reserved keyword in Dart/Flutter."
        }
        val targetDir = File(storageLayout.projectsDir, trimmed)
        if (targetDir.exists()) {
            return "A project with name '$trimmed' already exists."
        }
        return null
    }

    /**
     * Creates a new Flutter project by executing `flutter create` inside PRoot.
     */
    suspend fun createProject(
        name: String,
        org: String? = null,
        onOutput: (String) -> Unit = {}
    ): Result<FlutterProject> = withContext(Dispatchers.IO) {
        val validationError = validateProjectName(name)
        if (validationError != null) {
            return@withContext Result.failure(IllegalArgumentException(validationError))
        }

        val flutterBin = storageLayout.rootfsFlutterBin
        if (!flutterBin.exists()) {
            return@withContext Result.failure(IllegalStateException("Flutter SDK is not installed in rootfs."))
        }

        val projectDirInRootfs = "/home/flide/projects/$name"
        val cmd = mutableListOf(
            FlutterConfig.FLUTTER_BIN_IN_ROOTFS,
            "create",
            "--project-name", name
        )
        if (!org.isNullOrBlank()) {
            cmd.add("--org")
            cmd.add(org.trim())
        }
        cmd.add(projectDirInRootfs)

        onOutput("Creating Flutter project '$name'...")
        onOutput("$ ${cmd.joinToString(" ")}")

        val exitCode = prootRunner.execute(
            command = cmd,
            workingDir = "/home/flide/projects",
            asRoot = true,
            onOutput = onOutput
        )

        if (exitCode != 0) {
            val err = "flutter create failed with exit code $exitCode"
            onOutput("[x] $err")
            return@withContext Result.failure(RuntimeException(err))
        }

        onOutput("[✓] Flutter project created successfully!")
        val hostProjectDir = File(storageLayout.projectsDir, name)
        val pubspec = File(hostProjectDir, "pubspec.yaml")
        val createdProject = parsePubspec(name, hostProjectDir, pubspec)

        scanProjects()
        _activeProject.value = createdProject
        Result.success(createdProject)
    }

    /**
     * Deletes a project from the host filesystem.
     */
    fun deleteProject(project: FlutterProject): Boolean {
        val target = project.rootDir
        val deleted = target.deleteRecursively()
        if (deleted) {
            if (_activeProject.value?.name == project.name) {
                _activeProject.value = null
            }
            scanProjects()
        }
        return deleted
    }

    fun selectProject(project: FlutterProject?) {
        _activeProject.value = project
    }

    private fun parsePubspec(name: String, rootDir: File, pubspec: File): FlutterProject {
        var description = ""
        var version = "1.0.0"
        var lastModified = rootDir.lastModified()

        try {
            if (pubspec.exists()) {
                lastModified = pubspec.lastModified()
                pubspec.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("description:")) {
                        description = trimmed.substringAfter("description:").trim().trim('"', '\'')
                    } else if (trimmed.startsWith("version:")) {
                        version = trimmed.substringAfter("version:").trim().trim('"', '\'')
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading pubspec for $name: ${e.message}")
        }

        return FlutterProject(
            name = name,
            rootDir = rootDir,
            rootfsPath = "/home/flide/projects/$name",
            description = description,
            version = version,
            lastModified = lastModified
        )
    }

    companion object {
        private const val TAG = "ProjectManager"
    }
}
