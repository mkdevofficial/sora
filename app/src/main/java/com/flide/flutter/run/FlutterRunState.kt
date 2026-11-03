package com.flide.flutter.run

import com.flide.project.FlutterProject

/**
 * State machine for the active `flutter run -d web-server` session.
 */
sealed interface FlutterRunState {
    data object Idle : FlutterRunState

    data class Starting(
        val project: FlutterProject,
        val port: Int
    ) : FlutterRunState

    data class Compiling(
        val project: FlutterProject,
        val port: Int,
        val message: String
    ) : FlutterRunState

    data class Running(
        val project: FlutterProject,
        val url: String,
        val port: Int,
        val devToolsUrl: String? = null
    ) : FlutterRunState

    data class Reloading(
        val project: FlutterProject,
        val url: String,
        val port: Int
    ) : FlutterRunState

    data class Restarting(
        val project: FlutterProject,
        val url: String,
        val port: Int
    ) : FlutterRunState

    data class Stopped(
        val project: FlutterProject,
        val exitCode: Int,
        val message: String? = null
    ) : FlutterRunState

    data class Error(
        val project: FlutterProject,
        val message: String,
        val details: String? = null
    ) : FlutterRunState
}

enum class RunLogLevel {
    STDOUT,
    STDERR,
    SYSTEM,
    SUCCESS,
    WARNING,
    ERROR
}

data class RunLogEntry(
    val timestamp: String,
    val message: String,
    val level: RunLogLevel = RunLogLevel.STDOUT
)
