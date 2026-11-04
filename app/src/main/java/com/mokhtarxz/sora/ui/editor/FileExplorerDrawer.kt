package com.mokhtarxz.sora.ui.editor

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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.mokhtarxz.sora.project.FlutterProject
import java.io.File

@Composable
fun FileExplorerDrawer(
    project: FlutterProject,
    onFileSelected: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    var refreshKey by remember { mutableStateOf(0) }
    val expandedFolders = remember(project) {
        mutableStateMapOf<String, Boolean>().apply {
            // Expand lib by default so main.dart is immediately accessible
            put(File(project.rootDir, "lib").path, true)
        }
    }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var showDeleteDialog by remember { mutableStateOf<File?>(null) }

    var newFileName by remember { mutableStateOf("") }
    var newFolderName by remember { mutableStateOf("") }
    var renameTargetName by remember { mutableStateOf("") }

    // Dialog: New File
    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("New File") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("Relative path (e.g. lib/my_widget.dart)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            val targetFile = File(project.rootDir, newFileName.trim())
                            targetFile.parentFile?.mkdirs()
                            targetFile.createNewFile()
                            newFileName = ""
                            showNewFileDialog = false
                            refreshKey++
                            onFileSelected(targetFile)
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: New Folder
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Relative path (e.g. lib/screens)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            val targetDir = File(project.rootDir, newFolderName.trim())
                            targetDir.mkdirs()
                            newFolderName = ""
                            showNewFolderDialog = false
                            refreshKey++
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Rename
    showRenameDialog?.let { target ->
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename ${if (target.isDirectory) "Folder" else "File"}") },
            text = {
                OutlinedTextField(
                    value = renameTargetName,
                    onValueChange = { renameTargetName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameTargetName.isNotBlank()) {
                            val newDest = File(target.parentFile, renameTargetName.trim())
                            target.renameTo(newDest)
                            showRenameDialog = null
                            refreshKey++
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Delete
    showDeleteDialog?.let { target ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete ${if (target.isDirectory) "Folder" else "File"}") },
            text = { Text("Are you sure you want to delete '${target.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        target.deleteRecursively()
                        showDeleteDialog = null
                        refreshKey++
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }

    // Build hierarchical items list
    val allFiles = remember(project, refreshKey) {
        val list = mutableListOf<File>()
        if (project.rootDir.exists()) {
            project.rootDir.walkTopDown()
                .onEnter { dir ->
                    val name = dir.name
                    name != "build" && name != ".dart_tool" && name != ".git" && !name.startsWith(".")
                }
                .filter { it.path != project.rootDir.path }
                .toList()
                .sortedWith(compareBy({ !it.isDirectory }, { it.path }))
                .forEach { list.add(it) }
        }
        list
    }

    val visibleFiles = allFiles.filter { file ->
        var parent = file.parentFile
        var visible = true
        while (parent != null && parent.path != project.rootDir.path && parent.path.startsWith(project.rootDir.path)) {
            if (expandedFolders[parent.path] != true) {
                visible = false
                break
            }
            parent = parent.parentFile
        }
        visible
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with Explorer Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "PROJECT FILES",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = { showNewFileDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.NoteAdd, contentDescription = "New File", modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { showNewFolderDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { refreshKey++ },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // File Tree List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                items(visibleFiles) { file ->
                    val relativePath = file.relativeTo(project.rootDir).path
                    val depth = relativePath.count { it == '/' }
                    val isDir = file.isDirectory

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isDir) {
                                    val current = expandedFolders[file.path] ?: false
                                    expandedFolders[file.path] = !current
                                } else {
                                    onFileSelected(file)
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = (depth * 12).dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isDir) {
                                    if (expandedFolders[file.path] == true) Icons.Default.FolderOpen else Icons.Default.Folder
                                } else {
                                    Icons.Default.Description
                                },
                                contentDescription = null,
                                tint = if (isDir) Color(0xFFFFD54F) else when (file.extension) {
                                    "dart" -> Color(0xFF64B5F6)
                                    "yaml", "yml" -> Color(0xFFFF8A65)
                                    "json" -> Color(0xFF81C784)
                                    "md" -> Color(0xFFBA68C8)
                                    else -> Color(0xFF90A4AE)
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isDir) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        }

                        // Options
                        Row {
                            IconButton(
                                onClick = {
                                    renameTargetName = file.name
                                    showRenameDialog = file
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { showDeleteDialog = file },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
