package com.pinnr.app.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pinnr.app.data.PinRepository

class MainActivity : ComponentActivity() {
    private val permissionGranted = mutableStateOf(false)

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionGranted.value = PinRepository.get(this).hasLocationPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = PinRepository.get(this)
        permissionGranted.value = repo.hasLocationPermission()
        if (!permissionGranted.value) askPermission()

        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = if (android.os.Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            } else {
                if (dark) darkColorScheme(primary = Color(0xFFFF8A65)) else lightColorScheme(primary = Color(0xFFD84315))
            }
            MaterialTheme(colorScheme = scheme) {
                var openCategory by remember { mutableStateOf<Long?>(null) }
                BackHandler(enabled = openCategory != null) { openCategory = null }
                val current = openCategory
                if (current == null) {
                    CategoriesScreen(
                        repo = repo,
                        hasPermission = permissionGranted.value,
                        onRequestPermission = ::askPermission,
                        onOpen = { openCategory = it },
                    )
                } else {
                    PinsScreen(repo = repo, categoryId = current, onBack = { openCategory = null })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionGranted.value = PinRepository.get(this).hasLocationPermission()
    }

    private fun askPermission() {
        requestPermission.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }
}
