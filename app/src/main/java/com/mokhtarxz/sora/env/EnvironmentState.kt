package com.mokhtarxz.sora.env

sealed interface EnvironmentState {
    data class Idle(val isInstalled: Boolean) : EnvironmentState
    data object CheckingStorage : EnvironmentState
    data object StagingProot : EnvironmentState
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long = 0L
    ) : EnvironmentState
    data class VerifyingChecksum(val progress: Float) : EnvironmentState
    data class Extracting(val progress: Float, val currentFile: String = "") : EnvironmentState
    data class Configuring(val stepDescription: String) : EnvironmentState
    data object Ready : EnvironmentState
    data class Error(val message: String, val throwable: Throwable? = null) : EnvironmentState
}
