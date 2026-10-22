package com.pinnr.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pinnr.app.data.Category
import com.pinnr.app.data.DropResult
import com.pinnr.app.data.Pin
import com.pinnr.app.data.PinRepository
import com.pinnr.app.export.ExportFormat
import com.pinnr.app.export.Exporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinsScreen(repo: PinRepository, categoryId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var category by remember { mutableStateOf<Category?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(categoryId, reload) { category = repo.dao.category(categoryId) }
    val pins by repo.dao.pinsFor(categoryId).collectAsState(initial = emptyList())

    var editingNote by remember { mutableStateOf<Pin?>(null) }
    var editCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var dropping by remember { mutableStateOf(false) }
    val cat = category

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(cat?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ExportFormat.entries.forEach { f ->
                            DropdownMenuItem(
                                text = { Text("Export as ${f.name}") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick = {
                                    menu = false
                                    val c = cat ?: return@DropdownMenuItem
                                    scope.launch {
                                        Exporter.share(ctx, c.name, f, repo.dao.pinsForExport(c.id), listOf(c))
                                    }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Edit category") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menu = false; editCategory = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete category") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (dropping) return@ExtendedFloatingActionButton
                    dropping = true
                    scope.launch {
                        val msg = when (val r = repo.dropPin(categoryId)) {
                            is DropResult.Dropped -> "Pinned to ${r.categoryName}" +
                                (r.accuracyM?.let { " (±${it.toInt()} m)" } ?: "")
                            DropResult.NoPermission -> "Allow location access first"
                            DropResult.NoLocation -> "Couldn't get your location"
                            null -> null
                        }
                        dropping = false
                        msg?.let { snackbar.showSnackbar(it) }
                    }
                },
                icon = {
                    if (dropping) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.AddLocationAlt, null)
                },
                text = { Text("Pin my location") },
            )
        },
    ) { pad ->
        if (pins.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No pins yet.\nTap a tile in Android Auto, or use the button below.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pins, key = { it.id }) { pin ->
                    PinCard(
                        pin = pin,
                        color = Color(cat?.color ?: 0xFF888888.toInt()),
                        onEditNote = { editingNote = pin },
                        onOpenMap = {
                            val label = Uri.encode(cat?.name ?: "Pin")
                            val uri = Uri.parse("geo:${pin.lat},${pin.lng}?q=${pin.lat},${pin.lng}($label)")
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        },
                        onDelete = {
                            scope.launch {
                                repo.dao.deletePin(pin.id)
                                val res = snackbar.showSnackbar("Pin deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                                if (res == SnackbarResult.ActionPerformed) repo.dao.insertPin(pin)
                            }
                        },
                    )
                }
            }
        }
    }

    editingNote?.let { pin ->
        var text by remember(pin.id) { mutableStateOf(pin.note ?: "") }
        AlertDialog(
            onDismissRequest = { editingNote = null },
            title = { Text("Note") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 3,
                    placeholder = { Text("What was here?") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.dao.setNote(pin.id, text.trim().ifEmpty { null }) }
                    editingNote = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingNote = null }) { Text("Cancel") } },
        )
    }

    if (editCategory && cat != null) {
        CategoryDialog(
            title = "Edit category",
            initial = cat,
            onDismiss = { editCategory = false },
            onSave = { name, color, size ->
                editCategory = false
                scope.launch {
                    repo.dao.updateCategory(cat.copy(name = name, color = color, tileSize = size))
                    reload++
                }
            },
        )
    }

    if (confirmDelete && cat != null) {
        DeleteCategoryDialogs(
            category = cat,
            pinCount = pins.size,
            onDismiss = { confirmDelete = false },
            onConfirmed = {
                confirmDelete = false
                scope.launch { repo.dao.deleteCategory(cat); onBack() }
            },
        )
    }
}

private val fmt = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault())

@Composable
private fun PinCard(
    pin: Pin,
    color: Color,
    onEditNote: () -> Unit,
    onOpenMap: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onEditNote)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Icon(Icons.Default.Place, null, tint = color, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(fmt.format(Date(pin.timestamp)), style = MaterialTheme.typography.titleSmall)
                Text(
                    pin.address ?: "%.5f, %.5f".format(Locale.US, pin.lat, pin.lng),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                pin.accuracyM?.let {
                    Text(
                        "±${it.toInt()} m",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (it > 50) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    pin.note ?: "Tap to add a note",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pin.note == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
            }
            Column {
                IconButton(onClick = onOpenMap) { Icon(Icons.Default.Map, "Open in maps") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
            }
        }
    }
}
