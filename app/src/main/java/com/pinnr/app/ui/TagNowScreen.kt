package com.pinnr.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pinnr.app.data.Category
import com.pinnr.app.data.CategoryWithCount
import com.pinnr.app.data.DropResult
import com.pinnr.app.data.PinRepository
import kotlinx.coroutines.launch

/** Home: tap a tag tile to drop a pin right here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagNowScreen(
    repo: PinRepository,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cats by repo.dao.categoriesWithCounts().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pinning by remember { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<CategoryWithCount?>(null) }

    fun drop(c: Category) {
        if (pinning != null) return
        pinning = c.id
        scope.launch {
            val r = repo.dropPin(c.id)
            pinning = null
            snackbar.currentSnackbarData?.dismiss()
            when (r) {
                is DropResult.Dropped -> {
                    val acc = r.accuracyM
                    val msg = "Pinned to ${r.categoryName}" +
                        if (acc != null && acc > 50) " · low accuracy ±${acc.toInt()} m" else ""
                    val res = snackbar.showSnackbar(msg, actionLabel = "Undo", duration = SnackbarDuration.Short)
                    if (res == SnackbarResult.ActionPerformed) repo.undo(r.pinId)
                }
                DropResult.NoPermission -> snackbar.showSnackbar("Allow location access first")
                DropResult.NoLocation -> snackbar.showSnackbar("Couldn't get your location")
                null -> {}
            }
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { BackupButton(repo) },
                title = { Text("Tag Now") },
                actions = {
                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "New tag") }
                },
            )
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
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
            val list = cats
            if (list != null && list.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No tags yet. Tap + to make one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 48.dp),
                    )
                }
            } else if (list != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Tap a tag to pin your location · hold for options",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(
                    list,
                    key = { it.category.id },
                    span = { GridItemSpan(it.category.tileSize.coerceIn(1, 3).coerceAtMost(maxLineSpan)) },
                ) { cw ->
                    TagTile(
                        cw = cw,
                        busy = pinning == cw.category.id,
                        onTap = { drop(cw.category) },
                        onEdit = { editing = cw.category },
                        onDelete = { deleting = cw },
                        onToggleCar = { on -> scope.launch { repo.dao.updateCategory(cw.category.copy(showInCar = on)) } },
                    )
                }
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
                    repo.dao.insertCategory(Category(name = name, color = color, tileSize = size, sortOrder = cats?.size ?: 0))
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagTile(
    cw: CategoryWithCount,
    busy: Boolean,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleCar: (Boolean) -> Unit,
) {
    val c = cw.category
    var menuOpen by remember { mutableStateOf(false) }
    val size = c.tileSize.coerceIn(1, 3)
    val height = when (size) { 1 -> 120.dp; 2 -> 140.dp; else -> 160.dp }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(c.color))
            .combinedClickable(onClick = onTap, onLongClick = { menuOpen = true })
            .padding(14.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(
                color = Color.White, strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.TopStart).size(if (size == 1) 28.dp else 36.dp),
            )
        } else {
            Icon(
                Icons.Default.AddLocationAlt, null, tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.TopStart).size(if (size == 1) 28.dp else 36.dp),
            )
        }
        Box(Modifier.align(Alignment.BottomEnd)) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit tag") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { menuOpen = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text(if (c.showInCar) "Hide in Android Auto" else "Show in Android Auto") },
                    leadingIcon = { Icon(Icons.Default.DirectionsCar, null) },
                    onClick = { menuOpen = false; onToggleCar(!c.showInCar) },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
        if (c.showInCar) {
            Icon(
                Icons.Default.DirectionsCar, "Shown in Android Auto",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.TopEnd).size(18.dp),
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
