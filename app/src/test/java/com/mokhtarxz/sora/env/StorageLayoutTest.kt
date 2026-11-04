package com.mokhtarxz.sora.env

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageLayoutTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var baseDir: File
    private lateinit var layout: StorageLayout

    @Before
    fun setUp() {
        baseDir = tempFolder.newFolder("test_app_files")
        layout = StorageLayout(baseDir)
    }

    @Test
    fun testDirectoryPaths() {
        assertEquals(File(baseDir, "rootfs").absolutePath, layout.rootfsDir.absolutePath)
        assertEquals(File(baseDir, "downloads").absolutePath, layout.downloadsDir.absolutePath)
        assertEquals(File(baseDir, "projects").absolutePath, layout.projectsDir.absolutePath)
        assertEquals(File(baseDir, "logs").absolutePath, layout.logsDir.absolutePath)
        assertEquals(File(baseDir, "bin").absolutePath, layout.binDir.absolutePath)
        assertEquals(File(baseDir, "bin/lib").absolutePath, layout.libDir.absolutePath)
        assertEquals(File(baseDir, "bin/proot").absolutePath, layout.prootBin.absolutePath)
        assertEquals(File(baseDir, "bin/loader").absolutePath, layout.loaderBin.absolutePath)
        assertEquals(File(baseDir, "tmp").absolutePath, layout.tmpDir.absolutePath)
    }

    @Test
    fun testEnsureDirectoriesExist() {
        val success = layout.ensureDirectoriesExist()
        assertTrue(success)
        assertTrue(layout.rootfsDir.exists() && layout.rootfsDir.isDirectory)
        assertTrue(layout.downloadsDir.exists() && layout.downloadsDir.isDirectory)
        assertTrue(layout.projectsDir.exists() && layout.projectsDir.isDirectory)
        assertTrue(layout.logsDir.exists() && layout.logsDir.isDirectory)
        assertTrue(layout.binDir.exists() && layout.binDir.isDirectory)
        assertTrue(layout.libDir.exists() && layout.libDir.isDirectory)
        assertTrue(layout.tmpDir.exists() && layout.tmpDir.isDirectory)
    }

    @Test
    fun testRootfsSubPaths() {
        assertEquals(
            File(layout.rootfsDir, "home/flide").absolutePath,
            layout.rootfsHomeDir.absolutePath
        )
        assertEquals(
            File(layout.rootfsDir, "home/flide/flutter/bin/flutter").absolutePath,
            layout.rootfsFlutterBin.absolutePath
        )
    }

    @Test
    fun testProjectRelativePath() {
        val projectFolder = File(layout.projectsDir, "my_counter_app/lib/main.dart")
        val relative = layout.getProjectRelativePath(projectFolder)
        assertEquals("my_counter_app/lib/main.dart", relative)
    }
}
