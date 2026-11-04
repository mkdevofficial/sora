package com.mokhtarxz.sora.git

import android.util.Log
import com.mokhtarxz.sora.env.ProotRunner
import com.mokhtarxz.sora.project.FlutterProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Executes Git operations inside Debian userland under PRoot for Flutter projects.
 */
class GitManager(private val prootRunner: ProotRunner) {

    private val tag = "GitManager"

    suspend fun isGitRepo(project: FlutterProject): Boolean = withContext(Dispatchers.IO) {
        val gitDir = File(project.rootDir, ".git")
        gitDir.exists() && gitDir.isDirectory
    }

    suspend fun initRepo(project: FlutterProject): Boolean = withContext(Dispatchers.IO) {
        val (code, _) = prootRunner.executeCapture(listOf("git", "init"), project.rootfsPath)
        ensureGitUserConfig(project)
        code == 0
    }

    suspend fun ensureGitUserConfig(project: FlutterProject) {
        val (code, output) = prootRunner.executeCapture(listOf("git", "config", "user.name"), project.rootfsPath)
        if (code != 0 || output.isBlank()) {
            prootRunner.executeCapture(listOf("git", "config", "user.name", "Flide User"), project.rootfsPath)
            prootRunner.executeCapture(listOf("git", "config", "user.email", "flide@local.dev"), project.rootfsPath)
        }
    }

    suspend fun getStatus(project: FlutterProject): GitStatus = withContext(Dispatchers.IO) {
        if (!isGitRepo(project)) return@withContext GitStatus(branch = "none")

        val (code, output) = prootRunner.executeCapture(
            listOf("git", "status", "--porcelain=v1", "-b"),
            project.rootfsPath
        )
        if (code != 0) return@withContext GitStatus()
        parseGitStatusOutput(output)
    }

    suspend fun getDiff(project: FlutterProject, filePath: String? = null, staged: Boolean = false): String = withContext(Dispatchers.IO) {
        val cmd = mutableListOf("git", "diff")
        if (staged) cmd.add("--cached")
        if (!filePath.isNullOrBlank()) {
            cmd.add("--")
            cmd.add(filePath)
        }
        val (_, output) = prootRunner.executeCapture(cmd, project.rootfsPath)
        output
    }

    suspend fun stageAll(project: FlutterProject): Boolean = withContext(Dispatchers.IO) {
        val (code, _) = prootRunner.executeCapture(listOf("git", "add", "-A"), project.rootfsPath)
        code == 0
    }

    suspend fun stageFiles(project: FlutterProject, files: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext stageAll(project)
        val cmd = mutableListOf("git", "add", "--")
        cmd.addAll(files)
        val (code, _) = prootRunner.executeCapture(cmd, project.rootfsPath)
        code == 0
    }

    suspend fun unstageFiles(project: FlutterProject, files: List<String>): Boolean = withContext(Dispatchers.IO) {
        val cmd = mutableListOf("git", "reset", "HEAD", "--")
        cmd.addAll(files)
        val (code, _) = prootRunner.executeCapture(cmd, project.rootfsPath)
        code == 0
    }

    suspend fun commit(project: FlutterProject, message: String): Result<String> = withContext(Dispatchers.IO) {
        if (message.isBlank()) return@withContext Result.failure(IllegalArgumentException("Commit message cannot be empty"))
        ensureGitUserConfig(project)

        val (code, output) = prootRunner.executeCapture(
            listOf("git", "commit", "-m", message.trim()),
            project.rootfsPath
        )
        if (code == 0) {
            Result.success(output)
        } else {
            Result.failure(RuntimeException(output.ifBlank { "Commit failed (code $code)" }))
        }
    }

    suspend fun getBranches(project: FlutterProject): List<String> = withContext(Dispatchers.IO) {
        val (code, output) = prootRunner.executeCapture(
            listOf("git", "branch", "--list", "--format=%(refname:short)"),
            project.rootfsPath
        )
        if (code != 0 || output.isBlank()) return@withContext listOf("main")
        output.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    suspend fun checkoutBranch(
        project: FlutterProject,
        branchName: String,
        createNew: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val cmd = if (createNew) {
            listOf("git", "checkout", "-b", branchName.trim())
        } else {
            listOf("git", "checkout", branchName.trim())
        }
        val (code, output) = prootRunner.executeCapture(cmd, project.rootfsPath)
        if (code == 0) {
            Result.success(output)
        } else {
            Result.failure(RuntimeException(output.ifBlank { "Checkout failed (code $code)" }))
        }
    }

    suspend fun getLog(project: FlutterProject, limit: Int = 10): List<GitCommit> = withContext(Dispatchers.IO) {
        val (code, output) = prootRunner.executeCapture(
            listOf("git", "log", "-n", limit.toString(), "--pretty=format:%H|%h|%an|%s|%cd", "--date=short"),
            project.rootfsPath
        )
        if (code != 0 || output.isBlank()) return@withContext emptyList()
        val commits = mutableListOf<GitCommit>()
        output.lines().forEach { line ->
            val parts = line.split("|")
            if (parts.size >= 5) {
                commits.add(
                    GitCommit(
                        hash = parts[0],
                        shortHash = parts[1],
                        author = parts[2],
                        message = parts[3],
                        date = parts[4]
                    )
                )
            }
        }
        commits
    }

    companion object {
        fun parseGitStatusOutput(output: String): GitStatus {
            var branch = "main"
            val staged = mutableListOf<GitFileEntry>()
            val unstaged = mutableListOf<GitFileEntry>()
            val untracked = mutableListOf<String>()

            output.lines().forEach { rawLine ->
                val line = rawLine.trimEnd()
                if (line.startsWith("##")) {
                    val branchInfo = line.removePrefix("##").trim()
                    branch = when {
                        branchInfo.startsWith("No commits yet on ") -> branchInfo.removePrefix("No commits yet on ")
                        branchInfo.startsWith("Initial commit on ") -> branchInfo.removePrefix("Initial commit on ")
                        else -> branchInfo.substringBefore("...").substringBefore(" ")
                    }
                } else if (line.startsWith("?? ")) {
                    untracked.add(line.substring(3).trim())
                } else if (line.length >= 3) {
                    val indexStatus = line[0]
                    val worktreeStatus = line[1]
                    val filePath = line.substring(3).trim()

                    // Staged files
                    when (indexStatus) {
                        'M' -> staged.add(GitFileEntry(filePath, GitFileStatus.MODIFIED))
                        'A' -> staged.add(GitFileEntry(filePath, GitFileStatus.ADDED))
                        'D' -> staged.add(GitFileEntry(filePath, GitFileStatus.DELETED))
                        'R' -> staged.add(GitFileEntry(filePath, GitFileStatus.RENAMED))
                    }

                    // Unstaged files
                    when (worktreeStatus) {
                        'M' -> unstaged.add(GitFileEntry(filePath, GitFileStatus.MODIFIED))
                        'D' -> unstaged.add(GitFileEntry(filePath, GitFileStatus.DELETED))
                    }
                }
            }

            return GitStatus(
                branch = branch.ifBlank { "main" },
                stagedFiles = staged,
                unstagedFiles = unstaged,
                untrackedFiles = untracked
            )
        }
    }
}
