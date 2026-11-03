package com.flide.flutter

object FlutterConfig {
    const val GIT_REPO_URL = "https://github.com/flutter/flutter.git"
    const val DEFAULT_CHANNEL = "stable"
    const val DEFAULT_WEB_PORT = 8080
    const val DEFAULT_WEB_HOSTNAME = "127.0.0.1"

    val REQUIRED_DEBIAN_PACKAGES = listOf(
        "git",
        "curl",
        "unzip",
        "xz-utils",
        "zip",
        "ca-certificates",
        "libglu1-mesa",
        "clang",
        "cmake",
        "ninja-build",
        "pkg-config"
    )
}
