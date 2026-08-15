package com.BreadIQ.myapp.core

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Port of the real (non-UI) half of `Core/RecipeScanner.swift` — the
 * resize-before-OCR step and the text-recognition call itself
 * (`lib/textRecognition.ts`'s `recognizeText`, iOS's own upgrade of it
 * to `VNRecognizeTextRequest`). ML Kit Text Recognition
 * (`com.google.mlkit:text-recognition`, the bundled on-device variant,
 * not the cloud API) is the direct Android analog of `VNRecognizeTextRequest`
 * — confirmed offline, same as Vision: the model ships in the app binary,
 * no network call at inference time.
 *
 * **The picker/camera capture half of the source file lives in
 * `ui/components/RecipeScanCapture.kt`, not here** — a deliberate,
 * necessary split, not a scope-creep reorganization. Every other `core/`
 * file in this codebase is Compose-free pure Kotlin (verified directly —
 * no file in that package imports `androidx.compose` anything), and camera
 * capture/photo-picker launching are inherently Compose/Activity-bound
 * on Android (`rememberLauncherForActivityResult`, a live `PreviewView`
 * surface for CameraX) in a way iOS's `PHPickerViewController`/
 * `UIImagePickerController` aren't — those can be presented by grabbing
 * the app's root view controller from any plain class, with no
 * Composable/Activity ownership required. [resized]/[recognizeText]
 * below are this file's real, working, UI-independent counterpart of the
 * source's own `resized(_:maxWidth:)`/`recognizeText(in:)` — callable and
 * testable standalone, exactly like the source's non-`private`
 * `recognizeText(in:)` entry point.
 */
object RecipeScanner {

    private val genericFailureMessage =
        "We had trouble reading that image. Try better lighting or a cleaner angle, or use manual entry instead."

    /**
     * Matches the source's preprocessing step exactly: resize to a
     * 2000px-wide bitmap before OCR (bounds memory/processing time for
     * full-resolution camera photos). ML Kit doesn't need this for
     * accuracy — it works at any resolution — this is preserved purely
     * for parity with the source's own pipeline, per that file's own
     * comment.
     */
    fun resized(bitmap: Bitmap, maxWidth: Int = 2000): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap
        val scale = maxWidth.toFloat() / bitmap.width
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
    }

    /**
     * `lib/textRecognition.ts`'s `recognizeText` / the source's
     * `recognizeText(in:)`. [bitmap] is expected to already be upright
     * (EXIF-rotation-corrected) by the time it reaches here — that
     * correction happens at the picker/camera decode step in
     * `ui/components/RecipeScanCapture.kt`, since it's Uri/file-decode
     * plumbing, not OCR logic.
     *
     * ML Kit's `TextRecognizer` is callback-based
     * (`com.google.android.gms.tasks.Task`); bridged to a suspend
     * function directly via [suspendCancellableCoroutine] rather than
     * pulling in the separate `kotlinx-coroutines-play-services`
     * dependency just for its one `Task.await()` extension.
     *
     * Joins ML Kit's `Text.TextBlock.Line`s (flattened across every
     * block, in the order ML Kit returns them) with `"\n"` — the closest
     * Android analog of the source's own
     * `observations.compactMap { $0.topCandidates(1).first?.string }.joined(separator: "\n")`,
     * which joins one string per `VNRecognizedTextObservation` (roughly a
     * detected text line/region). ML Kit's `Line` is the equivalent
     * granularity — a block is a paragraph-like cluster of lines, not a
     * single recognized string, so lines (not blocks) are what get
     * joined here.
     */
    suspend fun recognizeText(bitmap: Bitmap): RecipeScanOutcome {
        val processed = resized(bitmap)
        val image = InputImage.fromBitmap(processed, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.textBlocks.flatMap { block -> block.lines.map { it.text } }.joinToString("\n")
                    if (continuation.isActive) continuation.resumeWith(Result.success(RecipeScanOutcome.Recognized(text)))
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(RecipeScanOutcome.Failure(ImportScanError(genericFailureMessage))))
                    }
                }
        }
    }
}
