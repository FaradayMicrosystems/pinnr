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
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.saveable.rememberSaveable
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
                val pager = rememberPagerState(pageCount = { 2 })
                val scope = rememberCoroutineScope()
                // Bottom bar follows the swipe as soon as the page is more than halfway across.
                val tab = pager.targetPage
                fun goTo(page: Int) { scope.launch { pager.animateScrollToPage(page) } }
                var openCategory by rememberSaveable { mutableStateOf<Long?>(null) }
                BackHandler(enabled = openCategory != null || pager.currentPage != 0) {
                    if (openCategory != null) openCategory = null else goTo(0)
                }
                val current = openCategory
                if (current != null) {
                    PinsScreen(repo = repo, categoryId = current, onBack = { openCategory = null })
                } else {
                    Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = tab == 0, onClick = { goTo(0) },
                                    icon = { Icon(Icons.Default.AddLocationAlt, null) },
                                    label = { Text("Tag Now") },
                                )
                                NavigationBarItem(
                                    selected = tab == 1, onClick = { goTo(1) },
                                    icon = { Icon(Icons.Default.Tune, null) },
                                    label = { Text("Tag Management") },
                                )
                            }
                        },
                    ) { pad ->
                        HorizontalPager(
                            state = pager,
                            beyondViewportPageCount = 1,
                            modifier = Modifier.padding(pad).fillMaxSize(),
                        ) { page ->
                            if (page == 0) {
                                TagNowScreen(
                                    repo = repo,
                                    hasPermission = permissionGranted.value,
                                    onRequestPermission = ::askPermission,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                TagManagementScreen(
                                    repo = repo,
                                    onOpen = { openCategory = it },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
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
