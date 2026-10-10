package com.zevbuild.zevsafe.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.zevbuild.zevsafe.crypto.CryptoEngine
import com.zevbuild.zevsafe.crypto.DecryptedFileEntry
import com.zevbuild.zevsafe.ui.components.AudioPlayerModal
import com.zevbuild.zevsafe.ui.components.CardAccent
import com.zevbuild.zevsafe.ui.components.CyberCard
import com.zevbuild.zevsafe.ui.theme.CardBorder
import com.zevbuild.zevsafe.ui.theme.PurpleLight
import com.zevbuild.zevsafe.ui.theme.PurplePrimary
import com.zevbuild.zevsafe.ui.theme.Success
import com.zevbuild.zevsafe.ui.theme.SurfaceDark
import com.zevbuild.zevsafe.ui.theme.SurfaceVariantDark
import com.zevbuild.zevsafe.ui.theme.TealLight
import com.zevbuild.zevsafe.ui.theme.TealPrimary
import com.zevbuild.zevsafe.ui.theme.TextMuted
import com.zevbuild.zevsafe.ui.theme.TextPrimary
import com.zevbuild.zevsafe.ui.theme.TextSecondary
import com.zevbuild.zevsafe.ui.viewer.FileViewerHelper
import com.zevbuild.zevsafe.ui.viewer.VideoPlayerActivity
import com.zevbuild.zevsafe.viewmodel.VaultViewModel
import java.io.File

// MIME Color Constants per R4 UI/UX Spec
private val MimeRose = Color(0xFFF43F5E)    // Video (video/*)
private val MimeAmber = Color(0xFFF59E0B)   // Audio (audio/*)
private val MimeTeal = Color(0xFF2DD4BF)    // Images (image/*)
private val MimeSkyBlue = Color(0xFF38BDF8) // Documents (text/*, application/pdf, etc.)
private val MimeViolet = Color(0xFFA78BFA)  // Archives (application/zip, .zev, etc.)

private fun isArchiveFile(entry: DecryptedFileEntry): Boolean {
    val nameLower = entry.name.lowercase()
    val mime = entry.mimeType.lowercase()
    return mime == "application/zip" || mime == "application/x-zip-compressed" ||
            mime == "application/x-tar" || mime == "application/gzip" || mime == "application/x-7z-compressed" ||
            nameLower.endsWith(".zev") || nameLower.endsWith(".zip") || nameLower.endsWith(".tar") ||
            nameLower.endsWith(".gz") || nameLower.endsWith(".7z") || nameLower.endsWith(".rar")
}

private fun isDocumentFile(entry: DecryptedFileEntry): Boolean {
    val nameLower = entry.name.lowercase()
    val mime = entry.mimeType.lowercase()
    return mime.startsWith("text/") || mime == "application/pdf" ||
            mime.contains("document") || mime.contains("sheet") || mime.contains("presentation") ||
            nameLower.endsWith(".pdf") || nameLower.endsWith(".txt") || nameLower.endsWith(".doc") ||
            nameLower.endsWith(".docx") || nameLower.endsWith(".xls") || nameLower.endsWith(".xlsx") ||
            nameLower.endsWith(".ppt") || nameLower.endsWith(".pptx") || nameLower.endsWith(".json") ||
            nameLower.endsWith(".xml") || nameLower.endsWith(".csv") || nameLower.endsWith(".md")
}

enum class FileCategory(val label: String, val color: Color) {
    ALL("All", Color(0xFF2DD4BF)),
    VIDEOS("Videos", Color(0xFFF43F5E)),
    AUDIO("Audio", Color(0xFFF59E0B)),
    IMAGES("Images", Color(0xFF2DD4BF)),
    DOCS("Docs", Color(0xFF38BDF8)),
    ARCHIVES("Archives", Color(0xFFA78BFA))
}

enum class FileSortOrder(val label: String) {
    NAME_ASC("Name A-Z"),
    NAME_DESC("Name Z-A"),
    SIZE_DESC("Largest"),
    SIZE_ASC("Smallest")
}

private fun matchesCategory(entry: DecryptedFileEntry, category: FileCategory): Boolean {
    return when (category) {
        FileCategory.ALL -> true
        FileCategory.VIDEOS -> entry.mimeType.startsWith("video/")
        FileCategory.AUDIO -> entry.mimeType.startsWith("audio/")
        FileCategory.IMAGES -> entry.mimeType.startsWith("image/")
        FileCategory.DOCS -> isDocumentFile(entry)
        FileCategory.ARCHIVES -> isArchiveFile(entry)
    }
}

