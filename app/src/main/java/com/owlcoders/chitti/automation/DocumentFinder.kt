package com.owlcoders.chitti.automation

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.owlcoders.chitti.db.AppDatabase
import com.owlcoders.chitti.db.entities.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.resume

data class DocumentSearchResult(
    val title: String,
    val mimeType: String,
    val snippet: String,
    val uriString: String,
    val isIndexed: Boolean
)

/**
 * Handles document importing, OCR text extraction, indexed storage,
 * and semantic / keyword searching across documents.
 */
class DocumentFinder(
    private val context: Context,
    private val database: AppDatabase
) {
    private val tag = "ChittiDocFinder"
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun importDocumentUri(uri: Uri): Document? = withContext(Dispatchers.IO) {
        try {
            var fileName = "Document_${System.currentTimeMillis()}"
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex) ?: fileName
                    }
                }
            }

            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            var contentText = ""
            var ocrText = ""

            // Extract content based on MIME type
            if (mimeType.startsWith("text/") || fileName.endsWith(".txt") || fileName.endsWith(".md") || fileName.endsWith(".json")) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).use { reader ->
                        contentText = reader.readText().take(50000)
                    }
                }
            } else if (mimeType.startsWith("image/")) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        ocrText = runOcr(bitmap)
                    }
                }
            }

            val doc = Document(
                filePath = uri.toString(),
                mimeType = mimeType,
                title = fileName,
                contentText = contentText,
                ocrText = ocrText,
                indexed = true,
                createdAt = System.currentTimeMillis()
            )

            val insertedId = database.documentDao().insertDocument(doc)
            Log.d(tag, "Successfully indexed document id=$insertedId: $fileName")
            doc.copy(id = insertedId.toInt())
        } catch (e: Exception) {
            Log.e(tag, "Failed to import document from uri: $uri", e)
            null
        }
    }

    suspend fun importBitmapFromCamera(bitmap: android.graphics.Bitmap, title: String = "Scanned Document"): Document = withContext(Dispatchers.IO) {
        val ocrText = runOcr(bitmap)
        val doc = Document(
            filePath = "camera://${System.currentTimeMillis()}",
            mimeType = "image/jpeg",
            title = "$title (${System.currentTimeMillis() % 10000})",
            contentText = "",
            ocrText = ocrText,
            indexed = true,
            createdAt = System.currentTimeMillis()
        )
        val insertedId = database.documentDao().insertDocument(doc)
        doc.copy(id = insertedId.toInt())
    }

    private suspend fun runOcr(bitmap: android.graphics.Bitmap): String = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    cont.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    Log.e(tag, "OCR extraction failed", e)
                    cont.resume("")
                }
        } catch (e: Exception) {
            Log.e(tag, "OCR processing error", e)
            cont.resume("")
        }
    }

    fun openDocument(doc: Document): Boolean {
        return try {
            val uri = Uri.parse(doc.filePath)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (doc.mimeType.isNotBlank()) doc.mimeType else "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to open document: ${e.message}")
            // Fallback without mimeType
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(doc.filePath)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (e2: Exception) {
                Log.e(tag, "Fallback open document failed: ${e2.message}")
                false
            }
        }
    }

    suspend fun searchDocuments(query: String): List<DocumentSearchResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.lowercase().trim()
        val results = mutableListOf<DocumentSearchResult>()

        // 1. Search indexed Room database documents
        try {
            val allDocs = database.documentDao().getDocumentCount()
            if (allDocs > 0) {
                // Read from room
                val db = database.openHelper.readableDatabase
                val cursor = db.query(
                    "SELECT id, title, mimeType, contentText, ocrText, filePath FROM documents WHERE title LIKE '%$cleanQuery%' OR contentText LIKE '%$cleanQuery%' OR ocrText LIKE '%$cleanQuery%'"
                )
                cursor.use {
                    while (it.moveToNext()) {
                        val title = it.getString(1) ?: "Untitled"
                        val mimeType = it.getString(2) ?: ""
                        val content = it.getString(3) ?: ""
                        val ocr = it.getString(4) ?: ""
                        val path = it.getString(5) ?: ""

                        val snippet = when {
                            content.isNotBlank() -> content.take(120).replace("\n", " ")
                            ocr.isNotBlank() -> ocr.take(120).replace("\n", " ")
                            else -> "Indexed document"
                        }
                        results.add(DocumentSearchResult(title, mimeType, snippet, path, true))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error searching room documents: ${e.message}")
        }

        results
    }
}
