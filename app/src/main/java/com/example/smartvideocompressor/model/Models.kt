package com.example.smartvideocompressor.model

import android.net.Uri

data class VideoInfo(
    val uri: Uri,
    val name: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val bitrateBps: Long = 0L,
    val frameRate: Double = 30.0,
) {
    val fileSizeMb: Double get() = fileSizeBytes / (1024.0 * 1024.0)
    val durationSeconds: Double get() = durationMs / 1000.0
    val resolution: String get() = "${width}x${height}"
    val durationFormatted: String
        get() {
            val totalSec = durationMs / 1000
            val hours = totalSec / 3600
            val minutes = (totalSec % 3600) / 60
            val seconds = totalSec % 60
            return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds)
            else "%02d:%02d".format(minutes, seconds)
        }
}

data class CompressionResult(
    val originalUri: Uri,
    val compressedUri: Uri,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
) {
    val originalSizeMb: Double get() = originalSizeBytes / (1024.0 * 1024.0)
    val compressedSizeMb: Double get() = compressedSizeBytes / (1024.0 * 1024.0)
    val compressionRatio: Double
        get() = if (originalSizeBytes > 0)
            compressedSizeBytes.toDouble() / originalSizeBytes.toDouble()
        else 0.0
    val spaceSavedBytes: Long get() = originalSizeBytes - compressedSizeBytes
    val spaceSavedMb: Double get() = spaceSavedBytes / (1024.0 * 1024.0)
    val spaceSavedPercent: Double
        get() = if (originalSizeBytes > 0)
            (spaceSavedBytes.toDouble() / originalSizeBytes.toDouble()) * 100.0
        else 0.0
}

data class CompressionParams(
    val videoBitrateBps: Long,
    val audioBitrateBps: Long,
    val targetWidth: Int,
    val targetHeight: Int,
    val estimatedOutputSizeMb: Double,
) {
    val totalBitrateKbps: Long get() = (videoBitrateBps + audioBitrateBps) / 1000
    val videoBitrateKbps: Long get() = videoBitrateBps / 1000
    val audioBitrateKbps: Long get() = audioBitrateBps / 1000
}

enum class CompressionQuality(
    val label: String,
    val description: String,
    val crf: Int,
    val scaleMultiplier: Double,
) {
    HIGH("High",   "Best quality, larger file",    crf = 23, scaleMultiplier = 1.0),
    MEDIUM("Medium", "Balanced quality & size",    crf = 28, scaleMultiplier = 0.75),
    LOW("Low",     "Smallest file, lower quality", crf = 35, scaleMultiplier = 0.5),
}