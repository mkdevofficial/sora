package com.mokhtarxz.sora.github

object GitHubConfig {
    const val OAUTH_CLIENT_ID = ""
    const val OAUTH_DEVICE_CODE_URL = "https://github.com/login/device/code"
    const val OAUTH_ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
    val REQUIRED_SCOPES = listOf("repo", "workflow")
}