@Composable
fun VaultBrowserScreen(
    viewModel: VaultViewModel,
    onNavigateToDecrypt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val decryptionResult by viewModel.decryptionResult.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(FileCategory.ALL) }
    var sortOrder by remember { mutableStateOf(FileSortOrder.NAME_ASC) }
    var selectedPreviewFile by remember { mutableStateOf<DecryptedFileEntry?>(null) }
    var selectedAudioFile by remember { mutableStateOf<DecryptedFileEntry?>(null) }

    // File Preview Modal
    if (selectedPreviewFile != null) {
        FilePreviewModal(
            entry = selectedPreviewFile!!,
            onDismiss = { selectedPreviewFile = null }
        )
    }

    // Audio Player Modal
    if (selectedAudioFile != null) {
        AudioPlayerModal(
            audioEntry = selectedAudioFile!!,
            onDismiss = { selectedAudioFile = null }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (decryptionResult == null) {
                CyberCard(
                    title = "Vault Browser",
                    subtitle = "Explore files and media inside restored vaults",
                    tag = "EXPLORER",
                    accent = CardAccent.PURPLE
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(PurplePrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = PurpleLight,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No Vault Decrypted Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Decrypt a .zev vault to view and inspect its files here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onNavigateToDecrypt,
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Go to Decrypt")
                            }
                        }
                    }
                }
            } else {
                val result = decryptionResult!!
                val categoryFiles = result.files.filter { matchesCategory(it, selectedCategory) }
                val filteredFiles = categoryFiles.filter {
                    it.path.contains(searchQuery, ignoreCase = true) || it.name.contains(searchQuery, ignoreCase = true)
                }.let { list ->
                    when (sortOrder) {
                        FileSortOrder.NAME_ASC -> list.sortedBy { it.name.lowercase() }
                        FileSortOrder.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
                        FileSortOrder.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
                        FileSortOrder.SIZE_ASC -> list.sortedBy { it.sizeBytes }
                    }
                }

                CyberCard(
                    title = result.vaultName,
                    subtitle = "${result.files.size} items · ${CryptoEngine.formatBytes(result.totalSizeBytes)} · ${result.version.displayName}",
                    tag = "ACTIVE VAULT",
                    accent = CardAccent.TEAL
                ) {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter files...", color = TextMuted) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("browser_search_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category Filter Chips & Sort Controls (aligned with zevsafe web explorer)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(end = 8.dp)
                        ) {
                            items(FileCategory.values()) { category ->
                                val isSelected = selectedCategory == category
                                val count = result.files.count { matchesCategory(it, category) }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) category.color.copy(alpha = 0.2f) else SurfaceVariantDark)
                                        .border(
                                            1.dp,
                                            if (isSelected) category.color else CardBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedCategory = category }
                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = category.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) category.color else TextSecondary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$count",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) category.color.copy(alpha = 0.85f) else TextMuted
                                    )
                                }
                            }
                        }

                        // Sort Order Toggle Button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    val values = FileSortOrder.values()
                                    val nextIdx = (sortOrder.ordinal + 1) % values.size
                                    sortOrder = values[nextIdx]
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort Files",
                                tint = TealLight,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = sortOrder.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // File List with bottom padding for Floating Export All FAB
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(440.dp),
                        contentPadding = PaddingValues(bottom = 76.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredFiles) { fileEntry ->
                            FileItemRow(
                                entry = fileEntry,
                                onClick = {
                                    if (!fileEntry.isDirectory) {
                                        viewModel.extractSingleFileIfNeeded(context, fileEntry) { extracted ->
                                            when {
                                                extracted.mimeType.startsWith("video/") -> {
                                                    val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                                                        putExtra(VideoPlayerActivity.EXTRA_URI, extracted.localFileUri.toString())
                                                        putExtra(VideoPlayerActivity.EXTRA_NAME, extracted.name)
                                                    }
                                                    context.startActivity(intent)
                                                }
                                                extracted.mimeType.startsWith("audio/") -> {
                                                    selectedAudioFile = extracted
                                                }
                                                extracted.mimeType.startsWith("image/") || extracted.mimeType.startsWith("text/") || extracted.textContentPreview != null -> {
                                                    selectedPreviewFile = extracted
                                                }
                                                else -> {
                                                    FileViewerHelper.openWithExternalApp(context, extracted)
                                                }
                                            }
                                        }
                                    }
                                },
                                onShare = {
                                    viewModel.extractSingleFileIfNeeded(context, fileEntry) { extracted ->
                                        FileViewerHelper.shareFile(context, extracted)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Floating "Export All" Extended FAB anchored at bottom-end
        if (decryptionResult != null) {
            val result = decryptionResult!!
            val fileCount = result.files.filter { !it.isDirectory }.size
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp)
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.exportDecryptedZip(context) { zipFile ->
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    zipFile
                                )
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Export All Files (.zip)"))
                            } catch (_: Exception) {}
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export All",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    text = {
                        Text(
                            text = "Export All ($fileCount)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    },
                    containerColor = TealPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_export_all")
                )
            }
        }
    }
}

