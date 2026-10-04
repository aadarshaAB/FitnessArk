package com.fitnessark.ui.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.fitnessark.util.CameraUtils
import kotlinx.coroutines.delay
import java.io.File

/**
 * F11: in-app camera for pose capture, opened instead of the system camera app so a default lens
 * and timer can actually be applied (the system camera intent has no extras for either — whatever
 * app handles it decides, which is why F11 isn't possible without an in-app camera).
 *
 * Opens on the front lens with a 5s countdown that starts automatically; tapping the shutter at
 * any point captures immediately instead of waiting out the countdown. The switch-camera button
 * lets a pose that's easier to frame from the back (e.g. Back) use the rear lens instead.
 *
 * @param poseLabel shown in the top bar (e.g. "Front").
 * @param poseKey used only for the temp file name, so a stray capture is identifiable on disk
 *   (e.g. during the kind of on-device debugging the Q20 bug needed) — the pose it's actually
 *   saved under is tracked by the caller, not parsed back out of this name.
 */
@Composable
fun InAppCameraScreen(
    poseLabel: String,
    poseKey: String,
    onCaptured: (Uri) -> Unit,
    onCaptureFailed: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var useFrontCamera by remember { mutableStateOf(true) }
    var countdown by remember { mutableIntStateOf(TIMER_SECONDS) }
    var capturing by remember { mutableStateOf(false) }
    // Blocks the switch-camera button while a bind is in flight, so rapid taps can't queue
    // overlapping unbindAll()/bindToLifecycle() calls against the shared provider.
    var binding by remember { mutableStateOf(true) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    // Set once this screen is leaving (cancelled or a capture already handed back), so a capture
    // callback that resolves after that point doesn't call back into a caller that has moved on.
    var left by remember { mutableStateOf(false) }

    fun capture() {
        if (capturing || left) return
        capturing = true
        capturePhoto(context, imageCapture, poseKey,
            onCaptured = { uri -> if (!left) { left = true; onCaptured(uri) } },
            onFailure = { capturing = false; if (!left) onCaptureFailed() }
        )
    }

    // Auto-counts down on entry and on every camera flip (a flip mid-countdown restarts it,
    // since the point you lined up for may no longer match the new lens's framing).
    LaunchedEffect(useFrontCamera) {
        countdown = TIMER_SECONDS
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        capture()
    }

    val previewView = remember { PreviewView(context) }

    // Binds (and rebinds on a camera-switch tap) whenever useFrontCamera changes; unbinds when
    // the screen leaves composition so the camera isn't left held open.
    DisposableEffect(useFrontCamera) {
        binding = true
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            if (left) return@addListener
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
                    else CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            }
            binding = false
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            left = true
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })

        // Top bar: back + pose label
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { left = true; onCancel() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel", tint = Color.White)
            }
            Text(poseLabel, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }

        // Countdown overlay
        if (countdown > 0 && !capturing) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp)
                    .background(Color.Black.copy(alpha = 0.45f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = countdown.toString(),
                    color = Color.White,
                    fontSize = 48.sp,
                    style = MaterialTheme.typography.displayMedium
                )
            }
        }

        // Bottom controls: switch camera + shutter (tap to capture immediately)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { useFrontCamera = !useFrontCamera },
                modifier = Modifier.padding(end = 40.dp),
                enabled = !binding
            ) {
                Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera", tint = Color.White)
            }
            FilledIconButton(
                onClick = { capture() },
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                enabled = !capturing
            ) {
                if (capturing) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 2.5.dp)
                }
            }
            Spacer(Modifier.width(40.dp + 48.dp))
        }
    }
}

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    poseKey: String,
    onCaptured: (Uri) -> Unit,
    onFailure: () -> Unit
) {
    // createTempCameraUri gives a FileProvider Uri backed by a real temp file CameraX can stream
    // the JPEG into directly via an OutputStream, the same temp-file convention the old
    // system-camera flow used, so the rest of the save path (ViewModel, PhotoRepository) needs no
    // in-app-camera-specific handling.
    val uri = CameraUtils.createTempCameraUri(context, "inapp_camera_$poseKey")
    val outputStream = context.contentResolver.openOutputStream(uri)
    if (outputStream == null) {
        onFailure()
        return
    }
    val options = ImageCapture.OutputFileOptions.Builder(outputStream).build()
    imageCapture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onCaptured(uri)
            }
            override fun onError(exception: ImageCaptureException) {
                // Don't leave a failed attempt's empty/partial file for the 24h stale-file sweep
                // to find later — nothing will ever write to it again. FileProvider's content://
                // delete() isn't guaranteed on every OEM, so fall back to the real path, which is
                // safe here since CameraUtils.createTempCameraUri just created this exact file.
                runCatching { context.contentResolver.delete(uri, null, null) }
                    .onFailure { uri.path?.let { File(it).delete() } }
                onFailure()
            }
        }
    )
}

private const val TIMER_SECONDS = 5
