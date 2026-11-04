package com.mokhtarxz.sora.github

import com.mokhtarxz.sora.env.ProotRunner
import com.mokhtarxz.sora.env.StorageLayout
import com.mokhtarxz.sora.git.GitManager
import com.mokhtarxz.sora.project.FlutterProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CloudBuildManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var baseDir: File
    private lateinit var storageLayout: StorageLayout
    private lateinit var cloudBuildManager: CloudBuildManager
    private lateinit var project: FlutterProject

    @Before
    fun setUp() {
        baseDir = tempFolder.newFolder("flide_data")
        storageLayout = StorageLayout(baseDir)
        storageLayout.ensureDirectoriesExist()

        val projectDir = File(storageLayout.projectsDir, "test_app")
        projectDir.mkdirs()
        File(projectDir, "pubspec.yaml").writeText("name: test_app\n")

        project = FlutterProject(
            name = "test_app",
            rootDir = projectDir,
            rootfsPath = "/home/flide/projects/test_app"
        )

        val runner = ProotRunner(storageLayout)
        val git = GitManager(runner)

        cloudBuildManager = CloudBuildManager(
            storageLayout = storageLayout,
            gitManager = git,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
    }

    @Test
    fun testGenerateWorkflowFile_generatesValidGitHubActionsYaml() {
        val workflowFile = cloudBuildManager.generateWorkflowFile(project)

        assertTrue(workflowFile.exists())
        val content = workflowFile.readText()

        assertTrue(content.contains("name: Build Flutter APK"))
        assertTrue(content.contains("workflow_dispatch:"))
        assertTrue(content.contains("actions/checkout@v4"))
        assertTrue(content.contains("java-version: '17'"))
        assertTrue(content.contains("subosito/flutter-action@v2"))
        assertTrue(content.contains("flutter build apk --release --split-per-abi"))
        assertTrue(content.contains("actions/upload-artifact@v4"))
        assertTrue(content.contains("app-arm64-v8a-release.apk"))
    }
}
