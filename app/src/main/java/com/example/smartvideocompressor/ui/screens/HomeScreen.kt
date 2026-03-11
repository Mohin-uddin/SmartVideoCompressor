package com.example.smartvideocompressor.ui.screens

import android.Manifest
import android.os.Build
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
import com.example.smartvideocompressor.viewmodel.VideoCompressorUiState
import com.example.smartvideocompressor.viewmodel.VideoCompressorViewModel
import kotlin.collections.forEach
import kotlin.let
import kotlin.text.format
import kotlin.text.isEmpty
import kotlin.text.matches
import kotlin.text.toDoubleOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: VideoCompressorUiState,
    viewModel: VideoCompressorViewModel,
    onStartCompression: () -> Unit
) {
    val scrollState = rememberScrollState()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled implicitly by video picker */ }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onVideoSelected(it) }
    }

    fun launchVideoPicker() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest. permission.READ_MEDIA_VIDEO
        } else {
            Manifest. permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
        videoPicker.launch("video/*")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Smart Compressor",
                        fontWeight = FontWeight.Bold
                    )
                },
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
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderBanner()

            SectionHeader("1. Select Video")

            VideoSelectorCard(
                uiState = uiState,
                onSelectVideo = { launchVideoPicker() }
            )

            AnimatedVisibility(
                visible = uiState.selectedVideo != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                uiState.selectedVideo?.let { video ->
                    VideoInfoCard(videoInfo = video)
                }
            }

            SectionHeader("2. Target Output Size")

            TargetSizeInput(
                value = uiState.targetSizeMb,
                onValueChange = { viewModel.onTargetSizeChanged(it) },
                maxSizeMb = uiState.selectedVideo?.fileSizeMb
            )

            SectionHeader("3. Compression Quality")

            QualitySelector(
                selected = uiState.selectedQuality,
                onSelect = { viewModel.onQualityChanged(it) }
            )

            AnimatedVisibility(
                visible = uiState.estimatedParams != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                uiState.estimatedParams?.let { params ->
                    EstimatedOutputCard(params = params)
                }
            }

            Button(
                onClick = onStartCompression,
                enabled = uiState.selectedVideo != null &&
                        uiState.targetSizeMb.toDoubleOrNull()?.let { it > 0 } == true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Compress, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Start Compression",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.dismissError() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
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
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(PrimaryDark, SecondaryDark)
                )
            )
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Compress Your Videos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "Reduce file size while maintaining quality",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            Icon(
                imageVector = Icons.Default.VideoFile,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun VideoSelectorCard(
    uiState: VideoCompressorUiState,
    onSelectVideo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectVideo() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        if (uiState.isLoadingVideoInfo) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Reading video info...", style = MaterialTheme.typography.bodyMedium)
            }
        } else if (uiState.selectedVideo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Video selected – tap to change",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Tap to Select Video",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Choose a video from your gallery",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TargetSizeInput(
    value: String,
    onValueChange: (String) -> Unit,
    maxSizeMb: Double?
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                onValueChange(input)
            }
        },
        label = { Text("Target Size (MB)") },
        placeholder = { Text("e.g. 10") },
        leadingIcon = {
            Icon(Icons.Default.DataUsage, contentDescription = null)
        },
        trailingIcon = {
            Text(
                "MB",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp)
            )
        },
        supportingText = {
            maxSizeMb?.let {
                Text("Original: %.1f MB → Set a smaller target".format(it))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun QualitySelector(
    selected: CompressionQuality,
    onSelect: (CompressionQuality) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompressionQuality.entries.forEach { quality ->
            val isSelected = quality == selected
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(quality) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (isSelected) CardDefaults.outlinedCardBorder() else null
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = when (quality) {
                            CompressionQuality.HIGH -> Icons.Default.HighQuality
                            CompressionQuality.MEDIUM -> Icons.Default.Tune
                            CompressionQuality.LOW -> Icons.Outlined.Compress
                        },
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        quality.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        quality.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
@Composable
private fun EstimatedOutputCard(
    params: CompressionParams
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "⚡ Estimated Output",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(8.dp))
            StatRow(
                "Estimated Size",
                "~%.1f MB".format(params.estimatedOutputSizeMb),
                valueColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            StatRow(
                "Video Bitrate",
                "${params.videoBitrateKbps} Kbps",
                valueColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            StatRow(
                "Audio Bitrate",
                "${params.audioBitrateKbps} Kbps",
                valueColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            StatRow(
                "Output Resolution",
                "${params.targetWidth}x${params.targetHeight}",
                valueColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
