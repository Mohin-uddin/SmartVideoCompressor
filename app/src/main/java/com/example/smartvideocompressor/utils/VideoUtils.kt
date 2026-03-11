package com.example.smartvideocompressor.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.smartvideocompressor.model.VideoInfo

object VideoUtils {

    /**
     * Extract full video metadata from a URI.
     */
    fun extractVideoInfo(context: Context, uri: Uri): VideoInfo? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            val width = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0

            val height = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0

            val bitrate = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_BITRATE
            )?.toLongOrNull() ?: 0L

            retriever.release()

            val fileSizeBytes = getFileSize(context, uri)
            val fileName = getFileName(context, uri)

            VideoInfo(
                uri = uri,
                name = fileName,
                fileSizeBytes = fileSizeBytes,
                durationMs = durationMs,
                width = width,
                height = height,
                bitrateBps = bitrate
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get file size from URI.
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                cursor.moveToFirst()
                if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get display name from URI.
     */
    fun getFileName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                if (nameIndex != -1) cursor.getString(nameIndex) else "video.mp4"
            } ?: "video.mp4"
        } catch (e: Exception) {
            "video.mp4"
        }
    }

    /**
     * Format bytes to human-readable string.
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> "%.2f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024L -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    /**
     * Format bitrate to human-readable string.
     */
    fun formatBitrate(bps: Long): String {
        return when {
            bps >= 1_000_000L -> "%.1f Mbps".format(bps / 1_000_000.0)
            bps >= 1_000L -> "%.0f Kbps".format(bps / 1_000.0)
            else -> "$bps bps"
        }
    }
}
