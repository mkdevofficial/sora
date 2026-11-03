package com.flide.flutter

sealed interface FlutterState {
    data object NotInstalled : FlutterState
    data class Cloning(val progress: Float) : FlutterState
    data class Precache(val message: String) : FlutterState
    data class Ready(val version: String, val channel: String) : FlutterState
    data class Error(val message: String, val throwable: Throwable? = null) : FlutterState
}
