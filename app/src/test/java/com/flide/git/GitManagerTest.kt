package com.flide.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitManagerTest {

    @Test
    fun testParseGitStatusOutput_cleanRepo() {
        val output = "## main...origin/main"
        val status = GitManager.parseGitStatusOutput(output)

        assertEquals("main", status.branch)
        assertTrue(status.isClean)
        assertTrue(status.stagedFiles.isEmpty())
        assertTrue(status.unstagedFiles.isEmpty())
        assertTrue(status.untrackedFiles.isEmpty())
    }

    @Test
    fun testParseGitStatusOutput_noCommitsYetBranch() {
        val output = "## No commits yet on master"
        val status = GitManager.parseGitStatusOutput(output)

        assertEquals("master", status.branch)
        assertTrue(status.isClean)
    }

    @Test
    fun testParseGitStatusOutput_stagedUnstagedAndUntracked() {
        val output = """
            ## feature-auth...origin/feature-auth [ahead 1]
            M  lib/main.dart
             M pubspec.yaml
            A  lib/auth/login.dart
             D lib/old_widget.dart
            ?? lib/screens/home.dart
        """.trimIndent()

        val status = GitManager.parseGitStatusOutput(output)

        assertEquals("feature-auth", status.branch)
        assertFalse(status.isClean)

        // Staged
        assertEquals(2, status.stagedFiles.size)
        assertEquals("lib/main.dart", status.stagedFiles[0].path)
        assertEquals(GitFileStatus.MODIFIED, status.stagedFiles[0].status)
        assertEquals("lib/auth/login.dart", status.stagedFiles[1].path)
        assertEquals(GitFileStatus.ADDED, status.stagedFiles[1].status)

        // Unstaged
        assertEquals(2, status.unstagedFiles.size)
        assertEquals("pubspec.yaml", status.unstagedFiles[0].path)
        assertEquals(GitFileStatus.MODIFIED, status.unstagedFiles[0].status)
        assertEquals("lib/old_widget.dart", status.unstagedFiles[1].path)
        assertEquals(GitFileStatus.DELETED, status.unstagedFiles[1].status)

        // Untracked
        assertEquals(1, status.untrackedFiles.size)
        assertEquals("lib/screens/home.dart", status.untrackedFiles[0])
    }
}
