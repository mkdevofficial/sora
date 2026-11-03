package com.flide.env

sealed interface EnvironmentState {
    data object NotInstalled : EnvironmentState
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : EnvironmentState
    data object VerifyingChecksum : EnvironmentState
    data class Extracting(val progress: Float) : EnvironmentState
    data class Configuring(val stepDescription: String) : EnvironmentState
    data object Ready : EnvironmentState
    data class Error(val message: String, val throwable: Throwable? = null) : EnvironmentState
}
