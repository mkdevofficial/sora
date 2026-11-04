package com.mokhtarxz.sora.env

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TarXzExtractorTest {

    @Test
    fun testStripLeadingComponent_debianTarballPrefix() {
        // Root directory entry should be skipped (returns null)
        assertNull(TarXzExtractor.stripLeadingComponent("debian-bookworm-aarch64"))
        assertNull(TarXzExtractor.stripLeadingComponent("debian-bookworm-aarch64/"))
        assertNull(TarXzExtractor.stripLeadingComponent("./debian-bookworm-aarch64"))
        assertNull(TarXzExtractor.stripLeadingComponent("./debian-bookworm-aarch64/"))

        // Subdirectories and files under debian-* prefix
        assertEquals("bin/sh", TarXzExtractor.stripLeadingComponent("debian-bookworm-aarch64/bin/sh"))
        assertEquals("bin/sh", TarXzExtractor.stripLeadingComponent("./debian-bookworm-aarch64/bin/sh"))
        assertEquals("usr/bin/dash", TarXzExtractor.stripLeadingComponent("debian-bookworm-aarch64/usr/bin/dash"))
        assertEquals("etc/resolv.conf", TarXzExtractor.stripLeadingComponent("debian-bookworm-aarch64/etc/resolv.conf"))
        assertEquals("var/log", TarXzExtractor.stripLeadingComponent("debian-bookworm-aarch64/var/log"))
    }

    @Test
    fun testStripLeadingComponent_flatTarball() {
        // Tarballs without debian-* container directory should preserve relative paths
        assertEquals("bin/sh", TarXzExtractor.stripLeadingComponent("bin/sh"))
        assertEquals("bin/sh", TarXzExtractor.stripLeadingComponent("./bin/sh"))
        assertEquals("usr/bin/dash", TarXzExtractor.stripLeadingComponent("usr/bin/dash"))
        assertEquals("etc/issue", TarXzExtractor.stripLeadingComponent("etc/issue"))
        assertNull(TarXzExtractor.stripLeadingComponent(""))
        assertNull(TarXzExtractor.stripLeadingComponent("./"))
    }
}
