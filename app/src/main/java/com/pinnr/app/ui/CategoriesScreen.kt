package com.pinnr.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pinnr.app.data.Category
import com.pinnr.app.data.PinRepository
import com.pinnr.app.export.ExportFormat
import com.pinnr.app.export.Exporter
import kotlinx.coroutines.launch

val PALETTE = listOf(
    0xFFD84315, 0xFFC62828, 0xFFAD1457, 0xFF6A1B9A, 0xFF283593, 0xFF1565C0,
    0xFF00838F, 0xFF2E7D32, 0xFF9E9D24, 0xFFF9A825, 0xFF4E342E, 0xFF455A64,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    repo: PinRepository,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val cats by repo.dao.categoriesWithCounts().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pinnr") },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.Share, "Export all") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ExportFormat.entries.forEach { f ->
                            DropdownMenuItem(
                                text = { Text("Export all as ${f.name}") },
                                onClick = {
                                    menu = false
                                    scope.launch {
                                        Exporter.share(ctx, "pinnr_all", f, repo.dao.allPins(), repo.dao.allCategories())
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New category") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!hasPermission) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Location access is off", style = MaterialTheme.typography.titleMedium)
                            Text("Pinnr needs precise location to drop pins, including from Android Auto.")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onRequestPermission) { Text("Allow location") }
                        }
                    }
                }
            }
            item {
                Text(
                    "Categories marked with the car icon appear as one-tap buttons in Android Auto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(cats, key = { it.category.id }) { cw ->
                val c = cw.category
                Card(Modifier.fillMaxWidth().clickable { onOpen(c.id) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, null, tint = Color(c.color), modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${cw.pinCount} pin${if (cw.pinCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconToggleButton(
                            checked = c.showInCar,
                            onCheckedChange = { on -> scope.launch { repo.dao.updateCategory(c.copy(showInCar = on)) } },
                        ) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                if (c.showInCar) "Shown in car" else "Hidden in car",
                                tint = if (c.showInCar) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        CategoryDialog(
            title = "New category",
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { name, color ->
                showAdd = false
                scope.launch {
                    repo.dao.insertCategory(Category(name = name, color = color, sortOrder = cats.size))
                }
            },
        )
    }
}

@Composable
fun CategoryDialog(
    title: String,
    initial: Category?,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableIntStateOf(initial?.color ?: PALETTE.first()) }
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
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), color) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
