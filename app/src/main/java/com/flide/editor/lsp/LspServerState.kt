package com.flide.editor.lsp

/**
 * State representing the lifecycle of the Dart Language Server.
 */
sealed class LspServerState {
    data object Disabled : LspServerState()
    data object Starting : LspServerState()
    data class Indexing(val title: String = "Analyzing...") : LspServerState()
    data object Ready : LspServerState()
    data class Error(val message: String) : LspServerState()
}
