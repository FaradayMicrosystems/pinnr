package com.pinnr.app.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
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
    /** Index into [PinRepository.BACK_STEPS]; 0 = pin where I am now. */
    private var backIndex = 0
    private val secondsBack get() = PinRepository.BACK_STEPS[backIndex]

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = repo.startTracking()
            override fun onStop(owner: LifecycleOwner) = repo.stopTracking()
        })
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
            .setTitle(
                when {
                    busy -> "Pinning…"
                    secondsBack > 0 -> "Tap to pin ${secondsBack}s back"
                    else -> "Tap to pin"
                }
            )
            .setHeaderAction(Action.APP_ICON)

        if (cats == null) {
            return builder.setLoading(true).build()
        }

        val list = ItemList.Builder()
        if (cats.isEmpty()) {
            list.setNoItemsMessage("Add tags in the Pinnr app on your phone")
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

        val strip = ActionStrip.Builder().addAction(
            Action.Builder()
                .setTitle(if (secondsBack == 0) "Now" else "-${secondsBack}s")
                .setOnClickListener {
                    backIndex = (backIndex + 1) % PinRepository.BACK_STEPS.size
                    if (!repo.hasLocationPermission()) backIndex = 0
                    invalidate()
                }.build()
        )
        lastPinId?.let { id ->
            strip.addAction(
                Action.Builder().setTitle("Undo")
                    .setOnClickListener {
                        repo.undo(id)
                        lastPinId = null
                        CarToast.makeText(carContext, "Pin removed", CarToast.LENGTH_SHORT).show()
                        invalidate()
                    }.build()
            )
        }
        builder.setActionStrip(strip.build())
        return builder.build()
    }

    private fun drop(cat: Category) {
        if (busy) return
        busy = true
        invalidate()
        lifecycleScope.launch {
            val wanted = secondsBack
            val msg = when (val r = repo.dropPin(cat.id, wanted)) {
                is DropResult.Dropped -> {
                    lastPinId = r.pinId
                    val acc = r.accuracyM
                    val where = when {
                        r.secondsBack > 0 -> " (${r.secondsBack}s back)"
                        wanted > 0 -> " (no earlier fix, used current)"
                        else -> ""
                    }
                    if (acc != null && acc > 50f) "Pinned to ${r.categoryName}$where, low GPS ±${acc.toInt()} m"
                    else "Pinned to ${r.categoryName}$where"
                }
                DropResult.NoPermission -> "Location permission needed"
                DropResult.NoLocation -> "Couldn't get your location"
                null -> null
            }
            busy = false
            backIndex = 0 // one-shot: next tap is "now" again
            msg?.let { CarToast.makeText(carContext, it, CarToast.LENGTH_LONG).show() }
            invalidate()
        }
    }
}
