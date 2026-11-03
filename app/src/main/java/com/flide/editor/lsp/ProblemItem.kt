package com.flide.editor.lsp

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import java.io.File

/**
 * Represents a code diagnostic/issue reported by the Dart Language Server.
 */
data class ProblemItem(
    val file: File,
    val message: String,
    val severity: DiagnosticSeverity,
    val line: Int,      // 1-based line number for user display & navigation
    val column: Int,    // 1-based column
    val source: String? = null
) {
    companion object {
        fun fromDiagnostic(file: File, diagnostic: Diagnostic): ProblemItem {
            return ProblemItem(
                file = file,
                message = diagnostic.message ?: "Unknown diagnostic",
                severity = diagnostic.severity ?: DiagnosticSeverity.Information,
                line = (diagnostic.range?.start?.line ?: 0) + 1,
                column = (diagnostic.range?.start?.character ?: 0) + 1,
                source = diagnostic.source
            )
        }
    }
}
