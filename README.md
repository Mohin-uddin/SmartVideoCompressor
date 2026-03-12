# Smart Video Compressor

An Android application that compresses video files to a user-specified target size while maintaining acceptable playback quality. Built as part of a mobile development assignment using Jetpack Compose and Android's native MediaCodec API.

---

## Assignment Requirements

### 1. Video Selection
Select a video from the device gallery using the system file picker. The following metadata is extracted and displayed before compression begins:
- File size (formatted as KB, MB, or GB)
- Duration (formatted as mm:ss or hh:mm:ss)
- Resolution (width x height)
- Original bitrate

### 2. Target Size Input
The user enters a desired output size in MB. The app automatically calculates the required video bitrate from that value using the formula:

```
total bits    = target MB * 8 * 1024 * 1024
audio budget  = audio bitrate bps * duration seconds
video bitrate = (total bits - audio budget) / duration seconds
```

An estimated output card is shown before compression starts, displaying the calculated video bitrate, audio bitrate, output resolution, and estimated size.

### 3. Video Compression
Compression is performed using Android's MediaCodec API. The output size closely matches the target because bitrate is calculated from the target rather than using a fixed quality setting. Resolution is also scaled down automatically when the bitrate is too low to support the original dimensions, ensuring the output remains watchable at around 5% of the original file size.

### 4. Processing and Progress
Compression runs in a Kotlin coroutine on the IO dispatcher, launched from the ViewModel. The UI remains fully responsive during processing. A dedicated compression screen displays a live percentage progress bar, a status message, and the current compression parameters. The user can cancel at any time by cancelling the coroutine job.

### 5. Result Screen
After compression completes, the result screen shows:
- Original file size
- Compressed file size
- Space saved (in bytes and as a percentage)
- Compression ratio
- Save to Gallery button
- Preview button (opens the compressed video in the device's default video player)

### Bonus: Implemented
- Estimated output bitrate is shown before compression starts
- Three quality presets (High, Medium, Low) let the user control the resolution scale multiplier
- Share button on the result screen allows exporting the compressed video to any app via Android's share sheet

---

## Approach

The core challenge was producing a compressed video that is actually watchable at around 5% of the original file size, and whose output size is close to what the user requested.

**Bitrate targeting**

Rather than using a fixed quality setting, the app works backwards from the desired file size. The target size is converted to bits, an audio budget is subtracted, and the remaining bits are divided by the video duration to get the required video bitrate. Bitrate is clamped between 50,000 bps and 20,000,000 bps. For targets under 5 MB, audio is reduced to 64 kbps instead of 128 kbps to give more budget to the video track.

**Adaptive resolution scaling**

Encoding at full resolution with a very low bitrate produces a blocky, unwatchable result. The app scales down the output resolution in steps based on the calculated bitrate:

```
under 200 kbps  -> 40% of original dimensions
under 500 kbps  -> 60%
under 1000 kbps -> 75%
above 1000 kbps -> original resolution
```

The quality preset also applies a scale multiplier, and whichever is smaller is used. Width and height are always rounded to even numbers since H.264 requires even dimensions.

**MediaCodec transcode pipeline**

1. MediaExtractor reads the source video and identifies video and audio tracks
2. A MediaCodec decoder decodes video frames and renders them to a Surface
3. A MediaCodec encoder (H.264 / AVC) reads from that Surface and re-encodes at the target bitrate
4. When the encoder output format is ready, both video and audio tracks are added to MediaMuxer before calling start() — this ordering is critical to avoid producing a file with audio but no video
5. A second MediaExtractor copies the audio track directly without re-encoding
6. The finished MP4 is written to the app's cache directory

**Navigation**

Navigation is state-based rather than NavHost. The ViewModel's UiState determines which of the three screens is shown — home, compression progress, or result.

**Permission handling**

A lifecycle-aware composable tracks denial state across multiple requests. First denial keeps the normal state so the user can try again. Second denial shows an "Open Settings" button. An ON_RESUME lifecycle observer rechecks permission automatically when the user returns from the system settings screen.

---

## Libraries Used

All video processing uses Android's built-in SDK APIs. No third-party video or compression libraries are used.

| Library | Purpose |
|---|---|
| Jetpack Compose + Material3 | UI framework and components |
| ViewModel + StateFlow | State management and lifecycle-aware UI state |
| MediaCodec | Hardware-accelerated H.264 video encoding and decoding |
| MediaExtractor | Reading video and audio samples from the source file |
| MediaMuxer | Writing the final MP4 output |
| MediaMetadataRetriever | Extracting video metadata (duration, resolution, bitrate) |
| FileProvider (AndroidX Core) | Generating safe content URIs for sharing and preview |
| WorkManager | Background compression worker (CoroutineWorker) |
| Lifecycle Compose | ON_RESUME observer for permission recheck after Settings |
| Coroutines + Flow | Async compression pipeline with progress emission |

---

## Project Structure

```
app/src/main/java/com/example/smartvideocompressor/
    model/
        Models.kt                    -- VideoInfo, CompressionResult, CompressionParams, CompressionQuality
    repository/
        VideoCompressorRepository.kt -- compression logic using MediaCodec
    ui/
        components/
            Components.kt            -- VideoInfoCard, GradientProgressBar, StatRow, SectionHeader
        screens/
            HomeScreen.kt            -- video selection, target size input, quality picker
            CompressionScreen.kt     -- progress display
            ResultScreen.kt          -- result, save, share, preview
        theme/
            Color.kt, Theme.kt, Type.kt
    utils/
        FileUtils.kt                 -- output file creation, FileProvider URIs, gallery save
        PermissionHandler.kt         -- runtime permission with lifecycle-aware recheck
        VideoUtils.kt                -- metadata extraction and formatting helpers
    viewmodel/
        VideoCompressorViewModel.kt  -- UiState, compression job management
    worker/
        VideoCompressionWorker.kt    -- WorkManager worker
    MainActivity.kt                  -- entry point, state-based navigation
```

---

## Limitations

- Compression size accuracy is approximate. H.264 encoding is inherently variable bitrate, so actual output size can differ from the target, especially for high-motion content at low bitrates.
- Audio is copied without re-encoding. For very short videos, the audio track can account for a significant portion of the total file size.
- Very short videos (under a few seconds) may not hit the target size accurately due to fixed overhead from codec headers and container metadata.
- Only MP4 output is supported. Input can be any format that Android's MediaCodec can decode, but MKV, AVI, and some HEVC sources may not work on all devices.
- The WorkManager integration is present in the codebase but the main UI flow runs compression in a ViewModel coroutine rather than a background work request.

---

## Setup

1. Clone the repository
2. Open in Android Studio Hedgehog or newer
3. Sync Gradle
4. Run on a physical device or emulator with API 24 or higher
5. Grant video permission when prompted on first launch