package com.flide.ui.env

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flide.FlideApplication
import com.flide.env.EnvironmentState
import com.flide.ui.theme.AccentGreen
import com.flide.ui.theme.AccentRed
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupEnvironmentScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = FlideApplication.instance
    val envManager = app.environmentManager
    val state by envManager.state.collectAsState()
    val logs by envManager.logs.collectAsState()

    val logListState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logListState.animateScrollToItem(logs.size - 1)
        }
    }

    val isRunning = state !is EnvironmentState.Idle &&
            state !is EnvironmentState.Ready &&
            state !is EnvironmentState.Error

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Linux Environment Setup",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status and Progress Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = getStepTitle(state),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        when (state) {
                            is EnvironmentState.Ready -> Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AccentGreen
                            )
                            is EnvironmentState.Error -> Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = AccentRed
                            )
                            else -> Unit
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = getStepDescription(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress bar
                    val progress = getProgressFloat(state)
                    if (isRunning && progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    } else if (isRunning) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }

                    // Progress details (speed, bytes)
                    if (state is EnvironmentState.Downloading) {
                        val d = state as EnvironmentState.Downloading
                        val dlMb = d.bytesDownloaded / (1024f * 1024f)
                        val totalMb = d.totalBytes / (1024f * 1024f)
                        val speedKb = d.speedBytesPerSec / 1024f

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f / %.1f MB (%.0f%%)", dlMb, totalMb, d.progress * 100),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = String.format(Locale.US, "%.0f KB/s", speedKb),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state is EnvironmentState.Ready) {
                    Button(
                        onClick = onNavigateToTerminal,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Open Debian Shell")
                    }
                } else if (!isRunning) {
                    Button(
                        onClick = { envManager.startSetup() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (state is EnvironmentState.Error) "Retry Setup" else "Start Setup")
                    }
                } else {
                    Button(
                        onClick = { envManager.cancelSetup() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Cancel")
                    }
                }

                OutlinedButton(
                    onClick = { envManager.resetEnvironment() },
                    enabled = !isRunning
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Reset")
                }
            }

            // Real-time Console Log Header & Box
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF141414)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Console Output (${logs.size} lines)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.LightGray
                            )
                        )

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Flide Setup Logs", logs.joinToString("\n"))
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Log",
                                tint = Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0A0A0A), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        if (logs.isEmpty()) {
                            Text(
                                text = "Ready to begin setup. Tap 'Start Setup' above.",
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        } else {
                            LazyColumn(
                                state = logListState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(logs) { line ->
                                    val color = when {
                                        line.startsWith("[x]") -> AccentRed
                                        line.startsWith("[✓]") -> AccentGreen
                                        line.startsWith("[!]") -> Color(0xFFFFB74D)
                                        line.startsWith("===") -> MaterialTheme.colorScheme.primary
                                        else -> Color(0xFFE0E0E0)
                                    }
                                    Text(
                                        text = line,
                                        color = color,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getStepTitle(state: EnvironmentState): String {
    return when (state) {
        is EnvironmentState.Idle -> if (state.isInstalled) "Environment Installed" else "Not Initialized"
        is EnvironmentState.CheckingStorage -> "Step 1/6: Storage Check"
        is EnvironmentState.StagingProot -> "Step 2/6: Staging PRoot Engine"
        is EnvironmentState.Downloading -> "Step 3/6: Downloading Debian Rootfs"
        is EnvironmentState.VerifyingChecksum -> "Step 4/6: Verifying SHA-256 Checksum"
        is EnvironmentState.Extracting -> "Step 5/6: Extracting Rootfs"
        is EnvironmentState.Configuring -> "Step 6/6: Configuring Environment"
        is EnvironmentState.Ready -> "Ready for Development"
        is EnvironmentState.Error -> "Setup Error"
    }
}

private fun getStepDescription(state: EnvironmentState): String {
    return when (state) {
        is EnvironmentState.Idle -> if (state.isInstalled) "The Debian arm64 environment is configured and ready." else "Tap 'Start Setup' to download and initialize the Debian arm64 userland."
        is EnvironmentState.CheckingStorage -> "Verifying device storage space (minimum 2.5 GB required)..."
        is EnvironmentState.StagingProot -> "Deploying Bionic-compatible PRoot engine and shared libraries..."
        is EnvironmentState.Downloading -> "Downloading Debian Bookworm arm64 archive from GitHub..."
        is EnvironmentState.VerifyingChecksum -> "Computing SHA-256 hash to ensure download integrity..."
        is EnvironmentState.Extracting -> "Extracting Linux root filesystem packages..."
        is EnvironmentState.Configuring -> state.stepDescription
        is EnvironmentState.Ready -> "Debian arm64 Linux userland is fully configured and ready."
        is EnvironmentState.Error -> state.message
    }
}

private fun getProgressFloat(state: EnvironmentState): Float? {
    return when (state) {
        is EnvironmentState.Downloading -> state.progress
        is EnvironmentState.VerifyingChecksum -> state.progress
        is EnvironmentState.Extracting -> state.progress
        else -> null
    }
}
