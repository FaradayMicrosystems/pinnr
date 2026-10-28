package com.pinnr.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pinnr.app.data.Category
import com.pinnr.app.data.CategoryWithCount
import com.pinnr.app.data.PinRepository
import com.pinnr.app.export.ExportFormat
import com.pinnr.app.export.Exporter
import kotlinx.coroutines.launch

val PALETTE = listOf(
    0xFFD84315, 0xFFC62828, 0xFFAD1457, 0xFF6A1B9A, 0xFF283593, 0xFF1565C0,
    0xFF00838F, 0xFF2E7D32, 0xFF9E9D24, 0xFFF9A825, 0xFF4E342E, 0xFF455A64,
).map { it.toInt() }

val SIZE_LABELS = listOf("Small", "Medium", "Large")

/** Download + Share entries for each export format. [onPick] gets (format, download). */
@Composable
fun ExportMenuItems(prefix: String = "", onPick: (ExportFormat, Boolean) -> Unit) {
    ExportFormat.entries.forEach { f ->
        DropdownMenuItem(
            text = { Text("Download $prefix${f.name}") },
            leadingIcon = { Icon(Icons.Default.Download, null) },
            onClick = { onPick(f, true) },
        )
        DropdownMenuItem(
            text = { Text("Share $prefix${f.name}") },
            leadingIcon = { Icon(Icons.Default.Share, null) },
            onClick = { onPick(f, false) },
        )
    }
}

@Composable
fun DeleteCategoryDialogs(
    category: Category,
    pinCount: Int,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit,
) {
    var step by remember(category.id) { mutableIntStateOf(1) }
    val pinsText = "$pinCount pin${if (pinCount == 1) "" else "s"}"
    if (step == 1) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text("Delete \"${category.name}\"?") },
            text = { Text("This tag and its $pinsText will be removed. Export first if you want to keep them.") },
            confirmButton = { TextButton(onClick = { step = 2 }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Are you sure?") },
            text = { Text("\"${category.name}\" and $pinsText will be permanently deleted. This can't be undone.") },
            confirmButton = {
                Button(
                    onClick = onConfirmed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Delete permanently") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Keep it") } },
        )
    }
}

@Composable
fun CategoryDialog(
    title: String,
    initial: Category?,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableIntStateOf(initial?.color ?: PALETTE.first()) }
    var size by remember { mutableIntStateOf(initial?.tileSize?.coerceIn(1, 3) ?: 1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30) },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text("Tile size", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SIZE_LABELS.forEachIndexed { i, label ->
                        FilterChip(
                            selected = size == i + 1,
                            onClick = { size = i + 1 },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                PALETTE.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEach { c ->
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                                    .then(
                                        if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { color = c }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), color, size) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Small top-left button: backs up every tag and pin to Downloads/Pinnr. */
@Composable
fun BackupButton(repo: PinRepository) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    IconButton(
        enabled = !working,
        onClick = {
            working = true
            scope.launch {
                val where = Exporter.backup(ctx, repo.dao.allPins(), repo.dao.allCategories())
                working = false
                android.widget.Toast.makeText(
                    ctx,
                    if (where != null) "Backed up to $where" else "Backup failed",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        },
    ) {
        if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(Icons.Default.Backup, "Back up pins")
    }
}

/**
 * Top-bar update button. Checks GitHub once when shown (a dot appears if a newer
 * build exists); tap to see it, back up, download and install over the current app.
 */
@Composable
fun UpdateButton(repo: PinRepository) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf<com.pinnr.app.update.Update?>(null) }
    var dialog by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun check(): Boolean = try {
        available = com.pinnr.app.update.Updater.check(); true
    } catch (e: Exception) { false }

    LaunchedEffect(Unit) { check() }

    IconButton(onClick = {
        dialog = true; message = null
        if (available == null) {
            checking = true
            scope.launch {
                if (!check()) message = "Couldn't reach GitHub. Check your connection."
                checking = false
            }
        }
    }) {
        BadgedBox(badge = { if (available != null) Badge() }) {
            Icon(Icons.Default.SystemUpdate, "Check for updates")
        }
    }

    if (dialog) {
        val up = available
        AlertDialog(
            onDismissRequest = { if (progress == null) dialog = false },
            icon = { Icon(Icons.Default.SystemUpdate, null) },
            title = { Text(if (up != null) "Update available" else "Updates") },
            text = {
                Column {
                    Text("Installed: build ${com.pinnr.app.update.Updater.currentBuild}")
                    when {
                        checking -> Text("Checking…")
                        message != null -> Text(message!!)
                        up != null -> {
                            Text("New: build ${up.build}")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Your tags and pins are kept. A backup is saved to Downloads/Pinnr first, just in case.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        else -> Text("You're on the newest build.")
                    }
                    progress?.let {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                if (up != null && progress == null) TextButton(onClick = {
                    if (!com.pinnr.app.update.Updater.canInstall(ctx)) {
                        message = "Allow Pinnr to install apps, then come back and tap Update again."
                        com.pinnr.app.update.Updater.openInstallPermission(ctx)
                        return@TextButton
                    }
                    progress = 0f; message = null
                    scope.launch {
                        try {
                            Exporter.backup(ctx, repo.dao.allPins(), repo.dao.allCategories())
                            val apk = com.pinnr.app.update.Updater.download(ctx, up) { progress = it }
                            progress = null; dialog = false
                            com.pinnr.app.update.Updater.install(ctx, apk)
                        } catch (e: Exception) {
                            progress = null
                            message = "Update failed: ${e.message ?: "unknown error"}"
                        }
                    }
                }) { Text("Update") }
            },
            dismissButton = {
                if (progress == null) TextButton(onClick = { dialog = false }) { Text("Close") }
            },
        )
    }
}
