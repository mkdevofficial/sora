package com.flide.ui.editor

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flide.project.FlutterProject
import java.io.File

@Composable
fun ProjectFileEditorView(
    project: FlutterProject,
    onFileSaved: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFile by remember(project.name) {
        val mainDart = project.mainDartFile
        mutableStateOf(if (mainDart.exists()) mainDart else project.pubspecFile)
    }
    var fileContent by remember(selectedFile) {
        mutableStateOf(if (selectedFile.exists()) selectedFile.readText() else "")
    }
    var isDirty by remember(selectedFile) { mutableStateOf(false) }
    var fileMenuExpanded by remember { mutableStateOf(false) }

    // Collect project files recursively, excluding build/ and .dart_tool/
    val projectFiles = remember(project) {
        if (!project.rootDir.exists()) {
            emptyList()
        } else {
            project.rootDir.walkTopDown()
                .onEnter { dir ->
                    val name = dir.name
                    name != "build" && name != ".dart_tool" && name != ".git" && !name.startsWith(".")
                }
                .filter { it.isFile }
                .sortedBy { it.relativeTo(project.rootDir).path }
                .toList()
        }
    }

    fun saveFile() {
        try {
            selectedFile.writeText(fileContent)
            isDirty = false
            Toast.makeText(context, "Saved ${selectedFile.name}", Toast.LENGTH_SHORT).show()
            onFileSaved(selectedFile)
        } catch (e: Exception) {
            Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Editor Toolbar
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // File selector dropdown button
                Box {
                    Row(
                        modifier = Modifier
                            .clickable { fileMenuExpanded = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedFile.exists()) selectedFile.relativeTo(project.rootDir).path else "Select File",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        if (isDirty) {
                            Text(
                                text = " • (unsaved)",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = fileMenuExpanded,
                        onDismissRequest = { fileMenuExpanded = false }
                    ) {
                        projectFiles.forEach { file ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = file.relativeTo(project.rootDir).path,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                },
                                onClick = {
                                    selectedFile = file
                                    fileContent = file.readText()
                                    isDirty = false
                                    fileMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Action buttons: Revert, Save
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (selectedFile.exists()) {
                                fileContent = selectedFile.readText()
                                isDirty = false
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload from Disk")
                    }

                    IconButton(
                        onClick = ::saveFile,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save file",
                            tint = if (isDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Code Editor Viewport
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .padding(8.dp)
        ) {
            val horizontalScrollState = rememberScrollState()
            val verticalScrollState = rememberScrollState()

            BasicTextField(
                value = fileContent,
                onValueChange = {
                    fileContent = it
                    isDirty = true
                },
                textStyle = TextStyle(
                    color = Color(0xFFD4D4D4),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                cursorBrush = SolidColor(Color(0xFF569CD6)),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
                    .horizontalScroll(horizontalScrollState)
            )
        }
    }
}
