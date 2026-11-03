package com.flide.git

/**
 * Represents the repository Git status for a project.
 */
data class GitStatus(
    val branch: String = "main",
    val stagedFiles: List<GitFileEntry> = emptyList(),
    val unstagedFiles: List<GitFileEntry> = emptyList(),
    val untrackedFiles: List<String> = emptyList()
) {
    val isClean: Boolean
        get() = stagedFiles.isEmpty() && unstagedFiles.isEmpty() && untrackedFiles.isEmpty()
}

data class GitFileEntry(
    val path: String,
    val status: GitFileStatus
)

enum class GitFileStatus {
    MODIFIED,
    ADDED,
    DELETED,
    RENAMED,
    UNTRACKED
}

data class GitCommit(
    val hash: String,
    val shortHash: String,
    val author: String,
    val message: String,
    val date: String
)
