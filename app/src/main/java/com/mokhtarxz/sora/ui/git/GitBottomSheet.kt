package com.mokhtarxz.sora.ui.git

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.mokhtarxz.sora.git.GitCommit
import com.mokhtarxz.sora.git.GitFileEntry
import com.mokhtarxz.sora.git.GitFileStatus
import com.mokhtarxz.sora.git.GitStatus
import com.mokhtarxz.sora.project.FlutterProject
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitBottomSheet(
    project: FlutterProject,
    onDismiss: () -> Unit,
    onFilesChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gitManager = SoraApplication.instance.gitManager
    val coroutineScope = rememberCoroutineScope()

    var status by remember { mutableStateOf(GitStatus()) }
    var branches by remember { mutableStateOf(listOf("main")) }
    var commits by remember { mutableStateOf(emptyList<GitCommit>()) }
    var commitMessage by remember { mutableStateOf("") }
    var isOperating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedDiff by remember { mutableStateOf<Pair<String, String>?>(null) } // File to diff text

    var showBranchDialog by remember { mutableStateOf(false) }
    var newBranchName by remember { mutableStateOf("") }

    fun refresh() {
        coroutineScope.launch {
            isOperating = true
            try {
                if (!gitManager.isGitRepo(project)) {
                    gitManager.initRepo(project)
                }
                status = gitManager.getStatus(project)
                branches = gitManager.getBranches(project)
                commits = gitManager.getLog(project, 8)
                onFilesChanged()
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isOperating = false
            }
        }
    }

    LaunchedEffect(project) {
        refresh()
    }

    // Diff Viewer Dialog
    selectedDiff?.let { (file, diffContent) ->
        AlertDialog(
            onDismissRequest = { selectedDiff = null },
            title = { Text("Diff: $file") },
            text = {
                Surface(
                    color = Color(0xFF1E1E24),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().height(300.dp)
                ) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        items(diffContent.lines()) { line ->
                            val color = when {
                                line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF69F0AE)
                                line.startsWith("-") && !line.startsWith("---") -> Color(0xFFFF5252)
                                line.startsWith("@@") -> Color(0xFF80D8FF)
                                else -> Color(0xFFD4D4D4)
                            }
                            Text(
                                text = line,
                                color = color,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDiff = null }) { Text("Close") }
            }
        )
    }

    // Branch Switcher Dialog
    if (showBranchDialog) {
        AlertDialog(
            onDismissRequest = { showBranchDialog = false },
            title = { Text("Switch Branch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Current branches:", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(modifier = Modifier.height(140.dp)) {
                        items(branches) { b ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            gitManager.checkoutBranch(project, b)
                                            showBranchDialog = false
                                            refresh()
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = b + if (b == status.branch) " (current)" else "",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (b == status.branch) FontWeight.Bold else FontWeight.Normal,
                                    color = if (b == status.branch) MaterialTheme.colorScheme.primary else Color.Unspecified
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newBranchName,
                        onValueChange = { newBranchName = it },
                        label = { Text("Create new branch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newBranchName.isNotBlank()) {
                            coroutineScope.launch {
                                gitManager.checkoutBranch(project, newBranchName.trim(), createNew = true)
                                newBranchName = ""
                                showBranchDialog = false
                                refresh()
                            }
                        }
                    }
                ) {
                    Text("Create & Checkout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBranchDialog = false }) { Text("Cancel") }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header: Branch and Refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { showBranchDialog = true }
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallSplit,
                        contentDescription = "Branch",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = status.branch,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = {
                            coroutineScope.launch {
                                gitManager.stageAll(project)
                                refresh()
                            }
                        },
                        enabled = !status.isClean && !isOperating
                    ) {
                        Text("Stage All", fontSize = 11.5.sp)
                    }

                    TextButton(onClick = { refresh() }, enabled = !isOperating) {
                        Text("Refresh", fontSize = 11.5.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Commit input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    placeholder = { Text("Commit message...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        coroutineScope.launch {
                            isOperating = true
                            // If no staged files but unstaged exist, stage all first
                            if (status.stagedFiles.isEmpty() && (!status.unstagedFiles.isEmpty() || !status.untrackedFiles.isEmpty())) {
                                gitManager.stageAll(project)
                            }
                            val res = gitManager.commit(project, commitMessage)
                            if (res.isSuccess) {
                                commitMessage = ""
                                refresh()
                            } else {
                                errorMessage = res.exceptionOrNull()?.message
                            }
                            isOperating = false
                        }
                    },
                    enabled = commitMessage.isNotBlank() && !isOperating
                ) {
                    Text("Commit")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Changes List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Section: Staged Changes
                if (status.stagedFiles.isNotEmpty()) {
                    item {
                        Text(
                            text = "STAGED CHANGES (${status.stagedFiles.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF69F0AE)
                        )
                    }
                    items(status.stagedFiles) { entry ->
                        GitFileRow(
                            entry = entry,
                            isStaged = true,
                            onToggleStage = {
                                coroutineScope.launch {
                                    gitManager.unstageFiles(project, listOf(entry.path))
                                    refresh()
                                }
                            },
                            onViewDiff = {
                                coroutineScope.launch {
                                    val diff = gitManager.getDiff(project, entry.path, staged = true)
                                    selectedDiff = entry.path to diff.ifBlank { "No changes in index" }
                                }
                            }
                        )
                    }
                }

                // Section: Working Tree Changes
                if (status.unstagedFiles.isNotEmpty()) {
                    item {
                        Text(
                            text = "CHANGES (${status.unstagedFiles.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD740)
                        )
                    }
                    items(status.unstagedFiles) { entry ->
                        GitFileRow(
                            entry = entry,
                            isStaged = false,
                            onToggleStage = {
                                coroutineScope.launch {
                                    gitManager.stageFiles(project, listOf(entry.path))
                                    refresh()
                                }
                            },
                            onViewDiff = {
                                coroutineScope.launch {
                                    val diff = gitManager.getDiff(project, entry.path, staged = false)
                                    selectedDiff = entry.path to diff.ifBlank { "No changes in worktree" }
                                }
                            }
                        )
                    }
                }

                // Section: Untracked Files
                if (status.untrackedFiles.isNotEmpty()) {
                    item {
                        Text(
                            text = "UNTRACKED FILES (${status.untrackedFiles.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF80D8FF)
                        )
                    }
                    items(status.untrackedFiles) { path ->
                        GitFileRow(
                            entry = GitFileEntry(path, GitFileStatus.UNTRACKED),
                            isStaged = false,
                            onToggleStage = {
                                coroutineScope.launch {
                                    gitManager.stageFiles(project, listOf(path))
                                    refresh()
                                }
                            },
                            onViewDiff = {}
                        )
                    }
                }

                // Clean repository indicator
                if (status.isClean) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF69F0AE),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Working tree clean. No uncommitted changes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Recent Commits
                if (commits.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "RECENT COMMITS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    items(commits) { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = c.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${c.shortHash} by ${c.author} on ${c.date}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitFileRow(
    entry: GitFileEntry,
    isStaged: Boolean,
    onToggleStage: () -> Unit,
    onViewDiff: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF1E1E24),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable { onViewDiff() }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val statusBadgeColor = when (entry.status) {
                    GitFileStatus.MODIFIED -> Color(0xFFFFD740)
                    GitFileStatus.ADDED -> Color(0xFF69F0AE)
                    GitFileStatus.DELETED -> Color(0xFFFF5252)
                    GitFileStatus.RENAMED -> Color(0xFF80D8FF)
                    GitFileStatus.UNTRACKED -> Color(0xFFB0BEC5)
                }
                val statusText = when (entry.status) {
                    GitFileStatus.MODIFIED -> "M"
                    GitFileStatus.ADDED -> "A"
                    GitFileStatus.DELETED -> "D"
                    GitFileStatus.RENAMED -> "R"
                    GitFileStatus.UNTRACKED -> "U"
                }

                Text(
                    text = statusText,
                    color = statusBadgeColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.width(18.dp)
                )

                Text(
                    text = entry.path,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            IconButton(onClick = onToggleStage, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (isStaged) Icons.Default.Remove else Icons.Default.Add,
                    contentDescription = if (isStaged) "Unstage" else "Stage",
                    tint = if (isStaged) Color(0xFFFF8A65) else Color(0xFF69F0AE),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
