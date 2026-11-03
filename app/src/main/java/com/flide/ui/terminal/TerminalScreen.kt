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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import java.util.UUID

data class TerminalTabItem(
    val id: String,
    val session: TerminalSession,
    val client: FlideTerminalClient,
    var title: String = "bash"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onNavigateBack: () -> Unit,
    initialWorkingDir: String = "/home/flide",
    initialCommand: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = FlideApplication.instance
    val terminalManager = app.terminalManager

    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }

    val density = LocalDensity.current.density
    val textSizePx = remember(density) { (13.5f * density).toInt() }

    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }
    val tabs = remember { mutableStateListOf<TerminalTabItem>() }
    var activeTabIndex by remember { mutableIntStateOf(0) }

    fun createTab(view: TerminalView?, command: String? = null) {
        val tabId = UUID.randomUUID().toString()
        val tabIndex = tabs.size + 1
        var client: FlideTerminalClient? = null
        client = FlideTerminalClient(
            context = context,
            onTitleChange = { newTitle ->
                val targetTab = tabs.find { it.id == tabId }
                if (targetTab != null) {
                    targetTab.title = newTitle.ifBlank { "bash $tabIndex" }
                }
            },
            onSessionFinish = {}
        )
        client.isCtrlActive = isCtrlActive
        client.isAltActive = isAltActive
        client.currentTextSize = textSizePx
        client.terminalView = view

        val session = terminalManager.createSession(
            client = client,
            workingDir = initialWorkingDir,
            asRoot = true
        )
        val tab = TerminalTabItem(id = tabId, session = session, client = client, title = "bash $tabIndex")
        tabs.add(tab)
        activeTabIndex = tabs.size - 1

        view?.let {
            it.setTerminalViewClient(client)
            it.attachSession(session)
            it.updateSize()
            it.requestFocus()
        }

        if (!command.isNullOrBlank()) {
            session.writeString("$command\n")
        }
    }

    fun switchTab(index: Int, view: TerminalView?) {
        if (index in tabs.indices) {
            activeTabIndex = index
            val tab = tabs[index]
            tab.client.isCtrlActive = isCtrlActive
            tab.client.isAltActive = isAltActive
            tab.client.currentTextSize = textSizePx
            view?.let {
                it.setTerminalViewClient(tab.client)
                it.attachSession(tab.session)
                it.updateSize()
                it.requestFocus()
            }
        }
    }

    fun closeTab(index: Int, view: TerminalView?) {
        if (index in tabs.indices) {
            val tab = tabs.removeAt(index)
            tab.session.finishIfRunning()
            if (tabs.isEmpty()) {
                createTab(view)
            } else {
                val newIndex = index.coerceAtMost(tabs.size - 1)
                switchTab(newIndex, view)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tabs.forEach { it.session.finishIfRunning() }
        }
    }

    Scaffold(
        topBar = {
            val activeTab = tabs.getOrNull(activeTabIndex)
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeTab?.title ?: "Terminal",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = "Debian arm64 Userland (${tabs.size} tabs)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF81C784)
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
                        activeTab?.let { it.client.onPasteTextFromClipboard(it.session) }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste"
                        )
                    }
                    // Restart active tab session
                    IconButton(onClick = {
                        activeTab?.session?.finishIfRunning()
                        closeTab(activeTabIndex, terminalViewRef)
                        createTab(terminalViewRef)
                    }) {
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
            // Multi-Tab Bar Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E24))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isActive = index == activeTabIndex
                    val tabBg = if (isActive) Color(0xFF2E2E3A) else Color(0xFF141418)

                    Row(
                        modifier = Modifier
                            .background(tabBg, RoundedCornerShape(4.dp))
                            .clickable { switchTab(index, terminalViewRef) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tab.title,
                            color = if (isActive) Color.White else Color(0xFF9E9E9E),
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                        if (tabs.size > 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Tab",
                                tint = Color(0xFF9E9E9E),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { closeTab(index, terminalViewRef) }
                            )
                        }
                    }
                }

                // Add Tab Button (+)
                IconButton(
                    onClick = { createTab(terminalViewRef) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Terminal Tab",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
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
                            val activeTab = tabs.getOrNull(activeTabIndex)
                            activeTab?.let { tab ->
                                if (tab.session.isRunning) {
                                    tab.session.writeString("$chip\n")
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
                            terminalViewRef = this

                            if (tabs.isEmpty()) {
                                createTab(this, initialCommand)
                            } else {
                                switchTab(activeTabIndex, this)
                            }

                            isFocusable = true
                            isFocusableInTouchMode = true
                            requestFocus()
                        }
                    },
                    update = { view ->
                        terminalViewRef = view
                        val activeTab = tabs.getOrNull(activeTabIndex)
                        if (activeTab != null && view.currentSession != activeTab.session) {
                            view.setTerminalViewClient(activeTab.client)
                            view.attachSession(activeTab.session)
                            view.updateSize()
                        }
                    }
                )
            }

            // Termux-Style Extra Keys Row (above software keyboard)
            ExtraKeysToolbar(
                isCtrlActive = isCtrlActive,
                onToggleCtrl = {
                    isCtrlActive = !isCtrlActive
                    tabs.forEach { it.client.isCtrlActive = isCtrlActive }
                },
                isAltActive = isAltActive,
                onToggleAlt = {
                    isAltActive = !isAltActive
                    tabs.forEach { it.client.isAltActive = isAltActive }
                },
                onSendKey = { keySequence ->
                    val activeTab = tabs.getOrNull(activeTabIndex)
                    activeTab?.let { tab ->
                        if (tab.session.isRunning) {
                            tab.session.writeString(keySequence)
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
