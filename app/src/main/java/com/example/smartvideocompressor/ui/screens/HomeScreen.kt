package com.example.smartvideocompressor.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.smartvideocompressor.model.CompressionParams
import com.example.smartvideocompressor.model.CompressionQuality
import com.example.smartvideocompressor.ui.components.SectionHeader
import com.example.smartvideocompressor.ui.components.StatRow
import com.example.smartvideocompressor.ui.components.VideoInfoCard
import com.example.smartvideocompressor.ui.theme.PrimaryDark
import com.example.smartvideocompressor.ui.theme.SecondaryDark
import com.example.smartvideocompressor.ui.theme.Success
import com.example.smartvideocompressor.utils.PermissionStatus
import com.example.smartvideocompressor.utils.rememberVideoPermissionHandler
import com.example.smartvideocompressor.viewmodel.VideoCompressorUiState
import com.example.smartvideocompressor.viewmodel.VideoCompressorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: VideoCompressorUiState,
    viewModel: VideoCompressorViewModel,
    onStartCompression: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isPickerOpen by remember { mutableStateOf(false) }
    var isInCooldown by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        isInCooldown = true
        scope.launch {
            delay(800)
            isInCooldown = false
            isPickerOpen = false
        }
        uri?.let { viewModel.onVideoSelected(it) }
    }

    fun launchPicker() {
        if (!isPickerOpen && !isInCooldown) {
            isPickerOpen = true
            videoPicker.launch("video/*")
        }
    }

    val permissionHandler = rememberVideoPermissionHandler(
        onGranted = { launchPicker() }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Compressor", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderBanner()

            SectionHeader("1. Select Video")

            VideoSelectorCard(
                uiState = uiState,
                permissionStatus = permissionHandler.status,
                isClickEnabled = !uiState.isLoadingVideoInfo && !isInCooldown && !isPickerOpen,
                onSelectVideo = { permissionHandler.requestOrProceed() },
                onOpenSettings = permissionHandler.openSettings,
            )

            AnimatedVisibility(
                visible = uiState.selectedVideo != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                uiState.selectedVideo?.let { VideoInfoCard(videoInfo = it) }
            }

            SectionHeader("2. Target Output Size")

            TargetSizeInput(
                value = uiState.targetSizeMb,
                onValueChange = { viewModel.onTargetSizeChanged(it) },
                maxSizeMb = uiState.selectedVideo?.fileSizeMb,
                minimumSizeMb = uiState.minimumTargetSizeMb,
                isBelowMinimum = uiState.isBelowMinimum,
            )

            SectionHeader("3. Compression Quality")

            QualitySelector(
                selected = uiState.selectedQuality,
                onSelect = { viewModel.onQualityChanged(it) },
                allQualityParams = uiState.allQualityParams,
            )

            AnimatedVisibility(
                visible = uiState.estimatedParams != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                uiState.estimatedParams?.let { EstimatedOutputCard(params = it) }
            }

            Button(
                onClick = onStartCompression,
                enabled = uiState.selectedVideo != null &&
                        uiState.targetSizeMb.toDoubleOrNull()?.let { it > 0 } == true,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Compress, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start Compression", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }

            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null,
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.dismissError() }) {
                            Icon(Icons.Default.Close, "Dismiss",
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeaderBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(colors = listOf(PrimaryDark, SecondaryDark)))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Compress Your Videos", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = Color.White)
                Text("Reduce file size while maintaining quality",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f))
            }
            Icon(Icons.Default.VideoFile, null, tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun VideoSelectorCard(
    uiState: VideoCompressorUiState,
    permissionStatus: PermissionStatus,
    isClickEnabled: Boolean,
    onSelectVideo: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = isClickEnabled) { onSelectVideo() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = CardDefaults.outlinedCardBorder()
    ) {
        when {
            uiState.isLoadingVideoInfo -> {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Reading video info...", style = MaterialTheme.typography.bodyMedium)
                }
            }
            uiState.selectedVideo != null -> {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Success,
                        modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(uiState.selectedVideo.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("Tap to change video", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            permissionStatus == PermissionStatus.PERMANENTLY_DENIED -> {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LockOpen, null, tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Permission Required", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Video access was denied. Please enable it in app Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Settings")
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.VideoLibrary, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Tap to Select Video", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("Choose a video from your gallery",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun TargetSizeInput(
    value: String,
    onValueChange: (String) -> Unit,
    maxSizeMb: Double?,
    minimumSizeMb: Double?,
    isBelowMinimum: Boolean,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { input ->
                if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) onValueChange(input)
            },
            label = { Text("Target Size (MB)") },
            placeholder = { Text("e.g. 10") },
            leadingIcon = { Icon(Icons.Default.DataUsage, contentDescription = null) },
            trailingIcon = {
                Text("MB", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp))
            },
            supportingText = {
                if (minimumSizeMb != null) {
                    Text("Minimum: %.2f MB  •  Original: %.1f MB".format(
                        minimumSizeMb, maxSizeMb ?: 0.0))
                } else {
                    maxSizeMb?.let { Text("Original: %.1f MB → Set a smaller target".format(it)) }
                }
            },
            isError = isBelowMinimum,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedVisibility(visible = isBelowMinimum) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Target is below minimum %.2f MB. Output may have no video.".format(
                        minimumSizeMb ?: 0.0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
private fun QualitySelector(
    selected: CompressionQuality,
    onSelect: (CompressionQuality) -> Unit,
    allQualityParams: Map<CompressionQuality, CompressionParams> = emptyMap(),
) {
    fun resFor(q: CompressionQuality): Pair<Int, Int>? {
        val p = allQualityParams[q] ?: return null
        return Pair(p.targetWidth, p.targetHeight)
    }
    val resMap = CompressionQuality.entries.associateWith { resFor(it) }

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompressionQuality.entries.forEach { quality ->
                val isSelected = quality == selected
                val res = resMap[quality]
                val isUnique = res != null && resMap.entries
                    .filter { it.key != quality }
                    .none { it.value == res }

                Card(
                    modifier = Modifier.weight(1f).clickable { onSelect(quality) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant),
                    border = if (isSelected) CardDefaults.outlinedCardBorder() else null
                ) {
                    Column(modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = when (quality) {
                                CompressionQuality.HIGH -> Icons.Default.HighQuality
                                CompressionQuality.MEDIUM -> Icons.Default.Tune
                                CompressionQuality.LOW -> Icons.Outlined.Compress
                            },
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(quality.label, style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface)
                        if (isUnique && res != null) {
                            Text("${res.first}x${res.second}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                        } else {
                            Text(quality.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 2)
                        }
                    }
                }
            }
        }

        val uniqueRes = resMap.values.filterNotNull().distinct()
        if (uniqueRes.size == 1) {
            Spacer(Modifier.height(6.dp))
            Text(
                "All presets output at ${uniqueRes[0].first}x${uniqueRes[0].second} for this video",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EstimatedOutputCard(params: CompressionParams) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("⚡ Estimated Output", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(8.dp))
            StatRow("Estimated Size", "~%.1f MB".format(params.estimatedOutputSizeMb),
                valueColor = MaterialTheme.colorScheme.onSecondaryContainer)
            StatRow("Video Bitrate", "${params.videoBitrateKbps} Kbps",
                valueColor = MaterialTheme.colorScheme.onSecondaryContainer)
            StatRow("Audio Bitrate", "${params.audioBitrateKbps} Kbps",
                valueColor = MaterialTheme.colorScheme.onSecondaryContainer)
            StatRow("Output Resolution", "${params.targetWidth}x${params.targetHeight}",
                valueColor = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}