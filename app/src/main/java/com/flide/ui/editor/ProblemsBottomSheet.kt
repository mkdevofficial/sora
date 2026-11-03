package com.flide.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flide.editor.lsp.ProblemItem
import org.eclipse.lsp4j.DiagnosticSeverity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProblemsBottomSheet(
    problems: List<ProblemItem>,
    onDismiss: () -> Unit,
    onSelectProblem: (ProblemItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var severityFilter by remember { mutableStateOf<DiagnosticSeverity?>(null) }

    val errorCount = problems.count { it.severity == DiagnosticSeverity.Error }
    val warningCount = problems.count { it.severity == DiagnosticSeverity.Warning }
    val infoCount = problems.count { it.severity == DiagnosticSeverity.Information || it.severity == DiagnosticSeverity.Hint }

    val filteredProblems = remember(problems, severityFilter) {
        if (severityFilter == null) problems else problems.filter { it.severity == severityFilter }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Problems (${problems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = severityFilter == null,
                        onClick = { severityFilter = null },
                        label = { Text("All", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = severityFilter == DiagnosticSeverity.Error,
                        onClick = { severityFilter = if (severityFilter == DiagnosticSeverity.Error) null else DiagnosticSeverity.Error },
                        label = { Text("Errors ($errorCount)", fontSize = 11.sp, color = if (errorCount > 0) Color(0xFFFF5252) else Color.Unspecified) }
                    )
                    FilterChip(
                        selected = severityFilter == DiagnosticSeverity.Warning,
                        onClick = { severityFilter = if (severityFilter == DiagnosticSeverity.Warning) null else DiagnosticSeverity.Warning },
                        label = { Text("Warnings ($warningCount)", fontSize = 11.sp, color = if (warningCount > 0) Color(0xFFFFD740) else Color.Unspecified) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredProblems.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF69F0AE),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No problems detected in open files.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredProblems) { problem ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectProblem(problem)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                val (icon, iconColor) = when (problem.severity) {
                                    DiagnosticSeverity.Error -> Icons.Default.Error to Color(0xFFFF5252)
                                    DiagnosticSeverity.Warning -> Icons.Default.Warning to Color(0xFFFFD740)
                                    else -> Icons.Default.Info to Color(0xFF80D8FF)
                                }

                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(18.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = problem.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${problem.file.name} [Ln ${problem.line}, Col ${problem.column}]",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
