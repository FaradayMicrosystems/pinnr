package com.pinnr.app.export

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.pinnr.app.data.Category
import com.pinnr.app.data.Pin
import com.pinnr.app.data.PinDao
import com.pinnr.app.ui.PALETTE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ImportResult(val added: Int, val skipped: Int, val newTags: Int, val error: String? = null)

/**
 * Imports pins from a CSV. Understands Pinnr exports/backups and most other CSVs
 * with latitude/longitude columns (e.g. a Google My Maps CSV export).
 * Rows without a Category/Tag column go into a tag named after the file.
 */
object Importer {

    suspend fun importCsv(context: Context, uri: Uri, dao: PinDao): ImportResult = withContext(Dispatchers.IO) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        } ?: return@withContext ImportResult(0, 0, 0, "Couldn't read that file")

        val rows = parse(text.removePrefix("﻿"))
        if (rows.size < 2) return@withContext ImportResult(0, 0, 0, "The file has no rows")

        val header = rows.first().map { it.trim().lowercase(Locale.US) }
        fun col(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }
        val iLat = col("latitude", "lat") ?: return@withContext ImportResult(0, 0, 0, "No Latitude column found")
        val iLng = col("longitude", "lng", "lon", "long") ?: return@withContext ImportResult(0, 0, 0, "No Longitude column found")
        val iName = col("name", "title")
        val iDesc = col("description", "note", "notes")
        val iCat = col("category", "tag", "list")
        val iTime = col("timestamp", "time", "date")
        val iAddr = col("address")
        val iAcc = col("accuracymeters", "accuracy")
        val iColor = col("tagcolor")
        val iSize = col("tagsize")
        val iCustom = col("customname")

        val fallbackTag = fileName(context, uri)
            ?.substringBeforeLast('.')
            ?.removePrefix("pinnr_")
            ?.replace('_', ' ')
            ?.trim()
            ?.ifEmpty { null } ?: "Imported"

        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val tagCache = mutableMapOf<String, Category>()
        var added = 0
        var skipped = 0
        var newTags = 0
        var order = dao.allCategories().size

        for (r in rows.drop(1)) {
            fun cell(i: Int?) = i?.let { r.getOrNull(it) }?.trim()?.ifEmpty { null }
            val lat = cell(iLat)?.toDoubleOrNull()
            val lng = cell(iLng)?.toDoubleOrNull()
            if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) { skipped++; continue }

            val tagName = cell(iCat) ?: fallbackTag
            val cat = tagCache[tagName.lowercase()] ?: (dao.categoryByName(tagName) ?: run {
                val color = cell(iColor)?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
                    ?: PALETTE[(order) % PALETTE.size]
                val size = cell(iSize)?.toIntOrNull()?.coerceIn(1, 3) ?: 1
                val c = Category(name = tagName, color = color, tileSize = size, sortOrder = order++)
                newTags++
                c.copy(id = dao.insertCategory(c))
            }).also { tagCache[tagName.lowercase()] = it }

            val ts = cell(iTime)?.let { t ->
                runCatching { iso.parse(t)?.time }.getOrNull() ?: t.toLongOrNull()
            } ?: System.currentTimeMillis()

            if (dao.countSame(cat.id, lat, lng, ts) > 0) { skipped++; continue }

            val rawName = cell(iName)
            // For Pinnr backups, only keep names the user actually set.
            val name = if (iCustom != null) rawName.takeIf { cell(iCustom) == "1" } else rawName
            dao.insertPin(
                Pin(
                    categoryId = cat.id, lat = lat, lng = lng,
                    accuracyM = cell(iAcc)?.toFloatOrNull(),
                    timestamp = ts,
                    address = cell(iAddr),
                    note = cell(iDesc),
                    name = name,
                )
            )
            added++
        }
        ImportResult(added, skipped, newTags)
    }

    private fun fileName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    /** RFC 4180-ish CSV parser: quoted fields, escaped quotes, newlines inside quotes. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(ch)
            } else when (ch) {
                '"' -> quoted = true
                ',' -> { row.add(field.toString()); field.clear() }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }
}
