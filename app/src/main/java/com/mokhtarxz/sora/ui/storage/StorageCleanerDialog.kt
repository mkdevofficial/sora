package com.mokhtarxz.sora.ui.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mokhtarxz.sora.SoraApplication
import com.mokhtarxz.sora.data.storage.StorageCleaner
import kotlinx.coroutines.launch

@Composable
fun StorageCleanerDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val storageCleaner = SoraApplication.instance.storageCleaner
    val coroutineScope = rememberCoroutineScope()

    var usage by remember { mutableStateOf<StorageCleaner.StorageUsage?>(null) }
    var isCleaning by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun refreshUsage() {
        coroutineScope.launch {
            usage = storageCleaner.calculateCleanableStorage()
        }
    }

    LaunchedEffect(Unit) {
        refreshUsage()
    }

    fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024f * 1024f)
        return if (mb >= 1024) {
            String.format("%.2f GB", mb / 1024f)
        } else {
            String.format("%.1f MB", mb)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Storage Cleaner")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Clean compiler artifacts and cached packages to recover internal device storage:",
                    style = MaterialTheme.typography.bodySmall
                )

                usage?.let { u ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pub Package Cache:", style = MaterialTheme.typography.bodyMedium)
                        Text(formatBytes(u.pubCacheBytes), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Project Build Outputs:", style = MaterialTheme.typography.bodyMedium)
                        Text(formatBytes(u.projectBuildsBytes), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Temporary Downloads:", style = MaterialTheme.typography.bodyMedium)
                        Text(formatBytes(u.downloadsBytes), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Cleanable:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(formatBytes(u.totalCleanableBytes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                } ?: run {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Calculating storage usage...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                statusMessage?.let {
                    Text(it, color = Color(0xFF69F0AE), style = MaterialTheme.typography.bodySmall)
                }

                if (isCleaning) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isCleaning = true
                        storageCleaner.cleanProjectBuilds()
                        storageCleaner.cleanDownloads()
                        storageCleaner.cleanPubCache()
                        refreshUsage()
                        isCleaning = false
                        statusMessage = "Cleanup completed successfully!"
                    }
                },
                enabled = !isCleaning
            ) {
                Text("Clean All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