@Composable
private fun FileItemRow(
    entry: DecryptedFileEntry,
    onClick: () -> Unit,
    onShare: () -> Unit
) {
    // MIME color coding per R4 UI/UX Spec
    val (itemColor, icon) = when {
        entry.isDirectory -> Pair(PurpleLight, Icons.Default.Folder)
        entry.mimeType.startsWith("video/") -> Pair(MimeRose, Icons.Default.Movie)
        entry.mimeType.startsWith("audio/") -> Pair(MimeAmber, Icons.Default.MusicNote)
        entry.mimeType.startsWith("image/") -> Pair(MimeTeal, Icons.Default.Image)
        isArchiveFile(entry) -> Pair(MimeViolet, Icons.Default.Archive)
        isDocumentFile(entry) -> Pair(MimeSkyBlue, Icons.Default.Description)
        else -> Pair(TextSecondary, Icons.Default.InsertDriveFile)
    }

    // File extension tag badge
    val ext = if (entry.isDirectory) {
        "DIR"
    } else {
        entry.name.substringAfterLast('.', "").uppercase().take(5).ifEmpty { "FILE" }
    }

    // Tap-to-preview hint
    val hint = when {
        entry.isDirectory -> "Folder"
        entry.mimeType.startsWith("video/") -> "Tap to play video"
        entry.mimeType.startsWith("audio/") -> "Tap to play audio"
        entry.mimeType.startsWith("image/") -> "Tap to preview image"
        entry.mimeType.startsWith("text/") || entry.textContentPreview != null -> "Tap to preview text"
        entry.mimeType == "application/pdf" || entry.name.endsWith(".pdf", ignoreCase = true) -> "Tap to open PDF"
        else -> "Tap to open"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariantDark.copy(alpha = 0.5f))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(itemColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = itemColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // File extension tag badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(itemColor.copy(alpha = 0.15f))
                            .border(1.dp, itemColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = ext,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = itemColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Subtitle with tap hint
                Text(
                    text = if (entry.isDirectory) "Directory" else "${CryptoEngine.formatBytes(entry.sizeBytes)} · $hint",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!entry.isDirectory) {
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun FilePreviewModal(
    entry: DecryptedFileEntry,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "${entry.mimeType} · ${CryptoEngine.formatBytes(entry.sizeBytes)}",
                            fontSize = 11.sp,
                            color = TealLight
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (entry.mimeType.startsWith("image/") && entry.localFileUri != null) {
                        AsyncImage(
                            model = entry.localFileUri,
                            contentDescription = entry.name,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (entry.mimeType.startsWith("video/") && entry.localFileUri != null) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(TealPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = "Video",
                                    tint = TealLight,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                                        putExtra(VideoPlayerActivity.EXTRA_URI, entry.localFileUri.toString())
                                        putExtra(VideoPlayerActivity.EXTRA_NAME, entry.name)
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "▶ Play Video Fullscreen",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    } else if (entry.textContentPreview != null) {
                        val scroll = rememberScrollState()
                        Text(
                            text = entry.textContentPreview,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scroll)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = TealLight,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${entry.mimeType.ifEmpty { "application/octet-stream" }} · ${CryptoEngine.formatBytes(entry.sizeBytes)}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    FileViewerHelper.openWithExternalApp(context, entry)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(0.92f)
                            ) {
                                Text(
                                    text = "🚀 Open in External App",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    FileViewerHelper.shareFile(context, entry)
                                },
                                border = BorderStroke(1.dp, CardBorder),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(0.92f)
                            ) {
                                Text(
                                    text = "📤 Share File",
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
