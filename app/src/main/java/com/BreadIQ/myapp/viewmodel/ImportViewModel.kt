package com.BreadIQ.myapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.BreadIQ.myapp.core.ConversionUnit
import com.BreadIQ.myapp.core.IngredientClassifier
import com.BreadIQ.myapp.core.IngredientDensityConverter
import com.BreadIQ.myapp.core.IngredientLineParser
import com.BreadIQ.myapp.core.ImportAnalysisOutcome
import com.BreadIQ.myapp.core.ImportAnalysisResult
import com.BreadIQ.myapp.core.ImportAnalyzer
import com.BreadIQ.myapp.core.ImportEnvironmentInput
import com.BreadIQ.myapp.core.ImportFermentationType
import com.BreadIQ.myapp.core.ImportIngredientInput
import com.BreadIQ.myapp.core.ImportPreFerment
import com.BreadIQ.myapp.core.ImportURLFetchOutcome
import com.BreadIQ.myapp.core.ImportURLFetching
import com.BreadIQ.myapp.core.IngredientCategory
import com.BreadIQ.myapp.core.RecipeScanOutcome
import com.BreadIQ.myapp.core.TemperatureFormatting
import com.BreadIQ.myapp.data.BackendImportURLFetcher
import com.BreadIQ.myapp.data.TemperatureUnitStore
import com.BreadIQ.myapp.model.TemperatureUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** One editable row in Step 1's ingredient list. Port of `ImportModal.tsx`'s `IngredientRow` interface (lines 331-339). */
data class ImportRow(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val quantityStr: String = "",
    val unit: String = "g",
    val category: IngredientCategory = IngredientCategory.UNKNOWN,
    val grams: Double = 0.0,
    val flagged: String? = null,
)

/**
 * Step 2's environment form state. Port of `ImportModal.tsx`'s
 * `EnvSettings` interface (lines 341-348) — string-backed temp/hours
 * fields match the source's own `TextInput` value type exactly, so
 * [ImportViewModel.analyze]'s `Number(env.ambientTempF) || 75` fallback
 * parsing ports directly rather than needing a separate "is this a valid
 * number" concept. `ambientTemp`/`waterTemp` hold text in the CURRENT
 * display unit's space (Fahrenheit or Celsius, whichever the user has
 * selected app-wide), not always Fahrenheit despite the source field
 * names (`ambientTempF`/`waterTempF`) implying otherwise — renamed here
 * to drop the misleading `F` suffix.
 */
data class ImportEnvState(
    val recipeName: String = "",
    val ambientTemp: String = "75",
    val waterTemp: String = "85",
    val fermentationType: ImportFermentationType = ImportFermentationType.STRAIGHT,
    val coldRetardHours: String = "16",
    val preFerment: ImportPreFerment = ImportPreFerment.NONE,
)

data class ImportUiState(
    val step: Int = 1,
    val rows: List<ImportRow> = listOf(ImportRow(), ImportRow(), ImportRow()),
    val env: ImportEnvState = ImportEnvState(),
    val result: ImportAnalysisResult? = null,
    val loading: Boolean = false,
    val analyzeError: String? = null,
    val urlStr: String = "",
    val urlLoading: Boolean = false,
    val urlError: String = "",
    val urlConfidence: String = "",
    val ocrLoading: Boolean = false,
    val ocrError: String = "",
    val ocrMode: Boolean = false,
    /** Mirrors the source's `@Environment(TemperatureUnitStore.self)` — same stub-until-a-Settings-screen-exists boundary as `CalculatorUiState.temperatureUnit`. */
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
)

val ImportUiState.canContinueFromStep1: Boolean
    get() = rows.any { it.category == IngredientCategory.FLOUR && it.grams > 0 }

/**
 * Ported from the iOS app's `Screens/ImportModal.swift` — the 3-step
 * ingredients → environment → results wizard. Presented from the
 * Calculator tab's "Import" button, same "genuinely single-call-site"
 * precedent already used for `ScheduleViewModel`.
 *
 * **Genuinely self-contained, confirmed directly against the source**:
 * there is no "Apply to Calculator" action anywhere in `ImportModal.swift`
 * — Step 3 displays the computed [ImportAnalysisResult] entirely within
 * the modal itself, and the modal is presented with only an `onClose`
 * callback. This ViewModel doesn't write into [CalculatorViewModel] or
 * any shared state; it's a standalone "what would this formula look
 * like" tool.
 *
 * **`Task { await scanRecipe(source:) }`'s picker/camera launch itself
 * has no counterpart here** — [handleScanOutcome] only consumes an
 * already-resolved [RecipeScanOutcome]; the Compose call site
 * (`ImportScreen.kt`) is the one that calls
 * `com.BreadIQ.myapp.ui.components.rememberRecipeScanner`'s launcher,
 * matching the Context/Activity boundary every other camera/picker-needing
 * feature in this app already established.
 */
