package com.pinnr.app

import android.app.Application
import com.pinnr.app.data.Category
import com.pinnr.app.data.PinRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val RECYCLE_DAYS = 14

class PinnrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("pinnr", MODE_PRIVATE)
        // Recycle bin keeps pins for 14 days, then they're gone for good.
        CoroutineScope(Dispatchers.IO).launch {
            PinRepository.get(this@PinnrApp).dao.purgeRecycled(System.currentTimeMillis() - RECYCLE_DAYS * 86_400_000L)
        }
        if (!prefs.getBoolean("seeded", false)) {
            val dao = PinRepository.get(this).dao
            CoroutineScope(Dispatchers.IO).launch {
                if (dao.allCategories().isEmpty()) {
                    dao.insertCategory(Category(name = "Favourite", color = 0xFFD84315.toInt(), sortOrder = 0, tileSize = 3))
                    dao.insertCategory(Category(name = "Fuel", color = 0xFF1565C0.toInt(), sortOrder = 1, tileSize = 2))
                    dao.insertCategory(Category(name = "Food", color = 0xFF2E7D32.toInt(), sortOrder = 2))
                }
                prefs.edit().putBoolean("seeded", true).apply()
            }
        }
    }
}
