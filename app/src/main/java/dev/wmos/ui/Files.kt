package dev.wmos.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens a private attachment through the FileProvider.
 *
 * The grant is read-only and temporary; if no viewer can handle the MIME type the caller gets
 * `false` back and should show a message instead of silently doing nothing.
 */
fun openPrivateFile(context: Context, uri: Uri, mimeType: String?): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType?.takeIf { it.isNotBlank() } ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}

fun formatFileSize(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> String.format("%.1f МБ", bytes / 1024.0 / 1024.0)
}
