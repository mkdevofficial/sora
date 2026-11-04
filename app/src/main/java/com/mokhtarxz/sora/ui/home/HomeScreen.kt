package com.mokhtarxz.sora.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mokhtarxz.sora.SoraApplication
import com.mokhtarxz.sora.data.settings.IdeSettings
import com.mokhtarxz.sora.data.update.AppRelease
import com.mokhtarxz.sora.project.FlutterProject
import com.mokhtarxz.sora.ui.guide.Android12GuideDialog
import com.mokhtarxz.sora.ui.project.CreateProjectDialog
import com.mokhtarxz.sora.ui.storage.StorageCleanerDialog
import com.mokhtarxz.sora.ui.update.AppUpdateDialog
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSetup: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToFlutterSetup: () -> Unit,
    onOpenProject: (FlutterProject) -> Unit,
    onOpenInTerminalWithCommand: (workingDir: String, command: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val app = SoraApplication.instance
    val settings by app.settingsRepository.settingsFlow.collectAsState(initial = IdeSettings())
    val flutterState by app.flutterManager.state.collectAsState()
    val projects by app.projectManager.projects.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<FlutterProject?>(null) }
    var showCleanerDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var availableRelease by remember { mutableStateOf<AppRelease?>(null) }

    val storageLayout = app.storageLayout
    val freeSpaceGb = storageLayout.getAvailableStorageBytes() / (1024f * 1024f * 1024f)

    LaunchedEffect(Unit) {
        app.projectManager.scanProjects()
        val updateRes = app.appUpdateManager.checkForUpdates()
        if (updateRes.isSuccess && updateRes.getOrNull()?.isNewer == true) {
            availableRelease = updateRes.getOrNull()
        }
    }

    if (showCleanerDialog) {
        StorageCleanerDialog(onDismiss = { showCleanerDialog = false })
    }

    if (showGuideDialog) {
        Android12GuideDialog(onDismiss = { showGuideDialog = false })
    }

    availableRelease?.let { rel ->
        AppUpdateDialog(release = rel, onDismiss = { availableRelease = null })
    }

    if (showCreateProjectDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateProjectDialog = false },
            onProjectCreated = { proj ->
                app.projectManager.selectProject(proj)
                onOpenProject(proj)
            },
            onOpenInTerminal = { projectName, command ->
                onOpenInTerminalWithCommand("/home/flide/projects", command)
            }
        )
    }

    projectToDelete?.let { proj ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project") },
            text = { Text("Are you sure you want to delete '${proj.name}'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        app.projectManager.deleteProject(proj)
                        projectToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Sora",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { showCleanerDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Storage Cleaner",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showGuideDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Help,
                            contentDescription = "Android 12+ Optimization Guide"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val context = LocalContext.current
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            } else true

            if (!isIgnoringBatteryOptimizations) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2E2218)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color(0xFFFFB74D)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Background Execution",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFFB74D)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To allow Flide and the Flutter web-server to run reliably in the background when the app is minimized or screen is locked, disable battery optimization.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE0E0E0)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e2: Exception) {}
                                }
                            }
                        ) {
                            Text("Disable Battery Optimization")
                        }
                    }
                }
            }

            // Environment Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Linux Userland (Phase 1)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (settings.environmentConfigured) {
                            "Status: Debian arm64 environment ready"
                        } else {
                            "Status: Not initialized. Ready for Phase 1 setup."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (settings.environmentConfigured) {
                            Button(onClick = onNavigateToTerminal) {
                                Text(text = "Open Terminal")
                            }
                            Button(onClick = onNavigateToSetup) {
                                Text(text = "Environment Details")
                            }
                        } else {
                            Button(onClick = onNavigateToSetup) {
                                Text(text = "Set Up Environment")
                            }
                        }
                    }
                }
            }

            // Flutter SDK Card (Phase 2)
            if (settings.environmentConfigured) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (flutterState is com.mokhtarxz.sora.flutter.FlutterState.Ready) {
                            Color(0xFF1B3B22)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (flutterState is com.mokhtarxz.sora.flutter.FlutterState.Ready) Color(0xFF81C784) else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Flutter SDK (Phase 2)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (flutterState is com.mokhtarxz.sora.flutter.FlutterState.Ready) Color(0xFF81C784) else Color.Unspecified
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (val state = flutterState) {
                                is com.mokhtarxz.sora.flutter.FlutterState.Ready ->
                                    "Status: Flutter ${state.version} (${state.channel}) ready in rootfs"
                                is com.mokhtarxz.sora.flutter.FlutterState.NotInstalled ->
                                    "Status: Not installed. Ready to install Flutter SDK."
                                is com.mokhtarxz.sora.flutter.FlutterState.Error ->
                                    "Status: Setup error: ${state.message}"
                                else ->
                                    "Status: Installing / configuring Flutter SDK..."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onNavigateToFlutterSetup) {
                            Text(
                                text = if (flutterState is com.mokhtarxz.sora.flutter.FlutterState.Ready) {
                                    "Flutter Details & Doctor"
                                } else {
                                    "Install Flutter SDK"
                                }
                            )
                        }
                    }
                }
            }

            // Flutter Projects Header & Actions (Phase 3)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Projects (${projects.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { showCreateProjectDialog = true },
                    enabled = settings.environmentConfigured
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Project")
                }
            }

            // Projects List
            if (projects.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Projects Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'New Project' above to create your first Flutter project (in the shell or directly).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showCreateProjectDialog = true },
                            enabled = settings.environmentConfigured
                        ) {
                            Text("Create Flutter Project")
                        }
                    }
                }
            } else {
                projects.forEach { project ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = project.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (project.description.isNotBlank()) project.description else "Flutter application",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(onClick = { projectToDelete = project }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Project",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        app.projectManager.selectProject(project)
                                        onOpenProject(project)
                                    }
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open IDE & Run")
                                }

                                OutlinedButton(
                                    onClick = {
                                        onOpenInTerminalWithCommand(
                                            "/home/flide/projects/${project.name}",
                                            null
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Shell")
                                }

                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val destZip = File(storageLayout.downloadsDir, "${project.name}_backup.zip")
                                            app.projectBackupManager.exportProjectToZip(project, destZip)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Export ZIP")
                                }
                            }
                        }
                    }
                }
            }

            // Storage Details Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Device Storage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format(Locale.US, "Available Space: %.2f GB", freeSpaceGb),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Rootfs Path: ${storageLayout.rootfsDir.absolutePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Projects Path: ${storageLayout.projectsDir.absolutePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
