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

    /** Writes the export to cache and opens the system share sheet. */
    fun share(context: Context, fileBase: String, format: ExportFormat, pins: List<Pin>, cats: List<Category>) {
        val map = cats.associateBy { it.id }
        val text = when (format) {
            ExportFormat.CSV -> csv(pins, map)
            ExportFormat.GPX -> gpx(pins, map)
        }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = fileBase.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifEmpty { "pins" }
        val file = File(dir, "$safe.${format.ext}")
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
