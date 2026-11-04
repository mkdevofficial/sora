package com.mokhtarxz.sora.editor.lsp

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ProblemItemTest {

    @Test
    fun testFromDiagnostic_translatesPositionAndProperties() {
        val file = File("/home/flide/projects/counter_app/lib/main.dart")
        val diagnostic = Diagnostic(
            Range(Position(14, 2), Position(14, 10)),
            "Undefined name 'myWidget'",
            DiagnosticSeverity.Error,
            "dart"
        )

        val problem = ProblemItem.fromDiagnostic(file, diagnostic)

        assertEquals(file, problem.file)
        assertEquals("Undefined name 'myWidget'", problem.message)
        assertEquals(DiagnosticSeverity.Error, problem.severity)
        assertEquals(15, problem.line) // 0-based to 1-based
        assertEquals(3, problem.column) // 0-based to 1-based
        assertEquals("dart", problem.source)
    }

    @Test
    fun testFromDiagnostic_defaultsWhenRangeNull() {
        val file = File("/home/flide/projects/counter_app/lib/main.dart")
        val diagnostic = Diagnostic().apply {
            message = "Unused import"
            severity = DiagnosticSeverity.Warning
        }

        val problem = ProblemItem.fromDiagnostic(file, diagnostic)

        assertEquals("Unused import", problem.message)
        assertEquals(DiagnosticSeverity.Warning, problem.severity)
        assertEquals(1, problem.line)
        assertEquals(1, problem.column)
    }
}
