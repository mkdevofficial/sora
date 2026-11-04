package com.mokhtarxz.sora.flutter

sealed interface FlutterState {
    data object NotInstalled : FlutterState
    data class CheckingEnvironment(val message: String) : FlutterState
    data class InstallingPackages(val progressMessage: String) : FlutterState
    data class Cloning(val channel: String, val message: String) : FlutterState
    data class Bootstrapping(val message: String) : FlutterState
    data class Configuring(val message: String) : FlutterState
    data class RunningDoctor(val message: String) : FlutterState
    data class Ready(
        val version: String,
        val channel: String,
        val doctorReport: String = ""
    ) : FlutterState
    data class Error(val message: String, val details: String? = null) : FlutterState
}
