package com.pinnr.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.coroutines.resume

sealed interface DropResult {
    /** [secondsBack] is how far back the pin actually went (0 = live fix). */
    data class Dropped(
        val pinId: Long, val categoryName: String, val accuracyM: Float?, val secondsBack: Int = 0,
    ) : DropResult
    data object NoPermission : DropResult
    data object NoLocation : DropResult
}

class PinRepository(private val context: Context) {
    val dao = PinnrDb.get(context).dao()

    /** Background work (address lookup) that should outlive a screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var lastDrop = 0L

    // ---- Pin-behind: rolling buffer of recent fixes while the car screen is open ----
    private val history = ArrayDeque<Location>()
    private var tracking = 0
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            synchronized(history) {
                result.locations.forEach { history.addLast(it) }
                val cutoff = System.currentTimeMillis() - HISTORY_MS
                while (history.isNotEmpty() && history.first().time < cutoff) history.removeFirst()
            }
        }
    }

    /** Call from a screen's onStart; balanced by [stopTracking]. */
    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (!hasLocationPermission()) return
        if (tracking++ > 0) return
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000)
            .setMinUpdateIntervalMillis(1_000).build()
        runCatching {
            LocationServices.getFusedLocationProviderClient(context)
                .requestLocationUpdates(req, callback, Looper.getMainLooper())
        }
    }

    fun stopTracking() {
        if (tracking == 0 || --tracking > 0) return
        LocationServices.getFusedLocationProviderClient(context).removeLocationUpdates(callback)
        synchronized(history) { history.clear() }
    }

    /** The buffered fix closest to [secondsBack] seconds ago, or null if none within 5 s of it. */
    private fun locationAgo(secondsBack: Int): Location? {
        val target = System.currentTimeMillis() - secondsBack * 1_000L
        return synchronized(history) {
            history.minByOrNull { kotlin.math.abs(it.time - target) }
                ?.takeIf { kotlin.math.abs(it.time - target) <= 5_000 }
        }
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        return try {
            withTimeoutOrNull(15_000) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            } ?: client.lastLocation.await()
        } catch (e: Exception) {
            null
        } finally {
            cts.cancel()
        }
    }

    /** Returns null if called again within 1.5 s (double-tap guard). */
    suspend fun dropPin(categoryId: Long, secondsBack: Int = 0): DropResult? {
        val now = System.currentTimeMillis()
        if (now - lastDrop < 1_500) return null
        lastDrop = now

        if (!hasLocationPermission()) return DropResult.NoPermission
        val cat = dao.category(categoryId) ?: return DropResult.NoLocation
        val past = if (secondsBack > 0) locationAgo(secondsBack) else null
        val loc = past ?: currentLocation() ?: return DropResult.NoLocation
        val usedBack = if (past != null) secondsBack else 0
        val id = dao.insertPin(
            Pin(
                categoryId = categoryId,
                lat = loc.latitude,
                lng = loc.longitude,
                accuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
                timestamp = if (past != null) past.time else now,
            )
        )
        appScope.launch { lookupAddress(id, loc.latitude, loc.longitude) }
        return DropResult.Dropped(id, cat.name, if (loc.hasAccuracy()) loc.accuracy else null, usedBack)
    }

    private suspend fun lookupAddress(pinId: Long, lat: Double, lng: Double) {
        if (!Geocoder.isPresent()) return
        val geocoder = Geocoder(context, Locale.getDefault())
        val address: Address? = try {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            cont.resume(addresses.firstOrNull())
                        }
                        override fun onError(errorMessage: String?) {
                            cont.resume(null)
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
            }
        } catch (e: Exception) {
            null
        }
        val text = address?.let { a ->
            (0..a.maxAddressLineIndex).joinToString(", ") { a.getAddressLine(it) }
        }
        if (!text.isNullOrBlank()) dao.setAddress(pinId, text)
    }

    fun undo(pinId: Long) {
        appScope.launch { dao.deletePin(pinId) }
    }

    companion object {
        private const val HISTORY_MS = 45_000L
        /** Pin-behind choices offered in the car, in seconds. */
        val BACK_STEPS = listOf(0, 10, 20, 30)
        @Volatile private var instance: PinRepository? = null
        fun get(context: Context): PinRepository = instance ?: synchronized(this) {
            instance ?: PinRepository(context.applicationContext).also { instance = it }
        }
    }
}
