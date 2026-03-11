package com.example.smartvideocompressor.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartvideocompressor.ui.components.GradientProgressBar
import com.example.smartvideocompressor.ui.components.StatRow
import com.example.smartvideocompressor.ui.theme.Primary
import com.example.smartvideocompressor.utils.VideoUtils
import com.example.smartvideocompressor.viewmodel.VideoCompressorUiState
import kotlin.let

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressionScreen(
    uiState: VideoCompressorUiState,
    onCancel: () -> Unit
) {
    val progress = uiState.compressionProgress
    val videoInfo = uiState.selectedVideo
    val params = uiState.estimatedParams

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compressing...", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            AnimatedCompressionIcon()

            Spacer(Modifier.height(32.dp))

            Text(
                text = "$progress%",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = getProgressMessage(progress),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            GradientProgressBar(
                progress = progress / 100f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    videoInfo?.let {
                        StatRow(
                            "Original Size",
                            VideoUtils.formatFileSize(it.fileSizeBytes)
                        )
                        Spacer(Modifier.height(6.dp))
                        StatRow(
                            "Duration",
                            it.durationFormatted
                        )
                    }
                    params?.let {
                        Spacer(Modifier.height(6.dp))
                        StatRow(
                            "Target Bitrate",
                            "${it.videoBitrateKbps} Kbps"
                        )
                        Spacer(Modifier.height(6.dp))
                        StatRow(
                            "Output Resolution",
                            "${it.targetWidth}x${it.targetHeight}"
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Cancel, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cancel Compression", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AnimatedCompressionIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "compression_anim")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Primary.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                )
            )
    ) {
        Icon(
            imageVector = Icons.Default.Autorenew,
            contentDescription = "Compressing",
            modifier = Modifier
                .size(64.dp)
                .rotate(rotation),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

private fun getProgressMessage(progress: Int): String = when {
    progress < 10 -> "Initializing compression..."
    progress < 30 -> "Analyzing video frames..."
    progress < 60 -> "Encoding video stream..."
    progress < 80 -> "Optimizing quality..."
    progress < 95 -> "Finalizing output..."
    else -> "Almost done..."
}
