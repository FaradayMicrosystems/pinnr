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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.coroutines.resume

sealed interface DropResult {
    data class Dropped(val pinId: Long, val categoryName: String, val accuracyM: Float?) : DropResult
    data object NoPermission : DropResult
    data object NoLocation : DropResult
}

class PinRepository(private val context: Context) {
    val dao = PinnrDb.get(context).dao()

    /** Background work (address lookup) that should outlive a screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var lastDrop = 0L

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
    suspend fun dropPin(categoryId: Long): DropResult? {
        val now = System.currentTimeMillis()
        if (now - lastDrop < 1_500) return null
        lastDrop = now

        if (!hasLocationPermission()) return DropResult.NoPermission
        val cat = dao.category(categoryId) ?: return DropResult.NoLocation
        val loc = currentLocation() ?: return DropResult.NoLocation
        val id = dao.insertPin(
            Pin(
                categoryId = categoryId,
                lat = loc.latitude,
                lng = loc.longitude,
                accuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
                timestamp = now,
            )
        )
        appScope.launch { lookupAddress(id, loc.latitude, loc.longitude) }
        return DropResult.Dropped(id, cat.name, if (loc.hasAccuracy()) loc.accuracy else null)
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
        @Volatile private var instance: PinRepository? = null
        fun get(context: Context): PinRepository = instance ?: synchronized(this) {
            instance ?: PinRepository(context.applicationContext).also { instance = it }
        }
    }
}
