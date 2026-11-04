package com.mokhtarxz.sora.terminal

import com.mokhtarxz.sora.env.ProotRunner
import com.mokhtarxz.sora.env.StorageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FlideTerminalManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var baseDir: File
    private lateinit var layout: StorageLayout
    private lateinit var runner: ProotRunner
    private lateinit var terminalManager: FlideTerminalManager

    @Before
    fun setUp() {
        baseDir = tempFolder.newFolder("flide_data")
        layout = StorageLayout(baseDir)
        layout.ensureDirectoriesExist()

        runner = ProotRunner(
            storageLayout = layout,
            nativeLibraryDir = File(baseDir, "lib").absolutePath
        )

        terminalManager = FlideTerminalManager(layout, runner)
    }

    @Test
    fun testBuildSessionArgs_structureAndPtyRequirements() {
        val args = terminalManager.buildSessionArgs(
            shell = "/bin/bash",
            workingDir = "/home/flide",
            asRoot = true
        )

        // For execvp in termux pty subprocess, argv[0] must be the binary name
        assertEquals("proot", args[0])

        val argsList = args.toList()
        assertTrue(argsList.contains("-0"))
        assertTrue(argsList.contains("--link2symlink"))
        assertTrue(argsList.contains("-r"))
        assertTrue(argsList.contains(layout.rootfsDir.absolutePath))
        assertTrue(argsList.contains("/dev"))
        assertTrue(argsList.contains("/proc"))
        assertTrue(argsList.contains("/sys"))
        assertTrue(argsList.contains("${layout.tmpDir.absolutePath}:/tmp"))
        assertTrue(argsList.contains("${layout.projectsDir.absolutePath}:/home/flide/projects"))
        assertTrue(argsList.contains("-w"))
        assertTrue(argsList.contains("/home/flide"))
        assertTrue(argsList.contains("/bin/bash"))
        assertTrue(argsList.contains("-l"))
    }
}
