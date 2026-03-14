package com.example.smartvideocompressor.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.smartvideocompressor.model.CompressionParams
import com.example.smartvideocompressor.model.CompressionQuality
import com.example.smartvideocompressor.model.CompressionResult
import com.example.smartvideocompressor.model.VideoInfo
import com.example.smartvideocompressor.repository.VideoCompressorRepository
import com.example.smartvideocompressor.utils.FileUtils
import com.example.smartvideocompressor.worker.VideoCompressionWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class VideoCompressorUiState(
    val selectedVideo: VideoInfo? = null,
    val targetSizeMb: String = "",
    val selectedQuality: CompressionQuality = CompressionQuality.MEDIUM,
    val estimatedParams: CompressionParams? = null,
    val allQualityParams: Map<CompressionQuality, CompressionParams> = emptyMap(),
    val compressionProgress: Int = 0,
    val isCompressing: Boolean = false,
    val compressionResult: CompressionResult? = null,
    val isSaving: Boolean = false,
    val savedUri: Uri? = null,
    val error: String? = null,
    val isLoadingVideoInfo: Boolean = false,
    val minimumTargetSizeMb: Double? = null,
    val isBelowMinimum: Boolean = false,
)

class VideoCompressorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoCompressorRepository(application)
    private val workManager = WorkManager.getInstance(application)

    private val _uiState = MutableStateFlow(VideoCompressorUiState())
    val uiState: StateFlow<VideoCompressorUiState> = _uiState.asStateFlow()

    private var currentWorkId: UUID? = null


    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingVideoInfo = true, error = null) }
            var videoInfo = repository.getVideoInfo(uri)

            if (videoInfo == null) {
                val retryDelays = listOf(300L, 600L)
                for (delayMs in retryDelays) {
                    if (videoInfo != null) break
                    delay(delayMs)
                    videoInfo = repository.getVideoInfo(uri)
                }
            }

            if (videoInfo != null) {
                val minSizeMb = repository.getMinimumTargetSizeMb(videoInfo.durationSeconds)
                _uiState.update {
                    it.copy(
                        selectedVideo = videoInfo,
                        isLoadingVideoInfo = false,
                        compressionResult = null,
                        savedUri = null,
                        error = null,
                        minimumTargetSizeMb = minSizeMb,
                        isBelowMinimum = false,
                    )
                }
                recalculateParams()
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingVideoInfo = false,
                        error = "Could not read video information. Please select a valid video file."
                    )
                }
            }
        }
    }

    // ── Target Size & Quality ─────────────────────────────────────────────────

    fun onTargetSizeChanged(value: String) {
        _uiState.update { it.copy(targetSizeMb = value) }
        recalculateParams()
        revalidateMinimum(value)
    }

    fun onQualityChanged(quality: CompressionQuality) {
        _uiState.update { it.copy(selectedQuality = quality) }
        recalculateParams()
    }

    private fun revalidateMinimum(targetValue: String) {
        val minMb = _uiState.value.minimumTargetSizeMb ?: return
        val targetMb = targetValue.toDoubleOrNull()
        val isBelowMinimum = targetMb != null && targetMb < minMb
        _uiState.update { it.copy(isBelowMinimum = isBelowMinimum) }
    }

    private fun recalculateParams() {
        val state = _uiState.value
        val videoInfo = state.selectedVideo ?: return
        val targetMb = state.targetSizeMb.toDoubleOrNull() ?: return
        if (targetMb <= 0) return
        val allParams = CompressionQuality.entries.associateWith { q ->
            repository.calculateCompressionParams(videoInfo, targetMb, q)
        }
        val selectedParams = allParams[state.selectedQuality]
        _uiState.update { it.copy(estimatedParams = selectedParams, allQualityParams = allParams) }
    }

    fun startCompression() {
        val state = _uiState.value
        val videoInfo = state.selectedVideo ?: run {
            _uiState.update { it.copy(error = "No video selected.") }
            return
        }
        val targetMb = state.targetSizeMb.toDoubleOrNull() ?: run {
            _uiState.update { it.copy(error = "Please enter a valid target size.") }
            return
        }
        if (targetMb <= 0) {
            _uiState.update { it.copy(error = "Target size must be greater than 0.") }
            return
        }

        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                videoInfo.uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val request = VideoCompressionWorker.buildRequest(
            videoUri = videoInfo.uri,
            targetSizeMb = targetMb,
            quality = state.selectedQuality
        )
        currentWorkId = request.id

        _uiState.update {
            it.copy(
                isCompressing = true,
                compressionProgress = 0,
                compressionResult = null,
                error = null
            )
        }

        workManager.enqueue(request)

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { workInfo ->
                if (workInfo == null) return@collect

                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getInt(VideoCompressionWorker.PROGRESS_KEY, 0)
                        _uiState.update { it.copy(compressionProgress = progress) }
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        val outputUriString = workInfo.outputData.getString(VideoCompressionWorker.KEY_OUTPUT_URI)
                        val originalSize = workInfo.outputData.getLong(VideoCompressionWorker.KEY_ORIGINAL_SIZE, 0L)
                        val compressedSize = workInfo.outputData.getLong(VideoCompressionWorker.KEY_COMPRESSED_SIZE, 0L)

                        if (outputUriString != null && compressedSize > 0) {
                            val result = CompressionResult(
                                originalUri = videoInfo.uri,
                                compressedUri = Uri.parse(outputUriString),
                                originalSizeBytes = originalSize,
                                compressedSizeBytes = compressedSize
                            )
                            _uiState.update {
                                it.copy(isCompressing = false, compressionProgress = 100, compressionResult = result)
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isCompressing = false,
                                    compressionProgress = 0,
                                    error = "Compression finished but output file is missing."
                                )
                            }
                        }
                    }

                    WorkInfo.State.FAILED -> {
                        val errorMsg = workInfo.outputData.getString(VideoCompressionWorker.KEY_ERROR)
                            ?: "Compression failed. Please try again or choose a higher target size."
                        _uiState.update {
                            it.copy(isCompressing = false, compressionProgress = 0, error = errorMsg)
                        }
                    }

                    WorkInfo.State.CANCELLED -> {
                        _uiState.update { it.copy(isCompressing = false, compressionProgress = 0) }
                    }

                    else -> {}
                }
            }
        }
    }

    fun cancelCompression() {
        currentWorkId?.let { workManager.cancelWorkById(it) }
        currentWorkId = null
        _uiState.update { it.copy(isCompressing = false, compressionProgress = 0) }
    }

    fun saveCompressedVideo() {
        val result = _uiState.value.compressionResult ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val savedUri = repository.saveCompressedVideo(result.compressedUri)
            if (savedUri != null) {
                _uiState.update { it.copy(isSaving = false, savedUri = savedUri) }
            } else {
                _uiState.update {
                    it.copy(isSaving = false, error = "Failed to save video. Please check storage permissions.")
                }
            }
        }
    }

    fun resetToHome() {
        _uiState.update { VideoCompressorUiState() }
        FileUtils.cleanupOldCacheFiles(getApplication())
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        cancelCompression()
    }
}