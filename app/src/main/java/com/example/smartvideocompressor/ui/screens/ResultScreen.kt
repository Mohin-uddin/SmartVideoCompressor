package com.example.smartvideocompressor.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartvideocompressor.model.CompressionResult
import com.example.smartvideocompressor.ui.components.StatRow
import com.example.smartvideocompressor.ui.theme.Primary
import com.example.smartvideocompressor.ui.theme.Secondary
import com.example.smartvideocompressor.ui.theme.Success
import com.example.smartvideocompressor.utils.VideoUtils
import com.example.smartvideocompressor.viewmodel.VideoCompressorUiState
import kotlin.apply
import kotlin.ranges.coerceAtLeast
import kotlin.text.format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    uiState: VideoCompressorUiState,
    onSave: () -> Unit,
    onCompressAnother: () -> Unit
) {
    val result = uiState.compressionResult ?: return
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "result_scale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compression Complete", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCompressAnother) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
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
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Box(
                modifier = Modifier
                    .scale(scale)
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Success.copy(alpha = 0.3f), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(72.dp)
                )
            }

            Text(
                "Compression Successful!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Success,
                textAlign = TextAlign.Center
            )

            SavingsHighlightCard(result = result)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Compression Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    StatRow(
                        "Original Size",
                        VideoUtils.formatFileSize(result.originalSizeBytes)
                    )
                    Spacer(Modifier.height(6.dp))
                    StatRow(
                        "Compressed Size",
                        VideoUtils.formatFileSize(result.compressedSizeBytes),
                        valueColor = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    StatRow(
                        "Space Saved",
                        VideoUtils.formatFileSize(result.spaceSavedBytes),
                        valueColor = Success
                    )
                    Spacer(Modifier.height(6.dp))
                    StatRow(
                        "Compression Ratio",
                        "%.1fx smaller".format(1.0 / result.compressionRatio.coerceAtLeast(0.01))
                    )
                    Spacer(Modifier.height(6.dp))
                    StatRow(
                        "Size Reduction",
                        "%.1f%%".format(result.spaceSavedPercent),
                        valueColor = Success
                    )
                }
            }


            AnimatedVisibility(
                visible = uiState.savedUri != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Success.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Success)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Saved to Movies/SmartCompressor",
                            color = Success,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(result.compressedUri, "video/mp4")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Play with..."))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Default.PlayCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Preview Compressed Video", fontWeight = FontWeight.SemiBold)
            }

            // Save button
            if (uiState.savedUri == null) {
                Button(
                    onClick = onSave,
                    enabled = !uiState.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Saving...")
                    } else {
                        Icon(Icons.Default.SaveAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save to Gallery", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Share button
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, result.compressedUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share compressed video"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Share Video", fontWeight = FontWeight.SemiBold)
            }

            // Compress another
            TextButton(
                onClick = onCompressAnother,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Compress Another Video")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SavingsHighlightCard(result: CompressionResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Primary.copy(alpha = 0.15f), Secondary.copy(alpha = 0.15f))
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SizeStat(
                    label = "Before",
                    value = "%.1f".format(result.originalSizeMb),
                    unit = "MB",
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        "-%.0f%%".format(result.spaceSavedPercent),
                        fontWeight = FontWeight.ExtraBold,
                        color = Success,
                        fontSize = 14.sp
                    )
                }

                SizeStat(
                    label = "After",
                    value = "%.1f".format(result.compressedSizeMb),
                    unit = "MB",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SizeStat(label: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Spacer(Modifier.width(2.dp))
            Text(
                unit,
                style = MaterialTheme.typography.bodySmall,
                color = color,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}
