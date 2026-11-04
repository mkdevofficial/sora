package com.mokhtarxz.sora.ui.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mokhtarxz.sora.SoraApplication
import com.mokhtarxz.sora.project.FlutterProject
import kotlinx.coroutines.launch

enum class CreationMode {
    TERMINAL_SHELL,
    BACKGROUND_TASK
}

@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onProjectCreated: (FlutterProject) -> Unit,
    onOpenInTerminal: (projectName: String, command: String) -> Unit
) {
    val app = SoraApplication.instance
    val projectManager = app.projectManager
    val coroutineScope = rememberCoroutineScope()

    var projectName by remember { mutableStateOf("") }
    var orgName by remember { mutableStateOf("com.example") }
    var creationMode by remember { mutableStateOf(CreationMode.TERMINAL_SHELL) }
    var isCreating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var creationLog by remember { mutableStateOf("") }

    val validationError = remember(projectName) {
        if (projectName.isEmpty()) null else projectManager.validateProjectName(projectName)
    }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Flutter Project",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = {
                        projectName = it.trim().lowercase()
                        errorMessage = null
                    },
                    label = { Text("Project Name") },
                    placeholder = { Text("e.g. counter_app") },
                    isError = validationError != null || errorMessage != null,
                    supportingText = {
                        val msg = validationError ?: errorMessage
                        if (msg != null) {
                            Text(text = msg, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Lowercase letters, digits, and underscores only.")
                        }
                    },
                    singleLine = true,
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = orgName,
                    onValueChange = { orgName = it.trim() },
                    label = { Text("Organization (optional)") },
                    placeholder = { Text("com.example") },
                    singleLine = true,
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Creation Method:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                // Shell Creation Option (User's preferred mode)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = creationMode == CreationMode.TERMINAL_SHELL,
                        onClick = { creationMode = CreationMode.TERMINAL_SHELL },
                        enabled = !isCreating
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run in Shell (Interactive)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        Text(
                            text = "Opens native terminal with 'flutter create $projectName'",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Background Creation Option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = creationMode == CreationMode.BACKGROUND_TASK,
                        onClick = { creationMode = CreationMode.BACKGROUND_TASK },
                        enabled = !isCreating
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Quick Create (Direct)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        Text(
                            text = "Executes command and immediately opens the IDE",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isCreating) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = creationLog.ifBlank { "Executing flutter create..." },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val err = projectManager.validateProjectName(projectName)
                    if (err != null) {
                        errorMessage = err
                        return@Button
                    }

                    if (creationMode == CreationMode.TERMINAL_SHELL) {
                        val orgFlag = if (orgName.isNotBlank()) "--org $orgName" else ""
                        val command = "flutter create $orgFlag /home/flide/projects/$projectName && cd /home/flide/projects/$projectName"
                        onDismiss()
                        onOpenInTerminal(projectName, command)
                    } else {
                        isCreating = true
                        errorMessage = null
                        coroutineScope.launch {
                            val res = projectManager.createProject(
                                name = projectName,
                                org = orgName.ifBlank { null },
                                onOutput = { line ->
                                    creationLog = line
                                }
                            )
                            isCreating = false
                            res.onSuccess { proj ->
                                onDismiss()
                                onProjectCreated(proj)
                            }.onFailure { ex ->
                                errorMessage = ex.message ?: "Failed creating project."
                            }
                        }
                    }
                },
                enabled = !isCreating && projectName.isNotBlank() && validationError == null
            ) {
                Text(if (creationMode == CreationMode.TERMINAL_SHELL) "Create in Shell" else "Create Project")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating
            ) {
                Text("Cancel")
            }
        }
    )
}
