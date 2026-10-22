package com.pinnr.app.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.pinnr.app.data.Category
import com.pinnr.app.data.Pin
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class ExportFormat(val ext: String, val mime: String) {
    CSV("csv", "text/csv"),
    GPX("gpx", "application/gpx+xml"),
}

object Exporter {
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val local = SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault())

    fun pinName(cat: Category, pin: Pin): String =
        pin.name?.takeIf { it.isNotBlank() } ?: "${cat.name} – ${local.format(Date(pin.timestamp))}"

    /**
     * CSV laid out for Google My Maps import: choose Latitude/Longitude as the
     * position columns and Name as the title column.
     */
    fun csv(pins: List<Pin>, cats: Map<Long, Category>): String = buildString {
        append("Name,Description,Latitude,Longitude,Category,Timestamp,Address,AccuracyMeters\n")
        pins.forEach { p ->
            val c = cats[p.categoryId] ?: return@forEach
            append(
                listOf(
                    pinName(c, p), p.note.orEmpty(), p.lat.toString(), p.lng.toString(), c.name,
                    iso.format(Date(p.timestamp)), p.address.orEmpty(),
                    p.accuracyM?.let { "%.0f".format(Locale.US, it) }.orEmpty(),
                ).joinToString(",") { csvCell(it) }
            )
            append('\n')
        }
    }

    private fun csvCell(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun gpx(pins: List<Pin>, cats: Map<Long, Category>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="Pinnr" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        pins.forEach { p ->
            val c = cats[p.categoryId] ?: return@forEach
            append("""  <wpt lat="${p.lat}" lon="${p.lng}">""").append('\n')
            append("    <time>${iso.format(Date(p.timestamp))}</time>\n")
            append("    <name>${xml(pinName(c, p))}</name>\n")
            val desc = listOfNotNull(p.note, p.address).joinToString("\n")
            if (desc.isNotBlank()) append("    <desc>${xml(desc)}</desc>\n")
            append("    <type>${xml(c.name)}</type>\n")
            append("  </wpt>\n")
        }
        append("</gpx>\n")
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun render(format: ExportFormat, pins: List<Pin>, cats: List<Category>): String {
        val map = cats.associateBy { it.id }
        return when (format) {
            ExportFormat.CSV -> csv(pins, map)
            ExportFormat.GPX -> gpx(pins, map)
        }
    }

    private fun fileName(fileBase: String, format: ExportFormat): String {
        val safe = fileBase.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifEmpty { "pins" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "pinnr_${safe}_$stamp.${format.ext}"
    }

    /** Share or download, then tell the user what happened. */
    suspend fun export(
        context: Context, fileBase: String, format: ExportFormat,
        pins: List<Pin>, cats: List<Category>, download: Boolean,
    ) {
        if (pins.isEmpty()) {
            android.widget.Toast.makeText(context, "No pins to export", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (download && android.os.Build.VERSION.SDK_INT >= 29) {
            val name = fileName(fileBase, format)
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                saveToDownloads(context, name, format, render(format, pins, cats))
            }
            android.widget.Toast.makeText(
                context,
                if (ok) "Saved to Downloads/$name" else "Couldn't save the file",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        } else {
            share(context, fileBase, format, pins, cats)
        }
    }

    @androidx.annotation.RequiresApi(29)
    private fun saveToDownloads(context: Context, name: String, format: ExportFormat, text: String): Boolean {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, format.mime)
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: return false
            values.clear()
            values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    /** Writes the export to cache and opens the system share sheet. */
    fun share(context: Context, fileBase: String, format: ExportFormat, pins: List<Pin>, cats: List<Category>) {
        val text = render(format, pins, cats)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName(fileBase, format))
        file.writeText(text)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Pinnr export: $fileBase")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export ${format.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
