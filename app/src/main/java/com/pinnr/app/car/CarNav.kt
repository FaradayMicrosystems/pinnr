package com.pinnr.app.car

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Bridge from the phone UI to the live Android Auto session.
 *
 * While Pinnr has a session on the car screen, the phone can hand a destination to
 * the car's navigation app (Google Maps / Waze running in Android Auto) without
 * opening anything on the phone.
 */
object CarNav {
    @Volatile
    private var carContext: CarContext? = null

    internal fun attach(ctx: CarContext) { carContext = ctx }
    internal fun detach(ctx: CarContext) { if (carContext === ctx) carContext = null }

    /** True while Android Auto is running a Pinnr session. */
    val isConnected: Boolean get() = carContext != null

    /**
     * Sends [destination] to the navigation app on the car screen.
     * [destination] is either "lat,lng" or a street address.
     * Returns true if the car accepted it; false means fall back to the phone.
     */
    fun navigate(destination: String, isCoords: Boolean): Boolean {
        val ctx = carContext ?: return false
        val uri = if (isCoords) "geo:$destination" else "geo:0,0?q=${Uri.encode(destination)}"
        val intent = Intent(CarContext.ACTION_NAVIGATE, Uri.parse(uri))
        var ok = false
        val run = Runnable {
            ok = runCatching { ctx.startCarApp(intent) }.isSuccess
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            run.run()
        } else {
            val done = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post { run.run(); done.countDown() }
            done.await(2, TimeUnit.SECONDS)
        }
        return ok
    }
}
