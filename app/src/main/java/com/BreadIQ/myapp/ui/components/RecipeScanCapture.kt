package com.BreadIQ.myapp.ui.components

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.BreadIQ.myapp.core.ImportScanError
import com.BreadIQ.myapp.core.RecipeScanOutcome
import com.BreadIQ.myapp.core.RecipeScanSource
import com.BreadIQ.myapp.core.RecipeScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * The Compose-layer half of the iOS app's `Core/RecipeScanner.swift` —
 * real Photo Picker + CameraX capture, feeding into
 * [RecipeScanner.recognizeText] (the pure OCR half, ported as-is in
 * `core/RecipeScanner.kt` — see that file's own doc comment for why the
 * split exists at all).
 *
 * **Library path — a deliberate, real upgrade over the source, matching
 * it exactly for the right reason.** The source's own upgrade note
 * explains it switched to `PHPickerViewController` specifically because
 * it runs the picker UI out-of-process and hands back only the one photo
 * the user selects — no photo-library permission prompt, no
 * "access denied" error path AT ALL, since there's no permission to
 * deny. Android's Photo Picker (`ActivityResultContracts.PickVisualMedia`,
 * stable since `androidx.activity:activity` 1.6.0 — confirmed already
 * well below this project's existing 1.9.3 pin, no version bump needed)
 * has the exact same out-of-process, no-permission property, so no
 * `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE` is requested or declared
 * anywhere in this app.
 *
 * **Camera path — real `CAMERA` runtime permission, requested lazily
 * inside [CameraCaptureScreen] the moment it's shown**, not eagerly at
 * app launch — matching the calendar session's `READ_CALENDAR`/
 * `WRITE_CALENDAR` pattern (permission needed only at the moment of a
 * specific user action) rather than the notifications session's
 * eager-at-launch one (permission needed for background work that could
 * fire anytime). Uses CameraX (`camera-core`/`camera-camera2`/
 * `camera-lifecycle`/`camera-view`, newly added this session) for
 * capture — a live `PreviewView` (via [AndroidView] interop; CameraX's
 * newer Compose-native `camera-compose` viewfinder API exists but isn't
 * verified-stable enough yet to build against here) bound to
 * [ProcessCameraProvider], with `ImageCapture` writing a full-resolution
 * JPEG to a private cache file — then immediately deleted once decoded,
 * same "temporary, app-private, never shared" lifetime as the file never
 * needed a `FileProvider`/content `Uri` for.
 *
 * **Not wired into any screen or nav destination this session** — per
 * direct instruction, `ImportModal`/`ImportReviewScreen` (the screens
 * that will actually call [rememberRecipeScanner]) are a separate,
 * later session. [rememberRecipeScanner]/[CameraCaptureScreen] are real,
 * complete, and ready to use — same "port it for real even without a
 * caller yet" precedent already established for `ui/components/BakeStepRow.kt`
 * in an earlier session.
 */
class RecipeScannerLauncher internal constructor(
    private val scope: CoroutineScope,
    private val context: Context,
    private val contentResolver: ContentResolver,
    private val pickMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest>,
    private val onResult: (RecipeScanOutcome) -> Unit,
) {
    /** Whether [CameraCaptureScreen] should currently be shown — the caller places it conditionally on this, see this class's own doc comment. */
    var showCamera: Boolean by mutableStateOf(false)
        internal set

    private val genericFailureMessage =
        "We had trouble reading that image. Try better lighting or a cleaner angle, or use manual entry instead."

    /** `handleScanRecipe(source:)`. */
    fun launch(source: RecipeScanSource) {
        when (source) {
            RecipeScanSource.LIBRARY -> pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            RecipeScanSource.CAMERA -> showCamera = true
        }
    }

    /** The Photo Picker launcher's own result callback — `uri == null` means the user backed out without picking anything, matching [RecipeScanOutcome.Cancelled]'s own doc comment (a true no-op, not an error). */
    internal fun onLibraryResult(uri: Uri?) {
        if (uri == null) {
            onResult(RecipeScanOutcome.Cancelled)
            return
        }
        scope.launch {
            val bitmap = decodeUprightBitmapFromUri(context, uri)
            onResult(if (bitmap != null) RecipeScanner.recognizeText(bitmap) else RecipeScanOutcome.Failure(ImportScanError(genericFailureMessage)))
        }
    }

    /** Wire to [CameraCaptureScreen]'s `onCaptured`. */
    fun onCameraCaptured(bitmap: Bitmap) {
        showCamera = false
        scope.launch { onResult(RecipeScanner.recognizeText(bitmap)) }
    }

    /** Wire to [CameraCaptureScreen]'s `onCancel` — the user tapped Cancel, matches [RecipeScanOutcome.Cancelled]. */
    fun onCameraCancelled() {
        showCamera = false
        onResult(RecipeScanOutcome.Cancelled)
    }

    /** Wire to [CameraCaptureScreen]'s `onPermissionDenied` — the source's own real denial copy, distinct from a plain cancel. */
    fun onCameraPermissionDenied() {
        showCamera = false
        onResult(RecipeScanOutcome.Failure(ImportScanError("BreadIQ needs camera access to scan recipes. Enable it in Settings → BreadIQ → Camera.")))
    }
}

