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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.zevbuild.zevsafe.crypto.CryptoEngine
import com.zevbuild.zevsafe.crypto.DecryptedFileEntry
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
import com.zevbuild.zevsafe.viewmodel.VaultViewModel
import java.io.File

@Composable
fun VaultBrowserScreen(
    viewModel: VaultViewModel,
    onNavigateToDecrypt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val decryptionResult by viewModel.decryptionResult.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedPreviewFile by remember { mutableStateOf<DecryptedFileEntry?>(null) }

    // File Preview Modal
    if (selectedPreviewFile != null) {
        FilePreviewModal(
            entry = selectedPreviewFile!!,
            onDismiss = { selectedPreviewFile = null }
        )
    }

    Column(
        modifier = modifier
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
                    }
                }
            }
        } else {
            val result = decryptionResult!!
            val filteredFiles = result.files.filter {
                it.path.contains(searchQuery, ignoreCase = true) || it.name.contains(searchQuery, ignoreCase = true)
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

                Spacer(modifier = Modifier.height(14.dp))

                // File List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredFiles) { fileEntry ->
                        FileItemRow(
                            entry = fileEntry,
                            onClick = {
                                if (!fileEntry.isDirectory) {
                                    selectedPreviewFile = fileEntry
                                }
                            },
                            onShare = {
                                fileEntry.localFileUri?.let { uri ->
                                    val file = File(uri.path ?: "")
                                    if (file.exists()) {
                                        val contentUri = FileProvider.getUriForFile(
                                            context,
                                            "com.aistudio.zevsafe.qvkn.fileprovider",
                                            file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = fileEntry.mimeType
                                            putExtra(Intent.EXTRA_STREAM, contentUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share ${fileEntry.name}"))
                                    }
                                }
                            }
                        )
                    }
                }
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
    val icon = when {
        entry.isDirectory -> Icons.Default.Folder
        entry.mimeType.startsWith("image/") -> Icons.Default.Image
        entry.mimeType.startsWith("text/") -> Icons.Default.Description
        entry.mimeType.startsWith("video/") -> Icons.Default.Movie
        entry.mimeType.startsWith("audio/") -> Icons.Default.MusicNote
        else -> Icons.Default.InsertDriveFile
    }

    val iconTint = when {
        entry.isDirectory -> PurpleLight
        entry.mimeType.startsWith("image/") -> TealLight
        entry.mimeType.startsWith("text/") -> Info
        else -> TextSecondary
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    text = if (entry.isDirectory) "Directory" else CryptoEngine.formatBytes(entry.sizeBytes),
                    fontSize = 11.sp,
                    color = TextSecondary
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

private val Info = Color(0xFF38BDF8)

@Composable
private fun FilePreviewModal(
    entry: DecryptedFileEntry,
    onDismiss: () -> Unit
) {
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
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (entry.mimeType.startsWith("image/") && entry.localFileUri != null) {
                        AsyncImage(
                            model = entry.localFileUri,
                            contentDescription = entry.name,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if ((entry.mimeType.startsWith("video/") || entry.mimeType.startsWith("audio/")) && entry.localFileUri != null) {
                        val playerContext = LocalContext.current
                        val exoPlayer = remember(entry.localFileUri) {
                            ExoPlayer.Builder(playerContext).build().apply {
                                setMediaItem(MediaItem.fromUri(entry.localFileUri!!))
                                prepare()
                                playWhenReady = true
                            }
                        }
                        DisposableEffect(entry.localFileUri) {
                            onDispose {
                                exoPlayer.release()
                            }
                        }
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = true
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Binary File",
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Use export to open in external application",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
