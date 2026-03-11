package com.example.smartvideocompressor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartvideocompressor.ui.screens.HomeScreen
import com.example.smartvideocompressor.ui.theme.SmartVideoCompressorTheme
import com.example.smartvideocompressor.viewmodel.VideoCompressorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartVideoCompressorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VideoCompressorApp()
                }
            }
        }
    }
}

@Composable
fun VideoCompressorApp() {
    val viewModel: VideoCompressorViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    HomeScreen(
        uiState = uiState,
        viewModel = viewModel,
        onStartCompression = { viewModel.startCompression() }
    )
}
