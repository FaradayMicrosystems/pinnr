package com.pinnr.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pinnr.app.data.Category
import com.pinnr.app.data.CategoryWithCount
import com.pinnr.app.data.PinRepository
import com.pinnr.app.export.Exporter
import com.pinnr.app.export.Importer
import kotlinx.coroutines.launch

/** View tag lists, export them, and import pins from CSV. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagementScreen(
    repo: PinRepository,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cats by repo.dao.categoriesWithCounts().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var exportAllMenu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<CategoryWithCount?>(null) }
    var importing by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val r = Importer.importCsv(ctx, uri, repo.dao)
            importing = false
            snackbar.showSnackbar(
                r.error ?: buildString {
                    append("Imported ${r.added} pin${if (r.added == 1) "" else "s"}")
                    if (r.newTags > 0) append(" · ${r.newTags} new tag${if (r.newTags == 1) "" else "s"}")
                    if (r.skipped > 0) append(" · ${r.skipped} skipped")
                },
                withDismissAction = true,
            )
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { BackupButton(repo) },
                title = { Text("Tag Management") },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !importing) {
                        if (importing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.FileUpload, "Import CSV")
                    }
                    IconButton(onClick = { exportAllMenu = true }) { Icon(Icons.Default.Download, "Export all") }
                    DropdownMenu(expanded = exportAllMenu, onDismissRequest = { exportAllMenu = false }) {
                        ExportMenuItems(prefix = "all as ") { f, dl ->
                            exportAllMenu = false
                            scope.launch {
                                Exporter.export(ctx, "all", f, repo.dao.allPins(), repo.dao.allCategories(), dl)
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New tag") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Tap a tag to view and edit its pins. Import: CSV from Pinnr, a backup, or Google My Maps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(cats, key = { it.category.id }) { cw ->
                TagRow(
                    cw = cw,
                    onOpen = { onOpen(cw.category.id) },
                    onEdit = { editing = cw.category },
                    onDelete = { deleting = cw },
                    onToggleCar = { on -> scope.launch { repo.dao.updateCategory(cw.category.copy(showInCar = on)) } },
                    onExport = { f, dl ->
                        scope.launch {
                            Exporter.export(ctx, cw.category.name, f, repo.dao.pinsForExport(cw.category.id), listOf(cw.category), dl)
                        }
                    },
                )
            }
        }
    }

    if (showAdd) {
        CategoryDialog(
            title = "New tag",
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { name, color, size ->
                showAdd = false
                scope.launch {
                    repo.dao.insertCategory(Category(name = name, color = color, tileSize = size, sortOrder = cats.size))
                }
            },
        )
    }
    editing?.let { c ->
        CategoryDialog(
            title = "Edit tag",
            initial = c,
            onDismiss = { editing = null },
            onSave = { name, color, size ->
                editing = null
                scope.launch { repo.dao.updateCategory(c.copy(name = name, color = color, tileSize = size)) }
            },
        )
    }
    deleting?.let { cw ->
        DeleteCategoryDialogs(
            category = cw.category,
            pinCount = cw.pinCount,
            onDismiss = { deleting = null },
            onConfirmed = {
                deleting = null
                scope.launch { repo.dao.deleteCategory(cw.category) }
            },
        )
    }
}

@Composable
private fun TagRow(
    cw: CategoryWithCount,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleCar: (Boolean) -> Unit,
    onExport: (com.pinnr.app.export.ExportFormat, Boolean) -> Unit,
) {
    val c = cw.category
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Place, null, tint = Color(c.color), modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${cw.pinCount} pin${if (cw.pinCount == 1) "" else "s"}" + if (c.showInCar) " · in Android Auto" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("View pins") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                        onClick = { menu = false; onOpen() },
                    )
                    ExportMenuItems { f, dl -> menu = false; onExport(f, dl) }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Edit tag") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (c.showInCar) "Hide in Android Auto" else "Show in Android Auto") },
                        leadingIcon = { Icon(Icons.Default.DirectionsCar, null) },
                        onClick = { menu = false; onToggleCar(!c.showInCar) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

