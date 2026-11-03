package com.flide.ui.terminal

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.flide.FlideApplication
import com.flide.terminal.FlideTerminalClient
import com.flide.terminal.writeString
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = FlideApplication.instance
    val terminalManager = app.terminalManager

    var terminalTitle by remember { mutableStateOf("Debian Bash") }
    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }
    var isSessionRunning by remember { mutableStateOf(true) }

    val density = LocalDensity.current.density
    val textSizePx = remember(density) { (13.5f * density).toInt() }

    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }
    var terminalSessionRef by remember { mutableStateOf<TerminalSession?>(null) }

    val terminalClient = remember {
        FlideTerminalClient(
            context = context,
            onTitleChange = { newTitle ->
                terminalTitle = newTitle.ifBlank { "Debian Shell" }
            },
            onSessionFinish = {
                isSessionRunning = false
            }
        )
    }

    // Keep active modifiers synced with client
    terminalClient.isCtrlActive = isCtrlActive
    terminalClient.isAltActive = isAltActive

    fun startNewSession(view: TerminalView?) {
        terminalSessionRef?.finishIfRunning()
        val session = terminalManager.createSession(
            client = terminalClient,
            workingDir = "/home/flide",
            asRoot = true
        )
        terminalSessionRef = session
        isSessionRunning = true
        view?.let {
            it.attachSession(session)
            it.updateSize()
            it.requestFocus()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            terminalSessionRef?.finishIfRunning()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = terminalTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = if (isSessionRunning) "ARM64 Debian Userland" else "Session Finished",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSessionRunning) Color(0xFF81C784) else Color(0xFFE57373)
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
                    // Paste from clipboard
                    IconButton(onClick = {
                        terminalSessionRef?.let { terminalClient.onPasteTextFromClipboard(it) }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste"
                        )
                    }
                    // Restart session
                    IconButton(onClick = { startNewSession(terminalViewRef) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart Terminal"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF000000))
                .imePadding()
        ) {
            // Quick Commands Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val quickChips = listOf(
                    "uname -a",
                    "apt update",
                    "git status",
                    "python3 --version",
                    "id",
                    "clear"
                )
                for (chip in quickChips) {
                    SuggestionChip(
                        onClick = {
                            terminalSessionRef?.let { session ->
                                if (session.isRunning) {
                                    session.writeString("$chip\n")
                                }
                            }
                        },
                        label = {
                            Text(
                                text = chip,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFF262626)
                        ),
                        border = null
                    )
                }
            }

            // Native Termux Terminal View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF000000))
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        TerminalView(ctx, null).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setTextSize(textSizePx)
                            setTypeface(Typeface.MONOSPACE)
                            setTerminalViewClient(terminalClient)
                            terminalClient.terminalView = this
                            terminalViewRef = this

                            startNewSession(this)

                            isFocusable = true
                            isFocusableInTouchMode = true
                            requestFocus()
                        }
                    },
                    update = { view ->
                        terminalViewRef = view
                        terminalClient.terminalView = view
                        val session = terminalSessionRef
                        if (session != null && view.currentSession != session) {
                            view.attachSession(session)
                            view.updateSize()
                        }
                    }
                )

                // Restart banner if session exited
                if (!isSessionRunning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color(0xDD212121))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Process exited. Restart session?",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Button(
                                onClick = { startNewSession(terminalViewRef) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Restart")
                            }
                        }
                    }
                }
            }

            // Termux-Style Extra Keys Row (above software keyboard)
            ExtraKeysToolbar(
                isCtrlActive = isCtrlActive,
                onToggleCtrl = { isCtrlActive = !isCtrlActive },
                isAltActive = isAltActive,
                onToggleAlt = { isAltActive = !isAltActive },
                onSendKey = { keySequence ->
                    terminalSessionRef?.let { session ->
                        if (session.isRunning) {
                            session.writeString(keySequence)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ExtraKeysToolbar(
    isCtrlActive: Boolean,
    onToggleCtrl: () -> Unit,
    isAltActive: Boolean,
    onToggleAlt: () -> Unit,
    onSendKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(0xFF1B1B1B))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ESC key
        ExtraKeyButton(label = "ESC") { onSendKey("\u001b") }

        // TAB key
        ExtraKeyButton(label = "TAB") { onSendKey("\t") }

        // CTRL toggle
        ExtraKeyButton(
            label = "CTRL",
            isActive = isCtrlActive,
            onClick = onToggleCtrl
        )

        // ALT toggle
        ExtraKeyButton(
            label = "ALT",
            isActive = isAltActive,
            onClick = onToggleAlt
        )

        // Common coding / terminal symbols
        ExtraKeyButton(label = "-") { onSendKey("-") }
        ExtraKeyButton(label = "/") { onSendKey("/") }
        ExtraKeyButton(label = "|") { onSendKey("|") }
        ExtraKeyButton(label = "~") { onSendKey("~") }

        // Directional arrow keys
        ExtraKeyButton(label = "▲") { onSendKey("\u001b[A") } // Up
        ExtraKeyButton(label = "▼") { onSendKey("\u001b[B") } // Down
        ExtraKeyButton(label = "◀") { onSendKey("\u001b[D") } // Left
        ExtraKeyButton(label = "▶") { onSendKey("\u001b[C") } // Right
    }
}

@Composable
private fun ExtraKeyButton(
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF2C2C2C),
            contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else Color(0xFFEEEEEE)
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
