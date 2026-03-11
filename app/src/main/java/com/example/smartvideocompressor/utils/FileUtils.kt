package com.example.smartvideocompressor.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    private const val AUTHORITY = "com.example.smartvideocompressor.fileprovider"

    fun createOutputFile(context: Context): File {
        val outputDir = File(context.cacheDir, "compressed").also { it.mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(outputDir, "compressed_${timestamp}.mp4")
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, AUTHORITY, file)
    }
    fun getFileFromUri(context: Context, uri: Uri): File? {
        return runCatching {
            when (uri.scheme) {
                "file" -> File(uri.path!!).takeIf { it.exists() }
                "content" -> {
                    val tempFile = File(
                        context.cacheDir,
                        "save_temp_${System.currentTimeMillis()}.mp4"
                    )
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    tempFile.takeIf { it.exists() && it.length() > 0 }
                }
                else -> null
            }
        }.getOrNull()
    }

    fun saveVideoToGallery(context: Context, sourceFile: File): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val displayName = "compressed_${timestamp}.mp4"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/SmartCompressor"
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues
            ) ?: return null

            runCatching {
                resolver.openOutputStream(uri)?.use { out ->
                    sourceFile.inputStream().use { it.copyTo(out) }
                }
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }.onFailure {
                resolver.delete(uri, null, null)
                return null
            }
            uri
        } else {
            val moviesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "SmartCompressor"
            ).also { it.mkdirs() }
            val destFile = File(moviesDir, displayName)
            runCatching {
                sourceFile.copyTo(destFile, overwrite = true)
                context.sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(destFile))
                )
                Uri.fromFile(destFile)
            }.getOrNull()
        }
    }

    fun cleanupOldCacheFiles(context: Context) {
        val cacheDir = File(context.cacheDir, "compressed")
        if (cacheDir.exists()) {
            val cutoff = System.currentTimeMillis() - (60 * 60 * 1000)
            cacheDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) file.delete()
            }
        }
    }
}