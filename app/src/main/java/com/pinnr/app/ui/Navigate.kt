package com.pinnr.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pinnr.app.data.Pin
import com.pinnr.app.data.PinRepository
import java.util.Locale

enum class NavApp(val label: String, val pkg: String) {
    GOOGLE_MAPS("Google Maps", "com.google.android.apps.maps"),
    WAZE("Waze", "com.waze"),
}

/** Where to send the nav app: the looked-up street address, or the exact GPS point. */
enum class NavTarget { ADDRESS, EXACT }

private fun isInstalled(ctx: Context, pkg: String): Boolean = try {
    ctx.packageManager.getPackageInfo(pkg, 0); true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

fun startNavigation(ctx: Context, app: NavApp, pin: Pin, address: String?, target: NavTarget) {
    val coords = "${pin.lat},${pin.lng}"
    val useAddress = target == NavTarget.ADDRESS && !address.isNullOrBlank()
    val dest = if (useAddress) Uri.encode(address) else coords

    val (appUri, webUri) = when (app) {
        NavApp.GOOGLE_MAPS -> "google.navigation:q=$dest&mode=d" to
            "https://www.google.com/maps/dir/?api=1&destination=$dest&travelmode=driving"
        NavApp.WAZE -> {
            val q = if (useAddress) "q=$dest" else "ll=$coords"
            "https://waze.com/ul?$q&navigate=yes".let { it to it }
        }
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(appUri)).setPackage(app.pkg)
    try {
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // App missing: open its Play Store page so the user can install it.
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.pkg}")))
        }.onFailure {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUri))) }
        }
    }
}

/**
 * "Navigate here" prompt. Looks up the pin's street address from its GPS point
 * (or reuses the saved one), then lets the user pick Google Maps or Waze.
 */
@Composable
fun NavigateDialog(repo: PinRepository, pin: Pin, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var loading by remember(pin.id) { mutableStateOf(true) }
    var address by remember(pin.id) { mutableStateOf<String?>(null) }
    var target by remember(pin.id) { mutableStateOf(NavTarget.ADDRESS) }
    LaunchedEffect(pin.id) {
        address = repo.addressFor(pin)
        if (address == null) target = NavTarget.EXACT
        loading = false
    }
    val coordsText = "%.5f, %.5f".format(Locale.US, pin.lat, pin.lng)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Navigation, null) },
        title = { Text("Navigate here") },
        text = {
            Column {
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Finding street address…")
                    }
                } else {
                    TargetOption(
                        selected = target == NavTarget.ADDRESS,
                        enabled = address != null,
                        title = "Street address",
                        detail = address ?: "No address found for this spot",
                        onClick = { target = NavTarget.ADDRESS },
                    )
                    TargetOption(
                        selected = target == NavTarget.EXACT,
                        enabled = true,
                        title = "Exact pin (GPS)",
                        detail = coordsText,
                        onClick = { target = NavTarget.EXACT },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("Open in", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                NavApp.entries.forEach { app ->
                    val installed = remember(app) { isInstalled(ctx, app.pkg) }
                    FilledTonalButton(
                        onClick = { onDismiss(); startNavigation(ctx, app, pin, address, target) },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Icon(if (app == NavApp.WAZE) Icons.Default.DirectionsCar else Icons.Default.Map, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (installed) app.label else "${app.label} (install)")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TargetOption(selected: Boolean, enabled: Boolean, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