/**
 * `RecipeScanner()` (the concrete `RecipeScanning` conformance) as a
 * Compose-idiomatic `rememberX` handle — see [RecipeScannerLauncher]'s
 * own doc comment for the full architecture writeup. Call
 * `scanner.launch(RecipeScanSource.LIBRARY)`/`.launch(RecipeScanSource.CAMERA)`
 * from a button's `onClick`; [onResult] fires once with the outcome.
 * For the camera path, the caller must also place
 * [RecipeScannerCameraOverlay] (or [CameraCaptureScreen] directly)
 * somewhere in its composition, conditional on `scanner.showCamera`.
 */
@Composable
fun rememberRecipeScanner(onResult: (RecipeScanOutcome) -> Unit): RecipeScannerLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    val launcherHolder = remember { mutableStateOf<RecipeScannerLauncher?>(null) }
    val pickMediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        launcherHolder.value?.onLibraryResult(uri)
    }
    return remember {
        RecipeScannerLauncher(scope, context, context.contentResolver, pickMediaLauncher) { currentOnResult(it) }
            .also { launcherHolder.value = it }
    }
}

/** Turnkey camera overlay for [rememberRecipeScanner] callers that don't need any custom presentation — shows [CameraCaptureScreen] full-bleed whenever [scanner]'s camera path is active, pre-wired to its own three outcome handlers. */
@Composable
fun RecipeScannerCameraOverlay(scanner: RecipeScannerLauncher, modifier: Modifier = Modifier) {
    if (scanner.showCamera) {
        CameraCaptureScreen(
            modifier = modifier,
            onCaptured = scanner::onCameraCaptured,
            onCancel = scanner::onCameraCancelled,
            onPermissionDenied = scanner::onCameraPermissionDenied,
        )
    }
}

/**
 * The real CameraX capture UI — live preview + shutter button. Requests
 * `CAMERA` lazily the moment it's composed (see this file's own doc
 * comment on why lazily-here rather than eagerly-at-launch), and calls
 * [onPermissionDenied] once if the user declines rather than silently
 * doing nothing.
 */
@Composable
fun CameraCaptureScreen(
    modifier: Modifier = Modifier,
    onCaptured: (Bitmap) -> Unit,
    onCancel: () -> Unit,
    onPermissionDenied: () -> Unit = onCancel,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) onPermissionDenied()
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) return

    val imageCapture = remember { ImageCapture.Builder().build() }

    DisposableEffect(Unit) {
        onDispose { ProcessCameraProvider.getInstance(context).get().unbindAll() }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).also { view ->
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener(
                        {
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                        },
                        ContextCompat.getMainExecutor(ctx),
                    )
                }
            },
        )

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            TextButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
                Text(" Cancel", color = Color.White)
            }
            IconButton(
                onClick = {
                    val outputFile = File(context.cacheDir, "recipe_scan_${System.currentTimeMillis()}.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                    imageCapture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                scope.launch {
                                    val bitmap = decodeUprightBitmapFromFile(outputFile)
                                    outputFile.delete()
                                    if (bitmap != null) onCaptured(bitmap) else onCancel()
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                outputFile.delete()
                                onCancel()
                            }
                        },
                    )
                },
                modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.White),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = "Capture", tint = Color.Black)
            }
            // Balances the Cancel button's width so the shutter button
            // stays visually centered rather than drifting toward it.
            Box(modifier = Modifier.size(72.dp))
        }
    }
}

// MARK: - Uri/file decode + EXIF-upright rotation (Android-specific plumbing, not OCR logic — see RecipeScanner.kt's own doc comment for why this lives here instead)

private fun decodeUprightBitmapFromUri(context: Context, uri: Uri): Bitmap? = try {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = true }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
    val rotationDegrees = context.contentResolver.openInputStream(uri)?.use { stream -> exifRotationDegrees(ExifInterface(stream)) } ?: 0
    rotateIfNeeded(bitmap, rotationDegrees)
} catch (e: Exception) {
    null
}

private fun decodeUprightBitmapFromFile(file: File): Bitmap? = try {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
    if (bitmap == null) {
        null
    } else {
        val rotationDegrees = exifRotationDegrees(ExifInterface(file.absolutePath))
        rotateIfNeeded(bitmap, rotationDegrees)
    }
} catch (e: Exception) {
    null
}

private fun exifRotationDegrees(exif: ExifInterface): Int =
    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

private fun rotateIfNeeded(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
