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
    val allCats by repo.dao.categoriesWithCounts().collectAsState(initial = emptyList())
    val recycled by repo.dao.recycledFor(categoryId).collectAsState(initial = emptyList())
    val prefs = remember { ctx.getSharedPreferences("pinnr", android.content.Context.MODE_PRIVATE) }
    var askBeforeDelete by remember { mutableStateOf(prefs.getBoolean(PREF_ASK_DELETE, true)) }
    fun setAsk(v: Boolean) { askBeforeDelete = v; prefs.edit().putBoolean(PREF_ASK_DELETE, v).apply() }
    var showBin by remember { mutableStateOf(false) }
    var openRow by remember { mutableStateOf<Any?>(null) }
    /** Pins waiting for "permanently delete?" (one pin, or the whole bin). */
    var confirmPurge by remember { mutableStateOf<List<Pin>?>(null) }
    fun purge(list: List<Pin>) {
        scope.launch {
            list.forEach { repo.dao.deletePin(it.id) }
            snackbar.showSnackbar(if (list.size == 1) "Pin permanently deleted" else "${list.size} pins permanently deleted")
        }
    }
    fun requestPurge(list: List<Pin>) { if (askBeforeDelete) confirmPurge = list else purge(list) }
    fun recycle(pin: Pin) {
        scope.launch {
            repo.dao.setDeletedAt(pin.id, System.currentTimeMillis())
            val r = snackbar.showSnackbar("Moved to recycle bin", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) repo.dao.setDeletedAt(pin.id, null)
        }
    }
    androidx.activity.compose.BackHandler(enabled = showBin) { showBin = false }

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
                title = { Text(if (showBin) "Recycle bin" else cat?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = { if (showBin) showBin = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (showBin) {
                        IconButton(enabled = recycled.isNotEmpty(), onClick = { requestPurge(recycled) }) {
                            Icon(Icons.Default.DeleteForever, "Empty recycle bin")
                        }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Ask before permanent delete") },
                                trailingIcon = { Checkbox(checked = askBeforeDelete, onCheckedChange = null) },
                                onClick = { setAsk(!askBeforeDelete) },
                            )
                        }
                        return@TopAppBar
                    }
                    IconButton(onClick = { showBin = true }) {
                        BadgedBox(badge = { if (recycled.isNotEmpty()) Badge { Text("${recycled.size}") } }) {
                            Icon(Icons.Default.DeleteOutline, "Recycle bin")
                        }
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ExportMenuItems { f, dl ->
                            menu = false
                            cat?.let { c ->
                                scope.launch {
                                    Exporter.export(ctx, c.name, f, repo.dao.pinsForExport(c.id), listOf(c), dl)
                                }
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("Edit tag") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menu = false; editCategory = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete tag") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!showBin) ExtendedFloatingActionButton(
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
        if (showBin) {
            RecycleBinList(
                pins = recycled, cat = cat, pad = pad,
                onRestore = { p -> scope.launch { repo.dao.setDeletedAt(p.id, null); snackbar.showSnackbar("Pin restored") } },
                onDelete = { p -> requestPurge(listOf(p)) },
            )
        } else if (pins.isEmpty()) {
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
                    val cs = MaterialTheme.colorScheme
                    SwipeActionsRow(
                        key = pin.id, openKey = openRow, onOpen = { openRow = it },
                        actions = listOf(
                            SwipeAction("Edit", Icons.Default.Edit, cs.secondaryContainer, cs.onSecondaryContainer) { editingNote = pin },
                            SwipeAction("Recycle", Icons.Default.DeleteOutline, cs.tertiaryContainer, cs.onTertiaryContainer) { recycle(pin) },
                            SwipeAction("Delete", Icons.Default.DeleteForever, cs.error, cs.onError) { requestPurge(listOf(pin)) },
                        ),
                    ) {
                        PinCard(
                            pin = pin,
                            title = cat?.let { Exporter.pinName(it, pin) } ?: "",
                            color = Color(cat?.color ?: 0xFF888888.toInt()),
                            onClick = { editingNote = pin },
                            onOpenMap = { openInGoogleMaps(ctx, pin) },
                        )
                    }
                }
            }
        }
    }

    editingNote?.let { pin ->
        var name by remember(pin.id) { mutableStateOf(pin.name ?: "") }
        var desc by remember(pin.id) { mutableStateOf(pin.note ?: "") }
        var targetCat by remember(pin.id) { mutableLongStateOf(pin.categoryId) }
        var catMenu by remember(pin.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editingNote = null },
            title = { Text("Edit pin") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(60) },
                        label = { Text("Name") }, singleLine = true,
                        placeholder = { Text(cat?.let { Exporter.pinName(it, pin.copy(name = null)) } ?: "") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = desc, onValueChange = { desc = it.take(500) },
                        label = { Text("Description") },
                        placeholder = { Text("Shown on the pin in Google My Maps") },
                        modifier = Modifier.fillMaxWidth(), minLines = 3,
                    )
                    Spacer(Modifier.height(12.dp))
                    Box {
                        val current = allCats.firstOrNull { it.category.id == targetCat }?.category
                        OutlinedButton(onClick = { catMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Label, null, Modifier.size(18.dp),
                                tint = Color(current?.color ?: 0xFF888888.toInt()))
                            Spacer(Modifier.width(6.dp))
                            Text("Tag: ${current?.name ?: ""}", Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                            allCats.forEach { cw ->
                                DropdownMenuItem(
                                    text = { Text(cw.category.name) },
                                    leadingIcon = { Icon(Icons.Default.Label, null, tint = Color(cw.category.color)) },
                                    onClick = { targetCat = cw.category.id; catMenu = false },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        pin.address ?: "%.5f, %.5f".format(Locale.US, pin.lat, pin.lng),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openInGoogleMaps(ctx, pin) }) {
                            Icon(Icons.Default.Map, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Google Maps")
                        }
                        OutlinedButton(
                            onClick = {
                                editingNote = null
                                recycle(pin)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Recycle")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.dao.setNameAndNote(pin.id, name.trim().ifEmpty { null }, desc.trim().ifEmpty { null })
                        if (targetCat != pin.categoryId) {
                            repo.dao.movePin(pin.id, targetCat)
                            val to = allCats.firstOrNull { it.category.id == targetCat }?.category?.name
                            snackbar.showSnackbar("Moved to ${to ?: "tag"}")
                        }
                    }
                    editingNote = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingNote = null }) { Text("Cancel") } },
        )
    }

    confirmPurge?.let { list ->
        var dontAsk by remember(list) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmPurge = null },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(if (list.size == 1) "Permanently delete this pin?" else "Permanently delete ${list.size} pins?") },
            text = {
                Column {
                    Text("This can't be undone.")
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { dontAsk = !dontAsk },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                        Text("Don't ask again")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { if (dontAsk) setAsk(false); confirmPurge = null; purge(list) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Delete permanently") }
            },
            dismissButton = { TextButton(onClick = { confirmPurge = null }) { Text("Cancel") } },
        )
    }

    if (editCategory && cat != null) {
        CategoryDialog(
            title = "Edit tag",
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

const val PREF_ASK_DELETE = "askBeforePermanentDelete"

@Composable
private fun RecycleBinList(
    pins: List<Pin>,
    cat: Category?,
    pad: PaddingValues,
    onRestore: (Pin) -> Unit,
    onDelete: (Pin) -> Unit,
) {
    if (pins.isEmpty()) {
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Recycle bin is empty.\nRecycled pins stay here for ${com.pinnr.app.RECYCLE_DAYS} days.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }
    LazyColumn(
        Modifier.padding(pad).fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Pins are permanently deleted ${com.pinnr.app.RECYCLE_DAYS} days after recycling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(pins, key = { it.id }) { pin ->
            val left = (com.pinnr.app.RECYCLE_DAYS -
                (System.currentTimeMillis() - (pin.deletedAt ?: 0L)) / 86_400_000L).coerceAtLeast(0)
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cat?.let { Exporter.pinName(it, pin) } ?: "", style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pin.address ?: "%.5f, %.5f".format(Locale.US, pin.lat, pin.lng),
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (left <= 1) "Deleted within a day" else "$left days left",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = { onRestore(pin) }) { Icon(Icons.Default.Restore, "Restore") }
                    IconButton(onClick = { onDelete(pin) }) {
                        Icon(Icons.Default.DeleteForever, "Delete permanently", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private val fmt = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault())

fun openInGoogleMaps(ctx: android.content.Context, pin: Pin) {
    val url = "https://www.google.com/maps/search/?api=1&query=${pin.lat},${pin.lng}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.apps.maps")
    try {
        ctx.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}

@Composable
private fun PinCard(
    pin: Pin,
    title: String,
    color: Color,
    onClick: () -> Unit,
    onOpenMap: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Place, null, tint = color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (pin.name != null) {
                    Text(fmt.format(Date(pin.timestamp)), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    pin.address ?: "%.5f, %.5f".format(Locale.US, pin.lat, pin.lng),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                pin.accuracyM?.takeIf { it > 50 }?.let {
                    Text("Low GPS accuracy (±${it.toInt()} m)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
                pin.note?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onOpenMap) { Icon(Icons.Default.Map, "Open in Google Maps") }
        }
    }
}
