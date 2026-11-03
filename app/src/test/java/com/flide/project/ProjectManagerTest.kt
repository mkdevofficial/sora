package com.flide.project

import com.flide.env.ProotRunner
import com.flide.env.StorageLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var baseDir: File
    private lateinit var layout: StorageLayout
    private lateinit var runner: ProotRunner
    private lateinit var projectManager: ProjectManager

    @Before
    fun setUp() {
        baseDir = tempFolder.newFolder("flide_data")
        layout = StorageLayout(baseDir)
        layout.ensureDirectoriesExist()

        runner = ProotRunner(layout)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        projectManager = ProjectManager(
            storageLayout = layout,
            prootRunner = runner,
            coroutineScope = scope,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun testValidateProjectName_validNames() {
        assertNull(projectManager.validateProjectName("counter_app"))
        assertNull(projectManager.validateProjectName("my_project_123"))
        assertNull(projectManager.validateProjectName("testapp"))
    }

    @Test
    fun testValidateProjectName_invalidFormats() {
        assertNotNull(projectManager.validateProjectName(""))
        assertNotNull(projectManager.validateProjectName("   "))
        assertNotNull(projectManager.validateProjectName("CounterApp")) // Uppercase not allowed
        assertNotNull(projectManager.validateProjectName("my-app")) // Hyphen not allowed
        assertNotNull(projectManager.validateProjectName("123app")) // Must start with letter
        assertNotNull(projectManager.validateProjectName("app with spaces"))
    }

    @Test
    fun testValidateProjectName_reservedDartKeywords() {
        assertNotNull(projectManager.validateProjectName("do"))
        assertNotNull(projectManager.validateProjectName("default"))
        assertNotNull(projectManager.validateProjectName("class"))
        assertNotNull(projectManager.validateProjectName("flutter"))
        assertNotNull(projectManager.validateProjectName("dart"))
    }

    @Test
    fun testScanProjects_detectsExistingProjectsAndPubspec() {
        val projDir = File(layout.projectsDir, "my_counter")
        projDir.mkdirs()

        val pubspec = File(projDir, "pubspec.yaml")
        pubspec.writeText(
            """
            name: my_counter
            description: "A counter demo project"
            version: 1.2.3+4
            environment:
              sdk: ^3.0.0
            """.trimIndent()
        )

        val mainDart = File(projDir, "lib/main.dart")
        mainDart.parentFile?.mkdirs()
        mainDart.writeText("void main() {}")

        projectManager.scanProjectsSync()

        val list = projectManager.projects.value
        assertEquals(1, list.size)
        val proj = list.first()
        assertEquals("my_counter", proj.name)
        assertEquals("A counter demo project", proj.description)
        assertEquals("1.2.3+4", proj.version)
        assertTrue(proj.isValid)
        assertTrue(proj.mainDartFile.exists())
    }

    @Test
    fun testDeleteProject_removesFilesAndUpdatesList() {
        val projDir = File(layout.projectsDir, "to_delete")
        projDir.mkdirs()
        File(projDir, "pubspec.yaml").writeText("name: to_delete\n")

        projectManager.scanProjectsSync()
        assertEquals(1, projectManager.projects.value.size)

        val proj = projectManager.projects.value.first()
        val deleted = projectManager.deleteProject(proj)
        assertTrue(deleted)
        assertTrue(!projDir.exists())
        assertEquals(0, projectManager.projects.value.size)
    }
}
