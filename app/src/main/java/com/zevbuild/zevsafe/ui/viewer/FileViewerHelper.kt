package com.zevbuild.zevsafe.ui.viewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.zevbuild.zevsafe.crypto.DecryptedFileEntry
import java.io.File

/**
 * Helper for opening unsupported files in external viewers or sharing them via Android intents.
 * Operates strictly offline on files extracted to internal storage.
 */
object FileViewerHelper {

    /**
     * Resolves the extracted local file from [entry] and launches an external viewer
     * application via Intent.ACTION_VIEW using dynamic FileProvider authority.
     * If no activity handles the MIME type, falls back gracefully to [shareFile].
     */
    fun openWithExternalApp(context: Context, entry: DecryptedFileEntry) {
        val fileUri = entry.localFileUri ?: return
        val file = File(fileUri.path ?: "")
        if (!file.exists()) return

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, entry.mimeType.ifEmpty { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(viewIntent, "Open ${entry.name} with...")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            shareFile(context, entry)
        }
    }

    /**
     * Shares the extracted local file from [entry] using Intent.ACTION_SEND
     * with the system share sheet.
     */
    fun shareFile(context: Context, entry: DecryptedFileEntry) {
        val fileUri = entry.localFileUri ?: return
        val file = File(fileUri.path ?: "")
        if (!file.exists()) return

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = entry.mimeType.ifEmpty { "*/*" }
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share ${entry.name}")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) {
            // Graceful silent fallback
        }
    }
}