class ImportViewModel(
    temperatureUnitStore: TemperatureUnitStore,
    private val urlFetcher: ImportURLFetching = BackendImportURLFetcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ImportUiState(
            temperatureUnit = temperatureUnitStore.unit.value,
            env = ImportEnvState(
                ambientTemp = TemperatureFormatting.editableText(75.0, temperatureUnitStore.unit.value),
                waterTemp = TemperatureFormatting.editableText(85.0, temperatureUnitStore.unit.value),
            ),
        ),
    )
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private fun update(block: (ImportUiState) -> ImportUiState) {
        _uiState.value = block(_uiState.value)
    }

    private fun updateRow(id: String, transform: (ImportRow) -> ImportRow) {
        update { state -> state.copy(rows = state.rows.map { if (it.id == id) transform(it) else it }) }
    }

    fun goToStep(step: Int) = update { it.copy(step = step) }

    // MARK: - Step 1: ingredient row editing

    fun updateRowName(id: String, name: String) = updateRow(id) { it.copy(name = name) }

    /** `handleNameBlur(_:)`. */
    fun handleNameBlur(id: String) {
        val row = _uiState.value.rows.firstOrNull { it.id == id } ?: return
        val category = IngredientClassifier.classifyName(row.name)
        val unit = IngredientDensityConverter.defaultUnit(category)
        val qty = row.quantityStr.toDoubleOrNull() ?: 0.0
        val conv = IngredientDensityConverter.convertToGrams(qty, unit.rawValue, category, row.name)
        updateRow(id) { it.copy(category = category, unit = unit.rawValue, grams = conv.grams, flagged = conv.flagged) }
    }

    /** `handleQtyChange(_:)` — fires on every keystroke, matching the source's own `.onChange(of: row.wrappedValue.quantityStr)`. */
    fun updateRowQuantity(id: String, quantityStr: String) {
        updateRow(id) { it.copy(quantityStr = quantityStr) }
        val row = _uiState.value.rows.firstOrNull { it.id == id } ?: return
        val qty = quantityStr.toDoubleOrNull() ?: 0.0
        val conv = IngredientDensityConverter.convertToGrams(qty, row.unit, row.category, row.name)
        updateRow(id) { it.copy(grams = conv.grams, flagged = conv.flagged) }
    }

    /** No source recompute handler is attached to the unit field itself (only name-blur/qty-change trigger [IngredientDensityConverter.convertToGrams]) — free-typing a unit just stores the raw text, matching the source exactly. */
    fun updateRowUnit(id: String, unit: String) = updateRow(id) { it.copy(unit = unit) }

    fun addRow() = update { it.copy(rows = it.rows + ImportRow()) }

    fun removeRow(id: String) = update { it.copy(rows = it.rows.filterNot { row -> row.id == id }) }

    // MARK: - Step 1 actions: URL fetch / OCR scan

    /** `rowsFromLines(_:lowConfidence:)` — `lowConfidence` in the source is accepted but never read inside the function body, so it's dropped here rather than carried forward as dead weight. */
    private fun rowsFromLines(lines: List<String>): List<ImportRow> = lines.map { line ->
        val parsed = IngredientLineParser.parseIngredientLine(line)
        val cleaned = IngredientLineParser.cleanIngredientName(parsed.name.ifEmpty { line })
        val category = IngredientClassifier.classifyName(cleaned.name)
        val normUnit = IngredientDensityConverter.normalizeUnit(parsed.unit)
        val defaultUnit = IngredientDensityConverter.defaultUnit(category)
        val bestUnit = if (normUnit != ConversionUnit.COUNT) normUnit else defaultUnit
        val qty = parsed.quantityStr.toDoubleOrNull() ?: 0.0
        val conv = IngredientDensityConverter.convertToGrams(qty, bestUnit.rawValue, category, cleaned.name)
        val needsReview = parsed.quantityStr.isEmpty() || category == IngredientCategory.UNKNOWN
        ImportRow(
            name = cleaned.name, quantityStr = parsed.quantityStr, unit = bestUnit.rawValue, category = category, grams = conv.grams,
            flagged = cleaned.flag ?: conv.flagged ?: (if (needsReview) "OCR — please verify" else null),
        )
    }

    fun updateUrlStr(value: String) = update { it.copy(urlStr = value, urlError = "") }

    /** `fetchURL()`. */
    fun fetchURL() {
        val trimmed = _uiState.value.urlStr.trim()
        if (trimmed.isEmpty()) return
        update { it.copy(urlLoading = true, urlError = "", urlConfidence = "") }
        viewModelScope.launch {
            when (val outcome = urlFetcher.fetchIngredients(trimmed)) {
                is ImportURLFetchOutcome.Success -> {
                    val filtered = IngredientLineParser.filterIngredientLines(outcome.result.ingredients)
                    val newRows = rowsFromLines(filtered)
                    update { it.copy(rows = newRows.ifEmpty { listOf(ImportRow()) }, urlConfidence = outcome.result.confidence, urlLoading = false) }
                }
                is ImportURLFetchOutcome.Failure -> update { it.copy(urlError = outcome.error.message, urlLoading = false) }
            }
        }
    }

    /** Fired by the Compose call site right before launching the camera/library picker — matches the source's own `ocrLoading = true` set at the top of `scanRecipe(source:)`, ahead of the (on Android, Compose-owned) capture call itself. */
    fun beginScan() = update { it.copy(ocrLoading = true, ocrError = "") }

    /** The back half of `scanRecipe(source:)` — see this class's own doc comment for why the picker/camera launch itself isn't here. */
    fun handleScanOutcome(outcome: RecipeScanOutcome) {
        update { it.copy(ocrLoading = false) }
        when (outcome) {
            is RecipeScanOutcome.Recognized -> {
                val text = outcome.text.trim()
                if (text.isEmpty()) {
                    update { it.copy(ocrError = "We had trouble reading that image. Try better lighting or a cleaner angle, or use manual entry instead.") }
                    return
                }
                val lines = IngredientLineParser.extractIngredientLines(text)
                if (lines.isEmpty()) {
                    update { it.copy(ocrError = "We couldn't find ingredients in that image. Try manual entry instead.") }
                    return
                }
                val newRows = rowsFromLines(lines)
                update { it.copy(rows = newRows.ifEmpty { listOf(ImportRow()) }, ocrMode = true, ocrError = "") }
            }
            RecipeScanOutcome.Cancelled -> Unit
            is RecipeScanOutcome.Failure -> update { it.copy(ocrError = outcome.error.message) }
        }
    }

    // MARK: - Step 2: environment

    fun updateRecipeName(value: String) = update { it.copy(env = it.env.copy(recipeName = value)) }
    fun updateAmbientTemp(value: String) = update { it.copy(env = it.env.copy(ambientTemp = value)) }
    fun updateWaterTemp(value: String) = update { it.copy(env = it.env.copy(waterTemp = value)) }
    fun updateFermentationType(value: ImportFermentationType) = update { it.copy(env = it.env.copy(fermentationType = value)) }
    fun updateColdRetardHours(value: String) = update { it.copy(env = it.env.copy(coldRetardHours = value)) }
    fun updatePreFerment(value: ImportPreFerment) = update { it.copy(env = it.env.copy(preFerment = value)) }

    /** `analyze()`. */
    fun analyze() {
        val s = _uiState.value
        val ingredients: List<ImportIngredientInput> = s.rows
            .filter { it.name.trim().isNotEmpty() && it.grams > 0 }
            .map { row ->
                val classified = IngredientClassifier.classify(row.name)
                ImportIngredientInput(
                    name = row.name, category = row.category, grams = row.grams,
                    flourType = classified.flourType, yeastType = classified.yeastType,
                    fatSource = classified.fatSource, sugarSource = classified.sugarSource,
                    dairySource = classified.dairySource, flagged = row.flagged,
                )
            }

        val ambientFallback = if (s.temperatureUnit == TemperatureUnit.FAHRENHEIT) 75.0 else TemperatureFormatting.fahrenheitToCelsius(75.0)
        val waterFallback = if (s.temperatureUnit == TemperatureUnit.FAHRENHEIT) 85.0 else TemperatureFormatting.fahrenheitToCelsius(85.0)
        val environment = ImportEnvironmentInput(
            ambientTempF = TemperatureFormatting.toFahrenheit(s.env.ambientTemp.toDoubleOrNull() ?: ambientFallback, s.temperatureUnit),
            waterTempF = TemperatureFormatting.toFahrenheit(s.env.waterTemp.toDoubleOrNull() ?: waterFallback, s.temperatureUnit),
            fermentationType = s.env.fermentationType,
            coldRetardHours = if (s.env.fermentationType == ImportFermentationType.COLD) (s.env.coldRetardHours.toDoubleOrNull() ?: 16.0) else null,
            preFerment = s.env.preFerment,
        )

        update { it.copy(loading = true) }
        val outcome = ImportAnalyzer.analyze(
            ingredients = ingredients,
            recipeName = s.env.recipeName.ifEmpty { null },
            environment = environment,
            temperatureUnit = s.temperatureUnit,
        )
        when (outcome) {
            is ImportAnalysisOutcome.Success -> update { it.copy(loading = false, result = outcome.result, analyzeError = null, step = 3) }
            is ImportAnalysisOutcome.Failure -> update { it.copy(loading = false, analyzeError = "Recipe must contain at least one flour ingredient.") }
        }
    }
}

class ImportViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("breadiq_prefs", Context.MODE_PRIVATE)
        @Suppress("UNCHECKED_CAST")
        return ImportViewModel(temperatureUnitStore = TemperatureUnitStore(prefs)) as T
    }
}
