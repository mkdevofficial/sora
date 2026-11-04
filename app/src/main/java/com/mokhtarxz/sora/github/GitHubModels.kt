package com.mokhtarxz.sora.github

import java.io.File

data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int
)

data class GitHubUser(
    val login: String,
    val name: String?,
    val avatarUrl: String?
)

data class WorkflowRun(
    val id: Long,
    val name: String,
    val status: String,       // queued, in_progress, completed
    val conclusion: String?,  // success, failure, cancelled, etc.
    val htmlUrl: String,
    val createdAt: String
)

data class ArtifactItem(
    val id: Long,
    val name: String,
    val sizeInBytes: Long,
    val archiveDownloadUrl: String,
    val expired: Boolean
)

sealed class CloudBuildState {
    data object Idle : CloudBuildState()
    data class DeviceAuth(val userCode: String, val verificationUri: String) : CloudBuildState()
    data object Preparing : CloudBuildState()
    data class Building(val runId: Long, val status: String, val conclusion: String?, val logs: String) : CloudBuildState()
    data class Downloading(val progress: Float) : CloudBuildState()
    data class ReadyToInstall(val apkFile: File) : CloudBuildState()
    data class Error(val message: String) : CloudBuildState()
}
