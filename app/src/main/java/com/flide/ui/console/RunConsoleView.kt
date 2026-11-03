package com.flide.ui.console

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flide.flutter.run.RunLogEntry
import com.flide.flutter.run.RunLogLevel

enum class ConsoleFilter {
    ALL,
    OUTPUT,
    ERRORS,
    SYSTEM
}

@Composable
fun RunConsoleView(
    logs: List<RunLogEntry>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf(ConsoleFilter.ALL) }
    val listState = rememberLazyListState()

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            ConsoleFilter.ALL -> logs
            ConsoleFilter.OUTPUT -> logs.filter { it.level == RunLogLevel.STDOUT || it.level == RunLogLevel.SUCCESS }
            ConsoleFilter.ERRORS -> logs.filter { it.level == RunLogLevel.ERROR || it.level == RunLogLevel.WARNING }
            ConsoleFilter.SYSTEM -> logs.filter { it.level == RunLogLevel.SYSTEM }
        }
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    fun copyLogsToClipboard() {
        val text = logs.joinToString("\n") { "[${it.timestamp}] ${it.message}" }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Run Console Logs", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Console logs copied", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Toolbar with Filter Chips and Actions
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Filter chips
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedFilter == ConsoleFilter.ALL,
                        onClick = { selectedFilter = ConsoleFilter.ALL },
                        label = { Text("All (${logs.size})") }
                    )
                    FilterChip(
                        selected = selectedFilter == ConsoleFilter.OUTPUT,
                        onClick = { selectedFilter = ConsoleFilter.OUTPUT },
                        label = { Text("Output") }
                    )
                    FilterChip(
                        selected = selectedFilter == ConsoleFilter.ERRORS,
                        onClick = { selectedFilter = ConsoleFilter.ERRORS },
                        label = { Text("Errors") }
                    )
                    FilterChip(
                        selected = selectedFilter == ConsoleFilter.SYSTEM,
                        onClick = { selectedFilter = ConsoleFilter.SYSTEM },
                        label = { Text("System") }
                    )
                }

                // Copy and Clear
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = ::copyLogsToClipboard,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Console Logs"
                        )
                    }
                    IconButton(
                        onClick = onClearLogs,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Console"
                        )
                    }
                }
            }
        }

        // Dark Terminal Console Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F1117))
        ) {
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No console logs to display.\nStart a run session to view build & runtime output.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF6E7681)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    items(filteredLogs) { entry ->
                        val textColor = when (entry.level) {
                            RunLogLevel.ERROR -> Color(0xFFFF5252)
                            RunLogLevel.WARNING -> Color(0xFFFFD740)
                            RunLogLevel.SUCCESS -> Color(0xFF69F0AE)
                            RunLogLevel.SYSTEM -> Color(0xFF80D8FF)
                            RunLogLevel.STDOUT -> Color(0xFFECEFF1)
                            RunLogLevel.STDERR -> Color(0xFFFF8A80)
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Text(
                                text = entry.timestamp,
                                color = Color(0xFF546E7A),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.width(60.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = entry.message,
                                color = textColor,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
