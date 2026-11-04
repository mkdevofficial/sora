package com.mokhtarxz.sora.env

/**
 * Configuration parameters for the Debian arm64 rootfs and proot execution.
 */
object EnvironmentConfig {
    const val DEBIAN_SUITE = "bookworm"
    const val ARCHITECTURE = "arm64"
    const val DEFAULT_USER = "flide"
    const val DEFAULT_USER_UID = 1000
    const val DEFAULT_USER_GID = 1000

    // Minimal required disk space in bytes (2.5 GB) before initiating bootstrap
    const val MIN_STORAGE_REQUIRED_BYTES: Long = 2_500_000_000L

    // Verified Debian arm64 rootfs archive from termux/proot-distro
    const val ROOTFS_URL = "https://github.com/termux/proot-distro/releases/download/v4.17.3/debian-bookworm-aarch64-pd-v4.17.3.tar.xz"
    const val ROOTFS_SHA256 = "3a841a794ae5999b33e33b329582ed0379d4f54ca62c6ce5a8eb9cff5ef8900b"
    const val ROOTFS_ARCHIVE_NAME = "debian-bookworm-arm64.tar.xz"
    const val ROOTFS_EXPECTED_SIZE_BYTES = 42_912_980L

    // Default DNS servers written to /etc/resolv.conf
    val DEFAULT_DNS_SERVERS = listOf("1.1.1.1", "8.8.8.8")

    // Default /etc/hosts content
    val DEFAULT_HOSTS_CONTENT = """
        127.0.0.1   localhost localhost.localdomain
        ::1         localhost localhost.localdomain ip6-localhost ip6-loopback
    """.trimIndent()
}
