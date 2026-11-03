package com.flide.ui.flutter

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.flide.FlideApplication
import com.flide.flutter.FlutterState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupFlutterScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = FlideApplication.instance
    val flutterManager = app.flutterManager
    val coroutineScope = rememberCoroutineScope()

    val flutterState by flutterManager.state.collectAsState()
    val logs by flutterManager.logs.collectAsState()

    var selectedChannel by remember { mutableStateOf("stable") }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    val isRunning = flutterState !is FlutterState.NotInstalled &&
            flutterState !is FlutterState.Ready &&
            flutterState !is FlutterState.Error

    fun copyLogToClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Flutter Setup Log", logs.joinToString("\n"))
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Flutter SDK Setup",
                        style = MaterialTheme.typography.titleMedium
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
                actions = {
                    IconButton(onClick = ::copyLogToClipboard) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Log"
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (flutterState) {
                        is FlutterState.Ready -> Color(0xFF1B3B22)
                        is FlutterState.Error -> Color(0xFF3B1B1B)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (flutterState) {
                            is FlutterState.Ready -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF81C784)
                                )
                                Text(
                                    text = "Flutter SDK Ready",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF81C784)
                                )
                            }
                            is FlutterState.Error -> {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = Color(0xFFE57373)
                                )
                                Text(
                                    text = "Setup Error",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE57373)
                                )
                            }
                            is FlutterState.NotInstalled -> {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Flutter Not Installed",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            else -> {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Setting Up Flutter...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val statusDetail = when (val state = flutterState) {
                        is FlutterState.NotInstalled ->
                            "Installs Flutter SDK (arm64), downloads Dart SDK, configures PATH, and enables web preview toolchain."
                        is FlutterState.CheckingStorage ->
                            "Checking available device storage: ${state.availableGb} GB..."
                        is FlutterState.InstallingPackages ->
                            "Step 1/5: ${state.progressMessage}"
                        is FlutterState.Cloning ->
                            "Step 2/5: Shallow cloning official Flutter ${state.channel} branch..."
                        is FlutterState.Bootstrapping ->
                            "Step 3/5: ${state.message}"
                        is FlutterState.Configuring ->
                            "Step 4/5: ${state.message}"
                        is FlutterState.RunningDoctor ->
                            "Step 5/5: ${state.message}"
                        is FlutterState.Ready ->
                            "Flutter version: ${state.version} (${state.channel} channel)\nWeb toolchain verified."
                        is FlutterState.Error ->
                            state.message
                    }

                    Text(
                        text = statusDetail,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (isRunning) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Channel Selector & Action Buttons
            if (!isRunning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Channel:", style = MaterialTheme.typography.labelLarge)
                    FilterChip(
                        selected = selectedChannel == "stable",
                        onClick = { selectedChannel = "stable" },
                        label = { Text("stable (recommended)") }
                    )
                    FilterChip(
                        selected = selectedChannel == "beta",
                        onClick = { selectedChannel = "beta" },
                        label = { Text("beta") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (flutterState is FlutterState.Ready) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    flutterManager.runDoctor()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Doctor")
                        }

                        OutlinedButton(
                            onClick = onNavigateToTerminal,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Terminal")
                        }
                    } else {
                        Button(
                            onClick = { flutterManager.installFlutter(selectedChannel) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (flutterState is FlutterState.Error) "Retry Setup" else "Install Flutter SDK")
                        }
                    }
                }
            } else {
                Button(
                    onClick = { flutterManager.cancelInstallation() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel Setup")
                }
            }

            // Live Output Console Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Installation & Toolchain Log",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${logs.size} lines",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Console Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF000000), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "Logs will appear here once Flutter installation or doctor verification begins...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(logs) { line ->
                            val color = when {
                                line.contains("[✓]") || line.contains("Successfully") -> Color(0xFF69F0AE)
                                line.contains("[x]") || line.contains("failed") || line.contains("Error") -> Color(0xFFFF5252)
                                line.contains("[!]") || line.contains("warning") -> Color(0xFFFFD740)
                                line.contains("===") || line.contains("[1/") || line.contains("[2/") || line.contains("[3/") || line.contains("[4/") || line.contains("[5/") || line.contains("[6/") -> MaterialTheme.colorScheme.primary
                                line.contains("[apt]") -> Color(0xFF80D8FF)
                                line.contains("[git]") -> Color(0xFFFFAB40)
                                line.contains("[flutter]") || line.contains("[doctor]") -> Color(0xFFB388FF)
                                else -> Color(0xFFDDDDDD)
                            }
                            Text(
                                text = line,
                                color = color,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
