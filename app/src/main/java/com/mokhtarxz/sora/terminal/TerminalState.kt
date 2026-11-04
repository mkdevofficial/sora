package com.mokhtarxz.sora.terminal

data class TerminalSessionInfo(
    val id: String,
    val title: String,
    val isRunning: Boolean = true
)

data class TerminalState(
    val sessions: List<TerminalSessionInfo> = emptyList(),
    val activeSessionId: String? = null
)
