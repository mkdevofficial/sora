package com.mokhtarxz.sora.flutter

object FlutterConfig {
    const val GIT_REPO_URL = "https://github.com/flutter/flutter.git"
    const val DEFAULT_CHANNEL = "stable"
    const val DEFAULT_WEB_PORT = 8080
    const val DEFAULT_WEB_HOSTNAME = "127.0.0.1"
    const val MIN_FREE_STORAGE_GB = 1.5f

    const val FLUTTER_DIR_IN_ROOTFS = "/home/flide/flutter"
    const val FLUTTER_BIN_IN_ROOTFS = "/home/flide/flutter/bin/flutter"
    const val DART_BIN_IN_ROOTFS = "/home/flide/flutter/bin/dart"

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
