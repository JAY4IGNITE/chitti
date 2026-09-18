package com.owlcoders.chitti.automation

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.owlcoders.chitti.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceFile(
    val id: Long,
    val name: String,
    val uriString: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateModifiedMs: Long,
    val location: String = "",
    val isOld: Boolean = false,
    val snippet: String = ""
)

enum class TimeFilter(val label: String) {
    ALL_TIME("All Time"),
    RECENT("Past Month"),
    PAST_YEAR("Past Year"),
    LONG_AGO("Long Ago (> 6 Mo)")
}

enum class FileCategory(val label: String) {
    ALL("All Files"),
    DOCUMENTS("Docs & PDFs"),
    IMAGES("Images & Scans"),
    DOWNLOADS("Downloads"),
    TEXT_NOTES("Notes & Text")
}

/**
 * Powerful on-device File Finder.
 * Queries MediaStore, Downloads, and Indexed OCR documents to instantly locate
 * current and long-ago files with time and category filters.
 */
class FileFinder(
    private val context: Context,
    private val database: AppDatabase
) {
    private val tag = "ChittiFileFinder"

    suspend fun queryFiles(
        keyword: String = "",
        timeFilter: TimeFilter = TimeFilter.ALL_TIME,
        category: FileCategory = FileCategory.ALL,
        limit: Int = 100
    ): List<DeviceFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DeviceFile>()
        val seenUris = mutableSetOf<String>()
        val now = System.currentTimeMillis()
        val sixMonthsAgoSec = (now - (180L * 86400000L)) / 1000L
        val oneMonthAgoSec = (now - (30L * 86400000L)) / 1000L
        val oneYearAgoSec = (now - (365L * 86400000L)) / 1000L

        // 1. Search indexed Room database documents first
        try {
            val count = database.documentDao().getDocumentCount()
            if (count > 0) {
                val db = database.openHelper.readableDatabase
                val searchClause = if (keyword.isNotBlank()) {
                    "WHERE title LIKE '%$keyword%' OR contentText LIKE '%$keyword%' OR ocrText LIKE '%$keyword%'"
                } else ""
                val cursor = db.query("SELECT id, title, mimeType, contentText, ocrText, filePath, createdAt FROM documents $searchClause ORDER BY createdAt DESC LIMIT 50")
                cursor.use {
                    while (it.moveToNext()) {
                        val id = it.getLong(0)
                        val title = it.getString(1) ?: "Untitled Document"
                        val mime = it.getString(2) ?: "application/octet-stream"
                        val content = it.getString(3) ?: ""
                        val ocr = it.getString(4) ?: ""
                        val path = it.getString(5) ?: ""
                        val createdAt = it.getLong(6)

                        val isOld = (now - createdAt) > (180L * 86400000L)
                        val snippet = when {
                            ocr.isNotBlank() -> "OCR: " + ocr.take(90).replace("\n", " ")
                            content.isNotBlank() -> content.take(90).replace("\n", " ")
                            else -> "Indexed Document"
                        }

                        if (seenUris.add(path)) {
                            results.add(
                                DeviceFile(
                                    id = id,
                                    name = title,
                                    uriString = path,
                                    mimeType = mime,
                                    sizeBytes = 0L,
                                    dateModifiedMs = createdAt,
                                    location = "Indexed Docs",
                                    isOld = isOld,
                                    snippet = snippet
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error searching room documents: ${e.message}")
        }

        // 2. Query MediaStore.Files for storage files
        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATA
            )

            val selectionParts = mutableListOf<String>()
            val selectionArgs = mutableListOf<String>()

            // Keyword filter
            if (keyword.isNotBlank()) {
                selectionParts.add("(${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.Files.FileColumns.DATA} LIKE ?)")
                selectionArgs.add("%$keyword%")
                selectionArgs.add("%$keyword%")
            }

            // Time filter
            when (timeFilter) {
                TimeFilter.RECENT -> {
                    selectionParts.add("${MediaStore.Files.FileColumns.DATE_MODIFIED} >= ?")
                    selectionArgs.add(oneMonthAgoSec.toString())
                }
                TimeFilter.PAST_YEAR -> {
                    selectionParts.add("${MediaStore.Files.FileColumns.DATE_MODIFIED} >= ?")
                    selectionArgs.add(oneYearAgoSec.toString())
                }
                TimeFilter.LONG_AGO -> {
                    // Files older than 6 months or past year!
                    selectionParts.add("${MediaStore.Files.FileColumns.DATE_MODIFIED} <= ?")
                    selectionArgs.add(sixMonthsAgoSec.toString())
                }
                TimeFilter.ALL_TIME -> {}
            }

            // Category filter
            when (category) {
                FileCategory.DOCUMENTS -> {
                    selectionParts.add("(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/pdf' OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/msword%' OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE '%document%' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pdf' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.doc%' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.xls%')")
                }
                FileCategory.IMAGES -> {
                    selectionParts.add("(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'image/%')")
                }
                FileCategory.DOWNLOADS -> {
                    selectionParts.add("(${MediaStore.Files.FileColumns.DATA} LIKE '%/Download/%')")
                }
                FileCategory.TEXT_NOTES -> {
                    selectionParts.add("(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'text/%' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.txt' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.md' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.json')")
                }
                FileCategory.ALL -> {}
            }

            val selection = if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null
            val sortOrder = if (timeFilter == TimeFilter.LONG_AGO) {
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} ASC LIMIT $limit"
            } else {
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT $limit"
            }

            val queryUri = MediaStore.Files.getContentUri("external")
            val cursor: Cursor? = context.contentResolver.query(
                queryUri,
                projection,
                selection,
                if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeCol = it.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeCol = it.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val dateCol = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val dataCol = it.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                while (it.moveToNext() && results.size < limit) {
                    val fileId = it.getLong(idCol)
                    val displayName = it.getString(nameCol) ?: "File_$fileId"
                    val mime = if (mimeCol != -1) it.getString(mimeCol) ?: "application/octet-stream" else "application/octet-stream"
                    val size = if (sizeCol != -1) it.getLong(sizeCol) else 0L
                    val dateSec = if (dateCol != -1) it.getLong(dateCol) else 0L
                    val filePath = if (dataCol != -1) it.getString(dataCol) ?: "" else ""

                    val contentUri = ContentUris.withAppendedId(queryUri, fileId)
                    val uriStr = contentUri.toString()
                    val isOld = (dateSec < sixMonthsAgoSec)

                    if (seenUris.add(uriStr)) {
                        results.add(
                            DeviceFile(
                                id = fileId,
                                name = displayName,
                                uriString = uriStr,
                                mimeType = mime,
                                sizeBytes = size,
                                dateModifiedMs = dateSec * 1000L,
                                location = if (filePath.contains("/Download/")) "Downloads" else if (filePath.contains("/Documents/")) "Documents" else "Storage",
                                isOld = isOld,
                                snippet = "Modified: " + formatFileDate(dateSec * 1000L)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to query MediaStore: ${e.message}")
        }

        results
    }

    fun openFile(file: DeviceFile): Boolean {
        return try {
            val uri = Uri.parse(file.uriString)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (file.mimeType.isNotBlank()) file.mimeType else "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to open file: ${e.message}")
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(file.uriString)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    private fun formatFileDate(ms: Long): String {
        if (ms <= 0L) return "Unknown date"
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ms))
    }
}
