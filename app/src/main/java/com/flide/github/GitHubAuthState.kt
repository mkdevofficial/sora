package com.flide.github

sealed interface GitHubAuthState {
    data object Unauthenticated : GitHubAuthState
    data class DeviceCodePrompt(
        val userCode: String,
        val verificationUri: String,
        val expiresInSeconds: Int
    ) : GitHubAuthState
    data class Authenticated(val username: String) : GitHubAuthState
    data class Error(val message: String) : GitHubAuthState
}
