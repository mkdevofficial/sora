package com.flide.ui.ide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flide.FlideApplication
import com.flide.flutter.run.FlutterRunState
import com.flide.project.FlutterProject
import com.flide.ui.console.RunConsoleView
import com.flide.ui.editor.TabbedEditorView
import com.flide.ui.preview.WebPreviewView
import com.flide.ui.terminal.TerminalScreen

enum class IdeTab(val title: String) {
    PREVIEW("Preview"),
    EDITOR("Editor"),
    CONSOLE("Console"),
    TERMINAL("Terminal")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeScreen(
    project: FlutterProject,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = FlideApplication.instance
    val runController = app.flutterRunController
    val runState by runController.runState.collectAsState()
    val webUrl by runController.activeWebUrl.collectAsState()
    val logs by runController.logs.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(IdeTab.PREVIEW.ordinal) }
    val isRunning = runController.isRunning

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        val statusText = when (val state = runState) {
                            is FlutterRunState.Running -> "● Running (${state.url})"
                            is FlutterRunState.Compiling -> "○ Compiling..."
                            is FlutterRunState.Starting -> "○ Starting..."
                            is FlutterRunState.Reloading -> "⚡ Reloading..."
                            is FlutterRunState.Restarting -> "🔄 Restarting..."
                            is FlutterRunState.Stopped -> "Stopped"
                            is FlutterRunState.Error -> "Error"
                            else -> "Idle"
                        }
                        val statusColor = when (runState) {
                            is FlutterRunState.Running -> Color(0xFF69F0AE)
                            is FlutterRunState.Compiling, is FlutterRunState.Starting -> Color(0xFFFFD740)
                            is FlutterRunState.Reloading, is FlutterRunState.Restarting -> Color(0xFF80D8FF)
                            is FlutterRunState.Error -> Color(0xFFFF5252)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = statusColor
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Quick Hot Reload Action (⚡)
                    IconButton(
                        onClick = { runController.sendHotReload() },
                        enabled = runState is FlutterRunState.Running
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Hot Reload",
                            tint = if (runState is FlutterRunState.Running) Color(0xFFFFD740) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }

                    // Quick Hot Restart Action (🔄)
                    IconButton(
                        onClick = { runController.sendHotRestart() },
                        enabled = runState is FlutterRunState.Running
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Hot Restart",
                            tint = if (runState is FlutterRunState.Running) Color(0xFF80D8FF) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Run / Stop Action Toggle
                    if (isRunning) {
                        FilledIconButton(
                            onClick = { runController.stopRun() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop Run Session")
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                                runController.startRun(project)
                                selectedTabIndex = IdeTab.PREVIEW.ordinal
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Run Web Server")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTabIndex == IdeTab.PREVIEW.ordinal,
                    onClick = { selectedTabIndex = IdeTab.PREVIEW.ordinal },
                    icon = { Icon(Icons.Default.Tv, contentDescription = "Preview") },
                    label = { Text("Preview") }
                )
                NavigationBarItem(
                    selected = selectedTabIndex == IdeTab.EDITOR.ordinal,
                    onClick = { selectedTabIndex = IdeTab.EDITOR.ordinal },
                    icon = { Icon(Icons.Default.Code, contentDescription = "Editor") },
                    label = { Text("Editor") }
                )
                NavigationBarItem(
                    selected = selectedTabIndex == IdeTab.CONSOLE.ordinal,
                    onClick = { selectedTabIndex = IdeTab.CONSOLE.ordinal },
                    icon = { Icon(Icons.Default.ViewStream, contentDescription = "Console") },
                    label = { Text("Console") }
                )
                NavigationBarItem(
                    selected = selectedTabIndex == IdeTab.TERMINAL.ordinal,
                    onClick = { selectedTabIndex = IdeTab.TERMINAL.ordinal },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = "Terminal") },
                    label = { Text("Terminal") }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (IdeTab.entries[selectedTabIndex]) {
                IdeTab.PREVIEW -> {
                    WebPreviewView(
                        url = webUrl,
                        runState = runState,
                        onHotReload = { runController.sendHotReload() },
                        onHotRestart = { runController.sendHotRestart() }
                    )
                }
                IdeTab.EDITOR -> {
                    TabbedEditorView(
                        project = project,
                        onFileSaved = {
                            if (runState is FlutterRunState.Running) {
                                runController.sendHotReload()
                            }
                        }
                    )
                }
                IdeTab.CONSOLE -> {
                    RunConsoleView(
                        logs = logs,
                        onClearLogs = { runController.clearLogs() }
                    )
                }
                IdeTab.TERMINAL -> {
                    TerminalScreen(
                        onNavigateBack = { selectedTabIndex = IdeTab.PREVIEW.ordinal },
                        initialWorkingDir = "/home/flide/projects/${project.name}"
                    )
                }
            }
        }
    }
}
