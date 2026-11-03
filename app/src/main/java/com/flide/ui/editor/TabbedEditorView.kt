package com.flide.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FindNext
import androidx.compose.material.icons.filled.FindPrevious
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.flide.editor.EditorController
import com.flide.editor.EditorTab
import com.flide.project.FlutterProject
import java.io.File

@Composable
fun TabbedEditorView(
    project: FlutterProject,
    onFileSaved: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val editorController = remember { EditorController() }

    // Tabs state
    val openTabs = remember {
        mutableStateListOf<EditorTab>().apply {
            val defaultFile = if (project.mainDartFile.exists()) project.mainDartFile else project.pubspecFile
            add(EditorTab(file = defaultFile))
        }
    }
    var activeTabIndex by remember { mutableIntStateOf(0) }

    // Search & Replace UI state
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    // Go to line UI state
    var showGotoLineDialog by remember { mutableStateOf(false) }
    var targetLineText by remember { mutableStateOf("") }

    // File Explorer Sheet state
    var showFileExplorer by remember { mutableStateOf(false) }

    fun openFile(file: File) {
        val existingIndex = openTabs.indexOfFirst { it.file.absolutePath == file.absolutePath }
        if (existingIndex >= 0) {
            activeTabIndex = existingIndex
        } else {
            openTabs.add(EditorTab(file = file))
            activeTabIndex = openTabs.size - 1
        }
    }

    fun closeTab(index: Int) {
        if (openTabs.size > 1) {
            openTabs.removeAt(index)
            if (activeTabIndex >= openTabs.size) {
                activeTabIndex = openTabs.size - 1
            }
        }
    }

    // Go to line dialog
    if (showGotoLineDialog) {
        AlertDialog(
            onDismissRequest = { showGotoLineDialog = false },
            title = { Text("Go to Line") },
            text = {
                OutlinedTextField(
                    value = targetLineText,
                    onValueChange = { targetLineText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Line number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lineNum = targetLineText.toIntOrNull() ?: 1
                        editorController.gotoLine(lineNum)
                        showGotoLineDialog = false
                        targetLineText = ""
                    }
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGotoLineDialog = false }) { Text("Cancel") }
            }
        )
    }

    // File Explorer Modal Sheet
    if (showFileExplorer) {
        ModalBottomSheet(
            onDismissRequest = { showFileExplorer = false }
        ) {
            FileExplorerDrawer(
                project = project,
                onFileSelected = { file ->
                    openFile(file)
                    showFileExplorer = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            )
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        // Tab Bar
        Surface(
            color = Color(0xFF1E1E24),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Explorer Toggle Button
                IconButton(
                    onClick = { showFileExplorer = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "File Explorer",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Scrollable Tabs List
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    openTabs.forEachIndexed { index, tab ->
                        val isActive = index == activeTabIndex
                        val tabBgColor = if (isActive) Color(0xFF2D2D38) else Color(0xFF18181D)

                        Row(
                            modifier = Modifier
                                .background(tabBgColor, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .clickable { activeTabIndex = index }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = when (tab.file.extension) {
                                    "dart" -> Color(0xFF64B5F6)
                                    "yaml", "yml" -> Color(0xFFFF8A65)
                                    "json" -> Color(0xFF81C784)
                                    else -> Color(0xFFB0BEC5)
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.title + if (tab.isDirty) " •" else "",
                                color = if (isActive) Color(0xFFECEFF1) else Color(0xFF90A4AE),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (openTabs.size > 1) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Tab",
                                    tint = Color(0xFF90A4AE),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { closeTab(index) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Search & Replace Floating Bar
        if (showSearchBar) {
            Surface(
                color = Color(0xFF252526),
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Find Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                editorController.search(it)
                            },
                            placeholder = { Text("Find...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).height(44.dp)
                        )

                        IconButton(onClick = { editorController.findPrevious() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.FindPrevious, contentDescription = "Previous")
                        }
                        IconButton(onClick = { editorController.findNext() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.FindNext, contentDescription = "Next")
                        }
                        IconButton(
                            onClick = {
                                showSearchBar = false
                                editorController.stopSearch()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Search")
                        }
                    }

                    // Replace Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            placeholder = { Text("Replace...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).height(44.dp)
                        )

                        FilledTonalButton(
                            onClick = { editorController.replace(replaceQuery) },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Replace", fontSize = 11.sp)
                        }

                        FilledTonalButton(
                            onClick = { editorController.replaceAll(replaceQuery) },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("All", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Active Sora CodeEditor
        val currentTab = openTabs.getOrNull(activeTabIndex)
        if (currentTab != null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SoraEditorView(
                    file = currentTab.file,
                    controller = editorController,
                    onDirtyChanged = { isDirty ->
                        if (activeTabIndex < openTabs.size) {
                            openTabs[activeTabIndex] = openTabs[activeTabIndex].copy(isDirty = isDirty)
                        }
                    },
                    onFileSaved = onFileSaved,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No files open. Use the file explorer icon above to open a file.")
            }
        }

        // Symbols & Actions Toolbar (Docked above soft keyboard)
        EditorToolbarRow(
            controller = editorController,
            onToggleSearch = { showSearchBar = !showSearchBar },
            onShowGotoLine = { showGotoLineDialog = true },
            onSaveRequested = {
                currentTab?.let { tab ->
                    try {
                        val text = editorController.activeEditor?.text?.toString() ?: ""
                        tab.file.writeText(text)
                        if (activeTabIndex < openTabs.size) {
                            openTabs[activeTabIndex] = openTabs[activeTabIndex].copy(isDirty = false)
                        }
                        onFileSaved(tab.file)
                    } catch (e: Exception) {}
                }
            }
        )
    }
}
