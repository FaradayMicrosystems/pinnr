package com.pinnr.app.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pinnr.app.R
import com.pinnr.app.data.Category
import com.pinnr.app.data.DropResult
import com.pinnr.app.data.PinRepository
import kotlinx.coroutines.launch

class PinGridScreen(carContext: CarContext) : Screen(carContext) {
    private val repo = PinRepository.get(carContext)
    private var categories: List<Category>? = null
    private var lastPinId: Long? = null
    private var busy = false

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.dao.carCategories().collect {
                    categories = it
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        if (!repo.hasLocationPermission()) {
            return MessageTemplate.Builder(
                "Open Pinnr on your phone and allow location access, then come back."
            )
                .setTitle("Location needed")
                .setHeaderAction(Action.APP_ICON)
                .addAction(
                    Action.Builder().setTitle("Check again")
                        .setOnClickListener { invalidate() }.build()
                )
                .build()
        }

        val cats = categories
        val builder = GridTemplate.Builder()
            .setTitle(if (busy) "Pinning…" else "Tap to pin")
            .setHeaderAction(Action.APP_ICON)

        if (cats == null) {
            return builder.setLoading(true).build()
        }

        val list = ItemList.Builder()
        if (cats.isEmpty()) {
            list.setNoItemsMessage("Add categories in the Pinnr app on your phone")
        }
        cats.forEach { cat ->
            val icon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_pin))
                .setTint(CarColor.createCustom(cat.color, cat.color))
                .build()
            list.addItem(
                GridItem.Builder()
                    .setTitle(cat.name)
                    .setImage(icon, GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener { drop(cat) }
                    .build()
            )
        }
        builder.setSingleList(list.build())

        lastPinId?.let { id ->
            builder.setActionStrip(
                ActionStrip.Builder().addAction(
                    Action.Builder().setTitle("Undo")
                        .setOnClickListener {
                            repo.undo(id)
                            lastPinId = null
                            CarToast.makeText(carContext, "Pin removed", CarToast.LENGTH_SHORT).show()
                            invalidate()
                        }.build()
                ).build()
            )
        }
        return builder.build()
    }

    private fun drop(cat: Category) {
        if (busy) return
        busy = true
        invalidate()
        lifecycleScope.launch {
            val msg = when (val r = repo.dropPin(cat.id)) {
                is DropResult.Dropped -> {
                    lastPinId = r.pinId
                    val acc = r.accuracyM
                    if (acc != null && acc > 50f) "Pinned to ${r.categoryName} (low GPS accuracy, ±${acc.toInt()} m)"
                    else "Pinned to ${r.categoryName}"
                }
                DropResult.NoPermission -> "Location permission needed"
                DropResult.NoLocation -> "Couldn't get your location"
                null -> null
            }
            busy = false
            msg?.let { CarToast.makeText(carContext, it, CarToast.LENGTH_LONG).show() }
            invalidate()
        }
    }
}
