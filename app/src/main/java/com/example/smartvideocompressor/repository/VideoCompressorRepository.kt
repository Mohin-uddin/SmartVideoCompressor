package com.example.smartvideocompressor.repository

import android.content.Context
import android.media.*
import android.net.Uri
import android.util.Log
import com.example.smartvideocompressor.model.CompressionParams
import com.example.smartvideocompressor.model.CompressionQuality
import com.example.smartvideocompressor.model.CompressionResult
import com.example.smartvideocompressor.model.VideoInfo
import com.example.smartvideocompressor.utils.FileUtils
import com.example.smartvideocompressor.utils.VideoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.sqrt

class VideoCompressorRepository(private val context: Context) {

    companion object {
        private const val TAG = "VideoCompressorRepo"
        private const val MIN_VIDEO_BITRATE_BPS = 150_000L
        private const val MAX_VIDEO_BITRATE_BPS = 20_000_000L
        private const val DEFAULT_AUDIO_BITRATE_BPS = 128_000L
        private const val LOW_AUDIO_BITRATE_BPS = 64_000L
        private const val TIMEOUT_US = 10_000L
        private const val MIN_BPP = 0.04
        private fun sizeCorrection(bitrateBps: Long, pixels: Int, fps: Double): Double {
            val bpp = bitrateBps.toDouble() / (pixels.toDouble() * fps.coerceAtLeast(1.0))
            return when {
                bpp >= 0.08  -> 0.97
                bpp >= 0.05  -> 0.96
                bpp >= 0.03  -> 0.94
                bpp >= 0.015 -> 0.91
                bpp >= 0.007 -> 0.87
                else         -> 0.83
            }
        }
    }

    suspend fun getVideoInfo(uri: Uri): VideoInfo? = withContext(Dispatchers.IO) {
        VideoUtils.extractVideoInfo(context, uri)
    }

    fun getMinimumTargetSizeMb(durationSeconds: Double): Double {
        val minTotalBps = MIN_VIDEO_BITRATE_BPS + LOW_AUDIO_BITRATE_BPS
        return minTotalBps * durationSeconds / (8.0 * 1024.0 * 1024.0)
    }

    fun calculateCompressionParams(
        videoInfo: VideoInfo,
        targetSizeMb: Double,
        quality: CompressionQuality
    ): CompressionParams {
        val durationSec = videoInfo.durationSeconds.coerceAtLeast(1.0)
        val totalBits = targetSizeMb * 8.0 * 1024.0 * 1024.0
        val audioBitrate = if (targetSizeMb < 5.0) LOW_AUDIO_BITRATE_BPS else DEFAULT_AUDIO_BITRATE_BPS
        val videoBits = totalBits - (audioBitrate * durationSec)
        val rawBitrate = (videoBits / durationSec).toLong()
            .coerceIn(MIN_VIDEO_BITRATE_BPS, MAX_VIDEO_BITRATE_BPS)

        // BPP correction: calculate quality resolution first, then get BPP
        val qualityW = (videoInfo.width * quality.scaleMultiplier).toInt().coerceAtLeast(320)
        val qualityH = (videoInfo.height * quality.scaleMultiplier).toInt().coerceAtLeast(240)
        val pixels = qualityW * qualityH
        val fps = videoInfo.frameRate.coerceAtLeast(1.0)
        val correction = sizeCorrection(rawBitrate, pixels, fps)
        val videoBitrate = (rawBitrate * correction).toLong()
            .coerceIn(MIN_VIDEO_BITRATE_BPS, MAX_VIDEO_BITRATE_BPS)

        val (w, h) = calculateTargetResolution(
            w = videoInfo.width,
            h = videoInfo.height,
            bitrateBps = videoBitrate,
            scaleMul = quality.scaleMultiplier,
            fps = fps,
        )
        val estimated = ((videoBitrate + audioBitrate) * durationSec) / (8.0 * 1024.0 * 1024.0)
        return CompressionParams(videoBitrate, audioBitrate, w, h, estimated)
    }

