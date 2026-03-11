package com.example.smartvideocompressor.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

enum class PermissionStatus {
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
}

val videoReadPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_VIDEO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

fun Context.hasVideoPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, videoReadPermission) ==
            PackageManager.PERMISSION_GRANTED

fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}

class VideoPermissionHandler(
    val status: PermissionStatus,
    val requestOrProceed: () -> Unit,
    val openSettings: () -> Unit,
)

@Composable
fun rememberVideoPermissionHandler(
    onGranted: () -> Unit,
): VideoPermissionHandler {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionStatus by remember {
        mutableStateOf(
            if (context.hasVideoPermission()) PermissionStatus.GRANTED
            else PermissionStatus.DENIED
        )
    }
    var hasBeenDeniedBefore by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        when {
            isGranted -> {
                permissionStatus = PermissionStatus.GRANTED
                onGranted()
            }
            hasBeenDeniedBefore -> {
                permissionStatus = PermissionStatus.PERMANENTLY_DENIED
            }
            else -> {
                permissionStatus = PermissionStatus.DENIED
                hasBeenDeniedBefore = true
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && context.hasVideoPermission()) {
                permissionStatus = PermissionStatus.GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val resolvedStatus = if (context.hasVideoPermission()) PermissionStatus.GRANTED
    else permissionStatus

    return VideoPermissionHandler(
        status = resolvedStatus,
        requestOrProceed = {
            when {
                context.hasVideoPermission() -> {
                    permissionStatus = PermissionStatus.GRANTED
                    onGranted()
                }
                resolvedStatus == PermissionStatus.PERMANENTLY_DENIED -> {
                    context.openAppSettings()
                }
                else -> {
                    launcher.launch(videoReadPermission)
                }
            }
        },
        openSettings = { context.openAppSettings() },
    )
}