package com.BreadIQ.myapp.core

// MARK: - URL import — POST /api/import/fetch-url

data class ImportURLFetchResult(
    val ingredients: List<String>,
    /**
     * `"high"` (schema.org markup found) or `"low"` (fell back to
     * page-text scraping) — `trySchemaOrg`/`tryTextParsing` in
     * `artifacts/api-server/src/routes/import.ts`.
     */
    val confidence: String,
)

data class ImportURLFetchError(val message: String)

sealed class ImportURLFetchOutcome {
    data class Success(val result: ImportURLFetchResult) : ImportURLFetchOutcome()
    data class Failure(val error: ImportURLFetchError) : ImportURLFetchOutcome()
}

/**
 * Seam for the "Backend REST API client" item (`POST /api/import/fetch-url`)
 * — the schema.org/page-text scraping itself stays server-side (fetching
 * arbitrary third-party HTML client-side and re-implementing
 * `trySchemaOrg`/`tryTextParsing` locally would be a materially bigger
 * and riskier undertaking than replicating a keyword classifier, unlike
 * `/api/import/analyze`'s pure math, which was worth porting locally as
 * [ImportAnalyzer]).
 */
interface ImportURLFetching {
    suspend fun fetchIngredients(url: String): ImportURLFetchOutcome
}

object UnconfiguredImportURLFetcher : ImportURLFetching {
    override suspend fun fetchIngredients(url: String): ImportURLFetchOutcome =
        ImportURLFetchOutcome.Failure(ImportURLFetchError("URL import isn't available yet — check back once the backend connection is finished."))
}

// MARK: - Camera/library OCR scan

data class ImportScanError(val message: String)

enum class RecipeScanSource { CAMERA, LIBRARY }

/**
 * The three outcomes `handleScanRecipe` actually distinguishes in the
 * source — a plain success/failure result can't represent "user
 * cancelled" as anything other than a failure, but the source's own
 * control flow treats it as a true no-op: `if (pickerResult.canceled ||
 * !pickerResult.assets?.[0]) return;` shows no error at all, unlike every
 * other failure path (permission denial, empty OCR text, a thrown
 * exception), which all call `setOcrError` with real copy.
 */
sealed class RecipeScanOutcome {
    data class Recognized(val text: String) : RecipeScanOutcome()
    data object Cancelled : RecipeScanOutcome()
    data class Failure(val error: ImportScanError) : RecipeScanOutcome()
}

/**
 * Seam for "Camera + Photo Library capture" and "on-device OCR" —
 * `PHPickerViewController`/`UIImagePickerController` for capture,
 * `VNRecognizeTextRequest` for text recognition on iOS; the Android
 * Photo Picker/CameraX + ML Kit Text Recognition equivalents live in
 * [RecipeScanner] (pure OCR/resize) and `ui/components/RecipeScanCapture.kt`
 * (the Compose-layer picker/camera orchestration — see that file's own
 * doc comment for why the real implementation can't be a single
 * `core/`-layer class the way the source's is). Returns raw recognized
 * text; [IngredientLineParser.extractIngredientLines] does the actual
 * ingredient-line extraction from it, same split as the source
 * (`recognizeText` vs. `extractIngredientLines` are two separate
 * functions there too).
 */
interface RecipeScanning {
    suspend fun scan(source: RecipeScanSource): RecipeScanOutcome
}

object UnconfiguredRecipeScanner : RecipeScanning {
    override suspend fun scan(source: RecipeScanSource): RecipeScanOutcome =
        RecipeScanOutcome.Failure(ImportScanError("Recipe scanning isn't available yet — check back once camera capture is finished."))
}

// MARK: - Staged import — GET /api/import/staged/:token (Safari-extension deep link)

data class StagedImportPayload(
    val recipeName: String? = null,
    val sourceURL: String? = null,
    val ingredients: List<StagedImportIngredient>,
    val confidence: String,
    val flags: List<String>,
)

data class StagedImportFetchError(val message: String)

sealed class StagedImportFetchOutcome {
    data class Success(val payload: StagedImportPayload) : StagedImportFetchOutcome()
    data class Failure(val error: StagedImportFetchError) : StagedImportFetchOutcome()
}

/**
 * Seam for the "Backend REST API client" item (`GET /api/import/staged/:token`)
 * — the row this reads was written by the Safari extension via
 * `POST /api/import/stage` and is atomically consumed (deleted)
 * server-side on fetch, so this can only ever be called once per token;
 * nothing about that requires local implementation.
 */
interface ImportStagingFetching {
    suspend fun fetchStagedImport(token: String): StagedImportFetchOutcome
}

object UnconfiguredImportStagingFetcher : ImportStagingFetching {
    override suspend fun fetchStagedImport(token: String): StagedImportFetchOutcome =
        StagedImportFetchOutcome.Failure(StagedImportFetchError("Failed to load import — check back once the backend connection is finished."))
}

// MARK: - Pending imports inbox — GET /api/import/staged (Chrome-extension companion)

/**
 * One row from the list-all-mine endpoint — deliberately thinner than
 * [StagedImportPayload] (no ingredients/confidence/flags): this only
 * needs to render a picker row (`PendingImportsListScreen`). Selecting
 * one hands its `token` to the existing single-token [ImportStagingFetching]
 * flow above unchanged — this type never reaches [CalculatorImportMapping]
 * directly.
 */
data class StagedImportListItem(
    val token: String,
    val recipeName: String? = null,
    val sourceURL: String? = null,
) {
    val id: String get() = token
}

data class ImportInboxFetchError(val message: String)

sealed class ImportInboxFetchOutcome {
    data class Success(val items: List<StagedImportListItem>) : ImportInboxFetchOutcome()
    data class Failure(val error: ImportInboxFetchError) : ImportInboxFetchOutcome()
}

/**
 * Seam for the Chrome-extension companion's pending-imports inbox.
 * ██ STUB-BACKEND ██ — `GET /api/import/staged` (list, no token in the
 * path — distinct from [ImportStagingFetching]'s existing
 * `GET /api/import/staged/:token`, which stays exactly as it is) does not
 * exist on `api-server` yet.
 */
interface ImportInboxFetching {
    suspend fun fetchPendingImports(): ImportInboxFetchOutcome
}

object UnconfiguredImportInboxFetcher : ImportInboxFetching {
    override suspend fun fetchPendingImports(): ImportInboxFetchOutcome =
        ImportInboxFetchOutcome.Failure(ImportInboxFetchError("Import list isn't available yet — check back once the backend is finished."))
}
