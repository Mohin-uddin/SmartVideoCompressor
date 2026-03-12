package com.example.smartvideocompressor.worker
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.smartvideocompressor.model.CompressionQuality
import com.example.smartvideocompressor.repository.VideoCompressorRepository
import androidx.core.net.toUri

class VideoCompressionWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_VIDEO_URI = "video_uri"
        const val KEY_TARGET_SIZE_MB = "target_size_mb"
        const val KEY_QUALITY = "quality"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_ORIGINAL_SIZE = "original_size"
        const val KEY_COMPRESSED_SIZE = "compressed_size"
        const val KEY_ERROR = "error"
        const val PROGRESS_KEY = "progress"

        private const val NOTIFICATION_CHANNEL_ID = "video_compression"
        private const val NOTIFICATION_ID = 1001

        fun buildRequest(
            videoUri: Uri,
            targetSizeMb: Double,
            quality: CompressionQuality = CompressionQuality.MEDIUM
        ): OneTimeWorkRequest = OneTimeWorkRequestBuilder<VideoCompressionWorker>()
            .setInputData(workDataOf(
                KEY_VIDEO_URI to videoUri.toString(),
                KEY_TARGET_SIZE_MB to targetSizeMb,
                KEY_QUALITY to quality.name
            ))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        return ForegroundInfo(NOTIFICATION_ID, buildNotification(0))
    }

    override suspend fun doWork(): Result {
        val videoUriString = inputData.getString(KEY_VIDEO_URI)
            ?: return Result.failure(workDataOf(KEY_ERROR to "No URI"))
        val targetSizeMb = inputData.getDouble(KEY_TARGET_SIZE_MB, 0.0)
            .takeIf { it > 0 }
            ?: return Result.failure(workDataOf(KEY_ERROR to "Invalid size"))
        val quality = try {
            CompressionQuality.valueOf(inputData.getString(KEY_QUALITY) ?: "MEDIUM")
        } catch (_: Exception) { CompressionQuality.MEDIUM }

        val repository = VideoCompressorRepository(context)
        val videoUri = videoUriString.toUri()
        val videoInfo = repository.getVideoInfo(videoUri)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Cannot read video"))
        val params = repository.calculateCompressionParams(videoInfo, targetSizeMb, quality)

        var outputUriString: String? = null
        var originalSize = 0L
        var compressedSize = 0L

        repository.compressVideo(videoInfo, params, quality).collect { (progress, result) ->
            when {
                progress == -1 -> return@collect
                result != null -> {
                    outputUriString = result.compressedUri.toString()
                    originalSize = result.originalSizeBytes
                    compressedSize = result.compressedSizeBytes
                }
                else -> {
                    setProgressAsync(workDataOf(PROGRESS_KEY to progress))
                    updateNotification(progress)
                }
            }
        }

        return if (outputUriString != null) {
            Result.success(workDataOf(
                KEY_OUTPUT_URI to outputUriString,
                KEY_ORIGINAL_SIZE to originalSize,
                KEY_COMPRESSED_SIZE to compressedSize
            ))
        } else {
            Result.failure(workDataOf(KEY_ERROR to "Compression failed"))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "Video Compression",
                NotificationManager.IMPORTANCE_LOW
            )
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(progress: Int) =
        NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Compressing Video")
            .setContentText("$progress% complete")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .build()

    private fun updateNotification(progress: Int) {
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(progress))
    }
}
