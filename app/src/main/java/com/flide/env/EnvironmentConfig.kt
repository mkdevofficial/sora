package com.flide.env

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

    // Default DNS servers written to resolv.conf
    val DEFAULT_DNS_SERVERS = listOf("1.1.1.1", "8.8.8.8")
}
