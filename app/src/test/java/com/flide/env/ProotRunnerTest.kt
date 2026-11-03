package com.flide.env

import android.content.Context
import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProotRunnerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var baseDir: File
    private lateinit var layout: StorageLayout

    @Before
    fun setUp() {
        baseDir = tempFolder.newFolder("test_app_files")
        layout = StorageLayout(baseDir)
        layout.ensureDirectoriesExist()
    }

    @Test
    fun testProotCommandConstruction() {
        // Create mock proot binary
        val prootBin = layout.prootBin
        prootBin.createNewFile()
        prootBin.setExecutable(true)

        // Mock context without android runtime
        val runner = object : ProotRunner(
            context = createDummyContext(),
            storageLayout = layout
        ) {}

        val cmd = runner.buildProotCommand(
            command = listOf("/bin/bash", "-c", "echo hello"),
            workingDir = "/home/flide",
            asRoot = true
        )

        assertTrue(cmd.contains("-0"))
        assertTrue(cmd.contains("--link2symlink"))
        assertTrue(cmd.contains("-r"))
        assertTrue(cmd.contains(layout.rootfsDir.absolutePath))
        assertTrue(cmd.contains("/dev"))
        assertTrue(cmd.contains("/proc"))
        assertTrue(cmd.contains("/sys"))
        assertTrue(cmd.contains("${layout.projectsDir.absolutePath}:/home/flide/projects"))
        assertTrue(cmd.contains("-w"))
        assertTrue(cmd.contains("/home/flide"))
        assertTrue(cmd.contains("/bin/bash"))
        assertTrue(cmd.contains("echo hello"))
    }

    @Test
    fun testProotEnvironmentVariables() {
        val runner = object : ProotRunner(
            context = createDummyContext(),
            storageLayout = layout
        ) {}

        val env = runner.getProotEnvironment()

        assertEquals("/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin", env["PATH"])
        assertEquals("/home/flide", env["HOME"])
        assertEquals("flide", env["USER"])
        assertEquals("xterm-256color", env["TERM"])
        assertEquals("C.UTF-8", env["LANG"])
        assertEquals("/tmp", env["TMPDIR"])
        assertTrue(env.containsKey("LD_LIBRARY_PATH"))
    }

    private fun createDummyContext(): Context {
        // Lightweight Context proxy for unit testing nativeLibraryDir
        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            Context::class.java.classLoader,
            arrayOf(Context::class.java)
        ) { _, method, _ ->
            if (method.name == "getApplicationInfo") {
                ApplicationInfo().apply {
                    nativeLibraryDir = File(baseDir, "lib").absolutePath
                }
            } else {
                null
            }
        }
        return proxy as Context
    }
}