    fun compressVideo(
        videoInfo: VideoInfo,
        params: CompressionParams,
        quality: CompressionQuality
    ): Flow<Pair<Int, CompressionResult?>> = flow {
        val outputFile = FileUtils.createOutputFile(context)
        try {
            doCompress(
                uri = videoInfo.uri,
                outputFile = outputFile,
                videoInfo = videoInfo,
                params = params,
                quality = quality,
            ) { progress -> emit(Pair(progress, null)) }

            if (outputFile.exists() && outputFile.length() > 0) {
                val uri = FileUtils.getUriForFile(context, outputFile)
                emit(Pair(100, CompressionResult(
                    originalUri = videoInfo.uri,
                    compressedUri = uri,
                    originalSizeBytes = videoInfo.fileSizeBytes,
                    compressedSizeBytes = outputFile.length()
                )))
            } else {
                outputFile.delete()
                emit(Pair(-1, null))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Compression error", e)
            outputFile.delete()
            emit(Pair(-1, null))
        }
    }.flowOn(Dispatchers.IO)

    fun cancelCompression() {}

    suspend fun saveCompressedVideo(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        val file = FileUtils.getFileFromUri(context, uri) ?: return@withContext null
        FileUtils.saveVideoToGallery(context, file)
    }

    private suspend fun doCompress(
        uri: Uri,
        outputFile: File,
        videoInfo: VideoInfo,
        params: CompressionParams,
        quality: CompressionQuality,
        onProgress: suspend (Int) -> Unit
    ) {
        val extractor = MediaExtractor().also { it.setDataSource(context, uri, null) }

        var videoIdx = -1; var audioIdx = -1
        var videoFmt: MediaFormat? = null; var audioFmt: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") && videoIdx == -1) { videoIdx = i; videoFmt = fmt }
            if (mime.startsWith("audio/") && audioIdx == -1) { audioIdx = i; audioFmt = fmt }
        }
        requireNotNull(videoFmt) { "No video track found" }

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxStarted = false; var videoMuxIdx = -1; var audioMuxIdx = -1

        try {
            val inputMime = videoFmt.getString(MediaFormat.KEY_MIME)!!
            val fps = runCatching { videoFmt.getInteger(MediaFormat.KEY_FRAME_RATE) }.getOrDefault(30)
            val iFrameInterval = when (quality) {
                CompressionQuality.HIGH -> 1
                CompressionQuality.MEDIUM -> 2
                CompressionQuality.LOW -> 4
            }

            val encFmt = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, params.targetWidth, params.targetHeight
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, params.videoBitrateBps.toInt())
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
                setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()

            val decoder = MediaCodec.createDecoderByType(inputMime)
            decoder.configure(videoFmt, inputSurface, null, 0)

            encoder.start(); decoder.start()
            extractor.selectTrack(videoIdx)
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val durationUs = videoInfo.durationMs * 1000L
            val decInfo = MediaCodec.BufferInfo()
            val encInfo = MediaCodec.BufferInfo()
            var decDone = false; var encDone = false

            while (!encDone && currentCoroutineContext().isActive) {
                if (!decDone) {
                    val idx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (idx >= 0) {
                        val buf = decoder.getInputBuffer(idx)!!
                        val sz = extractor.readSampleData(buf, 0)
                        if (sz < 0) {
                            decoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            decDone = true
                        } else {
                            val pts = extractor.sampleTime
                            decoder.queueInputBuffer(idx, 0, sz, pts, 0)
                            extractor.advance()
                            if (durationUs > 0) {
                                val pct = ((pts.toDouble() / durationUs) * 85).toInt().coerceIn(1, 84)
                                onProgress(pct)
                            }
                        }
                    }
                }

                val outIdx = decoder.dequeueOutputBuffer(decInfo, TIMEOUT_US)
                if (outIdx >= 0) {
                    val eos = decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(outIdx, true)
                    if (eos) encoder.signalEndOfInputStream()
                }

                var eIdx = encoder.dequeueOutputBuffer(encInfo, TIMEOUT_US)
                while (eIdx != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    when {
                        eIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoMuxIdx = muxer.addTrack(encoder.outputFormat)
                            if (audioIdx != -1 && audioFmt != null) {
                                audioMuxIdx = muxer.addTrack(audioFmt)
                            }
                            muxer.start(); muxStarted = true
                        }
                        eIdx >= 0 -> {
                            val data = encoder.getOutputBuffer(eIdx)!!
                            if (encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                                && encInfo.size > 0 && muxStarted) {
                                data.position(encInfo.offset).limit(encInfo.offset + encInfo.size)
                                muxer.writeSampleData(videoMuxIdx, data, encInfo)
                            }
                            if (encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encDone = true
                            encoder.releaseOutputBuffer(eIdx, false)
                        }
                    }
                    eIdx = if (!encDone) encoder.dequeueOutputBuffer(encInfo, TIMEOUT_US)
                    else MediaCodec.INFO_TRY_AGAIN_LATER
                }
            }

            decoder.stop(); decoder.release()
            encoder.stop(); encoder.release()
            inputSurface.release()
            extractor.release()

            if (audioIdx != -1 && muxStarted && audioMuxIdx >= 0) {
                onProgress(88)
                val audioEx = MediaExtractor().also { it.setDataSource(context, uri, null) }
                audioEx.selectTrack(audioIdx)
                audioEx.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val buf = ByteBuffer.allocate(256 * 1024)
                val aInfo = MediaCodec.BufferInfo()
                while (currentCoroutineContext().isActive) {
                    buf.clear()
                    val sz = audioEx.readSampleData(buf, 0)
                    if (sz < 0) break
                    val codecFlags = if (audioEx.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                        MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    aInfo.set(0, sz, audioEx.sampleTime, codecFlags)
                    muxer.writeSampleData(audioMuxIdx, buf, aInfo)
                    audioEx.advance()
                }
                audioEx.release()
            }

            onProgress(99)

        } finally {
            runCatching { if (muxStarted) muxer.stop() }
            muxer.release()
        }
    }

    private fun calculateTargetResolution(
        w: Int,
        h: Int,
        bitrateBps: Long,
        scaleMul: Double,
        fps: Double,
    ): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return Pair(1280, 720)
        val qualityW = (w * scaleMul).toInt()
        val qualityH = (h * scaleMul).toInt()
        val safeFps = fps.coerceAtLeast(1.0)
        val maxPixels = bitrateBps.toDouble() / (MIN_BPP * safeFps)
        val aspect = w.toDouble() / h.toDouble()
        val maxH = sqrt(maxPixels / aspect).toInt()
        val maxW = (maxH * aspect).toInt()

        val finalW = minOf(qualityW, maxW)
            .let { if (it % 2 != 0) it - 1 else it }.coerceAtLeast(320)
        val finalH = minOf(qualityH, maxH)
            .let { if (it % 2 != 0) it - 1 else it }.coerceAtLeast(240)

        return Pair(finalW, finalH)
    }
}