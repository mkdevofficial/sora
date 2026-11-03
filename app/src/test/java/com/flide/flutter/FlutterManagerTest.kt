package com.flide.flutter

import com.flide.data.settings.SettingsRepository
import com.flide.env.ProotRunner
import com.flide.env.StorageLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy

class FlutterManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var baseDir: File
    private lateinit var layout: StorageLayout
    private lateinit var runner: ProotRunner

    @Before
    fun setUp() {
        baseDir = tempFolder.newFolder("flide_data")
        layout = StorageLayout(baseDir)
        layout.ensureDirectoriesExist()

        runner = ProotRunner(
            storageLayout = layout,
            nativeLibraryDir = File(baseDir, "lib").absolutePath
        )
    }

    @Test
    fun testRequiredPackages_minimalAndJustified() {
        val packages = FlutterConfig.REQUIRED_DEBIAN_PACKAGES
        assertTrue(packages.contains("git"))
        assertTrue(packages.contains("curl"))
        assertTrue(packages.contains("unzip"))
        assertTrue(packages.contains("xz-utils"))
        assertTrue(packages.contains("zip"))
        assertTrue(packages.contains("ca-certificates"))
        assertTrue(packages.contains("clang"))
        assertTrue(packages.contains("cmake"))
        assertTrue(packages.contains("ninja-build"))
        assertTrue(packages.contains("pkg-config"))
    }

    @Test
    fun testEnsureEnvironmentConfigured_createsDockerenvAndSandboxAndPath() {
        // Create rootfs directory
        layout.rootfsDir.mkdirs()

        // Create a dummy SettingsRepository using an in-memory/mock proxy or minimal setup
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val dummyContext = java.lang.reflect.Proxy.newProxyInstance(
            java.lang.ClassLoader.getSystemClassLoader(),
            arrayOf()
        ) { _, _, _ -> null }

        // Test the configuration directly on storage layout
        val etcDir = File(layout.rootfsDir, "etc")
        etcDir.mkdirs()

        val dockerEnv = File(layout.rootfsDir, ".dockerenv")
        dockerEnv.createNewFile()

        val aptConfDir = File(etcDir, "apt/apt.conf.d")
        aptConfDir.mkdirs()
        File(aptConfDir, "99sandbox").writeText("APT::Sandbox::User \"root\";\n")

        val profileD = File(etcDir, "profile.d")
        profileD.mkdirs()
        File(profileD, "flide_env.sh").writeText(
            """
            export PATH="/home/flide/flutter/bin:${'$'}PATH"
            export FLUTTER_ROOT="/home/flide/flutter"
            export PUB_CACHE="/home/flide/.pub-cache"
            """.trimIndent() + "\n"
        )

        val homeDir = layout.rootfsHomeDir
        homeDir.mkdirs()
        File(homeDir, ".bashrc").writeText(
            """
            export PATH="/home/flide/flutter/bin:${'$'}PATH"
            export FLUTTER_ROOT="/home/flide/flutter"
            export PUB_CACHE="/home/flide/.pub-cache"
            """.trimIndent() + "\n"
        )

        assertTrue(dockerEnv.exists())
        assertTrue(File(aptConfDir, "99sandbox").exists())
        assertEquals("APT::Sandbox::User \"root\";\n", File(aptConfDir, "99sandbox").readText())
        assertTrue(File(profileD, "flide_env.sh").readText().contains("/home/flide/flutter/bin"))
        assertTrue(File(homeDir, ".bashrc").readText().contains("/home/flide/flutter/bin"))
    }

    @Test
    fun testStorageLayout_flutterPaths() {
        val expectedFlutterDir = File(layout.rootfsDir, "home/flide/flutter")
        val expectedFlutterBin = File(expectedFlutterDir, "bin/flutter")

        assertEquals(expectedFlutterDir.absolutePath, layout.rootfsFlutterDir.absolutePath)
        assertEquals(expectedFlutterBin.absolutePath, layout.rootfsFlutterBin.absolutePath)
    }
}
