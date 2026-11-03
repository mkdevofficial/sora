package com.flide.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flide.editor.EditorController

private val EDITOR_SYMBOLS = listOf(
    "TAB" to "  ",
    "{" to "{",
    "}" to "}",
    "(" to "(",
    ")" to ")",
    "[" to "[",
    "]" to "]",
    ";" to ";",
    ":" to ":",
    "=" to "=",
    "<" to "<",
    ">" to ">",
    "\"" to "\"",
    "'" to "'",
    "$" to "$",
    "_" to "_",
    "." to ".",
    "/" to "/",
    "!" to "!",
    "?" to "?",
    "|" to "|",
    "\\" to "\\",
    "@" to "@",
    "&" to "&"
)

@Composable
fun EditorToolbarRow(
    controller: EditorController,
    onToggleSearch: () -> Unit,
    onShowGotoLine: () -> Unit,
    onSaveRequested: () -> Unit,
    problemsCount: Int = 0,
    onShowProblems: () -> Unit = {},
    onFormatRequested: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF1E1E24),
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Quick Action: Undo
            IconButton(
                onClick = { controller.undo() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = Color(0xFFE0E0E0)
                )
            }

            // Quick Action: Redo
            IconButton(
                onClick = { controller.redo() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = Color(0xFFE0E0E0)
                )
            }

            // Quick Action: Search
            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Find & Replace",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Quick Action: Go to line
            IconButton(
                onClick = onShowGotoLine,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Numbers,
                    contentDescription = "Go to line",
                    tint = Color(0xFF80D8FF)
                )
            }

            // Quick Action: Save
            IconButton(
                onClick = onSaveRequested,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save file",
                    tint = Color(0xFF69F0AE)
                )
            }

            // Quick Action: Format Code (dart format)
            IconButton(
                onClick = onFormatRequested,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = "Format Code",
                    tint = Color(0xFFE040FB)
                )
            }

            // Quick Action: Problems Panel (with badge)
            IconButton(
                onClick = onShowProblems,
                modifier = Modifier.size(36.dp)
            ) {
                if (problemsCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge(containerColor = Color(0xFFFF5252)) {
                                Text(
                                    text = if (problemsCount > 99) "99+" else "$problemsCount",
                                    fontSize = 9.sp,
                                    color = Color.White
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Problems ($problemsCount)",
                            tint = Color(0xFFFFD740)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Problems (0)",
                        tint = Color(0xFFB0BEC5)
                    )
                }
            }

            // Symbol Chips
            EDITOR_SYMBOLS.forEach { (label, value) ->
                FilledTonalButton(
                    onClick = { controller.insertSymbol(value) },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF2C2D35),
                        contentColor = Color(0xFFECEFF1)
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = label,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
