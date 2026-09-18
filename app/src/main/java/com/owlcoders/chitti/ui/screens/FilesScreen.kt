package com.owlcoders.chitti.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.owlcoders.chitti.automation.DeviceFile
import com.owlcoders.chitti.automation.FileCategory
import com.owlcoders.chitti.automation.FileFinder
import com.owlcoders.chitti.automation.TimeFilter
import com.owlcoders.chitti.ui.components.GeminiCircularProgressIndicator
import com.owlcoders.chitti.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FilesScreen(
    fileFinder: FileFinder,
    onImportUri: (Uri) -> Unit = {},
    onScanBitmap: (Bitmap) -> Unit = {},
    onOpenFile: (DeviceFile) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTimeFilter by remember { mutableStateOf(TimeFilter.ALL_TIME) }
    var selectedCategory by remember { mutableStateOf(FileCategory.ALL) }
    var filesList by remember { mutableStateOf<List<DeviceFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Query files when filters change
    LaunchedEffect(searchQuery, selectedTimeFilter, selectedCategory) {
        isLoading = true
        filesList = fileFinder.queryFiles(
            keyword = searchQuery,
            timeFilter = selectedTimeFilter,
            category = selectedCategory,
            limit = 120
        )
        isLoading = false
    }

    // SAF Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportUri(uri)
            // Trigger refresh
            scope.launch {
                filesList = fileFinder.queryFiles(searchQuery, selectedTimeFilter, selectedCategory)
            }
        }
    }

    // Camera Scan
    val cameraScanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            onScanBitmap(bitmap)
            scope.launch {
                filesList = fileFinder.queryFiles(searchQuery, selectedTimeFilter, selectedCategory)
            }
        }
    }

    val context = LocalContext.current
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                cameraScanLauncher.launch(null)
            } catch (e: Exception) {
                Log.e("FilesScreen", "Failed to launch camera after grant: ${e.message}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GeminiDarkBg)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(GeminiSurface)
                .border(0.5.dp, GeminiBorder, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = GeminiCyan, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "File Finder",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "Find recent & long ago documents instantly",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GeminiSurfaceElevated
                    ) {
                        Text(
                            text = "${filesList.size} found",
                            style = MaterialTheme.typography.labelSmall,
                            color = GeminiCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            placeholder = { Text("Search files by name, type, or OCR...", color = TextMuted, fontSize = 14.sp) },
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

        // Time Filters: Especially "Long Ago"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimeFilter.values().forEach { filter ->
                val isSelected = selectedTimeFilter == filter
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { selectedTimeFilter = filter }
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) GeminiCyan else GeminiBorder,
                            shape = RoundedCornerShape(14.dp)
                        ),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) GeminiCyan.copy(alpha = 0.15f) else GeminiSurfaceElevated
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (filter == TimeFilter.LONG_AGO) {
                            Icon(Icons.Filled.History, contentDescription = null, tint = if (isSelected) GeminiCyan else GeminiAmber, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) GeminiCyan else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FileCategory.values().forEach { cat ->
                val isSelected = selectedCategory == cat
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedCategory = cat }
                        .border(
                            width = if (isSelected) 1.dp else 0.5.dp,
                            color = if (isSelected) GeminiBlue else GeminiBorder,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) GeminiBlue.copy(alpha = 0.2f) else GeminiSurfaceCard
                ) {
                    Text(
                        text = cat.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) TextPrimary else TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Import & Camera Scan Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    filePickerLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue)
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pick Any File", style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
                onClick = {
                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        try {
                            cameraScanLauncher.launch(null)
                        } catch (e: Exception) {
                            Log.e("FilesScreen", "Failed to launch camera: ${e.message}")
                        }
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GeminiCyan),
                border = androidx.compose.foundation.BorderStroke(1.dp, GeminiCyan.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan Doc", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Files List
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GeminiCircularProgressIndicator(modifier = Modifier.size(36.dp))
            }
        } else if (filesList.isEmpty()) {
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
                                Icons.Filled.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = GeminiCyan
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No files matched", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (selectedTimeFilter == TimeFilter.LONG_AGO)
                            "No older files found with current search. Try selecting \"All Time\" or changing keywords."
                        else "Try a different search term or select \"Pick Any File\" to import.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filesList, key = { it.uriString }) { file ->
                    FileCardItem(
                        file = file,
                        dateFormat = dateFormat,
                        onClick = { onOpenFile(file) }
                    )
                }
            }
        }
    }
}

@Composable
fun FileCardItem(
    file: DeviceFile,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val (iconRes, iconColor) = when {
        file.mimeType.contains("pdf") -> Pair(Icons.Filled.PictureAsPdf, GeminiPink)
        file.mimeType.contains("image") -> Pair(Icons.Filled.Image, GeminiCyan)
        file.mimeType.contains("video") -> Pair(Icons.Filled.Movie, GeminiAmber)
        file.mimeType.contains("audio") -> Pair(Icons.Filled.AudioFile, GeminiGreen)
        file.mimeType.contains("zip") || file.mimeType.contains("rar") -> Pair(Icons.Filled.FolderZip, GeminiPurple)
        else -> Pair(Icons.Filled.InsertDriveFile, GeminiBlue)
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
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(12.dp),
                color = iconColor.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, iconColor.copy(alpha = 0.3f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(iconRes, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = file.name,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (file.isOld) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GeminiAmber.copy(alpha = 0.18f),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, GeminiAmber.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "Long ago",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = GeminiAmber,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = file.location,
                        style = MaterialTheme.typography.labelSmall,
                        color = GeminiCyan,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = " · ${dateFormat.format(Date(file.dateModifiedMs))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )

                    if (file.sizeBytes > 0) {
                        val sizeKb = file.sizeBytes / 1024
                        val sizeMb = sizeKb / 1024
                        val sizeText = if (sizeMb > 0) "$sizeMb MB" else "$sizeKb KB"
                        Text(
                            text = " · $sizeText",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }

            IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.OpenInNew, contentDescription = "Open", tint = GeminiCyan, modifier = Modifier.size(20.dp))
            }
        }
    }
}
