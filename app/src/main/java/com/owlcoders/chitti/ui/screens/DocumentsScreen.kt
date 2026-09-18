package com.owlcoders.chitti.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owlcoders.chitti.db.entities.Document
import com.owlcoders.chitti.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DocumentsScreen(
    documents: List<Document>,
    onImportUri: (Uri) -> Unit = {},
    onScanBitmap: (Bitmap) -> Unit = {},
    onOpenDocument: (Document) -> Unit = {},
    onDeleteDocument: (Document) -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    var searchQuery by remember { mutableStateOf("") }

    // SAF Document Picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportUri(uri)
        }
    }

    // Camera Scan launcher for documents
    val cameraScanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            onScanBitmap(bitmap)
        }
    }

    val filteredDocs = if (searchQuery.isBlank()) documents
    else documents.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
                it.contentText.contains(searchQuery, ignoreCase = true) ||
                it.ocrText.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GeminiDarkBg)
    ) {
        // Top Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(GeminiSurface)
                .border(0.5.dp, GeminiBorder, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "📄 Document Finder",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${documents.size} indexed documents · OCR Search Ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GeminiSurfaceElevated
                    ) {
                        Text(
                            text = "${filteredDocs.size} shown",
                            style = MaterialTheme.typography.labelSmall,
                            color = GeminiCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text("Search documents by name or OCR content...", color = TextMuted, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = GeminiCyan) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = TextMuted)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GeminiCyan,
                unfocusedBorderColor = GeminiBorder,
                focusedContainerColor = GeminiSurfaceCard,
                unfocusedContainerColor = GeminiSurfaceCard,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        // Action Buttons: Import & Camera Scan
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "text/*",
                            "image/*",
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue)
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import File", style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
                onClick = { cameraScanLauncher.launch(null) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GeminiCyan),
                border = androidx.compose.foundation.BorderStroke(1.dp, GeminiCyan.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan OCR", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Document List
        if (filteredDocs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = GeminiSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GeminiBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Description,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = GeminiCyan
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No documents found", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (searchQuery.isNotEmpty()) "Try a different search term" else "Import files or take a photo of a document to enable offline AI search",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredDocs) { doc ->
                    GeminiDocumentCard(
                        document = doc,
                        dateFormat = dateFormat,
                        onClick = { onOpenDocument(doc) },
                        onDelete = { onDeleteDocument(doc) }
                    )
                }
            }
        }
    }
}

@Composable
fun GeminiDocumentCard(
    document: Document,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val (iconRes, iconColor) = when {
        document.mimeType.contains("pdf") -> Pair(Icons.Filled.PictureAsPdf, GeminiPink)
        document.mimeType.contains("image") -> Pair(Icons.Filled.Image, GeminiCyan)
        else -> Pair(Icons.AutoMirrored.Filled.InsertDriveFile, GeminiBlue)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(1.dp, GeminiBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GeminiSurfaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = iconColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, iconColor.copy(alpha = 0.3f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(iconRes, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.title.ifEmpty { "Untitled Document" },
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = if (document.mimeType.isNotBlank()) document.mimeType.substringAfter('/') else "document",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = " · ${dateFormat.format(Date(document.createdAt))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = TextMuted)
                }
            }

            // OCR / Extracted Content Preview
            val textSnippet = if (document.ocrText.isNotBlank()) document.ocrText else document.contentText
            if (textSnippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GeminiSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, GeminiBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = GeminiCyan, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (document.ocrText.isNotBlank()) "OCR Extracted Text" else "Indexed Content",
                                style = MaterialTheme.typography.labelSmall,
                                color = GeminiCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = textSnippet,
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        if (textSnippet.length > 80) {
                            Text(
                                text = if (expanded) "Show less" else "Show more",
                                color = GeminiBlue,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable { expanded = !expanded }
                            )
                        }
                    }
                }
            }
        }
    }
}
