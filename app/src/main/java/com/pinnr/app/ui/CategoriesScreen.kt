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
    var editing by remember { mutableStateOf<Category?>(null) }
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!hasPermission) {
                item(span = { GridItemSpan(maxLineSpan) }) {
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Tap to open · long-press to edit size and colour · car icon = shown in Android Auto",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(
                cats,
                key = { it.category.id },
                span = { GridItemSpan(it.category.tileSize.coerceIn(1, 3).coerceAtMost(maxLineSpan)) },
            ) { cw ->
                CategoryTile(
                    cw = cw,
                    onOpen = { onOpen(cw.category.id) },
                    onEdit = { editing = cw.category },
                    onToggleCar = { on -> scope.launch { repo.dao.updateCategory(cw.category.copy(showInCar = on)) } },
                )
            }
        }
    }

    if (showAdd) {
        CategoryDialog(
            title = "New category",
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
            title = "Edit category",
            initial = c,
            onDismiss = { editing = null },
            onSave = { name, color, size ->
                editing = null
                scope.launch { repo.dao.updateCategory(c.copy(name = name, color = color, tileSize = size)) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryTile(
    cw: CategoryWithCount,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggleCar: (Boolean) -> Unit,
) {
    val c = cw.category
    val size = c.tileSize.coerceIn(1, 3)
    val height = when (size) { 1 -> 120.dp; 2 -> 140.dp; else -> 160.dp }
    val bg = Color(c.color)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .combinedClickable(onClick = onOpen, onLongClick = onEdit)
            .padding(14.dp)
    ) {
        Icon(
            Icons.Default.Place, null, tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.TopStart).size(if (size == 1) 28.dp else 36.dp),
        )
        IconToggleButton(
            checked = c.showInCar,
            onCheckedChange = onToggleCar,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-10).dp),
        ) {
            Icon(
                Icons.Default.DirectionsCar,
                if (c.showInCar) "Shown in car" else "Hidden in car",
                tint = if (c.showInCar) Color.White else Color.White.copy(alpha = 0.35f),
            )
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(
                c.name, color = Color.White, maxLines = if (size == 1) 2 else 1, overflow = TextOverflow.Ellipsis,
                style = when (size) {
                    1 -> MaterialTheme.typography.titleSmall
                    2 -> MaterialTheme.typography.titleLarge
                    else -> MaterialTheme.typography.headlineSmall
                },
            )
            Text(
                "${cw.pinCount} pin${if (cw.pinCount == 1) "" else "s"}",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
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
