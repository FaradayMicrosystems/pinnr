package com.pinnr.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.pinnr.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Update(val build: Int, val title: String, val apkUrl: String, val sizeBytes: Long)

/**
 * Checks the GitHub releases for a newer build and installs it over the current app.
 * Installing over (not uninstalling) keeps every tag and pin.
 */
object Updater {
    private const val API = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=20"

    val currentBuild: Int get() = BuildConfig.VERSION_CODE

    /** Newest build above the installed one, or null. Throws on network errors. */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000; readTimeout = 15_000
        }
        val body = try { conn.inputStream.bufferedReader().readText() } finally { conn.disconnect() }
        val releases = JSONArray(body)
        var best: Update? = null
        for (i in 0 until releases.length()) {
            val r = releases.getJSONObject(i)
            if (r.optBoolean("draft")) continue
            val assets = r.getJSONArray("assets")
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                val build = Regex("""pinnr-(\d+)\.apk""").matchEntire(a.getString("name"))
                    ?.groupValues?.get(1)?.toIntOrNull() ?: continue
                if (build > currentBuild && build > (best?.build ?: 0)) {
                    best = Update(build, r.optString("name"), a.getString("browser_download_url"), a.optLong("size"))
                }
            }
        }
        best
    }

    /** Android 8+ needs the user to allow "Install unknown apps" for Pinnr once. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Downloads the APK, reporting progress 0..1. */
    suspend fun download(context: Context, update: Update, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val out = File(dir, "pinnr-${update.build}.apk")
            var url = URL(update.apkUrl)
            var conn: HttpURLConnection
            // GitHub redirects to its file host; follow manually across hosts.
            var hops = 0
            while (true) {
                conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 30_000
                }
                val code = conn.responseCode
                if (code in 300..399 && hops++ < 5) {
                    url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue
                }
                if (code != 200) { conn.disconnect(); error("Download failed (HTTP $code)") }
                break
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes
            try {
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            output.write(buf, 0, n); done += n
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally { conn.disconnect() }
            out
        }

    /** Hands the APK to Android's installer, which updates in place. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
